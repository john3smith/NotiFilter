package com.local.noti_filter;

import android.app.AlertDialog;
import android.app.Instrumentation;
import android.content.Intent;
import android.os.Bundle;
import android.os.SystemClock;
import android.view.ViewTreeObserver;
import android.widget.Button;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

public final class RuleUiInstrumentation extends Instrumentation {
    private boolean catalogChecks;
    @Override public void onCreate(Bundle args) { catalogChecks=args!=null && "true".equals(args.getString("catalogChecks"));super.onCreate(args);start(); }
    static Object field(Object owner,String name) throws Exception {
        Field f=owner.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(owner);
    }
    static void ui(Instrumentation test,Runnable action) {
        Throwable[] failure={null};test.runOnMainSync(()->{try{action.run();}catch(Throwable e){failure[0]=e;}});
        if(failure[0]!=null)throw new RuntimeException(failure[0]);
    }
    @Override public void onStart() {
        Bundle result=new Bundle();MainActivity screen=null;
        try {
            if(!getTargetContext().getPackageName().equals("com.local.noti_filter.uitest"))throw new IllegalStateException("Isolated clone only");
            screen=(MainActivity)startActivitySync(new Intent(getTargetContext(),MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            MainActivity activity=screen;waitForIdleSync();ArrayList<Long> times=new ArrayList<>();
            if(catalogChecks){CatalogDialogChecks.run(this,activity);result.putString("stream","PASS async dialog/cache/edit/picker checks; clone rules restored\n");finish(android.app.Activity.RESULT_OK,result);return;}
            for(int i=0;i<4;i++) {
                CountDownLatch drawn=new CountDownLatch(1);long[] started={0},elapsed={0};
                ui(this,()->{
                    try {
                        started[0]=SystemClock.elapsedRealtimeNanos();
                        ((Button)field(activity,"addRuleButton")).performClick();
                        AlertDialog d=(AlertDialog)field(activity,"addRuleDialog");
                        d.getWindow().getDecorView().getViewTreeObserver().addOnPreDrawListener(new ViewTreeObserver.OnPreDrawListener(){
                            @Override public boolean onPreDraw(){d.getWindow().getDecorView().getViewTreeObserver().removeOnPreDrawListener(this);
                                elapsed[0]=(SystemClock.elapsedRealtimeNanos()-started[0])/1000000;drawn.countDown();return true;}
                        });
                    } catch(Exception e){throw new RuntimeException(e);}
                });
                if(!drawn.await(20,TimeUnit.SECONDS))throw new IllegalStateException("Dialog did not draw");
                times.add(elapsed[0]);
                ui(this,()->{try{((AlertDialog)field(activity,"addRuleDialog")).getButton(AlertDialog.BUTTON_NEGATIVE).performClick();}catch(Exception e){throw new RuntimeException(e);}});
                waitForIdleSync();
            }
            result.putString("stream","PASS dialog click-to-first-draw ms="+times+" version="+getTargetContext().getPackageManager().getPackageInfo(getTargetContext().getPackageName(),0).versionName+"\n");
            finish(android.app.Activity.RESULT_OK,result);
        } catch(Throwable e){result.putString("stream","FAIL "+e+"\n");finish(android.app.Activity.RESULT_CANCELED,result);}
        finally {if(screen!=null){MainActivity done=screen;ui(this,done::finish);}}
    }
}
