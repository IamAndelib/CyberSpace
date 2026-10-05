package io.github.iamandelib.cyberspace

import android.annotation.SuppressLint
import android.content.ActivityNotFoundException
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.view.View
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.JavascriptInterface
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.graphics.ColorUtils
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.core.view.isVisible
import androidx.webkit.WebSettingsCompat
import androidx.webkit.WebViewFeature
import kotlin.math.max

/**
 * A plain, full-screen WebView for Cyberspace.
 *
 * The page is left alone: no styles or scripts are injected into it, so the site's own
 * themes work exactly as in a browser. The only script added is a read-only watcher that
 * reports the page's `theme-color` so the system bars can match the active theme.
 */
class MainActivity : ComponentActivity() {

    private lateinit var root: FrameLayout
    private lateinit var webView: WebView
    private lateinit var errorView: View

    @Volatile private var pageReady = false
    private var filePathCallback: ValueCallback<Array<Uri>>? = null
    private var customView: View? = null
    private var customViewCallback: WebChromeClient.CustomViewCallback? = null

    private val fileChooser =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            filePathCallback?.onReceiveValue(
                WebChromeClient.FileChooserParams.parseResult(result.resultCode, result.data),
            )
            filePathCallback = null
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        val splash = installSplashScreen()
        super.onCreate(savedInstanceState)
        val startedAt = SystemClock.uptimeMillis()
        splash.setKeepOnScreenCondition {
            !pageReady && SystemClock.uptimeMillis() - startedAt < SPLASH_MAX_MS
        }

