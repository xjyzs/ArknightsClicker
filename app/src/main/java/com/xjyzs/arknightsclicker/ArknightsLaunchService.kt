package com.xjyzs.arknightsclicker

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.IBinder
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader
import java.lang.Thread.sleep

class ArknightsLaunchService : Service() {

    private val serviceJob = SupervisorJob()
    private val serviceScope = CoroutineScope(Dispatchers.IO+serviceJob)
    override fun onCreate() {
        super.onCreate()
        val channel = NotificationChannel(
            "launch",
            "明日方舟启动提醒",
            NotificationManager.IMPORTANCE_LOW
        ).apply{}
        val notificationManager =
            getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.createNotificationChannel(channel)
        val notification = NotificationCompat.Builder(this, "launch")
            .setContentTitle("明日方舟启动中")
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .build()
        startForeground(1001, notification)
    }
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        var off=false
        serviceScope.launch {
            try {
                val process = ProcessBuilder("su").apply {
                    redirectErrorStream(true)
                }.start()
                val outputStream = process.outputStream
                BufferedReader(InputStreamReader(process.inputStream))
                outputStream.write("am start com.hypergryph.arknights/com.u8.sdk.U8UnityContext\n".toByteArray())
                outputStream.flush()
                for (i in 1..300) {
                    if (off)break
                    outputStream.write("input tap ${intent!!.getIntExtra("width",1920)/2} ${intent.getIntExtra("height",1080)*0.7}\n".toByteArray())
                    outputStream.flush()
                    sleep(100)
                }
            } catch (_: Exception) {
            }
        }
        serviceScope.launch {
            try {
                val process = ProcessBuilder("su").apply {
                    redirectErrorStream(true)
                }.start()
                val outputStream = process.outputStream
                val reader = BufferedReader(InputStreamReader(process.inputStream))
                outputStream.write("getevent\n".toByteArray())
                outputStream.flush()
                while (true) {
                    val line = reader.readLine() ?: break
                    if (line.contains("0036")) {
                        withContext(Dispatchers.Main) {
                            off=true
                            stopForeground(STOP_FOREGROUND_REMOVE)
                            stopSelf()
                        }
                        break
                    }
                }
            } catch (_: Exception) {
            }
        }
        return START_NOT_STICKY
    }

    override fun onBind(p0: Intent?): IBinder? {
        return null
    }
}