package com.example.poolaim

import kotlin.math.abs
import kotlin.math.atan
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * محرك فيزياء الوضع الاحترافي.
 *
 * يحاكي الضربة بنفس وحدات وثوابت طاولة اللعبة (طاولة 254 × 127 وحدة، كرة نصف قطرها 3.800475)
 * خطوة بخطوة (0.005 ثانية): انزلاق ثم دحرجة الكرة، تصادم الكرة البيضاء بالكرة المستهدفة،
 * الارتداد عن الوسائد (مع فقد السرعة وأثر الدوران)، فكوك الجيوب، ودخول الجيب.
 *
 * المدخلات: مركز الكرة البيضاء، مركز الكرة المستهدفة، وسرعة انطلاق الكرة البيضاء.
 * المخرجات: مسار الكرة المستهدفة حتى تقف أو تدخل الجيب.
 *
 * ملف Kotlin خالص (بدون أي استيراد من Android) ولا يمس الوضع العادي.
 */
object PoolPhysics {
    const val BALL_R = 3.800475
    const val TABLE_W = 254.0
    const val TABLE_H = 127.0

    private const val R2 = BALL_R * BALL_R
    private const val BOUND_L = -TABLE_W / 2.0 + BALL_R
    private const val BOUND_R = TABLE_W / 2.0 - BALL_R
    private const val BOUND_T = -TABLE_H / 2.0 + BALL_R
    private const val BOUND_B = TABLE_H / 2.0 - BALL_R

    private const val DT = 0.005
    private const val MIN_TIME = 1e-11
    private const val POCKET_R2 = 64.0

    // مراكز الجيوب الستة (x, y)
    private val POCKETS = doubleArrayOf(
        -130.8, -67.3, 0.0, -72.0, 130.8, -67.3,
        130.8, 67.3, 0.0, 72.0, -130.8, 67.3
    )

    // مضلع حواف الطاولة (الوسائد وفكوك الجيوب) — 46 نقطة
    private val SHAPE = doubleArrayOf(
        -127.0, 53.5, -136.9, 64.1, -138.2, 69.2, -136.7, 73.2, -132.7, 74.7, -127.6, 73.4,
        -117.0, 63.5, -7.8, 63.5, -6.1, 68.6, -5.7, 72.7, -3.7, 75.4, 0.0, 76.7,
        3.7, 75.4, 5.7, 72.7, 6.1, 68.6, 7.8, 63.5, 117.0, 63.5, 127.6, 73.4,
        132.7, 74.7, 136.7, 73.2, 138.2, 69.2, 136.9, 64.1, 127.0, 53.5, 127.0, -53.5,
        136.9, -64.1, 138.2, -69.2, 136.7, -73.2, 132.7, -74.7, 127.6, -73.4, 117.0, -63.5,
        7.8, -63.5, 6.1, -68.6, 5.7, -72.7, 3.7, -75.4, 0.0, -76.7, -3.7, -75.4,
        -5.7, -72.7, -6.1, -68.6, -7.8, -63.5, -117.0, -63.5, -127.6, -73.4, -132.7, -74.7,
        -136.7, -73.2, -138.2, -69.2, -136.9, -64.1, -127.0, -53.5
    )
    private const val SHAPE_N = 46

    // أقصى سرعة انطلاق لكل مستوى «قوة العصا» (المستوى 1 = عصا المبتدئين)
    private val CUE_POWER = doubleArrayOf(
        666.0, 682.0, 699.0, 715.0, 731.0, 747.0, 764.0, 780.0, 796.0, 813.0
    )

    // نسبة سحب شريط القوة الفعلية المقابلة لعلامات 25/50/75/100% (مقاسة من تسجيلات لعب حقيقية)
    private val PULL_PCT = doubleArrayOf(0.0, 25.0, 50.0, 75.0, 100.0)
    private val PULL_FRAC = doubleArrayOf(0.0, 0.296, 0.544, 0.783, 1.0)

    fun pullFraction(powerPct: Int): Double {
        val p = powerPct.coerceIn(0, 100).toDouble()
        for (i in 1 until PULL_PCT.size) {
            if (p <= PULL_PCT[i]) {
                val k = (p - PULL_PCT[i - 1]) / (PULL_PCT[i] - PULL_PCT[i - 1])
                return PULL_FRAC[i - 1] + (PULL_FRAC[i] - PULL_FRAC[i - 1]) * k
            }
        }
        return 1.0
    }

