package xyz.metiq.audio

import android.Manifest
import android.annotation.SuppressLint
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import xyz.metiq.MainActivity
import xyz.metiq.MetiqApp
import xyz.metiq.R
import xyz.metiq.TinnitusScheduleSlot
import java.util.Calendar

class TinnitusReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED -> {
                val pendingResult = goAsync()
                CoroutineScope(Dispatchers.IO).launch {
                    try {
                        val app = context.applicationContext as? MetiqApp
                        val settings = app?.settings?.flow?.first()
                        if (settings != null) {
                            TinnitusScheduler.syncAlarms(
                                context,
                                settings.tinnitusRemindersEnabled,
                                settings.tinnitusScheduleSlots,
                            )
                        }
                    } finally {
                        pendingResult.finish()
                    }
                }
            }
            ACTION_TINNITUS_SNOOZE -> {
                val slotId = intent.getIntExtra(EXTRA_SLOT_ID, 1)
                val modeId = intent.getStringExtra(EXTRA_MODE_ID) ?: "tinnitus_bimodal"
                val durationMinutes = intent.getIntExtra(EXTRA_DURATION_MIN, 30)
                val snoozeMinutes = intent.getIntExtra(EXTRA_SNOOZE_MINUTES, 15)
                NotificationManagerCompat.from(context).cancel(NOTIFICATION_BASE_ID + slotId)
                TinnitusScheduler.scheduleSnooze(
                    context = context,
                    slotId = slotId,
                    modeId = modeId,
                    durationMinutes = durationMinutes,
                    snoozeMinutes = snoozeMinutes,
                )
            }
            ACTION_TINNITUS_REMINDER -> {
                val slotId = intent.getIntExtra(EXTRA_SLOT_ID, 1)
                val modeId = intent.getStringExtra(EXTRA_MODE_ID) ?: "tinnitus_bimodal"
                val durationMinutes = intent.getIntExtra(EXTRA_DURATION_MIN, 30)
                val isSnooze = intent.getBooleanExtra(EXTRA_IS_SNOOZE, false)
                val hour = intent.getIntExtra(EXTRA_HOUR, 9)
                val minute = intent.getIntExtra(EXTRA_MINUTE, 0)

                TinnitusScheduler.postNotification(
                    context = context,
                    slotId = slotId,
                    modeId = modeId,
                    durationMinutes = durationMinutes,
                )

                // Re-arm for tomorrow if this was a regular daily slot alarm
                if (!isSnooze) {
                    TinnitusScheduler.scheduleDailySlot(
                        context = context,
                        slot = TinnitusScheduleSlot(
                            id = slotId,
                            enabled = true,
                            hour = hour,
                            minute = minute,
                            modeId = modeId,
                            durationMinutes = durationMinutes,
                        ),
                    )
                }
            }
        }
    }

    companion object {
        const val ACTION_TINNITUS_REMINDER = "xyz.metiq.action.TINNITUS_REMINDER"
        const val ACTION_TINNITUS_SNOOZE = "xyz.metiq.action.TINNITUS_SNOOZE"
        const val CHANNEL_ID = "tinnitus_reminders"
        const val NOTIFICATION_BASE_ID = 4100

        const val EXTRA_SLOT_ID = "slot_id"
        const val EXTRA_MODE_ID = "mode_id"
        const val EXTRA_DURATION_MIN = "duration_min"
        const val EXTRA_SNOOZE_MINUTES = "snooze_minutes"
        const val EXTRA_IS_SNOOZE = "is_snooze"
        const val EXTRA_HOUR = "hour"
        const val EXTRA_MINUTE = "minute"
        const val EXTRA_START_TINNITUS_MODE = "start_tinnitus_mode"
        const val EXTRA_START_TINNITUS_DURATION_MIN = "start_tinnitus_duration_min"
    }
}

object TinnitusScheduler {
    fun ensureChannel(context: Context) {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channel = NotificationChannel(
            TinnitusReminderReceiver.CHANNEL_ID,
            context.getString(R.string.tinnitus_reminder_channel_name),
            NotificationManager.IMPORTANCE_HIGH,
        ).apply {
            description = context.getString(R.string.tinnitus_reminder_channel_desc)
            enableVibration(true)
        }
        nm.createNotificationChannel(channel)
    }

    fun syncAlarms(
        context: Context,
        remindersEnabled: Boolean,
        slots: List<TinnitusScheduleSlot>,
    ) {
        ensureChannel(context)
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        slots.forEach { slot ->
            val pending = buildReminderPendingIntent(context, slot, isSnooze = false)
            am.cancel(pending)
            if (remindersEnabled && slot.enabled) {
                scheduleDailySlot(context, slot)
            }
        }
    }

    fun scheduleDailySlot(context: Context, slot: TinnitusScheduleSlot) {
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val now = System.currentTimeMillis()
        val cal = Calendar.getInstance().apply {
            timeInMillis = now
            set(Calendar.HOUR_OF_DAY, slot.hour.coerceIn(0, 23))
            set(Calendar.MINUTE, slot.minute.coerceIn(0, 59))
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            if (timeInMillis <= now + 5_000L) {
                add(Calendar.DAY_OF_YEAR, 1)
            }
        }
        val pi = buildReminderPendingIntent(context, slot, isSnooze = false)
        setInexactOrExactAlarm(am, cal.timeInMillis, pi)
    }

