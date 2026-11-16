package com.example.poolaim

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.WindowManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import kotlin.math.hypot
import kotlin.math.min

class BallView(ctx: Context) : View(ctx) {
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    override fun onDraw(c: Canvas) {
        val cx = width / 2f
        val cy = height / 2f
        val r = min(cx, cy) - 2f
        p.style = Paint.Style.FILL; p.color = Color.BLACK; c.drawCircle(cx, cy, r, p)
        p.color = Color.RED; c.drawCircle(cx, cy, r * 0.38f, p)
        p.color = Color.argb(120, 255, 255, 255); c.drawCircle(cx - r * 0.45f, cy - r * 0.45f, r * 0.14f, p)
        p.style = Paint.Style.STROKE; p.strokeWidth = 2f; p.color = Color.argb(160, 255, 255, 255)
        c.drawCircle(cx, cy, r, p)
    }
}

class OverlayService : Service() {
    private lateinit var wm: WindowManager
    private lateinit var view: OverlayView
    private lateinit var controls: LinearLayout
    private lateinit var controlsParams: WindowManager.LayoutParams
    private lateinit var overlayParams: WindowManager.LayoutParams
    private val handles = mutableListOf<Handle>()
    private val d get() = resources.displayMetrics.density

    override fun onBind(i: Intent?): IBinder? = null

    private fun cutoutFull(lp: WindowManager.LayoutParams) {
        if (Build.VERSION.SDK_INT >= 30) {
            lp.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
        } else if (Build.VERSION.SDK_INT >= 28) {
            lp.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        }
    }

