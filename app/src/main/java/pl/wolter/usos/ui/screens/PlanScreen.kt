package pl.wolter.usos.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import pl.wolter.usos.data.ClassEvent
import pl.wolter.usos.data.ClassType
import pl.wolter.usos.data.Derive
import pl.wolter.usos.data.Settings
import pl.wolter.usos.data.StudentData
import pl.wolter.usos.data.Time
import pl.wolter.usos.ui.AppViewModel
import pl.wolter.usos.ui.Dest
import pl.wolter.usos.ui.ScreenList
import pl.wolter.usos.ui.components.Card
import pl.wolter.usos.ui.components.Dot
import pl.wolter.usos.ui.components.Pill
import pl.wolter.usos.ui.components.Row2
import pl.wolter.usos.ui.components.Segmented
import pl.wolter.usos.ui.components.appear
import pl.wolter.usos.ui.components.press
import pl.wolter.usos.ui.theme.P
import pl.wolter.usos.ui.theme.T
import java.time.LocalDate
import java.time.LocalTime

@Composable
fun PlanScreen(vm: AppViewModel, d: StudentData, s: Settings) {
    val mon = vm.weekMonday()
    val days = Derive.weekDays(d, s, mon)
    val today = LocalDate.now()
    val defaultPos = if (vm.weekOffset == 0) days.indexOfFirst { it.date == today }.let { if (it < 0) 0 else it }
    else days.indexOfFirst { it.events.isNotEmpty() }.coerceAtLeast(0)
    val sel = (if (vm.day < 0) defaultPos else vm.day).coerceIn(0, (days.size - 1).coerceAtLeast(0))
    val selDay = days.getOrNull(sel)
    val (title, sub) = when (vm.planMode) {
        0 -> when (selDay?.date) {
            today -> "Dziś"
            today.plusDays(1) -> "Jutro"
            null -> "Plan"
            else -> Time.DOW_FULL[selDay.idx]
        } to (selDay?.let { Time.longDate(it.date) } ?: "")
        1 -> "Tydzień" to "${Time.dayMonth(days.first().date)} - ${Time.dayMonth(days.last().date)}${if (d.weekParity.isNotBlank() && vm.weekOffset == 0) " · ${d.weekParity}" else ""}"
        else -> "Terminy" to "Kolokwia i egzaminy"
    }

    // przesunięcie palcem: w trybie dnia - kolejny dzień (z przejściem między tygodniami), w trybie tygodnia - tydzień
    fun next() { if (vm.planMode == 0 && sel < days.size - 1) vm.day = sel + 1 else vm.shiftWeek(1, if (vm.planMode == 0) 0 else -1) }
    fun prev() { if (vm.planMode == 0 && sel > 0) vm.day = sel - 1 else vm.shiftWeek(-1, if (vm.planMode == 0) Int.MAX_VALUE else -1) }

    ScreenList(vm, title, sub) {
        item("modes") { Segmented(listOf("Dzień", "Tydzień", "Terminy"), vm.planMode, { vm.planMode = it }, Modifier.appear(0)) }
        if (vm.planMode != 2) item("week") { WeekSwitcher(vm, days, Modifier.appear(1)) }
        item("body") {
            var acc by remember { mutableFloatStateOf(0f) }
            AnimatedContent(
                targetState = vm.planMode to vm.weekOffset,
                transitionSpec = {
                    val dir = when {
                        targetState.first != initialState.first -> if (targetState.first > initialState.first) 1 else -1
                        else -> if (targetState.second > initialState.second) 1 else -1
                    }
                    (slideInHorizontally(spring(dampingRatio = .85f, stiffness = 380f)) { dir * it / 3 } + fadeIn(tween(220))) togetherWith
                        (slideOutHorizontally(tween(200)) { -dir * it / 5 } + fadeOut(tween(150)))
                },
                modifier = Modifier.pointerInput(vm.planMode, sel, days.size) {
                    if (vm.planMode == 2) return@pointerInput
                    detectHorizontalDragGestures(
                        onDragStart = { acc = 0f },
                        onDragEnd = { if (acc < -140f) next() else if (acc > 140f) prev(); acc = 0f },
                    ) { _, dx -> acc += dx }
                },
                label = "planMode",
            ) { (m, _) ->
                when (m) {
                    0 -> DayMode(vm, d, s, days, sel)
                    1 -> WeekMode(vm, days)
                    else -> ExamsMode(vm, d, s)
                }
            }
        }
    }
}

