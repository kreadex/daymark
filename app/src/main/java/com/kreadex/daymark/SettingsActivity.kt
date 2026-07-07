package com.kreadex.daymark

import android.content.Context
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kreadex.daymark.data.AppPreferences
import com.kreadex.daymark.data.db.AppDatabase
import com.kreadex.daymark.ui.theme.DaymarkTheme
import com.kreadex.daymark.utils.BackupManager
import kotlinx.coroutines.launch

import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalUriHandler
import kotlinx.coroutines.delay

class SettingsActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        val db = AppDatabase.get(this)
        val backupManager = BackupManager(db.calendarDao())

        val prefs = AppPreferences(this)

        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            var currentThemeMode by remember { mutableStateOf(prefs.themeMode) }

            val isDark = when (currentThemeMode) {
                AppPreferences.THEME_LIGHT -> false
                AppPreferences.THEME_DARK -> true
                else -> isSystemInDarkTheme()
            }

            DaymarkTheme(
                darkTheme = isDark
            ) {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    SettingsList(
                        modifier = Modifier.padding(innerPadding),
                        backupManager = backupManager,
                        context = this,
                        currentThemeMode = currentThemeMode,
                        onThemeChange = { nextMode ->
                            currentThemeMode = nextMode
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun SettingsList(
    modifier: Modifier = Modifier,
    backupManager: BackupManager,
    context: Context,
    currentThemeMode: Int,
    onThemeChange: (Int) -> Unit) {
    val scope = rememberCoroutineScope()
    val prefs = remember { AppPreferences(context) }

    var soundOn by remember { mutableStateOf(prefs.isSoundEnabled) }

    val uriHandler = LocalUriHandler.current

    val pickJsonLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            val json = context.contentResolver.openInputStream(uri)
                ?.bufferedReader()
                .use { it?.readText() }

            if (!json.isNullOrEmpty()) {
                scope.launch {
                    backupManager.importFromJson(json)
                }
            }
        }
    }

    var boostClicked by remember { mutableStateOf(false) }

    if (boostClicked) {
        LaunchedEffect(Unit) {
            delay(20_000)
            boostClicked = false
        }
    }

    val context = LocalContext.current
    CompositionLocalProvider(
        LocalTextStyle provides TextStyle(fontFamily = FontFamily(Font(R.font.pixy)))
    ) {
        Column(modifier.padding(horizontal = 10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(text = s(R.string.settings), fontSize = 40.sp)
                IconButton(onClick = { (context as? SettingsActivity)?.finish() }) {
                    Icon(
                        painter = BitmapPainter(ImageBitmap.imageResource(id = R.drawable.cross), filterQuality = FilterQuality.None),
                        contentDescription = null, modifier = Modifier.size(40.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Ряд ТЕМЫ
            SettingsRow(
                title = when (currentThemeMode) {
                    AppPreferences.THEME_LIGHT -> s(R.string.theme_light)
                    AppPreferences.THEME_DARK -> s(R.string.theme_dark)
                    else -> s(R.string.theme_system)
                },
                icon = when (currentThemeMode) {
                    AppPreferences.THEME_LIGHT -> R.drawable.sun
                    AppPreferences.THEME_DARK -> R.drawable.moon
                    else -> R.drawable.theme
                },
                onClick = {
                    val nextMode = (currentThemeMode + 1) % 3
                    prefs.themeMode = nextMode // Сохраняем в память
                    onThemeChange(nextMode)    // Триггерим перерисовку Activity и Темы
                }
            )

            SettingsRow(
                title = s(R.string.sound),
                icon = if (soundOn) R.drawable.sound else R.drawable.sound_off,
                onClick = {
                    soundOn = !soundOn
                    prefs.isSoundEnabled = soundOn

                    SoundManager.updateSoundEnabled(soundOn)
                }
            )

            Row (modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = s(R.string.exchange),
                    fontSize = 24.sp,
                    fontFamily = FontFamily(Font(R.font.pixy))
                )
                Row (horizontalArrangement = Arrangement.spacedBy(15.dp)) {
                    Button(
                        onClick = { pickJsonLauncher.launch(arrayOf("application/json")) },
                        shape = RoundedCornerShape(0.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                    ) {
                        //Text("DELETE", fontSize = 20.sp)
                        Image(
                            painter = BitmapPainter(
                                ImageBitmap.imageResource(id = R.drawable.importing),
                                filterQuality = FilterQuality.None
                            ),
                            contentDescription = null,
                            modifier = Modifier
                                //.fillMaxWidth()
                                .height(24.dp),
                            contentScale = ContentScale.Crop,
                            colorFilter = ColorFilter.tint(MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.7f))
                        )
                    }
                    Button(
                        onClick = {
                            scope.launch {
                                val json = backupManager.exportToJson()
                                backupManager.exportFile(context, json)
                            }
                        },
                        shape = RoundedCornerShape(0.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                    ) {
                        //Text("DELETE", fontSize = 20.sp)
                        Image(
                            painter = BitmapPainter(
                                ImageBitmap.imageResource(id = R.drawable.export),
                                filterQuality = FilterQuality.None
                            ),
                            contentDescription = null,
                            modifier = Modifier
                                //.fillMaxWidth()
                                .height(24.dp),
                            contentScale = ContentScale.Crop,
                            colorFilter = ColorFilter.tint(MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.7f))
                        )
                    }
                }

            }

            SettingsRow(
                title = if (boostClicked) s(R.string.thanks) else s(R.string.boost),
                icon = R.drawable.money,
                onClick = {
                    uriHandler.openUri("https://krx.su/donate")

                    boostClicked = true

                    SoundManager.play(R.raw.ko)
                }
            )

            Spacer(modifier = Modifier.height(20.dp))

            val versionName = getAppVersion()

            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(4.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    "Daymark",
                    fontSize = 16.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
                Text(
                    "Version $versionName",
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
            }

        }
    }



}

@Composable
fun getAppVersion(): String {
    val context = LocalContext.current
    return try {
        val packageInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.packageManager.getPackageInfo(context.packageName, PackageManager.PackageInfoFlags.of(0))
        } else {
            @Suppress("DEPRECATION")
            context.packageManager.getPackageInfo(context.packageName, 0)
        }
        packageInfo.versionName ?: "1.0"
    } catch (e: Exception) {
        "?.?"
    }
}

@Composable
fun SettingsRow(
    title: String,
    @androidx.annotation.DrawableRes icon: Int,
    onClick: () -> Unit
) {
    val customFont = FontFamily(Font(R.font.pixy))

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(60.dp)
            .clickable { onClick() },
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            fontSize = 24.sp,
            fontFamily = customFont
        )

        // Используем Image с FilterQuality.None для сохранения пиксельности
        Image(
            painter = BitmapPainter(
                ImageBitmap.imageResource(id = icon),
                filterQuality = FilterQuality.None
            ),
            contentDescription = null,
            modifier = Modifier.size(32.dp),
            colorFilter = ColorFilter.tint(MaterialTheme.colorScheme.primary)
        )
    }
}

@Preview(showBackground = true, showSystemUi = true, uiMode = Configuration.UI_MODE_NIGHT_NO)
@Composable
fun GreetingPreview2() {
    val context = LocalContext.current
    val fakeDao = CalendarDaoFake()
    val backupManager = BackupManager(fakeDao)

    // Эмулируем состояние для превью
    var previewThemeMode by remember { mutableStateOf(AppPreferences.THEME_SYSTEM) }

    DaymarkTheme(
        // Передаем принудительно, чтобы превью реагировало на изменение
        darkTheme = if(previewThemeMode == AppPreferences.THEME_DARK) true else false
    ) {
        SettingsList(
            backupManager = backupManager,
            context = context,
            currentThemeMode = previewThemeMode,
            onThemeChange = { nextMode ->
                previewThemeMode = nextMode
            }
        )
    }
}