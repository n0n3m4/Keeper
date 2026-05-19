package com.example.keeper

import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.content.ContextCompat
import java.util.Calendar

/*
 * Notifier — the god object for everything alarm/notification. It schedules
 * alarms with AlarmManager, builds the notification (with snooze + done
 * actions) and computes the next occurrence of a repeating reminder.
 *
 * Reliability notes: the app declares USE_EXACT_ALARM, so exact alarms are
 * granted at install with no prompt. Alarms use setAlarmClock — the alarm-clock
 * class the OS (and aggressive OEMs like MIUI) treat with Clock-app priority and
 * rarely defer. A firing alarm hands off to ReminderService, a short-lived
 * foreground service, so the work survives even when the app was swiped from
 * recents and its process killed. BootReceiver re-arms every reminder after a
 * reboot.
 */
object Notifier {
    private const val CHANNEL = "reminders"
    private const val BG_CHANNEL = "delivery"
    const val BG_NOTIF_ID = 0x7EEE   // transient foreground-service notification
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

    /** Low-importance channel for ReminderService's transient foreground
     *  notification — silent, shows only for the fraction of a second the
     *  service runs while delivering a reminder. */
    private fun ensureBgChannel(ctx: Context) {
        if (nm(ctx).getNotificationChannel(BG_CHANNEL) == null) {
            nm(ctx).createNotificationChannel(
                NotificationChannel(
                    BG_CHANNEL, "Reminder delivery", NotificationManager.IMPORTANCE_LOW,
                )
            )
        }
    }

    /** The transient notification shown while ReminderService is foreground. */
    fun deliveryNotification(ctx: Context): Notification {
        ensureBgChannel(ctx)
        return Notification.Builder(ctx, BG_CHANNEL)
            .setSmallIcon(R.drawable.ic_reminder)
            .setContentTitle("Delivering reminder…")
            .build()
    }

    /* ----- scheduling ----- */

    /** Arms the alarm for one note. No reminder, or a one-time reminder that
     *  already fired, means cancel instead.
     *
     *  Uses setAlarmClock — the alarm-clock class. The OS shows a status-bar
     *  alarm icon, exempts it from Doze, and OEMs rarely defer it (unlike
     *  setExactAndAllowWhileIdle). showPending is the activity opened when the
     *  user taps that icon. */
    fun schedule(ctx: Context, note: Note) {
        if (note.reminderAt <= 0L || (note.reminderFired && note.reminderRepeat == "NONE")) {
            cancel(ctx, note.id); return
        }
        am(ctx).setAlarmClock(
            AlarmManager.AlarmClockInfo(note.reminderAt, showPending(ctx, note.id)),
            firePending(ctx, note.id),
        )
        // A temporary snooze of a repeating reminder rides on its own alarm so
        // the repeat schedule (note.reminderAt) is left untouched.
        if (note.reminderSnoozeAt > 0L) {
            am(ctx).setAlarmClock(
                AlarmManager.AlarmClockInfo(note.reminderSnoozeAt, showPending(ctx, note.id)),
                snoozeFirePending(ctx, note.id),
            )
        } else {
            am(ctx).cancel(snoozeFirePending(ctx, note.id))
        }
    }

    fun cancel(ctx: Context, noteId: Long) {
        am(ctx).cancel(firePending(ctx, noteId))
        am(ctx).cancel(snoozeFirePending(ctx, noteId))
        nm(ctx).cancel(noteId.toInt())
    }

    /** Re-arms every pending reminder — called on boot and on app start. */
    fun rescheduleAll(ctx: Context) {
        Db.init(ctx)
        Db.withReminders().forEach { schedule(ctx, it) }
    }

    /** (Calendar field, amount) for a repeat code; null = non-repeating/unknown.
     *  Legacy codes (DAILY/WEEKLY/...) and custom "EVERY:<n>:<unit>" intervals
     *  both resolve here, so they share one scheduling path. */
    fun repeatStep(repeat: String): Pair<Int, Int>? = when {
        repeat == "DAILY" -> Calendar.DAY_OF_MONTH to 1
        repeat == "WEEKLY" -> Calendar.DAY_OF_MONTH to 7
        repeat == "MONTHLY" -> Calendar.MONTH to 1
        repeat == "YEARLY" -> Calendar.YEAR to 1
        repeat.startsWith("EVERY:") -> runCatching {
            val (_, n, unit) = repeat.split(":")
            when (unit) {
                "DAY" -> Calendar.DAY_OF_MONTH to n.toInt()
                "WEEK" -> Calendar.DAY_OF_MONTH to n.toInt() * 7
                "MONTH" -> Calendar.MONTH to n.toInt()
                "YEAR" -> Calendar.YEAR to n.toInt()
                else -> null
            }
        }.getOrNull()
        else -> null   // NONE or malformed
    }