/** Pasek tygodnia: ‹ Ten tydzień · 26 paź - 1 lis › - przejście do poprzedniego/następnego tygodnia. */
@Composable
private fun WeekSwitcher(vm: AppViewModel, days: List<pl.wolter.usos.data.WeekDay>, modifier: Modifier) {
    val o = vm.weekOffset
    val label = when {
        o == 0 -> "Ten tydzień"
        o == 1 -> "Następny tydzień"
        o == -1 -> "Poprzedni tydzień"
        o > 1 -> "Za $o tygodnie".let { if (o >= 5) "Za $o tygodni" else it }
        else -> "${-o} tyg. temu"
    }
    val count = days.sumOf { it.events.size }
    Row(
        modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(P.c.card).padding(5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ArrowBtn("‹") { vm.shiftWeek(-1) }
        Column(
            Modifier.weight(1f).clip(RoundedCornerShape(18.dp)).press(.97f) { vm.resetWeek() }.padding(vertical = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            AnimatedContent(label, transitionSpec = { fadeIn(tween(200)) togetherWith fadeOut(tween(120)) }, label = "wl") {
                Text(it, style = T.t14b, color = if (o == 0) P.c.blue else P.c.ink)
            }
            Text(
                "${Time.dayMonth(days.first().date)} - ${Time.dayMonth(days.last().date)} · " +
                    if (vm.weekLoading) "ładowanie…" else "$count zajęć" + if (o != 0) " · stuknij: dziś" else "",
                style = T.t12, color = P.c.sub,
            )
        }
        ArrowBtn("›") { vm.shiftWeek(1) }
    }
}

@Composable
private fun ArrowBtn(sym: String, onClick: () -> Unit) {
    Box(
        Modifier.size(42.dp).clip(RoundedCornerShape(18.dp)).background(P.c.bg).press(.85f, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Text(sym, style = T.h22, color = P.c.ink) }
}

@Composable
private fun DayMode(vm: AppViewModel, d: StudentData, s: Settings, days: List<pl.wolter.usos.data.WeekDay>, sel: Int) {
    val today = LocalDate.now()
    val exams = Derive.allExams(d, s, vm.repo.groupExams.collectAsState().value)
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(Modifier.fillMaxWidth().appear(1), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            days.forEachIndexed { i, wd ->
                val on = i == sel
                val bg by animateColorAsState(if (on) P.c.blue else P.c.card, label = "dbg")
                val fg by animateColorAsState(if (on) Color.White else P.c.ink, label = "dfg")
                val dot = when {
                    on -> Color.White
                    wd.events.any { it.type == ClassType.EGZ } || exams.any { it.date == wd.date.toString() } -> P.c.red
                    wd.date == today -> P.c.blue
                    else -> Color.Transparent
                }
                Column(
                    Modifier.weight(1f).height(64.dp).clip(RoundedCornerShape(20.dp)).background(bg)
                        .press(.92f, onLongClick = { vm.newExam(date = wd.date) }) { vm.day = i },
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(2.dp, Alignment.CenterVertically),
                ) {
                    Text(Time.DOW_SHORT[wd.idx], style = T.t11.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold), color = fg.copy(alpha = .8f))
                    Text("${wd.date.dayOfMonth}", style = T.h17.copy(fontSize = androidx.compose.ui.unit.TextUnit(18f, androidx.compose.ui.unit.TextUnitType.Sp)), color = fg)
                    Dot(dot, 4.dp)
                }
            }
        }
        // własne terminy wybranego dnia (z USOS są już w planie jako egzaminy)
        val dayExams = days.getOrNull(sel)?.let { wd -> exams.filter { it.own && it.date == wd.date.toString() } }.orEmpty()
        if (dayExams.isNotEmpty()) Card(color = P.c.type(ClassType.EGZ).second) {
            dayExams.forEach { e ->
                Row2(onClick = { vm.editExam(e.id) }, padding = PaddingValues(horizontal = 18.dp, vertical = 12.dp)) {
                    Dot(P.c.red)
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text("${e.kind} · ${e.name.substringBefore(" · ")}", style = T.t15, color = P.c.ink)
                        if (e.meta.isNotBlank()) Text(e.meta, style = T.t13, color = P.c.text2)
                    }
                    Text(Time.daysLabel(Time.daysUntil(Time.date(e.date))), style = T.t12b, color = P.c.red)
                }
            }
        }
        AnimatedContent(
            targetState = sel,
            transitionSpec = {
                val dir = if (targetState > initialState) 1 else -1
                (slideInHorizontally(spring(dampingRatio = .82f, stiffness = 400f)) { dir * it / 3 } + fadeIn(tween(200))) togetherWith
                    (slideOutHorizontally(tween(180)) { -dir * it / 5 } + fadeOut(tween(140)))
            },
            label = "day",
        ) { i ->
            val wd = days.getOrNull(i)
            Card(Modifier.animateContentSize()) {
                if (wd == null || wd.events.isEmpty()) {
                    Text(if (days.all { it.events.isEmpty() }) "Brak zajęć w tym tygodniu - przesuń palcem lub użyj strzałek." else "Wolne. Przesuń palcem, by zobaczyć kolejny dzień.", style = T.t15.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.Normal), color = P.c.sub, modifier = Modifier.padding(horizontal = 18.dp, vertical = 28.dp))
                } else {
                    val nowM = Time.nowMins()
                    val isToday = wd.date == today
                    wd.events.forEachIndexed { k, e ->
                        if (k > 0) {
                            val g = Time.mins(e.start) - Time.mins(wd.events[k - 1].end)
                            if (g >= 15) GapRow(Derive.gapLabel(g))
                        }
                        val live = isToday && Time.mins(e.start) <= nowM && Time.mins(e.end) > nowM
                        val past = (isToday && Time.mins(e.end) <= nowM) || wd.date.isBefore(today)
                        ClassRow(e, live, past, Modifier.appear(k, wd.date)) { vm.openSheet(e) }
                    }
                }
            }
        }
    }
}

