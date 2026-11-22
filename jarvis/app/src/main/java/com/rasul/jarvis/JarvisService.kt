package com.rasul.jarvis

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import java.util.Locale

/**
 * Фоновый сервис: постоянно слушает микрофон, ловит «Джарвис» и выполняет команду.
 */
class JarvisService : Service() {

    companion object {
        private const val CHANNEL_ID = "jarvis"
        private const val NOTIFICATION_ID = 1
        private const val AWAIT_COMMAND_MS = 7000L

        @Volatile
        var running = false
    }

    private val handler = Handler(Looper.getMainLooper())
    private var recognizer: SpeechRecognizer? = null
    private lateinit var music: MusicController
    private var tts: TextToSpeech? = null
    private var ttsReady = false

    // Если сказали просто «Джарвис», следующая фраза до этого момента считается командой
    private var awaitUntil = 0L

    private val restartRunnable = Runnable { startListening() }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        music = MusicController(this)
        tts = TextToSpeech(this) { status ->
            if (status == TextToSpeech.SUCCESS) {
                tts?.language = Locale("ru", "RU")
                ttsReady = true
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        createChannel()
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setContentTitle("Джарвис слушает")
            .setContentText("Скажи: «Джарвис, включи яндекс музыку ...»")
            .setOngoing(true)
            .build()

        val type = if (Build.VERSION.SDK_INT >= 29) ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE else 0
        ServiceCompat.startForeground(this, NOTIFICATION_ID, notification, type)

        running = true
        handler.post { startListening() }
        return START_STICKY
    }

    override fun onDestroy() {
        running = false
        handler.removeCallbacksAndMessages(null)
        recognizer?.destroy()
        recognizer = null
        tts?.shutdown()
        super.onDestroy()
    }

    // ---------- распознавание ----------

    private fun startListening() {
        if (!running) return
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            stopSelf()
            return
        }
        recognizer?.destroy()
        recognizer = SpeechRecognizer.createSpeechRecognizer(this).apply {
            setRecognitionListener(listener)
        }
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "ru-RU")
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false)
        }
        recognizer?.startListening(intent)
    }

    private fun restart(delayMs: Long) {
        handler.removeCallbacks(restartRunnable)
        handler.postDelayed(restartRunnable, delayMs)
    }

    private val listener = object : RecognitionListener {
        override fun onResults(results: Bundle?) {
            val texts = results
                ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                ?: arrayListOf()
            handleTexts(texts)
            restart(250)
        }

        override fun onError(error: Int) {
            when (error) {
                SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> stopSelf()
                SpeechRecognizer.ERROR_RECOGNIZER_BUSY,
                SpeechRecognizer.ERROR_CLIENT -> restart(1200)
                else -> restart(400) // тишина / не расслышал — слушаем дальше
            }
        }

        override fun onReadyForSpeech(params: Bundle?) {}
        override fun onBeginningOfSpeech() {}
        override fun onRmsChanged(rmsdB: Float) {}
        override fun onBufferReceived(buffer: ByteArray?) {}
        override fun onEndOfSpeech() {}
        override fun onPartialResults(partialResults: Bundle?) {}
        override fun onEvent(eventType: Int, params: Bundle?) {}
    }

    // ---------- команды ----------

    private fun handleTexts(texts: List<String>) {
        val awaiting = System.currentTimeMillis() < awaitUntil
        for (text in texts) {
            val cmd = CommandParser.parse(text, requireWake = !awaiting) ?: continue

            if (cmd is Command.WakeOnly) {
                awaitUntil = System.currentTimeMillis() + AWAIT_COMMAND_MS
                say("Слушаю")
                return
            }

            awaitUntil = 0L
            say(execute(cmd))
            return
        }
    }

    private fun execute(cmd: Command): String = when (cmd) {
        Command.VolumeUp -> { music.volumeUp(); "Громче" }
        Command.VolumeDown -> { music.volumeDown(); "Тише" }
        Command.Pause -> { music.pause(); "" }
        Command.Resume -> { music.resume(); "" }
        Command.Next -> { music.next(); "" }
        Command.Previous -> { music.previous(); "" }
        is Command.Play -> music.play(cmd.target, cmd.query)
        Command.WakeOnly -> ""
    }

    private fun say(text: String) {
        if (text.isNotEmpty() && ttsReady) {
            tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "jarvis")
        }
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            val nm = getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "Джарвис", NotificationManager.IMPORTANCE_LOW)
            )
        }
    }
}
