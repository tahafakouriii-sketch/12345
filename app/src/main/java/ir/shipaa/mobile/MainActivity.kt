package ir.shipaa.mobile

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.webkit.WebResourceError
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast

/**
 * Shipaa 0.3: Mobile-first secure website shell.
 * Course authorization remains entirely server-side on shipaa.ir.
 * This app never fabricates entitlements, lesson URLs or progress sync.
 */
class MainActivity : Activity() {
    private val origin = "https://shipaa.ir"
    private val home = "$origin/my-account/"
    private lateinit var browser: WebView
    private lateinit var browserFrame: FrameLayout
    private lateinit var header: LinearLayout
    private lateinit var bottomBar: LinearLayout
    private lateinit var progress: ProgressBar
    private lateinit var errorOverlay: LinearLayout
    private var fullscreenView: View? = null
    private var fullscreenCallback: WebChromeClient.CustomViewCallback? = null

    private val tabs = listOf(
        Triple("⌂", "خانه من", home),
        Triple("▦", "دوره‌ها", "$origin/shop/"),
        Triple("◉", "سایت", "$origin/"),
        Triple("♙", "حساب", home)
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = Color.rgb(247, 251, 248)
        window.navigationBarColor = Color.WHITE
        @Suppress("DEPRECATION")
        window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR or View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutDirection = View.LAYOUT_DIRECTION_RTL
            setBackgroundColor(Color.rgb(248, 251, 249))
        }
        header = LinearLayout(this).apply {
            gravity = Gravity.CENTER_VERTICAL
            orientation = LinearLayout.HORIZONTAL
            setPadding(dp(18), 0, dp(18), 0)
            setBackgroundColor(Color.WHITE)
        }
        val brand = TextView(this).apply {
            text = "شیپا  •  خانه یادگیری"
            textSize = 17f
            setTextColor(Color.rgb(55, 145, 66))
            gravity = Gravity.CENTER_VERTICAL or Gravity.RIGHT
        }
        header.addView(brand, LinearLayout.LayoutParams(0, -1, 1f))
        val reload = TextView(this).apply {
            text = "↻"
            textSize = 25f
            setTextColor(Color.rgb(69, 120, 90))
            gravity = Gravity.CENTER
            contentDescription = "بازنشانی صفحه"
            setOnClickListener { browser.reload() }
        }
        header.addView(reload, LinearLayout.LayoutParams(dp(48), dp(48)))
        root.addView(header, LinearLayout.LayoutParams(-1, dp(54)))

