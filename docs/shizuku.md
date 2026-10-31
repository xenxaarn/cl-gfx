# Shizuku on Android 13+

Android 13 (API 33) tightened scoped storage further: `/sdcard/Android/data/<package>`
is no longer writable by the app process, even with
`android:requestLegacyExternalStorage="true"`. cl-gfx therefore uses
[Shizuku](https://shizuku.rikka.app/) on API 33+ and keeps the direct/SAF path for
Android 10–12.

## Why a UserService?

Shizuku exposes two ways to run privileged code:

- `ShizukuBinderWrapper` for forwarding Binder calls to a system service.
- **UserService**: a Java process started by Shizuku with the shell (or root) UID.

Only the UserService gives us a normal `java.io.File` view of
`/sdcard/Android/data`. `StorageService` is that process. It implements
`IStorageService`:

| Method | Purpose |
|--------|---------|
| `writeFile(path, data)` | create parent dirs, write, `sync()`, verify length |
| `readFile(path)` | read bytes, or `null` when absent |
| `exists(path)` | existence check |
| `mkdirs(path)` | directory creation |
| `length(path)` | file length, `-1` if absent |
| `exec(argv)` | run argv without a shell, return combined output |
| `destroy()` | reserved Shizuku transaction; exits the process |

The app-side `ShizukuBridge` owns permission state and the `ServiceConnection`. File
contents travel as Binder byte arrays, so no path or content is ever parsed by a shell.

## User flow

### 1. Install Shizuku

The app's onboarding dialog links to the official download page. Shizuku is also on
Google Play; do not use random “Shizuku” clones.

### 2. Enable Wireless debugging

Developer options → **Wireless debugging** → pair/start. On Android 11+ this lets
Shizuku start without a computer.

### 3. Start Shizuku

Open Shizuku → **Start via Wireless debugging**. Keep Shizuku running; it must be
restarted after a reboot (unless the device is rooted).

### 4. Authorize cl-gfx

Reopen cl-gfx. On the first Apply/Restore, Shizuku shows its permission prompt. If it
was denied, open Shizuku → **Authorized apps** → allow cl-gfx, then tap Apply again.
The **Shizuku setup / permission** button rechecks the binder and requests permission.

## Failure handling

| State | What the app does |
|-------|-------------------|
| API < 33 | Never touches Shizuku; uses direct/SAF. |
| Shizuku not installed | AlertDialog with install, wireless-debugging and start steps. |
| Installed, binder dead | Same dialog with “Shizuku is installed, but its service is not running.” |
| Installed, pre-v11 | Dialog asks the user to update the official Shizuku app. |
| Binder alive, no permission | Requests permission; on denial, points at Shizuku → Authorized apps. |
| Permission granted, bind fails | Apply/restore reports `Shizuku: user service unavailable` and leaves files untouched. |
| Shizuku write fails | Falls back to direct/SAF; Apply reports the backend that actually worked. |

## Path handling and escaping

The write/read/exists path takes the absolute path as a Binder `String`, so spaces,
quotes and `$` in a path are safe. For the diagnostic `exec` API, `ShizukuBridge`
provides:

```java
static String quote(String value) {
    return "'" + value.replace("'", "'\\''") + "'";
}
```

`execShell("ls -l " + quote(path))` is safe for `My Games`, `a'b`, `$HOME`, `;` and
globs. The production write path does not use a shell at all.

## Library provenance

Vendored in `libs/` from Maven Central `dev.rikka.shizuku` **13.1.5**:

| Jar | AAR |
|-----|-----|
| `shizuku-api-13.1.5.jar` | `api` |
| `shizuku-provider-13.1.5.jar` | `provider` |
| `shizuku-shared-13.1.5.jar` | `shared` |
| `shizuku-aidl-13.1.5.jar` | `aidl` |
| `androidx-annotation-1.3.0.jar` | `androidx.annotation:annotation:1.3.0` |

All are MIT/APL-2.0 licensed; see `libs/README.md`. They are plain classes jars, not
AARs, so `d8` can dex them directly without Gradle.
