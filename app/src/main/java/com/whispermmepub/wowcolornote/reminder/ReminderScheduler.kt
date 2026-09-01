package com.whispermmepub.wowcolornote.reminder

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent

object ReminderScheduler {
    fun schedule(context: Context, noteId: Long, atMillis: Long, title: String) {
        if (noteId <= 0L || atMillis <= System.currentTimeMillis()) return
        val alarm = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(context, ReminderReceiver::class.java)
            .putExtra("note_id", noteId)
            .putExtra("title", title)
        val pi = PendingIntent.getBroadcast(
            context,
            noteId.toInt(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        runCatching { alarm.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, atMillis, pi) }
            .recoverCatching { alarm.set(AlarmManager.RTC_WAKEUP, atMillis, pi) }
    }

    fun cancel(context: Context, noteId: Long) {
        val alarm = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val pi = PendingIntent.getBroadcast(
            context,
            noteId.toInt(),
            Intent(context, ReminderReceiver::class.java),
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        ) ?: return
        alarm.cancel(pi)
        pi.cancel()
    }
}
