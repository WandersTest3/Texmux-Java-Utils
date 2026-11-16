package com.wanderstest3.termuxjavautils.ui.cicd;

import android.content.ContentValues;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;
import com.google.android.material.button.MaterialButton;
import com.wanderstest3.termuxjavautils.R;
import com.wanderstest3.termuxjavautils.build.GitUrl;
import com.wanderstest3.termuxjavautils.ui.common.BaseActivity;
import com.wanderstest3.termuxjavautils.ui.common.Ui;
import com.wanderstest3.termuxjavautils.util.GitHubApiClient;
import com.wanderstest3.termuxjavautils.util.Prefs;
import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import org.json.JSONArray;
import org.json.JSONObject;

public class CicdActivity extends BaseActivity {

    private SharedPreferences prefs;
    private Ui.Input etToken;
    private Ui.Input etRepo;
    private Ui.Input etBranch;
    private Ui.Input etWorkflow;
    private Ui.Card statusCard;
    private TextView tvStatus;
    private TextView tvRun;
    private MaterialButton btnDownload;
    private long lastRunId = -1;

    @Override
    protected String screenTitle() {
        return "GitHub Actions";
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        prefs = Prefs.get(this);

        content.addView(ui.hint("Compila nos servidores do GitHub, sem gastar bateria nem armazenamento do celular. "
                + "O repositório precisa ter um workflow com gatilho workflow_dispatch (o build.yml deste projeto tem)."));

        Ui.Card tokenCard = ui.card("1. Token de acesso",
                "No GitHub: Settings → Developer settings → Personal access tokens. Marque as permissões repo e workflow. "
                        + "O token fica salvo só neste celular e também serve para clonar repositórios privados.");
        etToken = ui.input("Token do GitHub", "Começa com ghp_ ou github_pat_", "");
        etToken.layout.setEndIconMode(com.google.android.material.textfield.TextInputLayout.END_ICON_PASSWORD_TOGGLE);
        etToken.edit.setInputType(android.text.InputType.TYPE_CLASS_TEXT
                | android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD);
        etToken.set(prefs.getString(Prefs.KEY_TOKEN, ""));
        MaterialButton save = ui.outlined("Salvar token");
        save.setOnClickListener(v -> {
            prefs.edit().putString(Prefs.KEY_TOKEN, etToken.get()).apply();
            Toast.makeText(this, R.string.cicd_token_saved, Toast.LENGTH_SHORT).show();
        });
        tokenCard.body.addView(etToken.layout);
        tokenCard.body.addView(save);
        content.addView(tokenCard.view);

        Ui.Card repoCard = ui.card("2. Repositório e workflow",
                "Cole o link do repositório. O branch e o arquivo do workflow dizem o que será executado.");
        etRepo = ui.input("Link do repositório", "", "https://github.com/usuario/repositorio");
        etBranch = ui.input("Branch", "", "main");
        etWorkflow = ui.input("Arquivo do workflow", "Nome do arquivo em .github/workflows", "build.yml");
        try {
            JSONObject o = new JSONObject(prefs.getString(Prefs.KEY_CICD, "{}"));
            etRepo.set(o.optString("repo", ""));
            etBranch.set(o.optString("branch", "main"));
            etWorkflow.set(o.optString("workflow", "build.yml"));
        } catch (Exception ignored) {
        }
        MaterialButton trigger = ui.button("Disparar workflow");
        MaterialButton status = ui.outlined("Verificar status");
        trigger.setOnClickListener(v -> trigger());
        status.setOnClickListener(v -> checkStatus());
        repoCard.body.addView(etRepo.layout);
        repoCard.body.addView(etBranch.layout);
        repoCard.body.addView(etWorkflow.layout);
        repoCard.body.addView(trigger);
        repoCard.body.addView(status);
        content.addView(repoCard.view);

        statusCard = ui.card("Resultado", null);
        tvStatus = ui.text("", 16, R.color.text_primary, true);
        tvRun = ui.hint("");
        btnDownload = ui.button("Baixar artefato (APK) para Downloads");
        btnDownload.setVisibility(View.GONE);
        btnDownload.setOnClickListener(v -> download());
        statusCard.body.addView(tvStatus);
        statusCard.body.addView(tvRun);
        statusCard.body.addView(btnDownload);
        statusCard.view.setVisibility(View.GONE);
        content.addView(statusCard.view);
    }

