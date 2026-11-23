package com.example.poolaim

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.PointF
import android.graphics.RectF
import android.view.View
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

class OverlayView(context: Context) : View(context) {
    private val d = resources.displayMetrics.density
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val text = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textAlign = Paint.Align.CENTER
    }

    private val prefs = context.getSharedPreferences("overlay_prefs", Context.MODE_PRIVATE)

    val flags = linkedMapOf(
        "lines" to true,
        "single" to false,
        "bank" to false,
        "double_bank" to true,
        "ghost" to true,
        "hud" to true,
        "animate" to false,
        "snap" to false,
        "glow" to false
    )

    var thickness = 3f
    var themeIdx = 0
    var cueForce = 1

    var ghostDistStep = 6
    var ghostSizeStep = 9
    var glowStep = 5
    var glowSizeStep = 0

    var locked = false
    var rectHidden = false
    var aimHidden = false
    var hideAll = false

    var onChanged: (() -> Unit)? = null

    private val rect = RectF()
    private val p1 = PointF()
    private val p2 = PointF()
    private val marker = PointF()

    private var loaded = false

    init {
        load()
        flags["animate"] = false
    }

    private val color get() = THEMES[themeIdx].second
    private val ballR get() = rect.width() * 0.0175f
    private val pocketR get() = ballR * 1.25f
    private val ghostR get() = ballR * (0.8f + ghostSizeStep * 0.04f)
    private val ghostDistPx get() = ballR * (2f + ghostDistStep * 0.15f)
    private val snapThresh get() = pocketR * 2.2f

    fun toggle(k: String) {
        flags[k] = !(flags[k] ?: false)
        save(); invalidate()
    }

    fun nextTheme(): String {
        themeIdx = (themeIdx + 1) % THEMES.size
        save(); invalidate()
        return THEMES[themeIdx].first
    }

    private fun screenW() = width.toFloat().takeIf { it > 0 } ?: resources.displayMetrics.widthPixels.toFloat()
    private fun screenH() = height.toFloat().takeIf { it > 0 } ?: resources.displayMetrics.heightPixels.toFloat()

    fun applyGameLayout() {
        val w = screenW()
        val h = screenH()
        rect.set(w * 0.197f, h * 0.241f, w * 0.803f, h * 0.904f)
        p1.set(rect.left + rect.width() * 0.25f, rect.centerY())
        p2.set(rect.right - rect.width() * 0.25f, rect.centerY())
        marker.set(rect.centerX(), rect.centerY())
        save()
        invalidate()
        onChanged?.invoke()
    }

    fun resetLayout() {
        val w = screenW()
        val h = screenH()

        if (w > h) {
            rect.set(w * 0.197f, h * 0.241f, w * 0.803f, h * 0.904f)
        } else {
            val tw = w * 0.92f
            val th = tw / 2.0f
            val l = (w - tw) / 2f
            val t = h * 0.28f
            rect.set(l, t, l + tw, t + th)
        }

        p1.set(rect.left + rect.width() * 0.25f, rect.centerY())
        p2.set(rect.right - rect.width() * 0.25f, rect.centerY())
        marker.set(rect.centerX(), rect.centerY())
        invalidate()
        onChanged?.invoke()
    }

    fun rotateTable() {
        val w = screenW()
        val h = screenH()

        if (w > h) {
            applyGameLayout()
            return
        }

        val nl = w - rect.right
        val nr = w - rect.left
        val nt = h - rect.bottom
        val nb = h - rect.top
        rect.set(nl, nt, nr, nb)

        fun rot(p: PointF) {
            p.set(w - p.x, h - p.y)
        }
        rot(p1)
        rot(p2)
        rot(marker)

        save()
        invalidate()
        onChanged?.invoke()
    }

    fun thicker(delta: Float) {
        thickness = (thickness + delta).coerceIn(1f, 10f)
        save(); invalidate()
    }

    fun pos(key: String): PointF = when (key) {
        "p1" -> p1
        "p2" -> p2
        "marker" -> marker
        "tl" -> PointF(rect.left, rect.top)
        "br" -> PointF(rect.right, rect.bottom)
        else -> PointF()
    }

    fun touchPos(key: String): PointF {
        val p = pos(key)
        return PointF(p.x, p.y)
    }

    fun setPoint(key: String, x: Float, y: Float, final: Boolean) {
        when (key) {
            "p1" -> p1.set(x, y)
            "p2" -> p2.set(x, y)
            "marker" -> marker.set(x, y)
            "tl" -> {
                rect.left = min(x, rect.right - 100f * d)
                rect.top = min(y, rect.bottom - 60f * d)
            }
            "br" -> {
                rect.right = max(x, rect.left + 100f * d)
                rect.bottom = max(y, rect.top + 60f * d)
            }
        }
        keepInside()
        invalidate()
        if (final) onChanged?.invoke()
    }

    private fun keepInside() {
        val b = box()
        fun clamp(p: PointF) {
            val (x, y) = Geometry.clamp(Pt(p.x, p.y), b)
            p.set(x, y)
        }
        clamp(p1)
        clamp(p2)
        clamp(marker)
    }

    private fun box(): Box = Box(rect.left, rect.top, rect.right, rect.bottom)
    private fun pt(p: PointF): Pt = Pt(p.x, p.y)

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        if (!loaded) {
            resetLayout()
            save()
        } else {
            keepInside()
        }
        post { onChanged?.invoke() }
    }

    override fun onDraw(c: Canvas) {
        if (hideAll) return
        val th = thickness * d
        val b = box()
        val pockets = Geometry.pocketCenters(b)
        paint.pathEffect = null

        if (!rectHidden) {
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = 2.25f * d
            paint.color = Color.WHITE
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

            // رسم مسار ارتداد الكرة البيضاء بهالة متوهجة
            val cueTangentSegs = Geometry.cueTangentSegments(pt(p1), pt(p2), Pt(dx / len, dy / len), b, 1, cueForce)
            for (s in cueTangentSegs) {
                paint.pathEffect = null
                paint.style = Paint.Style.STROKE
                paint.strokeWidth = 10f * d
                paint.color = Color.argb(65, 255, 255, 255)
                c.drawLine(s.first.first, s.first.second, s.second.first, s.second.second, paint)

                paint.strokeWidth = 6f * d
                paint.color = Color.argb(110, 255, 255, 255)
                c.drawLine(s.first.first, s.first.second, s.second.first, s.second.second, paint)

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
            glowDot(c, p1.x, p1.y)
            glowDot(c, p2.x, p2.y)
        }
        if (flags["lines"] == true) {
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

        val s = parts.joinToString(" | ")
        text.textSize = 12f * d
        val w = text.measureText(s) + 36f * d
        val x = (width - w) / 2
        paint.style = Paint.Style.FILL; paint.color = Color.argb(215, 15, 17, 23)
        c.drawRoundRect(x, 12f * d, x + w, 40f * d, 14f * d, 14f * d, paint)
        c.drawText(s, width / 2f, 31f * d, text)
    }

    private fun pulse(): Float {
        val t = (System.currentTimeMillis() % 1200L) / 1200f
        return sin(t * Math.PI.toFloat()).coerceIn(0f, 1f)
    }

    private fun rgba(a: Int) = (color and 0x00FFFFFF) or ((a and 0xFF) shl 24)

    fun save() {
        prefs.edit().apply {
            putFloat("l", rect.left); putFloat("t", rect.top); putFloat("r", rect.right); putFloat("b", rect.bottom)
            putFloat("p1x", p1.x); putFloat("p1y", p1.y); putFloat("p2x", p2.x); putFloat("p2y", p2.y)
            putFloat("mx", marker.x); putFloat("my", marker.y)
            putFloat("thick", thickness); putInt("theme", themeIdx)
            putInt("cue_force", cueForce)
            putInt("gdist", ghostDistStep); putInt("gsize", ghostSizeStep)
            putInt("glow_step", glowStep); putInt("glow_size", glowSizeStep)
            putBoolean("locked", locked); putBoolean("rect_hidden", rectHidden)
            putBoolean("aim_hidden", aimHidden); putBoolean("hide_all", hideAll)
            for ((k, v) in flags) putBoolean("f_$k", v)
            apply()
        }
    }

    private fun load() {
        ghostDistStep = prefs.getInt("gdist", 6).coerceIn(0, 10)
        ghostSizeStep = prefs.getInt("gsize", 9).coerceIn(0, 9)
        glowStep = prefs.getInt("glow_step", 5).coerceIn(0, 10)
        glowSizeStep = prefs.getInt("glow_size", 0).coerceIn(0, 10)
        locked = prefs.getBoolean("locked", false)
        rectHidden = prefs.getBoolean("rect_hidden", false)
        aimHidden = prefs.getBoolean("aim_hidden", false)
        hideAll = prefs.getBoolean("hide_all", false)
        thickness = prefs.getFloat("thick", 3f).coerceIn(1f, 10f)
        themeIdx = prefs.getInt("theme", 0).coerceIn(0, THEMES.size - 1)
        cueForce = prefs.getInt("cue_force", 1).coerceIn(1, 10)
        for (k in flags.keys.toList()) flags[k] = prefs.getBoolean("f_$k", flags[k]!!)
        if (!prefs.contains("l")) return
        val l = prefs.getFloat("l", 0f); val t = prefs.getFloat("t", 0f)
        val r = prefs.getFloat("r", 0f); val b = prefs.getFloat("b", 0f)
        if (r - l < 120f * d || b - t < 80f * d) return
        rect.set(l, t, r, b)
        p1.set(prefs.getFloat("p1x", 0f), prefs.getFloat("p1y", 0f))
        p2.set(prefs.getFloat("p2x", 0f), prefs.getFloat("p2y", 0f))
        marker.set(prefs.getFloat("mx", 0f), prefs.getFloat("my", 0f))
        keepInside()
        loaded = true
    }
}
