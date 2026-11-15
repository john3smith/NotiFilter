package com.local.noti_filter;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/** Immutable parsed AND expression (";"-separated terms), with a bounded shared parse cache. */
final class RuleExpression {
    static final String AND = ";";
    private static final Map<String, RuleExpression> CACHE =
            new LinkedHashMap<String, RuleExpression>(128, .75f, true) {
                @Override protected boolean removeEldestEntry(Map.Entry<String, RuleExpression> entry) {
                    return size() > 128;
                }
            };
    private final String[] terms;
    private RuleExpression(String[] terms) { this.terms = terms; }

    static synchronized RuleExpression compile(String expression) {
        if (expression == null) return new RuleExpression(null);
        RuleExpression cached = CACHE.get(expression);
        if (cached != null) return cached;
        String[] parsed = parse(expression);
        RuleExpression result = new RuleExpression(parsed);
        CACHE.put(expression, result);
        return result;
    }
    private static String[] parse(String expression) {
        if (expression.trim().isEmpty()) return null;
        String[] terms = expression.split(AND, -1);
        for (int i = 0; i < terms.length; i++) {
            terms[i] = terms[i].trim().toLowerCase(Locale.ROOT);
            if (isBlank(terms[i])) return null;
        }
        return terms;
    }
    static boolean isBlank(String value) {
        // Reject empty conditions without trimming more characters from literal terms.
        for (int i = 0; i < value.length();) {
            int c = value.codePointAt(i);
            if (!Character.isWhitespace(c) && !Character.isSpaceChar(c)) return false;
            i += Character.charCount(c);
        }
        return true;
    }
    boolean valid() { return terms != null; }
    boolean matchesNormalized(String text) {
        if (terms == null) return false;
        for (String term : terms) if (!text.contains(term)) return false;
        return true;
    }
}
