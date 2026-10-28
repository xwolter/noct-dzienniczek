package pl.wolter.usos.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import pl.wolter.usos.data.usos.UNIVERSITIES
import pl.wolter.usos.ui.AppViewModel
import pl.wolter.usos.ui.components.PrimaryButton
import pl.wolter.usos.ui.components.appear
import pl.wolter.usos.ui.components.press
import pl.wolter.usos.ui.theme.P
import pl.wolter.usos.ui.theme.T

@Composable
fun LoginScreen(vm: AppViewModel, openUrl: (String) -> Unit) {
    var uniIdx by rememberSaveable { mutableStateOf(0) }
    var custom by rememberSaveable { mutableStateOf("") }
    val uni = UNIVERSITIES[uniIdx]
    val base = if (custom.isNotBlank()) custom.trim() else uni.baseUrl
    val saved = remember(base) { vm.repo.savedKey(base) ?: vm.repo.defaultKey() }
    var ck by rememberSaveable(base) { mutableStateOf(saved.first) }
    var cs by rememberSaveable(base) { mutableStateOf(saved.second) }
    var showKeys by rememberSaveable { mutableStateOf(saved.first.isBlank()) }

    Box(Modifier.fillMaxSize().background(P.c.bg)) {
        AuroraBackground()
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).statusBarsPadding().navigationBarsPadding().imePadding()
                .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Spacer(Modifier.height(48.dp))
            Box(
                Modifier.appear(0).size(72.dp).clip(RoundedCornerShape(24.dp)).background(P.c.blue),
                contentAlignment = Alignment.Center,
            ) { Text("noct", style = T.t16.copy(fontWeight = FontWeight.ExtraBold), color = Color.White) }
            Column(Modifier.appear(1), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("NOCT DZIENNICZEK", style = T.cap, color = P.c.blue)
                Text("Twój dziennik\nstudenta", style = T.hero, color = P.c.ink)
                Text("Plan, punkty, oceny i prognoza średniej - prosto z USOS API.", style = T.t14, color = P.c.sub)
            }
            Spacer(Modifier.height(4.dp))
            Text("Uczelnia", style = T.t13b, color = P.c.sub, modifier = Modifier.appear(2).padding(start = 10.dp))
            Column(Modifier.appear(3).fillMaxWidth().clip(RoundedCornerShape(26.dp)).background(P.c.card).padding(vertical = 6.dp)) {
                UNIVERSITIES.forEachIndexed { i, u ->
                    val on = i == uniIdx && custom.isBlank()
                    val bg by animateColorAsState(if (on) P.c.nowRow else Color.Transparent, label = "ub")
                    Row(
                        Modifier.fillMaxWidth().background(bg).press(.98f) { uniIdx = i; custom = "" }.padding(horizontal = 18.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Text(u.code, style = T.t13b, color = if (on) P.c.blue else P.c.sub, modifier = Modifier.size(width = 58.dp, height = 18.dp))
                        Text(u.name, style = T.t15, color = P.c.ink, modifier = Modifier.weight(1f))
                        val sc by androidx.compose.animation.core.animateFloatAsState(if (on) 1f else 0f, androidx.compose.animation.core.spring(dampingRatio = .5f), label = "chk")
                        Box(Modifier.size(20.dp).graphicsLayer { scaleX = sc; scaleY = sc }.clip(CircleShape).background(P.c.blue), contentAlignment = Alignment.Center) {
                            Text("✓", style = T.t11, color = Color.White)
                        }
                    }
                }
            }
            Text(
                if (showKeys) "Ukryj klucz aplikacji ▴" else "Klucz aplikacji USOS API ▾", style = T.t13b, color = P.c.blue,
                modifier = Modifier.padding(start = 10.dp).press { showKeys = !showKeys },
            )
            AnimatedVisibility(showKeys, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
                Column(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(26.dp)).background(P.c.card).padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Text("Klucz (consumer key + secret) zarejestrujesz za darmo na ${base}developers/ - wystarczy raz, zapamiętamy go.",
                        style = T.t13, color = P.c.sub)
                    val colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = P.c.blue, unfocusedBorderColor = P.c.line, focusedTextColor = P.c.ink, unfocusedTextColor = P.c.ink)
                    OutlinedTextField(ck, { ck = it }, label = { Text("Consumer key") }, singleLine = true, shape = RoundedCornerShape(16.dp), colors = colors, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(cs, { cs = it }, label = { Text("Consumer secret") }, singleLine = true, shape = RoundedCornerShape(16.dp), colors = colors, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(custom, { custom = it }, label = { Text("Inna instalacja (URL usosapps)") }, singleLine = true, shape = RoundedCornerShape(16.dp), colors = colors, modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("https://usosapps.twoja-uczelnia.pl/") })
                }
            }
            Spacer(Modifier.height(4.dp))
            Box(Modifier.appear(4)) {
                PrimaryButton(if (vm.loginBusy) "" else "Zaloguj przez USOS", Modifier.fillMaxWidth()) {
                    if (!vm.loginBusy) vm.startLogin(base, ck, cs, openUrl)
                }
                if (vm.loginBusy) CircularProgressIndicator(Modifier.size(22.dp).align(Alignment.Center), color = P.c.primaryBtnFg, strokeWidth = 2.5.dp)
            }
            PrimaryButton("Wypróbuj na danych demo", Modifier.fillMaxWidth().appear(5), dark = false) { vm.repo.enterDemo() }
            Text("Logowanie odbywa się na stronie Twojej uczelni (OAuth). Aplikacja nie widzi Twojego hasła.",
                style = T.t12, color = P.c.muted, modifier = Modifier.padding(horizontal = 10.dp))
            Text("Noct Dzienniczek · autor: xwolter", style = T.t12b, color = P.c.muted,
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
            Spacer(Modifier.height(24.dp))
        }
        // zasłona pod paskiem stanu
        Box(Modifier.fillMaxWidth().background(androidx.compose.ui.graphics.Brush.verticalGradient(listOf(P.c.bg, P.c.bg.copy(alpha = 0f)))).statusBarsPadding().height(16.dp))
    }
}

