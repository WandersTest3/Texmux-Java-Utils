package com.wanderstest3.termuxjavautils.build;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class GitUrl {

    public final String url;
    public final String branch;
    public final String owner;
    public final String repo;

    private GitUrl(String url, String branch, String owner, String repo) {
        this.url = url;
        this.branch = branch;
        this.owner = owner;
        this.repo = repo;
    }

    private static final Pattern GITHUB = Pattern.compile(
            "^(?:https?://)?(?:www\\.)?github\\.com[/:]([A-Za-z0-9_.-]+)/([A-Za-z0-9_.-]+?)(?:\\.git)?(?:/(?:tree|blob)/([^?#]+?))?/?(?:[?#].*)?$");
    private static final Pattern SSH = Pattern.compile(
            "^git@github\\.com:([A-Za-z0-9_.-]+)/([A-Za-z0-9_.-]+?)(?:\\.git)?$");
    private static final Pattern GENERIC = Pattern.compile("^https://[^\\s/]+/\\S+$");

    public static GitUrl parse(String input) {
        if (input == null) return null;
        String s = input.trim();
        if (s.isEmpty()) return null;
        Matcher m = SSH.matcher(s);
        if (m.matches()) {
            return github(m.group(1), m.group(2), null);
        }
        m = GITHUB.matcher(s);
        if (m.matches()) {
            String branch = m.group(3);
            if (branch != null && branch.contains("/")) {
                branch = branch.substring(0, branch.indexOf('/'));
            }
            return github(m.group(1), m.group(2), branch);
        }
        if (GENERIC.matcher(s).matches()) {
            String clean = s.replaceAll("/+$", "");
            return new GitUrl(clean, "", "", "");
        }
        return null;
    }

    private static GitUrl github(String owner, String repo, String branch) {
        return new GitUrl("https://github.com/" + owner + "/" + repo,
                branch == null ? "" : branch, owner, repo);
    }
}
