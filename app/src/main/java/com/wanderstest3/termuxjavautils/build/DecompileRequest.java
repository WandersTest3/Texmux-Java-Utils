package com.wanderstest3.termuxjavautils.build;

import java.util.Locale;

public class DecompileRequest {

    public static final String MODE_JAVA = "JAVA";
    public static final String MODE_SMALI = "SMALI";
    public static final String MODE_BOTH = "BOTH";
    public static final String MODE_DEX2JAR = "DEX2JAR";
    public static final String MODE_EXTRACT = "EXTRACT";

    public String input = "";
    public String mode = MODE_JAVA;
    public boolean deobf = false;
    public boolean asFolder = false;
    public String javaHome = "";
    public String downloadDir = "/storage/emulated/0/Download";
    public String outputDir = "/storage/emulated/0/Download";
    public String workDir = "$HOME/decompiled";

    public static String extensionOf(String path) {
        if (path == null) return "";
        String p = path.trim();
        int slash = Math.max(p.lastIndexOf('/'), p.lastIndexOf('\\'));
        String name = slash >= 0 ? p.substring(slash + 1) : p;
        int dot = name.lastIndexOf('.');
        return dot < 0 ? "" : name.substring(dot + 1).toLowerCase(Locale.ROOT);
    }

    public static boolean isSupported(String path) {
        switch (extensionOf(path)) {
            case "apk":
            case "jar":
            case "aar":
            case "dex":
            case "class":
            case "zip":
                return true;
            default:
                return false;
        }
    }

    public static boolean isApk(String path) {
        return "apk".equals(extensionOf(path));
    }

    public static boolean isDexOrApk(String path) {
        String e = extensionOf(path);
        return "apk".equals(e) || "dex".equals(e);
    }

    public static boolean isSourceArchive(String path) {
        return "zip".equals(extensionOf(path));
    }
}
