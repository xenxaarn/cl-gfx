# Architecture

```
cl-gfx/
├── AndroidManifest.xml
├── build.sh                 one-shot aapt2 + aidl + javac + d8 + zipalign + apksigner build
├── libs/                    vendored Shizuku 13.1.5 jars (MIT) + androidx.annotation
├── assets/                  static data shipped to the device
│   ├── faq1..6.ini          sound/water side files
│   ├── chi1..4              FPS/render profiles (API < 33)
│   ├── l1..l3               Lite render profiles (FPS 30/40/60+)
│   └── 111..445             render profiles (API >= 33), 4 × 4 × 5
├── java/com/clgfx/
│   ├── MainActivity.java    programmatic UI, actions, Shizuku onboarding
│   ├── Games.java           package/name table + Lite flag
│   ├── Ini.java             templates, option→CVar model, encoder
│   ├── Storage.java         Shizuku + direct + SAF backends, backups
│   ├── ShizukuBridge.java   binder/permission/UserService bridge (app process)
│   ├── StorageService.java  user service that runs as shell/root
│   └── IStorageService.aidl Binder contract for the user service
└── res/                     theme, strings, adaptive + legacy launcher icons
```

No AndroidX UI, Material, Gradle or Kotlin is used. The only runtime third-party code is
the official Shizuku API/provider (MIT), vendored as plain jars so the build stays a
single `aapt2` + `javac` + `d8` pipeline. `androidx.annotation` is compile-only and is
not packaged. The APK is one DEX plus resources.

## Data flow

```
UI selections
   │
   ▼
Ini.build(game, options)  ──►  LinkedHashMap<key,value>  ──►  encoded text
   │
   ├── Storage.write("Saved/Config/Android/UserCustom.ini", text)
   ├── Storage.copyAsset("faqN.ini", "Saved/Config/Android/UserSettings.ini")
   └── Storage.copyAsset(profile, "Saved/SaveGames/Active.sav")   (optional)
   │
   ▼
status log in the UI
```

`Ini` is pure Java (no Android imports) so the mapping can be unit-tested on a desktop
JVM.

## Storage routing

Android version decides the primary writer:

| Android | API | Primary path |
|---------|-----|--------------|
| 7.0–9 | 24–28 | direct file I/O |
| 10–12 | 29–32 | direct file I/O (target 29 + `requestLegacyExternalStorage`) |
| 13–16+ | 33+ | Shizuku user service running as shell/root |

`Storage.write()` tries, in order:

1. **Shizuku** when the bridge is connected (Android 13+).
2. **Direct file I/O** from the external storage root.
3. **SAF tree** previously granted via *Choose folder*. The picked tree may be:
   - `Android/data` → relative path starts with `<package>/`
   - `Android/data/<package>` → relative path starts with `files/`
   - the game's `ShadowTrackerExtra` folder → relative path starts with `Saved/`

   `Storage` probes these prefixes and uses whichever contains the game directory; if
   none exists it falls back to `<package>/files/...` relative to the picked tree.

The fallbacks are deliberate: a device may already have a working legacy/SAF grant, and
Export always remains available.

### Shizuku path (Android 13+)

Scoped storage blocks an ordinary app process from `Android/data/<package>`, even when
the app targets API 29. Shizuku starts a **UserService** with the shell (or root) UID;
that process still has access to the tree. The bridge:

1. Checks `Build.VERSION.SDK_INT >= 33`.
2. Checks that Shizuku is installed (`<queries>` entry) and `Shizuku.pingBinder()`.
3. Requests Shizuku's runtime permission (`Shizuku.requestPermission`).
4. Binds `StorageService` via `Shizuku.bindUserService` and waits for the Binder.
5. Calls `IStorageService` methods. File bytes cross Binder; the service uses
   `java.io` as shell and verifies the written length.

The provider is declared as:

```xml
<provider
    android:name="rikka.shizuku.ShizukuProvider"
    android:authorities="${applicationId}.shizuku"
    android:multiprocess="false"
    android:enabled="true"
    android:exported="true"
    android:permission="android.permission.GRANT_RUNTIME_PERMISSIONS" />
```

`build.sh` substitutes `${applicationId}` with `com.clgfx` before `aapt2 link` (aapt2
has no AGP-style placeholder expansion).

See [shizuku.md](shizuku.md) for the user setup flow and the escaping rules.

### Direct/SAF path (Android 10–12)

Targeting API 29 with `android:requestLegacyExternalStorage="true"` keeps legacy
shared-storage semantics on Android 10–12. All writes are `flush()` + `close()` and
verified by re-reading the result. Direct writes compare the final file length; SAF
writes compare the stored bytes.

## Safety

- `Active.sav` is copied to `Active.sav.clgfx.bak` before the first overwrite; the
  backup is never overwritten afterwards. If `Active.sav` exists but cannot be read,
  the install is refused rather than destroying the only copy.
- `UserCustom.ini` is backed up to `UserCustom.ini.clgfx.bak` and `UserSettings.ini`
  to `UserSettings.ini.clgfx.bak` before the first write.
- Nothing outside the selected game's `Saved/` tree is written.
- If the game is not installed, Apply still writes the config (useful before first
  launch) but Launch reports that the package is missing.
- Shizuku file paths are passed as Binder arguments, not interpolated into a shell.
  `ShizukuBridge.execShell()` is only for diagnostics; callers must pass values through
  `ShizukuBridge.quote()` (POSIX single-quote escaping) first.

## Permissions

- `READ_EXTERNAL_STORAGE` / `WRITE_EXTERNAL_STORAGE` for the legacy direct path
  (requested only on API 24–32).
- `moe.shizuku.manager.permission.API_V23`, required by the Shizuku provider.
- The Shizuku provider is protected by
  `android.permission.GRANT_RUNTIME_PERMISSIONS`, held by the shell process that
  queries it.
- No `MANAGE_EXTERNAL_STORAGE`, which modern Android does not grant for
  `Android/data` anyway.
- `QUERY_ALL_PACKAGES` is **not** used; the game packages and
  `moe.shizuku.privileged.api` are declared in `<queries>`.

## Threading

Apply/restore run on a single background `ExecutorService`; UI updates are posted back
with `runOnUiThread`. Shizuku permission requests and dialogs run on the UI thread;
binding the user service and all file I/O happen on the worker. The bridge uses a
`CountDownLatch` so the worker waits for `onServiceConnected` without blocking the UI.
No network, no Android services, no receivers.
