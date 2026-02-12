package com.cyberspace.app

import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.webkit.JavascriptInterface
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.ProgressBar
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.graphics.ColorUtils
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsControllerCompat

class MainActivity : AppCompatActivity() {

    private lateinit var webView: WebView
    private lateinit var swipeRefresh: WebViewSwipeRefreshLayout
    private lateinit var loadingLayout: View
    private lateinit var loadingSpinner: ProgressBar
    private lateinit var loadingText: android.widget.TextView
    private lateinit var rootLayout: View
    private var fileUploadCallback: ValueCallback<Array<Uri>>? = null
    private var hadError = false
    private var isInitialLoad = true
    private var isActivityDestroyed = false
    private var displayProgress = 0
    private var targetProgress = 0
    private val progressTickRunnable = object : Runnable {
        override fun run() {
            if (isActivityDestroyed || loadingLayout.visibility != View.VISIBLE) return
            if (displayProgress < targetProgress) {
                displayProgress += maxOf((targetProgress - displayProgress) / 4, 1)
            } else if (displayProgress < 90) {
                displayProgress++
            }
            if (displayProgress > 90) displayProgress = 90
            loadingText.text = "LOADING... $displayProgress%"
            webView.postDelayed(this, 100)
        }
    }
    private val hideLoadingRunnable = Runnable {
        if (isActivityDestroyed) return@Runnable
        webView.removeCallbacks(progressTickRunnable)
        loadingLayout.visibility = View.GONE
        swipeRefresh.isRefreshing = false
    }

    @Volatile
    private var contentScrollY = 0

