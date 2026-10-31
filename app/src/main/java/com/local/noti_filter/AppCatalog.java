package com.local.noti_filter;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.drawable.Drawable;
import android.os.Handler;
import android.os.Looper;
import android.util.LruCache;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;
import java.util.function.Supplier;

/** App-private label cache. No package discovery, label or icon I/O runs on the UI thread. */
final class AppCatalog {
    static final class App {
        final String packageName,label,stamp;
        App(String pkg,String label,String stamp){this.packageName=pkg;this.label=label;this.stamp=stamp;}
    }
    interface Listener { void changed(List<App> apps,boolean loading,boolean failed); }
    private static AppCatalog instance;
    static synchronized AppCatalog get(Context c){if(instance==null)instance=new AppCatalog(c.getApplicationContext());return instance;}
    private final Context context;
    private final Handler main=new Handler(Looper.getMainLooper());
    final ExecutorService worker=Executors.newSingleThreadExecutor();
    private final ExecutorService icons=Executors.newSingleThreadExecutor();
    private final LruCache<String,Drawable> iconCache=new LruCache<>(48);
    // Guard generation, cache publication and callbacks with the cache monitor.
    private long iconGeneration;
    private final CopyOnWriteArrayList<Listener> listeners=new CopyOnWriteArrayList<>();
    private List<App> apps=Collections.emptyList();
    private boolean loading,failed,diskRead,refreshPending;
    private String language="";
    private Map<String,App> names=new HashMap<>();
    private AppCatalog(Context c){context=c.getApplicationContext();}
    static String labelFor(App previous,String stamp,Supplier<String> load){
        return previous!=null && previous.stamp.equals(stamp)?previous.label:load.get();
    }
    void subscribe(Listener listener){listeners.add(listener);notifyListeners();refresh();}
    void unsubscribe(Listener listener){listeners.remove(listener);}
    private void notifyListeners(){main.post(()->{
        List<App> current;boolean busy,error;
        synchronized(this){current=apps;busy=loading;error=failed;}
        for(Listener l:listeners)l.changed(current,busy,error);
    });}
    synchronized List<App> snapshot(){return apps;}
    synchronized void refresh(){
        if(loading){refreshPending=true;return;}
        loading=true;failed=false;notifyListeners();worker.execute(this::load);
    }
    private void load(){
        try {
            String locale=context.getResources().getConfiguration().getLocales().toLanguageTags();
            if(!diskRead){
                diskRead=true;
                try {
                    JSONObject saved=new JSONObject(context.getSharedPreferences("app_names_v1",Context.MODE_PRIVATE).getString("catalog","{}"));
                    if(saved.optInt("version")==1 && locale.equals(saved.optString("locale"))){
                        JSONArray rows=saved.getJSONArray("apps");List<App> cached=new ArrayList<>();
                        for(int i=0;i<rows.length() && i<5000;i++){
                            JSONObject row=rows.getJSONObject(i);App a=new App(row.getString("package"),row.getString("label"),row.getString("stamp"));
                            if(!a.packageName.isEmpty() && !a.packageName.equals(context.getPackageName())){cached.add(a);names.put(a.packageName,a);}
                        }
                        language=locale;
                        synchronized(this){apps=Collections.unmodifiableList(cached);}
                        notifyListeners();
                    }
                }catch(Exception ignored){names.clear();}
            }
            if(!locale.equals(language)){names.clear();invalidateIcons();}
            PackageManager pm=context.getPackageManager();
            List<ResolveInfo> resolved=pm.queryIntentActivities(new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER),0);
            List<App> fresh=new ArrayList<>();Map<String,App> next=new HashMap<>();HashSet<String> seen=new HashSet<>();
            boolean changed=false;
            for(ResolveInfo info:resolved){
                String pkg=info.activityInfo.packageName;
                if(pkg.equals(context.getPackageName()) || !seen.add(pkg))continue;
                try {
                    String stamp=pm.getPackageInfo(pkg,0).lastUpdateTime+":"+info.activityInfo.name+":"+info.activityInfo.labelRes+":"+String.valueOf(info.activityInfo.nonLocalizedLabel);
                    App previous=names.get(pkg);
                    String label=labelFor(previous,stamp,()->info.loadLabel(pm).toString());
                    App a=new App(pkg,label,stamp);fresh.add(a);next.put(pkg,a);
                    if(previous==null || !previous.stamp.equals(stamp))changed=true;
                }catch(PackageManager.NameNotFoundException removed){/* Package vanished during this scan. */}
            }
            fresh.sort(Comparator.comparing((App a)->a.label).thenComparing(a->a.packageName));
            if(changed || next.size()!=names.size())invalidateIcons();
            names=next;language=locale;
            // A locale change during discovery must not publish mixed-language cached labels.
            if(!locale.equals(context.getResources().getConfiguration().getLocales().toLanguageTags())){
                synchronized(this){refreshPending=true;}return;
            }
            JSONArray rows=new JSONArray();
            for(App a:fresh)rows.put(new JSONObject().put("package",a.packageName).put("label",a.label).put("stamp",a.stamp));
            context.getSharedPreferences("app_names_v1",Context.MODE_PRIVATE).edit().putString("catalog",
                new JSONObject().put("version",1).put("locale",locale).put("apps",rows).toString()).apply();
            synchronized(this){apps=Collections.unmodifiableList(fresh);failed=false;}
        }catch(Exception unavailable){synchronized(this){failed=true;}}
        finally{
            synchronized(this){
                // Coalesce requests received during discovery into one follow-up scan.
                if(refreshPending){refreshPending=false;failed=false;worker.execute(this::load);}
                else loading=false;
            }
            notifyListeners();
        }
    }
    Drawable cachedIcon(String pkg){return iconCache.get(pkg);}
    private void invalidateIcons(){
        synchronized(iconCache){iconGeneration++;iconCache.evictAll();}
    }
    void icon(String pkg,Consumer<Drawable> ready){
        final long generation;
        synchronized(iconCache){
            Drawable cached=iconCache.get(pkg);
            if(cached!=null){ready.accept(cached);return;}
            generation=iconGeneration;
        }
        icons.execute(()->{
            Drawable value;
            try{value=context.getPackageManager().getApplicationIcon(pkg);}catch(Exception missing){return;}
            synchronized(iconCache){
                if(generation!=iconGeneration)return;
                iconCache.put(pkg,value);
            }
            main.post(()->{
                synchronized(iconCache){
                    if(generation!=iconGeneration)return;
                    ready.accept(value);
                }
            });
        });
    }
}
