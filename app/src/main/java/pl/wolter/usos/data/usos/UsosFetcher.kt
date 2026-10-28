package pl.wolter.usos.data.usos

import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.doubleOrNull
import pl.wolter.usos.data.ClassEvent
import pl.wolter.usos.data.ClassType
import pl.wolter.usos.data.Course
import pl.wolter.usos.data.CourseGroup
import pl.wolter.usos.data.Exam
import pl.wolter.usos.data.FeedItem
import pl.wolter.usos.data.FinalGrade
import pl.wolter.usos.data.Grades
import pl.wolter.usos.data.Lecturer
import pl.wolter.usos.data.MoreRow
import pl.wolter.usos.data.PastTerm
import pl.wolter.usos.data.Student
import pl.wolter.usos.data.StudentData
import pl.wolter.usos.data.TestBlock
import pl.wolter.usos.data.TestItem
import pl.wolter.usos.data.Time
import java.time.LocalDate

// ---- pomocnicze dla luźnego JSON-a USOS ----
private operator fun JsonElement?.get(k: String): JsonElement? = (this as? JsonObject)?.get(k)
private val JsonElement?.str: String? get() = (this as? JsonPrimitive)?.takeIf { it !is JsonNull }?.content
private val JsonElement?.dbl: Double? get() = (this as? JsonPrimitive)?.doubleOrNull ?: str?.replace(',', '.')?.toDoubleOrNull()
private val JsonElement?.arr: List<JsonElement> get() = (this as? JsonArray) ?: emptyList()
private val JsonElement?.obj: Map<String, JsonElement> get() = (this as? JsonObject) ?: emptyMap()
/** Pola wielojęzyczne {pl, en}. */
private val JsonElement?.pl: String get() = when (this) {
    is JsonObject -> this["pl"].str ?: this["en"].str ?: ""
    else -> str ?: ""
}

fun classTypeOf(label: String, ttType: String? = null): ClassType {
    val l = label.lowercase()
    return when {
        ttType == "exam" || l.contains("egzamin") || l.contains("kolokw") -> ClassType.EGZ
        l.startsWith("wyk") -> ClassType.WYK
        l.startsWith("ćw") || l.startsWith("cw") -> ClassType.CW
        l.startsWith("lab") || l.contains("pracown") -> ClassType.LAB
        l.startsWith("lekt") || l.contains("język") -> ClassType.LEK
        l.startsWith("sem") || l.startsWith("prosem") -> ClassType.SEM
        else -> ClassType.INNE
    }
}

const val WEEKS_AHEAD = 3

class UsosFetcher(private val c: UsosClient, private val university: String) {

    /** Próbuje bogatszego zestawu pól, a przy błędzie (np. nieznane pole w danej instalacji) - prostszego. */
    private suspend fun callFallback(method: String, base: Map<String, String>, vararg fieldSets: String): JsonElement? {
        for (f in fieldSets) {
            val r = runCatching { c.call(method, base + ("fields" to f)) }
            if (r.isSuccess) return r.getOrNull()
            val e = r.exceptionOrNull()
            if (e is UsosException && e.code == 401) throw e
        }
        return null
    }

