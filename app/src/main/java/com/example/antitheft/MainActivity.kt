package com.example.antitheft

import android.Manifest
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(60, 60, 60, 60)
        }

        val infoText = TextView(this).apply {
            text = "Anti-Theft SMS Commands:\n\n" +
                   "1. #LOCK - Phone ko turant lock karein\n" +
                   "2. #ALARM - Full volume me alarm bajayein\n" +
                   "3. #LOCATE - Google Map location SMS se mangwayein\n\n" +
                   "Niche diye dono buttons daba kar permissions allow karein:"
            textSize = 16f
            setPadding(0, 0, 0, 40)
        }

        val btnPermissions = Button(this).apply {
            text = "1. Allow SMS & Location Permissions"
            setOnClickListener {
                ActivityCompat.requestPermissions(
                    this@MainActivity,
                    arrayOf(
                        Manifest.permission.RECEIVE_SMS,
                        Manifest.permission.SEND_SMS,
                        Manifest.permission.ACCESS_FINE_LOCATION,
                        Manifest.permission.ACCESS_COARSE_LOCATION
                    ),
                    101
                )
            }
        }

        val btnAdmin = Button(this).apply {
            text = "2. Enable Device Admin (For Lock)"
            setOnClickListener {
                val compName = ComponentName(this@MainActivity, AdminReceiver::class.java)
                val intent = Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN).apply {
                    putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, compName)
                    putExtra(DevicePolicyManager.EXTRA_ADD_EXPLANATION, "Phone चोरी होने पर लॉक करने के लिए यह परमिशन जरूरी है।")
                }
                startActivity(intent)
            }
        }

        layout.addView(infoText)
        layout.addView(btnPermissions)
        layout.addView(btnAdmin)
        setContentView(layout)
    }
}
