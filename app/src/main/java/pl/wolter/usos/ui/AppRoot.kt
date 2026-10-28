package pl.wolter.usos.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsTopHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import pl.wolter.usos.data.Mode
import pl.wolter.usos.data.Settings
import pl.wolter.usos.data.StudentData
import pl.wolter.usos.ui.components.press
import pl.wolter.usos.ui.screens.ClassSheet
import pl.wolter.usos.ui.screens.CourseScreen
import pl.wolter.usos.ui.screens.CoursesScreen
import pl.wolter.usos.ui.screens.ExamDialog
import pl.wolter.usos.ui.screens.GradesScreen
import pl.wolter.usos.ui.screens.LecturerScreen
import pl.wolter.usos.ui.screens.LoadingScreen
import pl.wolter.usos.ui.screens.LoginScreen
import pl.wolter.usos.ui.screens.MoreScreen
import pl.wolter.usos.ui.screens.PlanScreen
import pl.wolter.usos.ui.screens.StartScreen
import pl.wolter.usos.ui.theme.P
import pl.wolter.usos.ui.theme.T

/** Klucz ekranu dla AnimatedContent: karta + głębokość stosu. */
data class ScreenKey(val tab: Tab, val dest: Dest?, val depth: Int)

@Composable
fun AppRoot(vm: AppViewModel, mode: Mode, data: StudentData?, settings: Settings, openUrl: (String) -> Unit) {
    Box(Modifier.fillMaxSize().background(P.c.bg)) {
        AnimatedContent(
            targetState = when {
                mode == Mode.NONE -> 0
                data == null -> 1
                else -> 2
            },
            transitionSpec = { (fadeIn(tween(500)) + scaleIn(initialScale = .94f)) togetherWith fadeOut(tween(250)) },
            label = "root",
        ) { s ->
            when (s) {
                0 -> LoginScreen(vm, openUrl)
                1 -> LoadingScreen(onRetry = { vm.refresh() }, onLogout = { vm.repo.logout() })
                else -> data?.let { Main(vm, it, settings) }
            }
        }
        Toast(vm)
    }
}

@Composable
private fun Main(vm: AppViewModel, data: StudentData, settings: Settings) {
    BackHandler(enabled = vm.sheet != null || vm.stack.isNotEmpty() || vm.tab != Tab.Start) {
        when {
            vm.sheet != null -> vm.closeSheet()
            vm.stack.isNotEmpty() -> vm.pop()
            else -> vm.selectTab(Tab.Start)
        }
    }
    val key = ScreenKey(vm.tab, vm.stack.lastOrNull(), vm.stack.size)
    Box(Modifier.fillMaxSize()) {
        AnimatedContent(
            targetState = key,
            transitionSpec = { screenTransition(initialState, targetState) },
            contentKey = { it },
            label = "screens",
        ) { k ->
            when (val d = k.dest) {
                is Dest.Course -> CourseScreen(vm, data, settings, d.id)
                is Dest.Lect -> LecturerScreen(vm, data, d.id)
                null -> when (k.tab) {
                    Tab.Start -> StartScreen(vm, data, settings)
                    Tab.Plan -> PlanScreen(vm, data, settings)
                    Tab.Oceny -> GradesScreen(vm, data, settings)
                    Tab.Kursy -> CoursesScreen(vm, data, settings)
                    Tab.Wiecej -> MoreScreen(vm, data, settings)
                }
            }
        }
        BottomNav(vm, Modifier.align(Alignment.BottomCenter))
        ClassSheet(vm, data)
        vm.examEdit?.let { ExamDialog(vm, data, it) }
    }
}

private fun screenTransition(from: ScreenKey, to: ScreenKey): ContentTransform {
    val sp = spring<androidx.compose.ui.unit.IntOffset>(dampingRatio = .9f, stiffness = 420f)
    return when {
        to.depth > from.depth && to.tab == from.tab ->
            (slideInHorizontally(sp) { it / 3 } + fadeIn(tween(260))) togetherWith
                (slideOutHorizontally(sp) { -it / 6 } + fadeOut(tween(200)))
        to.depth < from.depth && to.tab == from.tab ->
            (slideInHorizontally(sp) { -it / 6 } + fadeIn(tween(260))) togetherWith
                (slideOutHorizontally(sp) { it / 3 } + fadeOut(tween(200)))
        else -> {
            val dir = if (to.tab.ordinal >= from.tab.ordinal) 1 else -1
            (fadeIn(tween(280, 60)) + slideInHorizontally(sp) { dir * it / 10 } + scaleIn(initialScale = .97f)) togetherWith
                (fadeOut(tween(160)) + scaleOut(targetScale = .985f))
        }
    }
}

