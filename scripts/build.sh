#!/usr/bin/env bash
# Local Friendly build for Linux / WSL. Mirrors .github/workflows/build.yml. `scripts/build.sh help` prints this.
#
#   scripts/build.sh check [--install]            # check (or install) JDK 21, Android cmdline-tools,
#                                                 # and print each configured key's SHA-256. Installs go to ~/.friendly-build.
#   scripts/build.sh debug                        # nightly-flavor debug APK, no signing needed
#   scripts/build.sh nightly [--version-code N]   # signed nightly-flavor APKs (nightly key), same as CI channel=nightly
#   scripts/build.sh release --version-code N [--version-name X]
#                                                 # nightly APKs + Play AAB (upload key), same as CI channel=release
# Outputs land in dist/; every artifact's signature is checked first. VERSION_CODE / VERSION_NAME env also work.
#
# SIGNING: env vars first, else keystore.properties at the repo root (gitignored; copy keystore.properties.example).
#   keystore.properties keys: nightly.storeFile nightly.storePassword nightly.keyAlias nightly.keyPassword
#                             upload.storeFile  upload.storePassword  upload.keyAlias  upload.keyPassword
#   env vars: NIGHTLY_KEYSTORE_FILE NIGHTLY_KEYSTORE_PASSWORD NIGHTLY_KEY_ALIAS NIGHTLY_KEY_PASSWORD
#             PLAY_UPLOAD_KEYSTORE_FILE PLAY_UPLOAD_KEYSTORE_PASSWORD PLAY_UPLOAD_KEY_ALIAS PLAY_UPLOAD_KEY_PASSWORD
#   GitHub secrets: the same 8 names, with *_KEYSTORE_BASE64 (base64 of the .jks) instead of *_KEYSTORE_FILE.
#
# KEYS (never commit them; keep an offline copy of both .jks files + passwords):
#   nightly (alias nightly) signs every GitHub APK. SHA-256 4A:74:6F:58:5A:87:7F:A9:EC:8A:CC:E4:C3:68:CF:51:73:CA:05:85:4A:6C:70:92:20:53:6B:8A:E9:DA:C9:FE
#   upload  (alias upload) signs the Play AAB.     SHA-256 32:8C:3C:59:35:8D:74:BA:B0:F2:6A:E8:37:52:E7:04:DC:2B:E4:62:E0:21:CE:8D:10:10:80:FB:CD:DE:52:5C
#   Verify:  keytool -list -v -keystore friendly-upload.jks -alias upload   (compare the SHA256 line)
#   New key: keytool -genkeypair -v -keystore friendly-upload.jks -storetype PKCS12 -alias upload -keyalg RSA -keysize 4096 -validity 10000
#   Secrets: base64 -w0 friendly-upload.jks | gh secret set PLAY_UPLOAD_KEYSTORE_BASE64 -R jimskin03/friendly
#            gh secret set PLAY_UPLOAD_KEYSTORE_PASSWORD -R jimskin03/friendly   (prompts; same for PLAY_UPLOAD_KEY_PASSWORD)
#            gh secret set PLAY_UPLOAD_KEY_ALIAS -R jimskin03/friendly --body upload    (NIGHTLY_* + alias nightly likewise)
#   A new nightly key means one more uninstall on the phone.
#   Lost upload key: Play Console > Test and release > App integrity > App signing > Request upload key reset,
#   upload `keytool -export -rfc -keystore new.jks -alias upload -file upload_cert.pem`, then update PLAY_UPLOAD_*.
#
# PLAY CONSOLE: payments profile; Internal testing > Create release > upload the AAB (turns on Play App Signing);
#   after billing is merged, Monetize > One-time products (non-consumable): theme_cute_minimal, theme_cozy_night,
#   theme_playful_doodle, theme_glass_frost, theme_pack_all; add License testers; privacy-policy URL before production.
#
# SELF-HOSTED RUNNER (optional, WSL2/Linux): `scripts/build.sh check --install`, export ANDROID_HOME, install gh, then
#   download the runner (repo > Settings > Actions > Runners > New self-hosted runner > Linux x64) and run
#   ./config.sh --url https://github.com/jimskin03/friendly --labels friendly --unattended \
#     --token "$(gh api -X POST repos/jimskin03/friendly/actions/runners/registration-token -q .token)"
#   echo "ANDROID_HOME=$HOME/Android/Sdk" >> .env && sudo ./svc.sh install && sudo ./svc.sh start
#   gh variable set BUILD_RUNNER -R jimskin03/friendly --body friendly   (gh variable delete BUILD_RUNNER to go back)
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
TOOLS="${FRIENDLY_TOOLS_DIR:-$HOME/.friendly-build}"
CHANNEL="${1:-}"
shift || true
VERSION_CODE="${VERSION_CODE:-}"
VERSION_NAME="${VERSION_NAME:-}"
INSTALL=0
while [ $# -gt 0 ]; do
  case "$1" in
    --version-code) VERSION_CODE="$2"; shift 2 ;;
    --version-name) VERSION_NAME="$2"; shift 2 ;;
    --install) INSTALL=1; shift ;;
    *) echo "Unknown option: $1" >&2; exit 2 ;;
  esac
