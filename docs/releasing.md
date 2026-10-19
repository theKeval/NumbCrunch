# Versioning and Releasing NumbCrunch

This guide covers versioning, signing, and publishing the NumbCrunch revamp
(`:numbcrunch`, application ID `io.keval.apps.numbcrunch`) to Google Play.
Guesser V1 (`:guesser_v1`) is not published and keeps its own fixed version in
`app/build.gradle`.

AI agents should also read the rules in the root [`AGENTS.md`](../AGENTS.md).

## Version Numbers

The version lives in one file, [`version.properties`](../version.properties):

```properties
VERSION_CODE=3
VERSION_NAME=1.1.0
```

`numbcrunch/build.gradle` reads it into `defaultConfig`, and the in-app About
screen shows `VERSION_NAME` at runtime. Do not set versions anywhere else.

| Field | Read by | Rule |
| --- | --- | --- |
| `VERSION_CODE` | Google Play and Android | Positive integer. Every upload must be **higher than any code ever uploaded** on any track. A code is consumed as soon as a bundle is uploaded, even if that release is discarded. Increment by exactly 1. |
| `VERSION_NAME` | Players (Play listing, About screen) | `MAJOR.MINOR.PATCH` semantic version. The build fails on any other format. |

Choosing `VERSION_NAME`:

- **PATCH** (`1.0.1` → `1.0.2`): bug fixes, copy fixes, small polish, and
  build or tooling changes.
- **MINOR** (`1.0.2` → `1.1.0`): new player-visible features or screens.
- **MAJOR** (`1.4.0` → `2.0.0`): a redesign or a change that breaks saved
  progress or established rules.

You never enter a version in Play Console. Play reads both values from the
uploaded bundle and pre-fills the release name, for example `2 (1.0.1)`.
Promoting a release between tracks reuses the same bundle and the same version.

Check the configured version and signing status at any time:

```bash
./gradlew -q :numbcrunch:printReleaseVersion
```

### Version Lifecycle

`main` always describes the next version to ship. Once a version code has been
released, no app change may be built under it again.

1. **A release is tagged** `v<name>` on `main`, for example `v1.0.1` for
   versionCode 2. That code is now consumed.
2. **The first pull request after the tag that changes the app** moves
   `version.properties` to the next version: `VERSION_CODE` + 1 and the next
   PATCH `VERSION_NAME`. It also adds a
   `## <name> (versionCode <code>) — unreleased` section at the top of
   [`CHANGELOG.md`](../CHANGELOG.md).
3. **Later pull requests before the next release keep that code.** They add
   bullets to the unreleased section and raise `VERSION_NAME` if their change
   calls for it, renaming the changelog heading to match. For example, a new
   feature turns an unreleased `1.0.2` into `1.1.0`.
4. **The next release is uploaded and tagged**, which starts the cycle again.
   Any number of merged pull requests ship together in one release.

"Changes the app" means changing any file outside these exempt paths:
`docs/`, `*.md`, `.github/`, `scripts/`, `app/` (Guesser V1),
`design_assets/`, `.idea/`, `LICENSE`, `.gitignore`, and `.gitattributes`.
Documentation-only pull requests never need a bump.

### Version Guard

[`scripts/check-version-bump.sh`](../scripts/check-version-bump.sh) enforces
the lifecycle. It compares the working tree with the latest `v*` tag and fails
when:

- app files changed but `VERSION_CODE` still equals the released code;
- `VERSION_CODE` is lower than the released code;
- the code moved but `VERSION_NAME` is not higher than the released name; or
- `CHANGELOG.md` has no `## <name> (versionCode <code>)` heading for the
  current version.

The guard runs:

- on every pull request to `main` and every push to `main`, in the
  *Version guard* GitHub Actions workflow;
- before every `:numbcrunch:bundleRelease`, including Android Studio's
  *Generate Signed App Bundle*, through the `checkVersionBump` task; and
- on demand:

  ```bash
  scripts/check-version-bump.sh
  ```

The guard needs release tags locally; run `git fetch --tags` if it reports
that none were found. To make the workflow block merging, mark the
`version-guard` check as required in the repository's branch rules on GitHub.

## Signing

### Two Keys

Play App Signing uses two keys:

- **Upload key** (yours): signs every bundle you upload. Play rejects a bundle
  signed by any other key.
