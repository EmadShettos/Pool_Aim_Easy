package com.example.poolaim

import android.app.Activity
import android.content.Context
import android.content.Intent
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
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
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
    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    private fun styled(label: String, bg: Int, fg: Int, onClick: () -> Unit): Button =
        Button(this).apply {
            text = label
            isAllCaps = false
            textSize = 14f
            setTextColor(fg)
            background = GradientDrawable().apply {
                setColor(bg)
                cornerRadius = dp(14).toFloat()
                setStroke(dp(1), 0xFFFFD21F.toInt())
            }
            setOnClickListener { onClick() }
        }

    override fun onCreate(s: Bundle?) {
        super.onCreate(s)
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

        root.addView(styled("1) Grant overlay permission", 0xFF14532D.toInt(), Color.WHITE) {
            startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
        }, lp())
        root.addView(styled("2) Start overlay", 0xFFFFD21F.toInt(), Color.BLACK) {
            if (Build.VERSION.SDK_INT >= 33) requestPermissions(arrayOf("android.permission.POST_NOTIFICATIONS"), 1)
            if (!Settings.canDrawOverlays(this)) {
                Toast.makeText(this, "Grant overlay permission first", Toast.LENGTH_LONG).show()
            } else startForegroundService(Intent(this, OverlayService::class.java))
        }, lp())
        root.addView(styled("Stop overlay", 0xFF7F1D1D.toInt(), Color.WHITE) {
            stopService(Intent(this, OverlayService::class.java))
        }, lp())

        val strip = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(dp(12), dp(10), dp(12), dp(10))
            background = GradientDrawable().apply {
                setColor(0xFF0B0B0B.toInt())
                cornerRadius = dp(14).toFloat()
                setStroke(dp(2), 0xFFFFD21F.toInt())
            }
        }
        strip.addView(TextView(this).apply {
            text = "إعداد بواسطة المطور Emad Shettos"
            setTextColor(0xFFFFD21F.toInt())
            textSize = 14f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
        })
        strip.addView(TextView(this).apply {
            text = "WhatsApp: +201093532277"
            setTextColor(0xFFEAEAEA.toInt())
            textSize = 13f
            gravity = Gravity.CENTER
        }, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = dp(8) })
        root.addView(strip, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = dp(14) })

        root.addView(BannerView(this), LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f
        ).apply { topMargin = dp(12) })

        setContentView(root)
    }
}
