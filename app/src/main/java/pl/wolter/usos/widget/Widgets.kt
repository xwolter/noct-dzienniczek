package pl.wolter.usos.widget

import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.TextUnitType
import androidx.compose.ui.unit.dp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.LocalSize
import androidx.glance.action.Action
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.LinearProgressIndicator
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.updateAll
import androidx.glance.background
import androidx.glance.color.ColorProvider
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxHeight
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider as Fixed
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import pl.wolter.usos.MainActivity
import pl.wolter.usos.UsosApp
import pl.wolter.usos.data.ClassEvent
import pl.wolter.usos.data.Derive
import pl.wolter.usos.data.Grades
import pl.wolter.usos.data.StudentData
import pl.wolter.usos.data.Time
import java.time.LocalDate

object Widgets {
    /** Każda zmiana „tyka” - wymusza przeliczenie widżetów (np. czasu „teraz”). */
    val tick = MutableStateFlow(0L)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    fun updateAll(ctx: Context) {
        tick.value = System.currentTimeMillis()
        scope.launch {
            runCatching {
                NowWidget().updateAll(ctx); WeekWidget().updateAll(ctx); ResultWidget().updateAll(ctx)
                ExamWidget().updateAll(ctx); ForecastWidget().updateAll(ctx)
                val hasNow = androidx.glance.appwidget.GlanceAppWidgetManager(ctx).getGlanceIds(NowWidget::class.java).isNotEmpty()
                val repo = UsosApp.repo
                val n = repo.data.value?.let { Derive.nowInfo(it, repo.settings.value) }
                pl.wolter.usos.work.Reminders.minuteTick(ctx, hasNow && n != null && timeWindow(n.event, n.live) != null)
            }
        }
    }
}

// ---------- kolory (dzień / noc) ----------
private val CARD = ColorProvider(Color(0xF5FFFFFF), Color(0xF517191E))
private val INK = ColorProvider(Color(0xFF111318), Color(0xFFF3F4F6))
private val TEXT2 = ColorProvider(Color(0xFF4A4E58), Color(0xFFC3C7CF))
private val SUB = ColorProvider(Color(0xFF6B6F7A), Color(0xFF9A9EA8))
private val LINE = ColorProvider(Color(0xFFECEEF2), Color(0xFF2A2D35))
private val TRACK = ColorProvider(Color(0xFFE4E7EE), Color(0xFF2A2D35))
private val BLUE = ColorProvider(Color(0xFF2F6BFF), Color(0xFF5B8CFF))
private val GREEN = ColorProvider(Color(0xFF1E9E57), Color(0xFF3CC177))
private val TODAY_COL = ColorProvider(Color(0xFFE8EEFF), Color(0xFF1E2740))
private val OTHER_COL = ColorProvider(Color(0xFFF2F3F5), Color(0xFF22252C))
private val RED = Fixed(Color(0xFFE04B5A))
private val WHITE = Fixed(Color.White)
private val WHITE85 = Fixed(Color(0xD9FFFFFF))

private fun sp(v: Int) = TextUnit(v.toFloat(), TextUnitType.Sp)
private fun st(size: Int, color: androidx.glance.unit.ColorProvider, bold: Boolean = false, medium: Boolean = false) =
    TextStyle(color = color, fontSize = sp(size), fontWeight = if (bold) FontWeight.Bold else if (medium) FontWeight.Medium else FontWeight.Normal)

private fun open(ctx: Context, target: String?): Action =
    actionStartActivity(Intent(ctx, MainActivity::class.java).apply {
        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        target?.let { putExtra("open", it) }
    })

@Composable
private fun Shell(ctx: Context, target: String?, bg: androidx.glance.unit.ColorProvider = CARD, pad: Dp = 18.dp, content: @Composable () -> Unit) {
    Box(
        GlanceModifier.fillMaxSize().cornerRadius(28.dp).background(bg).clickable(open(ctx, target)).padding(pad),
    ) { content() }
}

@Composable
private fun data(): StudentData? {
    val d by UsosApp.repo.data.collectAsState()
    Widgets.tick.collectAsState().value
    return d
}

@Composable
private fun Empty(ctx: Context) {
    Shell(ctx, null) {
        Column(GlanceModifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
            Text("NOCT DZIENNICZEK", style = st(12, BLUE, bold = true))
            Text("Otwórz aplikację i zaloguj się", style = st(13, TEXT2))
        }
    }
}

// ---------- 1. Teraz ----------
private fun shortLecturer(d: StudentData, e: ClassEvent): String =
    e.lecturerIds.firstNotNullOfOrNull { d.lecturers[it]?.name }.orEmpty()

