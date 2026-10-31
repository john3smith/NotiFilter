package com.local.noti_filter;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/** Immutable parsed OR-of-AND expression, with a bounded shared parse cache. */
final class RuleExpression {
    private static final Map<String, RuleExpression> CACHE =
            new LinkedHashMap<String, RuleExpression>(128, .75f, true) {
                @Override protected boolean removeEldestEntry(Map.Entry<String, RuleExpression> entry) {
                    return size() > 128;
                }
            };
    private final String[][] groups;
    private RuleExpression(String[][] groups) { this.groups = groups; }

    static synchronized RuleExpression compile(String expression) {
        if (expression == null) return new RuleExpression(null);
        RuleExpression cached = CACHE.get(expression);
        if (cached != null) return cached;
        String[][] parsed = parse(expression);
        RuleExpression result = new RuleExpression(parsed);
        CACHE.put(expression, result);
        return result;
    }
    private static String[][] parse(String expression) {
        if (expression.trim().isEmpty()) return null;
        String[] or = expression.split(";", -1);
        String[][] groups = new String[or.length][];
        for (int i = 0; i < or.length; i++) {
            groups[i] = or[i].split(",", -1);
            for (int j = 0; j < groups[i].length; j++) {
                groups[i][j] = groups[i][j].trim().toLowerCase(Locale.ROOT);
                if (isBlank(groups[i][j])) return null;
            }
        }
        return groups;
    }
    private static boolean isBlank(String value) {
        // Reject empty conditions without trimming more characters from literal terms.
        for (int i = 0; i < value.length();) {
            int c = value.codePointAt(i);
            if (!Character.isWhitespace(c) && !Character.isSpaceChar(c)) return false;
            i += Character.charCount(c);
        }
        return true;
    }
    boolean valid() { return groups != null; }
    boolean matchesNormalized(String text) {
        if (groups == null) return false;
        for (String[] group : groups) {
            boolean all = true;
            for (String term : group) if (!text.contains(term)) { all = false; break; }
            if (all) return true;
        }
        return false;
    }
}
