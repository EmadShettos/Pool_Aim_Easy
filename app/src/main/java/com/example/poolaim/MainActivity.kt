package com.example.poolaim

import android.app.Activity
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
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
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.RelativeLayout
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

class InstallResultReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)
        when (status) {
            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                @Suppress("DEPRECATION")
                val confirm = intent.getParcelableExtra<Intent>(Intent.EXTRA_INTENT)
                if (confirm != null) {
                    confirm.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    ctx.startActivity(confirm)
                }
            }
            PackageInstaller.STATUS_SUCCESS -> {}
            else -> {
                val msg = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE) ?: ""
                Toast.makeText(ctx, "Update install failed: $msg", Toast.LENGTH_LONG).show()
            }
        }
    }
}

class BannerView(ctx: Context) : View(ctx) {
    private val d = resources.displayMetrics.density
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private val bmpPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val gold = 0xFFFFD21F.toInt()
    private val topC = 0xFF0D5A3A.toInt()
    private val botC = 0xFF052417.toInt()

    private val bmp: Bitmap? by lazy {
        BitmapFactory.decodeResource(resources, R.drawable.pool_aim_icon)
    }

    override fun onDraw(c: Canvas) {
        super.onDraw(c)
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0f || h <= 0f) return

        p.shader = LinearGradient(0f, 0f, 0f, h, topC, botC, Shader.TileMode.CLAMP)
        p.style = Paint.Style.FILL
        c.drawRect(0f, 0f, w, h, p)
        p.shader = null

        p.color = gold
        p.textSize = 28f * d
        p.isFakeBoldText = true
        p.textAlign = Paint.Align.CENTER
        c.drawText("Pool Aim Shettos", w / 2f, 42f * d, p)

        bmp?.let { b ->
            val iconSize = (h - 70f * d).coerceAtLeast(100f * d)
            val left = (w - iconSize) / 2f
            val top = 55f * d
            val dst = RectF(left, top, left + iconSize, top + iconSize)
            c.drawBitmap(b, null, dst, bmpPaint)
        }
    }
}

class MainActivity : Activity() {
    private val d by lazy { resources.displayMetrics.density }
    private fun dp(v: Int) = (v * d).toInt()

    private val REPO = "EmadShettos/Pool_Aim_Easy"
    private val FALLBACK_APK = "https://github.com/EmadShettos/Pool_Aim_Easy/releases/download/latest/app-EmadShettos-debug.apk"
    private val ACTION_INSTALL_RESULT = "com.example.poolaim.INSTALL_RESULT"

    private var statusText = ""
    private var isUpdateRequired = false
    private var isDownloading = false
    private var isInstalling = false
    private var pendingUpdate = false
    private var scroll: ScrollView? = null

    private fun T(k: String) = Lang.t(this, k)
    private fun ar() = Lang.isAr(this)

    private fun setStatus(msg: String) {
        runOnUiThread {
            statusText = msg
            rebuildUi()
        }
    }

