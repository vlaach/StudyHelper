package ru.vlaach.studyhelper

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.material3.ExperimentalMaterial3Api
import kotlinx.coroutines.delay

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

@Composable
fun LessonCard(
    lesson: Lesson,
    onHomeworkChecked: (Boolean) -> Unit,
    onHomeworkClick: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    isMasterMode: Boolean,
    isHighlighted: Boolean
) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)
            .clickable { if (!isMasterMode) onHomeworkClick() },
        colors = CardDefaults.cardColors(
            containerColor = if (isHighlighted)
                MaterialTheme.colorScheme.tertiaryContainer
            else
                MaterialTheme.colorScheme.surfaceVariant
        ),
        border = if (isHighlighted)
            androidx.compose.foundation.BorderStroke(2.dp, MaterialTheme.colorScheme.primary)
        else null,
        elevation = CardDefaults.cardElevation(defaultElevation = if (isHighlighted) 6.dp else 2.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.Top
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(60.dp)) {
                Text(text = lesson.startTime, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                if (lesson.endTime.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Box(modifier = Modifier.width(4.dp).height(30.dp).background(lesson.subjectColor, RoundedCornerShape(2.dp)))
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(text = lesson.endTime, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Spacer(modifier = Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(text = lesson.title, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary)
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 4.dp)) {
                    if (lesson.room.isNotEmpty()) {
                        Icon(Icons.Default.LocationOn, "Кабинет", Modifier.size(14.dp), tint = MaterialTheme.colorScheme.secondary)
                        Text(text = lesson.room, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(start = 2.dp, end = 8.dp))
                    }
                    if (lesson.teacher.isNotEmpty()) {
                        Icon(Icons.Default.Person, "Учитель", Modifier.size(14.dp), tint = MaterialTheme.colorScheme.secondary)
                        Text(text = lesson.teacher, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(start = 2.dp))
                    }
                }
                if (isHighlighted) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("ИДЁТ СЕЙЧАС", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                }
                if (lesson.homework.isNotBlank() && !isMasterMode) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(text = "ДЗ: ${lesson.homework}", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.tertiary)
                }
            }

            Column(horizontalAlignment = Alignment.End) {
                IconButton(onClick = onEdit, modifier = Modifier.size(24.dp)) {
                    Icon(Icons.Default.Edit, "Изменить", tint = MaterialTheme.colorScheme.primary)
                }
                Spacer(modifier = Modifier.height(8.dp))
                IconButton(onClick = onDelete, modifier = Modifier.size(24.dp)) {
                    Icon(Icons.Default.Delete, "Удалить", tint = MaterialTheme.colorScheme.error.copy(alpha = 0.6f))
                }
                if (!isMasterMode) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Checkbox(checked = lesson.isCompleted, onCheckedChange = onHomeworkChecked)
                }
            }
        }
    }
}

