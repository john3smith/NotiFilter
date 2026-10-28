package com.local.noti_filter;

public final class Rule {
    public final long id;
    public final String packageName;
    public final String appName;
    public final String phrase;
    public final boolean enabled;
    // Bound to this immutable rule's lifetime, independent of shared-cache eviction.
    private volatile RuleExpression compiledExpression;

    public Rule(long id, String packageName, String appName, String phrase, boolean enabled) {
        this.id = id;
        this.packageName = packageName == null ? "" : packageName;
        this.appName = appName == null ? "" : appName;
        this.phrase = phrase == null ? "" : phrase.trim();
        this.enabled = enabled;
    }

    public Rule withEnabled(boolean value) {
        return new Rule(id, packageName, appName, phrase, value);
    }

    boolean matchesNormalized(String text) {
        RuleExpression expression = compiledExpression;
        if (expression == null) {
            expression = RuleExpression.compile(phrase);
            compiledExpression = expression;
        }
        return expression.matchesNormalized(text);
    }
}
