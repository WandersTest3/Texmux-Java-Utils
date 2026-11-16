package com.wanderstest3.termuxjavautils.util;

import org.json.JSONObject;
import java.io.IOException;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class GitHubApiClient {

    private static final String BASE_URL = "https://api.github.com";
    private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");

    private final OkHttpClient client;
    private final String token;

    public GitHubApiClient(String token) {
        this.token = token;
        this.client = new OkHttpClient();
    }

    public void triggerWorkflow(String owner, String repo, String workflowId, String ref,
                                Callback callback) {
        new Thread(() -> {
            try {
                String url = BASE_URL + "/repos/" + owner + "/" + repo
                        + "/actions/workflows/" + workflowId + "/dispatches";

                JSONObject body = new JSONObject();
                body.put("ref", ref);

                Request request = new Request.Builder()
                        .url(url)
                        .header("Authorization", "Bearer " + token)
                        .header("Accept", "application/vnd.github+json")
                        .header("X-GitHub-Api-Version", "2022-11-28")
                        .post(RequestBody.create(body.toString(), JSON))
                        .build();

                try (Response response = client.newCall(request).execute()) {
                    if (response.code() == 204) {
                        callback.onSuccess(null);
                    } else {
                        String bodyStr = response.body() != null ? response.body().string() : "";
                        callback.onError("HTTP " + response.code() + ": " + bodyStr);
                    }
                }
            } catch (Exception e) {
                callback.onError(e.getMessage());
            }
        }).start();
    }

    public void getLatestRun(String owner, String repo, String workflowId,
                             Callback callback) {
        new Thread(() -> {
            try {
                String url = BASE_URL + "/repos/" + owner + "/" + repo
                        + "/actions/workflows/" + workflowId + "/runs?per_page=1";

                Request request = new Request.Builder()
                        .url(url)
                        .header("Authorization", "Bearer " + token)
                        .header("Accept", "application/vnd.github+json")
                        .header("X-GitHub-Api-Version", "2022-11-28")
                        .get()
                        .build();

                try (Response response = client.newCall(request).execute()) {
                    if (response.isSuccessful() && response.body() != null) {
                        callback.onSuccess(new JSONObject(response.body().string()));
                    } else {
                        callback.onError("HTTP " + response.code());
                    }
                }
            } catch (Exception e) {
                callback.onError(e.getMessage());
            }
        }).start();
    }

    public void listArtifacts(String owner, String repo, long runId,
                              Callback callback) {
        new Thread(() -> {
            try {
                String url = BASE_URL + "/repos/" + owner + "/" + repo
                        + "/actions/runs/" + runId + "/artifacts";

                Request request = new Request.Builder()
                        .url(url)
                        .header("Authorization", "Bearer " + token)
                        .header("Accept", "application/vnd.github+json")
                        .header("X-GitHub-Api-Version", "2022-11-28")
                        .get()
                        .build();

                try (Response response = client.newCall(request).execute()) {
                    if (response.isSuccessful() && response.body() != null) {
                        callback.onSuccess(new JSONObject(response.body().string()));
                    } else {
                        callback.onError("HTTP " + response.code());
                    }
                }
            } catch (Exception e) {
                callback.onError(e.getMessage());
            }
        }).start();
    }

    public interface Callback {
        void onSuccess(JSONObject result);
        void onError(String error);
    }
}
