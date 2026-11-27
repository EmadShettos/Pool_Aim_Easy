package com.example.poolaim

import android.app.Activity
import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone
import kotlin.concurrent.thread
import kotlin.math.min

class BannerView(ctx: Context) : View(ctx) {
    private val d = resources.displayMetrics.density
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private val bmpPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val gold = 0xFFFFD21F.toInt()
    private val topC = 0xFF12894B.toInt()
    private val botC = 0xFF05301B.toInt()
    private val logo: Bitmap? by lazy {
        val id = resources.getIdentifier("pool_aim_icon", "drawable", context.packageName)
        if (id != 0) BitmapFactory.decodeResource(resources, id) else null
    }

    private fun lerp(a: Int, b: Int, t: Float): Int {
        val k = t.coerceIn(0f, 1f)
        val r = (Color.red(a) + (Color.red(b) - Color.red(a)) * k).toInt()
        val g = (Color.green(a) + (Color.green(b) - Color.green(a)) * k).toInt()
        val bl = (Color.blue(a) + (Color.blue(b) - Color.blue(a)) * k).toInt()
        return Color.rgb(r, g, bl)
    }

    override fun onDraw(c: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        val r = RectF(0f, 0f, w, h)
        val clip = Path().apply { addRoundRect(r, 24 * d, 24 * d, Path.Direction.CW) }
        c.save()
        c.clipPath(clip)

        p.style = Paint.Style.FILL
        p.shader = LinearGradient(0f, 0f, 0f, h, topC, botC, Shader.TileMode.CLAMP)
        c.drawRect(r, p)
        p.shader = null

        val bmp = logo
        val imgTop = h - w
        if (bmp != null) {
            val dst = RectF(0f, imgTop, w, h)
            c.drawBitmap(bmp, null, dst, bmpPaint)
            val bg = lerp(topC, botC, imgTop / h)
            val fadeH = w * 0.30f
            p.shader = LinearGradient(
                0f, imgTop, 0f, imgTop + fadeH,
                bg, (bg and 0x00FFFFFF), Shader.TileMode.CLAMP
            )
            c.drawRect(0f, imgTop, w, imgTop + fadeH, p)
            p.shader = null
        }
        c.restore()

        p.style = Paint.Style.FILL
        p.typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
        p.textAlign = Paint.Align.LEFT
        p.textSize = 100f
        val full = p.measureText("Pool Aim Shettos")
        val size = min(100f * (w * 0.86f) / full, w * 0.11f)
        p.textSize = size
        val partA = "Pool Aim "
        val wA = p.measureText(partA)
        val wB = p.measureText("Shettos")
        val x0 = (w - (wA + wB)) / 2f
        val base = size * 1.25f + 6 * d
        p.color = Color.WHITE
        c.drawText(partA, x0, base, p)
        p.color = gold
        c.drawText("Shettos", x0 + wA, base, p)
    }
}

class MainActivity : Activity() {
    private fun markFailed(msg: String) {
        isDownloading = false
        updateFailed = true
        statusText = msg
        runOnUiThread {
            if (!isFinishing) build(scroll?.scrollY ?: 0)
        }
    }

    // ---------- تثبيت التحديث عبر شاشة تثبيت النظام مباشرة (بدون متصفح أو أي رابط خارجي) ----------

    private fun installHint(): String =
        if (ar()) "اضغط «تحديث» في نافذة التثبيت. إذا ظهر تحذير Play Protect فاختر «التثبيت على أي حال»"
        else "Tap Update in the install window. If a Play Protect warning appears, choose Install anyway"

