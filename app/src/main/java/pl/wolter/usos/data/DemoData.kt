package pl.wolter.usos.data

import java.time.LocalDate

/** Dane z projektu „Aplikacja studenta v3” - tryb demo bez logowania do USOS. */
object DemoData {
    private val L = listOf(
        listOf("prof. dr hab. Marek Kowal", "Instytut Informatyki", "4120", "wt 12:30-14:00"),
        listOf("dr hab. Jan Nowak", "Instytut Matematyki", "5040", "pon 10:00-11:30"),
        listOf("dr Kamil Dąb", "Instytut Matematyki", "5012", "śr 9:00-10:00"),
        listOf("dr hab. Iga Mróz", "Instytut Informatyki", "3105", "czw 12:00-13:30"),
        listOf("mgr Ewa Zielińska", "Instytut Informatyki", "2.16", "pon 12:00-13:00"),
        listOf("dr Piotr Wiśniewski", "Instytut Informatyki", "3110", "śr 10:00-11:30"),
        listOf("dr Ola Wrona", "Instytut Informatyki", "1.14", "pt 12:00-13:00"),
        listOf("mgr Anna Lis", "Studium Języków Obcych", "1.10", "pt 10:00-11:00"),
        listOf("prof. Adam Grab", "Wydział Fizyki", "0.21", "czw 14:00-15:00"),
        listOf("dr Lena Sowa", "Wydział Fizyki", "1.42", "wt 13:00-14:00"),
        listOf("mgr Tomasz Bór", "Instytut Informatyki", "2.08", "śr 14:00-15:00"),
    ).mapIndexed { i, l ->
        val last = l[0].split(' ').last().lowercase()
            .replace("ą", "a").replace("ć", "c").replace("ę", "e").replace("ł", "l").replace("ń", "n")
            .replace("ó", "o").replace("ś", "s").replace("ź", "z").replace("ż", "z")
        Lecturer("$i", l[0], l[1], l[2], l[3], "$last@uczelnia.edu.pl")
    }

    private fun tb(name: String, comment: String, vararg items: Triple<String, Double?, Double>) =
        TestBlock(name, items.map { TestItem(it.first, it.second, it.third) }, comment)

    private fun t(n: String, p: Number?, m: Number) = Triple(n, p?.toDouble(), m.toDouble())

    private val COURSES = listOf(
        Course("ASD", "Algorytmy i struktury danych", 6.0, "1000-213ASD", listOf(
            tb("Kolokwium 1", "Sprawdził: prof. M. Kowal · 26.10", t("Zad. 1", 8, 8), t("Zad. 2", 7, 9), t("Zad. 3", 8, 8)),
            tb("Laboratoria", "Aktualizowane co tydzień", t("Lab 1-4", 34, 40)),
            tb("Kolokwium 2", "6 listopada, aula B", t("-", null, 25)),
        )),
        Course("AM", "Analiza matematyczna I", 6.0, "1000-111AM1", listOf(
            tb("Kartkówki", "Najsłabsza nie liczy się", t("K1", 4, 5), t("K2", 5, 5), t("K3", 2, 5), t("K4", 3, 5)),
            tb("Kolokwium 1", "Poprawa 13.11", t("Zad. 1-4", 31, 40)),
        )),
        Course("BD", "Bazy danych", 5.0, "1000-214BD", listOf(
            tb("Projekt · etap 1", "Etap 2 do 20.11", t("Model ER", 9, 10)),
            tb("Laboratoria", "", t("Lab 1-4", 18, 20)),
        )),
        Course("PO", "Programowanie obiektowe", 6.0, "1000-212PO", listOf(
            tb("Kolokwium", "", t("Teoria", 9, 10), t("Kod", 8, 10)),
            tb("Projekt", "Oddanie w styczniu", t("-", null, 30)),
        )),
        Course("SO", "Systemy operacyjne", 5.0, "1000-215SO", listOf(tb("Laboratoria", "", t("Lab 1-3", 12, 15)))),
        Course("FIZ", "Fizyka dla informatyków", 4.0, "1300-FIZ-INF", listOf(tb("Kolokwium 1", "Poprawa 20.11", t("Zad. 1-4", 12, 20)))),
        Course("ANG", "Język angielski B2", 2.0, "4000-ANG-B2", listOf(tb("Test 1", "", t("Grammar", 22, 25), t("Reading", 19, 25)))),
    )

