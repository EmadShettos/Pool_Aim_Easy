package com.example.poolaim

import android.app.Activity
import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
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
import org.json.JSONArray
import java.net.HttpURLConnection
import java.net.URL
import kotlin.concurrent.thread
import kotlin.math.max
import kotlin.math.min

class BannerView(ctx: Context) : View(ctx) {
    private val d = resources.displayMetrics.density
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private val gold = 0xFFFFD21F.toInt()

    private fun fit(text: String, maxW: Float, maxH: Float): Float {
        p.textSize = 100f
        val w = p.measureText(text)
        return min(100f * maxW / w, maxH)
    }

    override fun onDraw(c: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        val r = RectF(2 * d, 2 * d, w - 2 * d, h - 2 * d)

        p.style = Paint.Style.FILL
        p.shader = LinearGradient(0f, 0f, 0f, h, 0xFF12894B.toInt(), 0xFF05301B.toInt(), Shader.TileMode.CLAMP)
        c.drawRoundRect(r, 24 * d, 24 * d, p)
        p.shader = null
        p.style = Paint.Style.STROKE; p.strokeWidth = 3 * d; p.color = gold
        c.drawRoundRect(r, 24 * d, 24 * d, p)
        p.strokeWidth = 1 * d; p.color = Color.argb(110, 255, 210, 31)
        c.drawRoundRect(RectF(r.left + 8 * d, r.top + 8 * d, r.right - 8 * d, r.bottom - 8 * d), 18 * d, 18 * d, p)

        p.style = Paint.Style.FILL
        p.typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
        p.textAlign = Paint.Align.CENTER
        val cx = w / 2
        p.color = Color.WHITE
        p.textSize = fit("Pool Aim", w * 0.72f, h * 0.15f)
        c.drawText("Pool Aim", cx, h * 0.22f, p)
        p.color = gold
        p.textSize = fit("Shettos", w * 0.6f, h * 0.15f)
        c.drawText("Shettos", cx, h * 0.40f, p)

        val cy = h * 0.70f
        val rad = min(w * 0.2f, h * 0.17f)
        p.color = 0xFFB71C1C.toInt(); c.drawCircle(cx, cy, rad, p)
        p.color = 0xFFE53935.toInt(); c.drawCircle(cx - rad * 0.08f, cy - rad * 0.08f, rad * 0.88f, p)
        p.color = Color.argb(115, 255, 255, 255); c.drawCircle(cx - rad * 0.4f, cy - rad * 0.43f, rad * 0.17f, p)

        val hw = rad * 0.6f
        val baseY = cy - rad * 0.5f
        val topY = cy - rad * 1.3f
        val crown = Path().apply {
            moveTo(cx - hw, baseY)
            lineTo(cx - hw, topY + rad * 0.17f)
            lineTo(cx - hw * 0.5f, topY + rad * 0.55f)
            lineTo(cx, topY)
            lineTo(cx + hw * 0.5f, topY + rad * 0.55f)
            lineTo(cx + hw, topY + rad * 0.17f)
            lineTo(cx + hw, baseY)
            close()
        }
        p.color = 0xFFFFC933.toInt(); c.drawPath(crown, p)
        p.style = Paint.Style.STROKE; p.strokeWidth = 2 * d; p.color = 0xFF9A6B00.toInt(); c.drawPath(crown, p)
        p.style = Paint.Style.FILL; p.color = 0xFFC99100.toInt()
        c.drawRect(cx - hw, baseY - rad * 0.2f, cx + hw, baseY, p)
        p.color = Color.WHITE
        c.drawCircle(cx - hw, topY + rad * 0.17f, rad * 0.08f, p)
        c.drawCircle(cx, topY, rad * 0.08f, p)
        c.drawCircle(cx + hw, topY + rad * 0.17f, rad * 0.08f, p)
    }
}

class MainActivity : Activity() {
    private val gold = 0xFFFFD21F.toInt()
    private var scroll: ScrollView? = null
    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
    private fun T(k: String) = Lang.t(this, k)

    private var isUpdateRequired = false
    private var isDownloading = false
    private var pendingStart = false
    private var latestSha: String? = null
    private var downloadId: Long = -1L

    private val REPO = "EmadShettos/Pool_Aim_Easy"
    private val APK_URL = "https://github.com/EmadShettos/Pool_Aim_Easy/releases/download/latest/EmadShettos-PoolAim.apk"
    private val PREF_LAST_SHA = "last_installed_sha"
    private val PREF_PENDING_SHA = "pending_sha"
    private val PREF_PENDING_TIME = "pending_time"