    suspend fun fetch(): StudentData = coroutineScope {
        val today = LocalDate.now()
        val mon = Time.monday(today)

        val userD = async {
            callFallback("users/user", emptyMap(),
                "id|first_name|last_name|student_number|student_programmes",
                "id|first_name|last_name|student_number",
                "id|first_name|last_name")
        }
        val coursesD = async {
            callFallback("courses/user", mapOf("active_terms_only" to "false"),
                "course_editions[course_id|course_name|term_id|user_groups|grades]|terms",
                "course_editions|terms")
        }
        // plan: bieżący + 2 kolejne tygodnie (żeby zawsze było widać najbliższe zajęcia)
        val weeksD = (0 until WEEKS_AHEAD).map { w -> async { ttWeek(mon.plusDays(7L * w)) } }
        val testsD = async { runCatching { c.call("crstests/participant") }.getOrNull() }
        val cardsD = async { runCatching { c.call("cards/user") }.getOrNull() }

        val user = userD.await() ?: throw UsosException("Nie udało się pobrać profilu")
        val coursesJ = coursesD.await()

        // --- semestry ---
        data class Term(val id: String, val name: String, val start: String, val end: String)
        val terms = coursesJ["terms"].arr.map {
            Term(it["id"].str ?: "", it["name"].pl, it["start_date"].str ?: "", it["end_date"].str ?: "")
        }.sortedBy { it.start }
        val editions = coursesJ["course_editions"].obj
        val curTerm = terms.lastOrNull { it.start <= today.toString() && editions.containsKey(it.id) }
            ?: terms.lastOrNull { editions.containsKey(it.id) }
        val curTermId = curTerm?.id ?: editions.keys.maxOrNull() ?: ""

        // --- plan tygodni ---
        val lecturerIds = mutableSetOf<String>()
        val events = weeksD.mapIndexed { w, d -> parseTt(d.await(), mon.plusDays(7L * w).toString(), lecturerIds) }
            .flatten().sortedWith(compareBy({ it.date }, { it.start }))

        // --- przedmioty bieżącego semestru ---
        val curEd = editions[curTermId].arr
        curEd.forEach { ed -> ed["user_groups"].arr.forEach { g -> g["lecturers"].arr.forEach { l -> l["id"].str?.let(lecturerIds::add) } } }
        val courseIds = curEd.mapNotNull { it["course_id"].str }

        val ectsD = async {
            if (courseIds.isEmpty()) emptyMap() else runCatching {
                c.call("courses/courses", mapOf("course_ids" to courseIds.joinToString("|"), "fields" to "id|ects_credits_simplified"))
                    .obj.mapValues { it.value["ects_credits_simplified"].dbl ?: 0.0 }
            }.getOrDefault(emptyMap())
        }
        val lecturersD = async { fetchLecturers(lecturerIds.toList()) }
        val crs = async { fetchTests(testsD.await(), curTermId) }

        // --- oceny z poprzednich semestrów ---
        val pastTermIds = terms.filter { it.id != curTermId && editions.containsKey(it.id) && it.start < (curTerm?.start ?: "9") }
            .sortedByDescending { it.start }.take(6)
        val gradesD = async {
            if (pastTermIds.isEmpty()) null else runCatching {
                c.call("grades/terms2", mapOf("term_ids" to pastTermIds.joinToString("|") { it.id }, "fields" to "value_symbol|passes|exam_session_number|date_modified"))
            }.getOrNull()
        }
        val pastEcts = async {
            val ids = pastTermIds.flatMap { t -> editions[t.id].arr.mapNotNull { it["course_id"].str } }.distinct()
            ids.chunked(40).flatMap { chunk ->
                runCatching {
                    c.call("courses/courses", mapOf("course_ids" to chunk.joinToString("|"), "fields" to "id|ects_credits_simplified"))
                        .obj.map { it.key to (it.value["ects_credits_simplified"].dbl ?: 0.0) }
                }.getOrDefault(emptyList())
            }.toMap()
        }

        val ects = ectsD.await()
        val (testsByCourse, feed) = crs.await()
        val courses = curEd.map { ed ->
            val id = ed["course_id"].str ?: ""
            val groups = ed["user_groups"].arr.map { g ->
                val label = g["class_type"].pl
                CourseGroup(classTypeOf(label), label.ifBlank { "Zajęcia" }, g["group_number"].str ?: "",
                    g["lecturers"].arr.mapNotNull { it["id"].str })
            }
            Course(id, ed["course_name"].pl, ects[id] ?: 0.0, id, testsByCourse[id] ?: emptyList(), groups)
        }.sortedBy { it.name }

        val gradesJ = gradesD.await()
        val pEcts = pastEcts.await()
        val pastTerms = pastTermIds.map { t ->
            val names = editions[t.id].arr.associate { (it["course_id"].str ?: "") to it["course_name"].pl }
            val rows = gradesJ[t.id].obj.mapNotNull { (cid, g) ->
                val all = mutableListOf<JsonObject>()
                fun walk(e: JsonElement?) {
                    when (e) {
                        is JsonObject -> if (e.containsKey("value_symbol")) all += e else e.values.forEach(::walk)
                        is JsonArray -> e.forEach(::walk)
                        else -> {}
                    }
                }
                walk(g["course_grades"])
                if (all.isEmpty()) return@mapNotNull null
                val sorted = all.sortedBy { it["exam_session_number"].str?.toIntOrNull() ?: 0 }
                val last = sorted.last()
                val note = if (sorted.size > 1) sorted.joinToString(" · ") {
                    "${roman(it["exam_session_number"].str)} termin: ${it["value_symbol"].str}"
                } else "${roman(last["exam_session_number"].str)} termin"
                FinalGrade(names[cid] ?: cid, pEcts[cid] ?: 0.0, last["value_symbol"].str ?: "-", note)
            }.sortedBy { it.name }
            PastTerm(t.id, rows)
        }.filter { it.rows.isNotEmpty() }

        // --- egzaminy: z planu bieżącego i przyszłego tygodnia ---
        val exams = events.filter { it.type == ClassType.EGZ && it.date >= today.toString() }
            .map { Exam(it.date, "${it.courseName} · ${it.typeLabel.lowercase()}", "${it.start} · s. ${it.room}", it.courseId, kind = it.typeLabel, time = it.start) }

        // --- legitymacja ---
        // cards/user zwraca wszystkie nieanulowane karty (także stare, wygasłe) - bierzemy legitymację z najpóźniejszą datą
        val cards = cardsD.await().arr
        val card = cards.filter { it["type"].str in setOf("student", "phd", null) }.ifEmpty { cards }
            .maxByOrNull { it["expiration_date"].str.orEmpty() }
        val cardUntil = card["expiration_date"].str?.take(10)

        val prog = user["student_programmes"].arr.firstOrNull()
        val programme = prog["programme"]["description"].pl.ifBlank { prog["programme"]["id"].str ?: "Student" }

        StudentData(
            student = Student(user["first_name"].str ?: "", user["last_name"].str ?: "", programme,
                user["student_number"].str ?: user["id"].str ?: "", cardUntil, university),
            termLabel = curTerm?.id ?: curTermId,
            courses = courses,
            events = events,
            lecturers = lecturersD.await(),
            pastTerms = pastTerms,
            exams = exams,
            alerts = emptyList(),
            feed = feed,
            moreRows = listOf(
                MoreRow("Punkty ECTS w semestrze", "services/courses", Grades.fmtPts(courses.sumOf { it.ects })),
                MoreRow("Przedmioty", "courses/user", "${courses.size}"),
                MoreRow("Uczelnia", "zmiana instalacji USOS", university),
            ),
            fetchedAt = System.currentTimeMillis(),
            demo = false,
            loadedWeeks = (0 until WEEKS_AHEAD).map { mon.plusDays(7L * it).toString() },
        )
    }

