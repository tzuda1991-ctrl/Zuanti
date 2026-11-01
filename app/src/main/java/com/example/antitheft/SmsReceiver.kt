package com.example.antitheft

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.app.admin.DevicePolicyManager
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.SurfaceTexture
import android.hardware.camera2.CameraAccessException
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CameraManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.net.Uri
import android.os.BatteryManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.provider.Telephony
import android.speech.tts.TextToSpeech
import android.telephony.SmsManager
import android.telephony.SubscriptionManager
import android.view.Surface
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class SmsReceiver : BroadcastReceiver() {

    companion object {
        private var mediaPlayer: MediaPlayer? = null
        private var vibrator: Vibrator? = null
        private var tts: TextToSpeech? = null

        fun saveLog(context: Context, msg: String) {
            val prefs = context.getSharedPreferences("AntiTheftPrefs", Context.MODE_PRIVATE)
            val time = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
            val oldLogs = prefs.getString("app_logs", "") ?: ""
            val newLogs = "[$time] $msg\n$oldLogs".take(2500)
            prefs.edit().putString("app_logs", newLogs).apply()
        }

        fun speakMessage(context: Context, textToSpeak: String) {
            val am = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
            val maxVol = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
            am.setStreamVolume(AudioManager.STREAM_MUSIC, maxVol, 0)

            tts = TextToSpeech(context.applicationContext) { status ->
                if (status == TextToSpeech.SUCCESS) {
                    tts?.language = Locale("hi", "IN")
                    tts?.speak(textToSpeak, TextToSpeech.QUEUE_FLUSH, null, "ANTITHEFT_TTS")
                }
            }
        }

        @SuppressLint("MissingPermission")
                fun captureIntruderPhoto(context: Context, sender: String, subId: Int) {
            try {
                saveLog(context, "Launching invisible camera activity...")
                val photoIntent = Intent(context, PhotoCaptureActivity::class.java).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                    putExtra("sender", sender)
                    putExtra("subId", subId)
                }
                context.startActivity(photoIntent)
            } catch (e: Exception) {
                saveLog(context, "Camera launch error: ${e.message}")
                sendSmsReply(context, sender, "Camera launch fail: ${e.message}", subId)
            }
        }


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
                tts?.stop()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        @SuppressLint("MissingPermission")
        fun sendSmsReply(context: Context, sender: String, message: String, incomingSubId: Int = -1) {
            val cleanSender = sender.trim()
            try {
                val subManager = context.getSystemService(Context.TELEPHONY_SUBSCRIPTION_SERVICE) as SubscriptionManager
                val activeList = subManager.activeSubscriptionInfoList

                val targetSubId = when {
                    incomingSubId != -1 -> incomingSubId
                    SubscriptionManager.getDefaultSmsSubscriptionId() != SubscriptionManager.INVALID_SUBSCRIPTION_ID ->
                        SubscriptionManager.getDefaultSmsSubscriptionId()
                    !activeList.isNullOrEmpty() -> activeList[0].subscriptionId
                    else -> -1
                }

                val smsManager = if (targetSubId != -1) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        context.getSystemService(SmsManager::class.java).createForSubscriptionId(targetSubId)
                    } else {
                        @Suppress("DEPRECATION")
                        SmsManager.getSmsManagerForSubscriptionId(targetSubId)
                    }
                } else {
                    @Suppress("DEPRECATION")
                    SmsManager.getDefault()
                }

                val sentIntent = PendingIntent.getBroadcast(
                    context, 0, Intent("SMS_SENT_STATUS"),
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
                )

                val parts = smsManager.divideMessage(message)
                if (parts.size > 1) {
                    smsManager.sendMultipartTextMessage(cleanSender, null, parts, null, null)
                } else {
                    smsManager.sendTextMessage(cleanSender, null, message, sentIntent, null)
                }
                saveLog(context, "SMS sent to $cleanSender")
            } catch (e: Exception) {
                saveLog(context, "SMS FAILED: ${e.message}")
            }
        }

        @SuppressLint("MissingPermission")
        fun handleLocate(context: Context, sender: String, subId: Int) {
            val lm = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
            val battery = getBatteryLevel(context)

            val lastLoc = lm.getLastKnownLocation(LocationManager.GPS_PROVIDER)
                ?: lm.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
                ?: lm.getLastKnownLocation(LocationManager.PASSIVE_PROVIDER)

            if (lastLoc != null) {
                val link = "Phone Location (Bat $battery%): maps.google.com/?q=${lastLoc.latitude},${lastLoc.longitude}"
                sendSmsReply(context, sender, link, subId)
            } else {
                val provider = when {
                    lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER) -> LocationManager.NETWORK_PROVIDER
                    lm.isProviderEnabled(LocationManager.GPS_PROVIDER) -> LocationManager.GPS_PROVIDER
                    else -> null
                }

                if (provider != null) {
                    val listener = object : LocationListener {
                        override fun onLocationChanged(loc: Location) {
                            val link = "Live Location (Bat $battery%): maps.google.com/?q=${loc.latitude},${loc.longitude}"
                            sendSmsReply(context, sender, link, subId)
                            lm.removeUpdates(this)
                        }
                        override fun onProviderEnabled(p: String) {}
                        override fun onProviderDisabled(p: String) {}
                        @Deprecated("Deprecated in Java")
                        override fun onStatusChanged(p: String?, s: Int, e: Bundle?) {}
                    }
                    lm.requestLocationUpdates(provider, 0L, 0f, listener, Looper.getMainLooper())
                } else {
                    sendSmsReply(context, sender, "Phone GPS OFF hai! Battery: $battery%", subId)
                }
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
            val subId = bundle?.getInt("subscription", -1)
                ?: bundle?.getInt("android.telephony.extra.SUBSCRIPTION_INDEX", -1) ?: -1
            val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent)
            val prefs = context.getSharedPreferences("AntiTheftPrefs", Context.MODE_PRIVATE)
            val savedPin = prefs.getString("secret_pin", "1234") ?: "1234"
            val savedTrusted = prefs.getString("trusted_number", "") ?: ""

            for (sms in messages) {
                val rawBody = sms.messageBody?.trim() ?: ""
                val upperBody = rawBody.uppercase()
                val sender = sms.originatingAddress ?: continue

                if (!upperBody.contains("#")) continue

                val isPinValid = upperBody.contains(savedPin.uppercase()) ||
                        (savedTrusted.isNotEmpty() && sender.endsWith(savedTrusted.takeLast(10)))

                if (!isPinValid) {
                    saveLog(context, "Blocked command from $sender (Wrong PIN)")
                    continue
                }

                saveLog(context, "Authorized '$rawBody' from $sender")

                when {
                    upperBody.contains("#LOCK") -> {
                        val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
                        val adminName = ComponentName(context, AdminReceiver::class.java)
                        if (dpm.isAdminActive(adminName)) {
                            dpm.lockNow()
                            sendSmsReply(context, sender, "Phone Lock kar diya gaya hai.", subId)
                        }
                    }

                    upperBody.contains("#ALARM") || upperBody.contains("#SIREN") -> {
                        startAlarm(context)
                    }

                    upperBody.contains("#STOP") -> {
                        stopAlarm()
                        sendSmsReply(context, sender, "Alarm band kar diya gaya hai.", subId)
                    }

                    upperBody.contains("#RING") -> {
                        val am = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
                        am.ringerMode = AudioManager.RINGER_MODE_NORMAL
                        val maxRing = am.getStreamMaxVolume(AudioManager.STREAM_RING)
                        am.setStreamVolume(AudioManager.STREAM_RING, maxRing, 0)
                        sendSmsReply(context, sender, "Silent Mode hata kar Normal Full Volume kar diya hai.", subId)
                    }

                    upperBody.contains("#CALLME") -> {
                        try {
                            val callIntent = Intent(Intent.ACTION_CALL).apply {
                                data = Uri.parse("tel:$sender")
                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            }
                            context.startActivity(callIntent)
                        } catch (e: Exception) {
                            saveLog(context, "Call fail: ${e.message}")
                        }
                    }

                    upperBody.contains("#PHOTO") -> {
                        captureIntruderPhoto(context, sender, subId)
                    }

                    upperBody.contains("#SPEAK") -> {
                        val cleanMsg = rawBody.replace(Regex("(?i)#SPEAK"), "").replace(savedPin, "").trim()
                        speakMessage(context, cleanMsg.ifEmpty { "Yeh phone chori ka hai" })
                        sendSmsReply(context, sender, "Speaking message loud...", subId)
                    }

                    upperBody.contains("#STATUS") -> {
                        val battery = getBatteryLevel(context)
                        val am = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
                        val mode = when (am.ringerMode) {
                            AudioManager.RINGER_MODE_SILENT -> "Silent"
                            AudioManager.RINGER_MODE_VIBRATE -> "Vibrate"
                            else -> "Normal"
                        }
                        sendSmsReply(context, sender, "Status - Bat: $battery%, Sound: $mode", subId)
                    }

                    upperBody.contains("#LOCATE") -> {
                        handleLocate(context, sender, subId)
                    }
                }
            }
        }
    }
}
