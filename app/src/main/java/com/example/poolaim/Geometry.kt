package com.example.poolaim

import kotlin.math.*

typealias Pt = Pair<Float, Float>
data class Box(val l: Float, val t: Float, val r: Float, val b: Float)

val THEMES: List<Pair<String, Int>> = listOf(
    "Classic White" to 0xFFFFFFFF.toInt(),
    "Neon Green" to 0xFF39FF14.toInt(),
    "Cyber Cyan" to 0xFF00E5FF.toInt(),
    "Hot Pink" to 0xFFFF40A0.toInt(),
    "Solar Amber" to 0xFFFFC107.toInt(),
    "Crimson" to 0xFFFF5252.toInt(),
    "Violet" to 0xFFB388FF.toInt(),
)

object Geometry {
    private const val EPS = 1e-3f

    // المعامل الأساسي للقوة 1 (عصا بلياردو المبتدئين)
    private const val BASE_COEFF = 0.982f
    private const val FORCE_STEP = 0.0022f

    fun reboundCoeff(cueForce: Int): Float {
        val f = cueForce.coerceIn(1, 10)
        return (BASE_COEFF - (f - 1) * FORCE_STEP).coerceIn(0.92f, 1.0f)
    }

    fun dist(a: Pt, b: Pt) = hypot(a.first - b.first, a.second - b.second)

    fun pocketCenters(r: Box): List<Pt> {
        val m = (r.l + r.r) / 2
        return listOf(Pt(r.l, r.t), Pt(m, r.t), Pt(r.r, r.t), Pt(r.l, r.b), Pt(m, r.b), Pt(r.r, r.b))
    }

    fun nearestPocket(p: Pt, r: Box): Pair<Pt, Float> {
        val c = pocketCenters(r).minByOrNull { dist(p, it) }!!
        return c to dist(p, c)
    }

    fun clamp(p: Pt, r: Box) = Pt(min(max(p.first, r.l), r.r), min(max(p.second, r.t), r.b))

    fun bankSegments(start: Pt, dir: Pt, r: Box, maxBanks: Int = 4, cueForce: Int = 1): List<Pair<Pt, Pt>> {
        var vx = dir.first
        var vy = dir.second
        if (!vx.isFinite() || !vy.isFinite() || (vx == 0f && vy == 0f)) return emptyList()

        val initialLen = hypot(vx, vy)
        vx /= initialLen
        vy /= initialLen

        // ضبط نصف قطر الكرة الفعلي المتطابق مع وسائد اللعبة
        val ballR = (r.r - r.l) * 0.0172f
        val boundL = r.l + ballR
        val boundR = r.r - ballR
        val boundT = r.t + ballR
        val boundB = r.b - ballR

        var cx = start.first.coerceIn(boundL, boundR)
        var cy = start.second.coerceIn(boundT, boundB)
        val out = mutableListOf<Pair<Pt, Pt>>()

        val coeff = reboundCoeff(cueForce)

        for (bankIndex in 0 until max(0, maxBanks)) {
            val cand = mutableListOf<Pair<Float, Char>>()
            if (vx > 1e-6f) cand.add((boundR - cx) / vx to 'x')
            else if (vx < -1e-6f) cand.add((boundL - cx) / vx to 'x')

            if (vy > 1e-6f) cand.add((boundB - cy) / vy to 'y')
            else if (vy < -1e-6f) cand.add((boundT - cy) / vy to 'y')

            val valid = cand.filter { it.first > EPS }
            if (valid.isEmpty()) break

            val (t, axis) = valid.minByOrNull { it.first }!!
            val hitX = cx + vx * t
            val hitY = cy + vy * t
            out.add(Pt(cx, cy) to Pt(hitX, hitY))

            if (axis == 'x') {
                vx = -vx
                // إذا كانت الضربة شبه عمودية على الحافة الرأسية، ترتد عمودياً بنسبة 100%
                if (abs(vy) > 1e-4f) {
                    vy *= coeff
                }
            } else {
                vy = -vy
                // إذا كانت الضربة شبه عمودية على الحافة الأفقية، ترتد عمودياً بنسبة 100%
                if (abs(vx) > 1e-4f) {
                    vx *= coeff
                }
            }

            val len = hypot(vx, vy)
            if (len > 1e-6f) {
                vx /= len
                vy /= len
            }

            cx = hitX
            cy = hitY
        }
        return out
    }

    fun unit(a: Pt, b: Pt): Pt {
        val d = dist(a, b)
        return if (d < 1e-9f) Pt(0f, 0f) else Pt((b.first - a.first) / d, (b.second - a.second) / d)
    }

    fun ghostBall(obj: Pt, pocket: Pt, ballR: Float): Pt {
        val u = unit(obj, pocket)
        return Pt(obj.first - u.first * 2 * ballR, obj.second - u.second * 2 * ballR)
    }

    fun angleBetween(a: Pt, b: Pt): Float {
        val na = hypot(a.first, a.second)
        val nb = hypot(b.first, b.second)
        if (na < 1e-9f || nb < 1e-9f) return 0f
        val c = ((a.first * b.first + a.second * b.second) / (na * nb)).coerceIn(-1f, 1f)
        return Math.toDegrees(acos(c.toDouble())).toFloat()
    }

    fun cutAngle(cue: Pt, obj: Pt, pocket: Pt) = angleBetween(
        Pt(obj.first - cue.first, obj.second - cue.second),
        Pt(pocket.first - obj.first, pocket.second - obj.second)
    )

    fun bestPocket(cue: Pt, obj: Pt, r: Box, ballR: Float, maxCut: Float = 80f): Pair<Int, Float>? {
        var best: Pair<Int, Float>? = null
        pocketCenters(r).forEachIndexed { i, p ->
            if (dist(obj, p) <= 2 * ballR) return@forEachIndexed
            val a = cutAngle(cue, obj, p)
            if (a > maxCut) return@forEachIndexed
            if (best == null || a < best!!.second) best = i to a
        }
        return best
    }

    fun difficulty(angle: Float, length: Float, tableW: Float): String {
        val s = angle / 90f * 0.7f + min(length / max(tableW, 1f), 1.5f) / 1.5f * 0.3f
        return when {
            s < 0.22f -> "Easy"
            s < 0.45f -> "Medium"
            s < 0.7f -> "Hard"
            else -> "Very hard"
        }
    }
}
