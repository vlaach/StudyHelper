package ru.vlaach.studyhelper

import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.*
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale

class ScheduleService : Service() {

    companion object {
        const val ACTION_RELOAD_DATA = "ACTION_RELOAD_DATA"
    }

    private val serviceJob = Job()
    private val serviceScope = CoroutineScope(Dispatchers.Main + serviceJob)
    private val gson = Gson()
    private lateinit var prefs: SharedPreferences

    private var specificSchedule = mapOf<LocalDate, List<Lesson>>()
    private var masterSchedule = mapOf<DayOfWeek, List<Lesson>>()
    private var specificHolidays = setOf<LocalDate>()
    private var masterHolidays = setOf<DayOfWeek>()

    private var lastActiveLessonId: Int? = null

    override fun onCreate() {
        super.onCreate()
        prefs = getSharedPreferences("study_helper_data", Context.MODE_PRIVATE)
        NotificationHelper.createChannels(this)
        loadData()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_RELOAD_DATA) {
            loadData()
            updateNotificationLoop()
        } else {
            startForeground(NotificationHelper.NOTIFICATION_ID_PERSISTENT, buildCompactNotification("Study Helper", "Загрузка..."))
            startMonitoring()
        }
        return START_STICKY
    }

    private fun startMonitoring() {
        serviceScope.launch {
            while (isActive) {
                updateNotificationLoop()
                delay(1000)
            }
        }
    }

    private fun updateNotificationLoop() {
        val now = LocalDateTime.now()
        val today = now.toLocalDate()
        val time = now.toLocalTime()

        val lessons = getLessonsForDate(today)
        val (activeLesson, nextLesson) = findCurrentAndNext(lessons, time)

        if (activeLesson != null && activeLesson.id != lastActiveLessonId) {
            val start = parseTime(activeLesson.startTime)
            val secondsSinceStart = ChronoUnit.SECONDS.between(start, time)

            val isFreshStart = secondsSinceStart in 0..60
            val isTransition = lastActiveLessonId != null

            if (isFreshStart || isTransition) {
                sendAlertNotification("Урок начался", "${activeLesson.title} ${if (activeLesson.room.isNotEmpty()) "(каб. ${activeLesson.room})" else ""}")
            }
        } else if (lastActiveLessonId != null && activeLesson == null) {
            val justFinished = lessons.find { it.id == lastActiveLessonId }
            if (justFinished != null) {
                sendAlertNotification("Урок закончен", justFinished.title)
            }
            lastActiveLessonId = null
        }

        val (title, content) = when {
            activeLesson != null -> {
                val end = parseTime(activeLesson.endTime)
                val remainingSeconds = ChronoUnit.SECONDS.between(time, end)
                val timer = formatSeconds(remainingSeconds)

                "${activeLesson.title} ${if (activeLesson.room.isNotEmpty()) "(каб. ${activeLesson.room})" else ""}" to "Конец через $timer"
            }
            nextLesson != null -> {
                val start = parseTime(nextLesson.startTime)
                val remainingSeconds = ChronoUnit.SECONDS.between(time, start)
                val timer = formatSeconds(remainingSeconds)

                "Следующий: ${nextLesson.title} ${if(nextLesson.room.isNotEmpty()) "(каб. ${nextLesson.room})" else ""}" to "Начало через $timer"
            }
            else -> {
                "Study Helper" to "Уроки закончились!"
            }
        }

        val notification = buildCompactNotification(title, content)
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(NotificationHelper.NOTIFICATION_ID_PERSISTENT, notification)
    }

    private fun buildCompactNotification(title: String, content: String): Notification {
        val contentIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(this, 0, contentIntent, PendingIntent.FLAG_IMMUTABLE)

        return NotificationCompat.Builder(this, NotificationHelper.CHANNEL_PERSISTENT)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle(title)
            .setContentText(content)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()
    }

    private fun sendAlertNotification(title: String, content: String) {
        val builder = NotificationCompat.Builder(this, NotificationHelper.CHANNEL_ALERTS)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(title)
            .setContentText(content)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)

        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(System.currentTimeMillis().toInt(), builder.build())
    }

    private fun formatSeconds(totalSeconds: Long): String {
        if (totalSeconds < 0) return "00:00"
        val m = totalSeconds / 60
        val s = totalSeconds % 60
        return String.format(Locale.ENGLISH, "%02d:%02d", m, s)
    }

    private fun loadData() {
        try {
            val specJson = prefs.getString("specific_schedule", null)
            val typeSpec = object : TypeToken<Map<String, List<Lesson>>>() {}.type
            if (specJson != null) {
                val temp: Map<String, List<Lesson>> = gson.fromJson(specJson, typeSpec)
                specificSchedule = temp.mapKeys { LocalDate.parse(it.key) }
            }

            val masterJson = prefs.getString("master_schedule", null)
            val typeMaster = object : TypeToken<Map<String, List<Lesson>>>() {}.type
            if (masterJson != null) {
                val temp: Map<String, List<Lesson>> = gson.fromJson(masterJson, typeMaster)
                masterSchedule = temp.mapKeys { DayOfWeek.valueOf(it.key) }
            }

            val sH = prefs.getString("specific_holidays", null)
            if (sH != null) {
                val t = object : TypeToken<List<String>>() {}.type
                specificHolidays = gson.fromJson<List<String>>(sH, t).map { LocalDate.parse(it) }.toSet()
            }
            val mH = prefs.getString("master_holidays", null)
            if (mH != null) {
                val t = object : TypeToken<List<String>>() {}.type
                masterHolidays = gson.fromJson<List<String>>(mH, t).map { DayOfWeek.valueOf(it) }.toSet()
            }

        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun getLessonsForDate(date: LocalDate): List<Lesson> {
        if (specificHolidays.contains(date)) return emptyList()
        if (masterHolidays.contains(date.dayOfWeek) && !specificSchedule.containsKey(date)) return emptyList()
        return specificSchedule[date] ?: masterSchedule[date.dayOfWeek] ?: emptyList()
    }

    private fun findCurrentAndNext(lessons: List<Lesson>, time: LocalTime): Pair<Lesson?, Lesson?> {
        val sorted = lessons.sortedBy { parseTime(it.startTime) }
        var active: Lesson? = null
        var next: Lesson? = null

        for (lesson in sorted) {
            val start = parseTime(lesson.startTime)
            val end = parseTime(lesson.endTime)

            if ((time.isAfter(start) || time == start) && time.isBefore(end)) {
                active = lesson
            }
            if (active == null && time.isBefore(start)) {
                if (next == null) next = lesson
            }
        }
        return active to next
    }

    private fun parseTime(t: String): LocalTime = try { LocalTime.parse(t) } catch(e: Exception) { LocalTime.MIN }

    override fun onDestroy() {
        serviceJob.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}