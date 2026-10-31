package com.clgfx;

import android.content.ComponentName;
import android.content.Context;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.IBinder;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import rikka.shizuku.Shizuku;

/**
 * Execution bridge between the app and a Shizuku user service running as the
 * shell (or root) UID.
 *
 * <p>Android 13+ scoped storage blocks app processes from writing inside
 * {@code /sdcard/Android/data/<package>}, even with legacy external storage
 * requested. The user service is started by Shizuku and performs the same
 * java.io operations with the shell identity, which still has access to that
 * tree. See docs/shizuku.md.</p>
 */
final class ShizukuBridge {

    static final int REQUEST_CODE = 4101;
    static final String SHIZUKU_PACKAGE = "moe.shizuku.privileged.api";
    static final String DOWNLOAD_URL = "https://shizuku.rikka.app/download/";

    private static final long DEFAULT_CONNECT_TIMEOUT_MS = 12000L;

    private final Context ctx;
    private final Shizuku.UserServiceArgs args;

    private final Object lock = new Object();
    private final ServiceConnection connection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder binder) {
            service = IStorageService.Stub.asInterface(binder);
            CountDownLatch waiting = latch;
            if (waiting != null) {
                waiting.countDown();
            }
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            service = null;
            CountDownLatch waiting = latch;
            if (waiting != null) {
                waiting.countDown();
            }
        }
    };

    private volatile IStorageService service;
    private CountDownLatch latch;

    ShizukuBridge(Context context) {
        this.ctx = context.getApplicationContext();
        this.args = new Shizuku.UserServiceArgs(
                new ComponentName(ctx.getPackageName(), StorageService.class.getName()))
                .daemon(false)
                .processNameSuffix("storage")
                .debuggable(false)
                .version(2);
    }

    /** Android 13 is the first release where the legacy Android/data route is unreliable. */
    static boolean isRequired() {
        return Build.VERSION.SDK_INT >= 33;
    }

    boolean isInstalled() {
        try {
            ctx.getPackageManager().getPackageInfo(SHIZUKU_PACKAGE, 0);
            return true;
        } catch (Exception ignored) {
            return false;
        }
    }

    boolean isBinderAlive() {
        if (!isRequired()) {
            return false;
        }
        try {
            return Shizuku.pingBinder();
        } catch (Throwable ignored) {
            return false;
        }
    }

    boolean hasPermission() {
        if (!isBinderAlive()) {
            return false;
        }
        try {
            return Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED;
        } catch (Throwable ignored) {
            return false;
        }
    }

    /** Shizuku v11+ is required for UserService. */
    boolean isTooOld() {
        try {
            return Shizuku.isPreV11();
        } catch (Throwable ignored) {
            return false;
        }
    }

    boolean shouldShowRationale() {
        try {
            return Shizuku.shouldShowRequestPermissionRationale();
        } catch (Throwable ignored) {
            return false;
        }
    }

    void requestPermission() {
        try {
            Shizuku.requestPermission(REQUEST_CODE);
        } catch (Throwable ignored) {
            // The caller reports an actionable message if the binder is unavailable.
        }
    }

    boolean isConnected() {
        IStorageService current = service;
        if (current == null) {
            return false;
        }
        try {
            IBinder binder = current.asBinder();
            return binder != null && binder.pingBinder();
        } catch (Throwable ignored) {
            return false;
        }
    }

    /** Binds the user service and waits for the Binder. Call from a worker thread. */
    boolean connect() {
        return connect(DEFAULT_CONNECT_TIMEOUT_MS);
    }

    boolean connect(long timeoutMs) {
        if (!isBinderAlive() || !hasPermission()) {
            return false;
        }
        if (isConnected()) {
            return true;
        }

        CountDownLatch waiting;
        synchronized (lock) {
            if (isConnected()) {
                return true;
            }
            if (latch == null) {
                latch = new CountDownLatch(1);
                try {
                    Shizuku.bindUserService(args, connection);
                } catch (Throwable ignored) {
                    latch = null;
                    return false;
                }
            }
            waiting = latch;
        }

        try {
            waiting.await(timeoutMs, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        if (!isConnected()) {
            synchronized (lock) {
                if (latch == waiting) {
                    latch = null;
                }
            }
        }
        return isConnected();
    }

    void disconnect() {
        try {
            Shizuku.unbindUserService(args, connection, true);
        } catch (Throwable ignored) {
            // Service may already be gone.
        }
        synchronized (lock) {
            service = null;
            latch = null;
        }
    }

    // ------------------------------------------------------------ operations

    boolean writeFile(String path, byte[] data) {
        IStorageService current = service;
        if (current == null) {
            return false;
        }
        try {
            return current.writeFile(path, data);
        } catch (Throwable ignored) {
            return false;
        }
    }

    byte[] readFile(String path) {
        IStorageService current = service;
        if (current == null) {
            return null;
        }
        try {
            return current.readFile(path);
        } catch (Throwable ignored) {
            return null;
        }
    }

    boolean exists(String path) {
        IStorageService current = service;
        if (current == null) {
            return false;
        }
        try {
            return current.exists(path);
        } catch (Throwable ignored) {
            return false;
        }
    }

    long length(String path) {
        IStorageService current = service;
        if (current == null) {
            return -1;
        }
        try {
            return current.length(path);
        } catch (Throwable ignored) {
            return -1;
        }
    }

    /**
     * Runs argv through the user service without a shell, so no quoting is
     * needed. Useful for diagnostics such as {@code id} or {@code ls}.
     */
    String exec(String[] argv) {
        IStorageService current = service;
        if (current == null) {
            return "error=not connected";
        }
        try {
            return current.exec(argv);
        } catch (Throwable ignored) {
            return "error=not connected";
        }
    }

    /**
     * Runs a command through {@code sh -c}. Any path or user-supplied value
     * interpolated into {@code command} must pass through {@link #quote(String)}.
     */
    String execShell(String command) {
        return exec(new String[]{"sh", "-c", command});
    }

    /** POSIX single-quote escaping; safe for spaces, quotes, $, ; and globs. */
    static String quote(String value) {
        if (value == null || value.isEmpty()) {
            return "''";
        }
        return "'" + value.replace("'", "'\\''") + "'";
    }
}
