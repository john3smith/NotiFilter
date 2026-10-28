package com.local.noti_filter;

import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteStatement;
import android.os.SystemClock;
import java.io.File;
import java.util.Arrays;
import java.util.Locale;

/** Synthetic database only: does not open or change the application's history. */
final class HistoryRetentionChecks {
    private static final String OLD = "DELETE FROM history WHERE id NOT IN (SELECT id FROM history ORDER BY id DESC LIMIT 1000)";
    private static final String NEW = HistoryStore.RETAIN_LATEST_SQL;
    private static SQLiteDatabase open(File file) {
        SQLiteDatabase db = SQLiteDatabase.openOrCreateDatabase(file, null);
        try(Cursor c=db.rawQuery("PRAGMA secure_delete=ON",null)) {
            check(c.moveToFirst() && c.getInt(0)==1,"Secure deletion unavailable");
        }
        db.execSQL("CREATE TABLE history (id INTEGER PRIMARY KEY AUTOINCREMENT, content TEXT NOT NULL, icon BLOB)");
        return db;
    }
    private static void fill(SQLiteDatabase db, int count) {
        db.beginTransaction();
        try (SQLiteStatement insert=db.compileStatement("INSERT INTO history(content,icon) VALUES ('synthetic',zeroblob(256))")) {
            for(int i=0;i<count;i++) insert.executeInsert();
            db.setTransactionSuccessful();
        } finally { db.endTransaction(); }
    }
    private static String ids(SQLiteDatabase db) {
        StringBuilder result=new StringBuilder();
        try(Cursor c=db.rawQuery("SELECT id FROM history ORDER BY id",null)) {
            while(c.moveToNext()) result.append(c.getLong(0)).append(',');
        }
        return result.toString();
    }
    private static long count(SQLiteDatabase db) {
        try(Cursor c=db.rawQuery("SELECT COUNT(*) FROM history",null)){c.moveToFirst();return c.getLong(0);}
    }
    private static void check(boolean ok,String message) { if(!ok)throw new AssertionError(message); }
    static String run(Context context) throws Exception {
        File a=File.createTempFile("retention-before-",".db",context.getCacheDir());
        File b=File.createTempFile("retention-after-",".db",context.getCacheDir());
        StringBuilder report=new StringBuilder();
        try(SQLiteDatabase oldDb=open(a);SQLiteDatabase newDb=open(b)) {
            for(int rows:new int[]{0,1,999,1000,1001,1300}) {
                oldDb.execSQL("DELETE FROM history");newDb.execSQL("DELETE FROM history");
                fill(oldDb,rows);fill(newDb,rows);
                oldDb.execSQL(OLD);newDb.execSQL(NEW);
                check(ids(oldDb).equals(ids(newDb)),"Different retained IDs at "+rows);
                check(count(newDb)==Math.min(1000,rows),"Wrong retained count");
            }
            // Gaps in IDs and a larger backlog must still retain the newest 1000 rows.
            fill(oldDb,1600);fill(newDb,1600);
            oldDb.execSQL("DELETE FROM history WHERE id%3=0");newDb.execSQL("DELETE FROM history WHERE id%3=0");
            oldDb.execSQL(OLD);newDb.execSQL(NEW);
            check(ids(oldDb).equals(ids(newDb)) && count(newDb)==1000,"ID gap regression");
            String before=ids(newDb);
            newDb.beginTransaction();
            try {newDb.execSQL("INSERT INTO history(content) VALUES ('rollback')");newDb.execSQL(NEW);}
            finally {newDb.endTransaction();}
            check(before.equals(ids(newDb)),"Rollback lost existing records");
            newDb.execSQL(NEW);check(before.equals(ids(newDb)),"Pruning is not idempotent");
            report.append("PASS 9 retention cases: boundaries, gapped IDs, rollback and idempotence\n");
            try(Cursor c=newDb.rawQuery("EXPLAIN QUERY PLAN "+NEW,null)) {
                while(c.moveToNext()) report.append("new plan: ").append(c.getString(3)).append('\n');
            }
            double[] oldTimes=new double[7],newTimes=new double[7];
            for(int s=-1;s<7;s++) {
                // Alternate order to reduce systematic warmup/order bias.
                double first,second;
                if(s%2==0){first=measure(oldDb,OLD);second=measure(newDb,NEW);}
                else {second=measure(newDb,NEW);first=measure(oldDb,OLD);}
                if(s>=0){oldTimes[s]=first;newTimes[s]=second;}
            }
            Arrays.sort(oldTimes);Arrays.sort(newTimes);
            report.append(String.format(Locale.ROOT,"Android SQLite transaction median us: before=%.3f after=%.3f; 7 samples x 200 insert+prune transactions; secure_delete ON\n",oldTimes[3],newTimes[3]));
            check(count(oldDb)==1000 && count(newDb)==1000,"Benchmark retention regression");
            report.append("PASS retention benchmark counts\n");
            return report.toString();
        } finally {SQLiteDatabase.deleteDatabase(a);SQLiteDatabase.deleteDatabase(b);}
    }
    private static double measure(SQLiteDatabase db,String prune) {
        long start=SystemClock.elapsedRealtimeNanos();
        for(int i=0;i<200;i++) {
            db.beginTransaction();
            try {db.execSQL("INSERT INTO history(content,icon) VALUES ('synthetic',zeroblob(256))");db.execSQL(prune);db.setTransactionSuccessful();}
            finally {db.endTransaction();}
        }
        return (SystemClock.elapsedRealtimeNanos()-start)/200.0/1000;
    }
}
