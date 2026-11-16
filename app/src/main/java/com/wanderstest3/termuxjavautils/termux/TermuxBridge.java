package com.wanderstest3.termuxjavautils.termux;

import android.app.Activity;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Looper;
import androidx.core.content.ContextCompat;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public final class TermuxBridge {

    public static final String PACKAGE = "com.termux";
    public static final String PERMISSION = "com.termux.permission.RUN_COMMAND";
    public static final String BASH = "/data/data/com.termux/files/usr/bin/bash";
    public static final String HOME_DIR = "/data/data/com.termux/files/home";
    static final String ACTION_RESULT = "com.wanderstest3.termuxjavautils.TERMUX_RESULT";

    private static final AtomicInteger COUNTER = new AtomicInteger(1000);
    private static final ConcurrentHashMap<Integer, Callback> CALLBACKS = new ConcurrentHashMap<>();

    private TermuxBridge() {}

    public interface Callback {
        void onResult(Result result);
    }

    public static final class Result {
        public String stdout = "";
        public String stderr = "";
        public int exitCode = -1;
        public int err = Activity.RESULT_OK;
        public String errmsg = "";

        public boolean internalOk() {
            return err == Activity.RESULT_OK;
        }
    }

    public static boolean isInstalled(Context ctx) {
        try {
            ctx.getPackageManager().getPackageInfo(PACKAGE, 0);
            return true;
        } catch (PackageManager.NameNotFoundException e) {
            return false;
        }
    }

    public static boolean hasPermission(Context ctx) {
        return ContextCompat.checkSelfPermission(ctx, PERMISSION) == PackageManager.PERMISSION_GRANTED;
    }

    public static void run(Context ctx, String command, boolean background, String label, Callback cb) {
        Intent intent = new Intent("com.termux.RUN_COMMAND");
        intent.setClassName(PACKAGE, "com.termux.app.RunCommandService");
        intent.putExtra("com.termux.RUN_COMMAND_PATH", BASH);
        intent.putExtra("com.termux.RUN_COMMAND_ARGUMENTS", new String[]{"-c", command});
        intent.putExtra("com.termux.RUN_COMMAND_WORKDIR", HOME_DIR);
        intent.putExtra("com.termux.RUN_COMMAND_BACKGROUND", background);
        if (label != null) intent.putExtra("com.termux.RUN_COMMAND_COMMAND_LABEL", label);
        int code = -1;
        if (cb != null) {
            code = COUNTER.incrementAndGet();
            CALLBACKS.put(code, cb);
            Intent result = new Intent(ACTION_RESULT).setClass(ctx, TermuxResultReceiver.class);
            result.putExtra("code", code);
            int flags = PendingIntent.FLAG_UPDATE_CURRENT;
            if (Build.VERSION.SDK_INT >= 31) flags |= PendingIntent.FLAG_MUTABLE;
            PendingIntent pi = PendingIntent.getBroadcast(ctx, code, result, flags);
            intent.putExtra("com.termux.RUN_COMMAND_PENDING_INTENT", pi);
        }
        try {
            ContextCompat.startForegroundService(ctx, intent);
        } catch (RuntimeException e) {
            if (code != -1) CALLBACKS.remove(code);
            throw e;
        }
    }

    public static Result runBlocking(Context ctx, String command, int timeoutSeconds) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            throw new IllegalStateException("runBlocking não pode rodar na thread principal");
        }
        final Result[] box = new Result[1];
        final CountDownLatch latch = new CountDownLatch(1);
        try {
            run(ctx, command, true, null, r -> {
                box[0] = r;
                latch.countDown();
            });
        } catch (SecurityException e) {
            Result r = new Result();
            r.err = 1;
            r.errmsg = "SECURITY:" + e.getMessage();
            return r;
        } catch (RuntimeException e) {
            Result r = new Result();
            r.err = 1;
            r.errmsg = String.valueOf(e.getMessage());
            return r;
        }
        try {
            if (!latch.await(timeoutSeconds, TimeUnit.SECONDS)) return null;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return null;
        }
        return box[0];
    }

    static void deliver(int code, Bundle b) {
        Callback cb = CALLBACKS.remove(code);
        if (cb == null) return;
        Result r = new Result();
        if (b != null) {
            String out = b.getString("stdout");
            String errOut = b.getString("stderr");
            r.stdout = out == null ? "" : out;
            r.stderr = errOut == null ? "" : errOut;
            r.exitCode = b.getInt("exitCode", -1);
            r.err = b.getInt("err", Activity.RESULT_OK);
            String msg = b.getString("errmsg");
            r.errmsg = msg == null ? "" : msg;
        }
        cb.onResult(r);
    }
}
