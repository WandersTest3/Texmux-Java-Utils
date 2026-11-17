package com.wanderstest3.termuxjavautils.job;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.os.Build;
import android.os.IBinder;
import androidx.core.app.NotificationCompat;
import androidx.core.app.ServiceCompat;
import androidx.core.content.ContextCompat;
import com.wanderstest3.termuxjavautils.R;
import com.wanderstest3.termuxjavautils.build.Diagnosis;
import com.wanderstest3.termuxjavautils.build.DiagnosisEngine;
import com.wanderstest3.termuxjavautils.build.JobSnapshot;
import com.wanderstest3.termuxjavautils.build.ScriptBuilder;
import com.wanderstest3.termuxjavautils.termux.TermuxBridge;
import com.wanderstest3.termuxjavautils.ui.build.BuildActivity;
import com.wanderstest3.termuxjavautils.ui.decompile.DecompileActivity;

public class BuildService extends Service {

    private static final String ACTION_START = "com.wanderstest3.termuxjavautils.JOB_START";
    private static final String ACTION_ATTACH = "com.wanderstest3.termuxjavautils.JOB_ATTACH";
    private static final String ACTION_CANCEL = "com.wanderstest3.termuxjavautils.JOB_CANCEL";
    private static final String CHANNEL_PROGRESS = "progress";
    private static final String CHANNEL_RESULT = "result";
    private static final int NOTIF_PROGRESS = 1001;
    private static final int NOTIF_RESULT = 1002;
    private static final long MAX_DURATION_MS = 5L * 60 * 60 * 1000;

    private volatile Thread worker;
    private volatile boolean cancelRequested = false;
    private volatile String currentId = "";

    public static void startJob(Context ctx, String id, String title, String script, String token,
                                String kind, String sdkDir) {
        Intent i = new Intent(ctx, BuildService.class).setAction(ACTION_START);
        i.putExtra("id", id);
        i.putExtra("title", title);
        i.putExtra("script", script);
        i.putExtra("token", token == null ? "" : token);
        i.putExtra("kind", kind == null ? JobManager.KIND_BUILD : kind);
        i.putExtra("sdkDir", sdkDir == null ? "" : sdkDir);
        ContextCompat.startForegroundService(ctx, i);
    }

    public static void attachLast(Context ctx, String sdkDir) {
        Intent i = new Intent(ctx, BuildService.class).setAction(ACTION_ATTACH);
        i.putExtra("sdkDir", sdkDir == null ? "" : sdkDir);
        ContextCompat.startForegroundService(ctx, i);
    }

    public static void cancel(Context ctx) {
        Intent i = new Intent(ctx, BuildService.class).setAction(ACTION_CANCEL);
        ContextCompat.startForegroundService(ctx, i);
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        createChannels();
        startAsForeground(buildProgressNotification("Preparando…", 0, 0));
        String action = intent == null ? null : intent.getAction();
        if (ACTION_CANCEL.equals(action)) {
            doCancel();
            return START_NOT_STICKY;
        }
        if (worker != null && worker.isAlive()) {
            return START_NOT_STICKY;
        }
        if (intent == null) {
            stopSelf();
            return START_NOT_STICKY;
        }
        final boolean attach = ACTION_ATTACH.equals(action);
        final String id = attach ? "last" : intent.getStringExtra("id");
        final String title = attach ? "Último build" : intent.getStringExtra("title");
        final String script = intent.getStringExtra("script");
        final String token = intent.getStringExtra("token");
        final String kind = attach ? JobManager.KIND_BUILD : intent.getStringExtra("kind");
        final String sdkDir = intent.getStringExtra("sdkDir");
        cancelRequested = false;
        currentId = id;
        worker = new Thread(() -> runJob(id, title, script, token, kind, sdkDir, attach), "tju-job");
        worker.start();
        return START_NOT_STICKY;
    }

    private void startAsForeground(Notification n) {
        if (Build.VERSION.SDK_INT >= 29) {
            ServiceCompat.startForeground(this, NOTIF_PROGRESS, n,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC);
        } else {
            startForeground(NOTIF_PROGRESS, n);
        }
    }

