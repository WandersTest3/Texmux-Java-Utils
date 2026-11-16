package com.wanderstest3.termuxjavautils.ui.common;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.graphics.Typeface;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.ColorRes;
import androidx.core.content.ContextCompat;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.progressindicator.LinearProgressIndicator;
import com.google.android.material.radiobutton.MaterialRadioButton;
import com.google.android.material.switchmaterial.SwitchMaterial;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.wanderstest3.termuxjavautils.R;

public final class Ui {

    public static final class Card {
        public MaterialCardView view;
        public LinearLayout body;
        public TextView title;
        public TextView sub;
    }

    public static final class Input {
        public TextInputLayout layout;
        public TextInputEditText edit;

        public String get() {
            return edit.getText() == null ? "" : edit.getText().toString().trim();
        }

        public void set(String s) {
            edit.setText(s == null ? "" : s);
        }
    }

    private final Context ctx;
    private final LayoutInflater inflater;
    private final LinearLayout paramsParent;

    public Ui(Context ctx) {
        this.ctx = ctx;
        this.inflater = LayoutInflater.from(ctx);
        this.paramsParent = new LinearLayout(ctx);
        this.paramsParent.setOrientation(LinearLayout.VERTICAL);
    }

    private View inflate(int res) {
        return inflater.inflate(res, paramsParent, false);
    }

    public int dp(int v) {
        return Math.round(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v,
                ctx.getResources().getDisplayMetrics()));
    }

    public TextView text(String s, int sp, @ColorRes int color, boolean bold) {
        TextView t = new TextView(ctx);
        t.setText(s);
        t.setTextSize(sp);
        t.setTextColor(ContextCompat.getColor(ctx, color));
        if (bold) t.setTypeface(t.getTypeface(), Typeface.BOLD);
        t.setLineSpacing(0, 1.15f);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = dp(8);
        t.setLayoutParams(lp);
        return t;
    }

    public TextView body(String s) {
        return text(s, 14, R.color.text_primary, false);
    }

    public TextView hint(String s) {
        return text(s, 13, R.color.text_secondary, false);
    }

    public TextView mono(String s) {
        TextView t = text(s, 11, R.color.purple_on_surface, false);
        t.setTypeface(Typeface.MONOSPACE);
        t.setTextIsSelectable(true);
        return t;
    }

    public Card card(String title, String sub) {
        Card c = new Card();
        c.view = (MaterialCardView) inflate(R.layout.view_card);
        c.title = c.view.findViewById(R.id.card_title);
        c.sub = c.view.findViewById(R.id.card_sub);
        c.body = c.view.findViewById(R.id.card_body);
        c.title.setText(title);
        if (sub == null || sub.isEmpty()) {
            c.sub.setVisibility(View.GONE);
        } else {
            c.sub.setText(sub);
        }
        return c;
    }

    public Input input(String hint, String helper, String placeholder) {
        Input in = new Input();
        in.layout = (TextInputLayout) inflate(R.layout.view_input);
        in.edit = in.layout.findViewById(R.id.et);
        in.layout.setHint(hint);
        if (helper != null && !helper.isEmpty()) in.layout.setHelperText(helper);
        if (placeholder != null && !placeholder.isEmpty()) in.layout.setPlaceholderText(placeholder);
        return in;
    }

    public MaterialButton button(String label) {
        MaterialButton b = (MaterialButton) inflate(R.layout.view_button);
        b.setText(label);
        return b;
    }

    public MaterialButton outlined(String label) {
        MaterialButton b = (MaterialButton) inflate(R.layout.view_button_outlined);
        b.setText(label);
        return b;
    }

    public SwitchMaterial toggle(String label, boolean checked) {
        SwitchMaterial s = (SwitchMaterial) inflate(R.layout.view_switch);
        s.setText(label);
        s.setChecked(checked);
        return s;
    }

    public MaterialRadioButton radio(String label, int id) {
        MaterialRadioButton r = (MaterialRadioButton) inflate(R.layout.view_radio);
        r.setText(label);
        r.setId(id);
        return r;
    }

    public RadioGroup radioGroup() {
        RadioGroup g = new RadioGroup(ctx);
        g.setOrientation(RadioGroup.VERTICAL);
        g.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        return g;
    }

    public LinearProgressIndicator progress() {
        return (LinearProgressIndicator) inflate(R.layout.view_progress);
    }

    public LinearLayout row() {
        LinearLayout l = new LinearLayout(ctx);
        l.setOrientation(LinearLayout.HORIZONTAL);
        l.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        return l;
    }

    public LinearLayout column() {
        LinearLayout l = new LinearLayout(ctx);
        l.setOrientation(LinearLayout.VERTICAL);
        l.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        return l;
    }

    public void weight(View v, int marginEndDp) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        lp.setMarginEnd(dp(marginEndDp));
        v.setLayoutParams(lp);
    }

    public static void copy(Context ctx, String label, String text) {
        ClipboardManager cm = (ClipboardManager) ctx.getSystemService(Context.CLIPBOARD_SERVICE);
        cm.setPrimaryClip(ClipData.newPlainText(label, text));
        Toast.makeText(ctx, "Copiado", Toast.LENGTH_SHORT).show();
    }
}
