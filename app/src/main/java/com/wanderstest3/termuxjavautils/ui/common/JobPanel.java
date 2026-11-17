package com.wanderstest3.termuxjavautils.ui.common;

import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.progressindicator.LinearProgressIndicator;
import com.wanderstest3.termuxjavautils.R;
import com.wanderstest3.termuxjavautils.build.Diagnosis;
import com.wanderstest3.termuxjavautils.build.JobSnapshot;
import com.wanderstest3.termuxjavautils.build.ScriptBuilder;
import com.wanderstest3.termuxjavautils.job.BuildService;
import com.wanderstest3.termuxjavautils.job.JobManager;
import java.util.Locale;
import java.util.function.Predicate;

public final class JobPanel {

    public interface Host {
        void onRunningChanged(boolean running);

        String sdkDir();

        void runAgain();
    }

    private final BaseActivity act;
    private final Ui ui;
    private final Predicate<JobManager.Job> accepts;
    private final String againLabel;
    private final Host host;
    private final Handler handler = new Handler(Looper.getMainLooper());

    private final View card;
    private final TextView tvStatus;
    private final TextView tvStep;
    private final LinearProgressIndicator progress;
    private final TextView tvArtifacts;
    private final LinearLayout diagHolder;
    private final ScrollView logScroll;
    private final TextView tvLog;
    private final MaterialButton btnToggleLog;
    private final MaterialButton btnCopyLog;
    private String lastLogShown = "";
    private boolean logVisible = false;

    private final Runnable ticker = new Runnable() {
        @Override
        public void run() {
            JobManager.Job j = JobManager.current();
            if (j != null && !j.finished && accepts.test(j)) {
                updateElapsed(j);
                handler.postDelayed(this, 1000);
            }
        }
    };

