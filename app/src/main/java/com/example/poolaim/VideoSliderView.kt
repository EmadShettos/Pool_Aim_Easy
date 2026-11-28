package com.example.poolaim

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.SurfaceTexture
import android.graphics.drawable.GradientDrawable
import android.media.MediaPlayer
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.Surface
import android.view.TextureView
import android.view.VelocityTracker
import android.view.View
import android.view.ViewConfiguration
import android.widget.FrameLayout
import android.widget.ImageView
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * لوحة عرض الفيديوهات والصور.
 * تقرأ تلقائياً كل ملفات الفيديو (mp4...) والصور (png/jpg/webp...) الموجودة في مجلد res/raw،
 * وعدد نقاط المؤشر = عدد الملفات. أضف أو احذف ملفاً من المجلد وسيتغير كل شيء تلقائياً.
 * الترتيب بحسب الاسم والرقم: v1, v2, ... v10, v11
 * كل فيديو يتمدد ليملأ اللوحة بالكامل (مثل الفيديوهات القديمة).
 */
class VideoSliderView(ctx: Context) : FrameLayout(ctx) {

    private class Item(val resId: Int, val isVideo: Boolean)

    private val d = resources.displayMetrics.density
    private val gold = 0xFFFFD21F.toInt()
    private val ui = Handler(Looper.getMainLooper())
    private val slop = ViewConfiguration.get(ctx).scaledTouchSlop

    private val items: List<Item> = loadItems()

    private var currentIndex = 0
    private var started = false
    private var paused = false
    private var mediaPlayer: MediaPlayer? = null
    private var prepared = false
    private var surface: Surface? = null
    private var surfaceReady = false
    private var failStreak = 0
    private var loadToken = 0
    private var animGen = 0

    private val host = FrameLayout(ctx)
    private val textureView = TextureView(ctx)
    private val imageView = ImageView(ctx)
    private val indicator = IndicatorView(ctx)

    private val imageAdvance = Runnable { go(1) }

    private var downX = 0f
    private var downY = 0f
    private var dragging = false
    private var tracker: VelocityTracker? = null

    private fun dp(v: Int) = (v * d).toInt()

    init {
        background = GradientDrawable().apply {
            setColor(0xFF05301B.toInt())
            cornerRadius = 18f * d
        }
        clipToOutline = true
        foreground = GradientDrawable().apply {
            setColor(Color.TRANSPARENT)
            setStroke(dp(3), gold)
            cornerRadius = 18f * d
        }

        textureView.surfaceTextureListener = object : TextureView.SurfaceTextureListener {
            override fun onSurfaceTextureAvailable(st: SurfaceTexture, w: Int, h: Int) {
                surface = Surface(st)
                surfaceReady = true
                if (started && !paused && mediaPlayer == null && items.getOrNull(currentIndex)?.isVideo == true) {
                    startVideo(currentIndex)
                }
            }

            override fun onSurfaceTextureSizeChanged(st: SurfaceTexture, w: Int, h: Int) {}

            override fun onSurfaceTextureDestroyed(st: SurfaceTexture): Boolean {
                surfaceReady = false
                releasePlayer()
                surface?.release()
                surface = null
                return true
            }

            override fun onSurfaceTextureUpdated(st: SurfaceTexture) {}
        }

        imageView.scaleType = ImageView.ScaleType.CENTER_CROP
        imageView.visibility = View.GONE

        host.addView(textureView, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        host.addView(imageView, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        addView(host, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))

        indicator.visibility = if (items.size > 1) View.VISIBLE else View.GONE
        indicator.setState(0, items.size)
        addView(indicator, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT).apply {
            gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
            bottomMargin = dp(6)
        })
    }

    // ---------- اكتشاف الملفات تلقائياً من res/raw ----------

