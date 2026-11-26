package com.local.noti_filter;

import android.app.AlertDialog;
import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.InsetDrawable;
import android.graphics.drawable.RippleDrawable;
import android.graphics.drawable.StateListDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.RadioButton;
import android.widget.ScrollView;
import android.widget.TextView;

/** Shared modern card styling for AlertDialogs and form controls without external dependencies. */
final class ModernDialog {
    static final int SURFACE = Color.WHITE;
    static final int SURFACE_MUTED = Color.rgb(246, 248, 252);
    static final int SURFACE_TINT = Color.rgb(238, 244, 255);
    static final int SURFACE_SELECTED = Color.rgb(239, 246, 255);
    static final int SURFACE_DISABLED = Color.rgb(241, 245, 249);
    static final int BORDER = Color.rgb(226, 232, 240);
    static final int BORDER_INPUT = Color.rgb(203, 213, 225);
    static final int BORDER_SELECTED = Color.rgb(147, 197, 253);
    static final int INK = Color.rgb(17, 24, 39);
    static final int BODY = Color.rgb(51, 65, 85);
    static final int MUTED = Color.rgb(100, 116, 139);
    static final int HINT = Color.rgb(148, 163, 184);
    static final int BLUE = Color.rgb(37, 99, 235);
    static final int BLUE_DARK = Color.rgb(29, 78, 216);
    static final int DANGER = Color.rgb(220, 38, 38);
    static final int DANGER_SOFT = Color.rgb(254, 242, 242);

    private ModernDialog() {}

    static int dp(Context context, int value) {
        return Math.round(context.getResources().getDisplayMetrics().density * value);
    }

