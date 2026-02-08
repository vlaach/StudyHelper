package ru.vlaach.studyhelper

import android.app.Application
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.AndroidViewModel
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.concurrent.atomic.AtomicInteger

class ScheduleViewModel(application: Application) : AndroidViewModel(application) {

    private val sharedPreferences = application.getSharedPreferences("study_helper_data", Context.MODE_PRIVATE)
    private val gson = Gson()
    private val nextId = AtomicInteger(100)

    var isMasterScheduleMode = mutableStateOf(false)
        private set

    private val _specificSchedule = mutableStateMapOf<LocalDate, List<Lesson>>()
    private val _masterSchedule = mutableStateMapOf<DayOfWeek, List<Lesson>>()
    var specificHolidays = mutableStateOf(setOf<LocalDate>())
    var masterHolidays = mutableStateOf(setOf<DayOfWeek>())

    var selectedDate = mutableStateOf(getInitialSelectedDate())
        private set
    var selectedMasterDay = mutableStateOf(DayOfWeek.MONDAY)
        private set

    var lessonToEdit = mutableStateOf<Lesson?>(null)
    var showAddLessonDialog = mutableStateOf(false)
    var showHomeworkDialog = mutableStateOf<Lesson?>(null)


    val weekTabs: List<Any>
        get() = if (isMasterScheduleMode.value) {
            DayOfWeek.values().toList()
        } else {
            val startOfWeek = selectedDate.value.with(DayOfWeek.MONDAY)
            (0..6).map { startOfWeek.plusDays(it.toLong()) }
        }

    val lessonsForCurrentView: List<Lesson>
        get() {
            if (isMasterScheduleMode.value) {
                return _masterSchedule[selectedMasterDay.value]?.sortedBy { it.startTime } ?: emptyList()
            } else {
                val date = selectedDate.value
                if (isHoliday(date)) return emptyList()
                if (_specificSchedule.containsKey(date)) {
                    return _specificSchedule[date]?.sortedBy { it.startTime } ?: emptyList()
                }
                return _masterSchedule[date.dayOfWeek]?.map { it.copy() }?.sortedBy { it.startTime } ?: emptyList()
            }
        }

    val isCurrentDayModified: Boolean
        get() {
            if (isMasterScheduleMode.value) return false
            val date = selectedDate.value
            val isMasterHol = masterHolidays.value.contains(date.dayOfWeek)
            val isEffectiveHol = isHoliday(date)
            if (isMasterHol != isEffectiveHol) return true

            val specificLessons = _specificSchedule[date] ?: return false
            val masterLessons = _masterSchedule[date.dayOfWeek] ?: emptyList()
            if (specificLessons.size != masterLessons.size) return true

            val sortedSpecific = specificLessons.sortedBy { it.startTime }
            val sortedMaster = masterLessons.sortedBy { it.startTime }

            for (i in sortedSpecific.indices) {
                if (!sortedSpecific[i].isContentEqual(sortedMaster[i])) return true
            }
            return false
        }

    fun getCurrentOrNextLesson(currentTime: LocalTime): Pair<Lesson?, String> {
        if (isMasterScheduleMode.value || selectedDate.value != LocalDate.now()) return null to ""
        val lessons = lessonsForCurrentView.sortedBy { it.startTime }

        for (lesson in lessons) {
            val start = parseTime(lesson.startTime)
            val end = parseTime(lesson.endTime)
            if (currentTime.isAfter(start) && currentTime.isBefore(end)) return lesson to "ENDS_IN"
            if (currentTime.isBefore(start)) return lesson to "STARTS_IN"
        }
        return null to "FINISHED"
    }

    fun getTimeRemainingString(targetTimeStr: String, now: LocalTime): String {
        val target = parseTime(targetTimeStr)
        val diff = ChronoUnit.SECONDS.between(now, target)
        if (diff < 0) return "00:00"
        val minutes = diff / 60
        val seconds = diff % 60
        return String.format("%02d:%02d", minutes, seconds)
    }

    private fun parseTime(timeStr: String): LocalTime {
        return try { LocalTime.parse(timeStr, DateTimeFormatter.ofPattern("HH:mm")) } catch (e: Exception) { LocalTime.MIN }
    }

    private fun getInitialSelectedDate(): LocalDate {
        val today = LocalDate.now()
        return if (today.dayOfWeek == DayOfWeek.SUNDAY) today.plusDays(1) else today
    }

    fun isHoliday(date: LocalDate): Boolean {
        if (isMasterScheduleMode.value) return masterHolidays.value.contains(selectedMasterDay.value)
        if (specificHolidays.value.contains(date)) return true
        return masterHolidays.value.contains(date.dayOfWeek) && !_specificSchedule.containsKey(date)
    }

    init {
        loadData()
    }

