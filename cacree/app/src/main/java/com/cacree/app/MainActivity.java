package com.cacree.app;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.WindowManager;
import android.webkit.*;
import android.widget.EditText;
import android.widget.Toast;
import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsControllerCompat;
import java.util.*;
import org.json.*;

public class MainActivity extends AppCompatActivity {

    private WebView wv;
    private DatabaseHelper db;
    private SharedPreferences prefs;
    private ValueCallback<Uri[]> fileCallback;

    private static final String TAG      = "Cacree";
    private static final int    SMS_CODE = 101;
    private static final int    FILE_CODE= 102;

    @SuppressLint("SetJavaScriptEnabled")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // ── EDGE-TO-EDGE: glass UI extends under status bar ──────
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        if (Build.VERSION.SDK_INT >= 21) {
            getWindow().setStatusBarColor(Color.TRANSPARENT);
            getWindow().setNavigationBarColor(Color.TRANSPARENT);
        }
        // White icons on dark glass bg
        WindowInsetsControllerCompat ctrl =
            WindowCompat.getInsetsController(getWindow(), getWindow().getDecorView());
        ctrl.setAppearanceLightStatusBars(false);
        ctrl.setAppearanceLightNavigationBars(false);
        // Display cutout (notch) — API 28+
        if (Build.VERSION.SDK_INT >= 28) {
            getWindow().getAttributes().layoutInDisplayCutoutMode =
                WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
        }
        // Keep screen on
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        setContentView(R.layout.activity_main);

        wv = findViewById(R.id.webView);
        WebSettings ws = wv.getSettings();

