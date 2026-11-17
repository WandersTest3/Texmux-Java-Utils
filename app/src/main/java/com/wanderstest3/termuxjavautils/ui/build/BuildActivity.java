package com.wanderstest3.termuxjavautils.ui.build;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.RadioGroup;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import androidx.core.app.ActivityCompat;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.progressindicator.LinearProgressIndicator;
import com.google.android.material.switchmaterial.SwitchMaterial;
import com.wanderstest3.termuxjavautils.R;
import com.wanderstest3.termuxjavautils.build.BuildRequest;
import com.wanderstest3.termuxjavautils.build.Diagnosis;
import com.wanderstest3.termuxjavautils.build.GitUrl;
import com.wanderstest3.termuxjavautils.build.JobSnapshot;
import com.wanderstest3.termuxjavautils.build.ScriptBuilder;
import com.wanderstest3.termuxjavautils.job.BuildService;
import com.wanderstest3.termuxjavautils.job.JobManager;
import com.wanderstest3.termuxjavautils.termux.TermuxBridge;
import com.wanderstest3.termuxjavautils.ui.common.BaseActivity;
import com.wanderstest3.termuxjavautils.ui.common.Ui;
import com.wanderstest3.termuxjavautils.ui.setup.SetupActivity;
import com.wanderstest3.termuxjavautils.util.Prefs;
import com.wanderstest3.termuxjavautils.util.ProfileStore;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class BuildActivity extends BaseActivity implements JobManager.Listener {

    private static final int ID_ZIP = 9001;
    private static final int ID_GIT = 9002;
    private static final int ID_AUTO = 9011;
    private static final int ID_RELEASE = 9012;
    private static final int ID_DEBUG = 9013;
    private static final int ID_JAR = 9014;

    private final Handler handler = new Handler(Looper.getMainLooper());

    private LinearLayout warnHolder;
    private RadioGroup rgSource;
    private LinearLayout groupZip;
    private LinearLayout groupGit;
    private Ui.Input etZip;
    private Ui.Input etGit;
    private Ui.Input etBranch;
    private SwitchMaterial swToken;
    private RadioGroup rgOutput;
    private Ui.Input etModule;
    private Ui.Input etExtrasDir;
    private Ui.Input etExtraFiles;
    private Ui.Input etSignZip;
    private Ui.Input etGradleArgs;
    private SwitchMaterial swAapt2;
    private Ui.Input etSdk;
    private Ui.Input etJava;
    private Ui.Input etDownload;
    private Ui.Input etOutDir;
    private Ui.Input etWork;
    private LinearLayout advanced;
    private MaterialButton btnAdvanced;
    private MaterialButton btnBuild;
    private MaterialButton btnCancel;
    private MaterialButton btnResume;

    private View resultCard;
    private TextView tvStatus;
    private TextView tvStep;
    private LinearProgressIndicator progress;
    private TextView tvArtifacts;
    private LinearLayout diagHolder;
    private ScrollView logScroll;
    private TextView tvLog;
    private MaterialButton btnToggleLog;
    private MaterialButton btnCopyLog;
    private String lastLogShown = "";
    private boolean logVisible = false;

    private final Runnable ticker = new Runnable() {
        @Override
        public void run() {
            JobManager.Job j = JobManager.current();
            if (j != null && !j.finished) {
                updateElapsed(j);
                handler.postDelayed(this, 1000);
            }
        }
    };

    @Override
    protected String screenTitle() {
        return "Compilar projeto";
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        buildUi();
        fromRequest(ProfileStore.loadLast(this));
        updateSourceVisibility();
        requestNotificationPermission();
    }

    @Override
    protected void onStart() {
        super.onStart();
        JobManager.add(this);
        onJobChanged(JobManager.current());
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshWarnings();
    }

    @Override
    protected void onStop() {
        super.onStop();
        JobManager.remove(this);
        handler.removeCallbacks(ticker);
        ProfileStore.saveLast(this, toRequest());
    }

    private void buildUi() {
        content.addView(ui.hint("Informe de onde vem o código, toque em Compilar e pronto. "
                + "O app roda tudo no Termux, mostra o andamento e avisa por notificação quando terminar — "
                + "você pode usar o celular normalmente enquanto isso. Se falhar, ele explica o motivo e oferece a correção."));

        warnHolder = ui.column();
        content.addView(warnHolder);

        Ui.Card profiles = ui.card("Perfis salvos",
                "Guarde as configurações de um projeto (nome do ZIP, módulo, assinatura) para compilar com um toque.");
        MaterialButton btnLoad = ui.outlined("Carregar perfil");
        MaterialButton btnSave = ui.outlined("Salvar configuração atual como perfil");
        MaterialButton btnDelete = ui.outlined("Excluir perfil");
        btnLoad.setOnClickListener(v -> chooseProfile());
        btnSave.setOnClickListener(v -> saveProfile());
        btnDelete.setOnClickListener(v -> deleteProfile());
        profiles.body.addView(btnLoad);
        profiles.body.addView(btnSave);
        profiles.body.addView(btnDelete);
        content.addView(profiles.view);

        Ui.Card source = ui.card("1. Origem do código",
                "Escolha como entregar o projeto: um ZIP que você baixou ou o link de um repositório.");
        rgSource = ui.radioGroup();
        rgSource.addView(ui.radio("ZIP na pasta Downloads", ID_ZIP));
        rgSource.addView(ui.radio("Link do repositório (baixa e compila sozinho)", ID_GIT));
        rgSource.setOnCheckedChangeListener((g, id) -> updateSourceVisibility());
        source.body.addView(rgSource);

        groupZip = ui.column();
        etZip = ui.input("Nome do arquivo ZIP",
                "Só o nome, como aparece em Downloads. A extensão .zip é opcional.", "Pojarush_Test3-main.zip");
        groupZip.addView(etZip.layout);
        MaterialButton btnList = ui.outlined("Listar ZIPs da pasta Downloads");
        btnList.setOnClickListener(v -> listZips());
        groupZip.addView(btnList);
        source.body.addView(groupZip);

        groupGit = ui.column();
        etGit = ui.input("Link do repositório",
                "Aceita github.com/usuario/repo, .git e links /tree/branch.", "https://github.com/usuario/repositorio");
        etBranch = ui.input("Branch (opcional)", "Vazio = branch padrão do repositório.", "main");
        swToken = ui.toggle("Repositório privado: usar o token salvo em GitHub Actions", false);
        groupGit.addView(etGit.layout);
        groupGit.addView(etBranch.layout);
        groupGit.addView(swToken);
        source.body.addView(groupGit);
        content.addView(source.view);

        Ui.Card what = ui.card("2. O que gerar",
                "Automático escolhe sozinho: app Android com keystore → Release; sem keystore → Debug (instalável); mod/Java → JAR.");
        rgOutput = ui.radioGroup();
        rgOutput.addView(ui.radio("Automático (recomendado)", ID_AUTO));
        rgOutput.addView(ui.radio("APK Release (assinado, se configurado)", ID_RELEASE));
        rgOutput.addView(ui.radio("APK Debug (instala direto)", ID_DEBUG));
        rgOutput.addView(ui.radio("JAR / mod (tarefa build)", ID_JAR));
        what.body.addView(rgOutput);
        etModule = ui.input("Módulo do projeto (opcional)",
                "Deixe vazio para o app detectar. Ex.: app ou Pojarush.", "app");
        what.body.addView(etModule.layout);
        content.addView(what.view);

        Ui.Card sign = ui.card("3. Assinatura (automática)",
                "Deixe vazio e o app procura em Downloads um ZIP com o nome do projeto + \"assinatura\" (ex.: Pojarush_Test3-assinatura.zip) "
                        + "com a keystore (.jks) e os arquivos .store_password.txt, .key_password.txt e .key_alias.txt. "
                        + "As chaves ficam guardadas no Termux e são reutilizadas nos próximos builds, inclusive por link de repositório.");
        etSignZip = ui.input("ZIP de assinatura (opcional)",
                "Só preencha se o nome for diferente do padrão.", "Pojarush_Test3-assinatura.zip");
        sign.body.addView(etSignZip.layout);
        etExtrasDir = ui.input("Outros arquivos: pasta no Termux (opcional)", "Copia arquivos de uma pasta do Termux para o projeto, se precisar de algo além da assinatura.", "~/projects/MeuApp");
        etExtraFiles = ui.input("Outros arquivos: nomes a copiar", "Separados por espaço.", "config.properties");
        sign.body.addView(etExtrasDir.layout);
        sign.body.addView(etExtraFiles.layout);
        content.addView(sign.view);

        Ui.Card adv = ui.card("Opções avançadas", "Só mexa aqui se o padrão não servir para o seu Termux.");
        btnAdvanced = ui.outlined("Mostrar opções avançadas");
        advanced = ui.column();
        advanced.setVisibility(View.GONE);
        etGradleArgs = ui.input("Argumentos extras do Gradle",
                "Ex.: -Dorg.gradle.jvmargs=-Xmx1024m --max-workers=1", "");
        swAapt2 = ui.toggle("Usar o aapt2 do Termux (necessário no celular)", true);
        etSdk = ui.input("Pasta do Android SDK", "Dentro do Termux.", "$HOME/android-sdk");
        etJava = ui.input("Java home (opcional)", "Vazio = detecta o OpenJDK do Termux.",
                "$PREFIX/lib/jvm/java-17-openjdk");
        etDownload = ui.input("Pasta dos ZIPs", "", "/storage/emulated/0/Download");
        etOutDir = ui.input("Pasta de saída do APK/JAR", "", "/storage/emulated/0/Download");
        etWork = ui.input("Pasta de trabalho no Termux", "Onde o código é extraído e compilado.", "$HOME/projects/tju");
        advanced.addView(etGradleArgs.layout);
        advanced.addView(swAapt2);
        advanced.addView(etSdk.layout);
        advanced.addView(etJava.layout);
        advanced.addView(etDownload.layout);
        advanced.addView(etOutDir.layout);
        advanced.addView(etWork.layout);
        btnAdvanced.setOnClickListener(v -> {
            boolean show = advanced.getVisibility() != View.VISIBLE;
            advanced.setVisibility(show ? View.VISIBLE : View.GONE);
            btnAdvanced.setText(show ? "Ocultar opções avançadas" : "Mostrar opções avançadas");
        });
        adv.body.addView(btnAdvanced);
        adv.body.addView(advanced);
        content.addView(adv.view);

        btnBuild = ui.button("Compilar agora");
        btnBuild.setOnClickListener(v -> startBuild());
        btnCancel = ui.outlined("Cancelar build");
        btnCancel.setVisibility(View.GONE);
        btnCancel.setOnClickListener(v -> confirmCancel());
        btnResume = ui.outlined("Retomar último build");
        btnResume.setOnClickListener(v -> BuildService.attachLast(this, etSdk.get()));
        content.addView(btnBuild);
        content.addView(btnCancel);
        content.addView(btnResume);

        Ui.Card result = ui.card("Resultado", null);
        resultCard = result.view;
        resultCard.setVisibility(View.GONE);
        tvStatus = ui.text("", 16, R.color.text_primary, true);
        tvStep = ui.hint("");
        progress = ui.progress();
        tvArtifacts = ui.body("");
        diagHolder = ui.column();
        btnToggleLog = ui.outlined("Mostrar log completo");
        btnCopyLog = ui.outlined("Copiar log");
        logScroll = new ScrollView(this);
        logScroll.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ui.dp(320)));
        logScroll.setBackgroundColor(getColor(R.color.purple_surface));
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
            if (j != null) Ui.copy(this, "log", prettyLog(j.snapshot.log));
        });
        result.body.addView(tvStatus);
        result.body.addView(tvStep);
        result.body.addView(progress);
        result.body.addView(tvArtifacts);
        result.body.addView(diagHolder);
        result.body.addView(btnToggleLog);
        result.body.addView(logScroll);
        result.body.addView(btnCopyLog);
        content.addView(resultCard);
    }

    private void updateSourceVisibility() {
        boolean git = rgSource.getCheckedRadioButtonId() == ID_GIT;
        groupZip.setVisibility(git ? View.GONE : View.VISIBLE);
        groupGit.setVisibility(git ? View.VISIBLE : View.GONE);
    }

    private void refreshWarnings() {
        warnHolder.removeAllViews();
        String msg = null;
        if (!TermuxBridge.isInstalled(this)) {
            msg = "O Termux não está instalado. Instale a versão da F-Droid ou do GitHub.";
        } else if (!TermuxBridge.hasPermission(this)) {
            msg = "Falta liberar a permissão para este app executar comandos no Termux.";
        }
        if (msg == null) return;
        Ui.Card c = ui.card("Antes de compilar", msg);
        MaterialButton b = ui.button("Abrir Preparar Termux");
        b.setOnClickListener(v -> startActivity(new Intent(this, SetupActivity.class)));
        c.body.addView(b);
        warnHolder.addView(c.view);
    }

    private void requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= 33
                && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.POST_NOTIFICATIONS}, 77);
        }
    }

    private BuildRequest toRequest() {
        BuildRequest r = new BuildRequest();
        r.fromGit = rgSource.getCheckedRadioButtonId() == ID_GIT;
        r.zipName = etZip.get();
        r.gitUrl = etGit.get();
        r.branch = etBranch.get();
        r.useToken = swToken.isChecked();
        int out = rgOutput.getCheckedRadioButtonId();
        r.output = out == ID_RELEASE ? BuildRequest.OUT_RELEASE
                : out == ID_DEBUG ? BuildRequest.OUT_DEBUG
                : out == ID_JAR ? BuildRequest.OUT_JAR : BuildRequest.OUT_AUTO;
        r.module = etModule.get();
        r.extrasDir = etExtrasDir.get();
        r.extraFiles = etExtraFiles.get();
        r.signZip = etSignZip.get();
        r.gradleArgs = etGradleArgs.get();
        r.useAapt2 = swAapt2.isChecked();
        r.sdkDir = etSdk.get();
        r.javaHome = etJava.get();
        r.downloadDir = etDownload.get();
        r.outputDir = etOutDir.get();
        r.workDir = etWork.get();
        return r;
    }

    private void fromRequest(BuildRequest r) {
        rgSource.check(r.fromGit ? ID_GIT : ID_ZIP);
        etZip.set(r.zipName);
        etGit.set(r.gitUrl);
        etBranch.set(r.branch);
        swToken.setChecked(r.useToken);
        int id = BuildRequest.OUT_RELEASE.equals(r.output) ? ID_RELEASE
                : BuildRequest.OUT_DEBUG.equals(r.output) ? ID_DEBUG
                : BuildRequest.OUT_JAR.equals(r.output) ? ID_JAR : ID_AUTO;
        rgOutput.check(id);
        etModule.set(r.module);
        etExtrasDir.set(r.extrasDir);
        etExtraFiles.set(r.extraFiles);
        etSignZip.set(r.signZip);
        etGradleArgs.set(r.gradleArgs);
        swAapt2.setChecked(r.useAapt2);
        etSdk.set(r.sdkDir);
        etJava.set(r.javaHome);
        etDownload.set(r.downloadDir);
        etOutDir.set(r.outputDir);
        etWork.set(r.workDir);
        updateSourceVisibility();
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

    private void startBuild() {
        if (JobManager.isRunning()) {
            Toast.makeText(this, "Já existe um build em andamento.", Toast.LENGTH_SHORT).show();
            return;
        }
        BuildRequest r = toRequest();
        if (r.fromGit) {
            if (GitUrl.parse(r.gitUrl) == null) {
                etGit.layout.setError("Link inválido. Use algo como https://github.com/usuario/repo");
                return;
            }
            etGit.layout.setError(null);
        } else if (r.zipName.isEmpty()) {
            etZip.layout.setError("Informe o nome do ZIP (ou use Listar ZIPs)");
            return;
        } else {
            etZip.layout.setError(null);
        }
        if (!TermuxBridge.isInstalled(this) || !TermuxBridge.hasPermission(this)) {
            new MaterialAlertDialogBuilder(this)
                    .setTitle("Termux não está pronto")
                    .setMessage("Instale o Termux e conceda a permissão de executar comandos. A tela Preparar Termux guia cada passo.")
                    .setPositiveButton("Abrir Preparar Termux",
                            (d, w) -> startActivity(new Intent(this, SetupActivity.class)))
                    .setNegativeButton("Cancelar", null)
                    .show();
            return;
        }
        if (r.fromGit && r.useToken && Prefs.token(this).isEmpty()) {
            etGit.layout.setError("Salve um token na tela GitHub Actions ou desmarque \"usar token\"");
            return;
        }
        try {
            String id = "b" + System.currentTimeMillis();
            String script = ScriptBuilder.buildScript(r, id, loadBody(), false);
            String token = r.fromGit && r.useToken ? Prefs.token(this) : "";
            String title = r.sourceLabel();
            ProfileStore.saveLast(this, r);
            lastLogShown = "";
            BuildService.startJob(this, id, title, script, token, true, r.sdkDir);
            Toast.makeText(this, "Build enviado ao Termux. Você pode sair do app.", Toast.LENGTH_LONG).show();
        } catch (Exception e) {
            Toast.makeText(this, "Erro: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private void confirmCancel() {
        new MaterialAlertDialogBuilder(this)
                .setTitle("Cancelar build?")
                .setMessage("O Gradle será interrompido no Termux.")
                .setPositiveButton("Cancelar build", (d, w) -> BuildService.cancel(this))
                .setNegativeButton("Continuar", null)
                .show();
    }

    private void listZips() {
        if (!TermuxBridge.isInstalled(this) || !TermuxBridge.hasPermission(this)) {
            Toast.makeText(this, "Prepare o Termux primeiro (permissão).", Toast.LENGTH_LONG).show();
            return;
        }
        final String cmd = ScriptBuilder.listZipsCommand(etDownload.get());
        Toast.makeText(this, "Consultando Downloads no Termux…", Toast.LENGTH_SHORT).show();
        new Thread(() -> {
            TermuxBridge.Result res = TermuxBridge.runBlocking(this, cmd, 25);
            runOnUiThread(() -> {
                if (res == null || !res.internalOk()) {
                    Toast.makeText(this, "O Termux não respondeu. Veja Preparar Termux.", Toast.LENGTH_LONG).show();
                    return;
                }
                List<String> names = new ArrayList<>();
                for (String l : res.stdout.split("\\r?\\n")) if (!l.trim().isEmpty()) names.add(l.trim());
                if (names.isEmpty()) {
                    new MaterialAlertDialogBuilder(this)
                            .setTitle("Nenhum ZIP encontrado")
                            .setMessage("Não há ZIPs na pasta ou o Termux não tem acesso a ela. No Termux execute: termux-setup-storage")
                            .setPositiveButton("OK", null)
                            .show();
                    return;
                }
                final String[] arr = names.toArray(new String[0]);
                new MaterialAlertDialogBuilder(this)
                        .setTitle("ZIPs em Downloads (mais recentes primeiro)")
                        .setItems(arr, (d, i) -> etZip.set(arr[i]))
                        .show();
            });
        }).start();
    }

    private void chooseProfile() {
        final List<BuildRequest> list = ProfileStore.load(this);
        if (list.isEmpty()) {
            Toast.makeText(this, "Nenhum perfil salvo.", Toast.LENGTH_SHORT).show();
            return;
        }
        String[] names = new String[list.size()];
        for (int i = 0; i < names.length; i++) names[i] = list.get(i).name;
        new MaterialAlertDialogBuilder(this)
                .setTitle("Carregar perfil")
                .setItems(names, (d, i) -> fromRequest(list.get(i)))
                .show();
    }

    private void saveProfile() {
        final EditText input = new EditText(this);
        input.setInputType(InputType.TYPE_CLASS_TEXT);
        input.setHint("Nome do perfil");
        BuildRequest cur = toRequest();
        input.setText(cur.sourceLabel().replaceAll(".*/", "").replaceAll("\\.zip$", ""));
        new MaterialAlertDialogBuilder(this)
                .setTitle("Salvar perfil")
                .setView(input)
                .setPositiveButton("Salvar", (d, w) -> {
                    String name = input.getText().toString().trim();
                    if (name.isEmpty()) return;
                    BuildRequest r = toRequest();
                    r.name = name;
                    List<BuildRequest> list = ProfileStore.load(this);
                    list.removeIf(p -> p.name.equalsIgnoreCase(name));
                    list.add(r);
                    ProfileStore.save(this, list);
                    Toast.makeText(this, "Perfil salvo", Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton("Cancelar", null)
                .show();
    }

    private void deleteProfile() {
        final List<BuildRequest> list = ProfileStore.load(this);
        if (list.isEmpty()) return;
        String[] names = new String[list.size()];
        for (int i = 0; i < names.length; i++) names[i] = list.get(i).name;
        new MaterialAlertDialogBuilder(this)
                .setTitle("Excluir qual perfil?")
                .setItems(names, (d, i) -> {
                    list.remove(i);
                    ProfileStore.save(this, list);
                    Toast.makeText(this, "Perfil excluído", Toast.LENGTH_SHORT).show();
                })
                .show();
    }

    @Override
    public void onJobChanged(JobManager.Job job) {
        if (job == null) {
            resultCard.setVisibility(View.GONE);
            btnBuild.setEnabled(true);
            btnCancel.setVisibility(View.GONE);
            return;
        }
        resultCard.setVisibility(View.VISIBLE);
        JobSnapshot s = job.snapshot;
        boolean running = !job.finished;
        btnBuild.setEnabled(!running);
        btnResume.setEnabled(!running);
        btnCancel.setVisibility(running ? View.VISIBLE : View.GONE);
        progress.setVisibility(running ? View.VISIBLE : View.GONE);
        if (running) {
            if (s.total > 0) {
                progress.setIndeterminate(false);
                progress.setProgress(Math.max(1, s.step) * 100 / s.total);
            } else {
                progress.setIndeterminate(true);
            }
            tvStatus.setText(job.launching ? "Enviando ao Termux…" : "⟳ " + (s.stepName.isEmpty() ? "Executando…" : s.stepName));
            tvStatus.setTextColor(getColor(R.color.status_running));
            handler.removeCallbacks(ticker);
            handler.post(ticker);
        } else {
            handler.removeCallbacks(ticker);
            boolean ok = s.state == JobSnapshot.State.SUCCESS;
            tvStatus.setText(ok ? (job.isBuild ? "✓ Build concluído" : "✓ Comando concluído")
                    : s.state == JobSnapshot.State.CANCELLED ? "⊘ Cancelado" : "✗ O build falhou");
            tvStatus.setTextColor(getColor(ok ? R.color.status_success : R.color.status_failure));
            tvStep.setText(String.format(Locale.US, "Duração: %s", formatElapsed(System.currentTimeMillis() - job.startedAt)));
        }
        if (running) updateElapsed(job);

        if (!s.artifacts.isEmpty()) {
            StringBuilder sb = new StringBuilder("Arquivos gerados:\n");
            for (String a : s.artifacts) sb.append("• ").append(a).append('\n');
            sb.append("\nAbra o gerenciador de arquivos em Downloads para instalar.");
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
            TextView t = ui.hint("Pronto. Toque abaixo para compilar de novo.");
            diagHolder.addView(t);
            MaterialButton again = ui.button("Compilar de novo");
            again.setOnClickListener(v -> startBuild());
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
        c.title.setTextColor(getColor(error ? R.color.error : R.color.accent_orange));
        c.title.setTextSize(15);
        c.sub.setTextColor(getColor(R.color.text_primary));
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
            cp.setOnClickListener(v -> Ui.copy(this, "comando", d.copyText));
            c.body.addView(cp);
        }
        ((LinearLayout.LayoutParams) c.view.getLayoutParams()).bottomMargin = ui.dp(10);
        return c.view;
    }

    private void confirmFix(Diagnosis d) {
        new MaterialAlertDialogBuilder(this)
                .setTitle(d.fixLabel)
                .setMessage("Isto vai executar no Termux:\n\n" + d.fixCommand)
                .setPositiveButton("Executar", (dlg, w) -> {
                    String id = "f" + System.currentTimeMillis();
                    String script = ScriptBuilder.commandJobScript(id, d.fixLabel, d.fixCommand);
                    lastLogShown = "";
                    BuildService.startJob(this, id, d.fixLabel, script, "", false, etSdk.get());
                })
                .setNegativeButton("Cancelar", null)
                .show();
    }

    private static String prettyLog(String log) {
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