    /** سرعة انطلاق الكرة البيضاء (وحدة/ثانية) حسب قوة العصا (1..10) وقوة السحب (%) */
    fun shotSpeed(cueLevel: Int, powerPct: Int): Double {
        val maxPower = CUE_POWER[cueLevel.coerceIn(1, CUE_POWER.size) - 1]
        return (1.0 - sqrt(1.0 - pullFraction(powerPct))) * maxPower
    }

    // هامش صغير جدًا (0.001 وحدة) يُبقي الكرة الملاصقة للوسادة داخل حدود اللعب،
    // لأن الكرة الملتصقة بالوسادة تمامًا لا يُكتشف ارتدادها عنها فتخرج من الطاولة.
    private const val EDGE_EPS = 1e-3

    /** حصر مركز الكرة داخل حدود اللعب */
    fun clampX(x: Double) = x.coerceIn(BOUND_L + EDGE_EPS, BOUND_R - EDGE_EPS)
    fun clampY(y: Double) = y.coerceIn(BOUND_T + EDGE_EPS, BOUND_B - EDGE_EPS)

    /** هل النقطة داخل مضلع الطاولة (الوسائد وفكوك الجيوب) */
    private fun insideShape(x: Double, y: Double): Boolean {
        var inside = false
        var j = SHAPE_N - 1
        for (i in 0 until SHAPE_N) {
            val xi = SHAPE[i * 2]
            val yi = SHAPE[i * 2 + 1]
            val xj = SHAPE[j * 2]
            val yj = SHAPE[j * 2 + 1]
            if ((yi > y) != (yj > y) && x < (xj - xi) * (y - yi) / (yj - yi) + xi) inside = !inside
            j = i
        }
        return inside
    }

    /**
     * نتيجة المحاكاة.
     * legs: مسار الكرة المستهدفة؛ كل عنصر ضلع متصل (x0,y0,x1,y1,...) ينتهي عند ارتداد أو عند التوقف.
     * banks: عدد ارتدادات الكرة المستهدفة عن الوسائد.
     * hit: هل وصلت الكرة البيضاء إلى الكرة المستهدفة.
     * pocketed: هل دخلت الكرة المستهدفة الجيب.
     * endX/endY: موضع استقرار الكرة المستهدفة (أو موضع توقف البيضاء إذا لم تصل).
     */
    class Shot(
        val legs: List<DoubleArray>,
        val banks: Int,
        val hit: Boolean,
        val pocketed: Boolean,
        val endX: Double,
        val endY: Double
    )

    private class Ball(var x: Double, var y: Double) {
        var vx = 0.0
        var vy = 0.0
        var sx = 0.0
        var sy = 0.0
        var sz = 0.0
        var state = 1
        var on = true
        var pocket = -1

        fun moving() = vx != 0.0 || vy != 0.0 || sx != 0.0 || sy != 0.0 || sz != 0.0

        fun willCollideTable(t: Double): Boolean {
            val px = x + vx * t
            val py = y + vy * t
            val lx: Double
            val rx: Double
            val ty: Double
            val by: Double
            if (vx > 0.0) { lx = x; rx = px } else { lx = px; rx = x }
            if (vy > 0.0) { ty = y; by = py } else { ty = py; by = y }
            return lx < BOUND_L || rx > BOUND_R || ty < BOUND_T || by > BOUND_B
        }

        /** زمن الاصطدام بضلع من المضلع، أو NaN */
        fun lineColl(t: Double, ax: Double, ay: Double, bx: Double, by: Double): Double {
            if (vx == 0.0 && vy == 0.0) return Double.NaN
            val dx = bx - ax
            val dy = by - ay
            val v17 = dy * vx - dx * vy
            if (v17 == 0.0) return Double.NaN
            val inv = 1.0 / sqrt(dx * dx + dy * dy)
            val v21 = inv * BALL_R
            val v22 = x - ax - dy * v21
            val v23 = y - ay + dx * v21
            val v24 = (v22 * -vy - v23 * -vx) / v17
            if (v24 <= 0.0 || v24 >= 1.0) return Double.NaN
            val tm = (dx * v23 - dy * v22) / v17
            if (tm <= 0.0 || tm - 1e-11 > t) return Double.NaN
            if (vx * (dy * inv) + vy * -(dx * inv) > 0.0) return Double.NaN
            return tm
        }

