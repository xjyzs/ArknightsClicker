package com.xjyzs.arknightsclicker

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.util.DisplayMetrics
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.ActivityCompat.requestPermissions
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import androidx.core.net.toUri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import com.xjyzs.arknightsclicker.ui.theme.ArknightsClickerTheme
import kotlin.concurrent.thread


class MainViewModel : ViewModel() {
    var msgs = mutableStateListOf<String>()
    fun addMsg(msg: String) {
        msgs.add(msg)
    }

    var height by mutableIntStateOf(1080)
    var width by mutableIntStateOf(1920)
    var physicalHeight by mutableIntStateOf(1080)
    var physicalWidth by mutableIntStateOf(1920)
    var ratio90 by mutableFloatStateOf(0.165f)
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            ArknightsClickerTheme {
                val viewModel: MainViewModel = viewModel()
                Surface(Modifier.fillMaxSize()) {
                    MainUI(viewModel)
                }
            }
        }
    }
}

@Composable
fun MainUI(viewModel: MainViewModel) {
    val scrollState = rememberLazyListState()
    val context = LocalContext.current
    var isRunning = false
    var isIgnoringBatteryOptimizations by remember { mutableStateOf(true) }
    val pref = context.getSharedPreferences("main", Context.MODE_PRIVATE)
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        requestPermissions(context as Activity, arrayOf(Manifest.permission.POST_NOTIFICATIONS), 2)
    }
    LaunchedEffect(if (viewModel.msgs.isNotEmpty()) viewModel.msgs.size else 0) {
        scrollState.scrollToItem(viewModel.msgs.size)
    }
    LaunchedEffect(Unit) {
        viewModel.ratio90 = pref.getFloat("ratio90", 0.165f)
        thread {
            try {
                // 触屏分辨率
                val process = ProcessBuilder("su", "-c", "getevent -p")
                    .redirectErrorStream(true)
                    .start()
                val result = process.inputStream.bufferedReader().readText()
                process.waitFor()
                val re = Regex(""" 0035 .*?max (?<height>.*?),""")
                val re2 = Regex(""" 0036 .*?max (?<width>.*?),""")
                val match1 = re.find(result)
                val match2 = re2.find(result)
                viewModel.height = (match1?.groups["height"]?.value ?: "1080").toInt()
                viewModel.width = (match2?.groups["width"]?.value ?: "1920").toInt()

                // 物理分辨率
                val windowManager =
                    context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
                val displayMetrics = DisplayMetrics()
                windowManager.defaultDisplay.getRealMetrics(displayMetrics)
                val rawWidth = displayMetrics.widthPixels
                val rawHeight = displayMetrics.heightPixels
                viewModel.physicalHeight = minOf(rawWidth, rawHeight)
                viewModel.physicalWidth = maxOf(rawWidth, rawHeight)
            } catch (e: Exception) {
                viewModel.addMsg("请先授予 root 权限: ${e.message}")
            }
            val serIntent = Intent(context, ArknightsLaunchService::class.java).apply {
                putExtra("height", viewModel.physicalHeight)
                putExtra("width", viewModel.physicalWidth)
            }
            ContextCompat.startForegroundService(context, serIntent)
            isIgnoringBatteryOptimizations = isIgnoringBatteryOptimizations(context)
        }
    }
    if (!isIgnoringBatteryOptimizations) {
        AlertDialog(
            {},
            {
                TextButton({
                    val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
                    intent.data = "package:${context.packageName}".toUri()
                    context.startActivity(intent)
                    isIgnoringBatteryOptimizations=true
                }) { Text("确定") }
            },
            dismissButton = {
                TextButton({
                    isIgnoringBatteryOptimizations=true
                }) { Text("取消") }
            },
            title = { Text("忽略电池优化") },
            text = { Text("由于本应用涉及后台操作，你需要忽略电池优化") })
    }
    Column(Modifier.padding(12.dp)) {
        Spacer(Modifier.size(36.dp))
        Text("明日方舟点击器", fontSize = 36.sp, color = MaterialTheme.colorScheme.primary)
        Row {
            Button({
                val serIntent = Intent(context, ArknightsLaunchService::class.java)
                ContextCompat.startForegroundService(context, serIntent)
            }) {
                Text("启动 明日方舟")
            }
            Spacer(Modifier.size(12.dp))
            Button(
                {
                    if (!isRunning) {
                        viewModel.addMsg("暂停部署已启动")
                        isRunning = true
                        val serIntent = Intent(context, ClickerService::class.java).apply {
                            putExtra("height", viewModel.height)
                            putExtra("ratio90", viewModel.ratio90)
                        }
                        ContextCompat.startForegroundService(context, serIntent)
                    }
                }) {
                Text("暂停部署")
            }
        }
        TextField(
            viewModel.ratio90.toString(),
            modifier = Modifier.fillMaxWidth(),
            onValueChange = {
                try {
                    viewModel.ratio90 = it.toFloat()
                    pref.edit {
                        putFloat("ratio90", viewModel.ratio90)
                    }
                } catch (_: Exception) {
                }
            },
            label = {
                Text("待部署区高度(0-1)")
            })
        SelectionContainer {
            LazyColumn(state = scrollState) {
                itemsIndexed(viewModel.msgs) { _, msg ->
                    Text(msg)
                }
            }
        }
    }
}

fun isIgnoringBatteryOptimizations(context: Context): Boolean {
    val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
    return powerManager.isIgnoringBatteryOptimizations(context.packageName)
}