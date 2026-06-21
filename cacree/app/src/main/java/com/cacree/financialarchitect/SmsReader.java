package com.cacree.financialarchitect;

import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import org.json.JSONArray;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class SmsReader {

    private static final int LIMIT = 5000;

    public static String readAll(Context ctx, DatabaseHelper db) {
        JSONArray imported = new JSONArray();
        try {
            Cursor c = ctx.getContentResolver().query(
                Uri.parse("content://sms/inbox"),
                new String[]{"address", "body", "date"},
                null, null, "date DESC");

            if (c == null) return "[]";

            int idxAddr = c.getColumnIndex("address");
            int idxBody = c.getColumnIndex("body");
            int idxDate = c.getColumnIndex("date");
            int count   = 0;

            // Single transaction: thousands of per-row commits -> one commit.
            android.database.sqlite.SQLiteDatabase txDb = db.getWritableDatabase();
            txDb.beginTransaction();
            try {
            while (c.moveToNext() && count < LIMIT) {
                count++;
                String address = c.getString(idxAddr);
                String body    = c.getString(idxBody);
                long   date    = c.getLong(idxDate); // REAL SMS TIMESTAMP

                if (body == null || body.isEmpty()) continue;

                Transaction t = SmsParser.parse(body, address, date);
                if (t == null) continue;

                if (db.insert(t)) imported.put(t.toJSON());
            }
            txDb.setTransactionSuccessful();
            } finally { txDb.endTransaction(); }
            c.close();

            // Run spike + recurring detection after import
            detectPatternsAndUpdate(db);

        } catch (Exception e) {
            android.util.Log.e("Cacree", "SmsReader: " + e.getMessage());
        }
        return imported.toString();
    }

    /** Detect spending spikes (> 2x avg) and recurring payments */
    public static void detectPatternsAndUpdate(DatabaseHelper db) {
        try {
            List<Transaction> all = db.getAll();
            if (all.isEmpty()) return;

            // Per-currency average for spike detection
            Map<String, double[]> curStats = new HashMap<>();
            for (Transaction t : all) {
                if (!"expense".equals(t.type)) continue;
                String cur = t.currency != null ? t.currency : "KSh";
                double[] s = curStats.get(cur);
                if (s == null) { s = new double[]{0, 0}; curStats.put(cur, s); }
                s[0] += t.amount; s[1]++;
            }

            // Recurring: same amount + same category within 28 days of another
            Map<String, Long> seenKey = new HashMap<>();
            android.database.sqlite.SQLiteDatabase sqlDb = db.getWritableDatabase();
            sqlDb.beginTransaction();
            try {
            for (Transaction t : all) {
                if (!"expense".equals(t.type)) continue;
                String cur = t.currency != null ? t.currency : "KSh";

                // Spike detection
                double[] s = curStats.get(cur);
                boolean spike = false;
                if (s != null && s[1] > 2) {
                    double avg = s[0] / s[1];
                    if (t.amount > avg * 2.5) spike = true;
                }

                // Recurring detection
                String rKey = t.category + "_" + Math.round(t.amount);
                Long prevTs = seenKey.get(rKey);
                boolean recurring = false;
                if (prevTs != null) {
                    long daysDiff = Math.abs(t.timestamp - prevTs) / (86400_000L);
                    if (daysDiff <= 35) recurring = true;
                }
                seenKey.put(rKey, t.timestamp);

                if (spike || recurring) {
                    android.content.ContentValues v = new android.content.ContentValues();
                    v.put("is_spike",     spike     ? 1 : 0);
                    v.put("is_recurring", recurring ? 1 : 0);
                    sqlDb.update("transactions", v, "id=?",
                            new String[]{String.valueOf(t.id)});
                }
            }
            sqlDb.setTransactionSuccessful();
            } finally { sqlDb.endTransaction(); }
        } catch (Exception e) {
            android.util.Log.e("Cacree", "detectPatterns: " + e.getMessage());
        }
    }
}
