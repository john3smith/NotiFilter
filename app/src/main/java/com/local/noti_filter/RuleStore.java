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
    private final SharedPreferences prefs;

    public RuleStore(Context context) {
        prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public boolean isPaused() { return prefs.getBoolean(KEY_PAUSED, false); }
    public void setPaused(boolean value) { prefs.edit().putBoolean(KEY_PAUSED, value).apply(); }

    public List<Rule> load() {
        List<Rule> result = new ArrayList<>();
        try {
            JSONArray array = new JSONArray(prefs.getString(KEY_RULES, "[]"));
            for (int i = 0; i < array.length(); i++) {
                JSONObject item = array.getJSONObject(i);
                result.add(new Rule(item.getLong("id"), item.optString("package"),
                        item.optString("app"), item.optString("phrase"), item.optBoolean("enabled", true)));
            }
        } catch (JSONException ignored) {
            // An invalid local record must never cause unrelated notifications to be removed.
            return new ArrayList<>();
        }
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
