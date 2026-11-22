package com.rasul.jarvis

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast

class MainActivity : Activity() {

    private lateinit var status: TextView
    private lateinit var toggle: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val pad = (20 * resources.displayMetrics.density).toInt()
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, pad * 2, pad, pad)
        }

        root.addView(TextView(this).apply {
            text = "Джарвис"
            textSize = 32f
            gravity = Gravity.CENTER
        })

        status = TextView(this).apply {
            textSize = 16f
            setPadding(0, pad, 0, pad)
        }
        root.addView(status)

        toggle = Button(this).apply { setOnClickListener { onToggle() } }
        root.addView(toggle)

        root.addView(TextView(this).apply {
            textSize = 15f
            setPadding(0, pad, 0, 0)
            text = """
                Что можно сказать:

                • «Джарвис, включи яндекс музыку» — открыть плеер
                • «Джарвис, включи яндекс музыку Numb» — найти и включить трек
                • «Джарвис, включи в лейне Numb» — то же в Lane
                • «Джарвис, громче» / «тише»
                • «Джарвис, стоп» / «продолжи»
                • «Джарвис, дальше» / «назад»

                Можно сказать просто «Джарвис», дождаться «Слушаю» и потом команду.
            """.trimIndent()
        })

        setContentView(ScrollView(this).apply { addView(root) })
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }

    private fun refresh() {
        val mic = checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        val overlay = Settings.canDrawOverlays(this)
        status.text = buildString {
            append(if (JarvisService.running) "Статус: слушаю\n" else "Статус: выключен\n")
            append(if (mic) "✓ Микрофон разрешён\n" else "✗ Нет доступа к микрофону\n")
            append(if (overlay) "✓ Поверх других приложений разрешено" else "✗ Нужно разрешить «Поверх других приложений»")
        }
        toggle.text = if (JarvisService.running) "Остановить Джарвиса" else "Запустить Джарвиса"
    }

    private fun onToggle() {
        if (JarvisService.running) {
            stopService(Intent(this, JarvisService::class.java))
            JarvisService.running = false
            refresh()
            return
        }

        val needed = mutableListOf(Manifest.permission.RECORD_AUDIO)
        if (Build.VERSION.SDK_INT >= 33) needed += Manifest.permission.POST_NOTIFICATIONS
        val missing = needed.filter { checkSelfPermission(it) != PackageManager.PERMISSION_GRANTED }
        if (missing.isNotEmpty()) {
            requestPermissions(missing.toTypedArray(), 1)
            return
        }

        startJarvis()
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            startJarvis()
        } else {
            Toast.makeText(this, "Без микрофона Джарвис не работает", Toast.LENGTH_LONG).show()
            refresh()
        }
    }

    private fun startJarvis() {
        if (!Settings.canDrawOverlays(this)) {
            Toast.makeText(
                this,
                "Включи «Поверх других приложений» для Джарвиса и нажми «Запустить» ещё раз",
                Toast.LENGTH_LONG
            ).show()
            startActivity(
                Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName"))
            )
            return
        }
        startForegroundService(Intent(this, JarvisService::class.java))
        status.postDelayed({ refresh() }, 500)
    }
}
