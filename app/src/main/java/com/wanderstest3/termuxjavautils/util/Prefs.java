package com.wanderstest3.termuxjavautils.util;

import android.content.Context;
import android.content.SharedPreferences;

public final class Prefs {

    public static final String NAME = "termux_java_utils_prefs";
    public static final String KEY_TOKEN = "github_token";
    public static final String KEY_PROFILES = "build_profiles";
    public static final String KEY_LAST = "build_last_form";
    public static final String KEY_SEEDED = "profiles_seeded";
    public static final String KEY_CICD = "cicd_form";

    private Prefs() {}

    public static SharedPreferences get(Context ctx) {
        return ctx.getSharedPreferences(NAME, Context.MODE_PRIVATE);
    }

    public static String token(Context ctx) {
        return get(ctx).getString(KEY_TOKEN, "");
    }
}
