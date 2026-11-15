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
    // Absent = legacy "," AND / ";" OR grammar; 2 = ";" AND only.
    private static final String KEY_FORMAT = "rules_format";
    private static final int LEGACY_FORMAT = 1;
    private static final int CURRENT_FORMAT = 2;
    // UI and service hold separate stores over one process-wide preferences file.
    private static final Object MIGRATION_LOCK = new Object();
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
        synchronized (MIGRATION_LOCK) {
            if (prefs.getInt(KEY_FORMAT, LEGACY_FORMAT) < CURRENT_FORMAT) migrate();
        }
        String encoded = prefs.getString(KEY_RULES, "[]");
        if (encoded.equals(cachedJson) && cachedRules != null) return new ArrayList<>(cachedRules);
        List<Rule> result = parse(encoded);
        cachedJson = encoded;
        cachedRules = new ArrayList<>(result);
        return result;
    }

    /** Rewrites legacy ","/";" rules once; rules and format version are committed together. */
    private void migrate() {
        if (!prefs.contains(KEY_RULES)) {
            prefs.edit().putInt(KEY_FORMAT, CURRENT_FORMAT).commit();
            return;
        }
        String encoded = prefs.getString(KEY_RULES, "[]");
        List<Rule> legacy;
        try {
            legacy = parseStrict(encoded);
        } catch (JSONException invalid) {
            // Leave unreadable data untouched; load() keeps failing closed for it.
            return;
        }
        // One editor updates both keys in memory together; if the disk write fails, the legacy
        // file is still intact there and the deterministic migration reruns in the next process.
        prefs.edit().putString(KEY_RULES, encode(RuleMigration.fromLegacy(legacy)))
                .putInt(KEY_FORMAT, CURRENT_FORMAT).commit();
    }

    private static List<Rule> parse(String encoded) {
        try {
            return parseStrict(encoded);
        } catch (JSONException ignored) {
            // An invalid local record must never cause unrelated notifications to be removed.
            return new ArrayList<>();
        }
    }

    private static List<Rule> parseStrict(String encoded) throws JSONException {
        List<Rule> result = new ArrayList<>();
        JSONArray array = new JSONArray(encoded);
        for (int i = 0; i < array.length(); i++) {
            JSONObject item = array.getJSONObject(i);
            // Missing scope becomes "all apps" and a missing toggle becomes enabled
            // with opt* defaults. Reject malformed values rather than widen a rule.
            if (!(item.get("package") instanceof String)
                    || !(item.get("phrase") instanceof String)
                    || !(item.get("enabled") instanceof Boolean)
                    || (item.has("literal") && !(item.get("literal") instanceof Boolean))) {
                throw new JSONException("Invalid rule scope, phrase or enabled value");
            }
            result.add(new Rule(item.getLong("id"), item.getString("package"),
                    item.optString("app"), item.getString("phrase"), item.getBoolean("enabled"),
                    item.optBoolean("literal", false)));
        }
        return result;
    }

    public void save(List<Rule> rules) {
        prefs.edit().putString(KEY_RULES, encode(rules)).putInt(KEY_FORMAT, CURRENT_FORMAT).apply();
    }

    private static String encode(List<Rule> rules) {
        JSONArray array = new JSONArray();
        for (Rule rule : rules) {
            JSONObject item = new JSONObject();
            try {
                item.put("id", rule.id);
                item.put("package", rule.packageName);
                item.put("app", rule.appName);
                item.put("phrase", rule.phrase);
                item.put("enabled", rule.enabled);
                item.put("literal", rule.literal);
                array.put(item);
            } catch (JSONException impossible) { throw new IllegalStateException(impossible); }
        }
        return array.toString();
    }
}
