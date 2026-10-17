package com.local.noti_filter;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ComponentName;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.provider.Settings;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.BaseAdapter;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

public final class MainActivity extends Activity {
    private static final int INK = Color.rgb(28, 39, 58);
    private static final int MUTED = Color.rgb(97, 108, 126);
    private static final int BLUE = Color.rgb(37, 99, 235);
    private static final int BG = Color.rgb(247, 249, 252);
    private RuleStore store;
    private LinearLayout root;
    private Button addRuleButton;
    private AlertDialog addRuleDialog;
    private AlertDialog appPickerDialog;
    private AlertDialog ruleHelpDialog;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        store = new RuleStore(this);
        render();
    }

    @Override protected void onResume() {
        super.onResume();
        if (root != null) render();
    }

    @Override protected void onDestroy() {
        if (ruleHelpDialog != null) ruleHelpDialog.dismiss();
        if (appPickerDialog != null) appPickerDialog.dismiss();
        if (addRuleDialog != null) addRuleDialog.dismiss();
        addRuleButton = null;
        super.onDestroy();
    }

    private int dp(int value) { return Math.round(getResources().getDisplayMetrics().density * value); }

    private GradientDrawable shape(int color, int radius) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(color);
        d.setCornerRadius(dp(radius));
        return d;
    }

    private TextView text(String value, int size, int color, boolean bold) {
        TextView t = new TextView(this);
        t.setText(value);
        t.setTextSize(size);
        t.setTextColor(color);
        if (bold) t.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        return t;
    }

    private LinearLayout card() {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(18), dp(18), dp(18), dp(18));
        box.setBackground(shape(Color.WHITE, 20));
        box.setElevation(dp(2));
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, -2);
        p.bottomMargin = dp(14);
        root.addView(box, p);
        return box;
    }

    private void render() {
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(BG);
        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(20), dp(28), dp(20), dp(28));
        scroll.addView(root);
        setContentView(scroll);

        TextView eyebrow = text("알림을 더 조용하게", 13, BLUE, true);
        root.addView(eyebrow);
        TextView title = text("문구 알림 필터", 29, INK, true);
        LinearLayout.LayoutParams tp = new LinearLayout.LayoutParams(-1, -2);
        tp.topMargin = dp(5);
        root.addView(title, tp);
        TextView subtitle = text("원하는 앱과 문구를 선택하면 일치하는 알림을 자동으로 지웁니다.", 14, MUTED, false);
        LinearLayout.LayoutParams sp = new LinearLayout.LayoutParams(-1, -2);
        sp.topMargin = dp(8); sp.bottomMargin = dp(22);
        root.addView(subtitle, sp);

        LinearLayout access = card();
        boolean granted = hasAccess();
        access.addView(text(granted ? "●  알림 접근 허용됨" : "●  알림 접근 권한 필요", 17, granted ? Color.rgb(24, 137, 84) : Color.rgb(196, 107, 15), true));
        TextView accessInfo = text("알림 내용을 읽어야 문구를 검사할 수 있습니다. 데이터는 기기 밖으로 전송하지 않습니다.", 13, MUTED, false);
        LinearLayout.LayoutParams aip = new LinearLayout.LayoutParams(-1, -2);
        aip.topMargin = dp(8);
        access.addView(accessInfo, aip);
        Button settings = button("알림 접근 설정 열기", false);
        LinearLayout.LayoutParams sbp = new LinearLayout.LayoutParams(-1, dp(48));
        sbp.topMargin = dp(14);
        access.addView(settings, sbp);
        settings.setOnClickListener(v -> startActivity(new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)));

        LinearLayout control = card();
        LinearLayout controlRow = new LinearLayout(this);
        controlRow.setGravity(Gravity.CENTER_VERTICAL);
        TextView controlLabel = text("필터 일시정지", 17, INK, true);
        controlRow.addView(controlLabel, new LinearLayout.LayoutParams(0, -2, 1));
        Switch paused = new Switch(this);
        paused.setChecked(store.isPaused());
        controlRow.addView(paused);
        control.addView(controlRow);
        paused.setContentDescription("필터 일시정지");
        paused.setOnCheckedChangeListener((view, checked) -> {
            store.setPaused(checked);
            if (!checked) showScanResult(FilterService.clearExistingForEnabledRules());
        });
        Button history = button("삭제한 알림 이력", false);
        LinearLayout.LayoutParams historyParams = new LinearLayout.LayoutParams(-1, dp(48));
        historyParams.topMargin = dp(12);
        control.addView(history, historyParams);
        history.setOnClickListener(v -> startActivity(new Intent(this, HistoryActivity.class)));

        LinearLayout ruleHeader = new LinearLayout(this);
        ruleHeader.setGravity(Gravity.CENTER_VERTICAL);
        TextView header = text("차단 규칙", 20, INK, true);
        ruleHeader.addView(header, new LinearLayout.LayoutParams(0, -2, 1));
        Button add = button("+ 규칙 추가", true);
        addRuleButton = add;
        add.setEnabled(addRuleDialog == null);
        ruleHeader.addView(add, new LinearLayout.LayoutParams(-2, dp(44)));
        root.addView(ruleHeader);
        add.setOnClickListener(v -> showAddDialog());

        List<Rule> rules = store.load();
        if (rules.isEmpty()) {
            LinearLayout empty = card();
            empty.addView(text("아직 규칙이 없습니다", 17, INK, true));
            TextView hint = text("‘규칙 추가’를 눌러 앱과 포함 문구를 지정해 주세요.", 14, MUTED, false);
            LinearLayout.LayoutParams hp = new LinearLayout.LayoutParams(-1, -2);
            hp.topMargin = dp(7);
            empty.addView(hint, hp);
        } else {
            for (Rule rule : rules) addRuleCard(rule);
        }

        TextView foot = text("안내: 알림이 게시된 직후 지우는 방식이라 잠깐 표시되거나 소리가 먼저 날 수 있습니다. 일부 시스템·지속 알림은 지워지지 않을 수 있습니다.", 12, MUTED, false);
        LinearLayout.LayoutParams fp = new LinearLayout.LayoutParams(-1, -2);
        fp.topMargin = dp(8);
        root.addView(foot, fp);
    }

    private Button button(String label, boolean primary) {
        Button b = new Button(this);
        b.setAllCaps(false);
        b.setText(label);
        b.setTextSize(14);
        b.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        b.setTextColor(primary ? Color.WHITE : BLUE);
        b.setBackground(shape(primary ? BLUE : Color.rgb(234, 241, 255), 12));
        return b;
    }

    private void addRuleCard(Rule rule) {
        LinearLayout box = card();
        box.setOnClickListener(v -> {
            // A switch may have changed since this card was rendered.
            for (Rule current : store.load()) {
                if (current.id == rule.id) {
                    showRuleDialog(current);
                    return;
                }
            }
        });
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout names = new LinearLayout(this);
        names.setOrientation(LinearLayout.VERTICAL);
        names.addView(text(rule.appName.isEmpty() ? "모든 앱" : rule.appName, 16, INK, true));
        TextView phrase = text("포함 문구: " + rule.phrase, 14, MUTED, false);
        LinearLayout.LayoutParams pp = new LinearLayout.LayoutParams(-1, -2);
        pp.topMargin = dp(5);
        names.addView(phrase, pp);
        TextView editHint = text("터치하여 수정", 12, BLUE, false);
        LinearLayout.LayoutParams ehp = new LinearLayout.LayoutParams(-1, -2);
        ehp.topMargin = dp(5);
        names.addView(editHint, ehp);
        row.addView(names, new LinearLayout.LayoutParams(0, -2, 1));
        Switch enabled = new Switch(this);
        enabled.setChecked(rule.enabled);
        enabled.setContentDescription(rule.appName + " " + rule.phrase + " 규칙 활성화");
        row.addView(enabled);
        box.addView(row);
        enabled.setOnCheckedChangeListener((v, value) -> {
            List<Rule> rules = store.load();
            for (int i = 0; i < rules.size(); i++) if (rules.get(i).id == rule.id) rules.set(i, rule.withEnabled(value));
            store.save(rules);
            if (value) showScanResult(FilterService.clearExistingForNewRule(rule.withEnabled(true)));
        });
        TextView delete = text("삭제", 13, Color.rgb(193, 60, 60), true);
        delete.setGravity(Gravity.END);
        delete.setPadding(0, dp(12), 0, 0);
        box.addView(delete);
        delete.setOnClickListener(v -> new AlertDialog.Builder(this)
                .setMessage("이 규칙을 삭제하시겠습니까?")
                .setNegativeButton("취소", null)
                .setPositiveButton("삭제", (dialog, which) -> {
                    List<Rule> rules = store.load();
                    rules.removeIf(item -> item.id == rule.id);
                    store.save(rules);
                    render();
                }).show());
    }

    private void showAddDialog() {
        showRuleDialog(null);
    }

    private void showRuleDialog(Rule original) {
        // Add and edit share one dialog owner, including rapid repeated taps.
        if (addRuleDialog != null || isFinishing() || isDestroyed()) return;
        final boolean editing = original != null;
        List<AppOption> apps = installedApps();
        final AppOption[] selected = {apps.get(0)};
        if (editing && !original.packageName.isEmpty()) {
            // Retain the scope even if the app was uninstalled or has no launcher.
            selected[0] = new AppOption(original.packageName,
                    original.appName.isEmpty() ? original.packageName : original.appName,
                    getDrawable(R.drawable.app_icon));
            for (AppOption option : apps) {
                if (option.packageName.equals(original.packageName)) {
                    selected[0] = option;
                    break;
                }
            }
        }
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(22), dp(12), dp(22), 0);
        Button choose = button("대상 앱: " + selected[0].label + "  ▾", false);
        content.addView(choose, new LinearLayout.LayoutParams(-1, dp(48)));
        choose.setOnClickListener(v -> showAppPicker(apps, option -> {
            selected[0] = option;
            choose.setText("대상 앱: " + option.label + "  ▾");
        }));
        LinearLayout phraseHeader = new LinearLayout(this);
        phraseHeader.setGravity(Gravity.CENTER_VERTICAL);
        phraseHeader.addView(text("포함 문구 · , AND / ; OR", 14, MUTED, false),
                new LinearLayout.LayoutParams(0, -2, 1));
        TextView help = text("?", 19, MUTED, true);
        help.setGravity(Gravity.CENTER);
        help.setBackground(shape(Color.rgb(235, 239, 245), 22));
        help.setContentDescription("문구 조건과 AND OR 우선순위 도움말");
        help.setOnClickListener(v -> showRuleHelp());
        phraseHeader.addView(help, new LinearLayout.LayoutParams(dp(44), dp(44)));
        LinearLayout.LayoutParams hp = new LinearLayout.LayoutParams(-1, -2);
        hp.topMargin = dp(12);
        content.addView(phraseHeader, hp);
        EditText phrase = new EditText(this);
        phrase.setSingleLine(true);
        phrase.setHint("예: 택배,배송;배달,완료");
        phrase.setTextSize(16);
        if (editing) {
            phrase.setText(original.phrase);
            phrase.setSelection(phrase.length());
        }
        LinearLayout.LayoutParams ep = new LinearLayout.LayoutParams(-1, dp(58));
        ep.topMargin = dp(12);
        content.addView(phrase, ep);
        AlertDialog dialog = new AlertDialog.Builder(this).setTitle(editing ? "차단 규칙 수정" : "차단 규칙 추가")
                .setView(content).setNegativeButton("취소", null)
                .setPositiveButton(editing ? "저장" : "추가", null).create();
        addRuleDialog = dialog;
        if (addRuleButton != null) addRuleButton.setEnabled(false);
        // Rapid taps at the original Add location must not cancel/reopen it.
        // Explicit Cancel and the Android Back button still close the dialog.
        dialog.setCanceledOnTouchOutside(false);
        dialog.setOnDismissListener(ignored -> {
            if (addRuleDialog == dialog) {
                if (appPickerDialog != null) appPickerDialog.dismiss();
                if (ruleHelpDialog != null) ruleHelpDialog.dismiss();
                addRuleDialog = null;
                if (addRuleButton != null) addRuleButton.setEnabled(true);
            }
        });
        dialog.setOnShowListener(ignored -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            // A queued second positive click after dismissal must not save twice.
            if (addRuleDialog != dialog || !dialog.isShowing()) return;
            String value = phrase.getText().toString().trim();
            if (TextUtils.isEmpty(value)) {
                phrase.setError("문구를 입력해 주세요");
                return;
            }
            if (!RuleMatcher.isValidExpression(value)) {
                phrase.setError("구분자 앞뒤에 문구를 입력해 주세요. 빈 조건은 사용할 수 없습니다.");
                return;
            }
            List<Rule> rules = store.load();
            Rule savedRule;
            if (editing) {
                int index = -1;
                for (int i = 0; i < rules.size(); i++) {
                    if (rules.get(i).id == original.id) { index = i; break; }
                }
                if (index < 0) {
                    dialog.dismiss();
                    Toast.makeText(this, "규칙이 없어 수정할 수 없습니다", Toast.LENGTH_LONG).show();
                    render();
                    return;
                }
                savedRule = new Rule(original.id, selected[0].packageName, selected[0].label,
                        value, rules.get(index).enabled);
                rules.set(index, savedRule);
            } else {
                savedRule = new Rule(System.currentTimeMillis(), selected[0].packageName,
                        selected[0].label, value, true);
                rules.add(savedRule);
            }
            store.save(rules);
            dialog.dismiss();
            int existing = savedRule.enabled ? FilterService.clearExistingForNewRule(savedRule) : 0;
            String result = editing ? "규칙 수정됨" : "규칙 추가됨";
            String message = !savedRule.enabled ? result + " · 비활성 상태 유지"
                    : existing < 0 ? result + " · 기존 알림 확인에는 알림 접근이 필요합니다"
                    : result + " · 기존 알림 " + existing + "개 삭제 요청";
            Toast.makeText(this, message, Toast.LENGTH_LONG).show();
            render();
        }));
        dialog.show();
    }

    private void showRuleHelp() {
        if (ruleHelpDialog != null || isFinishing() || isDestroyed()) return;
        AlertDialog help = new AlertDialog.Builder(this)
                .setTitle("문구 조건 사용법")
                .setMessage("알림의 제목·본문 등에서 문구 포함 여부를 검사합니다.\n\n"
                        + "쉼표 , = AND (모두 포함)\n"
                        + "예: 택배,배송 → ‘택배’와 ‘배송’이 모두 있어야 차단합니다.\n\n"
                        + "세미콜론 ; = OR (하나 이상 포함)\n"
                        + "예: 광고;이벤트 → ‘광고’ 또는 ‘이벤트’가 있으면 차단합니다.\n\n"
                        + "혼합 시 AND가 OR보다 먼저 적용됩니다.\n"
                        + "예: 택배,배송;배달,완료\n"
                        + "→ (택배 AND 배송) OR (배달 AND 완료)\n"
                        + "예: 광고;결제,승인\n"
                        + "→ 광고 OR (결제 AND 승인)\n"
                        + "단어 순서는 상관없으며 같은 알림 안에 포함되면 됩니다.\n\n"
                        + "문구 앞뒤 공백은 무시하고 영문 대소문자는 구분하지 않습니다. "
                        + "문구 안의 띄어쓰기는 그대로 비교합니다.\n"
                        + "빈 조건(예: 광고,,이벤트 / 광고;)은 저장할 수 없습니다.\n"
                        + "반각 , 와 ; 만 구분자입니다. 괄호·정규식·구분자 자체 검색은 지원하지 않습니다.\n"
                        + "구분자 없는 기존 문구는 이전처럼 포함 검색합니다. "
                        + "기존 규칙에 , 또는 ;가 있으면 이제 조건 구분자로 해석됩니다.")
                .setPositiveButton("확인", null).create();
        ruleHelpDialog = help;
        help.setOnDismissListener(ignored -> {
            if (ruleHelpDialog == help) ruleHelpDialog = null;
        });
        help.show();
    }

    private interface AppSelection { void select(AppOption option); }

    private void showScanResult(int count) {
        Toast.makeText(this, count < 0 ? "기존 알림을 검사하려면 알림 접근 연결이 필요합니다."
                : "기존 알림 " + count + "개 삭제 요청 · 실제 삭제 확인 후 이력에 기록합니다.", Toast.LENGTH_LONG).show();
    }

    private void showAppPicker(List<AppOption> apps, AppSelection onSelected) {
        if (appPickerDialog != null || addRuleDialog == null || isFinishing() || isDestroyed()) return;
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(dp(12), dp(4), dp(12), 0);

        EditText search = new EditText(this);
        search.setSingleLine(true);
        search.setHint("앱 이름 또는 패키지 검색");
        search.setTextSize(16);
        layout.addView(search, new LinearLayout.LayoutParams(-1, dp(52)));

        ListView list = new ListView(this);
        AppAdapter adapter = new AppAdapter(apps);
        list.setAdapter(adapter);
        LinearLayout.LayoutParams listParams = new LinearLayout.LayoutParams(-1, dp(430));
        listParams.topMargin = dp(8);
        layout.addView(list, listParams);

        AlertDialog picker = new AlertDialog.Builder(this)
                .setTitle("대상 앱 선택")
                .setView(layout)
                .setNegativeButton("취소", null)
                .create();
        appPickerDialog = picker;
        picker.setCanceledOnTouchOutside(false);
        picker.setOnDismissListener(ignored -> {
            if (appPickerDialog == picker) appPickerDialog = null;
        });
        list.setOnItemClickListener((parent, view, position, id) -> {
            if (appPickerDialog != picker || !picker.isShowing()) return;
            onSelected.select(adapter.getItem(position));
            picker.dismiss();
        });
        search.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                adapter.filter(s.toString());
            }
            @Override public void afterTextChanged(Editable s) {}
        });
        picker.show();
        picker.getWindow().setSoftInputMode(
                android.view.WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN
                        | android.view.WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
    }

    private List<AppOption> installedApps() {
        List<AppOption> result = new ArrayList<>();
        result.add(new AppOption("", "모든 앱", getDrawable(R.drawable.app_icon)));
        PackageManager pm = getPackageManager();
        Intent launcher = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER);
        List<ResolveInfo> activities = pm.queryIntentActivities(launcher, 0);
        for (ResolveInfo info : activities) {
            String pkg = info.activityInfo.packageName;
            if (pkg.equals(getPackageName())) continue;
            boolean already = false;
            for (AppOption existing : result) if (existing.packageName.equals(pkg)) { already = true; break; }
            if (!already) result.add(new AppOption(pkg, info.loadLabel(pm).toString(), info.loadIcon(pm)));
        }
        result.subList(1, result.size()).sort(Comparator.comparing(o -> o.label));
        return result;
    }

    private boolean hasAccess() {
        String listeners = Settings.Secure.getString(getContentResolver(), "enabled_notification_listeners");
        if (listeners == null) return false;
        ComponentName self = new ComponentName(this, FilterService.class);
        for (String part : listeners.split(":")) if (self.equals(ComponentName.unflattenFromString(part))) return true;
        return false;
    }

    private static final class AppOption {
        final String packageName;
        final String label;
        final Drawable icon;
        AppOption(String packageName, String label, Drawable icon) {
            this.packageName = packageName;
            this.label = label;
            this.icon = icon;
        }
    }

    private final class AppAdapter extends BaseAdapter {
        private final List<AppOption> all;
        private final List<AppOption> visible = new ArrayList<>();
        AppAdapter(List<AppOption> all) { this.all = all; filter(""); }

        void filter(String query) {
            String needle = query.trim().toLowerCase(Locale.ROOT);
            visible.clear();
            for (AppOption item : all) {
                if (item.label.toLowerCase(Locale.ROOT).contains(needle)
                        || item.packageName.toLowerCase(Locale.ROOT).contains(needle)) visible.add(item);
            }
            notifyDataSetChanged();
        }

        @Override public int getCount() { return visible.size(); }
        @Override public AppOption getItem(int position) { return visible.get(position); }
        @Override public long getItemId(int position) { return position; }

        @Override public View getView(int position, View reusable, android.view.ViewGroup parent) {
            AppOption item = getItem(position);
            LinearLayout row = new LinearLayout(MainActivity.this);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(dp(12), dp(9), dp(12), dp(9));
            ImageView image = new ImageView(MainActivity.this);
            image.setImageDrawable(item.icon);
            image.setContentDescription(item.label + " 아이콘");
            row.addView(image, new LinearLayout.LayoutParams(dp(40), dp(40)));
            LinearLayout labels = new LinearLayout(MainActivity.this);
            labels.setOrientation(LinearLayout.VERTICAL);
            labels.addView(text(item.label, 16, INK, false));
            if (!item.packageName.isEmpty()) labels.addView(text(item.packageName, 11, MUTED, false));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
            lp.leftMargin = dp(14);
            row.addView(labels, lp);
            return row;
        }
    }
}