- **App signing key** (Google's): Play re-signs what players install. You never
  handle it.

**Use the same upload key for every upload, permanently.** Never generate a new
one. If it is lost or leaked, request an upload key reset in Play Console under
*Test and release → App integrity → App signing*; that takes several days.

### Where the Key Lives

The upload key is kept outside the repository, in
`~/data/projects/vault/numbcrunch-release/keys/`:

| File | Purpose |
| --- | --- |
| `numbcrunch-upload.p12` | Private PKCS12 keystore. **This is the file used for signing.** |
| `numbcrunch-upload.password` | Keystore password (also the key password). |
| `numbcrunch-upload-certificate.pem` | Public certificate, for fingerprint checks and key-reset requests only. |

- Key alias: `numbcrunch-upload`
- Certificate: `CN=NumbCrunch Upload`, RSA 3072, valid until 2054
- SHA-256 fingerprint:
  `9D:5C:30:17:D5:EB:63:34:3B:E5:DC:06:2B:99:D8:DB:FE:67:58:14:26:8D:60:A0:C4:6F:D5:78:73:FB:75:81`

This fingerprint must match *Upload key certificate* in Play Console under
*App integrity → App signing*. Keep an encrypted backup of the keystore and
password, for example in a password manager. `.gitignore` excludes keystores,
certificates, and password files; never commit them.

### One-Time Gradle Setup

Add the key paths to your user-level `~/.gradle/gradle.properties`. This file
is outside the repository and applies to every checkout and worktree:

```properties
NUMBCRUNCH_UPLOAD_STORE_FILE=~/data/projects/vault/numbcrunch-release/keys/numbcrunch-upload.p12
NUMBCRUNCH_UPLOAD_STORE_PASSWORD_FILE=~/data/projects/vault/numbcrunch-release/keys/numbcrunch-upload.password
```

Paths may be absolute or start with `~/`.

Supported properties. Each can also be an environment variable, for example a
CI secret:

| Property | Required | Default |
| --- | --- | --- |
| `NUMBCRUNCH_UPLOAD_STORE_FILE` | Yes | none |
| `NUMBCRUNCH_UPLOAD_STORE_PASSWORD_FILE` (preferred) or `NUMBCRUNCH_UPLOAD_STORE_PASSWORD` | Yes | none |
| `NUMBCRUNCH_UPLOAD_KEY_ALIAS` | No | `numbcrunch-upload` |
| `NUMBCRUNCH_UPLOAD_KEY_PASSWORD_FILE` (preferred) or `NUMBCRUNCH_UPLOAD_KEY_PASSWORD` | No | the store password |

**Keep passwords off the command line.** Never pass a password value as a `-P`
flag or an inline environment assignment: it is kept in shell history and is
visible in process listings and CI logs. Use the `*_PASSWORD_FILE` properties,
which take a path to a private file (`chmod 600`). Put the plain `*_PASSWORD`
properties only in `~/.gradle/gradle.properties` or a CI secret store. Paths
and the key alias are safe to pass as `-P` flags.

Confirm with `./gradlew -q :numbcrunch:printReleaseVersion`, which should report
`uploadSigning=configured`. Without these properties, release builds still
succeed but are **unsigned**, Gradle prints a warning, and Play will reject the
bundle.

### Signing From Android Studio Instead

*Build → Generate Signed App Bundle or APK → Android App Bundle*, module
`numbcrunch`:

- Key store path: `~/data/projects/vault/numbcrunch-release/keys/numbcrunch-upload.p12`
  (paste the path if the file picker greys out `.p12` files)
- Key store password and key password: the contents of
  `numbcrunch-upload.password`
- Key alias: `numbcrunch-upload`
- Build variant: `release`

## Release Checklist

Use this for every Play upload, starting from an up-to-date `main`.

1. **Confirm the version.** `version.properties` should already hold the next
   version, set by the first app change after the last release (see
   [Version Lifecycle](#version-lifecycle)). Its code must be exactly one
   higher than the last uploaded code: check the newest released entry in
   [`CHANGELOG.md`](../CHANGELOG.md) and Play Console's *App bundle explorer*.
2. **Adjust if needed.** If the release scope calls for a higher
   `VERSION_NAME` (for example, it now contains a new feature), change it on a
   release branch along with the changelog heading.
3. **Finish the unreleased [`CHANGELOG.md`](../CHANGELOG.md) section.** Keep
   the first bullets player-facing and short enough to paste as Play release
   notes (500 characters per language).
4. **Verify:**

   ```bash
   ./gradlew -q :numbcrunch:printReleaseVersion
   ./gradlew --no-daemon :numbcrunch:lintRelease :gameplay:testDebugUnitTest :domain:test :numbcrunch:bundleRelease
   ```

5. **Confirm the bundle is signed by the upload key:**

   ```bash
   jarsigner -verify numbcrunch/build/outputs/bundle/release/numbcrunch-<name>-<code>-release.aab
   keytool -printcert -jarfile numbcrunch/build/outputs/bundle/release/numbcrunch-<name>-<code>-release.aab
   ```

   `jarsigner` must print `jar verified.`, and the SHA-256 must match the
   fingerprint above.
6. **Merge the release pull request**, then tag the merge commit on `main`:

   ```bash
   git tag -a v<name> -m "NumbCrunch <name> (versionCode <code>)"
   git push origin v<name>
   ```

   If the bundle was built before merging, rebuild it from the tagged commit so
   the upload exactly matches the tag.
7. **Archive the bundle** outside the repository:

   ```bash
   mkdir -p ~/data/projects/vault/numbcrunch-release/releases/<name>-<code>
   cp numbcrunch/build/outputs/bundle/release/numbcrunch-<name>-<code>-release.aab ~/data/projects/vault/numbcrunch-release/releases/<name>-<code>/
   (cd ~/data/projects/vault/numbcrunch-release/releases/<name>-<code> && shasum -a 256 *.aab > SHA256SUMS)
   ```

8. **Upload to internal testing** (next section), then install the build from
   the Play testing link on a real device and smoke-test it.
9. **Record the upload** in `CHANGELOG.md`: replace `unreleased` with the
   date and track.

## Publishing in Play Console

### Internal Testing Update

1. *Test and release → Testing → Internal testing → Create new release*.
2. Upload the signed `.aab`. Play reads the version from the bundle.
3. Leave the pre-filled release name, paste release notes, then *Next*.
4. Resolve any errors, then *Save and publish* or *Start rollout*. Internal
   testing builds are usually available to testers within minutes.

### Promoting to Closed Testing or Production

Promote the tested release instead of uploading it again: open the release on
its track and choose *Promote release*, then pick the target track. Use a
staged rollout for production (for example 20%, then 100%) and watch Android
vitals for crashes and ANRs before widening it.

Before the first production release, Play requires:

- *Policy → App content*: privacy policy URL
  (`https://keval.io/apps/numbcrunch/privacy`, published from `PRIVACY.md`),
  Data safety, content rating, target audience, and ads declaration.
- *Grow → Store presence → Main store listing*: 512×512 icon, 1024×500
  feature graphic, phone screenshots, and descriptions.
- For personal developer accounts created after November 2023: a closed test
  with at least 12 opted-in testers for 14 consecutive days before production
  access can be requested.

Production releases are reviewed by Google, which can take from hours to
several days.

## Release Optimization

The `release` build enables R8 code optimization, shrinking, and obfuscation,
plus resource shrinking, using `proguard-android-optimize.txt`. Debug builds
remain unoptimized. Dependencies supply their own consumer rules; add a narrow
rule in `numbcrunch/proguard-rules.pro` only when runtime behavior requires it.
Avoid blanket keep rules; they prevent optimization.

AGP includes `BUNDLE-METADATA/com.android.tools/r8.json` and
`BUNDLE-METADATA/com.android.tools.build.obfuscation/proguard.map` in the AAB.
Play reads these to report optimization metrics and deobfuscate crash reports.
The local mapping is also saved at
`numbcrunch/build/outputs/mapping/release/mapping.txt`; keep the mapping for the
exact uploaded build when archiving its bundle. Obfuscation need not reach
100%: Android entry points and library keep rules legitimately preserve names.

Before uploading an optimized release, smoke-test the release build on a
device, not just a debug build: first-run tutorial, both game modes,
About and the offline Privacy policy, and persisted music/sound settings.
Check that audio and artwork still load. JVM tests alone cannot catch every
runtime issue caused by optimization.

## Debug Builds

Debug builds use their own application ID, so they install beside the Play
version instead of replacing it:

| Build | Application ID | Launcher name | About shows |
| --- | --- | --- | --- |
| Release (Play) | `io.keval.apps.numbcrunch` | NumbCrunch | `1.1.0` |
| Debug (Android Studio, `installDebug`) | `io.keval.apps.numbcrunch.debug` | NumbCrunch Debug | `1.1.0-debug` |

Debug builds made before 1.1.0 used the release application ID with the debug
key. A leftover copy like that, even one installed only in another user profile
such as a guest profile, blocks Play from installing the app. Find it and
remove it from every profile:

```bash
adb shell pm list packages -u | grep numbcrunch
adb shell pm uninstall io.keval.apps.numbcrunch
```

## Troubleshooting

| Problem | Cause and fix |
| --- | --- |
| *Version code N has already been used* | Increase `VERSION_CODE` past the highest code shown in *App bundle explorer* and rebuild. |
| *Your Android App Bundle is signed with the wrong key* | The bundle was not signed with `numbcrunch-upload.p12`. Check the fingerprint with `keytool -printcert -jarfile`. |
| *You uploaded an APK or App Bundle that was not signed* / `jarsigner` prints `no manifest.` | Upload signing is not configured; see *One-Time Gradle Setup*. |
| Build fails with `version.properties: ...` | Fix the reported value: a positive integer code and a `MAJOR.MINOR.PATCH` name. |
| About screen shows an old version | You installed an older build; reinstall from Play or rebuild. |
| Play shows *Can't install* or *App not installed* for a tester | A copy of the release application ID signed by another key (usually an old debug build) is on the device, possibly in another user profile. See [Debug Builds](#debug-builds). |
| `version-guard:` error in CI or `bundleRelease` | App files changed since the last release tag without moving to the next version. Follow the printed instructions and [Version Lifecycle](#version-lifecycle). |
