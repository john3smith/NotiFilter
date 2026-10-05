package com.local.noti_filter;

import android.app.Notification;
import android.os.Bundle;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;
import java.lang.ref.WeakReference;
import java.util.Collections;
import java.util.List;
import java.util.HashMap;
import java.util.Map;
import java.util.LinkedHashSet;
import android.content.pm.PackageManager;
import android.graphics.drawable.Drawable;
import android.os.SystemClock;

public final class FilterService extends NotificationListenerService {
    private static WeakReference<FilterService> connected = new WeakReference<>(null);
    private final Map<String, Pending> pending = new HashMap<>();
    private static final class Pending {
        final String pkg, text;
        final long requestedAt;
        Pending(StatusBarNotification sbn) {
            pkg = sbn.getPackageName(); text = notificationText(sbn);
            requestedAt = SystemClock.elapsedRealtime();
        }
    }

    @Override public void onListenerConnected() {
        super.onListenerConnected();
        connected = new WeakReference<>(this);
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
        RuleStore store = new RuleStore(this);
        if (store.isPaused()) return;
        if (matches(store.load(), sbn)) requestRemoval(sbn);
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

    private boolean requestRemoval(StatusBarNotification sbn) {
        pending.entrySet().removeIf(item -> SystemClock.elapsedRealtime() - item.getValue().requestedAt > 30000);
        if (pending.size() >= 1000 && !pending.containsKey(sbn.getKey())) return false;
        pending.put(sbn.getKey(), new Pending(sbn));
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
        return service == null ? -1 : clearExisting(new RuleStore(service).load());
    }

    private static int clearExisting(List<Rule> rules) {
        FilterService service = connected.get();
        if (service == null) return -1;
        if (new RuleStore(service).isPaused()) return 0;
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
            if (matches(rules, sbn) && service.requestRemoval(sbn)) requested++;
        }
        return requested;
    }

    private static boolean matches(List<Rule> rules, StatusBarNotification sbn) {
        return RuleMatcher.matches(rules, sbn.getPackageName(), notificationText(sbn));
    }

    private static String notificationText(StatusBarNotification sbn) {
        Notification notification = sbn.getNotification();
        if (notification == null) return "";
        Bundle extras = notification.extras;
        if (extras == null) return "";
        try {
        StringBuilder text = new StringBuilder();
        append(text, extras.getCharSequence(Notification.EXTRA_TITLE));
        append(text, extras.getCharSequence(Notification.EXTRA_TEXT));
        append(text, extras.getCharSequence(Notification.EXTRA_BIG_TEXT));
        CharSequence[] lines = extras.getCharSequenceArray(Notification.EXTRA_TEXT_LINES);
        if (lines != null) for (CharSequence line : lines) append(text, line);
        append(text, extras.getCharSequence(Notification.EXTRA_SUB_TEXT));
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
