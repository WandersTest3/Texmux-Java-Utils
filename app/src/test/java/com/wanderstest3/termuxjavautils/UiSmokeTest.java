package com.wanderstest3.termuxjavautils;

import static org.junit.Assert.assertNotNull;

import android.app.Activity;
import com.wanderstest3.termuxjavautils.build.Diagnosis;
import com.wanderstest3.termuxjavautils.build.JobSnapshot;
import com.wanderstest3.termuxjavautils.job.JobManager;
import com.wanderstest3.termuxjavautils.ui.build.BuildActivity;
import com.wanderstest3.termuxjavautils.ui.cicd.CicdActivity;
import com.wanderstest3.termuxjavautils.ui.decompile.DecompileActivity;
import com.wanderstest3.termuxjavautils.ui.scripts.ScriptsActivity;
import com.wanderstest3.termuxjavautils.ui.setup.SetupActivity;
import com.wanderstest3.termuxjavautils.ui.termux.TermuxActivity;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.Shadows;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34)
public class UiSmokeTest {

    private static <T extends Activity> T open(Class<T> c) {
        return Robolectric.buildActivity(c).setup().get();
    }

    @Test
    public void mainOpens() {
        assertNotNull(open(MainActivity.class));
    }

    @Test
    public void setupOpens() {
        assertNotNull(open(SetupActivity.class));
    }

    @Test
    public void cicdOpens() {
        assertNotNull(open(CicdActivity.class));
    }

    @Test
    public void termuxOpens() {
        assertNotNull(open(TermuxActivity.class));
    }

    @Test
    public void scriptsOpens() {
        assertNotNull(open(ScriptsActivity.class));
    }

    @Test
    public void decompileOpensAndRendersJob() {
        JobManager.Job job = new JobManager.Job();
        job.id = "d1";
        job.kind = JobManager.KIND_DECOMPILE;
        job.title = "app.apk";
        job.launching = false;
        job.finished = true;
        job.snapshot.state = JobSnapshot.State.FAILED;
        job.snapshot.log = "##TJU_FAIL## DEC_INPUT_NOT_FOUND app.apk\n";
        job.diagnoses = new com.wanderstest3.termuxjavautils.build.DiagnosisEngine(null)
                .analyze(job.snapshot.log, true);
        JobManager.publish(job);
        DecompileActivity a = open(DecompileActivity.class);
        assertNotNull(a);
        Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();
    }

    @Test
    public void buildOpensAndRendersFailedJob() {
        JobManager.Job job = new JobManager.Job();
        job.id = "b1";
        job.title = "x.zip";
        job.launching = false;
        job.finished = true;
        job.snapshot = JobSnapshot.parse("FAILED ZIP_NOT_FOUND\n##TJU_SEP##\n##TJU_SEP##\n##TJU_FAIL## ZIP_NOT_FOUND x.zip\n");
        job.diagnoses.add(new Diagnosis("T", Diagnosis.Severity.ERROR, "Titulo", "Causa", "Passo 1", "Passo 2")
                .fix("Instalar", "pkg install -y unzip").copy("cmd"));
        JobManager.publish(job);
        BuildActivity a = open(BuildActivity.class);
        Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();
        assertNotNull(a);
    }
}
