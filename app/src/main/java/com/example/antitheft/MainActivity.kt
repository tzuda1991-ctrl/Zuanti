package com.example.antitheft

import android.Manifest
import android.annotation.SuppressLint
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Bundle
import android.telephony.SubscriptionManager
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat

class MainActivity : AppCompatActivity() {
    private lateinit var logText: TextView

    @SuppressLint("MissingPermission")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val prefs = getSharedPreferences("AntiTheftPrefs", Context.MODE_PRIVATE)
        val scroll = ScrollView(this)
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(50, 40, 50, 40)
        }

        val titleText = TextView(this).apply {
            text = "Stealth Anti-Theft & Media Vault\n" +
                   "• #LOCK <PIN> | #ALARM <PIN> | #STOP <PIN>\n" +
                   "• #LOCATE <PIN> (GPS Map Link)\n" +
                   "• #CALLME <PIN> (Auto Voice Call)\n" +
                   "• #SPEAK <PIN> <Msg> (Loud TTS Voice)\n" +
                   "• #PHOTO <PIN> (Capture Front Camera Snap)\n" +
                   "• #RING <PIN> (Unmute Volume)"
            textSize = 14f
            setPadding(0, 0, 0, 25)
        }

        val inputNumber = EditText(this).apply {
            hint = "Emergency Mobile Number"
            setText(prefs.getString("trusted_number", ""))
        }

        val inputPin = EditText(this).apply {
            hint = "Secret 4-Digit PIN (for Calculator & SMS)"
            setText(prefs.getString("secret_pin", "1234"))
        }

        val btnSave = Button(this).apply {
            text = "SAVE SETTINGS"
            setOnClickListener {
                val num = inputNumber.text.toString().trim()
                val pin = inputPin.text.toString().trim().ifEmpty { "1234" }
                prefs.edit().putString("trusted_number", num).putString("secret_pin", pin).apply()
                Toast.makeText(this@MainActivity, "Saved!", Toast.LENGTH_SHORT).show()
                refreshLogs()
            }
        }

        val btnHide = Button(this).apply {
            text = "HIDE APP FROM DRAWER (STEALTH MODE)"
            setOnClickListener {
                val p = packageManager
                p.setComponentEnabledSetting(
                    ComponentName(this@MainActivity, CalculatorActivity::class.java),
                    PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                    PackageManager.DONT_KILL_APP
                )
                Toast.makeText(this@MainActivity, "App Hidden from Menu! Sirf Settings se dikhega.", Toast.LENGTH_LONG).show()
            }
        }

        val btnPermissions = Button(this).apply {
            text = "1. Allow All Permissions (Camera & SMS)"
            setOnClickListener {
                ActivityCompat.requestPermissions(
                    this@MainActivity,
                    arrayOf(
                        Manifest.permission.RECEIVE_SMS,
                        Manifest.permission.SEND_SMS,
                        Manifest.permission.READ_PHONE_STATE,
                        Manifest.permission.CALL_PHONE,
                        Manifest.permission.CAMERA,
                        Manifest.permission.RECORD_AUDIO,
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

        logText = TextView(this).apply {
            textSize = 13f
            setTextColor(Color.DKGRAY)
            setPadding(0, 30, 0, 30)
        }

        layout.addView(titleText)
        layout.addView(inputNumber)
        layout.addView(inputPin)
        layout.addView(btnSave)
        layout.addView(btnHide)
        layout.addView(btnPermissions)
        layout.addView(btnAdmin)
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
        val logs = prefs.getString("app_logs", "No logs yet.")
        val trusted = prefs.getString("trusted_number", "Not Set")
        val pin = prefs.getString("secret_pin", "1234")
        logText.text = "Emergency No: $trusted | PIN: $pin\n\n--- LIVE LOGS ---\n$logs"
    }
}