        progress = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
            max = 100
            visibility = View.GONE
            progressTintList = android.content.res.ColorStateList.valueOf(Color.rgb(108, 192, 78))
        }
        root.addView(progress, LinearLayout.LayoutParams(-1, dp(3)))

        browserFrame = FrameLayout(this)
        browser = WebView(this).apply {
            settings.javaScriptEnabled = true // Required by the existing WordPress dashboard.
            settings.domStorageEnabled = true
            settings.allowFileAccess = false
            settings.allowContentAccess = false
            settings.javaScriptCanOpenWindowsAutomatically = false
            settings.setSupportMultipleWindows(false)
            settings.mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
            settings.cacheMode = WebSettings.LOAD_DEFAULT
            CookieManager.getInstance().setAcceptCookie(true)
            CookieManager.getInstance().setAcceptThirdPartyCookies(this, false)
            webViewClient = object : WebViewClient() {
                override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                    if (!request.isForMainFrame) return false
                    val u = request.url
                    if (u.scheme == "https" && (u.host == "shipaa.ir" || u.host == "www.shipaa.ir")) return false
                    if (u.scheme == "http" || u.scheme == "https" || u.scheme == "mailto" || u.scheme == "tel" || u.scheme == "eitaa") return openExternal(u)
                    return true
                }
                override fun onPageStarted(view: WebView, url: String?, favicon: android.graphics.Bitmap?) {
                    super.onPageStarted(view, url, favicon)
                    errorOverlay.visibility = View.GONE
                }
                override fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceError) {
                    if (request.isForMainFrame) errorOverlay.visibility = View.VISIBLE
                }
                override fun onPageFinished(view: WebView, url: String?) {
                    super.onPageFinished(view, url)
                    CookieManager.getInstance().flush()
                }
            }
            webChromeClient = object : WebChromeClient() {
                override fun onProgressChanged(view: WebView, newProgress: Int) {
                    progress.progress = newProgress
                    progress.visibility = if (newProgress >= 100 || fullscreenView != null) View.GONE else View.VISIBLE
                }
                override fun onShowCustomView(view: View, callback: CustomViewCallback) {
                    if (fullscreenView != null) { callback.onCustomViewHidden(); return }
                    fullscreenView = view
                    fullscreenCallback = callback
                    browserFrame.addView(view, FrameLayout.LayoutParams(-1, -1))
                    browser.visibility = View.GONE
                    header.visibility = View.GONE
                    bottomBar.visibility = View.GONE
                    progress.visibility = View.GONE
                    errorOverlay.visibility = View.GONE
                }
                override fun onHideCustomView() { exitFullscreen() }
            }
        }
        browserFrame.addView(browser, FrameLayout.LayoutParams(-1, -1))
        errorOverlay = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(dp(30), dp(30), dp(30), dp(30))
            setBackgroundColor(Color.rgb(248, 251, 249))
            visibility = View.GONE
        }
        val message = TextView(this).apply {
            text = "اتصال برقرار نیست\nاینترنت را بررسی کن و دوباره تلاش کن."
            textSize = 16f
            gravity = Gravity.CENTER
            setTextColor(Color.rgb(61, 80, 67))
        }
        errorOverlay.addView(message)
        val retry = TextView(this).apply {
            text = "تلاش دوباره"
            textSize = 15f
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            setBackgroundColor(Color.rgb(74, 161, 72))
            setPadding(dp(26), dp(12), dp(26), dp(12))
            setOnClickListener { errorOverlay.visibility = View.GONE; browser.reload() }
        }
        val retryLayout = LinearLayout.LayoutParams(-2, -2).apply { topMargin = dp(20) }
        errorOverlay.addView(retry, retryLayout)
        browserFrame.addView(errorOverlay, FrameLayout.LayoutParams(-1, -1))
        root.addView(browserFrame, LinearLayout.LayoutParams(-1, 0, 1f))

        bottomBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setBackgroundColor(Color.WHITE)
            layoutDirection = View.LAYOUT_DIRECTION_RTL
            elevation = dp(6).toFloat()
        }
        tabs.forEach { (icon, title, link) ->
            val tab = TextView(this).apply {
                text = "$icon\n$title"
                textSize = 12f
                gravity = Gravity.CENTER
                setTextColor(Color.rgb(63, 119, 75))
                setOnClickListener { browser.loadUrl(link) }
            }
            bottomBar.addView(tab, LinearLayout.LayoutParams(0, dp(62), 1f))
        }
        root.addView(bottomBar)
        setContentView(root)
        if (savedInstanceState != null) browser.restoreState(savedInstanceState) else browser.loadUrl(home)
    }

    private fun exitFullscreen() {
        fullscreenView?.let { browserFrame.removeView(it) }
        fullscreenView = null
        fullscreenCallback?.onCustomViewHidden()
        fullscreenCallback = null
        browser.visibility = View.VISIBLE
        header.visibility = View.VISIBLE
        bottomBar.visibility = View.VISIBLE
    }

    private fun openExternal(uri: Uri): Boolean {
        return try {
            startActivity(Intent(Intent.ACTION_VIEW, uri))
            true
        } catch (_: Exception) {
            Toast.makeText(this, "امکان باز کردن پیوند وجود ندارد", Toast.LENGTH_SHORT).show()
            true
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        browser.saveState(outState)
        super.onSaveInstanceState(outState)
    }
    private fun dp(n: Int) = (n * resources.displayMetrics.density).toInt()
    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (fullscreenView != null) exitFullscreen()
        else if (browser.canGoBack()) browser.goBack()
        else super.onBackPressed()
    }
    override fun onDestroy() {
        browser.destroy()
        super.onDestroy()
    }
}
