package com.xjyzs.arknightsclicker

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.media.AudioManager
import android.media.MediaPlayer
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.io.BufferedReader
import java.io.InputStreamReader
import java.lang.Thread.sleep
import kotlin.concurrent.thread

class ClickerService : Service() {
    private val serviceJob = SupervisorJob()
    private val serviceScope = CoroutineScope(Dispatchers.IO + serviceJob)
    private lateinit var wakeLock: PowerManager.WakeLock
    private lateinit var mediaPlayer: MediaPlayer
    override fun onCreate() {
        super.onCreate()

        try {
            mediaPlayer = MediaPlayer.create(this, R.raw.kpalv) // keep alive
            val audioManager = getSystemService(AUDIO_SERVICE) as AudioManager
            val focusChangeListener = AudioManager.OnAudioFocusChangeListener { focusChange ->
                when (focusChange) {
                    AudioManager.AUDIOFOCUS_LOSS, AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> {
                        mediaPlayer.start()
                    }
                }
            }
            audioManager.requestAudioFocus(
                focusChangeListener,
                AudioManager.STREAM_MUSIC,
                AudioManager.AUDIOFOCUS_GAIN
            )
            mediaPlayer.apply {
                isLooping = true
                setVolume(0.01f, 0.01f)
                start()
            }

        } catch (e: Exception) {
            e.printStackTrace()
        }


        val powerManager = getSystemService(POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "Clicker::KeepAlive")
        wakeLock.acquire()
        val channel = NotificationChannel(
            "clicker", "明日方舟点击提醒", NotificationManager.IMPORTANCE_LOW
        ).apply {}
        val notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.createNotificationChannel(channel)
        val notification =
            NotificationCompat.Builder(this, "clicker").setContentTitle("已启动暂停部署")
                .setSmallIcon(R.drawable.ic_launcher).build()
        startForeground(1002, notification)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val height = intent!!.getIntExtra("height", 1080)
        val ratio90 = intent.getFloatExtra("ratio90", 0.165f)
        val ratio270 = 1 - ratio90
        var below = false
        var screenReversed = false
        serviceScope.launch {
            var arknightsFocused = false
            var actualThreshold = 0
            thread {
                try {
                    val process = ProcessBuilder("su").apply {
                        redirectErrorStream(true)
                    }.start()
                    val outputStream = process.outputStream
                    val p = Runtime.getRuntime().exec("su -c getevent")
                    val reader = BufferedReader(InputStreamReader(p.inputStream))
                    while (true) {
                        val line = reader.readLine() ?: break
                        if (arknightsFocused) {
                            if (" 0035 " in line) { // 触屏
                                val tmp = line.substring(30, 37).toInt(16)
                                if (!below && (screenReversed && tmp < actualThreshold || !screenReversed && tmp > actualThreshold)) {
                                    below = true
                                } else if (below && (screenReversed && tmp > actualThreshold || !screenReversed && tmp < actualThreshold)) {
                                    outputStream.write("input keyevent 4\n".toByteArray())
                                    outputStream.flush()
                                    while (true) {
                                        if ((reader.readLine()
                                                ?: break).contains("014a 00000000") // 松手
                                        ) {
                                            below = false
                                            break
                                        }
                                    }
                                }
                            } else if ("014a 00000000" in line) {
                                below = false
                            }
                        }
                    }
                } catch (_: Exception) {
                }
            }
            thread {
                try {
                    val process = ProcessBuilder("su").apply {
                        redirectErrorStream(true)
                    }.start()
                    val outputStream = process.outputStream
                    val reader = BufferedReader(InputStreamReader(process.inputStream))
                    while (true) {
                        outputStream.write("dumpsys window | grep -E 'mCurrentFocus|mDisplayRotation';echo 'END_OF_CMD'\n".toByteArray())
                        outputStream.flush()
                        val result = StringBuilder()
                        var line: String?
                        while (true) {
                            line = reader.readLine() ?: break
                            if (line.contains("END_OF_CMD")) break
                            result.appendLine(line)
                        }
                        val tmp = result.toString()
                        if ("com.u8.sdk.U8UnityContext" in tmp) arknightsFocused =
                            true else arknightsFocused = false
                        val re = Regex(""" mDisplayRotation=(?<rotation>.*?) """)
                        val match = re.find(tmp)
                        if ("270" in (match?.groups["rotation"]?.value ?: "90")) {
                            actualThreshold = (height * ratio270).toInt()
                            screenReversed = false
                        } else {
                            screenReversed = true
                            actualThreshold = (height * ratio90).toInt()
                        }
                        sleep(1000)
                    }
                } catch (_: Exception) {
                }
            }
        }
        return START_NOT_STICKY
    }

    override fun onBind(p0: Intent?): IBinder? {
        return null
    }

    override fun onDestroy() {
        mediaPlayer.stop()
        mediaPlayer.release()
        if (wakeLock.isHeld) {
            wakeLock.release()
        }
        super.onDestroy()
    }
}