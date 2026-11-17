package com.wanderstest3.termuxjavautils.build;

import java.util.ArrayList;
import java.util.List;

public final class JobSnapshot {

    public static final String SEP = "##TJU_SEP##";

    public enum State { RUNNING, SUCCESS, FAILED, CANCELLED, UNKNOWN }

    public String id = "";
    public String kind = "";
    public State state = State.UNKNOWN;
    public int step = 0;
    public int total = 0;
    public String stepName = "";
    public String failCode = "";
    public final List<String> artifacts = new ArrayList<>();
    public String log = "";

    public boolean finished() {
        return state == State.SUCCESS || state == State.FAILED || state == State.CANCELLED;
    }

    public static JobSnapshot parse(String stdout) {
        JobSnapshot s = new JobSnapshot();
        if (stdout == null) return s;
        String[] parts = stdout.split(SEP + "\\r?\\n?", -1);
        String head = parts.length > 0 ? parts[0] : "";
        String arts = parts.length > 1 ? parts[1] : "";
        s.log = parts.length > 2 ? parts[2] : "";
        for (String line : head.split("\\r?\\n")) {
            line = line.trim();
            if (line.startsWith("ID=")) {
                s.id = line.substring(3).trim();
            } else if (line.startsWith("KIND=")) {
                s.kind = line.substring(5).trim();
            } else if (line.startsWith("RUNNING")) {
                s.state = State.RUNNING;
                String rest = line.substring(7).trim();
                int sp = rest.indexOf(' ');
                String frac = sp < 0 ? rest : rest.substring(0, sp);
                s.stepName = sp < 0 ? "" : rest.substring(sp + 1).trim();
                int sl = frac.indexOf('/');
                if (sl > 0) {
                    try {
                        s.step = Integer.parseInt(frac.substring(0, sl));
                        s.total = Integer.parseInt(frac.substring(sl + 1));
                    } catch (NumberFormatException ignored) {
                    }
                }
            } else if (line.equals("SUCCESS")) {
                s.state = State.SUCCESS;
            } else if (line.startsWith("FAILED")) {
                s.state = State.FAILED;
                s.failCode = line.substring(6).trim();
            } else if (line.equals("CANCELLED")) {
                s.state = State.CANCELLED;
            }
        }
        for (String a : arts.split("\\r?\\n")) {
            if (!a.trim().isEmpty()) s.artifacts.add(a.trim());
        }
        return s;
    }
}
