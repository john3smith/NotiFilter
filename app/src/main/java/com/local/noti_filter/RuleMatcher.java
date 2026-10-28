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
            if (rule.matchesNormalized(haystack)) return true;
        }
        return false;
    }

    public static boolean isValidExpression(String expression) {
        return RuleExpression.compile(expression).valid();
    }
    public static boolean matchesExpression(String expression, String notificationText) {
        return notificationText != null && RuleExpression.compile(expression)
                .matchesNormalized(notificationText.toLowerCase(Locale.ROOT));
    }
}
