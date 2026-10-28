package pl.wolter.usos.data

import android.content.Context
import android.util.Log
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.tasks.await
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import pl.wolter.usos.BuildConfig

/**
 * Firebase: anonimowe konto + dokument usosStudent/{uid} w Firestore z ustawieniami,
 * własnymi wynikami („dziennik”) i profilem. Działa offline (cache Firestore).
 * Gdy Firebase nie jest skonfigurowany, wszystko po cichu działa lokalnie.
 */
class Cloud(private val ctx: Context) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    val enabled: Boolean = BuildConfig.HAS_FIREBASE && runCatching {
        FirebaseApp.getApps(ctx).isNotEmpty() || FirebaseApp.initializeApp(ctx) != null
    }.getOrDefault(false)

    private val _status = MutableStateFlow(if (enabled) "Łączenie…" else "Wyłączona")
    val status: StateFlow<String> = _status

    // jedna próba logowania naraz - inaczej równoległe wywołania tworzą kilka kont anonimowych
    private val authLock = kotlinx.coroutines.sync.Mutex()

    private suspend fun uid(): String? = authLock.withLock { signIn() }

    private suspend fun signIn(): String? {
        if (!enabled) return null
        return runCatching {
            val auth = FirebaseAuth.getInstance()
            (auth.currentUser ?: auth.signInAnonymously().await().user)?.uid
        }.onFailure {
            Log.w("Cloud", "auth", it)
            _status.value = "Błąd logowania Firebase (włącz logowanie anonimowe)"
        }.getOrNull()
    }

    private fun doc(uid: String) = FirebaseFirestore.getInstance().collection("usosStudent").document(uid)

    fun listenSettings(json: Json, onChange: (Settings) -> Unit) {
        if (!enabled) return
        scope.launch {
            val id = uid() ?: return@launch
            doc(id).addSnapshotListener { snap, err ->
                if (err != null) { _status.value = "Brak dostępu do Firestore"; return@addSnapshotListener }
                _status.value = "Zsynchronizowano"
                val s = snap?.getString("settings") ?: return@addSnapshotListener
                runCatching { json.decodeFromString<Settings>(s) }.getOrNull()?.let(onChange)
            }
        }
    }

    suspend fun pushSettings(json: Json, s: Settings) {
        val id = uid() ?: return
        runCatching {
            doc(id).set(mapOf("settings" to json.encodeToString(s), "settingsUpdatedAt" to s.updatedAt), SetOptions.merge()).await()
            _status.value = "Zsynchronizowano"
        }.onFailure { _status.value = "Nie zapisano w chmurze"; Log.w("Cloud", "push", it) }
    }

    suspend fun pushProfile(d: StudentData) {
        val id = uid() ?: return
        runCatching {
            doc(id).set(mapOf(
                "name" to "${d.student.firstName} ${d.student.lastName}",
                "university" to d.student.university,
                "term" to d.termLabel,
                "courses" to d.courses.size,
                "demo" to d.demo,
                "lastSync" to d.fetchedAt,
            ), SetOptions.merge()).await()
        }.onFailure { Log.w("Cloud", "profile", it) }
    }

    // ---------- wspólne terminy grupy: usosGroups/{kod}/members, /exams ----------

    private fun group(code: String) = FirebaseFirestore.getInstance().collection("usosGroups").document(code)
    private var groupReg: com.google.firebase.firestore.ListenerRegistration? = null
    private var membersReg: com.google.firebase.firestore.ListenerRegistration? = null

    suspend fun myUid(): String? = uid()

    private fun newCode(): String {
        val abc = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"   // bez 0/O i 1/I - łatwo przepisać
        val r = java.security.SecureRandom()
        // 8 znaków z 32 = ponad bilion kombinacji, więc kodu grupy nie da się zgadnąć.
        return (1..8).map { abc[r.nextInt(abc.length)] }.joinToString("")
    }

    /** Tworzy grupę i od razu do niej dołącza. Zwraca kod. */
    suspend fun createGroup(name: String, me: String): String {
        val id = uid() ?: throw IllegalStateException("Brak połączenia z Firebase")
        repeat(5) {
            val code = newCode()
            val exists = runCatching { group(code).get().await().exists() }.getOrDefault(true)
            if (!exists) {
                group(code).set(mapOf("name" to name.take(40), "createdBy" to id, "createdAt" to System.currentTimeMillis())).await()
                group(code).collection("members").document(id).set(mapOf("name" to me, "joinedAt" to System.currentTimeMillis())).await()
                return code
            }
        }
        throw IllegalStateException("Nie udało się wygenerować kodu")
    }

    /** Dołącza do grupy po kodzie. Zwraca nazwę grupy. */
    suspend fun joinGroup(code: String, me: String): String {
        val id = uid() ?: throw IllegalStateException("Brak połączenia z Firebase")
        val snap = group(code).get().await()
        if (!snap.exists()) throw IllegalArgumentException("Nie ma grupy o kodzie ${fmtCode(code)}")
        group(code).collection("members").document(id).set(mapOf("name" to me, "joinedAt" to System.currentTimeMillis())).await()
        return snap.getString("name") ?: "Grupa"
    }

    suspend fun leaveGroup(code: String) {
        stopGroup()
        val id = uid() ?: return
        runCatching { group(code).collection("members").document(id).delete().await() }
    }

    /** Nasłuch terminów i liczby członków; wywołuje się od razu i przy każdej zmianie. */
    fun listenGroup(code: String, onExams: (List<MyExam>) -> Unit, onMembers: (Int) -> Unit, onError: (String) -> Unit) {
        stopGroup()
        if (!enabled || code.isBlank()) return
        scope.launch {
            uid() ?: return@launch
            groupReg = group(code).collection("exams").addSnapshotListener { snap, err ->
                if (err != null) { onError("Brak dostępu do grupy ${fmtCode(code)}"); return@addSnapshotListener }
                onExams(snap?.documents.orEmpty().map { d ->
                    MyExam(
                        id = d.id, kind = d.getString("kind") ?: "Termin",
                        courseId = d.getString("courseId") ?: "", courseName = d.getString("courseName") ?: "",
                        date = d.getString("date") ?: "", time = d.getString("time") ?: "",
                        room = d.getString("room") ?: "", note = d.getString("note") ?: "",
                        shared = true, author = d.getString("author") ?: "", authorUid = d.getString("authorUid") ?: "",
                    )
                }.filter { it.date.length == 10 })
            }
            membersReg = group(code).collection("members").addSnapshotListener { snap, _ -> onMembers(snap?.size() ?: 0) }
        }
    }

    fun stopGroup() { groupReg?.remove(); membersReg?.remove(); groupReg = null; membersReg = null }

    /** Dodaje albo aktualizuje termin grupy (pusty id = nowy). */
    suspend fun saveGroupExam(code: String, e: MyExam, me: String) {
        val id = uid() ?: throw IllegalStateException("Brak połączenia z Firebase")
        val col = group(code).collection("exams")
        val ref = if (e.id.isBlank() || !e.shared) col.document() else col.document(e.id)
        ref.set(mapOf(
            "kind" to e.kind, "courseId" to e.courseId, "courseName" to e.courseName,
            "date" to e.date, "time" to e.time, "room" to e.room, "note" to e.note.take(300),
            "authorUid" to (e.authorUid.ifBlank { id }), "author" to (e.author.ifBlank { me }),
            "updatedAt" to System.currentTimeMillis(),
        )).await()
    }

    suspend fun deleteGroupExam(code: String, id: String) {
        uid() ?: return
        group(code).collection("exams").document(id).delete().await()
    }

    companion object {
        fun fmtCode(c: String) = if (c.length == 6) "${c.take(3)}-${c.drop(3)}" else c
        fun normCode(c: String) = c.uppercase().filter { it.isLetterOrDigit() }
    }
}
