package com.example.poolaim

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.drawable.GradientDrawable
import android.media.MediaPlayer
import android.net.Uri
import android.view.GestureDetector
import android.view.Gravity
import android.view.MotionEvent
import android.view.TextureView
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import kotlin.math.abs

class VideoSliderView(ctx: Context) : FrameLayout(ctx) {
    private val d = resources.displayMetrics.density
    private val gold = 0xFFFFD21F.toInt()

    private val videoResIds = listOf(
        R.raw.v1,
        R.raw.v2,
        R.raw.v3,
        R.raw.v4,
        R.raw.v5
    )

    private var currentIndex = 0
    private var mediaPlayer: MediaPlayer? = null
    private val textureView = TextureView(ctx)
    private val indicatorView = IndicatorView(ctx)
    private var isSurfaceAvailable = false

    private fun dp(v: Int) = (v * d).toInt()

    init {
        // تصميم اللوحة الخارجي: إطار ذهبي وزوايا منحنية متناسقة مع أسلوب التطبيق
        background = GradientDrawable(
            GradientDrawable.Orientation.TOP_BOTTOM,
            intArrayOf(0xFF12894B.toInt(), 0xFF05301B.toInt())
        ).apply {
            cornerRadius = 18f * d
            setStroke(dp(2), gold)
        }
        clipToOutline = true

        // إعداد عارض الفيديو
        val videoContainer = object : FrameLayout(ctx) {
            private val clipPath = Path()
            private val clipRect = RectF()

            override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
                super.onSizeChanged(w, h, oldw, oldh)
                clipRect.set(0f, 0f, w.toFloat(), h.toFloat())
                clipPath.reset()
                clipPath.addRoundRect(clipRect, 14f * d, 14f * d, Path.Direction.CW)
            }

            override fun dispatchDraw(canvas: Canvas) {
                canvas.save()
                canvas.clipPath(clipPath)
                super.dispatchDraw(canvas)
                canvas.restore()
            }
        }

        val pad = dp(4)
        videoContainer.setPadding(pad, pad, pad, pad)

        textureView.surfaceTextureListener = object : TextureView.SurfaceTextureListener {
            override fun onSurfaceTextureAvailable(surface: android.graphics.SurfaceTexture, width: Int, height: Int) {
                isSurfaceAvailable = true
                playVideo(currentIndex)
            }
            override fun onSurfaceTextureSizeChanged(surface: android.graphics.SurfaceTexture, width: Int, height: Int) {}
            override fun onSurfaceTextureDestroyed(surface: android.graphics.SurfaceTexture): Boolean {
                isSurfaceAvailable = false
                releasePlayer()
                return true
            }
            override fun onSurfaceTextureUpdated(surface: android.graphics.SurfaceTexture) {}
        }

        videoContainer.addView(textureView, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        addView(videoContainer, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT).apply {
            bottomMargin = dp(24) // مساحة مخصصة لمؤشر النقاط في الأسفل
        })

        // شريط دوائر التمرير (Dots Indicator) في المنتصف السفلي
        val bottomBar = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }
        bottomBar.addView(indicatorView, LinearLayout.LayoutParams(LayoutParams.WRAP_CONTENT, dp(18)))
        addView(bottomBar, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply {
            gravity = Gravity.BOTTOM
            bottomMargin = dp(4)
        })

        // دعم السحب اليدوي (Swipe) يميناً ويساراً للتنقل السريع بين الفيديوهات
        val gestureDetector = GestureDetector(ctx, object : GestureDetector.SimpleOnGestureListener() {
            override fun onFling(e1: MotionEvent?, e2: MotionEvent, velocityX: Float, velocityY: Float): Boolean {
                if (e1 == null) return false
                val diffX = e2.x - e1.x
                val diffY = e2.y - e1.y
                if (abs(diffX) > abs(diffY) && abs(diffX) > 50 * d && abs(velocityX) > 100 * d) {
                    val isAr = Lang.isAr(context)
                    if (diffX > 0) {
                        // سحب نحو اليمين
                        if (isAr) nextVideo() else prevVideo()
                    } else {
                        // سحب نحو اليسار
                        if (isAr) prevVideo() else nextVideo()
                    }
                    return true
                }
                return false
            }
        })

        setOnTouchListener { _, event ->
            gestureDetector.onTouchEvent(event)
            true
        }
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val width = MeasureSpec.getSize(widthMeasureSpec)
        // نسبة أبعاد الفيديو العريضة (1280x536) + مساحة شريط النقاط السفلي
        val videoHeight = (width * 536f / 1280f).toInt()
        val totalHeight = videoHeight + dp(28)
        super.onMeasure(
            widthMeasureSpec,
            MeasureSpec.makeMeasureSpec(totalHeight, MeasureSpec.EXACTLY)
        )
    }

    private fun playVideo(index: Int) {
        if (!isSurfaceAvailable) return
        releasePlayer()
        try {
            val uri = Uri.parse("android.resource://${context.packageName}/${videoResIds[index]}")
            mediaPlayer = MediaPlayer().apply {
                setSurface(android.view.Surface(textureView.surfaceTexture))
                setDataSource(context, uri)
                setOnPreparedListener { mp ->
                    mp.start()
                }
                // عند اكتمال مدة الفيديو ينتقل تلقائياً إلى الفيديو التالي
                setOnCompletionListener {
                    nextVideo()
                }
                prepareAsync()
            }
            indicatorView.setSelection(index, videoResIds.size)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun nextVideo() {
        currentIndex = (currentIndex + 1) % videoResIds.size
        playVideo(currentIndex)
    }

    fun prevVideo() {
        currentIndex = if (currentIndex - 1 < 0) videoResIds.size - 1 else currentIndex - 1
        playVideo(currentIndex)
    }

    fun pause() {
        mediaPlayer?.let { if (it.isPlaying) it.pause() }
    }

    fun resume() {
        if (mediaPlayer != null) {
            mediaPlayer?.start()
        } else if (isSurfaceAvailable) {
            playVideo(currentIndex)
        }
    }

    fun release() {
        releasePlayer()
    }

    private fun releasePlayer() {
        mediaPlayer?.let {
            try {
                if (it.isPlaying) it.stop()
                it.reset()
                it.release()
            } catch (e: Exception) {}
        }
        mediaPlayer = null
    }

    // مكون رسم نقاط مؤشر التمرير السفلية (كما في Snake)
    private inner class IndicatorView(ctx: Context) : View(ctx) {
        private val activePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = gold }
        private val inactivePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(130, 255, 255, 255) }
        private var activeIdx = 0
        private var totalCount = 5

        fun setSelection(idx: Int, total: Int) {
            activeIdx = idx
            totalCount = total
            invalidate()
        }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            if (totalCount <= 0) return
            val dotRadius = 3f * d
            val activeWidth = 14f * d
            val spacing = 7f * d

            val isAr = Lang.isAr(context)
            val totalWidth = (totalCount - 1) * (dotRadius * 2 + spacing) + activeWidth
            var startX = (width - totalWidth) / 2f
            val centerY = height / 2f

            for (i in 0 until totalCount) {
                val displayIdx = if (isAr) (totalCount - 1 - i) else i
                if (displayIdx == activeIdx) {
                    val rect = RectF(startX, centerY - dotRadius, startX + activeWidth, centerY + dotRadius)
                    canvas.drawRoundRect(rect, dotRadius, dotRadius, activePaint)
                    startX += activeWidth + spacing
                } else {
                    canvas.drawCircle(startX + dotRadius, centerY, dotRadius, inactivePaint)
                    startX += (dotRadius * 2) + spacing
                }
            }
        }
    }
}
