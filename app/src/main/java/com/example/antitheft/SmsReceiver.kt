package com.example.antitheft

import android.annotation.SuppressLint
import android.app.admin.DevicePolicyManager
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.location.LocationManager
import android.media.AudioManager
import android.media.RingtoneManager
import android.provider.Telephony
import android.telephony.SmsManager

class SmsReceiver : BroadcastReceiver() {
    @SuppressLint("MissingPermission")
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Telephony.Sms.Intents.SMS_RECEIVED_ACTION) {
            val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent)
            for (sms in messages) {
                val body = sms.messageBody ?: ""
                val sender = sms.originatingAddress ?: continue

                when {
                    body.contains("#LOCK", ignoreCase = true) -> {
                        val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
                        val adminName = ComponentName(context, AdminReceiver::class.java)
                        if (dpm.isAdminActive(adminName)) {
                            dpm.lockNow()
                        }
                    }
                    body.contains("#ALARM", ignoreCase = true) -> {
                        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
                        val maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_ALARM)
                        audioManager.setStreamVolume(AudioManager.STREAM_ALARM, maxVolume, 0)
                        
                        val alarmUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                        val ringtone = RingtoneManager.getRingtone(context, alarmUri)
                        ringtone.play()
                    }
                    body.contains("#LOCATE", ignoreCase = true) -> {
                        val lm = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
                        val loc = lm.getLastKnownLocation(LocationManager.GPS_PROVIDER)
                            ?: lm.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)

                        val smsManager = SmsManager.getDefault()
                        if (loc != null) {
                            val mapLink = "Phone Location: https://maps.google.com/?q=${loc.latitude},${loc.longitude}"
                            smsManager.sendTextMessage(sender, null, mapLink, null, null)
                        } else {
                            smsManager.sendTextMessage(sender, null, "Location active nahi hai ya GPS signal nahi mil raha.", null, null)
                        }
                    }
                }
            }
        }
    }
}
