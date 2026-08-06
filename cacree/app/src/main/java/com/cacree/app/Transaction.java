package com.cacree.app;

import org.json.JSONObject;

public class Transaction {
    public long    id;
    public double  amount;
    public String  type;        // income | expense
    public String  category;
    public String  title;
    public String  currency;    // TSh KSh UGX ZAR USD GBP EUR RWF ETB ZMW NGN GHS
    public String  source;      // M-Pesa, NMB, KCB, etc.
    public String  hash;        // SHA-256 dedup key — MUST be in JSON
    public long    timestamp;
    public boolean isRecurring;
    public boolean isSpike;
    public String  rawSms;

    public Transaction() {}

    public Transaction(double amount, String type, String category,
                       String source, String hash, long timestamp) {
        this.amount    = amount;
        this.type      = type;
        this.category  = category;
        this.source    = source;
        this.hash      = hash;
        this.timestamp = timestamp;
        this.currency  = "TSh"; // Default Tanzania — parser overrides per SMS
    }

    public JSONObject toJSON() {
        JSONObject o = new JSONObject();
        try {
            // id=0 until DB assigns — use hash as the stable unique key
            o.put("id",          hash != null ? hash : String.valueOf(id));
            o.put("dbId",        hash != null ? hash : String.valueOf(id));
            o.put("hash",        hash != null ? hash : "");

            o.put("amount",      amount);
            o.put("type",        type     != null ? type     : "expense");

            String cat = category != null ? category : "Essentials";
            o.put("cat",         cat);
            o.put("category",    cat);

            o.put("title",       title    != null ? title    : cat);
            o.put("merchant",    title    != null ? title    : cat);
            o.put("currency",    currency != null ? currency : "TSh");
            o.put("source",      source   != null ? source   : "SMS");
            o.put("timestamp",   timestamp);
            o.put("isRecurring", isRecurring);
            o.put("isSpike",     isSpike);

            if (rawSms != null) o.put("rawSms", rawSms);
        } catch (Exception ignored) {}
        return o;
    }
}