    private suspend fun ttWeek(mon: LocalDate): JsonElement? =
        callFallback("tt/user", mapOf("start" to mon.toString(), "days" to "7"),
            "start_time|end_time|name|course_id|course_name|classtype_name|building_name|room_number|group_number|lecturer_ids|type|unit_id",
            "start_time|end_time|name|course_id|course_name|classtype_name|building_name|room_number|group_number|lecturer_ids|type",
            "start_time|end_time|name|course_id|course_name|building_name|room_number|type")

    private fun parseTt(j: JsonElement?, prefix: String, lecturerIds: MutableSet<String>) = j.arr.mapIndexedNotNull { i, e ->
        val st = e["start_time"].str ?: return@mapIndexedNotNull null
        val en = e["end_time"].str ?: return@mapIndexedNotNull null
        val label = e["classtype_name"].pl
        val type = classTypeOf(label, e["type"].str)
        val lids = e["lecturer_ids"].arr.mapNotNull { it.str }
        lecturerIds += lids
        ClassEvent(
            id = "$prefix-$i", date = st.take(10), start = st.substring(11, 16), end = en.substring(11, 16),
            courseId = e["course_id"].str ?: e["name"].pl,
            courseName = e["course_name"].pl.ifBlank { e["name"].pl },
            type = type, typeLabel = label.ifBlank { if (type == ClassType.EGZ) "Egzamin" else "Zajęcia" },
            group = e["group_number"].str ?: "",
            building = e["building_name"].pl, room = e["room_number"].str ?: "",
            lecturerIds = lids, unitId = e["unit_id"].str ?: "",
        )
    }

