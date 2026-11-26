package com.local.noti_filter;

import java.util.Locale;

public final class Rule {
    public final long id;
    public final String packageName;
    public final String appName;
    public final String phrase;
    public final boolean enabled;
    public final boolean literal;
    public final boolean exact;
    // Bound to this immutable rule's lifetime, independent of shared-cache eviction.
    private volatile RuleExpression compiledExpression;

    public Rule(long id, String packageName, String appName, String phrase, boolean enabled) {
        this(id, packageName, appName, phrase, enabled, false, false);
    }

    public Rule(long id, String packageName, String appName, String phrase, boolean enabled, boolean literal) {
        this(id, packageName, appName, phrase, enabled, literal, false);
    }

    public Rule(long id, String packageName, String appName, String phrase, boolean enabled,
                boolean literal, boolean exact) {
        this.id = id;
        this.packageName = packageName == null ? "" : packageName;
        this.appName = appName == null ? "" : appName;
        this.exact = exact;
        this.literal = !exact && literal;
        this.phrase = phrase == null ? "" : normalizeLineEndings(phrase).trim();
        this.enabled = enabled;
    }

    public Rule withEnabled(boolean value) {
        return new Rule(id, packageName, appName, phrase, value, literal, exact);
    }

    static String normalizeLineEndings(String value) {
        return value.indexOf('\r') < 0 ? value : value.replace("\r\n", "\n").replace('\r', '\n');
    }

    boolean matchesNormalized(String text) {
        if (exact) {
            return !phrase.isEmpty()
                    && normalizeLineEndings(text).trim().equals(phrase.toLowerCase(Locale.ROOT));
        }
        if (literal) {
            return !phrase.isEmpty()
                    && normalizeLineEndings(text).contains(phrase.toLowerCase(Locale.ROOT));
        }
        RuleExpression expression = compiledExpression;
        if (expression == null) {
            expression = RuleExpression.compile(phrase);
            compiledExpression = expression;
        }
        return expression.matchesNormalized(text);
    }
}
