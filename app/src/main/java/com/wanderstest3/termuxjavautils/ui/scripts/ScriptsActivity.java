package com.wanderstest3.termuxjavautils.ui.scripts;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.view.View;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import com.wanderstest3.termuxjavautils.R;
import com.wanderstest3.termuxjavautils.databinding.ActivityScriptsBinding;

public class ScriptsActivity extends AppCompatActivity {

    private static final String TERMUX_PACKAGE = "com.termux";
    private static final String TERMUX_RUN_COMMAND = "com.termux.RUN_COMMAND";

    private ActivityScriptsBinding binding;
    private String generatedScript = "";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityScriptsBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        binding.btnBack.setOnClickListener(v -> finish());
        binding.btnGenerate.setOnClickListener(v -> generateScript());
        binding.btnCopy.setOnClickListener(v -> copyScript());
        binding.btnSendTermux.setOnClickListener(v -> sendToTermux());
    }

    private void generateScript() {
        String projectName = binding.etProjectName.getText() != null
                ? binding.etProjectName.getText().toString().trim() : "";
        String projectPath = binding.etProjectPath.getText() != null
                ? binding.etProjectPath.getText().toString().trim() : "";

        if (projectName.isEmpty()) {
            Toast.makeText(this, R.string.scripts_name_empty, Toast.LENGTH_SHORT).show();
            return;
        }

        int checkedId = binding.rgScriptType.getCheckedRadioButtonId();
        generatedScript = buildScript(checkedId, projectName, projectPath);

        binding.tvScriptOutput.setText(generatedScript);
        binding.cardOutput.setVisibility(View.VISIBLE);
    }

    private String buildScript(int typeId, String name, String path) {
        String cdPart = path.isEmpty() ? "" : "cd \"" + path + "\"\n";

        if (typeId == R.id.rb_apk) {
            return "#!/bin/bash\n"
                    + "# Build APK — " + name + "\n"
                    + "set -e\n"
                    + cdPart
                    + "echo \"[BUILD] Limpando projeto…\"\n"
                    + "./gradlew clean\n"
                    + "echo \"[BUILD] Compilando APK release…\"\n"
                    + "./gradlew assembleRelease\n"
                    + "echo \"[BUILD] Concluído.\"\n"
                    + "find . -name \"*.apk\" -path \"*/release/*\" | head -5\n";
        }

        if (typeId == R.id.rb_jar) {
            return "#!/bin/bash\n"
                    + "# Build JAR/Mod — " + name + "\n"
                    + "set -e\n"
                    + cdPart
                    + "echo \"[BUILD] Limpando projeto…\"\n"
                    + "./gradlew clean\n"
                    + "echo \"[BUILD] Compilando mod…\"\n"
                    + "./gradlew build\n"
                    + "echo \"[BUILD] Concluído.\"\n"
                    + "find . -name \"*.jar\" -path \"*/build/libs/*\" | head -5\n";
        }

        return "#!/bin/bash\n"
                + "# Build personalizado — " + name + "\n"
                + "set -e\n"
                + cdPart
                + "echo \"[BUILD] Iniciando…\"\n"
                + "# Insira seu comando aqui\n"
                + "./gradlew build\n"
                + "echo \"[BUILD] Concluído.\"\n";
    }

    private void copyScript() {
        if (generatedScript.isEmpty()) return;
        ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        ClipData clip = ClipData.newPlainText("build_script", generatedScript);
        clipboard.setPrimaryClip(clip);
        Toast.makeText(this, R.string.scripts_copied, Toast.LENGTH_SHORT).show();
    }

    private void sendToTermux() {
        if (generatedScript.isEmpty()) return;

        if (!isTermuxInstalled()) {
            Toast.makeText(this, R.string.termux_not_installed, Toast.LENGTH_SHORT).show();
            return;
        }

        Intent intent = new Intent(TERMUX_RUN_COMMAND);
        intent.setPackage(TERMUX_PACKAGE);
        intent.putExtra("com.termux.RUN_COMMAND_PATH",
                "/data/data/com.termux/files/usr/bin/bash");
        intent.putExtra("com.termux.RUN_COMMAND_ARGUMENTS",
                new String[]{"-c", generatedScript});
        intent.putExtra("com.termux.RUN_COMMAND_BACKGROUND", false);

        try {
            startActivity(intent);
            Toast.makeText(this, R.string.scripts_sent, Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            Toast.makeText(this, "Erro: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private boolean isTermuxInstalled() {
        try {
            getPackageManager().getPackageInfo(TERMUX_PACKAGE, 0);
            return true;
        } catch (PackageManager.NameNotFoundException e) {
            return false;
        }
    }
}
