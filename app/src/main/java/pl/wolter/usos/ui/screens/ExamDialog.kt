package pl.wolter.usos.ui.screens

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TimePickerDefaults
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import pl.wolter.usos.data.ClassType
import pl.wolter.usos.data.MyExam
import pl.wolter.usos.data.StudentData
import pl.wolter.usos.data.Time
import pl.wolter.usos.ui.AppViewModel
import pl.wolter.usos.ui.components.OneSwitch
import pl.wolter.usos.ui.components.press
import pl.wolter.usos.ui.theme.P
import pl.wolter.usos.ui.theme.T
import java.time.Instant
import java.time.ZoneOffset

val EXAM_KINDS = listOf("Kolokwium", "Egzamin", "Kartkówka", "Projekt", "Inne")

/** Dodawanie / edycja własnego terminu: typ, przedmiot, data, godzina, sala, notatka. */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ExamDialog(vm: AppViewModel, d: StudentData, initial: MyExam) {
    var m by remember(initial.id) { mutableStateOf(initial) }
    var pickDate by remember { mutableStateOf(false) }
    var pickTime by remember { mutableStateOf(false) }
    var courseMenu by remember { mutableStateOf(false) }
    val editing = initial.id.isNotBlank()
    val settings by vm.repo.settings.collectAsState()
    val inGroup = settings.groupCode.isNotBlank()
    // nowe terminy w grupie domyślnie idą do wszystkich; terminy grupy zawsze zostają w grupie
    var share by remember(initial.id) { mutableStateOf(initial.shared || (!editing && inGroup)) }
    val fieldColors = OutlinedTextFieldDefaults.colors(
        focusedBorderColor = P.c.blue, unfocusedBorderColor = P.c.line, focusedTextColor = P.c.ink, unfocusedTextColor = P.c.ink,
    )

    AlertDialog(
        onDismissRequest = { vm.closeExam() },
        containerColor = P.c.card,
        shape = RoundedCornerShape(30.dp),
        title = { Text(if (editing) "Edytuj termin" else "Nowy termin", style = T.h20, color = P.c.ink) },
        text = {
            Column(Modifier.heightIn(max = 520.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                // typ terminu
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    EXAM_KINDS.forEach { k ->
                        val on = m.kind == k
                        val (col, tint) = P.c.type(if (k == "Projekt") ClassType.LAB else ClassType.EGZ)
                        val bg by animateColorAsState(if (on) tint else P.c.chip, label = "kb")
                        val fg by animateColorAsState(if (on) col else P.c.text2, label = "kf")
                        Text(k, style = T.t13b, color = fg, modifier = Modifier.clip(RoundedCornerShape(99.dp)).background(bg)
                            .press(.92f) { m = m.copy(kind = k) }.padding(horizontal = 12.dp, vertical = 8.dp))
                    }
                }
                // przedmiot
                Box {
                    Field("PRZEDMIOT", m.courseName.ifBlank { "Wybierz przedmiot" }) { courseMenu = true }
                    DropdownMenu(courseMenu, { courseMenu = false }, Modifier.background(P.c.card)) {
                        d.courses.forEach { c ->
                            DropdownMenuItem(
                                text = { Text(c.name, style = T.t14, color = P.c.ink, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                                onClick = { m = m.copy(courseId = c.id, courseName = c.name); courseMenu = false },
                            )
                        }
                    }
                }
                // data i godzina
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    val date = Time.date(m.date)
                    Field("DATA", "${Time.DOW_SHORT[date.dayOfWeek.value - 1]}, ${Time.dayMonth(date)} ${date.year}", Modifier.weight(1f)) { pickDate = true }
                    Field("GODZINA", m.time.ifBlank { "-" }, Modifier.weight(.7f)) { pickTime = true }
                }
                OutlinedTextField(m.room, { m = m.copy(room = it) }, label = { Text("Sala (opcjonalnie)") }, singleLine = true,
                    shape = RoundedCornerShape(16.dp), colors = fieldColors, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(m.note, { m = m.copy(note = it) }, label = { Text("Notatka, np. zakres materiału") },
                    shape = RoundedCornerShape(16.dp), colors = fieldColors, modifier = Modifier.fillMaxWidth(), maxLines = 3)
                if (inGroup && !initial.shared) {
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(P.c.bg).press(.98f) { share = !share }
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text("Udostępnij grupie", style = T.t15, color = P.c.ink)
                            Text("„${settings.groupName}” - zobaczą wszyscy z kodem", style = T.t12, color = P.c.sub)
                        }
                        OneSwitch(share)
                    }
                }
                if (initial.shared) {
                    Text("Termin grupy „${settings.groupName}” · dodał(a) ${initial.author.ifBlank { "ktoś z roku" }}. Zmiany zobaczą wszyscy.",
                        style = T.t12, color = P.c.sub)
                }
                if (editing) {
                    Text(if (initial.shared) "Usuń termin z grupy" else "Usuń termin", style = T.t14b, color = P.c.red,
                        modifier = Modifier.clip(RoundedCornerShape(12.dp)).press { vm.deleteExam(initial) }.padding(vertical = 6.dp))
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                if (m.courseName.isBlank()) vm.message = "Wybierz przedmiot" else vm.saveExam(m, share)
            }) { Text("Zapisz", style = T.t14b, color = P.c.blue) }
        },
        dismissButton = { TextButton(onClick = { vm.closeExam() }) { Text("Anuluj", style = T.t14b, color = P.c.sub) } },
    )

    if (pickDate) {
        val state = rememberDatePickerState(
            initialSelectedDateMillis = Time.date(m.date).atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli(),
        )
        DatePickerDialog(
            onDismissRequest = { pickDate = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let { ms ->
                        m = m.copy(date = Instant.ofEpochMilli(ms).atZone(ZoneOffset.UTC).toLocalDate().toString())
                    }
                    pickDate = false
                }) { Text("OK", style = T.t14b, color = P.c.blue) }
            },
            dismissButton = { TextButton(onClick = { pickDate = false }) { Text("Anuluj", style = T.t14b, color = P.c.sub) } },
        ) { DatePicker(state = state, showModeToggle = false) }
    }

    if (pickTime) {
        val (h, min) = m.time.split(':').mapNotNull { it.toIntOrNull() }.let { if (it.size == 2) it[0] to it[1] else 10 to 0 }
        val state = rememberTimePickerState(h, min, is24Hour = true)
        val colors = TimePickerDefaults.colors(
            clockDialColor = P.c.bg, selectorColor = P.c.blue,
            clockDialSelectedContentColor = androidx.compose.ui.graphics.Color.White, clockDialUnselectedContentColor = P.c.ink,
            timeSelectorSelectedContainerColor = P.c.blueTint, timeSelectorSelectedContentColor = P.c.blue,
            timeSelectorUnselectedContainerColor = P.c.chip, timeSelectorUnselectedContentColor = P.c.ink,
        )
        AlertDialog(
            onDismissRequest = { pickTime = false },
            containerColor = P.c.card,
            title = { Text("Godzina", style = T.h20, color = P.c.ink) },
            text = { Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { TimePicker(state, colors = colors) } },
            confirmButton = {
                TextButton(onClick = { m = m.copy(time = "%02d:%02d".format(state.hour, state.minute)); pickTime = false }) {
                    Text("OK", style = T.t14b, color = P.c.blue)
                }
            },
            dismissButton = {
                TextButton(onClick = { m = m.copy(time = ""); pickTime = false }) { Text("Bez godziny", style = T.t14b, color = P.c.sub) }
            },
        )
    }
}

