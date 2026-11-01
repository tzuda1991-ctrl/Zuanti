package com.example.antitheft

import android.Manifest
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
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
                   "1. #LOCK - Phone lock karein\n" +
                   "2. #ALARM - Siren + Vibration chalu karein\n" +
                   "3. #STOP - Alarm band karein\n" +
                   "4. #LOCATE - Live Google Map + Battery %\n" +
                   "5. #STATUS - Battery & Sound status\n" +
                   "Auto: 3 Galat Password par Alarm + SMS Alert!\n\n" +
                   "Niche dono buttons daba kar permissions allow karein:"
            textSize = 15f
            setPadding(0, 0, 0, 40)
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
                    putExtra(DevicePolicyManager.EXTRA_ADD_EXPLANATION, "Lock aur Wrong Password detection ke liye.")
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
