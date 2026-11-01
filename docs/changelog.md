# Changelog

## 1.1.0 — 2026-10-31

Android 13+ file-execution support through Shizuku.

### Added
- `ShizukuBridge`: binder availability check, runtime permission request, UserService
  bind/unbind, and file/exec operations.
- `StorageService` + `IStorageService.aidl`: a shell/root user service that writes,
  reads, stats and executes inside `Android/data/<package>`.
- Android 13+ routing in `Storage`: Shizuku first, then direct/SAF fallback.
- First-run setup dialog (install Shizuku → enable Wireless debugging → start Shizuku
  → reopen cl-gfx) and a permanent **Shizuku setup / permission** button.
- New app logo: minimal dark “cl” monogram with a single blue accent, adaptive
  (API 26+), round and legacy vector variants, an in-app header mark, and
  `docs/logo.svg` as the source.
- `rikka.shizuku.ShizukuProvider` in the manifest, the
  `moe.shizuku.manager.permission.API_V23` permission, and a Shizuku `<queries>` entry.
- Vendored official Shizuku 13.1.5 API/provider/shared/aidl jars in `libs/`.
- `docs/shizuku.md` with the on-device flow, failure matrix and quoting rules.
- POSIX single-quote escaping (`ShizukuBridge.quote`) for optional shell commands.

### Changed
- `minSdk` raised from 23 to 24 (required by the Shizuku API), `build.sh` now runs
  `aidl`, injects the manifest placeholder and dexes `libs/*.jar`.
- Storage permissions are requested only on API 24–32; Android 13+ uses Shizuku.
- Apply/restore now report the backend that actually wrote the files
  (`Shizuku user service`, `direct storage`, or `SAF tree`).
- Status/UI text explains the Android 13+ requirement instead of only suggesting SAF.

### Fixed
- Applying a render profile no longer replaces the user's `Active.sav`. Only the
  seven render `IntProperty` values (`FPSLevel`, `BattleFPS`, `LobbyFPS`,
  `BattleRenderStyle`, `BattleRenderQuality`, `LobbyRenderStyle`,
  `LobbyRenderQuality`) are merged by property name. Sensitivity, gyroscope and all
  other bytes are preserved, and the file length stays the same. The full asset is
  copied only when no `Active.sav` exists. China/Lite profiles merge `FPSLevel` only.
- A malformed `Active.sav`, or one missing a property present in the chosen profile,
  now fails with a specific reason instead of being overwritten.
- Exporting `UserCustom.ini` uses `application/octet-stream`, so the document picker
  no longer appends `.txt` to the filename.
- `docs/config-format.md` documents the GVAS layout and the merge algorithm.

### Preserved
- Android 10–12 direct and SAF behaviour is unchanged.
- One-time `Active.sav.clgfx.bak`, `UserCustom.ini.clgfx.bak` and
  `UserSettings.ini.clgfx.bak` backups, including the refusal to overwrite an
  unreadable `Active.sav`.
- No ads, analytics, billing, network permission or Play Store distribution.

## 1.0.0 — 2026-10-30

First release.

### Added
- Complete `UserCustom.ini` generator using PUBG's hex-XOR (`0x79`) `+CVars=` format.
- Standard 38-key template and Lite 57-key template.
- Options: game, resolution, graphics preset, FPS, anti-aliasing, colour style,
  rendering quality, shadows, shadow distance, moving shadows, particle/spawn/mesh/
  foliage distance, colour quality, sound quality, water reflections.
- `faq1..6.ini` assets selected from the sound/water pair and installed as
  `UserSettings.ini`, with a one-time `UserSettings.ini.clgfx.bak` backup.
- Optional render profile install to `Saved/SaveGames/Active.sav` with an automatic
  one-time `Active.sav.clgfx.bak` backup and a restore button.
- Storage backends: direct file I/O and a persisted SAF tree grant with automatic
  prefix detection (`Android/data`, `<package>`, `ShadowTrackerExtra`, `Saved`).
- One-time `UserCustom.ini.clgfx.bak` backup and preservation of an existing
  `[BackUp DeviceProfile]` section.
- Export of the generated `UserCustom.ini` through the system file picker.
- Launch button for the selected game (declared `<queries>`, no `QUERY_ALL_PACKAGES`).
- Build script using aapt2, javac, d8, zipalign and apksigner only.

### Audit fixes
- Direct writes verify the final byte length; SAF writes try `wt` then `w` and verify
  the stored bytes. A failed provider write is reported instead of silently accepted.
- Clearing or replacing the SAF grant resets the cached prefix/package state.
- SAF prefix detection also accepts a tree rooted at the game's
  `ShadowTrackerExtra` folder.
- `installProfile()` refuses to overwrite an unreadable existing `Active.sav`.
- Apply catches all failures, always re-enables the UI, and reports the error.
- Export uses the same `wt` → `w` fallback as SAF writes.
- Lite no longer rewrites `r.DeviceFPSLevel`; the template value `6` is preserved.

### Notes
- Anti-aliasing 2X and 4X produce the same PUBG MSAA values.
- China FPS `Default` leaves the template untouched; the remaining positions set
  explicit FPS CVars and reuse the closest shipped profile.
- Lite uses `l1/l2/l3` profiles and only rewrites `PUBGDeviceFPSLow/Mid/High`; its
  template keeps `DeviceFPSLevel=6` and `PUBGDeviceFPSHDR=40`.
