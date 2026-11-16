package com.wanderstest3.termuxjavautils.ui.scripts;

import android.os.Bundle;
import android.view.View;
import android.widget.RadioGroup;
import android.widget.TextView;
import com.google.android.material.button.MaterialButton;
import com.wanderstest3.termuxjavautils.termux.TermuxBridge;
import com.wanderstest3.termuxjavautils.ui.common.BaseActivity;
import com.wanderstest3.termuxjavautils.ui.common.Ui;

public class ScriptsActivity extends BaseActivity {

    private static final int ID_APK = 8001;
    private static final int ID_JAR = 8002;
    private static final int ID_CUSTOM = 8003;

    private Ui.Input etName;
    private Ui.Input etPath;
    private RadioGroup rg;
    private Ui.Card outputCard;
    private TextView tvOutput;
    private TextView tvStatus;
    private String script = "";

    @Override
    protected String screenTitle() {
        return "Gerador de scripts";
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        content.addView(ui.hint("Gera um script bash pronto para o Termux (com Java configurado e aviso de erro). "
                + "Copie e cole no Termux ou envie direto."));

        Ui.Card c = ui.card("Configuração", null);
        etName = ui.input("Nome do projeto", "Só para identificar o script.", "MeuApp");
        etPath = ui.input("Pasta no Termux", "~ é a pasta inicial do Termux.", "~/projects/MeuApp");
        rg = ui.radioGroup();
        rg.addView(ui.radio("APK Release", ID_APK));
        rg.addView(ui.radio("JAR / mod", ID_JAR));
        rg.addView(ui.radio("Personalizado (editável depois de copiar)", ID_CUSTOM));
        rg.check(ID_APK);
        MaterialButton gen = ui.button("Gerar script");
        gen.setOnClickListener(v -> generate());
        c.body.addView(etName.layout);
        c.body.addView(etPath.layout);
        c.body.addView(rg);
        c.body.addView(gen);
        content.addView(c.view);

        outputCard = ui.card("Script gerado", null);
        tvOutput = ui.mono("");
        MaterialButton copy = ui.outlined("Copiar");
        MaterialButton send = ui.button("Enviar ao Termux");
        tvStatus = ui.hint("");
        copy.setOnClickListener(v -> Ui.copy(this, "build_script", script));
        send.setOnClickListener(v -> send());
        outputCard.body.addView(tvOutput);
        outputCard.body.addView(copy);
        outputCard.body.addView(send);
        outputCard.body.addView(tvStatus);
        outputCard.view.setVisibility(View.GONE);
        content.addView(outputCard.view);
    }

    private void generate() {
        String name = etName.get();
        if (name.isEmpty()) {
            etName.layout.setError("Informe o nome do projeto");
            return;
        }
        etName.layout.setError(null);
        String path = etPath.get();
        int id = rg.getCheckedRadioButtonId();
        StringBuilder sb = new StringBuilder();
        sb.append("#!/data/data/com.termux/files/usr/bin/bash\n");
        sb.append("set -e\n");
        sb.append("export JAVA_HOME=\"$PREFIX/lib/jvm/java-17-openjdk\"\n");
        sb.append("export PATH=\"$JAVA_HOME/bin:$PATH\"\n");
        sb.append("export ANDROID_HOME=\"$HOME/android-sdk\"\n");
        sb.append("export ANDROID_SDK_ROOT=\"$ANDROID_HOME\"\n");
        if (!path.isEmpty()) {
            String p = path.startsWith("~") ? "$HOME" + path.substring(1) : path;
            sb.append("cd \"").append(p).append("\"\n");
        }
        sb.append("chmod +x gradlew\n");
        sb.append("echo \"[BUILD] ").append(name.replace("\"", "")).append("\"\n");
        if (id == ID_JAR) {
            sb.append("./gradlew build --no-daemon\n");
            sb.append("find . -path '*/build/libs/*.jar' | head -5\n");
        } else if (id == ID_CUSTOM) {
            sb.append("./gradlew build --no-daemon\n");
        } else {
            sb.append("./gradlew assembleRelease --no-daemon -Pandroid.aapt2FromMavenOverride=\"$PREFIX/bin/aapt2\"\n");
            sb.append("find . -path '*/build/outputs/apk/release/*.apk' | head -5\n");
        }
        sb.append("echo \"[BUILD] Concluído.\"\n");
        script = sb.toString();
        tvOutput.setText(script);
        outputCard.view.setVisibility(View.VISIBLE);
    }

    private void send() {
        if (script.isEmpty()) return;
        if (!TermuxBridge.isInstalled(this)) {
            tvStatus.setText("✗ Termux não está instalado.");
            return;
        }
        if (!TermuxBridge.hasPermission(this)) {
            tvStatus.setText("✗ Falta a permissão de executar comandos. Abra Preparar Termux.");
            return;
        }
        try {
            String b64 = java.util.Base64.getEncoder().encodeToString(
                    script.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            TermuxBridge.run(this, "echo " + b64 + " | base64 -d | bash", false, "Script de build", null);
            tvStatus.setText("✓ Script enviado ao Termux.");
        } catch (Exception e) {
            tvStatus.setText("✗ " + e.getMessage());
        }
    }
}
