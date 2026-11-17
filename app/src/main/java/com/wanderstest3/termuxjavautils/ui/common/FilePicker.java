package com.wanderstest3.termuxjavautils.ui.common;

import android.net.Uri;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.wanderstest3.termuxjavautils.util.FileImporter;

public final class FilePicker {

    public interface Callback {
        void onPicked(String path, String name);
    }

    private final BaseActivity activity;
    private final Callback callback;
    private final ActivityResultLauncher<String[]> launcher;

    public FilePicker(BaseActivity activity, Callback callback) {
        this.activity = activity;
        this.callback = callback;
        this.launcher = activity.registerForActivityResult(
                new ActivityResultContracts.OpenDocument(), this::handle);
    }

    public void pick(String... mimeTypes) {
        launcher.launch(mimeTypes == null || mimeTypes.length == 0 ? new String[]{"*/*"} : mimeTypes);
    }

    private void handle(Uri uri) {
        if (uri == null) return;
        importAndReport(activity, uri, callback);
    }

    public static void importAndReport(BaseActivity a, Uri uri, Callback cb) {
        LinearLayout box = new LinearLayout(a);
        box.setOrientation(LinearLayout.VERTICAL);
        int pad = Math.round(20 * a.getResources().getDisplayMetrics().density);
        box.setPadding(pad, pad, pad, pad);
        TextView t = new TextView(a);
        t.setText("Trazendo o arquivo para o app…");
        box.addView(t, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        ProgressBar pb = new ProgressBar(a, null, android.R.attr.progressBarStyleHorizontal);
        pb.setIndeterminate(true);
        box.addView(pb, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        final AlertDialog dlg = new MaterialAlertDialogBuilder(a)
                .setTitle("Importando")
                .setView(box)
                .setCancelable(false)
                .create();
        dlg.show();
        new Thread(() -> {
            FileImporter.Imported res = null;
            String error = null;
            try {
                res = FileImporter.importUri(a, uri);
            } catch (Exception e) {
                error = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
            }
            final FileImporter.Imported fr = res;
            final String fe = error;
            a.runOnUiThread(() -> {
                if (a.isFinishing() || a.isDestroyed()) return;
                dlg.dismiss();
                if (fr == null) {
                    Toast.makeText(a, "Não consegui importar: " + fe, Toast.LENGTH_LONG).show();
                    return;
                }
                if (fr.copied) {
                    Toast.makeText(a, "Copiado para Downloads/" + FileImporter.SUBDIR, Toast.LENGTH_SHORT).show();
                }
                cb.onPicked(fr.path, fr.name);
            });
        }, "tju-import").start();
    }
}