        /** زمن الاصطدام برأس من رؤوس المضلع، أو NaN */
        fun pointColl(t: Double, px: Double, py: Double): Double {
            val dx = px - x
            val dy = py - y
            val v16 = -(vx * dx * 2.0) - (vy * dy * 2.0)
            if (v16 >= 0.0) return Double.NaN
            val vs = vx * vx + vy * vy
            val ds = dx * dx + dy * dy
            val us = v16 * v16
            if (ds - us / (vs * 4.0) >= R2) return Double.NaN
            val v22 = (-v16 - sqrt(us - vs * 4.0 * (ds - R2))) / (vs * 2.0)
            if (v22 < 0.0) return Double.NaN
            if (v22 - MIN_TIME > t) return Double.NaN
            return v22
        }

        /** زمن الاصطدام بكرة أخرى، أو NaN */
        fun ballColl(t: Double, o: Ball): Double {
            val rx = o.x - x
            val ry = o.y - y
            val dvx = o.vx - vx
            val dvy = o.vy - vy
            val v24 = (rx * dvx + ry * dvy) * 2.0
            if (v24 >= 0.0) return Double.NaN
            val dv2 = dvx * dvx + dvy * dvy
            val v27 = (rx * rx + ry * ry - R2 * 4) * (dv2 * 4.0)
            val sq = v24 * v24
            if (sq < v27) return Double.NaN
            val v28 = (-v24 - sqrt(sq - v27)) / (dv2 * 2.0)
            if (v28 < 0.0) return Double.NaN
            if (v28 - MIN_TIME > t) return Double.NaN
            return v28
        }

        /** احتكاك القماش لخطوة زمنية واحدة: انزلاق ثم دحرجة */
        fun calcVelocity() {
            if (!moving()) return
            val v15 = BALL_R * sx - vy
            val v16 = -vx - sy * BALL_R
            val v17 = sqrt(v16 * v16 + v15 * v15)
            val v18 = v17 * 0.00145772594752187
            if (v18 > MIN_TIME) {
                val v20 = if (v18 < DT) v18 else DT
                val v21 = 196.0 * v20 / v17
                val v22 = v16 * v21
                val v23 = v15 * v21
                vx += v22
                vy += v23
                sx -= v23 * 0.6578125102783204
                sy += v22 * 0.6578125102783204
            }
            if (v18 < DT) {
                val v24 = vx
                val v25 = vy
                val v27 = (DT - v18) * 10.878
                val sp = sqrt(v25 * v25 + v24 * v24)
                var v28 = if (sp > 0.0) 1.0 - v27 / sp else 0.0
                if (v28 < 0.0) v28 = 0.0
                vx = v24 * v28
                vy = v25 * v28
                sx = v25 * v28 / BALL_R
                sy = -(v24 * v28) / BALL_R
            }
            val v29 = 9.8 * DT
            sz = if (sz > 0.0) maxOf(sz - v29, 0.0) else minOf(sz + v29, 0.0)
        }

        /** الارتداد عن الوسادة: فقد السرعة العمودية وأثر الدوران */
        fun postCollision(angle: Double) {
            val c = cos(angle)
            val sn = sin(angle)
            val vX = c * vx - sn * vy
            val vY = sn * vx + c * vy
            val spinFactor = vX - BALL_R * sz
            val velocityFactor = abs(spinFactor) / 2.5
            val sdir = if (spinFactor > 0.0) 1.0 else -1.0
            var msf = 0.4 * abs(vY)
            if (velocityFactor < msf) msf = velocityFactor
            val spinChange = sdir * msf
            val nvX = vX - spinChange / 2.5
            val nvY = -0.804 * vY
            vx = sn * nvY + c * nvX
            vy = c * nvY - nvX * sn
            val nsX = sn * sx + c * sy
            val nsY = c * sx - sn * sy - vY * 0.1420875022201172
            val nsZ = sz + spinChange * 0.6578125102783204
            sx = sn * nsX + c * nsY
            sy = c * nsX - nsY * sn
            sz = nsZ
        }
    }

    private fun calcAngle(dx: Double, dy: Double): Double {
        if (dx == 0.0) return if (dy >= 0.0) Math.PI / 2 else Math.PI * 1.5
        var a = atan(dy / dx)
        if (dx < 0.0) a += Math.PI
        return a
    }

    /** مسجّل نقاط مسار الكرة المستهدفة */
    private class Recorder {
        val legs = ArrayList<DoubleArray>()
        private var buf = DoubleArray(512)
        private var n = 0

        fun add(x: Double, y: Double, force: Boolean) {
            if (n >= 2) {
                val dx = x - buf[n - 2]
                val dy = y - buf[n - 1]
                val d2 = dx * dx + dy * dy
                if (d2 < 1e-12) return
                if (!force && d2 < 0.0625) return
            }
            if (n + 2 > buf.size) buf = buf.copyOf(buf.size * 2)
            buf[n] = x
            buf[n + 1] = y
            n += 2
        }

