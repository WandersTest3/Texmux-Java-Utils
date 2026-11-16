package com.wanderstest3.termuxjavautils.ui.termux;

import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.view.View;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import com.wanderstest3.termuxjavautils.databinding.ActivityTermuxBinding;

public class TermuxActivity extends AppCompatActivity {

    private static final String TERMUX_PACKAGE = "com.termux";
    private static final String TERMUX_RUN_COMMAND = "com.termux.RUN_COMMAND";

    private ActivityTermuxBinding binding;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityTermuxBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        binding.btnBack.setOnClickListener(v -> finish());

        binding.rgBuildType.setOnCheckedChangeListener((group, checkedId) -> {
            binding.etCustomCmd.setText("");
        });

        binding.btnRunTermux.setOnClickListener(v -> runInTermux());
    }

    private void runInTermux() {
        String projectPath = binding.etProjectPath.getText() != null
                ? binding.etProjectPath.getText().toString().trim()
                : "";

        if (projectPath.isEmpty()) {
            showStatus(getString(com.wanderstest3.termuxjavautils.R.string.termux_path_empty));
            return;
        }

        if (!isTermuxInstalled()) {
            showStatus(getString(com.wanderstest3.termuxjavautils.R.string.termux_not_installed));
            return;
        }

        String command = buildCommand(projectPath);
        sendToTermux(command, projectPath);
    }

    private String buildCommand(String projectPath) {
        String customCmd = binding.etCustomCmd.getText() != null
                ? binding.etCustomCmd.getText().toString().trim()
                : "";

        if (!customCmd.isEmpty()) {
            return "cd " + projectPath + " && " + customCmd;
        }

        int checkedId = binding.rgBuildType.getCheckedRadioButtonId();
        if (checkedId == com.wanderstest3.termuxjavautils.R.id.rb_jar) {
            return "cd " + projectPath + " && ./gradlew build";
        }
        return "cd " + projectPath + " && ./gradlew assembleRelease";
    }

    private void sendToTermux(String command, String workDir) {
        Intent intent = new Intent(TERMUX_RUN_COMMAND);
        intent.setPackage(TERMUX_PACKAGE);
        intent.putExtra("com.termux.RUN_COMMAND_PATH", "/data/data/com.termux/files/usr/bin/bash");
        intent.putExtra("com.termux.RUN_COMMAND_ARGUMENTS", new String[]{"-c", command});
        intent.putExtra("com.termux.RUN_COMMAND_WORKDIR", workDir);
        intent.putExtra("com.termux.RUN_COMMAND_BACKGROUND", false);

        try {
            startActivity(intent);
            showStatus(getString(com.wanderstest3.termuxjavautils.R.string.termux_launched));
        } catch (Exception e) {
            showStatus("Erro: " + e.getMessage());
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

    private void showStatus(String message) {
        binding.tvStatus.setText(message);
        binding.tvStatus.setVisibility(View.VISIBLE);
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }
}