    private boolean ready() {
        String token = etToken.get().isEmpty() ? prefs.getString(Prefs.KEY_TOKEN, "") : etToken.get();
        GitUrl g = GitUrl.parse(etRepo.get());
        if (token.isEmpty()) {
            Toast.makeText(this, "Informe o token (passo 1).", Toast.LENGTH_SHORT).show();
            return false;
        }
        if (g == null || g.owner.isEmpty()) {
            etRepo.layout.setError("Use um link do GitHub, ex.: https://github.com/usuario/repositorio");
            return false;
        }
        etRepo.layout.setError(null);
        if (etWorkflow.get().isEmpty()) {
            etWorkflow.layout.setError("Informe o arquivo do workflow");
            return false;
        }
        prefs.edit().putString(Prefs.KEY_CICD, cicdJson()).apply();
        return true;
    }

    private String cicdJson() {
        try {
            JSONObject o = new JSONObject();
            o.put("repo", etRepo.get());
            o.put("branch", etBranch.get());
            o.put("workflow", etWorkflow.get());
            return o.toString();
        } catch (Exception e) {
            return "{}";
        }
    }

    private GitHubApiClient api() {
        String token = etToken.get().isEmpty() ? prefs.getString(Prefs.KEY_TOKEN, "") : etToken.get();
        return new GitHubApiClient(token);
    }

    private String branch() {
        return etBranch.get().isEmpty() ? "main" : etBranch.get();
    }

    private void show(String status, String run, int color) {
        statusCard.view.setVisibility(View.VISIBLE);
        tvStatus.setText(status);
        tvStatus.setTextColor(getColor(color));
        tvRun.setText(run == null ? "" : run);
    }

    private static String friendlyError(String err) {
        if (err == null) return "Erro desconhecido";
        if (err.contains("HTTP 401")) return "Token inválido ou expirado (HTTP 401).";
        if (err.contains("HTTP 403")) return "Token sem permissão. Marque repo e workflow (HTTP 403).";
        if (err.contains("HTTP 404")) return "Repositório ou workflow não encontrado. Confira o link e o nome do arquivo (HTTP 404).";
        if (err.contains("HTTP 422")) return "O branch não existe ou o workflow não tem workflow_dispatch (HTTP 422).";
        if (err.contains("Unable to resolve host")) return "Sem internet.";
        return err;
    }

    private void trigger() {
        if (!ready()) return;
        GitUrl g = GitUrl.parse(etRepo.get());
        show("Enviando…", null, R.color.status_running);
        api().triggerWorkflow(g.owner, g.repo, etWorkflow.get(), branch(), new GitHubApiClient.Callback() {
            @Override
            public void onSuccess(JSONObject result) {
                runOnUiThread(() -> show("✓ Workflow disparado. Toque em Verificar status em alguns segundos.", null,
                        R.color.status_success));
            }

            @Override
            public void onError(String error) {
                runOnUiThread(() -> show("✗ " + friendlyError(error), null, R.color.status_failure));
            }
        });
    }

