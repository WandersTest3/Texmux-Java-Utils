package com.wanderstest3.termuxjavautils.job;

import android.os.Handler;
import android.os.Looper;
import com.wanderstest3.termuxjavautils.build.Diagnosis;
import com.wanderstest3.termuxjavautils.build.JobSnapshot;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public final class JobManager {

    public static final class Job {
        public String id = "";
        public String title = "";
        public boolean isBuild = true;
        public String sdkDir = "";
        public boolean launching = true;
        public long startedAt = System.currentTimeMillis();
        public JobSnapshot snapshot = new JobSnapshot();
        public List<Diagnosis> diagnoses = new ArrayList<>();

        public boolean finished = false;

        public boolean running() {
            return !finished;
        }
    }

    public interface Listener {
        void onJobChanged(Job job);
    }

    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static final CopyOnWriteArrayList<Listener> LISTENERS = new CopyOnWriteArrayList<>();
    private static volatile Job current;

    private JobManager() {}

    public static Job current() {
        return current;
    }

    public static boolean isRunning() {
        Job j = current;
        return j != null && !j.finished;
    }

    public static void add(Listener l) {
        LISTENERS.add(l);
    }

    public static void remove(Listener l) {
        LISTENERS.remove(l);
    }

    public static void publish(Job job) {
        current = job;
        MAIN.post(() -> {
            for (Listener l : LISTENERS) l.onJobChanged(job);
        });
    }
}
