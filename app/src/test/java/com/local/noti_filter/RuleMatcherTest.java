package com.local.noti_filter;

import org.junit.Test;
import java.util.Arrays;
import java.util.Collections;
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
}
