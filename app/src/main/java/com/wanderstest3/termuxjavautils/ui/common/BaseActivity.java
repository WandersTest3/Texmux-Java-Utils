package com.wanderstest3.termuxjavautils.ui.common;

import android.os.Bundle;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import com.google.android.material.button.MaterialButton;
import com.wanderstest3.termuxjavautils.R;

public abstract class BaseActivity extends AppCompatActivity {

    protected Ui ui;
    protected LinearLayout content;
    protected ScrollView scroll;

    protected abstract String screenTitle();

    protected boolean showBack() {
        return true;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        ui = new Ui(this);
        scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(getColor(R.color.purple_surface));
        content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        int pad = ui.dp(20);
        content.setPadding(pad, pad, pad, pad * 2);
        scroll.addView(content, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        setContentView(scroll);

        LinearLayout header = ui.row();
        header.setGravity(Gravity.CENTER_VERTICAL);
        if (showBack()) {
            MaterialButton back = ui.outlined("←");
            back.setMinWidth(0);
            back.setMinimumWidth(0);
            back.setLayoutParams(new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            back.setOnClickListener(v -> finish());
            header.addView(back);
        }
        TextView title = ui.text(screenTitle(), 22, R.color.text_primary, true);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        lp.setMarginStart(showBack() ? ui.dp(12) : 0);
        title.setLayoutParams(lp);
        title.setPadding(0, 0, 0, 0);
        header.addView(title);
        LinearLayout.LayoutParams hp = (LinearLayout.LayoutParams) header.getLayoutParams();
        hp.bottomMargin = ui.dp(16);
        content.addView(header, hp);
    }
}