    fun toggleMasterMode() {
        isMasterScheduleMode.value = !isMasterScheduleMode.value
        if (isMasterScheduleMode.value) selectedMasterDay.value = DayOfWeek.MONDAY
        else selectedDate.value = LocalDate.now()
    }

    fun onTabSelected(index: Int) {
        if (isMasterScheduleMode.value) selectedMasterDay.value = DayOfWeek.values()[index]
        else selectedDate.value = selectedDate.value.with(DayOfWeek.MONDAY).plusDays(index.toLong())
    }

    fun onPreviousWeek() { if (!isMasterScheduleMode.value) selectedDate.value = selectedDate.value.minusWeeks(1) }
    fun onNextWeek() { if (!isMasterScheduleMode.value) selectedDate.value = selectedDate.value.plusWeeks(1) }

    fun onAddClicked() {
        lessonToEdit.value = null
        showAddLessonDialog.value = true
    }

    fun onEditClicked(lesson: Lesson) {
        lessonToEdit.value = lesson
        showAddLessonDialog.value = true
    }

    fun saveLesson(id: Int?, title: String, startTime: String, endTime: String, room: String, teacher: String) {
        if (id != null) {
            editLessonInternal(id, title, startTime, endTime, room, teacher)
        } else {
            addLessonInternal(title, startTime, endTime, room, teacher)
        }
        showAddLessonDialog.value = false
        lessonToEdit.value = null
        saveData()
    }

    private fun addLessonInternal(title: String, startTime: String, endTime: String, room: String, teacher: String) {
        val newLesson = Lesson(
            id = nextId.getAndIncrement(),
            title = title, startTime = startTime, endTime = endTime, room = room, teacher = teacher,
            subjectColor = getSubjectColor(title)
        )
        if (isMasterScheduleMode.value) {
            val day = selectedMasterDay.value
            _masterSchedule[day] = (_masterSchedule[day] ?: emptyList()) + newLesson
            masterHolidays.value = masterHolidays.value - day
        } else {
            val date = selectedDate.value
            ensureSpecificDayExists(date)
            _specificSchedule[date] = (_specificSchedule[date] ?: emptyList()) + newLesson
            specificHolidays.value = specificHolidays.value - date
        }
    }

    private fun editLessonInternal(id: Int, title: String, startTime: String, endTime: String, room: String, teacher: String) {
        val updater: (List<Lesson>?) -> List<Lesson> = { list ->
            list?.map {
                if (it.id == id) it.copy(title = title, startTime = startTime, endTime = endTime, room = room, teacher = teacher, subjectColor = getSubjectColor(title))
                else it
            } ?: emptyList()
        }

        if (isMasterScheduleMode.value) {
            val day = selectedMasterDay.value
            _masterSchedule[day] = updater(_masterSchedule[day])
        } else {
            val date = selectedDate.value
            ensureSpecificDayExists(date)
            val list = _specificSchedule[date] ?: emptyList()
            var targetId = id

            if (list.none { it.id == id }) {
                val snapshot = lessonToEdit.value
                val match = list.find { it.startTime == snapshot?.startTime && it.title == snapshot?.title }
                if (match != null) targetId = match.id
            }

            _specificSchedule[date] = list.map {
                if (it.id == targetId) it.copy(title = title, startTime = startTime, endTime = endTime, room = room, teacher = teacher, subjectColor = getSubjectColor(title))
                else it
            }
        }
    }

    fun deleteLesson(lessonId: Int) {
        if (isMasterScheduleMode.value) {
            val day = selectedMasterDay.value
            _masterSchedule[day] = (_masterSchedule[day] ?: emptyList()).filter { it.id != lessonId }
        } else {
            val date = selectedDate.value
            ensureSpecificDayExists(date)
            _specificSchedule[date] = (_specificSchedule[date] ?: emptyList()).filter { it.id != lessonId }
        }
        saveData()
    }

    fun resetCurrentDayToMaster() {
        if (isMasterScheduleMode.value) return
        val date = selectedDate.value
        _specificSchedule.remove(date)
        specificHolidays.value = specificHolidays.value - date
        saveData()
    }

    fun toggleHoliday() {
        if (isMasterScheduleMode.value) {
            val day = selectedMasterDay.value
            if (masterHolidays.value.contains(day)) {
                masterHolidays.value = masterHolidays.value - day
            } else {
                masterHolidays.value = masterHolidays.value + day
                _masterSchedule.remove(day)
            }
        } else {
            val date = selectedDate.value
            if (isHoliday(date)) {
                specificHolidays.value = specificHolidays.value - date
                if (masterHolidays.value.contains(date.dayOfWeek)) {
                    if (!_specificSchedule.containsKey(date)) _specificSchedule[date] = emptyList()
                }
            } else {
                specificHolidays.value = specificHolidays.value + date
                _specificSchedule.remove(date)
            }
        }
        saveData()
    }