done
usage() { sed -n '2,/^set -euo/p' "$0" | sed '$d' | sed 's/^# \{0,1\}//'; }
case "$CHANNEL" in
  debug|nightly|release|check) ;;
  help|-h|--help) usage; exit 0 ;;
  *) usage; exit 2 ;;
esac

die() { echo "ERROR: $*" >&2; exit 1; }
info() { echo "==> $*"; }

# ---- prerequisites -------------------------------------------------------------------------
java_major() { "$1" -version 2>&1 | sed -n 's/.*version "\([0-9]*\).*/\1/p' | head -1; }

setup_java() {
  local cands=()
  [ -n "${JAVA_HOME:-}" ] && cands+=("$JAVA_HOME")
  cands+=("$TOOLS/jdk")
  command -v java >/dev/null 2>&1 && cands+=("$(dirname "$(dirname "$(readlink -f "$(command -v java)")")")")
  for h in "${cands[@]}"; do
    if [ -x "$h/bin/java" ] && [ "$(java_major "$h/bin/java")" = "21" ]; then
      export JAVA_HOME="$h"; export PATH="$JAVA_HOME/bin:$PATH"; return 0
    fi
  done
  [ "$INSTALL" = 1 ] || die "JDK 21 not found. Set JAVA_HOME to a JDK 21 or rerun with --install."
  info "Installing Temurin JDK 21 into $TOOLS/jdk"
  mkdir -p "$TOOLS/jdk"
  curl -fsSL "https://api.adoptium.net/v3/binary/latest/21/ga/linux/x64/jdk/hotspot/normal/eclipse" \
    | tar xz -C "$TOOLS/jdk" --strip-components=1
  export JAVA_HOME="$TOOLS/jdk"; export PATH="$JAVA_HOME/bin:$PATH"
}

