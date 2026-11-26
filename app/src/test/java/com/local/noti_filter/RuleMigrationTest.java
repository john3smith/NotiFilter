package com.local.noti_filter;

import org.junit.Test;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import static org.junit.Assert.*;

public class RuleMigrationTest {
    /** Reference copy of the pre-1.0.27 matcher: "," = AND, ";" = OR, AND binds tighter. */
    private static boolean legacyMatches(Rule rule, String text) {
        if (!rule.enabled || rule.phrase.isEmpty()) return false;
        String haystack = text.toLowerCase(Locale.ROOT);
        if (rule.literal) return haystack.contains(rule.phrase.toLowerCase(Locale.ROOT));
        if (rule.phrase.trim().isEmpty()) return false;
        String[] or = rule.phrase.split(";", -1);
        String[][] groups = new String[or.length][];
        for (int i = 0; i < or.length; i++) {
            groups[i] = or[i].split(",", -1);
            for (int j = 0; j < groups[i].length; j++) {
                groups[i][j] = groups[i][j].trim().toLowerCase(Locale.ROOT);
                if (RuleExpression.isBlank(groups[i][j])) return false;
            }
        }
        for (String[] group : groups) {
            boolean all = true;
            for (String term : group) if (!haystack.contains(term)) { all = false; break; }
            if (all) return true;
        }
        return false;
    }

    private static List<String> phrases(List<Rule> rules) {
        List<String> result = new ArrayList<>();
        for (Rule rule : rules) result.add(rule.phrase);
        return result;
    }

    @Test public void commaAndBecomesSemicolonAndOrSplitsIntoRules() {
        List<Rule> migrated = RuleMigration.fromLegacy(Collections.singletonList(
                new Rule(1000, "com.example.shop", "Shop", " 택배 , 배송 ; 배달,완료;광고 ", true)));
        assertEquals(Arrays.asList("택배;배송", "배달;완료", "광고"), phrases(migrated));
        for (Rule rule : migrated) {
            assertEquals("com.example.shop", rule.packageName);
            assertEquals("Shop", rule.appName);
            assertTrue(rule.enabled);
            assertFalse(rule.literal);
        }
        assertEquals(1000, migrated.get(0).id);
    }

    @Test public void idsStayUniqueStableAndAdjacent() {
        List<Rule> legacy = Arrays.asList(new Rule(10, "", "", "a;b;c", false),
                new Rule(11, "", "", "x", true), new Rule(20, "", "", "p;q", true));
        List<Rule> migrated = RuleMigration.fromLegacy(legacy);
        Set<Long> ids = new HashSet<>();
        for (Rule rule : migrated) assertTrue("duplicate id " + rule.id, ids.add(rule.id));
        // 11 is taken by an existing rule, so the next free IDs after 10 are used.
        assertEquals(Arrays.asList(10L, 12L, 13L, 11L, 20L, 21L), idList(migrated));
        assertFalse(migrated.get(1).enabled);
        assertFalse(migrated.get(2).enabled);
        assertEquals(idList(migrated), idList(RuleMigration.fromLegacy(legacy)));
    }

    private static List<Long> idList(List<Rule> rules) {
        List<Long> result = new ArrayList<>();
        for (Rule rule : rules) result.add(rule.id);
        return result;
    }

    @Test public void literalAndPlainRulesAreUntouched() {
        Rule literal = new Rule(1, "app", "App", "광고;이벤트,안내", true, true);
        Rule plain = new Rule(2, "", "", "쿠폰", false);
        Rule exact = new Rule(3, "app", "App", "정확;문구,그대로", true, false, true, false);
        Rule empty = new Rule(4, "app", "App", "", true, false, false, true);
        List<Rule> migrated = RuleMigration.fromLegacy(Arrays.asList(literal, plain, exact, empty));
        assertEquals(4, migrated.size());
        assertSame(literal, migrated.get(0));
        assertEquals("쿠폰", migrated.get(1).phrase);
        assertEquals(2, migrated.get(1).id);
        assertFalse(migrated.get(1).enabled);
        assertSame(exact, migrated.get(2));
        assertSame(empty, migrated.get(3));
    }

    @Test public void malformedLegacyRulesStayInvalidAndSingle() {
        for (String bad : new String[]{"광고;", "광고,,이벤트", ";광고", "광고, ;이벤트", ",", ";"}) {
            List<Rule> migrated = RuleMigration.fromLegacy(Collections.singletonList(new Rule(5, "", "", bad, true)));
            assertEquals(bad, 1, migrated.size());
            assertEquals(5, migrated.get(0).id);
            assertFalse(bad, RuleMatcher.isValidExpression(migrated.get(0).phrase));
        }
    }

    @Test public void duplicateAlternativesCollapse() {
        List<Rule> migrated = RuleMigration.fromLegacy(Collections.singletonList(new Rule(1, "", "", "Sale;sale ;a,b", true)));
        assertEquals(Arrays.asList("Sale", "a;b"), phrases(migrated));
    }

    @Test public void migratedRulesMatchExactlyTheSameNotifications() {
        List<Rule> legacy = Arrays.asList(
                new Rule(1, "app", "App", "택배,배송;배달,완료", true),
                new Rule(2, "", "", "광고;결제,승인", true),
                new Rule(3, "", "", "Sale , TODAY ; urgent", true),
                new Rule(4, "other", "Other", "event;coupon", true),
                new Rule(5, "", "", "off;disabled", false),
                new Rule(6, "", "", "broken;", true),
                new Rule(7, "", "", "원문;그대로,비교", true, true),
                new Rule(8, "", "", "광고，이벤트", true));
        List<Rule> migrated = RuleMigration.fromLegacy(legacy);
        String[] packages = {"app", "other", "third"};
        String[] texts = {"배송 예정 택배", "배달 완료", "택배 완료", "광고", "승인된 결제", "결제",
                "today SALE", "URGENT", "sale", "event", "coupon", "off", "disabled", "broken",
                "원문;그대로,비교", "원문 그대로 비교", "광고，이벤트", "이벤트", "nothing"};
        for (String pkg : packages) {
            for (String text : texts) {
                boolean before = false;
                for (Rule rule : legacy) {
                    if (!rule.packageName.isEmpty() && !rule.packageName.equals(pkg)) continue;
                    if (legacyMatches(rule, text)) { before = true; break; }
                }
                assertEquals(pkg + " / " + text, before, RuleMatcher.matches(migrated, pkg, text));
            }
        }
    }

    @Test public void migratingTwiceWouldBeWrongSoCurrentFormatIsNotLegacy() {
        // Documents why RuleStore stores a format version: re-running the legacy
        // conversion on current-format rules would reinterpret ";" AND as OR.
        List<Rule> once = RuleMigration.fromLegacy(Collections.singletonList(new Rule(1, "", "", "a,b", true)));
        assertEquals(Collections.singletonList("a;b"), phrases(once));
        assertEquals(2, RuleMigration.fromLegacy(once).size());
    }
}
