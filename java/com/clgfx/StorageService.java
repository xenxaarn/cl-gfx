package com.clgfx;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/**
 * Runs inside the Shizuku user-service process with the shell (or root) UID.
 * It is deliberately free of Android UI/Context dependencies so it can run under
 * app_process. All file operations use java.io directly; Binder carries the bytes.
 */
public final class StorageService extends IStorageService.Stub {

    private static final int MAX_OUTPUT = 64 * 1024;

    public StorageService() {
    }

    /** Reserved Shizuku transaction: stop the user-service process. */
    @Override
    public void destroy() {
        System.exit(0);
    }

    @Override
    public boolean writeFile(String path, byte[] data) {
        if (path == null || data == null) {
            return false;
        }
        File file = new File(path);
        File parent = file.getParentFile();
        if (parent != null && !parent.isDirectory() && !parent.mkdirs()) {
            return false;
        }
        FileOutputStream out = null;
        try {
            out = new FileOutputStream(file);
            out.write(data);
            out.flush();
            try {
                out.getFD().sync();
            } catch (IOException ignored) {
                // sync is best-effort
            }
        } catch (Exception e) {
            return false;
        } finally {
            closeQuietly(out);
        }
        // Best effort: make the config readable/writable by the game's UID even
        // though the file was created by the shell identity. FUSE may ignore
        // this, but when it is honoured it avoids a game-owned file becoming
        // shell-owned-only.
        try {
            file.setReadable(true, false);
            file.setWritable(true, false);
        } catch (Exception ignored) {
            // chmod is not always permitted on the FUSE mount.
        }
        return file.length() == data.length;
    }

    @Override
    public byte[] readFile(String path) {
        if (path == null) {
            return null;
        }
        File file = new File(path);
        if (!file.isFile()) {
            return null;
        }
        try (InputStream in = new FileInputStream(file)) {
            return readAll(in);
        } catch (Exception e) {
            return null;
        }
    }

    @Override
    public boolean exists(String path) {
        return path != null && new File(path).exists();
    }

    @Override
    public boolean mkdirs(String path) {
        if (path == null) {
            return false;
        }
        File dir = new File(path);
        return dir.isDirectory() || dir.mkdirs();
    }

    @Override
    public long length(String path) {
        if (path == null) {
            return -1;
        }
        File file = new File(path);
        return file.isFile() ? file.length() : -1;
    }

    @Override
    public String exec(String[] command) {
        if (command == null || command.length == 0) {
            return "";
        }
        Process process = null;
        try {
            ProcessBuilder builder = new ProcessBuilder(command);
            builder.redirectErrorStream(true);
            process = builder.start();
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[4096];
            int n;
            InputStream in = process.getInputStream();
            while ((n = in.read(buf)) > 0) {
                if (out.size() + n > MAX_OUTPUT) {
                    break;
                }
                out.write(buf, 0, n);
            }
            int code = process.waitFor();
            String text = new String(out.toByteArray(), StandardCharsets.UTF_8);
            return "exit=" + code + "\n" + text;
        } catch (Exception e) {
            return "error=" + e;
        } finally {
            if (process != null) {
                process.destroy();
            }
        }
    }

    private static byte[] readAll(InputStream in) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        int n;
        while ((n = in.read(buf)) > 0) {
            out.write(buf, 0, n);
        }
        return out.toByteArray();
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