setup_android() {
  local sdk="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-}}"
  if [ -z "$sdk" ] && [ -f "$ROOT/local.properties" ]; then
    sdk="$(sed -n 's/^sdk.dir=//p' "$ROOT/local.properties" | head -1)"
  fi
  [ -z "$sdk" ] && sdk="$HOME/Android/Sdk"
  local sdkmanager=""
  for c in "$sdk/cmdline-tools/latest/bin/sdkmanager" "$sdk"/cmdline-tools/*/bin/sdkmanager; do
    [ -x "$c" ] && { sdkmanager="$c"; break; }
  done
  if [ -z "$sdkmanager" ]; then
    [ "$INSTALL" = 1 ] || die "Android SDK cmdline-tools not found under $sdk. Set ANDROID_HOME or rerun with --install."
    info "Installing Android cmdline-tools into $sdk"
    mkdir -p "$sdk/cmdline-tools"
    local zip="$TOOLS/cmdline-tools.zip"
    mkdir -p "$TOOLS"
    curl -fsSL -o "$zip" "https://dl.google.com/android/repository/commandlinetools-linux-13114758_latest.zip"
    rm -rf "$sdk/cmdline-tools/latest" "$sdk/cmdline-tools/cmdline-tools"
    unzip -q "$zip" -d "$sdk/cmdline-tools" && mv "$sdk/cmdline-tools/cmdline-tools" "$sdk/cmdline-tools/latest"
    sdkmanager="$sdk/cmdline-tools/latest/bin/sdkmanager"
  fi
  export ANDROID_HOME="$sdk"
  if [ ! -d "$sdk/licenses" ] || [ "$INSTALL" = 1 ]; then
    info "Accepting Android SDK licenses (AGP then downloads the platform, build-tools and NDK it needs)"
    yes | "$sdkmanager" --sdk_root="$sdk" --licenses >/dev/null 2>&1 || true
    "$sdkmanager" --sdk_root="$sdk" "platform-tools" >/dev/null
  fi
}

# ---- signing -------------------------------------------------------------------------------
# prop <key>: value from keystore.properties (first '=' splits key and value).
prop() {
  local f="$ROOT/keystore.properties"
  [ -f "$f" ] || return 0
  awk -v k="$1" 'index($0, k"=") == 1 { print substr($0, length(k) + 2); exit }' "$f" | tr -d '\r'
}

# load_key <ENV_PREFIX> <props prefix> -> exports KEYSTORE_FILE/KEYSTORE_PASSWORD/KEY_ALIAS/KEY_PASSWORD
load_key() {
  local p="$1" q="$2" file pass alias kpass v
  v="${p}_KEYSTORE_FILE";     file="${!v:-}";  [ -n "$file" ]  || file="$(prop "$q.storeFile")"
  v="${p}_KEYSTORE_PASSWORD"; pass="${!v:-}";  [ -n "$pass" ]  || pass="$(prop "$q.storePassword")"
  v="${p}_KEY_ALIAS";         alias="${!v:-}"; [ -n "$alias" ] || alias="$(prop "$q.keyAlias")"
  v="${p}_KEY_PASSWORD";      kpass="${!v:-}"; [ -n "$kpass" ] || kpass="$(prop "$q.keyPassword")"
  local missing=""
  [ -n "$file" ] || missing="$missing ${p}_KEYSTORE_FILE/$q.storeFile"
  [ -n "$pass" ] || missing="$missing ${p}_KEYSTORE_PASSWORD/$q.storePassword"
  [ -n "$alias" ] || missing="$missing ${p}_KEY_ALIAS/$q.keyAlias"
  [ -n "$kpass" ] || missing="$missing ${p}_KEY_PASSWORD/$q.keyPassword"
  [ -z "$missing" ] || die "Missing signing values:$missing (see keystore.properties.example)"
  case "$file" in /*) ;; *) file="$ROOT/$file" ;; esac
  [ -s "$file" ] || die "Keystore not found: $file"
  export KEYSTORE_FILE="$file" KEYSTORE_PASSWORD="$pass" KEY_ALIAS="$alias" KEY_PASSWORD="$kpass"
}

key_sha() {
  keytool -list -v -keystore "$KEYSTORE_FILE" -storepass "$KEYSTORE_PASSWORD" -alias "$KEY_ALIAS" 2>/dev/null \
    | sed -n 's/^.*SHA256: //p' | head -1 | tr -d ':' | tr 'A-F' 'a-f'
}

apksigner_bin() { ls -d "$ANDROID_HOME"/build-tools/*/ 2>/dev/null | sort -V | tail -1 | sed 's#$#apksigner#'; }

# ---- main ----------------------------------------------------------------------------------
cd "$ROOT"
setup_java
setup_android
info "JDK: $(java_major "$JAVA_HOME/bin/java") ($JAVA_HOME)"
info "Android SDK: $ANDROID_HOME"

if [ "$CHANNEL" = "release" ] && [ -z "$VERSION_CODE" ]; then
  die "release needs --version-code N (higher than every earlier Play upload; CI uses 300 + run number)"
