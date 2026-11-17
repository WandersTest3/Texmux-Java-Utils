package com.wanderstest3.termuxjavautils.build;

public class BuildRequest {

    public static final String OUT_AUTO = "AUTO";
    public static final String OUT_RELEASE = "RELEASE";
    public static final String OUT_DEBUG = "DEBUG";
    public static final String OUT_JAR = "JAR";

    public String name = "";
    public boolean fromGit = false;
    public String zipName = "";
    public String gitUrl = "";
    public String branch = "";
    public boolean useToken = false;
    public String module = "";
    public String output = OUT_AUTO;
    public String gradleArgs = "";
    public String extrasDir = "";
    public String extraFiles = "";
    public String signZip = "";
    public boolean useAapt2 = true;
    public String sdkDir = "$HOME/android-sdk";
    public String javaHome = "";
    public String downloadDir = "/storage/emulated/0/Download";
    public String outputDir = "/storage/emulated/0/Download";
    public String workDir = "$HOME/projects/tju";

    public String sourceLabel() {
        return fromGit ? gitUrl : zipName;
    }
}
