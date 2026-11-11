package com.local.noti_filter;

import android.app.Notification;
import android.os.Bundle;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;
import java.lang.ref.WeakReference;
import java.util.Collections;
import java.util.List;
import java.util.LinkedHashSet;
import android.content.pm.PackageManager;
import android.graphics.drawable.Drawable;
import android.os.SystemClock;

public final class FilterService extends NotificationListenerService {
    private static volatile WeakReference<FilterService> connected = new WeakReference<>(null);
    private RuleStore rules;
    @Override public void onCreate() { super.onCreate(); rules = new RuleStore(this); }
    private final PendingRemovals<Pending> pending = new PendingRemovals<>(1000, 30000, item -> item.requestedAt);
    private static final class Pending {
        final String pkg, text;
        final long requestedAt;
        Pending(StatusBarNotification sbn, String matchedText, long now) {
            pkg = sbn.getPackageName(); text = matchedText;
            requestedAt = now;
        }
    }

    @Override public void onListenerConnected() {
        super.onListenerConnected();
        connected = new WeakReference<>(this);
        // A reconnect (including profile availability changes) need not repost
        // notifications that were already present while the listener was offline.
        clearExistingForEnabledRules();
    }

    @Override public void onListenerDisconnected() {
        pending.clear();
        connected.clear();
        super.onListenerDisconnected();
    }

    @Override public void onDestroy() {
        if (connected.get() == this) connected.clear();
        pending.clear();
        super.onDestroy();
    }

    @Override public void onNotificationPosted(StatusBarNotification sbn) {
        if (sbn == null || getPackageName().equals(sbn.getPackageName())) return;
        RuleStore store = rules;
        if (store.isPaused()) return;
        String matched = matchingText(store.load(), sbn);
        if (matched != null) requestRemoval(sbn, matched);
    }

    @Override public void onNotificationRemoved(StatusBarNotification sbn, RankingMap ranking, int reason) {
        if (sbn == null) return;
        Pending record = pending.remove(sbn.getKey());
        if (record == null || reason != REASON_LISTENER_CANCEL
                || SystemClock.elapsedRealtime() - record.requestedAt > 30000) return;
        String label = record.pkg;
        Drawable icon = getDrawable(R.drawable.app_icon);
        try {
            PackageManager pm = getPackageManager();
            label = pm.getApplicationLabel(pm.getApplicationInfo(record.pkg, 0)).toString();
            icon = pm.getApplicationIcon(record.pkg);
        } catch (PackageManager.NameNotFoundException | RuntimeException unavailable) {
            // Keep package and a fallback icon even if the source app was uninstalled.
        }
        HistoryStore.get(this).record(System.currentTimeMillis(), record.pkg, label, record.text, icon);
    }

    private boolean requestRemoval(StatusBarNotification sbn, String matchedText) {
        long now = SystemClock.elapsedRealtime();
        if (!pending.offer(sbn.getKey(), () -> new Pending(sbn, matchedText, now), now)) return false;
        try { cancelNotification(sbn.getKey()); return true; }
        catch (SecurityException | IllegalStateException unavailable) {
            pending.remove(sbn.getKey()); return false;
        }
    }

    // User-triggered scans only; disabled rules and the global pause are respected.
    public static int clearExistingForNewRule(Rule rule) {
        return clearExisting(Collections.singletonList(rule));
    }

    public static int clearExistingForEnabledRules() {
        FilterService service = connected.get();
        return service == null ? -1 : clearExisting(service.rules.load());
    }

    /** Transient snapshot; only the selected notification becomes a saved rule. */
    public static final class ActiveNotification {
        public final String packageName, appName, text;
        public final long postedAt;
        ActiveNotification(String packageName, String appName, String text, long postedAt) {
            this.packageName = packageName;
            this.appName = appName;
            this.text = text;
            this.postedAt = postedAt;
        }
        public boolean canCreateRule() {
            return text.codePoints().anyMatch(c -> !Character.isWhitespace(c) && !Character.isSpaceChar(c));
        }
    }

