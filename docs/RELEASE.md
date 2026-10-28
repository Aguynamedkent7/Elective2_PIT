# Releasing NumLab

## Upload key (once)
The upload keystore lives **outside the repo**. Create it with:

```sh
mkdir -p ~/.android/keystores
keytool -genkeypair -v -keystore ~/.android/keystores/numlab-upload.jks \
  -alias numlab-upload -keyalg RSA -keysize 4096 -validity 10000 -dname "CN=NumLab, O=NumLab"
```

Then create `keystore.properties` in the repo root (git-ignored):

```properties
storeFile=/home/<you>/.android/keystores/numlab-upload.jks
storePassword=...
keyAlias=numlab-upload
keyPassword=...
```

Without that file the release build is produced **unsigned**. Back up the `.jks` and the
passwords somewhere safe; enrol in **Play App Signing** on first upload so Google holds the app
signing key and this is only the upload key.

## Version
- `numlab.versionCode` (single integer) and `numlab.versionName` in `gradle.properties`.
  Bump `versionCode` for every upload.

## Build
```sh
./gradlew test lint :domain:jacocoTestCoverageVerification   # all green before release
./gradlew :app:bundleRelease
```
Outputs:
- `app/build/outputs/bundle/release/app-release.aab` (upload this)
- `app/build/outputs/mapping/release/mapping.txt` (upload to Play Console for deobfuscated crashes)

## Smoke test the release build
```sh
./gradlew :app:assembleRelease
adb install -r app/build/outputs/apk/release/app-release.apk
adb shell am start -W -n app.numlab/.MainActivity   # TotalTime = cold start
```
Walk acceptance scripts A–D from PLAN.md §12. On-device UI tests (debug build):
`./gradlew :app:connectedDebugAndroidTest`.

To install the bundle itself: `bundletool build-apks --bundle=app-release.aab --output=numlab.apks --ks=... --ks-key-alias=numlab-upload`
then `bundletool install-apks --apks=numlab.apks`.

## Store assets
- 512 px icon: `docs/release/play-icon-512.png` (full-bleed, rendered from `docs/assets/logo.svg`).
- Data safety: **no data collected or shared**. The manifest has no `INTERNET` permission.
