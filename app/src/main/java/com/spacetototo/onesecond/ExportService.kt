package com.spacetototo.onesecond

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat

/**
 * 내보내기 중 앱이 백그라운드로 가도 프로세스가 죽지 않게 붙잡아 두는 포그라운드 서비스.
 * 실제 변환은 MainViewModel 의 Transformer 가 하고, 여기서는 알림만 관리한다.
 */
class ExportService : Service() {

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        ensureChannel(this)
        val n = buildNotification(this, "영상을 만드는 중…", 0, ongoing = true)
        ServiceCompat.startForeground(this, NOTIFICATION_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        return START_NOT_STICKY
    }

    companion object {
        private const val CHANNEL_ID = "export"
        private const val NOTIFICATION_ID = 1
        private const val DONE_ID = 2

        fun start(ctx: Context) {
            ContextCompat.startForegroundService(ctx, Intent(ctx, ExportService::class.java))
        }

        fun stop(ctx: Context) {
            ctx.stopService(Intent(ctx, ExportService::class.java))
        }

        fun updateProgress(ctx: Context, percent: Int) {
            val n = buildNotification(ctx, "영상을 만드는 중… $percent%", percent, ongoing = true)
            ctx.getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, n)
        }

        fun notifyDone(ctx: Context, success: Boolean) {
            ensureChannel(ctx)
            val text = if (success) "영상이 완성됐어요! 탭해서 열기" else "내보내기에 실패했어요"
            val n = buildNotification(ctx, text, 100, ongoing = false)
            ctx.getSystemService(NotificationManager::class.java).notify(DONE_ID, n)
        }

        private fun ensureChannel(ctx: Context) {
            val nm = ctx.getSystemService(NotificationManager::class.java)
            if (nm.getNotificationChannel(CHANNEL_ID) == null) {
                nm.createNotificationChannel(
                    NotificationChannel(CHANNEL_ID, "영상 내보내기", NotificationManager.IMPORTANCE_LOW)
                )
            }
        }

        private fun buildNotification(ctx: Context, text: String, percent: Int, ongoing: Boolean): Notification {
            val open = PendingIntent.getActivity(
                ctx, 0,
                Intent(ctx, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
            val b = NotificationCompat.Builder(ctx, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.stat_sys_upload)
                .setContentTitle("1초")
                .setContentText(text)
                .setContentIntent(open)
                .setOngoing(ongoing)
                .setOnlyAlertOnce(true)
                .setAutoCancel(!ongoing)
            if (ongoing) b.setProgress(100, percent, percent == 0)
            return b.build()
        }
    }
}
