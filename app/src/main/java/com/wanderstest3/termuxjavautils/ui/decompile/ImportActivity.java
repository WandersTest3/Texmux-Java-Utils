package com.wanderstest3.termuxjavautils.ui.decompile;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.widget.Toast;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.wanderstest3.termuxjavautils.build.DecompileRequest;
import com.wanderstest3.termuxjavautils.ui.build.BuildActivity;
import com.wanderstest3.termuxjavautils.ui.common.BaseActivity;
import com.wanderstest3.termuxjavautils.ui.common.FilePicker;
import com.wanderstest3.termuxjavautils.util.ProfileStore;

public class ImportActivity extends BaseActivity {

    @Override
    protected String screenTitle() {
        return "Importar arquivo";
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Uri uri = uriFrom(getIntent());
        if (uri == null) {
            Toast.makeText(this, "Nenhum arquivo recebido.", Toast.LENGTH_LONG).show();
            finish();
            return;
        }
        content.addView(ui.hint("Trazendo o arquivo para o Termux Java Utils…"));
        FilePicker.importAndReport(this, uri, (path, name) -> route(path, name));
    }

    private static Uri uriFrom(Intent i) {
        if (i == null) return null;
        if (Intent.ACTION_SEND.equals(i.getAction())) {
            Object o = i.getParcelableExtra(Intent.EXTRA_STREAM);
            if (o instanceof Uri) return (Uri) o;
            return null;
        }
        return i.getData();
    }

    private void route(String path, String name) {
        if (!DecompileRequest.isSupported(name) && !DecompileRequest.isSupported(path)) {
            Toast.makeText(this, "Formato não suportado: use APK, JAR, AAR, DEX, CLASS ou ZIP.", Toast.LENGTH_LONG).show();
            finish();
            return;
        }
        if (DecompileRequest.isSourceArchive(name)) {
            new MaterialAlertDialogBuilder(this)
                    .setTitle(name)
                    .setMessage("O que fazer com este ZIP?")
                    .setPositiveButton("Compilar como projeto", (d, w) -> openBuild(path))
                    .setNegativeButton("Descompilar", (d, w) -> openDecompile(path))
                    .setNeutralButton("Cancelar", (d, w) -> finish())
                    .setOnCancelListener(d -> finish())
                    .show();
        } else {
            openDecompile(path);
        }
    }

    private void openDecompile(String path) {
        Intent i = new Intent(this, DecompileActivity.class);
        i.putExtra(DecompileActivity.EXTRA_INPUT, path);
        startActivity(i);
        finish();
    }

    private void openBuild(String path) {
        com.wanderstest3.termuxjavautils.build.BuildRequest r = ProfileStore.loadLast(this);
        r.fromGit = false;
        r.zipName = path;
        ProfileStore.saveLast(this, r);
        startActivity(new Intent(this, BuildActivity.class));
        finish();
    }
}
