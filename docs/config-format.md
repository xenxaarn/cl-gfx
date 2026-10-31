# PUBG config format

What cl-gfx writes and where. These notes cover the game-facing contract: which file,
which key, which value. The mappings are implemented in `java/com/clgfx/Ini.java`.

## 1. Config file

Path (all games, same layout):

```
/Android/data/<package>/files/UE4Game/ShadowTrackerExtra/ShadowTrackerExtra/
    Saved/Config/Android/UserCustom.ini
```

Format:

```
[UserCustom DeviceProfile]
+CVars=<HEX>
+CVars=<HEX>
...
```

where `<HEX>` is each byte of `key=value` XOR `0x79`, encoded as uppercase hex, and
`=` itself becomes `0x44`. PUBG reads this directly.

cl-gfx preserves any `[BackUp DeviceProfile]` section that already exists in the file.

## 2. Option → CVar mapping

### Base template (38 keys)

`Ini.STANDARD_TEMPLATE` holds the 38-key default `UserCustom.ini`.

### Graphics preset

| Position | Label    | Change vs. Smooth |
|----------|----------|-------------------|
| 0 | Smooth   | — |
| 1 | Balanced | `r.UserQualitySetting=1` |
| 2 | HD       | `r.UserQualitySetting=2` |
| 3 | HDR      | `r.UserQualitySetting=2`, `r.MobileHDR=1`, `r.Mobile.TonemapperFilm=1`, `r.BloomQuality=0` |
| 4 | Ultra    | `r.MaxAnisotropy=4`, `r.ParticleLODBias=0`, `r.EmitterSpawnRateScale=1.0`, `r.Streaming.PoolSize=300`, `r.UserQualitySetting=3`, `r.MobileHDR=1`, `r.Mobile.TonemapperFilm=1`, `r.BloomQuality=0` |

### Resolution → `r.MobileContentScaleFactor`

`1, 0.60, 0.75, 0.80, 1, 1.20, 1.30, 1.35, 1.40, 1.44, 1.49, 1.49`

for `Default, 768x432, 960x540, 1024x576, 1152x648, 1280(HD), 1366x768,
1440(HD+), 1600x900, 1920x1080, 2160x1080(FHD+), 2560x1440`.

### FPS

Global/regional builds (excluding China):

| Position | Label | CVars (Low/Mid/High/HDR) | Profile asset |
|----------|-------|--------------------------|---------------|
| 0 | 30FPS | 30/30/30/30 | `chi1` |
| 1 | 40FPS | 40/40/40/40 | `chi2` |
| 2 | 60FPS | 60/60/60/60 | `chi3` |
| 3 | 90FPS | 60/60/60/60 | `chi4` |

China (`com.rekoo.pubgm`) has an extra default position and 120FPS; those values are
profile-driven and cl-gfx only changes the FPS CVars it can confirm.

Lite (`com.tencent.iglite`) uses its own 57-key template and `Low/Mid/High` = 30/40/60.

### Remaining options

| UI | CVars |
|----|-------|
| Anti-aliasing | Disable: `MSAACount=0.0`, `MobileMSAA=0.0`, `UserMSAAValue=2`; 2X/4X: `DefaultFeature.AntiAliasing=3.0`, `UserMSAASetting=1`, `MobileMSAA=2.0`, `UserMSAAValue=2` |
| Style | `UserHDRSetting` / `ACESStyle` = 1 / 2 / 3 / 4 / 6 |
| Rendering quality | `MaterialQualityLevel` = 0 / 2 / 1 for Low / Medium / High |
| Shadows | `UserShadowSwitch`, `ShadowQuality`, `Shadow.MaxCSMResolution`, `Shadow.CSM.MaxMobileCascades` per level |
| Shadow distance | `Shadow.DistanceScale` = 0.5 / 0.7 / 0.85 / 1.0 / 1 |
| Moving shadows | `Mobile.DynamicObjectShadow` = 1 |
| Particle distance | `ParticleLODBias` = 1 / 0 for Medium / High |
| Spawn distance | `EmitterSpawnRateScale` = 0.75 / 1.0 |
| Mesh distance | `StaticMeshLODDistanceScale` = 1.1 / 0.8 |
| Foliage distance | `foliage.LODDistanceScale` = 0.8 / 1.0 |
| Color quality | `Mobile.SceneColorFormat` = 1.0 / 2.0 |

### Sound / water (`faq1..6.ini` → `UserSettings.ini`)

PUBG reads sound and water reflection settings from
`Saved/Config/Android/UserSettings.ini`:

```
[SoundQuality]        SoundQualityType = 0/1/2
[ArtQuality]          WaterReflectionSetting = 0/1
```

cl-gfx ships six ready-made files `faq1..6.ini` and copies the chosen one over
`UserSettings.ini`, after a one-time `UserSettings.ini.clgfx.bak` backup.
`N = 1 + sound + 3*water`, i.e. `faq1..faq6`.

## 3. Render profiles and `Active.sav`

The destination for the numbered profiles is:

```
/Android/data/<package>/files/UE4Game/ShadowTrackerExtra/ShadowTrackerExtra/
    Saved/SaveGames/Active.sav
```

These are UE4 `GVAS` save-game files. cl-gfx picks:

- API < 33: `chi<fps+1>`
- API ≥ 33: `<min(preset+1,4)><fps+1><style+1>` (e.g. `445`), from assets `111..445`

Because `Active.sav` also holds the user's control layout, cl-gfx backs it up before
the first overwrite and exposes a restore action.

## 4. Android 13+ file access

The game-facing paths above do not change on Android 13–16; what changes is who is
allowed to write them. A normal app process is blocked there by scoped storage.

- Android 10–12: direct file I/O / SAF.
- Android 13+: a Shizuku UserService runs as shell (or root) and performs the same
  `java.io` writes through Binder.

The profile selection rule (`chi<fps+1>` below API 33, `<preset><fps><style>` at API 33+)
is unchanged. The Shizuku layer is a transport change, not a config-format change; see
[shizuku.md](shizuku.md).