    /** يفتح شاشة تثبيت النظام لملف APK. يعيد false إذا تعذر فتحها. */
    private fun launchInstaller(uri: Uri): Boolean {
        fun viewIntent(pkg: String?): Intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, APK_MIME)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION)
            if (pkg != null) setPackage(pkg)
        }
        val installers = listOf("com.google.android.packageinstaller", "com.android.packageinstaller")
            .filter { pkg -> runCatching { packageManager.getPackageInfo(pkg, 0) }.isSuccess }
        val candidates = installers.map { viewIntent(it) } + viewIntent(null)
        for (intent in candidates) {
            try {
                startActivity(intent)
                return true
            } catch (e: Exception) {
            }
        }
        return false
    }

    /** يفتح شاشة التثبيت الآن، أو عند العودة إلى التطبيق إذا لم يكن ظاهراً على الشاشة */
    private fun openInstaller(uri: Uri, manual: Boolean) {
        if (!resumed) {
            pendingInstallUri = uri
            pendingInstallManual = manual
            setStatus(
                if (ar()) "اكتمل التنزيل. ارجع إلى التطبيق لإكمال التثبيت"
                else "Download complete. Return to the app to finish installing"
            )
            return
        }
        pendingInstallUri = null
        if (launchInstaller(uri)) {
            awaitingInstall = true
            setStatus(installHint())
        } else if (manual) {
            markFailed(
                if (ar()) "اكتمل التنزيل لكن تعذر فتح شاشة التثبيت. اضغط إشعار اكتمال التنزيل لفتح الملف وتثبيته"
                else "Downloaded, but the installer could not be opened. Tap the download notification to open and install it"
            )
        } else {
            markFailed(if (ar()) "تعذر فتح شاشة التثبيت" else "Could not open the installer screen")
        }
    }

    // ---------- التنزيل والتثبيت اليدوي عبر مدير تنزيلات النظام ----------

    private fun downloadManager() = getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager

    private fun cancelManualDownload() {
        ui.removeCallbacks(manualPoll)
        val id = manualId
        manualId = -1L
        if (id != -1L) runCatching { downloadManager().remove(id) }
    }

    private fun startManualDownload() {
        if (isDownloading) {
            Toast.makeText(
                this,
                if (ar()) "التنزيل جارٍ بالفعل، انتظر قليلاً" else "A download is already in progress, please wait",
                Toast.LENGTH_SHORT
            ).show()
            return
        }
        if (!packageManager.canRequestPackageInstalls()) {
            pendingManual = true
            Toast.makeText(
                this,
                if (ar()) "فعّل إذن تثبيت التطبيقات لهذا التطبيق ثم ارجع" else "Allow Install unknown apps for this app, then come back",
                Toast.LENGTH_LONG
            ).show()
            startActivity(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:$packageName")))
            return
        }
        pendingManual = false
        awaitingInstall = false
        pendingInstallUri = null
        try {
            val dm = downloadManager()
            cancelManualDownload()
            val sp = getSharedPreferences(Lang.PREFS, MODE_PRIVATE)
            val old = sp.getLong(KEY_DM_ID, -1L)
            if (old != -1L) runCatching { dm.remove(old) }
            val req = DownloadManager.Request(Uri.parse(apkUrl ?: FALLBACK_APK)).apply {
                setTitle(if (ar()) "تحديث Pool Aim Shettos" else "Pool Aim Shettos update")
                setDescription(if (ar()) "اضغط بعد اكتمال التنزيل للتثبيت" else "Tap when finished to install")
                setMimeType(APK_MIME)
                setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                addRequestHeader("User-Agent", "PoolAimApp")
                if (Build.VERSION.SDK_INT >= 29) {
                    setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, MANUAL_APK_NAME)
                } else {
                    runCatching { File(getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), MANUAL_APK_NAME).delete() }
                    setDestinationInExternalFilesDir(this@MainActivity, Environment.DIRECTORY_DOWNLOADS, MANUAL_APK_NAME)
                }
            }
            val id = dm.enqueue(req)
            sp.edit().putLong(KEY_DM_ID, id).apply()
            manualId = id
            setStatus(if (ar()) "جارٍ التنزيل... 0%" else "Downloading... 0%")
            ui.post(manualPoll)
        } catch (e: Exception) {
            // مدير التنزيلات غير متاح على هذا الجهاز: نزّل داخل التطبيق ثم افتح شاشة التثبيت
            manualId = -1L
            startUpdateFlow()
        }
    }

    /** متابعة تقدّم التنزيل اليدوي، وعند اكتماله تُفتح شاشة التثبيت تلقائياً */
    private val manualPoll = object : Runnable {
        override fun run() {
            val id = manualId
            if (id == -1L || isFinishing || isDestroyed) return
            var again = false
            try {
                val dm = downloadManager()
                val c = dm.query(DownloadManager.Query().setFilterById(id))
                if (c == null || !c.moveToFirst()) {
                    c?.close()
                    manualId = -1L
                    markFailed(
                        if (ar()) "أُلغي التنزيل. اضغط الزر للمحاولة مجدداً"
                        else "Download cancelled. Tap the button to try again"
                    )
                    return
                }
                val status = c.getInt(c.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))
                val reason = c.getInt(c.getColumnIndexOrThrow(DownloadManager.COLUMN_REASON))
                val done = c.getLong(c.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR))
                val total = c.getLong(c.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES))
                c.close()
                when (status) {
                    DownloadManager.STATUS_SUCCESSFUL -> {
                        manualId = -1L
                        val uri = runCatching { dm.getUriForDownloadedFile(id) }.getOrNull()
                        if (uri != null) {
                            openInstaller(uri, true)
                        } else {
                            markFailed(
                                if (ar()) "اكتمل التنزيل. اضغط إشعار اكتمال التنزيل لفتح الملف وتثبيته"
                                else "Download complete. Tap the download notification to open and install it"
                            )
                        }
                    }
                    DownloadManager.STATUS_FAILED -> {
                        manualId = -1L
                        runCatching { dm.remove(id) }
                        markFailed(
                            if (ar()) "فشل التنزيل ($reason). اضغط إعادة المحاولة"
                            else "Download failed ($reason). Tap Retry"
                        )
                    }
                    else -> {
                        val pct = if (total > 0L) (done * 100 / total).toInt().coerceIn(0, 100) else 0
                        setStatus(
                            if (status == DownloadManager.STATUS_PAUSED) {
                                if (ar()) "في انتظار الشبكة... $pct%" else "Waiting for network... $pct%"
                            } else {
                                if (ar()) "جارٍ التنزيل... $pct%" else "Downloading... $pct%"
                            }
                        )
                        again = true
                    }
                }
            } catch (e: Exception) {
                manualId = -1L
                markFailed(
                    if (ar()) "تعذر متابعة التنزيل. اضغط إعادة المحاولة"
                    else "Could not track the download. Tap Retry"
                )
            }
            if (again) ui.postDelayed(this, 700L)
        }
    }

    /** حذف ملفات التحديث القديمة بعد أن يصبح التطبيق محدَّثاً */
    private fun cleanupUpdateFiles() {
        if (isDownloading || manualId != -1L) return
        runCatching { File(cacheDir, "update.apk").delete() }
        runCatching {
            val sp = getSharedPreferences(Lang.PREFS, MODE_PRIVATE)
            val old = sp.getLong(KEY_DM_ID, -1L)
            if (old != -1L) {
                downloadManager().remove(old)
                sp.edit().remove(KEY_DM_ID).apply()
            }
        }
    }

    private val gold = 0xFFFFD21F.toInt()
    private var scroll: ScrollView? = null
    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
    private fun T(k: String) = Lang.t(this, k)

    private var isUpdateRequired = false
    private var isDownloading = false
    private var pendingStart = false
    private var pendingUpdate = false
    private var pendingManual = false
    private var autoTried = false
    private var updateFailed = false
    // هل التطبيق ظاهر على الشاشة الآن
    private var resumed = false
    // فُتحت شاشة التثبيت وننتظر نتيجتها
    private var awaitingInstall = false
    // ملف جاهز للتثبيت ينتظر عودة المستخدم إلى التطبيق
    private var pendingInstallUri: Uri? = null
    private var pendingInstallManual = false
    // رقم التنزيل اليدوي الجاري في مدير التنزيلات (-1 = لا يوجد)
    private var manualId = -1L
    private val ui = Handler(Looper.getMainLooper())
    private var apkUrl: String? = null
    private var statusText: String = ""
    private var statusView: TextView? = null
    private var videoSlider: VideoSliderView? = null

    private val RELEASE_API = "https://api.github.com/repos/EmadShettos/Pool_Aim_Easy/releases/tags/${BuildConfig.UPDATE_TAG}"
    private val FALLBACK_APK = "https://github.com/EmadShettos/Pool_Aim_Easy/releases/download/${BuildConfig.UPDATE_TAG}/EmadShettos-PoolAim.apk"
    private val APK_MIME = "application/vnd.android.package-archive"
    private val MANUAL_APK_NAME = "PoolAimShettos-update.apk"
    private val KEY_DM_ID = "update_dm_id"
    private val PRIVACY_URL = "https://www.termsfeed.com/live/62a09e53-631e-42b3-b6dc-76bc545e19ba"

    private fun ar() = Lang.isAr(this)

    private fun setStatus(s: String) {
        statusText = s
        runOnUiThread { statusView?.text = s }
    }

    private fun parseTime(s: String): Long {
        val f = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US)
        f.timeZone = TimeZone.getTimeZone("UTC")
        return f.parse(s)?.time ?: 0L
    }

    private fun checkForUpdates() {
        if (isDownloading) return
        thread {
            try {
                val conn = (URL(RELEASE_API).openConnection() as HttpURLConnection).apply {
                    requestMethod = "GET"
                    setRequestProperty("User-Agent", "PoolAimApp")
                    setRequestProperty("Accept", "application/vnd.github+json")
                    setRequestProperty("Cache-Control", "no-cache")
                    connectTimeout = 8000
                    readTimeout = 8000
                }
                if (conn.responseCode != 200) return@thread
                val resp = conn.inputStream.bufferedReader().use { it.readText() }
                val json = JSONObject(resp)
                val assets = json.getJSONArray("assets")
                var url: String? = null
                var assetTime = 0L
                for (i in 0 until assets.length()) {
                    val a = assets.getJSONObject(i)
                    if (a.getString("name").endsWith(".apk")) {
                        url = a.optString("browser_download_url", FALLBACK_APK)
                        assetTime = runCatching { parseTime(a.getString("updated_at")) }.getOrDefault(0L)
                        break
                    }
                }
                if (url == null) return@thread
                val remoteBuild = Regex("build=(\\d+)").find(json.optString("body", ""))
                    ?.groupValues?.get(1)?.toIntOrNull()
                val need = if (remoteBuild != null) {
                    remoteBuild > BuildConfig.BUILD_NUMBER
                } else {
                    val installedAt = packageManager.getPackageInfo(packageName, 0).lastUpdateTime
                    assetTime > 0L && assetTime > (installedAt + 300_000L)
                }
                apkUrl = url
                if (!need) cleanupUpdateFiles()
                runOnUiThread {
                    if (!need) updateFailed = false
                    if (need != isUpdateRequired) {
                        isUpdateRequired = need
                        if (need) statusText = if (ar()) "تم العثور على تحديث، جارٍ التجهيز..." else "Update found, preparing..."
                        build(scroll?.scrollY ?: 0)
                    }
                    if (need && !autoTried) {
                        autoTried = true
                        startUpdateFlow()
                    }
                }
            } catch (e: Exception) {}
        }
    }

    private fun startUpdateFlow() {
        if (isDownloading) return
        if (!packageManager.canRequestPackageInstalls()) {
            pendingUpdate = true
            Toast.makeText(
                this,
                if (ar()) "فعّل إذن تثبيت التطبيقات لهذا التطبيق ثم ارجع" else "Allow Install unknown apps for this app, then come back",
                Toast.LENGTH_LONG
            ).show()
            startActivity(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:$packageName")))
            return
        }
        pendingUpdate = false
        awaitingInstall = false
        pendingInstallUri = null
        cancelManualDownload()
        if (updateFailed) {
            updateFailed = false
            build(scroll?.scrollY ?: 0)
        }
        isDownloading = true
        setStatus(if (ar()) "جارٍ تنزيل التحديث... 0%" else "Downloading update... 0%")
        val links = listOfNotNull(apkUrl, FALLBACK_APK).distinct()
        thread {
            val out = File(cacheDir, "update.apk")
            val tmp = File(cacheDir, "update.apk.part")
            var ok = false
            var lastError = ""
            loop@ for (link in links) {
                for (attempt in 1..2) {
                    try {
                        if (tmp.exists()) tmp.delete()
                        download(link, tmp)
                        ok = true
                        break@loop
                    } catch (e: Exception) {
                        lastError = e.message ?: "error"
                        Thread.sleep(1500)
                    }
                }
            }
            if (!ok) {
                markFailed(
                    if (ar()) "فشل التنزيل ($lastError). اضغط إعادة المحاولة أو نزّل يدوياً"
                    else "Download failed ($lastError). Tap Retry or download manually"
                )
                return@thread
            }
            if (out.exists()) out.delete()
            if (!tmp.renameTo(out)) {
                markFailed(if (ar()) "تعذر حفظ ملف التحديث" else "Could not save the update file")
                return@thread
            }
            runOnUiThread {
                isDownloading = false
                // فتح شاشة تثبيت النظام مباشرة (نفس مسار تثبيت الملفات المنزَّلة الذي يتجاوز تحذير Play Protect بنجاح)
                openInstaller(Uri.parse("content://$packageName.apkprovider/update.apk"), false)
            }
        }
    }

    private fun download(link: String, tmp: File) {
        val conn = (URL(link).openConnection() as HttpURLConnection).apply {
            instanceFollowRedirects = true
            setRequestProperty("User-Agent", "PoolAimApp")
            connectTimeout = 15000
            readTimeout = 30000
        }
        try {
            if (conn.responseCode != 200) throw Exception("HTTP ${conn.responseCode}")
            val total = conn.contentLengthLong
            var done = 0L
            var lastPct = -1
            conn.inputStream.use { input ->
                tmp.outputStream().use { os ->
                    val buf = ByteArray(64 * 1024)
                    while (true) {
                        val n = input.read(buf)
                        if (n < 0) break
                        os.write(buf, 0, n)
                        done += n
                        if (total > 0) {
                            val pct = (done * 100 / total).toInt()
                            if (pct != lastPct) {
                                lastPct = pct
                                setStatus(if (ar()) "جارٍ تنزيل التحديث... $pct%" else "Downloading update... $pct%")
                            }
                        }
                    }
                }
            }
            if (total > 0 && tmp.length() != total) throw Exception("incomplete")
            if (tmp.length() < 100_000L) throw Exception("file too small")
            tmp.inputStream().use { ins ->
                val head = ByteArray(2)
                if (ins.read(head) != 2 || head[0] != 'P'.code.toByte() || head[1] != 'K'.code.toByte()) {
                    throw Exception("not an APK")
                }
            }
        } finally {
            conn.disconnect()
        }
    }

    private fun styled(label: String, bg: Int, fg: Int, onClick: () -> Unit): Button =
        Button(this).apply {
            text = label
            isAllCaps = false
            textSize = 14f
            setTextColor(fg)
            background = GradientDrawable().apply {
                setColor(bg)
                cornerRadius = dp(14).toFloat()
                setStroke(dp(1), gold)
            }
            setOnClickListener { onClick() }
        }

    private fun smallBtn(label: String, bg: Int, fg: Int, onClick: () -> Unit): Button =
        styled(label, bg, fg, onClick).apply {
            textSize = 13f
            minHeight = 0
            minimumHeight = 0
            setPadding(dp(14), dp(6), dp(14), dp(6))
        }

    private fun label(t: String, sp: Float, col: Int, bold: Boolean = false, center: Boolean = false) =
        TextView(this).apply {
            text = t; textSize = sp; setTextColor(col)
            if (bold) typeface = Typeface.DEFAULT_BOLD
            if (center) gravity = Gravity.CENTER
        }

    private fun launchOverlay() {
        if (Build.VERSION.SDK_INT >= 33) requestPermissions(arrayOf("android.permission.POST_NOTIFICATIONS"), 1)
        startForegroundService(Intent(this, OverlayService::class.java))
    }

    private fun openPrivacyPolicy() {
        try {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(PRIVACY_URL)))
        } catch (e: Exception) {
            Toast.makeText(
                this,
                if (ar()) "تعذر فتح سياسة الخصوصية" else "Could not open the privacy policy",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    private fun helpCard(onTop: () -> Unit): LinearLayout {
        val isAr = Lang.isAr(this)
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), dp(16), dp(14), dp(16))
            background = GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                intArrayOf(0xFF12894B.toInt(), 0xFF05301B.toInt())
            ).apply {
                cornerRadius = dp(20).toFloat()
                setStroke(dp(2), gold)
            }
        }
        val wrap = LinearLayout.LayoutParams.WRAP_CONTENT
        val match = LinearLayout.LayoutParams.MATCH_PARENT
        val soft = Color.argb(235, 230, 240, 235)

        card.addView(label(if (isAr) "كيف تثبت التطبيق وكيف تلعب" else "How to install & play", 22f, Color.WHITE, true, true),
            LinearLayout.LayoutParams(match, wrap))

        card.addView(label(if (isAr) "أولاً: تثبيت التطبيق" else "Step 1: Install the app", 16f, gold, true, true),
            LinearLayout.LayoutParams(match, wrap).apply { topMargin = dp(16) })
        val installText = if (isAr) {
            "١) افتح ملف APK واسمح للهاتف بالتثبيت من هذا المصدر إذا طلب ذلك.\n" +
            "٢) افتح التطبيق واضغط زر تشغيل العرض (Start).\n" +
            "٣) في أول مرة سينقلك التطبيق إلى إعدادات الظهور فوق التطبيقات. فعّل الإذن ثم ارجع للتطبيق.\n" +
            "٤) عند رجوعك تظهر القائمة العائمة تلقائياً.\n" +
            "٥) عند صدور تحديث يظهر كرت التحديث ويبدأ التنزيل تلقائياً ثم تُفتح نافذة التثبيت. اضغط «تحديث»، وإذا ظهر تحذير Play Protect فاختر «التثبيت على أي حال»."
        } else {
            "1) Open the APK and allow installing from this source if asked.\n" +
            "2) Open the app and tap Start.\n" +
            "3) The first time, the app opens the 'Display over other apps' settings. Enable it and come back.\n" +
            "4) When you return, the floating menu starts automatically.\n" +
            "5) When an update is released, the update card appears, the download starts automatically, then the install window opens. Tap Update, and if a Play Protect warning appears choose Install anyway."
        }
        card.addView(label(installText, 13f, soft, false, false),
            LinearLayout.LayoutParams(match, wrap).apply { topMargin = dp(6) })

        card.addView(label(if (isAr) "ثانياً: كيف تلعب" else "Step 2: How to play", 16f, gold, true, true),
            LinearLayout.LayoutParams(match, wrap).apply { topMargin = dp(20) })
        card.addView(label(T("help_steps"), 13f, soft, false, true),
            LinearLayout.LayoutParams(match, wrap).apply { topMargin = dp(8) })

        val items = listOf(
            Triple("h_rect", "rect", "d_rect"),
            Triple("h_touch", "touch", "d_touch"),
            Triple("h_single", "single", "d_single"),
            Triple("h_bank", "bank", "d_bank"),
            Triple("h_double", "double", "d_double"),
            Triple("h_pockets", "pockets", "d_pockets"),
            Triple("h_ghost", "ghost", "d_ghost")
        )
        for ((name, kind, desc) in items) {
            card.addView(label(T(name), 16f, gold, true, true),
                LinearLayout.LayoutParams(match, wrap).apply { topMargin = dp(16) })
            card.addView(HowToDiagram(this, kind),
                LinearLayout.LayoutParams(match, dp(170)).apply { topMargin = dp(6) })
            card.addView(label(T(desc), 13f, soft, false, true),
                LinearLayout.LayoutParams(match, wrap).apply { topMargin = dp(6) })
        }
        card.addView(styled(T("back_top"), 0xFF0B0B0B.toInt(), gold) { onTop() },
            LinearLayout.LayoutParams(match, wrap).apply { topMargin = dp(18) })
        return card
    }

    private fun openWhatsApp() {
        val uri = Uri.parse("https://wa.me/201093532277")
        try {
            startActivity(Intent(Intent.ACTION_VIEW, uri).setPackage("com.whatsapp"))
        } catch (e: Exception) {
            try {
                startActivity(Intent(Intent.ACTION_VIEW, uri))
            } catch (e2: Exception) {
                Toast.makeText(
                    this,
                    if (Lang.isAr(this)) "تعذر فتح واتساب" else "Could not open WhatsApp",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    private fun switchLang(ar: Boolean) {
        val y = scroll?.scrollY ?: 0
        Lang.set(this, ar)
        build(y)
    }

    private fun build(restoreY: Int) {
        val sv = ScrollView(this).apply { isFillViewport = true }
        scroll = sv
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(28), dp(16), dp(16))
            background = GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                intArrayOf(0xFF0B3D24.toInt(), 0xFF04170D.toInt())
            )
        }
        fun lp() = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = dp(8) }

        val isAr = Lang.isAr(this)

        val langRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutDirection = View.LAYOUT_DIRECTION_LTR
        }
        val enBtn = smallBtn("\uD83C\uDDEC\uD83C\uDDE7  ${T("lang_en_btn")}", 0xFF0B0B0B.toInt(), gold) { switchLang(false) }
        val arBtn = smallBtn("\uD83C\uDDF8\uD83C\uDDE6  ${T("lang_ar_btn")}", 0xFF0B0B0B.toInt(), gold) { switchLang(true) }
        val exitBtn = smallBtn("\u2715  ${T("exit")}", 0xFF7F1D1D.toInt(), Color.WHITE) {
            stopService(Intent(this, OverlayService::class.java))
            finishAndRemoveTask()
        }
        val privacyBtn = smallBtn(
            if (isAr) "سياسة الخصوصية" else "Privacy Policy",
            0xFF0E5A6B.toInt(), Color.WHITE
        ) { openPrivacyPolicy() }

        val exitColumn = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutDirection = View.LAYOUT_DIRECTION_LTR
        }
        exitColumn.addView(exitBtn, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))
        exitColumn.addView(privacyBtn, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = dp(6) })

        val leftV: View = if (isAr) enBtn else exitColumn
        val rightV: View = if (isAr) exitColumn else arBtn
        langRow.addView(leftV, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT))
        langRow.addView(View(this), LinearLayout.LayoutParams(0, 1, 1f))
        langRow.addView(rightV, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT))
        root.addView(langRow, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))

        val slider = VideoSliderView(this)
        videoSlider = slider
        root.addView(slider, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply {
            topMargin = dp(14)
            bottomMargin = dp(6)
        })

        val card = helpCard { sv.smoothScrollTo(0, 0) }

        statusView = null
        if (isUpdateRequired) {
            val updateBox = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                setPadding(dp(14), dp(14), dp(14), dp(14))
                background = GradientDrawable().apply {
                    setColor(0xFF1E293B.toInt())
                    cornerRadius = dp(14).toFloat()
                    setStroke(dp(2), 0xFF38BDF8.toInt())
                }
            }
            updateBox.addView(label(
                if (isAr) "⚠️ يتوفر تحديث جديد للتطبيق!" else "⚠️ New update available!",
                16f, 0xFF38BDF8.toInt(), true, true
            ))
            val st = label(
                if (statusText.isNotEmpty()) statusText
                else if (isAr) "جارٍ التجهيز..." else "Preparing...",
                12f, Color.WHITE, false, true
            )
            statusView = st
            updateBox.addView(st, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                topMargin = dp(6)
            })

            val updateNowBtn = styled(
                if (isAr) "إعادة المحاولة / تحديث الآن 🚀" else "Retry / Update now 🚀",
                0xFF10B981.toInt(), Color.WHITE
            ) { startUpdateFlow() }
            updateBox.addView(updateNowBtn, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                topMargin = dp(10)
            })

            if (updateFailed) {
                updateBox.addView(
                    styled(
                        if (isAr) "تنزيل وتثبيت يدوي" else "Download & install manually",
                        0xFF0B0B0B.toInt(), gold
                    ) { startManualDownload() },
                    LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
                    ).apply { topMargin = dp(8) }
                )
            }

            root.addView(updateBox, lp())
        }

        root.addView(styled(T("grant"), 0xFF14532D.toInt(), Color.WHITE) {
            startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
        }, lp())

        val startBtnLabel = if (isUpdateRequired) {
            if (isAr) "🔒 قفل التشغيل (يجب التحديث أولاً)" else "🔒 Locked (Update required)"
        } else T("start")

        val startBtnBg = if (isUpdateRequired) 0xFF4B5563.toInt() else gold
        val startBtnFg = if (isUpdateRequired) Color.LTGRAY else Color.BLACK

        root.addView(styled(startBtnLabel, startBtnBg, startBtnFg) {
            if (isUpdateRequired) {
                Toast.makeText(
                    this,
                    if (isAr) "جارٍ التحديث، انتظر اكتمال التثبيت"
                    else "Updating, please wait for the install to finish",
                    Toast.LENGTH_LONG
                ).show()
                return@styled
            }
            if (!Settings.canDrawOverlays(this)) {
                pendingStart = true
                Toast.makeText(this, T("grant_first"), Toast.LENGTH_LONG).show()
                startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
            } else {
                launchOverlay()
            }
        }, lp())

        root.addView(styled(T("stop_overlay"), 0xFF7F1D1D.toInt(), Color.WHITE) {
            stopService(Intent(this, OverlayService::class.java))
        }, lp())

        root.addView(styled(if (isAr) "كيف تثبت التطبيق وكيف تلعب" else "How to install & play", 0xFF0B0B0B.toInt(), gold) {
            sv.post { sv.smoothScrollTo(0, card.top) }
        }, lp())

        // مستطيل المطور الأصلي الكلاسيكي مع زر الواتس في داخله كما في نسخة main تماماً
        val strip = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(dp(64), dp(10), dp(64), dp(10))
            background = GradientDrawable().apply {
                setColor(0xFF0B0B0B.toInt())
                cornerRadius = dp(14).toFloat()
                setStroke(dp(2), gold)
            }
        }
        strip.addView(label(T("dev"), 14f, gold, true, true))
        strip.addView(label(T("whatsapp"), 13f, 0xFFEAEAEA.toInt(), false, true),
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(8) })
        val goBtn = Button(this).apply {
            text = if (isAr) "اذهب" else "Go"
            isAllCaps = false
            textSize = 12f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.BLACK)
            minWidth = 0
            minHeight = 0
            minimumWidth = 0
            minimumHeight = 0
            setPadding(dp(20), dp(4), dp(20), dp(4))
            background = GradientDrawable().apply {
                setColor(gold)
                cornerRadius = dp(10).toFloat()
            }
            setOnClickListener { openWhatsApp() }
        }
        strip.addView(goBtn, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = dp(8) })
        root.addView(strip, lp())

        root.addView(BannerView(this), LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, dp(430)
        ).apply { topMargin = dp(14) })

        root.addView(card, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = dp(14) })

        sv.addView(root)
        setContentView(sv)
        if (restoreY > 0) sv.post { sv.scrollTo(0, restoreY) }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        build(0)
    }

    override fun onResume() {
        super.onResume()
        resumed = true
        if (pendingStart) {
            pendingStart = false
            if (!isUpdateRequired && Settings.canDrawOverlays(this)) {
                launchOverlay()
            }
        }
        if (awaitingInstall) {
            // عدنا من شاشة التثبيت والنسخة الحالية ما زالت تعمل: التثبيت لم يكتمل (أُلغي أو حُظر)
            awaitingInstall = false
            markFailed(
                if (ar()) "لم يكتمل التثبيت. اضغط إعادة المحاولة، وإذا ظهر تحذير Play Protect فاختر «التثبيت على أي حال»"
                else "The install was not completed. Tap Retry, and if a Play Protect warning appears choose Install anyway"
            )
        }
        if (pendingUpdate && packageManager.canRequestPackageInstalls()) {
            pendingUpdate = false
            startUpdateFlow()
        }
        if (pendingManual && packageManager.canRequestPackageInstalls()) {
            pendingManual = false
            startManualDownload()
        }
        pendingInstallUri?.let { openInstaller(it, pendingInstallManual) }
        checkForUpdates()
        videoSlider?.resume()
    }

    override fun onPause() {
        super.onPause()
        resumed = false
        videoSlider?.pause()
    }

    override fun onDestroy() {
        super.onDestroy()
        ui.removeCallbacks(manualPoll)
        videoSlider?.release()
    }
}
