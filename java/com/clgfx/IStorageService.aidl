// Binder interface exposed by the Shizuku user service.
//
// The service runs as the shell (or root) user, so it can reach
// /sdcard/Android/data/<package> on Android 13+ where ordinary app file I/O is
// blocked by scoped storage. File contents travel as byte arrays over Binder;
// no path is ever interpolated into a shell command by the write path.
package com.clgfx;

interface IStorageService {

    // Reserved transaction used by the Shizuku server to stop the user service.
    void destroy() = 16777114;

    boolean writeFile(String path, in byte[] data) = 1;

    byte[] readFile(String path) = 2;

    boolean exists(String path) = 3;

    boolean mkdirs(String path) = 4;

    long length(String path) = 5;

    // Runs argv directly (no shell) and returns combined stdout/stderr.
    String exec(in String[] command) = 6;
}
