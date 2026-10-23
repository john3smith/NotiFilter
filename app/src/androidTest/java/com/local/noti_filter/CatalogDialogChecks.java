package com.local.noti_filter;

import android.app.AlertDialog;
import android.app.Instrumentation;
import android.content.Context;
import android.os.Bundle;
import android.os.SystemClock;
import android.widget.Button;
import android.widget.EditText;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

final class CatalogDialogChecks {
    static void run(Instrumentation test,MainActivity activity) throws Exception {
        Context c=test.getTargetContext();RuleStore store=new RuleStore(c);List<Rule> before=store.load();
        // Retire only exact synthetic residue from interrupted older versions of this check.
        if(before.removeIf(r->r.id==70707070 && r.packageName.equals("com.example.notinstalled") && r.appName.equals("SYNTHETIC REMOVED APP") && !r.enabled && (r.phrase.equals("OLD SYNTHETIC") || r.phrase.equals("UPDATED SYNTHETIC")))){
            store.save(before);c.getSharedPreferences("filter_rules",Context.MODE_PRIVATE).edit().putString("rules",c.getSharedPreferences("filter_rules",Context.MODE_PRIVATE).getString("rules","[]")).commit();
        }
        String raw=c.getSharedPreferences("filter_rules",Context.MODE_PRIVATE).getString("rules","[]");
        AppCatalog catalog=AppCatalog.get(c);CountDownLatch entered=new CountDownLatch(1),release=new CountDownLatch(1);
        // Wait for initial package discovery, then emulate a blocked package service on its worker.
        catalog.worker.submit(()->{}).get(30,TimeUnit.SECONDS);
        if(catalog.snapshot().isEmpty())throw new AssertionError("Expected launcher apps");
        catalog.worker.execute(()->{entered.countDown();try{release.await(20,TimeUnit.SECONDS);}catch(InterruptedException e){Thread.currentThread().interrupt();}});
        if(!entered.await(5,TimeUnit.SECONDS))throw new AssertionError("No worker barrier");
        try {
            List<AppCatalog.App> warm=catalog.snapshot();
            java.lang.reflect.Field memory=AppCatalog.class.getDeclaredField("apps");memory.setAccessible(true);
            synchronized(catalog){memory.set(catalog,java.util.Collections.emptyList());}
            long coldStart=SystemClock.uptimeMillis();
            RuleUiInstrumentation.ui(test,()->{try{((Button)RuleUiInstrumentation.field(activity,"addRuleButton")).performClick();}catch(Exception e){throw new RuntimeException(e);}});
            if(SystemClock.uptimeMillis()-coldStart>1000)throw new AssertionError("Cold dialog waited for apps");
            AlertDialog cold=(AlertDialog)RuleUiInstrumentation.field(activity,"addRuleDialog");
            if(cold==null || !cold.isShowing())throw new AssertionError("Cold dialog missing");
            RuleUiInstrumentation.ui(test,()->cold.getButton(AlertDialog.BUTTON_NEGATIVE).performClick());test.waitForIdleSync();
            synchronized(catalog){memory.set(catalog,warm);}
            test.sendStatus(0,bundle("empty memory cache and blocked discovery still open the form immediately"));
            long start=SystemClock.uptimeMillis();
            RuleUiInstrumentation.ui(test,()->{try{Button add=(Button)RuleUiInstrumentation.field(activity,"addRuleButton");for(int i=0;i<8;i++)add.performClick();}catch(Exception e){throw new RuntimeException(e);}});
            if(SystemClock.uptimeMillis()-start>1000)throw new AssertionError("Dialog waited for package worker");
            AlertDialog dialog=(AlertDialog)RuleUiInstrumentation.field(activity,"addRuleDialog");
            if(dialog==null || !dialog.isShowing())throw new AssertionError("No immediate dialog");
            RuleUiInstrumentation.ui(test,()->{
                ((EditText)dialog.findViewById(R.id.rule_phrase)).setText("UNSAVED SYNTHETIC PHRASE");
                Button choose=dialog.findViewById(R.id.rule_choose_app);choose.performClick();choose.performClick();
            });
            AlertDialog picker=(AlertDialog)RuleUiInstrumentation.field(activity,"appPickerDialog");
            if(picker==null || !picker.isShowing())throw new AssertionError("No cached picker while scan blocked");
            RuleUiInstrumentation.ui(test,()->((EditText)picker.findViewById(R.id.app_search)).setText("com."));
            test.sendStatus(0,bundle("blocked package scan: one immediate editor and searchable cached app picker"));
            release.countDown();catalog.worker.submit(()->{}).get(30,TimeUnit.SECONDS);test.waitForIdleSync();
            RuleUiInstrumentation.ui(test,()->{
                if(!"UNSAVED SYNTHETIC PHRASE".equals(((EditText)dialog.findViewById(R.id.rule_phrase)).getText().toString()))throw new AssertionError("Refresh lost phrase");
                if(!"com.".equals(((EditText)picker.findViewById(R.id.app_search)).getText().toString()))throw new AssertionError("Refresh lost search");
                picker.dismiss();dialog.getButton(AlertDialog.BUTTON_NEGATIVE).performClick();
            });test.waitForIdleSync();
            if(!raw.equals(c.getSharedPreferences("filter_rules",Context.MODE_PRIVATE).getString("rules","[]")))throw new AssertionError("Cancel changed rules");
            List<Rule> seeded=new ArrayList<>(before);Rule fixture=new Rule(System.nanoTime(),"com.example.notinstalled","SYNTHETIC REMOVED APP","OLD SYNTHETIC",false);seeded.add(fixture);store.save(seeded);
            Method show=MainActivity.class.getDeclaredMethod("showRuleDialog",Rule.class);show.setAccessible(true);
            RuleUiInstrumentation.ui(test,()->{try{show.invoke(activity,fixture);}catch(Exception e){throw new RuntimeException(e);}});
            AlertDialog edit=(AlertDialog)RuleUiInstrumentation.field(activity,"addRuleDialog");
            RuleUiInstrumentation.ui(test,()->{
                if(!((Button)edit.findViewById(R.id.rule_choose_app)).getText().toString().contains(fixture.appName))throw new AssertionError("Removed app scope lost");
                ((EditText)edit.findViewById(R.id.rule_phrase)).setText("UPDATED SYNTHETIC");
                edit.getButton(AlertDialog.BUTTON_POSITIVE).performClick();edit.getButton(AlertDialog.BUTTON_POSITIVE).performClick();
            });test.waitForIdleSync();
            List<Rule> saved=store.load();Rule found=null;int count=0;
            for(Rule r:saved)if(r.id==fixture.id){found=r;count++;}
            if(count!=1 || found.enabled || !found.packageName.equals(fixture.packageName) || !found.phrase.equals("UPDATED SYNTHETIC"))throw new AssertionError("Scope/toggle/save: count="+count+" enabled="+(found==null?"missing":found.enabled)+" package="+(found==null?"missing":found.packageName)+" phrase="+(found==null?"missing":found.phrase));
            String cache=c.getSharedPreferences("app_names_v1",Context.MODE_PRIVATE).getString("catalog","");
            org.json.JSONObject data=new org.json.JSONObject(cache);
            if(data.getInt("version")!=1 || data.getJSONArray("apps").length()!=catalog.snapshot().size())throw new AssertionError("Name cache missing");
            java.util.HashSet<String> packages=new java.util.HashSet<>();
            for(AppCatalog.App app:catalog.snapshot())if(!packages.add(app.packageName))throw new AssertionError("Duplicate package");
            test.sendStatus(0,bundle("refresh preserves input/search; removed-app scope, disabled edit, duplicate save, disk cache and package deduplication"));
        } finally {
            release.countDown();catalog.worker.submit(()->{}).get(30,TimeUnit.SECONDS);
            RuleUiInstrumentation.ui(test,()->{try{AlertDialog d=(AlertDialog)RuleUiInstrumentation.field(activity,"addRuleDialog");if(d!=null)d.dismiss();}catch(Exception e){throw new RuntimeException(e);}});
            store.save(before);
            c.getSharedPreferences("filter_rules",Context.MODE_PRIVATE).edit().putString("rules",raw).commit();
            if(!raw.equals(c.getSharedPreferences("filter_rules",Context.MODE_PRIVATE).getString("rules","[]")))throw new AssertionError("Fixture restoration mismatch");
        }
    }
    private static Bundle bundle(String value){Bundle b=new Bundle();b.putString("stream",value+" OK\n");return b;}
}