    /** Doczytanie dowolnego tygodnia (strzałki w Planie) + brakujący prowadzący. */
    suspend fun fetchWeek(mon: LocalDate, known: Set<String>): Pair<List<ClassEvent>, Map<String, Lecturer>> {
        val ids = mutableSetOf<String>()
        val ev = parseTt(ttWeek(mon) ?: throw UsosException("Nie udało się pobrać planu"), mon.toString(), ids)
        return ev to fetchLecturers((ids - known).toList())
    }

    private fun roman(n: String?) = when (n) { "1" -> "I"; "2" -> "II"; "3" -> "III"; else -> n ?: "I" }

    private suspend fun fetchLecturers(ids: List<String>): Map<String, Lecturer> {
        if (ids.isEmpty()) return emptyMap()
        val out = mutableMapOf<String, Lecturer>()
        ids.chunked(40).forEach { chunk ->
            val j = callFallback("users/users", mapOf("user_ids" to chunk.joinToString("|")),
                "id|first_name|last_name|titles|email|office_hours|room|employment_positions",
                "id|first_name|last_name|titles|email",
                "id|first_name|last_name") ?: return@forEach
            j.obj.forEach { (id, u) ->
                if (u !is JsonObject) return@forEach
                val before = u["titles"]["before"].str.orEmpty()
                val after = u["titles"]["after"].str.orEmpty()
                val name = listOf(before, "${u["first_name"].str.orEmpty()} ${u["last_name"].str.orEmpty()}", after)
                    .filter { it.isNotBlank() }.joinToString(" ").trim()
                val room = u["room"].let { r -> listOf(r["building_name"].pl, r["number"].str.orEmpty()).filter { it.isNotBlank() }.joinToString(" ") }
                val unit = u["employment_positions"].arr.firstOrNull()?.get("faculty")?.get("name").pl
                out[id] = Lecturer(id, name, unit, room, u["office_hours"].pl.replace("\n", " ").take(60), u["email"].str.orEmpty())
            }
        }
        return out
    }

