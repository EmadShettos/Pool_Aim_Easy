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

    // المعامل الأساسي الناجح عند القوة 1
    private const val BASE_COEFF = 0.982f
    private const val FORCE_STEP = 0.0022f

    fun reboundCoeff(cueForce: Int): Float {
        val f = cueForce.coerceIn(1, 10)
        return (BASE_COEFF - (f - 1) * FORCE_STEP).coerceIn(0.92f, 1.0f)
    }

    // ===================== الوضع الاحترافي: قوة العصا × قوة السحب =====================
    // من الدليل: عند سحب 100% تقطع الكرة البيضاء تقريبًا (طول طاولة):
    // قوة 1-2: 1–1.5 | 3-4: 2–2.5 | 5-6: 3–3.5 | 7-8: 4–4.5 | 9-10: 5+
    // => المسافة القصوى = 0.5 + 0.5 × قوة العصا (بوحدة طول الطاولة)
    // السرعة خطية مع (القوة × نسبة السحب) [قوة 5 @100% = قوة 10 @50%]
    // والمسافة مع الاحتكاك ∝ مربع السرعة.
    // كل ارتطام بالحافة يخصم نسبة ثابتة من السرعة (Cushion Dampening).
    const val CUSHION_SPEED_KEEP = 0.90f

    fun maxTravelLengths(cueForce: Int): Float = 0.5f + 0.5f * cueForce.coerceIn(1, 10)

    /** ميزانية المسافة بالبكسل لسرعة البداية */
    fun travelBudget(cueForce: Int, powerPct: Int, tableW: Float): Float {
        val p = (powerPct.coerceIn(1, 100) / 100f)
        return maxTravelLengths(cueForce) * p * p * tableW
    }

    /**
     * معامل الارتداد حسب السرعة اللحظية لحظة الاصطدام (وليس رقم العصا وحده):
     * سرعة نسبية 0..1 حيث 1 = عصا 10 بسحب 100%.
     * الضربات الهادئة (عصي 1-3 أو سحب منخفض) ترتد بزاوية هندسية طبيعية،
     * والقوية جدًا تُضيّق الزاوية (انضغاط المطاط).
     */
    fun reboundCoeffBySpeed(speedRel: Float): Float {
        val idx = (speedRel.coerceIn(0f, 1f) * 10f)
        return (BASE_COEFF - max(0f, idx - 1f) * FORCE_STEP).coerceIn(0.92f, 1.0f)
    }

    data class ProPath(val segs: List<Pair<Pt, Pt>>, val aimEnd: Pt, val stopped: Boolean, val stopPt: Pt?)

    /**
     * مسار احترافي: يبدأ من الكرة البيضاء start مرورًا بنقطة التصويب aim،
     * ثم الارتدادات، وينقطع عند النقطة التي تقف فيها الكرة.
     */
    fun proPath(start: Pt, aim: Pt, r: Box, maxBanks: Int, cueForce: Int, powerPct: Int): ProPath {
        val tableW = max(1f, r.r - r.l)
        var budget = travelBudget(cueForce, powerPct, tableW)   // E ∝ v²
        val e0 = maxTravelLengths(10) * tableW                    // طاقة عصا 10 @100%
        val first = dist(start, aim)
        if (first >= budget) {
            val u = unit(start, aim)
            val s = Pt(start.first + u.first * budget, start.second + u.second * budget)
            return ProPath(emptyList(), s, true, s)
        }
        budget -= first
        val dx = aim.first - start.first
        val dy = aim.second - start.second
        val len = hypot(dx, dy)
        if (len < 1e-6f) return ProPath(emptyList(), aim, false, null)
        var vx = dx / len
        var vy = dy / len

        val ballR = (r.r - r.l) * 0.0175f
        val bL = r.l + ballR; val bR = r.r - ballR; val bT = r.t + ballR; val bB = r.b - ballR
        var cx = aim.first.coerceIn(bL, bR)
        var cy = aim.second.coerceIn(bT, bB)
        val out = mutableListOf<Pair<Pt, Pt>>()
        val keepE = CUSHION_SPEED_KEEP * CUSHION_SPEED_KEEP

        for (i in 0 until max(0, maxBanks)) {
            val cand = mutableListOf<Pair<Float, Char>>()
            if (vx > 1e-6f) cand.add((bR - cx) / vx to 'x') else if (vx < -1e-6f) cand.add((bL - cx) / vx to 'x')
            if (vy > 1e-6f) cand.add((bB - cy) / vy to 'y') else if (vy < -1e-6f) cand.add((bT - cy) / vy to 'y')
            val valid = cand.filter { it.first > EPS }
            if (valid.isEmpty()) break
            val (t, axis) = valid.minByOrNull { it.first }!!
            if (t >= budget) {
                val s = Pt(cx + vx * budget, cy + vy * budget)
                out.add(Pt(cx, cy) to s)
                return ProPath(out, aim, true, s)
            }
            val hx = cx + vx * t; val hy = cy + vy * t
            out.add(Pt(cx, cy) to Pt(hx, hy))
            budget -= t
            val speedRel = sqrt(max(0f, budget) / e0)
            val coeff = reboundCoeffBySpeed(speedRel)
            if (axis == 'x') { vx = -vx; if (abs(vy) > 1e-4f) vy *= coeff }
            else { vy = -vy; if (abs(vx) > 1e-4f) vx *= coeff }
            val l = hypot(vx, vy); if (l > 1e-6f) { vx /= l; vy /= l }
            budget *= keepE
            cx = hx; cy = hy
            if (budget < ballR * 0.5f) return ProPath(out, aim, true, Pt(cx, cy))
        }
        return ProPath(out, aim, false, null)
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

        // ضبط نصف القطر وإزاحة وسادة المطاط الدقيقة للعبة
        val ballR = (r.r - r.l) * 0.0175f
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
                if (abs(vy) > 1e-4f) {
                    vy *= coeff
                }
            } else {
                vy = -vy
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

    /**
     * حساب مسار ارتداد الكرة البيضاء بعد الاصطدام (Tangent Line) ممتداً حتى وسائد الطاولة.
     * نقطة البداية تقع في مركز الكرة البيضاء لحظة ملامستها للكرة المستهدفة،
     * أي بإزاحة مسافة قطر كرة كامل (2 * ballR) خلف مركز الكرة المستهدفة obj في اتجاه خط التصويب المعاكس:
     * P_contact = obj - 2 * ballR * u_target
     */
    fun cueTangentSegments(cue: Pt, obj: Pt, dir: Pt, r: Box, maxBanks: Int = 2, cueForce: Int = 1): List<Pair<Pt, Pt>> {
        val dx = dir.first
        val dy = dir.second
        val len = hypot(dx, dy)
        if (len < 1e-6f) return emptyList()

        val nx = dx / len
        val ny = dy / len

        val ballR = (r.r - r.l) * 0.0175f

        // مركز الكرة البيضاء لحظة الاصطدام (2 * ballR خلف الكرة المستهدفة)
        val contactX = obj.first - nx * 2f * ballR
        val contactY = obj.second - ny * 2f * ballR
        val contactPt = Pt(contactX, contactY)

        // تحديد زاوية الارتداد 90 درجة بالاعتماد على جهة قدوم الكرة البيضاء
        val approachX = obj.first - cue.first
        val approachY = obj.second - cue.second
        val cross = approachX * ny - approachY * nx

        val (tx, ty) = if (cross > 0f) Pt(-ny, nx) else Pt(ny, -nx)

        return bankSegments(contactPt, Pt(tx, ty), r, maxBanks, cueForce)
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
