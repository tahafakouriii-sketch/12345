package ir.shipaa.mobile

import android.app.Activity
import android.content.Intent
import android.content.pm.ActivityInfo
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.webkit.*
import android.widget.*
import androidx.media3.common.*
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import org.json.JSONObject
import java.util.concurrent.Executors

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
class MainActivity : Activity() {
    private val green = Color.rgb(91, 174, 59)
    private val ink = Color.rgb(37, 58, 47)
    private val pale = Color.rgb(246, 249, 245)
    private val main = Handler(Looper.getMainLooper())
    private val worker = Executors.newSingleThreadExecutor()
    private val api = LearningApi()
    private val prefs by lazy { getSharedPreferences("learning_progress_v1", MODE_PRIVATE) }
    private lateinit var root: LinearLayout
    private lateinit var title: TextView
    private lateinit var content: FrameLayout
    private lateinit var native: LinearLayout
    private lateinit var nav: LinearLayout
    private lateinit var browser: WebView
    private lateinit var playerView: PlayerView
    private lateinit var info: TextView
    private lateinit var status: TextView
    private lateinit var list: LinearLayout
    private var player: ExoPlayer? = null
    private var session: LearningApi.Session? = null
    private var lessons = emptyList<JSONObject>()
    private var activeId: String? = null
    private var avatar: android.graphics.Bitmap? = null
    private var avatarUser = 0L
    private var avatarLoading = false
    private var tab = "یادگیری من"
    private var web = false
    private var fullscreen = false
    private var generation = 0
    private var busy = false
    private var stopped = false
    private var htmlFull: View? = null
    private var htmlCallback: WebChromeClient.CustomViewCallback? = null
    private val tick = object : Runnable {
        override fun run() {
            if (!stopped && !web && player != null) saveProgress()
            main.postDelayed(this, 15000)
        }
    }
    private val verify = object : Runnable {
        override fun run() {
            if (!stopped && !web && session != null && !busy) refresh(false)
            main.postDelayed(this, 60000)
        }
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = pale; window.navigationBarColor = Color.WHITE
        root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; layoutDirection = View.LAYOUT_DIRECTION_RTL
            setBackgroundColor(pale)
            setOnApplyWindowInsetsListener { v, insets ->
                v.setPadding(insets.systemWindowInsetLeft, insets.systemWindowInsetTop,
                    insets.systemWindowInsetRight, insets.systemWindowInsetBottom); insets
            }
        }
        title = label("شیپا  ·  خانه یادگیری", 21f).apply { setTextColor(green) }
        root.addView(title)
        content = FrameLayout(this)
        root.addView(content, LinearLayout.LayoutParams(-1, 0, 1f))
        native = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        content.addView(native, FrameLayout.LayoutParams(-1, -1))
        playerView = PlayerView(this).apply {
            setShowBuffering(PlayerView.SHOW_BUFFERING_ALWAYS)
            setShowNextButton(false); setShowPreviousButton(false)
            setFullscreenButtonClickListener { setFull(it) }
            visibility = View.GONE
        }
        native.addView(playerView, LinearLayout.LayoutParams(-1, dp(220)))
        info = label("", 16f); native.addView(info)
        status = label("برای دریافت دوره، وارد حساب شیپا شو.", 13f); native.addView(status)
        val scroll = ScrollView(this)
        list = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(16), 0, dp(16), dp(20)) }
        scroll.addView(list); native.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        nav = LinearLayout(this).apply { setBackgroundColor(Color.WHITE) }
        for (name in listOf("یادگیری من", "جلسات", "پیشرفت", "حساب من")) {
            nav.addView(label(name, 12f).apply {
                gravity = Gravity.CENTER; setOnClickListener { tab = name; render() }
            }, LinearLayout.LayoutParams(0, dp(60), 1f))
        }
        root.addView(nav)
        browser = WebView(this).apply {
            visibility = View.GONE
            settings.javaScriptEnabled = true; settings.domStorageEnabled = true
            settings.allowFileAccess = false; settings.allowContentAccess = false
            settings.mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
            settings.javaScriptCanOpenWindowsAutomatically = false
            CookieManager.getInstance().setAcceptCookie(true)
            CookieManager.getInstance().setAcceptThirdPartyCookies(this, false)
            webViewClient = object : WebViewClient() {
                override fun shouldOverrideUrlLoading(v: WebView, r: WebResourceRequest): Boolean {
                    if (!r.isForMainFrame) return false
                    val u = r.url
                    if (trusted(u)) {
                        if (u.getQueryParameter("shipaa_learning_app") == "1" && u.getQueryParameter("course_id") == "174") {
                            home(); return true
                        }
                        return false
                    }
                    if (u.scheme in listOf("https", "http", "tel", "mailto", "eitaa")) {
                        try { startActivity(Intent(Intent.ACTION_VIEW, u)) } catch (_: Exception) { }
                    }
                    return true
                }
                override fun onPageFinished(v: WebView, url: String?) { CookieManager.getInstance().flush() }
                override fun onReceivedError(v: WebView, r: WebResourceRequest, e: WebResourceError) {
                    if (r.isForMainFrame) Toast.makeText(this@MainActivity, "اتصال برقرار نشد؛ برای تلاش دوباره به یادگیری برگرد.", Toast.LENGTH_LONG).show()
                }
            }
            webChromeClient = object : WebChromeClient() {
                override fun onShowCustomView(v: View, cb: CustomViewCallback) {
                    if (htmlFull != null) { cb.onCustomViewHidden(); return }
                    htmlFull = v; htmlCallback = cb
                    content.addView(v, FrameLayout.LayoutParams(-1, -1)); browser.visibility = View.GONE
                }
                override fun onHideCustomView() { closeHtml() }
            }
        }
        content.addView(browser, FrameLayout.LayoutParams(-1, -1))
        setContentView(root); render(); refresh(true)
        main.postDelayed(tick, 15000); main.postDelayed(verify, 60000)
    }
    private fun trusted(u: Uri) = u.scheme == "https" && u.host in listOf("shipaa.ir", "www.shipaa.ir") && (u.port == -1 || u.port == 443)
    private fun label(s: String, size: Float = 16f) = TextView(this).apply {
        text = s; textSize = size; setTextColor(ink)
        setPadding(dp(16), dp(10), dp(16), dp(10))
    }
    private fun button(s: String, color: Int = green, action: () -> Unit) {
        list.addView(label(s).apply {
            setTextColor(if (color == green) Color.WHITE else ink)
            background = GradientDrawable().apply { setColor(color); cornerRadius = dp(18).toFloat() }
            layoutParams = LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(10); bottomMargin = dp(4) }
            setPadding(dp(18), dp(18), dp(18), dp(18)); setOnClickListener { action() }
        })
    }
    private fun key(id: String) = "${session?.user?.optLong("id") ?: 0}:174:$id"
    private fun seconds(l: JSONObject): Long = if (prefs.getBoolean(key(l.getString("id"))+":pending", false))
        prefs.getLong(key(l.getString("id"))+":seconds", 0) else l.optJSONObject("progress")?.optLong("seconds") ?: 0
    private fun done(l: JSONObject) = (l.optJSONObject("progress")?.optBoolean("completed") ?: false) || prefs.getBoolean(key(l.getString("id"))+":done", false)
    private fun render() {
        if (web) return
        list.removeAllViews()
        for (i in 0 until nav.childCount) (nav.getChildAt(i) as TextView).setTextColor(if ((nav.getChildAt(i) as TextView).text == tab) green else ink)
        val s = session
        if (s == null) {
            info.text = "یادگیری با شیپا"
            list.addView(label("دوره آزمایشی: اقتصاد به زبان ساده", 22f))
            button("ورود به حساب شیپا") { openWeb("https://shipaa.ir/my-account/") }
            button("وارد شده‌ام؛ دریافت دوره", Color.rgb(230, 241, 255)) { refresh(true) }
            button("رفتن به سایت", Color.rgb(239, 232, 250)) { openWeb("https://shipaa.ir/") }
            return
        }
        when(tab) {
            "حساب من" -> {
                showAvatar(s.user)
                list.addView(label("سلام ${s.user.optString("name")} 🌱", 24f))
                list.addView(label("دوره‌ها و پیشرفت به همین حساب متصل‌اند."))
                button("حساب و تصویر پروفایل") { openWeb("https://shipaa.ir/my-account/") }
                button("سایت شیپا", Color.rgb(230, 241, 255)) { openWeb("https://shipaa.ir/") }
                button("تازه‌سازی دسترسی") { refresh(true) }
            }
            "پیشرفت" -> {
                val completed = lessons.count { done(it) }
                list.addView(label("$completed از ${lessons.size} جلسه تکمیل شده", 22f))
                list.addView(label("پیشرفت دوره بر پایه جلسات تکمیل‌شده: ${if(lessons.isEmpty()) 0 else completed*100/lessons.size}٪"))
                lessons.forEach { l ->
                    val duration = prefs.getLong(key(l.getString("id"))+":duration", 0)
                    val value = if(done(l)) "تکمیل شده ✓" else if(duration > 0) "${(seconds(l)*100/duration).coerceIn(0,100)}٪" else "${seconds(l)/60} دقیقه · مدت هنوز مشخص نیست"
                    button("${l.getString("title")}\n$value", Color.rgb(239, 232, 250)) { play(l.getString("id")) }
                }
            }
            else -> {
                if (tab == "یادگیری من") {
                    showAvatar(s.user)
                    list.addView(label("سلام ${s.user.optString("name")} 🌱", 22f))
                    list.addView(label(s.course.getString("title"), 24f))
                    val last = prefs.getString("${s.user.optLong("id")}:last", null)
                    val next = lessons.firstOrNull { it.optString("id") == last && !done(it) } ?: lessons.firstOrNull { !done(it) } ?: lessons.firstOrNull()
                    if(next != null) button("ادامه یادگیری  ←\n${next.getString("title")}") { play(next.getString("id")) }
                }
                lessons.forEachIndexed { i, l ->
                    button("${i+1}. ${l.getString("title")} ${if(done(l)) "✓" else ""}\n${l.optString("summary")}", Color.rgb(230, 241, 255)) { play(l.getString("id")) }
                }
                if (lessons.isEmpty()) list.addView(label("هنوز جلسه‌ای برای این دوره ثبت نشده است."))
                button("تازه‌سازی", Color.rgb(239, 232, 250)) { refresh(true) }
            }
        }
        list.alpha = 0.5f; list.animate().alpha(1f).setDuration(180).start()
    }
    private fun showAvatar(user: JSONObject) {
        val uid = user.getLong("id")
        if (avatarUser != uid) { avatar = null; avatarUser = uid }
        val view = ImageView(this).apply {
            contentDescription = "تصویر حساب"
            scaleType = ImageView.ScaleType.CENTER_CROP
            background = GradientDrawable().apply { setColor(Color.rgb(239,232,250)); cornerRadius = dp(32).toFloat() }
            clipToOutline = true
            if(avatar != null) setImageBitmap(avatar) else setImageResource(android.R.drawable.ic_menu_myplaces)
        }
        list.addView(view, LinearLayout.LayoutParams(dp(64), dp(64)))
        val address = user.optJSONObject("avatar_urls")?.optString("96") ?: return
        if(avatar != null || avatarLoading || Uri.parse(address).scheme != "https") return
        avatarLoading = true
        val g = generation
        worker.execute {
            var bitmap: android.graphics.Bitmap? = null
            try {
                val c = java.net.URL(address).openConnection() as java.net.HttpURLConnection
                try {
                    c.instanceFollowRedirects = false; c.connectTimeout = 5000; c.readTimeout = 5000
                    if(c.responseCode == 200) {
                        val bytes = c.inputStream.use { input ->
                            val out = java.io.ByteArrayOutputStream()
                            val buffer = ByteArray(8192)
                            while(out.size() <= 1048576) { val n = input.read(buffer); if(n < 0) break; out.write(buffer,0,n) }
                            out.toByteArray()
                        }
                        if(bytes.size <= 1048576) {
                            val bounds = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
                            android.graphics.BitmapFactory.decodeByteArray(bytes,0,bytes.size,bounds)
                            if(bounds.outWidth in 1..2048 && bounds.outHeight in 1..2048)
                                bitmap = android.graphics.BitmapFactory.decodeByteArray(bytes,0,bytes.size)
                        }
                    }
                } finally { c.disconnect() }
            } catch(_: Exception) { }
            val result = bitmap
            main.post {
                avatarLoading = false
                if(g == generation && !isDestroyed && avatarUser == uid) { avatar = result; if(result != null) view.setImageBitmap(result) }
            }
        }
    }
    private fun refresh(show: Boolean, requestedId: String? = null) {
        if (busy) return
        busy = true
        val g = generation
        if (show) status.text = "در حال بررسی حساب و دسترسی…"
        worker.execute {
            try {
                val s = api.session()
                main.post {
                    if (g != generation || isDestroyed) return@post
                    busy = false
                    if (session != null && session!!.user.getLong("id") != s.user.getLong("id")) release()
                    session = s
                    val array = s.course.getJSONArray("lessons")
                    lessons = (0 until array.length()).map { array.getJSONObject(it) }
                    status.text = "دسترسی تأیید شد"
                    render()
                    if(requestedId != null && !stopped && !web) startLesson(requestedId)
                    else flushPending()
                }
            } catch(e: Exception) {
                main.post {
                    if(g != generation || isDestroyed) return@post
                    busy = false
                    player?.pause()
                    if(e is LearningApi.Failure && e.status in listOf(401,403)) {
                        release(); session = null; lessons = emptyList()
                        status.text = "نشست منقضی شده یا دسترسی دوره تأیید نشده؛ وارد حساب شو."
                    } else { release(); status.text = "ارتباط با آموزش برقرار نشد. اینترنت را بررسی و تازه‌سازی کن." }
                    render()
                }
            }
        }
    }
    private fun play(id: String) { saveProgress(); player?.pause(); refresh(true, id) }
    private fun startLesson(id: String) {
        val lesson = lessons.firstOrNull { it.getString("id") == id } ?: return
        val uri = Uri.parse(lesson.optString("video_url"))
        if (uri.scheme != "https" || uri.host !in listOf("shipaa.ir", "dl.shipaa.ir") || (uri.port != -1 && uri.port != 443)) {
            status.text = "نشانی ویدیو معتبر نیست؛ با پشتیبانی تماس بگیر."; return
        }
        release(); activeId = id
        prefs.edit().putString("${session!!.user.getLong("id")}:last", id).apply()
        info.text = lesson.getString("title")
        val p = ExoPlayer.Builder(this).setSeekBackIncrementMs(10000).setSeekForwardIncrementMs(10000).build()
        player = p; playerView.player = p; playerView.visibility = View.VISIBLE
        p.setAudioAttributes(AudioAttributes.Builder().setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_MOVIE).build(), true)
        p.setHandleAudioBecomingNoisy(true)
        p.addListener(object : Player.Listener {
            override fun onPlaybackStateChanged(state: Int) {
                if(state == Player.STATE_READY) {
                    status.text = "برای سرعت پخش از تنظیمات پلیر استفاده کن."
                    if(p.duration > 0) prefs.edit().putLong(key(id)+":duration", p.duration/1000).apply()
                }
                if(state == Player.STATE_ENDED) { saveProgress(true); render() }
            }
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                playerView.keepScreenOn = isPlaying
                if (!isPlaying) saveProgress()
            }
            override fun onPlayerError(error: PlaybackException) {
                status.text = "پخش متوقف شد؛ اینترنت را بررسی کن و دوباره جلسه را انتخاب کن."
            }
        })
        p.setMediaItem(MediaItem.fromUri(uri))
        p.seekTo(if(done(lesson)) 0 else seconds(lesson)*1000)
        p.prepare(); p.play()
    }
    private fun saveProgress(completed: Boolean = false) {
        val p = player ?: return
        val id = activeId ?: return
        if (session == null || p.playbackState == Player.STATE_IDLE || p.playbackState == Player.STATE_BUFFERING) return
        val k = key(id)
        val sec = (p.currentPosition/1000).coerceAtLeast(0)
        val finished = completed || prefs.getBoolean(k+":done", false) || lessons.firstOrNull { it.optString("id") == id }?.let { done(it) } == true
        prefs.edit().putLong(k+":seconds", sec).putBoolean(k+":done", finished).putBoolean(k+":pending", true).apply()
        if (!busy) flushPending()
    }
    private fun flushPending() {
        if(session == null) return
        val g = generation
        lessons.forEach { l ->
            val id = l.getString("id"); val k = key(id)
            if(!prefs.getBoolean(k+":pending",false)) return@forEach
            val sec = prefs.getLong(k+":seconds",0); val finished = prefs.getBoolean(k+":done",false)
            worker.execute {
                if(g != generation) return@execute
                try {
                    api.progress(id,sec,finished)
                    main.post {
                        if(g != generation || isDestroyed) return@post
                        if(prefs.getLong(k+":seconds",0) == sec && prefs.getBoolean(k+":done", false) == finished) {
                            prefs.edit().putBoolean(k+":pending",false).apply()
                            l.put("progress",JSONObject().put("seconds",sec).put("completed",finished))
                        }
                        status.text = "پیشرفت ذخیره شد"
                    }
                } catch(e: Exception) {
                    main.post {
                        if(g != generation || isDestroyed) return@post
                        status.text = "پیشرفت روی گوشی محفوظ است؛ همگام‌سازی در اتصال بعدی."
                        if(e is LearningApi.Failure && e.status in listOf(401,403)) {
                            player?.pause(); release(); session = null; lessons = emptyList()
                            status.text = "برای تأیید دوباره دسترسی وارد حساب شو."; render()
                        }
                    }
                }
            }
        }
    }
    private fun release() {
        val old = player; player = null; activeId = null
        playerView.player = null; old?.release(); playerView.visibility = View.GONE
        playerView.keepScreenOn = false
    }
    private fun openWeb(url: String) {
        saveProgress(); release(); generation++; busy = false; session = null; lessons = emptyList()
        web = true; native.visibility = View.GONE; title.visibility = View.GONE; nav.visibility = View.GONE
        browser.visibility = View.VISIBLE; browser.onResume(); browser.loadUrl(url)
        Toast.makeText(this, "پس از ورود، با دکمه بازگشت گوشی به یادگیری برگرد.", Toast.LENGTH_LONG).show()
    }
    private fun home() {
        closeHtml(); browser.loadUrl("about:blank"); browser.onPause(); browser.visibility = View.GONE
        web = false; native.visibility = View.VISIBLE; title.visibility = View.VISIBLE; nav.visibility = View.VISIBLE
        render(); refresh(true)
    }
    private fun closeHtml() {
        htmlFull?.let { content.removeView(it) }; htmlFull = null
        htmlCallback?.onCustomViewHidden(); htmlCallback = null
        if(web) browser.visibility = View.VISIBLE
    }
    private fun setFull(value: Boolean) {
        fullscreen = value
        title.visibility = if(value) View.GONE else View.VISIBLE
        nav.visibility = if(value) View.GONE else View.VISIBLE
        info.visibility = if(value) View.GONE else View.VISIBLE
        status.visibility = if(value) View.GONE else View.VISIBLE
        native.getChildAt(3).visibility = if(value) View.GONE else View.VISIBLE
        playerView.layoutParams = LinearLayout.LayoutParams(-1, if(value) -1 else dp(220))
        requestedOrientation = if(value) ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE else ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        if(value) window.addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN) else window.clearFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN)
    }
    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        when { htmlFull != null -> closeHtml(); fullscreen -> setFull(false); web && browser.canGoBack() -> browser.goBack(); web -> home(); player != null -> { saveProgress(); release(); render() }; else -> super.onBackPressed() }
    }
    override fun onStop() {
        saveProgress(); player?.pause(); stopped = true
        browser.onPause(); super.onStop()
    }
    override fun onStart() {
        super.onStart(); val wasStopped = stopped; stopped = false
        if(::browser.isInitialized && web) browser.onResume()
        if(wasStopped && !web && session != null) refresh(false)
    }
    override fun onDestroy() {
        generation++; main.removeCallbacksAndMessages(null); release(); browser.destroy(); worker.shutdown(); super.onDestroy()
    }
    private fun dp(n: Int) = (n*resources.displayMetrics.density).toInt()
}
