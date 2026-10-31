package com.clgfx;

import java.io.UnsupportedEncodingException;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Pure-Java model of PUBG's UserCustom.ini.
 */
final class Ini {

    private static final String STANDARD_TEMPLATE =
            "r.MaxAnisotropy=2\n"
            + "r.MobileSimpleShader=1\n"
            + "r.MobileNumDynamicPointLights=0\n"
            + "r.DepthOfFieldQuality=0\n"
            + "r.RefractionQuality=0\n"
            + "foliage.MinLOD=0\n"
            + "r.ParticleLODBias=2\n"
            + "r.DetailMode=0\n"
            + "r.EmitterSpawnRateScale=0.5\n"
            + "foliage.LODDistanceScale=0.6\n"
            + "r.MSAACount=4.0\n"
            + "r.DefaultFeature.AntiAliasing=0.0\n"
            + "r.Mobile.SceneColorFormat=0.0\n"
            + "r.Streaming.PoolSize=150\n"
            + "diy.SetDecalBakingRTSizeInLobby=1024\n"
            + "r.UserQualitySetting=0\n"
            + "r.PUBGDeviceFPSLow=30\n"
            + "r.PUBGDeviceFPSMid=30\n"
            + "r.PUBGDeviceFPSHigh=30\n"
            + "r.PUBGDeviceFPSHDR=30\n"
            + "r.MobileContentScaleFactor=1.0\n"
            + "r.MobileHDR=0\n"
            + "r.PUBGLDR=1\n"
            + "r.UserHDRSetting=1\n"
            + "r.ACESStyle=1\n"
            + "r.LightShaftQuality=0\n"
            + "r.UserMSAASetting=0\n"
            + "r.MobileMSAA=1.0\n"
            + "r.MaterialQualityLevel=0\n"
            + "r.UserShadowSwitch=0\n"
            + "r.ShadowQuality=0\n"
            + "r.Shadow.MaxCSMResolution=4\n"
            + "r.Shadow.CSM.MaxMobileCascades=0\n"
            + "r.Shadow.DistanceScale=0.3\n"
            + "r.Mobile.DynamicObjectShadow=0\n"
            + "r.UserMSAAValue=0\n"
            + "r.StaticMeshLODDistanceScale=1.3\n"
            + "r.UserVulkanSetting=1";

    private static final String LITE_TEMPLATE =
            "r.PUBGMSAASupport=1\n"
            + "r.PUBGDeviceFPSLow=60\n"
            + "r.PUBGDeviceFPSMid=40\n"
            + "r.PUBGDeviceFPSHigh=40\n"
            + "r.PUBGDeviceFPSHDR=40\n"
            + "r.DeviceFPSLevel=6\n"
            + "r.Mobile.EnablePostProcessing=1\n"
            + "r.UserMSAASetting=0\n"
            + "r.PUBGQualityLevel=2\n"
            + "r.PUBGMaxSupportQualityLevel=2\n"
            + "r.UserQualitySetting=0\n"
            + "r.MobileContentScaleFactor=1\n"
            + "r.MaterialQualityLevel=0\n"
            + "r.ShadowQuality=0\n"
            + "r.Shadow.MaxCSMResolution=1024\n"
            + "r.Shadow.CSM.MaxMobileCascades=2\n"
            + "r.Shadow.DistanceScale=0.7\n"
            + "r.DepthOfFieldQuality=0\n"
            + "r.RefractionQuality=0\n"
            + "r.StaticMeshLODDistanceScale=0.8\n"
            + "foliage.LODDistanceScale=1.0\n"
            + "foliage.MinLOD=0\n"
            + "r.DetailMode=2\n"
            + "r.MaxAnisotropy=4\n"
            + "r.Streaming.PoolSize=100\n"
            + "r.EmitterSpawnRateScale=1.0\n"
            + "r.ParticleLODBias=0\n"
            + "landscape.LandscapeMixFactor=1\n"
            + "r.Landscape.DistanceFactorScale=1\n"
            + "r.GlobalParticleSystemCullingScreenSize=0\n"
            + "r.MobileNumDynamicPointLights=2\n"
            + "r.Mobile.EnableMovableSpotlights=1\n"
            + "r.PUBGVersion=3\n"
            + "r.Mobile.SceneColorFormat=0.0\n"
            + "r.DefaultFeature.AntiAliasing=0.0\n"
            + "r.MobileMSAA=1.0\n"
            + "r.MSAACount=4.0\n"
            + "r.ACESStyle=1\n"
            + "grass.LandscapeGrassWeightMode=2\n"
            + "r.PUBGLimit=1\n"
            + "r.PUBGCRLRuntmieMinMem=2.5\n"
            + "r.PUBGRenderSwitch=1\n"
            + "r.Mobile.EarlyZPass=1\n"
            + "r.AndroidOpenglDeleteUnusedShader=1\n"
            + "r.Streaming.MaxTempMemoryAllowed=15\n"
            + "grass.densityScale=0.6\n"
            + "r.ParticleDynamicInstance=0\n"
            + "r.Particle.DynamicInstanceES3=0\n"
            + "r.Particle.DynamicInstanceES2=0\n"
            + "landscape.SafeDistance=150\n"
            + "landscape.SafeLODLevel=3\n"
            + "Slate.EnableLayoutCaching=0\n"
            + "r.Mobile.SubSectionFrustumCull=1\n"
            + "p.DelayedFocusPlayerPawnTime=2.01\n"
            + "r.UserShadowSwitch=1\n"
            + "r.MobileHDR=0.0\n"
            + "r.UserHDRSetting=1";

