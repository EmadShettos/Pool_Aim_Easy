package com.example.poolaim

import android.app.Activity
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
    companion object {
        var instance: MainActivity? = null
    }

    private val gold = 0xFFFFD21F.toInt()
    private var scroll: ScrollView? = null
    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
    private fun T(k: String) = Lang.t(this, k)

    private var isUpdateRequired = false
    private var isDownloading = false
    private var autoTried = false
    private var apkUrl: String? = null
    private var statusText: String = ""
    private var statusView: TextView? = null
    private var videoSlider: VideoSliderView? = null

    private val RELEASE_API = "https://api.github.com/repos/EmadShettos/Pool_Aim_Easy/releases/tags/${BuildConfig.UPDATE_TAG}"
    private val FALLBACK_APK = "https://github.com/EmadShettos/Pool_Aim_Easy/releases/download/${BuildConfig.UPDATE_TAG}/EmadShettos-PoolAim.apk"
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
                    connectTimeout = 6000
                    readTimeout = 6000
                }
                if (conn.responseCode != 200) return@thread
                val resp = conn.inputStream.bufferedReader().use { it.readText() }
                val jsonObj = JSONObject(resp)
                val releasePublishedAt = parseTime(jsonObj.optString("published_at"))
                val assets = jsonObj.getJSONArray("assets")
                var url: String? = null
                var assetTime = 0L
                for (i in 0 until assets.length()) {
                    val a = assets.getJSONObject(i)
                    if (a.getString("name").endsWith(".apk")) {
                        url = a.optString("browser_download_url", FALLBACK_APK)
                        assetTime = parseTime(a.getString("updated_at"))
                        break
                    }
                }
                if (url == null) return@thread

                val checkTime = if (assetTime > 0) assetTime else releasePublishedAt
                val installedAt = packageManager.getPackageInfo(packageName, 0).lastUpdateTime

                val need = checkTime > (installedAt + 180_000L)
                apkUrl = url
                runOnUiThread {
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
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && !packageManager.canRequestPackageInstalls()) {
            Toast.makeText(
                this,
                if (ar()) "فعّل إذن تثبيت التطبيقات لهذا التطبيق ثم ارجع" else "Allow Install unknown apps for this app, then come back",
                Toast.LENGTH_LONG
            ).show()
            startActivity(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:$packageName")))
            return
        }
        isDownloading = true
        setStatus(if (ar()) "جارٍ تنزيل التحديث... 0%" else "Downloading update... 0%")
        val link = apkUrl ?: FALLBACK_APK
        thread {
            try {
                val out = File(cacheDir, "update.apk")
                val tmp = File(cacheDir, "update.apk.part")
                if (tmp.exists()) tmp.delete()
                val conn = (URL(link).openConnection() as HttpURLConnection).apply {
                    instanceFollowRedirects = true
                    setRequestProperty("User-Agent", "PoolAimApp")
                    connectTimeout = 15000
                    readTimeout = 30000
                }
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
                if (tmp.length() < 100_000L) throw Exception("file too small")
                if (out.exists()) out.delete()
                tmp.renameTo(out)
                runOnUiThread {
                    isDownloading = false
                    setStatus(if (ar()) "جارٍ فتح التثبيت..." else "Opening installer...")
                    installApkDirect(out)
                }
            } catch (e: Exception) {
                isDownloading = false
                setStatus(
                    if (ar()) "فشل التنزيل، اضغط الزر للمحاولة مرة أخرى"
                    else "Download failed, tap the button to retry"
                )
            }
        }
    }

    private fun installApkDirect(file: File) {
        try {
            val uri = Uri.parse("content://$packageName.apkprovider/update.apk")
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            startActivity(intent)
        } catch (e: Exception) {
            setStatus(if (ar()) "تعذر فتح التثبيت: ${e.message}" else "Could not open installer: ${e.message}")
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
            "٥) عند صدور تحديث يظهر كرت التحديث ويبدأ التنزيل والتثبيت تلقائياً. إذا ظهرت نافذة تأكيد التثبيت فاضغط تثبيت."
        } else {
            "1) Open the APK and allow installing from this source if asked.\n" +
            "2) Open the app and tap Start.\n" +
            "3) The first time, the app opens the 'Display over other apps' settings. Enable it and come back.\n" +
            "4) When you return, the floating menu starts automatically.\n" +
            "5) When an update is released, the update card appears and download/install starts automatically. If an install confirmation appears, tap Install."
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
        val exitBtn = smallBtn("✕  ${T("exit")}", 0xFF7F1D1D.toInt(), Color.WHITE) {
            stopService(Intent(this, OverlayService::class.java))
            finishAffinity()
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
            setPadding(dp(64), dp(10), dp(64), dp(10))
            background = GradientDrawable().apply {
                setColor(0xFF0B0B0B.toInt())
                cornerRadius = dp(14).toFloat()
                setStroke(dp(2), gold)
            }
        }
        strip.addView(label(T("dev"), 14f, gold, true, true))
        strip.addView(label(T("whatsapp"), 13f, 0xFFEAEAEA.toInt(), false, true),
            LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(2) })

        val goBtn = styled(T("go"), gold, Color.BLACK) { openWhatsApp() }.apply {
            minWidth = 0; minimumWidth = 0; minHeight = 0; minimumHeight = 0
            setPadding(dp(14), dp(10), dp(14), dp(10))
            textSize = 14f
            typeface = Typeface.DEFAULT_BOLD
        }
        val stripRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        stripRow.addView(strip, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        stripRow.addView(goBtn, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
            marginStart = dp(8)
        })
        root.addView(stripRow, lp())

        root.addView(BannerView(this), LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, dp(260)
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
        instance = this
        build(0)
    }

    override fun onResume() {
        super.onResume()
        checkForUpdates()
        videoSlider?.playCurrent()
    }

    override fun onPause() {
        super.onPause()
        videoSlider?.pauseCurrent()
    }

    override fun onDestroy() {
        super.onDestroy()
        if (instance == this) instance = null
        videoSlider?.release()
    }
}
