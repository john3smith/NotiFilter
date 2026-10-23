package com.local.noti_filter;

import java.text.Collator;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/** Presentation order only. Never change the stored rules or their creation IDs. */
public final class RuleOrder {
    public enum Mode {
        NEWEST("newest", "최신순"), OLDEST("oldest", "오래된순"), NAME("name", "이름순");
        public final String key;
        public final String label;
        Mode(String key, String label) { this.key=key;this.label=label; }
        public static Mode fromKey(String value) {
            for (Mode mode : values()) if (mode.key.equals(value)) return mode;
            return NEWEST;
        }
    }
    private RuleOrder() {}
    private static String displayName(Rule rule) { return rule.appName.isEmpty() ? "모든 앱" : rule.appName; }
    public static List<Rule> sorted(List<Rule> rules, Mode mode) {
        List<Rule> result=new ArrayList<>(rules);
        Comparator<Rule> creation=Comparator.comparingLong(rule -> rule.id);
        if (mode==Mode.NAME) {
            Collator names=Collator.getInstance(Locale.KOREAN);
            names.setStrength(Collator.SECONDARY);
            result.sort((a,b) -> {
                int compared=names.compare(displayName(a),displayName(b));
                if (compared==0) compared=names.compare(a.phrase,b.phrase);
                if (compared==0) compared=a.packageName.compareTo(b.packageName);
                return compared!=0 ? compared : Long.compare(b.id,a.id);
            });
        } else result.sort(mode==Mode.OLDEST ? creation : creation.reversed());
        return result;
    }
}
