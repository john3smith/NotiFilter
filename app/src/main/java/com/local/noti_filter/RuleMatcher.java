package com.local.noti_filter;

import java.util.List;
import java.util.Locale;

public final class RuleMatcher {
    private RuleMatcher() {}

    public static boolean matches(List<Rule> rules, String sourcePackage, String notificationText) {
        if (rules == null || sourcePackage == null || notificationText == null) return false;
        String haystack = notificationText.toLowerCase(Locale.ROOT);
        for (Rule rule : rules) {
            if (!rule.enabled || rule.phrase.isEmpty()) continue;
            if (!rule.packageName.isEmpty() && !rule.packageName.equals(sourcePackage)) continue;
            if (haystack.contains(rule.phrase.toLowerCase(Locale.ROOT))) return true;
        }
        return false;
    }
}
