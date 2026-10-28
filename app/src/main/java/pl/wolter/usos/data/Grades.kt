package pl.wolter.usos.data

import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale

data class Points(val got: Double, val max: Double) {
    val pct: Double get() = if (max > 0) got / max else 0.0
}

object Grades {
    val ORDER = listOf("5", "4,5", "4", "3,5", "3", "2")

    fun testsOf(c: Course, s: Settings): List<TestBlock> = c.tests + (s.manual[c.id] ?: emptyList())

    fun points(c: Course, s: Settings): Points {
        var p = 0.0; var mx = 0.0
        testsOf(c, s).forEach { t -> t.items.forEach { i -> if (i.points != null) { p += i.points; mx += i.max } } }
        return Points(p, mx)
    }

    fun blockPoints(t: TestBlock): Pair<Points, Boolean> {
        var a = 0.0; var mx = 0.0; var done = false
        t.items.forEach { mx += it.max; if (it.points != null) { a += it.points; done = true } }
        return Points(a, mx) to done
    }

    fun forecast(pct: Double, th: List<Double>): String = when {
        pct >= th[0] -> "5"
        pct >= th[1] -> "4,5"
        pct >= th[2] -> "4"
        pct >= th[3] -> "3,5"
        pct >= th[4] -> "3"
        else -> "2"
    }

    fun num(v: String): Double? = v.replace(',', '.').toDoubleOrNull()

    fun fmt(x: Double): String = String.format(Locale.US, "%.2f", x).replace('.', ',')

    fun fmtPts(x: Double): String =
        if (x % 1.0 == 0.0) x.toLong().toString() else String.format(Locale.US, "%.1f", x).replace('.', ',')

    /** Ważona ECTS prognoza średniej (przedmioty bez punktów są pomijane). */
    fun forecastAvg(d: StudentData, s: Settings): Double? {
        var ws = 0.0; var we = 0.0
        d.courses.forEach { c ->
            val p = points(c, s)
            if (p.max > 0) {
                val w = if (c.ects > 0) c.ects else 1.0
                ws += num(forecast(p.pct, s.thresholds))!! * w; we += w
            }
        }
        return if (we > 0) ws / we else null
    }

    fun termAverage(t: PastTerm): Double? {
        var s = 0.0; var e = 0.0
        t.rows.forEach { r -> val n = num(r.value); if (n != null && r.ects > 0) { s += n * r.ects; e += r.ects } }
        return if (e > 0) s / e else null
    }
}

object Time {
    val DATE: DateTimeFormatter = DateTimeFormatter.ISO_LOCAL_DATE
    private val PL = Locale.forLanguageTag("pl-PL")
    val DOW_SHORT = listOf("Pon", "Wt", "Śr", "Czw", "Pt", "Sob", "Nd")
    val DOW_FULL = listOf("Poniedziałek", "Wtorek", "Środa", "Czwartek", "Piątek", "Sobota", "Niedziela")
    val MON_SHORT = listOf("sty", "lut", "mar", "kwi", "maj", "cze", "lip", "sie", "wrz", "paź", "lis", "gru")
    val MON_GEN = listOf("stycznia", "lutego", "marca", "kwietnia", "maja", "czerwca", "lipca", "sierpnia", "września", "października", "listopada", "grudnia")

    fun mins(t: String): Int { val (h, m) = t.split(':').map { it.toInt() }; return h * 60 + m }
    fun date(s: String): LocalDate = LocalDate.parse(s, DATE)
    fun monday(d: LocalDate = LocalDate.now()): LocalDate = d.minusDays((d.dayOfWeek.value - 1).toLong())
    fun nowMins(): Int = LocalTime.now().let { it.hour * 60 + it.minute }
    fun dayMonth(d: LocalDate) = "${d.dayOfMonth} ${MON_SHORT[d.monthValue - 1]}"
    fun longDate(d: LocalDate) = "${DOW_FULL[d.dayOfWeek.value - 1]}, ${d.dayOfMonth} ${MON_GEN[d.monthValue - 1]}"
    fun daysUntil(d: LocalDate): Long = ChronoUnit.DAYS.between(LocalDate.now(), d)
    fun daysLabel(n: Long): String = when {
        n == 0L -> "dziś"
        n == 1L -> "jutro"
        n < 0 -> "minęło"
        else -> "$n dni"
    }
    fun ago(d: LocalDate): String {
        val n = ChronoUnit.DAYS.between(d, LocalDate.now())
        return when {
            n <= 0L -> "dziś"
            n == 1L -> "wczoraj"
            n < 7 -> DOW_SHORT[d.dayOfWeek.value - 1].lowercase(PL)
            else -> dayMonth(d)
        }
    }
}
