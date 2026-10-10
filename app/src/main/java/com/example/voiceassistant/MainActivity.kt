package com.example.voiceassistant

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

class MainActivity : AppCompatActivity() {

    private val prefs by lazy { getSharedPreferences("settings", Context.MODE_PRIVATE) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val keyInput = findViewById<EditText>(R.id.apiKeyInput)
        val wakeInput = findViewById<EditText>(R.id.wakeWordInput)
        val status = findViewById<TextView>(R.id.statusText)

        keyInput.setText(prefs.getString("api_key", ""))
        wakeInput.setText(prefs.getString("wake_word", "hey assistant"))

        findViewById<Button>(R.id.startButton).setOnClickListener {
            val key = keyInput.text.toString().trim()
            if (key.isEmpty()) {
                Toast.makeText(this, "Enter your OpenAI API key first", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            prefs.edit()
                .putString("api_key", key)
                .putString("wake_word", wakeInput.text.toString().trim().lowercase())
                .apply()

            if (!hasPermissions()) {
                requestPermissions()
                return@setOnClickListener
            }
            ContextCompat.startForegroundService(this, Intent(this, VoiceService::class.java))
            status.text = "Running. Say \"${wakeInput.text}\" then your question."
        }

        findViewById<Button>(R.id.stopButton).setOnClickListener {
            stopService(Intent(this, VoiceService::class.java))
            status.text = "Stopped"
        }
    }

    private fun hasPermissions(): Boolean =
        ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED

    private fun requestPermissions() {
        val perms = mutableListOf(Manifest.permission.RECORD_AUDIO)
        if (Build.VERSION.SDK_INT >= 33) perms += Manifest.permission.POST_NOTIFICATIONS
        ActivityCompat.requestPermissions(this, perms.toTypedArray(), 1)
    }

    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<out String>, grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (hasPermissions()) {
            Toast.makeText(this, "Permission granted. Tap Start again.", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(this, "Microphone permission is required", Toast.LENGTH_LONG).show()
        }
    }
}
