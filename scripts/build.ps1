<#
.SYNOPSIS
  Local Friendly build for Windows. Mirrors scripts/build.sh and .github/workflows/build.yml.

.EXAMPLE
  powershell -ExecutionPolicy Bypass -File scripts\build.ps1 debug
  powershell -ExecutionPolicy Bypass -File scripts\build.ps1 nightly
  powershell -ExecutionPolicy Bypass -File scripts\build.ps1 release -VersionCode 301 [-VersionName 3.0.1]
  powershell -ExecutionPolicy Bypass -File scripts\build.ps1 check -Install

.DESCRIPTION
  Channels: debug (no signing), nightly (nightly-flavor APKs, NIGHTLY key),
  release (nightly APKs + Play AAB, PLAY_UPLOAD key; -VersionCode required), check (prerequisites only).
  -Install installs missing JDK 21 with winget and Android cmdline-tools into
  %LOCALAPPDATA%\Android\Sdk.
  Signing comes from env vars, else keystore.properties at the repo root (gitignored, see
  keystore.properties.example). Env names match the GitHub secrets, with _FILE instead of _BASE64:
    NIGHTLY_KEYSTORE_FILE  NIGHTLY_KEYSTORE_PASSWORD  NIGHTLY_KEY_ALIAS  NIGHTLY_KEY_PASSWORD
    PLAY_UPLOAD_KEYSTORE_FILE  PLAY_UPLOAD_KEYSTORE_PASSWORD  PLAY_UPLOAD_KEY_ALIAS  PLAY_UPLOAD_KEY_PASSWORD
  Outputs land in dist\.
  keystore.properties keys: nightly.storeFile/storePassword/keyAlias/keyPassword, upload.* (same four).
  GitHub secrets: the same 8 env names, with *_KEYSTORE_BASE64 (base64 of the .jks) instead of *_KEYSTORE_FILE.

.NOTES
  Full key, Play Console and self-hosted-runner steps: the header of scripts/build.sh (`bash scripts/build.sh help`).
  Key fingerprints: nightly 4A:74:6F:58:...:DA:C9:FE, upload 32:8C:3C:59:...:DE:52:5C (`check` prints them).
  Update a keystore secret from Windows:
    [Convert]::ToBase64String([IO.File]::ReadAllBytes("friendly-upload.jks")) | gh secret set PLAY_UPLOAD_KEYSTORE_BASE64 -R jimskin03/friendly
    gh secret set PLAY_UPLOAD_KEYSTORE_PASSWORD -R jimskin03/friendly   # prompts; same for PLAY_UPLOAD_KEY_PASSWORD
  `scripts\build.ps1 help` prints this text.
#>
param(
  [Parameter(Mandatory = $true, Position = 0)][ValidateSet('debug', 'nightly', 'release', 'check', 'help')][string]$Channel,
  [string]$VersionCode = $env:VERSION_CODE,
  [string]$VersionName = $env:VERSION_NAME,
  [switch]$Install
)
$ErrorActionPreference = 'Stop'
if ($Channel -eq 'help') { Get-Help $PSCommandPath -Full; exit 0 }
$Root = Split-Path -Parent $PSScriptRoot
Set-Location $Root

function Die($msg) { Write-Host "ERROR: $msg" -ForegroundColor Red; exit 1 }
function Info($msg) { Write-Host "==> $msg" }
function Refresh-Path {
  $env:Path = [Environment]::GetEnvironmentVariable('Path', 'Machine') + ';' + [Environment]::GetEnvironmentVariable('Path', 'User')
}

# ---- prerequisites -------------------------------------------------------------------------
function Get-JavaMajor($javaExe) {
  $out = & $javaExe -version 2>&1 | Out-String
  if ($out -match 'version "(\d+)') { return $Matches[1] } else { return '' }
}

function Setup-Java {
  $cands = @()
  if ($env:JAVA_HOME) { $cands += $env:JAVA_HOME }
  $cands += (Get-ChildItem 'C:\Program Files\Eclipse Adoptium' -Directory -Filter 'jdk-21*' -ErrorAction SilentlyContinue | ForEach-Object FullName)
  $cmd = Get-Command java -ErrorAction SilentlyContinue
  if ($cmd) { $cands += (Split-Path (Split-Path $cmd.Source)) }
  foreach ($h in $cands) {
    $exe = Join-Path $h 'bin\java.exe'
    if ((Test-Path $exe) -and (Get-JavaMajor $exe) -eq '21') {
      $env:JAVA_HOME = $h; $env:Path = "$h\bin;$env:Path"; return
    }
  }
  if (-not $Install) { Die 'JDK 21 not found. Set JAVA_HOME to a JDK 21 or rerun with -Install.' }
  Info 'Installing Temurin JDK 21 with winget'
  winget install --id EclipseAdoptium.Temurin.21.JDK -e --accept-source-agreements --accept-package-agreements
  Refresh-Path
  $h = Get-ChildItem 'C:\Program Files\Eclipse Adoptium' -Directory -Filter 'jdk-21*' | Select-Object -First 1 -ExpandProperty FullName
  if (-not $h) { Die 'JDK 21 install finished but was not found; open a new terminal and retry.' }
  $env:JAVA_HOME = $h; $env:Path = "$h\bin;$env:Path"
}

