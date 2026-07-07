package com.kreadex.daymark

import android.content.Context
import android.content.res.Configuration
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kreadex.daymark.ui.theme.DaymarkTheme
import java.time.LocalDate
import java.time.YearMonth

import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.lifecycle.ViewModelProvider
import com.kreadex.daymark.data.db.AppDatabase
import com.kreadex.daymark.data.db.CalendarDao
import com.kreadex.daymark.data.db.CalendarEntity
import com.kreadex.daymark.data.db.DayMarkViewModel
import com.kreadex.daymark.data.db.DayMarkViewModelFactory
import com.kreadex.daymark.data.db.SettingsManager
import com.kreadex.daymark.utils.buildMonthGrid
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import java.time.Year

class CalendarActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val calendarId = intent.getLongExtra(EXTRA_CALENDAR_ID, -1L)
        require(calendarId != -1L) { "Calendar ID is missing" }

        val dao = AppDatabase.get(this).calendarDao()

        val viewModel: DayMarkViewModel = ViewModelProvider(
            this,
            DayMarkViewModelFactory(dao, calendarId)
        )[DayMarkViewModel::class.java]

        setContent {
            DaymarkTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    MyCalendar(
                        modifier = Modifier.padding(innerPadding),
                        viewModel = viewModel
                    )
                }
            }
        }
    }

    companion object {
        const val EXTRA_CALENDAR_ID = "calendar_id"
    }
}