    fun scheduleSnooze(
        context: Context,
        slotId: Int,
        modeId: String,
        durationMinutes: Int,
        snoozeMinutes: Int,
    ) {
        ensureChannel(context)
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val triggerAt = System.currentTimeMillis() + snoozeMinutes.coerceAtLeast(1) * 60_000L
        val slot = TinnitusScheduleSlot(
            id = slotId,
            enabled = true,
            hour = 9,
            minute = 0,
            modeId = modeId,
            durationMinutes = durationMinutes,
        )
        val pi = buildReminderPendingIntent(context, slot, isSnooze = true)
        setInexactOrExactAlarm(am, triggerAt, pi)
    }

    private fun setInexactOrExactAlarm(am: AlarmManager, triggerAtMillis: Long, pi: PendingIntent) {
        runCatching {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pi)
        }
    }

    private fun buildReminderPendingIntent(
        context: Context,
        slot: TinnitusScheduleSlot,
        isSnooze: Boolean,
    ): PendingIntent {
        val intent = Intent(context, TinnitusReminderReceiver::class.java).apply {
            action = TinnitusReminderReceiver.ACTION_TINNITUS_REMINDER
            putExtra(TinnitusReminderReceiver.EXTRA_SLOT_ID, slot.id)
            putExtra(TinnitusReminderReceiver.EXTRA_MODE_ID, slot.modeId)
            putExtra(TinnitusReminderReceiver.EXTRA_DURATION_MIN, slot.durationMinutes)
            putExtra(TinnitusReminderReceiver.EXTRA_IS_SNOOZE, isSnooze)
            putExtra(TinnitusReminderReceiver.EXTRA_HOUR, slot.hour)
            putExtra(TinnitusReminderReceiver.EXTRA_MINUTE, slot.minute)
        }
        val reqCode = if (isSnooze) 8000 + slot.id else 7000 + slot.id
        return PendingIntent.getBroadcast(
            context,
            reqCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    @SuppressLint("MissingPermission")
    fun postNotification(
        context: Context,
        slotId: Int,
        modeId: String,
        durationMinutes: Int,
    ) {
        ensureChannel(context)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS,
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) return
        }

        val modeTitle = modeLabel(context, modeId)
        val openIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(TinnitusReminderReceiver.EXTRA_START_TINNITUS_MODE, modeId)
            putExtra(TinnitusReminderReceiver.EXTRA_START_TINNITUS_DURATION_MIN, durationMinutes)
        }
        val openPi = PendingIntent.getActivity(
            context,
            9000 + slotId,
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val snooze15Intent = Intent(context, TinnitusReminderReceiver::class.java).apply {
            action = TinnitusReminderReceiver.ACTION_TINNITUS_SNOOZE
            putExtra(TinnitusReminderReceiver.EXTRA_SLOT_ID, slotId)
            putExtra(TinnitusReminderReceiver.EXTRA_MODE_ID, modeId)
            putExtra(TinnitusReminderReceiver.EXTRA_DURATION_MIN, durationMinutes)
            putExtra(TinnitusReminderReceiver.EXTRA_SNOOZE_MINUTES, 15)
        }
        val snooze15Pi = PendingIntent.getBroadcast(
            context,
            9100 + slotId,
            snooze15Intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val snooze60Intent = Intent(context, TinnitusReminderReceiver::class.java).apply {
            action = TinnitusReminderReceiver.ACTION_TINNITUS_SNOOZE
            putExtra(TinnitusReminderReceiver.EXTRA_SLOT_ID, slotId)
            putExtra(TinnitusReminderReceiver.EXTRA_MODE_ID, modeId)
            putExtra(TinnitusReminderReceiver.EXTRA_DURATION_MIN, durationMinutes)
            putExtra(TinnitusReminderReceiver.EXTRA_SNOOZE_MINUTES, 60)
        }
        val snooze60Pi = PendingIntent.getBroadcast(
            context,
            9200 + slotId,
            snooze60Intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val notification = NotificationCompat.Builder(context, TinnitusReminderReceiver.CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_category_tinnitus)
            .setContentTitle(context.getString(R.string.tinnitus_reminder_noti_title, modeTitle))
            .setContentText(context.getString(R.string.tinnitus_reminder_noti_body, durationMinutes))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(openPi)
            .addAction(0, context.getString(R.string.tinnitus_reminder_action_start), openPi)
            .addAction(0, context.getString(R.string.tinnitus_reminder_action_snooze_15), snooze15Pi)
            .addAction(0, context.getString(R.string.tinnitus_reminder_action_snooze_60), snooze60Pi)
            .build()

        try {
            NotificationManagerCompat.from(context).notify(
                TinnitusReminderReceiver.NOTIFICATION_BASE_ID + slotId,
                notification,
            )
        } catch (_: SecurityException) {
            // Permission revoked at runtime
        }
    }

    fun modeLabel(context: Context, modeId: String): String = when (modeId) {
        "tinnitus_am_ri" -> context.getString(R.string.tinnitus_mode_am_ri)
        "tinnitus_bimodal" -> context.getString(R.string.tinnitus_mode_bimodal)
        "tinnitus_notch" -> context.getString(R.string.tinnitus_mode_notch)
        "tinnitus_modulate" -> context.getString(R.string.tinnitus_mode_modulate)
        "tinnitus_sr" -> context.getString(R.string.tinnitus_mode_sr)
        "tinnitus_invert" -> context.getString(R.string.tinnitus_mode_invert)
        else -> context.getString(R.string.tinnitus_mode_match)
    }
}