    private val LABEL = mapOf(
        ClassType.WYK to "Wykład", ClassType.CW to "Ćwiczenia", ClassType.LAB to "Laboratorium",
        ClassType.LEK to "Lektorat", ClassType.EGZ to "Egzamin",
    )

    private val A = listOf(
        listOf(0, "8:15", "9:45", "AM", "WYK", 1, "Wydział Matematyki", "A-1", 1, "co tydzień"),
        listOf(0, "10:00", "11:30", "BD", "LAB", 3, "Budynek C", "2.14", 4, "co tydzień"),
        listOf(0, "12:00", "13:30", "PO", "CW", 2, "Budynek C", "3.02", 5, "co tydzień"),
        listOf(0, "15:15", "16:45", "ANG", "LEK", 7, "Studium Języków", "1.08", 7, "co tydzień"),
        listOf(1, "9:00", "10:30", "ASD", "WYK", 1, "Budynek B", "aula B", 0, "co tydzień"),
        listOf(1, "10:45", "12:15", "AM", "CW", 4, "Wydział Matematyki", "2.01", 2, "co tydzień"),
        listOf(1, "14:00", "15:30", "SO", "LAB", 2, "Budynek C", "1.12", 6, "co 2 tyg. (np.)"),
        listOf(2, "8:15", "9:45", "PO", "WYK", 1, "Budynek B", "aula A", 5, "co tydzień"),
        listOf(2, "12:00", "13:30", "ASD", "LAB", 5, "Budynek C", "2.10", 10, "co tydzień"),
        listOf(2, "14:00", "15:30", "BD", "WYK", 1, "Budynek C", "aula C", 3, "co tydzień"),
        listOf(3, "10:00", "11:30", "FIZ", "WYK", 1, "Wydział Fizyki", "0.03", 8, "co tydzień"),
        listOf(3, "12:00", "13:30", "FIZ", "CW", 3, "Wydział Fizyki", "1.40", 9, "co 2 tyg. (np.)"),
        listOf(4, "8:15", "9:45", "ANG", "LEK", 7, "Studium Języków", "1.08", 7, "co tydzień"),
        listOf(4, "10:00", "11:30", "SO", "WYK", 1, "Budynek B", "aula B", 6, "co tydzień"),
        listOf(4, "12:00", "13:00", "ASD", "EGZ", 1, "Budynek B", "aula B", 0, "jednorazowo"),
        listOf(4, "13:30", "15:00", "PO", "LAB", 2, "Budynek C", "2.14", 4, "co tydzień"),
    )

    private fun pad(t: String) = if (t.length == 4) "0$t" else t

    /** Plan demo dla dowolnego tygodnia: powtarza się co tydzień, „co 2 tyg.” co drugi, egzamin tylko raz. */
    fun weekEvents(mon: LocalDate, today: LocalDate = LocalDate.now()): List<ClassEvent> {
        val k = java.time.temporal.ChronoUnit.WEEKS.between(Time.monday(today), mon)
        return A.mapIndexedNotNull { i, a ->
            val type = ClassType.valueOf(a[4] as String)
            val freq = a[9] as String
            if (type == ClassType.EGZ && k != 0L) return@mapIndexedNotNull null
            if (freq.startsWith("co 2") && k % 2 != 0L) return@mapIndexedNotNull null
            val c = COURSES.first { it.id == a[3] }
            ClassEvent(
                id = "demo$mon-$i", date = mon.plusDays((a[0] as Int).toLong()).toString(),
                start = pad(a[1] as String), end = pad(a[2] as String), courseId = c.id, courseName = c.name,
                type = type, typeLabel = LABEL[type]!!, group = "${a[5]}", building = a[6] as String,
                room = a[7] as String, lecturerIds = listOf("${a[8]}"), freq = freq,
            )
        }
    }

