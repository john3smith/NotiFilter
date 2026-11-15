package com.local.noti_filter;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Converts rules saved with the legacy grammar ("," = AND, ";" = OR) to the current
 * grammar (";" = AND, no OR). Each OR alternative becomes its own rule so that the
 * set of matched notifications is unchanged. Pure and deterministic for retry safety.
 */
final class RuleMigration {
    private RuleMigration() {}

    static List<Rule> fromLegacy(List<Rule> legacy) {
        Set<Long> used = new HashSet<>();
        for (Rule rule : legacy) used.add(rule.id);
        List<Rule> result = new ArrayList<>(legacy.size());
        for (Rule rule : legacy) {
            List<String> alternatives = rule.literal ? null : legacyAlternatives(rule.phrase);
            if (rule.literal) {
                result.add(rule);
            } else if (alternatives == null) {
                // Malformed legacy expressions never matched; keep them invalid (same blank terms).
                result.add(copy(rule, rule.id, rule.phrase.replace(',', ';')));
            } else {
                long next = rule.id;
                for (int i = 0; i < alternatives.size(); i++) {
                    long id = rule.id;
                    if (i > 0) {
                        // Stay adjacent to the source rule in creation order.
                        do next++; while (used.contains(next));
                        used.add(next);
                        id = next;
                    }
                    result.add(copy(rule, id, alternatives.get(i)));
                }
            }
        }
        return result;
    }

    /** OR alternatives in the current AND syntax, or null when the legacy expression was invalid. */
    private static List<String> legacyAlternatives(String expression) {
        if (expression.trim().isEmpty()) return null;
        Set<String> seen = new HashSet<>();
        Set<String> alternatives = new LinkedHashSet<>();
        for (String group : expression.split(";", -1)) {
            String[] terms = group.split(",", -1);
            StringBuilder joined = new StringBuilder();
            for (int i = 0; i < terms.length; i++) {
                String term = terms[i].trim();
                if (RuleExpression.isBlank(term)) return null;
                if (i > 0) joined.append(RuleExpression.AND);
                joined.append(term);
            }
            String value = joined.toString();
            if (seen.add(value.toLowerCase(Locale.ROOT))) alternatives.add(value);
        }
        return new ArrayList<>(alternatives);
    }

    private static Rule copy(Rule rule, long id, String phrase) {
        return new Rule(id, rule.packageName, rule.appName, phrase, rule.enabled, rule.literal);
    }
}