    /** Next occurrence of a repeating reminder, skipping any already in the past. */
    fun next(at: Long, repeat: String): Long {
        val (field, amount) = repeatStep(repeat) ?: return 0L
        val c = Calendar.getInstance().apply { timeInMillis = at }
        do c.add(field, amount) while (c.timeInMillis <= System.currentTimeMillis())
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

    /** The fire alarm for a temporary snooze — a distinct request code so it
     *  coexists with the repeat alarm; the `snooze` extra tells the receiver to
     *  deliver without advancing the repeat schedule. */
    private fun snoozeFirePending(ctx: Context, noteId: Long) = PendingIntent.getBroadcast(
        ctx, code(noteId, 4),
        Intent(ctx, AlarmReceiver::class.java)
            .setAction(ACTION_FIRE).putExtra("note", noteId).putExtra("snooze", true),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    /** Opens the note — the AlarmClockInfo show-intent for the status-bar icon. */
    private fun showPending(ctx: Context, noteId: Long) = PendingIntent.getActivity(
        ctx, code(noteId, 6),
        Intent(ctx, MainActivity::class.java)
            .putExtra("open", noteId)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
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
        val noteId = intent.getLongExtra("note", 0)
        // A firing alarm hands straight off to a foreground service: this
        // receiver gets only ~10s and its process can be killed mid-work when
        // the app was swiped from recents. The service raises process priority
        // long enough to read the DB and post the notification. (Permitted from
        // the background because a fired setAlarmClock alarm grants a temporary
        // foreground-service-start exemption.)
        if (intent.action == Notifier.ACTION_FIRE) {
            ContextCompat.startForegroundService(
                ctx,
                Intent(ctx, ReminderService::class.java)
                    .putExtra("note", noteId)
                    .putExtra("snooze", intent.getBooleanExtra("snooze", false)),
            )
            return
        }
        // SNOOZE / DONE come from the user tapping a notification action, so the
        // process is already alive and the work is quick — handle inline.
        Db.init(ctx)
        val note = Db.note(noteId) ?: return
        val nm = ctx.getSystemService(NotificationManager::class.java)
        when (intent.action) {
            Notifier.ACTION_SNOOZE -> {
                val snoozeAt = Notifier.snoozeTime(intent.getStringExtra("kind"))
                if (note.reminderRepeat != "NONE") {
                    // Keep the repeat schedule — reminderAt already points at the
                    // next occurrence. Arm a temporary one-time alarm instead,
                    // unless it would land at/after that next repeat (pointless).
                    note.reminderSnoozeAt = if (snoozeAt >= note.reminderAt) 0L else snoozeAt
                } else {
                    // One-time reminder: snooze just moves its single fire time.
                    note.reminderAt = snoozeAt
                    note.reminderFired = false
                }
                Db.save(note)
                Notifier.schedule(ctx, note)
                nm.cancel(note.id.toInt())
            }
            Notifier.ACTION_DONE -> {
                note.reminderAt = 0
                note.reminderRepeat = "NONE"
                note.reminderFired = false
                note.reminderSnoozeAt = 0
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

/*
 * Short-lived foreground service that delivers one fired reminder. AlarmReceiver
 * starts it instead of doing the work itself: a foreground service raises
 * process priority so the work completes even if the app was swiped from
 * recents and the OS would otherwise kill the process. It posts the reminder
 * notification, advances a repeating reminder (or marks a one-time one done),
 * then stops itself — there is no lasting notification.
 */
class ReminderService : Service() {
    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Must go foreground immediately (within 5s of being started).
        val n = Notifier.deliveryNotification(this)
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(
                Notifier.BG_NOTIF_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SHORT_SERVICE,
            )
        } else {
            startForeground(Notifier.BG_NOTIF_ID, n)
        }

        val noteId = intent?.getLongExtra("note", 0L) ?: 0L
        val snooze = intent?.getBooleanExtra("snooze", false) ?: false
        if (noteId > 0L && !recentlyFired(noteId)) {
            Db.init(this)
            Db.note(noteId)?.let { note ->
                Notifier.fire(this, note)
                if (snooze) {                                  // temporary snooze fired
                    note.reminderSnoozeAt = 0                  // consume it; repeat untouched
                    Db.save(note)
                    Notifier.schedule(this, note)
                } else if (note.reminderRepeat != "NONE") {    // re-arm the next cycle
                    note.reminderAt = Notifier.next(note.reminderAt, note.reminderRepeat)
                    Db.save(note)
                    Notifier.schedule(this, note)
                } else {                                       // one-time: mark done
                    note.reminderFired = true
                    Db.save(note)
                }
            }
        }

        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
        return START_NOT_STICKY
    }

    /** Debounce: ignore a repeat fire for the same note within 30s, in case the
     *  alarm broadcast is delivered twice. Repeat intervals are >= 1 day, so a
     *  legitimate fire is never suppressed. */
    private fun recentlyFired(noteId: Long): Boolean {
        val now = System.currentTimeMillis()
        val last = lastFired.put(noteId, now)
        return last != null && now - last < 30_000L
    }

    companion object {
        private val lastFired = HashMap<Long, Long>()
    }
}
