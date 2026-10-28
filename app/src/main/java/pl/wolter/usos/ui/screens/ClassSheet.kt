package pl.wolter.usos.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.clickable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import pl.wolter.usos.data.ClassEvent
import pl.wolter.usos.data.StudentData
import pl.wolter.usos.data.Time
import pl.wolter.usos.ui.AppViewModel
import pl.wolter.usos.ui.Dest
import pl.wolter.usos.ui.components.Avatar
import pl.wolter.usos.ui.components.Chevron
import pl.wolter.usos.ui.components.InfoTile
import pl.wolter.usos.ui.components.PrimaryButton
import pl.wolter.usos.ui.components.press
import pl.wolter.usos.ui.theme.P
import pl.wolter.usos.ui.theme.T

/** Arkusz szczegółów zajęć: sprężyste wysunięcie, przeciągnięcie w dół zamyka. */
@Composable
fun ClassSheet(vm: AppViewModel, d: StudentData) {
    val e = vm.sheet
    val last = remember { mutableListOf<ClassEvent>() }
    if (e != null) { last.clear(); last.add(e) }
    val shown = e ?: last.firstOrNull()

    AnimatedVisibility(e != null, enter = fadeIn(tween(220)), exit = fadeOut(tween(220))) {
        Box(Modifier.fillMaxSize().background(P.c.scrim).clickable(MutableInteractionSource(), null) { vm.closeSheet() })
    }
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
        AnimatedVisibility(
            e != null,
            enter = slideInVertically(spring(dampingRatio = .78f, stiffness = 320f)) { it },
            exit = slideOutVertically(tween(220)) { it },
        ) {
            if (shown != null) SheetBody(vm, d, shown)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SheetBody(vm: AppViewModel, d: StudentData, e: ClassEvent) {
    val ctx = LocalContext.current
    val drag = remember(e.id) { Animatable(0f) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(e.id) { drag.snapTo(0f) }
    val (color, tint) = P.c.type(e.type)
    val l = e.lecturerIds.firstNotNullOfOrNull { d.lecturers[it] }
    val date = Time.date(e.date)
    Column(
        Modifier
            .navigationBarsPadding()
            .padding(8.dp)
            .fillMaxWidth()
            .heightIn(max = (LocalConfiguration.current.screenHeightDp * .86f).dp)
            .graphicsLayer { translationY = drag.value }
            .clip(RoundedCornerShape(34.dp))
            .background(P.c.card)
            .verticalScroll(rememberScrollState())
            .padding(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 22.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Box(
            Modifier.fillMaxWidth().height(20.dp).draggable(
                rememberDraggableState { dy -> scope.launch { drag.snapTo((drag.value + dy).coerceAtLeast(0f)) } },
                Orientation.Vertical,
                onDragStopped = { v ->
                    if (drag.value > 220f || v > 1800f) vm.closeSheet()
                    else drag.animateTo(0f, spring(dampingRatio = .6f))
                },
            ),
            contentAlignment = Alignment.Center,
        ) {
            Box(Modifier.width(36.dp).height(4.dp).clip(RoundedCornerShape(2.dp)).background(P.c.dash))
        }
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("${e.typeLabel}${if (e.group.isNotBlank()) " · gr. ${e.group}" else ""}", style = T.t11, color = color,
                modifier = Modifier.clip(RoundedCornerShape(99.dp)).background(tint).padding(horizontal = 10.dp, vertical = 4.dp))
            Text(e.courseName, style = T.h24, color = P.c.ink)
            Text("${Time.DOW_FULL[date.dayOfWeek.value - 1]}, ${Time.dayMonth(date)} · ${e.start}-${e.end}", style = T.t14, color = P.c.sub)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            InfoTile("SALA", e.room, Modifier.weight(1f), bg = P.c.bg)
            InfoTile("CZĘSTOTLIWOŚĆ", e.freq.ifBlank { "wg planu" }, Modifier.weight(1f), bg = P.c.bg)
        }
        // „mapa” budynku → nawigacja w Mapach Google
        Row(
            Modifier.fillMaxWidth().height(96.dp).clip(RoundedCornerShape(20.dp))
                .background(Brush.linearGradient(listOf(P.c.blueTint, if (P.c.dark) Color(0xFF232838) else Color(0xFFE8ECF3))))
                .press(.97f) {
                    runCatching { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0?q=" + Uri.encode("${e.building} ${d.student.university}")))) }
                }
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
                Text(e.building.ifBlank { "Budynek" }, style = T.t14b, color = P.c.ink)
                Text("mapa · services/geo", style = T.t12, color = P.c.text2)
            }
            Text("Nawiguj", style = T.t13b, color = P.c.blue)
        }
        if (l != null) {
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(P.c.bg)
                    .press(.97f) { vm.push(Dest.Lect(l.id)) }.padding(horizontal = 14.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically,
            ) {
                Avatar(l.initials, 40.dp)
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                    Text(l.name, style = T.t15, color = P.c.ink)
                    if (l.hours.isNotBlank()) Text("konsultacje: ${l.hours}", style = T.t12, color = P.c.sub)
                }
                Chevron()
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Kolejne terminy", style = T.t13b, color = P.c.sub)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                (vm.sheetDates.ifEmpty { listOf("ładowanie…") }).forEach {
                    Text(it, style = T.t13.copy(fontWeight = FontWeight.SemiBold), color = P.c.ink,
                        modifier = Modifier.clip(RoundedCornerShape(99.dp)).background(P.c.bg).padding(horizontal = 12.dp, vertical = 7.dp))
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PrimaryButton("Przedmiot i wyniki", Modifier.weight(1f)) { vm.push(Dest.Course(e.courseId)) }
            Box(
                Modifier.height(48.dp).clip(RoundedCornerShape(24.dp)).background(P.c.chip).press(.95f) { vm.closeSheet() }.padding(horizontal = 20.dp),
                contentAlignment = Alignment.Center,
            ) { Text("Zamknij", style = T.t14b, color = P.c.ink) }
        }
    }
}
