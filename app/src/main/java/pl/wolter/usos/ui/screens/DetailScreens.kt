package pl.wolter.usos.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import pl.wolter.usos.data.ClassType
import pl.wolter.usos.data.Derive
import pl.wolter.usos.data.Grades
import pl.wolter.usos.data.Settings
import pl.wolter.usos.data.StudentData
import pl.wolter.usos.data.TestBlock
import pl.wolter.usos.data.Time
import pl.wolter.usos.ui.AppViewModel
import pl.wolter.usos.ui.Dest
import pl.wolter.usos.ui.ScreenList
import pl.wolter.usos.ui.components.AnimatedBar
import pl.wolter.usos.ui.components.Avatar
import pl.wolter.usos.ui.components.Card
import pl.wolter.usos.ui.components.Chevron
import pl.wolter.usos.ui.components.InfoTile
import pl.wolter.usos.ui.components.PrimaryButton
import pl.wolter.usos.ui.components.Row2
import pl.wolter.usos.ui.components.SectionLabel
import pl.wolter.usos.ui.components.appear
import pl.wolter.usos.ui.components.press
import pl.wolter.usos.ui.theme.P
import pl.wolter.usos.ui.theme.T

private fun backLabel(vm: AppViewModel): String = when (vm.stack.getOrNull(vm.stack.size - 2)) {
    is Dest.Course -> "Przedmiot"
    is Dest.Lect -> "Prowadzący"
    null -> vm.tab.label
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CourseScreen(vm: AppViewModel, d: StudentData, s: Settings, id: String) {
    val c = d.courses.firstOrNull { it.id == id }
    if (c == null) {
        ScreenList(vm, "Przedmiot", "Nie znaleziono w bieżącym semestrze", small = true, back = backLabel(vm) to { vm.pop() }) {}
        return
    }
    val p = Grades.points(c, s)
    val f = if (p.max > 0) Grades.forecast(p.pct, s.thresholds) else "-"
    val (gbg, gfg) = P.c.grade(f)
    var expanded by rememberSaveable(id) { mutableStateOf<Int?>(null) }
    var adding by remember { mutableStateOf(false) }
    val tests = Grades.testsOf(c, s)
    // plan obejmuje kilka tygodni - każda grupa zajęć raz
    val events = d.events.filter { it.courseId == c.id && it.type != ClassType.EGZ }
        .distinctBy { listOf(it.type, it.group, Time.date(it.date).dayOfWeek, it.start) }

    ScreenList(vm, c.name, "Przedmiot", small = true, back = backLabel(vm) to { vm.pop() }) {
        item("chips") {
            FlowRow(Modifier.padding(horizontal = 4.dp).appear(0), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                listOfNotNull(c.code, if (c.ects > 0) "${Grades.fmtPts(c.ects)} ECTS" else null, d.termLabel).forEach {
                    Text(it, style = T.t12b.copy(fontWeight = FontWeight.Bold), color = P.c.text2,
                        modifier = Modifier.clip(RoundedCornerShape(99.dp)).background(P.c.card).padding(horizontal = 11.dp, vertical = 6.dp))
                }
            }
        }
        item("grade") {
            Row(
                Modifier.appear(1).fillMaxWidth().clip(RoundedCornerShape(26.dp)).background(gbg).padding(20.dp),
                horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text("PROGNOZA OCENY", style = T.cap, color = gfg)
                    Text("${(p.pct * 100).toInt()}% punktów · brak oceny w protokole", style = T.t14, color = P.c.text2)
                }
                val sc by animateFloatAsState(1f, spring(dampingRatio = .4f), label = "gs")
                Text(f, style = T.big.copy(fontWeight = FontWeight.ExtraBold), color = gfg, modifier = Modifier.graphicsLayer { scaleX = sc; scaleY = sc })
            }
        }
        item("testsL") { SectionLabel("Sprawdziany · ${Grades.fmtPts(p.got)}/${Grades.fmtPts(p.max)} pkt", Modifier.appear(2)) }
        item("tests") {
            Card(Modifier.appear(3).animateContentSize(spring(dampingRatio = .85f, stiffness = 400f))) {
                if (tests.isEmpty()) Text("Brak sprawdzianów w USOS.", style = T.t14, color = P.c.sub, modifier = Modifier.padding(18.dp))
                tests.forEachIndexed { i, t -> TestRow(t, expanded == i, { expanded = if (expanded == i) null else i }, if (t.manual) ({ vm.removeManual(c.id, t.id) }) else null) }
                Row2(onClick = { adding = true }) {
                    Text("+  Dodaj własny wynik", style = T.t15, color = P.c.blue, modifier = Modifier.weight(1f))
                    Text(if (vm.repo.cloud.enabled) "chmura" else "lokalnie", style = T.t12, color = P.c.muted)
                }
            }
        }
        item("examsL") { SectionLabel("Terminy", Modifier.appear(4)) }
        item("exams") {
            val ex = Derive.upcomingExams(d, s, vm.repo.groupExams.collectAsState().value).filter { it.courseId == c.id }
            Card(Modifier.appear(4)) {
                ex.forEach { e -> ExamRow(e) { if (e.own) vm.editExam(e.id) } }
                Row2(onClick = { vm.newExam(c.id) }) {
                    Text("+  Dodaj kolokwium / termin", style = T.t15, color = P.c.blue, modifier = Modifier.weight(1f))
                    Text(if (ex.isEmpty()) "brak w USOS" else "${ex.size}", style = T.t12, color = P.c.muted)
                }
            }
        }
        if (events.isNotEmpty() || c.groups.isNotEmpty()) {
            item("groupsL") { SectionLabel("Twoje grupy", Modifier.appear(4)) }
            item("groups") {
                Card(Modifier.appear(5)) {
                    val rows = if (events.isNotEmpty()) events.map { e ->
                        Triple(e.type, "${e.typeLabel} · gr. ${e.group}", "${Time.DOW_FULL[Time.date(e.date).dayOfWeek.value - 1]} ${e.start}-${e.end} · s. ${e.room}") to e.freq
                    } else c.groups.map { g -> Triple(g.type, "${g.typeLabel} · gr. ${g.group}", "") to "" }
                    rows.forEach { (t, freq) ->
                        Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min).padding(horizontal = 18.dp, vertical = 14.dp), horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.width(4.dp).fillMaxHeight().clip(RoundedCornerShape(2.dp)).background(P.c.type(t.first).first))
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text(t.second, style = T.t15, color = P.c.ink)
                                if (t.third.isNotBlank()) Text(t.third, style = T.t13, color = P.c.sub)
                            }
                            if (freq.isNotBlank()) Text(freq, style = T.t12b, color = P.c.muted)
                        }
                    }
                }
            }
        }
        val lids = (c.groups.flatMap { it.lecturerIds } + events.flatMap { it.lecturerIds }).distinct().filter { d.lecturers.containsKey(it) }
        if (lids.isNotEmpty()) {
            item("lectL") { SectionLabel("Prowadzący", Modifier.appear(6)) }
            item("lect") {
                Card(Modifier.appear(7)) {
                    lids.forEach { lid ->
                        val l = d.lecturers.getValue(lid)
                        val role = (c.groups.filter { lid in it.lecturerIds }.map { it.typeLabel } + events.filter { lid in it.lecturerIds }.map { it.typeLabel }).distinct().joinToString(", ")
                        Row2(onClick = { vm.push(Dest.Lect(lid)) }, padding = PaddingValues(horizontal = 18.dp, vertical = 12.dp)) {
                            Avatar(l.initials)
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                                Text(l.name, style = T.t15, color = P.c.ink)
                                if (role.isNotBlank()) Text(role, style = T.t12, color = P.c.sub)
                            }
                            Chevron()
                        }
                    }
                }
            }
        }
    }
    if (adding) AddResultDialog(onDismiss = { adding = false }) { name, pts, max -> vm.addManual(c.id, name, pts, max); adding = false }
}