    private void doCancel() {
        cancelRequested = true;
        final String id = currentId;
        new Thread(() -> {
            if (id != null && !id.isEmpty() && !"last".equals(id)) {
                TermuxBridge.runBlocking(this, ScriptBuilder.cancelCommand(id), 20);
            } else {
                JobManager.Job j = JobManager.current();
                if (j != null && !j.snapshot.id.isEmpty()) {
                    TermuxBridge.runBlocking(this, ScriptBuilder.cancelCommand(j.snapshot.id), 20);
                }
            }
            if (worker == null || !worker.isAlive()) {
                stopForeground(true);
                stopSelf();
            }
        }).start();
    }

    private void runJob(String id, String title, String script, String token, String kind,
                        String sdkDir, boolean attach) {
        JobManager.Job job = new JobManager.Job();
        job.id = id;
        job.title = title;
        job.kind = kind == null ? JobManager.KIND_BUILD : kind;
        job.isBuild = !JobManager.KIND_COMMAND.equals(job.kind);
        job.sdkDir = sdkDir == null ? "" : sdkDir;
        JobManager.publish(job);
        DiagnosisEngine engine = new DiagnosisEngine(sdkDir);
        String synthetic = null;

        if (!attach) {
            if (!TermuxBridge.isInstalled(this)) {
                synthetic = "##TJU_FAIL## TERMUX_NO_RESPONSE";
            } else if (!TermuxBridge.hasPermission(this)) {
                synthetic = "##TJU_FAIL## TERMUX_PERMISSION";
            } else {
                TermuxBridge.Result r = TermuxBridge.runBlocking(this,
                        ScriptBuilder.launchCommand(id, script, token, job.kind), 40);
                if (r == null) {
                    synthetic = "##TJU_FAIL## TERMUX_NO_RESPONSE";
                } else if (r.errmsg.startsWith("SECURITY:")) {
                    synthetic = "##TJU_FAIL## TERMUX_PERMISSION";
                } else if (!r.internalOk()) {
                    synthetic = "##TJU_FAIL## TERMUX_ERROR " + r.errmsg;
                }
            }
        }
        if (synthetic != null) {
            finishWith(job, engine, synthetic, JobSnapshot.State.FAILED);
            return;
        }

        job.launching = false;
        int errors = 0;
        long deadline = System.currentTimeMillis() + MAX_DURATION_MS;
        String pollId = attach ? "last" : id;
        while (System.currentTimeMillis() < deadline) {
            TermuxBridge.Result r = TermuxBridge.runBlocking(this, ScriptBuilder.pollCommand(pollId, 300), 20);
            if (r == null || !r.internalOk()) {
                errors++;
                if (errors >= 8) {
                    finishWith(job, engine, "##TJU_FAIL## TERMUX_LOST", JobSnapshot.State.FAILED);
                    return;
                }
                sleep(3000);
                continue;
            }
            errors = 0;
            JobSnapshot snap = JobSnapshot.parse(r.stdout);
            if (snap.state == JobSnapshot.State.UNKNOWN && r.stdout.contains("NOSTATUS")) {
                if (attach) {
                    finishWith(job, engine, "##TJU_FAIL## TERMUX_LOST", JobSnapshot.State.FAILED);
                    return;
                }
            }
            if (!snap.id.isEmpty()) job.id = snap.id;
            if (attach && !snap.kind.isEmpty()) {
                job.kind = snap.kind;
                job.isBuild = !JobManager.KIND_COMMAND.equals(job.kind);
            }
            job.snapshot = snap;
            JobManager.publish(job);
            updateProgressNotification(job, snap);
            if (snap.finished()) {
                TermuxBridge.Result full = TermuxBridge.runBlocking(this,
                        ScriptBuilder.pollCommand(job.id.isEmpty() ? pollId : job.id, 1500), 30);
                if (full != null && full.internalOk()) {
                    JobSnapshot f = JobSnapshot.parse(full.stdout);
                    if (!f.log.isEmpty()) snap.log = f.log;
                    if (!f.artifacts.isEmpty()) {
                        snap.artifacts.clear();
                        snap.artifacts.addAll(f.artifacts);
                    }
                }
                finishWith(job, engine, null, snap.state);
                return;
            }
            if (cancelRequested) {
                sleep(1500);
            } else {
                sleep(2000);
            }
        }
        finishWith(job, engine, "##TJU_FAIL## TERMUX_LOST", JobSnapshot.State.FAILED);
    }

