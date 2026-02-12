# Keep methods annotated with @JavascriptInterface (used by WebView JS bridges)
-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}
