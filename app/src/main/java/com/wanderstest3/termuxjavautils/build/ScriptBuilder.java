package com.wanderstest3.termuxjavautils.build;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

public final class ScriptBuilder {

    private ScriptBuilder() {}

    public static String quote(String s) {
        if (s == null) s = "";
        return "'" + s.replace("'", "'\\''") + "'";
    }

    private static String var(String name, String value) {
        return name + "=" + quote(value) + "\n";
    }

    public static String buildScript(BuildRequest r, String id, String body, boolean preflight) {
        GitUrl g = r.fromGit ? GitUrl.parse(r.gitUrl) : null;
        String url = g != null ? g.url : r.gitUrl;
        String branch = r.branch != null && !r.branch.trim().isEmpty()
                ? r.branch.trim() : (g != null ? g.branch : "");
        StringBuilder sb = new StringBuilder();
        sb.append("#!/data/data/com.termux/files/usr/bin/bash\n");
        sb.append(var("TJU_ID", id));
        sb.append(var("TJU_MODE", preflight ? "preflight" : "build"));
        sb.append(var("SRC_MODE", r.fromGit ? "git" : "zip"));
        sb.append(var("ZIP_NAME", nz(r.zipName).trim()));
        sb.append(var("GIT_URL", url));
        sb.append(var("GIT_BRANCH", branch));
        sb.append(var("MODULE", nz(r.module).trim()));
        sb.append(var("OUTPUT_KIND", nz(r.output)));
        sb.append(var("GRADLE_ARGS", nz(r.gradleArgs).trim()));
        sb.append(var("EXTRAS_DIR", nz(r.extrasDir).trim()));
        sb.append(var("EXTRA_FILES", nz(r.extraFiles).trim()));
        sb.append(var("USE_AAPT2", r.useAapt2 ? "1" : "0"));
        sb.append(var("SDK_DIR", orDefault(r.sdkDir, "$HOME/android-sdk")));
        sb.append(var("JAVA_HOME_OVERRIDE", nz(r.javaHome).trim()));
        sb.append(var("DOWNLOAD_DIR", orDefault(r.downloadDir, "/storage/emulated/0/Download")));
        sb.append(var("OUT_DIR", orDefault(r.outputDir, "/storage/emulated/0/Download")));
        sb.append(var("WORK_BASE", orDefault(r.workDir, "$HOME/projects/tju")));
        sb.append(body);
        return sb.toString();
    }

    public static String commandJobScript(String id, String title, String command) {
        StringBuilder sb = new StringBuilder();
        sb.append("#!/data/data/com.termux/files/usr/bin/bash\n");
        sb.append(var("TJU_ID", id));
        sb.append(var("TJU_TITLE", title));
        sb.append("export PREFIX=\"${PREFIX:-/data/data/com.termux/files/usr}\"\n");
        sb.append("export HOME=\"${HOME:-/data/data/com.termux/files/home}\"\n");
        sb.append("export DEBIAN_FRONTEND=noninteractive\n");
        sb.append("TJU_DIR=\"$HOME/.tju/jobs/$TJU_ID\"\n");
        sb.append("mkdir -p \"$TJU_DIR\"\n");
        sb.append("echo $$ > \"$TJU_DIR/pid\"\n");
        sb.append(": > \"$TJU_DIR/artifacts\"\n");
        sb.append("command -v termux-wake-lock >/dev/null 2>&1 && termux-wake-lock >/dev/null 2>&1\n");
        sb.append("trap 'echo CANCELLED > \"$TJU_DIR/status\"; echo \"##TJU_FAIL## CANCELLED\"; exit 143' TERM INT\n");
        sb.append("echo \"RUNNING 1/1 $TJU_TITLE\" > \"$TJU_DIR/status\"\n");
        sb.append("echo \"##TJU_STEP## 1/1 $TJU_TITLE\"\n");
        sb.append("(\n").append(command).append("\n)\n");
        sb.append("RC=$?\n");
        sb.append("command -v termux-wake-unlock >/dev/null 2>&1 && termux-wake-unlock >/dev/null 2>&1\n");
        sb.append("if [ \"$RC\" -eq 0 ]; then echo SUCCESS > \"$TJU_DIR/status\"; echo \"##TJU_DONE## ok\"; exit 0; fi\n");
        sb.append("echo \"##TJU_FAIL## CMD_FAILED $RC\"\n");
        sb.append("echo \"FAILED CMD_FAILED\" > \"$TJU_DIR/status\"\n");
        sb.append("exit 1\n");
        return sb.toString();
    }

