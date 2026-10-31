package com.clgfx;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import rikka.shizuku.Shizuku;

public final class MainActivity extends Activity {

    private static final int REQ_TREE = 1001;
    private static final int REQ_EXPORT = 1002;

    private final ExecutorService worker = Executors.newSingleThreadExecutor();

    private Storage storage;
    private ShizukuBridge shizuku;
    private Spinner gameSpinner;
    private Spinner presetSpinner;
    private Spinner fpsSpinner;

    private Spinner resolution;
    private Spinner aa;
    private Spinner style;
    private Spinner rendering;
    private Spinner shadows;
    private Spinner shadowDistance;
    private Spinner movingShadows;
    private Spinner particle;
    private Spinner spawn;
    private Spinner mesh;
    private Spinner foliage;
    private Spinner color;
    private Spinner sound;
    private Spinner water;
    private Spinner profileSpinner;

    private TextView status;
    private Button applyButton;
    private Button launchButton;
    private Button folderButton;
    private Button restoreButton;
    private Button exportButton;
    private Button shizukuButton;

    private byte[] pendingExport;
    private Runnable pendingShizukuAction;

    private final Shizuku.OnBinderReceivedListener binderReceivedListener =
            () -> post(() -> log("Shizuku binder received."));
    private final Shizuku.OnBinderDeadListener binderDeadListener =
            () -> post(() -> log("Shizuku binder died. Start Shizuku again, then reopen cl-gfx."));
    private final Shizuku.OnRequestPermissionResultListener permissionResultListener =
            this::onShizukuPermissionResult;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        storage = new Storage(this);
        shizuku = new ShizukuBridge(this);
        storage.setShizuku(shizuku);
        setContentView(buildUi());
        refreshGameDependent();
        requestPermissionsIfNeeded();
        log("Ready. Select options, then Apply.");
        if (ShizukuBridge.isRequired()) {
            Shizuku.addBinderReceivedListenerSticky(binderReceivedListener);
            Shizuku.addBinderDeadListener(binderDeadListener);
            Shizuku.addRequestPermissionResultListener(permissionResultListener);
            log("Android " + Build.VERSION.RELEASE + " (API " + Build.VERSION.SDK_INT
                    + "): Shizuku backend for Android/data.");
            status.postDelayed(this::maybeShowShizukuOnboarding, 700);
        } else {
            log("Android " + Build.VERSION.RELEASE + " (API " + Build.VERSION.SDK_INT
                    + "): legacy direct/SAF storage backend.");
            log("Storage: " + (storage.hasTree() ? "SAF grant remembered" : "direct only"));
        }
    }

    @Override
    protected void onDestroy() {
        if (ShizukuBridge.isRequired()) {
            try {
                Shizuku.removeBinderReceivedListener(binderReceivedListener);
                Shizuku.removeBinderDeadListener(binderDeadListener);
                Shizuku.removeRequestPermissionResultListener(permissionResultListener);
            } catch (Throwable ignored) {
                // Binder may already be gone.
            }
            shizuku.disconnect();
        }
        worker.shutdownNow();
        super.onDestroy();
    }

    // ------------------------------------------------------------------- UI

    private View buildUi() {
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(Color.rgb(16, 18, 24));

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        int pad = dp(18);
        root.setPadding(pad, pad, pad, pad);
        scroll.addView(root, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(android.view.Gravity.CENTER_VERTICAL);

        ImageView logo = new ImageView(this);
        logo.setImageResource(R.drawable.ic_logo);
        logo.setContentDescription(getString(R.string.app_name) + " logo");
        header.addView(logo, new LinearLayout.LayoutParams(dp(58), dp(58)));

        LinearLayout titles = new LinearLayout(this);
        titles.setOrientation(LinearLayout.VERTICAL);
        titles.setPadding(dp(14), 0, 0, 0);
        header.addView(titles, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        TextView title = new TextView(this);
        title.setText("cl-gfx");
        title.setTextColor(Color.WHITE);
        title.setTextSize(28);
        title.setTypeface(title.getTypeface(), android.graphics.Typeface.BOLD);
        titles.addView(title);

        TextView sub = new TextView(this);
        sub.setText("Clean PUBG graphics configurator");
        sub.setTextColor(Color.rgb(150, 158, 175));
        sub.setTextSize(13);
        sub.setPadding(0, dp(2), 0, 0);
        titles.addView(sub);

        root.addView(header);
        root.addView(new View(this), new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(12)));

        gameSpinner = addRow(root, "Game", "Target package and config layout",
                Games.LABELS, 0, null);
        presetSpinner = addRow(root, "Graphics preset",
                "Template profile; individual options below override it",
                Ini.PRESETS, 0, null);
        resolution = addRow(root, "Resolution", "r.MobileContentScaleFactor",
                Ini.RESOLUTIONS, 0, null);
        fpsSpinner = addRow(root, "FPS", "PUBGDeviceFPS* values",
                Ini.FPS, 0, null);
        aa = addRow(root, "Anti-aliasing", "MSAA setting", Ini.AA, 0, null);
        style = addRow(root, "Color style", "UserHDRSetting / ACESStyle",
                Ini.STYLES, 0, null);
        rendering = addRow(root, "Rendering quality", "MaterialQualityLevel",
                Ini.RENDERING, 0, null);
        shadows = addRow(root, "Shadows", "Shadow resolution and cascades",
                Ini.SHADOWS, 0, null);
        shadowDistance = addRow(root, "Shadow distance", "Shadow.DistanceScale",
                Ini.SHADOW_DISTANCE, 0, null);
        movingShadows = addRow(root, "Moving shadows", "Dynamic object shadows",
                Ini.MOVING_SHADOWS, 0, null);
        particle = addRow(root, "Particle distance", "ParticleLODBias",
                Ini.PARTICLE, 0, null);
        spawn = addRow(root, "Spawn distance", "EmitterSpawnRateScale",
                Ini.SPAWN, 0, null);
        mesh = addRow(root, "Mesh distance", "StaticMeshLODDistanceScale",
                Ini.MESH, 0, null);
        foliage = addRow(root, "Foliage distance", "foliage.LODDistanceScale",
                Ini.FOLIAGE, 0, null);
        color = addRow(root, "Color quality", "Mobile.SceneColorFormat",
                Ini.COLOR, 0, null);
        sound = addRow(root, "Sound quality", "faq*.ini SoundQualityType",
                Ini.SOUND, 0, null);
        water = addRow(root, "Water reflections", "faq*.ini WaterReflectionSetting",
                Ini.WATER, 0, null);
        profileSpinner = addRow(root, "Render profile (Active.sav)",
                "Install matching GVAS profile; existing file is backed up",
                new String[]{"Skip", "Install"}, 1, null);

        applyButton = button("Apply");
        launchButton = button("Launch game");
        folderButton = button("Choose config folder");
        restoreButton = button("Restore Active.sav");
        exportButton = button("Export UserCustom.ini");
        shizukuButton = button("Shizuku setup / permission");

        LinearLayout row1 = horizontal();
        row1.addView(applyButton, weight());
        row1.addView(launchButton, weight());
        root.addView(row1, topMargin(14));

        LinearLayout row2 = horizontal();
        row2.addView(folderButton, weight());
        row2.addView(restoreButton, weight());
        root.addView(row2, topMargin(8));

        root.addView(exportButton, topMargin(8));
        root.addView(shizukuButton, topMargin(8));

        TextView note = new TextView(this);
        note.setText("cl-gfx never writes outside the game's Saved folder. "
                + "No ads, no analytics, no network access.\n"
                + "Android 13+: Shizuku is required to write into Android/data; "
                + "Android 10–12 keeps the existing direct/SAF path.");
        note.setTextColor(Color.rgb(120, 128, 145));
        note.setTextSize(11);
        note.setPadding(0, dp(12), 0, dp(6));
        root.addView(note);

        status = new TextView(this);
        status.setTextColor(Color.rgb(180, 220, 190));
        status.setTextSize(11);
        status.setTypeface(android.graphics.Typeface.MONOSPACE);
        status.setTextIsSelectable(true);
        root.addView(status);

        applyButton.setOnClickListener(v -> apply());
        launchButton.setOnClickListener(v -> launch());
        folderButton.setOnClickListener(v -> pickFolder());
        restoreButton.setOnClickListener(v -> restore());
        exportButton.setOnClickListener(v -> export());
        shizukuButton.setOnClickListener(v -> showShizukuSetup());

        gameSpinner.setOnItemSelectedListener(new SimpleSelect() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int pos, long id) {
                refreshGameDependent();
            }
        });

        return scroll;
    }

    private Spinner addRow(LinearLayout parent, String title, String desc,
                           String[] items, int selected,
                           AdapterView.OnItemSelectedListener listener) {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(0, dp(10), 0, dp(4));

        TextView t = new TextView(this);
        t.setText(title);
        t.setTextColor(Color.rgb(232, 234, 240));
        t.setTextSize(15);
        t.setTypeface(t.getTypeface(), android.graphics.Typeface.BOLD);
        box.addView(t);

        TextView d = new TextView(this);
        d.setText(desc);
        d.setTextColor(Color.rgb(140, 148, 165));
        d.setTextSize(11);
        box.addView(d);

        Spinner sp = new Spinner(this, Spinner.MODE_DROPDOWN);
        ArrayAdapter<String> adapter = new ArrayAdapter<String>(this,
                android.R.layout.simple_spinner_item, items) {
            @Override
            public View getView(int position, View convertView, ViewGroup p) {
                TextView v = (TextView) super.getView(position, convertView, p);
                v.setTextColor(Color.WHITE);
                v.setTextSize(14);
                return v;
            }

            @Override
            public View getDropDownView(int position, View convertView, ViewGroup p) {
                TextView v = (TextView) super.getDropDownView(position, convertView, p);
                v.setTextColor(Color.rgb(232, 234, 240));
                v.setTextSize(14);
                return v;
            }
        };
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        sp.setAdapter(adapter);
        sp.setSelection(selected);
        sp.setPadding(0, dp(4), 0, dp(4));
        if (listener != null) {
            sp.setOnItemSelectedListener(listener);
        }
        box.addView(sp, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        parent.addView(box);
        return sp;
    }

    private Button button(String text) {
        Button b = new Button(this);
        b.setText(text);
        b.setAllCaps(false);
        b.setTextSize(14);
        b.setTextColor(Color.WHITE);
        b.setBackgroundColor(Color.rgb(38, 44, 60));
        b.setPadding(dp(8), dp(10), dp(8), dp(10));
        return b;
    }

    private LinearLayout horizontal() {
        LinearLayout l = new LinearLayout(this);
        l.setOrientation(LinearLayout.HORIZONTAL);
        return l;
    }

    private LinearLayout.LayoutParams weight() {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        p.setMargins(dp(3), 0, dp(3), 0);
        return p;
    }

    private LinearLayout.LayoutParams topMargin(int dp) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        p.topMargin = dp(dp);
        return p;
    }

    private int dp(int v) {
        return (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v,
                getResources().getDisplayMetrics());
    }

    // -------------------------------------------------------------- actions

    private void refreshGameDependent() {
        String pkg = Games.PACKAGES[gameSpinner.getSelectedItemPosition()];
        boolean lite = Games.isLite(pkg);
        boolean china = Games.isChina(pkg);

        int preset = presetSpinner.getSelectedItemPosition();
        String[] presets = lite ? Ini.PRESETS_LITE : Ini.PRESETS;
        ArrayAdapter<String> pa = new ArrayAdapter<String>(this,
                android.R.layout.simple_spinner_item, presets) {
            @Override
            public View getView(int position, View convertView, ViewGroup p) {
                TextView v = (TextView) super.getView(position, convertView, p);
                v.setTextColor(Color.WHITE);
                v.setTextSize(14);
                return v;
            }

            @Override
            public View getDropDownView(int position, View convertView, ViewGroup p) {
                TextView v = (TextView) super.getDropDownView(position, convertView, p);
                v.setTextColor(Color.rgb(232, 234, 240));
                v.setTextSize(14);
                return v;
            }
        };
        pa.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        presetSpinner.setAdapter(pa);
        presetSpinner.setSelection(Math.min(preset, presets.length - 1));

        int fps = fpsSpinner.getSelectedItemPosition();
        String[] fpsItems = china ? Ini.FPS_CHINA : Ini.FPS;
        ArrayAdapter<String> fa = new ArrayAdapter<String>(this,
                android.R.layout.simple_spinner_item, fpsItems) {
            @Override
            public View getView(int position, View convertView, ViewGroup p) {
                TextView v = (TextView) super.getView(position, convertView, p);
                v.setTextColor(Color.WHITE);
                v.setTextSize(14);
                return v;
            }

            @Override
            public View getDropDownView(int position, View convertView, ViewGroup p) {
                TextView v = (TextView) super.getDropDownView(position, convertView, p);
                v.setTextColor(Color.rgb(232, 234, 240));
                v.setTextSize(14);
                return v;
            }
        };
        fa.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        fpsSpinner.setAdapter(fa);
        fpsSpinner.setSelection(Math.min(fps, fpsItems.length - 1));
    }

    private Ini.Options collect() {
        Ini.Options o = new Ini.Options();
        o.pkg = Games.PACKAGES[gameSpinner.getSelectedItemPosition()];
        o.lite = Games.isLite(o.pkg);
        o.china = Games.isChina(o.pkg);
        o.preset = presetSpinner.getSelectedItemPosition();
        o.resolution = resolution.getSelectedItemPosition();
        o.fps = fpsSpinner.getSelectedItemPosition();
        o.aa = aa.getSelectedItemPosition();
        o.style = style.getSelectedItemPosition();
        o.rendering = rendering.getSelectedItemPosition();
        o.shadows = shadows.getSelectedItemPosition();
        o.shadowDistance = shadowDistance.getSelectedItemPosition();
        o.movingShadows = movingShadows.getSelectedItemPosition();
        o.particle = particle.getSelectedItemPosition();
        o.spawn = spawn.getSelectedItemPosition();
        o.mesh = mesh.getSelectedItemPosition();
        o.foliage = foliage.getSelectedItemPosition();
        o.color = color.getSelectedItemPosition();
        o.sound = sound.getSelectedItemPosition();
        o.water = water.getSelectedItemPosition();
        o.applyProfile = profileSpinner.getSelectedItemPosition() > 0;
        return o;
    }

    private void apply() {
        final Ini.Options o = collect();
        if (!prepareShizukuForAction(this::apply)) {
            return;
        }
        setBusy(true);
        log("Applying for " + o.pkg + "...");
        worker.execute(() -> {
            StringBuilder report = new StringBuilder();
            boolean success;
            try {
                if (ShizukuBridge.isRequired() && !shizuku.connect()) {
                    report.append("Shizuku: user service unavailable\n");
                    success = false;
                } else {
                    success = applyBlocking(o, report);
                }
            } catch (Throwable t) {
                report.append("Apply failed: ").append(t).append('\n');
                success = false;
            }
            final String out = report.toString();
            final boolean ok = success;
            post(() -> {
                setBusy(false);
                log(out);
                if (!ok) {
                    if (ShizukuBridge.isRequired()) {
                        log("Could not write the config. Make sure Shizuku is running and "
                                + "cl-gfx is allowed in Shizuku, then try again. Close the game "
                                + "first. You can also Export and copy the file manually.");
                    } else {
                        log("Could not write the config. On Android 11–12 the game folder is "
                                + "protected: use \"Choose config folder\" and pick the game's "
                                + "Android/data/<package> folder, or Export and copy the file "
                                + "manually. Close the game first.");
                    }
                } else {
                    log("Done. Close the game completely before launching it.");
                }
            });
        });
    }

    private boolean applyBlocking(Ini.Options o, StringBuilder report) {
        boolean ok = true;

        String customRel = "Saved/Config/Android/UserCustom.ini";
        String settingsRel = "Saved/Config/Android/UserSettings.ini";

        String text = Ini.encode(Ini.build(o));
        boolean customExists = storage.exists(o.pkg, customRel);
        byte[] existing = storage.read(o.pkg, customRel);
        if (customExists && existing == null) {
            report.append("UserCustom.ini exists but cannot be read; refusing to overwrite.\n");
            return false;
        }

        boolean settingsExists = storage.exists(o.pkg, settingsRel);
        byte[] oldSettings = storage.read(o.pkg, settingsRel);
        if (settingsExists && oldSettings == null) {
            report.append("UserSettings.ini exists but cannot be read; refusing to overwrite.\n");
            return false;
        }

        if (existing != null) {
            String old = new String(existing, StandardCharsets.UTF_8);
            if (storage.read(o.pkg, customRel + ".clgfx.bak") == null) {
                boolean b = storage.write(o.pkg, customRel + ".clgfx.bak", existing);
                report.append(b ? "backup UserCustom.ini: ok\n" : "backup UserCustom.ini: FAILED\n");
            }
            text = Ini.withBackup(text, old);
        }
        boolean wroteIni = storage.write(o.pkg, customRel,
                text.getBytes(StandardCharsets.UTF_8));
        report.append(wroteIni ? "UserCustom.ini: ok\n" : "UserCustom.ini: FAILED\n");
        ok &= wroteIni;

        String faq = Ini.faqAsset(o);
        if (oldSettings != null && storage.read(o.pkg,
                settingsRel + ".clgfx.bak") == null) {
            boolean b = storage.write(o.pkg, settingsRel + ".clgfx.bak", oldSettings);
            report.append(b ? "backup UserSettings.ini: ok\n"
                    : "backup UserSettings.ini: FAILED\n");
        }
        boolean wroteFaq = storage.copyAsset(o.pkg, faq, settingsRel);
        report.append("UserSettings.ini (" + faq + "): "
                + (wroteFaq ? "ok" : "FAILED") + "\n");
        ok &= wroteFaq;

        if (o.applyProfile) {
            String asset = Ini.profileAsset(o, Build.VERSION.SDK_INT);
            if (asset == null) {
                report.append("render profile: not available for this game\n");
            } else {
                boolean installed = storage.installProfile(o.pkg, asset);
                report.append("Active.sav (" + asset + "): "
                        + (installed ? "ok" : "FAILED") + "\n");
                if (installed) {
                    report.append("previous Active.sav kept as Active.sav.clgfx.bak\n");
                }
                ok &= installed;
            }
        } else {
            report.append("Active.sav: skipped\n");
        }

        report.append("backend: ").append(ok ? storage.backend() : "none")
                .append("\n");
        return ok;
    }

    private void restore() {
        Ini.Options o = collect();
        if (!prepareShizukuForAction(this::restore)) {
            return;
        }
        log("Restoring Active.sav...");
        worker.execute(() -> {
            boolean ok;
            if (ShizukuBridge.isRequired() && !shizuku.connect()) {
                post(() -> log("Shizuku user service unavailable; cannot restore."));
                return;
            }
            try {
                ok = storage.restoreActive(o.pkg);
            } catch (Throwable t) {
                post(() -> log("Restore failed: " + t));
                return;
            }
            post(() -> log(ok ? "Active.sav restored from backup."
                    : "No Active.sav.clgfx.bak found for " + o.pkg + "."));
        });
    }

    private void launch() {
        Ini.Options o = collect();
        Intent i = getPackageManager().getLaunchIntentForPackage(o.pkg);
        if (i == null) {
            log("Not installed: " + o.pkg);
            return;
        }
        try {
            startActivity(i);
        } catch (Exception e) {
            log("Launch failed: " + e.getMessage());
        }
    }

    private void pickFolder() {
        Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
        i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION
                | Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
        try {
            startActivityForResult(i, REQ_TREE);
        } catch (Exception e) {
            toast("No document picker available");
        }
    }

    private void export() {
        Ini.Options o = collect();
        pendingExport = Ini.encode(Ini.build(o)).getBytes(StandardCharsets.UTF_8);
        Intent i = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        i.addCategory(Intent.CATEGORY_OPENABLE);
        i.setType("text/plain");
        i.putExtra(Intent.EXTRA_TITLE, "UserCustom.ini");
        try {
            startActivityForResult(i, REQ_EXPORT);
        } catch (Exception e) {
            toast("No file picker available");
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode != RESULT_OK || data == null || data.getData() == null) {
            return;
        }
        Uri uri = data.getData();
        if (requestCode == REQ_TREE) {
            try {
                getContentResolver().takePersistableUriPermission(uri,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION
                                | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
            } catch (Exception ignored) {
                // Some providers do not offer persistable grants.
            }
            storage.setTree(uri);
            log("Folder granted: " + uri);
            log("Prefix will be resolved against the game folder on Apply.");
        } else if (requestCode == REQ_EXPORT && pendingExport != null) {
            java.io.OutputStream out = null;
            try {
                try {
                    out = getContentResolver().openOutputStream(uri, "wt");
                } catch (Exception ignored) {
                    out = null;
                }
                if (out == null) {
                    out = getContentResolver().openOutputStream(uri, "w");
                }
                if (out == null) {
                    throw new java.io.IOException("cannot open the chosen file");
                }
                out.write(pendingExport);
                out.flush();
                log("Exported UserCustom.ini to the chosen file.");
            } catch (Exception e) {
                log("Export failed: " + e);
            } finally {
                if (out != null) {
                    try {
                        out.close();
                    } catch (Exception ignored) {
                        // ignore
                    }
                }
                pendingExport = null;
            }
        }
    }

    // -------------------------------------------------------------- Shizuku

    /**
     * Runs before Apply/Restore. Returns true only when the Shizuku binder is
     * alive and this app has been granted access. On Android 10–12 it is a no-op.
     */
    private boolean prepareShizukuForAction(Runnable retry) {
        if (!ShizukuBridge.isRequired()) {
            return true;
        }
        if (!shizuku.isBinderAlive()) {
            showShizukuDialog(shizuku.isInstalled()
                    ? "Shizuku is installed, but its service is not running."
                    : "Shizuku is not installed.");
            return false;
        }
        if (shizuku.isTooOld()) {
            showShizukuDialog("The installed Shizuku version is too old. "
                    + "Update the official Shizuku app and start it again.");
            return false;
        }
        if (!shizuku.hasPermission()) {
            if (shizuku.shouldShowRationale()) {
                showShizukuDialog("Shizuku is running, but cl-gfx is not authorized. "
                        + "Open Shizuku → Authorized apps and allow cl-gfx.");
            } else {
                pendingShizukuAction = retry;
                log("Requesting Shizuku permission...");
                shizuku.requestPermission();
            }
            return false;
        }
        return true;
    }

    private void maybeShowShizukuOnboarding() {
        if (!ShizukuBridge.isRequired()) {
            return;
        }
        if (!shizuku.isBinderAlive()) {
            showShizukuDialog(shizuku.isInstalled()
                    ? "Shizuku is installed, but its service is not running."
                    : "Shizuku is not installed.");
        } else if (!shizuku.hasPermission()) {
            log("Shizuku is running. Tap Apply to request permission.");
        }
    }

    private void showShizukuSetup() {
        if (!ShizukuBridge.isRequired()) {
            toast("Shizuku is only used on Android 13+. This device uses the legacy backend.");
            return;
        }
        if (!shizuku.isBinderAlive()) {
            showShizukuDialog(shizuku.isInstalled()
                    ? "Shizuku is installed, but its service is not running."
                    : "Shizuku is not installed.");
            return;
        }
        if (!shizuku.hasPermission()) {
            if (shizuku.shouldShowRationale()) {
                showShizukuDialog("cl-gfx is not authorized in Shizuku. "
                        + "Open Shizuku → Authorized apps and allow cl-gfx.");
            } else {
                pendingShizukuAction = null;
                log("Requesting Shizuku permission...");
                shizuku.requestPermission();
            }
            return;
        }
        log("Checking Shizuku user service...");
        worker.execute(() -> {
            boolean connected = shizuku.connect();
            String identity = connected ? shizuku.exec(new String[]{"id"}).trim() : "";
            post(() -> log(connected
                    ? "Shizuku ready. " + identity
                    : "Shizuku did not connect; check that Shizuku is running."));
        });
    }

    private void onShizukuPermissionResult(int requestCode, int grantResult) {
        if (requestCode != ShizukuBridge.REQUEST_CODE) {
            return;
        }
        if (grantResult == PackageManager.PERMISSION_GRANTED) {
            log("Shizuku permission granted.");
            Runnable next = pendingShizukuAction;
            pendingShizukuAction = null;
            if (next != null) {
                post(next);
            }
        } else {
            pendingShizukuAction = null;
            log("Shizuku permission denied. Open Shizuku → Authorized apps and allow cl-gfx.");
        }
    }

    private void showShizukuDialog(String reason) {
        new AlertDialog.Builder(this)
                .setTitle("Shizuku required on Android 13+")
                .setMessage(reason + "\n\nTo set it up:\n"
                        + "1. Install the official Shizuku app.\n"
                        + "2. Enable Developer options → Wireless debugging.\n"
                        + "3. Open Shizuku, start the service via Wireless debugging, "
                        + "then reopen cl-gfx and allow it in Shizuku.\n\n"
                        + "cl-gfx uses Shizuku only to write the game's config files; "
                        + "no other data is touched.")
                .setPositiveButton(shizuku.isInstalled() ? "Open Shizuku" : "Get Shizuku",
                        (dialog, which) -> openShizuku())
                .setNeutralButton("Developer options",
                        (dialog, which) -> openDeveloperOptions())
                .setNegativeButton("Close", null)
                .show();
    }

    private void openShizuku() {
        Intent launch = getPackageManager().getLaunchIntentForPackage(
                ShizukuBridge.SHIZUKU_PACKAGE);
        if (launch == null) {
            launch = new Intent(Intent.ACTION_VIEW, Uri.parse(ShizukuBridge.DOWNLOAD_URL));
        }
        try {
            startActivity(launch);
        } catch (Exception e) {
            toast("Could not open Shizuku: " + e.getMessage());
        }
    }

    private void openDeveloperOptions() {
        try {
            startActivity(new Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS));
        } catch (Exception e) {
            toast("Open Settings → Developer options manually.");
        }
    }

    private void requestPermissionsIfNeeded() {
        if (Build.VERSION.SDK_INT >= 23 && Build.VERSION.SDK_INT < 33) {
            List<String> need = new ArrayList<>();
            if (checkSelfPermission(android.Manifest.permission.WRITE_EXTERNAL_STORAGE)
                    != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                need.add(android.Manifest.permission.WRITE_EXTERNAL_STORAGE);
            }
            if (checkSelfPermission(android.Manifest.permission.READ_EXTERNAL_STORAGE)
                    != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                need.add(android.Manifest.permission.READ_EXTERNAL_STORAGE);
            }
            if (!need.isEmpty()) {
                requestPermissions(need.toArray(new String[0]), 77);
            }
        }
    }

    // ---------------------------------------------------------------- utils

    private void setBusy(boolean busy) {
        applyButton.setEnabled(!busy);
        launchButton.setEnabled(!busy);
        folderButton.setEnabled(!busy);
        restoreButton.setEnabled(!busy);
        exportButton.setEnabled(!busy);
        shizukuButton.setEnabled(!busy);
    }

    private void post(Runnable r) {
        runOnUiThread(r);
    }

    private void log(String s) {
        if (TextUtils.isEmpty(s)) {
            return;
        }
        status.append(s.endsWith("\n") ? s : s + "\n");
    }

    private void toast(String s) {
        Toast.makeText(this, s, Toast.LENGTH_SHORT).show();
    }

    private abstract static class SimpleSelect implements AdapterView.OnItemSelectedListener {
        @Override
        public void onNothingSelected(AdapterView<?> parent) {
        }
    }
}