private fun countdown(e: ClassEvent): String {
    val start = Time.date(e.date).atTime(java.time.LocalTime.parse(e.start))
    val m = java.time.Duration.between(java.time.LocalDateTime.now(), start).toMinutes()
    return when {
        m < 60 -> "za $m min"
        m < 24 * 60 -> "za ${m / 60} h${if (m % 60 != 0L) " ${m % 60} min" else ""}"
        else -> "${Time.DOW_SHORT[Time.date(e.date).dayOfWeek.value - 1]} ${Time.dayMonth(Time.date(e.date))}"
    }
}

/** Mała pigułka z typem zajęć w kolorze typu. */
@Composable
private fun TypeChip(e: ClassEvent) {
    val c = typeColor(e.type)
    Box(
        GlanceModifier.cornerRadius(99.dp).background(ColorProvider(c.copy(alpha = .14f), c.copy(alpha = .28f)))
            .padding(horizontal = 8.dp, vertical = 2.dp),
    ) { Text(e.typeLabel, style = st(11, Fixed(c), bold = true), maxLines = 1) }
}

/** Wiersz kolejnych zajęć: godzina | pasek koloru | przedmiot + sala · typ · prowadzący. */
@Composable
private fun NextRow(d: StudentData, e: ClassEvent, showDay: Boolean) {
    Row(GlanceModifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(GlanceModifier.width(46.dp)) {
            if (showDay) Text(Time.DOW_SHORT[Time.date(e.date).dayOfWeek.value - 1], style = st(11, SUB, bold = true))
            Text(e.start, style = st(13, INK, bold = true))
        }
        Box(GlanceModifier.width(3.dp).height(30.dp).cornerRadius(2.dp).background(Fixed(typeColor(e.type)))) {}
        Spacer(GlanceModifier.width(10.dp))
        Column(GlanceModifier.defaultWeight()) {
            Text(e.courseName, style = st(13, INK, bold = true), maxLines = 1)
            val lect = shortLecturer(d, e)
            Text(
                listOf("s. ${e.room}", e.typeLabel, lect).filter { it.isNotBlank() && it != "s. " }.joinToString(" · "),
                style = st(12, SUB), maxLines = 1,
            )
        }
    }
}

/** Okno czasu paska: trwające zajęcia albo ostatnia godzina przed startem; null = nie pokazuj. */
private fun timeWindow(e: ClassEvent, live: Boolean): Pair<java.time.LocalDateTime, java.time.LocalDateTime>? {
    val start = Time.date(e.date).atTime(java.time.LocalTime.parse(e.start))
    val end = Time.date(e.date).atTime(java.time.LocalTime.parse(e.end))
    val now = java.time.LocalDateTime.now()
    return when {
        live -> start to end
        java.time.Duration.between(now, start).toMinutes() in 0..59 -> start.minusHours(1) to start
        else -> null
    }
}

private val BLUE_SOFT = ColorProvider(Color(0xFF9DBBFF), Color(0xFF3A5BB0))
private val ORANGE = ColorProvider(Color(0xFFD07A12), Color(0xFFE8963A))
private val ORANGE_SOFT = ColorProvider(Color(0xFFF2C58F), Color(0xFF7A4C1A))

/**
 * Pasek czasu: 30 segmentów (wypełnione / bieżący / pozostałe) odświeżany co minutę
 * + natywny Chronometer odliczający sekundy („jeszcze 24:13” / „start za 18:42”).
 */