    public JobPanel(BaseActivity act, Predicate<JobManager.Job> accepts, String againLabel, Host host) {
        this.act = act;
        this.ui = new Ui(act);
        this.accepts = accepts;
        this.againLabel = againLabel;
        this.host = host;

        Ui.Card result = ui.card("Resultado", null);
        card = result.view;
        card.setVisibility(View.GONE);
        tvStatus = ui.text("", 16, R.color.text_primary, true);
        tvStep = ui.hint("");
        progress = ui.progress();
        tvArtifacts = ui.body("");
        diagHolder = ui.column();
        btnToggleLog = ui.outlined("Mostrar log completo");
        btnCopyLog = ui.outlined("Copiar log");
        logScroll = new ScrollView(act);
        logScroll.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ui.dp(320)));
        logScroll.setBackgroundColor(act.getColor(R.color.purple_surface));
        logScroll.setPadding(ui.dp(8), ui.dp(8), ui.dp(8), ui.dp(8));
        tvLog = ui.mono("");
        logScroll.addView(tvLog);
        logScroll.setVisibility(View.GONE);
        btnToggleLog.setOnClickListener(v -> {
            logVisible = !logVisible;
            logScroll.setVisibility(logVisible ? View.VISIBLE : View.GONE);
            btnToggleLog.setText(logVisible ? "Ocultar log" : "Mostrar log completo");
            if (logVisible) logScroll.post(() -> logScroll.fullScroll(View.FOCUS_DOWN));
        });
        btnCopyLog.setOnClickListener(v -> {
            JobManager.Job j = JobManager.current();
            if (j != null) Ui.copy(act, "log", prettyLog(j.snapshot.log));
        });
        result.body.addView(tvStatus);
        result.body.addView(tvStep);
        result.body.addView(progress);
        result.body.addView(tvArtifacts);
        result.body.addView(diagHolder);
        result.body.addView(btnToggleLog);
        result.body.addView(logScroll);
        result.body.addView(btnCopyLog);
    }

    public View view() {
        return card;
    }

    public void reset() {
        lastLogShown = "";
    }

    public void stop() {
        handler.removeCallbacks(ticker);
    }

    public void onJobChanged(JobManager.Job job) {
        boolean running = job != null && !job.finished;
        if (job == null || !accepts.test(job)) {
            card.setVisibility(View.GONE);
            host.onRunningChanged(running);
            return;
        }
        card.setVisibility(View.VISIBLE);
        JobSnapshot s = job.snapshot;
        host.onRunningChanged(running);
        progress.setVisibility(running ? View.VISIBLE : View.GONE);
        boolean dec = JobManager.KIND_DECOMPILE.equals(job.kind);
        if (running) {
            if (s.total > 0) {
                progress.setIndeterminate(false);
                progress.setProgress(Math.max(1, s.step) * 100 / s.total);
            } else {
                progress.setIndeterminate(true);
            }
            tvStatus.setText(job.launching ? "Enviando ao Termux…"
                    : "⟳ " + (s.stepName.isEmpty() ? "Executando…" : s.stepName));
            tvStatus.setTextColor(act.getColor(R.color.status_running));
            handler.removeCallbacks(ticker);
            handler.post(ticker);
        } else {
            handler.removeCallbacks(ticker);
            boolean ok = s.state == JobSnapshot.State.SUCCESS;
            String done = dec ? "✓ Descompilação concluída" : job.isBuild ? "✓ Build concluído" : "✓ Comando concluído";
            String failed = dec ? "✗ A descompilação falhou" : "✗ O build falhou";
            tvStatus.setText(ok ? done : s.state == JobSnapshot.State.CANCELLED ? "⊘ Cancelado" : failed);
            tvStatus.setTextColor(act.getColor(ok ? R.color.status_success : R.color.status_failure));
            tvStep.setText(String.format(Locale.US, "Duração: %s",
                    formatElapsed(System.currentTimeMillis() - job.startedAt)));
        }
        if (running) updateElapsed(job);

        if (!s.artifacts.isEmpty()) {
            StringBuilder sb = new StringBuilder("Arquivos gerados:\n");
            for (String a : s.artifacts) sb.append("• ").append(a).append('\n');
            sb.append(dec ? "\nAbra o gerenciador de arquivos em Downloads para ver o resultado."
                    : "\nAbra o gerenciador de arquivos em Downloads para instalar.");
            tvArtifacts.setText(sb.toString());
            tvArtifacts.setVisibility(View.VISIBLE);
        } else {
            tvArtifacts.setVisibility(View.GONE);
        }

        String pretty = prettyLog(s.log);
        if (!pretty.equals(lastLogShown)) {
            lastLogShown = pretty;
            tvLog.setText(pretty);
            if (logVisible) logScroll.post(() -> logScroll.fullScroll(View.FOCUS_DOWN));
        }
        renderDiagnoses(job);
    }

    private void updateElapsed(JobManager.Job job) {
        JobSnapshot s = job.snapshot;
        String step = s.total > 0 ? "Etapa " + s.step + " de " + s.total + " · " : "";
        tvStep.setText(step + "tempo: " + formatElapsed(System.currentTimeMillis() - job.startedAt)
                + " · pode sair do app, você será avisado");
    }

    private static String formatElapsed(long ms) {
        long sec = ms / 1000;
        return String.format(Locale.US, "%02d:%02d", sec / 60, sec % 60);
    }

    private void renderDiagnoses(JobManager.Job job) {
        diagHolder.removeAllViews();
        if (!job.finished) return;
        boolean ok = job.snapshot.state == JobSnapshot.State.SUCCESS;
        if (ok && !job.isBuild) {
            diagHolder.addView(ui.hint("Pronto. Toque abaixo para tentar de novo."));
            MaterialButton again = ui.button(againLabel);
            again.setOnClickListener(v -> host.runAgain());
            diagHolder.addView(again);
        }
        if (!job.diagnoses.isEmpty()) {
            diagHolder.addView(ui.text(ok ? "Avisos" : "Por que falhou e como resolver", 15,
                    R.color.text_primary, true));
        }
        for (Diagnosis d : job.diagnoses) diagHolder.addView(diagnosisView(d));
    }

    private View diagnosisView(Diagnosis d) {
        boolean error = d.severity == Diagnosis.Severity.ERROR;
        Ui.Card c = ui.card((error ? "✗ " : "⚠ ") + d.title, d.cause);
        c.title.setTextColor(act.getColor(error ? R.color.error : R.color.accent_orange));
        c.title.setTextSize(15);
        c.sub.setTextColor(act.getColor(R.color.text_primary));
        int n = 1;
        for (String step : d.steps) {
            c.body.addView(ui.hint((d.steps.size() > 1 ? (n++) + ". " : "") + step));
        }
        if (d.hasFix()) {
            MaterialButton fix = ui.button("Corrigir automaticamente: " + d.fixLabel);
            fix.setOnClickListener(v -> confirmFix(d));
            c.body.addView(fix);
        }
        if (!d.copyText.isEmpty()) {
            MaterialButton cp = ui.outlined("Copiar comando para o Termux");
            cp.setOnClickListener(v -> Ui.copy(act, "comando", d.copyText));
            c.body.addView(cp);
        }
        ((LinearLayout.LayoutParams) c.view.getLayoutParams()).bottomMargin = ui.dp(10);
        return c.view;
    }

    private void confirmFix(Diagnosis d) {
        new MaterialAlertDialogBuilder(act)
                .setTitle(d.fixLabel)
                .setMessage("Isto vai executar no Termux:\n\n" + d.fixCommand)
                .setPositiveButton("Executar", (dlg, w) -> {
                    String id = "f" + System.currentTimeMillis();
                    String script = ScriptBuilder.commandJobScript(id, d.fixLabel, d.fixCommand);
                    lastLogShown = "";
                    BuildService.startJob(act, id, d.fixLabel, script, "", JobManager.KIND_COMMAND, host.sdkDir());
                })
                .setNegativeButton("Cancelar", null)
                .show();
    }

    public static String prettyLog(String log) {
        if (log == null) return "";
        StringBuilder sb = new StringBuilder();
        for (String line : log.split("\\r?\\n")) {
            if (line.startsWith("##TJU_CHECK##")) continue;
            if (line.startsWith("##TJU_STEP## ")) sb.append("▶ ").append(line.substring(13)).append('\n');
            else if (line.startsWith("##TJU_FAIL## ")) sb.append("✗ ").append(line.substring(13)).append('\n');
            else if (line.startsWith("##TJU_WARN## ")) sb.append("⚠ ").append(line.substring(13)).append('\n');
            else if (line.startsWith("##TJU_DONE##")) sb.append("✓ ").append(line.substring(12).trim()).append('\n');
            else sb.append(line).append('\n');
        }
        return sb.toString();
    }
}
