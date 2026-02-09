package ru.vlaach.studyhelper

data class ScheduleBackup(
    val specificSchedule: Map<String, List<Lesson>>,
    val masterSchedule: Map<String, List<Lesson>>,
    val specificHolidays: List<String>,
    val masterHolidays: List<String>
)