@Composable
private fun GapRow(label: String) {
    val dash = P.c.dash
    Row(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        Box(Modifier.width(46.dp))
        Box(
            Modifier.weight(1f).height(2.dp).drawBehind {
                drawLine(dash, Offset(0f, size.height / 2), Offset(size.width, size.height / 2), 1.5.dp.toPx(),
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f)))
            },
        )
        Text(label, style = T.t12b, color = P.c.muted)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ClassRow(e: ClassEvent, live: Boolean, past: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val (color, tint) = P.c.type(e.type)
    Row(
        modifier.fillMaxWidth().background(if (live) P.c.nowRow else Color.Transparent)
            .press(.98f, onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 13.dp)
            .height(IntrinsicSize.Min)
            .let { if (past) it.then(Modifier.background(Color.Transparent)) else it },
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        val a = if (past) .45f else 1f
        Column(Modifier.width(46.dp).padding(top = 2.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(e.start, style = T.t14b, color = P.c.ink.copy(alpha = a))
            Text(e.end, style = T.t12, color = P.c.muted.copy(alpha = a))
        }
        Box(Modifier.width(4.dp).fillMaxHeight().clip(RoundedCornerShape(2.dp)).background(color.copy(alpha = a)))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(e.courseName, style = T.t16, color = P.c.ink.copy(alpha = a))
            Text("s. ${e.room} · ${e.building}", style = T.t13, color = P.c.sub.copy(alpha = a))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Pill(e.typeLabel, tint, color.copy(alpha = a))
                if (live) Pill("trwa", P.c.primaryBtn, P.c.primaryBtnFg)
                if (e.freq.isNotBlank() && e.freq != "co tydzień") Pill(e.freq, P.c.chip, P.c.text2, style = T.t11.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold))
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun WeekMode(vm: AppViewModel, days: List<pl.wolter.usos.data.WeekDay>) {
    val today = LocalDate.now()
    val all = days.flatMap { it.events }
    val h0 = minOf(8, all.minOfOrNull { Time.mins(it.start) / 60 } ?: 8)
    val h1 = maxOf(19, all.maxOfOrNull { (Time.mins(it.end) + 59) / 60 } ?: 19)
    val px = 34.dp
    val gridH = px * (h1 - h0)
    Card(padding = PaddingValues(start = 6.dp, end = 12.dp, top = 14.dp, bottom = 14.dp), modifier = Modifier.appear(1)) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(Modifier.padding(start = 30.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                days.forEach { wd ->
                    Text(
                        "${Time.DOW_SHORT[wd.idx]} ${wd.date.dayOfMonth}", style = T.t12b.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.Bold),
                        color = if (wd.date == today) P.c.blue else P.c.text2, modifier = Modifier.weight(1f),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center, maxLines = 1,
                    )
                }
            }
            Box(Modifier.fillMaxWidth().height(gridH)) {
                Row(Modifier.fillMaxWidth().height(gridH), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Box(Modifier.width(26.dp).fillMaxHeight()) {
                        for (h in h0..h1) {
                            Text("$h", style = T.t10, color = P.c.faint, modifier = Modifier.align(Alignment.TopEnd).offset(y = px * (h - h0) - 6.dp).padding(end = 2.dp))
                        }
                    }
                    days.forEachIndexed { ci, wd ->
                        Box(
                            Modifier.weight(1f).fillMaxHeight().clip(RoundedCornerShape(10.dp))
                                .background(if (wd.date == today) P.c.todayCol else P.c.otherCol),
                        ) {
                            wd.events.forEachIndexed { k, e ->
                                val (color, tint) = P.c.type(e.type)
                                val top = px * ((Time.mins(e.start) - h0 * 60) / 60f)
                                val h = px * ((Time.mins(e.end) - Time.mins(e.start)) / 60f) - 2.dp
                                Row(
                                    Modifier.offset(y = top).fillMaxWidth().height(h).appear(ci * 3 + k)
                                        .clip(RoundedCornerShape(10.dp)).background(tint).press(.94f) { vm.openSheet(e) },
                                ) {
                                    Box(Modifier.width(3.dp).fillMaxHeight().background(color))
                                    Column(Modifier.padding(horizontal = 5.dp, vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                                        Text(shortName(e), style = T.t11.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.ExtraBold), color = color, maxLines = 1, overflow = TextOverflow.Clip)
                                        Text(e.room, style = T.t10, color = P.c.text2, maxLines = 1, overflow = TextOverflow.Clip)
                                    }
                                }
                            }
                        }
                    }
                }
                // linia „teraz”
                if (days.any { it.date == today }) {
                    val n = LocalTime.now()
                    val m = n.hour * 60 + n.minute
                    if (m in h0 * 60..h1 * 60) {
                        val red = P.c.red
                        Box(Modifier.padding(start = 30.dp).fillMaxWidth().offset(y = px * ((m - h0 * 60) / 60f)).height(2.dp).background(red))
                    }
                }
            }
            FlowRow(Modifier.padding(start = 30.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                all.map { it.type to it.typeLabel }.distinctBy { it.first }.forEach { (t, l) ->
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                        Box(Modifier.size(8.dp).clip(RoundedCornerShape(3.dp)).background(P.c.type(t).first))
                        Text(l, style = T.t11.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold), color = P.c.text2)
                    }
                }
            }
        }
    }
}

