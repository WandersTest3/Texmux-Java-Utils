package com.wanderstest3.termuxjavautils.ui.decompile;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.RadioGroup;
import android.widget.Toast;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.switchmaterial.SwitchMaterial;
import com.wanderstest3.termuxjavautils.R;
import com.wanderstest3.termuxjavautils.build.DecompileRequest;
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
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;

public class DecompileActivity extends BaseActivity implements JobManager.Listener {

    public static final String EXTRA_INPUT = "input_path";

    private static final int ID_JAVA = 9101;
    private static final int ID_SMALI = 9102;
    private static final int ID_BOTH = 9103;
    private static final int ID_DEX2JAR = 9104;
    private static final int ID_EXTRACT = 9105;
    private static final int ID_DOTNET = 9106;

    private LinearLayout warnHolder;
    private Ui.Input etInput;
    private RadioGroup rgMode;
    private SwitchMaterial swDeobf;
    private SwitchMaterial swFolder;
    private Ui.Input etJava;
    private Ui.Input etDownload;
    private Ui.Input etOutDir;
    private Ui.Input etWork;
    private LinearLayout advanced;
    private MaterialButton btnAdvanced;
    private MaterialButton btnRun;
    private MaterialButton btnCancel;
    private MaterialButton btnResume;
    private JobPanel panel;
    private FilePicker picker;

    @Override
    protected String screenTitle() {
        return "Descompilar";
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        buildUi();
        load();
        handleIntent(getIntent());
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        handleIntent(intent);
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
        save();
    }

    private void handleIntent(Intent i) {
        if (i == null) return;
        String path = i.getStringExtra(EXTRA_INPUT);
        if (path != null && !path.isEmpty()) {
            setInput(path);
            i.removeExtra(EXTRA_INPUT);
        }
    }

    private void buildUi() {
        content.addView(ui.hint("Abre um APK, APKM, XAPK, APKS, JAR (inclusive mods), AAR, DEX ou ZIP e devolve o código-fonte para ler. "
                + "Tudo roda no Termux em segundo plano; o app instala o que faltar sozinho (jadx, apktool, dex2jar) "
                + "e avisa quando terminar. Só funciona com arquivos que você tem direito de analisar."));
        warnHolder = ui.column();
        content.addView(warnHolder);

        Ui.Card file = ui.card("1. Arquivo",
                "Escolha o arquivo na memória do celular. O app o traz para o Termux sozinho. "
                        + "Você também pode compartilhar um APK/JAR/ZIP de qualquer gerenciador de arquivos para este app.");
        picker = new FilePicker(this, (path, name) -> setInput(path));
        MaterialButton btnPick = ui.button("Escolher arquivo (APK, APKM, XAPK, JAR, AAR, DEX, ZIP)");
        btnPick.setOnClickListener(v -> picker.pick("application/vnd.android.package-archive",
                "application/java-archive", "application/zip", "application/x-zip-compressed",
                "application/octet-stream"));
        file.body.addView(btnPick);
        etInput = ui.input("Arquivo escolhido (ou digite o nome)",
                "Aceita o caminho completo ou só o nome de um arquivo em Downloads.", "app-release.apk");
        file.body.addView(etInput.layout);
        MaterialButton btnList = ui.outlined("Listar arquivos da pasta Downloads");
        btnList.setOnClickListener(v -> listFiles());
        file.body.addView(btnList);
        content.addView(file.view);

        Ui.Card what = ui.card("2. O que gerar",
                "Java é o mais legível. Smali é o código de baixo nível do Android, útil para editar e reempacotar. "
                        + "O resultado vai para Downloads.");
        rgMode = ui.radioGroup();
        rgMode.addView(ui.radio("Código Java (jadx) — recomendado", ID_JAVA));
        rgMode.addView(ui.radio("Smali + recursos decodificados (apktool) — só APK", ID_SMALI));
        rgMode.addView(ui.radio("Java + Smali — só APK", ID_BOTH));
        rgMode.addView(ui.radio("Converter para JAR (dex2jar) — APK ou DEX", ID_DEX2JAR));
        rgMode.addView(ui.radio("Assemblies .NET (Xamarin/Mono) — extrai as DLLs do APK", ID_DOTNET));
        rgMode.addView(ui.radio("Só extrair o conteúdo (como um ZIP)", ID_EXTRACT));
        what.body.addView(rgMode);
        swDeobf = ui.toggle("Desofuscar nomes (jadx) — para apps com nomes como a.b.c", false);
        swFolder = ui.toggle("Salvar como pasta em vez de ZIP (mais lento)", false);
        what.body.addView(swDeobf);
        what.body.addView(swFolder);
        content.addView(what.view);

        Ui.Card adv = ui.card("Opções avançadas", "Só mexa aqui se o padrão não servir.");
        btnAdvanced = ui.outlined("Mostrar opções avançadas");
        advanced = ui.column();
        advanced.setVisibility(View.GONE);
        etJava = ui.input("Java home (opcional)", "Vazio = detecta o OpenJDK do Termux.",
                "$PREFIX/lib/jvm/java-21-openjdk");
        etDownload = ui.input("Pasta dos arquivos", "Onde nomes simples são procurados.", "/storage/emulated/0/Download");
        etOutDir = ui.input("Pasta de saída", "", "/storage/emulated/0/Download");
        etWork = ui.input("Pasta de trabalho no Termux", "Guarda uma cópia do resultado.", "$HOME/decompiled");
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

        btnRun = ui.button("Descompilar agora");
        btnRun.setOnClickListener(v -> start());
        btnCancel = ui.outlined("Cancelar");
        btnCancel.setVisibility(View.GONE);
        btnCancel.setOnClickListener(v -> new MaterialAlertDialogBuilder(this)
                .setTitle("Cancelar?")
                .setMessage("O processo será interrompido no Termux.")
                .setPositiveButton("Cancelar", (d, w) -> BuildService.cancel(this))
                .setNegativeButton("Continuar", null)
                .show());
        btnResume = ui.outlined("Retomar último trabalho");
        btnResume.setOnClickListener(v -> BuildService.attachLast(this, ""));
        content.addView(btnRun);
        content.addView(btnCancel);
        content.addView(btnResume);

        panel = new JobPanel(this, j -> JobManager.KIND_DECOMPILE.equals(j.kind), "Descompilar de novo",
                new JobPanel.Host() {
                    @Override
                    public void onRunningChanged(boolean running) {
                        btnRun.setEnabled(!running);
                        btnResume.setEnabled(!running);
                        btnCancel.setVisibility(running ? View.VISIBLE : View.GONE);
                    }

                    @Override
                    public String sdkDir() {
                        return "";
                    }

                    @Override
                    public void runAgain() {
                        start();
                    }
                });
        content.addView(panel.view());
    }