        fun close() {
            if (n >= 4) legs.add(simplify(buf, n / 2, 0.04))
            n = 0
        }
    }

    /** تبسيط الخط (Douglas–Peucker) مع الحفاظ على شكل الانحناء */
    private fun simplify(pts: DoubleArray, count: Int, eps: Double): DoubleArray {
        if (count <= 2) return pts.copyOf(count * 2)
        val keep = BooleanArray(count)
        keep[0] = true
        keep[count - 1] = true
        val stack = IntArray(count * 2 + 4)
        var sp = 0
        stack[sp++] = 0
        stack[sp++] = count - 1
        while (sp > 0) {
            val b = stack[--sp]
            val a = stack[--sp]
            if (b - a < 2) continue
            val ax = pts[a * 2]
            val ay = pts[a * 2 + 1]
            val bx = pts[b * 2]
            val by = pts[b * 2 + 1]
            val dx = bx - ax
            val dy = by - ay
            val len = sqrt(dx * dx + dy * dy)
            var best = -1
            var bestD = eps
            for (i in a + 1 until b) {
                val px = pts[i * 2] - ax
                val py = pts[i * 2 + 1] - ay
                val dd = if (len < 1e-12) sqrt(px * px + py * py) else abs(dx * py - dy * px) / len
                if (dd > bestD) { bestD = dd; best = i }
            }
            if (best >= 0) {
                keep[best] = true
                stack[sp++] = a
                stack[sp++] = best
                stack[sp++] = best
                stack[sp++] = b
            }
        }
        var m = 0
        for (i in 0 until count) if (keep[i]) m++
        val out = DoubleArray(m * 2)
        var k = 0
        for (i in 0 until count) if (keep[i]) {
            out[k++] = pts[i * 2]
            out[k++] = pts[i * 2 + 1]
        }
        return out
    }

