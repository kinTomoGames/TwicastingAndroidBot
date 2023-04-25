package com.kingtomoya.twicasting.bot

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {

    private lateinit var serviceIntent: Intent

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val userIdEditView: EditText = findViewById(R.id.user_id_name_edit)
        val userCommentEditView: EditText = findViewById(R.id.user_comment_edit)
        val startButton = findViewById<Button>(R.id.bot_start_button)
        startButton.setOnClickListener {
            serviceIntent = Intent(this, StartLiveObserveService::class.java)
            serviceIntent.putExtra("userId", userIdEditView.text.toString())
            serviceIntent.putExtra("userComment", userCommentEditView.text.toString())

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(serviceIntent)
            }
        }

        val stopButton = findViewById<Button>(R.id.bot_stop_button)
        stopButton.setOnClickListener {
            try {
                stopService(serviceIntent)
            } catch (e: Exception) {
                throw e
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        stopService(serviceIntent)
    }
}