    private void setInput(String path) {
        etInput.set(path);
        etInput.layout.setError(null);
        String ext = DecompileRequest.extensionOf(path);
        int checked = rgMode.getCheckedRadioButtonId();
        boolean apkLike = DecompileRequest.isApkOrBundle(path);
        if (!apkLike && (checked == ID_SMALI || checked == ID_BOTH || checked == ID_DOTNET)) rgMode.check(ID_JAVA);
        if (!apkLike && !"dex".equals(ext) && checked == ID_DEX2JAR) rgMode.check(ID_JAVA);
        if (!DecompileRequest.isSupported(path)) {
            etInput.layout.setError("Formato não suportado. Use APK, APKM, XAPK, APKS, JAR, AAR, DEX, CLASS ou ZIP.");
        }
    }

    private String modeOf(int id) {
        return id == ID_SMALI ? DecompileRequest.MODE_SMALI
                : id == ID_BOTH ? DecompileRequest.MODE_BOTH
                : id == ID_DEX2JAR ? DecompileRequest.MODE_DEX2JAR
                : id == ID_DOTNET ? DecompileRequest.MODE_DOTNET
                : id == ID_EXTRACT ? DecompileRequest.MODE_EXTRACT : DecompileRequest.MODE_JAVA;
    }

    private int idOf(String mode) {
        return DecompileRequest.MODE_SMALI.equals(mode) ? ID_SMALI
                : DecompileRequest.MODE_BOTH.equals(mode) ? ID_BOTH
                : DecompileRequest.MODE_DEX2JAR.equals(mode) ? ID_DEX2JAR
                : DecompileRequest.MODE_DOTNET.equals(mode) ? ID_DOTNET
                : DecompileRequest.MODE_EXTRACT.equals(mode) ? ID_EXTRACT : ID_JAVA;
    }

    private DecompileRequest toRequest() {
        DecompileRequest r = new DecompileRequest();
        r.input = etInput.get();
        r.mode = modeOf(rgMode.getCheckedRadioButtonId());
        r.deobf = swDeobf.isChecked();
        r.asFolder = swFolder.isChecked();
        r.javaHome = etJava.get();
        r.downloadDir = etDownload.get();
        r.outputDir = etOutDir.get();
        r.workDir = etWork.get();
        return r;
    }