    private val onDownloadComplete = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val id = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1)
            if (id == downloadId) {
                isDownloading = false
                val dm = getSystemService(DOWNLOAD_SERVICE) as DownloadManager
                val fileUri = dm.getUriForDownloadedFile(downloadId)
                if (fileUri != null) {
                    installApk(fileUri)
                } else {
                    Toast.makeText(
                        this@MainActivity,
                        if (Lang.isAr(this@MainActivity)) "فشل تنزيل التحديث" else "Update download failed",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }
    }

    private fun installApk(fileUri: Uri) {
        try {
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
        if (isDownloading) {
            Toast.makeText(this, if (Lang.isAr(this)) "جاري تنزيل التحديث في الخلفية..." else "Downloading update in background...", Toast.LENGTH_SHORT).show()
            return
        }

        getSharedPreferences(Lang.PREFS, MODE_PRIVATE).edit()
            .putString(PREF_PENDING_SHA, latestSha)
            .putLong(PREF_PENDING_TIME, System.currentTimeMillis())
            .apply()

        val request = DownloadManager.Request(Uri.parse(APK_URL)).apply {
            setTitle("Pool Aim Shettos Update")
            setDescription("تحديث التطبيق")
            setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            setDestinationInExternalFilesDir(this@MainActivity, Environment.DIRECTORY_DOWNLOADS, "PoolAim-update.apk")
            setMimeType("application/vnd.android.package-archive")
        }

        val dm = getSystemService(DOWNLOAD_SERVICE) as DownloadManager
        downloadId = dm.enqueue(request)
        isDownloading = true
        Toast.makeText(this, if (Lang.isAr(this)) "بدأ تنزيل التحديث... سيفتح التثبيت عند اكتماله" else "Downloading update... install will open when done", Toast.LENGTH_LONG).show()
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
                val url = URL("https://api.github.com/repos/$REPO/commits?per_page=1")
                val conn = (url.openConnection() as HttpURLConnection).apply {
                    requestMethod = "GET"
                    setRequestProperty("User-Agent", "PoolAimApp")
                    connectTimeout = 4000
                    readTimeout = 4000
                }
                if (conn.responseCode == 200) {
                    val resp = conn.inputStream.bufferedReader().use { it.readText() }
                    val json = JSONArray(resp)
                    if (json.length() > 0) {
                        val sha = json.getJSONObject(0).getString("sha")
                        latestSha = sha
                        val prefs = getSharedPreferences(Lang.PREFS, MODE_PRIVATE)
                        var installedSha = prefs.getString(PREF_LAST_SHA, null)

                        val pendingSha = prefs.getString(PREF_PENDING_SHA, null)
                        if (pendingSha != null) {
                            val pendingTime = prefs.getLong(PREF_PENDING_TIME, 0L)
                            val updatedAt = packageManager.getPackageInfo(packageName, 0).lastUpdateTime
                            if (updatedAt >= pendingTime) {
                                prefs.edit()
                                    .putString(PREF_LAST_SHA, pendingSha)
                                    .remove(PREF_PENDING_SHA)
                                    .remove(PREF_PENDING_TIME)
                                    .apply()
                                installedSha = pendingSha
                            }
                        }

                        if (installedSha == null) {
                            prefs.edit().putString(PREF_LAST_SHA, sha).apply()
                        } else {
                            val needUpdate = installedSha != sha
                            if (needUpdate != isUpdateRequired) {
                                runOnUiThread {
                                    isUpdateRequired = needUpdate
                                    build(scroll?.scrollY ?: 0)
                                }
                            }
                        }
                    }
                }
            } catch (e: Exception) {
            }
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
            "٥) عند صدور تحديث يظهر كرت التحديث ويُقفل زر التشغيل. اضغط تحديث ثم ثبّت الملف الذي ينزل."
        } else {
            "1) Open the APK and allow installing from this source if asked.\n" +
            "2) Open the app and tap Start.\n" +
            "3) The first time, the app opens the \"Display over other apps\" settings. Enable it and come back.\n" +
            "4) When you return, the floating menu starts automatically.\n" +
            "5) When an update is released, an update card appears and Start is locked. Tap update, then install the downloaded file."
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
            updateBox.addView(label(
                if (isAr) "⚠️ يتوفر تحديث جديد للتطبيق!" else "⚠️ New update available!",
                16f, 0xFF38BDF8.toInt(), true, true
            ))
            updateBox.addView(label(
                if (isAr) "اضغط أدناه لتنزيل التحديث وتثبيته للمتابعة"
                else "Tap below to download and install the update",
                12f, Color.WHITE, false, true
            ), LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                topMargin = dp(6)
            })

            val updateNowBtn = styled(
                if (isAr) "تنزيل التحديث وتثبيته 🚀" else "Download & Install Update 🚀",
                0xFF10B981.toInt(), Color.WHITE
            ) { startDirectDownload() }
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
                    if (isAr) "يوجد تحديث أحدث، يرجى الضغط على زر التحديث أعلاه!"
                    else "A new update is available. Please update above!",
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

        val bannerH = max(dp(320), (resources.displayMetrics.heightPixels * 0.5f).toInt())
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

    override fun onCreate(s: Bundle?) {
        super.onCreate(s)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(onDownloadComplete, IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE), RECEIVER_EXPORTED)
        } else {
            registerReceiver(onDownloadComplete, IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE))
        }
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
        checkForUpdates()
    }

    override fun onDestroy() {
        runCatching { unregisterReceiver(onDownloadComplete) }
        super.onDestroy()
    }

    override fun onPause() {
        scroll?.let {
            getSharedPreferences(Lang.PREFS, MODE_PRIVATE).edit().putInt("main_scroll", it.scrollY).apply()
        }
        super.onPause()
    }
}