    /**
     * محاكاة ضربة مستقيمة: الكرة البيضاء عند (cueX, cueY) تنطلق بسرعة v0 نحو مركز الكرة المستهدفة (tgtX, tgtY).
     * كل الإحداثيات بوحدات الطاولة ومركزها (0,0).
     */
    fun simulate(
        cueX: Double, cueY: Double,
        tgtX: Double, tgtY: Double,
        v0: Double,
        maxTime: Double = 60.0
    ): Shot {
        val ddx = tgtX - cueX
        val ddy = tgtY - cueY
        val dist = sqrt(ddx * ddx + ddy * ddy)
        if (dist < 1e-9 || !(v0 > 0.0)) return Shot(emptyList(), 0, false, false, cueX, cueY)
        val ux = ddx / dist
        val uy = ddy / dist

        val tgt = Ball(tgtX, tgtY)
        val cue = Ball(cueX, cueY)
        val balls: Array<Ball>
        val rec = Recorder()
        var hit = false
        if (dist <= 2.0 * BALL_R + 1e-6) {
            // الكرتان متلامستان: تنتقل السرعة كاملة إلى الكرة المستهدفة مباشرة
            tgt.vx = v0 * ux
            tgt.vy = v0 * uy
            balls = arrayOf(tgt)
            hit = true
            rec.add(tgt.x, tgt.y, true)
        } else {
            cue.vx = v0 * ux
            cue.vy = v0 * uy
            balls = arrayOf(cue, tgt)
        }

        var banks = 0
        var pocketed = false
        var t = 0.0
        val n = balls.size
        // آخر موضع سليم للكرة المستهدفة داخل الطاولة
        var safeX = tgt.x
        var safeY = tgt.y

        main@ while (true) {
            var time = DT
            do {
                var time2 = time
                var kind = 0            // 0 لا شيء | 1 كرة-كرة | 2 ضلع | 3 رأس
                var cA: Ball? = null
                var cB: Ball? = null
                var cAngle = 0.0
                var cPx = 0.0
                var cPy = 0.0

                for (i in 0 until n) {
                    val b = balls[i]
                    if (!b.on) continue
                    if (b.state == 1) {
                        for (j in i + 1 until n) {
                            val o = balls[j]
                            if (o.state == 1) {
                                val r = b.ballColl(time2, o)
                                if (!r.isNaN()) { time2 = r; kind = 1; cA = b; cB = o }
                            }
                        }
                    }
                    if (b.willCollideTable(time2)) {
                        if (b.state == 1) {
                            for (p in 0 until 6) {
                                val px = POCKETS[p * 2] - b.x
                                val py = POCKETS[p * 2 + 1] - b.y
                                val d2 = px * px + py * py
                                if (d2 < POCKET_R2) {
                                    val ut = time2 * 120.0
                                    b.vx += px * ut
                                    b.vy += py * ut
                                    if (d2 < R2) { b.state = 2; b.pocket = p }
                                }
                            }
                        }
                        for (k in 0 until SHAPE_N) {
                            val k2 = (k + 1) % SHAPE_N
                            val ax = SHAPE[k * 2]
                            val ay = SHAPE[k * 2 + 1]
                            val bx = SHAPE[k2 * 2]
                            val by = SHAPE[k2 * 2 + 1]
                            var r = b.lineColl(time2, ax, ay, bx, by)
                            if (!r.isNaN()) {
                                time2 = r; kind = 2; cA = b
                                cAngle = -calcAngle(bx - ax, by - ay)
                            } else {
                                r = b.pointColl(time2, ax, ay)
                                if (!r.isNaN()) { time2 = r; kind = 3; cA = b; cPx = ax; cPy = ay }
                            }
                        }
                    }
                    if (b.state == 2) {
                        b.state = 3
                        b.on = false
                        b.vx = 0.0; b.vy = 0.0
                        b.sx = 0.0; b.sy = 0.0; b.sz = 0.0
                    }
                }

                for (b in balls) {
                    if (b.on && b.moving() && (b.vx != 0.0 || b.vy != 0.0)) {
                        b.x += b.vx * time2
                        b.y += b.vy * time2
                    }
                }

                if (!tgt.on) {
                    // دخلت الكرة المستهدفة الجيب
                    pocketed = true
                    if (hit) {
                        rec.add(tgt.x, tgt.y, true)
                        if (tgt.pocket >= 0) rec.add(POCKETS[tgt.pocket * 2], POCKETS[tgt.pocket * 2 + 1], true)
                    }
                    break@main
                }

                when (kind) {
                    1 -> {
                        val a = cA!!
                        val o = cB!!
                        val rx = a.x - o.x
                        val ry = a.y - o.y
                        val inv = 1.0 / sqrt(rx * rx + ry * ry)
                        val nx = rx * inv
                        val ny = ry * inv
                        val ca = a.vx * nx + a.vy * ny
                        val cb = o.vx * nx + o.vy * ny
                        val vax = nx * ca
                        val vay = ny * ca
                        val vbx = nx * cb
                        val vby = ny * cb
                        a.vx = vbx - (vax - a.vx)
                        a.vy = vby - (vay - a.vy)
                        o.vx = vax - (vbx - o.vx)
                        o.vy = vay - (vby - o.vy)
                        // بداية مسار الكرة المستهدفة، أو تغيّر اتجاهها عند اصطدام ثانٍ
                        rec.add(tgt.x, tgt.y, true)
                        if (hit) { rec.close(); rec.add(tgt.x, tgt.y, true) }
                        hit = true
                    }
                    2 -> {
                        val b = cA!!
                        b.postCollision(cAngle)
                        if (b === tgt && hit) {
                            rec.add(tgt.x, tgt.y, true); rec.close(); rec.add(tgt.x, tgt.y, true)
                            banks++
                        }
                    }
                    3 -> {
                        val b = cA!!
                        val dx = cPy - b.y
                        val dy = -(cPx - b.x)
                        b.postCollision(-calcAngle(dx, dy))
                        if (b === tgt && hit) {
                            rec.add(tgt.x, tgt.y, true); rec.close(); rec.add(tgt.x, tgt.y, true)
                            banks++
                        }
                    }
                }
                time -= time2
            } while (time > MIN_TIME)

            t += DT
            var any = false
            for (b in balls) {
                if (b.on) {
                    b.calcVelocity()
                    if (b.moving()) any = true
                }
            }
            if ((tgt.x < BOUND_L || tgt.x > BOUND_R || tgt.y < BOUND_T || tgt.y > BOUND_B) &&
                !insideShape(tgt.x, tgt.y)
            ) {
                // حماية: خرجت الكرة من مضلع الطاولة (حالة عددية شاذة) — يُنهى المسار عند آخر موضع سليم
                tgt.x = safeX
                tgt.y = safeY
                break
            }
            safeX = tgt.x
            safeY = tgt.y
            if (hit) rec.add(tgt.x, tgt.y, false)
            if (!any || t > maxTime) break
        }

        if (!pocketed && hit) rec.add(tgt.x, tgt.y, true)
        rec.close()
        return if (hit) Shot(rec.legs, banks, true, pocketed, tgt.x, tgt.y)
        else Shot(emptyList(), 0, false, false, cue.x, cue.y)
    }
}