    private void save() {
        DecompileRequest r = toRequest();
        SharedPreferences.Editor e = Prefs.get(this).edit();
        e.putString(Prefs.KEY_DECOMPILE + "_input", r.input);
        e.putString(Prefs.KEY_DECOMPILE + "_mode", r.mode);
        e.putBoolean(Prefs.KEY_DECOMPILE + "_deobf", r.deobf);
        e.putBoolean(Prefs.KEY_DECOMPILE + "_folder", r.asFolder);
        e.putString(Prefs.KEY_DECOMPILE + "_java", r.javaHome);
        e.putString(Prefs.KEY_DECOMPILE + "_dl", r.downloadDir);
        e.putString(Prefs.KEY_DECOMPILE + "_out", r.outputDir);
        e.putString(Prefs.KEY_DECOMPILE + "_work", r.workDir);
        e.apply();
    }

    private void load() {
        SharedPreferences p = Prefs.get(this);
        DecompileRequest d = new DecompileRequest();
        etInput.set(p.getString(Prefs.KEY_DECOMPILE + "_input", ""));
        rgMode.check(idOf(p.getString(Prefs.KEY_DECOMPILE + "_mode", DecompileRequest.MODE_JAVA)));
        swDeobf.setChecked(p.getBoolean(Prefs.KEY_DECOMPILE + "_deobf", false));
        swFolder.setChecked(p.getBoolean(Prefs.KEY_DECOMPILE + "_folder", false));
        etJava.set(p.getString(Prefs.KEY_DECOMPILE + "_java", ""));
        etDownload.set(p.getString(Prefs.KEY_DECOMPILE + "_dl", d.downloadDir));
        etOutDir.set(p.getString(Prefs.KEY_DECOMPILE + "_out", d.outputDir));
        etWork.set(p.getString(Prefs.KEY_DECOMPILE + "_work", d.workDir));
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
        Ui.Card c = ui.card("Antes de descompilar", msg);
        MaterialButton b = ui.button("Abrir Preparar Termux");
        b.setOnClickListener(v -> startActivity(new Intent(this, SetupActivity.class)));
        c.body.addView(b);
        warnHolder.addView(c.view);
    }

    private String readAsset(String name) throws Exception {
        try (BufferedReader br = new BufferedReader(new InputStreamReader(getAssets().open(name), "UTF-8"))) {
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = br.readLine()) != null) sb.append(line).append('\n');
            return sb.toString();
        }
    }

    private String loadBody() throws Exception {
        return ScriptBuilder.embedDotnetExtractor(readAsset("tju_decompile.sh"), readAsset("tju_dotnet.py"));
    }

    private void start() {
        if (JobManager.isRunning()) {
            Toast.makeText(this, "Já existe um trabalho em andamento.", Toast.LENGTH_SHORT).show();
            return;
        }
        DecompileRequest r = toRequest();
        if (r.input.isEmpty()) {
            etInput.layout.setError("Escolha um arquivo com o botão acima");
            return;
        }
        if (!DecompileRequest.isSupported(r.input)) {
            etInput.layout.setError("Formato não suportado. Use APK, APKM, XAPK, APKS, JAR, AAR, DEX, CLASS ou ZIP.");
            return;
        }
        etInput.layout.setError(null);
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
        try {
            String id = "d" + System.currentTimeMillis();
            String script = ScriptBuilder.decompileScript(r, id, loadBody());
            String title = r.input.substring(Math.max(r.input.lastIndexOf('/') + 1, 0));
            panel.reset();
            save();
            BuildService.startJob(this, id, title, script, "", JobManager.KIND_DECOMPILE, "");
            Toast.makeText(this, "Enviado ao Termux. Você pode sair do app.", Toast.LENGTH_LONG).show();
        } catch (Exception e) {
            Toast.makeText(this, "Erro: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private void listFiles() {
        if (!TermuxBridge.isInstalled(this) || !TermuxBridge.hasPermission(this)) {
            Toast.makeText(this, "Prepare o Termux primeiro (permissão).", Toast.LENGTH_LONG).show();
            return;
        }
        final String cmd = ScriptBuilder.listFilesCommand(etDownload.get(), "apk", "apkm", "xapk", "apks", "jar", "aar", "dex", "zip");
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
                            .setTitle("Nenhum arquivo encontrado")
                            .setMessage("Não há APK/APKM/XAPK/JAR/AAR/DEX/ZIP na pasta ou o Termux não tem acesso a ela. No Termux execute: termux-setup-storage")
                            .setPositiveButton("OK", null)
                            .show();
                    return;
                }
                final String[] arr = names.toArray(new String[0]);
                new MaterialAlertDialogBuilder(this)
                        .setTitle("Downloads (mais recentes primeiro)")
                        .setItems(arr, (d, i) -> setInput(arr[i]))
                        .show();
            });
        }).start();
    }

    @Override
    public void onJobChanged(JobManager.Job job) {
        panel.onJobChanged(job);
    }
}
