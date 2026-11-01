package com.clgfx;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/**
 * Reads the GVAS (Unreal Engine save game) property stream used by PUBG Mobile's
 * {@code Active.sav}, and merges the handful of render properties that cl-gfx owns
 * into an existing save without touching anything else.
 *
 * <p>Only the IntProperty values listed in {@link #MERGE_PROPERTIES} are copied.
 * Sensitivity, gyroscope and every other byte stay exactly as the user had them,
 * and the merged file keeps the original length. See docs/config-format.md.
 */
final class Gvas {

    /** Render settings cl-gfx is allowed to change in an existing Active.sav. */
    static final String[] MERGE_PROPERTIES = {
            "FPSLevel",
            "BattleFPS",
            "LobbyFPS",
            "BattleRenderStyle",
            "BattleRenderQuality",
            "LobbyRenderStyle",
            "LobbyRenderQuality",
    };

    private static final int MAX_CUSTOM_VERSIONS = 4096;
    private static final int MAX_PROPERTIES = 4096;

    private Gvas() {
    }

    /** Result of {@link #merge(byte[], byte[])}. Exactly one field is non-null. */
    static final class MergeResult {
        final byte[] data;
        final String error;

        private MergeResult(byte[] data, String error) {
            this.data = data;
            this.error = error;
        }
    }

    static final class Prop {
        final String name;
        final String type;
        final long size;
        final int valueOffset;

        Prop(String name, String type, long size, int valueOffset) {
            this.name = name;
            this.type = type;
            this.size = size;
            this.valueOffset = valueOffset;
        }
    }

    /**
     * Copies the render IntProperties present in {@code asset} into a clone of
     * {@code target}. Returns an error instead of a partial result when either
     * file is not a usable GVAS save, a property exists in the profile but not
     * in the user's file, or a property is not a 4-byte IntProperty.
     *
     * <p>China and Lite profiles only carry {@code FPSLevel}; their other render
     * values are not part of those save formats, so they are left alone.
     */
    static MergeResult merge(byte[] target, byte[] asset) {
        Map<String, Prop> existing;
        Map<String, Prop> profile;
        try {
            existing = parse(target);
            profile = parse(asset);
        } catch (IOException e) {
            return new MergeResult(null, "not a valid GVAS save (" + e.getMessage() + ")");
        }

        byte[] merged = target.clone();
        int applied = 0;
        for (String name : MERGE_PROPERTIES) {
            Prop from = profile.get(name);
            if (from == null) {
                // Not part of this save format (China/Lite profiles only carry FPSLevel).
                continue;
            }
            Prop to = existing.get(name);
            if (to == null) {
                return new MergeResult(null,
                        "existing Active.sav is missing the " + name + " property");
            }
            if (!"IntProperty".equals(to.type) || !"IntProperty".equals(from.type)) {
                return new MergeResult(null,
                        name + " is not an IntProperty in both files");
            }
            if (to.size != 4 || from.size != 4) {
                return new MergeResult(null, name + " is not a 4-byte integer");
            }
            if (to.valueOffset < 0 || to.valueOffset + 4 > merged.length
                    || from.valueOffset < 0 || from.valueOffset + 4 > asset.length) {
                return new MergeResult(null, name + " value is outside the file");
            }
            System.arraycopy(asset, from.valueOffset, merged, to.valueOffset, 4);
            applied++;
        }
        if (applied == 0) {
            return new MergeResult(null, "profile contains no render properties");
        }
        if (merged.length != target.length) {
            return new MergeResult(null, "merged length changed");
        }
        return new MergeResult(merged, null);
    }

    // ---------------------------------------------------------------- parser

