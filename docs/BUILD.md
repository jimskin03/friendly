# Building Friendly

## Quick local build (scripts)

| OS | Command |
|---|---|
| Linux / WSL | `scripts/build.sh <debug\|nightly\|release\|check> [--version-code N] [--version-name X] [--install]` |
| Windows | `powershell -ExecutionPolicy Bypass -File scripts\build.ps1 <debug\|nightly\|release\|check> [-VersionCode N] [-VersionName X] [-Install]` |

- `check`: checks JDK 21, Android SDK cmdline-tools, Node 22+ and pnpm, and shows the certificate SHA-256 of each configured key. Start here.
- `--install` / `-Install`: installs whatever is missing. On Linux it goes into `~/.friendly-build` (JDK, Node) and `$ANDROID_HOME` (cmdline-tools). On Windows it uses winget for Temurin 21 and Node LTS, and puts cmdline-tools in `%LOCALAPPDATA%\Android\Sdk`. It also accepts the SDK licenses. Gradle then downloads the platform, build-tools and NDK on the first build.
- `debug`: nightly-flavor debug APK. No keys needed.
- `nightly`: signed nightly-flavor release APKs (nightly key), the same as the CI `nightly` channel.
- `release`: nightly APKs plus the signed Play AAB (upload key). `--version-code` is required (see [RELEASE.md](RELEASE.md)).
- Output goes to `dist/` (gitignored). Each artifact's signature is checked against the key before it's copied.

**Signing input:** environment variables first, then `keystore.properties` at the repo root (copy `keystore.properties.example`; it's gitignored).

| keystore.properties | Env var | GitHub secret |
|---|---|---|
| `nightly.storeFile` | `NIGHTLY_KEYSTORE_FILE` | `NIGHTLY_KEYSTORE_BASE64` (file content) |
| `nightly.storePassword` | `NIGHTLY_KEYSTORE_PASSWORD` | `NIGHTLY_KEYSTORE_PASSWORD` |
| `nightly.keyAlias` | `NIGHTLY_KEY_ALIAS` | `NIGHTLY_KEY_ALIAS` |
| `nightly.keyPassword` | `NIGHTLY_KEY_PASSWORD` | `NIGHTLY_KEY_PASSWORD` |
| `upload.storeFile` | `PLAY_UPLOAD_KEYSTORE_FILE` | `PLAY_UPLOAD_KEYSTORE_BASE64` (file content) |
| `upload.storePassword` | `PLAY_UPLOAD_KEYSTORE_PASSWORD` | `PLAY_UPLOAD_KEYSTORE_PASSWORD` |
| `upload.keyAlias` | `PLAY_UPLOAD_KEY_ALIAS` | `PLAY_UPLOAD_KEY_ALIAS` |
| `upload.keyPassword` | `PLAY_UPLOAD_KEY_PASSWORD` | `PLAY_UPLOAD_KEY_PASSWORD` |

The scripts and CI hand the chosen key to Gradle as `KEYSTORE_FILE`, `KEYSTORE_PASSWORD`, `KEY_ALIAS` and `KEY_PASSWORD`, which `app/build.gradle.kts` reads (env first, then `local.properties`). Without a key, release builds fall back to debug signing, so always use the scripts or CI, which check the signature. Version overrides: `-Pfriendly.versionCode=N` and `-Pfriendly.versionName=X`.

## Plain Gradle

```bash
cd web-ui && pnpm install --frozen-lockfile && cd ..   # web module's preBuild runs `pnpm run build`
./gradlew :app:assembleNightlyDebug                     # or :app:assemblePlayDebug
./gradlew :app:testNightlyDebugUnitTest                 # unit tests
```

Needs JDK 21 for the Gradle daemon (`gradle/gradle-daemon-jvm.properties`; it compiles to Java 17), an Android SDK (`ANDROID_HOME`, or `sdk.dir` in untracked `local.properties`), Node 22+, and pnpm. A full release build needs about 6 GB of RAM (Gradle runs with `-Xmx4096m`).

## Optional: run CI on your own PC (self-hosted runner)

The workflow steps are bash, so run the runner in **WSL2 (Ubuntu)** on Windows, or on any Linux box.

1. In WSL, install the tools and the SDK: `sudo apt install -y unzip jq binutils curl git && scripts/build.sh check --install`. Then `export ANDROID_HOME=~/Android/Sdk`, and install `gh` (needed by the nightly step).
2. Register the runner:
   ```bash
   mkdir ~/actions-runner && cd ~/actions-runner
   # The download URL and version are shown on GitHub: repo › Settings › Actions › Runners › New self-hosted runner › Linux x64
   TOKEN=$(gh api -X POST repos/jimskin03/friendly/actions/runners/registration-token -q .token)
   ./config.sh --url https://github.com/jimskin03/friendly --token "$TOKEN" --labels friendly --unattended
   echo "ANDROID_HOME=$HOME/Android/Sdk" >> .env
   sudo ./svc.sh install && sudo ./svc.sh start     # or ./run.sh in a terminal
   ```
3. Send the jobs to it: `gh variable set BUILD_RUNNER -R jimskin03/friendly --body friendly`. Delete the variable (`gh variable delete BUILD_RUNNER -R jimskin03/friendly`) to go back to GitHub-hosted `ubuntu-latest`. While the variable is set, jobs wait in the queue whenever the PC is off.