    static GradientDrawable rounded(Context context, int color, int radiusDp) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(color);
        d.setCornerRadius(dp(context, radiusDp));
        return d;
    }

    static GradientDrawable outlined(Context context, int fill, int stroke, float strokeDp, int radiusDp) {
        GradientDrawable d = rounded(context, fill, radiusDp);
        int width = Math.max(1, Math.round(context.getResources().getDisplayMetrics().density * strokeDp));
        d.setStroke(width, stroke);
        return d;
    }

    static RippleDrawable ripple(Context context, int rippleColor, int fill, int radiusDp) {
        return new RippleDrawable(ColorStateList.valueOf(rippleColor),
                rounded(context, fill, radiusDp), rounded(context, Color.WHITE, radiusDp));
    }

    static RippleDrawable outlinedRipple(Context context, int rippleColor, int fill, int stroke, float strokeDp, int radiusDp) {
        return new RippleDrawable(ColorStateList.valueOf(rippleColor),
                outlined(context, fill, stroke, strokeDp, radiusDp), rounded(context, Color.WHITE, radiusDp));
    }

    static StateListDrawable inputBackground(Context context) {
        StateListDrawable states = new StateListDrawable();
        states.addState(new int[]{-android.R.attr.state_enabled},
                outlined(context, SURFACE_DISABLED, BORDER, 1f, 14));
        states.addState(new int[]{android.R.attr.state_focused},
                outlined(context, SURFACE, BLUE, 1.5f, 14));
        states.addState(new int[]{},
                outlined(context, SURFACE_MUTED, BORDER_INPUT, 1f, 14));
        return states;
    }

    static AlertDialog.Builder builder(Context context, String title, String subtitle) {
        AlertDialog.Builder b = new AlertDialog.Builder(context);
        b.setCustomTitle(header(context, title, subtitle));
        return b;
    }

    static LinearLayout header(Context context, String title, String subtitle) {
        LinearLayout box = new LinearLayout(context);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(context, 24), dp(context, 22), dp(context, 24), dp(context, 10));
        TextView titleView = new TextView(context);
        titleView.setText(title);
        titleView.setTextSize(20);
        titleView.setTextColor(INK);
        titleView.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        box.addView(titleView, new LinearLayout.LayoutParams(-1, -2));
        if (subtitle != null && !subtitle.isEmpty()) {
            TextView sub = new TextView(context);
            sub.setText(subtitle);
            sub.setTextSize(13);
            sub.setTextColor(MUTED);
            sub.setLineSpacing(dp(context, 2), 1f);
            LinearLayout.LayoutParams sp = new LinearLayout.LayoutParams(-1, -2);
            sp.topMargin = dp(context, 6);
            box.addView(sub, sp);
        }
        return box;
    }

    static void applyChrome(AlertDialog dialog) {
        Window window = dialog.getWindow();
        if (window == null) return;
        Context context = dialog.getContext();
        GradientDrawable card = outlined(context, SURFACE, BORDER, 1f, 24);
        InsetDrawable inset = new InsetDrawable(card, dp(context, 14), dp(context, 20), dp(context, 14), dp(context, 20));
        window.setBackgroundDrawable(inset);
        window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
        WindowManager.LayoutParams lp = window.getAttributes();
        lp.dimAmount = 0.45f;
        window.setAttributes(lp);
    }

    static void styleButtons(AlertDialog dialog, boolean destructivePositive) {
        Context context = dialog.getContext();
        Button positive = dialog.getButton(AlertDialog.BUTTON_POSITIVE);
        Button negative = dialog.getButton(AlertDialog.BUTTON_NEGATIVE);
        Button neutral = dialog.getButton(AlertDialog.BUTTON_NEUTRAL);
        ViewGroup bar = null;
        if (positive != null && positive.getVisibility() == View.VISIBLE) {
            styleActionButton(context, positive, true, destructivePositive);
            if (positive.getParent() instanceof ViewGroup) bar = (ViewGroup) positive.getParent();
        }
        if (negative != null && negative.getVisibility() == View.VISIBLE) {
            styleActionButton(context, negative, false, false);
            if (bar == null && negative.getParent() instanceof ViewGroup) bar = (ViewGroup) negative.getParent();
        }
        if (neutral != null && neutral.getVisibility() == View.VISIBLE) {
            styleNeutralButton(context, neutral);
            if (bar == null && neutral.getParent() instanceof ViewGroup) bar = (ViewGroup) neutral.getParent();
        }
        if (bar != null) {
            bar.setPadding(dp(context, 18), dp(context, 10), dp(context, 18), dp(context, 18));
            if (bar.getParent() instanceof ViewGroup) {
                ViewGroup outer = (ViewGroup) bar.getParent();
                outer.setBackgroundColor(Color.TRANSPARENT);
            }
        }
        addButtonSpacing(context, negative, positive);
        addButtonSpacing(context, neutral, negative != null && negative.getVisibility() == View.VISIBLE ? negative : positive);
    }

    private static void styleActionButton(Context context, Button button, boolean primary, boolean destructive) {
        button.setAllCaps(false);
        button.setTextSize(14);
        button.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        button.setMinHeight(dp(context, 44));
        button.setMinimumHeight(dp(context, 44));
        button.setPadding(dp(context, 18), dp(context, 10), dp(context, 18), dp(context, 10));
        button.setStateListAnimator(null);
        button.setElevation(0);
        button.setTranslationZ(0);
        if (primary) {
            int fill = destructive ? DANGER : BLUE;
            button.setTextColor(Color.WHITE);
            button.setBackground(ripple(context, 0x33FFFFFF, fill, 12));
        } else {
            button.setTextColor(BODY);
            button.setBackground(ripple(context, 0x1A1E293B, SURFACE_DISABLED, 12));
        }
    }

    private static void styleNeutralButton(Context context, Button button) {
        button.setAllCaps(false);
        button.setTextSize(14);
        button.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        button.setMinHeight(dp(context, 44));
        button.setMinimumHeight(dp(context, 44));
        button.setPadding(dp(context, 14), dp(context, 10), dp(context, 14), dp(context, 10));
        button.setStateListAnimator(null);
        button.setElevation(0);
        button.setTranslationZ(0);
        button.setTextColor(BLUE_DARK);
        button.setBackground(ripple(context, 0x222563EB, SURFACE_TINT, 12));
    }

    private static void addButtonSpacing(Context context, Button left, Button right) {
        if (left == null || right == null) return;
        if (left.getVisibility() != View.VISIBLE || right.getVisibility() != View.VISIBLE) return;
        ViewGroup.LayoutParams lp = right.getLayoutParams();
        if (lp instanceof ViewGroup.MarginLayoutParams) {
            ((ViewGroup.MarginLayoutParams) lp).setMarginStart(dp(context, 8));
            right.setLayoutParams(lp);
        }
    }

    static void styleInput(Context context, EditText input) {
        input.setBackground(inputBackground(context));
        input.setPadding(dp(context, 14), dp(context, 12), dp(context, 14), dp(context, 12));
        input.setTextColor(INK);
        input.setHintTextColor(HINT);
        input.setTextSize(15);
    }

    static void styleSelectorButton(Context context, Button button) {
        button.setAllCaps(false);
        button.setTextSize(15);
        button.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        button.setTextColor(INK);
        button.setGravity(Gravity.CENTER_VERTICAL | Gravity.START);
        button.setSingleLine(false);
        button.setMaxLines(Integer.MAX_VALUE);
        button.setMinWidth(0);
        button.setMinimumWidth(0);
        button.setMinHeight(dp(context, 52));
        button.setMinimumHeight(dp(context, 52));
        button.setPadding(dp(context, 16), dp(context, 12), dp(context, 16), dp(context, 12));
        button.setBackground(outlinedRipple(context, 0x222563EB, SURFACE_MUTED, BORDER_INPUT, 1f, 14));
        button.setStateListAnimator(null);
        button.setElevation(0);
        button.setTranslationZ(0);
    }

    static TextView sectionLabel(Context context, String label) {
        TextView view = new TextView(context);
        view.setText(label);
        view.setTextSize(13);
        view.setTextColor(MUTED);
        view.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        return view;
    }

    static TextView badge(Context context, String label, int bgColor, int textColor) {
        TextView chip = new TextView(context);
        chip.setText(label);
        chip.setTextSize(12);
        chip.setTextColor(textColor);
        chip.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        chip.setPadding(dp(context, 10), dp(context, 4), dp(context, 10), dp(context, 4));
        chip.setBackground(rounded(context, bgColor, 8));
        return chip;
    }

    static LinearLayout optionCard(Context context, CheckBox check, String title, String description) {
        LinearLayout card = new LinearLayout(context);
        card.setOrientation(LinearLayout.HORIZONTAL);
        card.setGravity(Gravity.CENTER_VERTICAL);
        card.setMinimumHeight(dp(context, 54));
        card.setPadding(dp(context, 12), dp(context, 10), dp(context, 14), dp(context, 10));

        int[][] states = {new int[]{android.R.attr.state_checked}, new int[]{}};
        check.setButtonTintList(new ColorStateList(states, new int[]{BLUE, HINT}));
        check.setMinWidth(dp(context, 40));
        check.setMinHeight(dp(context, 40));
        check.setContentDescription(title);
        card.addView(check, new LinearLayout.LayoutParams(-2, -2));

        LinearLayout labels = new LinearLayout(context);
        labels.setOrientation(LinearLayout.VERTICAL);
        TextView titleView = new TextView(context);
        titleView.setText(title);
        titleView.setTextSize(14);
        titleView.setTextColor(INK);
        titleView.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        labels.addView(titleView, new LinearLayout.LayoutParams(-1, -2));

        if (description != null && !description.isEmpty()) {
            TextView descView = new TextView(context);
            descView.setText(description);
            descView.setTextSize(12);
            descView.setTextColor(MUTED);
            descView.setLineSpacing(dp(context, 1), 1f);
            LinearLayout.LayoutParams dpParams = new LinearLayout.LayoutParams(-1, -2);
            dpParams.topMargin = dp(context, 2);
            labels.addView(descView, dpParams);
        }
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, -2, 1);
        lp.leftMargin = dp(context, 6);
        card.addView(labels, lp);

        updateOptionCard(context, card, check);
        card.setOnClickListener(v -> {
            if (check.isEnabled()) check.performClick();
        });
        return card;
    }

    static void updateOptionCard(Context context, LinearLayout card, CheckBox check) {
        boolean enabled = check.isEnabled();
        boolean checked = check.isChecked();
        int fill = !enabled ? SURFACE_DISABLED : checked ? SURFACE_SELECTED : SURFACE_MUTED;
        int stroke = !enabled ? BORDER : checked ? BORDER_SELECTED : BORDER;
        card.setBackground(outlinedRipple(context, 0x1A2563EB, fill, stroke, checked && enabled ? 1.2f : 1f, 14));
        card.setAlpha(enabled ? 1f : 0.58f);
    }

    static void styleRadioOption(Context context, RadioButton option) {
        option.setTextSize(15);
        option.setTextColor(INK);
        option.setMinHeight(dp(context, 50));
        option.setMinimumHeight(dp(context, 50));
        option.setPadding(dp(context, 12), dp(context, 10), dp(context, 14), dp(context, 10));
        int[][] states = {new int[]{android.R.attr.state_checked}, new int[]{}};
        option.setButtonTintList(new ColorStateList(states, new int[]{BLUE, HINT}));
        updateRadioOption(context, option);
    }

    static void updateRadioOption(Context context, RadioButton option) {
        boolean checked = option.isChecked();
        boolean enabled = option.isEnabled();
        int fill = !enabled ? SURFACE_DISABLED : checked ? SURFACE_SELECTED : SURFACE_MUTED;
        int stroke = !enabled ? BORDER : checked ? BORDER_SELECTED : BORDER;
        option.setBackground(outlined(context, fill, stroke, checked && enabled ? 1.2f : 1f, 14));
        option.setAlpha(enabled ? 1f : 0.55f);
    }

    static void styleListView(Context context, ListView list, int dividerDp) {
        list.setDivider(new ColorDrawable(Color.TRANSPARENT));
        list.setDividerHeight(dp(context, dividerDp));
        list.setClipToPadding(false);
        list.setPadding(0, dp(context, 4), 0, dp(context, 8));
        list.setSelector(new ColorDrawable(Color.TRANSPARENT));
    }

    static ScrollView messageBody(Context context, String message) {
        TextView body = new TextView(context);
        body.setText(message);
        body.setTextSize(14);
        body.setTextColor(BODY);
        body.setLineSpacing(dp(context, 4), 1f);
        body.setPadding(dp(context, 24), dp(context, 6), dp(context, 24), dp(context, 14));
        ScrollView scroll = new ScrollView(context);
        scroll.addView(body);
        return scroll;
    }

    static void show(AlertDialog dialog, boolean destructivePositive) {
        dialog.setOnShowListener(ignored -> styleButtons(dialog, destructivePositive));
        dialog.show();
        applyChrome(dialog);
    }
}
