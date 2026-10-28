package pl.wolter.usos.ui.screens

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import pl.wolter.usos.data.Derive
import pl.wolter.usos.data.Grades
import pl.wolter.usos.data.Settings
import pl.wolter.usos.data.StudentData
import pl.wolter.usos.data.Time
import pl.wolter.usos.ui.AppViewModel
import pl.wolter.usos.ui.Dest
import pl.wolter.usos.ui.ScreenList
import pl.wolter.usos.ui.components.AnimatedBar
import pl.wolter.usos.ui.components.Card
import pl.wolter.usos.ui.components.CountText
import pl.wolter.usos.ui.components.Dot
import pl.wolter.usos.ui.components.Row2
import pl.wolter.usos.ui.components.SectionLabel
import pl.wolter.usos.ui.components.appear
import pl.wolter.usos.ui.components.press
import pl.wolter.usos.ui.theme.P
import pl.wolter.usos.ui.theme.T
import java.time.LocalDate

@Composable
fun StartScreen(vm: AppViewModel, d: StudentData, s: Settings) {
    // odświeżaj „teraz” co 30 s
    var tick by remember { mutableStateOf(0) }
    LaunchedEffect(Unit) { while (true) { delay(30_000); tick++ } }
    val now = remember(tick, d, s) { Derive.nowInfo(d, s) }
    val exam = Derive.nextExam(d, s, vm.repo.groupExams.collectAsState().value)
    val avg = Grades.forecastAvg(d, s)
    val hello = d.student.firstName.ifBlank { "Studencie" }

    ScreenList(vm, "Cześć, $hello", Time.longDate(LocalDate.now()) + if (d.demo) " · demo" else "") {
        item("now") { NowCard(vm, now, Modifier.appear(0)) }
        if (d.feed.isNotEmpty()) {
            item("feedL") { SectionLabel("Nowe wyniki", Modifier.appear(1)) }
            item("feed") {
                Card(Modifier.appear(2)) {
                    d.feed.forEach { f ->
                        val (bg, fg) = P.c.grade(if (f.pct >= .8) "5" else if (f.pct >= .65) "4" else "3")
                        Row2(onClick = { vm.push(Dest.Course(f.courseId)) }, padding = PaddingValues(horizontal = 18.dp, vertical = 13.dp)) {
                            Box(
                                Modifier.defaultMinSize(52.dp, 40.dp).height(40.dp).clip(RoundedCornerShape(14.dp)).background(bg).padding(horizontal = 8.dp),
                                contentAlignment = Alignment.Center,
                            ) { Text(f.value, style = T.t14b.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.ExtraBold), color = fg) }
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text(f.courseName, style = T.t15, color = P.c.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(f.what, style = T.t13, color = P.c.sub, maxLines = 1)
                            }
                            Text(Time.ago(Time.date(f.date)), style = T.t12b, color = P.c.muted)
                        }
                    }
                }
            }
        }
        item("tiles") {
            Row(Modifier.fillMaxWidth().appear(3), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Column(
                    Modifier.weight(1f).height(128.dp).clip(RoundedCornerShape(24.dp)).background(P.c.red)
                        .press { vm.goExams() }.padding(16.dp),
                    verticalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(if (exam != null) Derive.examLabel(exam) else "TERMINY", style = T.cap, color = Color.White.copy(alpha = .9f))
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        if (exam != null) {
                            val days = Time.daysUntil(Time.date(exam.date))
                            Text(Time.daysLabel(days), style = T.num30, color = Color.White)
                            Text("${exam.name.substringBefore(" · ")} · ${Time.dayMonth(Time.date(exam.date))}", style = T.t12b, color = Color.White.copy(alpha = .9f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        } else {
                            Text("+ Dodaj", style = T.num30, color = Color.White)
                            Text("kolokwium lub termin", style = T.t12b, color = Color.White.copy(alpha = .9f))
                        }
                    }
                }
                Column(
                    Modifier.weight(1f).height(128.dp).clip(RoundedCornerShape(24.dp)).background(P.c.card)
                        .press { vm.goGrades() }.padding(16.dp),
                    verticalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text("PROGNOZA", style = T.cap, color = P.c.sub)
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        CountText(avg, Grades::fmt, T.num30, P.c.ink)
                        Text("z punktów · ${d.termLabel}", style = T.t12b, color = P.c.sub, maxLines = 1)
                    }
                }
            }
        }
        if (d.alerts.isNotEmpty()) item("alerts") {
            Card(Modifier.appear(4)) {
                d.alerts.forEach { a ->
                    Row(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Dot(P.c.kind(a.kind))
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(a.title, style = T.t15, color = P.c.ink)
                            Text(a.sub, style = T.t13, color = P.c.sub)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun NowCard(vm: AppViewModel, now: pl.wolter.usos.data.NowInfo?, modifier: Modifier) {
    Column(
        modifier.fillMaxWidth().clip(RoundedCornerShape(28.dp)).background(P.c.hero)
            .press { now?.let { vm.openSheet(it.event) } ?: vm.selectTab(pl.wolter.usos.ui.Tab.Plan) }
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        if (now == null) {
            Text("PLAN", style = T.cap, color = Color(0xFF8FB0FF))
            Text("Brak zajęć w najbliższych tygodniach", style = T.h22, color = Color.White)
            Text("Odpoczywaj - albo przeciągnij w dół, by odświeżyć.", style = T.t14, color = Color.White.copy(alpha = .7f))
            return@Column
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (now.live) LiveDot()
                Text(now.caption, style = T.cap, color = Color(0xFF8FB0FF), maxLines = 1)
            }
            Text(now.rightLabel, style = T.t12b, color = Color.White.copy(alpha = .7f))
        }
        Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(now.event.courseName, style = T.h22, color = Color.White, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text("s. ${now.event.room} · ${now.event.building}", style = T.t14, color = Color.White.copy(alpha = .7f))
        }
        AnimatedBar(if (now.live) now.pct else 0f, P.c.blue, track = Color.White.copy(alpha = .15f), height = 6.dp, delayMs = 300)
        if (now.then != null) {
            Box(Modifier.fillMaxWidth().height(1.dp).background(Color.White.copy(alpha = .12f)))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Potem ${now.then.start}", style = T.t13, color = Color.White.copy(alpha = .7f))
                Spacer(Modifier.size(8.dp))
                Text("${now.then.courseName} · s. ${now.then.room}", style = T.t13b, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

/** Pulsująca kropka „na żywo”. */
@Composable
private fun LiveDot() {
    val inf = rememberInfiniteTransition(label = "live")
    val s by inf.animateFloat(1f, 2.2f, infiniteRepeatable(tween(1200), RepeatMode.Restart), label = "s")
    val a by inf.animateFloat(.6f, 0f, infiniteRepeatable(tween(1200), RepeatMode.Restart), label = "a")
    Box(contentAlignment = Alignment.Center) {
        Box(Modifier.size(8.dp).graphicsLayer { scaleX = s; scaleY = s; alpha = a }.clip(CircleShape).background(P.c.blue))
        Box(Modifier.size(8.dp).clip(CircleShape).background(Color(0xFF8FB0FF)))
    }
}
