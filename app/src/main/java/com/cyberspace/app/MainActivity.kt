package com.cyberspace.app

import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
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

    private lateinit var webViewContainer: android.widget.FrameLayout
    private lateinit var webView: WebView
    private lateinit var swipeRefresh: WebViewSwipeRefreshLayout
    private lateinit var loadingLayout: View
    private lateinit var loadingSpinner: ProgressBar
    private lateinit var loadingText: android.widget.TextView
    private lateinit var rootLayout: View
    private var fileUploadCallback: ValueCallback<Array<Uri>>? = null
    private var isActivityDestroyed = false
    private var displayProgress = 0
    private var targetProgress = 0
    
    // Track the background WebView used for seamless refresh
    private var backgroundWebView: WebView? = null

    // Cached web font typeface
    private var cachedTypeface: Typeface? = null
    private var cachedFontName: String? = null

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
                function ws() { return window.scrollY || window.pageYOffset || 0; }
                window.addEventListener('scroll', function() { report(ws()); }, { passive: true });
                document.addEventListener('scroll', function(e) {
                    var t = e.target;
                    if (t && t !== document && t.scrollTop !== undefined && t.scrollTop > 0) {
                        report(t.scrollTop);
                    } else {
                        report(ws());
                    }
                }, { capture: true, passive: true });
                report(ws());
            })();
        """

        private const val THEME_OBSERVER_JS = """
            (function() {
                if (window.__themeObserverInstalled) return;
                window.__themeObserverInstalled = true;
                var lastKey = '';
                var cvs = document.createElement('canvas').getContext('2d');
                function toRgb(c) {
                    if (!c || c === 'transparent' || c === 'rgba(0, 0, 0, 0)') return null;
                    if (c.indexOf('rgb(') === 0) return c;
                    cvs.fillStyle = '#000000'; cvs.fillStyle = c; var v = cvs.fillStyle;
                    if (v.charAt(0) === '#') {
                        return 'rgb(' + parseInt(v.substr(1,2),16) + ', ' + parseInt(v.substr(3,2),16) + ', ' + parseInt(v.substr(5,2),16) + ')';
                    }
                    return v;
                }
                function findFontUrl(family) {
                    var name = family.split(',')[0].trim().replace(/["']/g, '');
                    if (!name) return '';
                    try {
                        for (var i = 0; i < document.styleSheets.length; i++) {
                            var rules; try { rules = document.styleSheets[i].cssRules; } catch(e) { continue; }
                            if (!rules) continue;
                            for (var j = 0; j < rules.length; j++) {
                                if (rules[j].type !== 5) continue;
                                var ff = rules[j].style.getPropertyValue('font-family').replace(/["']/g, '').trim();
                                if (ff.toLowerCase() !== name.toLowerCase()) continue;
                                var src = rules[j].style.getPropertyValue('src');
                                var urls = []; var re = /url\(["']?([^"')]+)["']?\)/g; var m;
                                while ((m = re.exec(src)) !== null) urls.push(m[1]);
                                var pick = urls.find(function(u){return /\.ttf/i.test(u)})
                                    || urls.find(function(u){return /\.woff(?!2)/i.test(u)})
                                    || urls[0];
                                if (pick) return new URL(pick, document.styleSheets[i].href || document.baseURI).href;
                            }
                        }
                    } catch(e) {}
                    return '';
                }
                function readTheme() {
                    var s = getComputedStyle(document.documentElement);
                    var bg = toRgb(s.getPropertyValue('--color-bg').trim());
                    var fg = toRgb(s.getPropertyValue('--color-fg').trim());
                    var font = s.getPropertyValue('--theme-font-mono').trim();
                    if (!bg) bg = toRgb(getComputedStyle(document.body).backgroundColor);
                    if (!fg) fg = toRgb(getComputedStyle(document.body).color);
                    return { bg: bg, fg: fg, font: font };
                }
                var debounce = null;
                function report() {
                    if (debounce) clearTimeout(debounce);
                    debounce = setTimeout(function() {
                        var t = readTheme();
                        var key = (t.bg||'') + '|' + (t.fg||'') + '|' + (t.font||'');
                        if (t.bg && key !== lastKey) {
                            lastKey = key;
                            var fontUrl = t.font ? findFontUrl(t.font) : '';
                            AndroidTheme.reportTheme(t.bg, t.fg || 'rgb(255, 255, 255)', t.font || '', fontUrl);
                        }
                    }, 50);
                }
                var obs = new MutationObserver(report);
                obs.observe(document.documentElement, { attributes: true });
                obs.observe(document.body, { attributes: true, childList: true });
                var nuxt = document.querySelector('#__nuxt');
                if (nuxt) obs.observe(nuxt, { attributes: true, childList: true, subtree: true });
                report();
                setTimeout(report, 300);
                setTimeout(report, 1000);
            })();
        """

        private const val CONTENT_READY_JS = """
            (function() {
                function check() {
                    var n = document.querySelector('#__nuxt');
                    if (n && n.children.length > 0) {
                        AndroidSwap.ready();
                    } else {
                        var obs = new MutationObserver(function(_, o) {
                            var n2 = document.querySelector('#__nuxt');
                            if (n2 && n2.children.length > 0) { o.disconnect(); AndroidSwap.ready(); }
                        });
                        if (n) {
                            obs.observe(n, { childList: true });
                        } else {
                            obs.observe(document.body, { childList: true, subtree: true });
                        }
                        setTimeout(function() { obs.disconnect(); AndroidSwap.ready(); }, 5000);
                    }
                }
                check();
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

        webViewContainer = findViewById(R.id.webViewContainer)
        swipeRefresh = findViewById(R.id.swipeRefresh)
        webView = findViewById(R.id.webView)
        loadingLayout = findViewById(R.id.loadingLayout)
        loadingSpinner = findViewById(R.id.loadingSpinner)
        loadingText = findViewById(R.id.loadingText)
        rootLayout = findViewById(R.id.rootLayout)

        // Load saved theme
        val prefs = getSharedPreferences("app_prefs", MODE_PRIVATE)
        val savedBgColor = prefs.getInt("theme_color_bg", Color.BLACK)
        val savedFgColor = prefs.getInt("theme_color_fg", Color.WHITE)

        // Restore cached font from disk
        val savedFontPath = prefs.getString("theme_font_path", null)
        if (savedFontPath != null) {
            try {
                val f = java.io.File(savedFontPath)
                if (f.exists()) {
                    cachedTypeface = Typeface.createFromFile(f)
                    cachedFontName = prefs.getString("theme_font_name", null)
                }
            } catch (_: Exception) {}
        }

        applyTheme(savedBgColor, savedFgColor)

        setupWebView(webView)
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

    private fun applyTheme(bgColor: Int, fgColor: Int) {
        rootLayout.setBackgroundColor(bgColor)
        loadingSpinner.indeterminateTintList = ColorStateList.valueOf(fgColor)
        loadingText.setTextColor(fgColor)
        swipeRefresh.setColorSchemeColors(fgColor)
        loadingLayout.setBackgroundColor(bgColor)

        // Apply cached web font if available, otherwise use generic fallback
        loadingText.typeface = cachedTypeface ?: Typeface.create("monospace", Typeface.NORMAL)

        window.statusBarColor = bgColor
        window.navigationBarColor = bgColor
        val insetsController = WindowInsetsControllerCompat(window, window.decorView)
        val light = ColorUtils.calculateLuminance(bgColor) > 0.5
        insetsController.isAppearanceLightStatusBars = light
        insetsController.isAppearanceLightNavigationBars = light
    }

    private fun parseCssColor(css: String): Int? {
        val trimmed = css.trim()
        // Handle hex colors (#RGB or #RRGGBB)
        if (trimmed.startsWith("#")) {
            val hex = trimmed.substring(1)
            return try {
                when (hex.length) {
                    3 -> Color.rgb(
                        Integer.parseInt("${hex[0]}${hex[0]}", 16),
                        Integer.parseInt("${hex[1]}${hex[1]}", 16),
                        Integer.parseInt("${hex[2]}${hex[2]}", 16)
                    )
                    else -> Color.parseColor(trimmed)
                }
            } catch (_: Exception) { null }
        }
        // Handle rgb()/rgba()
        val regex = Regex("""rgba?\(\s*(\d+),\s*(\d+),\s*(\d+)""")
        val match = regex.find(trimmed) ?: return null
        return Color.rgb(
            match.groupValues[1].toInt().coerceIn(0, 255),
            match.groupValues[2].toInt().coerceIn(0, 255),
            match.groupValues[3].toInt().coerceIn(0, 255)
        )
    }

    private fun isTrustedHost(url: String?): Boolean {
        val host = if (url != null) Uri.parse(url).host else null
        return host == DOMAIN || host?.endsWith(".$DOMAIN") == true
    }

    private fun setupWebView(targetWebView: WebView) {
        targetWebView.isNestedScrollingEnabled = false

        targetWebView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            mediaPlaybackRequiresUserGesture = false
            cacheMode = WebSettings.LOAD_DEFAULT
            mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
            setSupportMultipleWindows(false)
            allowFileAccess = false
        }
        
        // Add minimal scroll interface
        targetWebView.addJavascriptInterface(object : Any() {
            @JavascriptInterface
            fun report(scrollY: Int) {
                // Determine if we are at the top (0 or very close to it)
                if (targetWebView == webView) {
                    contentScrollY = scrollY
                }
            }
        }, "AndroidScroll")
        
        targetWebView.addJavascriptInterface(object : Any() {
            @JavascriptInterface
            fun reportTheme(bgCss: String, fgCss: String, fontFamily: String, fontUrl: String) {
                val bgColor = parseCssColor(bgCss) ?: return
                val fgColor = parseCssColor(fgCss) ?: Color.WHITE
                // Start font download in background (before UI thread work)
                if (fontFamily.isNotBlank() && fontUrl.isNotBlank()) {
                    loadFont(fontFamily, fontUrl)
                }
                val prefs = getSharedPreferences("app_prefs", MODE_PRIVATE)
                val prevBg = prefs.getInt("theme_color_bg", Color.BLACK)
                val themeChanged = bgColor != prevBg
                prefs.edit()
                    .putInt("theme_color_bg", bgColor)
                    .putInt("theme_color_fg", fgColor)
                    .apply()
                runOnUiThread {
                    if (isActivityDestroyed) return@runOnUiThread
                    applyTheme(bgColor, fgColor)
                    if (targetWebView == webView) {
                        webView.removeCallbacks(hideLoadingRunnable)
                        webView.removeCallbacks(progressTickRunnable)
                        loadingLayout.visibility = View.GONE
                        swipeRefresh.isRefreshing = false
                        // Auto-refresh on mid-browse theme change
                        if (themeChanged && backgroundWebView == null) {
                            triggerBackgroundRefresh()
                        }
                    }
                }
            }
        }, "AndroidTheme")

        // Content-readiness signal for background WebView swap
        targetWebView.addJavascriptInterface(object : Any() {
            @JavascriptInterface
            fun ready() {
                runOnUiThread {
                    if (isActivityDestroyed || targetWebView != backgroundWebView) return@runOnUiThread
                    // Content is in the DOM — now wait for compositor to paint it
                    targetWebView.postVisualStateCallback(0, object : WebView.VisualStateCallback() {
                        override fun onComplete(requestId: Long) {
                            runOnUiThread { swapWebView(targetWebView) }
                        }
                    })
                }
            }
        }, "AndroidSwap")

        targetWebView.webViewClient = object : WebViewClient() {
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
                // Show loader ONLY if we are navigating on the ACTIVE view.
                // Background refresh views do not trigger the main loader.
                if (view == webView) {
                    contentScrollY = 0
                    loadingLayout.visibility = View.VISIBLE
                    displayProgress = 0
                    targetProgress = 0
                    loadingText.text = "LOADING... 0%"
                    view.removeCallbacks(progressTickRunnable)
                    view.post(progressTickRunnable)
                }

                // Safety timeout for loading screen
                view.postDelayed({
                   if (!isActivityDestroyed && view == webView) {
                       hideLoadingRunnable.run()
                   }
                }, 10000)
            }

            override fun onPageFinished(view: WebView, url: String?) {
                if (isTrustedHost(url)) {
                    view.evaluateJavascript(THEME_OBSERVER_JS, null)
                    view.evaluateJavascript(SCROLL_OBSERVER_JS, null)
                    view.evaluateJavascript(CUSTOM_UI_MODIFIER_JS, null)
                }

                if (view == backgroundWebView) {
                    // Inject content-readiness observer: waits for #__nuxt to have
                    // children (SPA rendered), then signals AndroidSwap.ready()
                    // which chains postVisualStateCallback before swapping.
                    view.evaluateJavascript(CONTENT_READY_JS, null)
                } else if (view == webView) {
                    // Theme observer will hide the overlay when CSS is applied.
                    // Fallback: hide after 3s in case theme never reports.
                    view.removeCallbacks(hideLoadingRunnable)
                    view.postDelayed(hideLoadingRunnable, 3000)
                }
            }

            override fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceError) {
                if (request.isForMainFrame) {
                    if (view == backgroundWebView) {
                        // Background refresh failed
                        runOnUiThread {
                            cancelBackgroundRefresh()
                            Toast.makeText(this@MainActivity, "Refresh failed", Toast.LENGTH_SHORT).show()
                        }
                        return
                    }

                    loadingLayout.visibility = View.GONE
                    // Show error page on active view
                    val prefs = getSharedPreferences("app_prefs", MODE_PRIVATE)
                    val errBg = String.format("#%06X", 0xFFFFFF and prefs.getInt("theme_color_bg", Color.BLACK))
                    val errFg = String.format("#%06X", 0xFFFFFF and prefs.getInt("theme_color_fg", Color.WHITE))
                    view.loadDataWithBaseURL(null,
                        "<html><body style='background:$errBg;color:$errFg;display:flex;justify-content:center;align-items:center;height:100vh'><h2>Connection Error</h2></body></html>",
                        "text/html", "UTF-8", null)
                    swipeRefresh.isRefreshing = false
                }
            }
        }

        targetWebView.webChromeClient = object : WebChromeClient() {
            override fun onShowFileChooser(
                webView: WebView,
                callback: ValueCallback<Array<Uri>>,
                params: FileChooserParams
            ): Boolean {
                if (webView != this@MainActivity.webView) return false 

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
                if (view == webView) {
                    targetProgress = newProgress * 90 / 100
                }
            }
        }
    }
    
    private fun swapWebView(newWebView: WebView) {
        if (isActivityDestroyed || backgroundWebView != newWebView) return
        
        val oldWebView = webView
        webView = newWebView
        backgroundWebView = null
        
        // Prepare new view to take over
        webView.visibility = View.VISIBLE
        webView.isFocusable = true
        webView.isFocusableInTouchMode = true
        webView.requestFocus()
        
        // Remove old view
        webViewContainer.removeView(oldWebView)
        oldWebView.destroy()
        
        swipeRefresh.isRefreshing = false
        
        // Reset scroll tracking
        contentScrollY = 0
    }
    
    private fun cancelBackgroundRefresh() {
        val bgView = backgroundWebView ?: return
        backgroundWebView = null
        webViewContainer.removeView(bgView)
        bgView.destroy()
        swipeRefresh.isRefreshing = false
    }

    private fun loadFont(fontFamily: String, fontUrl: String) {
        val fontName = fontFamily.split(",").first().trim()
            .removeSurrounding("\"").removeSurrounding("'").trim()
        if (fontName.isBlank() || fontName == cachedFontName) return

        // Check disk cache first
        val cacheFile = java.io.File(cacheDir, "font_${fontName.replace(Regex("[^a-zA-Z0-9]"), "_")}")
        if (cacheFile.exists()) {
            try {
                val tf = Typeface.createFromFile(cacheFile)
                cachedTypeface = tf
                cachedFontName = fontName
                runOnUiThread {
                    if (!isActivityDestroyed) loadingText.typeface = tf
                }
                getSharedPreferences("app_prefs", MODE_PRIVATE).edit()
                    .putString("theme_font_path", cacheFile.absolutePath)
                    .putString("theme_font_name", fontName)
                    .apply()
                return
            } catch (_: Exception) { cacheFile.delete() }
        }

        // Download in background
        Thread {
            try {
                val conn = java.net.URL(fontUrl).openConnection() as java.net.HttpURLConnection
                conn.connectTimeout = 5000
                conn.readTimeout = 10000
                val rawData = conn.inputStream.use { it.readBytes() }

                // Convert WOFF to OTF if needed (WOFF signature: 'wOFF' = 0x774F4646)
                val fontData = if (rawData.size > 4 &&
                    rawData[0] == 0x77.toByte() && rawData[1] == 0x4F.toByte() &&
                    rawData[2] == 0x46.toByte() && rawData[3] == 0x46.toByte()) {
                    convertWoffToOtf(rawData) ?: return@Thread
                } else {
                    rawData
                }

                cacheFile.writeBytes(fontData)
                val tf = Typeface.createFromFile(cacheFile)
                cachedTypeface = tf
                cachedFontName = fontName
                runOnUiThread {
                    if (!isActivityDestroyed) loadingText.typeface = tf
                }
                getSharedPreferences("app_prefs", MODE_PRIVATE).edit()
                    .putString("theme_font_path", cacheFile.absolutePath)
                    .putString("theme_font_name", fontName)
                    .apply()
            } catch (_: Exception) {
                cacheFile.delete()
            }
        }.start()
    }

    private fun convertWoffToOtf(woff: ByteArray): ByteArray? {
        try {
            if (woff.size < 44) return null
            val buf = java.nio.ByteBuffer.wrap(woff).order(java.nio.ByteOrder.BIG_ENDIAN)
            if (buf.int != 0x774F4646) return null
            val flavor = buf.int
            buf.int // length
            val numTables = buf.short.toInt() and 0xFFFF
            buf.short // reserved
            val totalSfntSize = buf.int
            if (totalSfntSize > 10 * 1024 * 1024) return null
            buf.position(44)

            data class T(val tag: Int, val off: Int, val cLen: Int, val oLen: Int, val csum: Int)
            val tables = (0 until numTables).map { T(buf.int, buf.int, buf.int, buf.int, buf.int) }

            val otf = java.nio.ByteBuffer.allocate(totalSfntSize).order(java.nio.ByteOrder.BIG_ENDIAN)
            otf.putInt(flavor)
            otf.putShort(numTables.toShort())
            var p = 1; var es = 0
            while (p * 2 <= numTables) { p *= 2; es++ }
            val sr = p * 16
            otf.putShort(sr.toShort())
            otf.putShort(es.toShort())
            otf.putShort((numTables * 16 - sr).toShort())

            var dataOff = 12 + numTables * 16
            val entries = tables.map { t ->
                val data = if (t.cLen < t.oLen) {
                    val inf = java.util.zip.Inflater()
                    inf.setInput(woff, t.off, t.cLen)
                    val result = ByteArray(t.oLen)
                    inf.inflate(result)
                    inf.end()
                    result
                } else {
                    woff.copyOfRange(t.off, t.off + t.oLen)
                }
                otf.putInt(t.tag); otf.putInt(t.csum); otf.putInt(dataOff); otf.putInt(t.oLen)
                val pair = Pair(dataOff, data)
                dataOff += (t.oLen + 3) and 3.inv()
                pair
            }

            for ((off, data) in entries) {
                if (off + data.size > totalSfntSize) return null
                otf.position(off)
                otf.put(data)
            }
            return otf.array()
        } catch (_: Exception) {
            return null
        }
    }

    private fun triggerBackgroundRefresh() {
        cancelBackgroundRefresh()

        val newWebView = WebView(this)
        backgroundWebView = newWebView

        newWebView.layoutParams = android.widget.FrameLayout.LayoutParams(
            android.view.ViewGroup.LayoutParams.MATCH_PARENT,
            android.view.ViewGroup.LayoutParams.MATCH_PARENT
        )
        newWebView.visibility = View.VISIBLE
        newWebView.isFocusable = false
        newWebView.isFocusableInTouchMode = false

        val prefs = getSharedPreferences("app_prefs", MODE_PRIVATE)
        newWebView.setBackgroundColor(prefs.getInt("theme_color_bg", Color.BLACK))

        webViewContainer.addView(newWebView, 0)
        setupWebView(newWebView)

        newWebView.onResume()
        newWebView.resumeTimers()
        newWebView.loadUrl(webView.url ?: URL)

        newWebView.postDelayed({
            if (backgroundWebView == newWebView && !isActivityDestroyed) {
                cancelBackgroundRefresh()
                Toast.makeText(this, "Refresh timed out", Toast.LENGTH_SHORT).show()
            }
        }, 15000)
    }

    private fun setupSwipeRefresh() {
        swipeRefresh.setOnChildScrollUpCallback { _, _ ->
            webView.canScrollVertically(-1) || webView.scrollY > 0 || contentScrollY > 0
        }
        swipeRefresh.setOnRefreshListener {
            swipeRefresh.isRefreshing = true
            triggerBackgroundRefresh()
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
        backgroundWebView?.onResume() 
        backgroundWebView?.resumeTimers()
    }

    override fun onPause() {
        super.onPause()
        webView.onPause()
        webView.pauseTimers()
        backgroundWebView?.onPause()
        backgroundWebView?.pauseTimers()
    }

    override fun onDestroy() {
        isActivityDestroyed = true
        webView.removeCallbacks(hideLoadingRunnable)
        webView.removeCallbacks(progressTickRunnable)
        fileUploadCallback?.onReceiveValue(null)
        fileUploadCallback = null
        webView.destroy()
        backgroundWebView?.destroy()
        backgroundWebView = null
        super.onDestroy()
    }
    
    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        webView.saveState(outState)
    }
}
