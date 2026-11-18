package com.example.poolaim

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
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
import android.text.SpannableString
import android.text.Spanned
import android.text.style.RelativeSizeSpan
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

object Lang {
    const val PREFS = "pool_aim"
    const val KEY = "lang_ar"

    fun isAr(ctx: Context): Boolean =
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY, false)

    fun set(ctx: Context, ar: Boolean) {
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean(KEY, ar).apply()
    }

    fun t(ctx: Context, k: String): String {
        val m = if (isAr(ctx)) AR else EN
        return m[k] ?: EN[k] ?: k
    }

    private val EN = mapOf(
        "grant" to "1) Grant overlay permission",
        "start" to "2) Start overlay",
        "stop_overlay" to "Stop overlay",
        "how_to_play" to "How to play",
        "exit" to "Exit",
        "lang_en_btn" to "English",
        "lang_ar_btn" to "Arabic",
        "dev" to "Developed by Emad Shettos",
        "whatsapp" to "WhatsApp: +201093532277",
        "grant_first" to "Grant overlay permission first",
        "help_steps" to "1) Grant overlay permission.  2) Start overlay.  3) Open your table, tap the small ball icon to show the menu, then match the rectangle to the table.",
        "back_top" to "Back to top",
        "h_rect" to "Rectangle",
        "d_rect" to "Drag the two white corner circles until the rectangle matches the table. Use Lock rectangle to freeze it, Hide rect to hide it, and Reset to start over.",
        "h_touch" to "Touch circles",
        "d_touch" to "Drag the red circle beside each dot to move it. The white ring is the grab area. C is the cue ball and 8 is the target ball.",
        "h_single" to "1 Line",
        "d_single" to "A black line runs from C through 8 so you can see your aim direction.",
        "h_bank" to "2 Line",
        "d_bank" to "Extends the aim line off a cushion, up to 2 banks.",
        "h_double" to "4 Line",
        "d_double" to "Adds more cushion bounces, up to 4 banks.",
        "h_pockets" to "Pocket lines",
        "d_pockets" to "Drag the center dot. Lines run from it to all six pockets.",
        "h_ghost" to "Ghost ball",
        "d_ghost" to "Open the Ghost ball menu and tick the box to show the best pocket line and the ghost circle. Use Distance and Size to adjust the circle.",
        "m_hide_all" to "Hide all",
        "m_show_all" to "Show",
        "m_stop" to "Stop",
        "m_hide_rect" to "Hide rect",
        "m_show_rect" to "Show rect",
        "m_lock" to "Lock rectangle",
        "m_unlock" to "Unlock rectangle",
        "m_reset" to "Reset",
        "m_theme" to "Theme",
        "m_line_plus" to "Line +",
        "m_line_minus" to "Line -",
        "m_ghost" to "Ghost ball",
        "m_c8_title" to "C-8 line (Bank Shot)",
        "m_c8" to "C-8 line",
        "m_line" to "Line",
        "m_glow" to "Guide line glow",
        "m_transparency" to "Transparency",
        "m_distance" to "Distance",
        "m_size" to "Size",
        "m_pockets" to "Pocket lines",
        "m_hud" to "HUD",
        "m_snap" to "Snap to pockets",
        "hud_locked" to "Rect locked",
        "hud_unlocked" to "Rect unlocked",
        "hud_cut" to "Cut",
        "hud_pocket" to "pocket",
        "hud_banks" to "bank(s)",
        "hud_pocketed" to "pocketed"
    )

    private val AR = mapOf(
        "grant" to "1) منح إذن العرض فوق التطبيقات",
        "start" to "2) تشغيل العرض",
        "stop_overlay" to "إيقاف العرض",
        "how_to_play" to "كيف تلعب",
        "exit" to "خروج",
        "lang_en_btn" to "الإنجليزية",
        "lang_ar_btn" to "العربية",
        "dev" to "إعداد بواسطة المطور Emad Shettos",
        "whatsapp" to "WhatsApp: +201093532277",
        "grant_first" to "امنح إذن العرض فوق التطبيقات أولاً",
        "help_steps" to "1) امنح إذن العرض فوق التطبيقات.  2) شغّل العرض.  3) افتح الطاولة داخل اللعبة، واضغط على أيقونة الكرة الصغيرة لإظهار القائمة، ثم اضبط المستطيل ليطابق الطاولة.",
        "back_top" to "العودة للأعلى",
        "h_rect" to "المستطيل",
        "d_rect" to "اسحب الدائرتين البيضاوين في الزاويتين حتى يطابق المستطيل الطاولة. استخدم قفل المستطيل لتثبيته، وإخفاء المستطيل لإخفائه، وإعادة الضبط للبدء من جديد.",
        "h_touch" to "دوائر اللمس",
        "d_touch" to "اسحب الدائرة الحمراء بجوار كل نقطة لتحريكها. الحلقة البيضاء هي منطقة اللمس. C هي الكرة البيضاء و8 هي الكرة المستهدفة.",
        "h_single" to "خط 1",
        "d_single" to "خط أسود يمتد من C عبر 8 ليوضح لك اتجاه التصويب.",
        "h_bank" to "خط 2",
        "d_bank" to "يمتد خط التصويب بعد الارتداد عن الجدار، حتى ارتدادين.",
        "h_double" to "خط 4",
        "d_double" to "يضيف ارتدادات أكثر عن الجدران، حتى 4 ارتدادات.",
        "h_pockets" to "خطوط الجيوب",
        "d_pockets" to "اسحب النقطة المركزية. تمتد خطوط منها إلى الجيوب الستة.",
        "h_ghost" to "الكرة الشبحية",
        "d_ghost" to "افتح قائمة الكرة الشبحية وعلّم المربع لإظهار أفضل خط للجيب والدائرة الشبحية. استخدم المسافة والحجم لضبط الدائرة.",
        "m_hide_all" to "إخفاء الكل",
        "m_show_all" to "إظهار",
        "m_stop" to "إيقاف",
        "m_hide_rect" to "إخفاء المستطيل",
        "m_show_rect" to "إظهار المستطيل",
        "m_lock" to "قفل المستطيل",
        "m_unlock" to "فتح المستطيل",
        "m_reset" to "إعادة ضبط",
        "m_theme" to "المظهر",
        "m_line_plus" to "سماكة +",
        "m_line_minus" to "سماكة -",
        "m_ghost" to "الكرة الشبحية",
        "m_c8_title" to "خط C-8 (ضربة مرتدة)",
        "m_c8" to "خط C-8",
        "m_line" to "خط",
        "m_glow" to "هالة الخط",
        "m_transparency" to "الشفافية",
        "m_distance" to "المسافة",
        "m_size" to "الحجم",
        "m_pockets" to "خطوط الجيوب",
        "m_hud" to "الشريط العلوي",
        "m_snap" to "الالتصاق بالجيوب",
        "hud_locked" to "المستطيل مقفل",
        "hud_unlocked" to "المستطيل غير مقفل",
        "hud_cut" to "الزاوية",
        "hud_pocket" to "الجيب",
        "hud_banks" to "ارتداد",
        "hud_pocketed" to "دخلت الجيب"
    )
}

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
            p.color = Color.BLACK; c.drawCircle(X(x), Y(y), 1f * d, p)
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
                p.style = Paint.Style.STROKE; p.strokeWidth = 2f * d; p.color = Color.WHITE
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
    private lateinit var menuScroll: ScrollView
    private lateinit var ghostPanelView: LinearLayout
    private lateinit var c8PanelView: LinearLayout
    private lateinit var glowPanelView: LinearLayout
    private lateinit var controlsParams: WindowManager.LayoutParams
    private lateinit var overlayParams: WindowManager.LayoutParams
    private val handles = mutableListOf<Handle>()
    private val d get() = resources.displayMetrics.density
    private fun prefs(): SharedPreferences = getSharedPreferences(Lang.PREFS, MODE_PRIVATE)
    private fun T(k: String) = Lang.t(this, k)

    private val langListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key == Lang.KEY && ::controls.isInitialized && ::view.isInitialized) {
            rebuildControls()
            view.invalidate()
        }
    }

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
        prefs().registerOnSharedPreferenceChangeListener(langListener)
        syncHandles()
        view.post { syncHandles() }
    }

    private fun syncHandles() {
        if (!::view.isInitialized) return
        val loc = IntArray(2)
        view.getLocationOnScreen(loc)
        for (h in handles) {
            val active = if (view.hideAll) false else when (h.key) {
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

    private fun btn(label: CharSequence, action: (Button) -> Unit): Button =
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

    private fun panelBase(open: Boolean): LinearLayout {
        val pad = (8 * d).toInt()
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, pad, pad, pad)
            background = darkBox()
            visibility = if (open) View.VISIBLE else View.GONE
        }
    }

    private fun checkRow(title: String, checked: Boolean, bold: Boolean, onChange: (Boolean) -> Unit): LinearLayout {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        row.addView(tv(title, if (bold) 12f else 11f, Color.WHITE, bold),
            LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        val cb = CheckBox(this).apply {
            isChecked = checked
            buttonTintList = ColorStateList.valueOf(Color.WHITE)
            setOnCheckedChangeListener { _, on -> onChange(on) }
        }
        row.addView(cb)
        return row
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

    private fun flagRow(label: String, key: String, bold: Boolean): LinearLayout =
        checkRow(label, view.flags[key] == true, bold) { on ->
            if ((view.flags[key] == true) != on) view.toggle(key)
            syncHandles()
        }

    private fun ghostPanel(open: Boolean): LinearLayout {
        val panel = panelBase(open)
        
        // الصف الأول: Ghost ball وبجواره Snap to pockets في نفس الصف بمسافة قريبة
        val row1 = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        
        val tvGhost = tv(T("m_ghost"), 11f, Color.WHITE, true)
        val cbGhost = CheckBox(this).apply {
            isChecked = view.flags["ghost"] == true
            buttonTintList = ColorStateList.valueOf(Color.WHITE)
            setOnCheckedChangeListener { _, on ->
                if ((view.flags["ghost"] == true) != on) view.toggle("ghost")
                syncHandles()
            }
        }
        
        val tvSnap = tv(T("m_snap"), 10f, Color.argb(220, 255, 255, 255), false)
        val cbSnap = CheckBox(this).apply {
            isChecked = view.flags["snap"] == true
            buttonTintList = ColorStateList.valueOf(Color.WHITE)
            setOnCheckedChangeListener { _, on ->
                if ((view.flags["snap"] == true) != on) view.toggle("snap")
            }
        }

        row1.addView(tvGhost)
        row1.addView(cbGhost)
        row1.addView(View(this), LinearLayout.LayoutParams((10 * d).toInt(), 1))
        row1.addView(tvSnap)
        row1.addView(cbSnap)
        panel.addView(row1)

        panel.addView(seekRow(T("m_distance"), 10, view.ghostDistStep) { v ->
            view.ghostDistStep = v; view.invalidate()
        })
        panel.addView(seekRow(T("m_size"), 9, view.ghostSizeStep) { v ->
            view.ghostSizeStep = v; view.invalidate()
        })
        return panel
    }

    private fun c8Panel(open: Boolean): LinearLayout {
        val panel = panelBase(open)
        
        // السطر الأول: C-8 line وبجواره مربع التشغيل الرئيسي
        panel.addView(checkRow(T("m_c8"), !view.aimHidden, true) { on ->
            view.aimHidden = !on
            view.save(); view.invalidate(); syncHandles()
        })
        
        // السطر الثاني: كلمة Line وبجوارها في نفس الصف مربعات 1 و 2 و 4
        val rowLines = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, (4 * d).toInt(), 0, 0)
        }
        rowLines.addView(tv(T("m_line"), 11f, Color.WHITE, true),
            LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.2f))

        fun addMiniCheck(num: String, key: String) {
            val h = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding((2 * d).toInt(), 0, (2 * d).toInt(), 0)
            }
            h.addView(tv(num, 11f, Color.rgb(0, 229, 255), true))
            val cb = CheckBox(this).apply {
                isChecked = view.flags[key] == true
                buttonTintList = ColorStateList.valueOf(Color.WHITE)
                setOnCheckedChangeListener { _, on ->
                    if ((view.flags[key] == true) != on) view.toggle(key)
                    syncHandles()
                }
            }
            h.addView(cb)
            rowLines.addView(h, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        }

        addMiniCheck("1", "single")
        addMiniCheck("2", "bank")
        addMiniCheck("4", "double_bank")
        panel.addView(rowLines)

        return panel
    }

    private fun glowPanel(open: Boolean): LinearLayout {
        val panel = panelBase(open)
        panel.addView(flagRow(T("m_glow"), "glow", true))
        panel.addView(seekRow(T("m_transparency"), 10, view.glowStep) { v ->
            view.glowStep = v; view.invalidate()
        })
        panel.addView(seekRow(T("m_size"), 10, view.glowSizeStep) { v ->
            view.glowSizeStep = v; view.invalidate()
        })
        return panel
    }

    private fun saveMenu() {
        if (!::controls.isInitialized || !::menuScroll.isInitialized ||
            !::ghostPanelView.isInitialized || !::c8PanelView.isInitialized || !::glowPanelView.isInitialized) return
        prefs().edit()
            .putInt("menu_x", controlsParams.x)
            .putInt("menu_y", controlsParams.y)
            .putBoolean("menu_open", menuScroll.visibility == View.VISIBLE)
            .putBoolean("ghost_open", ghostPanelView.visibility == View.VISIBLE)
            .putBoolean("c8_open", c8PanelView.visibility == View.VISIBLE)
            .putBoolean("glow_open", glowPanelView.visibility == View.VISIBLE)
            .putInt("menu_scroll", menuScroll.scrollY)
            .apply()
    }

    private fun rebuildControls() {
        saveMenu()
        runCatching { wm.removeView(controls) }
        buildControls()
    }

    private fun togglePanel(p: View) {
        p.visibility = if (p.visibility == View.GONE) View.VISIBLE else View.GONE
        saveMenu()
    }

    private fun buildControls() {
        val dm = resources.displayMetrics
        val sp = prefs()
        val bs = (44 * d).toInt()
        val startX = sp.getInt("menu_x", 16).coerceIn(0, (dm.widthPixels - bs).coerceAtLeast(0))
        val startY = sp.getInt("menu_y", 120).coerceIn(0, (dm.heightPixels - bs).coerceAtLeast(0))
        val menuOpen = sp.getBoolean("menu_open", false)
        val savedScroll = sp.getInt("menu_scroll", 0)

        controls = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val list = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val scroll = ScrollView(this).apply {
            addView(list)
            visibility = if (menuOpen) View.VISIBLE else View.GONE
            setOnScrollChangeListener { _, _, y, _, _ ->
                prefs().edit().putInt("menu_scroll", y).apply()
            }
        }
        menuScroll = scroll
        val ghost = ghostPanel(sp.getBoolean("ghost_open", false))
        val c8 = c8Panel(sp.getBoolean("c8_open", false))
        val glow = glowPanel(sp.getBoolean("glow_open", false))
        ghostPanelView = ghost
        c8PanelView = c8
        glowPanelView = glow

        controlsParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE, PixelFormat.TRANSLUCENT
        ).apply { gravity = Gravity.TOP or Gravity.START; x = startX; y = startY }

        val ball = BallView(this)
        val slop = ViewConfiguration.get(this).scaledTouchSlop
        var downX = 0f; var downY = 0f; var sx0 = 0; var sy0 = 0; var moved = false
        ball.setOnTouchListener { _, e ->
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downX = e.rawX; downY = e.rawY
                    sx0 = controlsParams.x; sy0 = controlsParams.y
                    moved = false; true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = e.rawX - downX
                    val dy = e.rawY - downY
                    if (!moved && hypot(dx, dy) > slop) moved = true
                    if (moved) {
                        controlsParams.x = (sx0 + dx).toInt()
                        controlsParams.y = (sy0 + dy).toInt()
                        wm.updateViewLayout(controls, controlsParams)
                    }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    if (!moved) scroll.visibility = if (scroll.visibility == View.GONE) View.VISIBLE else View.GONE
                    saveMenu()
                    true
                }
                else -> false
            }
        }

        val panelW = (220 * d).toInt()
        val panelLp = { LinearLayout.LayoutParams(panelW, LinearLayout.LayoutParams.WRAP_CONTENT) }

        // صياغة اسم الزر C-8 line (Bank Shot) بحيث يكون ما بين القوسين بحجم أصغر قليلاً
        val rawTitle = T("m_c8_title")
        val styledC8Title = SpannableString(rawTitle).apply {
            val openIdx = rawTitle.indexOf('(')
            if (openIdx >= 0) {
                setSpan(RelativeSizeSpan(0.82f), openIdx, rawTitle.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            }
        }

        // الترتيب الدقيق المطلوب للأزرار من الأعلى للأسفل:
        list.addView(btn(T(if (view.hideAll) "m_show_all" else "m_hide_all")) { b ->
            view.hideAll = !view.hideAll
            b.text = T(if (view.hideAll) "m_show_all" else "m_hide_all")
            view.save(); view.invalidate(); syncHandles()
        })
        list.addView(btn(T(if (view.rectHidden) "m_show_rect" else "m_hide_rect")) { b ->
            view.rectHidden = !view.rectHidden
            b.text = T(if (view.rectHidden) "m_show_rect" else "m_hide_rect")
            view.save(); view.invalidate(); syncHandles()
        })
        list.addView(btn(T(if (view.locked) "m_unlock" else "m_lock")) { b ->
            view.locked = !view.locked
            b.text = T(if (view.locked) "m_unlock" else "m_lock")
            view.save(); view.invalidate(); syncHandles()
        })
        list.addView(btn(T("m_pockets")) { view.toggle("lines"); syncHandles() })
        list.addView(btn(styledC8Title) { togglePanel(c8) })
        list.addView(c8, panelLp())
        list.addView(btn(T("m_ghost")) { togglePanel(ghost) })
        list.addView(ghost, panelLp())
        list.addView(btn(T("m_line_plus")) { view.thicker(1f) })
        list.addView(btn(T("m_line_minus")) { view.thicker(-1f) })
        list.addView(btn(T("m_glow")) { togglePanel(glow) })
        list.addView(glow, panelLp())
        list.addView(btn(T("m_theme")) { view.nextTheme() })
        list.addView(btn(T("m_hud")) { view.toggle("hud") })
        list.addView(btn(T("m_reset")) { view.resetLayout(); view.save() })
        list.addView(btn(T("m_stop")) { stopSelf() })

        val scrollH = (min(dm.widthPixels, dm.heightPixels) * 0.45f).toInt()
        controls.addView(ball, LinearLayout.LayoutParams(bs, bs))
        controls.addView(scroll, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT, scrollH))

        wm.addView(controls, controlsParams)
        if (menuOpen) scroll.post { scroll.scrollTo(0, savedScroll) }
    }

    override fun onDestroy() {
        runCatching { prefs().unregisterOnSharedPreferenceChangeListener(langListener) }
        saveMenu()
        view.save()
        for (h in handles) if (h.attached) runCatching { wm.removeView(h.v) }
        runCatching { wm.removeView(controls) }
        runCatching { wm.removeView(view) }
        super.onDestroy()
    }
}
