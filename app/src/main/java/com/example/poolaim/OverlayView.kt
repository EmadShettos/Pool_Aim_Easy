package com.example.poolaim

import android.content.Context
import android.graphics.*
import android.os.SystemClock
import android.view.View
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

class OverlayView(ctx: Context) : View(ctx) {
    private val prefs = ctx.getSharedPreferences("pool_aim", Context.MODE_PRIVATE)
    private val d = resources.displayMetrics.density
    private val t0 = SystemClock.uptimeMillis()

    val rect = RectF()
    val p1 = PointF()
    val p2 = PointF()
    val marker = PointF()
    val flags = linkedMapOf(
        "lines" to true, "single" to true, "bank" to true, "double_bank" to true,
        "ghost" to true, "hud" to true, "animate" to false, "snap" to true
    )
    var thickness = 3f
    var themeIdx = 0
    var locked = false
    var rectHidden = false
    var aimHidden = false
    var onChanged: (() -> Unit)? = null
    private var loaded = false

    private val pocketR = 14f * d
    private val snapThresh = 24f * d
    val touchOffX get() = 2.6f * pocketR
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { strokeCap = Paint.Cap.ROUND }
    private val text = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE; textAlign = Paint.Align.CENTER }

    private val touchFill = Color.argb(55, 255, 60, 60)

    init { load(); flags["animate"] = false }

    private val color get() = THEMES[themeIdx].second
    private fun rgba(a: Int) = (color and 0x00FFFFFF) or (a shl 24)
    private fun box() = Box(rect.left, rect.top, rect.right, rect.bottom)
    private fun pt(p: PointF) = Pt(p.x, p.y)
    private val ballR get() = rect.width() * 0.0375f
    private fun pulse() = 0.5f

    private val haloOuter = Color.argb(60, 255, 255, 255)
    private val haloInner = Color.argb(110, 255, 255, 255)

    fun resetLayout() {
        val w = width.toFloat().takeIf { it > 0 } ?: resources.displayMetrics.widthPixels.toFloat()
        val h = height.toFloat().takeIf { it > 0 } ?: resources.displayMetrics.heightPixels.toFloat()
        rect.set(w * 0.08f, h * 0.22f, w * 0.92f, h * 0.78f)
        p1.set(rect.left + rect.width() * 0.25f, rect.centerY())
        p2.set(rect.right - rect.width() * 0.25f, rect.centerY())
        marker.set(rect.centerX(), rect.centerY())
        invalidate()
        onChanged?.invoke()
    }

    override fun onSizeChanged(w: Int, h: Int, ow: Int, oh: Int) {
        if (!loaded) { resetLayout(); loaded = true }
        onChanged?.invoke()
    }

    fun toggle(k: String) { flags[k] = !(flags[k] ?: false); flags["animate"] = false; save(); invalidate() }
    fun nextTheme() { themeIdx = (themeIdx + 1) % THEMES.size; save(); invalidate() }
    fun thicker(delta: Float) { thickness = (thickness + delta).coerceIn(1f, 10f); save(); invalidate() }

    fun pos(key: String): PointF = when (key) {
        "p1" -> PointF(p1.x, p1.y)
        "p2" -> PointF(p2.x, p2.y)
        "marker" -> PointF(marker.x, marker.y)
        "tl" -> PointF(rect.left, rect.top)
        else -> PointF(rect.right, rect.bottom)
    }

    fun touchPos(key: String): PointF {
        val p = pos(key)
        return if (key == "p1" || key == "p2" || key == "marker") PointF(p.x + touchOffX, p.y) else p
    }

    fun setPoint(key: String, x: Float, y: Float, final: Boolean) {
        when (key) {
            "p1", "p2" -> if (final) place(key, x, y) else {
                val (nx, ny) = Geometry.clamp(Pt(x, y), box())
                (if (key == "p1") p1 else p2).set(nx, ny)
            }
            "marker" -> { val (nx, ny) = Geometry.clamp(Pt(x, y), box()); marker.set(nx, ny) }
            "tl" -> {
                rect.left = min(x.coerceIn(0f, width.toFloat()), rect.right - 120f * d)
                rect.top = min(y.coerceIn(0f, height.toFloat()), rect.bottom - 80f * d)
                keepInside()
            }
            "br" -> {
                rect.right = max(x.coerceIn(0f, width.toFloat()), rect.left + 120f * d)
                rect.bottom = max(y.coerceIn(0f, height.toFloat()), rect.top + 80f * d)
                keepInside()
            }
        }
        invalidate()
    }

    private fun place(which: String, x: Float, y: Float) {
        var (nx, ny) = Geometry.clamp(Pt(x, y), box())
        if (flags["snap"] == true) {
            val (pc, dd) = Geometry.nearestPocket(Pt(nx, ny), box())
            if (dd <= snapThresh) { nx = pc.first; ny = pc.second }
        }
        if (which == "p1") p1.set(nx, ny) else p2.set(nx, ny)
    }

    private fun keepInside() {
        for (p in listOf(p1, p2, marker)) {
            val (x, y) = Geometry.clamp(Pt(p.x, p.y), box()); p.set(x, y)
        }
    }

    // ---------- drawing ----------
    private fun shadowLine(c: Canvas, a: PointF, b: PointF, outer: Int = rgba(30), inner: Int = rgba(70)) {
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = pocketR; paint.color = outer; c.drawLine(a.x, a.y, b.x, b.y, paint)
        paint.strokeWidth = pocketR * 0.6f; paint.color = inner; c.drawLine(a.x, a.y, b.x, b.y, paint)
    }

    private fun line(c: Canvas, ax: Float, ay: Float, bx: Float, by: Float, w: Float, col: Int) {
        paint.style = Paint.Style.STROKE; paint.strokeWidth = w; paint.color = col
        c.drawLine(ax, ay, bx, by, paint)
    }

    private fun ring(c: Canvas, x: Float, y: Float, r: Float, col: Int, w: Float = 2f * d) {
        paint.style = Paint.Style.STROKE; paint.strokeWidth = w; paint.color = col
        c.drawCircle(x, y, r, paint)
    }

    private fun touchCircle(c: Canvas, x: Float, y: Float, r: Float) {
        paint.style = Paint.Style.FILL; paint.color = touchFill
        c.drawCircle(x, y, r, paint)
        ring(c, x, y, r, Color.WHITE, 2f * d)
    }

    private fun glowDot(c: Canvas, x: Float, y: Float) {
        paint.style = Paint.Style.FILL
        paint.color = Color.argb(70, 255, 20, 20); c.drawCircle(x, y, 5.5f * d, paint)
        paint.color = Color.argb(150, 255, 30, 30); c.drawCircle(x, y, 3.5f * d, paint)
        paint.color = Color.rgb(255, 40, 40); c.drawCircle(x, y, 2f * d, paint)
    }

    override fun onDraw(c: Canvas) {
        val th = thickness * d
        val b = box()
        val pockets = Geometry.pocketCenters(b)
        paint.pathEffect = null

        if (!rectHidden) {
            paint.style = Paint.Style.STROKE; paint.strokeWidth = 3f * d; paint.color = Color.WHITE
            c.drawRoundRect(rect, 22f * d, 22f * d, paint)
        }

        if (flags["lines"] == true) {
            for (p in pockets) shadowLine(c, marker, PointF(p.first, p.second))
            for (p in pockets) line(c, marker.x, marker.y, p.first, p.second, th, color)
        }
        if (!aimHidden && flags["single"] == true) {
            shadowLine(c, p1, p2, haloOuter, haloInner)
            line(c, p1.x, p1.y, p2.x, p2.y, th, Color.BLACK)
        }

        var segs: List<Pair<Pt, Pt>> = emptyList()
        var snapped = false
        val dx = p2.x - p1.x
        val dy = p2.y - p1.y
        if (!aimHidden && (flags["bank"] == true || flags["double_bank"] == true) && (dx != 0f || dy != 0f)) {
            val len = hypot(dx, dy)
            val maxBanks = if (flags["double_bank"] == true) 4 else 2
            val bw = max(2f * d, th + d)
            shadowLine(c, p1, p2, haloOuter, haloInner)
            line(c, p1.x, p1.y, p2.x, p2.y, bw, Color.BLACK)
            val m = Geometry.bankSegments(pt(p2), Pt(dx / len, dy / len), b, maxBanks).toMutableList()
            if (m.isNotEmpty()) {
                val last = m.last()
                val (np, dd) = Geometry.nearestPocket(last.second, b)
                if (dd <= pocketR * 1.4f) {
                    m[m.lastIndex] = last.first to np; snapped = true
                    ring(c, np.first, np.second, pocketR + 3 * d + 5 * d * pulse(), rgba((120 + 130 * pulse()).toInt()))
                }
            }
            segs = m
            for ((i, s) in m.withIndex()) {
                shadowLine(c, PointF(s.first.first, s.first.second), PointF(s.second.first, s.second.second), haloOuter, haloInner)
                line(c, s.first.first, s.first.second, s.second.first, s.second.second, bw, Color.BLACK)
                if (i < m.size - 1) {
                    paint.style = Paint.Style.FILL; paint.color = Color.argb(200, 0, 0, 0)
                    c.drawCircle(s.second.first, s.second.second, 3.5f * d, paint)
                }
            }
        }

        var info: Triple<Int, Float, Float>? = null
        if (!aimHidden && flags["ghost"] == true) info = drawGhost(c, b)

        if (!aimHidden) {
            for ((p, label) in listOf(p1 to "C", p2 to "8")) {
                val qx = p.x + touchOffX
                touchCircle(c, qx, p.y, pocketR)
                text.textSize = 10f * d; text.isFakeBoldText = true
                c.drawText(label, qx, p.y - pocketR - 4f * d, text)
                glowDot(c, p.x, p.y)
            }
        }
        if (flags["lines"] == true) {
            touchCircle(c, marker.x + touchOffX, marker.y, pocketR)
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

    private fun drawGhost(c: Canvas, b: Box): Triple<Int, Float, Float>? {
        val cue = pt(p1); val obj = pt(p2)
        if (Geometry.dist(cue, obj) < 1f) return null
        val best = Geometry.bestPocket(cue, obj, b, ballR) ?: return null
        val (idx, angle) = best
        val pocket = Geometry.pocketCenters(b)[idx]
        val ghost = Geometry.ghostBall(obj, pocket, ballR)
        paint.pathEffect = DashPathEffect(floatArrayOf(10f * d, 8f * d), 0f)
        line(c, obj.first, obj.second, pocket.first, pocket.second, max(1.5f * d, thickness * 0.7f * d), rgba(220))
        paint.pathEffect = DashPathEffect(floatArrayOf(2f * d, 6f * d), 0f)
        line(c, cue.first, cue.second, ghost.first, ghost.second, 1.5f * d, rgba(120))
        paint.pathEffect = null
        paint.style = Paint.Style.FILL; paint.color = rgba(40)
        c.drawCircle(ghost.first, ghost.second, ballR, paint)
        ring(c, ghost.first, ghost.second, ballR, rgba(255), 1.8f * d)
        ring(c, pocket.first, pocket.second, pocketR + 4 * d + 3 * d * pulse(), rgba(190))
        val label = "${angle.toInt()}\u00B0"
        text.textSize = 12f * d
        val w = text.measureText(label) + 14f * d
        val bx = obj.first + ballR + 6 * d
        val by = obj.second - 30 * d
        paint.style = Paint.Style.FILL; paint.color = Color.argb(215, 15, 17, 23)
        c.drawRoundRect(bx, by, bx + w, by + 20 * d, 10 * d, 10 * d, paint)
        c.drawText(label, bx + w / 2, by + 14 * d, text)
        return Triple(idx + 1, angle, Geometry.dist(cue, obj) + Geometry.dist(obj, pocket))
    }

    private fun drawHud(c: Canvas, info: Triple<Int, Float, Float>?, segs: List<Pair<Pt, Pt>>, snapped: Boolean) {
        val parts = mutableListOf(if (locked) "Rect locked" else "Rect unlocked")
        if (info != null) {
            parts.add("Cut ${info.second.toInt()}\u00B0 > pocket ${info.first}")
            parts.add(Geometry.difficulty(info.second, info.third, rect.width()))
        }
        if (segs.isNotEmpty()) {
            parts.add("${max(0, segs.size - 1)} bank(s)")
            if (snapped) parts.add("pocketed")
        }
        val s = parts.joinToString("  |  ")
        text.textSize = 12f * d
        val w = text.measureText(s) + 36f * d
        val x = (width - w) / 2
        paint.style = Paint.Style.FILL; paint.color = Color.argb(215, 15, 17, 23)
        c.drawRoundRect(x, 12f * d, x + w, 40f * d, 14f * d, 14f * d, paint)
        c.drawText(s, width / 2f, 31f * d, text)
    }

    // ---------- persistence ----------
    fun save() {
        prefs.edit().apply {
            putFloat("l", rect.left); putFloat("t", rect.top); putFloat("r", rect.right); putFloat("b", rect.bottom)
            putFloat("p1x", p1.x); putFloat("p1y", p1.y); putFloat("p2x", p2.x); putFloat("p2y", p2.y)
            putFloat("mx", marker.x); putFloat("my", marker.y)
            putFloat("thick", thickness); putInt("theme", themeIdx)
            for ((k, v) in flags) putBoolean("f_$k", v)
            apply()
        }
    }

    private fun load() {
        if (!prefs.contains("l")) return
        val l = prefs.getFloat("l", 0f); val t = prefs.getFloat("t", 0f)
        val r = prefs.getFloat("r", 0f); val b = prefs.getFloat("b", 0f)
        if (r - l < 120f * d || b - t < 80f * d) return
        rect.set(l, t, r, b)
        p1.set(prefs.getFloat("p1x", 0f), prefs.getFloat("p1y", 0f))
        p2.set(prefs.getFloat("p2x", 0f), prefs.getFloat("p2y", 0f))
        marker.set(prefs.getFloat("mx", 0f), prefs.getFloat("my", 0f))
        thickness = prefs.getFloat("thick", 3f).coerceIn(1f, 10f)
        themeIdx = prefs.getInt("theme", 0).coerceIn(0, THEMES.size - 1)
        for (k in flags.keys.toList()) flags[k] = prefs.getBoolean("f_$k", flags[k]!!)
        keepInside()
        loaded = true
    }
}
