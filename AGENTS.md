# AGENTS.md: handover for coding agents

Friendly is an Android AI assistant (Kotlin, Jetpack Compose) with an optional self-hosted Linux "Computer" host. Owner: Greg (GitHub `jimskin03`). Start with this file, then [docs/BUILD.md](docs/BUILD.md) and [docs/RELEASE.md](docs/RELEASE.md).

## Layout

| Path | What |
|---|---|
| `app/` | Android app. Flavors: `nightly` (sideload, full phone automation) and `play` (Play-safe, `BuildConfig.IS_PLAY_BUILD`). Same `applicationId friendly.cryptgregresearch.org`. |
| `ai/`, `search/`, `speech/`, `workspace/`, `document/`, `highlight/`, `common/`, `oauth/`, `videogen/`, `material3/` | Library modules |
| `web/` + `web-ui/` | Embedded web server + React UI (pnpm; built by `:web` preBuild) |
| `host/` | Self-hosted Linux Control API / MCP / noVNC stream ([host/README.md](host/README.md)) |
| `scripts/build.sh`, `scripts/build.ps1` | Local builds (debug / nightly / release / check) |
| `.github/workflows/build.yml` | CI: nightly + release channels |

## Branches

- `friendly-2.0`: the default branch and main line. The scheduled nightly builds from it.
- `play-billing-themes`: Google Play Billing for the 4 paid themes. Product IDs are `theme_cute_minimal`, `theme_cozy_night`, `theme_playful_doodle`, `theme_glass_frost`, `theme_pack_all`. The nightly flavor keeps the themes unlocked through `BuildConfig.UNLOCK_PAID_THEMES`, and the branch removes the old "nightly includes them free" text. **Pending Greg's UI approval. Do not merge before he approves.**

## Greg's rules

- He installs only from the **`nightly` GitHub release**. He has historically not built Android locally (scripts/build.ps1 now exists for his Windows PC).
- **Design or UI changes need mockups or screenshots and his explicit approval before they reach `friendly-2.0`**, because anything on `friendly-2.0` ends up in the nightly.
- No force-push. No rewriting pushed history.
- Never commit or print secrets, keystores or passwords. `keystore.properties`, `*.jks`, `*.keystore`, `local.properties` and `google-services.json` are gitignored.
- Commit as `jimskin03 <diktatorkejam@yahoo.com>`.

## Build / test / release

- Build: `scripts/build.sh check`, then `scripts/build.sh debug` (Windows: `scripts\build.ps1`). Details are in docs/BUILD.md.
- Unit tests: `./gradlew :app:testNightlyDebugUnitTest`
- Nightly: automatic, or `gh workflow run build.yml -R jimskin03/friendly -f channel=nightly`
- Release (APKs + Play AAB, draft GitHub release): `gh workflow run build.yml -R jimskin03/friendly -f channel=release -f version_code=N`
- Secrets: `NIGHTLY_KEYSTORE_BASE64`, `NIGHTLY_KEYSTORE_PASSWORD`, `NIGHTLY_KEY_ALIAS`, `NIGHTLY_KEY_PASSWORD`, `PLAY_UPLOAD_KEYSTORE_BASE64`, `PLAY_UPLOAD_KEYSTORE_PASSWORD`, `PLAY_UPLOAD_KEY_ALIAS`, `PLAY_UPLOAD_KEY_PASSWORD`, and optionally `GOOGLE_SERVICES_JSON`. Key fingerprints, rotation and Play Console steps are in docs/RELEASE.md.

## Open work (October 2026)

1. **Paid themes billing:** `play-billing-themes` is waiting for Greg's approval of the Paid Themes screen. After approval, merge it into `friendly-2.0`. Resolve any conflict in `app/build.gradle.kts` by keeping the `friendly.versionCode` override that is already there.
2. **Play Console:** upload the first AAB to internal testing, create the 5 products, add license testers, and publish a privacy-policy URL (docs/RELEASE.md).
3. **Logo redesign:** concepts were shown to Greg and are waiting for his pick. They are not in the repo, so ask him.
4. **First nightly on the stable key** needs a one-time uninstall on his phone (docs/RELEASE.md).

## Known issues

- The `play` merged manifest still contains `SYSTEM_ALERT_WINDOW`, pulled in by `io.github.petterpx:floatingx-system`. Play review may ask about it. Remove it with `tools:node="remove"` in `app/src/play/AndroidManifest.xml` if it's not needed.
- Play and nightly builds share the same applicationId, so they can't be installed side by side.
- Release builds with no key configured silently fall back to debug signing in Gradle. The scripts and CI catch this by checking certificates.
- GitHub warns that the actions (checkout, setup-java, cache, setup-node, pnpm) still use Node 20. Bump to current major versions when convenient.
