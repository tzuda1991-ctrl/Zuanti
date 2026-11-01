package com.example.antitheft

import android.Manifest
import android.annotation.SuppressLint
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
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
            text = "Anti-Theft Setup & Commands\n" +
                   "Use PIN with commands (e.g., #LOCATE 1234):\n" +
                   "• #LOCK <PIN> (Lock Phone)\n" +
                   "• #ALARM <PIN> / #STOP <PIN>\n" +
                   "• #LOCATE <PIN> (GPS Map Link)\n" +
                   "• #STATUS <PIN> (Battery & Sound)\n" +
                   "• #CALLME <PIN> (Phone calls you back)\n" +
                   "• #SPEAK <PIN> <Msg> (Speak loud message)\n" +
                   "• #RING <PIN> (Unmute silent phone)"
            textSize = 14f
            setPadding(0, 0, 0, 25)
        }

        val inputNumber = EditText(this).apply {
            hint = "Emergency Mobile Number (e.g. +918238523830)"
            setText(prefs.getString("trusted_number", "+918238523830"))
        }

        val inputPin = EditText(this).apply {
            hint = "Secret 4-Digit PIN (e.g. 1234)"
            setText(prefs.getString("secret_pin", "1234"))
        }

        val btnSave = Button(this).apply {
            text = "SAVE NUMBER, PIN & LOCK CURRENT SIM"
            setOnClickListener {
                val num = inputNumber.text.toString().trim()
                val pin = inputPin.text.toString().trim().ifEmpty { "1234" }

                var simId = "SIM_SAVED"
                try {
                    val sm = getSystemService(Context.TELEPHONY_SUBSCRIPTION_SERVICE) as SubscriptionManager
                    simId = sm.activeSubscriptionInfoList?.firstOrNull()?.iccId ?: "SIM_SAVED"
                } catch (_: Exception) {}

                prefs.edit()
                    .putString("trusted_number", num)
                    .putString("secret_pin", pin)
                    .putString("saved_sim_id", simId)
                    .apply()

                SmsReceiver.saveLog(this@MainActivity, "Saved Emergency Number: $num & PIN: $pin")
                Toast.makeText(this@MainActivity, "Settings Saved Successfully!", Toast.LENGTH_SHORT).show()
                refreshLogs()
            }
        }

        val btnPermissions = Button(this).apply {
            text = "1. Allow All Permissions (With Call)"
            setOnClickListener {
                ActivityCompat.requestPermissions(
                    this@MainActivity,
                    arrayOf(
                        Manifest.permission.RECEIVE_SMS,
                        Manifest.permission.SEND_SMS,
                        Manifest.permission.READ_PHONE_STATE,
                        Manifest.permission.CALL_PHONE,
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
            text = "3. Test GPS & Send SMS to Saved Number"
            setOnClickListener {
                val sender = prefs.getString("trusted_number", null)
                if (!sender.isNullOrEmpty()) {
                    SmsReceiver.saveLog(this@MainActivity, "Manual Test started for $sender...")
                    SmsReceiver.handleLocate(this@MainActivity, sender, -1)
                    refreshLogs()
                } else {
                    Toast.makeText(this@MainActivity, "Pehle upar Emergency Number save karein!", Toast.LENGTH_SHORT).show()
                }
            }
        }

        val btnRefresh = Button(this).apply {
            text = "4. Refresh Activity Logs"
            setOnClickListener { refreshLogs() }
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
        val trusted = prefs.getString("trusted_number", "Not Set")
        val pin = prefs.getString("secret_pin", "1234")
        logText.text = "Emergency No: $trusted | Secret PIN: $pin\n\n--- LIVE LOGS ---\n$logs"
    }
}
