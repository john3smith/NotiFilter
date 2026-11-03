package com.local.noti_filter;

public final class Rule {
    public final long id;
    public final String packageName;
    public final String appName;
    public final String phrase;
    public final boolean enabled;
    public final boolean literal;
    // Bound to this immutable rule's lifetime, independent of shared-cache eviction.
    private volatile RuleExpression compiledExpression;

    public Rule(long id, String packageName, String appName, String phrase, boolean enabled) {
        this(id, packageName, appName, phrase, enabled, false);
    }

    public Rule(long id, String packageName, String appName, String phrase, boolean enabled, boolean literal) {
        this.id = id;
        this.packageName = packageName == null ? "" : packageName;
        this.appName = appName == null ? "" : appName;
        this.phrase = phrase == null ? "" : phrase.trim();
        this.enabled = enabled;
        this.literal = literal;
    }

    public Rule withEnabled(boolean value) {
        return new Rule(id, packageName, appName, phrase, value, literal);
    }

    boolean matchesNormalized(String text) {
        if (literal) return !phrase.isEmpty() && text.contains(phrase.toLowerCase(java.util.Locale.ROOT));
        RuleExpression expression = compiledExpression;
        if (expression == null) {
            expression = RuleExpression.compile(phrase);
            compiledExpression = expression;
        }
        return expression.matchesNormalized(text);
    }
}
