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

Because `Active.sav` also holds the user's sensitivity, gyroscope and control layout,
cl-gfx never replaces an existing file. It backs it up once and merges only the seven
render integers cl-gfx owns; every other byte stays as it was.

### What is merged

The seven `IntProperty` values that differ between the shipped profiles:

| Property | Meaning |
|----------|---------|
| `FPSLevel` | frame-rate level |
| `BattleFPS` | in-match frame-rate cap |
| `LobbyFPS` | lobby frame-rate cap |
| `BattleRenderStyle` | in-match colour/style |
| `BattleRenderQuality` | in-match quality |
| `LobbyRenderStyle` | lobby colour/style |
| `LobbyRenderQuality` | lobby quality |

The matching values are read by property name from the chosen asset and written by
property name into the user's file. The file length is unchanged and no sensitivity
or gyroscope bytes are touched.

If the user has no `Active.sav` yet, the whole asset is copied. China (`chi*`) and
Lite (`l*`) profiles only contain `FPSLevel`; the other six values are not part of
those save formats, so those profiles merge `FPSLevel` only. If the existing file is
unreadable, is not a valid GVAS save, or is missing a property that the chosen profile
does contain, Apply reports the reason and leaves the file untouched (no partial
merge). The one-time backup is `Active.sav.clgfx.bak`; **Restore Active.sav** writes
it back.

## 4. Active.sav (GVAS) layout

`Active.sav` is a little-endian Unreal Engine `GVAS` property stream. The numbered
profiles here are 4241 bytes; China profiles are 2815 bytes and Lite profiles 788 bytes.

Offsets in the 4241-byte files:

| Offset | Size | Field |
|--------|------|-------|
| 0 | 4 | magic `GVAS` |
| 4 | 4 | save-game version (2) |
| 8 | 4 | package file version (520) |
| 12 | 2 | engine major (4) |
| 14 | 2 | engine minor (18) |
| 16 | 2 | engine patch (1) |
| 18 | 4 | engine changelist (0) |
| 22 | 4+n | FString engine branch (`++UE4+Release-4.18`) |
| 45 | 4 | custom-version format (3) |
| 49 | 4 | custom-version count (37) |
| 53 | 740 | custom versions: 16-byte GUID + int32, 20 bytes each |
| 793 | 4+n | FString save class (`SettingConfig_C`) |
| 813 | … | first property tag |

**FString**: `int32` length. Positive = UTF-8 with a trailing NUL, so the stored byte
count equals the length. Negative = UTF-16LE, byte count `-length * 2` including a
2-byte NUL. Zero = empty string.

**Property tag**:

```
FString name
FString type
int64   size
  ArrayProperty / SetProperty / StructProperty / ByteProperty:
      FString inner name (element type / struct / enum)
  MapProperty:
      FString key type
      FString value type
  BoolProperty:
      uint8 value, uint8 has-guid, optional 16-byte guid
  everything else:
      uint8 has-guid, optional 16-byte guid, then `size` value bytes
```

The stream ends with a property named `None`; there is no trailer and the length of
the file is not stored anywhere.

### Merge algorithm (`Gvas.java`)

1. Parse both files sequentially and record each property's type, size and value
   offset. Reject a file at the first malformed or truncated tag.
2. For each name in `MERGE_PROPERTIES`, look it up in the chosen asset. If the asset
   does not carry it (China/Lite), skip it. Otherwise the existing file must carry the
   same name as a 4-byte `IntProperty`, or the merge fails.
3. Copy those 4-byte values from the asset into a clone of the user's file. No other
   byte is written.
4. Confirm the clone still has the original length, then write it back.

In `111` the seven value offsets are `FPSLevel` 1867, `BattleFPS` 2816,
`BattleRenderStyle` 2867, `BattleRenderQuality` 2920, `LobbyFPS` 2962,
`LobbyRenderStyle` 3012 and `LobbyRenderQuality` 3064. The first two sensitivity
values are `CamLensSenNoneSniper` at 964 (`FloatProperty`) and
`GyroscopeSenNoneSniper` at 1597. Offsets shift between save layouts, which is why the
merge matches by name rather than by a hard-coded offset.

## 5. Android 13+ file access

The game-facing paths above do not change on Android 13–16; what changes is who is
allowed to write them. A normal app process is blocked there by scoped storage.

- Android 10–12: direct file I/O / SAF.
- Android 13+: a Shizuku UserService runs as shell (or root) and performs the same
  `java.io` writes through Binder.

The profile selection rule (`chi<fps+1>` below API 33, `<preset><fps><style>` at API 33+)
is unchanged. The Shizuku layer is a transport change, not a config-format change; see
[shizuku.md](shizuku.md).

