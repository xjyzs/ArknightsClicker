package com.xjyzs.arknightsclicker

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.xjyzs.arknightsclicker.theme.ArknightsClickerTheme
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
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
                MainUI(viewModel)
            }
        }
    }
}

@Composable
fun MainUI(viewModel: MainViewModel) {
    val scrollState = rememberLazyListState()
    val scope=rememberCoroutineScope()
    LaunchedEffect(if (viewModel.msgs.isNotEmpty())viewModel.msgs.size else 0) {
        scrollState.scrollToItem(viewModel.msgs.size)
    }
    LaunchedEffect(Unit) {
        viewModel.addMsg("服务已启动")
        startGetEventMonitoring(viewModel)
    }
    Column {
        Spacer(Modifier.size(36.dp))
        Text("明日方舟暂停部署", fontSize = 36.sp, color = MaterialTheme.colorScheme.primary)
        Button({
            Runtime.getRuntime().exec("su -c am start com.hypergryph.arknights/com.u8.sdk.U8UnityContext")
            viewModel.addMsg("com.hypergryph.arknights/com.u8.sdk.U8UnityContext 已启动")
        }) {
            Text("启动 明日方舟")
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
            val inputStream = process.inputStream.bufferedReader()
            outputStream.write("getevent\n".toByteArray())
            outputStream.flush()
            while (true) {
                val line = inputStream.readLine() ?: break
                if (line.contains("0036")) { // X 坐标
                    if (line.substring(10,18).toInt(16)<1200) {
                        Runtime.getRuntime().exec("input keyevent 4")
                        viewModel.addMsg("返回成功")
                        while (true) {
                            if ((inputStream.readLine() ?: break).contains("0039 ffffffff")) {
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
