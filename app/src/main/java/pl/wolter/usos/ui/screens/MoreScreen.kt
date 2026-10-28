package pl.wolter.usos.ui.screens

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import pl.wolter.usos.BuildConfig
import pl.wolter.usos.data.ClassType
import pl.wolter.usos.data.Grades
import pl.wolter.usos.data.Settings
import pl.wolter.usos.data.StudentData
import pl.wolter.usos.data.Time
import pl.wolter.usos.ui.AppViewModel
import pl.wolter.usos.ui.ScreenList
import pl.wolter.usos.ui.components.Avatar
import pl.wolter.usos.ui.components.Card
import pl.wolter.usos.ui.components.OneSwitch
import pl.wolter.usos.ui.components.Row2
import pl.wolter.usos.ui.components.SectionLabel
import pl.wolter.usos.ui.components.Segmented
import pl.wolter.usos.ui.components.appear
import pl.wolter.usos.ui.components.press
import pl.wolter.usos.ui.theme.P
import pl.wolter.usos.ui.theme.T
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val TYPE_LABEL = mapOf(
    ClassType.WYK to "Wykład", ClassType.CW to "Ćwiczenia", ClassType.LAB to "Laboratorium",
    ClassType.LEK to "Lektorat", ClassType.EGZ to "Egzamin", ClassType.SEM to "Seminarium", ClassType.INNE to "Inne",
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MoreScreen(vm: AppViewModel, d: StudentData, s: Settings) {
    val st = d.student
    val cloud by vm.repo.cloud.status.collectAsState()
    ScreenList(vm, "Więcej", "Konto, dokumenty, ustawienia") {
        item("me") {
            Row(
                Modifier.appear(0).fillMaxWidth().clip(RoundedCornerShape(26.dp)).background(P.c.card).padding(18.dp),
                horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically,
            ) {
                Avatar("${st.firstName.take(1)}${st.lastName.take(1)}", 56.dp, T.h20)
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text("${st.firstName} ${st.lastName}", style = T.h17, color = P.c.ink)
                    Text("${st.programme} · ${st.index}", style = T.t13, color = P.c.sub)
                }
            }
        }
        item("card") {
            val until = st.cardValidUntil?.let { runCatching { Time.date(it) }.getOrNull() }
            val valid = until != null && Time.daysUntil(until) >= 0
            Column(
                Modifier.appear(1).fillMaxWidth().clip(RoundedCornerShape(26.dp)).background(P.c.hero).padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp),
            ) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("mLEGITYMACJA", style = T.t13b.copy(letterSpacing = androidx.compose.ui.unit.TextUnit(.5f, androidx.compose.ui.unit.TextUnitType.Sp)), color = Color.White.copy(alpha = .7f))
                    Text(if (until == null) "Brak danych" else if (valid) "Ważna" else "Nieważna", style = T.t12b, color = Color.White,
                        modifier = Modifier.clip(RoundedCornerShape(99.dp)).background(if (until == null) Color(0xFF4A4E58) else if (valid) Color(0xFF1E9E57) else P.c.red).padding(horizontal = 10.dp, vertical = 4.dp))
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Bottom) {
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text("ważna do", style = T.t13, color = Color.White.copy(alpha = .7f))
                        Text(until?.let { "%02d.%02d.%d".format(it.dayOfMonth, it.monthValue, it.year) } ?: "-", style = T.h20, color = Color.White)
                    }
                    PseudoQr(st.index)
                }
            }
        }
        item("rows") {
            Card(Modifier.appear(2)) {
                d.moreRows.forEach { m ->
                    Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 15.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(m.label, style = T.t15, color = P.c.ink)
                            Text(m.sub, style = T.t12, color = P.c.muted)
                        }
                        Text(m.value, style = T.t13b, color = P.c.kind(m.kind))
                    }
                }
            }
        }
        item("filtersL") { SectionLabel("Plan · typy zajęć", Modifier.appear(3)) }
        item("filters") {
            val types = (d.events.map { it.type } + listOf(ClassType.WYK, ClassType.CW, ClassType.LAB, ClassType.LEK, ClassType.EGZ)).distinct()
            Card(Modifier.appear(4), padding = PaddingValues(16.dp)) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    types.forEach { t ->
                        val on = t !in s.hidden
                        val (color, tint) = P.c.type(t)
                        val bg by animateColorAsState(if (on) tint else P.c.chip, label = "fb")
                        val fg by animateColorAsState(if (on) color else P.c.muted, label = "ff")
                        val dot by animateColorAsState(if (on) color else P.c.faint, label = "fd")
                        Row(
                            Modifier.height(36.dp).clip(RoundedCornerShape(99.dp)).background(bg)
                                .press(.92f) { vm.repo.updateSettings { it.copy(hidden = if (on) it.hidden + t else it.hidden - t) } }
                                .padding(horizontal = 14.dp),
                            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Box(Modifier.size(8.dp).clip(CircleShape).background(dot))
                            Text(TYPE_LABEL[t]!!, style = T.t13b, color = fg)
                        }
                    }
                }
            }
        }
        item("toggles") {
            Card(Modifier.appear(5)) {
                Toggle("Przypomnienie 15 min przed", "Z salą i budynkiem w treści", s.notif) { vm.repo.updateSettings { it.copy(notif = !it.notif) } }
                Toggle("Powiadomienia o wynikach", "Nowe punkty i oceny w protokołach", s.grades) { vm.repo.updateSettings { it.copy(grades = !it.grades) } }
                Toggle("Subskrypcja kalendarza", "Link webcal z tt/upcoming_share", s.webcal) { vm.repo.updateSettings { it.copy(webcal = !it.webcal) } }
            }
        }
        item("groupL") { SectionLabel("Grupa roku · wspólne terminy", Modifier.appear(6)) }
        item("group") { GroupCard(vm, s, Modifier.appear(6)) }
        item("themeL") { SectionLabel("Wygląd", Modifier.appear(6)) }
        item("theme") { Segmented(listOf("Systemowy", "Jasny", "Ciemny"), s.theme, { t -> vm.repo.updateSettings { it.copy(theme = t) } }, Modifier.appear(6)) }
        item("thL") { SectionLabel("Progi prognozy oceny", Modifier.appear(7)) }
        item("th") {
            Card(Modifier.appear(7)) {
                listOf("5", "4,5", "4", "3,5", "3").forEachIndexed { i, g ->
                    Row(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        pl.wolter.usos.ui.components.GradeBadge(g, 44.dp, 34.dp, T.t14b)
                        Text("od ${(s.thresholds[i] * 100).toInt()}% punktów", style = T.t15, color = P.c.ink, modifier = Modifier.weight(1f))
                        Stepper("−") { vm.repo.updateSettings { it.copy(thresholds = it.thresholds.toMutableList().also { l -> l[i] = (l[i] - .05).coerceAtLeast(.05) }) } }
                        Stepper("+") { vm.repo.updateSettings { it.copy(thresholds = it.thresholds.toMutableList().also { l -> l[i] = (l[i] + .05).coerceAtMost(1.0) }) } }
                    }
                }
                Text("USOS nie zwraca prognozy - liczymy ją z procentu punktów. Progi i własne wyniki synchronizują się przez Firebase.",
                    style = T.t12, color = P.c.sub, modifier = Modifier.padding(horizontal = 18.dp, vertical = 10.dp))
            }
        }
        item("syncL") { SectionLabel("Synchronizacja", Modifier.appear(8)) }
        item("sync") {
            Card(Modifier.appear(8)) {
                Row2 {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text("Firebase", style = T.t15, color = P.c.ink)
                        Text(cloud, style = T.t12, color = P.c.sub)
                    }
                    Box(Modifier.size(10.dp).clip(CircleShape).background(if (cloud == "Zsynchronizowano") P.c.green else P.c.faint))
                }
                Row2(onClick = { vm.refresh() }) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(if (d.demo) "Odśwież dane demo" else "Pobierz z USOS teraz", style = T.t15, color = P.c.blue)
                        Text("Ostatnio: " + SimpleDateFormat("d MMM, HH:mm", Locale.forLanguageTag("pl")).format(Date(d.fetchedAt)), style = T.t12, color = P.c.sub)
                    }
                }
                Row2(onClick = { vm.repo.logout() }) {
                    Text(if (d.demo) "Zakończ tryb demo" else "Wyloguj z USOS", style = T.t15, color = P.c.red, modifier = Modifier.weight(1f))
                }
            }
        }
        item("ver") {
            Column(Modifier.fillMaxWidth().padding(8.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text("Noct Dzienniczek ${BuildConfig.VERSION_NAME}", style = T.t13b, color = P.c.sub)
                Text("autor: xwolter", style = T.t12, color = P.c.muted)
            }
        }
    }
}

