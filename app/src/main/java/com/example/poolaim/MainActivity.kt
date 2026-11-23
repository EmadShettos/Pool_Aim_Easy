package com.example.poolaim

import android.app.Activity
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.graphics.*
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.widget.*
import androidx.core.content.FileProvider
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
                // في حالة فشل أو رفض الجلسة (مثلاً بواسطة Play Protect)، نطلق مثبت الحزم المباشر بـ FileProvider
                val out = File(ctx.cacheDir, "update.apk")
                if (out.exists()) {
                    MainActivity.launchFileProviderInstall(ctx, out)
                }
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
    private val gold = 0xFFFFD21F.toInt()
    private val darkGreen = 0xFF0D3B28.toInt()
    private val darkBg = 0xFF051810.toInt()
    private val soft = 0xFFCCCCCC.toInt()

    private var statusText = ""
    private var isUpdateRequired = false
    private var isDownloading = false
    private var isInstalling = false
    private var pendingUpdate = false

    private val REPO = "EmadShettos/Pool_Aim_Easy"
    private val FALLBACK_APK = "https://github.com/EmadShettos/Pool_Aim_Easy/releases/download/latest/app-EmadShettos-debug.apk"
    private val ACTION_INSTALL_RESULT = "com.example.poolaim.INSTALL_RESULT"

    companion object {
        fun launchFileProviderInstall(ctx: Context, file: File) {
            try {
                val uri: Uri = FileProvider.getUriForFile(ctx, "${ctx.packageName}.fileprovider", file)
                val installIntent = Intent(Intent.ACTION_VIEW).apply {
                    setDataAndType(uri, "application/vnd.android.package-archive")
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION
                }
                ctx.startActivity(installIntent)
            } catch (e: Exception) {
                Toast.makeText(ctx, "Install error: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        rebuildUi()
        checkForUpdates()
    }

    private fun ar() = Lang.isAr(this)

    private fun setStatus(msg: String) {
        runOnUiThread {
            statusText = msg
            rebuildUi()
        }
    }

    private fun checkForUpdates() {
        if (isDownloading || isInstalling) return
        Thread {
            try {
                val api = URL("https://api.github.com/repos/$REPO/releases/latest")
                val conn = (api.openConnection() as HttpURLConnection).apply {
                    connectTimeout = 10000
                    readTimeout = 10000
                    setRequestProperty("User-Agent", "PoolAimShettos")
                    setRequestProperty("Accept", "application/vnd.github+json")
                }
                if (conn.responseCode in 200..299) {
                    val body = conn.inputStream.bufferedReader().use { it.readText() }
                    val json = JSONObject(body)
                    val assets = json.optJSONArray("assets")
                    var url = FALLBACK_APK
                    var assetTime = 0L
                    if (assets != null && assets.length() > 0) {
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

    private fun parseTime(iso: String): Long {
        return try {
            val f = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US)
            f.timeZone = TimeZone.getTimeZone("UTC")
            f.parse(iso)?.time ?: 0L
        } catch (_: Exception) { 0L }
    }

    private fun startUpdateFlow(customUrl: String? = null) {
        if (isDownloading || isInstalling) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && !packageManager.canRequestPackageInstalls()) {
            pendingUpdate = true
            setStatus(
                if (ar()) "فعّل \"السماح بتثبيت التطبيقات\" لهذا التطبيق ثم ارجع" else "Allow \"Install unknown apps\" for this app then return"
            )
            val intent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES_SETTINGS).apply {
                data = Uri.parse("package:$packageName")
            }
            startActivity(intent)
            return
        }

        isDownloading = true
        setStatus(if (ar()) "جارٍ تنزيل التحديث... 0%" else "Downloading update... 0%")
        val apkUrl = customUrl ?: FALLBACK_APK

        Thread {
            try {
                val u = URL(apkUrl)
                val conn = (u.openConnection() as HttpURLConnection).apply {
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
                        if (ar()) "تعذر التنزيل، اضغط زر إعادة المحاولة"
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
            // مثبت النظام الاحتياطي المباشر
            launchFileProviderInstall(this, file)
            setStatus(
                if (ar()) "جارٍ فتح مثبت التطبيقات..."
                else "Opening installer..."
            )
        }
    }

    private fun showHelpDialog() {
        val isAr = ar()
        val dlg = android.app.Dialog(this)
        dlg.requestWindowFeature(android.view.Window.FEATURE_NO_TITLE)

        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding((16 * d).toInt(), (16 * d).toInt(), (16 * d).toInt(), (16 * d).toInt())
            background = roundRect(0xFF0F2D1F.toInt(), 16f * d)
        }

        card.addView(label(if (isAr) "كيف تثبت التطبيق وكيف تلعب" else "How to install & play", 22f, gold, true, true))

        val scroll = ScrollView(this)
        val textBody = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }

        textBody.addView(label(if (isAr) "أولاً: تثبيت التطبيق" else "Step 1: Install the app", 16f, gold, true, false))
        val installText = if (isAr) {
            "1) افتح ملف الـ APK ووافق على التثبيت من هذا المصدر إذا طُلب منك ذلك.\n" +
            "2) اضغط زر منح إذن الظهور فوق التطبيقات.\n" +
            "3) اضغط زر تشغيل المساعد.\n" +
            "4) عند توفر تحديث جديد، يقوم التطبيق بالتنزيل والتثبيت التلقائي."
        } else {
            "1) Open the APK and allow installing from this source if asked.\n" +
            "2) Tap Grant overlay permission.\n" +
            "3) Tap Start overlay.\n" +
            "4) When an update is released, the app will download and install automatically."
        }
        textBody.addView(label(installText, 13f, soft, false, false), lp(b = 14))

        scroll.addView(textBody)
        card.addView(scroll, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))

        val closeBtn = styled(if (isAr) "إغلاق" else "Close", gold, Color.BLACK) { dlg.dismiss() }
        card.addView(closeBtn, lp(t = 12))

        dlg.setContentView(card)
        dlg.window?.apply {
            setLayout((resources.displayMetrics.widthPixels * 0.90).toInt(), (resources.displayMetrics.heightPixels * 0.82).toInt())
            setBackgroundDrawable(android.graphics.drawable.ColorDrawable(Color.TRANSPARENT))
        }
        dlg.show()
    }

    private fun rebuildUi() {
        val isAr = ar()
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(darkBg)
            setPadding((18 * d).toInt(), (14 * d).toInt(), (18 * d).toInt(), (14 * d).toInt())
        }

        val topBar = RelativeLayout(this)
        val exitBtn = styled("✕ " + (if (isAr) "خروج" else "Exit"), 0xFF3B1212.toInt(), 0xFFFF8888.toInt()) { finish() }
        val exitLp = RelativeLayout.LayoutParams(RelativeLayout.LayoutParams.WRAP_CONTENT, (38 * d).toInt()).apply {
            addRule(RelativeLayout.ALIGN_PARENT_START)
            addRule(RelativeLayout.CENTER_VERTICAL)
        }
        topBar.addView(exitBtn, exitLp)

        val langBtn = styled("🌐 " + Lang.label(this), darkGreen, gold) {
            Lang.toggle(this)
            rebuildUi()
        }
        val langLp = RelativeLayout.LayoutParams(RelativeLayout.LayoutParams.WRAP_CONTENT, (38 * d).toInt()).apply {
            addRule(RelativeLayout.ALIGN_PARENT_END)
            addRule(RelativeLayout.CENTER_VERTICAL)
        }
        topBar.addView(langBtn, langLp)
        root.addView(topBar, lp(b = 10))

        if (isUpdateRequired) {
            val updateBox = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding((12 * d).toInt(), (10 * d).toInt(), (12 * d).toInt(), (10 * d).toInt())
                background = roundRect(0xFF1E293B.toInt(), 12f * d)
            }
            updateBox.addView(label(
                if (isAr) "⚠️ يتوفر تحديث جديد للتطبيق!" else "⚠️ New update available!",
                15f, gold, true, true
            ))
            val st = label(statusText, 12f, soft, false, true)
            updateBox.addView(st, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                gravity = Gravity.CENTER_HORIZONTAL
                topMargin = (4 * d).toInt()
                bottomMargin = (8 * d).toInt()
            })
            val updateNowBtn = styled(
                if (isAr) "إعادة المحاولة / تحديث الآن 🚀" else "Retry / Update now 🚀",
                0xFF0284C7.toInt(), Color.WHITE
            ) { startUpdateFlow() }
            updateBox.addView(updateNowBtn, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, (42 * d).toInt()))
            root.addView(updateBox, lp())
        }

        root.addView(styled(if (isAr) "1) منح إذن الظهور" else "1) Grant overlay permission", darkGreen, gold) {
            if (!Settings.canDrawOverlays(this)) {
                val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName"))
                startActivity(intent)
            } else {
                Toast.makeText(this, if (isAr) "الإذن ممنوح بالفعل ✅" else "Permission already granted ✅", Toast.LENGTH_SHORT).show()
            }
        }, lp())

        val startBtnLabel = if (isUpdateRequired) {
            if (isAr) "🔒 قفل التشغيل (يجب التحديث أولاً)" else "🔒 Locked (Update required)"
        } else {
            if (isAr) "2) تشغيل المساعد" else "2) Start overlay"
        }
        val startBtnBg = if (isUpdateRequired) 0xFF4B5563.toInt() else gold
        val startBtnFg = if (isUpdateRequired) Color.LTGRAY else Color.BLACK

        root.addView(styled(startBtnLabel, startBtnBg, startBtnFg) {
            if (isUpdateRequired) {
                Toast.makeText(
                    this,
                    if (isAr) "يرجى الانتظار حتى اكتمال التثبيت أو الضغط على زر التحديث أعلاه"
                    else "Updating, please wait for the install to finish",
                    Toast.LENGTH_LONG
                ).show()
                return@styled
            }
            if (!Settings.canDrawOverlays(this)) {
                Toast.makeText(this, if (isAr) "يرجى منح الإذن أولاً" else "Grant permission first", Toast.LENGTH_SHORT).show()
                return@styled
            }
            startService(Intent(this, OverlayService::class.java))
            finish()
        }, lp())

        root.addView(styled(if (isAr) "إيقاف المساعد" else "Stop overlay", darkGreen, gold) {
            stopService(Intent(this, OverlayService::class.java))
            Toast.makeText(this, if (isAr) "تم إيقاف المساعد" else "Overlay stopped", Toast.LENGTH_SHORT).show()
        }, lp())

        root.addView(styled(if (isAr) "كيف تثبت التطبيق وكيف تلعب" else "How to install & play", 0xFF143D2B.toInt(), gold) {
            showHelpDialog()
        }, lp(b = 10))

        val devCard = RelativeLayout(this).apply {
            setPadding((14 * d).toInt(), (10 * d).toInt(), (14 * d).toInt(), (10 * d).toInt())
            background = roundRect(0xFF0F2D1F.toInt(), 12f * d)
        }

        val devTextCol = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            id = View.generateViewId()
        }
        val tDev = label(if (isAr) "المطور: عماد شيتوس" else "Developed by Emad Shettos", 13f, gold, true, false)
        val tWp = label("WhatsApp: +201093532277", 12f, soft, false, false)
        devTextCol.addView(tDev)
        devTextCol.addView(tWp)

        val goBtn = Button(this).apply {
            text = if (isAr) "اذهب" else "Go"
            textSize = 12f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.BLACK)
            background = roundRect(gold, 22f * d)
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
        val btnLp = RelativeLayout.LayoutParams((44 * d).toInt(), (44 * d).toInt()).apply {
            addRule(if (isAr) RelativeLayout.ALIGN_PARENT_START else RelativeLayout.ALIGN_PARENT_END)
            addRule(RelativeLayout.CENTER_VERTICAL)
        }

        devCard.addView(devTextCol, textLp)
        devCard.addView(goBtn, btnLp)
        root.addView(devCard, lp(b = 8))

        val banner = BannerView(this)
        root.addView(banner, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))

        setContentView(root)
    }

    private fun styled(txt: String, bg: Int, fg: Int, onClick: () -> Unit): Button {
        return Button(this).apply {
            text = txt
            setTextColor(fg)
            textSize = 13.5f
            typeface = Typeface.DEFAULT_BOLD
            background = roundRect(bg, 10f * d)
            setOnClickListener { onClick() }
        }
    }

    private fun label(txt: String, sz: Float, c: Int, bold: Boolean, center: Boolean): TextView {
        return TextView(this).apply {
            text = txt
            textSize = sz
            setTextColor(c)
            typeface = if (bold) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
            if (center) gravity = Gravity.CENTER
        }
    }

    private fun lp(t: Int = 4, b: Int = 4): LinearLayout.LayoutParams {
        return LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, (44 * d).toInt()).apply {
            topMargin = (t * d).toInt()
            bottomMargin = (b * d).toInt()
        }
    }

    private fun roundRect(color: Int, rad: Float): android.graphics.drawable.GradientDrawable {
        return android.graphics.drawable.GradientDrawable().apply {
            setColor(color)
            cornerRadius = rad
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