@Composable
fun MyCalendar(
    modifier: Modifier = Modifier,
    viewModel: DayMarkViewModel
) {
    var zoomLevel by remember { mutableFloatStateOf(1f) }
    val animatedZoom by animateFloatAsState(
        targetValue = zoomLevel,
        animationSpec = spring(stiffness = Spring.StiffnessLow, dampingRatio = Spring.DampingRatioLowBouncy),
        label = "ZoomAnimation"
    )

    val columnsCount = when {
        animatedZoom > 0.7f -> 1
        animatedZoom > 0.45f -> 2
        else -> 3
    }

    var currentYear by remember { mutableStateOf(Year.now().value) }
    var showDayDialog by remember { mutableStateOf<LocalDate?>(null) }
    val calendar by viewModel.calendar.collectAsState()
    val customFont = FontFamily(Font(R.font.pixy))

    val marksMap by remember(calendar, currentYear) {
        derivedStateOf {
            calendar?.months
                ?.filter { it.date.startsWith(currentYear.toString()) }
                ?.associateBy { it.date } ?: emptyMap()
        }
    }

    val calendarColors = remember(calendar?.settings) {
        val settings = SettingsManager.fromJson(calendar?.settings)
        settings.dayColors.map { Color(it.removePrefix("0x").toLong(16)) }
    }

    CompositionLocalProvider(LocalTextStyle provides TextStyle(fontFamily = customFont)) {
        Column(
            modifier = modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.surface)
                .pointerInput(Unit) {
                    awaitEachGesture {
                        awaitFirstDown(requireUnconsumed = false)

                        do {
                            val event = awaitPointerEvent()
                            val canceled = event.changes.any { it.isConsumed }

                            if (!canceled && event.changes.size > 1) {
                                event.changes.forEach { it.consume() }

                                val zoomFactor = event.calculateZoom()
                                if (zoomFactor != 1f) {
                                    zoomLevel = (zoomLevel * zoomFactor).coerceIn(0.35f, 1.1f)
                                }
                            }
                        } while (event.changes.any { it.pressed })
                    }
                }
        ) {
            YearHeader(currentYear, animatedZoom, onYearChange = { currentYear = it })

            val listState = rememberLazyGridState(initialFirstVisibleItemIndex = LocalDate.now().monthValue - 1)

            LazyVerticalGrid(
                columns = GridCells.Fixed(columnsCount),
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(10.dp),
                verticalArrangement = Arrangement.spacedBy((16 * animatedZoom).dp),
                horizontalArrangement = Arrangement.spacedBy((16 * animatedZoom).dp)
            ) {
                items(12, key = { "$currentYear-$it" }) { monthIdx ->
                    Box(
                        modifier = Modifier.animateItem(
                            placementSpec = spring(
                                stiffness = Spring.StiffnessMediumLow,
                                dampingRatio = Spring.DampingRatioLowBouncy
                            )
                        )
                    ) {
                        MonthBlock(
                            month = YearMonth.of(currentYear, monthIdx + 1),
                            marksMap = marksMap,
                            onDayClick = { showDayDialog = it },
                            zoom = animatedZoom,
                            calendarColors = calendarColors
                        )
                    }
                }
            }
        }

        // Sheet...
        showDayDialog?.let { date ->
            val currentMark = marksMap[date.toString()]

            DayMarkSheet(
                date = date,
                calendarColors = calendarColors,
                initialColorIndex = currentMark?.colorIndex ?: 0,
                initialNote = currentMark?.note ?: "",
                initialStrokeColor = currentMark?.event,
                onDismiss = { showDayDialog = null },
                onSave = { colorIdx, note, strokeHex ->
                    viewModel.setDay(date, colorIdx, note, strokeHex)
                    showDayDialog = null
                }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun YearHeader(year: Int, zoom: Float, onYearChange: (Int) -> Unit) {
    val sheetState = rememberModalBottomSheetState()
    var isSheetOpen by remember { mutableStateOf(false) }

    var pendingYear by remember { mutableStateOf(year) }

    LaunchedEffect(sheetState.isVisible) {
        if (!sheetState.isVisible && isSheetOpen) {
            onYearChange(pendingYear)
            isSheetOpen = false
        }
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .graphicsLayer {
                alpha = ((zoom - 0.4f) * 2f).coerceIn(0f, 1f)
                translationY = (1f - zoom) * -50f
            },
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = {
            onYearChange(year - 1)
            SoundManager.play(R.raw.pop)}) {
            Text("<", fontSize = 32.sp)
        }

        Text(
            text = year.toString(),
            fontSize = 32.sp,
            modifier = Modifier.clickable {
                pendingYear = year // Сбрасываем временный год на текущий перед открытием
                isSheetOpen = true
                SoundManager.play(R.raw.pop)
            }
        )

        IconButton(onClick = {
            onYearChange(year + 1)
            SoundManager.play(R.raw.pop) }) {
            Text(">", fontSize = 32.sp)
        }
    }

    if (isSheetOpen) {
        ModalBottomSheet(
            onDismissRequest = {
                onYearChange(pendingYear)
                isSheetOpen = false
            },
            sheetState = sheetState,
            scrimColor = Color.Black.copy(alpha = 0.6f),
            shape = RectangleShape
        ) {
            YearPickerWheel(
                initialYear = year,
                onYearScrolled = { scrollingYear ->
                    pendingYear = scrollingYear
                    SoundManager.play(R.raw.scr)
                }
            )
        }
    }
}

@Composable
fun YearPickerWheel(
    initialYear: Int,
    onYearScrolled: (Int) -> Unit
) {
    val itemHeight = 55.dp
    val years = remember { (initialYear - 50..initialYear + 50).toList() }

    val listState = rememberLazyListState(
        initialFirstVisibleItemIndex = years.indexOf(initialYear).coerceAtLeast(0)
    )
    val snapFlingBehavior = rememberSnapFlingBehavior(lazyListState = listState)

    val currentYear by remember {
        derivedStateOf {
            val layoutInfo = listState.layoutInfo
            val visibleItemsInfo = layoutInfo.visibleItemsInfo
            if (visibleItemsInfo.isEmpty()) initialYear
            else {
                val viewportCenter = (layoutInfo.viewportStartOffset + layoutInfo.viewportEndOffset) / 2
                val closestItem = visibleItemsInfo.minByOrNull {
                    kotlin.math.abs((it.offset + it.size / 2) - viewportCenter)
                }
                closestItem?.index?.let { years.getOrNull(it) } ?: initialYear
            }
        }
    }

    LaunchedEffect(currentYear) {
        onYearScrolled(currentYear)
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(250.dp), // Общая высота (примерно 4-5 строк)
        contentAlignment = Alignment.Center
    ) {
        // Рамка фокуса (теперь она точно совпадает с itemHeight)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(itemHeight)
                .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f))
        )

        LazyColumn(
            state = listState,
            flingBehavior = snapFlingBehavior,
            contentPadding = PaddingValues(vertical = 100.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxWidth()
        ) {
            items(years) { year ->
                Box(
                    modifier = Modifier
                        .height(itemHeight),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = year.toString(),
                        fontSize = if (year == currentYear) 30.sp else 22.sp,
                        color = if (year == currentYear)
                            MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }
}

@Composable
fun MonthBlock(
    month: YearMonth,
    marksMap: Map<String, DayMark>,
    calendarColors: List<Color>,
    onDayClick: (LocalDate) -> Unit,
    zoom: Float
) {
    val months = listOf(
        s(R.string.january), s(R.string.february), s(R.string.march),
        s(R.string.april), s(R.string.may), s(R.string.june),
        s(R.string.july), s(R.string.august), s(R.string.september),
        s(R.string.october), s(R.string.november), s(R.string.december)
    )

    val monthName = months[month.monthValue - 1]

    Column {
        val alpha = ((zoom - 0.45f) * 3f).coerceIn(0f, 1f)
        if (zoom > 0.45f) {
            Text(
                text = monthName,
                fontSize = (22 * zoom).sp,
                modifier = Modifier
                    .padding(bottom = (4 * zoom).dp)
                    .graphicsLayer { this.alpha = alpha }
            )
        }
        CalendarGrid(month, marksMap, calendarColors, onDayClick, zoom)
    }
}

@Composable
fun CalendarGrid(
    month: YearMonth,
    marksMap: Map<String, DayMark>,
    calendarColors: List<Color>,
    onDayClick: (LocalDate) -> Unit,
    zoom: Float
) {
    val days = remember(month) { buildMonthGrid(month) }
    val textMeasurer = rememberTextMeasurer()
    val customFont = FontFamily(Font(R.font.pixy))
    val textColor = MaterialTheme.colorScheme.onSurface

    val labelAlpha by animateFloatAsState(if (zoom > 0.6f) 1f else 0f, label = "labelAlpha")
    val dayTextAlpha by animateFloatAsState(if (zoom > 0.8f) 1f else 0f, label = "dayTextAlpha")

    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        val cellSize = maxWidth / 7
        val isCompact = zoom < 0.6f
        val targetHeaderHeight = if (isCompact) 0.dp else cellSize * 0.7f
        val animatedHeaderHeight by animateDpAsState(targetHeaderHeight, label = "h")

        val rows = (days.size + 6) / 7
        val totalHeight = (cellSize * rows) + animatedHeaderHeight

        val daysOfWeek = listOf(
            s(R.string.mon), s(R.string.tue), s(R.string.wed),
            s(R.string.thu), s(R.string.fri), s(R.string.sat), s(R.string.sun)
        )

        val animatedColors = days.map { day ->
            if (day == null) return@map Color.Transparent
            val date = LocalDate.of(month.year, month.month, day)
            val colorIdx = marksMap[date.toString()]?.colorIndex ?: 0
            val targetColor = calendarColors.getOrElse(colorIdx) { Color.Gray }

            animateColorAsState(
                targetValue = targetColor,
                animationSpec = tween(400),
                label = "color_$day"
            ).value
        }

        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(totalHeight)
                .pointerInput(month, days) {
                    detectTapGestures { offset ->
                        val cellPx = size.width / 7
                        val hPx = animatedHeaderHeight.toPx()
                        val col = (offset.x / cellPx).toInt()
                        val row = ((offset.y - hPx) / cellPx).toInt()
                        if (row >= 0 && col in 0..6) {
                            val index = row * 7 + col
                            days.getOrNull(index)?.let { d ->
                                onDayClick(LocalDate.of(month.year, month.month, d))
                                SoundManager.play(R.raw.pop)
                            }
                        }
                    }
                }
        ) {
            val cellPx = size.width / 7
            val hPx = animatedHeaderHeight.toPx()

            if (!isCompact) {
                daysOfWeek.forEachIndexed { i, title ->
                    val measuredText = textMeasurer.measure(
                        text = title,
                        style = TextStyle(
                            fontSize = 16.sp,
                            color = textColor.copy(alpha = 0.7f),
                            fontFamily = customFont
                        )
                    )
                    if (zoom > 0.7f) {
                        drawText(
                            textLayoutResult = measuredText,
                            topLeft = Offset(
                                i * cellPx + (cellPx - measuredText.size.width) / 2,
                                (cellPx - measuredText.size.height) - 27.dp.toPx() // Смещение как в оригинале
                            )
                        )
                    }

                }
            }

            days.forEachIndexed { index, day ->
                if (day == null) return@forEachIndexed
                val date = LocalDate.of(month.year, month.month, day)
                val rect = Rect(Offset((index % 7) * cellPx, (index / 7) * cellPx + hPx), Size(cellPx, cellPx))

                // Используем уже вычисленный анимированный цвет из списка
                drawRect(
                    color = animatedColors[index],
                    topLeft = rect.topLeft,
                    size = rect.size
                )

                // Обводка (Сегодняшний день или обычная)
                // Внутри Canvas в CalendarGrid
                val mark = marksMap[date.toString()]
                val strokeHex = mark?.event // Добавьте это поле в вашу модель DayMark

                val customStrokeColor = try {
                    if (strokeHex != null) Color(strokeHex.removePrefix("0x").toLong(16)) else null
                } catch (e: Exception) { null }

// Обводка
                val strokeWidth = (0.8f + (zoom * 1.5f)).dp.toPx()
                drawRect(
                    color = when {
                        customStrokeColor != null -> customStrokeColor
                        date == LocalDate.now() -> Color.Cyan
                        else -> Color.Black.copy(0.1f)
                    },
                    topLeft = Offset(rect.left + strokeWidth / 2, rect.top + strokeWidth / 2),
                    size = Size(rect.width - strokeWidth, rect.height - strokeWidth),
                    style = Stroke(width = strokeWidth)
                )

                if (dayTextAlpha > 0f) {
                    val textLayout = textMeasurer.measure(
                        day.toString(),
                        TextStyle(fontSize = (14 * zoom).sp, fontFamily = customFont, color = textColor.copy(alpha = dayTextAlpha))
                    )
                    drawText(textLayout, topLeft = Offset(rect.right - textLayout.size.width - 4.dp.toPx(), rect.top + 4.dp.toPx()))
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DayMarkSheet(
    date: LocalDate,
    calendarColors: List<Color>,
    initialColorIndex: Int,
    initialNote: String,
    initialStrokeColor: String?,
    onDismiss: () -> Unit,
    onSave: (Int, String, String?) -> Unit
) {
    var selectedColorIndex by remember { mutableStateOf(initialColorIndex) }
    var note by remember { mutableStateOf(initialNote) }
    var event by remember { mutableStateOf(initialStrokeColor) }
    var showStrokePicker by remember { mutableStateOf(false) }

    val customFont = FontFamily(Font(R.font.pixy))
    val sheetState = rememberModalBottomSheetState()

    ModalBottomSheet(
        onDismissRequest = {
            onSave(selectedColorIndex, note, event)
        },
        sheetState = sheetState,
        dragHandle = { BottomSheetDefaults.DragHandle() },
        containerColor = MaterialTheme.colorScheme.surface,
        shape = RectangleShape,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 40.dp)
        ) {
            Text(
                text = date.toString(),
                fontSize = 24.sp,
                fontFamily = customFont,
                modifier = Modifier.padding(bottom = 12.dp)
            )

            // Выбор цвета фона
            FlowRow(
                modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                calendarColors.forEachIndexed { index, color ->
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .background(color, RectangleShape)
                            .border(
                                width = if (selectedColorIndex == index) 3.dp else 1.dp,
                                color = if (selectedColorIndex == index) MaterialTheme.colorScheme.primary else Color.Black.copy(0.1f)
                            )
                            .clickable { selectedColorIndex = index
                                SoundManager.play(R.raw.ko)}
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .background(
                            if (event != null) Color(event!!.removePrefix("0x").toLong(16))
                            else Color.Transparent,
                            RectangleShape
                        )
                        .border(1.dp, MaterialTheme.colorScheme.outline, RectangleShape)
                        .clickable { showStrokePicker = true
                            SoundManager.play(R.raw.ko)},
                    contentAlignment = Alignment.Center
                ) {
                    if (event == null) {
                        Canvas(Modifier.fillMaxSize().padding(8.dp)) {
                            drawLine(
                                color = Color.Gray.copy(alpha = 0.5f),
                                start = Offset(0f, size.height),
                                end = Offset(size.width, 0f),
                                strokeWidth = 1.dp.toPx()
                            )
                        }
                        Text("NONE", fontSize = 10.sp, fontFamily = customFont, color = Color.Gray)
                    } else {
                        Icon(
                            painter = BitmapPainter(
                                ImageBitmap.imageResource(id = R.drawable.pencil),
                                filterQuality = FilterQuality.None
                            ),
                            contentDescription = null,
                            modifier = Modifier.size(24.dp), // Уменьшили размер для аккуратности
                            tint = Color.White.copy(alpha = 0.9f)
                        )
                    }
                }

                // 2. Поле заметки (справа, занимает все оставшееся место)
                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    label = { Text(s(R.string.note), fontFamily = customFont) },
                    modifier = Modifier.weight(1f),
                    textStyle = TextStyle(fontFamily = customFont),
                    shape = RectangleShape,
                    singleLine = true
                )
            }

            Spacer(Modifier.height(24.dp))

            // Кнопка Сохранить
            Button(
                onClick = { onSave(selectedColorIndex, note, event)
                    SoundManager.play(R.raw.pop)},
                modifier = Modifier.fillMaxWidth().height(50.dp),
                shape = RectangleShape,
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
            ) {
                Text(s(R.string.save), fontFamily = customFont, fontSize = 18.sp)
            }
        }
    }

    if (showStrokePicker) {
        ColorEditDialog(
            initialHex = event ?: "0xFFFF0000",
            deleteButtonText = s(R.string.default_b),
            onDismiss = { showStrokePicker = false },
            onDelete = {
                event = null
                showStrokePicker = false
            },
            onConfirm = { newHex ->
                event = newHex
                showStrokePicker = false
            }
        )
    }
}

@Preview(showBackground = true, showSystemUi = true, uiMode = Configuration.UI_MODE_NIGHT_NO)
@Composable
fun CalendarPreview() {
    DaymarkTheme {
        MyCalendar(
            viewModel = fakeDayMarkViewModel()
        )
    }
}

fun fakeDayMarkViewModel(): DayMarkViewModel {
    val fakeDao = CalendarDaoFake()
    return DayMarkViewModel(
        calendarDao = fakeDao,
        calendarId = 0L
    )
}



class CalendarDaoFake : CalendarDao {
    override fun getAll(): Flow<List<CalendarEntity>> = flowOf(
        listOf(
            CalendarEntity(
                id = 0L,
                name = "Preview Calendar",
                months = buildFakeMonth()
            )
        )
    )

    override suspend fun getAllDirectly(): List<CalendarEntity> {
        return emptyList()
    }

    override suspend fun insert(calendar: CalendarEntity) {}
    override suspend fun delete(calendar: CalendarEntity) {}
    override suspend fun clearAll() {}
    override suspend fun getAllList(): List<CalendarEntity> {
        return emptyList()
    }
    override suspend fun insertAll(calendars: List<CalendarEntity>) {}
    override suspend fun update(calendar: CalendarEntity) {}
    override suspend fun updateOrder(id: Long, newOrder: Int) {}
    override suspend fun getMaxOrder(): Int? {
        return null
    }


    override suspend fun getById(id: Long): CalendarEntity? {
        return CalendarEntity(
            id = id,
            name = "Preview Calendar",
            months = buildFakeMonth()
        )
    }

    companion object {
        fun buildFakeMonth(): List<DayMark> {
            val month = YearMonth.now()
            return (1..month.lengthOfMonth()).mapNotNull { day ->
                if (day % 3 == 0) DayMark(
                    date = "${month.year}-${month.monthValue.toString().padStart(2, '0')}-${day.toString().padStart(2, '0')}",
                    colorIndex = day % 7,
                    note = "Note $day",
                    event = null
                ) else null
            }
        }
    }
}


