package com.cacree.app;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.telephony.SmsMessage;
import android.util.Log;
import android.webkit.WebView;

import java.lang.ref.WeakReference;

public class SmsReceiver extends BroadcastReceiver {

    private static final String TAG     = "Cacree";
    private static final String CH_ID   = "cacree_tx";
    private static final String CH_NAME = "New Transactions";
    private static int notifId = 5000;

    // Weak reference — avoids leaking Activity if app is destroyed
    private static WeakReference<WebView> sWebView = new WeakReference<>(null);

    /** Called by MainActivity.onResume() / onPause() */
    public static void setWebView(WebView wv) {
        sWebView = new WeakReference<>(wv);
    }

    @Override
    public void onReceive(Context ctx, Intent intent) {
        try {
            Bundle extras = intent.getExtras();
            if (extras == null) return;
            Object[] pdus = (Object[]) extras.get("pdus");
            if (pdus == null) return;
            String format = extras.getString("format");

            DatabaseHelper db = new DatabaseHelper(ctx);

            for (Object pdu : pdus) {
                try {
                    SmsMessage sms = Build.VERSION.SDK_INT >= 23
                            ? SmsMessage.createFromPdu((byte[]) pdu, format)
                            : SmsMessage.createFromPdu((byte[]) pdu);
                    if (sms == null) continue;

                    String body    = sms.getMessageBody();
                    String address = sms.getOriginatingAddress();
                    long   date    = sms.getTimestampMillis();

                    Transaction t = SmsParser.parse(body, address, date);
                    if (t == null) continue;

                    boolean inserted = db.insert(t);
                    Log.d(TAG, "SmsReceiver: " + t.currency + " " + t.amount
                            + " from=" + address + " inserted=" + inserted);

                    if (inserted) {
                        pushToWebView(ctx, t);
                        sendNotification(ctx, t);
                    }
                } catch (Exception inner) {
                    Log.e(TAG, "SmsReceiver pdu error: " + inner.getMessage());
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "SmsReceiver error: " + e.getMessage());
        }
    }

    /**
     * If the app is in the foreground (WebView registered), inject the new
     * transaction directly into the running JS context so the UI updates instantly.
     */
    private void pushToWebView(Context ctx, Transaction t) {
        WebView wv = sWebView.get();
        if (wv == null) return; // app not in foreground — notification handles it

        // Escape strings for safe JSON injection
        String merchant  = escJson(t.title    != null ? t.title    : "");
        String category  = escJson(t.category != null ? t.category : "Shopping");
        String source    = escJson(t.source   != null ? t.source   : "SMS");
        String currency  = escJson(t.currency != null ? t.currency : "KSh");
        String type      = "income".equals(t.type) ? "income" : "expense";
        String hash      = escJson(t.hash     != null ? t.hash     : "");

        // Build a minimal JS transaction object and call window.onNewTransaction
        String js = "if(window.onNewTransaction){" +
            "window.onNewTransaction({" +
                "id:'" + Math.abs(t.hashCode()) + System.currentTimeMillis() % 10000 + "'," +
                "hash:'" + hash + "'," +
                "type:'" + type + "'," +
                "amount:" + t.amount + "," +
                "currency:'" + currency + "'," +
                "merchant:'" + merchant + "'," +
                "category:'" + category + "'," +
                "source:'" + source + "'," +
                "date:'" + new java.util.Date(t.timestamp).toInstant().toString().substring(0, 10) + "'," +
                "timestamp:" + t.timestamp +
            "});" +
        "}";

        wv.post(() -> wv.evaluateJavascript(js, null));
        Log.d(TAG, "Pushed transaction to WebView: " + merchant + " " + t.amount);
    }

    private void sendNotification(Context ctx, Transaction t) {
        try {
            NotificationManager nm = (NotificationManager)
                    ctx.getSystemService(Context.NOTIFICATION_SERVICE);
            if (nm == null) return;

            if (Build.VERSION.SDK_INT >= 26) {
                NotificationChannel ch = new NotificationChannel(
                        CH_ID, CH_NAME, NotificationManager.IMPORTANCE_HIGH);
                ch.setDescription("Financial transaction alerts");
                nm.createNotificationChannel(ch);
            }

            String cur    = t.currency != null ? t.currency : "KSh";
            boolean isInc = "income".equals(t.type);
            String amtFmt = cur + " " + String.format("%,.0f", t.amount);
            String name   = (t.title != null && !t.title.isEmpty())
                    ? t.title : (t.category != null ? t.category : "Transaction");
            String catEmoji = catEmoji(t.category);
            String title  = isInc ? "\uD83D\uDCB0 " + amtFmt + " received"
                                  : catEmoji + " " + amtFmt + " spent";
            String body   = name + (t.category != null ? "  \u00B7  " + t.category : "");
            String big    = body + "\nTracked automatically by CACREE \u00B7 Tap to see where your money went";

            Intent open = new Intent(ctx, MainActivity.class);
            open.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            int piFlags = Build.VERSION.SDK_INT >= 23
                    ? PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
                    : PendingIntent.FLAG_UPDATE_CURRENT;
            PendingIntent pi = PendingIntent.getActivity(ctx, notifId, open, piFlags);

            android.app.Notification notif;
            if (Build.VERSION.SDK_INT >= 26) {
                notif = new android.app.Notification.Builder(ctx, CH_ID)
                        .setSmallIcon(R.drawable.ic_stat_cacree)
                        .setColor(0xFF2EAD4F)
                        .setContentTitle(title)
                        .setContentText(body)
                        .setStyle(new android.app.Notification.BigTextStyle().bigText(big))
                        .setShowWhen(true)
                        .setAutoCancel(true)
                        .setContentIntent(pi)
                        .build();
            } else {
                notif = new android.app.Notification.Builder(ctx)
                        .setSmallIcon(R.drawable.ic_stat_cacree)
                        .setColor(0xFF2EAD4F)
                        .setContentTitle(title)
                        .setContentText(body)
                        .setStyle(new android.app.Notification.BigTextStyle().bigText(big))
                        .setAutoCancel(true)
                        .setContentIntent(pi)
                        .build();
            }

            nm.notify(notifId++, notif);
            Log.d(TAG, "Notification: " + title + " — " + body);

        } catch (Exception e) {
            Log.e(TAG, "Notification error: " + e.getMessage());
        }
    }

    private static String catEmoji(String c) {
        if (c == null) return "\uD83D\uDCB3";
        switch (c) {
            case "Groceries": case "Dining": return "\uD83C\uDF72";
            case "Transport": return "\uD83D\uDE8C";
            case "Bills": return "\uD83D\uDCA1";
            case "Airtime": case "Data Bundle": case "Telecom": return "\uD83D\uDCF1";
            case "Shopping": return "\uD83D\uDECD";
            case "Peer": case "Withdrawal": return "\uD83D\uDD01";
            default: return "\uD83D\uDCB3";
        }
    }

    private static String escJson(String s) {
        if (s == null) return "";
        StringBuilder sb = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '\\') sb.append("\\\\");
            else if (c == '\'') sb.append("\\'");
            else if (c == '"') sb.append("\\\"");
            else if (c == '\u2028' || c == '\u2029') sb.append(' ');  // JS line separators break string literals
            else if (c < 0x20) sb.append(' ');                        // all control chars incl \n \r \t
            else sb.append(c);
        }
        return sb.toString();
    }
}
