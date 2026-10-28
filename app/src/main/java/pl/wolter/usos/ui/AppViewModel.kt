package pl.wolter.usos.ui

import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch
import pl.wolter.usos.UsosApp
import pl.wolter.usos.data.ClassEvent
import pl.wolter.usos.data.Derive
import pl.wolter.usos.data.TestBlock
import pl.wolter.usos.data.TestItem
import java.time.LocalDate

enum class Tab(val label: String) { Start("Start"), Plan("Plan"), Oceny("Oceny"), Kursy("Kursy"), Wiecej("Więcej") }

sealed interface Dest {
    data class Course(val id: String) : Dest
    data class Lect(val id: String) : Dest
}

class AppViewModel : ViewModel() {
    val repo = UsosApp.repo

    var tab by mutableStateOf(Tab.Start)
        private set
    val stack = mutableStateListOf<Dest>()
    var planMode by mutableIntStateOf(0)
    var day by mutableIntStateOf(-1)   // -1 = domyślny (dziś / pierwszy dzień z zajęciami)
    var weekOffset by mutableIntStateOf(0)
        private set
    var weekLoading by mutableStateOf(false)
        private set

    fun weekMonday(): LocalDate = pl.wolter.usos.data.Time.monday().plusWeeks(weekOffset.toLong())

    /** Przejście o [delta] tygodni; [toDay] = indeks dnia (-1 domyślny, Int.MAX_VALUE = ostatni). */
    fun shiftWeek(delta: Int, toDay: Int = -1) {
        weekOffset += delta
        day = toDay
        loadWeek()
    }

    fun resetWeek() { if (weekOffset != 0) { weekOffset = 0; day = -1 } }

    private fun loadWeek() {
        val mon = weekMonday()
        viewModelScope.launch {
            weekLoading = true
            repo.ensureWeek(mon).onFailure { message = "Nie udało się pobrać tygodnia: ${it.message}" }
            weekLoading = false
        }
    }
    var term by mutableIntStateOf(0)
    var sheet by mutableStateOf<ClassEvent?>(null)
        private set
    var sheetDates by mutableStateOf<List<String>>(emptyList())
        private set
    var refreshing by mutableStateOf(false)
        private set
    var message by mutableStateOf<String?>(null)
    var loginBusy by mutableStateOf(false)
        private set

    init {
        if (repo.mode.value != pl.wolter.usos.data.Mode.NONE) refresh(silent = true)
    }

    fun selectTab(t: Tab) {
        if (t == tab && stack.isEmpty()) return
        tab = t; stack.clear(); sheet = null
    }

    fun push(d: Dest) { sheet = null; stack.add(d) }
    fun pop(): Boolean = if (stack.isNotEmpty()) { stack.removeAt(stack.lastIndex); true } else false

    fun goExams() { tab = Tab.Plan; planMode = 2; stack.clear() }
    fun goGrades() { tab = Tab.Oceny; term = 0; stack.clear() }

    fun openSheet(e: ClassEvent) {
        sheet = e
        sheetDates = if (e.freq.isNotBlank() || e.unitId.isBlank()) Derive.nextDates(e) else emptyList()
        if (e.unitId.isNotBlank()) viewModelScope.launch {
            val today = LocalDate.now().toString()
            val dates = repo.groupDates(e).filter { it > e.date && it >= today }.distinct().take(6)
                .map { pl.wolter.usos.data.Time.dayMonth(pl.wolter.usos.data.Time.date(it)) }
            if (sheet?.id == e.id) sheetDates = dates.ifEmpty { listOf("brak kolejnych") }
        }
    }

    fun closeSheet() { sheet = null }

    fun refresh(silent: Boolean = false) {
        if (refreshing) return
        viewModelScope.launch {
            refreshing = !silent
            repo.refresh().onFailure { message = "Nie udało się odświeżyć: ${it.message}" }
            refreshing = false
        }
    }

    fun startLogin(baseUrl: String, ck: String, cs: String, open: (String) -> Unit) {
        if (ck.isBlank() || cs.isBlank()) { message = "Wpisz consumer key i secret aplikacji USOS"; return }
        viewModelScope.launch {
            loginBusy = true
            runCatching { repo.startLogin(baseUrl, ck.trim(), cs.trim()) }
                .onSuccess(open)
                .onFailure { message = "Błąd logowania: ${it.message}" }
            loginBusy = false
        }
    }

    fun handleCallback(uri: Uri, university: String) {
        val verifier = uri.getQueryParameter("oauth_verifier") ?: run { message = "Logowanie anulowane"; return }
        // Link usos-student://oauth może wysłać dowolna strona albo aplikacja - przyjmujemy tylko powrót z naszego logowania.
        val expected = repo.pendingToken()
        if (expected == null || uri.getQueryParameter("oauth_token") != expected) { message = "Ten link logowania nie pochodzi z Twojego logowania"; return }
        viewModelScope.launch {
            loginBusy = true
            runCatching { repo.finishLogin(verifier, university) }
                .onFailure { message = "Nie udało się zalogować: ${it.message}" }
            loginBusy = false
        }
    }

