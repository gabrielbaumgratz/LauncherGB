package io.freemytv;

import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.text.InputType;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * The questions the settings ask (a name, a city, a team, "forget this device?") in the home screen's own look: a
 * rounded card, its fonts, the same white focus on the buttons, black or white by the theme. The keyboard is the TV's.
 */
final class Prompt {
    private Prompt() {}

    interface Answer { void onAnswer(String text); }

    /** Asks for a line of text; `ok` gets it when the person confirms (OK on the button or Enter on the keyboard). */
    static void ask(Activity a, String title, String message, String hint, String initial, int action, String okLabel, Answer ok) {
        show(a, title, message, true, hint, initial, action, okLabel, ok);
    }

    /** Asks yes or no. */
    static void confirm(Activity a, String title, String message, String okLabel, Runnable yes) {
        show(a, title, message, false, "", "", EditorInfo.IME_ACTION_DONE, okLabel, text -> yes.run());
    }

    private static void show(Activity a, String title, String message, boolean field, String hint, String initial, int action,
            String okLabel, Answer ok) {
        final boolean dark = HomeData.get(a).isNight(System.currentTimeMillis());
        final int ink = dark ? 0xF2FFFFFF : 0xFF1D1D1F, dim = dark ? 0xADFFFFFF : 0xFF6E6E73;
        final float dp = a.getResources().getDisplayMetrics().density;
        final Dialog dialog = new Dialog(a, android.R.style.Theme_Material_Dialog_NoActionBar);
        Window window = dialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            window.setDimAmount(0.55f);
            window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
            window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE | WindowManager.LayoutParams.SOFT_INPUT_ADJUST_PAN);
        }
        LinearLayout box = new LinearLayout(a);
        box.setOrientation(LinearLayout.VERTICAL);
        int pad = Math.round(28 * dp);
        box.setPadding(pad, pad, pad, Math.round(22 * dp));
        GradientDrawable card = new GradientDrawable();
        card.setColor(dark ? 0xFF1C1C1E : 0xFFF5F5F7);
        card.setCornerRadius(26 * dp);
        card.setStroke(Math.round(Math.max(1, dp)), dark ? 0x33FFFFFF : 0x1A000000);
        box.setBackground(card);

        box.addView(text(a, title, 21, Ui.semibold, ink, dp), new LinearLayout.LayoutParams(-1, -2));
        if (!message.isEmpty()) {
            TextView m = text(a, message, 14, Ui.regular, dim, dp);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
            lp.topMargin = Math.round(6 * dp);
            box.addView(m, lp);
        }
        final EditText input = field ? new EditText(a) : null;
        if (input != null) {
            input.setSingleLine(true);
            input.setText(initial);
            input.setSelectAllOnFocus(true);
            input.setHint(hint);
            input.setHintTextColor(dim);
            input.setTextColor(ink);
            input.setTypeface(Ui.medium);
            input.setTextSize(TypedValue.COMPLEX_UNIT_SP, 20);
            input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_WORDS);
            input.setImeOptions(action);
            GradientDrawable line = new GradientDrawable();
            line.setColor(dark ? 0x14FFFFFF : 0x0F000000);
            line.setCornerRadius(14 * dp);
            input.setBackground(line);
            input.setPadding(Math.round(16 * dp), Math.round(12 * dp), Math.round(16 * dp), Math.round(12 * dp));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
            lp.topMargin = Math.round(18 * dp);
            box.addView(input, lp);
        }
        LinearLayout buttons = new LinearLayout(a);
        buttons.setOrientation(LinearLayout.HORIZONTAL);
        buttons.setGravity(Gravity.END);
        final Runnable confirmIt = () -> {
            dialog.dismiss();
            ok.onAnswer(input == null ? "" : input.getText().toString());
        };
        TextView cancel = button(a, Ui.tr("Cancel"), false, dark, dp);
        TextView okButton = button(a, okLabel, true, dark, dp);
        cancel.setOnClickListener(v -> dialog.dismiss());
        okButton.setOnClickListener(v -> confirmIt.run());
        LinearLayout.LayoutParams bp = new LinearLayout.LayoutParams(-2, -2);
        bp.leftMargin = Math.round(10 * dp);
        buttons.addView(cancel, new LinearLayout.LayoutParams(-2, -2));
        buttons.addView(okButton, bp);
        LinearLayout.LayoutParams bl = new LinearLayout.LayoutParams(-1, -2);
        bl.topMargin = Math.round(20 * dp);
        box.addView(buttons, bl);
        if (input != null) {
            input.setOnEditorActionListener((v, actionId, event) -> {
                confirmIt.run();
                return true;
            });
        }
        dialog.setContentView(box, new ViewGroup.LayoutParams(Math.round(560 * dp), ViewGroup.LayoutParams.WRAP_CONTENT));
        dialog.show();
        if (input != null) input.requestFocus();
        else okButton.requestFocus();
    }

    private static TextView text(Context c, String s, int sp, Typeface face, int color, float dp) {
        TextView t = new TextView(c);
        t.setText(s);
        t.setTextColor(color);
        t.setTypeface(face);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        return t;
    }

    /** A pill: glass-gray at rest, the home screen's white when focused (with dark text). */
    private static TextView button(Context c, String label, boolean primary, boolean dark, float dp) {
        TextView b = new TextView(c);
        b.setText(label);
        b.setTypeface(Ui.semibold);
        b.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        b.setGravity(Gravity.CENTER);
        b.setPadding(Math.round(26 * dp), Math.round(11 * dp), Math.round(26 * dp), Math.round(11 * dp));
        b.setFocusable(true);
        b.setClickable(true);
        GradientDrawable rest = new GradientDrawable();
        rest.setCornerRadius(40 * dp);
        rest.setColor(primary ? (dark ? 0x40FFFFFF : 0xFF1D1D1F) : (dark ? 0x1FFFFFFF : 0x14000000));
        GradientDrawable focused = new GradientDrawable();
        focused.setCornerRadius(40 * dp);
        focused.setColor(0xFFFFFFFF);
        focused.setStroke(Math.round(2 * dp), dark ? 0x00FFFFFF : 0x33000000);
        StateListDrawable states = new StateListDrawable();
        states.addState(new int[] {android.R.attr.state_focused}, focused);
        states.addState(new int[] {}, rest);
        b.setBackground(states);
        int restInk = primary && !dark ? 0xFFFFFFFF : dark ? 0xF2FFFFFF : 0xFF1D1D1F;
        b.setTextColor(restInk);
        b.setOnFocusChangeListener((v, has) -> b.setTextColor(has ? 0xFF1C1C1E : restInk));
        return b;
    }
}
