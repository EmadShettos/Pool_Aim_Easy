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
        "lines" to true, "single" to false, "bank" to false, "double_bank" to true,
        "ghost" to true, "hud" to true, "animate" to false, "snap" to false, "glow" to false
    )
    var thickness = 3f
    var themeIdx = 0
    var cueForce = 1
    var locked = false
    var rectHidden = false
    var aimHidden = false
    var hideAll = false
    var ghostDistStep = 6
    var ghostSizeStep = 9
    var glowStep = 5
    var glowSizeStep = 0
    var onChanged: (() -> Unit)? = null
    private var loaded = false

    private val pocketR = 14f * d
    private val touchR = 15.4f * d
    private val snapThresh = 28f * d
    val touchOffX get() = 2.9f * pocketR
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { strokeCap = Paint.Cap.ROUND }
    private val text = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE; textAlign = Paint.Align.CENTER }

    private val touchFill = Color.argb(55, 255, 60, 60)

    init { load(); flags["animate"] = false }

    private val color get() = THEMES[themeIdx].second
    private fun rgba(a: Int) = (color and 0x00FFFFFF) or (a shl 24)
    private fun box() = Box(rect.left, rect.top, rect.right, rect.bottom)
    private fun pt(p: PointF) = Pt(p.x, p.y)
    private val ballR get() = 9f * d
    private val ghostR get() = 9f * d * (ghostSizeStep + 3) / 12f
    private val ghostDistPx get() = ghostDistStep * 3f * d
    private fun pulse() = 0.5f

    private fun screenW() = width.toFloat().takeIf { it > 0 } ?: resources.displayMetrics.widthPixels.toFloat()
    private fun screenH() = height.toFloat().takeIf { it > 0 } ?: resources.displayMetrics.heightPixels.toFloat()

    // الوضع الافتراضي للعبة 8 Ball Pool (قياس من لقطة الشاشة): الزاوية العلوية اليسرى والسفلية اليمنى
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

    override fun onSizeChanged(w: Int, h: Int, ow: Int, oh: Int) {
        val orientationChanged = (ow > 0 && oh > 0) && ((ow > oh) != (w > h))
        if (!loaded || orientationChanged) {
            resetLayout()
            loaded = true
        }
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
            "p1", "p2" -> {
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

    private fun keepInside() {
        for (p in listOf(p1, p2, marker)) {
            val (x, y) = Geometry.clamp(Pt(p.x, p.y), box()); p.set(x, y)
        }
    }

    // ---------- drawing ----------
    private fun glowAlpha(base: Int) = (base * glowStep / 5f).toInt().coerceIn(0, 255)

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
        paint.color = Color.BLACK; c.drawCircle(x, y, 0.9f * d, paint)
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

            // رسم خط مسار ارتداد الكرة البيضاء: هالة بلون أبيض شفاف فقط بدون أسهم وعرضها كعرض الكرة البيضاء
            val cueTangentSegs = Geometry.cueTangentSegments(pt(p1), pt(p2), Pt(dx / len, dy / len), b, 1, cueForce)
            for (s in cueTangentSegs) {
                paint.pathEffect = null
                paint.style = Paint.Style.STROKE
                paint.strokeWidth = 2f * ballR
                paint.color = Color.argb(60, 255, 255, 255)
                c.drawLine(s.first.first, s.first.second, s.second.first, s.second.second, paint)
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
        val ar = Lang.isAr(context)
        val parts = mutableListOf(Lang.t(context, if (locked) "hud_locked" else "hud_unlocked"))
        if (info != null) {
            val cut = Lang.t(context, "hud_cut")
            val pk = Lang.t(context, "hud_pocket")
            parts.add(
                if (ar) "$cut ${info.second.toInt()}\u00B0 - $pk ${info.first}"
                else "$cut ${info.second.toInt()}\u00B0 > $pk ${info.first}"
            )
            parts.add(diffText(Geometry.difficulty(info.second, info.third, rect.width())))
        }
        if (segs.isNotEmpty()) {
            parts.add("${max(0, segs.size - 1)} ${Lang.t(context, "hud_banks")}")
            parts.add("${if (ar) "قوة" else "P"}: $cueForce")
            if (snapped) parts.add(Lang.t(context, "hud_pocketed"))
        }
        val s = parts.joinToString("  |  ")
        text.textSize = 12f * d
        text.isFakeBoldText = false
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