/** Klikane pole w stylu kafelka (etykieta + wartość). */
@Composable
private fun Field(label: String, value: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Column(
        modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(P.c.bg).press(.97f, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(label, style = T.t11, color = P.c.muted)
        Text(value, style = T.t15, color = if (value == "-") P.c.muted else P.c.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** Wiersz terminu na listach (Terminy, przedmiot). Własne mają znaczek ✎ i otwierają edycję. */
@Composable
fun ExamRow(e: pl.wolter.usos.data.Exam, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val date = Time.date(e.date)
    Row(
        modifier.fillMaxWidth().press(.98f, onClick = onClick).padding(horizontal = 18.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Column(
            Modifier.width(48.dp).clip(RoundedCornerShape(16.dp)).background(if (e.own) P.c.type(ClassType.EGZ).second else P.c.bg)
                .padding(horizontal = 10.dp, vertical = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text("${date.dayOfMonth}", style = T.h17, color = if (e.own) P.c.red else P.c.ink)
            Text(Time.MON_SHORT[date.monthValue - 1], style = T.t11, color = P.c.sub)
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(e.name + if (e.shared) "  👥" else if (e.own) "  ✎" else "", style = T.t15, color = P.c.ink)
            if (e.meta.isNotBlank()) Text(e.meta, style = T.t13, color = P.c.sub, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        Text(Time.daysLabel(Time.daysUntil(date)), style = T.t12b, color = if (Time.daysUntil(date) <= 3) P.c.red else P.c.orange)
    }
}

@Composable
fun AddExamButton(text: String = "+  Dodaj kolokwium lub termin", onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().height(52.dp).clip(RoundedCornerShape(26.dp)).background(P.c.primaryBtn).press(.97f, onClick = onClick),
        horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically,
    ) { Text(text, style = T.t14b, color = P.c.primaryBtnFg) }
}
