package com.wanderstest3.termuxjavautils.build;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public final class Diagnosis {

    public enum Severity { ERROR, WARNING }

    public final String code;
    public final Severity severity;
    public final String title;
    public final String cause;
    public final List<String> steps = new ArrayList<>();
    public String fixLabel = "";
    public String fixCommand = "";
    public String copyText = "";

    public Diagnosis(String code, Severity severity, String title, String cause, String... steps) {
        this.code = code;
        this.severity = severity;
        this.title = title;
        this.cause = cause;
        this.steps.addAll(Arrays.asList(steps));
    }

    public Diagnosis fix(String label, String command) {
        this.fixLabel = label;
        this.fixCommand = command;
        return this;
    }

    public Diagnosis copy(String text) {
        this.copyText = text;
        return this;
    }

    public boolean hasFix() {
        return !fixCommand.isEmpty();
    }
}
