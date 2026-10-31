package com.clgfx;

import java.util.Locale;

/** Game package table and per-game quirks. */
final class Games {

    static final String[] LABELS = {
            "Global",
            "BGMI (India)",
            "China",
            "Korea",
            "Vietnam",
            "Taiwan",
            "Lite",
            "Global (Beta)"
    };

    static final String[] PACKAGES = {
            "com.tencent.ig",
            "com.tencent.tmgp.pubgmhd",
            "com.rekoo.pubgm",
            "com.pubg.krmobile",
            "com.vng.pubgmobile",
            "com.pubg.imobile",
            "com.tencent.iglite",
            "com.tencent.igce"
    };

    /** Relative to /sdcard. */
    static String gameRoot(String pkg) {
        return String.format(Locale.US,
                "/Android/data/%s/files/UE4Game/ShadowTrackerExtra/ShadowTrackerExtra", pkg);
    }

    static boolean isLite(String pkg) {
        return "com.tencent.iglite".equals(pkg);
    }

    static boolean isChina(String pkg) {
        return "com.rekoo.pubgm".equals(pkg);
    }

    private Games() {
    }
}