@Composable
private fun TimeBar(ctx: Context, e: ClassEvent, live: Boolean, w: Pair<java.time.LocalDateTime, java.time.LocalDateTime>) {
    val now = java.time.LocalDateTime.now()
    val total = java.time.Duration.between(w.first, w.second).toMillis().coerceAtLeast(1)
    val done = java.time.Duration.between(w.first, now).toMillis().coerceIn(0, total)
    val n = 30
    val filled = (done * n / total).toInt()
    val (on, cur) = if (live) BLUE to BLUE_SOFT else ORANGE to ORANGE_SOFT
    Column(GlanceModifier.fillMaxWidth()) {
        // Glance: max. 10 dzieci w wierszu → 3 grupy po 10 segmentów
        Row(GlanceModifier.fillMaxWidth().height(8.dp)) {
            for (g in 0 until 3) Row(GlanceModifier.defaultWeight().fillMaxHeight()) {
                for (k in 0 until 10) {
                    val i = g * 10 + k
                    Box(GlanceModifier.defaultWeight().fillMaxHeight().padding(horizontal = 1.dp)) {
                        Box(GlanceModifier.fillMaxSize().cornerRadius(3.dp).background(if (i < filled) on else if (i == filled) cur else TRACK)) {}
                    }
                }
            }
        }
        Spacer(GlanceModifier.height(5.dp))
        Row(GlanceModifier.fillMaxWidth().height(18.dp), verticalAlignment = Alignment.CenterVertically) {
            val target = (if (live) Time.date(e.date).atTime(java.time.LocalTime.parse(e.end)) else w.second)
                .atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
            val rv = android.widget.RemoteViews(ctx.packageName, pl.wolter.usos.R.layout.widget_chrono).apply {
                setChronometer(pl.wolter.usos.R.id.chrono,
                    android.os.SystemClock.elapsedRealtime() + (target - System.currentTimeMillis()),
                    if (live) "jeszcze %s" else "start za %s", true)
                setChronometerCountDown(pl.wolter.usos.R.id.chrono, true)
                setTextColor(pl.wolter.usos.R.id.chrono, if (live) 0xFF2F6BFF.toInt() else 0xFFD07A12.toInt())
            }
            // natywny widok potrafi zająć całą wysokość - trzymamy go w ramce o stałej wysokości
            Box(GlanceModifier.defaultWeight().height(18.dp), contentAlignment = Alignment.CenterStart) {
                androidx.glance.appwidget.AndroidRemoteViews(rv, GlanceModifier.height(18.dp))
            }
            Text(if (live) "${e.start}-${e.end}" else "start ${e.start}", style = st(11, SUB, medium = true), maxLines = 1)
        }
    }
}

class NowWidget : GlanceAppWidget() {
    override val sizeMode = SizeMode.Exact
    override suspend fun provideGlance(context: Context, id: GlanceId) = provideContent {
        val d = data() ?: return@provideContent Empty(context)
        val s = UsosApp.repo.settings.collectAsState().value
        val n = Derive.nowInfo(d, s)
        val size = LocalSize.current
        Shell(context, "plan", pad = 0.dp) {
            Column(GlanceModifier.fillMaxSize().padding(horizontal = 18.dp, vertical = 14.dp)) {
                if (n == null) {
                    Text("PLAN", style = st(12, BLUE, bold = true))
                    Spacer(GlanceModifier.height(8.dp))
                    Text("Brak zajęć w najbliższych tygodniach", style = st(16, INK, bold = true))
                    return@Column
                }
                val e = n.event
                val window = timeWindow(e, n.live)
                // Glance pokazuje max. 10 dzieci w kolumnie - dlatego treść jest w osobnych sekcjach
                Column(GlanceModifier.fillMaxWidth()) {
                // nagłówek: TERAZ · LAB …… do 11:30 · 25 min
                Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(n.caption, style = st(12, BLUE, bold = true), maxLines = 1, modifier = GlanceModifier.defaultWeight())
                    Text(
                        if (n.live) "do ${e.end}" else if (window != null) "${e.start}-${e.end}" else countdown(e),
                        style = st(12, SUB, medium = true), maxLines = 1,
                    )
                }
                Spacer(GlanceModifier.height(6.dp))
                Text(e.courseName, style = st(19, INK, bold = true), maxLines = 1)
                Text(
                    listOf("s. ${e.room}", e.building, if (e.group.isNotBlank()) "gr. ${e.group}" else "").filter { it.isNotBlank() }.joinToString(" · "),
                    style = st(13, TEXT2), maxLines = 1,
                )
                Spacer(GlanceModifier.height(4.dp))
                Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    TypeChip(e)
                    val lect = shortLecturer(d, e)
                    if (lect.isNotBlank()) {
                        Spacer(GlanceModifier.width(8.dp))
                        Text(lect, style = st(12, SUB, medium = true), maxLines = 1, modifier = GlanceModifier.defaultWeight())
                    }
                    if (window == null) Text("${e.start}-${e.end}", style = st(12, SUB, medium = true))
                }
                if (window != null) {
                    Spacer(GlanceModifier.height(10.dp))
                    TimeBar(context, e, n.live, window)
                }
                }
                // kolejne zajęcia: tyle, ile zmieści się w wysokości widżetu
                val rest = Derive.upcoming(d, s).filter { it.id != e.id }
                val sameDay = rest.filter { it.date == e.date }
                val room = ((size.height.value - (if (window != null) 170 else 140)) / 38).toInt().coerceIn(0, 4)
                if (room > 0) Column(GlanceModifier.fillMaxWidth()) {
                    Spacer(GlanceModifier.height(8.dp))
                    Box(GlanceModifier.fillMaxWidth().height(1.dp).background(LINE)) {}
                    Spacer(GlanceModifier.height(6.dp))
                    if (sameDay.isNotEmpty()) {
                        Text("POTEM", style = st(11, SUB, bold = true))
                        sameDay.take(room).forEach { NextRow(d, it, showDay = false) }
                        // wolne miejsce → kolejny dzień z zajęciami
                        val left = room - sameDay.size
                        val nextDay = rest.firstOrNull { it.date != e.date }?.date
                        if (left > 0 && nextDay != null) Column(GlanceModifier.fillMaxWidth()) {
                            val nd = Time.date(nextDay)
                            Spacer(GlanceModifier.height(4.dp))
                            Text("${Time.DOW_FULL[nd.dayOfWeek.value - 1].uppercase()} · ${Time.dayMonth(nd).uppercase()}", style = st(11, SUB, bold = true))
                            rest.filter { it.date == nextDay }.take(left).forEach { NextRow(d, it, showDay = false) }
                        }
                    } else {
                        Text("KONIEC NA DZIŚ · NASTĘPNE", style = st(11, SUB, bold = true))
                        rest.take(1).forEach { NextRow(d, it, showDay = true) }
                    }
                }
            }
        }
    }
}

