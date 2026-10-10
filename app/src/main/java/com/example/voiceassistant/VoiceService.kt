package com.example.voiceassistant

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import androidx.core.app.NotificationCompat
import java.util.Locale
import java.util.concurrent.Executors

/**
 * Foreground service that:
 * 1. listens continuously for speech,
 * 2. reacts when the text starts with the wake word,
 * 3. sends the question to OpenAI,
 * 4. speaks the answer.
 */
class VoiceService : Service(), TextToSpeech.OnInitListener {

    private val main = Handler(Looper.getMainLooper())
    private val worker = Executors.newSingleThreadExecutor()

    private var recognizer: SpeechRecognizer? = null
    private var tts: TextToSpeech? = null
    private var ttsReady = false
    private var speaking = false
    private var running = false
    private var wakeLock: PowerManager.WakeLock? = null

    private lateinit var wakeWord: String
    private lateinit var client: OpenAiClient

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        val prefs = getSharedPreferences("settings", Context.MODE_PRIVATE)
        wakeWord = prefs.getString("wake_word", "hey assistant").orEmpty().ifBlank { "hey assistant" }
        client = OpenAiClient(prefs.getString("api_key", "").orEmpty())
        tts = TextToSpeech(this, this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startAsForeground("Listening for \"$wakeWord\"")
        if (wakeLock == null) {
            // Keeps the CPU awake so listening continues while the phone is locked.
            wakeLock = getSystemService(PowerManager::class.java)
                .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "voiceassistant:listening")
                .apply { acquire() }
        }
        running = true
        main.post { startListening() }
        return START_STICKY
    }

    override fun onDestroy() {
        running = false
        main.removeCallbacksAndMessages(null)
        recognizer?.destroy()
        tts?.shutdown()
        worker.shutdownNow()
        wakeLock?.let { if (it.isHeld) it.release() }
        wakeLock = null
        super.onDestroy()
    }

    // ---------- Text to speech ----------

    override fun onInit(status: Int) {
        ttsReady = status == TextToSpeech.SUCCESS
        if (ttsReady) {
            tts?.language = Locale.getDefault()
            tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) {}
                override fun onDone(utteranceId: String?) {
                    speaking = false
                    main.post { if (running) startListening() }
                }
                @Deprecated("Deprecated in Java")
                override fun onError(utteranceId: String?) {
                    speaking = false
                    main.post { if (running) startListening() }
                }
            })
        }
    }

    private fun speak(text: String) {
        if (!ttsReady) {
            main.post { if (running) startListening() }
            return
        }
        speaking = true
        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "reply")
    }

    // ---------- Speech recognition ----------

    private fun startListening() {
        if (!running || speaking) return
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            updateNotification("Speech recognition is not available on this device")
            return
        }
        recognizer?.destroy()
        recognizer = SpeechRecognizer.createSpeechRecognizer(this).apply {
            setRecognitionListener(listener)
        }
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false)
        }
        recognizer?.startListening(intent)
    }

    private val listener = object : RecognitionListener {
        override fun onResults(results: Bundle?) {
            val heard = results
                ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                ?.firstOrNull()
                ?.lowercase()
                .orEmpty()
            handleHeard(heard)
        }

        override fun onError(error: Int) {
            // Silence / no match is normal: just listen again after a short pause.
            main.postDelayed({ if (running) startListening() }, 700)
        }

        override fun onReadyForSpeech(params: Bundle?) {}
        override fun onBeginningOfSpeech() {}
        override fun onRmsChanged(rmsdB: Float) {}
        override fun onBufferReceived(buffer: ByteArray?) {}
        override fun onEndOfSpeech() {}
        override fun onPartialResults(partialResults: Bundle?) {}
        override fun onEvent(eventType: Int, params: Bundle?) {}
    }

    private fun handleHeard(heard: String) {
        val idx = heard.indexOf(wakeWord)
        if (idx < 0) {
            startListening() // not for us, keep listening
            return
        }
        val question = heard.substring(idx + wakeWord.length).trim()
        if (question.isEmpty()) {
            speak("Yes?")
            return
        }
        updateNotification("Thinking...")
        worker.execute {
            val answer = try {
                client.ask(question)
            } catch (e: Exception) {
                "Sorry, I couldn't reach OpenAI. Check your internet connection."
            }
            main.post {
                updateNotification("Listening for \"$wakeWord\"")
                speak(answer)
            }
        }
    }

    // ---------- Notification (required for foreground services) ----------

    private fun buildNotification(text: String): Notification {
        val nm = getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= 26 && nm.getNotificationChannel(CHANNEL_ID) == null) {
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "Voice assistant", NotificationManager.IMPORTANCE_LOW)
            )
        }
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Voice Assistant")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setOngoing(true)
            .build()
    }

    private fun startAsForeground(text: String) {
        val notification = buildNotification(text)
        if (Build.VERSION.SDK_INT >= 29) {
            startForeground(NOTIF_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE)
        } else {
            startForeground(NOTIF_ID, notification)
        }
    }

    private fun updateNotification(text: String) {
        getSystemService(NotificationManager::class.java).notify(NOTIF_ID, buildNotification(text))
    }

    companion object {
        private const val CHANNEL_ID = "voice_assistant"
        private const val NOTIF_ID = 1
    }
}
