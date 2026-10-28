package pl.wolter.usos.work

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import pl.wolter.usos.UsosApp
import pl.wolter.usos.data.StudentData
import pl.wolter.usos.data.Time
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

/** Przypomnienie 15 min przed zajęciami - z salą i budynkiem w treści. */
object Reminders {
    private fun pi(ctx: Context, req: Int, title: String = "", text: String = "", flags: Int = PendingIntent.FLAG_UPDATE_CURRENT) =
        PendingIntent.getBroadcast(ctx, req, Intent(ctx, ReminderReceiver::class.java).apply {
            putExtra("title", title); putExtra("text", text); putExtra("id", req); putExtra("silent", req >= 3000)
        }, flags or PendingIntent.FLAG_IMMUTABLE)

    /** Co minutę w trakcie zajęć (i godzinę przed) - przesuwa pasek czasu w widżecie „Teraz”. */
    fun minuteTick(ctx: Context, on: Boolean) {
        val am = ctx.getSystemService(AlarmManager::class.java)
        val p = pi(ctx, 3999)
        if (on) am.setWindow(AlarmManager.RTC, System.currentTimeMillis() + 60_000, 15_000, p) else am.cancel(p)
    }

    /** Alarmy na początek/koniec zajęć - widżet „Teraz” przełącza się dokładnie wtedy. */
    fun scheduleWidgetTicks(ctx: Context, d: StudentData) {
        val am = ctx.getSystemService(AlarmManager::class.java)
        val now = LocalDateTime.now()
        val times = d.events.flatMap { e -> listOf(e.start, e.end).map { Time.date(e.date).atTime(LocalTime.parse(it)) } }
            .filter { it.isAfter(now) }.distinct().sorted().take(40)
        for (i in 0 until 40) pi(ctx, 3000 + i, flags = PendingIntent.FLAG_NO_CREATE)?.let { am.cancel(it) }
        times.forEachIndexed { i, t ->
            am.setAndAllowWhileIdle(AlarmManager.RTC, t.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli(), pi(ctx, 3000 + i))
        }
    }

    fun schedule(ctx: Context, d: StudentData) {
        cancelAll(ctx)
        val am = ctx.getSystemService(AlarmManager::class.java)
        val now = LocalDateTime.now()
        val hidden = UsosApp.repo.settings.value.hidden
        val ids = mutableSetOf<Int>()
        d.events.filter { it.type !in hidden }.take(40).forEach { e ->
            val at = Time.date(e.date).atTime(LocalTime.parse(e.start)).minusMinutes(15)
            if (at.isBefore(now)) return@forEach
            val req = 1000 + ids.size
            ids += req
            val text = "${e.start}-${e.end} · s. ${e.room}${if (e.building.isNotBlank()) " · ${e.building}" else ""}"
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli(),
                pi(ctx, req, "Za 15 min · ${e.courseName}", "${e.typeLabel} · $text"))
        }
        // własne terminy: wieczór przed (19:00) i w dniu terminu (2 h przed albo 7:30)
        pl.wolter.usos.data.Derive.upcomingExams(d, UsosApp.repo.settings.value, UsosApp.repo.groupExams.value).take(20).forEachIndexed { i, e ->
            val date = Time.date(e.date)
            val what = "${e.kind.ifBlank { "Termin" }}: ${e.name.substringBefore(" · ")}"
            val sub = e.meta.ifBlank { "Powodzenia!" }
            listOf(
                date.minusDays(1).atTime(19, 0) to "Jutro · $what",
                (if (e.time.isNotBlank()) date.atTime(LocalTime.parse(e.time)).minusHours(2) else date.atTime(7, 30)) to "Dziś · $what",
            ).forEachIndexed { k, (at, title) ->
                if (at.isAfter(now)) am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli(),
                    pi(ctx, 2000 + i * 2 + k, title, sub))
            }
        }
        ctx.getSharedPreferences("usos", 0).edit().putInt("alarms", ids.size).apply()
    }

    fun cancelAll(ctx: Context) {
        val am = ctx.getSystemService(AlarmManager::class.java)
        val n = ctx.getSharedPreferences("usos", 0).getInt("alarms", 0)
        for (i in 0 until maxOf(n, 40)) pi(ctx, 1000 + i, flags = PendingIntent.FLAG_NO_CREATE)?.let { am.cancel(it) }
        for (i in 0 until 40) pi(ctx, 2000 + i, flags = PendingIntent.FLAG_NO_CREATE)?.let { am.cancel(it) }
    }
}

class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, i: Intent) {
        pl.wolter.usos.widget.Widgets.updateAll(ctx)
        if (i.getBooleanExtra("silent", false)) return
        Notifier.show(ctx, i.getIntExtra("id", 0), Notifier.CH_CLASSES, i.getStringExtra("title") ?: "", i.getStringExtra("text") ?: "", "plan")
    }
}

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, i: Intent) {
        val repo = UsosApp.repo
        repo.data.value?.let {
            if (repo.settings.value.notif) Reminders.schedule(ctx, it)
            Reminders.scheduleWidgetTicks(ctx, it)
        }
    }
}