    private inner class Handle(val key: String) {
        val size = (30 * resources.displayMetrics.density).toInt()
        val v = View(this@OverlayService)
        val lp = WindowManager.LayoutParams(
            size, size, WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply { gravity = Gravity.TOP or Gravity.START; alpha = 0.79f }
        var attached = false
        private var sx = 0f
        private var sy = 0f
        private var ox = 0f
        private var oy = 0f

        init {
            cutoutFull(lp)
            v.setOnTouchListener { _, e ->
                when (e.actionMasked) {
                    MotionEvent.ACTION_DOWN -> {
                        sx = e.rawX; sy = e.rawY
                        val p = view.pos(key); ox = p.x; oy = p.y
                        true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        view.setPoint(key, ox + (e.rawX - sx), oy + (e.rawY - sy), false)
                        syncHandles(); true
                    }
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                        view.setPoint(key, ox + (e.rawX - sx), oy + (e.rawY - sy), true)
                        view.save(); syncHandles(); true
                    }
                    else -> false
                }
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        startAsForeground()
        wm = getSystemService(WINDOW_SERVICE) as WindowManager
        view = OverlayView(this)
        view.locked = false
        overlayParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
            PixelFormat.TRANSLUCENT
        )
        overlayParams.alpha = 0.79f
        cutoutFull(overlayParams)
        wm.addView(view, overlayParams)
        for (k in listOf("p1", "p2", "marker", "tl", "br")) handles.add(Handle(k))
        view.onChanged = { syncHandles() }
        buildControls()
        syncHandles()
        view.post { syncHandles() }
    }

    private fun syncHandles() {
        if (!::view.isInitialized) return
        val loc = IntArray(2)
        view.getLocationOnScreen(loc)
        for (h in handles) {
            val active = when (h.key) {
                "tl", "br" -> !view.locked && !view.rectHidden
                "marker" -> view.flags["lines"] == true
                else -> true
            }
            val p = view.touchPos(h.key)
            h.lp.x = (loc[0] + p.x - h.size / 2f).toInt()
            h.lp.y = (loc[1] + p.y - h.size / 2f).toInt()
            if (active && !h.attached) { wm.addView(h.v, h.lp); h.attached = true }
            else if (active) wm.updateViewLayout(h.v, h.lp)
            else if (h.attached) { wm.removeView(h.v); h.attached = false }
        }
    }

    private fun startAsForeground() {
        val ch = "pool_aim"
        val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        nm.createNotificationChannel(NotificationChannel(ch, "Pool Aim", NotificationManager.IMPORTANCE_LOW))
        val n = Notification.Builder(this, ch)
            .setContentTitle("Pool Aim is running")
            .setSmallIcon(android.R.drawable.ic_menu_compass)
            .build()
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(1, n, android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else startForeground(1, n)
    }

    private fun btn(label: String, action: (Button) -> Unit): Button =
        Button(this).apply {
            text = label
            isAllCaps = false
            textSize = 11f
            minHeight = 0
            minimumHeight = (38 * d).toInt()
            setOnClickListener { action(this) }
        }

    private fun buildControls() {
        val dm = resources.displayMetrics
        controls = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val list = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val scroll = ScrollView(this).apply { addView(list); visibility = View.GONE }

        val ball = BallView(this)
        val slop = ViewConfiguration.get(this).scaledTouchSlop
        var downX = 0f; var downY = 0f; var startX = 0; var startY = 0; var moved = false
        ball.setOnTouchListener { _, e ->
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downX = e.rawX; downY = e.rawY
                    startX = controlsParams.x; startY = controlsParams.y
                    moved = false; true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = e.rawX - downX
                    val dy = e.rawY - downY
                    if (!moved && hypot(dx, dy) > slop) moved = true
                    if (moved) {
                        controlsParams.x = (startX + dx).toInt()
                        controlsParams.y = (startY + dy).toInt()
                        wm.updateViewLayout(controls, controlsParams)
                    }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    if (!moved) scroll.visibility = if (scroll.visibility == View.GONE) View.VISIBLE else View.GONE
                    true
                }
                else -> false
            }
        }

        list.addView(btn("Stop") { stopSelf() })
        list.addView(btn("Hide rect") { b ->
            view.rectHidden = !view.rectHidden
            b.text = if (view.rectHidden) "Show rect" else "Hide rect"
            view.invalidate(); syncHandles()
        })
        list.addView(btn("Hide C-8 line") { b ->
            view.aimHidden = !view.aimHidden
            b.text = if (view.aimHidden) "Show C-8 line" else "Hide C-8 line"
            view.invalidate()
        })
        list.addView(btn("Lock rectangle") { b ->
            view.locked = !view.locked
            b.text = if (view.locked) "Unlock rectangle" else "Lock rectangle"
            view.invalidate(); syncHandles()
        })
        list.addView(btn("Swap C / 8") { view.swap() })
        list.addView(btn("Reset") { view.resetLayout() })
        list.addView(btn("Theme") { view.nextTheme() })
        list.addView(btn("Line +") { view.thicker(1f) })
        list.addView(btn("Line -") { view.thicker(-1f) })
        val names = mapOf(
            "lines" to "Pocket lines", "single" to "Single aim", "bank" to "Bank shot",
            "double_bank" to "Double bank", "ghost" to "Ghost ball", "hud" to "HUD",
            "animate" to "Animations", "snap" to "Snap to pockets"
        )
        for ((k, n) in names) list.addView(btn(n) { view.toggle(k); syncHandles() })

        val bs = (44 * d).toInt()
        controls.addView(ball, LinearLayout.LayoutParams(bs, bs))
        controls.addView(scroll, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT, (dm.heightPixels * 0.6f).toInt()))

        controlsParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE, PixelFormat.TRANSLUCENT
        ).apply { gravity = Gravity.TOP or Gravity.START; x = 16; y = 120 }
        wm.addView(controls, controlsParams)
    }

    override fun onDestroy() {
        view.save()
        for (h in handles) if (h.attached) runCatching { wm.removeView(h.v) }
        runCatching { wm.removeView(controls) }
        runCatching { wm.removeView(view) }
        super.onDestroy()
    }
}