    /** UI selections. Indexes are spinner positions. */
    static final class Options {
        String pkg = Games.PACKAGES[0];
        int resolution = 0;
        int preset = 0;
        int fps = 0;
        int aa = 0;
        int style = 0;
        int rendering = 0;
        int shadows = 0;
        int shadowDistance = 0;
        int movingShadows = 0;
        int particle = 0;
        int spawn = 0;
        int mesh = 0;
        int foliage = 0;
        int color = 0;
        int sound = 0;
        int water = 0;

        boolean applyProfile = true;
        boolean lite;
        boolean china;
    }

    static final String[] PRESETS = {"Smooth", "Balanced", "HD", "HDR", "Ultra"};
    static final String[] PRESETS_LITE = {"Smooth", "Balanced", "HD"};
    static final String[] RESOLUTIONS = {
            "Default", "768x432", "960x540", "1024x576", "1152x648", "1280 (HD)",
            "1366x768", "1440 (HD+)", "1600x900", "1920x1080", "2160x1080 (FHD+)",
            "2560x1440"
    };
    static final String[] FPS = {"30 FPS", "40 FPS", "60 FPS", "90 FPS (if supported)"};
    static final String[] FPS_CHINA = {"Default", "30 FPS", "40 FPS", "60 FPS", "90 FPS", "120 FPS"};
    static final String[] AA = {"Disable", "2X", "4X"};
    static final String[] STYLES = {"Classic", "Colorful", "Realistic", "Soft", "Movie"};
    static final String[] RENDERING = {"Low", "Medium", "High"};
    static final String[] SHADOWS = {"Disable", "Low", "Medium", "High", "Ultra", "Extreme"};
    static final String[] SHADOW_DISTANCE = {"Low", "Medium", "High", "Ultra", "Extreme"};
    static final String[] MOVING_SHADOWS = {"Disable", "Enable"};
    static final String[] PARTICLE = {"Default", "Low", "Medium", "High"};
    static final String[] SPAWN = {"Default", "Low", "Medium", "High"};
    static final String[] MESH = {"Default", "Low", "Medium", "High"};
    static final String[] FOLIAGE = {"Default", "Low", "Medium", "High"};
    static final String[] COLOR = {"Default", "32-bit", "64-bit"};
    static final String[] SOUND = {"Default", "Low", "High", "Ultra"};
    static final String[] WATER = {"Disable", "Enable"};

    private static final String[] RESOLUTION_SCALE = {
            "1.0", "0.60", "0.75", "0.80", "1.0", "1.20", "1.30", "1.35", "1.40", "1.44",
            "1.49", "1.49"
    };

    static LinkedHashMap<String, String> build(Options o) {
        LinkedHashMap<String, String> m = parse(o.lite ? LITE_TEMPLATE : STANDARD_TEMPLATE);

        if (!o.lite) {
            applyPreset(m, o);
        }
        applyResolution(m, o);
        applyFps(m, o);
        applyAa(m, o);
        applyStyle(m, o);
        applyRendering(m, o);
        applyShadows(m, o);
        applyShadowDistance(m, o);
        applyMovingShadows(m, o);
        applyParticle(m, o);
        applySpawn(m, o);
        applyMesh(m, o);
        applyFoliage(m, o);
        applyColor(m, o);
        if (o.lite) {
            // The Lite presets carry their own MaterialQualityLevel/ShadowQuality, so the
            // preset is applied last and wins over the generic defaults.
            applyPreset(m, o);
        }
        return m;
    }