@Composable
fun CountdownWidget(viewModel: ScheduleViewModel) {
    var currentTime by remember { mutableStateOf(LocalTime.now()) }
    LaunchedEffect(Unit) {
        while (true) {
            currentTime = LocalTime.now()
            delay(1000)
        }
    }
    val (activeLesson, state) = viewModel.getCurrentOrNextLesson(currentTime)
    if (activeLesson != null) {
        val targetTime = if (state == "ENDS_IN") activeLesson.endTime else activeLesson.startTime
        val label = if (state == "ENDS_IN") "Заканчивается через" else "Начинается через"
        val remaining = viewModel.getTimeRemainingString(targetTime, currentTime)

        Card(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
        ) {
            Row(
                modifier = Modifier.padding(16.dp).fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(activeLesson.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(if(state == "ENDS_IN") "Текущий урок" else "Следующий урок", style = MaterialTheme.typography.labelMedium)
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(remaining, style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.primary)
                    Text(label, style = MaterialTheme.typography.labelSmall)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScheduleScreen(viewModel: ScheduleViewModel) {

    val isMasterMode by viewModel.isMasterScheduleMode
    val lessons = viewModel.lessonsForCurrentView
    val tabs = viewModel.weekTabs
    val selectedDate by viewModel.selectedDate
    val selectedMasterDay by viewModel.selectedMasterDay
    val isHoliday = viewModel.isHoliday(selectedDate)
    val isModified = viewModel.isCurrentDayModified

    var showResetDialog by remember { mutableStateOf(false) }

    var currentTime by remember { mutableStateOf(LocalTime.now()) }
    LaunchedEffect(Unit) {
        while (true) {
            currentTime = LocalTime.now()
            delay(1000)
        }
    }

    val (activeLesson, state) = viewModel.getCurrentOrNextLesson(currentTime)
    val activeLessonId = if (state == "ENDS_IN") activeLesson?.id else null

    if (viewModel.showAddLessonDialog.value) {
        val editingLesson = viewModel.lessonToEdit.value
        AddLessonDialog(
            initialLesson = editingLesson,
            onDismiss = { viewModel.showAddLessonDialog.value = false },
            onConfirm = { title, start, end, room, teacher ->
                viewModel.saveLesson(editingLesson?.id, title, start, end, room, teacher)
            }
        )
    }

    viewModel.showHomeworkDialog.value?.let { lesson ->
        HomeworkDialog(
            initialText = lesson.homework,
            onDismiss = { viewModel.showHomeworkDialog.value = null },
            onConfirm = { text -> viewModel.updateHomeworkText(lesson.id, text) }
        )
    }

    if (showResetDialog) {
        AlertDialog(
            onDismissRequest = { showResetDialog = false },
            title = { Text("Сбросить изменения?") },
            text = { Text("Вы действительно хотите вернуть расписание из шаблона? Все изменения за этот день (включая домашки) будут удалены.") },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.resetCurrentDayToMaster()
                        showResetDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) { Text("Сбросить") }
            },
            dismissButton = {
                TextButton(onClick = { showResetDialog = false }) { Text("Отмена") }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(if (isMasterMode) "Редактор шаблона" else "Расписание")
                        if (isMasterMode) {
                            Text("Для всех недель", style = MaterialTheme.typography.labelSmall)
                        } else if (isModified) {
                            Text("Изменено (отлич. от шаблона)", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.tertiary)
                        }
                    }
                },
                actions = {
                    FilledTonalIconToggleButton(
                        checked = isMasterMode,
                        onCheckedChange = { viewModel.toggleMasterMode() }
                    ) {
                        if (isMasterMode) Icon(Icons.Default.Check, "Готово") else Icon(Icons.Default.Edit, "Изм. шаблон")
                    }
                }
            )
        },
        floatingActionButton = {
            if (!isHoliday) {
                FloatingActionButton(onClick = { viewModel.onAddClicked() }) {
                    Icon(Icons.Default.Add, contentDescription = "Добавить")
                }
            }
        },
        bottomBar = {
            BottomAppBar(
                actions = {
                    if (!isMasterMode && isModified) {
                        TextButton(onClick = { showResetDialog = true }) {
                            Icon(Icons.Default.Refresh, "Reset", modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Сбросить")
                        }
                    }
                    Spacer(Modifier.weight(1f))
                    Button(
                        onClick = { viewModel.toggleHoliday() },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.secondaryContainer,
                            contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    ) {
                        Text(if (isHoliday) "Сделать учебным" else "Сделать выходным")
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(Modifier.padding(paddingValues)) {

            if (isMasterMode) {
                Box(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.primaryContainer).padding(16.dp)) {
                    Text("Настройка постоянного расписания.\nЭти уроки будут появляться каждую неделю.",
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        style = MaterialTheme.typography.bodyMedium)
                }
            } else {
                WeekNavigator(
                    currentDate = selectedDate,
                    onPreviousWeek = { viewModel.onPreviousWeek() },
                    onNextWeek = { viewModel.onNextWeek() },
                    isModified = isModified
                )
            }

            ScrollableTabRow(
                selectedTabIndex = if(isMasterMode) tabs.indexOf(selectedMasterDay) else tabs.indexOf(selectedDate),
                edgePadding = 0.dp
            ) {
                tabs.forEachIndexed { index, tab ->
                    val isSelected = if(isMasterMode) tab == selectedMasterDay else tab == selectedDate
                    val mainText = if (tab is LocalDate) tab.dayOfMonth.toString() else (tab as DayOfWeek).getDisplayName(TextStyle.SHORT, Locale("ru"))
                    val subText = if (tab is LocalDate) tab.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale("ru")) else ""

                    Tab(
                        selected = isSelected,
                        onClick = { viewModel.onTabSelected(index) },
                        text = {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(mainText, fontWeight = FontWeight.Bold)
                                if(subText.isNotEmpty()) Text(subText, style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    )
                }
            }

            CountdownWidget(viewModel = viewModel)

            when {
                isHoliday -> {
                    Box(Modifier.fillMaxSize().padding(32.dp), Alignment.Center) {
                        Text("Выходной день", style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.secondary)
                    }
                }
                lessons.isEmpty() -> {
                    Box(Modifier.fillMaxSize().padding(32.dp), Alignment.Center) {
                        Text(
                            if (isMasterMode) "Шаблон пуст." else "Уроков нет.",
                            style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                else -> {
                    LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 80.dp)) {
                        items(lessons, key = { it.id }) { lesson ->
                            LessonCard(
                                lesson = lesson,
                                onHomeworkChecked = { viewModel.toggleHomeworkCompletion(lesson.id) },
                                onHomeworkClick = { viewModel.showHomeworkDialog.value = lesson },
                                onEdit = { viewModel.onEditClicked(lesson) },
                                onDelete = { viewModel.deleteLesson(lesson.id) },
                                isMasterMode = isMasterMode,
                                isHighlighted = (lesson.id == activeLessonId)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun WeekNavigator(currentDate: LocalDate, onPreviousWeek: () -> Unit, onNextWeek: () -> Unit, isModified: Boolean) {
    val formatter = DateTimeFormatter.ofPattern("d MMMM", Locale("ru"))
    val startOfWeek = currentDate.with(DayOfWeek.MONDAY)
    val endOfWeek = startOfWeek.plusDays(6)
    Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
        IconButton(onClick = onPreviousWeek) { Icon(Icons.Default.ArrowBack, "Prev") }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(text = "${startOfWeek.format(formatter)} - ${endOfWeek.format(formatter)}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            if (isModified) { Spacer(Modifier.width(8.dp)); Icon(Icons.Default.Edit, "Modified", modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.tertiary) }
        }
        IconButton(onClick = onNextWeek) { Icon(Icons.Default.ArrowForward, "Next") }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddLessonDialog(
    initialLesson: Lesson? = null,
    onDismiss: () -> Unit,
    onConfirm: (String, String, String, String, String) -> Unit
) {
    var title by remember { mutableStateOf(initialLesson?.title ?: "") }
    var room by remember { mutableStateOf(initialLesson?.room ?: "") }
    var teacher by remember { mutableStateOf(initialLesson?.teacher ?: "") }
    var startTime by remember { mutableStateOf(initialLesson?.startTime ?: "08:30") }
    var endTime by remember { mutableStateOf(initialLesson?.endTime ?: "09:15") }

    var showTimePicker by remember { mutableStateOf(false) }
    var isPickingStartTime by remember { mutableStateOf(true) }

    if (showTimePicker) {
        TimePickerDialog(
            onDismissRequest = { showTimePicker = false },
            onConfirm = {
                val formattedTime = String.format(Locale.US, "%02d:%02d", it.hour, it.minute)
                if (isPickingStartTime) startTime = formattedTime else endTime = formattedTime
                showTimePicker = false
            }
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initialLesson == null) "Добавить урок" else "Редактировать урок") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = title, onValueChange = { title = it }, label = { Text("Предмет") })
                TimeInputRow("Начало", startTime) { isPickingStartTime = true; showTimePicker = true }
                TimeInputRow("Конец", endTime) { isPickingStartTime = false; showTimePicker = true }
                OutlinedTextField(value = room, onValueChange = { room = it }, label = { Text("Кабинет") })
                OutlinedTextField(value = teacher, onValueChange = { teacher = it }, label = { Text("Учитель") })
            }
        },
        confirmButton = {
            Button(onClick = { if (title.isNotBlank()) onConfirm(title, startTime, endTime, room, teacher) }) { Text("Сохранить") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } }
    )
}

@Composable
fun HomeworkDialog(initialText: String, onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var text by remember { mutableStateOf(initialText) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Домашнее задание") },
        text = { OutlinedTextField(value = text, onValueChange = { text = it }, modifier = Modifier.fillMaxWidth()) },
        confirmButton = { Button(onClick = { onConfirm(text) }) { Text("OK") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } }
    )
}

@Composable
private fun TimeInputRow(text: String, time: String, onClick: () -> Unit) {
    Row(modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
        Text(text, style = MaterialTheme.typography.bodyLarge)
        Text(text = time, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Medium)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimePickerDialog(onDismissRequest: () -> Unit, onConfirm: (java.time.LocalTime) -> Unit) {
    val timeState = rememberTimePickerState()
    AlertDialog(
        modifier = Modifier.fillMaxWidth(),
        onDismissRequest = onDismissRequest,
        title = { Text("Выберите время") },
        text = { Box(Modifier.fillMaxWidth(), Alignment.Center) { TimePicker(state = timeState) } },
        confirmButton = { Button(onClick = { onConfirm(java.time.LocalTime.of(timeState.hour, timeState.minute)) }) { Text("OK") } },
        dismissButton = { TextButton(onClick = onDismissRequest) { Text("Отмена") } }
    )
}