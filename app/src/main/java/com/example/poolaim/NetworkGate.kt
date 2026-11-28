package com.example.poolaim

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import java.net.HttpURLConnection
import java.net.URL
import kotlin.concurrent.thread
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

class SpinnerView(ctx: Context) : View(ctx) {
    private val d = resources.displayMetrics.density
    private val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        strokeCap = Paint.Cap.ROUND
        strokeWidth = 4f * d
    }
    private val h = Handler(Looper.getMainLooper())
    private var step = 0
    private val tick = object : Runnable {
        override fun run() {
            step = (step + 1) % SPOKES
            invalidate()
            h.postDelayed(this, 85L)
        }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        h.postDelayed(tick, 85L)
    }

    override fun onDetachedFromWindow() {
        h.removeCallbacks(tick)
        super.onDetachedFromWindow()
    }

    override fun onDraw(c: Canvas) {
        val cx = width / 2f
        val cy = height / 2f
        val outer = min(cx, cy) - 2f * d
        val inner = outer * 0.5f
        for (i in 0 until SPOKES) {
            val age = (step - i + SPOKES) % SPOKES
            val a = (255 - age * 17).coerceAtLeast(45)
            p.color = Color.argb(a, 255, 255, 255)
            val ang = Math.toRadians(i * 360.0 / SPOKES - 90.0)
            val ca = cos(ang).toFloat()
            val sa = sin(ang).toFloat()
            c.drawLine(cx + ca * inner, cy + sa * inner, cx + ca * outer, cy + sa * outer, p)
        }
    }

    companion object {
        private const val SPOKES = 12
    }
}

/**
 * بوابة الإنترنت: تظهر بطاقة «جارٍ الاتصال بالخادم» مع مؤشر دائري دوار،
 * ثم تخبر الخدمة هل يوجد اتصال فعلي بالإنترنت أم لا.
 */
class NetworkGate(private val ctx: Context, private val wm: WindowManager) {
    private val ui = Handler(Looper.getMainLooper())
    private val d = ctx.resources.displayMetrics.density
    private var card: View? = null
    @Volatile private var cancelled = false

    private fun ar() = Lang.isAr(ctx)
    private fun dp(v: Int) = (v * d).toInt()

    private fun hasNetwork(): Boolean {
        val cm = ctx.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return false
        val net = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(net) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    private fun reachable(): Boolean {
        for ((url, strict) in TEST_URLS) {
            var conn: HttpURLConnection? = null
            try {
                conn = (URL(url).openConnection() as HttpURLConnection).apply {
                    requestMethod = "GET"
                    instanceFollowRedirects = false
                    connectTimeout = 4000
                    readTimeout = 4000
                    setRequestProperty("User-Agent", "PoolAimApp")
                    setRequestProperty("Cache-Control", "no-cache")
                }
                val code = conn.responseCode
                if (if (strict) code == 204 else code in 200..499) return true
            } catch (e: Exception) {
            } finally {
                runCatching { conn?.disconnect() }
            }
        }
        return false
    }

    fun run(onResult: (Boolean) -> Unit) {
        show(
            spinner = true,
            title = if (ar()) "جارٍ الاتصال بالخادم" else "Connecting to the server",
            msg = if (ar()) "يرجى الانتظار" else "Please wait",
            touchable = false,
            button = null,
            onButton = null
        )
        val t0 = System.currentTimeMillis()
        thread {
            val ok = hasNetwork() && reachable()
            val wait = (MIN_SHOW_MS - (System.currentTimeMillis() - t0)).coerceAtLeast(0L)
            ui.postDelayed({
                if (!cancelled) {
                    dismiss()
                    onResult(ok)
                }
            }, wait)
        }
    }

    fun showNoInternet(onClosed: () -> Unit) {
        var closed = false
        val close = {
            if (!closed) {
                closed = true
                dismiss()
                onClosed()
            }
        }
        show(
            spinner = false,
            title = if (ar()) "لا يوجد اتصال بالإنترنت" else "No internet connection",
            msg = if (ar()) "فعّل بيانات الهاتف أو شبكة الواي فاي ثم اضغط Start مرة أخرى."
            else "Turn on mobile data or Wi-Fi, then tap Start again.",
            touchable = true,
            button = if (ar()) "حسناً" else "OK",
            onButton = { close() }
        )
        ui.postDelayed({ close() }, 7000L)
    }

    fun cancel() {
        cancelled = true
        ui.removeCallbacksAndMessages(null)
        dismiss()
    }

    fun dismiss() {
        val v = card
        card = null
        if (v != null) runCatching { wm.removeView(v) }
    }

    private fun show(
        spinner: Boolean,
        title: String,
        msg: String,
        touchable: Boolean,
        button: String?,
        onButton: (() -> Unit)?
    ) {
        dismiss()
        val dm = ctx.resources.displayMetrics
        val box = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(dp(24), dp(24), dp(24), dp(20))
            background = GradientDrawable().apply {
                setColor(0xF2202124.toInt())
                cornerRadius = dp(24).toFloat()
            }
        }
        val matchW = LinearLayout.LayoutParams.MATCH_PARENT
        val wrap = LinearLayout.LayoutParams.WRAP_CONTENT
        if (spinner) box.addView(SpinnerView(ctx), LinearLayout.LayoutParams(dp(56), dp(56)))
        box.addView(
            TextView(ctx).apply {
                text = title
                textSize = 18f
                setTextColor(Color.WHITE)
                typeface = Typeface.DEFAULT_BOLD
                gravity = Gravity.CENTER
            },
            LinearLayout.LayoutParams(matchW, wrap).apply { topMargin = if (spinner) dp(16) else 0 }
        )
        box.addView(
            TextView(ctx).apply {
                text = msg
                textSize = 14f
                setTextColor(0xFFB8B8B8.toInt())
                gravity = Gravity.CENTER
            },
            LinearLayout.LayoutParams(matchW, wrap).apply { topMargin = dp(8) }
        )
        if (button != null) {
            val b = Button(ctx).apply {
                text = button
                isAllCaps = false
                textSize = 14f
                setTextColor(Color.BLACK)
                background = GradientDrawable().apply {
                    setColor(0xFFFFD21F.toInt())
                    cornerRadius = dp(14).toFloat()
                }
                minHeight = 0
                minimumHeight = dp(40)
                setOnClickListener { onButton?.invoke() }
            }
            box.addView(b, LinearLayout.LayoutParams(matchW, wrap).apply { topMargin = dp(16) })
        }
        val w = min(dp(320), (min(dm.widthPixels, dm.heightPixels) * 0.86f).toInt())
        var flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
        if (!touchable) flags = flags or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
        val lp = WindowManager.LayoutParams(
            w, WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            flags, PixelFormat.TRANSLUCENT
        ).apply { gravity = Gravity.CENTER }
        wm.addView(box, lp)
        card = box
    }

    companion object {
        private const val MIN_SHOW_MS = 900L
        private val TEST_URLS = listOf(
            "https://www.google.com/generate_204" to true,
            "https://api.github.com/" to false
        )
    }
}