// ---------- 2. Ten tydzień ----------
class WeekWidget : GlanceAppWidget() {
    override val sizeMode = SizeMode.Exact
    override suspend fun provideGlance(context: Context, id: GlanceId) = provideContent {
        val d = data() ?: return@provideContent Empty(context)
        val s = UsosApp.repo.settings.collectAsState().value
        val days = Derive.weekDays(d, s).take(5)
        val today = LocalDate.now()
        val gridH = 104
        Shell(context, "plan", pad = 16.dp) {
            Column(GlanceModifier.fillMaxSize()) {
                Row(GlanceModifier.fillMaxWidth()) {
                    Text("TEN TYDZIEŃ", style = st(12, SUB, bold = true), modifier = GlanceModifier.defaultWeight())
                    val todayN = days.firstOrNull { it.date == today }?.events?.size
                    Text((if (todayN != null) "dziś $todayN · " else "") + "tydz. ${days.sumOf { it.events.size }}", style = st(12, SUB, medium = true))
                }
                Spacer(GlanceModifier.height(10.dp))
                Row(GlanceModifier.fillMaxWidth()) {
                    days.forEachIndexed { i, wd ->
                        if (i > 0) Spacer(GlanceModifier.width(6.dp))
                        Column(GlanceModifier.defaultWeight(), horizontalAlignment = Alignment.CenterHorizontally) {
                            Column(
                                GlanceModifier.fillMaxWidth().height(gridH.dp).cornerRadius(8.dp)
                                    .background(if (wd.date == today) TODAY_COL else OTHER_COL).padding(horizontal = 3.dp),
                            ) {
                                // bloki ułożone w czasie 8:00-17:00 (9 h)
                                var cursor = 0f
                                wd.events.forEach { e ->
                                    val top = ((Time.mins(e.start) - 480) / 540f * gridH).coerceIn(0f, gridH.toFloat())
                                    val h = ((Time.mins(e.end) - Time.mins(e.start)) / 540f * gridH).coerceAtLeast(4f)
                                    val gap = (top - cursor).coerceAtLeast(0f)
                                    if (gap > 0) Spacer(GlanceModifier.height(gap.dp))
                                    val eh = h.coerceAtMost(gridH - maxOf(top, cursor))
                                    if (eh > 1) Box(GlanceModifier.fillMaxWidth().height(eh.dp).cornerRadius(4.dp).background(Fixed(typeColor(e.type)))) {}
                                    cursor = maxOf(top, cursor) + eh
                                }
                            }
                            Spacer(GlanceModifier.height(6.dp))
                            Text(Time.DOW_SHORT[wd.idx], style = st(11, if (wd.date == today) BLUE else SUB, bold = true))
                        }
                    }
                }
            }
        }
    }
}

private fun typeColor(t: pl.wolter.usos.data.ClassType) = when (t) {
    pl.wolter.usos.data.ClassType.WYK -> Color(0xFF2F6BFF)
    pl.wolter.usos.data.ClassType.CW -> Color(0xFF1E9E57)
    pl.wolter.usos.data.ClassType.LAB -> Color(0xFF7C4DEB)
    pl.wolter.usos.data.ClassType.LEK -> Color(0xFFD07A12)
    pl.wolter.usos.data.ClassType.EGZ -> Color(0xFFE04B5A)
    pl.wolter.usos.data.ClassType.SEM -> Color(0xFF0E9AA7)
    pl.wolter.usos.data.ClassType.INNE -> Color(0xFF8A8E98)
}

