package com.example.keeper

import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import java.util.Calendar

/*
 * Notifier — the god object for everything alarm/notification. It schedules
 * exact alarms with AlarmManager, builds the notification (with snooze + done
 * actions) and computes the next occurrence of a repeating reminder.
 *
 * Reliability notes: the app declares USE_EXACT_ALARM, so exact alarms are
 * granted at install with no prompt; setExactAndAllowWhileIdle fires even in
 * Doze; BootReceiver re-arms every reminder after a reboot.
 */
object Notifier {
    private const val CHANNEL = "reminders"
    const val ACTION_FIRE = "com.example.keeper.FIRE"
    const val ACTION_SNOOZE = "com.example.keeper.SNOOZE"
    const val ACTION_DONE = "com.example.keeper.DONE"

    private fun nm(ctx: Context) = ctx.getSystemService(NotificationManager::class.java)
    private fun am(ctx: Context) = ctx.getSystemService(AlarmManager::class.java)

    private fun ensureChannel(ctx: Context) {
        if (nm(ctx).getNotificationChannel(CHANNEL) == null) {
            nm(ctx).createNotificationChannel(
                NotificationChannel(CHANNEL, "Reminders", NotificationManager.IMPORTANCE_HIGH)
            )
        }
    }

    /* ----- scheduling ----- */

    /** Arms the exact alarm for one note. No reminder, or a one-time reminder
     *  that already fired, means cancel instead. */
    fun schedule(ctx: Context, note: Note) {
        if (note.reminderAt <= 0L || (note.reminderFired && note.reminderRepeat == "NONE")) {
            cancel(ctx, note.id); return
        }
        am(ctx).setExactAndAllowWhileIdle(
            AlarmManager.RTC_WAKEUP, note.reminderAt, firePending(ctx, note.id)
        )
    }

    fun cancel(ctx: Context, noteId: Long) {
        am(ctx).cancel(firePending(ctx, noteId))
        nm(ctx).cancel(noteId.toInt())
    }

    /** Re-arms every pending reminder — called on boot and on app start. */
    fun rescheduleAll(ctx: Context) {
        Db.init(ctx)
        Db.withReminders().forEach { schedule(ctx, it) }
    }

    /** Next occurrence of a repeating reminder, skipping any already in the past. */
    fun next(at: Long, repeat: String): Long {
        if (repeat == "NONE") return 0L
        val c = Calendar.getInstance().apply { timeInMillis = at }
        fun step() = when (repeat) {
            "DAILY" -> c.add(Calendar.DAY_OF_MONTH, 1)
            "WEEKLY" -> c.add(Calendar.DAY_OF_MONTH, 7)
            "MONTHLY" -> c.add(Calendar.MONTH, 1)
            "YEARLY" -> c.add(Calendar.YEAR, 1)
            else -> {}
        }
        do step() while (c.timeInMillis <= System.currentTimeMillis())
        return c.timeInMillis
    }

    /* ----- notification ----- */

    fun fire(ctx: Context, note: Note) {
        ensureChannel(ctx)
        val text =
            if (note.checklist) note.items.joinToString("\n") {
                (if (it.checked) "☑ " else "☐ ") + it.text
            } else note.body

        val open = PendingIntent.getActivity(
            ctx, code(note.id, 5),
            Intent(ctx, MainActivity::class.java)
                .putExtra("open", note.id)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_IMMUTABLE,
        )

        val n = Notification.Builder(ctx, CHANNEL)
            .setSmallIcon(R.drawable.ic_reminder)
            .setContentTitle(note.title.ifBlank { "Reminder" })
            .setContentText(text)
            .setStyle(Notification.BigTextStyle().bigText(text))
            .setContentIntent(open)
            .setAutoCancel(true)
            .addAction(0, "1 hour", actionPending(ctx, note.id, 1, ACTION_SNOOZE, "LATER"))
            .addAction(0, "Tomorrow", actionPending(ctx, note.id, 2, ACTION_SNOOZE, "TOMORROW"))
            .addAction(0, "Done", actionPending(ctx, note.id, 3, ACTION_DONE, null))
            .build()
        nm(ctx).notify(note.id.toInt(), n)
    }

    /** Resolves a snooze keyword to an absolute trigger time. */
    fun snoozeTime(kind: String?): Long = when (kind) {
        "TOMORROW" -> Calendar.getInstance().apply {
            add(Calendar.DAY_OF_MONTH, 1)
            set(Calendar.HOUR_OF_DAY, 9); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0)
        }.timeInMillis
        else -> System.currentTimeMillis() + 60L * 60L * 1000L   // "1 hour"
    }

    /* ----- pending intents ----- */

    private fun code(noteId: Long, slot: Int) = noteId.toInt() * 10 + slot

    private fun firePending(ctx: Context, noteId: Long) = PendingIntent.getBroadcast(
        ctx, code(noteId, 0),
        Intent(ctx, AlarmReceiver::class.java).setAction(ACTION_FIRE).putExtra("note", noteId),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    private fun actionPending(
        ctx: Context, noteId: Long, slot: Int, action: String, kind: String?,
    ) = PendingIntent.getBroadcast(
        ctx, code(noteId, slot),
        Intent(ctx, AlarmReceiver::class.java)
            .setAction(action).putExtra("note", noteId).putExtra("kind", kind),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )
}

/* Receives the alarm and the notification's snooze/done buttons. */
class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        Db.init(ctx)
        val note = Db.note(intent.getLongExtra("note", 0)) ?: return
        val nm = ctx.getSystemService(NotificationManager::class.java)
        when (intent.action) {
            Notifier.ACTION_FIRE -> {
                Notifier.fire(ctx, note)
                if (note.reminderRepeat != "NONE") {           // re-arm the next cycle
                    note.reminderAt = Notifier.next(note.reminderAt, note.reminderRepeat)
                    Db.save(note)
                    Notifier.schedule(ctx, note)
                } else {                                       // one-time: mark done
                    note.reminderFired = true
                    Db.save(note)
                }
            }
            Notifier.ACTION_SNOOZE -> {
                note.reminderAt = Notifier.snoozeTime(intent.getStringExtra("kind"))
                note.reminderFired = false
                Db.save(note)
                Notifier.schedule(ctx, note)
                nm.cancel(note.id.toInt())
            }
            Notifier.ACTION_DONE -> {
                note.reminderAt = 0
                note.reminderRepeat = "NONE"
                note.reminderFired = false
                Db.save(note)
                Notifier.cancel(ctx, note.id)                  // drop alarm + notification
            }
        }
    }
}

/* Re-arms all reminders after the device reboots. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) Notifier.rescheduleAll(ctx)
    }
}
