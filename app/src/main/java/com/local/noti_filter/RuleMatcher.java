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
            if (matchesExpression(rule.phrase, haystack)) return true;
        }
        return false;
    }

    // OR groups contain AND terms: A,B;C means (A AND B) OR C.
    // Reject the entire expression if any term is empty; never match-all.
    private static String[][] parse(String expression) {
        if (expression == null || expression.trim().isEmpty()) return null;
        String[] groups = expression.split(";", -1);
        String[][] terms = new String[groups.length][];
        for (int i = 0; i < groups.length; i++) {
            terms[i] = groups[i].split(",", -1);
            for (int j = 0; j < terms[i].length; j++) {
                terms[i][j] = terms[i][j].trim().toLowerCase(Locale.ROOT);
                if (terms[i][j].isEmpty()) return null;
            }
        }
        return terms;
    }

    public static boolean isValidExpression(String expression) {
        return parse(expression) != null;
    }

    public static boolean matchesExpression(String expression, String notificationText) {
        if (notificationText == null) return false;
        String[][] groups = parse(expression);
        if (groups == null) return false;
        String text = notificationText.toLowerCase(Locale.ROOT);
        for (String[] group : groups) {
            boolean all = true;
            for (String term : group) {
                if (!text.contains(term)) { all = false; break; }
            }
            if (all) return true;
        }
        return false;
    }
}