    fun toggleHomeworkCompletion(lessonId: Int) {
        if (isMasterScheduleMode.value) return
        updateLessonSafely(lessonId) { it.copy(isCompleted = !it.isCompleted) }
    }

    fun updateHomeworkText(lessonId: Int, text: String) {
        if (isMasterScheduleMode.value) return
        updateLessonSafely(lessonId) { it.copy(homework = text) }
        showHomeworkDialog.value = null
    }

    private fun updateLessonSafely(lessonId: Int, update: (Lesson) -> Lesson) {
        val date = selectedDate.value
        if (!_specificSchedule.containsKey(date)) {
            val masterLessons = _masterSchedule[date.dayOfWeek] ?: emptyList()
            _specificSchedule[date] = masterLessons.map { it.copy(id = nextId.getAndIncrement()) }
        }
        val list = _specificSchedule[date] ?: return
        val targetLesson = list.find { it.id == lessonId }
            ?: list.find {
                val phantom = lessonsForCurrentView.find { p -> p.id == lessonId }
                phantom != null && it.startTime == phantom.startTime
            }
        if (targetLesson != null) {
            _specificSchedule[date] = list.map { if (it == targetLesson) update(it) else it }
        }
        saveData()
    }

    private fun ensureSpecificDayExists(date: LocalDate) {
        if (!_specificSchedule.containsKey(date)) {
            val masterLessons = _masterSchedule[date.dayOfWeek] ?: emptyList()
            _specificSchedule[date] = masterLessons.map { it.copy(id = nextId.getAndIncrement()) }
        }
    }

    private fun getSubjectColor(subject: String): Color {
        val s = subject.lowercase()
        return when {
            "math" in s || "алгеб" in s || "геом" in s || "матем" in s -> Color(0xFFE57373)
            "lit" in s || "лит" in s || "рус" in s -> Color(0xFFFFF176)
            "phys" in s || "физ" in s -> Color(0xFF64B5F6)
            "bio" in s || "био" in s -> Color(0xFF81C784)
            "hist" in s || "ист" in s -> Color(0xFFFFB74D)
            "eng" in s || "анг" in s -> Color(0xFFBA68C8)
            "pe" in s || "fiz" in s || "спорт" in s -> Color(0xFFA1887F)
            else -> Color(0xFFE0E0E0)
        }
    }

    private fun saveData() {
        val editor = sharedPreferences.edit()
        editor.putString("specific_schedule", gson.toJson(_specificSchedule.entries.associate { it.key.toString() to it.value }))
        editor.putString("master_schedule", gson.toJson(_masterSchedule.entries.associate { it.key.name to it.value }))
        editor.putString("specific_holidays", gson.toJson(specificHolidays.value.map { it.toString() }))
        editor.putString("master_holidays", gson.toJson(masterHolidays.value.map { it.name }))
        editor.apply()

        val context = getApplication<Application>()
        val intent = Intent(context, ScheduleService::class.java).apply {
            action = ScheduleService.ACTION_RELOAD_DATA
        }

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            context.startForegroundService(intent)
        } else {
            context.startService(intent)
        }
    }

    private fun loadData() {
        try {
            val specJson = sharedPreferences.getString("specific_schedule", null)
            if (specJson != null) {
                val type = object : TypeToken<Map<String, List<Lesson>>>() {}.type
                val data: Map<String, List<Lesson>> = gson.fromJson(specJson, type)
                _specificSchedule.clear()
                data.forEach { (k, v) -> _specificSchedule[LocalDate.parse(k)] = v }
            }
            val masterJson = sharedPreferences.getString("master_schedule", null)
            if (masterJson != null) {
                val type = object : TypeToken<Map<String, List<Lesson>>>() {}.type
                val data: Map<String, List<Lesson>> = gson.fromJson(masterJson, type)
                _masterSchedule.clear()
                data.forEach { (k, v) -> _masterSchedule[DayOfWeek.valueOf(k)] = v }
            }
            val specHolJson = sharedPreferences.getString("specific_holidays", null)
            if (specHolJson != null) {
                val type = object : TypeToken<List<String>>() {}.type
                specificHolidays.value = gson.fromJson<List<String>>(specHolJson, type).map { LocalDate.parse(it) }.toSet()
            }
            val masterHolJson = sharedPreferences.getString("master_holidays", null)
            if (masterHolJson != null) {
                val type = object : TypeToken<List<String>>() {}.type
                masterHolidays.value = gson.fromJson<List<String>>(masterHolJson, type).map { DayOfWeek.valueOf(it) }.toSet()
            }
            val maxId = (_specificSchedule.values.flatten() + _masterSchedule.values.flatten()).maxOfOrNull { it.id } ?: 100
            nextId.set(maxId + 1)
        } catch (e: Exception) { e.printStackTrace() }
    }
}