package com.cacree.financialarchitect;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.*;
import org.json.JSONArray;
import java.util.ArrayList;
import java.util.List;

public class DatabaseHelper extends SQLiteOpenHelper {

    private static final String DB  = "cacree_v4.db";
    private static final int    VER = 1;

    public DatabaseHelper(Context ctx) { super(ctx, DB, null, VER); }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL(
            "CREATE TABLE transactions (" +
            "id          INTEGER PRIMARY KEY AUTOINCREMENT," +
            "amount      REAL    NOT NULL," +
            "type        TEXT    NOT NULL," +
            "category    TEXT    NOT NULL," +
            "title       TEXT," +
            "currency    TEXT    DEFAULT 'KSh'," +
            "source      TEXT," +
            "hash        TEXT    UNIQUE," +
            "timestamp   INTEGER NOT NULL," +
            "is_recurring INTEGER DEFAULT 0," +
            "is_spike     INTEGER DEFAULT 0)"
        );
        db.execSQL("CREATE INDEX idx_ts   ON transactions(timestamp DESC)");
        db.execSQL("CREATE UNIQUE INDEX idx_hash ON transactions(hash)");
        db.execSQL("CREATE INDEX idx_cur  ON transactions(currency)");
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int o, int n) {
        db.execSQL("DROP TABLE IF EXISTS transactions");
        onCreate(db);
    }

    public boolean insert(Transaction t) {
        try {
            SQLiteDatabase db = getWritableDatabase();
            ContentValues v   = new ContentValues();
            v.put("amount",       t.amount);
            v.put("type",         t.type);
            v.put("category",     t.category);
            v.put("title",        t.title);
            v.put("currency",     t.currency != null ? t.currency : "KSh");
            v.put("source",       t.source);
            v.put("hash",         t.hash);
            v.put("timestamp",    t.timestamp);
            v.put("is_recurring", t.isRecurring ? 1 : 0);
            v.put("is_spike",     t.isSpike     ? 1 : 0);
            return db.insertWithOnConflict("transactions", null, v,
                    SQLiteDatabase.CONFLICT_IGNORE) != -1;
        } catch (Exception e) { return false; }
    }

    public List<Transaction> getAll() {
        List<Transaction> list = new ArrayList<>();
        try {
            Cursor c = getReadableDatabase().rawQuery(
                "SELECT * FROM transactions ORDER BY timestamp DESC", null);
            while (c.moveToNext()) { list.add(fromCursor(c)); }
            c.close();
        } catch (Exception ignored) {}
        return list;
    }

    public List<Transaction> getByCurrency(String currency) {
        List<Transaction> list = new ArrayList<>();
        try {
            Cursor c = getReadableDatabase().rawQuery(
                "SELECT * FROM transactions WHERE currency=? ORDER BY timestamp DESC",
                new String[]{currency});
            while (c.moveToNext()) { list.add(fromCursor(c)); }
            c.close();
        } catch (Exception ignored) {}
        return list;
    }

    private Transaction fromCursor(Cursor c) {
        Transaction t  = new Transaction();
        t.id           = c.getLong(c.getColumnIndexOrThrow("id"));
        t.amount       = c.getDouble(c.getColumnIndexOrThrow("amount"));
        t.type         = c.getString(c.getColumnIndexOrThrow("type"));
        t.category     = c.getString(c.getColumnIndexOrThrow("category"));
        t.title        = c.getString(c.getColumnIndexOrThrow("title"));
        t.currency     = c.getString(c.getColumnIndexOrThrow("currency"));
        t.source       = c.getString(c.getColumnIndexOrThrow("source"));
        t.timestamp    = c.getLong(c.getColumnIndexOrThrow("timestamp"));
        t.isRecurring  = c.getInt(c.getColumnIndexOrThrow("is_recurring")) == 1;
        t.isSpike      = c.getInt(c.getColumnIndexOrThrow("is_spike")) == 1;
        return t;
    }

    public JSONArray getAllJSON() {
        JSONArray arr = new JSONArray();
        for (Transaction t : getAll()) arr.put(t.toJSON());
        return arr;
    }

    public JSONArray getByCurrencyJSON(String currency) {
        JSONArray arr = new JSONArray();
        for (Transaction t : getByCurrency(currency)) arr.put(t.toJSON());
        return arr;
    }

    /** Returns one page of transactions (for incremental loading after SMS import). */
    public JSONArray getPage(int offset, int limit) {
        JSONArray arr = new JSONArray();
        try {
            Cursor c = getReadableDatabase().rawQuery(
                "SELECT * FROM transactions ORDER BY timestamp DESC LIMIT ? OFFSET ?",
                new String[]{String.valueOf(limit), String.valueOf(offset)});
            while (c.moveToNext()) arr.put(fromCursor(c).toJSON());
            c.close();
        } catch (Exception ignored) {}
        return arr;
    }

    public int count() {
        try {
            Cursor c = getReadableDatabase().rawQuery(
                "SELECT COUNT(*) FROM transactions", null);
            int n = c.moveToFirst() ? c.getInt(0) : 0;
            c.close(); return n;
        } catch (Exception e) { return 0; }
    }

    public void clearAll() {
        try { getWritableDatabase().execSQL("DELETE FROM transactions"); }
        catch (Exception ignored) {}
    }
}
