package com.example.poolaim

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.IBinder
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.WindowManager
import android.widget.Button
import android.widget.CheckBox
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.TextView
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

class TickSeekBar(ctx: Context) : SeekBar(ctx) {
    private val tick = Paint().apply { color = Color.argb(210, 255, 255, 255); strokeWidth = 2f }
    override fun onDraw(c: Canvas) {
        val dn = resources.displayMetrics.density
        val l = paddingLeft.toFloat()
        val r = (width - paddingRight).toFloat()
        val cy = height / 2f
        val hh = 7f * dn
        val n = this.max
        if (n > 0) for (i in 0..n) {
            val x = l + (r - l) * i / n
            c.drawLine(x, cy - hh, x, cy + hh, tick)
        }
        super.onDraw(c)
    }
}

class HowToDiagram(ctx: Context, private val kind: String) : View(ctx) {
    private val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { strokeCap = Paint.Cap.ROUND }
    private val cyan = Color.rgb(0, 229, 255)

    override fun onDraw(c: Canvas) {
        val d = resources.displayMetrics.density
        val w = width.toFloat()
        val h = height.toFloat()
        p.pathEffect = null
        p.style = Paint.Style.FILL; p.color = Color.rgb(112, 72, 42)
        c.drawRoundRect(0f, 0f, w, h, 10f * d, 10f * d, p)
        val m = 7f * d
        val l = m; val t = m; val r = w - m; val b = h - m
        p.color = Color.rgb(22, 108, 94)
        c.drawRoundRect(l, t, r, b, 6f * d, 6f * d, p)
        p.style = Paint.Style.STROKE; p.strokeWidth = 1.2f * d; p.color = Color.argb(200, 255, 255, 255)
        c.drawRoundRect(l + 3f * d, t + 3f * d, r - 3f * d, b - 3f * d, 4f * d, 4f * d, p)

        fun X(x: Float) = l + (r - l) * x
        fun Y(y: Float) = t + (b - t) * y

        p.style = Paint.Style.FILL; p.color = Color.BLACK
        for (px in listOf(0f, 0.5f, 1f)) for (py in listOf(0f, 1f)) c.drawCircle(X(px), Y(py), 5.5f * d, p)

        fun ln(x1: Float, y1: Float, x2: Float, y2: Float, col: Int, wd: Float, dash: Boolean = false) {
            p.style = Paint.Style.STROKE; p.strokeWidth = wd * d; p.color = col
            p.pathEffect = if (dash) DashPathEffect(floatArrayOf(6f * d, 5f * d), 0f) else null
            c.drawLine(X(x1), Y(y1), X(x2), Y(y2), p)
            p.pathEffect = null
        }
        fun ball(x: Float, y: Float, col: Int) {
            p.style = Paint.Style.FILL; p.color = col; c.drawCircle(X(x), Y(y), 6f * d, p)
            p.style = Paint.Style.STROKE; p.strokeWidth = 1f * d; p.color = Color.argb(180, 255, 255, 255)
            c.drawCircle(X(x), Y(y), 6f * d, p)
        }
        fun dot(x: Float, y: Float) {
            p.style = Paint.Style.FILL
            p.color = Color.argb(90, 255, 20, 20); c.drawCircle(X(x), Y(y), 5f * d, p)
            p.color = Color.rgb(255, 40, 40); c.drawCircle(X(x), Y(y), 2.2f * d, p)
        }
        fun touch(x: Float, y: Float) {
            p.style = Paint.Style.FILL; p.color = Color.argb(70, 255, 60, 60)
            c.drawCircle(X(x) + 16f * d, Y(y), 7f * d, p)
            p.style = Paint.Style.STROKE; p.strokeWidth = 1.6f * d; p.color = Color.WHITE
            c.drawCircle(X(x) + 16f * d, Y(y), 7f * d, p)
        }
        fun label(s: String, x: Float, y: Float) {
            p.style = Paint.Style.FILL; p.color = Color.WHITE; p.textSize = 9f * d
            p.textAlign = Paint.Align.CENTER; p.isFakeBoldText = true
            c.drawText(s, X(x) + 16f * d, Y(y) - 10f * d, p)
            p.isFakeBoldText = false; p.textAlign = Paint.Align.LEFT
        }
        fun aim(pts: List<Pair<Float, Float>>) {
            for (i in 0 until pts.size - 1) {
                ln(pts[i].first, pts[i].second, pts[i + 1].first, pts[i + 1].second, Color.argb(110, 255, 255, 255), 5f)
            }
            for (i in 0 until pts.size - 1) {
                ln(pts[i].first, pts[i].second, pts[i + 1].first, pts[i + 1].second, Color.BLACK, 2f)
            }
        }

        when (kind) {
            "pockets" -> {
                for (px in listOf(0f, 0.5f, 1f)) for (py in listOf(0f, 1f)) ln(0.5f, 0.5f, px, py, cyan, 1.6f)
                dot(0.5f, 0.5f); touch(0.5f, 0.5f)
            }
            "single" -> {
                aim(listOf(0.15f to 0.75f, 0.62f to 0.38f))
                ball(0.15f, 0.75f, Color.WHITE); ball(0.62f, 0.38f, Color.BLACK)
            }
            "bank" -> {
                aim(listOf(0.12f to 0.85f, 0.3f to 0.6f, 0.73f to 0.0f, 1.0f to 0.375f))
                ball(0.12f, 0.85f, Color.WHITE); ball(0.3f, 0.6f, Color.BLACK)
            }
            "double" -> {
                aim(listOf(0.12f to 0.85f, 0.3f to 0.6f, 0.73f to 0.0f, 1.0f to 0.375f, 0.55f to 1.0f))
                ball(0.12f, 0.85f, Color.WHITE); ball(0.3f, 0.6f, Color.BLACK)
            }
            "ghost" -> {
                ln(0.62f, 0.42f, 1.0f, 0.0f, cyan, 1.6f, true)
                ln(0.15f, 0.78f, 0.54f, 0.51f, Color.argb(160, 0, 229, 255), 1.2f, true)
                ball(0.62f, 0.42f, Color.BLACK); ball(0.15f, 0.78f, Color.WHITE)
                p.style = Paint.Style.FILL; p.color = Color.argb(50, 0, 229, 255)
                c.drawCircle(X(0.54f), Y(0.51f), 7f * d, p)
                p.style = Paint.Style.STROKE; p.strokeWidth = 1.6f * d; p.color = cyan
                c.drawCircle(X(0.54f), Y(0.51f), 7f * d, p)
                c.drawCircle(X(1.0f), Y(0.0f), 9f * d, p)
                dot(0.62f, 0.42f)
            }
            "touch" -> {
                ball(0.25f, 0.65f, Color.WHITE); ball(0.65f, 0.4f, Color.BLACK)
                dot(0.25f, 0.65f); dot(0.65f, 0.4f)
                touch(0.25f, 0.65f); touch(0.65f, 0.4f)
                label("C", 0.25f, 0.65f); label("8", 0.65f, 0.4f)
            }
            "rect" -> {
                p.style = Paint.Style.STROKE; p.strokeWidth = 2.5f * d; p.color = Color.WHITE
                c.drawRoundRect(X(0.1f), Y(0.12f), X(0.9f), Y(0.88f), 10f * d, 10f * d, p)
                for ((cx, cy) in listOf(0.1f to 0.12f, 0.9f to 0.88f)) {
                    p.style = Paint.Style.FILL; p.color = Color.argb(90, 255, 255, 255)
                    c.drawCircle(X(cx), Y(cy), 8f * d, p)
                    p.style = Paint.Style.STROKE; p.strokeWidth = 1.6f * d; p.color = Color.WHITE
                    c.drawCircle(X(cx), Y(cy), 8f * d, p)
                }
            }
        }
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
                "p1", "p2" -> !view.aimHidden
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
        nm.createNotificationChannel(NotificationChannel(ch, "Pool Aim Shettos", NotificationManager.IMPORTANCE_LOW))
        val n = Notification.Builder(this, ch)
            .setContentTitle("Pool Aim Shettos is running")
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
            textSize = 9.9f
            minHeight = 0
            minWidth = 0
            minimumHeight = (34.2f * d).toInt()
            minimumWidth = (79f * d).toInt()
            val ph = (14f * d).toInt()
            setPadding(ph, 0, ph, 0)
            setOnClickListener { action(this) }
        }

