package com.local.noti_filter;

import android.app.Activity;
import android.app.AlertDialog;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public final class HistoryActivity extends Activity {
    private final List<HistoryStore.Entry> entries = new ArrayList<>();
    private final Adapter adapter = new Adapter();
    private TextView status;
    private AlertDialog clearDialog;
    private volatile int requestId;
    private int dp(int n) { return Math.round(getResources().getDisplayMetrics().density * n); }
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(16), dp(20), dp(16), dp(12));
        root.setOnApplyWindowInsetsListener((view, insets) -> {
            view.setPadding(dp(16), dp(20) + insets.getSystemWindowInsetTop(),
                    dp(16), dp(12) + insets.getSystemWindowInsetBottom());
            return insets;
        });
        root.setBackgroundColor(Color.rgb(247, 249, 252));
        LinearLayout header = new AdaptiveRow(this, false);
        header.setGravity(Gravity.CENTER_VERTICAL);
        TextView title = label("알림 처리 이력", 24, true);
        header.addView(title, new LinearLayout.LayoutParams(0, -2, 1));
        Button refresh = actionButton("새로고침", false, false);
        header.addView(refresh, new LinearLayout.LayoutParams(-2, -2)); root.addView(header);
        refresh.setOnClickListener(v -> refresh());
        status = label("불러오는 중…", 13, false);
        status.setTextColor(ModernDialog.MUTED);
        LinearLayout.LayoutParams statusParams = new LinearLayout.LayoutParams(-1, -2);
        statusParams.topMargin = dp(8);
        root.addView(status, statusParams);
        TextView meta = label("처리 확인 시간 · 최근 1,000개 · 이전 버전 기록 포함", 12, false);
        meta.setTextColor(ModernDialog.MUTED);
        LinearLayout.LayoutParams metaParams = new LinearLayout.LayoutParams(-1, -2);
        metaParams.topMargin = dp(2);
        root.addView(meta, metaParams);
        Button clear = actionButton("저장된 이력 모두 삭제", false, true);
        LinearLayout.LayoutParams clearParams = new LinearLayout.LayoutParams(-1, -2);
        clearParams.topMargin = dp(12); clearParams.bottomMargin = dp(10);
        root.addView(clear, clearParams);
        clear.setOnClickListener(v -> {
            if (isFinishing() || isDestroyed() || clearDialog != null) return;
            clearDialog = ModernDialog.builder(this, "저장된 알림 이력을 삭제할까요?", null)
                .setView(ModernDialog.messageBody(this,
                        "기기 내부에 보관된 알림 내용과 아이콘을 삭제합니다. 필터 규칙은 유지되며 새로 숨긴 알림은 다시 기록될 수 있습니다."))
                .setNegativeButton("취소", null)
                .setPositiveButton("삭제", (dialog, which) -> {
                    clear.setEnabled(false);
                    requestId++;
                    HistoryStore.get(this).clear(() -> {
                        if (isFinishing() || isDestroyed()) return;
                        clear.setEnabled(true); refresh();
                    }, () -> {
                        if (isFinishing() || isDestroyed()) return;
                        clear.setEnabled(true); status.setText("이력을 삭제하지 못했습니다. 다시 시도해 주세요.");
                    });
                }).create();
            clearDialog.setOnDismissListener(dialog -> clearDialog = null);
            ModernDialog.show(clearDialog, true);
        });
        ListView list = new ListView(this);
        ModernDialog.styleListView(this, list, 10);
        list.setAdapter(adapter);
        root.addView(list, new LinearLayout.LayoutParams(-1, 0, 1));
        Button back = actionButton("메인화면으로 돌아가기", true, false);
        LinearLayout.LayoutParams backParams = new LinearLayout.LayoutParams(-1, -2);
        backParams.topMargin = dp(10);
        back.setOnClickListener(v -> finish()); root.addView(back, backParams);
        setContentView(root);
    }
    @Override public void onResume() { super.onResume(); refresh(); }
    @Override public void onDestroy() {
        requestId++;
        if (clearDialog != null) {
            clearDialog.dismiss();
            clearDialog = null;
        }
        super.onDestroy();
    }
    private void refresh() {
        int current = ++requestId;
        status.setText("불러오는 중…");
        HistoryStore.get(this).load(() -> current == requestId, result -> {
            if (isFinishing() || isDestroyed() || current != requestId) return;
            entries.clear(); entries.addAll(result); adapter.notifyDataSetChanged();
            status.setText(entries.isEmpty() ? "아직 처리한 알림이 없습니다." : "처리 이력 " + entries.size() + "개 · 최신순");
        }, () -> {
            if (!isFinishing() && !isDestroyed() && current == requestId) status.setText("이력을 불러오지 못했습니다. 다시 시도해 주세요.");
        });
    }
    private Button actionButton(String text, boolean primary, boolean destructive) {
        Button b = new Button(this);
        b.setText(text);
        b.setAllCaps(false);
        b.setTextSize(14);
        b.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        b.setGravity(Gravity.CENTER);
        b.setMinWidth(0);
        b.setMinimumWidth(0);
        b.setMinHeight(dp(46));
        b.setMinimumHeight(dp(46));
        b.setPadding(dp(16), dp(10), dp(16), dp(10));
        b.setStateListAnimator(null);
        b.setElevation(0);
        b.setTranslationZ(0);
        if (primary) {
            b.setTextColor(Color.WHITE);
            b.setBackground(ModernDialog.ripple(this, 0x33FFFFFF, ModernDialog.BLUE, 12));
        } else if (destructive) {
            b.setTextColor(ModernDialog.DANGER);
            b.setBackground(ModernDialog.ripple(this, 0x22DC2626, ModernDialog.DANGER_SOFT, 12));
        } else {
            b.setTextColor(ModernDialog.BLUE_DARK);
            b.setBackground(ModernDialog.ripple(this, 0x222563EB, ModernDialog.SURFACE_TINT, 12));
        }
        return b;
    }
    private TextView label(String value, int size, boolean bold) {
        TextView t = new TextView(this); t.setText(value); t.setTextSize(size);
        t.setTextColor(ModernDialog.INK);
        if (bold) t.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        return t;
    }
    private final class Adapter extends BaseAdapter {
        private final SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault());
        @Override public int getCount() { return entries.size(); }
        @Override public HistoryStore.Entry getItem(int position) { return entries.get(position); }
        @Override public long getItemId(int position) { return position; }
        @Override public View getView(int position, View reusable, ViewGroup parent) {
            HistoryStore.Entry item = getItem(position);
            LinearLayout row = new LinearLayout(HistoryActivity.this);
            row.setPadding(dp(14), dp(14), dp(14), dp(14));
            row.setBackground(ModernDialog.outlined(HistoryActivity.this,
                    ModernDialog.SURFACE, ModernDialog.BORDER, 1f, 16));
            ImageView icon = new ImageView(HistoryActivity.this);
            if (item.icon != null) icon.setImageBitmap(item.icon); else icon.setImageResource(R.drawable.app_icon);
            icon.setContentDescription(item.app + " 아이콘");
            row.addView(icon, new LinearLayout.LayoutParams(dp(42), dp(42)));
            LinearLayout content = new LinearLayout(HistoryActivity.this);
            content.setOrientation(LinearLayout.VERTICAL);
            content.addView(label(item.app, 15, true));
            TextView time = label(format.format(new Date(item.time)), 12, false);
            time.setTextColor(ModernDialog.MUTED);
            content.addView(time);
            TextView pkg = label(item.pkg, 11, false);
            pkg.setTextColor(ModernDialog.HINT);
            content.addView(pkg);
            TextView body = label(item.content.isEmpty() ? "(내용 없음)" : item.content, 14, false);
            body.setTextColor(item.content.isEmpty() ? ModernDialog.MUTED : ModernDialog.BODY);
            body.setTextIsSelectable(true); body.setPadding(0, dp(6), 0, 0); content.addView(body);
            LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(0, -2, 1); cp.leftMargin = dp(12);
            row.addView(content, cp);
            return row;
        }
    }
}
