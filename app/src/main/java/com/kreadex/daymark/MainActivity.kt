package com.kreadex.daymark

import SoundManager
import android.content.Intent
import android.content.res.Configuration
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.annotation.StringRes
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import com.kreadex.daymark.data.db.AppDatabase
import com.kreadex.daymark.data.db.CalendarEntity
import com.kreadex.daymark.data.db.CalendarSettings
import com.kreadex.daymark.data.db.CalendarViewModel
import com.kreadex.daymark.data.db.CalendarViewModelFactory
import com.kreadex.daymark.data.db.SettingsManager

import com.kreadex.daymark.ui.theme.DaymarkTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

import androidx.compose.ui.text.TextStyle
import com.kreadex.daymark.data.AppPreferences

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        SoundManager.init(this)

        val prefs = AppPreferences(this)

        val db = AppDatabase.get(this)
        val factory = CalendarViewModelFactory(db.calendarDao())
        val viewModel = ViewModelProvider(this, factory)[CalendarViewModel::class.java]

        lifecycleScope.launch(Dispatchers.IO) {
            val dao = db.calendarDao()
            val list = dao.getAllList()
            if (list.isNotEmpty() && list.all { it.orderIndex == 0 }) {
                list.forEachIndexed { index, entity ->
                    dao.updateOrder(entity.id, index)
                }
                Log.d("DB_FIX", "Orders initialized")
            }
        }

        enableEdgeToEdge()

        setContent {
            var updateTick by remember { mutableStateOf(0) }

            val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
            androidx.compose.runtime.DisposableEffect(lifecycleOwner) {
                val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
                    if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                        updateTick++
                    }
                }
                lifecycleOwner.lifecycle.addObserver(observer)
                onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
            }

            val currentThemeMode = remember(updateTick) { prefs.themeMode }

            val isDark = when (currentThemeMode) {
                AppPreferences.THEME_LIGHT -> false
                AppPreferences.THEME_DARK -> true
                else -> isSystemInDarkTheme()
            }

            DaymarkTheme(darkTheme = isDark, prefs = prefs) {
                Scaffold(
                    modifier = Modifier.fillMaxSize()
                ) { innerPadding ->
                    Greeting(
                        viewModel = viewModel,
                        modifier = Modifier.padding(innerPadding)
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun Greeting(viewModel: CalendarViewModel? = null, modifier: Modifier = Modifier) {
    val calendars by viewModel?.calendars?.collectAsState(initial = emptyList())
        ?: remember { mutableStateOf(emptyList()) }

    val context = LocalContext.current
    val customFont = FontFamily(Font(R.font.pixy))

    var showAddDialog by remember { mutableStateOf(false) }
    var editingCalendar by remember { mutableStateOf<CalendarEntity?>(null) }
    val listState = rememberLazyListState()
    var flippedCalendarId by remember { mutableStateOf<Long?>(null) }
    val scope = rememberCoroutineScope()

    Column(modifier = modifier.padding(horizontal = 10.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                modifier = Modifier.padding(horizontal = 10.dp),
                text = s(R.string.hello),
                fontSize = 40.sp,
                fontFamily = customFont
            )

            IconButton(
                onClick = { context.startActivity(Intent(context, SettingsActivity::class.java)) },
                modifier = Modifier.size(50.dp)
            ) {
                Icon(
                    painter = BitmapPainter(
                        ImageBitmap.imageResource(id = R.drawable.settings),
                        filterQuality = FilterQuality.None
                    ),
                    contentDescription = null,
                    modifier = Modifier.size(40.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
            items(calendars, key = { it.id }) { calendar ->
                Box(modifier = Modifier.animateItem()) {
                    CalendarCard(
                        calendar = calendar,
                        isFlipped = flippedCalendarId == calendar.id,
                        onFlip = {
                            flippedCalendarId = if (flippedCalendarId == calendar.id) null else calendar.id
                        },
                        onEdit = { editingCalendar = it },
                        onDelete = { viewModel?.deleteCalendar(it) },
                        onMoveUp = {
                            viewModel?.moveStep(calendar, -1)
                            scope.launch {
                                val index = calendars.indexOf(calendar)
                                if (index > 0) listState.animateScrollToItem(index - 1)
                            }
                            SoundManager.play(R.raw.scr)
                        },
                        onMoveDown = { viewModel?.moveStep(calendar, 1)
                            SoundManager.play(R.raw.scr)}
                    )
                }
            }
            item {
                AddCalendar { showAddDialog = true }
            }
        }
    }

    // Диалог редактирования
    if (editingCalendar != null) {
        CalendarDialog(
            calendar = editingCalendar,
            onDismiss = { editingCalendar = null
                SoundManager.play(R.raw.pop)},
            onConfirm = { name, description, settingsJson ->
                viewModel?.updateCalendar(
                    editingCalendar!!.copy(
                        name = name,
                        description = description,
                        settings = settingsJson,
                        dateEdited = System.currentTimeMillis()
                    )
                )
                SoundManager.play(R.raw.pop)
                editingCalendar = null
            }
        )
    }

    // Диалог добавления
    if (showAddDialog) {
        CalendarDialog(
            calendar = null,
            onDismiss = { showAddDialog = false },
            onConfirm = { name, description, settingsJson ->
                viewModel?.addCalendar(name, description, settingsJson)
                showAddDialog = false
            }
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun CalendarCard(
    calendar: CalendarEntity,
    isFlipped: Boolean,
    onFlip: () -> Unit,
    onEdit: (CalendarEntity) -> Unit,
    onDelete: (CalendarEntity) -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit
) {
    val context = LocalContext.current
    var showDeleteDialog by remember { mutableStateOf(false) }
    val customFont = FontFamily(Font(R.font.pixy))

    val rotation by animateFloatAsState(
        targetValue = if (isFlipped) 180f else 0f,
        animationSpec = tween(500, easing = FastOutSlowInEasing),
        label = "cardFlip"
    )

    Box {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp)
                .graphicsLayer {
                    rotationY = rotation
                    cameraDistance = 12f * density
                }
                .combinedClickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = {
                        if (isFlipped) onFlip()
                        else {
                            val intent = Intent(context, CalendarActivity::class.java).apply {
                                putExtra(CalendarActivity.EXTRA_CALENDAR_ID, calendar.id)
                            }
                            context.startActivity(intent)
                        }
                    },
                    onLongClick = { onFlip() }
                ),
            shape = RectangleShape,
            border = BorderStroke(8.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceDim)
        ) {
            if (rotation <= 90f) {
                FrontSide(calendar, customFont)
            } else {
                Box(Modifier.graphicsLayer { rotationY = 180f }.fillMaxSize()) {
                    BackSide(
                        calendar = calendar,
                        customFont = customFont,
                        onEdit = { onEdit(calendar)
                            SoundManager.play(R.raw.pop)},
                        onDelete = { showDeleteDialog = true
                            SoundManager.play(R.raw.pop)},
                        onMoveUp = onMoveUp,
                        onMoveDown = onMoveDown
                    )
                }
            }
        }

        if (showDeleteDialog) {
            AlertDialog(
                onDismissRequest = { showDeleteDialog = false },
                shape = RectangleShape,
                title = { Text(s(R.string.delete_calendar_q), fontFamily = customFont) },
                text = { Text(s(R.string.action_cannot), fontFamily = customFont) },
                confirmButton = {
                    TextButton(onClick = {
                        showDeleteDialog = false
                        onDelete(calendar)
                    }) {
                        Text(s(R.string.delete), color = Color.Red, fontFamily = customFont)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showDeleteDialog = false }) {
                        Text(s(R.string.cancel), fontFamily = customFont)
                    }
                }
            )
        }
    }
}

@Composable
fun FrontSide(calendar: CalendarEntity, customFont: FontFamily) {

    val settings = remember(calendar.settings) { SettingsManager.fromJson(calendar.settings) }

    Column {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(200.dp),
            contentAlignment = Alignment.Center
        ) {
            Image(
                painter = BitmapPainter(
                    image = ImageBitmap.imageResource(id = getCalendarIcon(settings.iconIndex)),
                    filterQuality = FilterQuality.None
                ),
                contentDescription = null,
                modifier = Modifier.size(120.dp),
                contentScale = ContentScale.Fit,
                colorFilter = ColorFilter.tint(MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f))
            )
        }

        Column(modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 12.dp)) {
            Text(calendar.name, fontSize = 36.sp, fontFamily = customFont)
            calendar.description?.let {
                Text(text = it, fontSize = 26.sp, modifier = Modifier.alpha(0.6f), fontFamily = customFont)
            }
        }
    }
}

@Composable
fun BackSide(
    calendar: CalendarEntity,
    customFont: FontFamily,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth().height(277.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(calendar.name, fontSize = 32.sp, fontFamily = customFont, modifier = Modifier.padding(bottom = 20.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            var totalDelta by remember(calendar.id) { mutableStateOf(0f) }
            val threshold = 100f

            // Draggable handle
            Box(
                modifier = Modifier
                    .size(width = 64.dp, height = 40.dp)
                    .background(MaterialTheme.colorScheme.secondary)
                    .draggable(
                        state = rememberDraggableState { delta ->
                            totalDelta += delta
                            if (totalDelta > threshold) {
                                onMoveDown()
                                totalDelta = 0f
                            } else if (totalDelta < -threshold) {
                                onMoveUp()
                                totalDelta = 0f
                            }
                        },
                        orientation = Orientation.Vertical,
                        onDragStarted = { totalDelta = 0f },
                        onDragStopped = { totalDelta = 0f }
                    ).clickable(
                        onClick = { SoundManager.play(R.raw.pop) }
                    ),
                contentAlignment = Alignment.Center
            ) {
                Image(
                    painter = BitmapPainter(
                        ImageBitmap.imageResource(id = R.drawable.stick),
                        filterQuality = FilterQuality.None
                    ),
                    contentDescription = null,
                    modifier = Modifier.size(40.dp),
                    colorFilter = ColorFilter.tint(MaterialTheme.colorScheme.onSecondary.copy(alpha = 0.7f))
                )
            }

            // --- КНОПКА EDIT ---
            Button(
                onClick = onEdit,
                modifier = Modifier.height(40.dp),
                shape = RoundedCornerShape(0.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                contentPadding = PaddingValues(0.dp)
            ) {
                Image(
                    painter = BitmapPainter(
                        ImageBitmap.imageResource(id = R.drawable.pencil),
                        filterQuality = FilterQuality.None
                    ),
                    contentDescription = null,
                    modifier = Modifier.size(40.dp),
                    colorFilter = ColorFilter.tint(MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.7f))
                )
            }

            // --- КНОПКА DELETE ---
            Button(
                onClick = onDelete,
                modifier = Modifier.height(40.dp),
                shape = RoundedCornerShape(0.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                contentPadding = PaddingValues(0.dp)
            ) {
                Image(
                    painter = BitmapPainter(
                        ImageBitmap.imageResource(id = R.drawable.trash),
                        filterQuality = FilterQuality.None
                    ),
                    contentDescription = null,
                    modifier = Modifier.size(40.dp),
                    colorFilter = ColorFilter.tint(MaterialTheme.colorScheme.onError.copy(alpha = 0.7f))
                )
            }
        }
    }
}

@Composable
fun getCalendarIcon(index: Int): Int {
    val context = LocalContext.current
    val resourceName = "mark_$index"
    val resId = context.resources.getIdentifier(resourceName, "drawable", context.packageName)
    return if (resId != 0) resId else R.drawable.crossword_24px
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CalendarDialog(
    calendar: CalendarEntity? = null,
    onDismiss: () -> Unit,
    onConfirm: (String, String?, String) -> Unit
) {
    var name by remember { mutableStateOf(calendar?.name ?: "") }
    var description by remember { mutableStateOf(calendar?.description ?: "") }

    val customFont = FontFamily(Font(R.font.pixy))

    val initialSettings = remember { SettingsManager.fromJson(calendar?.settings) }
    var currentColors by remember { mutableStateOf(initialSettings.dayColors) }
    var selectedIconIndex by remember { mutableStateOf(initialSettings.iconIndex) }

    var showPickerIndex by remember { mutableStateOf<Int?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RectangleShape,
        containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),
        title = {
            Text(
                text = if (calendar != null) s(R.string.edit_calendar) else s(R.string.add_calendar),
                fontFamily = customFont
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                // 1. Поле имени
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(s(R.string.calendar_name), fontFamily = customFont) },
                    shape = RectangleShape,
                    textStyle = TextStyle(fontFamily = customFont),
                    modifier = Modifier.fillMaxWidth()
                )

                // 2. Поле описания
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text(s(R.string.calendar_description_optional), fontFamily = customFont) },
                    shape = RectangleShape,
                    textStyle = TextStyle(fontFamily = customFont),
                    modifier = Modifier.fillMaxWidth()
                )

                Text(s(R.string.icon) + ":", fontSize = 14.sp, fontFamily = customFont, fontWeight = FontWeight.Bold)
                LazyRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(horizontal = 2.dp, vertical = 4.dp)
                ) {
                    items(30) { i ->
                        val index = i + 1
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .background(
                                    if (selectedIconIndex == index) MaterialTheme.colorScheme.primaryContainer
                                    else Color.Transparent,
                                    RectangleShape
                                )
                                .border(
                                    width = if (selectedIconIndex == index) 3.dp else 1.dp,
                                    color = if (selectedIconIndex == index) MaterialTheme.colorScheme.primary else Color.LightGray
                                )
                                .clickable { selectedIconIndex = index
                                    SoundManager.play(R.raw.ko)},
                            contentAlignment = Alignment.Center
                        ) {
                            Image(
                                painter = BitmapPainter(
                                    image = ImageBitmap.imageResource(id = getCalendarIcon(index)),
                                    filterQuality = FilterQuality.None
                                ),
                                contentDescription = null,
                                modifier = Modifier.size(32.dp),
                                contentScale = ContentScale.Fit,
                                colorFilter = ColorFilter.tint(MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f))
                            )
                        }
                    }
                }

                // 4. Выбор цветов дня
                Text(s(R.string.colors) + ":", fontSize = 14.sp, fontFamily = customFont, fontWeight = FontWeight.Bold)
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    currentColors.forEachIndexed { index, colorHex ->
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .background(Color(colorHex.removePrefix("0x").toLong(16)), RectangleShape)
                                .border(1.dp, Color.Black.copy(alpha = 0.2f))
                                .clickable { showPickerIndex = index
                                    SoundManager.play(R.raw.ko)}
                        )
                    }
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .border(1.dp, Color.Gray, RectangleShape)
                            .clickable { currentColors = currentColors + "0xFF888888"
                                SoundManager.play(R.raw.ko)},
                        contentAlignment = Alignment.Center
                    ) {
                        Text("+", fontSize = 20.sp, fontFamily = customFont)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                if (name.isNotBlank()) {
                    val jsonSettings = SettingsManager.toJson(
                        CalendarSettings(currentColors, selectedIconIndex)
                    )
                    onConfirm(name, description.ifBlank { null }, jsonSettings)
                }
            }) {
                Text(s(R.string.save), fontFamily = customFont)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(s(R.string.cancel), fontFamily = customFont)
            }
        }
    )

    // Вызов диалога палитры при клике на квадрат цвета
    showPickerIndex?.let { index ->
        ColorEditDialog(
            initialHex = currentColors[index],
            deleteButtonText = s(R.string.delete),
            onDismiss = { showPickerIndex = null},
            onDelete = {
                if (currentColors.size > 1) {
                    currentColors = currentColors.filterIndexed { i, _ -> i != index }
                }
                showPickerIndex = null
                SoundManager.play(R.raw.pop)
            },
            onConfirm = { newHex ->
                val newList = currentColors.toMutableList()
                newList[index] = newHex
                currentColors = newList
                showPickerIndex = null
                SoundManager.play(R.raw.pop)
            }
        )
    }
}
@Composable
fun ColorEditDialog(
    initialHex: String,
    deleteButtonText: String,
    onDismiss: () -> Unit,
    onDelete: () -> Unit,
    onConfirm: (String) -> Unit
) {
    val customFont = FontFamily(Font(R.font.pixy))

    val hsv = remember {
        val hsvArray = FloatArray(3)
        try {
            val colorInt = initialHex.removePrefix("0x").toLong(16).toInt()
            android.graphics.Color.colorToHSV(colorInt, hsvArray)
        } catch (_: Exception) {
            floatArrayOf(0f, 1f, 1f)
        }
        mutableStateOf(hsvArray)
    }

    var hexInput by remember { mutableStateOf(initialHex.removePrefix("0x").uppercase()) }
    val currentColor = remember(hsv.value) { Color(android.graphics.Color.HSVToColor(hsv.value)) }

    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RectangleShape,
        containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),
        title = { Text(s(R.string.color_edit), fontFamily = customFont) },
        text = {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(40.dp)
                        .background(currentColor, RectangleShape)
                        .border(1.dp, Color.Black)
                )

                Box(
                    modifier = Modifier
                        .size(200.dp)
                        .border(1.dp, Color.Black)
                        .pointerInput(Unit) {
                            detectDragGestures { change, _ ->
                                // ИСПОЛЬЗУЕМ size.width.toFloat() и т.д.
                                val w = size.width.toFloat()
                                val h = size.height.toFloat()
                                val x = change.position.x.coerceIn(0f, w)
                                val y = change.position.y.coerceIn(0f, h)

                                hsv.value = hsv.value.copyOf().apply {
                                    this[1] = x / w // Saturation
                                    this[2] = 1f - (y / h) // Value
                                }
                                hexInput = Integer.toHexString(android.graphics.Color.HSVToColor(hsv.value)).uppercase()
                            }
                        }
                ) {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val hueColor = Color(android.graphics.Color.HSVToColor(floatArrayOf(hsv.value[0], 1f, 1f)))
                        drawRect(hueColor)
                        drawRect(brush = Brush.horizontalGradient(listOf(Color.White, Color.Transparent)))
                        drawRect(brush = Brush.verticalGradient(listOf(Color.Transparent, Color.Black)))

                        val selectorPos = Offset(
                            x = hsv.value[1] * size.width,
                            y = (1f - hsv.value[2]) * size.height
                        )
                        drawCircle(Color.White, radius = 8.dp.toPx(), center = selectorPos, style = Stroke(width = 2.dp.toPx()))
                        drawCircle(Color.Black, radius = 9.dp.toPx(), center = selectorPos, style = Stroke(width = 1.dp.toPx()))
                    }
                }

                // 2. ПОЛОСКА (Hue)
                Canvas(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(24.dp)
                        .border(1.dp, Color.Black)
                        .pointerInput(Unit) {
                            detectTapGestures { offset ->
                                val w = size.width.toFloat()
                                val newHue = (offset.x / w) * 360f
                                hsv.value = hsv.value.copyOf().apply { this[0] = newHue.coerceIn(0f, 360f) }
                                hexInput = Integer.toHexString(android.graphics.Color.HSVToColor(hsv.value)).uppercase()
                            }
                        }
                ) {
                    val w = size.width
                    val h = size.height
                    for (i in 0..360) {
                        drawRect(
                            color = Color(android.graphics.Color.HSVToColor(floatArrayOf(i.toFloat(), 1f, 1f))),
                            topLeft = Offset((i / 360f) * w, 0f),
                            size = Size(w / 360f + 1f, h)
                        )
                    }
                    val xPos = (hsv.value[0] / 360f) * w
                    drawLine(Color.Black, Offset(xPos, 0f), Offset(xPos, h), 3.dp.toPx())
                }

                // 3. ТЕКСТОВОЕ ПОЛЕ
                OutlinedTextField(
                    value = hexInput,
                    onValueChange = { input ->
                        val clean = input.replace("#", "").uppercase().take(8)
                        hexInput = clean
                        try {
                            if (clean.length == 6 || clean.length == 8) {
                                val finalStr = if (clean.length == 6) "FF$clean" else clean
                                val colorInt = finalStr.toLong(16).toInt()
                                val newHsv = FloatArray(3)
                                android.graphics.Color.colorToHSV(colorInt, newHsv)
                                hsv.value = newHsv
                            }
                        } catch (_: Exception) {}
                    },
                    label = { Text("HEX Code", fontFamily = customFont) },
                    prefix = { Text("#", fontFamily = customFont) },
                    shape = RectangleShape,
                    textStyle = TextStyle(fontFamily = customFont),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm("0x$hexInput") }) {
                Text(s(R.string.save), fontFamily = customFont)
            }
        },
        dismissButton = {
            TextButton(onClick = onDelete) {
                Text(deleteButtonText, color = Color.Red, fontFamily = customFont)
            }
        }
    )
}

@Composable
fun AddCalendar(onClick: () -> Unit) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().padding(8.dp),
        shape = RectangleShape,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Image(
            painter = painterResource(R.drawable.add_24px),
            contentDescription = null,
            modifier = Modifier.fillMaxWidth().height(200.dp),
            contentScale = ContentScale.Fit,
            colorFilter = ColorFilter.tint(MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f))
        )
    }
}

@Composable
fun s(@StringRes id: Int) = stringResource(id)


@Preview(showBackground = true, showSystemUi = true, uiMode = Configuration.UI_MODE_NIGHT_NO)
@Composable
fun GreetingPreview() {
    Greeting()
}