    private fun tv(t: String, sp: Float, col: Int = Color.WHITE, bold: Boolean = false) =
        TextView(this).apply {
            text = t; textSize = sp; setTextColor(col)
            if (bold) setTypeface(typeface, Typeface.BOLD)
        }

    private fun darkBox(): GradientDrawable = GradientDrawable().apply {
        setColor(Color.argb(235, 15, 17, 23))
        cornerRadius = 10f * d
        setStroke((1.2f * d).toInt().coerceAtLeast(1), Color.argb(150, 0, 229, 255))
    }

    private fun seekRow(title: String, maxV: Int, init: Int, onChange: (Int) -> Unit): LinearLayout {
        val row = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        row.addView(tv(title, 11f, Color.WHITE, true))
        val sb = TickSeekBar(this).apply {
            max = maxV
            progress = init
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(s: SeekBar?, pr: Int, fromUser: Boolean) {
                    if (fromUser) onChange(pr)
                }
                override fun onStartTrackingTouch(s: SeekBar?) {}
                override fun onStopTrackingTouch(s: SeekBar?) { view.save() }
            })
        }
        row.addView(sb, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, (30 * d).toInt()))
        return row
    }

    private fun ghostPanel(): LinearLayout {
        val pad = (8 * d).toInt()
        val panel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, pad, pad, pad)
            background = darkBox()
            visibility = View.GONE
        }
        val row1 = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        row1.addView(tv("Ghost ball", 12f, Color.WHITE, true),
            LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        val cb = CheckBox(this).apply {
            isChecked = view.flags["ghost"] == true
            buttonTintList = ColorStateList.valueOf(Color.WHITE)
            setOnCheckedChangeListener { _, on ->
                if ((view.flags["ghost"] == true) != on) view.toggle("ghost")
            }
        }
        row1.addView(cb)
        panel.addView(row1)
        panel.addView(seekRow("Distance", 10, view.ghostDistStep) { v ->
            view.ghostDistStep = v; view.invalidate()
        })
        panel.addView(seekRow("Size", 9, view.ghostSizeStep) { v ->
            view.ghostSizeStep = v; view.invalidate()
        })
        return panel
    }

    private fun helpCard(): LinearLayout {
        val pad = (10 * d).toInt()
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, pad, pad, pad)
            background = darkBox()
        }
        card.addView(tv("How to play", 16f, Color.WHITE, true).apply { gravity = Gravity.CENTER_HORIZONTAL },
            LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))
        card.addView(tv("Open the game, tap the ball icon to show the menu, then set up the table once.",
            10.5f, Color.argb(220, 200, 210, 220)).apply { gravity = Gravity.CENTER_HORIZONTAL; setPadding(0, (4 * d).toInt(), 0, (8 * d).toInt()) },
            LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))

        val items = listOf(
            Triple("Rectangle", "rect", "Drag the two white corner circles until the rectangle matches the table. Use Lock rectangle to freeze it, Hide rect to hide it, Reset to start over."),
            Triple("Touch circles", "touch", "Drag the red circle next to each dot to move it. The white ring is the grab area. C is the cue ball and 8 is the target ball."),
            Triple("Single aim", "single", "A black line runs from C through 8 so you can see your aim direction."),
            Triple("Bank shot", "bank", "Extends the aim line off a cushion, up to 2 banks."),
            Triple("Double bank", "double", "Adds more cushion bounces, up to 4 banks."),
            Triple("Pocket lines", "pockets", "Drag the center dot. Lines run from it to all six pockets."),
            Triple("Ghost ball", "ghost", "Tick Ghost ball to show the best pocket line and the ghost circle. Use Distance and Size to adjust the circle.")
        )
        for ((name, kind, desc) in items) {
            card.addView(tv(name, 13f, Color.rgb(0, 229, 255), true).apply { gravity = Gravity.CENTER_HORIZONTAL; setPadding(0, (10 * d).toInt(), 0, (4 * d).toInt()) },
                LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))
            card.addView(HowToDiagram(this, kind),
                LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, (110 * d).toInt()))
            card.addView(tv(desc, 10.5f, Color.argb(230, 220, 228, 235)).apply { setPadding(0, (4 * d).toInt(), 0, 0) },
                LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))
        }
        return card
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

        val panelW = (220 * d).toInt()
        val card = helpCard()
        val ghost = ghostPanel()

        list.addView(btn("Stop") { stopSelf() })
        list.addView(btn("How to play") {
            scroll.post { scroll.smoothScrollTo(0, card.top) }
        })
        list.addView(btn("Hide rect") { b ->
            view.rectHidden = !view.rectHidden
            b.text = if (view.rectHidden) "Show rect" else "Hide rect"
            view.invalidate(); syncHandles()
        })
        list.addView(btn("Hide C-8 line") { b ->
            view.aimHidden = !view.aimHidden
            b.text = if (view.aimHidden) "Show C-8 line" else "Hide C-8 line"
            view.invalidate(); syncHandles()
        })
        list.addView(btn("Lock rectangle") { b ->
            view.locked = !view.locked
            b.text = if (view.locked) "Unlock rectangle" else "Lock rectangle"
            view.invalidate(); syncHandles()
        })
        list.addView(btn("Reset") { view.resetLayout() })
        list.addView(btn("Theme") { view.nextTheme() })
        list.addView(btn("Line +") { view.thicker(1f) })
        list.addView(btn("Line -") { view.thicker(-1f) })
        list.addView(btn("Ghost ball") {
            ghost.visibility = if (ghost.visibility == View.GONE) View.VISIBLE else View.GONE
        })
        list.addView(ghost, LinearLayout.LayoutParams(panelW, LinearLayout.LayoutParams.WRAP_CONTENT))
        val names = linkedMapOf(
            "lines" to "Pocket lines", "single" to "Single aim", "bank" to "Bank shot",
            "double_bank" to "Double bank", "hud" to "HUD",
            "snap" to "Snap to pockets"
        )
        for ((k, n) in names) list.addView(btn(n) { view.toggle(k); syncHandles() })
        list.addView(card, LinearLayout.LayoutParams(panelW, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
            topMargin = (8 * d).toInt(); bottomMargin = (8 * d).toInt()
        })

        val bs = (44 * d).toInt()
        val scrollH = (min(dm.widthPixels, dm.heightPixels) * 0.45f).toInt()
        controls.addView(ball, LinearLayout.LayoutParams(bs, bs))
        controls.addView(scroll, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT, scrollH))

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