    private val fileChooserLauncher: ActivityResultLauncher<Intent> =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            val uris = WebChromeClient.FileChooserParams.parseResult(result.resultCode, result.data)
            fileUploadCallback?.onReceiveValue(uris)
            fileUploadCallback = null
        }

    companion object {
        private const val URL = "https://cyberspace.online/"
        private const val DOMAIN = "cyberspace.online"

        private const val SCROLL_OBSERVER_JS = """
            (function() {
                if (window.__scrollObserverInstalled) return;
                window.__scrollObserverInstalled = true;
                var last = -1;
                function report(y) {
                    y = Math.round(y);
                    if (y !== last) { last = y; AndroidScroll.report(y); }
                }
                function windowScroll() {
                    report(window.scrollY || window.pageYOffset || 0);
                }
                window.addEventListener('scroll', windowScroll, { passive: true });
                document.addEventListener('scroll', function(e) {
                    var t = e.target;
                    if (t && t !== document && t.scrollTop !== undefined) {
                        report(t.scrollTop);
                    }
                }, { capture: true, passive: true });
                windowScroll();
            })();
        """

        private const val THEME_OBSERVER_JS = """
            (function() {
                if (window.__themeObserverInstalled) return;
                window.__themeObserverInstalled = true;
                function reportBg() {
                    var style = getComputedStyle(document.body);
                    var bg = style.backgroundColor;
                    var fg = style.color;

                    if (!bg || bg === 'rgba(0, 0, 0, 0)' || bg === 'transparent') {
                        var docStyle = getComputedStyle(document.documentElement);
                        bg = docStyle.backgroundColor;
                        if (!fg) fg = docStyle.color;
                    }

                    if (bg && bg !== 'rgba(0, 0, 0, 0)' && bg !== 'transparent') {
                        AndroidTheme.reportTheme(bg, fg || '#ffffff');
                    }
                }
                var obs = new MutationObserver(reportBg);
                obs.observe(document.body, { attributes: true, attributeFilter: ['class', 'style'] });
                obs.observe(document.documentElement, { attributes: true, attributeFilter: ['class', 'style', 'data-theme'] });
                reportBg();
            })();
        """

        private const val CUSTOM_UI_MODIFIER_JS = """
            (function() {
                if (window.__customUiObserverInstalled) return;
                window.__customUiObserverInstalled = true;

                function processButtons() {
                    var buttons = document.querySelectorAll('button, a, div[role="button"]');
                    buttons.forEach(function(el) {
                        var text = el.innerText ? el.innerText.trim() : "";
                        var label = (el.getAttribute('aria-label') || "").toLowerCase();
                        var lowerText = text.toLowerCase();

                        // --- OPEN BUTTON ---
                        if (text.includes('[↵] Open') || label.includes('open link')) {
                            if (!el.innerHTML.includes('↗')) {
                                el.innerHTML = '<span style="font-size: 1.5em; line-height: 1;">↗</span>';
                                el.classList.add('icon-btn-custom');
                                setCommonBtnStyles(el);
                            }
                        }

                        // --- SAVE BUTTON (Outline) ---
                        else if ((text.includes('[S] Save') || label === 'save post') && !lowerText.includes('unsave') && !label.includes('unsave')) {
                            if (!el.innerHTML.includes('svg') || el.innerHTML.includes('fill="currentColor"')) {
                                el.innerHTML = '<svg xmlns="http://www.w3.org/2000/svg" width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" style="display: block;"><path d="M19 21l-7-5-7 5V5a2 2 0 0 1 2-2h10a2 2 0 0 1 2 2z"></path></svg>';
                                el.classList.add('icon-btn-custom');
                                setCommonBtnStyles(el);
                            }
                        }

                        // --- UNSAVE BUTTON (Filled) ---
                        else if (lowerText.includes('unsave') || label.includes('unsave') || lowerText === 'remove' || text.includes('[R] Remove') || label === 'remove post') {
                            if (!el.innerHTML.includes('svg') || el.innerHTML.includes('fill="none"')) {
                                el.innerHTML = '<svg xmlns="http://www.w3.org/2000/svg" width="20" height="20" viewBox="0 0 24 24" fill="currentColor" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" style="display: block;"><path d="M19 21l-7-5-7 5V5a2 2 0 0 1 2-2h10a2 2 0 0 1 2 2z"></path></svg>';
                                el.classList.add('icon-btn-custom');
                                setCommonBtnStyles(el);
                            }
                        }
                    });
                }

                function setCommonBtnStyles(el) {
                    el.style.padding = '4px 8px';
                    el.style.display = 'flex';
                    el.style.alignItems = 'center';
                    el.style.justifyContent = 'center';
                }

                var debounceTimer = null;
                var obs = new MutationObserver(function() {
                    if (debounceTimer) clearTimeout(debounceTimer);
                    debounceTimer = setTimeout(processButtons, 150);
                });
                obs.observe(document.body, { childList: true, subtree: true, characterData: true });
                processButtons();
            })();
        """
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        setContentView(R.layout.activity_main)

        swipeRefresh = findViewById(R.id.swipeRefresh)
        webView = findViewById(R.id.webView)
        loadingLayout = findViewById(R.id.loadingLayout)
        loadingSpinner = findViewById(R.id.loadingSpinner)
        loadingText = findViewById(R.id.loadingText)
        rootLayout = findViewById(R.id.rootLayout)

        // Load saved theme colors
        val prefs = getSharedPreferences("app_prefs", MODE_PRIVATE)
        val savedBgColor = prefs.getInt("theme_color_bg", Color.BLACK)
        val savedFgColor = prefs.getInt("theme_color_fg", Color.WHITE)

        rootLayout.setBackgroundColor(savedBgColor)
        loadingSpinner.indeterminateTintList = ColorStateList.valueOf(savedFgColor)
        loadingText.setTextColor(savedFgColor)
        swipeRefresh.setColorSchemeColors(savedFgColor)
        webView.setBackgroundColor(savedBgColor)
        loadingLayout.setBackgroundColor(savedBgColor)

        // Apply saved theme to system bars
        window.statusBarColor = savedBgColor
        window.navigationBarColor = savedBgColor
        val insetsController = WindowInsetsControllerCompat(window, window.decorView)
        val light = isLightColor(savedBgColor)
        insetsController.isAppearanceLightStatusBars = light
        insetsController.isAppearanceLightNavigationBars = light

        setupWebView()
        setupSwipeRefresh()
        setupBackNavigation()

        if (savedInstanceState != null) {
            webView.restoreState(savedInstanceState)
        } else {
            val deepLinkUri = intent?.data
            val deepLinkHost = deepLinkUri?.host
            if (deepLinkUri != null && (deepLinkHost == DOMAIN || deepLinkHost?.endsWith(".$DOMAIN") == true)) {
                webView.loadUrl(deepLinkUri.toString())
            } else {
                webView.loadUrl(URL)
            }
        }
    }

    private fun parseCssColor(css: String): Int? {
        val regex = Regex("""rgba?\(\s*(\d+),\s*(\d+),\s*(\d+)""")
        val match = regex.find(css) ?: return null
        return Color.rgb(
            match.groupValues[1].toInt().coerceIn(0, 255),
            match.groupValues[2].toInt().coerceIn(0, 255),
            match.groupValues[3].toInt().coerceIn(0, 255)
        )
    }

    private fun isLightColor(color: Int): Boolean {
        return ColorUtils.calculateLuminance(color) > 0.5
    }

    private fun isTrustedHost(url: String?): Boolean {
        val host = if (url != null) Uri.parse(url).host else null
        return host == DOMAIN || host?.endsWith(".$DOMAIN") == true
    }

    private fun setupWebView() {
        webView.isNestedScrollingEnabled = false

        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            mediaPlaybackRequiresUserGesture = false
            cacheMode = WebSettings.LOAD_DEFAULT
            mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
            setSupportMultipleWindows(false)
            allowFileAccess = false
        }

        webView.addJavascriptInterface(object {
            @JavascriptInterface
            fun report(scrollY: Int) {
                contentScrollY = scrollY
            }
        }, "AndroidScroll")

        webView.addJavascriptInterface(object {
            @JavascriptInterface
            fun reportTheme(bgCss: String, fgCss: String) {
                if (!isTrustedHost(webView.url)) return

                val bgColor = parseCssColor(bgCss) ?: return
                val fgColor = parseCssColor(fgCss) ?: Color.WHITE

                runOnUiThread {
                    if (isActivityDestroyed) return@runOnUiThread
                    rootLayout.setBackgroundColor(bgColor)
                    webView.setBackgroundColor(bgColor)
                    loadingLayout.setBackgroundColor(bgColor)
                    loadingSpinner.indeterminateTintList = ColorStateList.valueOf(fgColor)
                    loadingText.setTextColor(fgColor)
                    swipeRefresh.setColorSchemeColors(fgColor)

                    window.statusBarColor = bgColor
                    window.navigationBarColor = bgColor
                    val controller = WindowInsetsControllerCompat(window, window.decorView)
                    val lightBg = isLightColor(bgColor)
                    controller.isAppearanceLightStatusBars = lightBg
                    controller.isAppearanceLightNavigationBars = lightBg

                    // Theme reported → page has rendered with CSS applied
                    webView.removeCallbacks(hideLoadingRunnable)
                    webView.removeCallbacks(progressTickRunnable)
                    loadingLayout.visibility = View.GONE
                    swipeRefresh.isRefreshing = false
                }

                getSharedPreferences("app_prefs", MODE_PRIVATE)
                    .edit()
                    .putInt("theme_color_bg", bgColor)
                    .putInt("theme_color_fg", fgColor)
                    .apply()
            }
        }, "AndroidTheme")

        webView.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                val host = request.url.host ?: return false
                if (host == DOMAIN || host.endsWith(".$DOMAIN")) {
                    return false
                }
                try {
                    startActivity(Intent(Intent.ACTION_VIEW, request.url))
                } catch (_: ActivityNotFoundException) {
                    Toast.makeText(this@MainActivity, "No app can handle this link", Toast.LENGTH_SHORT).show()
                }
                return true
            }

            override fun onPageStarted(view: WebView, url: String?, favicon: android.graphics.Bitmap?) {
                contentScrollY = 0
                loadingLayout.visibility = View.VISIBLE
                displayProgress = 0
                targetProgress = 0
                loadingText.text = "LOADING... 0%"
                hadError = false

                view.removeCallbacks(progressTickRunnable)
                view.removeCallbacks(hideLoadingRunnable)
                view.post(progressTickRunnable)
                view.postDelayed(hideLoadingRunnable, 10000)
            }

            override fun onPageFinished(view: WebView, url: String?) {
                if (isTrustedHost(url)) {
                    view.evaluateJavascript(THEME_OBSERVER_JS, null)
                    view.evaluateJavascript(SCROLL_OBSERVER_JS, null)
                    view.evaluateJavascript(CUSTOM_UI_MODIFIER_JS, null)
                }
                isInitialLoad = false

                // Replace 10s safety with a 3s fallback;
                // the theme observer will hide sooner when it reports.
                view.removeCallbacks(hideLoadingRunnable)
                view.postDelayed(hideLoadingRunnable, 3000)
            }

            override fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceError) {
                if (request.isForMainFrame) {
                    loadingLayout.visibility = View.GONE
                    hadError = true
                    val prefs = getSharedPreferences("app_prefs", MODE_PRIVATE)
                    val errBg = String.format("#%06X", 0xFFFFFF and prefs.getInt("theme_color_bg", Color.BLACK))
                    val errFg = String.format("#%06X", 0xFFFFFF and prefs.getInt("theme_color_fg", Color.WHITE))
                    view.loadDataWithBaseURL(
                        null,
                        """
                        <html><body style="display:flex;justify-content:center;align-items:center;height:100vh;margin:0;font-family:sans-serif;background:$errBg;color:$errFg;text-align:center">
                        <div>
                            <h2>No Connection</h2>
                            <p>Could not reach Cyberspace. Check your internet connection.</p>
                            <button onclick="location.href='$URL'" style="padding:12px 24px;font-size:16px;border:none;border-radius:8px;background:#6366f1;color:#fff;cursor:pointer">Retry</button>
                        </div>
                        </body></html>
                        """.trimIndent(),
                        "text/html",
                        "UTF-8",
                        null
                    )
                    swipeRefresh.isRefreshing = false
                }
            }
        }

        webView.webChromeClient = object : WebChromeClient() {
            override fun onShowFileChooser(
                webView: WebView,
                callback: ValueCallback<Array<Uri>>,
                params: FileChooserParams
            ): Boolean {
                fileUploadCallback?.onReceiveValue(null)
                fileUploadCallback = callback
                try {
                    fileChooserLauncher.launch(params.createIntent())
                } catch (e: Exception) {
                    fileUploadCallback?.onReceiveValue(null)
                    fileUploadCallback = null
                    return false
                }
                return true
            }

            override fun onProgressChanged(view: WebView, newProgress: Int) {
                targetProgress = newProgress * 90 / 100
            }
        }
    }

    private fun setupSwipeRefresh() {
        swipeRefresh.setOnChildScrollUpCallback { _, _ ->
            webView.canScrollVertically(-1) || contentScrollY > 0 || webView.scrollY > 0
        }
        swipeRefresh.setOnRefreshListener {
            webView.reload()
        }
    }

    private fun setupBackNavigation() {
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (webView.canGoBack()) {
                    webView.goBack()
                } else {
                    isEnabled = false
                    onBackPressedDispatcher.onBackPressed()
                }
            }
        })
    }

    override fun onResume() {
        super.onResume()
        webView.onResume()
        webView.resumeTimers()
    }

    override fun onPause() {
        super.onPause()
        webView.onPause()
        webView.pauseTimers()
    }

    override fun onDestroy() {
        isActivityDestroyed = true
        webView.removeCallbacks(hideLoadingRunnable)
        webView.removeCallbacks(progressTickRunnable)
        fileUploadCallback?.onReceiveValue(null)
        fileUploadCallback = null
        webView.destroy()
        super.onDestroy()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        webView.saveState(outState)
    }
}