    private void checkStatus() {
        if (!ready()) return;
        GitUrl g = GitUrl.parse(etRepo.get());
        api().getLatestRun(g.owner, g.repo, etWorkflow.get(), new GitHubApiClient.Callback() {
            @Override
            public void onSuccess(JSONObject result) {
                runOnUiThread(() -> {
                    try {
                        JSONArray runs = result.getJSONArray("workflow_runs");
                        if (runs.length() == 0) {
                            show("Nenhuma execução encontrada", null, R.color.text_secondary);
                            return;
                        }
                        JSONObject run = runs.getJSONObject(0);
                        lastRunId = run.getLong("id");
                        String st = run.getString("status");
                        String concl = run.optString("conclusion", "");
                        boolean ok = "completed".equals(st) && "success".equals(concl);
                        int color = ok ? R.color.status_success
                                : "completed".equals(st) ? R.color.status_failure : R.color.status_running;
                        show(formatStatus(st, concl), "Execução #" + lastRunId
                                + "\n" + run.optString("html_url", ""), color);
                        btnDownload.setVisibility(ok ? View.VISIBLE : View.GONE);
                    } catch (Exception e) {
                        show("✗ Resposta inesperada do GitHub", null, R.color.status_failure);
                    }
                });
            }

            @Override
            public void onError(String error) {
                runOnUiThread(() -> show("✗ " + friendlyError(error), null, R.color.status_failure));
            }
        });
    }

    private static String formatStatus(String status, String conclusion) {
        if ("completed".equals(status)) {
            switch (conclusion) {
                case "success": return "✓ Concluído com sucesso";
                case "failure": return "✗ Falhou (veja o log no link abaixo)";
                case "cancelled": return "⊘ Cancelado";
                default: return "Concluído: " + conclusion;
            }
        }
        if ("in_progress".equals(status)) return "⟳ Em execução…";
        if ("queued".equals(status)) return "⌛ Na fila…";
        return status;
    }

    private void download() {
        if (lastRunId < 0) return;
        GitUrl g = GitUrl.parse(etRepo.get());
        final GitHubApiClient client = api();
        client.listArtifacts(g.owner, g.repo, lastRunId, new GitHubApiClient.Callback() {
            @Override
            public void onSuccess(JSONObject result) {
                try {
                    JSONArray arts = result.getJSONArray("artifacts");
                    if (arts.length() == 0) {
                        runOnUiThread(() -> Toast.makeText(CicdActivity.this,
                                "Nenhum artefato nessa execução.", Toast.LENGTH_LONG).show());
                        return;
                    }
                    JSONObject a = arts.getJSONObject(0);
                    String url = a.getString("archive_download_url");
                    String name = a.optString("name", "artifact") + ".zip";
                    saveArtifact(client, url, name);
                } catch (Exception e) {
                    runOnUiThread(() -> Toast.makeText(CicdActivity.this,
                            "Erro: " + e.getMessage(), Toast.LENGTH_LONG).show());
                }
            }

            @Override
            public void onError(String error) {
                runOnUiThread(() -> Toast.makeText(CicdActivity.this,
                        friendlyError(error), Toast.LENGTH_LONG).show());
            }
        });
    }

    private void saveArtifact(GitHubApiClient client, String url, String name) throws Exception {
        OutputStream out;
        if (Build.VERSION.SDK_INT >= 29) {
            ContentValues cv = new ContentValues();
            cv.put(MediaStore.Downloads.DISPLAY_NAME, name);
            cv.put(MediaStore.Downloads.MIME_TYPE, "application/zip");
            cv.put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS);
            Uri uri = getContentResolver().insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, cv);
            if (uri == null) throw new IllegalStateException("Não consegui criar o arquivo em Downloads");
            out = getContentResolver().openOutputStream(uri);
        } else {
            File dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
            dir.mkdirs();
            out = new FileOutputStream(new File(dir, name));
        }
        runOnUiThread(() -> Toast.makeText(this, "Baixando…", Toast.LENGTH_SHORT).show());
        client.downloadArtifact(url, out, new GitHubApiClient.DownloadCallback() {
            @Override
            public void onDone(long bytes) {
                runOnUiThread(() -> Toast.makeText(CicdActivity.this,
                        "✓ " + name + " salvo em Downloads (" + bytes / 1024 + " KB). Extraia o ZIP para pegar o APK.",
                        Toast.LENGTH_LONG).show());
            }

            @Override
            public void onError(String error) {
                runOnUiThread(() -> Toast.makeText(CicdActivity.this,
                        "✗ " + friendlyError(error), Toast.LENGTH_LONG).show());
            }
        });
    }
}
