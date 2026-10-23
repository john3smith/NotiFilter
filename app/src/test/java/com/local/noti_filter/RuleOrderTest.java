package com.local.noti_filter;

import org.junit.Test;
import java.util.Arrays;
import java.util.List;
import static org.junit.Assert.*;

public class RuleOrderTest {
    private Rule rule(long id,String app,String phrase) { return new Rule(id,"fixture",app,phrase,false); }
    @Test public void creationOrderKeepsOriginalListAndRuleObjects() {
        Rule oldest=rule(1,"가","오래된 규칙"),newest=rule(Long.MAX_VALUE,"나","최신 규칙");
        List<Rule> stored=Arrays.asList(oldest,newest);
        List<Rule> sorted=RuleOrder.sorted(stored,RuleOrder.Mode.NEWEST);
        assertSame(newest,sorted.get(0));assertSame(oldest,stored.get(0));
        assertSame(oldest,RuleOrder.sorted(stored,RuleOrder.Mode.OLDEST).get(0));
        assertFalse(sorted.get(0).enabled);
    }
    @Test public void nameOrderUsesVisibleAppNameThenPhraseAndKeepsDuplicateNames() {
        Rule later=rule(3,"가나다","나 문구"),first=rule(2,"가나다","가 문구"),other=rule(1,"나비","가 문구"),all=rule(4,"","가 문구");
        List<Rule> sorted=RuleOrder.sorted(Arrays.asList(other,later,all,first),RuleOrder.Mode.NAME);
        assertEquals(Arrays.asList(first,later,other,all),sorted);
    }
    @Test public void unknownOrMissingPreferenceUsesNewest() {
        assertEquals(RuleOrder.Mode.NEWEST,RuleOrder.Mode.fromKey(null));
        assertEquals(RuleOrder.Mode.NEWEST,RuleOrder.Mode.fromKey("obsolete"));
        assertEquals(RuleOrder.Mode.OLDEST,RuleOrder.Mode.fromKey("oldest"));
    }
}
