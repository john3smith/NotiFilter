package com.local.noti_filter;

import android.app.Activity;
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
    private int requestId;
    private int dp(int n) { return Math.round(getResources().getDisplayMetrics().density * n); }
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(16), dp(20), dp(16), dp(12));
        root.setBackgroundColor(Color.rgb(247, 249, 252));
        LinearLayout header = new LinearLayout(this);
        header.setGravity(Gravity.CENTER_VERTICAL);
        TextView title = label("삭제한 알림 이력", 23, true);
        header.addView(title, new LinearLayout.LayoutParams(0, -2, 1));
        Button refresh = new Button(this); refresh.setText("새로고침");
        header.addView(refresh); root.addView(header);
        refresh.setOnClickListener(v -> refresh());
        status = label("불러오는 중…", 13, false); root.addView(status);
        root.addView(label("삭제 확인 시간 · 최근 1,000개 · 기기 내부에만 저장", 12, false));
        ListView list = new ListView(this);
        list.setAdapter(adapter);
        root.addView(list, new LinearLayout.LayoutParams(-1, 0, 1));
        Button back = new Button(this); back.setText("메인화면으로 돌아가기");
        back.setOnClickListener(v -> finish()); root.addView(back);
        setContentView(root);
    }
    @Override public void onResume() { super.onResume(); refresh(); }
    @Override public void onDestroy() { requestId++; super.onDestroy(); }
    private void refresh() {
        int current = ++requestId;
        status.setText("불러오는 중…");
        HistoryStore.get(this).load(result -> {
            if (isFinishing() || isDestroyed() || current != requestId) return;
            entries.clear(); entries.addAll(result); adapter.notifyDataSetChanged();
            status.setText(entries.isEmpty() ? "아직 삭제한 알림이 없습니다." : "삭제 이력 " + entries.size() + "개 · 최신순");
        }, () -> {
            if (!isFinishing() && !isDestroyed() && current == requestId) status.setText("이력을 불러오지 못했습니다. 다시 시도해 주세요.");
        });
    }
    private TextView label(String value, int size, boolean bold) {
        TextView t = new TextView(this); t.setText(value); t.setTextSize(size);
        t.setTextColor(Color.rgb(28, 39, 58));
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
            row.setPadding(dp(8), dp(14), dp(8), dp(14));
            ImageView icon = new ImageView(HistoryActivity.this);
            if (item.icon != null) icon.setImageBitmap(item.icon); else icon.setImageResource(R.drawable.app_icon);
            icon.setContentDescription(item.app + " 아이콘");
            row.addView(icon, new LinearLayout.LayoutParams(dp(42), dp(42)));
            LinearLayout content = new LinearLayout(HistoryActivity.this);
            content.setOrientation(LinearLayout.VERTICAL);
            content.addView(label(item.app, 16, true));
            content.addView(label(format.format(new Date(item.time)), 13, false));
            content.addView(label(item.pkg, 11, false));
            TextView body = label(item.content.isEmpty() ? "(내용 없음)" : item.content, 15, false);
            body.setTextIsSelectable(true); body.setPadding(0, dp(6), 0, 0); content.addView(body);
            LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(0, -2, 1); cp.leftMargin = dp(12);
            row.addView(content, cp);
            return row;
        }
    }
}
