package ru.vlaach.studyhelper

import androidx.compose.ui.graphics.Color

data class Lesson(
    val id: Int,
    val title: String,
    val startTime: String,
    val endTime: String,
    val room: String,
    val teacher: String,
    val subjectColor: Color,
    var isCompleted: Boolean = false,
    var homework: String = ""
) {
    fun isContentEqual(other: Lesson): Boolean {
        return title == other.title &&
                startTime == other.startTime &&
                endTime == other.endTime &&
                room == other.room &&
                teacher == other.teacher
    }
}