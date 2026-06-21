# Keep JavaScript bridge methods — called by name from WebView JS
-keepclassmembers class com.cacree.app.MainActivity$JSBridge {
    @android.webkit.JavascriptInterface <methods>;
}

# Keep all public JS-bridge classes
-keep class com.cacree.app.** { *; }

# AndroidX / AppCompat
-keep class androidx.** { *; }
-dontwarn androidx.**

# JSON
-keep class org.json.** { *; }