fi
case "$VERSION_CODE" in ''|*[!0-9]*) [ -z "$VERSION_CODE" ] || die "--version-code must be a number" ;; esac
# Same default versionName as CI: the literal in app/build.gradle.kts.
DEFAULT_VERSION_NAME="$(sed -n 's/.*?: "\([0-9][^"]*\)".*/\1/p' app/build.gradle.kts | head -1)"
GRADLE_ARGS=(--console=plain)
[ -n "$VERSION_CODE" ] && GRADLE_ARGS+=("-Pfriendly.versionCode=$VERSION_CODE")
[ -n "$VERSION_NAME" ] && GRADLE_ARGS+=("-Pfriendly.versionName=$VERSION_NAME")

if [ "$CHANNEL" = "check" ]; then
  for pair in "NIGHTLY nightly" "PLAY_UPLOAD upload"; do
    set -- $pair
    if ( load_key "$1" "$2" ) 2>/dev/null; then
      ( load_key "$1" "$2"; info "$2 key OK, certificate SHA-256 $(key_sha)" )
    else
      info "$2 key not configured (needed for: $([ "$2" = nightly ] && echo 'nightly, release' || echo release))"
    fi
  done
  info "Prerequisites OK"
  exit 0
fi

# Fail before the long build if a needed key is missing or unreadable.
if [ "$CHANNEL" != "debug" ]; then
  ( load_key NIGHTLY nightly; [ -n "$(key_sha)" ] || die "Cannot read the nightly key (wrong password or alias?)" )
fi
if [ "$CHANNEL" = "release" ]; then
  ( load_key PLAY_UPLOAD upload; [ -n "$(key_sha)" ] || die "Cannot read the upload key (wrong password or alias?)" )
fi

chmod +x gradlew
mkdir -p dist

if [ "$CHANNEL" = "debug" ]; then
  ./gradlew :app:assembleNightlyDebug "${GRADLE_ARGS[@]}"
  cp app/build/outputs/apk/nightly/debug/*.apk dist/
  info "Debug APKs in dist/"; ls -1 dist/*debug*.apk
  exit 0
fi

# Nightly-flavor APKs, signed with the nightly key (both channels)
(
  load_key NIGHTLY nightly
  expected="$(key_sha)"
  [ -n "$expected" ] || die "Cannot read the nightly key (wrong password or alias?)"
  ./gradlew assembleNightlyRelease "${GRADLE_ARGS[@]}"
  signer="$(apksigner_bin)"
  for apk in app/build/outputs/apk/nightly/release/*.apk; do
    got="$("$signer" verify --print-certs "$apk" | sed -n 's/.*SHA-256 digest: //p' | head -1)"
    [ "$got" = "$expected" ] || die "$apk signed with $got, expected nightly key $expected"
    cp "$apk" dist/
  done
)
info "Nightly APKs in dist/"

if [ "$CHANNEL" = "release" ]; then
  (
    load_key PLAY_UPLOAD upload
    expected="$(key_sha)"
    [ -n "$expected" ] || die "Cannot read the upload key (wrong password or alias?)"
    ./gradlew bundlePlayRelease "${GRADLE_ARGS[@]}"
    aab=app/build/outputs/bundle/playRelease/app-play-release.aab
    got="$(keytool -printcert -jarfile "$aab" | sed -n 's/^.*SHA256: //p' | head -1 | tr -d ':' | tr 'A-F' 'a-f')"
    [ "$got" = "$expected" ] || die "AAB signed with $got, expected upload key $expected"
    unzip -p "$aab" base/manifest/AndroidManifest.xml | strings | grep -q com.android.vending.BILLING \
      || echo "WARNING: AAB has no BILLING permission (Play Billing not merged yet)"
    cp "$aab" "dist/friendly-play-${VERSION_NAME:-$DEFAULT_VERSION_NAME}-$VERSION_CODE.aab"
  )
  info "Play AAB in dist/"
fi
ls -1 dist/
