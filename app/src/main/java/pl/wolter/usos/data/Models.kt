package pl.wolter.usos.data

import kotlinx.serialization.Serializable

@Serializable
enum class ClassType { WYK, CW, LAB, LEK, EGZ, SEM, INNE }

@Serializable
data class Student(
    val firstName: String,
    val lastName: String,
    val programme: String,
    val index: String,
    val cardValidUntil: String? = null,
    val university: String = "",
)

@Serializable
data class Lecturer(
    val id: String,
    val name: String,
    val unit: String = "",
    val room: String = "",
    val hours: String = "",
    val email: String = "",
) {
    val initials: String
        get() = name.split(' ').filter { it.isNotBlank() && !it.endsWith('.') }.takeLast(2)
            .mapNotNull { it.firstOrNull()?.uppercaseChar() }.joinToString("")
}

@Serializable
data class ClassEvent(
    val id: String,
    val date: String,          // yyyy-MM-dd
    val start: String,         // HH:mm
    val end: String,
    val courseId: String,
    val courseName: String,
    val type: ClassType,
    val typeLabel: String,
    val group: String = "",
    val building: String = "",
    val room: String = "",
    val lecturerIds: List<String> = emptyList(),
    val freq: String = "",
    val unitId: String = "",
)

@Serializable
data class TestItem(val name: String, val points: Double?, val max: Double)

@Serializable
data class TestBlock(
    val name: String,
    val items: List<TestItem>,
    val comment: String = "",
    val manual: Boolean = false,
    val id: String = "",
)

@Serializable
data class CourseGroup(
    val type: ClassType,
    val typeLabel: String,
    val group: String,
    val lecturerIds: List<String> = emptyList(),
)

@Serializable
data class Course(
    val id: String,
    val name: String,
    val ects: Double,
    val code: String,
    val tests: List<TestBlock> = emptyList(),
    val groups: List<CourseGroup> = emptyList(),
)

@Serializable
data class FinalGrade(val name: String, val ects: Double, val value: String, val note: String)

@Serializable
data class PastTerm(val label: String, val rows: List<FinalGrade>)

@Serializable
data class Exam(
    val date: String,
    val name: String,
    val meta: String,
    val courseId: String,
    val kind: String = "",
    val time: String = "",
    val id: String = "",
    val own: Boolean = false,
    val shared: Boolean = false,
    val author: String = "",
)

/** Termin dodany ręcznie (kolokwia zwykle nie trafiają do USOS). Synchronizowany przez Firebase. */
@Serializable
data class MyExam(
    val id: String,
    val kind: String,          // Kolokwium, Egzamin, Kartkówka, Projekt, Inne
    val courseId: String,
    val courseName: String,
    val date: String,          // yyyy-MM-dd
    val time: String = "",     // HH:mm
    val room: String = "",
    val note: String = "",
    val shared: Boolean = false,   // termin grupy (Firestore usosGroups/{kod}/exams)
    val author: String = "",
    val authorUid: String = "",
)

@Serializable
data class Alert(val kind: String, val title: String, val sub: String)

@Serializable
data class FeedItem(
    val courseId: String,
    val courseName: String,
    val what: String,
    val value: String,
    val pct: Double,
    val date: String,
)

@Serializable
data class MoreRow(val label: String, val sub: String, val value: String, val kind: String = "neutral")

@Serializable
data class StudentData(
    val student: Student,
    val termLabel: String,
    val courses: List<Course>,
    val events: List<ClassEvent>,
    val lecturers: Map<String, Lecturer>,
    val pastTerms: List<PastTerm>,
    val exams: List<Exam>,
    val alerts: List<Alert>,
    val feed: List<FeedItem>,
    val moreRows: List<MoreRow>,
    val holidays: String = "",
    val weekParity: String = "",
    val fetchedAt: Long = 0,
    val demo: Boolean = false,
    val loadedWeeks: List<String> = emptyList(),   // poniedziałki tygodni obecnych w events
)

@Serializable
data class Settings(
    val hidden: Set<ClassType> = emptySet(),
    val notif: Boolean = true,
    val grades: Boolean = true,
    val webcal: Boolean = false,
    val thresholds: List<Double> = listOf(0.9, 0.8, 0.7, 0.6, 0.5),
    val manual: Map<String, List<TestBlock>> = emptyMap(),
    val theme: Int = 0, // 0 system, 1 jasny, 2 ciemny
    val myExams: List<MyExam> = emptyList(),
    val groupCode: String = "",
    val groupName: String = "",
    val updatedAt: Long = 0,
)

@Serializable
data class UsosSession(
    val baseUrl: String,
    val university: String,
    val consumerKey: String,
    val consumerSecret: String,
    val token: String,
    val tokenSecret: String,
)
