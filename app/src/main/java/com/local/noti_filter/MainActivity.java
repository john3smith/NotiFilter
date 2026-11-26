package com.local.noti_filter;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ComponentName;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.os.Bundle;
import android.provider.Settings;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.BaseAdapter;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.ImageButton;
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
    private Button activeNotificationsButton;
    private AlertDialog activeNotificationsDialog;
    private int activeNotificationsRequest;
    private final java.util.concurrent.ExecutorService notificationLoader =
            java.util.concurrent.Executors.newSingleThreadExecutor();
    private AlertDialog addRuleDialog;
    private AlertDialog appPickerDialog;
    private AlertDialog ruleHelpDialog;
    private AlertDialog deleteRuleDialog;
    private AppCatalog catalog;
    private AppAdapter pickerAdapter;
    private EditText pickerSearch;
    private TextView pickerStatus;
    private boolean appsLoading=true,appsFailed;
    private final java.util.Set<String> expandedApps = new java.util.HashSet<>();
    private static final int PICK_BACKGROUND = 41;
    private final java.util.concurrent.ExecutorService photoWorker =
            java.util.concurrent.Executors.newSingleThreadExecutor();
    private ImageView backgroundImage;
    // Read on every render so values changed in Settings apply on return.
    private int frameTransparency = BackgroundPhoto.DEFAULT_FRAME_TRANSPARENCY;
    private int buttonTransparency = BackgroundPhoto.DEFAULT_BUTTON_TRANSPARENCY;
    private android.graphics.Bitmap backgroundBitmap;
    private long backgroundBitmapVersion = -1;
    private long backgroundLoadingVersion = -1;
    private int backgroundRequest;
    private boolean destroying;
    // Edits in the background dialog stay staged until 저장; 취소 leaves everything unchanged.
    private AlertDialog backgroundDialog;
    private Runnable backgroundDialogRefresh;
    private BackgroundPhoto.Mode stagedMode;
    private boolean stagedRemove;
    private java.io.File stagedPhoto;
    private android.net.Uri preparingPhoto;
    private String photoError;
    private boolean photoPicking;
    private int photoRequest;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        if (state != null && state.getStringArrayList("expandedApps") != null)
            expandedApps.addAll(state.getStringArrayList("expandedApps"));
        catalog=AppCatalog.get(this);
        catalog.refresh();
        store = new RuleStore(this);
        render();
        if (state != null && state.getBoolean("backgroundDialog")) showBackgroundDialog(state);
    }

    @Override protected void onResume() {
        super.onResume();
        if(catalog!=null)catalog.refresh();
        if (root != null) render();
    }

    @Override protected void onSaveInstanceState(Bundle state) {
        state.putStringArrayList("expandedApps", new ArrayList<>(expandedApps));
        if (backgroundDialog != null) {
            state.putBoolean("backgroundDialog", true);
            state.putString("backgroundMode", stagedMode.key);
            state.putBoolean("backgroundRemove", stagedRemove);
            if (stagedPhoto != null) state.putString("backgroundStaged", stagedPhoto.getPath());
            if (preparingPhoto != null) state.putParcelable("backgroundPreparing", preparingPhoto);
        }
        super.onSaveInstanceState(state);
    }

    @Override protected void onDestroy() {
        // Keep staged files for a recreated activity; abandoned ones are swept on the next fresh open.
        destroying = true;
        photoRequest++;
        backgroundRequest++;
        if (backgroundDialog != null) backgroundDialog.dismiss();
        photoWorker.shutdownNow();
        activeNotificationsRequest++;
        if (activeNotificationsDialog != null) activeNotificationsDialog.dismiss();
        notificationLoader.shutdownNow();
        if (deleteRuleDialog != null) deleteRuleDialog.dismiss();
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

    private int frameColor(int color) { return BackgroundPhoto.withTransparency(color, frameTransparency); }
    private int buttonColor(int color) { return BackgroundPhoto.withTransparency(color, buttonTransparency); }

    /** Translucent background with an opaque ripple mask so feedback stays visible at any alpha. */
    private RippleDrawable pressable(int ripple, int color, int radius) {
        return new RippleDrawable(ColorStateList.valueOf(ripple), shape(color, radius), shape(Color.WHITE, radius));
    }

    private LinearLayout card() {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(18), dp(18), dp(18), dp(18));
        box.setBackground(shape(frameColor(Color.WHITE), 20));
        // An elevation shadow would show through a translucent frame as a dark tint.
        box.setElevation(frameTransparency == 0 ? dp(2) : 0);
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, -2);
        p.bottomMargin = dp(14);
        root.addView(box, p);
        return box;
    }

    private void render() {
        frameTransparency = BackgroundPhoto.frameTransparency(this);
        buttonTransparency = BackgroundPhoto.buttonTransparency(this);
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(20), dp(28), dp(20), dp(28));
        scroll.setClipToPadding(true);
        scroll.setOnApplyWindowInsetsListener((view, insets) -> {
            view.setPadding(insets.getSystemWindowInsetLeft(), insets.getSystemWindowInsetTop(),
                    insets.getSystemWindowInsetRight(), insets.getSystemWindowInsetBottom());
            return insets;
        });
        scroll.addView(root);
        // The photo stays fixed behind the scrolling content in its original colors;
        // only frame and button colors are translucent, and text stays clean without outlines.
        FrameLayout screen = new FrameLayout(this);
        screen.setBackgroundColor(BG);
        backgroundImage = new ImageView(this);
        backgroundImage.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        screen.addView(backgroundImage, new FrameLayout.LayoutParams(-1, -1));
        screen.addView(scroll, new FrameLayout.LayoutParams(-1, -1));
        setContentView(screen);
        applyBackground();

        LinearLayout titles = new LinearLayout(this);
        titles.setOrientation(LinearLayout.VERTICAL);
        TextView eyebrow = text("알림을 더 조용하게", 13, BLUE, true);
        titles.addView(eyebrow);
        TextView title = text("문구 알림 필터", 29, INK, true);
        LinearLayout.LayoutParams tp = new LinearLayout.LayoutParams(-1, -2);
        tp.topMargin = dp(5);
        titles.addView(title, tp);
        LinearLayout headerActions = new LinearLayout(this);
        headerActions.addView(headerIcon(R.id.main_background, R.drawable.ic_photo, "배경 사진",
                v -> showBackgroundDialog(null)), new LinearLayout.LayoutParams(dp(48), dp(48)));
        LinearLayout.LayoutParams settingsParams = new LinearLayout.LayoutParams(dp(48), dp(48));
        settingsParams.leftMargin = dp(8);
        headerActions.addView(headerIcon(R.id.main_settings, R.drawable.ic_settings, "설정",
                v -> startActivity(new Intent(this, SettingsActivity.class))), settingsParams);
        LinearLayout heading = new LinearLayout(this) {
            @Override protected void onMeasure(int widthSpec, int heightSpec) {
                // Narrow or large-font screens move the icons to their own row instead of
                // squeezing the title into a column beside them.
                int available = MeasureSpec.getSize(widthSpec) - getPaddingLeft() - getPaddingRight();
                headerActions.measure(MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED),
                        MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED));
                int titleWidth = (int) Math.ceil(Math.max(
                        android.text.Layout.getDesiredWidth(title.getText(), title.getPaint()),
                        android.text.Layout.getDesiredWidth(eyebrow.getText(), eyebrow.getPaint())));
                boolean stacked = MeasureSpec.getMode(widthSpec) != MeasureSpec.UNSPECIFIED
                        && titleWidth + dp(12) + headerActions.getMeasuredWidth() > available;
                setOrientation(stacked ? VERTICAL : HORIZONTAL);
                LayoutParams titleParams = (LayoutParams) titles.getLayoutParams();
                titleParams.width = stacked ? LayoutParams.MATCH_PARENT : 0;
                titleParams.weight = stacked ? 0 : 1;
                LayoutParams actionParams = (LayoutParams) headerActions.getLayoutParams();
                actionParams.gravity = stacked ? Gravity.END : Gravity.TOP;
                actionParams.leftMargin = stacked ? 0 : dp(12);
                actionParams.topMargin = stacked ? dp(10) : 0;
                super.onMeasure(widthSpec, heightSpec);
            }
        };
        heading.addView(titles, new LinearLayout.LayoutParams(0, -2, 1));
        heading.addView(headerActions, new LinearLayout.LayoutParams(-2, -2));
        root.addView(heading);
        TextView subtitle = text("원하는 앱과 문구를 선택하면 일치하는 알림을 자동으로 숨깁니다.", 14, MUTED, false);
        LinearLayout.LayoutParams sp = new LinearLayout.LayoutParams(-1, -2);
        sp.topMargin = dp(8); sp.bottomMargin = dp(12);
        root.addView(subtitle, sp);

        boolean granted = hasAccess();
        TextView accessStatus = text(granted ? "●  알림 접근 허용됨" : "●  알림 접근 권한 필요 · 설정에서 확인", 13,
                granted ? Color.rgb(24, 137, 84) : Color.rgb(196, 107, 15), true);
        LinearLayout.LayoutParams statusParams = new LinearLayout.LayoutParams(-1, -2);
        statusParams.bottomMargin = dp(18);
        root.addView(accessStatus, statusParams);

        LinearLayout control = card();
        LinearLayout controlRow = new LinearLayout(this);
        controlRow.setGravity(Gravity.CENTER_VERTICAL);
        TextView controlLabel = text("필터 일시정지", 17, INK, true);
        controlRow.addView(controlLabel, new LinearLayout.LayoutParams(0, -2, 1));
        Switch paused = toggleSwitch();
        paused.setId(R.id.filter_paused);
        paused.setChecked(store.isPaused());
        controlRow.addView(paused);
        control.addView(controlRow);
        paused.setContentDescription("필터 일시정지");
        paused.setOnCheckedChangeListener((view, checked) -> {
            store.setPaused(checked);
            if (!checked) showScanResult(FilterService.clearExistingForEnabledRules());
        });
        Button history = screenButton("알림 처리 이력", false);
        LinearLayout.LayoutParams historyParams = new LinearLayout.LayoutParams(-1, -2);
        historyParams.topMargin = dp(12);
        control.addView(history, historyParams);
        history.setOnClickListener(v -> startActivity(new Intent(this, HistoryActivity.class)));
        TextView header = text("차단 규칙", 20, INK, true);
        root.addView(header);
        LinearLayout ruleHeader = new LinearLayout(this);
        ruleHeader.setGravity(Gravity.CENTER_VERTICAL);
        Button add = screenButton("+ 규칙 추가", true);
        addRuleButton = add;
        add.setEnabled(addRuleDialog == null && activeNotificationsDialog == null);
        ruleHeader.addView(add, new LinearLayout.LayoutParams(0, -2, 1));
        Button active = screenButton("현재 알림에서 규칙 추가", false);
        active.setId(R.id.active_notifications);
        activeNotificationsButton = active;
        active.setEnabled(activeNotificationsDialog == null && addRuleDialog == null);
        LinearLayout.LayoutParams activeParams = new LinearLayout.LayoutParams(0, -2, 2);
        activeParams.leftMargin = dp(8);
        ruleHeader.addView(active, activeParams);
        LinearLayout.LayoutParams actionsParams = new LinearLayout.LayoutParams(-1, -2);
        actionsParams.topMargin = dp(10);
        root.addView(ruleHeader, actionsParams);
        add.setOnClickListener(v -> showAddDialog());
        active.setOnClickListener(v -> showActiveNotifications());

        LinearLayout sortRow=new AdaptiveRow(this, true);
        LinearLayout sortPanel = new LinearLayout(this);
        sortPanel.setOrientation(LinearLayout.VERTICAL);
        sortPanel.setPadding(dp(12), dp(10), dp(12), dp(6));
        sortPanel.setBackground(shape(frameColor(Color.rgb(239, 242, 246)), 12));
        sortPanel.addView(text("목록 정렬", 12, MUTED, true));
        RuleOrder.Mode selectedOrder=store.sortOrder();
        int[] sortIds={R.id.sort_newest,R.id.sort_oldest,R.id.sort_name};
        for (RuleOrder.Mode mode : RuleOrder.Mode.values()) {
            Button choice=button(mode.label, mode==selectedOrder);
            choice.setTextSize(13);
            choice.setPadding(dp(8), dp(4), dp(8), dp(4));
            choice.setTextColor(mode==selectedOrder ? Color.WHITE : MUTED);
            choice.setBackground(pressable(0x22334455,
                    mode==selectedOrder ? buttonColor(INK) : Color.TRANSPARENT, 8));
            choice.setId(sortIds[mode.ordinal()]);
            choice.setSelected(mode==selectedOrder);
            choice.setContentDescription(mode.label + (mode==selectedOrder ? " 선택됨" : " 정렬"));
            LinearLayout.LayoutParams cp=new LinearLayout.LayoutParams(0,-2,1);
            sortRow.addView(choice,cp);
            choice.setOnClickListener(v -> {
                if (store.sortOrder()==mode) return;
                int position=scroll.getScrollY();
                store.setSortOrder(mode);
                render();
                ScrollView next=(ScrollView)root.getParent();
                next.post(() -> next.scrollTo(0,position));
            });
        }
        LinearLayout.LayoutParams sortParams=new LinearLayout.LayoutParams(-1,-2);
        sortParams.topMargin=dp(24);sortParams.bottomMargin=dp(16);
        LinearLayout.LayoutParams sortOptions = new LinearLayout.LayoutParams(-1, -2);
        sortOptions.topMargin = dp(6);
        sortPanel.addView(sortRow,sortOptions);
        root.addView(sortPanel,sortParams);

        List<Rule> rules = RuleOrder.sorted(store.load(),selectedOrder);
        if (rules.isEmpty()) {
            LinearLayout empty = card();
            empty.addView(text("아직 규칙이 없습니다", 17, INK, true));
            TextView hint = text("‘규칙 추가’를 눌러 앱과 포함 문구를 지정해 주세요.", 14, MUTED, false);
            LinearLayout.LayoutParams hp = new LinearLayout.LayoutParams(-1, -2);
            hp.topMargin = dp(7);
            empty.addView(hint, hp);
        } else {
            java.util.Map<String, List<Rule>> groups = new java.util.LinkedHashMap<>();
            for (Rule rule : rules)
                groups.computeIfAbsent(rule.packageName, key -> new ArrayList<>()).add(rule);
            expandedApps.retainAll(groups.keySet());
            for (java.util.Map.Entry<String, List<Rule>> group : groups.entrySet()) {
                String key = group.getKey();
                List<Rule> members = group.getValue();
                Rule first = members.get(0);
                String name = key.isEmpty() ? "모든 앱" : first.appName.isEmpty() ? key : first.appName;
                LinearLayout groupBox = card();
                LinearLayout groupHeader = new LinearLayout(this);
                groupHeader.setGravity(Gravity.CENTER_VERTICAL);
                groupHeader.setPadding(dp(12), dp(8), dp(8), dp(8));
                groupHeader.setBackground(shape(frameColor(Color.rgb(225, 235, 252)), 12));
                android.widget.ImageView icon = new android.widget.ImageView(this);
                android.graphics.drawable.Drawable appIcon = getPackageManager().getDefaultActivityIcon();
                if (!key.isEmpty()) {
                    try { appIcon = getPackageManager().getApplicationIcon(key); }
                    catch (android.content.pm.PackageManager.NameNotFoundException ignored) { }
                }
                icon.setImageDrawable(appIcon);
                icon.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
                groupHeader.addView(icon, new LinearLayout.LayoutParams(dp(32), dp(32)));
                LinearLayout groupTitle = new LinearLayout(this);
                groupTitle.setOrientation(LinearLayout.VERTICAL);
                groupTitle.setPadding(dp(10), 0, dp(8), 0);
                groupTitle.addView(text(name, 16, INK, true));
                groupTitle.addView(text("규칙 " + members.size() + "개", 12, MUTED, false));
                groupHeader.addView(groupTitle, new LinearLayout.LayoutParams(0, -2, 1));
                Button toggle = screenButton("", false);
                toggle.setTextSize(12);
                toggle.setPadding(dp(8), dp(4), dp(8), dp(4));
                groupHeader.addView(toggle, new LinearLayout.LayoutParams(-2, -2));
                LinearLayout contents = new LinearLayout(this);
                contents.setOrientation(LinearLayout.VERTICAL);
                Runnable updateGroup = () -> {
                    boolean open = expandedApps.contains(key);
                    toggle.setText(open ? "접기 ▴" : "펼치기 ▾");
                    toggle.setContentDescription(name + ", 규칙 " + members.size() + "개, " + (open ? "접기" : "펼치기"));
                    contents.setVisibility(open ? View.VISIBLE : View.GONE);
                };
                groupBox.addView(groupHeader, new LinearLayout.LayoutParams(-1, -2));
                groupBox.addView(contents, new LinearLayout.LayoutParams(-1, -2));
                for (Rule rule : members) addRuleCard(rule, contents);
                updateGroup.run();
                toggle.setOnClickListener(v -> {
                    if (!expandedApps.remove(key)) expandedApps.add(key);
                    updateGroup.run();
                });
            }
        }

        TextView foot = text("안내: 알림을 24시간씩 숨기며, 필터 작동 중 다시 나타나면 다시 숨깁니다. 일시정지·규칙 해제 후에도 이미 숨긴 알림은 시간이 끝난 뒤 나타날 수 있습니다. 일부 시스템 알림은 숨길 수 없습니다.", 12, MUTED, false);
        LinearLayout.LayoutParams fp = new LinearLayout.LayoutParams(-1, -2);
        fp.topMargin = dp(8);
        root.addView(foot, fp);
    }

    private void applyBackground() {
        backgroundImage.setScaleType(BackgroundPhoto.mode(this).scaleType);
        if (!BackgroundPhoto.hasPhoto(this)) {
            backgroundBitmap = null;
            backgroundBitmapVersion = -1;
            showBackgroundBitmap();
            return;
        }
        long version = BackgroundPhoto.version(this);
        // Until a changed photo finishes decoding, keep showing the previous one.
        showBackgroundBitmap();
        if (backgroundBitmapVersion == version || backgroundLoadingVersion == version) return;
        backgroundLoadingVersion = version;
        final int request = ++backgroundRequest;
        android.util.DisplayMetrics metrics = getResources().getDisplayMetrics();
        int width = metrics.widthPixels, height = metrics.heightPixels;
        android.content.Context app = getApplicationContext();
        try {
            photoWorker.execute(() -> {
                android.graphics.Bitmap bitmap = BackgroundPhoto.loadForDisplay(app, width, height);
                runOnUiThread(() -> {
                    if (isDestroyed() || request != backgroundRequest) return;
                    backgroundLoadingVersion = -1;
                    if (BackgroundPhoto.version(this) != version) {
                        applyBackground();
                        return;
                    }
                    backgroundBitmap = bitmap;
                    backgroundBitmapVersion = version;
                    if (bitmap == null)
                        Toast.makeText(this, "배경 사진을 읽지 못해 기본 배경을 표시합니다.", Toast.LENGTH_LONG).show();
                    showBackgroundBitmap();
                });
            });
        } catch (java.util.concurrent.RejectedExecutionException stopped) {
            backgroundLoadingVersion = -1;
        }
    }

    private void showBackgroundBitmap() {
        backgroundImage.setImageBitmap(backgroundBitmap);
        int visibility = backgroundBitmap != null ? View.VISIBLE : View.GONE;
        backgroundImage.setVisibility(visibility);
    }

    private void showBackgroundDialog(Bundle restored) {
        if (backgroundDialog != null || isFinishing() || isDestroyed()) return;
        if (restored == null) {
            // No dialog references staged copies now, so leftovers are safe to remove.
            BackgroundPhoto.clearStaging(this);
            stagedMode = BackgroundPhoto.mode(this);
            stagedRemove = false;
            stagedPhoto = null;
            preparingPhoto = null;
        } else {
            stagedMode = BackgroundPhoto.Mode.fromKey(restored.getString("backgroundMode", ""));
            stagedRemove = restored.getBoolean("backgroundRemove");
            String path = restored.getString("backgroundStaged");
            java.io.File staged = path == null ? null : new java.io.File(path);
            stagedPhoto = BackgroundPhoto.isStaged(this, staged) ? staged : null;
            preparingPhoto = restored.getParcelable("backgroundPreparing");
        }
        photoError = null;
        photoPicking = false;

        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(24), dp(8), dp(24), dp(12));
        TextView status = text("", 13, ModernDialog.BODY, false);
        status.setId(R.id.background_status);
        status.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);
        status.setPadding(dp(14), dp(12), dp(14), dp(12));
        status.setBackground(ModernDialog.outlined(this, ModernDialog.SURFACE_MUTED, ModernDialog.BORDER, 1f, 12));
        content.addView(status, new LinearLayout.LayoutParams(-1, -2));
        LinearLayout actions = new AdaptiveRow(this, true);
        Button pick = button("사진 선택", true);
        pick.setId(R.id.background_pick);
        actions.addView(pick, new LinearLayout.LayoutParams(0, -2, 1));
        Button remove = button("사진 삭제", false);
        remove.setId(R.id.background_remove);
        remove.setTextColor(ModernDialog.DANGER);
        remove.setBackground(ModernDialog.ripple(this, 0x22DC2626, ModernDialog.DANGER_SOFT, 12));
        actions.addView(remove, new LinearLayout.LayoutParams(0, -2, 1));
        LinearLayout.LayoutParams actionsParams = new LinearLayout.LayoutParams(-1, -2);
        actionsParams.topMargin = dp(14);
        content.addView(actions, actionsParams);
        TextView modeLabel = ModernDialog.sectionLabel(this, "표시 방식");
        LinearLayout.LayoutParams labelParams = new LinearLayout.LayoutParams(-1, -2);
        labelParams.topMargin = dp(18);
        content.addView(modeLabel, labelParams);
        android.widget.RadioGroup modes = new android.widget.RadioGroup(this);
        int[] modeIds = {R.id.background_mode_fill, R.id.background_mode_crop, R.id.background_mode_fit};
        List<android.widget.RadioButton> modeButtons = new ArrayList<>();
        Runnable syncModes = () -> {
            for (android.widget.RadioButton option : modeButtons) ModernDialog.updateRadioOption(this, option);
        };
        for (BackgroundPhoto.Mode mode : BackgroundPhoto.Mode.values()) {
            android.widget.RadioButton option = new android.widget.RadioButton(this);
            option.setId(modeIds[mode.ordinal()]);
            option.setText(mode.label);
            option.setChecked(mode == stagedMode);
            ModernDialog.styleRadioOption(this, option);
            option.setOnCheckedChangeListener((view, checked) -> {
                if (checked) stagedMode = mode;
                syncModes.run();
            });
            android.widget.RadioGroup.LayoutParams rp = new android.widget.RadioGroup.LayoutParams(-1, -2);
            rp.topMargin = dp(8);
            modes.addView(option, rp);
            modeButtons.add(option);
        }
        LinearLayout.LayoutParams modesParams = new LinearLayout.LayoutParams(-1, -2);
        modesParams.topMargin = dp(2);
        content.addView(modes, modesParams);
        ScrollView formScroll = new ScrollView(this);
        formScroll.addView(content);
        AlertDialog dialog = ModernDialog.builder(this, "배경 사진", "메인 화면의 배경과 표시 방식을 설정합니다.")
                .setView(formScroll).setNegativeButton("취소", null)
                .setPositiveButton("저장", null).create();
        backgroundDialog = dialog;
        Runnable refresh = () -> {
            if (backgroundDialog != dialog) return;
            boolean current = BackgroundPhoto.hasPhoto(this);
            boolean photo = stagedPhoto != null || (current && !stagedRemove);
            boolean busy = photoPicking || preparingPhoto != null;
            status.setText(photoError != null ? photoError
                    : preparingPhoto != null ? "사진을 준비하는 중입니다…"
                    : photoPicking ? "사진을 선택해 주세요."
                    : stagedPhoto != null ? "새 사진이 준비되었습니다. 저장을 누르면 적용됩니다."
                    : stagedRemove && current ? "저장하면 사진을 삭제하고 기본 배경으로 되돌립니다."
                    : current ? "현재 배경 사진을 사용 중입니다."
                    : "배경 사진이 없습니다. 기본 배경을 사용 중입니다.");
            status.setTextColor(photoError != null ? ModernDialog.DANGER : ModernDialog.BODY);
            status.setBackground(ModernDialog.outlined(this,
                    photoError != null ? ModernDialog.DANGER_SOFT : ModernDialog.SURFACE_MUTED,
                    photoError != null ? Color.rgb(252, 165, 165) : ModernDialog.BORDER, 1f, 12));
            pick.setText(photo ? "사진 변경" : "사진 선택");
            pick.setEnabled(!busy);
            remove.setEnabled(!busy && photo);
            remove.setAlpha(remove.isEnabled() ? 1f : 0.45f);
            for (android.widget.RadioButton option : modeButtons) option.setEnabled(photo && !busy);
            syncModes.run();
            Button save = dialog.getButton(AlertDialog.BUTTON_POSITIVE);
            if (save != null) save.setEnabled(!busy);
        };
        backgroundDialogRefresh = refresh;
        pick.setOnClickListener(v -> {
            if (backgroundDialog != dialog || photoPicking || preparingPhoto != null) return;
            Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT)
                    .addCategory(Intent.CATEGORY_OPENABLE).setType("image/*");
            photoError = null;
            try {
                startActivityForResult(intent, PICK_BACKGROUND);
                photoPicking = true;
            } catch (android.content.ActivityNotFoundException missing) {
                photoError = "사진을 선택할 수 있는 앱이 없습니다.";
            }
            refresh.run();
        });
        remove.setOnClickListener(v -> {
            if (backgroundDialog != dialog || photoPicking || preparingPhoto != null) return;
            if (stagedPhoto != null) {
                //noinspection ResultOfMethodCallIgnored
                stagedPhoto.delete();
                stagedPhoto = null;
            }
            stagedRemove = true;
            photoError = null;
            refresh.run();
        });
        dialog.setCanceledOnTouchOutside(false);
        dialog.setOnDismissListener(ignored -> {
            if (backgroundDialog != dialog) return;
            backgroundDialog = null;
            backgroundDialogRefresh = null;
            photoRequest++;
            // An activity being recreated restores and keeps using the staged copy.
            if (!destroying && stagedPhoto != null) //noinspection ResultOfMethodCallIgnored
                stagedPhoto.delete();
            stagedPhoto = null;
            preparingPhoto = null;
            photoPicking = false;
            photoError = null;
        });
        dialog.setOnShowListener(ignored -> {
            ModernDialog.styleButtons(dialog, false);
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> saveBackground(dialog));
            refresh.run();
        });
        dialog.show();
        ModernDialog.applyChrome(dialog);
        if (preparingPhoto != null) startPhotoPrepare(preparingPhoto);
    }

    @Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != PICK_BACKGROUND) return;
        photoPicking = false;
        if (backgroundDialog == null) return;
        android.net.Uri picked = resultCode == RESULT_OK && data != null ? data.getData() : null;
        if (picked != null) startPhotoPrepare(picked);
        else if (backgroundDialogRefresh != null) backgroundDialogRefresh.run();
    }

    private void startPhotoPrepare(android.net.Uri uri) {
        final int request = ++photoRequest;
        preparingPhoto = uri;
        photoError = null;
        if (backgroundDialogRefresh != null) backgroundDialogRefresh.run();
        android.content.Context app = getApplicationContext();
        Runnable task = () -> {
            java.io.File prepared = null;
            String error = null;
            try {
                prepared = BackgroundPhoto.prepare(app, uri);
            } catch (BackgroundPhoto.PhotoException failed) {
                error = failed.getMessage();
            } catch (java.io.IOException | RuntimeException failed) {
                error = "사진을 준비하지 못했습니다. 다시 선택해 주세요.";
            }
            final java.io.File result = prepared;
            final String message = error;
            runOnUiThread(() -> {
                // Results for a closed dialog, a newer pick or a destroyed activity are discarded.
                if (isDestroyed() || request != photoRequest || backgroundDialog == null) {
                    if (result != null) //noinspection ResultOfMethodCallIgnored
                        result.delete();
                    return;
                }
                preparingPhoto = null;
                if (result != null) {
                    if (stagedPhoto != null) //noinspection ResultOfMethodCallIgnored
                        stagedPhoto.delete();
                    stagedPhoto = result;
                    stagedRemove = false;
                } else {
                    photoError = message;
                }
                if (backgroundDialogRefresh != null) backgroundDialogRefresh.run();
            });
        };
        try {
            photoWorker.execute(task);
        } catch (java.util.concurrent.RejectedExecutionException stopped) {
            preparingPhoto = null;
        }
    }

    private void saveBackground(AlertDialog dialog) {
        if (backgroundDialog != dialog || !dialog.isShowing() || photoPicking || preparingPhoto != null) return;
        boolean replaced = stagedPhoto != null;
        boolean removed = !replaced && stagedRemove && BackgroundPhoto.hasPhoto(this);
        if (!BackgroundPhoto.commit(this, stagedPhoto, stagedRemove, stagedMode)) {
            photoError = "배경을 저장하지 못했습니다. 이전 배경을 유지합니다.";
            if (backgroundDialogRefresh != null) backgroundDialogRefresh.run();
            return;
        }
        stagedPhoto = null; // Now the committed photo, not a staged copy.
        dialog.dismiss();
        Toast.makeText(this, replaced ? "배경 사진을 적용했습니다" : removed ? "기본 배경으로 되돌렸습니다"
                : "배경 설정을 저장했습니다", Toast.LENGTH_SHORT).show();
        ScrollView scroll = (ScrollView) root.getParent();
        int position = scroll.getScrollY();
        render();
        ScrollView next = (ScrollView) root.getParent();
        next.post(() -> next.scrollTo(0, position));
    }

    private ImageButton headerIcon(int id, int icon, String description, View.OnClickListener onClick) {
        ImageButton action = new ImageButton(this);
        action.setId(id);
        action.setImageResource(icon);
        action.setContentDescription(description);
        action.setPadding(dp(12), dp(12), dp(12), dp(12));
        action.setBackground(pressable(0x222563EB, buttonColor(Color.rgb(234, 241, 255)), 12));
        action.setOnClickListener(onClick);
        return action;
    }

    /** Main-screen button whose background color follows the button transparency setting. */
    private Button screenButton(String label, boolean primary) {
        Button b = button(label, primary);
        b.setBackground(pressable(primary ? 0x33FFFFFF : 0x222563EB,
                buttonColor(primary ? BLUE : Color.rgb(234, 241, 255)), 12));
        return b;
    }

    private Button button(String label, boolean primary) {
        Button b = new Button(this);
        b.setAllCaps(false);
        b.setText(label);
        b.setTextSize(14);
        b.setSingleLine(false);
        b.setMaxLines(Integer.MAX_VALUE);
        b.setGravity(Gravity.CENTER);
        b.setMinWidth(0);
        b.setMinimumWidth(0);
        b.setMinHeight(dp(48));
        b.setMinimumHeight(dp(48));
        b.setPadding(dp(16), dp(12), dp(16), dp(12));
        b.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        b.setTextColor(primary ? Color.WHITE : BLUE);
        b.setBackground(new RippleDrawable(ColorStateList.valueOf(primary ? 0x33FFFFFF : 0x222563EB),
                shape(primary ? BLUE : Color.rgb(234, 241, 255), 12), null));
        // The platform Button's default raised shadow is clipped by tight action rows.
        b.setStateListAnimator(null);
        b.setElevation(0);
        b.setTranslationZ(0);
        return b;
    }

    private Switch toggleSwitch() {
        Switch toggle = new Switch(this);
        toggle.setMinWidth(dp(76));
        toggle.setMinimumWidth(dp(76));
        toggle.setMinHeight(dp(56));
        toggle.setMinimumHeight(dp(56));
        toggle.setPadding(dp(10), dp(10), dp(10), dp(10));
        toggle.setSwitchMinWidth(dp(56));
        GradientDrawable thumb = shape(Color.WHITE, 13);
        thumb.setSize(dp(26), dp(26));
        toggle.setThumbDrawable(thumb);
        GradientDrawable track = shape(Color.WHITE, 9);
        track.setSize(dp(56), dp(18));
        toggle.setTrackDrawable(track);
        int[][] states = {new int[]{android.R.attr.state_checked}, new int[]{}};
        toggle.setThumbTintList(new ColorStateList(states, new int[]{BLUE, Color.rgb(112, 122, 138)}));
        toggle.setTrackTintList(new ColorStateList(states,
                new int[]{Color.rgb(195, 214, 255), Color.rgb(218, 223, 231)}));
        return toggle;
    }

    private void addRuleCard(Rule rule, LinearLayout parent) {
        LinearLayout box = card();
        root.removeView(box);
        parent.addView(box);
        box.setElevation(0);
        // Rows sit directly on the group frame; only the frame itself carries a color.
        box.setBackground(null);
        box.setPadding(dp(12), dp(12), dp(12), dp(12));
        box.setTag(R.id.rule_card_id,rule.id);
        Runnable editRule = () -> {
            // A switch may have changed since this card was rendered.
            for (Rule current : store.load()) {
                if (current.id == rule.id) {
                    showRuleDialog(current);
                    return;
                }
            }
        };
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout names = new LinearLayout(this);
        names.setId(R.id.rule_content);
        names.setOrientation(LinearLayout.VERTICAL);
        names.setMinimumHeight(dp(56));
        names.setPadding(dp(4), dp(4), dp(4), dp(4));
        names.setGravity(Gravity.CENTER_VERTICAL);
        names.setBackground(pressable(0x222563EB, Color.TRANSPARENT, 8));
        names.setFocusable(true);
        String ruleSummary = rule.matchEmpty ? "내용이 없는 알림 제거"
                : rule.exact ? "정확히 일치: " + rule.phrase
                : "포함 문구: " + rule.phrase;
        names.setContentDescription("규칙 수정: " + rule.appName + " " + ruleSummary);
        names.setOnClickListener(v -> editRule.run());
        if (rule.matchEmpty || rule.exact) {
            TextView badge = ModernDialog.badge(this,
                    rule.matchEmpty ? "내용 없는 알림" : "정확히 일치",
                    ModernDialog.SURFACE_TINT, ModernDialog.BLUE_DARK);
            LinearLayout.LayoutParams bp = new LinearLayout.LayoutParams(-2, -2);
            bp.bottomMargin = dp(4);
            names.addView(badge, bp);
        }
        TextView phrase = text(rule.matchEmpty ? "제목·본문이 없는 알림 자동 제거"
                : rule.exact ? rule.phrase : "포함 문구: " + rule.phrase, 15, INK, false);
        LinearLayout.LayoutParams pp = new LinearLayout.LayoutParams(-1, -2);
        pp.topMargin = dp(2);
        names.addView(phrase, pp);
        LinearLayout.LayoutParams namesParams = new LinearLayout.LayoutParams(0, -2, 1);
        namesParams.setMarginEnd(dp(8));
        row.addView(names, namesParams);
        Switch enabled = toggleSwitch();
        enabled.setId(R.id.rule_enabled);
        enabled.setChecked(rule.enabled);
        enabled.setContentDescription(rule.appName + " " + ruleSummary + " 규칙 활성화");
        row.addView(enabled);
        box.addView(row);
        enabled.setOnCheckedChangeListener((v, value) -> {
            List<Rule> rules = store.load();
            for (int i = 0; i < rules.size(); i++) if (rules.get(i).id == rule.id) rules.set(i, rule.withEnabled(value));
            store.save(rules);
            if (value) showScanResult(FilterService.clearExistingForNewRule(rule.withEnabled(true)));
        });
        LinearLayout actions = new LinearLayout(this);
        actions.setGravity(Gravity.END);
        Button edit = button("수정", false);
        edit.setOnClickListener(v -> editRule.run());
        Button delete = button("삭제", false);
        delete.setTextColor(Color.rgb(193, 60, 60));
        for (Button action : new Button[]{edit, delete}) {
            action.setTextSize(12);
            action.setPadding(dp(12), 0, dp(12), 0);
            // Rows have no backing of their own, so the actions carry the button color.
            action.setBackground(pressable(0x222563EB, buttonColor(Color.rgb(234, 241, 255)), 8));
        }
        actions.addView(edit,new LinearLayout.LayoutParams(-2,-2));
        LinearLayout.LayoutParams deleteParams=new LinearLayout.LayoutParams(-2,-2);
        deleteParams.leftMargin=dp(8);
        actions.addView(delete,deleteParams);
        box.addView(actions);
        delete.setOnClickListener(v -> showDeleteRuleDialog(rule));
    }

    private void showDeleteRuleDialog(Rule rule) {
        if (deleteRuleDialog != null || isFinishing() || isDestroyed()) return;
        String appLabel = rule.packageName.isEmpty() ? "모든 앱" : (rule.appName.isEmpty() ? rule.packageName : rule.appName);
        String summary = rule.matchEmpty ? "내용이 없는 알림 제거"
                : rule.exact ? "정확히 일치: " + rule.phrase
                : "포함 문구: " + rule.phrase;
        AlertDialog confirmation = ModernDialog.builder(this, "규칙 삭제", null)
                .setView(ModernDialog.messageBody(this,
                        "이 차단 규칙을 삭제하시겠습니까?\n\n대상 앱: " + appLabel + "\n조건: " + summary))
                .setNegativeButton("취소", null)
                .setPositiveButton("삭제", (dialog, which) -> {
                    if (deleteRuleDialog != dialog || isFinishing() || isDestroyed()) return;
                    List<Rule> rules = store.load();
                    rules.removeIf(item -> item.id == rule.id);
                    store.save(rules);
                    render();
                }).create();
        deleteRuleDialog = confirmation;
        confirmation.setCanceledOnTouchOutside(false);
        confirmation.setOnDismissListener(dialog -> {
            if (deleteRuleDialog == confirmation) deleteRuleDialog = null;
        });
        ModernDialog.show(confirmation, true);
    }

    private void showAddDialog() {
        showRuleDialog(null);
    }

    private void showRuleDialog(Rule original) {
        // Add and edit share one dialog owner, including rapid repeated taps.
        if (addRuleDialog != null || activeNotificationsDialog != null || isFinishing() || isDestroyed()) return;
        final boolean editing = original != null;
        final boolean initialLiteral = editing && original.literal;
        final boolean initialExact = editing && original.exact;
        final boolean initialEmpty = editing && original.matchEmpty;
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
        content.setPadding(dp(24), dp(8), dp(24), dp(12));

        content.addView(ModernDialog.sectionLabel(this, "대상 앱"), new LinearLayout.LayoutParams(-1, -2));
        Button choose = new Button(this);
        choose.setId(R.id.rule_choose_app);
        choose.setText("대상 앱: " + selected[0].label + "  ▾");
        ModernDialog.styleSelectorButton(this, choose);
        LinearLayout.LayoutParams chooseParams = new LinearLayout.LayoutParams(-1, -2);
        chooseParams.topMargin = dp(8);
        content.addView(choose, chooseParams);

        TextView loading = text("앱 목록 확인 중…", 12, ModernDialog.MUTED, false);
        LinearLayout.LayoutParams loadParams = new LinearLayout.LayoutParams(-1, -2);
        loadParams.topMargin = dp(6);
        content.addView(loading, loadParams);
        choose.setOnClickListener(v -> showAppPicker(apps, option -> {
            selected[0] = option;
            choose.setText("대상 앱: " + option.label + "  ▾");
        }));

        LinearLayout phraseHeader = new LinearLayout(this);
        phraseHeader.setGravity(Gravity.CENTER_VERTICAL);
        TextView phraseLabel = ModernDialog.sectionLabel(this, "");
        phraseHeader.addView(phraseLabel, new LinearLayout.LayoutParams(0, -2, 1));
        TextView help = text("?", 16, ModernDialog.BLUE_DARK, true);
        help.setGravity(Gravity.CENTER);
        help.setMinWidth(dp(36));
        help.setMinHeight(dp(36));
        help.setBackground(ModernDialog.ripple(this, 0x222563EB, ModernDialog.SURFACE_TINT, 18));
        help.setContentDescription("문구 조건 도움말");
        phraseHeader.addView(help, new LinearLayout.LayoutParams(dp(36), dp(36)));
        LinearLayout.LayoutParams hp = new LinearLayout.LayoutParams(-1, -2);
        hp.topMargin = dp(16);
        content.addView(phraseHeader, hp);

        EditText phrase = new EditText(this);
        phrase.setId(R.id.rule_phrase);
        ModernDialog.styleInput(this, phrase);
        phrase.setMinHeight(dp(54));
        if (editing && !initialEmpty) {
            phrase.setText(original.phrase);
            phrase.setSelection(phrase.length());
        }
        LinearLayout.LayoutParams ep = new LinearLayout.LayoutParams(-1, -2);
        ep.topMargin = dp(8);
        content.addView(phrase, ep);

        TextView optionsLabel = ModernDialog.sectionLabel(this, "매칭 옵션");
        LinearLayout.LayoutParams opLabelParams = new LinearLayout.LayoutParams(-1, -2);
        opLabelParams.topMargin = dp(16);
        content.addView(optionsLabel, opLabelParams);

        CheckBox exactCheck = new CheckBox(this);
        exactCheck.setId(R.id.rule_exact);
        exactCheck.setChecked(initialExact);
        LinearLayout exactCard = ModernDialog.optionCard(this, exactCheck,
                "정확히 같은 알람만 제거",
                "알림 전체 내용이 입력한 문구와 정확히 일치할 때만 제거합니다.");
        LinearLayout.LayoutParams exactParams = new LinearLayout.LayoutParams(-1, -2);
        exactParams.topMargin = dp(8);
        content.addView(exactCard, exactParams);

        CheckBox emptyCheck = new CheckBox(this);
        emptyCheck.setId(R.id.rule_match_empty);
        emptyCheck.setChecked(initialEmpty);
        LinearLayout emptyCard = ModernDialog.optionCard(this, emptyCheck,
                "내용이 없는 알림 제거",
                "제목과 본문이 모두 비어 있는 알림을 자동으로 제거합니다.");
        LinearLayout.LayoutParams emptyParams = new LinearLayout.LayoutParams(-1, -2);
        emptyParams.topMargin = dp(8);
        content.addView(emptyCard, emptyParams);

        Runnable syncForm = () -> {
            boolean matchEmpty = emptyCheck.isChecked();
            boolean exactMode = !matchEmpty && exactCheck.isChecked();
            exactCheck.setEnabled(!matchEmpty);
            phrase.setEnabled(!matchEmpty);
            phrase.setAlpha(matchEmpty ? 0.55f : 1f);
            if (matchEmpty) {
                phrase.setError(null);
                phrase.setHint("내용이 없는 알림을 자동으로 제거합니다");
                phraseLabel.setText("알림 문구 · 내용 없음 조건 사용 중");
            } else if (exactMode) {
                phrase.setSingleLine(false);
                phrase.setMaxLines(4);
                phrase.setHint("예: 정확히 일치할 알림 전체 문구");
                phraseLabel.setText("일치 문구 · 알림 전체 내용과 정확히 비교");
            } else if (initialLiteral) {
                phrase.setSingleLine(false);
                phrase.setMaxLines(4);
                phrase.setHint("알림 문구 그대로 입력");
                phraseLabel.setText("포함 문구 · 알림 문구 그대로 비교");
            } else {
                phrase.setSingleLine(true);
                phrase.setHint("예: 택배;배송");
                phraseLabel.setText("포함 문구 · ; 로 AND (모두 포함)");
            }
            ModernDialog.updateOptionCard(this, exactCard, exactCheck);
            ModernDialog.updateOptionCard(this, emptyCard, emptyCheck);
        };
        exactCheck.setOnCheckedChangeListener((buttonView, isChecked) -> {
            phrase.setError(null);
            syncForm.run();
        });
        emptyCheck.setOnCheckedChangeListener((buttonView, isChecked) -> syncForm.run());
        help.setOnClickListener(v -> {
            if (initialLiteral && !exactCheck.isChecked() && !emptyCheck.isChecked()) {
                Toast.makeText(this, "알림에서 만든 규칙은 쉼표·세미콜론·줄바꿈도 문구 그대로 비교합니다. 영문 대소문자는 구분하지 않습니다.", Toast.LENGTH_LONG).show();
            } else {
                showRuleHelp();
            }
        });
        syncForm.run();

        ScrollView formScroll = new ScrollView(this);
        formScroll.addView(content);
        AlertDialog dialog = ModernDialog.builder(this,
                        editing ? "차단 규칙 수정" : "차단 규칙 추가",
                        "선택한 앱에서 조건에 맞는 알림을 자동으로 숨깁니다.")
                .setView(formScroll).setNegativeButton("취소", null)
                .setPositiveButton(editing ? "저장" : "추가", null).create();
        addRuleDialog = dialog;
        AppCatalog.Listener listener=(available,busy,failed)->{
            if(addRuleDialog!=dialog || !dialog.isShowing() || isDestroyed())return;
            apps.clear();apps.add(new AppOption("","모든 앱",getDrawable(R.drawable.app_icon)));
            for(AppCatalog.App app:available)apps.add(new AppOption(app.packageName,app.label,null));
            for(AppOption app:apps)if(app.packageName.equals(selected[0].packageName)){selected[0]=app;break;}
            choose.setText("대상 앱: "+selected[0].label+"  ▾");
            appsLoading=busy;appsFailed=failed;
            loading.setText(busy?"앱 목록 확인 중…":failed?"목록을 새로 읽지 못했습니다. 대상 앱 선택에서 다시 시도하세요.":"");
            loading.setVisibility(busy||failed?View.VISIBLE:View.GONE);
            if(pickerAdapter!=null)pickerAdapter.filter(pickerSearch.getText().toString());
            updatePickerStatus();
        };
        if (addRuleButton != null) addRuleButton.setEnabled(false);
        if (activeNotificationsButton != null) activeNotificationsButton.setEnabled(false);
        // Rapid taps at the original Add location must not cancel/reopen it.
        // Explicit Cancel and the Android Back button still close the dialog.
        dialog.setCanceledOnTouchOutside(false);
        dialog.setOnDismissListener(ignored -> {
            catalog.unsubscribe(listener);
            if (addRuleDialog == dialog) {
                if (appPickerDialog != null) appPickerDialog.dismiss();
                if (ruleHelpDialog != null) ruleHelpDialog.dismiss();
                addRuleDialog = null;
                if (addRuleButton != null) addRuleButton.setEnabled(true);
                if (activeNotificationsButton != null) activeNotificationsButton.setEnabled(true);
            }
        });
        dialog.setOnShowListener(ignored -> {
            ModernDialog.styleButtons(dialog, false);
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
                // A queued second positive click after dismissal must not save twice.
                if (addRuleDialog != dialog || !dialog.isShowing()) return;
                boolean matchEmpty = emptyCheck.isChecked();
                boolean exactMode = !matchEmpty && exactCheck.isChecked();
                boolean literalMode = !matchEmpty && !exactMode && initialLiteral;
                String value = matchEmpty ? "" : phrase.getText().toString().trim();
                if (!matchEmpty) {
                    if (TextUtils.isEmpty(value)) {
                        phrase.setError("문구를 입력해 주세요");
                        return;
                    }
                    if (!exactMode && !literalMode && !RuleMatcher.isValidExpression(value)) {
                        phrase.setError("세미콜론(;) 앞뒤에 문구를 입력해 주세요. 빈 조건은 사용할 수 없습니다.");
                        return;
                    }
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
                            value, rules.get(index).enabled, literalMode, exactMode, matchEmpty);
                    rules.set(index, savedRule);
                } else {
                    savedRule = new Rule(System.currentTimeMillis(), selected[0].packageName,
                            selected[0].label, value, true, false, exactMode, matchEmpty);
                    rules.add(savedRule);
                }
                store.save(rules);
                dialog.dismiss();
                int existing = savedRule.enabled ? FilterService.clearExistingForNewRule(savedRule) : 0;
                expandedApps.add(savedRule.packageName);
                String result = editing ? "규칙 수정됨" : "규칙 추가됨";
                String message = !savedRule.enabled ? result + " · 비활성 상태 유지"
                        : existing < 0 ? result + " · 기존 알림 확인에는 알림 접근이 필요합니다"
                        : result + " · 기존 알림 " + existing + "개 숨김 요청";
                Toast.makeText(this, message, Toast.LENGTH_LONG).show();
                render();
            });
        });
        dialog.show();
        ModernDialog.applyChrome(dialog);
        // Show the form before querying packages or reading the on-disk label cache.
        catalog.subscribe(listener);
    }

    private void showActiveNotifications() {
        if (activeNotificationsDialog != null || addRuleDialog != null || isFinishing() || isDestroyed()) return;
        LinearLayout content = new LinearLayout(this) {
            @Override protected void onMeasure(int widthSpec, int heightSpec) {
                // AlertDialog bounds its custom panel after reserving title/actions.
                // Fill that bound so the weighted list receives the remaining space.
                if (View.MeasureSpec.getMode(heightSpec) == View.MeasureSpec.AT_MOST)
                    heightSpec = View.MeasureSpec.makeMeasureSpec(View.MeasureSpec.getSize(heightSpec), View.MeasureSpec.EXACTLY);
                super.onMeasure(widthSpec, heightSpec);
            }
        };
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(20), dp(4), dp(20), 0);

        CheckBox exactCheck = new CheckBox(this);
        exactCheck.setId(R.id.active_notification_exact);
        LinearLayout exactCard = ModernDialog.optionCard(this, exactCheck,
                "정확히 같은 알람만 제거",
                "선택한 알림과 전체 내용이 정확히 일치할 때만 제거합니다.");
        exactCheck.setOnCheckedChangeListener((buttonView, isChecked) ->
                ModernDialog.updateOptionCard(this, exactCard, exactCheck));
        content.addView(exactCard, new LinearLayout.LayoutParams(-1, -2));

        TextView status = text("현재 알림 확인 중…", 13, ModernDialog.MUTED, false);
        status.setId(R.id.active_notification_status);
        status.setPadding(dp(4), dp(10), dp(4), dp(10));
        content.addView(status, new LinearLayout.LayoutParams(-1, -2));

        ListView list = new ListView(this);
        list.setId(R.id.active_notification_list);
        ModernDialog.styleListView(this, list, 8);
        content.addView(list, new LinearLayout.LayoutParams(-1, 0, 1));
        List<FilterService.ActiveNotification> items = new ArrayList<>();
        BaseAdapter adapter = new BaseAdapter() {
            @Override public int getCount() { return items.size(); }
            @Override public FilterService.ActiveNotification getItem(int position) { return items.get(position); }
            @Override public long getItemId(int position) { return position; }
            @Override public boolean areAllItemsEnabled() { return false; }
            @Override public boolean isEnabled(int position) { return getItem(position).canCreateRule(); }
            @Override public View getView(int position, View recycled, android.view.ViewGroup parent) {
                FilterService.ActiveNotification item = getItem(position);
                LinearLayout row = new LinearLayout(MainActivity.this);
                row.setOrientation(LinearLayout.VERTICAL);
                row.setMinimumHeight(dp(64));
                row.setPadding(dp(14), dp(12), dp(14), dp(12));
                row.setBackground(ModernDialog.outlinedRipple(MainActivity.this,
                        0x1A2563EB, ModernDialog.SURFACE_MUTED, ModernDialog.BORDER, 1f, 14));
                row.addView(text(item.appName, 15, ModernDialog.INK, true), new LinearLayout.LayoutParams(-1, -2));
                TextView pkgView = text(item.packageName, 11, ModernDialog.HINT, false);
                row.addView(pkgView, new LinearLayout.LayoutParams(-1, -2));
                if (item.isEmptyContent()) {
                    TextView emptyChip = ModernDialog.badge(MainActivity.this,
                            "내용 없음 · 빈 알림 제거 규칙으로 추가",
                            ModernDialog.SURFACE_TINT, ModernDialog.BLUE_DARK);
                    LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(-2, -2);
                    cp.topMargin = dp(8);
                    row.addView(emptyChip, cp);
                } else {
                    TextView body = text(item.text, 13, ModernDialog.BODY, false);
                    body.setPadding(0, dp(6), 0, 0);
                    row.addView(body, new LinearLayout.LayoutParams(-1, -2));
                }
                row.setAlpha(item.canCreateRule() ? 1f : 0.6f);
                return row;
            }
        };
        list.setAdapter(adapter);
        AlertDialog dialog = ModernDialog.builder(this, "현재 알림 선택",
                        "알림을 누르면 규칙 저장 후 바로 1회 적용합니다.")
                .setView(content).setNegativeButton("닫기", null)
                .setPositiveButton("새로고침", null).create();
        activeNotificationsDialog = dialog;
        activeNotificationsButton.setEnabled(false);
        if (addRuleButton != null) addRuleButton.setEnabled(false);
        dialog.setCanceledOnTouchOutside(false);
        dialog.setOnDismissListener(ignored -> {
            if (activeNotificationsDialog == dialog) {
                activeNotificationsDialog = null;
                activeNotificationsRequest++;
                items.clear();
                if (activeNotificationsButton != null) activeNotificationsButton.setEnabled(true);
                if (addRuleButton != null) addRuleButton.setEnabled(true);
            }
        });
        Runnable refresh = () -> {
            final int request = ++activeNotificationsRequest;
            items.clear(); adapter.notifyDataSetChanged(); list.setEnabled(false);
            status.setText("현재 알림 확인 중…");
            status.setOnClickListener(null);
            Button refreshBtn = dialog.getButton(AlertDialog.BUTTON_POSITIVE);
            if (refreshBtn != null) refreshBtn.setEnabled(false);
            notificationLoader.execute(() -> {
                List<FilterService.ActiveNotification> result;
                try { result = FilterService.activeNotifications(); }
                catch (RuntimeException unavailable) { result = null; }
                final List<FilterService.ActiveNotification> snapshot = result;
                runOnUiThread(() -> {
                    if (isFinishing() || isDestroyed() || activeNotificationsDialog != dialog
                            || !dialog.isShowing() || request != activeNotificationsRequest) return;
                    Button btn = dialog.getButton(AlertDialog.BUTTON_POSITIVE);
                    if (btn != null) btn.setEnabled(true);
                    if (snapshot == null) {
                        status.setText("알림을 읽을 수 없습니다. 여기를 눌러 설정에서 알림 접근을 허용한 뒤 다시 시도해 주세요.");
                        status.setOnClickListener(v -> {
                            dialog.dismiss();
                            startActivity(new Intent(this, SettingsActivity.class));
                        });
                    } else {
                        items.addAll(snapshot); adapter.notifyDataSetChanged(); list.setEnabled(true);
                        status.setText(items.isEmpty() ? "읽을 수 있는 알림이 없습니다. 보안폴더 알림이 없다면 보안폴더의 알림 설정을 확인해 주세요."
                                : "일시정지는 유지됩니다. 내용이 없는 알림도 선택해 제거 규칙으로 추가할 수 있습니다.");
                    }
                });
            });
        };
        list.setOnItemClickListener((parent, view, position, id) -> {
            if (activeNotificationsDialog != dialog || !dialog.isShowing() || !list.isEnabled()
                    || position < 0 || position >= items.size() || !adapter.isEnabled(position)) return;
            FilterService.ActiveNotification item = items.get(position);
            list.setEnabled(false);
            List<Rule> rules = store.load();
            long nextId = System.currentTimeMillis();
            for (Rule rule : rules) if (rule.id >= nextId) nextId = rule.id + 1;
            Rule saved;
            if (item.isEmptyContent()) {
                saved = new Rule(nextId, item.packageName, item.appName, "", true, false, false, true);
            } else {
                boolean exactOnly = exactCheck.isChecked();
                saved = new Rule(nextId, item.packageName, item.appName, item.text,
                        true, !exactOnly, exactOnly, false);
            }
            rules.add(saved);
            store.save(rules);
            expandedApps.add(saved.packageName);
            dialog.dismiss();
            render();
            int count = FilterService.applyCurrentNotificationRuleOnce(saved);
            String message = count < 0 ? "규칙 추가됨 · 1회 적용하려면 알림 접근 연결을 확인해 주세요"
                    : "규칙 추가됨 · 1회 적용: 기존 알림 " + count + "개 숨김 요청";
            if (store.isPaused()) message += " · 일시정지 유지";
            Toast.makeText(this, message, Toast.LENGTH_LONG).show();
        });
        dialog.setOnShowListener(ignored -> {
            ModernDialog.styleButtons(dialog, false);
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> refresh.run());
        });
        dialog.show();
        ModernDialog.applyChrome(dialog);
        dialog.getWindow().setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN);
        View decor = dialog.getWindow().getDecorView();
        decor.getViewTreeObserver().addOnGlobalLayoutListener(() -> {
            if (!dialog.isShowing()) return;
            Rect visible = new Rect(); decor.getWindowVisibleDisplayFrame(visible);
            int height = Math.min(dp(640), Math.round(visible.height() * 0.9f));
            if (height > 0 && Math.abs(dialog.getWindow().getAttributes().height - height) > dp(2))
                dialog.getWindow().setLayout(dialog.getWindow().getAttributes().width, height);
        });
        refresh.run();
    }

    private void showRuleHelp() {
        if (ruleHelpDialog != null || isFinishing() || isDestroyed()) return;
        String guide = "알림의 제목·본문 등에서 문구 일치 여부를 검사합니다.\n\n"
                + "• 기본 포함 문구 (; = AND)\n"
                + "예: 택배;배송 → ‘택배’와 ‘배송’이 모두 포함되면 차단합니다. 단어 순서는 상관없습니다.\n"
                + "OR 조건은 지원하지 않으므로, 하나만 있어도 차단하려면 규칙을 따로 추가해 주세요.\n\n"
                + "• 정확히 같은 알람만 제거\n"
                + "알림의 전체 내용(제목·본문)이 입력한 문구와 정확히 일치할 때만 제거합니다. "
                + "세미콜론(;)도 기호 그대로 비교합니다.\n\n"
                + "• 내용이 없는 알림 제거\n"
                + "제목과 본문이 모두 비어 있는 알림을 자동으로 제거합니다.\n\n"
                + "• 공통 안내\n"
                + "문구 앞뒤 공백은 무시하고 영문 대소문자는 구분하지 않습니다. "
                + "문구 안의 띄어쓰기와 쉼표(,)는 그대로 비교합니다.";
        AlertDialog help = ModernDialog.builder(this, "문구 조건 사용법", null)
                .setView(ModernDialog.messageBody(this, guide))
                .setPositiveButton("확인", null).create();
        ruleHelpDialog = help;
        help.setOnDismissListener(ignored -> {
            if (ruleHelpDialog == help) ruleHelpDialog = null;
        });
        ModernDialog.show(help, false);
    }

    private interface AppSelection { void select(AppOption option); }

    private void showScanResult(int count) {
        Toast.makeText(this, count < 0 ? "기존 알림을 검사하려면 알림 접근 연결이 필요합니다."
                : "기존 알림 " + count + "개 숨김 요청 · 실제 숨김 확인 후 이력에 기록합니다.", Toast.LENGTH_LONG).show();
    }

    private void showAppPicker(List<AppOption> apps, AppSelection onSelected) {
        if (appPickerDialog != null || addRuleDialog == null || isFinishing() || isDestroyed()) return;
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(dp(20), dp(4), dp(20), 0);

        EditText search = new EditText(this);
        search.setId(R.id.app_search);
        search.setSingleLine(true);
        search.setHint("앱 이름 또는 패키지 검색");
        ModernDialog.styleInput(this, search);
        search.setMinHeight(dp(50));
        layout.addView(search, new LinearLayout.LayoutParams(-1, -2));
        pickerSearch=search;
        pickerStatus=text("",12,ModernDialog.BLUE_DARK,true);
        pickerStatus.setId(R.id.app_load_status);
        pickerStatus.setPadding(dp(12),dp(7),dp(12),dp(7));
        pickerStatus.setBackground(ModernDialog.ripple(this, 0x222563EB, ModernDialog.SURFACE_TINT, 10));
        pickerStatus.setOnClickListener(v->catalog.refresh());
        LinearLayout.LayoutParams statusParams = new LinearLayout.LayoutParams(-2, -2);
        statusParams.topMargin = dp(8);
        layout.addView(pickerStatus, statusParams);updatePickerStatus();

        ListView list = new ListView(this);
        ModernDialog.styleListView(this, list, 6);
        AppAdapter adapter = new AppAdapter(apps);
        pickerAdapter=adapter;
        list.setAdapter(adapter);
        LinearLayout.LayoutParams listParams = new LinearLayout.LayoutParams(-1, 0, 1);
        listParams.topMargin = dp(8);
        layout.addView(list, listParams);

        AlertDialog picker = ModernDialog.builder(this, "대상 앱 선택", "규칙을 적용할 앱을 선택하세요.")
                .setView(layout)
                .setNegativeButton("취소", null)
                .create();
        appPickerDialog = picker;
        picker.setCanceledOnTouchOutside(false);
        picker.setOnDismissListener(ignored -> {
            if (appPickerDialog == picker) {appPickerDialog = null;pickerAdapter=null;pickerSearch=null;pickerStatus=null;}
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
        ModernDialog.show(picker, false);
        catalog.refresh();
        picker.getWindow().setSoftInputMode(
                android.view.WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN
                        | android.view.WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        View pickerDecor = picker.getWindow().getDecorView();
        pickerDecor.getViewTreeObserver().addOnGlobalLayoutListener(() -> {
            if (!picker.isShowing()) return;
            Rect visible = new Rect();
            pickerDecor.getWindowVisibleDisplayFrame(visible);
            int height = Math.min(dp(640), Math.round(visible.height() * 0.9f));
            if (height > 0 && Math.abs(picker.getWindow().getAttributes().height - height) > dp(2)) {
                picker.getWindow().setLayout(picker.getWindow().getAttributes().width, height);
            }
        });
    }

    private List<AppOption> installedApps() {
        List<AppOption> result = new ArrayList<>();
        result.add(new AppOption("", "모든 앱", getDrawable(R.drawable.app_icon)));
        for(AppCatalog.App app:catalog.snapshot())result.add(new AppOption(app.packageName,app.label,null));
        return result;
    }

    private void updatePickerStatus(){
        if(pickerStatus==null)return;
        pickerStatus.setText(appsLoading?"앱 목록 불러오는 중…":appsFailed?"앱 목록 새로고침 실패 · 눌러서 다시 시도":"앱 목록 새로고침");
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
            row.setPadding(dp(12), dp(10), dp(12), dp(10));
            row.setBackground(ModernDialog.outlinedRipple(MainActivity.this,
                    0x1A2563EB, ModernDialog.SURFACE_MUTED, ModernDialog.BORDER, 1f, 14));
            ImageView image = new ImageView(MainActivity.this);
            Drawable cached=item.packageName.isEmpty()?null:catalog.cachedIcon(item.packageName);
            image.setImageDrawable(item.icon!=null?item.icon:cached!=null?cached:getDrawable(R.drawable.app_icon));
            image.setTag(item.packageName);
            if(!item.packageName.isEmpty() && cached==null)catalog.icon(item.packageName,icon->{
                if(item.packageName.equals(image.getTag()) && image.isAttachedToWindow())image.setImageDrawable(icon);
            });
            image.setContentDescription(item.label + " 아이콘");
            row.addView(image, new LinearLayout.LayoutParams(dp(38), dp(38)));
            LinearLayout labels = new LinearLayout(MainActivity.this);
            labels.setOrientation(LinearLayout.VERTICAL);
            labels.addView(text(item.label, 15, ModernDialog.INK, true));
            if (!item.packageName.isEmpty()) labels.addView(text(item.packageName, 11, ModernDialog.MUTED, false));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
            lp.leftMargin = dp(12);
            row.addView(labels, lp);
            return row;
        }
    }
}
