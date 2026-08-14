package com.deepseek.harness

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import androidx.core.app.NotificationCompat
import java.io.File

class DshService : Service() {

    companion object {
        private const val CHANNEL_ID = "dsh_running"
        private const val NOTIF_ID = 1
        const val URL = "http://127.0.0.1:3080"

        var process: Process? = null
            private set

        fun isRunning(): Boolean = process?.isAlive == true

        fun stopProcess() {
            try {
                process?.destroy()
            } catch (_: Throwable) {}
            process = null
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createChannel()
        startForeground(
            NOTIF_ID,
            NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle(getString(R.string.notif_title))
                .setSmallIcon(R.drawable.ic_stat_whale)
                .setOngoing(true)
                .build()
        )
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (process?.isAlive != true) {
            Thread { launchDsh() }.start()
        }
        return START_STICKY
    }

    private fun launchDsh() {
        try {
            val filesDir = filesDir.absolutePath
            val rootfs = File(filesDir, "rootfs")
            val prootBin = File(filesDir, "proot")
            if (!prootBin.exists() || !rootfs.exists()) return

            val logFile = File(filesDir, "dsh.log")
            val cmd = mutableListOf(
                prootBin.absolutePath,
                "-0",
                "-r", rootfs.absolutePath,
                "-b", "/dev",
                "-b", "/proc",
                "-b", "/sys",
                "-b", "/storage/emulated/0:/sdcard",
                "-w", "/root",
                "--kill-on-exit",
                "/usr/bin/env",
                "-i",
                "HOME=/root",
                "DSH_HOME=/root/.dsh",
                "PATH=/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin:/opt/node/bin",
                "LANG=C.UTF-8",
                "/bin/bash",
                "/opt/dsh/entry.sh"
            )
            process = ProcessBuilder(cmd)
                .redirectErrorStream(true)
                .redirectOutput(logFile)
                .start()
        } catch (e: Throwable) {
            e.printStackTrace()
        }
    }

    override fun onDestroy() {
        stopProcess()
        super.onDestroy()
    }

    private fun createChannel() {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, getString(R.string.notif_channel), NotificationManager.IMPORTANCE_LOW)
        )
    }
}