@Composable
private fun TestRow(t: TestBlock, expanded: Boolean, toggle: () -> Unit, onDelete: (() -> Unit)?) {
    val (bp, done) = Grades.blockPoints(t)
    val col = when { !done -> P.c.faint; bp.pct >= .8 -> P.c.green; bp.pct >= .6 -> P.c.blue; else -> P.c.orange }
    Column(
        Modifier.fillMaxWidth().press(.985f, onLongClick = onDelete, onClick = toggle).padding(horizontal = 18.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text(t.name + if (t.manual) "  ✎" else "", style = T.t15, color = P.c.ink, modifier = Modifier.weight(1f))
            Text(if (done) "${Grades.fmtPts(bp.got)}/${Grades.fmtPts(bp.max)}" else "-/${Grades.fmtPts(bp.max)}", style = T.t14b, color = col)
        }
        AnimatedBar(bp.pct.toFloat(), col)
        AnimatedVisibility(expanded, enter = expandVertically(spring(dampingRatio = .8f)) + fadeIn(), exit = shrinkVertically() + fadeOut()) {
            Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min).padding(top = 4.dp)) {
                Box(Modifier.width(2.dp).fillMaxHeight().background(P.c.line))
                Column(
                    Modifier.fillMaxWidth().padding(start = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    t.items.forEach { it ->
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(it.name, style = T.t13, color = P.c.text2)
                            Text(if (it.points == null) "-/${Grades.fmtPts(it.max)}" else "${Grades.fmtPts(it.points)}/${Grades.fmtPts(it.max)}", style = T.t13b, color = P.c.ink)
                        }
                    }
                    if (t.comment.isNotBlank()) Text(t.comment, style = T.t12, color = P.c.muted)
                    if (onDelete != null) Text("Przytrzymaj, aby usunąć", style = T.t12, color = P.c.muted)
                }
            }
        }
    }
}