@Composable
private fun Toggle(label: String, sub: String, on: Boolean, onClick: () -> Unit) {
    Row2(onClick = onClick) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(label, style = T.t15, color = P.c.ink)
            Text(sub, style = T.t12, color = P.c.sub)
        }
        OneSwitch(on)
    }
}

@Composable
private fun Stepper(sym: String, onClick: () -> Unit) {
    Box(Modifier.size(34.dp).clip(CircleShape).background(P.c.chip).press(.85f, onClick = onClick), contentAlignment = Alignment.Center) {
        Text(sym, style = T.h17, color = P.c.ink)
    }
}

/** Dekoracyjny „kod” legitymacji generowany z numeru albumu. */
@Composable
private fun PseudoQr(seed: String) {
    val n = 9
    var h = seed.hashCode().toLong() or 1L
    val bits = BooleanArray(n * n) { h = h * 6364136223846793005L + 1442695040888963407L; (h ushr 33) and 1L == 1L }
    Box(Modifier.size(64.dp).clip(RoundedCornerShape(14.dp)).background(Color.White).padding(6.dp)) {
        Canvas(Modifier.size(52.dp)) {
            val c = size.width / n
            fun finder(x: Int, y: Int) {
                drawRoundRect(Color(0xFF111318), Offset(x * c, y * c), Size(c * 3, c * 3), CornerRadius(c * .6f))
                drawRoundRect(Color.White, Offset(x * c + c * .6f, y * c + c * .6f), Size(c * 1.8f, c * 1.8f), CornerRadius(c * .3f))
                drawRect(Color(0xFF111318), Offset(x * c + c, y * c + c), Size(c, c))
            }
            for (y in 0 until n) for (x in 0 until n) {
                val inFinder = (x < 3 && y < 3) || (x >= n - 3 && y < 3) || (x < 3 && y >= n - 3)
                if (!inFinder && bits[y * n + x]) drawRoundRect(Color(0xFF111318), Offset(x * c, y * c), Size(c * .92f, c * .92f), CornerRadius(c * .25f))
            }
            finder(0, 0); finder(n - 3, 0); finder(0, n - 3)
        }
    }
}