    // Off the UI thread: listener binder and app labels can be slow.
    // null means unavailable; empty means a successful empty snapshot.
    public static List<ActiveNotification> activeNotifications() {
        FilterService service = connected.get();
        if (service == null) return null;
        try {
            StatusBarNotification[] active = service.getActiveNotifications();
            if (active == null) return null;
            List<ActiveNotification> result = new java.util.ArrayList<>();
            PackageManager pm = service.getPackageManager();
            for (StatusBarNotification sbn : active) {
                if (sbn == null || service.getPackageName().equals(sbn.getPackageName())) continue;
                String pkg = sbn.getPackageName();
                if (pkg == null || pkg.isEmpty()) continue;
                String label = pkg;
                try { label = pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString(); }
                catch (PackageManager.NameNotFoundException | RuntimeException unavailable) { /* Use package. */ }
                String original = notificationText(sbn.getNotification()).trim();
                String visible = publicText(sbn.getNotification()).trim();
                if (!sbn.getUser().equals(android.os.Process.myUserHandle())) label += " · 다른 프로필";
                result.add(new ActiveNotification(pkg, label,
                        original.isEmpty() ? visible : original, sbn.getPostTime()));
                if (!original.isEmpty() && !visible.isEmpty() && !visible.equals(original)) {
                    result.add(new ActiveNotification(pkg, label + " · 내용 숨김 표시", visible, sbn.getPostTime()));
                }
            }
            result.sort((a, b) -> Long.compare(b.postedAt, a.postedAt));
            return result;
        } catch (SecurityException | IllegalStateException unavailable) { return null; }
    }

    private static int clearExisting(List<Rule> rules) {
        FilterService service = connected.get();
        if (service == null) return -1;
        if (service.rules.isPaused()) return 0;
        StatusBarNotification[] active;
        try {
            active = service.getActiveNotifications();
        } catch (SecurityException | IllegalStateException unavailable) {
            return -1;
        }
        if (active == null) return -1;
        int requested = 0;
        for (StatusBarNotification sbn : active) {
            if (sbn == null || service.getPackageName().equals(sbn.getPackageName())) continue;
            String matched = matchingText(rules, sbn);
            if (matched != null && service.requestRemoval(sbn, matched)) requested++;
        }
        return requested;
    }

    private static String matchingText(List<Rule> rules, StatusBarNotification sbn) {
        String original = notificationText(sbn.getNotification());
        if (RuleMatcher.matches(rules, sbn.getPackageName(), original)) return original;
        String visible = publicText(sbn.getNotification());
        // Each representation must match independently. Combining them could
        // satisfy an AND rule using words that never appear together on screen.
        return !visible.isEmpty() && RuleMatcher.matches(rules, sbn.getPackageName(), visible)
                ? visible : null;
    }

    private static String publicText(Notification notification) {
        // Read only the public replacement supplied by Android; never recover
        // protected content or recursively follow an untrusted publicVersion.
        return notification == null ? "" : notificationText(notification.publicVersion);
    }

    private static String notificationText(Notification notification) {
        if (notification == null) return "";
        Bundle extras = notification.extras;
        if (extras == null) return "";
        try {
        StringBuilder text = new StringBuilder();
        append(text, extras.getCharSequence(Notification.EXTRA_TITLE));
        append(text, extras.getCharSequence(Notification.EXTRA_TITLE_BIG));
        append(text, extras.getCharSequence(Notification.EXTRA_TEXT));
        append(text, extras.getCharSequence(Notification.EXTRA_BIG_TEXT));
        CharSequence[] lines = extras.getCharSequenceArray(Notification.EXTRA_TEXT_LINES);
        if (lines != null) for (CharSequence line : lines) append(text, line);
        append(text, extras.getCharSequence(Notification.EXTRA_SUB_TEXT));
        append(text, extras.getCharSequence(Notification.EXTRA_SUMMARY_TEXT));
        // Expanded notifications often repeat the compact body; display it once.
        LinkedHashSet<String> unique = new LinkedHashSet<>();
        for (String part : text.toString().split("\\n")) if (!part.isEmpty()) unique.add(part);
        return String.join("\n", unique);
        } catch (RuntimeException malformed) { return ""; }
    }

    private static void append(StringBuilder out, CharSequence value) {
        if (value != null) out.append(value).append('\n');
    }
}