        WindowCompat.setDecorFitsSystemWindows(window, false)
        setContentView(R.layout.activity_main)
        root = findViewById(R.id.root)
        webView = findViewById(R.id.web)
        errorView = findViewById(R.id.error)
        findViewById<View>(R.id.retry).setOnClickListener {
            errorView.isVisible = false
            webView.reload()
        }
        setUpSystemBars()
        setUpWebView()

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                when {
                    customView != null -> hideCustomView()
                    webView.canGoBack() -> webView.goBack()
                    else -> {
                        isEnabled = false
                        onBackPressedDispatcher.onBackPressed()
                        isEnabled = true
                    }
                }
            }
        })

        if (savedInstanceState == null || webView.restoreState(savedInstanceState) == null) {
            webView.loadUrl(linkFrom(intent) ?: START_URL)
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        linkFrom(intent)?.let { webView.loadUrl(it) }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        webView.saveState(outState)
    }

    override fun onPause() {
        super.onPause()
        CookieManager.getInstance().flush()
    }

    override fun onDestroy() {
        (webView.parent as? ViewGroup)?.removeView(webView)
        webView.destroy()
        super.onDestroy()
    }

    private fun setUpSystemBars() {
        @Suppress("DEPRECATION")
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.VANILLA_ICE_CREAM) {
            window.statusBarColor = Color.TRANSPARENT
            window.navigationBarColor = Color.TRANSPARENT
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            window.isNavigationBarContrastEnforced = false
        }
        // The root view's background shows through the transparent bars, and its padding
        // keeps the page clear of them (and of the keyboard).
        ViewCompat.setOnApplyWindowInsetsListener(root) { view, insets ->
            val bars = insets.getInsets(
                WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout(),
            )
            val ime = insets.getInsets(WindowInsetsCompat.Type.ime())
            view.setPadding(bars.left, bars.top, bars.right, max(bars.bottom, ime.bottom))
            WindowInsetsCompat.CONSUMED
        }
        applyBarColor(Color.BLACK)
    }

    private fun applyBarColor(color: Int) {
        root.setBackgroundColor(color)
        val light = ColorUtils.calculateLuminance(color) > 0.5
        WindowInsetsControllerCompat(window, root).apply {
            isAppearanceLightStatusBars = light
            isAppearanceLightNavigationBars = light
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun setUpWebView() {
        webView.setBackgroundColor(Color.BLACK)
        with(webView.settings) {
            javaScriptEnabled = true
            domStorageEnabled = true
            mediaPlaybackRequiresUserGesture = false
            mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
            loadWithOverviewMode = true
            useWideViewPort = true
            builtInZoomControls = false
            displayZoomControls = false
            userAgentString = "$userAgentString CyberspaceAndroid/${BuildConfig.VERSION_NAME}"
        }
        // Never let Android repaint the site in its own "dark mode"; the site's themes decide.
        if (WebViewFeature.isFeatureSupported(WebViewFeature.ALGORITHMIC_DARKENING)) {
            WebSettingsCompat.setAlgorithmicDarkeningAllowed(webView.settings, false)
        }
        CookieManager.getInstance().apply {
            setAcceptCookie(true)
            setAcceptThirdPartyCookies(webView, true)
        }
        webView.addJavascriptInterface(ThemeBridge(), "CyberspaceAndroid")

        webView.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                if (!request.isForMainFrame) return false
                val uri = request.url
                if (isCyberspace(uri)) return false
                openExternally(uri)
                return true
            }

            override fun onPageCommitVisible(view: WebView, url: String?) {
                pageReady = true
            }

            override fun onPageFinished(view: WebView, url: String?) {
                pageReady = true
                view.evaluateJavascript(THEME_WATCH_JS, null)
                CookieManager.getInstance().flush()
            }

            override fun onReceivedError(
                view: WebView,
                request: WebResourceRequest,
                error: WebResourceError,
            ) {
                if (request.isForMainFrame) {
                    pageReady = true
                    errorView.isVisible = true
                }
            }
        }

        webView.webChromeClient = object : WebChromeClient() {
            override fun onShowFileChooser(
                view: WebView,
                callback: ValueCallback<Array<Uri>>,
                params: FileChooserParams,
            ): Boolean {
                filePathCallback?.onReceiveValue(null)
                filePathCallback = callback
                return try {
                    fileChooser.launch(params.createIntent())
                    true
                } catch (e: ActivityNotFoundException) {
                    filePathCallback = null
                    false
                }
            }

            override fun onShowCustomView(view: View, callback: CustomViewCallback) {
                if (customView != null) {
                    callback.onCustomViewHidden()
                    return
                }
                customView = view
                customViewCallback = callback
                root.addView(view, FrameLayout.LayoutParams(MATCH, MATCH))
                WindowInsetsControllerCompat(window, root).apply {
                    hide(WindowInsetsCompat.Type.systemBars())
                    systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                }
            }

            override fun onHideCustomView() = hideCustomView()
        }

        webView.setDownloadListener { url, _, _, _, _ -> openExternally(Uri.parse(url)) }
    }

    private fun hideCustomView() {
        val view = customView ?: return
        root.removeView(view)
        customView = null
        customViewCallback?.onCustomViewHidden()
        customViewCallback = null
        WindowInsetsControllerCompat(window, root).show(WindowInsetsCompat.Type.systemBars())
    }

    private fun openExternally(uri: Uri) {
        val intent = if (uri.scheme == "intent") {
            runCatching { Intent.parseUri(uri.toString(), Intent.URI_INTENT_SCHEME) }.getOrNull()
                ?.apply { addCategory(Intent.CATEGORY_BROWSABLE); component = null; selector = null }
        } else {
            Intent(Intent.ACTION_VIEW, uri)
        } ?: return
        try {
            startActivity(intent)
        } catch (e: ActivityNotFoundException) {
            Toast.makeText(this, R.string.no_app_for_link, Toast.LENGTH_SHORT).show()
        }
    }

    private fun linkFrom(intent: Intent?): String? {
        val data = intent?.takeIf { it.action == Intent.ACTION_VIEW }?.data ?: return null
        return data.toString().takeIf { isCyberspace(data) }
    }

    private inner class ThemeBridge {
        @JavascriptInterface
        fun onThemeColor(value: String) {
            val color = parseCssColor(value) ?: return
            runOnUiThread { applyBarColor(color) }
        }
    }

    companion object {
        const val START_URL = "https://cyberspace.online/"
        private const val SPLASH_MAX_MS = 4_000L
        private const val MATCH = ViewGroup.LayoutParams.MATCH_PARENT

        /**
         * Reports the active theme's background colour now and whenever it changes. Read-only.
         * The site sets `<meta name="theme-color">` when the theme is switched, but on load only
         * its `--color-bg` CSS variable reflects the saved theme, so that is the fallback.
         */
        private const val THEME_WATCH_JS = """
            (function () {
              if (window.__cyberspaceAndroidWatch) return;
              window.__cyberspaceAndroidWatch = true;
              var last = null;
              function current() {
                var root = document.documentElement;
                var bg = getComputedStyle(root).getPropertyValue('--color-bg').trim();
                var m = document.querySelector('meta[name="theme-color"]');
                var meta = m ? (m.getAttribute('content') || '').trim() : '';
                return bg || meta;
              }
              function send() {
                var c = current();
                if (c && c !== last) { last = c; CyberspaceAndroid.onThemeColor(c); }
              }
              send();
              var observer = new MutationObserver(send);
              observer.observe(document.head, {
                subtree: true, childList: true, attributes: true, attributeFilter: ['content']
              });
              observer.observe(document.documentElement, {
                attributes: true, attributeFilter: ['data-theme', 'class', 'style']
              });
            })();
        """

        fun isCyberspace(uri: Uri): Boolean {
            val host = uri.host?.lowercase() ?: return false
            return uri.scheme == "https" && (host == "cyberspace.online" || host.endsWith(".cyberspace.online"))
        }

        fun parseCssColor(value: String): Int? {
            val v = value.trim()
            if (v.startsWith("rgb")) {
                val parts = v.substringAfter('(').substringBefore(')')
                    .split(',', ' ', '/').filter { it.isNotBlank() }
                val rgb = parts.take(3).map { it.trim().toFloatOrNull() ?: return null }
                if (rgb.size < 3) return null
                return Color.rgb(rgb[0].toInt(), rgb[1].toInt(), rgb[2].toInt())
            }
            // Expand CSS shorthand (#rgb) which Color.parseColor does not accept.
            val hex = if (v.length == 4 && v.startsWith("#")) "#" + v.drop(1).map { "$it$it" }.joinToString("") else v
            return runCatching { Color.parseColor(hex) }.getOrNull()
        }
    }
}
