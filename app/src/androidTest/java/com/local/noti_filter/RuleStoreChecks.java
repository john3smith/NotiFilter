package com.local.noti_filter;

import android.content.Context;
import android.content.SharedPreferences;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.List;

/** Real Android JSON/preferences semantics; never runs against the installed user package. */
final class RuleStoreChecks {
    static String run(Context context) throws Exception {
        SharedPreferences prefs = context.getSharedPreferences("filter_rules", Context.MODE_PRIVATE);
        boolean hadRules = prefs.contains("rules");
        String before = prefs.getString("rules", "[]");
        RuleStore ui = new RuleStore(context), service = new RuleStore(context);
        int rejected = 0;
        List<String> failures = new ArrayList<>();
        try {
            // Nulls, absent members and JSON coercions must not widen package/enable/text scope.
            String[] fields = {"package", "phrase", "enabled"};
            Object[][] invalid = {{null, JSONObject.NULL, 123, false},
                                  {null, JSONObject.NULL, 123, true},
                                  {null, JSONObject.NULL, "true", 1}};
            for (int field = 0; field < fields.length; field++) {
                for (Object value : invalid[field]) {
                    JSONObject item = valid();
                    if (value == null) item.remove(fields[field]); else item.put(fields[field], value);
                    String raw = new JSONArray().put(item).toString();
                    if (!prefs.edit().putString("rules", raw).commit()) throw new AssertionError("Fixture write failed");
                    if (!ui.load().isEmpty() || !service.load().isEmpty()) failures.add(fields[field] + ":" + String.valueOf(value));
                    else rejected++;
                    if (!raw.equals(prefs.getString("rules", ""))) throw new AssertionError("Load rewrote source data");
                }
            }
            // No partially trusted list after a malformed member, preserving existing fail-closed policy.
            JSONObject bad = valid(); bad.remove("package");
            prefs.edit().putString("rules", new JSONArray().put(valid()).put(bad).toString()).commit();
            if (!ui.load().isEmpty()) failures.add("partially accepted array"); else rejected++;

            List<Rule> expected = new ArrayList<>();
            expected.add(new Rule(1, "com.example.scope", "SYNTHETIC APP", "coupon", true));
            expected.add(new Rule(2, "", "모든 앱", "sale,done;event", true));
            expected.add(new Rule(3, "", "모든 앱", "disabled", false));
            ui.save(expected);
            List<Rule> loaded = service.load();
            if (loaded.size() != 3 || !loaded.get(0).packageName.equals("com.example.scope")
                    || !loaded.get(1).packageName.isEmpty() || loaded.get(2).enabled)
                throw new AssertionError("Valid scope/toggle round trip");
            if (!RuleMatcher.matches(loaded, "com.example.scope", "COUPON")
                    || RuleMatcher.matches(loaded, "com.example.other", "coupon")
                    || !RuleMatcher.matches(loaded, "com.example.other", "sale done")
                    || !RuleMatcher.matches(loaded, "com.example.other", "event")
                    || RuleMatcher.matches(loaded, "com.example.other", "sale")
                    || RuleMatcher.matches(loaded, "com.example.other", "disabled"))
                throw new AssertionError("Valid matching regression");
            loaded.clear();
            if (service.load().size() != 3) throw new AssertionError("Cache defensive copy");
            expected.set(0, expected.get(0).withEnabled(false)); ui.save(expected);
            if (RuleMatcher.matches(service.load(), "com.example.scope", "coupon"))
                throw new AssertionError("Service cache ignored updated UI preferences");
            if (!failures.isEmpty()) throw new AssertionError("Unsafe persisted rules accepted: " + failures);
            return "PASS rule-store: 13 malformed records rejected without rewriting preferences; valid save/load, scoped/global/disabled and AND/OR matching, cross-instance cache refresh and defensive copies\n";
        } finally {
            SharedPreferences.Editor restore = prefs.edit();
            if (hadRules) restore.putString("rules", before); else restore.remove("rules");
            if (!restore.commit() || prefs.contains("rules") != hadRules
                    || (hadRules && !before.equals(prefs.getString("rules", ""))))
                throw new AssertionError("Isolated fixture restoration failed");
        }
    }
    private static JSONObject valid() throws Exception {
        return new JSONObject().put("id", 77).put("package", "com.example.scope")
                .put("app", "SYNTHETIC APP").put("phrase", "coupon").put("enabled", true);
    }
}
