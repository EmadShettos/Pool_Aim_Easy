package com.example.poolaim

import android.content.Context
import android.graphics.*
import android.view.MotionEvent
import android.view.View
import kotlin.math.*

class OverlayView(ctx: Context) : View(ctx) {
    private val d = resources.displayMetrics.density
    private val prefs = ctx.getSharedPreferences("overlay_prefs", Context.MODE_PRIVATE)

    val flags = linkedMapOf(
        "lines" to true, "single" to false, "bank" to false, "double_bank" to true,
        "ghost" to true, "hud" to true, "animate" to false, "snap" to false, "glow" to false
    )
    var thickness = 3f
    var themeIdx = 0
    var cueForce = 1
    var locked = false
    var hideAll = false
    var aimHidden = false
    var rectHidden = false

    var ghostDistStep = 0
    var ghostSizeStep = 0
    var glowStep = 5
    var glowSizeStep = 5

    private val p1 = PointF()
    private val p2 = PointF()
    private val marker = PointF()
    private val rect = RectF()

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val text = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE; textAlign = Paint.Align.CENTER }
    private val hudBg = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(190, 15, 23, 42) }
    private val hudBorder = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; strokeWidth = 1.2f * d; color = Color.argb(120, 255, 255, 255)
    }

    private var activePt: Any? = null
    private var touchMode = ""
    private var lastX = 0f
    private var lastY = 0f

    private val touchR get() = 18f * d
    private val touchOffX get() = -32f * d
    private val ballR get() = rect.width() * 0.0175f
    private val pocketR get() = ballR * 1.25f
    private val ghostR get() = ballR * (0.8f + ghostSizeStep * 0.04f)
    private val ghostDistPx get() = ballR * (2f + ghostDistStep * 0.15f)
    private val snapThresh get() = pocketR * 2.2f

    init { load(); flags["animate"] = false }

    private val color get() = THEMES[themeIdx].second

    fun toggle(k: String) { flags[k] = !(flags[k] ?: false); save(); invalidate() }
    fun nextTheme(): String { themeIdx = (themeIdx + 1) % THEMES.size; save(); invalidate(); return THEMES[themeIdx].first }
    fun changeThickness(step: Float) { thickness = (thickness + step).coerceIn(1f, 8f); save(); invalidate() }

    fun setAimHiddenState(h: Boolean) { aimHidden = h; invalidate() }
    fun setRectHiddenState(h: Boolean) { rectHidden = h; invalidate() }
    fun setHideAllState(h: Boolean) { hideAll = h; invalidate() }
    fun setLockedState(l: Boolean) { locked = l; invalidate() }

    private fun box() = Box(rect.left, rect.top, rect.right, rect.bottom)
    private fun pt(p: PointF) = Pt(p.x, p.y)

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        if (rect.isEmpty) {
            val padX = w * 0.08f
            val padY = h * 0.12f
            rect.set(padX, padY, w - padX, h - padY)
            p1.set(rect.centerX() - 60f * d, rect.centerY())
            p2.set(rect.centerX() + 60f * d, rect.centerY())
            marker.set(rect.centerX(), rect.centerY() - 40f * d)
        }
    }

    override fun onDraw(c: Canvas) {
        if (hideAll) return
        val th = thickness * d
        val b = box()
        val pockets = Geometry.pocketCenters(b)
        paint.pathEffect = null

        if (!rectHidden) {
            paint.style = Paint.Style.STROKE; paint.strokeWidth = 2.25f * d; paint.color = Color.WHITE
            c.drawRoundRect(rect, 22f * d, 22f * d, paint)
        }

        if (flags["lines"] == true) {
            for (p in pockets) glowLine(c, marker.x, marker.y, p.first, p.second, color, 30, 70)
            for (p in pockets) line(c, marker.x, marker.y, p.first, p.second, th, color)
        }

        var segs: List<Pair<Pt, Pt>> = emptyList()
        var snapped = false
        val dx = p2.x - p1.x
        val dy = p2.y - p1.y

        val hasAim = (dx != 0f || dy != 0f)
        val isSingle = flags["single"] == true
        val isBank = flags["bank"] == true
        val isDouble = flags["double_bank"] == true

        if (!aimHidden && hasAim && (isSingle || isBank || isDouble)) {
            val len = hypot(dx, dy)
            var maxBanks = if (isDouble) 4 else if (isBank) 2 else 1
            val bw = max(2f * d, th + d)

            glowLine(c, p1.x, p1.y, p2.x, p2.y, Color.WHITE, 60, 110)
            line(c, p1.x, p1.y, p2.x, p2.y, th, Color.BLACK)

            var m = Geometry.bankSegments(pt(p2), Pt(dx / len, dy / len), b, maxBanks, cueForce)

            if (flags["snap"] == true && m.isNotEmpty()) {
                val last = m.last().second
                val (pc, distP) = Geometry.nearestPocket(last, b)
                if (distP <= snapThresh) {
                    val mod = m.toMutableList()
                    val lastSeg = mod.removeAt(mod.size - 1)
                    mod.add(lastSeg.first to pc)
                    m = mod
                    snapped = true
                }
            }

            segs = m
            for ((i, s) in m.withIndex()) {
                glowLine(c, s.first.first, s.first.second, s.second.first, s.second.second, Color.WHITE, 60, 110)
                line(c, s.first.first, s.first.second, s.second.first, s.second.second, bw, Color.BLACK)
                if (i < m.size - 1) {
                    paint.style = Paint.Style.FILL; paint.color = Color.argb(200, 0, 0, 0)
                    c.drawCircle(s.second.first, s.second.second, 3.5f * d, paint)
                }
            }

            // رسم خط مسار ارتداد الكرة البيضاء بهالة بيضاء مستقلة لا تتأثر بالثيمات
            val cueTangentSegs = Geometry.cueTangentSegments(pt(p1), pt(p2), Pt(dx / len, dy / len), b, 1, cueForce)
            for (s in cueTangentSegs) {
                // هالة بيضاء شفافة متوهجة
                paint.pathEffect = null
                paint.style = Paint.Style.STROKE
                paint.strokeWidth = 10f * d
                paint.color = Color.argb(65, 255, 255, 255)
                c.drawLine(s.first.first, s.first.second, s.second.first, s.second.second, paint)

                paint.strokeWidth = 6f * d
                paint.color = Color.argb(110, 255, 255, 255)
                c.drawLine(s.first.first, s.first.second, s.second.first, s.second.second, paint)

                // خط أبيض منقط/متقطع ناصع
                paint.pathEffect = DashPathEffect(floatArrayOf(8f * d, 6f * d), 0f)
                paint.strokeWidth = max(2.2f * d, th * 0.8f)
                paint.color = Color.WHITE
                c.drawLine(s.first.first, s.first.second, s.second.first, s.second.second, paint)
                paint.pathEffect = null
            }
        }

        var info: Triple<Int, Float, Float>? = null
        if (flags["ghost"] == true) info = drawGhost(c, b)

        if (!aimHidden || flags["ghost"] == true) {
            for ((p, label) in listOf(p1 to "C", p2 to "8")) {
                val qx = p.x + touchOffX
                touchCircle(c, qx, p.y, touchR)
                text.textSize = 10f * d; text.isFakeBoldText = true
                c.drawText(label, qx, p.y - touchR - 4f * d, text)
                glowDot(c, p.x, p.y)
            }
        }
        if (flags["lines"] == true) {
            touchCircle(c, marker.x + touchOffX, marker.y, touchR)
            glowDot(c, marker.x, marker.y)
        }

        if (!locked && !rectHidden) {
            for ((x, y) in listOf(rect.left to rect.top, rect.right to rect.bottom)) {
                paint.style = Paint.Style.FILL; paint.color = Color.argb(90, 255, 255, 255)
                c.drawCircle(x, y, pocketR * 1.2f, paint)
                ring(c, x, y, pocketR * 1.2f, Color.WHITE)
                paint.style = Paint.Style.FILL; paint.color = Color.WHITE
                c.drawCircle(x, y, 3f * d, paint)
            }
        }

        if (flags["hud"] == true) drawHud(c, info, segs, snapped)
    }

    private fun glowLine(c: Canvas, ax: Float, ay: Float, bx: Float, by: Float, rgb: Int, outerA: Int, innerA: Int) {
        if (flags["glow"] != true || glowStep <= 0) return
        val k = 1f + glowSizeStep / 10f
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = pocketR * k
        paint.color = (rgb and 0x00FFFFFF) or (glowAlpha(outerA) shl 24)
        c.drawLine(ax, ay, bx, by, paint)
        paint.strokeWidth = pocketR * 0.6f * k
        paint.color = (rgb and 0x00FFFFFF) or (glowAlpha(innerA) shl 24)
        c.drawLine(ax, ay, bx, by, paint)
    }

    private fun line(c: Canvas, ax: Float, ay: Float, bx: Float, by: Float, w: Float, rgb: Int) {
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = w
        paint.color = rgb
        c.drawLine(ax, ay, bx, by, paint)
    }

    private fun ring(c: Canvas, x: Float, y: Float, r: Float, rgb: Int, w: Float = 1.5f * d) {
        paint.style = Paint.Style.STROKE; paint.strokeWidth = w; paint.color = rgb
        c.drawCircle(x, y, r, paint)
    }

    private fun touchCircle(c: Canvas, x: Float, y: Float, r: Float) {
        paint.style = Paint.Style.FILL; paint.color = Color.argb(85, 239, 68, 68)
        c.drawCircle(x, y, r, paint)
        ring(c, x, y, r, Color.WHITE, 2f * d)
    }

    private fun glowDot(c: Canvas, x: Float, y: Float) {
        paint.style = Paint.Style.FILL; paint.color = Color.WHITE
        c.drawCircle(x, y, 4f * d, paint)
        ring(c, x, y, 6.5f * d, Color.argb(160, 0, 0, 0), 1.2f * d)
    }

    private fun glowAlpha(base: Int): Int {
        val factor = (glowStep / 10f).coerceIn(0f, 1f)
        return (base * factor).roundToInt().coerceIn(0, 255)
    }

    private fun drawGhost(c: Canvas, b: Box): Triple<Int, Float, Float>? {
        val cue = pt(p1); val obj = pt(p2)
        if (Geometry.dist(cue, obj) < 1f) return null
        val best = Geometry.bestPocket(cue, obj, b, ballR) ?: return null
        val (idx, angle) = best
        val pocket = Geometry.pocketCenters(b)[idx]
        val vx = obj.first - pocket.first
        val vy = obj.second - pocket.second
        val vl = hypot(vx, vy)
        if (vl < 1f) return null
        val gx = obj.first + vx / vl * ghostDistPx
        val gy = obj.second + vy / vl * ghostDistPx
        paint.pathEffect = DashPathEffect(floatArrayOf(10f * d, 8f * d), 0f)
        line(c, obj.first, obj.second, pocket.first, pocket.second, max(1.5f * d, thickness * 0.7f * d), rgba(220))
        paint.pathEffect = DashPathEffect(floatArrayOf(2f * d, 6f * d), 0f)
        line(c, cue.first, cue.second, gx, gy, 1.5f * d, rgba(120))
        paint.pathEffect = null
        paint.style = Paint.Style.FILL; paint.color = rgba(40)
        c.drawCircle(gx, gy, ghostR, paint)
        ring(c, gx, gy, ghostR, rgba(255), 1.8f * d)
        ring(c, pocket.first, pocket.second, pocketR + 4 * d + 3 * d * pulse(), rgba(190))
        return Triple(idx + 1, angle, Geometry.dist(cue, obj) + Geometry.dist(obj, pocket))
    }

    private fun diffText(s: String): String {
        if (!Lang.isAr(context)) return s
        return when (s.trim().lowercase()) {
            "very easy" -> "سهل جدا"
            "easy" -> "سهل"
            "medium" -> "متوسط"
            "hard" -> "صعب"
            "very hard" -> "صعب جدا"
            else -> s
        }
    }

    private fun drawHud(c: Canvas, info: Triple<Int, Float, Float>?, segs: List<Pair<Pt, Pt>>, snapped: Boolean) {
        val parts = mutableListOf<String>()
        val ar = Lang.isAr(context)
        parts.add(if (locked) (if (ar) "المستطيل مقفل" else "Rect locked") else (if (ar) "المستطيل غير مقفل" else "Rect unlocked"))
        if (info != null) {
            parts.add("${if (ar) "القطع" else "Cut"} ${info.second.roundToInt()}°")
            parts.add("${if (ar) "الجيب" else "pocket"} ${info.first}")
            parts.add(diffText(Geometry.difficulty(info.second, info.third, rect.width())))
        }
        if (segs.isNotEmpty()) {
            val count = max(0, segs.size - 1)
            parts.add("$count ${if (ar) "ارتداد" else "bank(s)"}")
        }
        if (snapped) parts.add(if (ar) "تم الالتصاق" else "Snapped")
        parts.add("${if (ar) "قوة" else "P"}: $cueForce")

        val txt = parts.joinToString(" | ")
        text.textSize = 10f * d; text.isFakeBoldText = false
        val tw = text.measureText(txt)
        val padH = 10f * d
        val padV = 4f * d
        val boxW = tw + padH * 2
        val boxH = 14f * d + padV * 2
        val cx = rect.centerX()
        val cy = rect.top - boxH / 2 - 4f * d
        val hr = RectF(cx - boxW / 2, cy - boxH / 2, cx + boxW / 2, cy + boxH / 2)
        c.drawRoundRect(hr, 6f * d, 6f * d, hudBg)
        c.drawRoundRect(hr, 6f * d, 6f * d, hudBorder)
        c.drawText(txt, cx, cy + 4f * d, text)
    }

    private fun pulse(): Float {
        val t = (System.currentTimeMillis() % 1200L) / 1200f
        return sin(t * Math.PI.toFloat()).coerceIn(0f, 1f)
    }

    private fun rgba(a: Int) = (color and 0x00FFFFFF) or ((a and 0xFF) shl 24)

    override fun onTouchEvent(e: MotionEvent): Boolean {
        if (hideAll) return false
        val x = e.x; val y = e.y
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                activePt = findTouchTarget(x, y)
                lastX = x; lastY = y
                return activePt != null
            }
            MotionEvent.ACTION_MOVE -> {
                val dx = (x - lastX) * 0.75f
                val dy = (y - lastY) * 0.75f
                lastX = x; lastY = y
                when (val t = activePt) {
                    is PointF -> {
                        t.offset(dx, dy)
                        val (nx, ny) = Geometry.clamp(Pt(t.x, t.y), box())
                        t.set(nx, ny)
                    }
                    is RectF -> {
                        when (touchMode) {
                            "rect_tl" -> { t.left += dx; t.top += dy }
                            "rect_br" -> { t.right += dx; t.bottom += dy }
                            "rect_drag" -> t.offset(dx, dy)
                        }
                    }
                }
                invalidate()
                return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                activePt = null
                touchMode = ""
                save()
                return true
            }
        }
        return super.onTouchEvent(e)
    }

    private fun findTouchTarget(x: Float, y: Float): Any? {
        val targets = mutableListOf<Pair<PointF, String>>()
        if (!aimHidden || flags["ghost"] == true) {
            targets.add(p1 to "p1")
            targets.add(p2 to "p2")
        }
        if (flags["lines"] == true) {
            targets.add(marker to "marker")
        }
        for ((p, _) in targets) {
            val qx = p.x + touchOffX
            if (hypot(x - qx, y - p.y) <= touchR * 1.5f) return p
        }
        if (!locked && !rectHidden) {
            if (hypot(x - rect.left, y - rect.top) <= pocketR * 2f) { touchMode = "rect_tl"; return rect }
            if (hypot(x - rect.right, y - rect.bottom) <= pocketR * 2f) { touchMode = "rect_br"; return rect }
            if (rect.contains(x, y)) { touchMode = "rect_drag"; return rect }
        }
        return null
    }

    fun save() {
        prefs.edit().apply {
            putFloat("p1_x", p1.x); putFloat("p1_y", p1.y)
            putFloat("p2_x", p2.x); putFloat("p2_y", p2.y)
            putFloat("marker_x", marker.x); putFloat("marker_y", marker.y)
            putFloat("r_l", rect.left); putFloat("r_t", rect.top)
            putFloat("r_r", rect.right); putFloat("r_b", rect.bottom)
            putFloat("thickness", thickness)
            putInt("theme_idx", themeIdx)
            putInt("cue_force", cueForce)
            putBoolean("locked", locked)
            putInt("ghost_dist", ghostDistStep)
            putInt("ghost_size", ghostSizeStep)
            putInt("glow_step", glowStep)
            putInt("glow_size", glowSizeStep)
            for ((k, v) in flags) putBoolean("flag_$k", v)
            apply()
        }
    }

    fun load() {
        p1.set(prefs.getFloat("p1_x", 0f), prefs.getFloat("p1_y", 0f))
        p2.set(prefs.getFloat("p2_x", 0f), prefs.getFloat("p2_y", 0f))
        marker.set(prefs.getFloat("marker_x", 0f), prefs.getFloat("marker_y", 0f))
        rect.set(
            prefs.getFloat("r_l", 0f), prefs.getFloat("r_t", 0f),
            prefs.getFloat("r_r", 0f), prefs.getFloat("r_b", 0f)
        )
        thickness = prefs.getFloat("thickness", 3f)
        themeIdx = prefs.getInt("theme_idx", 0).coerceIn(0, THEMES.size - 1)
        cueForce = prefs.getInt("cue_force", 1).coerceIn(1, 10)
        locked = prefs.getBoolean("locked", false)
        ghostDistStep = prefs.getInt("ghost_dist", 0)
        ghostSizeStep = prefs.getInt("ghost_size", 0)
        glowStep = prefs.getInt("glow_step", 5)
        glowSizeStep = prefs.getInt("glow_size", 5)
        for (k in flags.keys.toList()) {
            flags[k] = prefs.getBoolean("flag_$k", flags[k] ?: false)
        }
    }

    fun resetTable() {
        val w = width.toFloat(); val h = height.toFloat()
        if (w <= 0 || h <= 0) return
        val padX = w * 0.08f; val padY = h * 0.12f
        rect.set(padX, padY, w - padX, h - padY)
        p1.set(rect.centerX() - 60f * d, rect.centerY())
        p2.set(rect.centerX() + 60f * d, rect.centerY())
        marker.set(rect.centerX(), rect.centerY() - 40f * d)
        save(); invalidate()
    }
}
