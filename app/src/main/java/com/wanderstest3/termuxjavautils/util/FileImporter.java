package com.wanderstest3.termuxjavautils.util;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.DocumentsContract;
import android.provider.MediaStore;
import android.provider.OpenableColumns;
import android.webkit.MimeTypeMap;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Locale;

public final class FileImporter {

    public static final String SUBDIR = "TermuxJavaUtils";
    private static final String PRIMARY = "/storage/emulated/0/";

    public static final class Imported {
        public String path = "";
        public String name = "";
        public boolean copied = false;
    }

    private FileImporter() {}

    public static Imported importUri(Context ctx, Uri uri) throws IOException {
        Imported r = new Imported();
        r.name = displayName(ctx, uri);
        String direct = directPath(uri);
        if (direct != null) {
            r.path = direct;
            if (r.name.isEmpty()) r.name = new File(direct).getName();
            return r;
        }
        if (r.name.isEmpty()) r.name = "arquivo-" + System.currentTimeMillis();
        r.name = safeName(r.name);
        r.path = copyToDownloads(ctx, uri, r);
        r.copied = true;
        return r;
    }

    static String directPath(Uri uri) {
        if (uri == null) return null;
        if ("file".equals(uri.getScheme())) {
            String p = uri.getPath();
            return p != null && p.startsWith(PRIMARY) && !p.startsWith(PRIMARY + "Android/") ? p : null;
        }
        if (uri.getAuthority() == null) return null;
        String auth = uri.getAuthority();
        String id;
        try {
            id = DocumentsContract.getDocumentId(uri);
        } catch (Exception e) {
            return null;
        }
        return pathFromDocument(auth, id);
    }

    public static String pathFromDocument(String auth, String id) {
        if (auth == null || id == null) return null;
        if ("com.android.externalstorage.documents".equals(auth)) {
            int c = id.indexOf(':');
            if (c <= 0) return null;
            String vol = id.substring(0, c);
            String rest = id.substring(c + 1);
            if (!"primary".equals(vol) || rest.isEmpty() || rest.startsWith("Android/")) return null;
            return PRIMARY + rest;
        }
        if ("com.android.providers.downloads.documents".equals(auth) && id.startsWith("raw:")) {
            String p = id.substring(4);
            return p.startsWith(PRIMARY) && !p.startsWith(PRIMARY + "Android/") ? p : null;
        }
        return null;
    }

    static String displayName(Context ctx, Uri uri) {
        if ("file".equals(uri.getScheme())) {
            String p = uri.getLastPathSegment();
            return p == null ? "" : p;
        }
        try (Cursor c = ctx.getContentResolver().query(uri, new String[]{OpenableColumns.DISPLAY_NAME},
                null, null, null)) {
            if (c != null && c.moveToFirst()) {
                String n = c.getString(0);
                if (n != null) return n;
            }
        } catch (Exception ignored) {
        }
        String p = uri.getLastPathSegment();
        return p == null ? "" : p.substring(p.lastIndexOf('/') + 1);
    }

    static String safeName(String n) {
        String s = n.replaceAll("[\\\\/:*?\"<>|\\u0000-\\u001f]", "_").trim();
        return s.isEmpty() ? "arquivo" : s;
    }

    private static String mimeFor(String name) {
        int dot = name.lastIndexOf('.');
        String ext = dot < 0 ? "" : name.substring(dot + 1).toLowerCase(Locale.ROOT);
        switch (ext) {
            case "apk": return "application/vnd.android.package-archive";
            case "jar": return "application/java-archive";
            case "zip": return "application/zip";
            default:
                String m = MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext);
                return m == null ? "application/octet-stream" : m;
        }
    }

    private static String copyToDownloads(Context ctx, Uri uri, Imported r) throws IOException {
        ContentResolver cr = ctx.getContentResolver();
        if (Build.VERSION.SDK_INT >= 29) {
            String rel = Environment.DIRECTORY_DOWNLOADS + "/" + SUBDIR;
            try {
                cr.delete(MediaStore.Downloads.EXTERNAL_CONTENT_URI,
                        MediaStore.MediaColumns.DISPLAY_NAME + "=? AND "
                                + MediaStore.MediaColumns.RELATIVE_PATH + " LIKE ?",
                        new String[]{r.name, rel + "%"});
            } catch (Exception ignored) {
            }
            ContentValues v = new ContentValues();
            v.put(MediaStore.MediaColumns.DISPLAY_NAME, r.name);
            v.put(MediaStore.MediaColumns.MIME_TYPE, mimeFor(r.name));
            v.put(MediaStore.MediaColumns.RELATIVE_PATH, rel);
            v.put(MediaStore.MediaColumns.IS_PENDING, 1);
            Uri out = cr.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, v);
            if (out == null) throw new IOException("Não consegui criar o arquivo em Downloads");
            try (InputStream in = cr.openInputStream(uri); OutputStream os = cr.openOutputStream(out)) {
                if (in == null || os == null) throw new IOException("Não consegui abrir o arquivo escolhido");
                pump(in, os);
            } catch (IOException e) {
                cr.delete(out, null, null);
                throw e;
            }
            ContentValues done = new ContentValues();
            done.put(MediaStore.MediaColumns.IS_PENDING, 0);
            cr.update(out, done, null, null);
            String finalName = displayName(ctx, out);
            if (!finalName.isEmpty()) r.name = finalName;
            return PRIMARY + rel + "/" + r.name;
        }
        File dir = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), SUBDIR);
        if (!dir.exists() && !dir.mkdirs()) {
            throw new IOException("Sem permissão para gravar em Downloads");
        }
        File dest = new File(dir, r.name);
        try (InputStream in = cr.openInputStream(uri); OutputStream os = new FileOutputStream(dest)) {
            if (in == null) throw new IOException("Não consegui abrir o arquivo escolhido");
            pump(in, os);
        }
        return dest.getAbsolutePath();
    }

    private static void pump(InputStream in, OutputStream out) throws IOException {
        byte[] buf = new byte[256 * 1024];
        int n;
        while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
        out.flush();
    }
}