    private fun parseTime(s: String): Long = try {
        val f = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US)
        f.timeZone = TimeZone.getTimeZone("UTC")
        f.parse(s)?.time ?: 0L
    } catch (_: Exception) { 0L }

    private fun checkForUpdates() {
        if (isDownloading || isInstalling) return
        Thread {
            try {
                val u = URL("https://api.github.com/repos/$REPO/releases/latest")
                val conn = (u.openConnection() as HttpURLConnection).apply {
                    connectTimeout = 8000
                    readTimeout = 8000
                    setRequestProperty("User-Agent", "PoolAimShettos")
                    setRequestProperty("Accept", "application/vnd.github+json")
                }
                if (conn.responseCode in 200..299) {
                    val body = conn.inputStream.bufferedReader().use { it.readText() }
                    val json = JSONObject(body)
                    val assets = json.optJSONArray("assets")
                    var url = FALLBACK_APK
                    var assetTime = 0L
                    if (assets != null) {
                        for (i in 0 until assets.length()) {
                            val a = assets.getJSONObject(i)
                            val name = a.optString("name", "")
                            if (name.endsWith(".apk")) {
                                url = a.optString("browser_download_url", FALLBACK_APK)
                                assetTime = parseTime(a.getString("updated_at"))
                                break
                            }
                        }
                    }
                    val installedAt = packageManager.getPackageInfo(packageName, 0).lastUpdateTime
                    val need = assetTime > installedAt
                    runOnUiThread {
                        if (need != isUpdateRequired) {
                            isUpdateRequired = need
                            if (need) statusText = if (ar()) "تم العثور على تحديث، جارٍ التجهيز..." else "Update found, preparing..."
                            rebuildUi()
                            if (need) startUpdateFlow(url)
                        }
                    }
                }
            } catch (_: Exception) {}
        }.start()
    }

    private fun startUpdateFlow(customUrl: String? = null) {
        if (isDownloading || isInstalling) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && !packageManager.canRequestPackageInstalls()) {
            pendingUpdate = true
            setStatus(
                if (ar()) "فعّل \"السماح بتثبيت التطبيقات\" لهذا التطبيق ثم ارجع"
                else "Allow \"Install unknown apps\" for this app then return"
            )
            startActivity(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:$packageName")))
            return
        }

        isDownloading = true
        setStatus(if (ar()) "جارٍ تنزيل التحديث... 0%" else "Downloading update... 0%")
        val apkUrl = customUrl ?: FALLBACK_APK

        Thread {
            try {
                val conn = (URL(apkUrl).openConnection() as HttpURLConnection).apply {
                    connectTimeout = 15000
                    readTimeout = 30000
                    instanceFollowRedirects = true
                    setRequestProperty("User-Agent", "PoolAimShettos")
                }
                val total = conn.contentLength
                val out = File(cacheDir, "update.apk")
                val tmp = File(cacheDir, "update.apk.part")

                conn.inputStream.use { ins ->
                    tmp.outputStream().use { os ->
                        val buf = ByteArray(8192)
                        var read: Int
                        var cur = 0L
                        var lastPct = -1
                        while (ins.read(buf).also { read = it } != -1) {
                            os.write(buf, 0, read)
                            cur += read
                            if (total > 0) {
                                val pct = ((cur * 100) / total).toInt()
                                if (pct != lastPct) {
                                    lastPct = pct
                                    setStatus(if (ar()) "جارٍ تنزيل التحديث... $pct%" else "Downloading update... $pct%")
                                }
                            }
                        }
                    }
                }
                if (out.exists()) out.delete()
                tmp.renameTo(out)

                runOnUiThread {
                    isDownloading = false
                    installApk(out)
                }
            } catch (e: Exception) {
                runOnUiThread {
                    isDownloading = false
                    setStatus(
                        if (ar()) "تعذر التنزيل، اضغط الزر للمحاولة مرة أخرى"
                        else "Download failed, tap the button to retry"
                    )
                }
            }
        }.start()
    }

    private fun installApk(file: File) {
        try {
            isInstalling = true
            setStatus(if (ar()) "جارٍ التثبيت..." else "Installing...")
            val pi = packageManager.packageInstaller
            val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL)
            params.setAppPackageName(packageName)
            if (Build.VERSION.SDK_INT >= 31) {
                params.setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED)
            }
            val id = pi.createSession(params)
            val session = pi.openSession(id)
            file.inputStream().use { ins ->
                session.openWrite("update.apk", 0, file.length()).use { os ->
                    ins.copyTo(os)
                    session.fsync(os)
                }
            }
            val intent = Intent(this, InstallResultReceiver::class.java).setAction(ACTION_INSTALL_RESULT)
            val flags = PendingIntent.FLAG_UPDATE_CURRENT or
                (if (Build.VERSION.SDK_INT >= 31) PendingIntent.FLAG_MUTABLE else 0)
            val pending = PendingIntent.getBroadcast(this, id, intent, flags)
            session.commit(pending.intentSender)
            session.close()
        } catch (e: Exception) {
            isInstalling = false
            setStatus(
                if (ar()) "تعذر التثبيت، اضغط الزر للمحاولة مرة أخرى"
                else "Install failed, tap the button to retry"
            )
        }
    }

    private fun styled(label: String, bg: Int, fg: Int, onClick: () -> Unit): Button =
        Button(this).apply {
            text = label
            isAllCaps = false
            textSize = 14f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(fg)
            background = GradientDrawable().apply {
                setColor(bg)
                cornerRadius = 12f * d
            }
            setOnClickListener { onClick() }
        }

    private fun label(s: String, sz: Float, c: Int, bold: Boolean, center: Boolean): TextView =
        TextView(this).apply {
            text = s
            textSize = sz
            setTextColor(c)
            typeface = if (bold) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
            if (center) gravity = Gravity.CENTER
        }

    private fun smallBtn(label: String, bg: Int, fg: Int, onClick: () -> Unit): Button =
        Button(this).apply {
            text = label
            isAllCaps = false
            textSize = 11.5f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(fg)
            background = GradientDrawable().apply {
                setColor(bg)
                cornerRadius = 8f * d
                setStroke(dp(1), 0x33FFFFFF)
            }
            setPadding(dp(12), dp(6), dp(12), dp(6))
            setOnClickListener { onClick() }
        }

    private fun showHelpDialog() {
        val isAr = Lang.isAr(this)
        val dlg = android.app.Dialog(this)
        dlg.requestWindowFeature(android.view.Window.FEATURE_NO_TITLE)

        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(16), dp(16), dp(16))
            background = GradientDrawable().apply {
                setColor(0xFF0F2D1F.toInt())
                cornerRadius = 16f * d
            }
        }

        card.addView(label(if (isAr) "كيف تثبت التطبيق وكيف تلعب" else "How to install & play", 22f, 0xFFFFD21F.toInt(), true, true))

        val scroll = ScrollView(this)
        val textBody = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }

        textBody.addView(label(if (isAr) "أولاً: تثبيت التطبيق" else "Step 1: Install the app", 16f, 0xFFFFD21F.toInt(), true, false))
        val installText = if (isAr) {
            "1) افتح ملف الـ APK ووافق على التثبيت من هذا المصدر إذا طُلب منك ذلك.\n" +
            "2) اضغط زر منح إذن الظهور فوق التطبيقات.\n" +
            "3) اضغط زر تشغيل المساعد.\n" +
            "4) عند توفر تحديث جديد، يظهر لك إشعار التحديث في هذه الشاشة."
        } else {
            "1) Open the APK and allow installing from this source if asked.\n" +
            "2) Tap Grant overlay permission.\n" +
            "3) Tap Start overlay.\n" +
            "4) When an update is released, the update card appears here."
        }
        textBody.addView(label(installText, 13f, 0xFFCCCCCC.toInt(), false, false))

        scroll.addView(textBody)
        card.addView(scroll, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))

        val closeBtn = styled(if (isAr) "إغلاق" else "Close", 0xFFFFD21F.toInt(), Color.BLACK) { dlg.dismiss() }
        card.addView(closeBtn, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(44)).apply { topMargin = dp(12) })

        dlg.setContentView(card)
        dlg.window?.apply {
            setLayout((resources.displayMetrics.widthPixels * 0.90).toInt(), (resources.displayMetrics.heightPixels * 0.82).toInt())
            setBackgroundDrawable(android.graphics.drawable.ColorDrawable(Color.TRANSPARENT))
        }
        dlg.show()
    }

    private fun devCard(): View {
        val isAr = Lang.isAr(this)
        val gold = 0xFFFFD21F.toInt()

        val card = RelativeLayout(this).apply {
            setPadding(dp(14), dp(10), dp(14), dp(10))
            background = GradientDrawable().apply {
                setColor(0xFF0F2D1F.toInt())
                cornerRadius = 12f * d
            }
        }

        val textCol = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            id = View.generateViewId()
        }
        val tDev = label(if (isAr) "المطور: عماد شيتوس" else "Developed by Emad Shettos", 13f, gold, true, false)
        val tWp = label("WhatsApp: +201093532277", 12f, 0xFFCCCCCC.toInt(), false, false)
        textCol.addView(tDev)
        textCol.addView(tWp)

        val goBtn = Button(this).apply {
            text = if (isAr) "اذهب" else "Go"
            textSize = 12f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.BLACK)
            background = GradientDrawable().apply {
                setColor(gold)
                cornerRadius = 22f * d
            }
            setOnClickListener {
                try {
                    val url = "https://wa.me/201093532277"
                    startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                } catch (_: Exception) {
                    Toast.makeText(this@MainActivity, "WhatsApp not available", Toast.LENGTH_SHORT).show()
                }
            }
        }

        val textLp = RelativeLayout.LayoutParams(RelativeLayout.LayoutParams.WRAP_CONTENT, RelativeLayout.LayoutParams.WRAP_CONTENT).apply {
            addRule(if (isAr) RelativeLayout.ALIGN_PARENT_END else RelativeLayout.ALIGN_PARENT_START)
            addRule(RelativeLayout.CENTER_VERTICAL)
        }
        val btnLp = RelativeLayout.LayoutParams(dp(44), dp(44)).apply {
            addRule(if (isAr) RelativeLayout.ALIGN_PARENT_START else RelativeLayout.ALIGN_PARENT_END)
            addRule(RelativeLayout.CENTER_VERTICAL)
        }

        card.addView(textCol, textLp)
        card.addView(goBtn, btnLp)
        card.layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
            topMargin = dp(18)
        }
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
        fun lp() = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(44)).apply { topMargin = dp(10) }

        val isAr = Lang.isAr(this)
        val langRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        val exitBtn = smallBtn("✕  ${T("exit_btn")}", 0xFF2A0D0D.toInt(), 0xFFFF6B6B.toInt()) { finish() }
        val enBtn = smallBtn("🇬🇧  ${T("lang_en_btn")}", 0xFF0B0B0B.toInt(), Color.WHITE) { switchLang(false) }
        val arBtn = smallBtn("🇸🇦  ${T("lang_ar_btn")}", 0xFF0B0B0B.toInt(), Color.WHITE) { switchLang(true) }

        val leftV: View = if (isAr) arBtn else exitBtn
        val rightV: View = if (isAr) exitBtn else arBtn

        langRow.addView(leftV, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, dp(34)))
        langRow.addView(View(this), LinearLayout.LayoutParams(0, 1, 1f))
        langRow.addView(rightV, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, dp(34)))
        root.addView(langRow, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))

        if (isUpdateRequired) {
            val updateBox = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(12), dp(10), dp(12), dp(10))
                background = GradientDrawable().apply {
                    setColor(0xFF1E293B.toInt())
                    cornerRadius = 12f * d
                }
            }
            updateBox.addView(label(
                if (isAr) "⚠️ يتوفر تحديث جديد للتطبيق!" else "⚠️ New update available!",
                15f, 0xFFFFD21F.toInt(), true, true
            ))
            val st = label(statusText, 12f, 0xFFCCCCCC.toInt(), false, true)
            updateBox.addView(st, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                gravity = Gravity.CENTER_HORIZONTAL
                topMargin = dp(4)
                bottomMargin = dp(8)
            })
            val updateNowBtn = styled(
                if (isAr) "إعادة المحاولة / تحديث الآن 🚀" else "Retry / Update now 🚀",
                0xFF0284C7.toInt(), Color.WHITE
            ) { startUpdateFlow() }
            updateBox.addView(updateNowBtn, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(42)))
            root.addView(updateBox, lp())
        }

        root.addView(styled("1)  ${T("btn_grant")}", 0xFF14532D.toInt(), 0xFFFFD21F.toInt()) {
            if (!Settings.canDrawOverlays(this)) {
                startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
            } else {
                Toast.makeText(this, if (isAr) "الإذن ممنوح بالفعل ✅" else "Permission already granted ✅", Toast.LENGTH_SHORT).show()
            }
        }, lp())

        val startBtnLabel = if (isUpdateRequired) {
            if (isAr) "🔒 قفل التشغيل (يجب التحديث أولاً)" else "🔒 Locked (Update required)"
        } else {
            "2)  ${T("btn_start")}"
        }
        val startBtnBg = if (isUpdateRequired) 0xFF4B5563.toInt() else 0xFFFFD21F.toInt()
        val startBtnFg = if (isUpdateRequired) Color.LTGRAY else Color.BLACK

        root.addView(styled(startBtnLabel, startBtnBg, startBtnFg) {
            if (isUpdateRequired) {
                Toast.makeText(
                    this,
                    if (isAr) "يرجى التحديث أولاً للتشغيل" else "Please update first to start",
                    Toast.LENGTH_LONG
                ).show()
                return@styled
            }
            if (!Settings.canDrawOverlays(this)) {
                Toast.makeText(this, T("need_perm"), Toast.LENGTH_SHORT).show()
                return@styled
            }
            startService(Intent(this, OverlayService::class.java))
            finish()
        }, lp())

        root.addView(styled(T("btn_stop"), 0xFF14532D.toInt(), 0xFFFFD21F.toInt()) {
            stopService(Intent(this, OverlayService::class.java))
            Toast.makeText(this, T("stopped"), Toast.LENGTH_SHORT).show()
        }, lp())

        root.addView(styled(if (isAr) "كيف تثبت التطبيق وكيف تلعب" else "How to install & play", 0xFF122C1E.toInt(), 0xFFFFD21F.toInt()) {
            showHelpDialog()
        }, lp())

        root.addView(devCard())

        val banner = BannerView(this)
        root.addView(banner, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(220)).apply {
            topMargin = dp(16)
        })

        sv.addView(root)
        setContentView(sv)

        if (restoreY > 0) sv.post { sv.scrollTo(0, restoreY) }
    }

    private fun rebuildUi() {
        val y = scroll?.scrollY ?: 0
        build(y)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val y = getSharedPreferences(Lang.PREFS, MODE_PRIVATE).getInt("main_scroll", 0)
        build(y)
        checkForUpdates()
    }

    override fun onPause() {
        super.onPause()
        scroll?.let {
            getSharedPreferences(Lang.PREFS, MODE_PRIVATE).edit().putInt("main_scroll", it.scrollY).apply()
        }
    }

    override fun onResume() {
        super.onResume()
        if (pendingUpdate && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && packageManager.canRequestPackageInstalls()) {
            pendingUpdate = false
            startUpdateFlow()
        }
        checkForUpdates()
    }
}
