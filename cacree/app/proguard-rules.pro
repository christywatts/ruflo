# Keep JavaScript bridge methods — called by name from WebView JS
-keepclassmembers class com.cacree.financialarchitect.MainActivity$JSBridge {
    @android.webkit.JavascriptInterface <methods>;
}

# Keep all app classes (correct package name)
-keep class com.cacree.financialarchitect.** { *; }

# JSON
-keep class org.json.** { *; }

# Suppress warnings for missing classes
-dontwarn androidx.**
