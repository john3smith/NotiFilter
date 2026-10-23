package com.local.noti_filter;
import org.junit.Test;
import static org.junit.Assert.*;
import java.util.concurrent.atomic.AtomicInteger;

public class AppCatalogTest {
    @Test public void unchangedPackageReusesLabelWithoutCallingLoader(){
        AppCatalog.App previous=new AppCatalog.App("com.example.app","저장된 앱 이름","123:Activity");
        assertEquals("저장된 앱 이름",AppCatalog.labelFor(previous,"123:Activity",()->{throw new AssertionError("Label reloaded");}));
    }
    @Test public void updateOrLauncherChangeRefreshesLabel(){
        AtomicInteger calls=new AtomicInteger();AppCatalog.App previous=new AppCatalog.App("com.example.app","old","123:Activity");
        assertEquals("updated",AppCatalog.labelFor(previous,"124:Activity",()->{calls.incrementAndGet();return "updated";}));
        assertEquals("renamed",AppCatalog.labelFor(previous,"123:OtherActivity",()->{calls.incrementAndGet();return "renamed";}));
        assertEquals(2,calls.get());
    }
    @Test public void missingOrLocaleInvalidatedCacheLoadsNewLabel(){
        assertEquals("새 이름",AppCatalog.labelFor(null,"1",()->"새 이름"));
    }
}
