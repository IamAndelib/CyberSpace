# WebView JS interface — keep any class annotated with @JavascriptInterface
-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}

# Keep WebView-related classes from being stripped
-keepclassmembers class * extends android.webkit.WebViewClient {
    public void *(android.webkit.WebView, java.lang.String, android.graphics.Bitmap);
    public boolean *(android.webkit.WebView, java.lang.String);
}
-keepclassmembers class * extends android.webkit.WebChromeClient {
    public void *(android.webkit.WebView, java.lang.String);
}

# AndroidX Browser (CustomTabsClient etc.)
-keep class androidx.browser.** { *; }

# Keep our Activity
-keep class online.cyberspace.twa.** { *; }
