package com.example.antitheft

import android.app.admin.DeviceAdminReceiver
import android.app.admin.DevicePolicyManager
import android.content.Context
import android.content.Intent

class AdminReceiver : DeviceAdminReceiver() {
    override fun onPasswordFailed(context: Context, intent: Intent) {
        super.onPasswordFailed(context, intent)
        val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
        val failedAttempts = dpm.currentFailedPasswordAttempts

        if (failedAttempts >= 3) {
            SmsReceiver.startAlarm(context)
            val prefs = context.getSharedPreferences("AntiTheftPrefs", Context.MODE_PRIVATE)
            val savedNumber = prefs.getString("trusted_number", null)
            if (!savedNumber.isNullOrEmpty()) {
                SmsReceiver.sendSmsReply(
                    context,
                    savedNumber,
                    "ALERT! Kisi ne aapke phone me $failedAttempts baar galat password dala hai!"
                )
            }
        }
    }
}