@Composable
private fun BottomNav(vm: AppViewModel, modifier: Modifier) {
    val haptic = LocalHapticFeedback.current
    val tabs = Tab.entries
    Box(
        modifier
            .navigationBarsPadding()
            .padding(start = 12.dp, end = 12.dp, bottom = 14.dp)
            .fillMaxWidth()
            .height(64.dp)
            .shadow(18.dp, RoundedCornerShape(32.dp), ambientColor = Color(0x22141828), spotColor = Color(0x22141828))
            .clip(RoundedCornerShape(32.dp))
            .background(P.c.card)
            .padding(6.dp),
    ) {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val w = (maxWidth - 2.dp * (tabs.size - 1)) / tabs.size
            val x by animateDpAsState(
                (w + 2.dp) * vm.tab.ordinal,
                spring(dampingRatio = .68f, stiffness = Spring.StiffnessMediumLow), label = "nav",
            )
            Box(Modifier.offset(x = x).width(w).height(52.dp).clip(RoundedCornerShape(26.dp)).background(P.c.primaryBtn))
            Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                tabs.forEach { t ->
                    val on = t == vm.tab
                    val fg by androidx.compose.animation.animateColorAsState(if (on) P.c.primaryBtnFg else P.c.text2, label = "navfg")
                    Box(
                        Modifier.width(w).height(52.dp).clip(RoundedCornerShape(26.dp)).press(.9f) {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            vm.selectTab(t)
                        },
                        contentAlignment = Alignment.Center,
                    ) { Text(t.label, style = T.t12b.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.Bold), color = fg, maxLines = 1) }
                }
            }
        }
    }
}

/**
 * Lista ekranu z dużym nagłówkiem One UI: tytuł w „strefie oglądania” zwija się
 * przy przewijaniu do paska u góry (z efektem paralaksy i zanikania).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScreenList(
    vm: AppViewModel,
    title: String,
    subtitle: String,
    small: Boolean = false,
    back: Pair<String, () -> Unit>? = null,
    state: LazyListState = rememberLazyListState(),
    content: LazyListScope.() -> Unit,
) {
    val headerPx = with(LocalDensity.current) { 150.dp.toPx() }
    val collapse by remember {
        derivedStateOf { if (state.firstVisibleItemIndex > 0) 1f else (state.firstVisibleItemScrollOffset / headerPx).coerceIn(0f, 1f) }
    }
    PullToRefreshBox(isRefreshing = vm.refreshing, onRefresh = { vm.refresh() }, modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            state = state,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 130.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item("statusbar") { Spacer(Modifier.windowInsetsTopHeight(WindowInsets.statusBars)) }
            item("header") {
                Column(
                    Modifier.fillMaxWidth().height(150.dp)
                        .graphicsLayer {
                            alpha = (1f - collapse * 1.6f).coerceIn(0f, 1f)
                            translationY = collapse * headerPx * .45f
                        }
                        .padding(start = 10.dp, end = 10.dp, top = 6.dp, bottom = 14.dp),
                    verticalArrangement = Arrangement.SpaceBetween,
                ) {
                    Box(Modifier.height(36.dp), contentAlignment = Alignment.CenterStart) {
                        back?.let { (label, onBack) -> BackPill(label, onBack) }
                    }
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(title, style = if (small) T.heroSmall else T.hero, color = P.c.ink, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        Text(subtitle, style = T.t14, color = P.c.sub)
                    }
                }
            }
            content()
        }
        // zwinięty pasek tytułu
        val barAlpha = ((collapse - .55f) / .45f).coerceIn(0f, 1f)
        Box(
            Modifier.fillMaxWidth()
                .graphicsLayer { alpha = barAlpha }
                .background(P.c.bg)
                .statusBarsPadding()
                .height(56.dp)
                .padding(horizontal = 16.dp),
            contentAlignment = Alignment.CenterStart,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                if (back != null && barAlpha > .01f) BackPill("", back.second)
                Text(title, style = T.h20, color = P.c.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@Composable
fun BackPill(label: String, onClick: () -> Unit) {
    Row(
        Modifier.height(36.dp).clip(RoundedCornerShape(18.dp)).background(P.c.card)
            .press(.92f, onClick = onClick).padding(start = 10.dp, end = if (label.isEmpty()) 12.dp else 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text("‹", style = T.h20.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.Bold), color = P.c.ink)
        if (label.isNotEmpty()) Text(label, style = T.t14b, color = P.c.ink)
    }
}

@Composable
private fun Toast(vm: AppViewModel) {
    val msg = vm.message
    LaunchedEffect(msg) { if (msg != null) { delay(3800); vm.message = null } }
    Box(Modifier.fillMaxSize().statusBarsPadding().padding(16.dp), contentAlignment = Alignment.TopCenter) {
        AnimatedVisibility(
            visible = msg != null,
            enter = slideInVertically(spring(dampingRatio = .7f)) { -it * 2 } + fadeIn(),
            exit = slideOutVertically { -it * 2 } + fadeOut(),
        ) {
            Text(
                msg ?: "", style = T.t14b, color = P.c.primaryBtnFg,
                modifier = Modifier.clip(RoundedCornerShape(22.dp)).background(P.c.primaryBtn)
                    .press { vm.message = null }.padding(horizontal = 18.dp, vertical = 12.dp),
            )
        }
    }
}
