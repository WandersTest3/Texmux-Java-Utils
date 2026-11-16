package com.wanderstest3.termuxjavautils;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.wanderstest3.termuxjavautils.build.Diagnosis;
import com.wanderstest3.termuxjavautils.build.DiagnosisEngine;
import com.wanderstest3.termuxjavautils.build.GitUrl;
import com.wanderstest3.termuxjavautils.build.JobSnapshot;
import java.util.List;
import org.junit.Test;

public class DiagnosisEngineTest {

    private static boolean has(List<Diagnosis> l, String code) {
        for (Diagnosis d : l) if (d.code.equals(code)) return true;
        return false;
    }

    @Test
    public void zipNotFoundMarker() {
        List<Diagnosis> l = new DiagnosisEngine(null).analyze("##TJU_FAIL## ZIP_NOT_FOUND foo.zip\n", true);
        assertTrue(has(l, "ZIP_NOT_FOUND"));
    }

    @Test
    public void aapt2Error() {
        String log = "Android resource linking failed\naapt2 E ERROR: Syntax error: \"(\" unexpected\n";
        assertTrue(has(new DiagnosisEngine(null).analyze(log, true), "AAPT2_ARM"));
    }

    @Test
    public void licensesOffersFix() {
        String log = "Failed to install the following Android SDK packages as some licenses have not been accepted.";
        for (Diagnosis d : new DiagnosisEngine("~/android-sdk").analyze(log, true)) {
            if (d.code.equals("LICENSES")) {
                assertTrue(d.fixCommand.contains("$HOME/android-sdk"));
                return;
            }
        }
        throw new AssertionError("LICENSES not found");
    }

    @Test
    public void unknownFailureIsGeneric() {
        List<Diagnosis> l = new DiagnosisEngine(null).analyze("* What went wrong:\nSomething odd\n* Try:\n", true);
        assertTrue(has(l, "UNKNOWN"));
    }

    @Test
    public void successHasNoErrors() {
        assertTrue(new DiagnosisEngine(null).analyze("##TJU_STEP## 1/5 x\n##TJU_DONE## 1\n", false).isEmpty());
    }

    @Test
    public void gitUrlParsing() {
        GitUrl g = GitUrl.parse("https://github.com/a/b/tree/dev");
        assertNotNull(g);
        assertEquals("https://github.com/a/b", g.url);
        assertEquals("dev", g.branch);
        assertNull(GitUrl.parse("nada"));
    }

    @Test
    public void snapshotParsing() {
        JobSnapshot s = JobSnapshot.parse("ID=b1\nRUNNING 4/5 Compilando\n##TJU_SEP##\n/x/a.apk\n##TJU_SEP##\nlog line\n");
        assertEquals(JobSnapshot.State.RUNNING, s.state);
        assertEquals(4, s.step);
        assertEquals("Compilando", s.stepName);
        assertEquals(1, s.artifacts.size());
        assertEquals("b1", s.id);
    }
}
