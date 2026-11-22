package com.example.poolaim

import android.app.Activity
import android.app.DownloadManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageInstaller
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
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.widget.Button
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
    private val panelTop = 0xFF12894B.toInt()
    private val panelBottom = 0xFF0A3A22.toInt()
    private val logo: Bitmap? by lazy {
        val id = resources.getIdentifier("pool_aim_icon", "drawable", context.packageName)
        if (id != 0) {
            val o = BitmapFactory.Options().apply { inScaled = false }
            BitmapFactory.decodeResource(resources, id, o)
        } else null
    }

    private fun fit(text: String, maxW: Float, maxH: Float): Float {
        p.textSize = 100f
        val w = p.measureText(text)
        return min(100f * maxW / w, maxH)
    }

    override fun onDraw(c: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        val size = min(w, h - 110 * d)
        val imgTop = h - size
        val left = (w - size) / 2f

        val clip = Path().apply { addRoundRect(RectF(0f, 0f, w, h), 24 * d, 24 * d, Path.Direction.CW) }
        c.save()
        c.clipPath(clip)

        p.style = Paint.Style.FILL
        p.shader = LinearGradient(0f, 0f, 0f, imgTop, panelTop, panelBottom, Shader.TileMode.CLAMP)
        c.drawRect(0f, 0f, w, h, p)
        p.shader = null

        val bmp = logo
        if (bmp != null) {
            val dst = RectF(left, imgTop, left + size, imgTop + size)
            c.drawBitmap(bmp, null, dst, bmpPaint)
            val fadeH = size * 0.22f
            p.shader = LinearGradient(
                0f, imgTop, 0f, imgTop + fadeH,
                panelBottom, (panelBottom and 0x00FFFFFF), Shader.TileMode.CLAMP
            )
            c.drawRect(left, imgTop, left + size, imgTop + fadeH, p)
            p.shader = null
        }

        p.style = Paint.Style.FILL
        p.typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
        p.textAlign = Paint.Align.CENTER
        p.setShadowLayer(4 * d, 0f, 2 * d, Color.argb(160, 0, 0, 0))
        val cx = w / 2
        p.color = Color.WHITE
        p.textSize = fit("Pool Aim", w * 0.72f, imgTop * 0.34f)
        c.drawText("Pool Aim", cx, imgTop * 0.46f, p)
        p.color = gold
        p.textSize = fit("Shettos", w * 0.6f, imgTop * 0.34f)
        c.drawText("Shettos", cx, imgTop * 0.88f, p)
        p.clearShadowLayer()

        c.restore()
    }
}

class MainActivity : Activity() {
    private val gold = 0xFFFFD21F.toInt()
    private var scroll: ScrollView? = null
    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
    private fun T(k: String) = Lang.t(this, k)

    private var isUpdateRequired = false
    private var isDownloading = false
    private var isInstalling = false
    private var pendingStart = false
    private var pendingInstallFile: File? = null
    private var assetUpdatedAt: String? = null
    private var assetUrl: String = ""
    private var downloadId: Long = -1L

    private val REPO = "EmadShettos/Pool_Aim_Easy"
    private val APK_URL = "https://github.com/EmadShettos/Pool_Aim_Easy/releases/download/latest/EmadShettos-PoolAim.apk"
    private val APK_FILE_NAME = "PoolAim-update.apk"
    private val PREF_AUTO_DL = "auto_dl_for"
    private val ACTION_INSTALL_STATUS = "com.example.poolaim.INSTALL_STATUS"

