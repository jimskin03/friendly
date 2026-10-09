# Releasing Friendly

Everything runs through one workflow, [`.github/workflows/build.yml`](../.github/workflows/build.yml) ("Build").

## Channels

| Channel | Trigger | Output |
|---|---|---|
| `nightly` | Schedule (09:00 and 18:00 UTC, only if `friendly-2.0` changed in the last 24 h) or manual | Nightly-flavor APKs (`app-nightly-{arm64-v8a,x86_64,universal}-release.apk`) on the `nightly` prerelease. Old assets are replaced. |
| `release` | Manual only | The same APKs plus `friendly-play-<versionName>-<versionCode>.aab` (Play flavor, upload-key signed) on GitHub release `v<versionName>-<versionCode>`. It's a **draft** by default, so nothing is public until you press Publish. The AAB is also kept as a run artifact for 30 days. |

```bash
gh workflow run build.yml -R jimskin03/friendly -f channel=nightly
gh workflow run build.yml -R jimskin03/friendly -f channel=release -f version_code=301 [-f version_name=3.0.1] [-f draft=false]
gh run watch -R jimskin03/friendly "$(gh run list -R jimskin03/friendly -w build.yml -L 1 --json databaseId -q '.[0].databaseId')"
```

**versionCode:** use the `version_code` input. When it's empty, the workflow uses `300 + run number`, which always goes up. Play rejects any upload whose versionCode is not higher than all earlier uploads. Local release builds (`scripts/build.sh release --version-code N`) must pass N explicitly. **versionName:** use the `version_name` input, or the `?: "3.0.0"` default in `app/build.gradle.kts`.

The workflow fails right away if a secret it needs is missing. It also fails if an APK is not signed with the nightly key, or if the AAB is not signed with the upload key.

## Keys and secrets

| Key | Signs | Repo secrets | Box copy (Grok Bot) |
|---|---|---|---|
| Nightly / sideload key (alias `nightly`, PKCS12, SHA-256 `4A:74:6F:58:5A:87:7F:A9:EC:8A:CC:E4:C3:68:CF:51:73:CA:05:85:4A:6C:70:92:20:53:6B:8A:E9:DA:C9:FE`) | Every GitHub APK (both channels), so nightlies and releases install over each other | `NIGHTLY_KEYSTORE_BASE64`, `NIGHTLY_KEYSTORE_PASSWORD`, `NIGHTLY_KEY_ALIAS`, `NIGHTLY_KEY_PASSWORD` | `/home/box/secrets/friendly-play/friendly-nightly.jks`, `nightly-keystore-password.txt` |
| Play upload key (alias `upload`, PKCS12, SHA-256 `32:8C:3C:59:35:8D:74:BA:B0:F2:6A:E8:37:52:E7:04:DC:2B:E4:62:E0:21:CE:8D:10:10:80:FB:CD:DE:52:5C`) | The Play AAB only. Google re-signs it with the Play app-signing key. | `PLAY_UPLOAD_KEYSTORE_BASE64`, `PLAY_UPLOAD_KEYSTORE_PASSWORD`, `PLAY_UPLOAD_KEY_ALIAS`, `PLAY_UPLOAD_KEY_PASSWORD` | `/home/box/secrets/friendly-play/friendly-upload.jks`, `keystore-password.txt` |

`GOOGLE_SERVICES_JSON` is optional (Firebase). GitHub secrets cannot be read back, so **keep your own offline copy of both .jks files and their passwords.** Never commit them. `keystore.properties`, `*.jks` and `*.keystore` are gitignored.

**One-time uninstall:** nightlies before October 2026 were signed with a throwaway debug key generated on each CI run, so no nightly could update the one before it. The first nightly signed with the nightly key needs one uninstall, and after that each nightly installs over the last. If the nightly key is ever lost, create a new one the same way (below) and uninstall once more.

### Getting the existing keys

Grok Bot will send you both `.jks` files and their passwords. Put them outside the repo (for example `C:\Users\<you>\keys\`), then fill in `keystore.properties` (copy `keystore.properties.example`).

Check a fingerprint against the table above:

```bash
keytool -list -v -keystore friendly-upload.jks -alias upload    # asks for the password; compare the SHA256 line
```

### Making a new key and updating the secrets

```bash
keytool -genkeypair -v -keystore friendly-upload.jks -storetype PKCS12 -alias upload \
  -keyalg RSA -keysize 4096 -validity 10000          # same for friendly-nightly.jks with -alias nightly

R=jimskin03/friendly
base64 -w0 friendly-upload.jks | gh secret set PLAY_UPLOAD_KEYSTORE_BASE64 -R $R   # PowerShell: [Convert]::ToBase64String([IO.File]::ReadAllBytes("friendly-upload.jks")) | gh secret set ...
gh secret set PLAY_UPLOAD_KEYSTORE_PASSWORD -R $R   # prompts; paste the password
gh secret set PLAY_UPLOAD_KEY_ALIAS -R $R --body upload
gh secret set PLAY_UPLOAD_KEY_PASSWORD -R $R        # same as the store password for PKCS12
gh secret list -R $R
```

For the nightly key, use the `NIGHTLY_*` names with alias `nightly`.

**Lost upload key:** Friendly uses Play App Signing, so the upload key can be replaced. In Play Console go to *Test and release › App integrity › App signing › Request upload key reset*, and upload the new certificate (`keytool -export -rfc -keystore new.jks -alias upload -file upload_cert.pem`). Google approves it within a few days. Then update the four `PLAY_UPLOAD_*` secrets.

## Google Play Console

1. Set up a payments profile, which is needed to sell anything.
2. Run the `release` channel and download the AAB, from the draft release or the run artifact.
3. *Testing › Internal testing › Create release*: upload the AAB, add testers (email list), and roll out. The first upload of an AAB signed with the upload key turns on Play App Signing.
4. Once an AAB that includes the BILLING permission is uploaded (needs the `play-billing-themes` branch merged), go to *Monetize › Products › One-time products* and create these **non-consumable** products with these exact IDs, then activate them. IDs can't be reused after deletion.

   | Theme | Product ID |
   |---|---|
   | Cute Minimal | `theme_cute_minimal` |
   | Cozy Night | `theme_cozy_night` |
   | Playful Doodle | `theme_playful_doodle` |
   | Glass Frost | `theme_glass_frost` |
   | All four (bundle) | `theme_pack_all` |

5. *Settings › License testing*: add tester Gmail accounts so their test purchases aren't charged.
6. Install from the internal-testing opt-in link. Play and GitHub builds share `applicationId friendly.cryptgregresearch.org` but are signed differently, so uninstall the nightly first (or use another device).
7. A privacy-policy URL is still required before going to production.