function Setup-Android {
  $sdk = $env:ANDROID_HOME
  if (-not $sdk) { $sdk = $env:ANDROID_SDK_ROOT }
  if (-not $sdk -and (Test-Path 'local.properties')) {
    $line = Select-String -Path 'local.properties' -Pattern '^sdk.dir=' | Select-Object -First 1
    if ($line) { $sdk = $line.Line.Substring(8).Replace('\\', '\').Replace('\:', ':') }
  }
  if (-not $sdk) { $sdk = Join-Path $env:LOCALAPPDATA 'Android\Sdk' }
  $sdkmanager = Join-Path $sdk 'cmdline-tools\latest\bin\sdkmanager.bat'
  if (-not (Test-Path $sdkmanager)) {
    if (-not $Install) { Die "Android SDK cmdline-tools not found under $sdk. Set ANDROID_HOME or rerun with -Install." }
    Info "Installing Android cmdline-tools into $sdk"
    New-Item -ItemType Directory -Force -Path "$sdk\cmdline-tools" | Out-Null
    $zip = Join-Path $env:TEMP 'cmdline-tools.zip'
    Invoke-WebRequest -Uri 'https://dl.google.com/android/repository/commandlinetools-win-13114758_latest.zip' -OutFile $zip
    Remove-Item -Recurse -Force "$sdk\cmdline-tools\latest", "$sdk\cmdline-tools\cmdline-tools" -ErrorAction SilentlyContinue
    Expand-Archive -Path $zip -DestinationPath "$sdk\cmdline-tools" -Force
    Rename-Item "$sdk\cmdline-tools\cmdline-tools" 'latest'
  }
  $env:ANDROID_HOME = $sdk
  if ($Install -or -not (Test-Path "$sdk\licenses")) {
    Info 'Accepting Android SDK licenses (AGP then downloads the platform, build-tools and NDK it needs)'
    $yes = ('y' + [Environment]::NewLine) * 30
    $yes | & $sdkmanager "--sdk_root=$sdk" --licenses | Out-Null
    & $sdkmanager "--sdk_root=$sdk" 'platform-tools' | Out-Null
  }
}

# ---- signing -------------------------------------------------------------------------------
function Get-Prop($key) {
  $f = Join-Path $Root 'keystore.properties'
  if (-not (Test-Path $f)) { return '' }
  foreach ($line in Get-Content $f) {
    if ($line.StartsWith("$key=")) { return $line.Substring($key.Length + 1).Trim("`r") }
  }
  return ''
}

# Sets KEYSTORE_FILE/KEYSTORE_PASSWORD/KEY_ALIAS/KEY_PASSWORD (read by app/build.gradle.kts).
function Use-Key($envPrefix, $propPrefix) {
  $vals = [ordered]@{
    KEYSTORE_FILE     = @("${envPrefix}_KEYSTORE_FILE", "$propPrefix.storeFile")
    KEYSTORE_PASSWORD = @("${envPrefix}_KEYSTORE_PASSWORD", "$propPrefix.storePassword")
    KEY_ALIAS         = @("${envPrefix}_KEY_ALIAS", "$propPrefix.keyAlias")
    KEY_PASSWORD      = @("${envPrefix}_KEY_PASSWORD", "$propPrefix.keyPassword")
  }
  $missing = @()
  foreach ($target in $vals.Keys) {
    $envName, $propName = $vals[$target]
    $v = [Environment]::GetEnvironmentVariable($envName)
    if (-not $v) { $v = Get-Prop $propName }
    if (-not $v) { $missing += "$envName/$propName"; continue }
    if ($target -eq 'KEYSTORE_FILE' -and -not [IO.Path]::IsPathRooted($v)) { $v = Join-Path $Root $v }
    Set-Item -Path "env:$target" -Value $v
  }
  if ($missing.Count) { Die "Missing signing values: $($missing -join ', ') (see keystore.properties.example)" }
  if (-not (Test-Path $env:KEYSTORE_FILE)) { Die "Keystore not found: $env:KEYSTORE_FILE" }
}

function Get-KeySha {
  $out = & keytool -list -v -keystore $env:KEYSTORE_FILE -storepass $env:KEYSTORE_PASSWORD -alias $env:KEY_ALIAS 2>$null | Out-String
  if ($out -match 'SHA256:\s*([0-9A-F:]+)') { return $Matches[1].Replace(':', '').ToLower() } else { return '' }
}

function Clear-Key { Remove-Item env:KEYSTORE_FILE, env:KEYSTORE_PASSWORD, env:KEY_ALIAS, env:KEY_PASSWORD -ErrorAction SilentlyContinue }

function Get-ApkSigner {
  $bt = Get-ChildItem "$env:ANDROID_HOME\build-tools" -Directory | Sort-Object { [version]($_.Name -replace '-.*$', '') } | Select-Object -Last 1
  return Join-Path $bt.FullName 'apksigner.bat'
}

function Invoke-Gradle {
  & .\gradlew.bat @args
  if ($LASTEXITCODE -ne 0) { Die "Gradle failed: $args" }
}

# ---- main ----------------------------------------------------------------------------------
Setup-Java
Setup-Android
Info "JDK: $env:JAVA_HOME"
Info "Android SDK: $env:ANDROID_HOME"

if ($Channel -eq 'release' -and -not $VersionCode) { Die 'release needs -VersionCode N (higher than every earlier Play upload; CI uses 300 + run number)' }
if ($VersionCode -and $VersionCode -notmatch '^\d+$') { Die '-VersionCode must be a number' }
# Same default versionName as CI: the literal in app/build.gradle.kts.
$defaultName = ''
if ((Get-Content 'app\build.gradle.kts' -Raw) -match '\?: "(\d[^"]*)"') { $defaultName = $Matches[1] }
$gradleArgs = @('--console=plain')
if ($VersionCode) { $gradleArgs += "-Pfriendly.versionCode=$VersionCode" }
if ($VersionName) { $gradleArgs += "-Pfriendly.versionName=$VersionName" }

if ($Channel -eq 'check') {
  foreach ($k in @(@('NIGHTLY', 'nightly'), @('PLAY_UPLOAD', 'upload'))) {
    try { Use-Key $k[0] $k[1]; Info "$($k[1]) key OK, certificate SHA-256 $(Get-KeySha)" }
    catch { Info "$($k[1]) key not configured" }
    Clear-Key
  }
  Info 'Prerequisites OK'
  exit 0
}

# Fail before the long build if a needed key is missing or unreadable.
if ($Channel -ne 'debug') { Use-Key 'NIGHTLY' 'nightly'; if (-not (Get-KeySha)) { Die 'Cannot read the nightly key (wrong password or alias?)' }; Clear-Key }
if ($Channel -eq 'release') { Use-Key 'PLAY_UPLOAD' 'upload'; if (-not (Get-KeySha)) { Die 'Cannot read the upload key (wrong password or alias?)' }; Clear-Key }

New-Item -ItemType Directory -Force -Path dist | Out-Null

if ($Channel -eq 'debug') {
  Invoke-Gradle ':app:assembleNightlyDebug' @gradleArgs
  Copy-Item app\build\outputs\apk\nightly\debug\*.apk dist\
  Info 'Debug APKs in dist\'
  exit 0
}

# Nightly-flavor APKs, signed with the nightly key (both channels)
Use-Key 'NIGHTLY' 'nightly'
$expected = Get-KeySha
if (-not $expected) { Die 'Cannot read the nightly key (wrong password or alias?)' }
Invoke-Gradle 'assembleNightlyRelease' @gradleArgs
$apksigner = Get-ApkSigner
foreach ($apk in Get-ChildItem app\build\outputs\apk\nightly\release\*.apk) {
  $out = & $apksigner verify --print-certs $apk.FullName | Out-String
  $got = if ($out -match 'SHA-256 digest: ([0-9a-f]+)') { $Matches[1] } else { '' }
  if ($got -ne $expected) { Die "$($apk.Name) signed with $got, expected nightly key $expected" }
  Copy-Item $apk.FullName dist\
}
Clear-Key
Info 'Nightly APKs in dist\'

if ($Channel -eq 'release') {
  Use-Key 'PLAY_UPLOAD' 'upload'
  $expected = Get-KeySha
  if (-not $expected) { Die 'Cannot read the upload key (wrong password or alias?)' }
  Invoke-Gradle 'bundlePlayRelease' @gradleArgs
  $aab = 'app\build\outputs\bundle\playRelease\app-play-release.aab'
  $out = & keytool -printcert -jarfile $aab | Out-String
  $got = if ($out -match 'SHA256:\s*([0-9A-F:]+)') { $Matches[1].Replace(':', '').ToLower() } else { '' }
  if ($got -ne $expected) { Die "AAB signed with $got, expected upload key $expected" }
  Clear-Key
  $name = if ($VersionName) { $VersionName } else { $defaultName }
  Copy-Item $aab "dist\friendly-play-$name-$VersionCode.aab"
  Info 'Play AAB in dist\'
}
Get-ChildItem dist | Select-Object -ExpandProperty Name
