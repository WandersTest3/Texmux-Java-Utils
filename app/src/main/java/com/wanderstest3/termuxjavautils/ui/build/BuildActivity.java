package com.wanderstest3.termuxjavautils.ui.build;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
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
import com.wanderstest3.termuxjavautils.ui.common.FilePicker;
import com.wanderstest3.termuxjavautils.ui.common.JobPanel;
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

    private JobPanel panel;
    private FilePicker zipPicker;
    private FilePicker signPicker;

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
        panel.stop();
        ProfileStore.saveLast(this, toRequest());
    }

    private void buildUi() {
        content.addView(ui.hint("Escolha o ZIP do projeto (ou cole o link do repositório), toque em Compilar e pronto. "
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
                "Escolha o ZIP do projeto direto da memória do celular (o app o traz para o Termux) ou cole o link de um repositório.");
        rgSource = ui.radioGroup();
        rgSource.addView(ui.radio("ZIP do projeto (escolher arquivo)", ID_ZIP));
        rgSource.addView(ui.radio("Link do repositório (baixa e compila sozinho)", ID_GIT));
        rgSource.setOnCheckedChangeListener((g, id) -> updateSourceVisibility());
        source.body.addView(rgSource);

        groupZip = ui.column();
        zipPicker = new FilePicker(this, (path, name) -> {
            etZip.set(path);
            etZip.layout.setError(null);
        });
        MaterialButton btnPickZip = ui.button("Escolher ZIP no celular");
        btnPickZip.setOnClickListener(v -> zipPicker.pick("application/zip", "application/x-zip-compressed",
                "application/octet-stream"));
        groupZip.addView(btnPickZip);
        etZip = ui.input("ZIP escolhido (ou digite o nome)",
                "O botão acima preenche com o caminho. Também aceita só o nome do arquivo em Downloads.",
                "Pojarush_Test3-main.zip");
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
        signPicker = new FilePicker(this, (path, name) -> etSignZip.set(path));
        MaterialButton btnPickSign = ui.outlined("Escolher ZIP de assinatura no celular");
        btnPickSign.setOnClickListener(v -> signPicker.pick("application/zip", "application/x-zip-compressed",
                "application/octet-stream"));
        sign.body.addView(btnPickSign);
        etSignZip = ui.input("ZIP de assinatura (opcional)",
                "Só preencha se o nome for diferente do padrão ou se escolheu pelo botão.", "Pojarush_Test3-assinatura.zip");
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

        panel = new JobPanel(this, j -> !JobManager.KIND_DECOMPILE.equals(j.kind), "Compilar de novo",
                new JobPanel.Host() {
                    @Override
                    public void onRunningChanged(boolean running) {
                        btnBuild.setEnabled(!running);
                        btnResume.setEnabled(!running);
                        btnCancel.setVisibility(running ? View.VISIBLE : View.GONE);
                    }

                    @Override
                    public String sdkDir() {
                        return etSdk.get();
                    }

                    @Override
                    public void runAgain() {
                        startBuild();
                    }
                });
        content.addView(panel.view());
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
            etZip.layout.setError("Escolha o ZIP com o botão ou informe o nome");
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
            panel.reset();
            BuildService.startJob(this, id, title, script, token, JobManager.KIND_BUILD, r.sdkDir);
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
        panel.onJobChanged(job);
    }
}