    private static void applyPreset(Map<String, String> m, Options o) {
        if (o.lite) {
            switch (o.preset) {
                case 1:
                    m.put("r.UserQualitySetting", "1");
                    m.put("r.MaterialQualityLevel", "2");
                    break;
                case 2:
                    m.put("r.UserQualitySetting", "2");
                    m.put("r.MaterialQualityLevel", "1");
                    m.put("r.ShadowQuality", "3");
                    break;
                default:
                    break;
            }
            return;
        }
        switch (o.preset) {
            case 1:
                m.put("r.UserQualitySetting", "1");
                break;
            case 2:
                m.put("r.UserQualitySetting", "2");
                break;
            case 3:
                m.put("r.UserQualitySetting", "2");
                m.put("r.MobileHDR", "1");
                m.put("r.Mobile.TonemapperFilm", "1");
                m.put("r.BloomQuality", "0");
                break;
            case 4:
                m.put("r.MaxAnisotropy", "4");
                m.put("r.ParticleLODBias", "0");
                m.put("r.EmitterSpawnRateScale", "1.0");
                m.put("r.Streaming.PoolSize", "300");
                m.put("r.UserQualitySetting", "3");
                m.put("r.MobileHDR", "1");
                m.put("r.Mobile.TonemapperFilm", "1");
                m.put("r.BloomQuality", "0");
                break;
            default:
                break;
        }
    }

    private static void applyResolution(Map<String, String> m, Options o) {
        int i = clamp(o.resolution, RESOLUTION_SCALE.length - 1);
        m.put("r.MobileContentScaleFactor", RESOLUTION_SCALE[i]);
    }

    private static void applyFps(Map<String, String> m, Options o) {
        String fps;
        if (o.china) {
            if (o.fps <= 0) {
                return; // default: leave the template alone
            }
            int[] table = {30, 30, 40, 60, 90, 120};
            fps = String.valueOf(table[clamp(o.fps, table.length - 1)]);
        } else {
            int[] table = {30, 40, 60, 60};
            fps = String.valueOf(table[clamp(o.fps, table.length - 1)]);
        }
        m.put("r.PUBGDeviceFPSLow", fps);
        m.put("r.PUBGDeviceFPSMid", fps);
        m.put("r.PUBGDeviceFPSHigh", fps);
        if (o.lite) {
            // The reference's Lite branch only rewrites Low/Mid/High; HDR keeps the
            // template value (40). r.DeviceFPSLevel is fixed at 6 in the template.
            return;
        }
        m.put("r.PUBGDeviceFPSHDR", fps);
    }

    private static void applyAa(Map<String, String> m, Options o) {
        if (o.aa <= 0) {
            return;
        }
        // The reference maps both 2X and 4X to the same PUBG MSAA profile.
        m.put("r.DefaultFeature.AntiAliasing", "3.0");
        m.put("r.UserMSAASetting", "1");
        m.put("r.MobileMSAA", "2.0");
        m.put("r.UserMSAAValue", "2");
    }

    private static void applyStyle(Map<String, String> m, Options o) {
        int[] hdr = {1, 2, 3, 4, 6};
        int i = clamp(o.style, hdr.length - 1);
        m.put("r.UserHDRSetting", String.valueOf(hdr[i]));
        m.put("r.ACESStyle", String.valueOf(hdr[i]));
    }

    private static void applyRendering(Map<String, String> m, Options o) {
        int[] level = {0, 2, 1};
        m.put("r.MaterialQualityLevel", String.valueOf(level[clamp(o.rendering, 2)]));
    }

    private static void applyShadows(Map<String, String> m, Options o) {
        int i = clamp(o.shadows, 5);
        if (i == 0) {
            m.put("r.UserShadowSwitch", "0");
            m.put("r.ShadowQuality", "0");
            m.put("r.Shadow.MaxCSMResolution", "4");
            m.put("r.Shadow.CSM.MaxMobileCascades", "0");
            return;
        }
        m.put("r.UserShadowSwitch", "1");
        m.put("r.ShadowQuality", "1");
        int[][] table = {
                {128, 1}, {512, 1}, {512, 2}, {1024, 2}, {2048, 2}
        };
        int[] row = table[clamp(i - 1, table.length - 1)];
        m.put("r.Shadow.MaxCSMResolution", String.valueOf(row[0]));
        m.put("r.Shadow.CSM.MaxMobileCascades", String.valueOf(row[1]));
    }

    private static void applyShadowDistance(Map<String, String> m, Options o) {
        String[] table = {"0.3", "0.5", "0.7", "0.85", "1.0"};
        m.put("r.Shadow.DistanceScale", table[clamp(o.shadowDistance, 4)]);
    }

