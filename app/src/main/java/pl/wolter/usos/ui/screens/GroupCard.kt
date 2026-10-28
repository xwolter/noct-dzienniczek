package pl.wolter.usos.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import pl.wolter.usos.data.Cloud
import pl.wolter.usos.data.Settings
import pl.wolter.usos.ui.AppViewModel
import pl.wolter.usos.ui.Tab
import pl.wolter.usos.ui.components.Card
import pl.wolter.usos.ui.components.PrimaryButton
import pl.wolter.usos.ui.components.press
import pl.wolter.usos.ui.theme.P
import pl.wolter.usos.ui.theme.T

/** Karta „Grupa roku” w Więcej: tworzenie, dołączanie kodem, udostępnianie kodu, wyjście. */
@Composable
fun GroupCard(vm: AppViewModel, s: Settings, modifier: Modifier = Modifier) {
    val ctx = LocalContext.current
    val members by vm.repo.groupMembers.collectAsState()
    var dialog by remember { mutableStateOf<String?>(null) }   // "join" | "create" | "leave"

    Card(modifier, padding = PaddingValues(18.dp)) {
        if (!vm.repo.cloud.enabled) {
            Text("Wspólne terminy wymagają Firebase - w tej wersji jest wyłączony.", style = T.t14, color = P.c.sub)
            return@Card
        }
        if (s.groupCode.isBlank()) {
            Text("Wspólne terminy", style = T.h17, color = P.c.ink)
            Text("Jedna osoba z roku wpisuje kolokwium, a widzą je wszyscy z kodem grupy - z przypomnieniem dzień wcześniej.",
                style = T.t13, color = P.c.sub, modifier = Modifier.padding(top = 4.dp, bottom = 14.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PrimaryButton("Dołącz kodem", Modifier.weight(1f)) { dialog = "join" }
                PrimaryButton("Utwórz grupę", Modifier.weight(1f), dark = false, light = P.c.chip) { dialog = "create" }
            }
        } else {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(Modifier.size(44.dp).clip(CircleShape).background(P.c.blueTint), contentAlignment = Alignment.Center) {
                    Text("👥", style = T.h17)
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(s.groupName, style = T.h17, color = P.c.ink)
                    Text(if (members > 0) "$members ${osoby(members)} · ${vm.repo.groupExams.collectAsState().value.size} terminów" else "łączenie…",
                        style = T.t13, color = P.c.sub)
                }
                if (vm.groupBusy) CircularProgressIndicator(Modifier.size(20.dp), color = P.c.blue, strokeWidth = 2.dp)
            }
            // kod - stuknięcie kopiuje
            Column(
                Modifier.padding(vertical = 14.dp).fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(P.c.bg)
                    .press(.97f) {
                        ctx.getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText("kod", Cloud.fmtCode(s.groupCode)))
                        vm.message = "Skopiowano kod ${Cloud.fmtCode(s.groupCode)}"
                    }.padding(vertical = 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text("KOD GRUPY", style = T.t11, color = P.c.muted)
                Text(Cloud.fmtCode(s.groupCode), style = T.num30.copy(letterSpacing = androidx.compose.ui.unit.TextUnit(4f, androidx.compose.ui.unit.TextUnitType.Sp)), color = P.c.ink)
                Text("stuknij, aby skopiować", style = T.t12, color = P.c.muted)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PrimaryButton("Udostępnij kod", Modifier.weight(1f)) {
                    val txt = "Dołącz do grupy „${s.groupName}” w Noct Dzienniczek - wspólne kolokwia i terminy.\nKod: ${Cloud.fmtCode(s.groupCode)}"
                    ctx.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, txt), "Udostępnij kod grupy"))
                }
                PrimaryButton("Opuść", dark = false, light = P.c.chip) { dialog = "leave" }
            }
        }
    }

    when (dialog) {
        "join" -> TextDialog("Dołącz do grupy", "Kod grupy (np. K7M-Q2X)", caps = true, onDismiss = { dialog = null }) { vm.joinGroup(it); dialog = null }
        "create" -> TextDialog("Nowa grupa", "Nazwa, np. Informatyka II rok", caps = false, onDismiss = { dialog = null }) { vm.createGroup(it); dialog = null }
        "leave" -> AlertDialog(
            onDismissRequest = { dialog = null },
            containerColor = P.c.card, shape = RoundedCornerShape(28.dp),
            title = { Text("Opuścić grupę?", style = T.h20, color = P.c.ink) },
            text = { Text("Przestaniesz widzieć wspólne terminy „${s.groupName}”. Możesz wrócić tym samym kodem.", style = T.t14, color = P.c.sub) },
            confirmButton = { TextButton(onClick = { vm.leaveGroup(); dialog = null }) { Text("Opuść", style = T.t14b, color = P.c.red) } },
            dismissButton = { TextButton(onClick = { dialog = null }) { Text("Anuluj", style = T.t14b, color = P.c.sub) } },
        )
    }
}

private fun osoby(n: Int) = when {
    n == 1 -> "osoba"
    n % 10 in 2..4 && n % 100 !in 12..14 -> "osoby"
    else -> "osób"
}

@Composable
private fun TextDialog(title: String, label: String, caps: Boolean, onDismiss: () -> Unit, onOk: (String) -> Unit) {
    var v by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = P.c.card, shape = RoundedCornerShape(28.dp),
        title = { Text(title, style = T.h20, color = P.c.ink) },
        text = {
            OutlinedTextField(
                v, { v = if (caps) it.uppercase().take(7) else it.take(40) }, label = { Text(label) }, singleLine = true,
                shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(capitalization = if (caps) KeyboardCapitalization.Characters else KeyboardCapitalization.Sentences),
                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = P.c.blue, unfocusedBorderColor = P.c.line, focusedTextColor = P.c.ink, unfocusedTextColor = P.c.ink),
            )
        },
        confirmButton = { TextButton(onClick = { onOk(v.trim()) }) { Text("OK", style = T.t14b, color = P.c.blue) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Anuluj", style = T.t14b, color = P.c.sub) } },
    )
}

/** Pasek nad listą Terminy: status grupy albo zachęta do dołączenia. */
@Composable
fun GroupStrip(vm: AppViewModel, s: Settings, modifier: Modifier = Modifier) {
    val members by vm.repo.groupMembers.collectAsState()
    Row(
        modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(if (s.groupCode.isNotBlank()) P.c.blueTint else P.c.card)
            .press(.98f) { vm.selectTab(Tab.Wiecej) }.padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text("👥", style = T.t15)
        if (s.groupCode.isNotBlank()) {
            Text("Grupa „${s.groupName}”", style = T.t14b.copy(fontWeight = FontWeight.Bold), color = P.c.blue, modifier = Modifier.weight(1f))
            Text(if (members > 0) "$members ${osoby(members)}" else Cloud.fmtCode(s.groupCode), style = T.t12b, color = P.c.blue)
        } else {
            Text("Dołącz do grupy roku - wspólne kolokwia", style = T.t14, color = P.c.text2, modifier = Modifier.weight(1f))
            Text("›", style = T.h20, color = P.c.faint)
        }
    }
}
