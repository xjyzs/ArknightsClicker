package com.xjyzs.arknightsclicker

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.ActivityCompat.requestPermissions
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import com.xjyzs.arknightsclicker.ui.theme.ArknightsClickerTheme
import java.io.BufferedReader
import java.io.InputStreamReader
import kotlin.concurrent.thread


class MainViewModel : ViewModel() {
    var msgs = mutableStateListOf<String>()
    fun addMsg(msg: String) {
        msgs.add(msg)
    }

    var arknightsFocused by mutableStateOf(false)
    var height by mutableIntStateOf(1080)
    var width by mutableIntStateOf(1920)
    val ratio270min = 0.825
    val ratio270max = 0.835
    val ratio90min = 1 - ratio270max
    val ratio90max = 1 - ratio270min
    var actualMin = 0
    var actualMax = 0
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
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        requestPermissions(context as Activity, arrayOf(Manifest.permission.POST_NOTIFICATIONS), 2)
    }
    LaunchedEffect(if (viewModel.msgs.isNotEmpty()) viewModel.msgs.size else 0) {
        scrollState.scrollToItem(viewModel.msgs.size)
    }
    LaunchedEffect(Unit) {
        thread {
            try {
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
            }catch (_: Exception){
                viewModel.addMsg("请先授予 root 权限")
            }
            val serIntent = Intent(context, ArknightsLaunchService::class.java).apply {
                putExtra("height", viewModel.height)
                putExtra("width", viewModel.width)
            }
            ContextCompat.startForegroundService(context, serIntent)
        }
    }
    Column {
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
            Button({
                if (!isRunning) {
                    viewModel.addMsg("暂停部署已启动")
                    startGetEventMonitoring(viewModel)
                    isRunning = true
                }
            }) {
                Text("暂停部署")
            }
        }
        SelectionContainer {
            LazyColumn(state = scrollState) {
                itemsIndexed(viewModel.msgs) { _, msg ->
                    Text(msg)
                }
            }
        }
    }
}

fun startGetEventMonitoring(viewModel: MainViewModel) {
    thread {
        try {
            val process = ProcessBuilder("su").apply {
                redirectErrorStream(true)
            }.start()
            val outputStream = process.outputStream
            val p = Runtime.getRuntime().exec("su -c getevent")
            val reader = BufferedReader(InputStreamReader(p.inputStream))
            while (true) {
                val line = reader.readLine() ?: break
                if (viewModel.arknightsFocused) {
                    if (" 0035 " in line) {
                        if (viewModel.actualMin < line.substring(30, 37)
                                .toInt(16) && line.substring(
                                30,
                                37
                            ).toInt(16) < viewModel.actualMax
                        ) {
                            outputStream.write("input keyevent 4\n".toByteArray())
                            outputStream.flush()
                            viewModel.addMsg("返回成功")
                            while (true) {
                                if ((reader.readLine() ?: break).contains("014a 00000000")) {
                                    break
                                }
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            viewModel.addMsg(e.toString())
        }
    }
    thread {
        try {
            val process = ProcessBuilder("su").apply {
                redirectErrorStream(true)
            }.start()
            val outputStream = process.outputStream
            val reader = BufferedReader(InputStreamReader(process.inputStream))
            while (true) {
                outputStream.write("dumpsys window | grep -E 'mCurrentFocus|mDisplayRotation';echo 'END_OF_CMD'\n".toByteArray())
                outputStream.flush()
                val result = StringBuilder()
                var line: String?
                while (true) {
                    line = reader.readLine() ?: break
                    if (line.contains("END_OF_CMD")) break
                    result.appendLine(line)
                }
                val tmp = result.toString()
                if ("com.u8.sdk.U8UnityContext" in tmp) viewModel.arknightsFocused =
                    true else viewModel.arknightsFocused = false
                val re =
                    Regex(""" mDisplayRotation=(?<rotation>.*?) """)
                val match = re.find(tmp)
                if ("270" in (match?.groups["rotation"]?.value ?: "90")) {
                    viewModel.actualMin = (viewModel.height * viewModel.ratio270min).toInt()
                    viewModel.actualMax = (viewModel.height * viewModel.ratio270max).toInt()
                } else {
                    viewModel.actualMin = (viewModel.height * viewModel.ratio90min).toInt()
                    viewModel.actualMax = (viewModel.height * viewModel.ratio90max).toInt()
                }
                Thread.sleep(1000)
            }
        } catch (e: Exception) {
            viewModel.addMsg(e.toString())
        }
    }
}