/** Skrót nazwy przedmiotu do kafelka tygodnia (ASD, AM, BD…). */
fun shortName(e: ClassEvent): String {
    if (e.courseId.length in 1..4 && e.courseId.all { it.isLetter() }) return e.courseId
    val words = e.courseName.split(' ', '-').filter { it.length > 2 || it.firstOrNull()?.isUpperCase() == true }
    return words.take(3).mapNotNull { it.firstOrNull()?.uppercaseChar() }.joinToString("").ifBlank { e.courseName.take(3) }
}

@Composable
private fun ExamsMode(vm: AppViewModel, d: StudentData, s: Settings) {
    val exams = Derive.upcomingExams(d, s, vm.repo.groupExams.collectAsState().value)
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        GroupStrip(vm, s, Modifier.appear(1))
        Box(Modifier.appear(1)) { AddExamButton { vm.newExam() } }
        Card(Modifier.appear(2)) {
            if (exams.isEmpty()) {
                Text("Brak terminów. Dodaj kolokwium, kartkówkę albo oddanie projektu - przypomnimy dzień wcześniej.",
                    style = T.t15.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.Normal), color = P.c.sub,
                    modifier = Modifier.padding(horizontal = 18.dp, vertical = 24.dp))
            }
            exams.forEachIndexed { i, e ->
                ExamRow(e, Modifier.appear(i + 3)) { if (e.own) vm.editExam(e.id) else vm.push(Dest.Course(e.courseId)) }
            }
        }
        Text("Wskazówka: przytrzymaj dzień w widoku „Dzień”, aby dodać termin na ten dzień.",
            style = T.t12, color = P.c.muted, modifier = Modifier.padding(horizontal = 10.dp))
        if (d.holidays.isNotBlank()) {
            Card(padding = PaddingValues(horizontal = 18.dp, vertical = 14.dp), radius = 22.dp, modifier = Modifier.appear(8)) {
                Text("Dni wolne · services/calendar", style = T.t14.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold), color = P.c.ink)
                Text(d.holidays, style = T.t13, color = P.c.sub)
            }
        }
    }
}
