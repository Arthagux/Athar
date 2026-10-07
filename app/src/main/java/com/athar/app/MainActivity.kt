package com.athar.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

data class ChatLine(val author: String, var text: String)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { MaterialTheme { AtharScreen() } }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AtharScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val engine = remember { AtharLocalEngine(context.applicationContext) }
    var message by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf("Selecciona un modelo GGUF para activar ATHAR") }
    val messages = remember { mutableStateListOf<ChatLine>() }

    DisposableEffect(Unit) { onDispose { engine.destroy() } }

    val modelPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            busy = true
            status = "Copiando y cargando modelo local…"
            scope.launch(Dispatchers.IO) {
                try {
                    val dir = File(context.filesDir, "models").apply { mkdirs() }
                    val model = File(dir, "athar-model.gguf")
                    context.contentResolver.openInputStream(uri)!!.use { input ->
                        model.outputStream().use { output -> input.copyTo(output) }
                    }
                    engine.loadModel(model)
                    withContext(Dispatchers.Main) {
                        status = "IA local lista • modelo cargado en el teléfono"
                        busy = false
                    }
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) {
                        status = "No pude cargar el GGUF: " + (e.message ?: "error")
                        busy = false
                    }
                }
            }
        }
    }

    Scaffold(topBar = { TopAppBar(title = { Text("ATHAR v0.3 • IA local real") }) }) { pad ->
        Column(Modifier.padding(pad).padding(16.dp).fillMaxSize()) {
            Text(status, style = MaterialTheme.typography.labelLarge)
            Text("El modelo se ejecuta en este dispositivo. Internet es una herramienta aparte.", style = MaterialTheme.typography.bodySmall)
            Spacer(Modifier.height(8.dp))
            OutlinedButton(onClick = { modelPicker.launch(arrayOf("*/*")) }, enabled = !busy) {
                Text(if (engine.isReady) "Cambiar modelo GGUF" else "Seleccionar modelo GGUF")
            }
            Spacer(Modifier.height(8.dp))
            LazyColumn(Modifier.weight(1f).fillMaxWidth()) {
                items(messages) { Text("${it.author}: ${it.text}", Modifier.padding(vertical = 6.dp)) }
            }
            if (busy) Text("ATHAR está trabajando…")
            OutlinedTextField(value = message, onValueChange = { message = it }, label = { Text("Mensaje") }, modifier = Modifier.fillMaxWidth())
            Button(
                enabled = engine.isReady && !busy && message.isNotBlank(),
                modifier = Modifier.fillMaxWidth(),
                onClick = {
                    val sent = message.trim()
                    message = ""
                    messages.add(ChatLine("Tú", sent))
                    messages.add(ChatLine("ATHAR", ""))
                    val answerIndex = messages.lastIndex
                    busy = true
                    engine.generate(
                        sent,
                        onToken = { token ->
                            val old = messages[answerIndex]
                            messages[answerIndex] = old.copy(text = old.text + token)
                        },
                        onDone = { busy = false },
                        onError = { err ->
                            messages[answerIndex] = ChatLine("ATHAR", "Error: $err")
                            busy = false
                        }
                    )
                }
            ) { Text("Enviar") }
        }
    }
}
