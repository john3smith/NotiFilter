package com.local.noti_filter;

import android.content.Context;
import android.content.SharedPreferences;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.List;

public final class RuleStore {
    private static final String PREFS = "filter_rules";
    private static final String KEY_RULES = "rules";
    private static final String KEY_PAUSED = "paused";
    private static final String KEY_SORT = "sort_order";
    private final SharedPreferences prefs;
    private String cachedJson;
    private List<Rule> cachedRules;

    public RuleStore(Context context) {
        prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public boolean isPaused() { return prefs.getBoolean(KEY_PAUSED, false); }
    public void setPaused(boolean value) { prefs.edit().putBoolean(KEY_PAUSED, value).apply(); }
    public RuleOrder.Mode sortOrder() { return RuleOrder.Mode.fromKey(prefs.getString(KEY_SORT, "newest")); }
    public void setSortOrder(RuleOrder.Mode mode) { prefs.edit().putString(KEY_SORT, mode.key).apply(); }

    public synchronized List<Rule> load() {
        String encoded = prefs.getString(KEY_RULES, "[]");
        if (encoded.equals(cachedJson) && cachedRules != null) return new ArrayList<>(cachedRules);
        List<Rule> result = new ArrayList<>();
        try {
            JSONArray array = new JSONArray(encoded);
            for (int i = 0; i < array.length(); i++) {
                JSONObject item = array.getJSONObject(i);
                // Missing scope becomes "all apps" and a missing toggle becomes enabled
                // with opt* defaults. Reject malformed values rather than widen a rule.
                if (!(item.get("package") instanceof String)
                        || !(item.get("phrase") instanceof String)
                        || !(item.get("enabled") instanceof Boolean)) {
                    throw new JSONException("Invalid rule scope, phrase or enabled value");
                }
                result.add(new Rule(item.getLong("id"), item.getString("package"),
                        item.optString("app"), item.getString("phrase"), item.getBoolean("enabled")));
            }
        } catch (JSONException ignored) {
            // An invalid local record must never cause unrelated notifications to be removed.
            result.clear();
        }
        cachedJson = encoded;
        cachedRules = new ArrayList<>(result);
        return result;
    }

    public void save(List<Rule> rules) {
        JSONArray array = new JSONArray();
        for (Rule rule : rules) {
            JSONObject item = new JSONObject();
            try {
                item.put("id", rule.id);
                item.put("package", rule.packageName);
                item.put("app", rule.appName);
                item.put("phrase", rule.phrase);
                item.put("enabled", rule.enabled);
                array.put(item);
            } catch (JSONException impossible) { throw new IllegalStateException(impossible); }
        }
        prefs.edit().putString(KEY_RULES, array.toString()).apply();
    }
}
