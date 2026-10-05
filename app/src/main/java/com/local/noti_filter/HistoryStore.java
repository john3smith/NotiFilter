package com.local.noti_filter;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.drawable.Drawable;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

/** Private, bounded history. All database and icon decoding work is off the UI thread. */
public final class HistoryStore extends SQLiteOpenHelper {
    private static HistoryStore instance;
    private static final ExecutorService IO = Executors.newSingleThreadExecutor();
    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    public static synchronized HistoryStore get(Context context) {
        if (instance == null) instance = new HistoryStore(context.getApplicationContext());
        return instance;
    }
    private HistoryStore(Context context) { super(context, "notification_history.db", null, 1); }
    @Override public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE history (id INTEGER PRIMARY KEY AUTOINCREMENT, removed_at INTEGER NOT NULL, package_name TEXT NOT NULL, app_name TEXT NOT NULL, content TEXT NOT NULL, icon BLOB)");
    }
    @Override public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {}

    public void record(long removedAt, String pkg, String app, String content, Drawable icon) {
        IO.execute(() -> {
            try {
                byte[] bytes = null;
                if (icon != null) {
                    Bitmap bitmap = Bitmap.createBitmap(64, 64, Bitmap.Config.ARGB_8888);
                    try (ByteArrayOutputStream output = new ByteArrayOutputStream()) {
                        icon.setBounds(0, 0, 64, 64);
                        icon.draw(new Canvas(bitmap));
                        bitmap.compress(Bitmap.CompressFormat.PNG, 100, output);
                        bytes = output.toByteArray();
                    } catch (Exception iconError) {
                        // A broken/removed app icon must not discard the notification history.
                        bytes = null;
                    } finally { bitmap.recycle(); }
                }
                ContentValues values = new ContentValues();
                values.put("removed_at", removedAt); values.put("package_name", pkg);
                values.put("app_name", app); values.put("content", content); values.put("icon", bytes);
                SQLiteDatabase db = getWritableDatabase();
                db.beginTransaction();
                try {
                    db.insertOrThrow("history", null, values);
                    db.execSQL("DELETE FROM history WHERE id NOT IN (SELECT id FROM history ORDER BY id DESC LIMIT 1000)");
                    db.setTransactionSuccessful();
                } finally { db.endTransaction(); }
            } catch (Exception error) { Log.w("NotiFilter", "History persistence failed"); }
        });
    }

    public void load(Consumer<List<Entry>> success, Runnable failure) {
        IO.execute(() -> {
            List<Entry> entries = new ArrayList<>();
            try (Cursor cursor = getReadableDatabase().query("history", null, null, null, null, null, "id DESC", "1000")) {
                while (cursor.moveToNext()) {
                    byte[] icon = cursor.getBlob(cursor.getColumnIndexOrThrow("icon"));
                    entries.add(new Entry(cursor.getLong(cursor.getColumnIndexOrThrow("removed_at")),
                            cursor.getString(cursor.getColumnIndexOrThrow("package_name")),
                            cursor.getString(cursor.getColumnIndexOrThrow("app_name")),
                            cursor.getString(cursor.getColumnIndexOrThrow("content")),
                            icon == null ? null : BitmapFactory.decodeByteArray(icon, 0, icon.length)));
                }
                MAIN.post(() -> success.accept(entries));
            } catch (RuntimeException error) { MAIN.post(failure); }
        });
    }
    public static final class Entry {
        public final long time;
        public final String pkg, app, content;
        public final Bitmap icon;
        Entry(long time, String pkg, String app, String content, Bitmap icon) {
            this.time = time; this.pkg = pkg; this.app = app; this.content = content; this.icon = icon;
        }
    }
}
