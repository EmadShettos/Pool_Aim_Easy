package com.example.poolaim

import android.content.Context
import android.graphics.*
import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

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
    private var lastTarget = "p2"
    private var loaded = false

    private val pocketR = 14f * d
    private val hit = 44f * d
    private val grip = 10f * d
    private val snapThresh = 24f * d
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { strokeCap = Paint.Cap.ROUND }
    private val text = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE; textAlign = Paint.Align.CENTER }

    private var dragKind: String? = null
    private var offX = 0f
    private var offY = 0f

    init { load() }

    private val color get() = THEMES[themeIdx].second
    private fun rgba(a: Int) = (color and 0x00FFFFFF) or (a shl 24)
    private fun box() = Box(rect.left, rect.top, rect.right, rect.bottom)
    private fun pt(p: PointF) = Pt(p.x, p.y)
    private val ballR get() = rect.width() * 0.0125f
    private val tsec get() = (SystemClock.uptimeMillis() - t0) / 1000f
    private fun pulse() = if (flags["animate"] == true) 0.5f + 0.5f * sin(tsec * 4f) else 0.5f

    fun resetLayout() {
        val w = width.toFloat().takeIf { it > 0 } ?: resources.displayMetrics.widthPixels.toFloat()
        val h = height.toFloat().takeIf { it > 0 } ?: resources.displayMetrics.heightPixels.toFloat()
        rect.set(w * 0.08f, h * 0.22f, w * 0.92f, h * 0.78f)
        p1.set(rect.left + rect.width() * 0.25f, rect.centerY())
        p2.set(rect.right - rect.width() * 0.25f, rect.centerY())
        marker.set(rect.centerX(), rect.centerY())
        lastTarget = "p2"
        invalidate()
    }

    override fun onSizeChanged(w: Int, h: Int, ow: Int, oh: Int) {
        if (!loaded) { resetLayout(); loaded = true }
    }

    fun swap() {
        val x = p1.x; val y = p1.y
        p1.set(p2.x, p2.y); p2.set(x, y)
        lastTarget = if (lastTarget == "p2") "p1" else "p2"
        invalidate()
    }

    fun toggle(k: String) { flags[k] = !(flags[k] ?: false); save(); invalidate() }
    fun nextTheme() { themeIdx = (themeIdx + 1) % THEMES.size; save(); invalidate() }
    fun thicker(delta: Float) { thickness = (thickness + delta).coerceIn(1f, 10f); save(); invalidate() }

    // ---------- drawing ----------
    private fun shadowLine(c: Canvas, a: PointF, b: PointF) {
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = pocketR; paint.color = rgba(30); c.drawLine(a.x, a.y, b.x, b.y, paint)
        paint.strokeWidth = pocketR * 0.6f; paint.color = rgba(70); c.drawLine(a.x, a.y, b.x, b.y, paint)
    }

    private fun line(c: Canvas, ax: Float, ay: Float, bx: Float, by: Float, w: Float, col: Int) {
        paint.style = Paint.Style.STROKE; paint.strokeWidth = w; paint.color = col
        c.drawLine(ax, ay, bx, by, paint)
    }

    private fun ring(c: Canvas, x: Float, y: Float, r: Float, col: Int, w: Float = 2f * d) {
        paint.style = Paint.Style.STROKE; paint.strokeWidth = w; paint.color = col
        c.drawCircle(x, y, r, paint)
    }

    override fun onDraw(c: Canvas) {
        val th = thickness * d
        val b = box()
        val pockets = Geometry.pocketCenters(b)

        paint.pathEffect = null
        ring(c, 0f, 0f, 0f, 0) // reset state
        paint.style = Paint.Style.STROKE; paint.strokeWidth = 3f * d; paint.color = Color.WHITE
        c.drawRoundRect(rect, 22f * d, 22f * d, paint)
        for (p in pockets) ring(c, p.first, p.second, pocketR, color, 1f * d)

        if (flags["lines"] == true) {
            for (p in pockets) shadowLine(c, marker, PointF(p.first, p.second))
            for (p in pockets) line(c, marker.x, marker.y, p.first, p.second, th, color)
            ring(c, marker.x, marker.y, pocketR, color, th)
        }
        if (flags["single"] == true) {
            shadowLine(c, p1, p2); line(c, p1.x, p1.y, p2.x, p2.y, th, color)
        }

        var segs: List<Pair<Pt, Pt>> = emptyList()
        var snapped = false
        val dx = p2.x - p1.x
        val dy = p2.y - p1.y
        if ((flags["bank"] == true || flags["double_bank"] == true) && (dx != 0f || dy != 0f)) {
            val len = hypot(dx, dy)
            val maxBanks = if (flags["double_bank"] == true) 4 else 2
            val bw = max(2f * d, th + d)
            shadowLine(c, p1, p2); line(c, p1.x, p1.y, p2.x, p2.y, bw, color)
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
                shadowLine(c, PointF(s.first.first, s.first.second), PointF(s.second.first, s.second.second))
                line(c, s.first.first, s.first.second, s.second.first, s.second.second, bw, color)
                if (i < m.size - 1) {
                    paint.style = Paint.Style.FILL; paint.color = rgba(200)
                    c.drawCircle(s.second.first, s.second.second, 3.5f * d, paint)
                }
            }
        }

        var info: Triple<Int, Float, Float>? = null
        if (flags["ghost"] == true) info = drawGhost(c, b)

        for ((p, label) in listOf(p1 to "C", p2 to "8")) {
            ring(c, p.x, p.y, pocketR, Color.WHITE)
            text.textSize = 11f * d; text.isFakeBoldText = true
            c.drawText(label, p.x, p.y + 4f * d, text)
        }

        if (!locked) {
            paint.style = Paint.Style.FILL; paint.color = Color.WHITE
            for ((_, g) in gripPoints()) c.drawRect(g.x - grip / 2, g.y - grip / 2, g.x + grip / 2, g.y + grip / 2, paint)
        }

        if (flags["hud"] == true) drawHud(c, info, segs, snapped)
        if (flags["animate"] == true) postInvalidateOnAnimation()
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
        val parts = mutableListOf(if (locked) "Locked" else "Edit")
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

    // ---------- touch ----------
    private fun gripPoints(): List<Pair<String, PointF>> = listOf(
        "LT" to PointF(rect.left, rect.top), "T" to PointF(rect.centerX(), rect.top),
        "RT" to PointF(rect.right, rect.top), "L" to PointF(rect.left, rect.centerY()),
        "R" to PointF(rect.right, rect.centerY()), "LB" to PointF(rect.left, rect.bottom),
        "B" to PointF(rect.centerX(), rect.bottom), "RB" to PointF(rect.right, rect.bottom)
    )

    private fun near(p: PointF, x: Float, y: Float, r: Float) = hypot(p.x - x, p.y - y) <= r

    private fun clampToTable(x: Float, y: Float): Pair<Float, Float> =
        Geometry.clamp(Pt(x, y), box())

    private fun place(which: String, x: Float, y: Float) {
        var (nx, ny) = clampToTable(x, y)
        if (flags["snap"] == true) {
            val (pc, dd) = Geometry.nearestPocket(Pt(nx, ny), box())
            if (dd <= snapThresh) { nx = pc.first; ny = pc.second }
        }
        if (which == "p1") p1.set(nx, ny) else p2.set(nx, ny)
    }

    private fun keepInside() {
        for (p in listOf(p1, p2, marker)) { val (x, y) = clampToTable(p.x, p.y); p.set(x, y) }
    }

    override fun onTouchEvent(e: MotionEvent): Boolean {
        if (locked) return false
        val x = e.x; val y = e.y
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                dragKind = when {
                    near(p1, x, y, hit) -> { lastTarget = "p1"; "p1" }
                    near(p2, x, y, hit) -> { lastTarget = "p2"; "p2" }
                    near(marker, x, y, hit) -> "marker"
                    else -> gripPoints().firstOrNull { near(it.second, x, y, hit) }?.let { "g:" + it.first }
                        ?: if (rect.contains(x, y)) { offX = x - rect.left; offY = y - rect.top; "table" } else null
                }
                return dragKind != null
            }
            MotionEvent.ACTION_MOVE -> {
                val k = dragKind ?: return false
                when {
                    k == "p1" || k == "p2" -> { val (nx, ny) = clampToTable(x, y); (if (k == "p1") p1 else p2).set(nx, ny) }
                    k == "marker" -> { val (nx, ny) = clampToTable(x, y); marker.set(nx, ny) }
                    k.startsWith("g:") -> resize(k.substring(2), x, y)
                    k == "table" -> {
                        val w = rect.width(); val h = rect.height()
                        val nl = (x - offX).coerceIn(0f, max(0f, width - w))
                        val nt = (y - offY).coerceIn(0f, max(0f, height - h))
                        val dx = nl - rect.left; val dy = nt - rect.top
                        rect.offsetTo(nl, nt)
                        p1.offset(dx, dy); p2.offset(dx, dy); marker.offset(dx, dy)
                    }
                }
                invalidate(); return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                val k = dragKind
                if (k == "p1") place("p1", p1.x, p1.y)
                if (k == "p2") place("p2", p2.x, p2.y)
                dragKind = null; save(); invalidate(); return true
            }
        }
        return false
    }

    private fun resize(edge: String, x: Float, y: Float) {
        val cx = x.coerceIn(0f, width.toFloat()); val cy = y.coerceIn(0f, height.toFloat())
        val minW = 120f * d; val minH = 80f * d
        if (edge.contains('L')) rect.left = min(cx, rect.right - minW)
        if (edge.contains('R')) rect.right = max(cx, rect.left + minW)
        if (edge.contains('T')) rect.top = min(cy, rect.bottom - minH)
        if (edge.contains('B')) rect.bottom = max(cy, rect.top + minH)
        keepInside()
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
