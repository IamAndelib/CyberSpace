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

        // Load saved theme colors
        val prefs = getSharedPreferences("app_prefs", MODE_PRIVATE)
        val savedBgColor = prefs.getInt("theme_color_bg", Color.BLACK)
        val savedFgColor = prefs.getInt("theme_color_fg", Color.WHITE)

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
        // Note: We don't change webView background here constantly to avoid flashes,
        // it's handled on creation.
        loadingLayout.setBackgroundColor(bgColor)

        window.statusBarColor = bgColor
        window.navigationBarColor = bgColor
        val insetsController = WindowInsetsControllerCompat(window, window.decorView)
        val light = ColorUtils.calculateLuminance(bgColor) > 0.5
        insetsController.isAppearanceLightStatusBars = light
        insetsController.isAppearanceLightNavigationBars = light
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
        
        // Pass the theme update responsibility to a passive interface if needed,
        // or just let the page load naturally. We removed the ACTIVE theme reporter.
        // If we want to detect theme changes *after* load, we can add a passive observer,
        // but for now let's keep it simple as requested.
        
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
                    view.evaluateJavascript(CUSTOM_UI_MODIFIER_JS, null)
                    view.evaluateJavascript(SCROLL_OBSERVER_JS, null)
                }

                // If this is the background WebView, wait for visual state to be ready, then swap
                if (view == backgroundWebView) {
                    view.postVisualStateCallback(0, object : WebView.VisualStateCallback() {
                        override fun onComplete(requestId: Long) {
                             runOnUiThread {
                                 swapWebView(view)
                             }
                        }
                    })
                } else if (view == webView) {
                    // Normal load on active view
                    view.removeCallbacks(hideLoadingRunnable)
                    hideLoadingRunnable.run()
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

    private fun setupSwipeRefresh() {
        swipeRefresh.setOnChildScrollUpCallback { _, _ ->
            // Use both native scroll check AND JS scroll reporting
            // contentScrollY > 10 allows a tiny bit of slop, but > 0 is stricter.
            // Let's use > 0 for strict "at top" requirement.
            webView.canScrollVertically(-1) || webView.scrollY > 0 || contentScrollY > 0
        }
        swipeRefresh.setOnRefreshListener {
            // Cancel any existing background refresh
            cancelBackgroundRefresh()
            
            // Create new background WebView
            val newWebView = WebView(this)
            backgroundWebView = newWebView
            
            newWebView.layoutParams = android.widget.FrameLayout.LayoutParams(
                android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                android.view.ViewGroup.LayoutParams.MATCH_PARENT
            )

            // Visible but behind current view (Index 0)
            newWebView.visibility = View.VISIBLE
            // Not focusable while loading
            newWebView.isFocusable = false
            newWebView.isFocusableInTouchMode = false
            
            val prefs = getSharedPreferences("app_prefs", MODE_PRIVATE)
            val savedBgColor = prefs.getInt("theme_color_bg", Color.BLACK)
            newWebView.setBackgroundColor(savedBgColor)

            webViewContainer.addView(newWebView, 0)
            setupWebView(newWebView)
            
            // Lifecycle requirements for JS
            newWebView.onResume() 
            newWebView.resumeTimers()
            
            newWebView.loadUrl(URL)
            
            // Timeout safety (15s)
            newWebView.postDelayed({
                if (backgroundWebView == newWebView && !isActivityDestroyed) {
                    cancelBackgroundRefresh()
                    Toast.makeText(this, "Refresh timed out", Toast.LENGTH_SHORT).show()
                }
            }, 15000)
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
