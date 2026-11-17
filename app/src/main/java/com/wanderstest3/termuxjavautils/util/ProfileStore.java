package com.wanderstest3.termuxjavautils.util;

import android.content.Context;
import com.wanderstest3.termuxjavautils.build.BuildRequest;
import java.util.ArrayList;
import java.util.List;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

public final class ProfileStore {

    private ProfileStore() {}

    public static JSONObject toJson(BuildRequest r) throws JSONException {
        JSONObject o = new JSONObject();
        o.put("name", r.name);
        o.put("fromGit", r.fromGit);
        o.put("zipName", r.zipName);
        o.put("gitUrl", r.gitUrl);
        o.put("branch", r.branch);
        o.put("useToken", r.useToken);
        o.put("module", r.module);
        o.put("output", r.output);
        o.put("gradleArgs", r.gradleArgs);
        o.put("extrasDir", r.extrasDir);
        o.put("extraFiles", r.extraFiles);
        o.put("signZip", r.signZip);
        o.put("useAapt2", r.useAapt2);
        o.put("sdkDir", r.sdkDir);
        o.put("javaHome", r.javaHome);
        o.put("downloadDir", r.downloadDir);
        o.put("outputDir", r.outputDir);
        o.put("workDir", r.workDir);
        return o;
    }

    public static BuildRequest fromJson(JSONObject o) {
        BuildRequest r = new BuildRequest();
        r.name = o.optString("name", r.name);
        r.fromGit = o.optBoolean("fromGit", r.fromGit);
        r.zipName = o.optString("zipName", r.zipName);
        r.gitUrl = o.optString("gitUrl", r.gitUrl);
        r.branch = o.optString("branch", r.branch);
        r.useToken = o.optBoolean("useToken", r.useToken);
        r.module = o.optString("module", r.module);
        r.output = o.optString("output", r.output);
        r.gradleArgs = o.optString("gradleArgs", r.gradleArgs);
        r.extrasDir = o.optString("extrasDir", r.extrasDir);
        r.extraFiles = o.optString("extraFiles", r.extraFiles);
        r.signZip = o.optString("signZip", r.signZip);
        r.useAapt2 = o.optBoolean("useAapt2", r.useAapt2);
        r.sdkDir = o.optString("sdkDir", r.sdkDir);
        r.javaHome = o.optString("javaHome", r.javaHome);
        r.downloadDir = o.optString("downloadDir", r.downloadDir);
        r.outputDir = o.optString("outputDir", r.outputDir);
        r.workDir = o.optString("workDir", r.workDir);
        return r;
    }

    public static List<BuildRequest> load(Context ctx) {
        List<BuildRequest> list = new ArrayList<>();
        try {
            JSONArray arr = new JSONArray(Prefs.get(ctx).getString(Prefs.KEY_PROFILES, "[]"));
            for (int i = 0; i < arr.length(); i++) list.add(fromJson(arr.getJSONObject(i)));
        } catch (JSONException ignored) {
        }
        if (!Prefs.get(ctx).getBoolean(Prefs.KEY_SEEDED, false)) {
            list.add(pojarushExample());
            save(ctx, list);
            Prefs.get(ctx).edit().putBoolean(Prefs.KEY_SEEDED, true).apply();
        }
        return list;
    }

    public static void save(Context ctx, List<BuildRequest> list) {
        JSONArray arr = new JSONArray();
        try {
            for (BuildRequest r : list) arr.put(toJson(r));
        } catch (JSONException ignored) {
        }
        Prefs.get(ctx).edit().putString(Prefs.KEY_PROFILES, arr.toString()).apply();
    }

    public static void saveLast(Context ctx, BuildRequest r) {
        try {
            Prefs.get(ctx).edit().putString(Prefs.KEY_LAST, toJson(r).toString()).apply();
        } catch (JSONException ignored) {
        }
    }

    public static BuildRequest loadLast(Context ctx) {
        String s = Prefs.get(ctx).getString(Prefs.KEY_LAST, "");
        if (s.isEmpty()) return new BuildRequest();
        try {
            return fromJson(new JSONObject(s));
        } catch (JSONException e) {
            return new BuildRequest();
        }
    }

    public static BuildRequest pojarushExample() {
        BuildRequest r = new BuildRequest();
        r.name = "Pojarush_Test3";
        r.zipName = "Pojarush_Test3-main.zip";
        r.module = "Pojarush";
        r.output = BuildRequest.OUT_RELEASE;
        r.signZip = "Pojarush_Test3-assinatura.zip";
        r.useAapt2 = true;
        return r;
    }
}
