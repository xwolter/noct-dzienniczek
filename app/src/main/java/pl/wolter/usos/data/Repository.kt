package pl.wolter.usos.data

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import pl.wolter.usos.BuildConfig
import pl.wolter.usos.data.usos.UsosClient
import pl.wolter.usos.data.usos.UsosException
import pl.wolter.usos.data.usos.UsosFetcher
import pl.wolter.usos.widget.Widgets
import pl.wolter.usos.work.Notifier
import pl.wolter.usos.work.Reminders

enum class Mode { NONE, DEMO, USOS }

class Repository(private val ctx: Context) {
    private val prefs = ctx.getSharedPreferences("usos", Context.MODE_PRIVATE)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val lock = Mutex()

    val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    private val _data = MutableStateFlow(read<StudentData>("data"))
    val data: StateFlow<StudentData?> = _data.asStateFlow()

    private val _settings = MutableStateFlow(read<Settings>("settings") ?: Settings())
    val settings: StateFlow<Settings> = _settings.asStateFlow()

    private val _mode = MutableStateFlow(Mode.valueOf(prefs.getString("mode", Mode.NONE.name)!!))
    val mode: StateFlow<Mode> = _mode.asStateFlow()

    private var session: UsosSession? = read("session")
    private var pending: UsosClient? = null

    val cloud = Cloud(ctx)

    // ---- wspólne terminy grupy ----
    private val _groupExams = MutableStateFlow(read<List<MyExam>>("groupExams") ?: emptyList())
    val groupExams: StateFlow<List<MyExam>> = _groupExams.asStateFlow()
    private val _groupMembers = MutableStateFlow(0)
    val groupMembers: StateFlow<Int> = _groupMembers.asStateFlow()

    /** Podpis autora terminów w grupie: „Anna K.” */
    fun myName(): String = _data.value?.student?.let { st ->
        listOf(st.firstName, st.lastName.take(1).let { if (it.isNotEmpty()) "$it." else "" }).filter { it.isNotBlank() }.joinToString(" ")
    }?.ifBlank { null } ?: "Student"

    private fun startGroup(code: String) {
        if (code.isBlank()) { cloud.stopGroup(); _groupMembers.value = 0; return }
        val seenKey = "seen:$code"
        cloud.listenGroup(code,
            onExams = { list ->
                // powiadomienie tylko o nowych terminach od innych (przy pierwszym wczytaniu grupy - bez spamu)
                val seen = prefs.getStringSet(seenKey, null)
                scope.launch {
                    val me = cloud.myUid()
                    if (seen != null && _settings.value.notif) list.filter { it.id !in seen && it.authorUid != me }.forEach { e ->
                        Notifier.show(ctx, e.id.hashCode(), Notifier.CH_RESULTS, "Nowy termin w grupie: ${e.kind.lowercase()}",
                            "${e.courseName} · ${Time.dayMonth(Time.date(e.date))}${if (e.time.isNotBlank()) " ${e.time}" else ""} · dodał(a) ${e.author}", "exams")
                    }
                }
                prefs.edit().putStringSet(seenKey, list.map { it.id }.toSet()).apply()
                _groupExams.value = list
                write("groupExams", list)
                _data.value?.let { if (_settings.value.notif) Reminders.schedule(ctx, it) }
                Widgets.updateAll(ctx)
            },
            onMembers = { _groupMembers.value = it },
            onError = { _groupMembers.value = 0 },
        )
    }

    suspend fun createGroup(name: String): String {
        val code = cloud.createGroup(name.ifBlank { "Mój rok" }, myName())
        updateSettings { it.copy(groupCode = code, groupName = name.ifBlank { "Mój rok" }) }
        startGroup(code)
        return code
    }

    suspend fun joinGroup(raw: String): String {
        val code = Cloud.normCode(raw)
        if (code.length != 6) throw IllegalArgumentException("Kod ma 6 znaków, np. K7M-Q2X")
        val name = cloud.joinGroup(code, myName())
        updateSettings { it.copy(groupCode = code, groupName = name) }
        startGroup(code)
        return name
    }

    suspend fun leaveGroup() {
        val code = _settings.value.groupCode
        cloud.leaveGroup(code)
        _groupExams.value = emptyList(); write("groupExams", emptyList<MyExam>())
        _groupMembers.value = 0
        updateSettings { it.copy(groupCode = "", groupName = "") }
    }

    suspend fun saveGroupExam(e: MyExam) = cloud.saveGroupExam(_settings.value.groupCode, e, myName())
    suspend fun deleteGroupExam(id: String) = cloud.deleteGroupExam(_settings.value.groupCode, id)

    init {
        startGroup(_settings.value.groupCode)
        // Ustawienia i własne wyniki synchronizują się przez Firestore między urządzeniami.
        cloud.listenSettings(json) { remote ->
            if (remote.updatedAt > _settings.value.updatedAt) {
                val groupChanged = remote.groupCode != _settings.value.groupCode
                _settings.value = remote; write("settings", remote)
                if (groupChanged) startGroup(remote.groupCode)
                Widgets.updateAll(ctx)
            }
        }
    }

    private inline fun <reified T> read(key: String): T? =
        prefs.getString(key, null)?.let { runCatching { json.decodeFromString<T>(it) }.getOrNull() }

    private inline fun <reified T> write(key: String, v: T?) {
        prefs.edit().apply { if (v == null) remove(key) else putString(key, json.encodeToString(v)) }.apply()
    }

    val sessionUniversity: String? get() = session?.university

    fun defaultKey() = BuildConfig.USOS_CONSUMER_KEY to BuildConfig.USOS_CONSUMER_SECRET

