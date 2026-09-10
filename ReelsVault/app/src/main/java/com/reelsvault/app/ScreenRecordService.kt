package com.reelsvault.app

import android.app.*
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.MediaRecorder
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.DisplayMetrics
import android.view.WindowManager
import androidx.core.app.NotificationCompat
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

/**
 * Records the whole device screen (whatever app is in front - Instagram, YouTube, etc.)
 * for a fixed duration the user picked, then stops itself and saves an mp4 into the
 * app's own "sessions" folder, ready to show up in the reels feed.
 */
class ScreenRecordService : Service() {

    private var mediaProjection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var mediaRecorder: MediaRecorder? = null
    private val stopHandler = Handler(Looper.getMainLooper())
    private var outputFile: File? = null

    companion object {
        const val CHANNEL_ID = "recording_channel"
        const val NOTIF_ID = 42
        const val EXTRA_RESULT_CODE = "resultCode"
        const val EXTRA_RESULT_DATA = "resultData"
        const val EXTRA_DURATION_MINUTES = "durationMinutes"
        var isRecording = false
            private set

        fun sessionsDir(context: Context): File {
            val dir = File(context.getExternalFilesDir(null), "sessions")
            if (!dir.exists()) dir.mkdirs()
            return dir
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val resultCode = intent?.getIntExtra(EXTRA_RESULT_CODE, 0) ?: 0
        val resultData = intent?.getParcelableExtra<Intent>(EXTRA_RESULT_DATA)
        val minutes = intent?.getIntExtra(EXTRA_DURATION_MINUTES, 15) ?: 15

        if (resultData == null) {
            stopSelf()
            return START_NOT_STICKY
        }

        startForegroundNotification(minutes)
        startRecording(resultCode, resultData, minutes)
        return START_NOT_STICKY
    }

    private fun startForegroundNotification(minutes: Int) {
        val nm = getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID, "Screen recording", NotificationManager.IMPORTANCE_LOW
            )
            nm.createNotificationChannel(channel)
        }
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Recording your session")
            .setContentText("Stops automatically in $minutes min. Scroll away.")
            .setSmallIcon(android.R.drawable.ic_menu_camera)
            .setOngoing(true)
            .build()

        if (Build.VERSION.SDK_INT >= 29) {
            startForeground(
                NOTIF_ID, notification,
                android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION
            )
        } else {
            startForeground(NOTIF_ID, notification)
        }
    }

    private fun startRecording(resultCode: Int, resultData: Intent, minutes: Int) {
        val mpManager = getSystemService(MediaProjectionManager::class.java)
        mediaProjection = mpManager.getMediaProjection(resultCode, resultData)

        val wm = getSystemService(WindowManager::class.java)
        val metrics = DisplayMetrics()
        @Suppress("DEPRECATION")
        wm.defaultDisplay.getRealMetrics(metrics)
        val width = metrics.widthPixels
        val height = metrics.heightPixels
        val density = metrics.densityDpi

        val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val file = File(sessionsDir(this), "session_$stamp.mp4")
        outputFile = file

        val recorder = MediaRecorder()
        recorder.setVideoSource(MediaRecorder.VideoSource.SURFACE)
        recorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
        recorder.setVideoEncoder(MediaRecorder.VideoEncoder.H264)
        recorder.setVideoSize(width, height)
        recorder.setVideoEncodingBitRate(8_000_000)
        recorder.setVideoFrameRate(30)
        recorder.setOutputFile(file.absolutePath)
        recorder.prepare()
        mediaRecorder = recorder

        virtualDisplay = mediaProjection?.createVirtualDisplay(
            "ReelsVaultCapture",
            width, height, density,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            recorder.surface, null, null
        )

        recorder.start()
        isRecording = true

        stopHandler.postDelayed({ stopRecording() }, minutes * 60_000L)
    }

    private fun stopRecording() {
        try {
            mediaRecorder?.stop()
            mediaRecorder?.reset()
        } catch (_: Exception) {
            // If stopped too soon after start, MediaRecorder can throw - the file
            // may still be a short valid clip, so we don't crash the service over it.
        }
        mediaRecorder?.release()
        virtualDisplay?.release()
        mediaProjection?.stop()
        isRecording = false
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        super.onDestroy()
        if (isRecording) stopRecording()
    }
}
