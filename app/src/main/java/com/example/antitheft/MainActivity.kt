package com.example.antitheft

import android.Manifest
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat

class MainActivity : AppCompatActivity() {
    private lateinit var logText: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val scroll = ScrollView(this)
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(50, 50, 50, 50)
        }

        val infoText = TextView(this).apply {
            text = "SMS Commands: #LOCK, #ALARM, #STOP, #LOCATE, #STATUS"
            textSize = 16f
            setPadding(0, 0, 0, 30)
        }

        val btnPermissions = Button(this).apply {
            text = "1. Allow All Permissions"
            setOnClickListener {
                ActivityCompat.requestPermissions(
                    this@MainActivity,
                    arrayOf(
                        Manifest.permission.RECEIVE_SMS,
                        Manifest.permission.SEND_SMS,
                        Manifest.permission.READ_PHONE_STATE,
                        Manifest.permission.ACCESS_FINE_LOCATION,
                        Manifest.permission.ACCESS_COARSE_LOCATION
                    ),
                    101
                )
            }
        }

        val btnAdmin = Button(this).apply {
            text = "2. Enable Device Admin"
            setOnClickListener {
                val compName = ComponentName(this@MainActivity, AdminReceiver::class.java)
                val intent = Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN).apply {
                    putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, compName)
                }
                startActivity(intent)
            }
        }

        val btnTestGps = Button(this).apply {
            text = "3. Test GPS & Send SMS to Last Sender"
            setOnClickListener {
                val prefs = getSharedPreferences("AntiTheftPrefs", Context.MODE_PRIVATE)
                val sender = prefs.getString("trusted_number", null)
                if (sender != null) {
                    SmsReceiver.saveLog(this@MainActivity, "Manual Test started for $sender...")
                    SmsReceiver.handleLocate(this@MainActivity, sender, -1)
                    refreshLogs()
                } else {
                    SmsReceiver.saveLog(this@MainActivity, "Pehle dusre phone se koi # command SMS bhejein taaki number save ho!")
                    refreshLogs()
                }
            }
        }

        val btnRefresh = Button(this).apply {
            text = "4. Refresh Activity Logs"
            setOnClickListener { refreshLogs() }
        }

        logText = TextView(this).apply {
            textSize = 14f
            setTextColor(Color.DKGRAY)
            setPadding(0, 40, 0, 40)
        }

        layout.addView(infoText)
        layout.addView(btnPermissions)
        layout.addView(btnAdmin)
        layout.addView(btnTestGps)
        layout.addView(btnRefresh)
        layout.addView(logText)
        scroll.addView(layout)
        setContentView(scroll)

        refreshLogs()
    }

    override fun onResume() {
        super.onResume()
        refreshLogs()
    }

    private fun refreshLogs() {
        val prefs = getSharedPreferences("AntiTheftPrefs", Context.MODE_PRIVATE)
        val logs = prefs.getString("app_logs", "Abhi tak koi command nahi aaya.")
        val trusted = prefs.getString("trusted_number", "None")
        logText.text = "Last Command Sender: $trusted\n\n--- LIVE LOGS ---\n$logs"
    }
}
