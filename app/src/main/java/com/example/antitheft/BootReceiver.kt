package com.example.antitheft

import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.telephony.SubscriptionManager

class BootReceiver : BroadcastReceiver() {
    @SuppressLint("MissingPermission")
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            val prefs = context.getSharedPreferences("AntiTheftPrefs", Context.MODE_PRIVATE)
            val trustedNumber = prefs.getString("trusted_number", "") ?: ""
            if (trustedNumber.isEmpty()) return

            val subManager = context.getSystemService(Context.TELEPHONY_SUBSCRIPTION_SERVICE) as SubscriptionManager
            val currentSimId = subManager.activeSubscriptionInfoList?.firstOrNull()?.iccId ?: "SIM_DETECTED"
            val savedSimId = prefs.getString("saved_sim_id", "") ?: ""

            if (savedSimId.isNotEmpty() && savedSimId != currentSimId) {
                SmsReceiver.saveLog(context, "SIM CHANGE DETECTED on Boot!")
                SmsReceiver.sendSmsReply(context, trustedNumber, "ALERT! Aapke phone me NAYA SIM lagaya gaya hai ya SIM badla gaya hai!")
                SmsReceiver.handleLocate(context, trustedNumber, -1)
            } else {
                SmsReceiver.saveLog(context, "Phone Restarted - Sending Location to $trustedNumber")
                SmsReceiver.sendSmsReply(context, trustedNumber, "ALERT! Aapka phone abhi Switch ON (Restart) hua hai!")
                SmsReceiver.handleLocate(context, trustedNumber, -1)
            }
        }
    }
}