    fun savedKey(baseUrl: String): Pair<String, String>? {
        val k = prefs.getString("ck:$baseUrl", null) ?: return null
        return k to (prefs.getString("cs:$baseUrl", null) ?: "")
    }

    // ---------- logowanie OAuth ----------
    suspend fun startLogin(baseUrl: String, ck: String, cs: String): String {
        val c = UsosClient(baseUrl, ck, cs)
        pending = c
        prefs.edit().putString("ck:$baseUrl", ck).putString("cs:$baseUrl", cs).putString("pendingBase", baseUrl).apply()
        val scopes = listOf("studies|offline_access|crstests|grades|email|cards", "studies|offline_access|crstests|grades", "studies|offline_access")
        var last: Exception? = null
        for (s in scopes) {
            try {
                val url = c.requestToken("usos-student://oauth", s)
                prefs.edit().putString("pendingToken", c.token).putString("pendingSecret", c.tokenSecret).apply()
                return url
            } catch (e: UsosException) { last = e; if (e.code == 401) throw e }
        }
        throw last ?: UsosException("Nie udało się rozpocząć logowania")
    }

    /** Token z rozpoczętego logowania - powrót z przeglądarki musi nieść ten sam (inny link to podróbka). */
    fun pendingToken(): String? = pending?.token ?: prefs.getString("pendingToken", null)

    suspend fun finishLogin(verifier: String, university: String) {
        val base = prefs.getString("pendingBase", null) ?: throw UsosException("Brak rozpoczętego logowania")
        val (ck, cs) = savedKey(base) ?: throw UsosException("Brak klucza aplikacji")
        val c = pending ?: UsosClient(base, ck, cs, prefs.getString("pendingToken", null), prefs.getString("pendingSecret", null))
        c.accessToken(verifier)
        val s = UsosSession(base, university, ck, cs, c.token!!, c.tokenSecret!!)
        session = s; write("session", s)
        pending = null
        setMode(Mode.USOS)
        refresh()
    }

    fun enterDemo() { setMode(Mode.DEMO); scope.launch { refresh() } }

    fun logout() {
        session = null; write<UsosSession>("session", null); write<StudentData>("data", null)
        _data.value = null
        setMode(Mode.NONE)
        Reminders.cancelAll(ctx)
        Widgets.updateAll(ctx)
    }

    private fun setMode(m: Mode) { _mode.value = m; prefs.edit().putString("mode", m.name).apply() }

    // ---------- dane ----------
    suspend fun refresh(): Result<Unit> = lock.withLock {
        runCatching {
            val old = _data.value
            val fresh = when (_mode.value) {
                Mode.DEMO -> DemoData.build()
                Mode.USOS -> {
                    val s = session ?: throw UsosException("Zaloguj się ponownie")
                    UsosFetcher(UsosClient(s.baseUrl, s.consumerKey, s.consumerSecret, s.token, s.tokenSecret), s.university).fetch()
                }
                Mode.NONE -> return@runCatching
            }
            _data.value = fresh
            write("data", fresh)
            if (old != null && !fresh.demo && _settings.value.grades) Notifier.notifyNewResults(ctx, old, fresh)
            if (_settings.value.notif) Reminders.schedule(ctx, fresh) else Reminders.cancelAll(ctx)
            Reminders.scheduleWidgetTicks(ctx, fresh)
            Widgets.updateAll(ctx)
            cloud.pushProfile(fresh)
        }.onFailure { e ->
            if (e is UsosException && e.code == 401) { /* token unieważniony */ }
        }
    }

    /** Doczytuje tydzień zaczynający się w [mon], jeśli nie ma go jeszcze w pamięci. */
    suspend fun ensureWeek(mon: java.time.LocalDate): Result<Unit> = lock.withLock {
        runCatching {
            val d = _data.value ?: return@runCatching
            val key = mon.toString()
            if (key in d.loadedWeeks) return@runCatching
            val (ev, lects) = when (_mode.value) {
                Mode.DEMO -> DemoData.weekEvents(mon) to emptyMap()
                Mode.USOS -> {
                    val s = session ?: return@runCatching
                    UsosFetcher(UsosClient(s.baseUrl, s.consumerKey, s.consumerSecret, s.token, s.tokenSecret), s.university)
                        .fetchWeek(mon, d.lecturers.keys)
                }
                Mode.NONE -> return@runCatching
            }
            val end = mon.plusDays(7).toString()
            val merged = d.copy(
                events = (d.events.filter { it.date < key || it.date >= end } + ev).sortedWith(compareBy({ it.date }, { it.start })),
                lecturers = d.lecturers + lects,
                loadedWeeks = d.loadedWeeks + key,
            )
            _data.value = merged
            write("data", merged)
        }
    }

    suspend fun groupDates(e: ClassEvent): List<String> {
        val s = session ?: return emptyList()
        if (e.unitId.isBlank()) return emptyList()
        return UsosFetcher(UsosClient(s.baseUrl, s.consumerKey, s.consumerSecret, s.token, s.tokenSecret), s.university)
            .groupDates(e.unitId, e.group)
    }

    fun updateSettings(f: (Settings) -> Settings) {
        val n = f(_settings.value).copy(updatedAt = System.currentTimeMillis())
        _settings.value = n
        write("settings", n)
        scope.launch { cloud.pushSettings(json, n) }
        _data.value?.let { d ->
            if (n.notif) Reminders.schedule(ctx, d) else Reminders.cancelAll(ctx)
        }
        Widgets.updateAll(ctx)
    }
}
