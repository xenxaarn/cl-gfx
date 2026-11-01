package com.clgfx;

import android.content.ContentResolver;
import android.content.Context;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.net.Uri;
import android.os.Environment;
import android.provider.DocumentsContract;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Writes into a game's Saved tree using either direct file I/O or a SAF tree grant.
 * See docs/architecture.md.
 */
final class Storage {

    private static final String GAME_SUBPATH =
            "files/UE4Game/ShadowTrackerExtra/ShadowTrackerExtra";
    private static final String PREF = "clgfx";
    private static final String KEY_TREE = "tree";

    private static final int MODE_DIRECT = 0;
    private static final int MODE_SAF = 1;
    private static final int MODE_SHIZUKU = 2;

    private final Context ctx;
    private final SharedPreferences prefs;

    private Uri tree;
    private int mode = MODE_DIRECT;  // direct, SAF or Shizuku
    private String safBase;    // prefix inside the tree, "" for the game root
    private boolean safSaved;  // tree root is the Saved folder
    private String safPkg;     // package the resolved prefix belongs to
    private ShizukuBridge shizuku;
    private String lastError;  // why installProfile() failed, if it did
    private String lastDetail; // what installProfile() did on success

    Storage(Context ctx) {
        this.ctx = ctx.getApplicationContext();
        this.prefs = this.ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE);
        String saved = prefs.getString(KEY_TREE, null);
        if (saved != null) {
            try {
                tree = Uri.parse(saved);
            } catch (Exception ignored) {
                tree = null;
            }
        }
    }

    String backend() {
        if (mode == MODE_SHIZUKU) {
            return "Shizuku user service";
        }
        return mode == MODE_DIRECT ? "direct storage" : "SAF tree";
    }

    /** Human-readable reason the last {@link #installProfile} failed, or null. */
    String lastError() {
        return lastError;
    }

    /** Human-readable summary of the last successful {@link #installProfile}. */
    String lastDetail() {
        return lastDetail;
    }

    void setShizuku(ShizukuBridge bridge) {
        this.shizuku = bridge;
    }

    boolean shizukuReady() {
        return shizuku != null && shizuku.isConnected();
    }

    boolean hasTree() {
        return tree != null;
    }

    Uri tree() {
        return tree;
    }

    void setTree(Uri uri) {
        tree = uri;
        prefs.edit().putString(KEY_TREE, uri == null ? null : uri.toString()).apply();
        mode = MODE_DIRECT;
        safBase = null;
        safPkg = null;
    }

    void clearTree() {
        tree = null;
        prefs.edit().remove(KEY_TREE).apply();
        mode = MODE_DIRECT;
        safBase = null;
        safSaved = false;
        safPkg = null;
    }

    // ---------------------------------------------------------------- public

    /** rel is relative to the game's ShadowTrackerExtra folder. */
    boolean write(String pkg, String rel, byte[] data) {
        // Android 13+: the shell identity reached through Shizuku is the only
        // reliable writer for Android/data. Fall through to direct/SAF so a
        // working legacy install or an existing SAF grant still works.
        if (shizukuReady() && writeShizuku(pkg, rel, data)) {
            mode = MODE_SHIZUKU;
            return true;
        }
        if (tryDirect(pkg, rel, data)) {
            mode = MODE_DIRECT;
            return true;
        }
        if (tree != null) {
            try {
                if (resolveSaf(pkg) && writeSaf(rel, data)) {
                    mode = MODE_SAF;
                    return true;
                }
            } catch (Exception ignored) {
                // fall through
            }
        }
        return false;
    }

    boolean exists(String pkg, String rel) {
        if (shizukuReady()) {
            String path = shizukuPath(pkg, rel);
            if (path != null && shizuku.exists(path)) {
                return true;
            }
        }
        File f = directFile(pkg, rel);
        if (f != null && f.exists()) {
            return true;
        }
        if (tree != null) {
            try {
                if (resolveSaf(pkg)) {
                    return docId(rel, false) != null;
                }
            } catch (Exception ignored) {
                // ignore
            }
        }
        return false;
    }

    byte[] read(String pkg, String rel) {
        if (shizukuReady()) {
            String path = shizukuPath(pkg, rel);
            if (path != null) {
                byte[] data = shizuku.readFile(path);
                if (data != null) {
                    return data;
                }
            }
        }
        File f = directFile(pkg, rel);
        if (f != null && f.isFile()) {
            try {
                return readAll(new FileInputStream(f));
            } catch (Exception ignored) {
                // fall through
            }
        }
        if (tree != null) {
            try {
                if (resolveSaf(pkg)) {
                    String id = docId(rel, false);
                    if (id != null) {
                        InputStream in = ctx.getContentResolver()
                                .openInputStream(docUri(id));
                        if (in != null) {
                            return readAll(in);
                        }
                    }
                }
            } catch (Exception ignored) {
                // ignore
            }
        }
        return null;
    }

    boolean copyAsset(String pkg, String asset, String rel) {
        byte[] data;
        try {
            InputStream in = ctx.getAssets().open(asset);
            data = readAll(in);
        } catch (IOException e) {
            return false;
        }
        return write(pkg, rel, data);
    }

    /**
     * Back up Active.sav once, then install the profile.
     *
     * <p>When the user already has an Active.sav, only the render IntProperties in
     * {@link Gvas#MERGE_PROPERTIES} are copied from the profile asset. Sensitivity,
     * gyroscope and every other byte are preserved, and the file keeps its length.
     * The whole asset is copied only when no Active.sav exists yet.
     */
    boolean installProfile(String pkg, String asset) {
        lastError = null;
        lastDetail = null;
        String active = "Saved/SaveGames/Active.sav";
        String backup = active + ".clgfx.bak";
        boolean activeExists = exists(pkg, active);
        byte[] current = read(pkg, active);
        if (activeExists && current == null) {
            // Do not overwrite a layout we cannot read: the backup would be lost.
            lastError = "existing Active.sav cannot be read; refusing to overwrite";
            return false;
        }
        if (current != null && read(pkg, backup) == null) {
            if (!write(pkg, backup, current)) {
                lastError = "cannot write Active.sav.clgfx.bak";
                return false;
            }
        }

        if (current == null) {
            if (!copyAsset(pkg, asset, active)) {
                lastError = "cannot write " + active;
                return false;
            }
            lastDetail = "no existing Active.sav; installed the full profile";
            return true;
        }

        byte[] source;
        try {
            InputStream in = ctx.getAssets().open(asset);
            source = readAll(in);
        } catch (IOException e) {
            lastError = "profile asset " + asset + " is unreadable";
            return false;
        }

        Gvas.MergeResult result = Gvas.merge(current, source);
        if (result.data == null) {
            lastError = result.error;
            return false;
        }
        if (!write(pkg, active, result.data)) {
            lastError = "cannot write the merged Active.sav";
            return false;
        }
        lastDetail = "kept existing file; merged render settings only "
                + "(sensitivity and gyro preserved), backup: Active.sav.clgfx.bak";
        return true;
    }

    boolean restoreActive(String pkg) {
        String active = "Saved/SaveGames/Active.sav";
        byte[] backup = read(pkg, active + ".clgfx.bak");
        if (backup == null) {
            return false;
        }
        return write(pkg, active, backup);
    }

    // --------------------------------------------------------------- direct

    private File directRoot() {
        return Environment.getExternalStorageDirectory();
    }

    private File directFile(String pkg, String rel) {
        File root = directRoot();
        if (root == null) {
            return null;
        }
        return new File(root, "Android/data/" + pkg + "/" + GAME_SUBPATH + "/" + rel);
    }

    private boolean tryDirect(String pkg, String rel, byte[] data) {
        try {
            File f = directFile(pkg, rel);
            if (f == null) {
                return false;
            }
            File dir = f.getParentFile();
            if (dir == null || (!dir.isDirectory() && !dir.mkdirs())) {
                return false;
            }
            FileOutputStream out = null;
            try {
                out = new FileOutputStream(f);
                out.write(data);
                out.flush();
            } finally {
                closeQuietly(out);
            }
            return f.length() == data.length;
        } catch (Exception e) {
            return false;
        }
    }

    // ------------------------------------------------------------- Shizuku

    private String shizukuPath(String pkg, String rel) {
        File root = directRoot();
        if (root == null) {
            return null;
        }
        return new File(root,
                "Android/data/" + pkg + "/" + GAME_SUBPATH + "/" + rel).getAbsolutePath();
    }

    private boolean writeShizuku(String pkg, String rel, byte[] data) {
        String path = shizukuPath(pkg, rel);
        return path != null && shizuku.writeFile(path, data);
    }

    // ------------------------------------------------------------------ SAF

    private boolean resolveSaf(String pkg) {
        if (!pkg.equals(safPkg)) {
            safBase = null;
            safSaved = false;
            safPkg = pkg;
        }
        if (safBase != null) {
            return true;
        }
        if (tree == null) {
            return false;
        }
        String name = treeName();
        List<String> candidates = new ArrayList<>();
        candidates.add("Android/data/" + pkg + "/" + GAME_SUBPATH);
        candidates.add("data/" + pkg + "/" + GAME_SUBPATH);
        candidates.add(pkg + "/" + GAME_SUBPATH);
        candidates.add(GAME_SUBPATH);
        candidates.add("");
        for (String base : candidates) {
            String probe = join(base, "Saved/Config/Android");
            try {
                if (docId(probe, false) != null) {
                    safBase = base;
                    safSaved = false;
                    return true;
                }
            } catch (IOException ignored) {
                // try next candidate
            }
        }
        String savedProbe = "Config/Android";
        try {
            if (docId(savedProbe, false) != null) {
                safBase = "";
                safSaved = true;
                return true;
            }
        } catch (IOException ignored) {
            // fall through
        }
        // Not found: guess from the folder name so a fresh install can still work.
        if (pkg.equals(name)) {
            safBase = GAME_SUBPATH;
            safSaved = false;
            return true;
        }
        if ("data".equals(name)) {
            safBase = pkg + "/" + GAME_SUBPATH;
            safSaved = false;
            return true;
        }
        if ("Saved".equals(name)) {
            safBase = "";
            safSaved = true;
            return true;
        }
        if ("ShadowTrackerExtra".equals(name)) {
            safBase = "";
            safSaved = false;
            return true;
        }
        return false;
    }

    private String treeName() {
        try (Cursor c = ctx.getContentResolver().query(
                DocumentsContract.buildDocumentUriUsingTree(
                        tree, DocumentsContract.getTreeDocumentId(tree)),
                new String[]{DocumentsContract.Document.COLUMN_DISPLAY_NAME},
                null, null, null)) {
            if (c != null && c.moveToFirst()) {
                return c.getString(0);
            }
        } catch (Exception ignored) {
            // ignore
        }
        return "";
    }

    private String[] safSegments(String rel) {
        String full = safSaved ? rel.replaceFirst("^Saved/", "") : join(safBase, rel);
        List<String> parts = new ArrayList<>();
        for (String p : full.split("/")) {
            if (!p.isEmpty()) {
                parts.add(p);
            }
        }
        return parts.toArray(new String[0]);
    }

    private String docId(String rel, boolean create) throws IOException {
        String[] parts = safSegments(rel);
        String id = DocumentsContract.getTreeDocumentId(tree);
        for (int i = 0; i < parts.length; i++) {
            boolean last = i == parts.length - 1;
            String child = findChild(id, parts[i]);
            if (child == null) {
                if (!create) {
                    return null;
                }
                String type = last ? "application/octet-stream"
                        : DocumentsContract.Document.MIME_TYPE_DIR;
                Uri created = DocumentsContract.createDocument(
                        ctx.getContentResolver(), docUri(id), type, parts[i]);
                // Providers disagree about the returned id, so re-resolve by name.
                child = findChild(id, parts[i]);
                if (child == null && created != null) {
                    child = created.getLastPathSegment();
                }
                if (child == null) {
                    return null;
                }
            }
            id = child;
        }
        return id;
    }

    private String findChild(String parentId, String name) {
        ContentResolver cr = ctx.getContentResolver();
        Uri children = DocumentsContract.buildChildDocumentsUriUsingTree(tree, parentId);
        try (Cursor c = cr.query(children, new String[]{
                DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                DocumentsContract.Document.COLUMN_DISPLAY_NAME
        }, null, null, null)) {
            if (c == null) {
                return null;
            }
            while (c.moveToNext()) {
                if (name.equals(c.getString(1))) {
                    return c.getString(0);
                }
            }
        } catch (Exception ignored) {
            // ignore
        }
        return null;
    }

    private Uri docUri(String id) {
        return DocumentsContract.buildDocumentUriUsingTree(tree, id);
    }

    private boolean writeSaf(String rel, byte[] data) throws IOException {
        String id = docId(rel, true);
        if (id == null) {
            throw new IOException("cannot resolve " + rel);
        }
        OutputStream out = null;
        try {
            // "wt" truncates; some providers only implement "w".
            try {
                out = ctx.getContentResolver().openOutputStream(docUri(id), "wt");
            } catch (Exception ignored) {
                out = null;
            }
            if (out == null) {
                out = ctx.getContentResolver().openOutputStream(docUri(id), "w");
            }
            if (out == null) {
                throw new IOException("cannot open " + rel);
            }
            out.write(data);
            out.flush();
        } finally {
            closeQuietly(out);
        }
        // Verify the provider actually stored the bytes we sent.
        try (InputStream in = ctx.getContentResolver().openInputStream(docUri(id))) {
            return Arrays.equals(data, readAll(in));
        }
    }

    // ---------------------------------------------------------------- utils

    private static String join(String a, String b) {
        if (a == null || a.isEmpty()) {
            return b;
        }
        return a + "/" + b;
    }

    private static byte[] readAll(InputStream in) throws IOException {
        if (in == null) {
            throw new IOException("null stream");
        }
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) > 0) {
                out.write(buf, 0, n);
            }
            return out.toByteArray();
        } finally {
            closeQuietly(in);
        }
    }

    private static void closeQuietly(java.io.Closeable c) {
        if (c != null) {
            try {
                c.close();
            } catch (IOException ignored) {
                // ignore
            }
        }
    }
}