    static Map<String, Prop> parse(byte[] data) throws IOException {
        Reader r = new Reader(data);
        if (data.length < 4
                || data[0] != 'G' || data[1] != 'V' || data[2] != 'A' || data[3] != 'S') {
            throw new IOException("missing GVAS header");
        }
        r.pos = 4;
        r.i32(); // save game version
        r.i32(); // package file version
        r.u16(); // engine major
        r.u16(); // engine minor
        r.u16(); // engine patch
        r.i32(); // engine changelist
        r.fstring(); // engine branch

        int customFormat = r.i32();
        int customCount = r.i32();
        if (customCount < 0 || customCount > MAX_CUSTOM_VERSIONS) {
            throw new IOException("bad custom version count");
        }
        if (customFormat == 1 || customFormat == 3) {
            // 16-byte version GUID + int32 version per entry.
            r.skip((long) customCount * 20);
        } else if (customFormat == 2) {
            for (int i = 0; i < customCount; i++) {
                r.fstring();
                r.i32();
            }
        } else {
            throw new IOException("unsupported custom version format " + customFormat);
        }

        r.fstring(); // save class name, e.g. SettingConfig_C

        Map<String, Prop> props = new HashMap<>();
        for (int i = 0; i < MAX_PROPERTIES; i++) {
            String name = r.fstring();
            if ("None".equals(name)) {
                return props;
            }
            String type = r.fstring();
            long size = r.i64();
            if (size < 0 || size > data.length) {
                throw new IOException("bad size for " + name);
            }

            if ("ArrayProperty".equals(type) || "SetProperty".equals(type)
                    || "StructProperty".equals(type) || "ByteProperty".equals(type)) {
                r.fstring(); // inner type / struct name / enum name
            } else if ("MapProperty".equals(type)) {
                r.fstring(); // key type
                r.fstring(); // value type
            }

            int valueOffset;
            if ("BoolProperty".equals(type)) {
                // Bool stores its value in the tag, before the optional GUID.
                valueOffset = r.pos;
                r.skip(1);
                if (r.u8() != 0) {
                    r.skip(16);
                }
            } else {
                if (r.u8() != 0) {
                    r.skip(16); // property GUID
                }
                valueOffset = r.pos;
                r.skip(size);
            }
            props.put(name, new Prop(name, type, size, valueOffset));
        }
        throw new IOException("too many properties");
    }

    /** Bounds-checked little-endian reader. */
    private static final class Reader {
        private final byte[] data;
        int pos;

        Reader(byte[] data) {
            this.data = data;
        }

        int u8() throws IOException {
            if (pos < 0 || pos >= data.length) {
                throw new IOException("truncated byte");
            }
            return data[pos++] & 0xff;
        }

        int u16() throws IOException {
            if (pos < 0 || pos + 2 > data.length) {
                throw new IOException("truncated int16");
            }
            int v = (data[pos] & 0xff) | ((data[pos + 1] & 0xff) << 8);
            pos += 2;
            return v;
        }

        int i32() throws IOException {
            if (pos < 0 || pos + 4 > data.length) {
                throw new IOException("truncated int32");
            }
            int v = (data[pos] & 0xff)
                    | ((data[pos + 1] & 0xff) << 8)
                    | ((data[pos + 2] & 0xff) << 16)
                    | ((data[pos + 3] & 0xff) << 24);
            pos += 4;
            return v;
        }

        long i64() throws IOException {
            long lo = i32() & 0xffffffffL;
            long hi = i32() & 0xffffffffL;
            return lo | (hi << 32);
        }

        void skip(long n) throws IOException {
            if (n < 0 || n > data.length || pos + n > data.length) {
                throw new IOException("truncated data");
            }
            pos += (int) n;
        }

        String fstring() throws IOException {
            int length = i32();
            if (length == 0) {
                return "";
            }
            if (length > 0) {
                if (!has(length)) {
                    throw new IOException("truncated string");
                }
                String s = new String(data, pos, length - 1, StandardCharsets.UTF_8);
                pos += length;
                return s;
            }
            long bytes = -(long) length * 2;
            if (bytes < 2 || bytes > data.length - pos) {
                throw new IOException("truncated string");
            }
            String s = new String(data, pos, (int) (bytes - 2), StandardCharsets.UTF_16LE);
            pos += (int) bytes;
            return s;
        }

        private boolean has(int n) {
            return pos >= 0 && n >= 0 && n <= data.length - pos;
        }
    }
}
