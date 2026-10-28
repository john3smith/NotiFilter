package com.local.noti_filter;

import org.junit.Test;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.Assert.*;

public class PendingRemovalsTest {
    private PendingRemovals<Long> create(int capacity) {
        return new PendingRemovals<>(capacity,30000,value -> value);
    }
    private boolean offer(PendingRemovals<Long> pending, String key, long value, long now) {
        return pending.offer(key,()->value,now);
    }
    @Test public void fullCapacityDoesNotExtractNotificationContent() {
        PendingRemovals<Long> pending=create(1);
        pending.offer("a",()->0L,0);
        assertFalse(pending.offer("b",()->{throw new AssertionError("Content extracted at capacity");},1));
    }
    @Test public void exactExpiryBoundaryIsPreserved() {
        PendingRemovals<Long> pending=create(1);
        assertTrue(offer(pending,"a",0L,0));
        assertFalse(offer(pending,"b",30000L,30000));
        assertTrue(offer(pending,"b",30001L,30001));
        assertNull(pending.remove("a"));
        assertEquals(Long.valueOf(30001),pending.remove("b"));
    }
    @Test public void refreshingExistingKeyMovesItBehindOlderRequestsAtCapacity() {
        PendingRemovals<Long> pending=create(2);
        assertTrue(offer(pending,"a",0L,0));
        assertTrue(offer(pending,"b",1L,1));
        assertTrue(offer(pending,"a",20000L,20000));
        assertTrue(offer(pending,"c",30002L,30002));
        assertNull(pending.remove("b"));
        assertEquals(Long.valueOf(20000),pending.remove("a"));
        assertEquals(Long.valueOf(30002),pending.remove("c"));
    }
    @Test public void removalAndDisconnectClearReleaseCapacity() {
        PendingRemovals<Long> pending=create(1);
        offer(pending,"a",0L,0);
        assertEquals(Long.valueOf(0),pending.remove("a"));
        assertNull(pending.remove("a"));
        assertTrue(offer(pending,"b",1L,1));
        pending.clear();
        assertNull(pending.remove("b"));
        assertTrue(offer(pending,"c",2L,2));
    }
    @Test public void aLargeUnexpiredSetInspectsOnlyItsOldestRequest() {
        AtomicInteger inspections=new AtomicInteger();
        PendingRemovals<Long> pending=new PendingRemovals<>(1000,30000,value->{inspections.incrementAndGet();return value;});
        for(int i=0;i<1000;i++) assertTrue(offer(pending,"k"+i,(long)i,i));
        Map<String,Long> previous=new HashMap<>();
        for(int i=0;i<1000;i++)previous.put("k"+i,(long)i);
        AtomicInteger previousInspections=new AtomicInteger();
        previous.entrySet().removeIf(e->{previousInspections.incrementAndGet();return 1000-e.getValue()>30000;});
        assertEquals(1000,previousInspections.get());
        inspections.set(0);
        assertFalse(offer(pending,"full",1000L,1000));
        assertEquals(1,inspections.get());
        inspections.set(0);
        assertTrue(offer(pending,"expired",40000L,40000));
        assertEquals(1000,inspections.get());
    }
    @Test public void randomizedOperationsMatchThePreviousFullScanBehavior() {
        PendingRemovals<Long> pending=create(20);
        Map<String,Long> previous=new HashMap<>();
        Random random=new Random(20261029);long now=0;
        for(int i=0;i<20000;i++) {
            now+=random.nextInt(3000);String key="k"+random.nextInt(30);
            int operation=random.nextInt(10);
            if(operation==0){pending.clear();previous.clear();}
            else if(operation<4){assertEquals(previous.remove(key),pending.remove(key));}
            else {
                final long current=now;
                previous.entrySet().removeIf(e->current-e.getValue()>30000);
                boolean accepted=previous.size()<20 || previous.containsKey(key);
                if(accepted)previous.put(key,now);
                assertEquals(accepted,offer(pending,key,now,now));
            }
        }
        for(int i=0;i<30;i++)assertEquals(previous.remove("k"+i),pending.remove("k"+i));
    }
}
