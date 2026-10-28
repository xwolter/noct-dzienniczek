package pl.wolter.usos.work

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import pl.wolter.usos.MainActivity
import pl.wolter.usos.R
import pl.wolter.usos.data.Grades
import pl.wolter.usos.data.StudentData

object Notifier {
    const val CH_CLASSES = "classes"
    const val CH_RESULTS = "results"

    fun createChannels(ctx: Context) {
        val nm = ctx.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(CH_CLASSES, "Przypomnienia o zajęciach", NotificationManager.IMPORTANCE_HIGH))
        nm.createNotificationChannel(NotificationChannel(CH_RESULTS, "Nowe wyniki", NotificationManager.IMPORTANCE_DEFAULT))
    }

    private fun allowed(ctx: Context) = android.os.Build.VERSION.SDK_INT < 33 ||
        ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    fun openApp(ctx: Context, extra: String? = null): PendingIntent =
        PendingIntent.getActivity(ctx, extra.hashCode(), Intent(ctx, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            extra?.let { putExtra("open", it) }
        }, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)

    fun show(ctx: Context, id: Int, channel: String, title: String, text: String, open: String? = null) {
        if (!allowed(ctx)) return
        val n = NotificationCompat.Builder(ctx, channel)
            .setSmallIcon(R.drawable.ic_stat)
            .setColor(0xFF2F6BFF.toInt())
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setAutoCancel(true)
            .setContentIntent(openApp(ctx, open))
            .build()
        runCatching { NotificationManagerCompat.from(ctx).notify(id, n) }
    }

    /** Porównuje punkty przed i po synchronizacji - powiadamia o każdym nowym wyniku. */
    fun notifyNewResults(ctx: Context, old: StudentData, new: StudentData) {
        new.courses.forEach { c ->
            val oc = old.courses.firstOrNull { it.id == c.id } ?: return@forEach
            c.tests.forEach { t ->
                val ot = oc.tests.firstOrNull { it.name == t.name }
                t.items.forEachIndexed { i, it ->
                    val before = ot?.items?.getOrNull(i)?.points
                    if (it.points != null && it.points != before) {
                        show(ctx, (c.id + t.name + i).hashCode(), CH_RESULTS, "Nowy wynik · ${c.name}",
                            "${t.name} · ${it.name}: ${Grades.fmtPts(it.points)}/${Grades.fmtPts(it.max)} pkt", "course:${c.id}")
                    }
                }
            }
        }
    }
}
