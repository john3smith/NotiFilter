package com.local.noti_filter;

import android.app.AlertDialog;
import android.app.Instrumentation;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Switch;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

final class RuleSortChecks {
    static void run(Instrumentation test,MainActivity initial) throws Exception {
        Context c=test.getTargetContext();
        if(!c.getPackageName().equals("com.local.noti_filter.uitest"))throw new AssertionError("Isolated clone only");
        SharedPreferences prefs=c.getSharedPreferences("filter_rules",Context.MODE_PRIVATE);
        String before=prefs.getString("rules","[]"),beforeSort=prefs.getString("sort_order",null);
        MainActivity[] screen={initial};RuleStore store=new RuleStore(c);
        Method render=MainActivity.class.getDeclaredMethod("render");render.setAccessible(true);
        Runnable redraw=()->{try{render.invoke(screen[0]);}catch(Exception e){throw new RuntimeException(e);}};
        List<Rule> fixtures=Arrays.asList(new Rule(200,"fixture.b","나비","가 문구",true),
                new Rule(100,"fixture.a","가나다","나 문구",false),
                new Rule(400,"","","가 문구",false),new Rule(300,"fixture.a","가나다","가 문구",false));
        try {
            prefs.edit().remove("sort_order").commit();store.save(fixtures);
            String raw=prefs.getString("rules","[]");
            RuleUiInstrumentation.ui(test,redraw);
            assertOrder(screen[0],400,300,200,100);assertSelected(screen[0],R.id.sort_newest);
            report(test,"default newest uses creation IDs and preserves disabled rules");

            click(test,screen[0],R.id.sort_oldest);assertOrder(screen[0],100,200,300,400);
            if(!raw.equals(prefs.getString("rules","[]")))throw new AssertionError("Sort rewrote rules");
            report(test,"oldest changes display only and leaves exact stored rule JSON intact");

            click(test,screen[0],R.id.sort_name);assertOrder(screen[0],300,100,200,400);
            assertSelected(screen[0],R.id.sort_name);
            if(!raw.equals(prefs.getString("rules","[]")))throw new AssertionError("Name sort rewrote rules");
            report(test,"name sorts app name then phrase, including all-app fallback");

            Instrumentation.ActivityMonitor monitor=test.addMonitor(MainActivity.class.getName(),null,false);
            RuleUiInstrumentation.ui(test,()->screen[0].recreate());
            MainActivity recreated=(MainActivity)test.waitForMonitorWithTimeout(monitor,20000);
            test.removeMonitor(monitor);if(recreated==null)throw new AssertionError("No recreated screen");
            screen[0]=recreated;test.waitForIdleSync();
            if(new RuleStore(c).sortOrder()!=RuleOrder.Mode.NAME)throw new AssertionError("Sort not persisted");
            assertOrder(screen[0],300,100,200,400);assertSelected(screen[0],R.id.sort_name);
            report(test,"selected order survives Activity recreation and a new store instance");

            List<Rule> added=store.load();added.add(new Rule(500,"fixture.a","가나다","마 문구",false));store.save(added);
            RuleUiInstrumentation.ui(test,redraw);assertOrder(screen[0],300,100,500,200,400);
            click(test,screen[0],R.id.sort_newest);assertOrder(screen[0],500,400,300,200,100);
            click(test,screen[0],R.id.sort_name);
            report(test,"new rules obey selected order and newest includes the new rule first");

            RuleUiInstrumentation.ui(test,()->card(screen[0],300).performClick());
            // AlertDialog delivers OnShow through the UI loop. Wait for its
            // custom Save handler before interacting with the visible form.
            test.waitForIdleSync();
            RuleUiInstrumentation.ui(test,()->{
                try {
                    AlertDialog dialog=(AlertDialog)RuleUiInstrumentation.field(screen[0],"addRuleDialog");
                    ((EditText)dialog.findViewById(R.id.rule_phrase)).setText("하 문구");
                    dialog.getButton(AlertDialog.BUTTON_POSITIVE).performClick();
                }catch(Exception e){throw new RuntimeException(e);}
            });test.waitForIdleSync();assertOrder(screen[0],100,500,300,200,400);
            Rule edited=null;for(Rule rule:store.load())if(rule.id==300)edited=rule;
            if(edited==null || edited.enabled || !edited.phrase.equals("하 문구"))throw new AssertionError("Sorted edit changed identity/enabled state");
            report(test,"editing a sorted card targets its ID, preserves creation and refreshes phrase order");

            RuleUiInstrumentation.ui(test,()->{
                LinearLayout row=(LinearLayout)card(screen[0],200).getChildAt(0);
                ((Switch)row.getChildAt(1)).performClick();
            });test.waitForIdleSync();
            for(Rule rule:store.load())if(rule.id==200 && rule.enabled)throw new AssertionError("Wrong toggle target");
            assertOrder(screen[0],100,500,300,200,400);
            report(test,"toggle in name order updates the correct rule without changing display order");

            store.save(new ArrayList<>());RuleUiInstrumentation.ui(test,redraw);
            click(test,screen[0],R.id.sort_oldest);assertOrder(screen[0]);assertSelected(screen[0],R.id.sort_oldest);
            report(test,"empty list still permits persistent sorting without creating a rule");
        } finally {
            SharedPreferences.Editor restore=prefs.edit().putString("rules",before);
            if(beforeSort==null)restore.remove("sort_order");else restore.putString("sort_order",beforeSort);
            if(!restore.commit())throw new AssertionError("Failed to restore clone preferences");
            RuleUiInstrumentation.ui(test,()->screen[0].finish());
            if(!before.equals(prefs.getString("rules","[]")))throw new AssertionError("Rule restoration mismatch");
        }
    }
    private static LinearLayout root(MainActivity screen) {
        try{return (LinearLayout)RuleUiInstrumentation.field(screen,"root");}
        catch(Exception e){throw new RuntimeException(e);}
    }
    private static LinearLayout card(MainActivity screen,long id) {
        LinearLayout root=root(screen);
        for(int i=0;i<root.getChildCount();i++) {
            View child=root.getChildAt(i);Object tag=child.getTag(R.id.rule_card_id);
            if(tag instanceof Long && (Long)tag==id)return (LinearLayout)child;
        }
        throw new AssertionError("Missing rule card "+id);
    }
    private static void assertOrder(MainActivity screen,long...expected) {
        List<Long> actual=new ArrayList<>();LinearLayout root=root(screen);
        for(int i=0;i<root.getChildCount();i++){Object tag=root.getChildAt(i).getTag(R.id.rule_card_id);if(tag instanceof Long)actual.add((Long)tag);}
        List<Long> wanted=new ArrayList<>();for(long id:expected)wanted.add(id);
        if(!actual.equals(wanted))throw new AssertionError("Order "+actual+" expected "+wanted);
    }
    private static void assertSelected(MainActivity screen,int selected) {
        for(int id:new int[]{R.id.sort_newest,R.id.sort_oldest,R.id.sort_name})
            if(screen.findViewById(id).isSelected()!=(id==selected))throw new AssertionError("Selection marker");
    }
    private static void click(Instrumentation test,MainActivity screen,int id) {
        RuleUiInstrumentation.ui(test,()->((Button)screen.findViewById(id)).performClick());test.waitForIdleSync();
    }
    private static void report(Instrumentation test,String text) {Bundle b=new Bundle();b.putString("stream",text+" OK\n");test.sendStatus(0,b);}
}
