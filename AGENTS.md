# AGENTS.md: Friendly handover

Android AI assistant (Kotlin/Compose). Owner: Greg (`jimskin03`). Main branch `friendly-2.0`; flavors `nightly` (sideload) and `play` share `applicationId friendly.cryptgregresearch.org`.

## Rules
- No force-push or rewriting pushed history. Commit as `jimskin03 <diktatorkejam@yahoo.com>`.
- Never commit or print secrets/keystores/passwords (`keystore.properties`, `*.jks`, `local.properties` are gitignored).
- Design/UI changes need mockups and Greg's approval before they reach `friendly-2.0` (everything there ships in the nightly). Greg installs only from the `nightly` GitHub release.

## Build locally
- `scripts/build.sh check --install`, then `debug` | `nightly` | `release --version-code N [--version-name X]` (Windows: `scripts\build.ps1 <channel> [-VersionCode N] [-Install]`). Output in `dist/`.
- `nightly` = signed nightly APKs (nightly key). `release` = those APKs + Play AAB (upload key).
- Tests: `./gradlew :app:testNightlyDebugUnitTest`. Needs JDK 21, Android SDK, Node 22 + pnpm (the `check` channel installs them).
- Key steps (fingerprints, verify, new key, `gh secret set`, upload-key reset), Play Console steps and the self-hosted runner: `scripts/build.sh help` (Windows notes: `scripts\build.ps1 help`).

## CI: `.github/workflows/build.yml` ("Build")
- `gh workflow run build.yml -R jimskin03/friendly -f channel=nightly`: APKs replace the assets on the `nightly` prerelease. Also scheduled 09:00/18:00 UTC if `friendly-2.0` changed in the last 24 h.
- `-f channel=release [-f version_code=N] [-f version_name=X] [-f draft=false]`: draft release `v<name>-<code>` with APKs + `friendly-play-<name>-<code>.aab`.
- versionCode: the input, else `300 + run number` (Play needs it to increase). Fails on missing secrets or wrong signatures.
- Optional: repo variable `BUILD_RUNNER` routes jobs to a self-hosted runner (unset = `ubuntu-latest`).

## Signing
- Secrets: `NIGHTLY_KEYSTORE_BASE64`, `NIGHTLY_KEYSTORE_PASSWORD`, `NIGHTLY_KEY_ALIAS`, `NIGHTLY_KEY_PASSWORD`, `PLAY_UPLOAD_KEYSTORE_BASE64`, `PLAY_UPLOAD_KEYSTORE_PASSWORD`, `PLAY_UPLOAD_KEY_ALIAS`, `PLAY_UPLOAD_KEY_PASSWORD` (optional `GOOGLE_SERVICES_JSON`).
- `keystore.properties` (copy `keystore.properties.example`): `nightly.storeFile`, `nightly.storePassword`, `nightly.keyAlias`, `nightly.keyPassword`, and `upload.` + the same four keys. Env vars use the secret names, with `*_KEYSTORE_FILE` instead of `*_KEYSTORE_BASE64`.
- The nightly key signs every GitHub APK; the upload key signs only the Play AAB (Play App Signing).
- One-time uninstall: nightlies before Oct 2026 used a throwaway debug key, so the first nightly on the stable key needs one uninstall on the phone. Play and nightly builds can't be installed side by side.

## Open items
1. `play-billing-themes` (Play Billing for paid themes; IDs `theme_cute_minimal`, `theme_cozy_night`, `theme_playful_doodle`, `theme_glass_frost`, `theme_pack_all`): waiting for Greg's UI approval. Don't merge before he approves.
2. Logo redesign: concepts shown to Greg, waiting for his pick (not in the repo; ask him).
3. Play upload: first AAB to internal testing, create the 5 products, license testers, privacy-policy URL.

## Known issues
- The `play` manifest has `SYSTEM_ALERT_WINDOW` (via floatingx-system); Play review may ask about it.
- Without a configured key, Gradle release builds fall back to debug signing; the scripts and CI check certificates to catch this.
