package com.example.antitheft

import android.annotation.SuppressLint
import android.app.admin.DevicePolicyManager
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.os.BatteryManager
import android.os.Build
import android.os.Bundle
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.provider.Telephony
import android.telephony.SmsManager
import android.telephony.SubscriptionManager

class SmsReceiver : BroadcastReceiver() {

    companion object {
        private var mediaPlayer: MediaPlayer? = null
        private var vibrator: Vibrator? = null

        fun startAlarm(context: Context) {
            stopAlarm()
            val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
            val maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_ALARM)
            audioManager.setStreamVolume(AudioManager.STREAM_ALARM, maxVolume, 0)

            val alarmUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)

            mediaPlayer = MediaPlayer().apply {
                setDataSource(context, alarmUri)
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
                isLooping = true
                prepare()
                start()
            }

            vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator?.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 500, 500), 0))
            } else {
                @Suppress("DEPRECATION")
                vibrator?.vibrate(longArrayOf(0, 500, 500), 0)
            }
        }

        fun stopAlarm() {
            try {
                mediaPlayer?.let {
                    if (it.isPlaying) it.stop()
                    it.release()
                }
                mediaPlayer = null
                vibrator?.cancel()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        @SuppressLint("MissingPermission")
        fun sendSmsReply(context: Context, sender: String, message: String, subId: Int = -1) {
            try {
                val smsManager = if (subId != -1 && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    context.getSystemService(SmsManager::class.java).createForSubscriptionId(subId)
                } else if (subId != -1) {
                    @Suppress("DEPRECATION")
                    SmsManager.getSmsManagerForSubscriptionId(subId)
                } else {
                    val defaultSubId = SubscriptionManager.getDefaultSmsSubscriptionId()
                    if (defaultSubId != SubscriptionManager.INVALID_SUBSCRIPTION_ID) {
                        @Suppress("DEPRECATION")
                        SmsManager.getSmsManagerForSubscriptionId(defaultSubId)
                    } else {
                        @Suppress("DEPRECATION")
                        SmsManager.getDefault()
                    }
                }
                smsManager.sendTextMessage(sender, null, message, null, null)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        fun getBatteryLevel(context: Context): Int {
            val intent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
            val level = intent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
            val scale = intent?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
            return if (level != -1 && scale != -1) (level * 100 / scale) else -1
        }
    }

    @SuppressLint("MissingPermission")
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Telephony.Sms.Intents.SMS_RECEIVED_ACTION) {
            val bundle = intent.extras
            val subId = bundle?.getInt("subscription", -1) ?: -1
            val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent)

            for (sms in messages) {
                val body = sms.messageBody?.uppercase() ?: ""
                val sender = sms.originatingAddress ?: continue

                if (body.contains("#")) {
                    context.getSharedPreferences("AntiTheftPrefs", Context.MODE_PRIVATE)
                        .edit().putString("trusted_number", sender).apply()
                }

                when {
                    body.contains("#LOCK") -> {
                        val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
                        val adminName = ComponentName(context, AdminReceiver::class.java)
                        if (dpm.isAdminActive(adminName)) {
                            dpm.lockNow()
                            sendSmsReply(context, sender, "Phone Lock kar diya gaya hai.", subId)
                        }
                    }

                    body.contains("#ALARM") || body.contains("#SIREN") -> {
                        startAlarm(context)
                    }

                    body.contains("#STOP") -> {
                        stopAlarm()
                        sendSmsReply(context, sender, "Alarm band kar diya gaya hai.", subId)
                    }

                    body.contains("#STATUS") -> {
                        val battery = getBatteryLevel(context)
                        val am = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
                        val mode = when (am.ringerMode) {
                            AudioManager.RINGER_MODE_SILENT -> "Silent"
                            AudioManager.RINGER_MODE_VIBRATE -> "Vibrate"
                            else -> "Normal"
                        }
                        sendSmsReply(context, sender, "Phone Status:\nBattery: $battery%\nSound Mode: $mode", subId)
                    }

                    body.contains("#LOCATE") -> {
                        val lm = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
                        val battery = getBatteryLevel(context)

                        val lastLoc = lm.getLastKnownLocation(LocationManager.GPS_PROVIDER)
                            ?: lm.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)

                        if (lastLoc != null) {
                            val link = "Location (Bat: $battery%): https://maps.google.com/?q=${lastLoc.latitude},${lastLoc.longitude}"
                            sendSmsReply(context, sender, link, subId)
                        } else {
                            val provider = when {
                                lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER) -> LocationManager.NETWORK_PROVIDER
                                lm.isProviderEnabled(LocationManager.GPS_PROVIDER) -> LocationManager.GPS_PROVIDER
                                else -> null
                            }

                            if (provider != null) {
                                val pendingResult = goAsync()
                                val listener = object : LocationListener {
                                    override fun onLocationChanged(loc: Location) {
                                        val link = "Live Location (Bat: $battery%): https://maps.google.com/?q=${loc.latitude},${loc.longitude}"
                                        sendSmsReply(context, sender, link, subId)
                                        lm.removeUpdates(this)
                                        pendingResult.finish()
                                    }
                                    override fun onProviderEnabled(p: String) {}
                                    override fun onProviderDisabled(p: String) {}
                                    @Deprecated("Deprecated in Java")
                                    override fun onStatusChanged(p: String?, s: Int, e: Bundle?) {}
                                }
                                lm.requestLocationUpdates(provider, 0L, 0f, listener, Looper.getMainLooper())
                            } else {
                                sendSmsReply(context, sender, "Phone ka GPS/Location OFF hai! Battery: $battery%", subId)
                            }
                        }
                    }
                }
            }
        }
    }
}