    private fun loadItems(): List<Item> {
        val found = ArrayList<Pair<String, Item>>()
        val tv = TypedValue()
        for (f in R.raw::class.java.fields) {
            val id = try { f.getInt(null) } catch (e: Exception) { continue }
            val ext = try {
                resources.getValue(id, tv, true)
                tv.string?.toString()?.substringAfterLast('.', "")?.lowercase() ?: ""
            } catch (e: Exception) { "" }
            val video = ext in VIDEO_EXT
            val image = ext in IMAGE_EXT
            if (video || image) found.add(f.name to Item(id, video))
        }
        found.sortWith(
            compareBy<Pair<String, Item>>({ prefixOf(it.first) }, { numberOf(it.first) }, { it.first })
        )
        return found.map { it.second }
    }

    private fun prefixOf(name: String) = name.trimEnd { it.isDigit() }

    private fun numberOf(name: String): Int =
        name.substring(prefixOf(name).length).toIntOrNull() ?: Int.MAX_VALUE

    // ---------- القياس ----------

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val w = MeasureSpec.getSize(widthMeasureSpec)
        val h = if (items.isEmpty()) 0 else (w * ASPECT).toInt()
        super.onMeasure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(h, MeasureSpec.EXACTLY))
    }

    // ---------- دورة الحياة ----------

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        if (!started && items.isNotEmpty()) {
            started = true
            showItem(currentIndex)
        }
    }

    override fun onDetachedFromWindow() {
        cleanup()
        started = false
        super.onDetachedFromWindow()
    }

    fun pause() {
        paused = true
        ui.removeCallbacks(imageAdvance)
        val mp = mediaPlayer
        if (mp != null && prepared) {
            try { if (mp.isPlaying) mp.pause() } catch (e: Exception) {}
        }
    }

    fun resume() {
        paused = false
        if (!started || items.isEmpty()) return
        if (items[currentIndex].isVideo) {
            val mp = mediaPlayer
            if (mp != null && prepared) {
                try { mp.start() } catch (e: Exception) {}
            } else if (mp == null && surfaceReady) {
                startVideo(currentIndex)
            }
        } else {
            scheduleImageAdvance()
        }
    }

    fun release() {
        cleanup()
    }

    private fun cleanup() {
        animGen++
        loadToken++
        ui.removeCallbacksAndMessages(null)
        host.animate().cancel()
        releasePlayer()
    }

    // ---------- عرض العنصر الحالي ----------

    private fun showItem(index: Int) {
        val n = items.size
        if (n == 0) return
        currentIndex = ((index % n) + n) % n
        ui.removeCallbacks(imageAdvance)
        loadToken++
        indicator.setState(currentIndex, n)
        val item = items[currentIndex]
        if (item.isVideo) {
            imageView.visibility = View.GONE
            imageView.setImageDrawable(null)
            textureView.visibility = View.VISIBLE
            if (surfaceReady) startVideo(currentIndex) else releasePlayer()
        } else {
            releasePlayer()
            textureView.visibility = View.INVISIBLE
            imageView.visibility = View.VISIBLE
            loadImage(currentIndex)
        }
    }

    private fun startVideo(index: Int) {
        releasePlayer()
        val sf = surface ?: return
        val token = loadToken
        try {
            val mp = MediaPlayer()
            mediaPlayer = mp
            mp.setSurface(sf)
            mp.setDataSource(context, Uri.parse("android.resource://${context.packageName}/${items[index].resId}"))
            mp.isLooping = items.size == 1
            mp.setOnPreparedListener { p ->
                if (token == loadToken) {
                    prepared = true
                    failStreak = 0
                    if (!paused) {
                        try { p.start() } catch (e: Exception) {}
                    }
                }
            }
            mp.setOnCompletionListener {
                if (token == loadToken && items.size > 1) go(1)
            }
            mp.setOnErrorListener { _, _, _ ->
                if (token == loadToken) onMediaFailed()
                true
            }
            mp.prepareAsync()
        } catch (e: Exception) {
            onMediaFailed()
        }
    }

    private fun loadImage(index: Int) {
        val token = loadToken
        val resId = items[index].resId
        val targetW = max(width, resources.displayMetrics.widthPixels)
        Thread {
            var bmp: Bitmap? = null
            try {
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                resources.openRawResource(resId).use { BitmapFactory.decodeStream(it, null, bounds) }
                var sample = 1
                while (bounds.outWidth / (sample * 2) >= targetW) sample *= 2
                val opts = BitmapFactory.Options().apply { inSampleSize = sample }
                bmp = resources.openRawResource(resId).use { BitmapFactory.decodeStream(it, null, opts) }
            } catch (e: Throwable) {
                bmp = null
            }
            val result = bmp
            ui.post {
                if (token == loadToken) {
                    if (result == null) {
                        onMediaFailed()
                    } else {
                        imageView.setImageBitmap(result)
                        failStreak = 0
                        scheduleImageAdvance()
                    }
                }
            }
        }.start()
    }

    private fun scheduleImageAdvance() {
        ui.removeCallbacks(imageAdvance)
        if (items.size > 1 && !paused) ui.postDelayed(imageAdvance, IMAGE_MS)
    }

    private fun onMediaFailed() {
        failStreak++
        if (items.size < 2 || failStreak >= items.size) return
        val token = loadToken
        ui.postDelayed({ if (token == loadToken) go(1) }, 300L)
    }

    // ---------- التنقل ----------

    fun nextVideo() = go(1)

    fun prevVideo() = go(-1)

    private fun go(dir: Int) {
        if (items.size > 1) goTo(currentIndex + dir, dir)
    }

    private fun goTo(target: Int, dirHint: Int) {
        val n = items.size
        if (n < 2) return
        val idx = ((target % n) + n) % n
        if (idx == currentIndex) {
            if (host.translationX != 0f || host.alpha != 1f) {
                host.animate().alpha(1f).translationX(0f).setDuration(150L).start()
            }
            return
        }
        val dir = if (dirHint != 0) dirHint else if (idx > currentIndex) 1 else -1
        val vis = if (Lang.isAr(context)) -dir else dir
        val off = max(width, 1) * 0.25f
        val gen = ++animGen
        host.animate().cancel()
        host.animate().alpha(0f).translationX(-vis * off).setDuration(110L).withEndAction {
            if (gen == animGen) {
                showItem(idx)
                host.translationX = vis * off
                host.animate().alpha(1f).translationX(0f).setDuration(170L).start()
            }
        }.start()
    }

    // ---------- اللمس: تمرير القائمة يميناً ويساراً ----------

    override fun onTouchEvent(e: MotionEvent): Boolean {
        if (items.size < 2) return false
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = e.x
                downY = e.y
                dragging = false
                tracker?.recycle()
                tracker = VelocityTracker.obtain().also { it.addMovement(e) }
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                tracker?.addMovement(e)
                val dx = e.x - downX
                val dy = e.y - downY
                if (!dragging && abs(dx) > slop && abs(dx) > abs(dy)) {
                    dragging = true
                    host.animate().cancel()
                    parent?.requestDisallowInterceptTouchEvent(true)
                }
                if (dragging) {
                    host.translationX = dx * 0.6f
                    host.alpha = 1f - min(abs(dx) / max(width, 1), 1f) * 0.5f
                }
                return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                tracker?.addMovement(e)
                tracker?.computeCurrentVelocity(1000)
                val vx = tracker?.xVelocity ?: 0f
                tracker?.recycle()
                tracker = null
                if (dragging) {
                    dragging = false
                    parent?.requestDisallowInterceptTouchEvent(false)
                    val dx = e.x - downX
                    val far = abs(dx) > width * 0.2f
                    val fast = abs(vx) > 400f * d && (vx < 0f) == (dx < 0f)
                    if (e.actionMasked == MotionEvent.ACTION_UP && (far || fast)) {
                        var dir = if (dx < 0f) 1 else -1
                        if (Lang.isAr(context)) dir = -dir
                        goTo(currentIndex + dir, dir)
                    } else {
                        host.animate().alpha(1f).translationX(0f).setDuration(150L).start()
                    }
                }
                return true
            }
        }
        return super.onTouchEvent(e)
    }

    private fun releasePlayer() {
        prepared = false
        val mp = mediaPlayer
        mediaPlayer = null
        if (mp != null) {
            try { mp.setOnCompletionListener(null) } catch (e: Exception) {}
            try { mp.setOnErrorListener(null) } catch (e: Exception) {}
            try { mp.setOnPreparedListener(null) } catch (e: Exception) {}
            try { mp.reset() } catch (e: Exception) {}
            try { mp.release() } catch (e: Exception) {}
        }
    }

    // ---------- مؤشر النقاط (فوق الوسائط، ويمكن الضغط على أي نقطة) ----------

    private inner class IndicatorView(ctx: Context) : View(ctx) {
        private val activePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = gold }
        private val inactivePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(170, 255, 255, 255) }
        private val pillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(120, 0, 0, 0) }
        private val dotR = 3.5f * d
        private val activeW = 16f * d
        private val padH = 12f * d
        private var activeIdx = 0
        private var total = 0

        fun setState(idx: Int, count: Int) {
            val changed = count != total
            activeIdx = idx
            total = count
            if (changed) requestLayout()
            invalidate()
        }

        private fun spacingFor(avail: Float): Float {
            val normal = 8f * d
            if (total <= 1) return normal
            val fixed = activeW + (total - 1) * dotR * 2f
            val fit = (avail - fixed) / (total - 1)
            return min(normal, max(fit, 1.5f * d))
        }

        private fun contentWidth(sp: Float): Float =
            if (total <= 0) 0f else activeW + (total - 1) * (dotR * 2f + sp)

        override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
            val avail = max(MeasureSpec.getSize(widthMeasureSpec) - 2f * padH, 0f)
            val w = contentWidth(spacingFor(avail)) + 2f * padH
            setMeasuredDimension(w.toInt() + 1, dp(24))
        }

        private fun layoutDots(): Pair<FloatArray, IntArray> {
            val sp = spacingFor(max(width - 2f * padH, 0f))
            val centers = FloatArray(total)
            val ids = IntArray(total)
            val ar = Lang.isAr(context)
            var x = (width - contentWidth(sp)) / 2f
            for (p in 0 until total) {
                val idx = if (ar) total - 1 - p else p
                val w = if (idx == activeIdx) activeW else dotR * 2f
                centers[p] = x + w / 2f
                ids[p] = idx
                x += w + sp
            }
            return centers to ids
        }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            if (total <= 0) return
            val h = height.toFloat()
            canvas.drawRoundRect(RectF(0f, 0f, width.toFloat(), h), h / 2f, h / 2f, pillPaint)
            val (centers, ids) = layoutDots()
            val cy = h / 2f
            for (p in 0 until total) {
                if (ids[p] == activeIdx) {
                    canvas.drawRoundRect(
                        RectF(centers[p] - activeW / 2f, cy - dotR, centers[p] + activeW / 2f, cy + dotR),
                        dotR, dotR, activePaint
                    )
                } else {
                    canvas.drawCircle(centers[p], cy, dotR, inactivePaint)
                }
            }
        }

        override fun onTouchEvent(e: MotionEvent): Boolean {
            if (total < 2) return false
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    parent?.requestDisallowInterceptTouchEvent(true)
                    return true
                }
                MotionEvent.ACTION_UP -> {
                    val (centers, ids) = layoutDots()
                    var best = -1
                    var bestDist = Float.MAX_VALUE
                    for (p in 0 until total) {
                        val dist = abs(e.x - centers[p])
                        if (dist < bestDist) {
                            bestDist = dist
                            best = p
                        }
                    }
                    if (best >= 0 && bestDist <= dp(16)) goTo(ids[best], 0)
                    return true
                }
                MotionEvent.ACTION_CANCEL -> return true
            }
            return true
        }
    }

    companion object {
        private const val ASPECT = 536f / 1280f
        private const val IMAGE_MS = 4500L
        private val VIDEO_EXT = setOf("mp4", "m4v", "3gp", "webm", "mkv")
        private val IMAGE_EXT = setOf("png", "jpg", "jpeg", "webp", "gif", "bmp")
    }
}
