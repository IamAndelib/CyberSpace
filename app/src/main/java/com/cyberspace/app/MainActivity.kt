package com.cyberspace.app

import android.content.ActivityNotFoundException
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.webkit.JavascriptInterface
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat

class MainActivity : AppCompatActivity() {

    private lateinit var webView: WebView
    private lateinit var swipeRefresh: WebViewSwipeRefreshLayout
    private var fileUploadCallback: ValueCallback<Array<Uri>>? = null
    private var hadError = false

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
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        setContentView(R.layout.activity_main)

        swipeRefresh = findViewById(R.id.swipeRefresh)
        webView = findViewById(R.id.webView)

        setupWebView()
        setupSwipeRefresh()
        setupBackNavigation()

        if (savedInstanceState != null) {
            webView.restoreState(savedInstanceState)
        } else {
            webView.loadUrl(URL)
        }
    }

    private fun setupWebView() {
        webView.isNestedScrollingEnabled = false
        webView.setBackgroundColor(Color.BLACK)

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

        webView.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                val host = request.url.host ?: return false
                if (host == DOMAIN || host.endsWith(".$DOMAIN")) {
                    return false
                }
                try {
                    startActivity(Intent(Intent.ACTION_VIEW, request.url))
                } catch (_: ActivityNotFoundException) {
                    // No app can handle this URL
                }
                return true
            }

            override fun onPageFinished(view: WebView, url: String?) {
                swipeRefresh.isRefreshing = false
                if (hadError && url != null && url.contains(DOMAIN)) {
                    hadError = false
                    view.clearHistory()
                }
                view.evaluateJavascript(SCROLL_OBSERVER_JS, null)
            }

            override fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceError) {
                if (request.isForMainFrame) {
                    hadError = true
                    view.loadDataWithBaseURL(
                        null,
                        """
                        <html><body style="display:flex;justify-content:center;align-items:center;height:100vh;margin:0;font-family:sans-serif;background:#111;color:#fff;text-align:center">
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
        }
    }

    private fun setupSwipeRefresh() {
        swipeRefresh.setOnChildScrollUpCallback { _, _ ->
            webView.canScrollVertically(-1) || contentScrollY > 0
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

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        webView.saveState(outState)
    }
}
