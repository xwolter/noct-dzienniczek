package pl.wolter.usos.data

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

data class WeekDay(val date: LocalDate, val idx: Int, val events: List<ClassEvent>)

data class NowInfo(
    val event: ClassEvent,
    val live: Boolean,
    val caption: String,
    val rightLabel: String,
    val pct: Float,
    val then: ClassEvent?,
)

object Derive {
    fun visible(d: StudentData, s: Settings) = d.events.filter { it.type !in s.hidden }

    fun weekDays(d: StudentData, s: Settings, mon: LocalDate = Time.monday()): List<WeekDay> {
        val vis = visible(d, s)
        return (0..6).mapNotNull { i ->
            val date = mon.plusDays(i.toLong())
            val ev = vis.filter { it.date == date.toString() }.sortedBy { it.start }
            if (i >= 5 && ev.isEmpty()) null else WeekDay(date, i, ev)
        }
    }

    fun upcoming(d: StudentData, s: Settings, now: LocalDateTime = LocalDateTime.now()): List<ClassEvent> =
        visible(d, s).filter { at(it.date, it.end) > now }.sortedWith(compareBy({ it.date }, { it.start }))

    private fun at(date: String, t: String) = Time.date(date).atTime(LocalTime.parse(t))

    fun nowInfo(d: StudentData, s: Settings, now: LocalDateTime = LocalDateTime.now()): NowInfo? {
        val up = upcoming(d, s, now)
        val e = up.firstOrNull() ?: return null
        val then = up.getOrNull(1)?.takeIf { it.date == e.date }
        val start = at(e.date, e.start)
        val end = at(e.date, e.end)
        val type = e.typeLabel.uppercase()
        return if (!start.isAfter(now)) {
            val total = java.time.Duration.between(start, end).toMinutes().coerceAtLeast(1)
            val done = java.time.Duration.between(start, now).toMinutes()
            val left = java.time.Duration.between(now, end).toMinutes()
            NowInfo(e, true, "TERAZ · $type", "jeszcze $left min", done.toFloat() / total, then)
        } else {
            val mins = java.time.Duration.between(now, start).toMinutes()
            val date = Time.date(e.date)
            val caption = when {
                date == now.toLocalDate() && mins <= 90 -> "ZA $mins MIN · $type"
                date == now.toLocalDate() -> "DZIŚ ${e.start} · $type"
                date == now.toLocalDate().plusDays(1) -> "JUTRO ${e.start} · $type"
                java.time.temporal.ChronoUnit.DAYS.between(now.toLocalDate(), date) < 7 ->
                    "${Time.DOW_SHORT[date.dayOfWeek.value - 1].uppercase()} ${e.start} · $type"
                else -> "${Time.DOW_SHORT[date.dayOfWeek.value - 1].uppercase()} ${Time.dayMonth(date).uppercase()} · $type"
            }
            NowInfo(e, false, caption, "${e.start}-${e.end}", 0f, then)
        }
    }

    /** Terminy z USOS + własne, posortowane po dacie i godzinie. */
    /** Terminy z USOS + własne + wspólne z grupy ([g]), posortowane po dacie i godzinie. */
    fun allExams(d: StudentData, s: Settings, g: List<MyExam>): List<Exam> = (d.exams + (s.myExams + g).map { m ->
        Exam(
            date = m.date, name = "${m.courseName} · ${m.kind.lowercase()}",
            meta = listOf(m.time, if (m.room.isNotBlank()) "s. ${m.room}" else "", m.note,
                if (m.shared) "grupa · ${m.author.ifBlank { "ktoś z roku" }}" else "").filter { it.isNotBlank() }.joinToString(" · "),
            courseId = m.courseId, kind = m.kind, time = m.time, id = m.id, own = true, shared = m.shared, author = m.author,
        )
    }).sortedWith(compareBy({ it.date }, { it.time }))

    fun upcomingExams(d: StudentData, s: Settings, g: List<MyExam>): List<Exam> =
        allExams(d, s, g).filter { Time.daysUntil(Time.date(it.date)) >= 0 }

    fun nextExam(d: StudentData, s: Settings, g: List<MyExam>): Exam? = upcomingExams(d, s, g).firstOrNull()

    /** Etykieta kafelka/widżetu: KOLOKWIUM, EGZAMIN… */
    fun examLabel(e: Exam): String = e.kind.ifBlank { "Termin" }.uppercase()

    fun gapLabel(g: Int): String =
        if (g >= 60) "Okienko ${g / 60} h${if (g % 60 != 0) " ${g % 60} min" else ""}" else "Przerwa $g min"

    /** Kolejne terminy zajęć (gdy USOS nie podał dat - wyliczane z częstotliwości). */
    fun nextDates(e: ClassEvent): List<String> {
        if (e.type == ClassType.EGZ) return listOf("tylko ${Time.dayMonth(Time.date(e.date))}")
        val step = if (e.freq.startsWith("co 2")) 14L else 7L
        val base = Time.date(e.date)
        return (1..4).map { Time.dayMonth(base.plusDays(it * step)) }
    }
}
