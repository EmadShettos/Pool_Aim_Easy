package com.example.poolaim

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView

class OverlayService : Service() {
    private lateinit var wm: WindowManager
    private lateinit var view: OverlayView
    private lateinit var controls: LinearLayout
    private lateinit var overlayParams: WindowManager.LayoutParams

    override fun onBind(i: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        startAsForeground()
        wm = getSystemService(WINDOW_SERVICE) as WindowManager
        view = OverlayView(this)
        view.locked = true
        overlayParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
            PixelFormat.TRANSLUCENT
        )
        overlayParams.alpha = 0.79f
        wm.addView(view, overlayParams)
        buildControls()
    }

    private fun startAsForeground() {
        val ch = "pool_aim"
        val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        nm.createNotificationChannel(NotificationChannel(ch, "Pool Aim", NotificationManager.IMPORTANCE_LOW))
        val n = Notification.Builder(this, ch)
            .setContentTitle("Pool Aim is running")
            .setSmallIcon(android.R.drawable.ic_menu_compass)
            .build()
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(1, n, android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else startForeground(1, n)
    }

    private fun btn(label: String, action: (Button) -> Unit): Button =
        Button(this).apply { text = label; isAllCaps = false; setOnClickListener { action(this) } }

    private fun applyLock(locked: Boolean, b: Button) {
        view.locked = locked
        b.text = if (locked) "Edit (move lines)" else "Play (lock)"
        val f = WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
        overlayParams.flags = if (locked) overlayParams.flags or f else overlayParams.flags and f.inv()
        wm.updateViewLayout(view, overlayParams)
        view.invalidate()
    }

    private fun buildControls() {
        val dm = resources.displayMetrics
        controls = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val list = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val scroll = ScrollView(this).apply { addView(list); visibility = View.GONE }

        val menu = btn("\u2630") { scroll.visibility = if (scroll.visibility == View.GONE) View.VISIBLE else View.GONE }

        list.addView(btn("Stop") { stopSelf() })
        list.addView(btn("Edit (move lines)") { b -> applyLock(!view.locked, b) })
        list.addView(btn("Swap C / 8") { view.swap() })
        list.addView(btn("Reset") { view.resetLayout() })
        list.addView(btn("Theme") { view.nextTheme() })
        list.addView(btn("Line +") { view.thicker(1f) })
        list.addView(btn("Line -") { view.thicker(-1f) })
        val names = mapOf(
            "lines" to "Pocket lines", "single" to "Single aim", "bank" to "Bank shot",
            "double_bank" to "Double bank", "ghost" to "Ghost ball", "hud" to "HUD",
            "animate" to "Animations", "snap" to "Snap to pockets"
        )
        for ((k, n) in names) list.addView(btn(n) { view.toggle(k) })

        controls.addView(menu)
        controls.addView(scroll, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT, (dm.heightPixels * 0.6f).toInt()))

        val lp = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE, PixelFormat.TRANSLUCENT
        ).apply { gravity = Gravity.TOP or Gravity.START; x = 16; y = 120 }
        wm.addView(controls, lp)
    }

    override fun onDestroy() {
        view.save()
        runCatching { wm.removeView(controls) }
        runCatching { wm.removeView(view) }
        super.onDestroy()
    }
}