/** Delikatnie płynące kolorowe plamy w tle (jak tapeta z projektu widżetów). */
@Composable
private fun AuroraBackground() {
    val inf = rememberInfiniteTransition(label = "aurora")
    val t by inf.animateFloat(0f, 1f, infiniteRepeatable(tween(9000), RepeatMode.Reverse), label = "t")
    val a = if (P.c.dark) .35f else .8f
    Canvas(Modifier.fillMaxSize().blur(80.dp)) {
        drawCircle(Color(0xFFC9D6F2).copy(alpha = a), size.width * .6f, Offset(size.width * (.2f + .3f * t), size.height * .12f))
        drawCircle(Color(0xFFE6DDF0).copy(alpha = a), size.width * .5f, Offset(size.width * (.9f - .3f * t), size.height * (.3f + .05f * t)))
        drawCircle(Color(0xFFF3E6DC).copy(alpha = a * .8f), size.width * .45f, Offset(size.width * .5f, size.height * (.05f + .1f * t)))
    }
}

@Composable
fun LoadingScreen(onRetry: () -> Unit = {}, onLogout: () -> Unit = {}) {
    var late by remember { mutableStateOf(false) }
    androidx.compose.runtime.LaunchedEffect(Unit) { kotlinx.coroutines.delay(8000); late = true }
    val inf = rememberInfiniteTransition(label = "load")
    val s by inf.animateFloat(.92f, 1.06f, infiniteRepeatable(tween(700), RepeatMode.Reverse), label = "s")
    Box(Modifier.fillMaxSize().background(P.c.bg), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(18.dp)) {
            Box(
                Modifier.size(72.dp).graphicsLayer { scaleX = s; scaleY = s }.clip(RoundedCornerShape(24.dp)).background(P.c.blue),
                contentAlignment = Alignment.Center,
            ) { Text("noct", style = T.t16.copy(fontWeight = FontWeight.ExtraBold), color = Color.White) }
            Text("Pobieram plan i wyniki…", style = T.t14, color = P.c.sub)
            AnimatedVisibility(late, enter = fadeIn() + expandVertically()) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PrimaryButton("Spróbuj ponownie", onClick = onRetry)
                    PrimaryButton("Wyloguj", dark = false, onClick = onLogout)
                }
            }
        }
    }
}
