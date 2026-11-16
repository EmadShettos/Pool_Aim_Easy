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

    fun bankSegments(start: Pt, dir: Pt, r: Box, maxBanks: Int = 2): List<Pair<Pt, Pt>> {
        var vx = dir.first
        var vy = dir.second
        if (!vx.isFinite() || !vy.isFinite() || (vx == 0f && vy == 0f)) return emptyList()
        var cx = start.first
        var cy = start.second
        val out = mutableListOf<Pair<Pt, Pt>>()
        repeat(max(0, maxBanks)) {
            val cand = mutableListOf<Pair<Float, Char>>()
            if (vx > 0) cand.add((r.r - cx) / vx to 'x') else if (vx < 0) cand.add((r.l - cx) / vx to 'x')
            if (vy > 0) cand.add((r.b - cy) / vy to 'y') else if (vy < 0) cand.add((r.t - cy) / vy to 'y')
            val valid = cand.filter { it.first > EPS }
            if (valid.isEmpty()) return out
            val (t, axis) = valid.minByOrNull { it.first }!!
            val hit = Pt(cx + vx * t, cy + vy * t)
            out.add(Pt(cx, cy) to hit)
            if (axis == 'x') vx = -vx else vy = -vy
            cx = hit.first
            cy = hit.second
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
