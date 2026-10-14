package com.local.noti_filter;

import org.junit.Test;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import static org.junit.Assert.*;

public class RuleMatcherTest {
    @Test public void matchesSelectedAppAndPhraseIgnoringCase() {
        Rule rule = new Rule(1, "com.example.chat", "채팅", "광고", true);
        assertTrue(RuleMatcher.matches(Collections.singletonList(rule), "com.example.chat", "오늘의 광고입니다"));
        assertFalse(RuleMatcher.matches(Collections.singletonList(rule), "com.other", "광고"));
        assertFalse(RuleMatcher.matches(Collections.singletonList(rule), "com.example.chat", "일반 대화"));
    }
    @Test public void globalRuleAndDisabledRule() {
        Rule all = new Rule(1, "", "모든 앱", "sale", true);
        Rule off = new Rule(2, "", "모든 앱", "urgent", false);
        assertTrue(RuleMatcher.matches(Arrays.asList(all, off), "any.package", "SALE today"));
        assertFalse(RuleMatcher.matches(Arrays.asList(all, off), "any.package", "urgent"));
    }
    @Test public void emptyRuleNeverMatches() {
        assertFalse(RuleMatcher.matches(Collections.singletonList(new Rule(1, "", "", "  ", true)), "app", "anything"));
    }
    @Test public void andRequiresEveryTermInEitherOrder() {
        assertTrue(RuleMatcher.matchesExpression("택배,배송", "배송 예정인 택배"));
        assertFalse(RuleMatcher.matchesExpression("택배,배송", "택배 접수"));
        assertFalse(RuleMatcher.matchesExpression("택배,배송", "배송 안내"));
    }
    @Test public void orAcceptsEitherTerm() {
        assertTrue(RuleMatcher.matchesExpression("광고;이벤트", "오늘의 이벤트"));
        assertTrue(RuleMatcher.matchesExpression("광고;이벤트", "광고입니다"));
        assertFalse(RuleMatcher.matchesExpression("광고;이벤트", "결제 승인"));
    }
    @Test public void andTakesPrecedenceOverOr() {
        String expression = "광고;결제,승인";
        assertTrue(RuleMatcher.matchesExpression(expression, "광고"));
        assertTrue(RuleMatcher.matchesExpression(expression, "승인된 결제"));
        assertFalse(RuleMatcher.matchesExpression(expression, "결제"));
        assertFalse(RuleMatcher.matchesExpression(expression, "승인"));
        assertTrue(RuleMatcher.matchesExpression("택배,배송;배달,완료", "배달 완료"));
        assertFalse(RuleMatcher.matchesExpression("택배,배송;배달,완료", "택배 완료"));
    }
    @Test public void trimsEachTermAndIgnoresCaseButKeepsInnerSpaces() {
        assertTrue(RuleMatcher.matchesExpression(" Sale , TODAY ; urgent ", "today SALE"));
        assertTrue(RuleMatcher.matchesExpression("Sale,TODAY; urgent", "URGENT"));
        assertFalse(RuleMatcher.matchesExpression("배송 완료", "배송완료"));
    }
    @Test public void malformedExpressionsFailClosedEvenIfAnotherGroupMatches() {
        for (String bad : new String[]{"", " ", ",", ";", ",광고", "광고,", "광고;",
                ";광고", "광고,,이벤트", "광고;;이벤트", "광고, ;이벤트", "광고; ,이벤트"}) {
            assertFalse(bad, RuleMatcher.isValidExpression(bad));
            assertFalse(bad, RuleMatcher.matchesExpression(bad, "광고 이벤트"));
        }
    }
    @Test public void nullInputsNeverMatch() {
        assertFalse(RuleMatcher.isValidExpression(null));
        assertFalse(RuleMatcher.matchesExpression(null, "anything"));
        assertFalse(RuleMatcher.matchesExpression("sale", null));
        assertFalse(RuleMatcher.matches(null, "app", "sale"));
        assertFalse(RuleMatcher.matches(Collections.emptyList(), null, "sale"));
        assertFalse(RuleMatcher.matches(Collections.emptyList(), "app", null));
    }
    @Test public void expressionStillRespectsAppScopeAndEnabledFlag() {
        Rule scoped = new Rule(1, "app", "App", "sale,today;urgent", true);
        Rule disabled = new Rule(2, "", "All", "anything;else", false);
        assertTrue(RuleMatcher.matches(Arrays.asList(scoped, disabled), "app", "TODAY sale"));
        assertFalse(RuleMatcher.matches(Arrays.asList(scoped, disabled), "other", "TODAY sale"));
        assertFalse(RuleMatcher.matches(Arrays.asList(scoped, disabled), "app", "anything"));
        assertFalse(RuleMatcher.matches(Collections.singletonList(scoped), "app", "sale"));
    }
    @Test public void multipleRulesAreOrAndInvalidRulesDoNotMaskValidOnes() {
        List<Rule> rules = Arrays.asList(new Rule(1, "", "", "sale;", true),
                new Rule(2, "", "", "배송,완료", true));
        assertTrue(RuleMatcher.matches(rules, "app", "배송 완료"));
        assertFalse(RuleMatcher.matches(rules, "app", "sale"));
    }
    @Test public void onlyAsciiSeparatorsAreOperators() {
        assertTrue(RuleMatcher.matchesExpression("광고，이벤트", "광고，이벤트"));
        assertFalse(RuleMatcher.matchesExpression("광고，이벤트", "광고"));
        assertFalse(RuleMatcher.matchesExpression("(광고)", "광고"));
    }
}
