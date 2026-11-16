package com.wanderstest3.termuxjavautils.ui.cicd;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import com.wanderstest3.termuxjavautils.R;
import com.wanderstest3.termuxjavautils.databinding.ActivityCicdBinding;
import com.wanderstest3.termuxjavautils.util.GitHubApiClient;
import org.json.JSONArray;
import org.json.JSONObject;

public class CicdActivity extends AppCompatActivity {

    private static final String PREFS_NAME = "termux_java_utils_prefs";
    private static final String KEY_TOKEN = "github_token";

    private ActivityCicdBinding binding;
    private SharedPreferences prefs;
    private long lastRunId = -1;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityCicdBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);

        String savedToken = prefs.getString(KEY_TOKEN, "");
        if (!savedToken.isEmpty()) {
            binding.etToken.setText(savedToken);
        }

        binding.btnBack.setOnClickListener(v -> finish());
        binding.btnSaveToken.setOnClickListener(v -> saveToken());
        binding.btnTrigger.setOnClickListener(v -> triggerWorkflow());
        binding.btnCheckStatus.setOnClickListener(v -> checkStatus());
        binding.btnDownload.setOnClickListener(v -> downloadArtifact());
    }

    private void saveToken() {
        String token = getToken();
        if (token.isEmpty()) return;
        prefs.edit().putString(KEY_TOKEN, token).apply();
        Toast.makeText(this, R.string.cicd_token_saved, Toast.LENGTH_SHORT).show();
    }

    private void triggerWorkflow() {
        if (!validateFields()) return;

        binding.btnTrigger.setEnabled(false);
        showCardStatus(getString(R.string.cicd_status_idle), null);

        GitHubApiClient api = new GitHubApiClient(getToken());
        api.triggerWorkflow(getOwner(), getRepo(), getWorkflow(), getBranch(),
                new GitHubApiClient.Callback() {
                    @Override
                    public void onSuccess(JSONObject result) {
                        runOnUiThread(() -> {
                            binding.btnTrigger.setEnabled(true);
                            showCardStatus(getString(R.string.cicd_triggered), null);
                            Toast.makeText(CicdActivity.this,
                                    R.string.cicd_triggered, Toast.LENGTH_SHORT).show();
                        });
                    }

                    @Override
                    public void onError(String error) {
                        runOnUiThread(() -> {
                            binding.btnTrigger.setEnabled(true);
                            showCardStatus("Erro: " + error, null);
                        });
                    }
                });
    }

    private void checkStatus() {
        if (!validateFields()) return;

        binding.btnCheckStatus.setEnabled(false);

        GitHubApiClient api = new GitHubApiClient(getToken());
        api.getLatestRun(getOwner(), getRepo(), getWorkflow(),
                new GitHubApiClient.Callback() {
                    @Override
                    public void onSuccess(JSONObject result) {
                        runOnUiThread(() -> {
                            binding.btnCheckStatus.setEnabled(true);
                            try {
                                JSONArray runs = result.getJSONArray("workflow_runs");
                                if (runs.length() == 0) {
                                    showCardStatus("Nenhuma execução encontrada", null);
                                    return;
                                }
                                JSONObject run = runs.getJSONObject(0);
                                lastRunId = run.getLong("id");
                                String status = run.getString("status");
                                String conclusion = run.optString("conclusion", "");
                                String display = formatStatus(status, conclusion);
                                String idText = "Run ID: " + lastRunId;

                                showCardStatus(display, idText);

                                boolean finished = "completed".equals(status);
                                boolean succeeded = "success".equals(conclusion);
                                binding.btnDownload.setVisibility(
                                        finished && succeeded ? View.VISIBLE : View.GONE);

                                binding.tvRunStatus.setTextColor(getStatusColor(status, conclusion));
                            } catch (Exception e) {
                                showCardStatus("Erro ao processar resposta", null);
                            }
                        });
                    }

                    @Override
                    public void onError(String error) {
                        runOnUiThread(() -> {
                            binding.btnCheckStatus.setEnabled(true);
                            showCardStatus("Erro: " + error, null);
                        });
                    }
                });
    }

    private void downloadArtifact() {
        if (lastRunId < 0) {
            Toast.makeText(this, "Verifique o status primeiro", Toast.LENGTH_SHORT).show();
            return;
        }

        GitHubApiClient api = new GitHubApiClient(getToken());
        api.listArtifacts(getOwner(), getRepo(), lastRunId,
                new GitHubApiClient.Callback() {
                    @Override
                    public void onSuccess(JSONObject result) {
                        runOnUiThread(() -> {
                            try {
                                JSONArray artifacts = result.getJSONArray("artifacts");
                                if (artifacts.length() == 0) {
                                    Toast.makeText(CicdActivity.this,
                                            "Nenhum artefato disponível", Toast.LENGTH_SHORT).show();
                                    return;
                                }
                                JSONObject artifact = artifacts.getJSONObject(0);
                                String archiveUrl = artifact.getString("archive_download_url");
                                String urlWithToken = archiveUrl + "?token=" + getToken();
                                Intent browserIntent = new Intent(Intent.ACTION_VIEW,
                                        Uri.parse(urlWithToken));
                                startActivity(browserIntent);
                            } catch (Exception e) {
                                Toast.makeText(CicdActivity.this,
                                        "Erro: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                            }
                        });
                    }

                    @Override
                    public void onError(String error) {
                        runOnUiThread(() ->
                                Toast.makeText(CicdActivity.this,
                                        "Erro: " + error, Toast.LENGTH_SHORT).show());
                    }
                });
    }

    private void showCardStatus(String status, String runId) {
        binding.cardStatus.setVisibility(View.VISIBLE);
        binding.tvRunStatus.setText(status);
        if (runId != null) {
            binding.tvRunId.setText(runId);
            binding.tvRunId.setVisibility(View.VISIBLE);
        } else {
            binding.tvRunId.setVisibility(View.GONE);
        }
    }

    private String formatStatus(String status, String conclusion) {
        if ("completed".equals(status)) {
            switch (conclusion) {
                case "success": return "✓ Concluído com sucesso";
                case "failure": return "✗ Falhou";
                case "cancelled": return "⊘ Cancelado";
                default: return "Concluído: " + conclusion;
            }
        }
        if ("in_progress".equals(status)) return "⟳ Em execução…";
        if ("queued".equals(status)) return "⌛ Na fila…";
        return status;
    }

    private int getStatusColor(String status, String conclusion) {
        if ("completed".equals(status)) {
            if ("success".equals(conclusion))
                return getColor(com.wanderstest3.termuxjavautils.R.color.status_success);
            return getColor(com.wanderstest3.termuxjavautils.R.color.status_failure);
        }
        return getColor(com.wanderstest3.termuxjavautils.R.color.status_running);
    }

    private boolean validateFields() {
        if (getToken().isEmpty() || getOwner().isEmpty()
                || getRepo().isEmpty() || getWorkflow().isEmpty()) {
            Toast.makeText(this, R.string.cicd_error_fields, Toast.LENGTH_SHORT).show();
            return false;
        }
        return true;
    }

    private String getToken() {
        return binding.etToken.getText() != null
                ? binding.etToken.getText().toString().trim() : "";
    }

    private String getOwner() {
        return binding.etOwner.getText() != null
                ? binding.etOwner.getText().toString().trim() : "";
    }

    private String getRepo() {
        return binding.etRepo.getText() != null
                ? binding.etRepo.getText().toString().trim() : "";
    }

    private String getBranch() {
        String branch = binding.etBranch.getText() != null
                ? binding.etBranch.getText().toString().trim() : "";
        return branch.isEmpty() ? "main" : branch;
    }

    private String getWorkflow() {
        return binding.etWorkflow.getText() != null
                ? binding.etWorkflow.getText().toString().trim() : "";
    }
}