    fun handleOpen(target: String) {
        when {
            target == "plan" -> { tab = Tab.Plan; planMode = 0; weekOffset = 0; day = -1; stack.clear() }
            target.startsWith("course:") -> { tab = Tab.Kursy; stack.clear(); stack.add(Dest.Course(target.removePrefix("course:"))) }
            target == "exams" -> goExams()
            target == "grades" -> goGrades()
        }
    }

    // ---- własne terminy (kolokwia, kartkówki…), synchronizowane przez Firebase ----
    var examEdit by mutableStateOf<pl.wolter.usos.data.MyExam?>(null)
        private set

    fun newExam(courseId: String? = null, date: LocalDate = LocalDate.now().plusDays(7)) {
        val courses = repo.data.value?.courses.orEmpty()
        val c = courses.firstOrNull { it.id == courseId } ?: courses.firstOrNull()
        examEdit = pl.wolter.usos.data.MyExam("", "Kolokwium", c?.id ?: "", c?.name ?: "", date.toString())
    }

    fun editExam(id: String) {
        examEdit = repo.settings.value.myExams.firstOrNull { it.id == id } ?: repo.groupExams.value.firstOrNull { it.id == id }
    }
    fun closeExam() { examEdit = null }

    /** [share] = zapis do grupy (widzą wszyscy z kodem), inaczej tylko u mnie. */
    fun saveExam(m: pl.wolter.usos.data.MyExam, share: Boolean) {
        val isNew = m.id.isBlank()
        examEdit = null
        if (share) viewModelScope.launch {
            runCatching { repo.saveGroupExam(m) }
                .onSuccess {
                    // prywatny termin przeniesiony do grupy - usuń lokalną kopię
                    if (!m.shared && !isNew) repo.updateSettings { s -> s.copy(myExams = s.myExams.filter { it.id != m.id }) }
                    message = if (isNew || !m.shared) "Udostępniono grupie: ${m.kind.lowercase()} · ${m.courseName}" else "Zapisano zmiany w grupie"
                }
                .onFailure { message = "Nie zapisano w grupie: ${it.message}" }
        } else {
            val n = if (isNew) m.copy(id = "e${System.currentTimeMillis()}") else m
            repo.updateSettings { s -> s.copy(myExams = s.myExams.filter { it.id != n.id } + n) }
            message = if (isNew) "Dodano: ${n.kind.lowercase()} · ${n.courseName}" else "Zapisano zmiany"
        }
    }

    fun deleteExam(m: pl.wolter.usos.data.MyExam) {
        examEdit = null
        if (m.shared) viewModelScope.launch {
            runCatching { repo.deleteGroupExam(m.id) }
                .onSuccess { message = "Usunięto termin z grupy" }
                .onFailure {
                    message = if (it.message.orEmpty().contains("PERMISSION_DENIED")) "Termin z grupy może usunąć tylko osoba, która go dodała"
                    else "Nie usunięto: ${it.message}"
                }
        } else {
            repo.updateSettings { s -> s.copy(myExams = s.myExams.filter { it.id != m.id }) }
            message = "Usunięto termin"
        }
    }

    // ---- grupa roku ----
    var groupBusy by mutableStateOf(false)
        private set

    fun createGroup(name: String) = groupAction { "Utworzono grupę · kod ${pl.wolter.usos.data.Cloud.fmtCode(repo.createGroup(name))}" }
    fun joinGroup(code: String) = groupAction { "Dołączono do grupy „${repo.joinGroup(code)}”" }
    fun leaveGroup() = groupAction { repo.leaveGroup(); "Opuszczono grupę" }

    private fun groupAction(block: suspend () -> String) {
        if (groupBusy) return
        viewModelScope.launch {
            groupBusy = true
            runCatching { block() }.onSuccess { message = it }.onFailure { message = it.message ?: "Błąd grupy" }
            groupBusy = false
        }
    }

    // ---- dziennik: własne wyniki, synchronizowane przez Firebase ----
    fun addManual(courseId: String, name: String, points: Double?, max: Double) {
        repo.updateSettings { s ->
            val list = (s.manual[courseId] ?: emptyList()) +
                TestBlock(name, listOf(TestItem("Mój wynik", points, max)), "Dodane ręcznie", manual = true, id = "m${System.currentTimeMillis()}")
            s.copy(manual = s.manual + (courseId to list))
        }
    }

    fun removeManual(courseId: String, id: String) {
        repo.updateSettings { s -> s.copy(manual = s.manual + (courseId to (s.manual[courseId] ?: emptyList()).filter { it.id != id })) }
    }
}
