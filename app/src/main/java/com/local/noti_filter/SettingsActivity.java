package com.local.noti_filter;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ComponentName;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.os.Bundle;
import android.provider.Settings;
import android.view.Gravity;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

/** Local settings entry point; the existing disclosure remains before Android permission settings. */
public final class SettingsActivity extends Activity {
    private static final int BLUE = Color.rgb(37, 99, 235);
    private static final int INK = Color.rgb(28, 39, 58);
    private static final int MUTED = Color.rgb(97, 108, 126);
    private LinearLayout root;
    private AlertDialog settingsDialog;

    @Override public void onCreate(Bundle state) { super.onCreate(state); render(); }
    @Override protected void onResume() { super.onResume(); if (root != null) render(); }
    @Override protected void onDestroy() {
        if (settingsDialog != null) settingsDialog.dismiss();
        super.onDestroy();
    }
    private void showSettingsDialog(AlertDialog dialog) {
        if ((settingsDialog != null && settingsDialog.isShowing()) || isFinishing() || isDestroyed()) return;
        settingsDialog = dialog;
        dialog.setOnDismissListener(closed -> {
            if (settingsDialog == dialog) settingsDialog = null;
        });
        dialog.show();
    }
    private int dp(int value) { return Math.round(getResources().getDisplayMetrics().density * value); }
    private GradientDrawable shape(int color) {
        GradientDrawable background = new GradientDrawable();
        background.setColor(color); background.setCornerRadius(dp(16)); return background;
    }
    private TextView text(String value, int size, int color, boolean bold) {
        TextView view = new TextView(this); view.setText(value); view.setTextSize(size); view.setTextColor(color);
        if (bold) view.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        return view;
    }
    private Button button(String label) {
        Button view = new Button(this);
        view.setText(label); view.setAllCaps(false); view.setTextSize(14);
        view.setTypeface(Typeface.DEFAULT, Typeface.BOLD); view.setTextColor(BLUE);
        view.setGravity(Gravity.CENTER); view.setSingleLine(false); view.setMaxLines(Integer.MAX_VALUE);
        view.setMinWidth(0); view.setMinimumWidth(0); view.setMinHeight(dp(48)); view.setMinimumHeight(dp(48));
        view.setPadding(dp(16), dp(12), dp(16), dp(12));
        view.setBackground(new RippleDrawable(ColorStateList.valueOf(0x222563EB), shape(Color.rgb(234, 241, 255)), null));
        view.setStateListAnimator(null); view.setElevation(0); view.setTranslationZ(0);
        return view;
    }
    private LinearLayout card() {
        LinearLayout card = new LinearLayout(this); card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(18), dp(18), dp(18), dp(18)); card.setBackground(shape(Color.WHITE));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2); params.topMargin = dp(18);
        root.addView(card, params); return card;
    }
    private void render() {
        ScrollView scroll = new ScrollView(this); scroll.setFillViewport(true);
        scroll.setBackgroundColor(Color.rgb(247, 249, 252)); scroll.setClipToPadding(true);
        scroll.setOnApplyWindowInsetsListener((view, insets) -> {
            view.setPadding(insets.getSystemWindowInsetLeft(), insets.getSystemWindowInsetTop(),
                    insets.getSystemWindowInsetRight(), insets.getSystemWindowInsetBottom());
            return insets;
        });
        root = new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(20), dp(28), dp(20), dp(28)); scroll.addView(root); setContentView(scroll);
        AdaptiveRow heading = new AdaptiveRow(this, false);
        heading.addView(text("설정", 26, INK, true), new LinearLayout.LayoutParams(0, -2, 1));
        Button back = button("돌아가기"); back.setId(R.id.settings_back);
        back.setOnClickListener(view -> finish()); heading.addView(back, new LinearLayout.LayoutParams(-2, -2)); root.addView(heading);

        LinearLayout access = card(); boolean granted = hasAccess();
        access.addView(text(granted ? "●  알림 접근 허용됨" : "●  알림 접근 권한 필요", 17,
                granted ? Color.rgb(24, 137, 84) : Color.rgb(196, 107, 15), true));
        TextView info = text("알림 내용을 읽어야 문구를 검사할 수 있습니다. 데이터는 기기 밖으로 전송하지 않습니다.", 13, MUTED, false);
        LinearLayout.LayoutParams infoParams = new LinearLayout.LayoutParams(-1, -2); infoParams.topMargin = dp(8); access.addView(info, infoParams);
        Button permission = button("알림 접근 설정 열기"); permission.setId(R.id.settings_notification_access);
        LinearLayout.LayoutParams permissionParams = new LinearLayout.LayoutParams(-1, -2); permissionParams.topMargin = dp(14);
        access.addView(permission, permissionParams); permission.setOnClickListener(view -> showAccessDisclosure());

        LinearLayout privacy = card(); privacy.addView(text("개인정보와 권한", 17, INK, true));
        Button policy = button("개인정보·권한 안내"); policy.setId(R.id.settings_privacy);
        LinearLayout.LayoutParams policyParams = new LinearLayout.LayoutParams(-1, -2); policyParams.topMargin = dp(12);
        privacy.addView(policy, policyParams);
        policy.setOnClickListener(view -> StoreInfo.show(this, this::showSettingsDialog));
    }
    private boolean hasAccess() {
        String listeners = Settings.Secure.getString(getContentResolver(), "enabled_notification_listeners");
        if (listeners == null) return false;
        ComponentName self = new ComponentName(this, FilterService.class);
        for (String part : listeners.split(":")) if (self.equals(ComponentName.unflattenFromString(part))) return true;
        return false;
    }
    private void showAccessDisclosure() {
        if (settingsDialog != null || isFinishing() || isDestroyed()) return;
        AlertDialog disclosure = new AlertDialog.Builder(this).setTitle("알림 접근 권한 사용 안내")
                .setMessage("규칙 검사를 위해 알림의 앱·제목·본문을 읽고, 일치하는 알림을 제거합니다. 삭제한 알림 내용은 기기 내부 이력에 최대 1,000개 저장하며 이력 화면에서 지울 수 있습니다. 개발자 서버로 전송하지 않습니다. 권한을 거부하면 자동 필터만 사용할 수 없으며 Android 설정에서 언제든 해제할 수 있습니다.")
                .setNegativeButton("취소", null)
                .setNeutralButton("개인정보 안내", (dialog, which) -> {
                    dialog.dismiss();
                    StoreInfo.show(this, this::showSettingsDialog);
                })
                .setPositiveButton("동의하고 설정 열기", (dialog, which) -> {
                    if (settingsDialog != dialog || isFinishing() || isDestroyed()) return;
                    try { startActivity(new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)); }
                    catch (android.content.ActivityNotFoundException unavailable) {
                        Toast.makeText(this, "이 기기에서 알림 접근 설정을 열 수 없습니다.", Toast.LENGTH_SHORT).show();
                    }
                }).create();
        showSettingsDialog(disclosure);
    }
}
