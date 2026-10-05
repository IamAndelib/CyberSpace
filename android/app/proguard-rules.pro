# Methods exposed to page JavaScript must keep their names.
-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}