    fun build(today: LocalDate = LocalDate.now()): StudentData {
        val mon = Time.monday(today)
        val weeks = (0 until 3).map { mon.plusDays(7L * it) }
        val events = weeks.flatMap { weekEvents(it, today) }
        val courses = COURSES.map { c ->
            c.copy(groups = events.filter { it.courseId == c.id && it.type != ClassType.EGZ }
                .distinctBy { it.type to it.group }
                .map { CourseGroup(it.type, it.typeLabel, it.group, it.lecturerIds) })
        }
        val past = listOf(
            PastTerm("2025/26 L", listOf(
                FinalGrade("Matematyka dyskretna", 6.0, "5", "I termin · 24.06"),
                FinalGrade("Podstawy programowania II", 8.0, "4,5", "I termin · 27.06"),
                FinalGrade("Architektura komputerów", 5.0, "4", "I termin · 30.06"),
                FinalGrade("Logika dla informatyków", 5.0, "3,5", "I termin: 2 · II termin: 3,5"),
                FinalGrade("Język angielski B1", 2.0, "5", "zaliczenie"),
                FinalGrade("Wychowanie fizyczne", 0.0, "ZAL", "zaliczenie"),
            )),
            PastTerm("2025/26 Z", listOf(
                FinalGrade("Wstęp do informatyki", 6.0, "5", "I termin · 29.01"),
                FinalGrade("Algebra liniowa", 6.0, "4", "I termin · 3.02"),
                FinalGrade("Podstawy programowania I", 8.0, "4,5", "I termin · 6.02"),
                FinalGrade("Analiza - wstęp", 5.0, "4", "I termin · 9.02"),
                FinalGrade("BHP", 0.0, "ZAL", "zaliczenie"),
            )),
        )
        val exams = listOf(
            Exam(mon.plusDays(4).toString(), "Algorytmy · kolokwium 1 (popr.)", "12:00 · aula B", "ASD", kind = "Kolokwium", time = "12:00"),
            Exam(mon.plusDays(11).toString(), "Algorytmy · kolokwium 2", "12:00 · aula B · bud. B", "ASD", kind = "Kolokwium", time = "12:00"),
            Exam(mon.plusDays(18).toString(), "Analiza · poprawa kolokwium", "10:45 · s. 2.01", "AM", kind = "Kolokwium", time = "10:45"),
            Exam(mon.plusDays(95).toString(), "Analiza matematyczna I", "Egzamin · I termin · aula A-1", "AM", kind = "Egzamin"),
            Exam(mon.plusDays(99).toString(), "Programowanie obiektowe", "Egzamin · I termin · aula A", "PO", kind = "Egzamin"),
        )
        val feed = listOf(
            FeedItem("ASD", "Algorytmy i struktury danych", "Kolokwium 1 · 92%", "23/25", 0.92, today.toString()),
            FeedItem("BD", "Bazy danych", "Projekt · etap 1", "9/10", 0.9, today.minusDays(1).toString()),
            FeedItem("FIZ", "Fizyka dla informatyków", "Kolokwium 1 · 60%", "12/20", 0.6, today.minusDays(3).toString()),
        )
        return StudentData(
            student = Student("Anna", "Kowalska", "Informatyka I st. · II rok", "412857", today.plusMonths(5).toString(), "UW"),
            termLabel = "2026/27 Z",
            courses = courses,
            events = events,
            lecturers = L.associateBy { it.id },
            pastTerms = past,
            exams = exams,
            alerts = listOf(
                Alert("red", "Opłata za akademik · 650 zł", "Termin 10 listopada · services/payments"),
                Alert("blue", "Rejestracja na WF otwarta", "Do 31 października · services/registrations"),
                Alert("orange", "3 ankiety do wypełnienia", "Ocena zajęć 2025/26 L · services/surveys"),
            ),
            feed = feed,
            moreRows = listOf(
                MoreRow("Płatności", "services/payments", "650 zł", "red"),
                MoreRow("Rejestracje", "services/registrations", "1 otwarta", "blue"),
                MoreRow("Ankiety", "services/surveys", "3", "orange"),
                MoreRow("Aktualności", "services/news", "5 nowych"),
                MoreRow("Punkty ECTS i etapy", "services/progs · credits", "90 / 180"),
                MoreRow("Uczelnia", "tryb demo", "UW"),
            ),
            holidays = "2 lis · 11 lis · 23 gru - 6 sty · sesja od 26 sty",
            weekParity = "tydz. nieparzysty",
            fetchedAt = System.currentTimeMillis(),
            demo = true,
            loadedWeeks = weeks.map { it.toString() },
        )
    }
}