        ws.setJavaScriptEnabled(true);
        ws.setDomStorageEnabled(true);
        ws.setAllowFileAccess(true);
        ws.setCacheMode(WebSettings.LOAD_DEFAULT);
        ws.setTextZoom(100);
        ws.setUseWideViewPort(true);
        ws.setLoadWithOverviewMode(true);
        ws.setBuiltInZoomControls(false);
        ws.setDisplayZoomControls(false);
        ws.setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);
        // Required for file:// sub-resources (CSS, JS from assets)
        ws.setAllowFileAccessFromFileURLs(true);
        ws.setAllowUniversalAccessFromFileURLs(true);
        if (Build.VERSION.SDK_INT >= 17) ws.setMediaPlaybackRequiresUserGesture(false);

        wv.setScrollBarStyle(WebView.SCROLLBARS_OUTSIDE_OVERLAY);
        wv.setOverScrollMode(WebView.OVER_SCROLL_NEVER);
        wv.setBackgroundColor(Color.parseColor("#080808"));

        // ── WEB VIEW CLIENT ───────────────────────────────────────
        wv.setWebViewClient(new WebViewClient() {
            @Override
            public void onReceivedError(WebView v, int code, String desc, String url) {
                Log.e(TAG, "WebView error " + code + ": " + desc + " [" + url + "]");
            }
            @Override
            public void onPageFinished(WebView view, String url) {
                Log.d(TAG, "Loaded: " + url);
                // Inject safe-area CSS via JS after page loads
                injectSafeAreaVars();
            }
        });

        // ── CHROME CLIENT (file chooser, dialogs, console) ────────
        wv.setWebChromeClient(new WebChromeClient() {

            @Override
            public boolean onShowFileChooser(WebView view,
                    ValueCallback<Uri[]> fileCb, FileChooserParams params) {
                if (fileCallback != null) fileCallback.onReceiveValue(null);
                fileCallback = fileCb;
                try { startActivityForResult(params.createIntent(), FILE_CODE); }
                catch (Exception e) { fileCallback = null; return false; }
                return true;
            }

            @Override
            public boolean onJsAlert(WebView v, String u, final String msg, final JsResult r) {
                try {
                    new AlertDialog.Builder(MainActivity.this).setMessage(msg)
                        .setPositiveButton("OK", (d, w) -> r.confirm())
                        .setOnCancelListener(d -> r.cancel()).show();
                } catch (Exception e) { r.confirm(); }
                return true;
            }

            @Override
            public boolean onJsConfirm(WebView v, String u, final String msg, final JsResult r) {
                try {
                    new AlertDialog.Builder(MainActivity.this).setMessage(msg)
                        .setPositiveButton("OK", (d, w) -> r.confirm())
                        .setNegativeButton("Cancel", (d, w) -> r.cancel())
                        .setOnCancelListener(d -> r.cancel()).show();
                } catch (Exception e) { r.cancel(); }
                return true;
            }

            @Override
            public boolean onJsPrompt(WebView v, String u, final String msg,
                    String def, final JsPromptResult r) {
                try {
                    final EditText et = new EditText(MainActivity.this);
                    et.setText(def != null ? def : "");
                    new AlertDialog.Builder(MainActivity.this).setMessage(msg).setView(et)
                        .setPositiveButton("OK",     (d, w) -> r.confirm(et.getText().toString()))
                        .setNegativeButton("Cancel", (d, w) -> r.cancel())
                        .setOnCancelListener(d -> r.cancel()).show();
                } catch (Exception e) { r.cancel(); }
                return true;
            }

            @Override
            public boolean onConsoleMessage(ConsoleMessage cm) {
                Log.d(TAG, "JS[" + cm.lineNumber() + "]: " + cm.message());
                return true;
            }
        });

        // ── JS BRIDGE ─────────────────────────────────────────────
        wv.addJavascriptInterface(new JSBridge(), "Android");
        wv.addJavascriptInterface(new JSBridge(), "AndroidBridge"); // compat alias

        wv.loadUrl("file:///android_asset/index.html");
        Log.d(TAG, "loadUrl → index.html");

        // Init DB after short delay (WebView loads async)
        wv.postDelayed(() -> {
            try {
                prefs = getSharedPreferences("cacree", MODE_PRIVATE);
                db    = new DatabaseHelper(MainActivity.this);
                Log.d(TAG, "DB ready, count=" + db.count());
            } catch (Exception e) { Log.e(TAG, "DB init: " + e.getMessage()); }
        }, 400);

        // Request SMS permission after UI settles
        wv.postDelayed(this::requestPerms, 3000);

        // Handle back press with predictive-back API (Android 13+) and WebView history
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                if (wv != null && wv.canGoBack()) {
                    wv.goBack();
                } else {
                    setEnabled(false);
                    getOnBackPressedDispatcher().onBackPressed();
                }
            }
        });
    }

    /** Injects actual status bar height so the glass top bar clears the system bar */
    private void injectSafeAreaVars() {
        int statusBarPx = 0;
        int resId = getResources().getIdentifier("status_bar_height", "dimen", "android");
        if (resId > 0) statusBarPx = getResources().getDimensionPixelSize(resId);
        float density = getResources().getDisplayMetrics().density;
        int statusBarDp = Math.round(statusBarPx / density);
        final String cssValue = statusBarDp + "px";
        wv.post(() -> wv.evaluateJavascript(
            "document.documentElement.style.setProperty('--safe-top','" + cssValue + "');", null));
    }

    private void requestPerms() {
        try {
            List<String> need = new ArrayList<>();
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_SMS)
                    != PackageManager.PERMISSION_GRANTED)
                need.add(Manifest.permission.READ_SMS);
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECEIVE_SMS)
                    != PackageManager.PERMISSION_GRANTED)
                need.add(Manifest.permission.RECEIVE_SMS);
            if (Build.VERSION.SDK_INT >= 33 &&
                    ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                            != PackageManager.PERMISSION_GRANTED)
                need.add(Manifest.permission.POST_NOTIFICATIONS);
            if (!need.isEmpty())
                ActivityCompat.requestPermissions(this, need.toArray(new String[0]), SMS_CODE);
        } catch (Exception e) { Log.e(TAG, "requestPerms: " + e.getMessage()); }
    }

    @Override
    protected void onActivityResult(int req, int res, Intent data) {
        super.onActivityResult(req, res, data);
        if (req == FILE_CODE && fileCallback != null) {
            Uri[] uris = null;
            if (res == RESULT_OK && data != null) {
                String s = data.getDataString();
                if (s != null) uris = new Uri[]{ Uri.parse(s) };
                else if (data.getClipData() != null) {
                    int n = data.getClipData().getItemCount();
                    uris = new Uri[n];
                    for (int i = 0; i < n; i++)
                        uris[i] = data.getClipData().getItemAt(i).getUri();
                }
            }
            fileCallback.onReceiveValue(uris);
            fileCallback = null;
        }
    }

    @Override
    public void onRequestPermissionsResult(int code,
            @NonNull String[] perms, @NonNull int[] res) {
        super.onRequestPermissionsResult(code, perms, res);
        final boolean ok = res.length > 0 && res[0] == PackageManager.PERMISSION_GRANTED;
        Log.d(TAG, "Permission result: " + ok + " code=" + code);
        if (wv != null) wv.post(() ->
            wv.evaluateJavascript(
                ok ? "if(window.onSmsGranted)window.onSmsGranted();"
                   : "if(window.onSmsDenied)window.onSmsDenied();", null));
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (wv != null) wv.onResume();
        SmsReceiver.setWebView(wv);   // register live WebView for real-time push
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (wv != null) wv.onPause();
        SmsReceiver.setWebView(null); // unregister — app backgrounded
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (wv != null) { wv.destroy(); wv = null; }
        if (db != null) { db.close(); db = null; }
    }

    // ── JS BRIDGE ─────────────────────────────────────────────────
    public class JSBridge {

        private DatabaseHelper getDb() {
            if (db == null)
                try { db = new DatabaseHelper(MainActivity.this); } catch (Exception ignored) {}
            return db;
        }

        private SharedPreferences getPrefs() {
            if (prefs == null) prefs = getSharedPreferences("cacree", MODE_PRIVATE);
            return prefs;
        }

        /** Import all SMS, parse, dedup, return JSON array */
        @JavascriptInterface
        public String importSms() {
            try {
                if (!hasSmsPermission()) return "{\"error\":\"no_permission\"}";
                DatabaseHelper d = getDb();
                if (d == null) return "{\"error\":\"db_null\"}";
                String r = SmsReader.readAll(MainActivity.this, d);
                return r != null ? r : "[]";
            } catch (Exception e) {
                Log.e(TAG, "importSms: " + e);
                return "{\"error\":\"" + e.getMessage() + "\"}";
            }
        }

        /** Open the native Android share sheet (all apps, not just WhatsApp) */
        @JavascriptInterface
        public void shareText(String text) {
            try {
                Intent send = new Intent(Intent.ACTION_SEND);
                send.setType("text/plain");
                send.putExtra(Intent.EXTRA_TEXT, text != null ? text : "");
                Intent chooser = Intent.createChooser(send, "Share your recap");
                chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(chooser);
            } catch (Exception e) { Log.e(TAG, "shareText: " + e); }
        }

        /** Write a CSV file and open the share sheet with it attached. */
        @JavascriptInterface
        public void exportCsv(final String filename, final String content) {
            try {
                String fname = (filename == null || filename.isEmpty()) ? "cacree_statement.csv" : filename;
                java.io.File dir = new java.io.File(getCacheDir(), "exports");
                if (!dir.exists()) dir.mkdirs();
                java.io.File f = new java.io.File(dir, fname);
                java.io.FileOutputStream fos = new java.io.FileOutputStream(f);
                fos.write((content != null ? content : "").getBytes("UTF-8"));
                fos.close();
                android.net.Uri uri = androidx.core.content.FileProvider.getUriForFile(
                        MainActivity.this, "com.cacree.app.fileprovider", f);
                Intent send = new Intent(Intent.ACTION_SEND);
                send.setType("text/csv");
                send.putExtra(Intent.EXTRA_STREAM, uri);
                send.putExtra(Intent.EXTRA_SUBJECT, "CACREE Statement");
                send.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                Intent chooser = Intent.createChooser(send, "Export statement");
                chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(chooser);
            } catch (Exception e) {
                Log.e(TAG, "exportCsv: " + e);
                shareText(content);   // fallback: share as plain text
            }
        }

        /**
         * Async SMS import — runs on a background thread so the WebView's JS
         * thread (and the whole UI) stays responsive during the scan.
         * Result is delivered via window.onSmsImported(jsonArray).
         */
        @JavascriptInterface
        public void importSmsAsync() {
            new Thread(() -> {
                String result;
                try {
                    if (!hasSmsPermission()) result = "{\"error\":\"no_permission\"}";
                    else {
                        DatabaseHelper d = getDb();
                        if (d == null) result = "{\"error\":\"db_null\"}";
                        else {
                            String r = SmsReader.readAll(MainActivity.this, d);
                            result = r != null ? r : "[]";
                        }
                    }
                } catch (Exception e) {
                    Log.e(TAG, "importSmsAsync: " + e);
                    result = "{\"error\":\"scan_failed\"}";
                }
                final String payload = result;
                if (wv != null) wv.post(() -> {
                    if (wv != null) wv.evaluateJavascript(
                        "if(window.onSmsImported)window.onSmsImported(" +
                        payload + ");", null);
                });
            }, "cacree-sms-import").start();
        }

        /** Return all stored transactions as JSON — called on app boot */
        @JavascriptInterface
        public String getTransactions() {
            try {
                DatabaseHelper d = getDb();
                return d != null ? d.getAllJSON().toString() : "[]";
            } catch (Exception e) { return "[]"; }
        }

        /** Return transactions for a specific currency */
        @JavascriptInterface
        public String getTransactionsByCurrency(String currency) {
            try {
                DatabaseHelper d = getDb();
                return d != null ? d.getByCurrencyJSON(currency).toString() : "[]";
            } catch (Exception e) { return "[]"; }
        }

        @JavascriptInterface
        public boolean hasSmsPermission() {
            return ContextCompat.checkSelfPermission(MainActivity.this,
                    Manifest.permission.READ_SMS) == PackageManager.PERMISSION_GRANTED;
        }

        @JavascriptInterface
        public void requestSmsPermission() {
            try {
                ActivityCompat.requestPermissions(MainActivity.this,
                    new String[]{ Manifest.permission.READ_SMS, Manifest.permission.RECEIVE_SMS },
                    SMS_CODE);
            } catch (Exception e) { Log.e(TAG, "reqSms: " + e); }
        }

        /** Save full user profile as JSON string */
        @JavascriptInterface
        public void saveUserProfile(String email, String name, String phone) {
            try {
                getPrefs().edit()
                    .putString("email",  email != null ? email : "")
                    .putString("name",   name  != null ? name  : "")
                    .putString("phone",  phone != null ? phone : "")
                    .putBoolean("registered", true)
                    .apply();
            } catch (Exception ignored) {}
        }

        @JavascriptInterface
        public String getUserProfile() {
            try {
                JSONObject o = new JSONObject();
                o.put("email", getPrefs().getString("email", ""));
                o.put("name",  getPrefs().getString("name",  ""));
                o.put("phone", getPrefs().getString("phone", ""));
                return o.toString();
            } catch (Exception e) { return "{}"; }
        }

        /** Set Pro status (wire to real billing in production) */
        @JavascriptInterface
        public void setPro(boolean active) {
            getPrefs().edit().putBoolean("pro", active).apply();
        }

        @JavascriptInterface
        public boolean isPro() {
            return getPrefs().getBoolean("pro", false);
        }

        /** Save base currency preference */
        @JavascriptInterface
        public void setBaseCurrency(String code) {
            getPrefs().edit().putString("base_currency", code != null ? code : "USD").apply();
        }

        @JavascriptInterface
        public String getBaseCurrency() {
            return getPrefs().getString("base_currency", "USD");
        }

        /** Clear all transaction data */
        @JavascriptInterface
        public void clearAll() {
            try {
                DatabaseHelper d = getDb();
                if (d != null) d.clearAll();
            } catch (Exception ignored) {}
        }

        @JavascriptInterface
        public int txCount() {
            try {
                DatabaseHelper d = getDb();
                return d != null ? d.count() : 0;
            } catch (Exception e) { return 0; }
        }

        /** Run intelligence engine and return JSON insights */
        @JavascriptInterface
        public String getInsights() {
            try {
                DatabaseHelper d = getDb();
                if (d == null) return "[]";
                Map<String, Double> m = MetricsEngine.compute(d.getAll());
                List<String> insights  = IntelligenceEngine.generate(m);
                JSONArray arr = new JSONArray();
                for (String s : insights) arr.put(s);
                return arr.toString();
            } catch (Exception e) { return "[]"; }
        }

        /** Show a native toast (for critical errors where JS toast may not be visible) */
        @JavascriptInterface
        public void nativeToast(final String msg) {
            runOnUiThread(() -> Toast.makeText(MainActivity.this, msg, Toast.LENGTH_SHORT).show());
        }

        /** Post a system notification (used by budget alerts). */
        @JavascriptInterface
        public void notify(final String title, final String body) {
            try {
                android.app.NotificationManager nm = (android.app.NotificationManager)
                        getSystemService(NOTIFICATION_SERVICE);
                if (nm == null) return;
                String chId = "cacree_alerts";
                if (android.os.Build.VERSION.SDK_INT >= 26) {
                    android.app.NotificationChannel ch = new android.app.NotificationChannel(
                            chId, "CACREE Alerts", android.app.NotificationManager.IMPORTANCE_HIGH);
                    ch.setDescription("Budget and spending alerts");
                    nm.createNotificationChannel(ch);
                }
                android.content.Intent open = new android.content.Intent(MainActivity.this, MainActivity.class);
                open.setFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK | android.content.Intent.FLAG_ACTIVITY_SINGLE_TOP);
                int piFlags = android.os.Build.VERSION.SDK_INT >= 23
                        ? android.app.PendingIntent.FLAG_UPDATE_CURRENT | android.app.PendingIntent.FLAG_IMMUTABLE
                        : android.app.PendingIntent.FLAG_UPDATE_CURRENT;
                android.app.PendingIntent pi = android.app.PendingIntent.getActivity(
                        MainActivity.this, 7000, open, piFlags);
                android.app.Notification.Builder b = (android.os.Build.VERSION.SDK_INT >= 26)
                        ? new android.app.Notification.Builder(MainActivity.this, chId)
                        : new android.app.Notification.Builder(MainActivity.this);
                b.setSmallIcon(R.drawable.ic_stat_cacree)
                 .setColor(0xFF2EAD4F)
                 .setContentTitle(title)
                 .setContentText(body)
                 .setStyle(new android.app.Notification.BigTextStyle().bigText(body))
                 .setAutoCancel(true)
                 .setContentIntent(pi);
                nm.notify((int)(System.currentTimeMillis() & 0xFFFF), b.build());
            } catch (Exception e) { Log.e(TAG, "notify: " + e); }
        }
    }
}