    private fun updateFile(): File =
        File(getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), APK_FILE_NAME)

    private fun refreshUi() {
        runOnUiThread { build(scroll?.scrollY ?: 0) }
    }

    @Suppress("DEPRECATION")
    private fun confirmIntentOf(i: Intent): Intent? =
        if (Build.VERSION.SDK_INT >= 33) i.getParcelableExtra(Intent.EXTRA_INTENT, Intent::class.java)
        else i.getParcelableExtra(Intent.EXTRA_INTENT)

    private val onDownloadComplete = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val id = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1)
            if (id != downloadId) return
            isDownloading = false
            val f = updateFile()
            if (f.exists() && f.length() > 0) {
                beginInstall(f)
            } else {
                Toast.makeText(
                    this@MainActivity,
                    if (Lang.isAr(this@MainActivity)) "فشل تنزيل التحديث" else "Update download failed",
                    Toast.LENGTH_LONG
                ).show()
            }
            refreshUi()
        }
    }

    private val installStatusReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)) {
                PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                    val confirm = confirmIntentOf(intent)
                    if (confirm != null) {
                        confirm.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        startActivity(confirm)
                    }
                }
                PackageInstaller.STATUS_SUCCESS -> {}
                else -> {
                    isInstalling = false
                    val msg = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE) ?: ""
                    Toast.makeText(
                        this@MainActivity,
                        (if (Lang.isAr(this@MainActivity)) "تعذر التثبيت: " else "Install failed: ") + msg,
                        Toast.LENGTH_LONG
                    ).show()
                    refreshUi()
                }
            }
        }
    }

    private fun beginInstall(file: File) {
        if (Build.VERSION.SDK_INT >= 26 && !packageManager.canRequestPackageInstalls()) {
            pendingInstallFile = file
            Toast.makeText(
                this,
                if (Lang.isAr(this)) "فعّل السماح بتثبيت التطبيقات لهذا التطبيق ثم ارجع" else "Allow installing apps for this app, then come back",
                Toast.LENGTH_LONG
            ).show()
            startActivity(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:$packageName")))
            return
        }
        isInstalling = true
        try {
            sessionInstall(file)
        } catch (e: Exception) {
            isInstalling = false
            fallbackInstall()
        }
        refreshUi()
    }

    private fun sessionInstall(file: File) {
        val installer = packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL)
        if (Build.VERSION.SDK_INT >= 31) {
            params.setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED)
        }
        val sid = installer.createSession(params)
        val session = installer.openSession(sid)
        try {
            session.openWrite("base.apk", 0, file.length()).use { out ->
                file.inputStream().use { it.copyTo(out) }
                session.fsync(out)
            }
            val intent = Intent(ACTION_INSTALL_STATUS).setPackage(packageName)
            val flags = PendingIntent.FLAG_UPDATE_CURRENT or
                (if (Build.VERSION.SDK_INT >= 31) PendingIntent.FLAG_MUTABLE else 0)
            val pending = PendingIntent.getBroadcast(this, 0, intent, flags)
            session.commit(pending.intentSender)
        } finally {
            session.close()
        }
    }

    private fun fallbackInstall() {
        try {
            val dm = getSystemService(DOWNLOAD_SERVICE) as DownloadManager
            val fileUri = dm.getUriForDownloadedFile(downloadId)
            if (fileUri == null) {
                Toast.makeText(this, if (Lang.isAr(this)) "تعذر فتح ملف التثبيت" else "Could not open the installer", Toast.LENGTH_LONG).show()
                return
            }
            val installIntent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(fileUri, "application/vnd.android.package-archive")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION
            }
            startActivity(installIntent)
        } catch (e: Exception) {
            Toast.makeText(this, "تعذر فتح ملف التثبيت: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun startDirectDownload() {
        if (isDownloading || isInstalling) return
        runCatching { updateFile().delete() }
        val url = if (assetUrl.isNotEmpty()) assetUrl else APK_URL
        val request = DownloadManager.Request(Uri.parse(url)).apply {
            setTitle("Pool Aim Shettos Update")
            setDescription("تحديث التطبيق")
            setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            setDestinationInExternalFilesDir(this@MainActivity, Environment.DIRECTORY_DOWNLOADS, APK_FILE_NAME)
            setMimeType("application/vnd.android.package-archive")
        }
        val dm = getSystemService(DOWNLOAD_SERVICE) as DownloadManager
        downloadId = dm.enqueue(request)
        isDownloading = true
        refreshUi()
    }

    private fun maybeAutoDownload() {
        val key = assetUpdatedAt ?: return
        val prefs = getSharedPreferences(Lang.PREFS, MODE_PRIVATE)
        if (isDownloading || isInstalling) return
        if (prefs.getString(PREF_AUTO_DL, null) == key) return
        prefs.edit().putString(PREF_AUTO_DL, key).apply()
        startDirectDownload()
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

    private fun checkForUpdates() {
        thread {
            try {
                val url = URL("https://api.github.com/repos/$REPO/releases/tags/latest")
                val conn = (url.openConnection() as HttpURLConnection).apply {
                    requestMethod = "GET"
                    setRequestProperty("User-Agent", "PoolAimApp")
                    setRequestProperty("Accept", "application/vnd.github+json")
                    connectTimeout = 5000
                    readTimeout = 5000
                }
                if (conn.responseCode != 200) return@thread
                val resp = conn.inputStream.bufferedReader().use { it.readText() }
                val assets = JSONObject(resp).getJSONArray("assets")
                var updated: String? = null
                var dl: String? = null
                for (i in 0 until assets.length()) {
                    val a = assets.getJSONObject(i)
                    if (a.getString("name").endsWith(".apk")) {
                        updated = a.getString("updated_at")
                        dl = a.getString("browser_download_url")
                        break
                    }
                }
                if (updated == null || dl == null) return@thread
                val fmt = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US)
                fmt.timeZone = TimeZone.getTimeZone("UTC")
                val assetTime = fmt.parse(updated)?.time ?: return@thread
                val installedTime = packageManager.getPackageInfo(packageName, 0).lastUpdateTime
                val need = assetTime > installedTime
                assetUpdatedAt = updated
                assetUrl = dl
                runOnUiThread {
                    if (need != isUpdateRequired) {
                        isUpdateRequired = need
                        build(scroll?.scrollY ?: 0)
                    }
                    if (need) maybeAutoDownload()
                }
            } catch (e: Exception) {}
        }
    }

    private fun launchOverlay() {
        if (Build.VERSION.SDK_INT >= 33) requestPermissions(arrayOf("android.permission.POST_NOTIFICATIONS"), 1)
        startForegroundService(Intent(this, OverlayService::class.java))
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
            "٣) في أول مرة سينقلك التطبيق إلى إعدادات \"الظهور فوق التطبيقات\". فعّل الإذن ثم ارجع للتطبيق.\n" +
            "٤) عند رجوعك تظهر القائمة العائمة تلقائياً.\n" +
            "٥) عند صدور تحديث يبدأ التنزيل تلقائياً عند فتح التطبيق، ثم أكّد التثبيت إذا طلب الهاتف ذلك."
        } else {
            "1) Open the APK and allow installing from this source if asked.\n" +
            "2) Open the app and tap Start.\n" +
            "3) The first time, the app opens the \"Display over other apps\" settings. Enable it and come back.\n" +
            "4) When you return, the floating menu starts automatically.\n" +
            "5) When an update is released, it downloads automatically when you open the app. Confirm the install if your phone asks."
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
        val leftV = if (isAr) enBtn else exitBtn
        val rightV = if (isAr) exitBtn else arBtn
        langRow.addView(leftV, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT))
        langRow.addView(View(this), LinearLayout.LayoutParams(0, 1, 1f))
        langRow.addView(rightV, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT))
        root.addView(langRow, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))

        val card = helpCard { sv.smoothScrollTo(0, 0) }

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
            val title = when {
                isInstalling -> if (isAr) "⚙️ جاري تثبيت التحديث..." else "⚙️ Installing update..."
                isDownloading -> if (isAr) "⬇️ جاري تنزيل التحديث تلقائياً..." else "⬇️ Downloading update automatically..."
                else -> if (isAr) "⚠️ يتوفر تحديث جديد للتطبيق!" else "⚠️ New update available!"
            }
            updateBox.addView(label(title, 16f, 0xFF38BDF8.toInt(), true, true))
            updateBox.addView(label(
                if (isAr) "إذا ظهرت نافذة تأكيد التثبيت فاضغط تثبيت"
                else "If an install prompt appears, tap Install",
                12f, Color.WHITE, false, true
            ), LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                topMargin = dp(6)
            })

            if (!isDownloading && !isInstalling) {
                val updateNowBtn = styled(
                    if (isAr) "تنزيل التحديث وتثبيته 🚀" else "Download & Install Update 🚀",
                    0xFF10B981.toInt(), Color.WHITE
                ) { startDirectDownload() }
                updateBox.addView(updateNowBtn, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                    topMargin = dp(10)
                })
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
                    if (isAr) "يوجد تحديث أحدث، يتم تنزيله وتثبيته الآن"
                    else "A new update is available. It is being installed now",
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

        val strip = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(dp(12), dp(10), dp(12), dp(10))
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
        root.addView(strip, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = dp(14) })

        val dm = resources.displayMetrics
        val side = min(dm.widthPixels - dp(32), (dm.heightPixels * 0.8f).toInt())
        val bannerH = side + dp(120)
        root.addView(BannerView(this), LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, bannerH
        ).apply { topMargin = dp(12) })

        root.addView(card, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = dp(16) })

        sv.addView(root)
        setContentView(sv)
        sv.post { sv.scrollTo(0, restoreY) }
    }

    private fun registerCompat(r: BroadcastReceiver, f: IntentFilter) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(r, f, RECEIVER_EXPORTED)
        } else {
            registerReceiver(r, f)
        }
    }

    override fun onCreate(s: Bundle?) {
        super.onCreate(s)
        registerCompat(onDownloadComplete, IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE))
        registerCompat(installStatusReceiver, IntentFilter(ACTION_INSTALL_STATUS))
        val y = getSharedPreferences(Lang.PREFS, MODE_PRIVATE).getInt("main_scroll", 0)
        build(y)
        checkForUpdates()
    }

    override fun onResume() {
        super.onResume()
        if (pendingStart) {
            pendingStart = false
            if (!isUpdateRequired && Settings.canDrawOverlays(this)) {
                launchOverlay()
            }
        }
        val f = pendingInstallFile
        if (f != null && Build.VERSION.SDK_INT >= 26 && packageManager.canRequestPackageInstalls()) {
            pendingInstallFile = null
            beginInstall(f)
        }
        checkForUpdates()
    }

    override fun onDestroy() {
        runCatching { unregisterReceiver(onDownloadComplete) }
        runCatching { unregisterReceiver(installStatusReceiver) }
        super.onDestroy()
    }

    override fun onPause() {
        scroll?.let {
            getSharedPreferences(Lang.PREFS, MODE_PRIVATE).edit().putInt("main_scroll", it.scrollY).apply()
        }
        super.onPause()
    }
}