    private static void applyMovingShadows(Map<String, String> m, Options o) {
        m.put("r.Mobile.DynamicObjectShadow", o.movingShadows > 0 ? "1" : "0");
    }

    private static void applyParticle(Map<String, String> m, Options o) {
        // Default and Low keep the template value; Medium/High reduce the LOD bias.
        if (o.particle >= 2) {
            m.put("r.ParticleLODBias", o.particle >= 3 ? "0" : "1");
        }
    }

    private static void applySpawn(Map<String, String> m, Options o) {
        if (o.spawn >= 2) {
            m.put("r.EmitterSpawnRateScale", o.spawn >= 3 ? "1.0" : "0.75");
        }
    }

    private static void applyMesh(Map<String, String> m, Options o) {
        if (o.mesh >= 2) {
            m.put("r.StaticMeshLODDistanceScale", o.mesh >= 3 ? "0.8" : "1.1");
        }
    }

    private static void applyFoliage(Map<String, String> m, Options o) {
        if (o.foliage >= 2) {
            m.put("foliage.LODDistanceScale", o.foliage >= 3 ? "1.0" : "0.8");
        }
    }

    private static void applyColor(Map<String, String> m, Options o) {
        if (o.color <= 0) {
            return;
        }
        m.put("r.Mobile.SceneColorFormat", o.color == 1 ? "1.0" : "2.0");
    }

    /** Asset name of the Active.sav render profile, or null to skip. */
    static String profileAsset(Options o, int sdk) {
        if (o.lite) {
            // Lite has its own small profiles: l1 = 30, l2 = 40, l3 = 60/90.
            return "l" + Math.min(clamp(o.fps, 3) + 1, 3);
        }
        int fpsDigit = clamp(o.fps, 4) + 1;
        if (sdk >= 33) {
            int presetDigit = Math.min(o.preset + 1, 4);
            int styleDigit = clamp(o.style, 4) + 1;
            return "" + presetDigit + Math.min(fpsDigit, 4) + styleDigit;
        }
        return "chi" + Math.min(fpsDigit, 4);
    }

    /** faq1..6 from the sound/water pair. */
    static String faqAsset(Options o) {
        int sound = o.sound <= 0 ? 0 : Math.min(o.sound - 1, 2);
        int water = o.water > 0 ? 1 : 0;
        return "faq" + (1 + sound + 3 * water) + ".ini";
    }

    static String encode(Map<String, String> m) {
        StringBuilder sb = new StringBuilder("[UserCustom DeviceProfile]\n");
        for (Map.Entry<String, String> e : m.entrySet()) {
            sb.append("+CVars=").append(hexXor(e.getKey() + "=" + e.getValue())).append('\n');
        }
        return sb.toString();
    }

    /** XOR 0x79, uppercase hex. All template text is ASCII. */
    static String hexXor(String s) {
        byte[] b;
        try {
            b = s.getBytes("UTF-8");
        } catch (UnsupportedEncodingException e) {
            b = s.getBytes();
        }
        StringBuilder out = new StringBuilder(b.length * 2);
        for (byte v : b) {
            int x = (v ^ 0x79) & 0xFF;
            out.append(Character.toUpperCase(Character.forDigit(x >> 4, 16)));
            out.append(Character.toUpperCase(Character.forDigit(x & 0xF, 16)));
        }
        return out.toString();
    }

    /**
     * Preserve an existing [BackUp DeviceProfile] section. Returns "" when absent.
     */
    static String backupSection(String existing) {
        if (existing == null) {
            return "";
        }
        String marker = "[BackUp DeviceProfile]";
        int start = existing.indexOf(marker);
        if (start < 0) {
            return "";
        }
        int next = existing.indexOf('[', start + marker.length());
        String section = next < 0 ? existing.substring(start) : existing.substring(start, next);
        return section.trim();
    }

    static String withBackup(String encoded, String existing) {
        String backup = backupSection(existing);
        if (backup.isEmpty()) {
            return encoded;
        }
        return encoded + "\n" + backup + "\n";
    }

    private static LinkedHashMap<String, String> parse(String template) {
        LinkedHashMap<String, String> m = new LinkedHashMap<>();
        for (String line : template.split("\n")) {
            int eq = line.indexOf('=');
            if (eq > 0) {
                m.put(line.substring(0, eq), line.substring(eq + 1));
            }
        }
        return m;
    }

    private static int clamp(int v, int max) {
        return v < 0 ? 0 : (v > max ? max : v);
    }

    private Ini() {
    }
}
