package com.wanderstest3.termuxjavautils.ui.termux;

import android.os.Bundle;
import android.widget.RadioGroup;
import android.widget.TextView;
import com.google.android.material.button.MaterialButton;
import com.wanderstest3.termuxjavautils.R;
import com.wanderstest3.termuxjavautils.build.ScriptBuilder;
import com.wanderstest3.termuxjavautils.termux.TermuxBridge;
import com.wanderstest3.termuxjavautils.ui.common.BaseActivity;
import com.wanderstest3.termuxjavautils.ui.common.Ui;

public class TermuxActivity extends BaseActivity {

    private static final int ID_APK = 7001;
    private static final int ID_JAR = 7002;
    private static final int ID_CUSTOM = 7003;

    private Ui.Input etPath;
    private Ui.Input etCmd;
    private RadioGroup rg;
    private TextView tvStatus;

    @Override
    protected String screenTitle() {
        return "Comandos no Termux";
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        content.addView(ui.hint("Abre o Termux e roda um comando Gradle na pasta do projeto, com a sessão visível "
                + "para você acompanhar. Para compilar sem olhar o Termux, use Compilar projeto."));

        Ui.Card c = ui.card("Projeto", "Para o app avisar e explicar erros automaticamente, prefira Compilar projeto.");
        etPath = ui.input("Pasta do projeto no Termux", "~ é a pasta inicial do Termux.", "~/projects/MeuApp");
        rg = ui.radioGroup();
        rg.addView(ui.radio("APK release (./gradlew assembleRelease)", ID_APK));
        rg.addView(ui.radio("JAR / mod (./gradlew build)", ID_JAR));
        rg.addView(ui.radio("Comando personalizado", ID_CUSTOM));
        rg.check(ID_APK);
        etCmd = ui.input("Comando personalizado", "Só usado na opção personalizada.", "./gradlew assembleDebug");
        MaterialButton run = ui.button("Executar no Termux");
        run.setOnClickListener(v -> run());
        tvStatus = ui.hint("");
        c.body.addView(etPath.layout);
        c.body.addView(rg);
        c.body.addView(etCmd.layout);
        c.body.addView(run);
        c.body.addView(tvStatus);
        content.addView(c.view);
    }

    private void run() {
        String path = etPath.get();
        if (path.isEmpty()) {
            etPath.layout.setError("Informe a pasta do projeto");
            return;
        }
        etPath.layout.setError(null);
        if (!TermuxBridge.isInstalled(this)) {
            tvStatus.setText("✗ " + getString(R.string.termux_not_installed));
            return;
        }
        if (!TermuxBridge.hasPermission(this)) {
            tvStatus.setText("✗ Falta a permissão de executar comandos no Termux. Abra Preparar Termux.");
            return;
        }
        int id = rg.getCheckedRadioButtonId();
        String cmd = id == ID_JAR ? "./gradlew build"
                : id == ID_CUSTOM && !etCmd.get().isEmpty() ? etCmd.get() : "./gradlew assembleRelease";
        String full = "cd " + dir(path) + " && " + cmd + "; echo; echo \"[fim] código de saída: $?\"";
        try {
            TermuxBridge.run(this, full, false, "Termux Java Utils", null);
            tvStatus.setText("✓ Comando enviado. O Termux foi aberto para mostrar a execução.");
        } catch (Exception e) {
            tvStatus.setText("✗ " + e.getMessage()
                    + "\nConfirme a permissão e allow-external-apps em Preparar Termux.");
        }
    }

    private static String dir(String p) {
        String q = p.startsWith("~") ? "$HOME" + p.substring(1) : p;
        return "\"" + q.replace("\"", "\\\"") + "\"";
    }
}
