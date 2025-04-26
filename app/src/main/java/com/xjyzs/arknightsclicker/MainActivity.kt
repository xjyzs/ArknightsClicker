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
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import com.xjyzs.arknightsclicker.theme.ArknightsClickerTheme
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

private fun startGetEventMonitoring(viewModel: MainViewModel) {
    var lastUpdatedTimeX=0f
    var lastUpdatedTimeY=0f
    thread {
        try {
            val process = Runtime.getRuntime().exec("su -c getevent -lt")
            val reader = BufferedReader(InputStreamReader(process.inputStream))
            while (true) {
                val line = reader.readLine() ?: break
                if (line.contains("ABS_MT_POSITION_X")) {
                    val lastX=line.substring(71,79).toInt(16)
                    if (4400<lastX && lastX<7000){
                        lastUpdatedTimeX=line.substring(4,16).toFloat()
                    }
                } else if (line.contains("ABS_MT_POSITION_Y")) {
                    val lastY = line.substring(71,79).toInt(16)
                    if (lastY<2400) {
                        lastUpdatedTimeY = line.substring(4, 16).toFloat()
                    }
                } else if (line.contains("EV_ABS") && line.contains("ABS_MT_TRACKING_ID") && line.contains("ffffffff")) {
                    val currentTime=line.substring(4,16).toFloat()
                    if (currentTime-lastUpdatedTimeX<0.1 && currentTime-lastUpdatedTimeY<0.1) {
                        Runtime.getRuntime().exec(
                            arrayOf("su", "-c", "input keyevent 4")
                        )
                        viewModel.addMsg("$currentTime 返回成功")
                    }
                }
            }
        } catch (e: Exception) {
            viewModel.addMsg(e.toString())
        }
    }
}