@Composable
private fun AddResultDialog(onDismiss: () -> Unit, onSave: (String, Double?, Double) -> Unit) {
    var name by remember { mutableStateOf("") }
    var pts by remember { mutableStateOf("") }
    var max by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = P.c.card,
        shape = RoundedCornerShape(28.dp),
        title = { Text("Własny wynik", style = T.h20, color = P.c.ink) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Np. kartkówka, której nie ma jeszcze w USOS. Liczy się do prognozy i synchronizuje przez Firebase.", style = T.t13, color = P.c.sub)
                OutlinedTextField(name, { name = it }, label = { Text("Nazwa") }, singleLine = true, shape = RoundedCornerShape(16.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(pts, { pts = it }, label = { Text("Punkty") }, singleLine = true, modifier = Modifier.weight(1f), shape = RoundedCornerShape(16.dp),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
                    OutlinedTextField(max, { max = it }, label = { Text("Max") }, singleLine = true, modifier = Modifier.weight(1f), shape = RoundedCornerShape(16.dp),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val m = Grades.num(max) ?: return@TextButton
                onSave(name.ifBlank { "Własny wynik" }, Grades.num(pts), m)
            }) { Text("Zapisz", style = T.t14b, color = P.c.blue) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Anuluj", style = T.t14b, color = P.c.sub) } },
    )
}

@Composable
fun LecturerScreen(vm: AppViewModel, d: StudentData, id: String) {
    val l = d.lecturers[id] ?: return
    val ctx = LocalContext.current
    val mon = Time.monday()
    val acts = d.events.filter { id in it.lecturerIds && Time.date(it.date) >= mon && Time.date(it.date) < mon.plusDays(7) }
    val courses = (acts.map { it.courseId } + d.courses.filter { c -> c.groups.any { id in it.lecturerIds } }.map { it.id }).distinct()
    ScreenList(vm, l.name, "Prowadzący", small = true, back = backLabel(vm) to { vm.pop() }) {
        item("head") {
            Row(
                Modifier.appear(0).fillMaxWidth().clip(RoundedCornerShape(26.dp)).background(P.c.card).padding(20.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically,
            ) {
                Avatar(l.initials, 72.dp, T.h24)
                Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    if (l.unit.isNotBlank()) Text(l.unit, style = T.t14.copy(fontWeight = FontWeight.SemiBold), color = P.c.sub)
                    if (l.email.isNotBlank()) Text(l.email, style = T.t13.copy(fontWeight = FontWeight.SemiBold), color = P.c.blue)
                }
            }
        }
        item("btns") {
            Row(Modifier.appear(1), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PrimaryButton("Napisz e-mail", Modifier.weight(1f)) {
                    if (l.email.isNotBlank()) runCatching { ctx.startActivity(Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:${l.email}"))) }
                    else vm.message = "Brak adresu e-mail w USOS"
                }
                PrimaryButton("Kopiuj e-mail", Modifier.weight(1f), dark = false) {
                    ctx.getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText("email", l.email))
                    vm.message = "Skopiowano ${l.email}"
                }
            }
        }
        item("tiles") {
            Row(Modifier.appear(2), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                InfoTile("POKÓJ", l.room, Modifier.weight(1f))
                InfoTile("KONSULTACJE", l.hours, Modifier.weight(1f))
            }
        }
        if (acts.isNotEmpty()) {
            item("schedL") { SectionLabel("Plan prowadzącego · ten tydzień", Modifier.appear(3)) }
            item("sched") {
                Card(Modifier.appear(4)) {
                    acts.forEach { a ->
                        Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min).press(.98f) { vm.openSheet(a) }.padding(horizontal = 18.dp, vertical = 13.dp),
                            horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text("${Time.DOW_SHORT[Time.date(a.date).dayOfWeek.value - 1]} ${a.start}", style = T.t13b, color = P.c.ink, modifier = Modifier.width(64.dp))
                            Box(Modifier.width(4.dp).fillMaxHeight().clip(RoundedCornerShape(2.dp)).background(P.c.type(a.type).first))
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text(a.courseName, style = T.t14.copy(fontWeight = FontWeight.SemiBold), color = P.c.ink)
                                Text("${a.typeLabel} · gr. ${a.group} · s. ${a.room}", style = T.t12, color = P.c.sub)
                            }
                        }
                    }
                }
            }
        }
        if (courses.isNotEmpty()) {
            item("crsL") { SectionLabel("Prowadzi u Ciebie", Modifier.appear(5)) }
            item("crs") {
                Card(Modifier.appear(6)) {
                    courses.forEach { cid ->
                        val name = d.courses.firstOrNull { it.id == cid }?.name ?: acts.firstOrNull { it.courseId == cid }?.courseName ?: cid
                        Row2(onClick = { vm.push(Dest.Course(cid)) }, padding = PaddingValues(horizontal = 18.dp, vertical = 15.dp)) {
                            Text(name, style = T.t15, color = P.c.ink, modifier = Modifier.weight(1f)); Chevron()
                        }
                    }
                }
            }
        }
    }
}
