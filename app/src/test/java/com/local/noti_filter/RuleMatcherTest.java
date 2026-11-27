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
    @Test public void exactMatchRequiresFullNormalizedNotificationText() {
        Rule exact = new Rule(1, "com.example.chat", "채팅", "광고;이벤트", true, false, true);
        assertTrue(RuleMatcher.matches(Collections.singletonList(exact), "com.example.chat", "광고;이벤트"));
        assertTrue(RuleMatcher.matches(Collections.singletonList(exact), "com.example.chat", "  광고;이벤트  "));
        assertFalse(RuleMatcher.matches(Collections.singletonList(exact), "com.example.chat", "광고;이벤트 안내"));
        assertFalse(RuleMatcher.matches(Collections.singletonList(exact), "com.example.chat", "[광고;이벤트]"));
        assertFalse(RuleMatcher.matches(Collections.singletonList(exact), "com.example.chat", "광고 이벤트"));
        assertFalse(RuleMatcher.matches(Collections.singletonList(exact), "com.other", "광고;이벤트"));

        Rule multiline = new Rule(2, "", "모든 앱", "Title\nBody Line", true, false, true);
        assertTrue(RuleMatcher.matches(Collections.singletonList(multiline), "any", "TITLE\r\nbody line"));
        assertFalse(RuleMatcher.matches(Collections.singletonList(multiline), "any", "Title\nBody Line\nExtra"));
        assertFalse(RuleMatcher.matches(Collections.singletonList(multiline), "any", "Title"));

        Rule emptyExact = new Rule(3, "com.example.chat", "채팅", "   ", true, false, true);
        assertFalse(RuleMatcher.matches(Collections.singletonList(emptyExact), "com.example.chat", ""));
        assertFalse(RuleMatcher.matches(Collections.singletonList(emptyExact), "com.example.chat", "anything"));
    }
    @Test public void exactRuleRemovesTitleOnlyKakaoTalkButKeepsNotificationsWithContent() {
        String kakaoPkg = "com.kakao.talk";
        List<Rule> rules = Collections.singletonList(
                new Rule(1, kakaoPkg, "카카오톡", "카카오톡", true, false, true));

        // 제목이 "카카오톡"이고 내용이 없는 알림들 -> 제거됨
        String titleOnly = FilterService.extractText("카카오톡", null, null, null, null, null, null);
        assertEquals("카카오톡", titleOnly);
        assertEquals("카카오톡", FilterService.matchingText(rules, kakaoPkg, titleOnly, ""));

        String titleAndEmptyBody = FilterService.extractText("카카오톡", "카카오톡", "", "", null, "", "");
        assertEquals("카카오톡", titleAndEmptyBody);
        assertEquals("카카오톡", FilterService.matchingText(rules, kakaoPkg, titleAndEmptyBody, ""));

        String titleAndBlankBody = FilterService.extractText("카카오톡", null, "   ", null, null, null, null);
        assertNotNull(FilterService.matchingText(rules, kakaoPkg, titleAndBlankBody, ""));

        // 원본이 비어 있고 publicVersion만 "카카오톡"인 경우 -> 제거됨
        assertEquals("카카오톡", FilterService.matchingText(rules, kakaoPkg, "", "카카오톡"));

        // 제목이 "카카오톡"이고 내용이 있는 알림들 -> 제거되지 않음
        String withBody = FilterService.extractText("카카오톡", null, "안녕하세요", null, null, null, null);
        assertNull(FilterService.matchingText(rules, kakaoPkg, withBody, ""));

        String withBigText = FilterService.extractText("카카오톡", null, null, "긴 메시지 내용", null, null, null);
        assertNull(FilterService.matchingText(rules, kakaoPkg, withBigText, ""));

        String withLines = FilterService.extractText("카카오톡", null, null, null,
                new CharSequence[]{"홍길동: 안녕하세요"}, null, null);
        assertNull(FilterService.matchingText(rules, kakaoPkg, withLines, ""));

        String withSummary = FilterService.extractText("카카오톡", null, null, null, null, null, "읽지 않은 메시지 2개");
        assertNull(FilterService.matchingText(rules, kakaoPkg, withSummary, ""));

        String senderAndMessage = FilterService.extractText("홍길동", null, "카카오톡 확인해줘", null, null, null, null);
        assertNull(FilterService.matchingText(rules, kakaoPkg, senderAndMessage, ""));

        // 내용이 있는 카카오톡 알림에 잠금화면용 publicVersion이 "카카오톡"만 있어도 제거되지 않음
        assertNull(FilterService.matchingText(rules, kakaoPkg, withBody, "카카오톡"));
        assertNull(FilterService.matchingText(rules, kakaoPkg, senderAndMessage, "카카오톡"));

        // 원본이 비어 있고 publicVersion에 내용이 있는 경우도 제거되지 않음
        assertNull(FilterService.matchingText(rules, kakaoPkg, "", "카카오톡\n새 메시지가 도착했습니다."));

        // 기존 비정확 규칙은 원본이 있어도 publicVersion 매칭 유지
        List<Rule> nonExact = Collections.singletonList(
                new Rule(2, kakaoPkg, "카카오톡", "내용 숨김", true, true, false));
        assertEquals("내용 숨김", FilterService.matchingText(nonExact, kakaoPkg, senderAndMessage, "내용 숨김"));
    }
    @Test public void emptyExactRuleRequiresSameNotificationKeyAndNoAvailableText() {
        Rule rule = new Rule(1, "app", "App", "", true, false, true, "same-key");
        List<Rule> rules = Collections.singletonList(rule);
        assertEquals("", FilterService.matchingText(rules, "app", "", "", "same-key"));
        assertEquals("", FilterService.matchingText(rules, "app", " \t", "\u00a0", "same-key"));
        assertNull(FilterService.matchingText(rules, "app", "", "", "other-key"));
        assertNull(FilterService.matchingText(rules, "other", "", "", "same-key"));
        assertNull(FilterService.matchingText(rules, "app", "original", "", "same-key"));
        assertNull(FilterService.matchingText(rules, "app", "", "public", "same-key"));
        assertNull(FilterService.matchingText(
                Collections.singletonList(rule.withEnabled(false)), "app", "", "", "same-key"));
        assertEquals("", FilterService.matchingText(
                Collections.singletonList(rule.withEnabled(false).withEnabled(true)), "app", "", "", "same-key"));
        assertNull(FilterService.matchingText(
                Collections.singletonList(new Rule(1, "app", "App", "", true, false, true)),
                "app", "", "", "same-key"));
    }

    @Test public void withEnabledPreservesExactMode() {
        Rule exact = new Rule(1, "app", "App", "정확 문구", true, false, true).withEnabled(false).withEnabled(true);
        assertTrue(exact.exact);
        assertTrue(RuleMatcher.matches(Collections.singletonList(exact), "app", "정확 문구"));
        assertFalse(RuleMatcher.matches(Collections.singletonList(exact), "app", "정확 문구 추가"));
    }
}
