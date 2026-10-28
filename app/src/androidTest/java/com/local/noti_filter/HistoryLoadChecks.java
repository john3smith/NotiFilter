package com.local.noti_filter;

import android.app.Instrumentation;
import android.content.ContentValues;
import android.os.Handler;
import android.os.Looper;
import java.lang.reflect.Field;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/** Isolated clone only. Synthetic row is removed by its exact ID in finally. */
final class HistoryLoadChecks {
    private static void check(boolean value,String message) {if(!value)throw new AssertionError(message);}
    private static void await(CountDownLatch latch) throws Exception {check(latch.await(20,TimeUnit.SECONDS),"Timed out awaiting test checkpoint");}
    private static void drain(ExecutorService io,Instrumentation test) throws Exception {
        io.submit(()->{}).get(20,TimeUnit.SECONDS);test.waitForIdleSync();
    }
    static String run(Instrumentation test) throws Exception {
        HistoryStore store=HistoryStore.get(test.getTargetContext());
        Field field=HistoryStore.class.getDeclaredField("IO");field.setAccessible(true);
        ExecutorService io=(ExecutorService)field.get(null);
        long id=io.submit(()->{
            ContentValues row=new ContentValues();row.put("removed_at",0L);row.put("package_name","test.synthetic");
            row.put("app_name","Synthetic");row.put("content",HistoryCipher.encrypt("test-only-content"));
            return store.getWritableDatabase().insertOrThrow("history",null,row);
        }).get(20,TimeUnit.SECONDS);
        try {
            AtomicInteger generation=new AtomicInteger(),accepted=new AtomicInteger(),skipped=new AtomicInteger(),success=new AtomicInteger(),failure=new AtomicInteger();
            CountDownLatch blocked=new CountDownLatch(1),release=new CountDownLatch(1);
            io.execute(()->{blocked.countDown();try{release.await(20,TimeUnit.SECONDS);}catch(InterruptedException e){Thread.currentThread().interrupt();}});
            try {
                await(blocked);
                for(int i=0;i<100;i++) {
                    int token=generation.incrementAndGet();AtomicBoolean first=new AtomicBoolean(true);
                    store.load(()->{
                        boolean current=token==generation.get();
                        if(first.getAndSet(false)){if(current)accepted.incrementAndGet();else skipped.incrementAndGet();}
                        return current;
                    },rows->success.incrementAndGet(),failure::incrementAndGet);
                }
            } finally {release.countDown();}
            drain(io,test);
            check(accepted.get()==1 && skipped.get()==99 && success.get()==1 && failure.get()==0,"Stale queued loads were not skipped");

            AtomicInteger rowChecks=new AtomicInteger(),callbacks=new AtomicInteger();
            store.load(()->rowChecks.incrementAndGet()==1,rows->callbacks.incrementAndGet(),callbacks::incrementAndGet);
            drain(io,test);
            check(rowChecks.get()==2 && callbacks.get()==0,"Row-boundary cancellation failed");

            // Invalidate after worker completion but before its posted UI delivery.
            AtomicBoolean current=new AtomicBoolean(true);
            CountDownLatch mainBlocked=new CountDownLatch(1),mainRelease=new CountDownLatch(1);
            new Handler(Looper.getMainLooper()).post(()->{mainBlocked.countDown();try{mainRelease.await(5,TimeUnit.SECONDS);}catch(InterruptedException e){Thread.currentThread().interrupt();}});
            try {
                await(mainBlocked);
                store.load(current::get,rows->callbacks.incrementAndGet(),callbacks::incrementAndGet);
                io.submit(()->{}).get(4,TimeUnit.SECONDS);
                current.set(false);
            } finally {mainRelease.countDown();}
            test.waitForIdleSync();check(callbacks.get()==0,"Stale main-thread delivery was not suppressed");

            // Current decryption failure must still reach the failure callback.
            io.submit(()->{ContentValues bad=new ContentValues();bad.put("content","invalid-ciphertext");store.getWritableDatabase().update("history",bad,"id=?",new String[]{Long.toString(id)});}).get(20,TimeUnit.SECONDS);
            store.load(()->true,rows->success.incrementAndGet(),failure::incrementAndGet);
            drain(io,test);check(success.get()==1 && failure.get()==1,"Current failure was swallowed");
            return "PASS 4 history load checks: 100 queued requests -> 99 skipped before DB access, 1 current result; row cancellation; stale UI delivery; current failure delivered\n";
        } finally {
            io.submit(()->store.getWritableDatabase().delete("history","id=?",new String[]{Long.toString(id)})).get(20,TimeUnit.SECONDS);
        }
    }
}