// ---------- 3. Nowy wynik ----------
class ResultWidget : GlanceAppWidget() {
    override val sizeMode = SizeMode.Exact
    override suspend fun provideGlance(context: Context, id: GlanceId) = provideContent {
        val d = data() ?: return@provideContent Empty(context)
        val f = d.feed.firstOrNull()
        Shell(context, f?.let { "course:${it.courseId}" } ?: "grades") {
            Column(GlanceModifier.fillMaxSize()) {
                Text("NOWY WYNIK", style = st(12, GREEN, bold = true))
                Spacer(GlanceModifier.defaultWeight())
                if (f == null) Text("Brak nowych wyników", style = st(14, TEXT2, medium = true))
                else {
                    Text(f.value, style = st(32, INK, bold = true))
                    Text(f.courseName, style = st(13, TEXT2, medium = true), maxLines = 1)
                    Text(f.what.substringBefore(" · "), style = st(13, TEXT2, medium = true), maxLines = 1)
                    Text("${(f.pct * 100).toInt()}% · ${Time.ago(Time.date(f.date))}", style = st(12, if (f.pct >= .8) GREEN else SUB, bold = true), maxLines = 1)
                }
            }
        }
    }
}

// ---------- 4. Kolokwium (odliczanie) ----------
class ExamWidget : GlanceAppWidget() {
    override val sizeMode = SizeMode.Exact
    override suspend fun provideGlance(context: Context, id: GlanceId) = provideContent {
        val d = data() ?: return@provideContent Empty(context)
        val e = Derive.nextExam(d, UsosApp.repo.settings.collectAsState().value, UsosApp.repo.groupExams.collectAsState().value)
        Shell(context, "exams", bg = RED) {
            Column(GlanceModifier.fillMaxSize()) {
                Text(if (e != null) Derive.examLabel(e) else "TERMINY", style = st(12, WHITE85, bold = true))
                Spacer(GlanceModifier.defaultWeight())
                if (e == null) Text("Brak terminów", style = st(18, WHITE, bold = true))
                else {
                    val date = Time.date(e.date)
                    Text(Time.daysLabel(Time.daysUntil(date)), style = st(32, WHITE, bold = true))
                    Text("${e.name.substringBefore(" · ")} · ${Time.dayMonth(date)}", style = st(13, WHITE85, medium = true), maxLines = 1)
                    Text(e.meta, style = st(12, WHITE85), maxLines = 1)
                }
            }
        }
    }
}

// ---------- 5. Prognoza średniej ----------
class ForecastWidget : GlanceAppWidget() {
    override val sizeMode = SizeMode.Exact
    override suspend fun provideGlance(context: Context, id: GlanceId) = provideContent {
        val d = data() ?: return@provideContent Empty(context)
        val s = UsosApp.repo.settings.collectAsState().value
        val avg = Grades.forecastAvg(d, s)
        Shell(context, "grades", pad = 0.dp) {
            Row(GlanceModifier.fillMaxSize().padding(horizontal = 18.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(GlanceModifier.defaultWeight().fillMaxHeight(), verticalAlignment = Alignment.CenterVertically) {
                    Text("PROGNOZA ŚREDNIEJ", style = st(12, SUB, bold = true))
                    val withPts = d.courses.count { Grades.points(it, s).max > 0 }
                    Text("z punktów · $withPts/${d.courses.size} przedmiotów", style = st(13, TEXT2), maxLines = 1)
                }
                Text(avg?.let(Grades::fmt) ?: "-", style = st(30, INK, bold = true))
            }
        }
    }
}

class NowWidgetReceiver : GlanceAppWidgetReceiver() { override val glanceAppWidget: GlanceAppWidget = NowWidget() }
class WeekWidgetReceiver : GlanceAppWidgetReceiver() { override val glanceAppWidget: GlanceAppWidget = WeekWidget() }
class ResultWidgetReceiver : GlanceAppWidgetReceiver() { override val glanceAppWidget: GlanceAppWidget = ResultWidget() }
class ExamWidgetReceiver : GlanceAppWidgetReceiver() { override val glanceAppWidget: GlanceAppWidget = ExamWidget() }
class ForecastWidgetReceiver : GlanceAppWidgetReceiver() { override val glanceAppWidget: GlanceAppWidget = ForecastWidget() }
