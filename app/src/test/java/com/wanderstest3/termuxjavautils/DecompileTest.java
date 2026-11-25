package com.wanderstest3.termuxjavautils;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.wanderstest3.termuxjavautils.build.DecompileRequest;
import com.wanderstest3.termuxjavautils.build.Diagnosis;
import com.wanderstest3.termuxjavautils.build.DiagnosisEngine;
import com.wanderstest3.termuxjavautils.build.JobSnapshot;
import com.wanderstest3.termuxjavautils.build.ScriptBuilder;
import com.wanderstest3.termuxjavautils.util.FileImporter;
import java.util.List;
import org.junit.Test;

public class DecompileTest {

    private static boolean has(List<Diagnosis> l, String code) {
        for (Diagnosis d : l) if (d.code.equals(code)) return true;
        return false;
    }

    @Test
    public void extensionsAndSupport() {
        assertEquals("apk", DecompileRequest.extensionOf("/storage/emulated/0/Download/App.APK"));
        assertEquals("", DecompileRequest.extensionOf("semextensao"));
        assertTrue(DecompileRequest.isSupported("mod-1.0.jar"));
        assertTrue(DecompileRequest.isSupported("lib.aar"));
        assertTrue(DecompileRequest.isSupported("classes.dex"));
        assertFalse(DecompileRequest.isSupported("foto.png"));
        assertTrue(DecompileRequest.isApk("a.apk"));
        assertFalse(DecompileRequest.isApk("a.jar"));
    }

    @Test
    public void scriptHeaderQuotesValues() {
        DecompileRequest r = new DecompileRequest();
        r.input = "/storage/emulated/0/Download/it's.apk";
        r.mode = DecompileRequest.MODE_BOTH;
        r.deobf = true;
        String s = ScriptBuilder.decompileScript(r, "d1", "echo body\n");
        assertTrue(s.contains("TJU_ID='d1'"));
        assertTrue(s.contains("DEC_INPUT='/storage/emulated/0/Download/it'\\''s.apk'"));
        assertTrue(s.contains("DEC_MODE='BOTH'"));
        assertTrue(s.contains("DEC_DEOBF='1'"));
        assertTrue(s.endsWith("echo body\n"));
    }

    @Test
    public void launchWritesKindAndPollReadsIt() {
        String launch = ScriptBuilder.launchCommand("d1", "echo", "", JobManagerKinds.DECOMPILE);
        assertTrue(launch.contains("> \"$D/kind\""));
        assertTrue(ScriptBuilder.pollCommand("d1", 10).contains("KIND="));
        JobSnapshot s = JobSnapshot.parse("ID=d1\nKIND=decompile\nRUNNING 2/4 Preparando\n"
                + JobSnapshot.SEP + "\n" + JobSnapshot.SEP + "\nlog\n");
        assertEquals("decompile", s.kind);
        assertEquals(2, s.step);
        assertEquals(4, s.total);
    }

    @Test
    public void listFilesCommandUsesGlobs() {
        String c = ScriptBuilder.listFilesCommand("", "apk", "jar");
        assertTrue(c.contains("\"$D\"/*.apk"));
        assertTrue(c.contains("\"$D\"/*.jar"));
    }

    @Test
    public void documentPaths() {
        assertEquals("/storage/emulated/0/Download/a.apk",
                FileImporter.pathFromDocument("com.android.externalstorage.documents", "primary:Download/a.apk"));
        assertNull(FileImporter.pathFromDocument("com.android.externalstorage.documents", "1234-ABCD:a.apk"));
        assertNull(FileImporter.pathFromDocument("com.android.externalstorage.documents", "primary:Android/data/x/a.apk"));
        assertEquals("/storage/emulated/0/Download/a.zip",
                FileImporter.pathFromDocument("com.android.providers.downloads.documents",
                        "raw:/storage/emulated/0/Download/a.zip"));
        assertNull(FileImporter.pathFromDocument("com.android.providers.downloads.documents", "msf:123"));
        assertNull(FileImporter.pathFromDocument("com.other.provider", "x"));
    }

    @Test
    public void decompileDiagnoses() {
        DiagnosisEngine e = new DiagnosisEngine(null);
        assertTrue(has(e.analyze("##TJU_FAIL## DEC_INPUT_NOT_FOUND x.apk\n", true), "DEC_INPUT_NOT_FOUND"));
        assertTrue(has(e.analyze("##TJU_FAIL## DEC_UNSUPPORTED png\n", true), "DEC_UNSUPPORTED"));
        assertTrue(has(e.analyze("##TJU_WARN## DEC_PARTIAL erro\n##TJU_DONE## ok\n", false), "DEC_PARTIAL"));
        List<Diagnosis> inst = e.analyze("##TJU_FAIL## TOOL_INSTALL_FAILED jadx\n", true);
        assertTrue(has(inst, "TOOL_INSTALL_FAILED"));
        assertTrue(inst.get(0).fixCommand.contains("pkg install -y jadx"));
    }

    @Test
    public void bundlesAreSupported() {
        assertTrue(DecompileRequest.isSupported("jogo.xapk"));
        assertTrue(DecompileRequest.isSupported("jogo.APKM"));
        assertTrue(DecompileRequest.isSupported("jogo.apks"));
        assertTrue(DecompileRequest.isBundle("a.xapk"));
        assertFalse(DecompileRequest.isBundle("a.apk"));
        assertTrue(DecompileRequest.isApkOrBundle("a.apkm"));
        assertFalse(DecompileRequest.isApkOrBundle("a.jar"));
    }

    @Test
    public void dotnetModeAndEmbeddedExtractor() {
        DecompileRequest r = new DecompileRequest();
        r.input = "/storage/emulated/0/Download/jogo.xapk";
        r.mode = DecompileRequest.MODE_DOTNET;
        assertTrue(ScriptBuilder.decompileScript(r, "d1", "echo body\n").contains("DEC_MODE='DOTNET'"));
        String body = ScriptBuilder.embedDotnetExtractor("echo a\n#TJU_DOTNET_PY#\necho b\n", "print('x')");
        assertTrue(body.contains("cat > \"$TJU_PY\" <<'TJU_PY_EOF'\nprint('x')\nTJU_PY_EOF\necho b"));
        assertEquals("echo\n", ScriptBuilder.embedDotnetExtractor("echo\n", "p"));
    }

    @Test
    public void dotnetDiagnoses() {
        DiagnosisEngine e = new DiagnosisEngine(null);
        assertTrue(has(e.analyze("##TJU_FAIL## DEC_BUNDLE_NO_BASE x.xapk\n", true), "DEC_BUNDLE_NO_BASE"));
        assertTrue(has(e.analyze("##TJU_FAIL## DEC_DOTNET_NONE x.apk\n", true), "DEC_DOTNET_NONE"));
        assertTrue(has(e.analyze("##TJU_WARN## DEC_DOTNET_DETECTED x.apk\n##TJU_DONE## ok\n", false), "DEC_DOTNET_DETECTED"));
        assertTrue(has(e.analyze("##TJU_WARN## DEC_DOTNET_NO_ILSPY x.apk\n##TJU_DONE## ok\n", false), "DEC_DOTNET_NO_ILSPY"));
    }

    private static final class JobManagerKinds {
        static final String DECOMPILE = "decompile";
    }
}
