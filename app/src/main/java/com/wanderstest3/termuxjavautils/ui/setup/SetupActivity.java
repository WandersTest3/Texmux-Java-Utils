package com.wanderstest3.termuxjavautils.ui.setup;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import androidx.core.app.ActivityCompat;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.wanderstest3.termuxjavautils.R;
import com.wanderstest3.termuxjavautils.build.BuildRequest;
import com.wanderstest3.termuxjavautils.build.JobSnapshot;
import com.wanderstest3.termuxjavautils.build.ScriptBuilder;
import com.wanderstest3.termuxjavautils.job.BuildService;
import com.wanderstest3.termuxjavautils.job.JobManager;
import com.wanderstest3.termuxjavautils.termux.TermuxBridge;
import com.wanderstest3.termuxjavautils.ui.common.BaseActivity;
import com.wanderstest3.termuxjavautils.ui.common.Ui;
import com.wanderstest3.termuxjavautils.util.ProfileStore;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class SetupActivity extends BaseActivity implements JobManager.Listener {

    private static final String SETUP_COMMAND =
            "mkdir -p ~/.termux && touch ~/.termux/termux.properties && "
                    + "sed -i '/allow-external-apps/d' ~/.termux/termux.properties && "
                    + "echo 'allow-external-apps = true' >> ~/.termux/termux.properties && "
                    + "termux-reload-settings; termux-setup-storage";

    private TextView tvStatusLine;
    private LinearLayout checksHolder;
    private MaterialButton btnInstall;
    private TextView tvJob;
    private TextView tvJobLog;
    private Set<String> missingPackages = new LinkedHashSet<>();

    @Override
    protected String screenTitle() {
        return "Preparar Termux";
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        content.addView(ui.hint("Faça isto uma única vez. Depois disso, compilar é só colar o nome do ZIP ou o link do repositório."));

        Ui.Card steps = ui.card("Passo a passo", null);
        steps.body.addView(ui.body("1. Instale o Termux pela F-Droid ou pelo GitHub (a versão da Play Store está desatualizada e não funciona)."));
        steps.body.addView(ui.body("2. Abra o Termux, cole o comando de configuração abaixo e permita o acesso ao armazenamento quando o Android perguntar. Ele ativa \"allow-external-apps\", que deixa este app enviar comandos."));
        MaterialButton btnOpen = ui.outlined("Abrir Termux");
        MaterialButton btnCopy = ui.button("Copiar comando de configuração");
        steps.body.addView(btnOpen);
        steps.body.addView(btnCopy);
        steps.body.addView(ui.body("3. Volte aqui e conceda a permissão \"Executar comandos no Termux\"."));
        tvStatusLine = ui.hint("");
        MaterialButton btnPerm = ui.button("Conceder permissão");
        MaterialButton btnSettings = ui.outlined("Abrir configurações do app (se a janela não aparecer)");
        steps.body.addView(tvStatusLine);
        steps.body.addView(btnPerm);
        steps.body.addView(btnSettings);
        steps.body.addView(ui.body("4. Toque em Verificar ambiente. O app descobre o que falta e instala para você."));
        content.addView(steps.view);

        btnOpen.setOnClickListener(v -> {
            Intent i = getPackageManager().getLaunchIntentForPackage(TermuxBridge.PACKAGE);
            if (i == null) {
                Toast.makeText(this, "Termux não está instalado.", Toast.LENGTH_LONG).show();
            } else {
                startActivity(i);
            }
        });
        btnCopy.setOnClickListener(v -> Ui.copy(this, "setup", SETUP_COMMAND));
        btnPerm.setOnClickListener(v -> ActivityCompat.requestPermissions(this,
                new String[]{TermuxBridge.PERMISSION}, 55));
        btnSettings.setOnClickListener(v -> startActivity(new Intent(
                Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + getPackageName()))));

        Ui.Card env = ui.card("Verificar ambiente",
                "Confere Java, unzip, git, aapt2, Android SDK, acesso a Downloads, espaço e internet.");
        MaterialButton btnCheck = ui.button("Verificar ambiente");
        btnCheck.setOnClickListener(v -> runPreflight(btnCheck));
        env.body.addView(btnCheck);
        checksHolder = ui.column();
        env.body.addView(checksHolder);
        btnInstall = ui.button("Instalar o que falta");
        btnInstall.setVisibility(View.GONE);
        btnInstall.setOnClickListener(v -> installMissing());
        env.body.addView(btnInstall);
        content.addView(env.view);

        Ui.Card job = ui.card("Andamento da instalação", null);
        tvJob = ui.text("", 15, R.color.text_primary, true);
        tvJobLog = ui.mono("");
        job.body.addView(tvJob);
        job.body.addView(tvJobLog);
        job.view.setVisibility(View.GONE);
        jobCard = job.view;
        content.addView(job.view);

        Ui.Card sdk = ui.card("Android SDK",
                "Para compilar apps Android o Termux precisa de uma pasta com o Android SDK (platforms e build-tools). "
                        + "O app usa $HOME/android-sdk por padrão; se o seu estiver em outro lugar, ajuste em Compilar → Opções avançadas. "
                        + "Se as licenças ou plataformas faltarem, o app oferece a correção quando o build falhar.");
        content.addView(sdk.view);
    }

    private View jobCard;

    @Override
    protected void onResume() {
        super.onResume();
        JobManager.add(this);
        refreshStatus();
        onJobChanged(JobManager.current());
    }

    @Override
    protected void onPause() {
        super.onPause();
        JobManager.remove(this);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        refreshStatus();
    }

    private void refreshStatus() {
        String s;
        if (!TermuxBridge.isInstalled(this)) {
            s = "✗ Termux não encontrado neste celular.";
        } else if (!TermuxBridge.hasPermission(this)) {
            s = "✗ Permissão ainda não concedida.";
        } else {
            s = "✓ Termux instalado e permissão concedida.";
        }
        tvStatusLine.setText(s);
    }

    private String loadBody() throws Exception {
        try (BufferedReader br = new BufferedReader(new InputStreamReader(
                getAssets().open("tju_build.sh"), "UTF-8"))) {
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = br.readLine()) != null) sb.append(line).append('\n');
            return sb.toString();
        }
    }

    private void runPreflight(MaterialButton btn) {
        if (!TermuxBridge.isInstalled(this) || !TermuxBridge.hasPermission(this)) {
            Toast.makeText(this, "Conclua os passos 1 a 3 primeiro.", Toast.LENGTH_LONG).show();
            return;
        }
        final BuildRequest r = ProfileStore.loadLast(this);
        final String script;
        try {
            script = ScriptBuilder.buildScript(r, "pf", loadBody(), true);
        } catch (Exception e) {
            Toast.makeText(this, "Erro: " + e.getMessage(), Toast.LENGTH_LONG).show();
            return;
        }
        btn.setEnabled(false);
        checksHolder.removeAllViews();
        checksHolder.addView(ui.hint("Verificando… (até 30 s)"));
        new Thread(() -> {
            TermuxBridge.Result res = TermuxBridge.runBlocking(this, ScriptBuilder.preflightCommand(script), 60);
            runOnUiThread(() -> {
                btn.setEnabled(true);
                showChecks(res);
            });
        }).start();
    }

    private static String label(String id) {
        switch (id) {
            case "java": return "Java (OpenJDK)";
            case "unzip": return "unzip";
            case "git": return "git";
            case "curl": return "curl";
            case "aapt2": return "aapt2 do Termux";
            case "sdk": return "Pasta do Android SDK";
            case "platforms": return "Plataformas do SDK";
            case "buildtools": return "Build-tools do SDK";
            case "storage": return "Acesso à pasta Downloads";
            case "disk": return "Espaço livre";
            case "memory": return "Memória disponível";
            case "net": return "Internet";
            default: return id;
        }
    }

    private static String pkgFor(String id) {
        switch (id) {
            case "java": return "openjdk-17";
            case "unzip": return "unzip";
            case "git": return "git";
            case "curl": return "curl";
            case "aapt2": return "aapt2";
            default: return null;
        }
    }

    private void showChecks(TermuxBridge.Result res) {
        checksHolder.removeAllViews();
        missingPackages = new LinkedHashSet<>();
        if (res == null) {
            checksHolder.addView(ui.text("✗ O Termux não respondeu.", 14, R.color.error, true));
            checksHolder.addView(ui.hint("Abra o Termux, confirme o passo 2 (allow-external-apps) e tente de novo."));
            return;
        }
        if (!res.internalOk()) {
            checksHolder.addView(ui.text("✗ O Termux recusou o comando.", 14, R.color.error, true));
            checksHolder.addView(ui.hint(res.errmsg.isEmpty()
                    ? "Confirme o passo 2: allow-external-apps = true." : res.errmsg));
            return;
        }
        Matcher m = Pattern.compile("^##TJU_CHECK## (\\S+) (OK|FAIL|WARN) ?(.*)$", Pattern.MULTILINE).matcher(res.stdout);
        Map<String, String[]> rows = new LinkedHashMap<>();
        while (m.find()) rows.put(m.group(1), new String[]{m.group(2), m.group(3).trim()});
        if (rows.isEmpty()) {
            checksHolder.addView(ui.text("Não consegui ler o resultado.", 14, R.color.error, true));
            checksHolder.addView(ui.mono(res.stdout + res.stderr));
            return;
        }
        boolean storageFail = false;
        boolean sdkFail = false;
        for (Map.Entry<String, String[]> e : rows.entrySet()) {
            String st = e.getValue()[0];
            String icon = "OK".equals(st) ? "✓" : "FAIL".equals(st) ? "✗" : "⚠";
            int color = "OK".equals(st) ? R.color.status_success : "FAIL".equals(st) ? R.color.error : R.color.accent_orange;
            TextView t = ui.text(icon + " " + label(e.getKey()), 14, color, true);
            t.setPadding(0, 0, 0, 0);
            checksHolder.addView(t);
            String detail = e.getValue()[1];
            if (!detail.isEmpty()) {
                TextView d = ui.hint(detail);
                d.setPadding(ui.dp(18), 0, 0, 0);
                checksHolder.addView(d);
            }
            if ("FAIL".equals(st)) {
                String p = pkgFor(e.getKey());
                if (p != null) missingPackages.add(p);
                if ("storage".equals(e.getKey())) storageFail = true;
                if ("sdk".equals(e.getKey())) sdkFail = true;
            }
        }
        if (storageFail) {
            checksHolder.addView(ui.body("Acesso a Downloads: no Termux execute termux-setup-storage e permita."));
            MaterialButton c = ui.outlined("Copiar termux-setup-storage");
            c.setOnClickListener(v -> Ui.copy(this, "cmd", "termux-setup-storage"));
            checksHolder.addView(c);
        }
        if (sdkFail) {
            checksHolder.addView(ui.body("O Android SDK precisa existir no Termux (veja a seção Android SDK abaixo). Projetos de mod/JAR não precisam dele."));
        }
        boolean allOk = missingPackages.isEmpty() && !storageFail && !sdkFail;
        if (allOk) {
            checksHolder.addView(ui.text("Tudo pronto para compilar.", 15, R.color.status_success, true));
        }
        btnInstall.setVisibility(missingPackages.isEmpty() ? View.GONE : View.VISIBLE);
        if (!missingPackages.isEmpty()) {
            btnInstall.setText("Instalar o que falta: " + String.join(" ", missingPackages));
        }
    }

    private void installMissing() {
        if (missingPackages.isEmpty()) return;
        final String pkgs = String.join(" ", missingPackages);
        new MaterialAlertDialogBuilder(this)
                .setTitle("Instalar no Termux")
                .setMessage("Vai executar:\n\npkg install -y " + pkgs + "\n\nPode levar alguns minutos e baixar centenas de MB. Use Wi-Fi.")
                .setPositiveButton("Instalar", (d, w) -> {
                    String id = "i" + System.currentTimeMillis();
                    String script = ScriptBuilder.commandJobScript(id, "Instalando " + pkgs,
                            "pkg update -y; pkg install -y " + pkgs);
                    BuildService.startJob(this, id, "Instalando " + pkgs, script, "", false,
                            ProfileStore.loadLast(this).sdkDir);
                })
                .setNegativeButton("Cancelar", null)
                .show();
    }

    @Override
    public void onJobChanged(JobManager.Job job) {
        if (job == null || job.isBuild) {
            jobCard.setVisibility(View.GONE);
            return;
        }
        jobCard.setVisibility(View.VISIBLE);
        JobSnapshot s = job.snapshot;
        if (!job.finished) {
            tvJob.setText("⟳ " + job.title);
            tvJob.setTextColor(getColor(R.color.status_running));
        } else if (s.state == JobSnapshot.State.SUCCESS) {
            tvJob.setText("✓ Instalação concluída. Toque em Verificar ambiente de novo.");
            tvJob.setTextColor(getColor(R.color.status_success));
        } else {
            tvJob.setText("✗ A instalação falhou");
            tvJob.setTextColor(getColor(R.color.status_failure));
        }
        String[] lines = s.log == null ? new String[0] : s.log.split("\\r?\\n");
        StringBuilder sb = new StringBuilder();
        for (int i = Math.max(0, lines.length - 25); i < lines.length; i++) sb.append(lines[i]).append('\n');
        tvJobLog.setText(sb.toString());
    }
}
