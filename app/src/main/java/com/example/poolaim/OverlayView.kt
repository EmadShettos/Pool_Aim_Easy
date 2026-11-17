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

class OverlayView(ctx: Context) : View(ctx) {

    private val prefs =
        ctx.getSharedPreferences("pool_aim", Context.MODE_PRIVATE)

    private val d = resources.displayMetrics.density

    val rect = RectF()
    val p1 = PointF()
    val p2 = PointF()
    val marker = PointF()

    val flags = linkedMapOf(
        "lines" to true,
        "single" to true,
        "bank" to true,
        "double_bank" to true,
        "ghost" to true,
        "hud" to true,
        "animate" to false,
        "snap" to true
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

    val touchOffX: Float
        get() = 2.6f * pocketR

    // Fixed drawing radius: 10% larger than the previous 9dp.
    private val ballR: Float
        get() = 9.9f * d

    private val ghostExtraDistance: Float
        get() = 1f * d

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        strokeCap = Paint.Cap.ROUND
    }

    private val text = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textAlign = Paint.Align.CENTER
    }

    private val touchFill = Color.argb(55, 255, 60, 60)

    private val color: Int
        get() = THEMES[themeIdx].second

    init {
        load()
        flags["animate"] = false
    }

    private fun rgba(alpha: Int): Int {
        return (color and 0x00FFFFFF) or
            (alpha.coerceIn(0, 255) shl 24)
    }

    private fun box(): Box {
        return Box(
            rect.left,
            rect.top,
            rect.right,
            rect.bottom
        )
    }

    private fun pt(p: PointF): Pt {
        return Pt(p.x, p.y)
    }

    fun resetLayout() {
        val w = width.toFloat().takeIf { it > 0f }
            ?: resources.displayMetrics.widthPixels.toFloat()

        val h = height.toFloat().takeIf { it > 0f }
            ?: resources.displayMetrics.heightPixels.toFloat()

        rect.set(
            w * 0.08f,
            h * 0.22f,
            w * 0.92f,
            h * 0.78f
        )

        p1.set(
            rect.left + rect.width() * 0.25f,
            rect.centerY()
        )

        p2.set(
            rect.right - rect.width() * 0.25f,
            rect.centerY()
        )

        marker.set(rect.centerX(), rect.centerY())

        invalidate()
        onChanged?.invoke()
    }

    override fun onSizeChanged(
        w: Int,
        h: Int,
        oldw: Int,
        oldh: Int
    ) {
        super.onSizeChanged(w, h, oldw, oldh)

        if (!loaded) {
            resetLayout()
            loaded = true
        }

        onChanged?.invoke()
    }

    fun toggle(key: String) {
        flags[key] = !(flags[key] ?: false)
        flags["animate"] = false
        save()
        invalidate()
    }

    fun nextTheme() {
        themeIdx = (themeIdx + 1) % THEMES.size
        save()
        invalidate()
    }

    fun thicker(delta: Float) {
        thickness = when {
            delta > 0f && thickness < 1f -> 1f
            else -> (thickness + delta).coerceIn(0.5f, 11f)
        }

        save()
        invalidate()
    }

    fun pos(key: String): PointF {
        return when (key) {
            "p1" -> PointF(p1.x, p1.y)
            "p2" -> PointF(p2.x, p2.y)
            "marker" -> PointF(marker.x, marker.y)
            "tl" -> PointF(rect.left, rect.top)
            else -> PointF(rect.right, rect.bottom)
        }
    }

    fun touchPos(key: String): PointF {
        val p = pos(key)

        return if (
            key == "p1" ||
            key == "p2" ||
            key == "marker"
        ) {
            PointF(p.x + touchOffX, p.y)
        } else {
            p
        }
    }

    fun setPoint(
        key: String,
        x: Float,
        y: Float,
        final: Boolean
    ) {
        when (key) {
            "p1", "p2" -> {
                if (final) {
                    place(key, x, y)
                } else {
                    val (nx, ny) = Geometry.clamp(
                        Pt(x, y),
                        box()
                    )

                    val target = if (key == "p1") p1 else p2
                    target.set(nx, ny)
                }
            }

            "marker" -> {
                val (nx, ny) = Geometry.clamp(
                    Pt(x, y),
                    box()
                )

                marker.set(nx, ny)
            }

            "tl" -> {
                rect.left = min(
                    x.coerceIn(0f, width.toFloat()),
                    rect.right - 120f * d
                )

                rect.top = min(
                    y.coerceIn(0f, height.toFloat()),
                    rect.bottom - 80f * d
                )

                keepInside()
            }

            "br" -> {
                rect.right = max(
                    x.coerceIn(0f, width.toFloat()),
                    rect.left + 120f * d
                )

                rect.bottom = max(
                    y.coerceIn(0f, height.toFloat()),
                    rect.top + 80f * d
                )

                keepInside()
            }
        }

        invalidate()
    }

    private fun place(which: String, x: Float, y: Float) {
        var (nx, ny) = Geometry.clamp(Pt(x, y), box())

        if (flags["snap"] == true) {
            val (pocket, distance) = Geometry.nearestPocket(
                Pt(nx, ny),
                box()
            )

            if (distance <= snapThresh) {
                nx = pocket.first
                ny = pocket.second
            }
        }

        if (which == "p1") {
            p1.set(nx, ny)
        } else {
            p2.set(nx, ny)
        }
    }

    private fun keepInside() {
        for (p in listOf(p1, p2, marker)) {
            val (x, y) = Geometry.clamp(pt(p), box())
            p.set(x, y)
        }
    }

    private fun line(
        canvas: Canvas,
        ax: Float,
        ay: Float,
        bx: Float,
        by: Float,
        stroke: Float,
        lineColor: Int
    ) {
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = stroke
        paint.color = lineColor

        canvas.drawLine(ax, ay, bx, by, paint)
    }

    private fun ring(
        canvas: Canvas,
        x: Float,
        y: Float,
        radius: Float,
        ringColor: Int,
        stroke: Float = 2f * d
    ) {
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = stroke
        paint.color = ringColor

        canvas.drawCircle(x, y, radius, paint)
    }

    private fun touchCircle(
        canvas: Canvas,
        x: Float,
        y: Float,
        radius: Float
    ) {
        paint.style = Paint.Style.FILL
        paint.color = touchFill

        canvas.drawCircle(x, y, radius, paint)

        ring(
            canvas,
            x,
            y,
            radius,
            Color.WHITE,
            2f * d
        )
    }

    private fun glowDot(
        canvas: Canvas,
        x: Float,
        y: Float
    ) {
        paint.style = Paint.Style.FILL

        paint.color = Color.argb(70, 255, 20, 20)
        canvas.drawCircle(x, y, 5.5f * d, paint)

        paint.color = Color.argb(150, 255, 30, 30)
        canvas.drawCircle(x, y, 3.5f * d, paint)

        paint.color = Color.rgb(255, 40, 40)
        canvas.drawCircle(x, y, 2f * d, paint)

        paint.color = Color.BLACK
        canvas.drawCircle(x, y, 0.9f * d, paint)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val stroke = thickness * d
        val bounds = box()
        val pockets = Geometry.pocketCenters(bounds)

        paint.pathEffect = null

        if (!rectHidden) {
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = 3f * d
            paint.color = Color.WHITE

            canvas.drawRoundRect(
                rect,
                22f * d,
                22f * d,
                paint
            )
        }

        // Lines only: no wide translucent halos.
        if (flags["lines"] == true) {
            for (pocket in pockets) {
                line(
                    canvas,
                    marker.x,
                    marker.y,
                    pocket.first,
                    pocket.second,
                    stroke,
                    color
                )
            }
        }

        val dx = p2.x - p1.x
        val dy = p2.y - p1.y

        val bankEnabled =
            !aimHidden &&
            (
                flags["bank"] == true ||
                flags["double_bank"] == true
            ) &&
            (dx != 0f || dy != 0f)

        if (
            !aimHidden &&
            (flags["single"] == true || bankEnabled)
        ) {
            line(
                canvas,
                p1.x,
                p1.y,
                p2.x,
                p2.y,
                stroke,
                Color.BLACK
            )
        }

        var segments: List<Pair<Pt, Pt>> = emptyList()
        var snapped = false

        if (bankEnabled) {
            val length = hypot(dx, dy)

            val maxBanks = if (
                flags["double_bank"] == true
            ) {
                4
            } else {
                2
            }

            val calculated = Geometry.bankSegments(
                pt(p2),
                Pt(dx / length, dy / length),
                bounds,
                maxBanks
            ).toMutableList()

            if (calculated.isNotEmpty()) {
                val last = calculated.last()

                val (nearest, distance) =
                    Geometry.nearestPocket(
                        last.second,
                        bounds
                    )

                if (distance <= pocketR * 1.4f) {
                    calculated[calculated.lastIndex] =
                        last.first to nearest

                    snapped = true

                    ring(
                        canvas,
                        nearest.first,
                        nearest.second,
                        pocketR + 5.5f * d,
                        rgba(185)
                    )
                }
            }

            segments = calculated

            for ((index, segment) in calculated.withIndex()) {
                line(
                    canvas,
                    segment.first.first,
                    segment.first.second,
                    segment.second.first,
                    segment.second.second,
                    stroke,
                    Color.BLACK
                )

                if (index < calculated.size - 1) {
                    paint.style = Paint.Style.FILL
                    paint.color = Color.argb(200, 0, 0, 0)

                    canvas.drawCircle(
                        segment.second.first,
                        segment.second.second,
                        3.5f * d,
                        paint
                    )
                }
            }
        }

        var info: Triple<Int, Float, Float>? = null

        if (!aimHidden && flags["ghost"] == true) {
            info = drawGhost(canvas, bounds)
        }

        if (!aimHidden) {
            for ((point, label) in listOf(
                p1 to "C",
                p2 to "8"
            )) {
                val touchX = point.x + touchOffX

                touchCircle(
                    canvas,
                    touchX,
                    point.y,
                    pocketR
                )

                text.textSize = 10f * d
                text.isFakeBoldText = true

                canvas.drawText(
                    label,
                    touchX,
                    point.y - pocketR - 4f * d,
                    text
                )

                glowDot(canvas, point.x, point.y)
            }
        }

        if (flags["lines"] == true) {
            touchCircle(
                canvas,
                marker.x + touchOffX,
                marker.y,
                pocketR
            )

            glowDot(canvas, marker.x, marker.y)
        }

        if (!locked && !rectHidden) {
            for ((x, y) in listOf(
                rect.left to rect.top,
                rect.right to rect.bottom
            )) {
                paint.style = Paint.Style.FILL
                paint.color = Color.argb(90, 255, 255, 255)

                canvas.drawCircle(
                    x,
                    y,
                    pocketR * 1.2f,
                    paint
                )

                ring(
                    canvas,
                    x,
                    y,
                    pocketR * 1.2f,
                    Color.WHITE
                )

                paint.style = Paint.Style.FILL
                paint.color = Color.WHITE

                canvas.drawCircle(x, y, 3f * d, paint)
            }
        }

        if (flags["hud"] == true) {
            drawHud(canvas, info, segments, snapped)
        }
    }

    private fun drawGhost(
        canvas: Canvas,
        bounds: Box
    ): Triple<Int, Float, Float>? {
        val cue = pt(p1)
        val obj = pt(p2)

        if (Geometry.dist(cue, obj) < 1f) return null

        // ghostBall places the center at twice this radius.
        // Half the added distance produces a 1dp extra offset.
        val placementRadius =
            ballR + ghostExtraDistance / 2f

        val best = Geometry.bestPocket(
            cue,
            obj,
            bounds,
            placementRadius
        ) ?: return null

        val (index, angle) = best
        val pocket = Geometry.pocketCenters(bounds)[index]

        val ghost = Geometry.ghostBall(
            obj,
            pocket,
            placementRadius
        )

        val ghostStroke = thickness * d

        paint.pathEffect = DashPathEffect(
            floatArrayOf(10f * d, 8f * d),
            0f
        )

        line(
            canvas,
            obj.first,
            obj.second,
            pocket.first,
            pocket.second,
            ghostStroke,
            rgba(220)
        )

        paint.pathEffect = DashPathEffect(
            floatArrayOf(2f * d, 6f * d),
            0f
        )

        line(
            canvas,
            cue.first,
            cue.second,
            ghost.first,
            ghost.second,
            ghostStroke,
            rgba(120)
        )

        paint.pathEffect = null

        paint.style = Paint.Style.FILL
        paint.color = rgba(40)

        canvas.drawCircle(
            ghost.first,
            ghost.second,
            ballR,
            paint
        )

        ring(
            canvas,
            ghost.first,
            ghost.second,
            ballR,
            rgba(255),
            1.8f * d
        )

        ring(
            canvas,
            pocket.first,
            pocket.second,
            pocketR + 5.5f * d,
            rgba(190)
        )

        // No angle label above the ghost circle.
        return Triple(
            index + 1,
            angle,
            Geometry.dist(cue, obj) +
                Geometry.dist(obj, pocket)
        )
    }

    private fun drawHud(
        canvas: Canvas,
        info: Triple<Int, Float, Float>?,
        segments: List<Pair<Pt, Pt>>,
        snapped: Boolean
    ) {
        val parts = mutableListOf(
            if (locked) "Rect locked" else "Rect unlocked"
        )

        if (info != null) {
            parts.add(
                "Cut ${info.second.toInt()}\u00B0 > pocket ${info.first}"
            )

            parts.add(
                Geometry.difficulty(
                    info.second,
                    info.third,
                    rect.width()
                )
            )
        }

        if (segments.isNotEmpty()) {
            parts.add(
                "${max(0, segments.size - 1)} bank(s)"
            )

            if (snapped) {
                parts.add("pocketed")
            }
        }

        val message = parts.joinToString("  |  ")

        text.textSize = 12f * d
        text.isFakeBoldText = false

        val hudWidth = text.measureText(message) + 36f * d
        val left = (width - hudWidth) / 2f

        paint.style = Paint.Style.FILL
        paint.color = Color.argb(215, 15, 17, 23)

        canvas.drawRoundRect(
            left,
            12f * d,
            left + hudWidth,
            40f * d,
            14f * d,
            14f * d,
            paint
        )

        canvas.drawText(
            message,
            width / 2f,
            31f * d,
            text
        )
    }

    fun save() {
        prefs.edit().apply {
            putFloat("l", rect.left)
            putFloat("t", rect.top)
            putFloat("r", rect.right)
            putFloat("b", rect.bottom)

            putFloat("p1x", p1.x)
            putFloat("p1y", p1.y)
            putFloat("p2x", p2.x)
            putFloat("p2y", p2.y)

            putFloat("mx", marker.x)
            putFloat("my", marker.y)

            putFloat("thick", thickness)
            putInt("theme", themeIdx)

            for ((key, value) in flags) {
                putBoolean("f_$key", value)
            }

            apply()
        }
    }

    private fun load() {
        if (!prefs.contains("l")) return

        val left = prefs.getFloat("l", 0f)
        val top = prefs.getFloat("t", 0f)
        val right = prefs.getFloat("r", 0f)
        val bottom = prefs.getFloat("b", 0f)

        if (
            right - left < 120f * d ||
            bottom - top < 80f * d
        ) {
            return
        }

        rect.set(left, top, right, bottom)

        p1.set(
            prefs.getFloat("p1x", 0f),
            prefs.getFloat("p1y", 0f)
        )

        p2.set(
            prefs.getFloat("p2x", 0f),
            prefs.getFloat("p2y", 0f)
        )

        marker.set(
            prefs.getFloat("mx", 0f),
            prefs.getFloat("my", 0f)
        )

        thickness = prefs.getFloat("thick", 3f)
            .coerceIn(0.5f, 11f)

        themeIdx = prefs.getInt("theme", 0)
            .coerceIn(0, THEMES.size - 1)

        for (key in flags.keys.toList()) {
            flags[key] = prefs.getBoolean(
                "f_$key",
                flags[key] ?: false
            )
        }

        flags["animate"] = false

        keepInside()
        loaded = true
    }
}
