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
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.ActivityCompat.requestPermissions
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.xjyzs.arknightsclicker.ui.theme.ArknightsClickerTheme
import java.io.BufferedReader
import java.io.InputStreamReader
import kotlin.concurrent.thread


class MainViewModel : ViewModel() {
    var msgs= mutableStateListOf<String>()
    fun addMsg(msg: String){
        msgs.add(msg)
    }
}
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            ArknightsClickerTheme {
                val viewModel: MainViewModel=viewModel()
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
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        requestPermissions(context as Activity,arrayOf(Manifest.permission.POST_NOTIFICATIONS), 2)
    }
    LaunchedEffect(if (viewModel.msgs.isNotEmpty())viewModel.msgs.size else 0) {
        scrollState.scrollToItem(viewModel.msgs.size)
    }
    LaunchedEffect(Unit){
        val serIntent=Intent(context, ArknightsLaunchService::class.java)
        ContextCompat.startForegroundService(context, serIntent)
    }
    Column {
        Spacer(Modifier.size(36.dp))
        Text("明日方舟点击器", fontSize = 36.sp, color = MaterialTheme.colorScheme.primary)
        Row {
            Button({
                Runtime.getRuntime()
                    .exec("su -c am start com.hypergryph.arknights/com.u8.sdk.U8UnityContext")
                viewModel.addMsg("com.hypergryph.arknights/com.u8.sdk.U8UnityContext 已启动")
            }) {
                Text("启动 明日方舟")
            }
            Spacer(Modifier.size(12.dp))
            Button({
                viewModel.addMsg("暂停部署已启动")
                startGetEventMonitoring(viewModel)
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
                if (line.contains("0036")) {
                    if (line.substring(29, 37).toInt(16) < 1200) {
                        outputStream.write("input keyevent 4\n".toByteArray())
                        outputStream.flush()
                        viewModel.addMsg("返回成功")
                        while (true) {
                            if ((reader.readLine() ?: break).contains("0039 ffffffff")) {
                                break
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            viewModel.addMsg(e.toString())
        }
    }
}