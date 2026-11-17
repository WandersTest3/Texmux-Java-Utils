package com.wanderstest3.termuxjavautils;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import com.google.android.material.button.MaterialButton;
import com.wanderstest3.termuxjavautils.job.JobManager;
import com.wanderstest3.termuxjavautils.termux.TermuxBridge;
import com.wanderstest3.termuxjavautils.ui.build.BuildActivity;
import com.wanderstest3.termuxjavautils.ui.cicd.CicdActivity;
import com.wanderstest3.termuxjavautils.ui.common.BaseActivity;
import com.wanderstest3.termuxjavautils.ui.common.Ui;
import com.wanderstest3.termuxjavautils.ui.decompile.DecompileActivity;
import com.wanderstest3.termuxjavautils.ui.scripts.ScriptsActivity;
import com.wanderstest3.termuxjavautils.ui.setup.SetupActivity;
import com.wanderstest3.termuxjavautils.ui.termux.TermuxActivity;

public class MainActivity extends BaseActivity {

    private android.widget.LinearLayout statusHolder;

    @Override
    protected String screenTitle() {
        return "Termux Java Utils";
    }

    @Override
    protected boolean showBack() {
        return false;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        content.addView(ui.hint("Compile apps Android e mods Java direto do celular. "
                + "Escolha um ZIP no celular ou cole o link de um repositório e o app faz o resto, "
                + "avisando quando terminar e explicando qualquer erro."));
        statusHolder = ui.column();
        content.addView(statusHolder);

        addCard("Compilar projeto",
                "O principal. Escolha um ZIP em Downloads ou cole o link do repositório. O app baixa, compila no Termux em segundo plano, copia o APK/JAR para Downloads e, se falhar, diz exatamente por quê.",
                "Compilar", BuildActivity.class, true);
        addCard("Descompilar",
                "Abre APK, JAR (mods), AAR, DEX ou ZIP e gera o código Java ou Smali para você ler. Escolha o arquivo na memória do celular; o app instala jadx/apktool no Termux se precisar.",
                "Descompilar", DecompileActivity.class, false);
        addCard("Preparar Termux",
                "Configuração de uma vez só: libera a comunicação com o Termux e instala Java, unzip, git e aapt2 automaticamente.",
                "Preparar", SetupActivity.class, false);
        addCard("GitHub Actions (nuvem)",
                "Compila no servidor do GitHub: dispare o workflow, acompanhe o status e baixe o APK sem gastar bateria.",
                "Abrir", CicdActivity.class, false);
        addCard("Comandos no Termux",
                "Executa um comando Gradle em uma pasta do Termux, com a sessão visível.",
                "Abrir", TermuxActivity.class, false);
        addCard("Gerador de scripts",
                "Cria scripts de build prontos para copiar ou enviar ao Termux.",
                "Abrir", ScriptsActivity.class, false);
    }

    @Override
    protected void onResume() {
        super.onResume();
        statusHolder.removeAllViews();
        String msg = null;
        if (!TermuxBridge.isInstalled(this)) {
            msg = "Termux não encontrado. Toque em Preparar Termux.";
        } else if (!TermuxBridge.hasPermission(this)) {
            msg = "Falta conceder a permissão para executar comandos no Termux. Toque em Preparar Termux.";
        } else if (JobManager.isRunning()) {
            msg = "Há um build em andamento. Toque em Compilar projeto para acompanhar.";
        }
        if (msg != null) {
            Ui.Card c = ui.card("Atenção", msg);
            c.title.setTextColor(getColor(R.color.accent_orange));
            statusHolder.addView(c.view);
        }
    }

    private void addCard(String title, String desc, String button, Class<?> target, boolean primary) {
        Ui.Card c = ui.card(title, desc);
        MaterialButton b = primary ? ui.button(button) : ui.outlined(button);
        b.setOnClickListener((View v) -> startActivity(new Intent(this, target)));
        c.body.addView(b);
        content.addView(c.view);
    }
}
