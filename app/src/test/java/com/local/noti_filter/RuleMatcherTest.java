package com.local.noti_filter;

import org.junit.Test;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import static org.junit.Assert.*;

public class RuleMatcherTest {
    @Test public void largeRuleSetRemainsCorrectAcrossSharedCacheEvictionAndEdits() {
        List<Rule> rules = new java.util.ArrayList<>();
        for (int i=0;i<512;i++) rules.add(new Rule(i,"app","App","missing"+i+";today",true));
        rules.add(new Rule(600,"app","App","배송;완료",true));
        rules.add(new Rule(601,"app","App","ordinary;",true));
        for (int repeat=0;repeat<3;repeat++) {
            assertFalse(RuleMatcher.matches(rules,"app","ordinary notification"));
            assertTrue(RuleMatcher.matches(rules,"app","완료된 배송"));
            assertFalse(RuleMatcher.matches(rules,"app","배송 시작"));
            assertFalse(RuleMatcher.matches(rules,"other","배송 완료"));
            for (int i=0;i<256;i++) RuleMatcher.isValidExpression("eviction"+i);
        }
        rules.set(512, rules.get(512).withEnabled(false));
        assertFalse(RuleMatcher.matches(rules,"app","배송 완료"));
        rules.set(512,new Rule(600,"app","App","payment;received",true));
        assertFalse(RuleMatcher.matches(rules,"app","배송 완료"));
        assertTrue(RuleMatcher.matches(rules,"app","payment received"));
    }

    @Test public void concurrentFirstUsePublishesOnlyCompleteExpressions() throws Exception {
        Rule rule=new Rule(1,"","","sale;approved",true);
        java.util.concurrent.ExecutorService pool=java.util.concurrent.Executors.newFixedThreadPool(4);
        try {
            java.util.List<java.util.concurrent.Callable<Boolean>> work=new java.util.ArrayList<>();
            for(int i=0;i<32;i++) work.add(()-> {
                for(int j=0;j<100;j++) {
                    if(!RuleMatcher.matches(Collections.singletonList(rule),"app","APPROVED SALE")) return false;
                    if(RuleMatcher.matches(Collections.singletonList(rule),"app","sale")) return false;
                }
                return true;
            });
            for(java.util.concurrent.Future<Boolean> result:pool.invokeAll(work)) assertTrue(result.get());
        } finally { pool.shutdownNow(); }
    }
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
    @Test public void semicolonRequiresEveryTermInEitherOrder() {
        assertTrue(RuleMatcher.matchesExpression("택배;배송", "배송 예정인 택배"));
        assertFalse(RuleMatcher.matchesExpression("택배;배송", "택배 접수"));
        assertFalse(RuleMatcher.matchesExpression("택배;배송", "배송 안내"));
        assertTrue(RuleMatcher.matchesExpression("광고;결제;승인", "승인된 결제 광고"));
        assertFalse(RuleMatcher.matchesExpression("광고;결제;승인", "광고"));
    }
    @Test public void orIsNoLongerSupported() {
        // ";" used to mean OR; now one missing term rejects the whole rule.
        assertFalse(RuleMatcher.matchesExpression("광고;이벤트", "오늘의 이벤트"));
        assertFalse(RuleMatcher.matchesExpression("광고;이벤트", "광고입니다"));
        assertTrue(RuleMatcher.matchesExpression("광고;이벤트", "광고 이벤트"));
    }
    @Test public void commaIsAnOrdinaryCharacter() {
        assertTrue(RuleMatcher.isValidExpression("택배,배송"));
        assertTrue(RuleMatcher.matchesExpression("택배,배송", "택배,배송 완료"));
        assertFalse(RuleMatcher.matchesExpression("택배,배송", "배송 예정인 택배"));
        assertTrue(RuleMatcher.isValidExpression(","));
        assertTrue(RuleMatcher.matchesExpression("1,000원;결제", "결제 1,000원"));
        assertFalse(RuleMatcher.matchesExpression("1,000원;결제", "결제 1000원"));
    }
    @Test public void trimsEachTermAndIgnoresCaseButKeepsInnerSpaces() {
        assertTrue(RuleMatcher.matchesExpression(" Sale ; TODAY ", "today SALE"));
        assertFalse(RuleMatcher.matchesExpression(" Sale ; TODAY ", "SALE"));
        assertFalse(RuleMatcher.matchesExpression("배송 완료", "배송완료"));
    }
    @Test public void malformedExpressionsFailClosed() {
        for (String bad : new String[]{"", " ", ";", ";광고", "광고;", "광고;;이벤트",
                "광고; ;이벤트", "광고;　"}) {
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
        Rule scoped = new Rule(1, "app", "App", "sale;today", true);
        Rule disabled = new Rule(2, "", "All", "anything", false);
        assertTrue(RuleMatcher.matches(Arrays.asList(scoped, disabled), "app", "TODAY sale"));
        assertFalse(RuleMatcher.matches(Arrays.asList(scoped, disabled), "other", "TODAY sale"));
        assertFalse(RuleMatcher.matches(Arrays.asList(scoped, disabled), "app", "anything"));
        assertFalse(RuleMatcher.matches(Collections.singletonList(scoped), "app", "sale"));
    }
    @Test public void multipleRulesAreOrAndInvalidRulesDoNotMaskValidOnes() {
        List<Rule> rules = Arrays.asList(new Rule(1, "", "", "sale;", true),
                new Rule(2, "", "", "배송;완료", true), new Rule(3, "", "", "광고", true));
        assertTrue(RuleMatcher.matches(rules, "app", "배송 완료"));
        assertTrue(RuleMatcher.matches(rules, "app", "광고"));
        assertFalse(RuleMatcher.matches(rules, "app", "sale"));
    }
    @Test public void onlyAsciiSemicolonIsAnOperator() {
        assertTrue(RuleMatcher.matchesExpression("광고；이벤트", "광고；이벤트"));
        assertFalse(RuleMatcher.matchesExpression("광고；이벤트", "광고 이벤트"));
        assertFalse(RuleMatcher.matchesExpression("(광고)", "광고"));
    }
    @Test public void literalRulesCompareSeparatorsVerbatim() {
        Rule literal = new Rule(1, "", "", "광고;이벤트,안내", true, true);
        assertTrue(RuleMatcher.matches(Collections.singletonList(literal), "app", "[광고;이벤트,안내]"));
        assertFalse(RuleMatcher.matches(Collections.singletonList(literal), "app", "광고 이벤트 안내"));
    }
}
