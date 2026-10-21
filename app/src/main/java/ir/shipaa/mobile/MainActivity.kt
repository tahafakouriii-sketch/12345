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
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast

/** Pilot 0.4: one Android navigation surface at a time. WordPress remains the authority for access. */
class MainActivity : Activity() {
    private val site = "https://shipaa.ir"
    private lateinit var root: LinearLayout
    private lateinit var landing: LinearLayout
    private lateinit var browser: WebView
    private lateinit var holder: FrameLayout
    private lateinit var toolbar: LinearLayout
    private lateinit var back: TextView
    private lateinit var error: TextView
    private var full: View? = null
    private var fullCallback: WebChromeClient.CustomViewCallback? = null
    private var pageIsOpen = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = Color.rgb(248, 251, 249)
        window.navigationBarColor = Color.WHITE
        root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutDirection = View.LAYOUT_DIRECTION_RTL
            setBackgroundColor(Color.rgb(248, 251, 249))
        }
        toolbar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setBackgroundColor(Color.WHITE)
            setPadding(dp(12), 0, dp(12), 0)
        }
        val heading = TextView(this).apply {
            text = "شیپا | خانه یادگیری"
            textSize = 18f
            setTextColor(Color.rgb(57, 149, 66))
            gravity = Gravity.CENTER_VERTICAL
        }
        toolbar.addView(heading, LinearLayout.LayoutParams(0, dp(52), 1f))
        back = TextView(this).apply {
            text = "بازگشت"
            textSize = 14f
            gravity = Gravity.CENTER
            setPadding(dp(12), 0, dp(12), 0)
            setTextColor(Color.rgb(56, 112, 75))
            visibility = View.GONE
            setOnClickListener { showHome() }
        }
        toolbar.addView(back)
        root.addView(toolbar)

        landing = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(22), dp(28), dp(22), dp(16))
        }
        landing.addView(text("یادگیری با شیپا", 26f, Color.rgb(47, 113, 60)))
        landing.addView(text("از اینجا به دوره‌های مجاز، حساب کاربری و آموزش‌ها دسترسی داری.", 15f, Color.DKGRAY))
        landing.addView(text("آزمایش نخست: اقتصاد به زبان ساده • دوره ۱۷۴", 16f, Color.rgb(67, 131, 77)))
        landing.addView(action("ورود به خانه و دوره‌های من") { open("$site/my-account/") })
        landing.addView(action("بررسی دوره ۱۷۴") { open("$site/?shipaa_learning_app=1&course_id=174") })
        landing.addView(action("مشاهده همه دوره‌ها") { open("$site/shop/") })
        landing.addView(text("پخش جلسات پولی فقط از مسیر مجاز سایت انجام می‌شود. اتصال پلیر اختصاصی و همگام‌سازی پیشرفت در نسخه آزمایشی بعدی تکمیل می‌شود.", 13f, Color.GRAY))
        root.addView(landing, LinearLayout.LayoutParams(-1, 0, 1f))

        holder = FrameLayout(this)
        browser = WebView(this).apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.allowFileAccess = false
            settings.allowContentAccess = false
            settings.javaScriptCanOpenWindowsAutomatically = false
            settings.setSupportMultipleWindows(false)
            settings.mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
            CookieManager.getInstance().setAcceptCookie(true)
            CookieManager.getInstance().setAcceptThirdPartyCookies(this, false)
            webViewClient = object : WebViewClient() {
                override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                    if (!request.isForMainFrame) return false
                    val u = request.url
                    if (u.scheme == "https" && (u.host == "shipaa.ir" || u.host == "www.shipaa.ir")) return false
                    if (u.scheme in listOf("https", "http", "mailto", "tel", "eitaa")) return external(u)
                    return true
                }
                override fun onPageStarted(view: WebView, url: String?, favicon: android.graphics.Bitmap?) {
                    error.visibility = View.GONE
                }
                override fun onReceivedError(view: WebView, request: WebResourceRequest, reason: WebResourceError) {
                    if (request.isForMainFrame) error.visibility = View.VISIBLE
                }
                override fun onPageFinished(view: WebView, url: String?) {
                    CookieManager.getInstance().flush()
                }
            }
            webChromeClient = object : WebChromeClient() {
                override fun onShowCustomView(view: View, callback: CustomViewCallback) {
                    if (full != null) { callback.onCustomViewHidden(); return }
                    full = view
                    fullCallback = callback
                    holder.addView(view, FrameLayout.LayoutParams(-1, -1))
                    browser.visibility = View.GONE
                    toolbar.visibility = View.GONE
                }
                override fun onHideCustomView() { closeFullscreen() }
            }
        }
        holder.addView(browser, FrameLayout.LayoutParams(-1, -1))
        error = text("اتصال برقرار نیست. اینترنت را بررسی کن و به خانه برگرد.", 15f, Color.DKGRAY).apply {
            gravity = Gravity.CENTER
            setBackgroundColor(Color.rgb(248, 251, 249))
            visibility = View.GONE
        }
        holder.addView(error, FrameLayout.LayoutParams(-1, -1))
        root.addView(holder, LinearLayout.LayoutParams(-1, 0, 1f))
        holder.visibility = View.GONE
        setContentView(root)
        // Deliberately no second Android bottom menu: WordPress's own navigation remains available inside its pages.
    }

    private fun text(value: String, size: Float, color: Int) = TextView(this).apply {
        text = value; textSize = size; setTextColor(color)
        setPadding(dp(4), dp(12), dp(4), dp(12))
    }
    private fun action(label: String, click: () -> Unit) = TextView(this).apply {
        text = label; textSize = 16f; gravity = Gravity.CENTER
        setTextColor(Color.WHITE); setBackgroundColor(Color.rgb(77, 169, 72))
        val p = LinearLayout.LayoutParams(-1, dp(54))
        p.topMargin = dp(12); layoutParams = p
        setOnClickListener { click() }
    }
    private fun open(url: String) {
        pageIsOpen = true
        landing.visibility = View.GONE
        holder.visibility = View.VISIBLE
        back.visibility = View.VISIBLE
        browser.loadUrl(url)
    }
    private fun showHome() {
        pageIsOpen = false
        holder.visibility = View.GONE
        landing.visibility = View.VISIBLE
        back.visibility = View.GONE
    }
    private fun closeFullscreen() {
        full?.let { holder.removeView(it) }
        full = null
        fullCallback?.onCustomViewHidden()
        fullCallback = null
        browser.visibility = View.VISIBLE
        toolbar.visibility = View.VISIBLE
    }
    private fun external(u: Uri) = try {
        startActivity(Intent(Intent.ACTION_VIEW, u)); true
    } catch (_: Exception) {
        Toast.makeText(this, "امکان باز کردن پیوند نیست", Toast.LENGTH_SHORT).show()
        true
    }
    private fun dp(n: Int) = (n * resources.displayMetrics.density).toInt()
    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        when {
            full != null -> closeFullscreen()
            pageIsOpen && browser.canGoBack() -> browser.goBack()
            pageIsOpen -> showHome()
            else -> super.onBackPressed()
        }
    }
    override fun onDestroy() { browser.destroy(); super.onDestroy() }
}
