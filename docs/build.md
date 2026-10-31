# Build

## Prerequisites

| Tool | Version |
|------|---------|
| JDK  | 17 (`javac`, `keytool`) |
| Android SDK platform | `android-34` |
| Android build-tools | `34.0.0` (`aapt2`, `aidl`, `d8`, `zipalign`, `apksigner`) |

No Gradle is involved. Install the SDK pieces with Google's
[command-line tools](https://developer.android.com/studio#command-line-tools-only):

```sh
sdkmanager "platforms;android-34" "build-tools;34.0.0"
export ANDROID_SDK_ROOT="$HOME/Android/Sdk"   # wherever your SDK lives
```

`build.sh` reads `ANDROID_SDK_ROOT` and signs with the debug keystore at
`~/.android/debug.keystore` (generated on first run if missing).

The Shizuku jars in `libs/` are committed; see [libs/README.md](../libs/README.md) for
their coordinates and license.

```sh
./build.sh
```

Output: `build/cl-gfx.apk` (debug-signed). The script also leaves `build/unsigned.apk`
and `build/aligned.apk` as intermediates.

## What build.sh does

1. `aapt2 compile` every file under `res/` into `build/res.zip`.
2. Substitute `${applicationId}` → `com.clgfx` in a copy of `AndroidManifest.xml`
   (aapt2 does not implement AGP manifest placeholders).
3. `aapt2 link` against `android-34/android.jar` with the generated manifest
   (`minSdk 24`, `targetSdk 29`), producing `build/base.apk` + `R.java`.
4. `aidl` generates `IStorageService.java` from
   `java/com/clgfx/IStorageService.aidl` into `build/gen`.
5. `javac --release 11` compiles `R.java`, the generated AIDL, and `java/**/*.java`
   against `android.jar` plus `libs/*.jar` into `build/classes`.
6. `d8 --min-api 24` dexes the app classes **and** the Shizuku jars into
   `build/dex/classes.dex`.
7. The dex and `assets/` are added to the APK with `zip`.
8. `zipalign -p 4` then `apksigner sign` with the debug key.

## Verify

```sh
BT="$ANDROID_SDK_ROOT/build-tools/34.0.0"
"$BT/apksigner" verify --print-certs build/cl-gfx.apk
"$BT/aapt2" dump badging build/cl-gfx.apk
"$BT/aapt2" dump permissions build/cl-gfx.apk
"$BT/aapt2" dump xmltree build/cl-gfx.apk --file AndroidManifest.xml | grep -A7 ShizukuProvider
```

Expected package: `com.clgfx`, label `cl-gfx`, versionCode `2`,
versionName `1.1.0`, minSdk `24`,
targetSdk `29`, and the Shizuku provider authority `com.clgfx.shizuku`.

## Releasing

Debug-signed builds are fine for testing. For a release, sign `build/aligned.apk` with
your own key (`apksigner sign --ks <your.jks> ...`) and attach the result to a GitHub
Release. Never commit the keystore; `.gitignore` already excludes common key formats.

## Verification performed for 1.1.0

- `aapt2 dump badging` → package `com.clgfx`, label `cl-gfx`, minSdk 24, targetSdk 29.
- `aapt2 dump permissions` → READ/WRITE_EXTERNAL_STORAGE and
  `moe.shizuku.manager.permission.API_V23`; no network permission.
- `aapt2 dump xmltree` → `rikka.shizuku.ShizukuProvider` at
  `com.clgfx.shizuku`, protected by `GRANT_RUNTIME_PERMISSIONS`.
- `aapt2 dump resources` → `drawable/ic_launcher`, `drawable/ic_launcher_round`,
  `drawable-anydpi-v26/ic_launcher` (adaptive) and the two icon layers are packaged.
- `dexdump` → `com.clgfx.ShizukuBridge`, `com.clgfx.StorageService`,
  `com.clgfx.IStorageService$Stub`, and `rikka.shizuku.*` are present in the DEX.
- The 93 shipped assets (`faq1..6`, `chi1..4`, `l1..l3`, `111..445`) are present at
  `assets/` in the APK.
- `Ini.build()` was exercised for all 810 game/preset/FPS/style combinations and every
  referenced profile/faq asset exists. The check also asserts that Lite output keeps
  `r.DeviceFPSLevel=6`, leaves `r.PUBGDeviceFPSHDR` at `40`, and selects `l1..l3`.
- Encoder round-trip: every emitted `+CVars=` line decodes (XOR 0x79) to a valid
  `key=value` pair and decodes back to the exact input map.

A real Shizuku bind/write requires a device with the Shizuku app running; the Binder
path cannot be exercised in a plain JVM. See [shizuku.md](shizuku.md) for the on-device
checklist.