    public static String launchCommand(String id, String script, String token) {
        String b64 = Base64.getEncoder().encodeToString(script.getBytes(StandardCharsets.UTF_8));
        StringBuilder sb = new StringBuilder();
        sb.append("D=\"$HOME/.tju/jobs/").append(id).append("\"; ");
        sb.append("mkdir -p \"$D\"; ");
        sb.append("echo ").append(b64).append(" | base64 -d > \"$D/build.sh\"; ");
        if (token != null && !token.isEmpty()) {
            sb.append("(umask 077; printf %s ").append(quote(token)).append(" > \"$D/token\"); ");
        }
        sb.append("echo ").append(id).append(" > \"$HOME/.tju/last\"; ");
        sb.append(": > \"$D/log\"; ");
        sb.append("echo \"RUNNING 0/5 Iniciando\" > \"$D/status\"; ");
        sb.append("nohup bash \"$D/build.sh\" > \"$D/log\" 2>&1 < /dev/null & ");
        sb.append("echo started");
        return sb.toString();
    }

    public static String preflightCommand(String script) {
        String b64 = Base64.getEncoder().encodeToString(script.getBytes(StandardCharsets.UTF_8));
        return "echo " + b64 + " | base64 -d | bash";
    }

    public static String pollCommand(String idOrLast, int tailLines) {
        String dir;
        if ("last".equals(idOrLast)) {
            dir = "ID=\"$(cat \"$HOME/.tju/last\" 2>/dev/null)\"; D=\"$HOME/.tju/jobs/$ID\"; ";
        } else {
            dir = "ID=" + idOrLast + "; D=\"$HOME/.tju/jobs/$ID\"; ";
        }
        return dir
                + "echo \"ID=$ID\"; "
                + "cat \"$D/status\" 2>/dev/null || echo NOSTATUS; "
                + "echo '" + JobSnapshot.SEP + "'; "
                + "cat \"$D/artifacts\" 2>/dev/null; "
                + "echo '" + JobSnapshot.SEP + "'; "
                + "tail -n " + tailLines + " \"$D/log\" 2>/dev/null";
    }

    public static String cancelCommand(String id) {
        return "D=\"$HOME/.tju/jobs/" + id + "\"; "
                + "echo CANCELLED > \"$D/status\"; "
                + "P=\"$(cat \"$D/pid\" 2>/dev/null)\"; "
                + "[ -n \"$P\" ] && pkill -TERM -P \"$P\" 2>/dev/null; "
                + "[ -n \"$P\" ] && kill -TERM \"$P\" 2>/dev/null; "
                + "W=\"$(cat \"$D/workdir\" 2>/dev/null)\"; "
                + "[ -n \"$W\" ] && [ -x \"$W/gradlew\" ] && (cd \"$W\" && ./gradlew --stop >/dev/null 2>&1); "
                + "echo cancelled";
    }

    public static String listZipsCommand(String downloadDir) {
        String d = orDefault(downloadDir, "/storage/emulated/0/Download");
        return "D=" + quote(d) + "; D=\"${D/#\\$HOME/$HOME}\"; "
                + "ls -1t \"$D\"/*.zip 2>/dev/null | head -30 | sed 's#.*/##'";
    }

    private static String nz(String s) {
        return s == null ? "" : s;
    }

    private static String orDefault(String s, String d) {
        return s == null || s.trim().isEmpty() ? d : s.trim();
    }
}