    private void finishWith(JobManager.Job job, DiagnosisEngine engine, String syntheticLog,
                            JobSnapshot.State state) {
        JobSnapshot snap = job.snapshot;
        if (syntheticLog != null) {
            snap.log = (snap.log == null ? "" : snap.log) + "\n" + syntheticLog + "\n";
            snap.state = state;
        }
        boolean failed = snap.state == JobSnapshot.State.FAILED || snap.state == JobSnapshot.State.CANCELLED;
        job.diagnoses = engine.analyze(snap.log, failed);
        job.finished = true;
        JobManager.publish(job);
        postResultNotification(job);
        stopForeground(true);
        stopSelf();
    }

    private void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private void createChannels() {
        if (Build.VERSION.SDK_INT < 26) return;
        NotificationManager nm = getSystemService(NotificationManager.class);
        NotificationChannel p = new NotificationChannel(CHANNEL_PROGRESS, "Andamento do build",
                NotificationManager.IMPORTANCE_LOW);
        NotificationChannel r = new NotificationChannel(CHANNEL_RESULT, "Resultado do build",
                NotificationManager.IMPORTANCE_HIGH);
        nm.createNotificationChannel(p);
        nm.createNotificationChannel(r);
    }

    private PendingIntent openIntent(String kind) {
        Class<?> target = JobManager.KIND_DECOMPILE.equals(kind) ? DecompileActivity.class : BuildActivity.class;
        Intent i = new Intent(this, target);
        i.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        return PendingIntent.getActivity(this, 0, i,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    private Notification buildProgressNotification(String text, int step, int total) {
        return buildProgressNotification(JobManager.KIND_BUILD, text, step, total);
    }

    private Notification buildProgressNotification(String kind, String text, int step, int total) {
        String head = JobManager.KIND_DECOMPILE.equals(kind) ? "Descompilando…"
                : JobManager.KIND_COMMAND.equals(kind) ? "Executando no Termux…" : "Compilando…";
        NotificationCompat.Builder b = new NotificationCompat.Builder(this, CHANNEL_PROGRESS)
                .setSmallIcon(R.drawable.ic_launcher_foreground)
                .setContentTitle(head)
                .setContentText(text)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setContentIntent(openIntent(kind));
        if (total > 0) b.setProgress(total, step, false);
        else b.setProgress(0, 0, true);
        return b.build();
    }

    private void updateProgressNotification(JobManager.Job job, JobSnapshot snap) {
        String text = snap.stepName.isEmpty() ? job.title : "Etapa " + snap.step + "/" + snap.total + ": " + snap.stepName;
        NotificationManager nm = getSystemService(NotificationManager.class);
        try {
            nm.notify(NOTIF_PROGRESS, buildProgressNotification(job.kind, text, snap.step, snap.total));
        } catch (SecurityException ignored) {
        }
    }

    private void postResultNotification(JobManager.Job job) {
        JobSnapshot snap = job.snapshot;
        String title;
        String text;
        if (snap.state == JobSnapshot.State.SUCCESS) {
            title = JobManager.KIND_DECOMPILE.equals(job.kind) ? "✓ Descompilação concluída"
                    : job.isBuild ? "✓ Build concluído" : "✓ Comando concluído";
            if (!snap.artifacts.isEmpty()) {
                String first = snap.artifacts.get(0);
                text = first.substring(first.lastIndexOf('/') + 1)
                        + (snap.artifacts.size() > 1 ? " e mais " + (snap.artifacts.size() - 1) : "")
                        + " salvo em Downloads";
            } else {
                text = job.title;
            }
        } else {
            boolean dec = JobManager.KIND_DECOMPILE.equals(job.kind);
            title = snap.state == JobSnapshot.State.CANCELLED ? (dec ? "Cancelado" : "Build cancelado")
                    : (dec ? "✗ A descompilação falhou" : "✗ Build falhou");
            Diagnosis first = null;
            for (Diagnosis d : job.diagnoses) {
                if (d.severity == Diagnosis.Severity.ERROR) { first = d; break; }
            }
            text = first != null ? first.title : "Toque para ver o motivo";
        }
        Notification n = new NotificationCompat.Builder(this, CHANNEL_RESULT)
                .setSmallIcon(R.drawable.ic_launcher_foreground)
                .setContentTitle(title)
                .setContentText(text)
                .setStyle(new NotificationCompat.BigTextStyle().bigText(text))
                .setAutoCancel(true)
                .setContentIntent(openIntent(job.kind))
                .build();
        try {
            getSystemService(NotificationManager.class).notify(NOTIF_RESULT, n);
        } catch (SecurityException ignored) {
        }
    }
}
