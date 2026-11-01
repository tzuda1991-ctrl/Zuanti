package com.example.antitheft

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.graphics.ImageFormat
import android.graphics.SurfaceTexture
import android.hardware.camera2.*
import android.media.ImageReader
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Surface
import android.view.WindowManager
import java.io.DataOutputStream
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL

class PhotoCaptureActivity : Activity() {
    private var cameraDevice: CameraDevice? = null
    private var imageReader: ImageReader? = null
    private var dummyTexture: SurfaceTexture? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
            )
        }

        val sender = intent.getStringExtra("sender") ?: ""
        val subId = intent.getIntExtra("subId", -1)

        // 500ms delay so Android brings activity to foreground and unlocks camera policy
        Handler(Looper.getMainLooper()).postDelayed({
            takeSilentPhoto(sender, subId)
        }, 500)
    }

    @SuppressLint("MissingPermission")
    private fun takeSilentPhoto(sender: String, subId: Int) {
        val manager = getSystemService(Context.CAMERA_SERVICE) as CameraManager
        try {
            val cameraId = manager.cameraIdList.firstOrNull {
                manager.getCameraCharacteristics(it).get(CameraCharacteristics.LENS_FACING) == CameraCharacteristics.LENS_FACING_FRONT
            } ?: manager.cameraIdList[0]

            imageReader = ImageReader.newInstance(640, 480, ImageFormat.JPEG, 2)
            imageReader?.setOnImageAvailableListener({ reader ->
                val image = reader.acquireLatestImage() ?: return@setOnImageAvailableListener
                val buffer = image.planes[0].buffer
                val bytes = ByteArray(buffer.remaining())
                buffer.get(bytes)
                image.close()

                val file = File(getExternalFilesDir(null), "intruder_${System.currentTimeMillis()}.jpg")
                FileOutputStream(file).use { it.write(bytes) }

                SmsReceiver.saveLog(this, "Photo saved: ${file.name}. Uploading...")
                uploadAndSendLink(file, sender, subId)
                closeCameraAndFinish()
            }, Handler(Looper.getMainLooper()))

            manager.openCamera(cameraId, object : CameraDevice.StateCallback() {
                override fun onOpened(camera: CameraDevice) {
                    cameraDevice = camera
                    createCaptureSession(camera, sender, subId)
                }
                override fun onDisconnected(camera: CameraDevice) {
                    closeCameraAndFinish()
                }
                override fun onError(camera: CameraDevice, error: Int) {
                    SmsReceiver.sendSmsReply(this@PhotoCaptureActivity, sender, "Camera Error Code: $error", subId)
                    closeCameraAndFinish()
                }
            }, Handler(Looper.getMainLooper()))

        } catch (e: Exception) {
            SmsReceiver.sendSmsReply(this, sender, "Photo error: ${e.message}", subId)
            closeCameraAndFinish()
        }
    }

    private fun createCaptureSession(camera: CameraDevice, sender: String, subId: Int) {
        try {
            dummyTexture = SurfaceTexture(10)
            dummyTexture?.setDefaultBufferSize(640, 480)
            val previewSurface = Surface(dummyTexture)
            val captureSurface = imageReader!!.surface

            @Suppress("DEPRECATION")
            camera.createCaptureSession(listOf(previewSurface, captureSurface), object : CameraCaptureSession.StateCallback() {
                override fun onConfigured(session: CameraCaptureSession) {
                    try {
                        val previewReq = camera.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW).apply {
                            addTarget(previewSurface)
                        }
                        session.setRepeatingRequest(previewReq.build(), null, null)

                        // Wait 800ms for front camera auto-exposure, then snap JPEG
                        Handler(Looper.getMainLooper()).postDelayed({
                            try {
                                val captureReq = camera.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE).apply {
                                    addTarget(captureSurface)
                                    set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE)
                                    set(CaptureRequest.JPEG_ORIENTATION, 270)
                                }
                                session.capture(captureReq.build(), null, null)
                            } catch (e: Exception) {
                                SmsReceiver.sendSmsReply(this@PhotoCaptureActivity, sender, "Snap fail: ${e.message}", subId)
                                closeCameraAndFinish()
                            }
                        }, 800)
                    } catch (e: Exception) {
                        closeCameraAndFinish()
                    }
                }
                override fun onConfigureFailed(session: CameraCaptureSession) {
                    closeCameraAndFinish()
                }
            }, Handler(Looper.getMainLooper()))
        } catch (e: Exception) {
            closeCameraAndFinish()
        }
    }

    private fun uploadAndSendLink(file: File, sender: String, subId: Int) {
        Thread {
            try {
                val boundary = "Boundary-${System.currentTimeMillis()}"
                val url = URL("https://0x0.st")
                val conn = (url.openConnection() as HttpURLConnection).apply {
                    doOutput = true
                    requestMethod = "POST"
                    setRequestProperty("User-Agent", "curl/7.68.0")
                    setRequestProperty("Content-Type", "multipart/form-data; boundary=$boundary")
                    connectTimeout = 10000
                }

                DataOutputStream(conn.outputStream).use { out ->
                    out.writeBytes("--$boundary\r\n")
                    out.writeBytes("Content-Disposition: form-data; name=\"file\"; filename=\"${file.name}\"\r\n")
                    out.writeBytes("Content-Type: image/jpeg\r\n\r\n")
                    file.inputStream().use { it.copyTo(out) }
                    out.writeBytes("\r\n--$boundary--\r\n")
                }

                if (conn.responseCode == 200) {
                    val rawLink = conn.inputStream.bufferedReader().readText().trim()
                    val cleanLink = rawLink.replace("https://", "")
                    SmsReceiver.sendSmsReply(applicationContext, sender, "Intruder Photo Captured! View here: $cleanLink", subId)
                } else {
                    SmsReceiver.sendSmsReply(applicationContext, sender, "Photo phone me save ho gayi hai (${file.name}), upload code: ${conn.responseCode}", subId)
                }
            } catch (e: Exception) {
                SmsReceiver.sendSmsReply(applicationContext, sender, "Photo phone me save ho gayi hai (${file.name}) par internet band hai.", subId)
            }
        }.start()
    }

    private fun closeCameraAndFinish() {
        try {
            cameraDevice?.close()
            cameraDevice = null
            imageReader?.close()
            dummyTexture?.release()
        } catch (_: Exception) {}
        finish()
    }
}
