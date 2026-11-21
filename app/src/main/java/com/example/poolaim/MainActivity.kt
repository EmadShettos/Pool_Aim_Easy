package com.example.poolaim

import android.app.AlertDialog
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val btnStart = findViewById<Button>(R.id.btnStart)
        val btnGuide = findViewById<Button>(R.id.btnGuide)

        btnGuide.text = "كيف تثبت التطبيق وكيف تلعب"

        btnStart.setOnClickListener {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
                Toast.makeText(this, "يرجى تفعيل إذن الظهور فوق التطبيقات أولاً", Toast.LENGTH_LONG).show()
                val intent = Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:$packageName")
                )
                startActivity(intent)
            } else {
                val serviceIntent = Intent(this, OverlayService::class.java)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    startForegroundService(serviceIntent)
                } else {
                    startService(serviceIntent)
                }
                Toast.makeText(this, "تم تفعيل خطوط التصويب بنجاح", Toast.LENGTH_SHORT).show()
                finish()
            }
        }

        btnGuide.setOnClickListener {
            showInstallationAndPlayGuide()
        }
    }

    private fun showInstallationAndPlayGuide() {
        val guideMessage = """
            📌 أولاً: خطوات تثبيت وتحديث التطبيق
            1. عند تنزيل تحديث جديد، تأكد من تثبيته مباشرة.
            2. لتفادي رسالة "تعارض الحزمة"، احرص على استخدام نفس نسخة التوقيع (Debug Key) دائماً عند التصدير.
            3. عند تشغيل التطبيق أول مرة والضغط على Start، سيتم توجيهك لتفعيل إذن الظهور فوق التطبيقات، فعّله ثم اضغط Start لتظهر القائمة.
            
            🎱 ثانياً: كيفية اللعب وضبط القوة
            1. ضع دوائر التحديد على كرات البلياردو لتظهر مسارات الارتداد تلقائياً.
            2. استخدم زر قوة العصا (Force) من 1 إلى 10:
               - القوة 1: مخصصة لعصا المبتدئين؛ تعطي ارتدادات عمودية ومستقيمة نقية بدقة 100%.
               - القوة 10: مخصصة لعصا Archons وعصا الملاك Max؛ تضبط مسار الارتداد ليواكب السرعة الفائقة وانضغاط وسائد الطاولة.
        """.trimIndent()

        AlertDialog.Builder(this)
            .setTitle("دليل التثبيت وطريقة اللعب")
            .setMessage(guideMessage)
            .setPositiveButton("فهمت ذلك", null)
            .show()
    }
}