    /** Sprawdziany: drzewo węzłów (foldery = bloki, zadania = pozycje) + punkty studenta. */
    private suspend fun fetchTests(participant: JsonElement?, termId: String): Pair<Map<String, List<TestBlock>>, List<FeedItem>> {
        val tests = participant["tests"].obj
        val termTests = (tests[termId] ?: tests.values.lastOrNull()).obj
        if (termTests.isEmpty()) return emptyMap<String, List<TestBlock>>() to emptyList()

        // „own” = punkty studenta zwrócone razem z węzłem (node2: students_points)
        data class Node(val id: String, val name: String, val type: String, val max: Double?, val children: List<Node>, val own: JsonElement?)
        fun parseNode(j: JsonElement?): Node? {
            val id = j["node_id"].str ?: j["id"].str ?: return null
            val max = j["task_node_details"]["points_max"].dbl ?: j["points_max"].dbl
            val own = j["students_points"].takeIf { it is JsonObject && it.isNotEmpty() }
            return Node(id, j["name"].pl, j["type"].str ?: "", max, j["subnodes"].arr.ifEmpty { j["children"].arr }.mapNotNull(::parseNode), own)
        }
        // node2 używa „task”, starsze crstests/node - „pkt”
        fun isTask(n: Node) = n.type == "task" || n.type == "pkt"

        // crstests/node2 (USOS 7+), z zagnieżdżeniem do 3 poziomów; starsze instalacje → crstests/node
        val leaf = "id|name|type|task_node_details[points_max]|students_points"
        val node2Fields = "$leaf|subnodes[$leaf|subnodes[$leaf|subnodes[$leaf]]]"

        val byCourse = mutableMapOf<String, MutableList<TestBlock>>()
        val feed = mutableListOf<Triple<String, FeedItem, String>>()
        for ((rootId, info) in termTests) {
            val courseId = info["course_edition"]["course_id"].str ?: continue
            val courseName = info["course_edition"]["course_name"].pl
            val tree = (callFallback("crstests/node2", mapOf("node_id" to rootId), node2Fields)
                ?: callFallback("crstests/node", mapOf("node_id" to rootId, "recursive" to "true"),
                    "node_id|name|type|points_max|subnodes",
                    "node_id|name|type|points_max|children",
                    "node_id|name|type|subnodes"))?.let(::parseNode) ?: continue

            val leaves = mutableListOf<Node>()
            fun collect(n: Node) { if (isTask(n)) leaves += n; n.children.forEach(::collect) }
            collect(tree)
            if (leaves.isEmpty()) continue
            val missing = leaves.filter { it.own == null }
            val fetched = if (missing.isEmpty()) emptyMap() else runCatching {
                c.call("crstests/user_points", mapOf("node_ids" to missing.joinToString("|") { it.id }))
            }.getOrNull().arr.associateBy({ it["node_id"].str ?: "" }, { it })
            val pts: Map<String, JsonElement> = leaves.mapNotNull { n -> (n.own ?: fetched[n.id])?.let { n.id to it } }.toMap()

            fun item(n: Node) = TestItem(n.name.ifBlank { "Punkty" }, pts[n.id]?.get("points").dbl, n.max ?: 0.0)
            val blocks = mutableListOf<TestBlock>()
            val loose = mutableListOf<TestItem>()
            tree.children.forEach { ch ->
                if (isTask(ch)) loose += item(ch)
                else {
                    val l = mutableListOf<Node>(); fun c2(n: Node) { if (isTask(n)) l += n; n.children.forEach(::c2) }; c2(ch)
                    if (l.isNotEmpty()) {
                        val comment = l.mapNotNull { pts[it.id]?.get("comment").str }.firstOrNull { it.isNotBlank() }.orEmpty()
                        blocks += TestBlock(ch.name, l.map(::item), comment, id = ch.id)
                    }
                }
            }
            if (loose.isNotEmpty()) blocks += TestBlock(tree.name.ifBlank { "Punkty" }, loose, id = tree.id)
            byCourse.getOrPut(courseId) { mutableListOf() } += blocks

            // kanał „Nowe wyniki”
            blocks.forEach { b ->
                val (p, done) = Grades.blockPoints(b)
                if (!done) return@forEach
                val changed = b.items.indices.mapNotNull { i ->
                    leaves.firstOrNull { it.name == b.items[i].name }?.let { pts[it.id]?.get("last_changed").str }
                }.maxOrNull() ?: ""
                feed += Triple(changed, FeedItem(courseId, courseName, "${b.name} · ${(p.pct * 100).toInt()}%",
                    "${Grades.fmtPts(p.got)}/${Grades.fmtPts(p.max)}", p.pct, changed.take(10).ifBlank { LocalDate.now().toString() }), b.name)
            }
        }
        return byCourse to feed.sortedByDescending { it.first }.take(3).map { it.second }
    }

    /** Kolejne terminy zajęć danej grupy (tt/classgroup_dates2). */
    suspend fun groupDates(unitId: String, group: String): List<String> = runCatching {
        c.call("tt/classgroup_dates2", mapOf("unit_id" to unitId, "group_number" to group, "fields" to "start_time"))
            .arr.mapNotNull { it["start_time"].str?.take(10) }
    }.getOrDefault(emptyList())
}
