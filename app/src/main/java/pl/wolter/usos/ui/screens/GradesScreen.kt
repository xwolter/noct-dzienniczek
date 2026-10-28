package pl.wolter.usos.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import pl.wolter.usos.data.ClassType
import pl.wolter.usos.data.Grades
import pl.wolter.usos.data.Settings
import pl.wolter.usos.data.StudentData
import pl.wolter.usos.ui.AppViewModel
import pl.wolter.usos.ui.Dest
import pl.wolter.usos.ui.ScreenList
import pl.wolter.usos.ui.components.AnimatedBar
import pl.wolter.usos.ui.components.Card
import pl.wolter.usos.ui.components.CountText
import pl.wolter.usos.ui.components.GradeBadge
import pl.wolter.usos.ui.components.Pill
import pl.wolter.usos.ui.components.Row2
import pl.wolter.usos.ui.components.Segmented
import pl.wolter.usos.ui.components.appear
import pl.wolter.usos.ui.components.press
import pl.wolter.usos.ui.theme.P
import pl.wolter.usos.ui.theme.T

private data class GRow(val name: String, val meta: String, val value: String, val pct: Float?, val courseId: String?)

@Composable
fun GradesScreen(vm: AppViewModel, d: StudentData, s: Settings) {
    val terms = listOf(d.termLabel) + d.pastTerms.map { it.label }
    val term = vm.term.coerceIn(0, terms.size - 1)
    ScreenList(vm, "Oceny", "Sprawdziany i protokoły") {
        item("terms") { Segmented(terms.take(3), term.coerceAtMost(2), { vm.term = it }, Modifier.appear(0)) }
        item("body") {
            AnimatedContent(term, transitionSpec = { fadeIn(tween(260, 60)) togetherWith fadeOut(tween(120)) }, label = "term") { t ->
                val rows: List<GRow>
                val label: String; val avg: Double?; val ects: String; val note: String
                if (t == 0) {
                    rows = d.courses.map { c ->
                        val p = Grades.points(c, s)
                        GRow(c.name, "${Grades.fmtPts(p.got)}/${Grades.fmtPts(p.max)} pkt · ${(p.pct * 100).toInt()}% · prognoza",
                            if (p.max > 0) Grades.forecast(p.pct, s.thresholds) else "-", p.pct.toFloat(), c.id)
                    }
                    label = "Prognoza średniej"; avg = Grades.forecastAvg(d, s)
                    ects = "0 / ${Grades.fmtPts(d.courses.sumOf { it.ects })}"
                    note = "Oceny końcowe pojawią się w protokołach po sesji."
                } else {
                    val pt = d.pastTerms[t - 1]
                    rows = pt.rows.map { r -> GRow(r.name, "${Grades.fmtPts(r.ects)} ECTS · ${r.note}", r.value, null, null) }
                    label = "Średnia ważona ECTS"; avg = Grades.termAverage(pt)
                    ects = "${Grades.fmtPts(pt.rows.sumOf { it.ects })} / 30"
                    note = "Średnia z ocen końcowych, ważona punktami ECTS."
                }
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Card(padding = PaddingValues(20.dp), modifier = Modifier.appear(1, t)) {
                        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Bottom) {
                                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                    Text(label, style = T.t13.copy(fontWeight = FontWeight.SemiBold), color = P.c.sub)
                                    CountText(avg, Grades::fmt, T.big, P.c.ink)
                                }
                                Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                    Text("ECTS", style = T.t13.copy(fontWeight = FontWeight.SemiBold), color = P.c.sub)
                                    Text(ects, style = T.h22, color = P.c.blue)
                                }
                            }
                            DistributionBar(rows.map { it.value })
                            Text(note, style = T.t12, color = P.c.sub)
                        }
                    }
                    Card(Modifier.appear(2, t)) {
                        rows.forEachIndexed { i, g ->
                            Row2(Modifier.appear(i + 3, t), onClick = g.courseId?.let { id -> { vm.push(Dest.Course(id)) } }) {
                                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                                    Text(g.name, style = T.t15, color = P.c.ink)
                                    Text(g.meta, style = T.t12, color = P.c.sub)
                                    if (g.pct != null) AnimatedBar(g.pct, P.c.grade(g.value).second, delayMs = 200 + i * 40)
                                }
                                GradeBadge(g.value)
                            }
                        }
                    }
                }
            }
        }
    }
}

/** Pasek rozkładu ocen - segmenty rozrastają się jeden po drugim. */
@Composable
private fun DistributionBar(values: List<String>) {
    val order = listOf("5" to P.c.green, "4,5" to Color(0xFF4CC283), "4" to P.c.blue, "3,5" to Color(0xFF7FA6FF), "3" to P.c.orange, "2" to P.c.red)
    val segs = order.map { (v, c) -> values.count { it == v } to c }.filter { it.first > 0 }
    if (segs.isEmpty()) return
    val a = remember(values) { Animatable(0f) }
    LaunchedEffect(values) { a.animateTo(1f, tween(800)) }
    Row(Modifier.fillMaxWidth().height(8.dp), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
        segs.forEachIndexed { i, (w, c) ->
            val local = ((a.value * segs.size) - i).coerceIn(0f, 1f)
            Row(Modifier.weight(w.toFloat()).fillMaxHeight()) {
                if (local > 0f) androidx.compose.foundation.layout.Box(
                    Modifier.fillMaxWidth(local).fillMaxHeight().clip(RoundedCornerShape(4.dp)).background(c),
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CoursesScreen(vm: AppViewModel, d: StudentData, s: Settings) {
    ScreenList(vm, "Przedmioty", "${d.termLabel} · ${Grades.fmtPts(d.courses.sumOf { it.ects })} ECTS") {
        if (d.courses.isEmpty()) item("empty") {
            Card(padding = PaddingValues(18.dp)) { Text("Brak przedmiotów w bieżącym semestrze.", style = T.t15, color = P.c.sub) }
        }
        d.courses.forEachIndexed { i, c ->
            item(c.id) {
                val p = Grades.points(c, s)
                val lects = c.groups.flatMap { it.lecturerIds }.distinct().mapNotNull { d.lecturers[it]?.name }
                Column(
                    Modifier.appear(i).fillMaxWidth().clip(RoundedCornerShape(26.dp)).background(P.c.card)
                        .press(.97f) { vm.push(Dest.Course(c.id)) }.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(c.name, style = T.h17, color = P.c.ink, modifier = Modifier.weight(1f))
                        if (c.ects > 0) Pill("${Grades.fmtPts(c.ects)} ECTS", P.c.blueTint, P.c.blue, style = T.t12b.copy(fontWeight = FontWeight.Bold))
                    }
                    if (lects.isNotEmpty()) Text(lects.joinToString(", "), style = T.t13, color = P.c.sub, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    val types = c.groups.filter { it.type != ClassType.EGZ }.distinctBy { it.type }
                    if (types.isNotEmpty()) FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        types.forEach { g -> val (col, tint) = P.c.type(g.type); Pill(g.typeLabel, tint, col) }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        AnimatedBar(p.pct.toFloat(), P.c.blue, Modifier.weight(1f), delayMs = 200 + i * 50)
                        Text("${Grades.fmtPts(p.got)}/${Grades.fmtPts(p.max)} pkt", style = T.t12b.copy(fontWeight = FontWeight.Bold), color = P.c.text2)
                    }
                }
            }
        }
    }
}
