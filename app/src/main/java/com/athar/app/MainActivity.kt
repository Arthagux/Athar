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
import java.net.HttpURLConnection
import java.net.URL

data class ChatLine(val author: String, var text: String)

private const val DEFAULT_MODEL_FILE = "qwen2.5-1.5b-instruct-q4_k_m.gguf"
private const val DEFAULT_MODEL_URL =
    "https://huggingface.co/Qwen/Qwen2.5-1.5B-Instruct-GGUF/resolve/main/qwen2.5-1.5b-instruct-q4_k_m.gguf?download=true"

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { MaterialTheme { AtharScreen() } }
    }
}

private fun modelFile(context: android.content.Context): File {
    return File(File(context.filesDir, "models").apply { mkdirs() }, DEFAULT_MODEL_FILE)
}

private fun downloadModel(
    target: File,
    onProgress: (Int) -> Unit
) {
    val tmp = File(target.parentFile, target.name + ".part")
    var conn: HttpURLConnection? = null
    try {
        conn = (URL(DEFAULT_MODEL_URL).openConnection() as HttpURLConnection).apply {
            instanceFollowRedirects = true
            connectTimeout = 30_000
            readTimeout = 30_000
            requestMethod = "GET"
            setRequestProperty("User-Agent", "ATHAR-Android/0.4")
        }
        conn.connect()
        if (conn.responseCode !in 200..299) {
            error("HTTP ${conn.responseCode}")
        }

        val total = conn.contentLengthLong
        conn.inputStream.buffered().use { input ->
            tmp.outputStream().buffered().use { output ->
                val buffer = ByteArray(1024 * 1024)
                var read: Int
                var done = 0L
                while (input.read(buffer).also { read = it } >= 0) {
                    if (read == 0) continue
                    output.write(buffer, 0, read)
                    done += read
                    if (total > 0L) {
                        onProgress(((done * 100L) / total).toInt().coerceIn(0, 100))
                    }
                }
            }
        }
        if (target.exists()) target.delete()
        if (!tmp.renameTo(target)) {
            tmp.copyTo(target, overwrite = true)
            tmp.delete()
        }
    } catch (e: Exception) {
        tmp.delete()
        throw e
    } finally {
        conn?.disconnect()
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
    var progress by remember { mutableIntStateOf(0) }
    var status by remember { mutableStateOf("Preparando ATHAR…") }
    val messages = remember { mutableStateListOf<ChatLine>() }

    DisposableEffect(Unit) { onDispose { engine.destroy() } }

    fun loadLocalModel(file: File) {
        busy = true
        status = "Cargando IA local…"
        scope.launch(Dispatchers.IO) {
            try {
                engine.loadModel(file)
                withContext(Dispatchers.Main) {
                    status = "IA local lista • Qwen2.5 1.5B cargado en el teléfono"
                    busy = false
                    progress = 100
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    status = "No pude cargar el modelo: " + (e.message ?: "error")
                    busy = false
                }
            }
        }
    }

    LaunchedEffect(Unit) {
        val local = modelFile(context)
        if (local.exists() && local.length() > 500_000_000L) {
            loadLocalModel(local)
        } else {
            status = "ATHAR necesita descargar su modelo local (≈1,12 GB)"
        }
    }

    val modelPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            busy = true
            status = "Copiando y cargando modelo local…"
            scope.launch(Dispatchers.IO) {
                try {
                    val model = modelFile(context)
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

    Scaffold(topBar = { TopAppBar(title = { Text("ATHAR v0.4 • IA local real") }) }) { pad ->
        Column(Modifier.padding(pad).padding(16.dp).fillMaxSize()) {
            Text(status, style = MaterialTheme.typography.labelLarge)
            Text(
                "El modelo se ejecuta en este dispositivo. Internet solo se usa para descargarlo y como herramienta aparte.",
                style = MaterialTheme.typography.bodySmall
            )
            Spacer(Modifier.height(8.dp))

            if (!engine.isReady) {
                Button(
                    enabled = !busy,
                    modifier = Modifier.fillMaxWidth(),
                    onClick = {
                        busy = true
                        progress = 0
                        status = "Descargando modelo local Qwen2.5 1.5B…"
                        scope.launch(Dispatchers.IO) {
                            try {
                                val model = modelFile(context)
                                downloadModel(model) { p ->
                                    scope.launch(Dispatchers.Main) {
                                        progress = p
                                        status = "Descargando modelo local… $p%"
                                    }
                                }
                                withContext(Dispatchers.Main) {
                                    status = "Descarga completa • cargando IA local…"
                                }
                                engine.loadModel(model)
                                withContext(Dispatchers.Main) {
                                    status = "IA local lista • Qwen2.5 1.5B cargado"
                                    progress = 100
                                    busy = false
                                }
                            } catch (e: Exception) {
                                withContext(Dispatchers.Main) {
                                    status = "Error al descargar/cargar: " + (e.message ?: "error")
                                    busy = false
                                }
                            }
                        }
                    }
                ) { Text("Descargar cerebro de ATHAR (~1,12 GB)") }

                if (busy && progress in 1..99) {
                    Spacer(Modifier.height(8.dp))
                    LinearProgressIndicator(progress = { progress / 100f }, modifier = Modifier.fillMaxWidth())
                    Text("$progress%")
                }

                Spacer(Modifier.height(8.dp))
                OutlinedButton(
                    onClick = { modelPicker.launch(arrayOf("*/*")) },
                    enabled = !busy,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Seleccionar GGUF manualmente")
                }
            } else {
                OutlinedButton(
                    onClick = { modelPicker.launch(arrayOf("*/*")) },
                    enabled = !busy,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Cambiar modelo GGUF")
                }
            }

            Spacer(Modifier.height(8.dp))
            LazyColumn(Modifier.weight(1f).fillMaxWidth()) {
                items(messages) { Text("${it.author}: ${it.text}", Modifier.padding(vertical = 6.dp)) }
            }
            if (busy && progress !in 1..99) Text("ATHAR está trabajando…")
            OutlinedTextField(
                value = message,
                onValueChange = { message = it },
                label = { Text("Mensaje") },
                modifier = Modifier.fillMaxWidth()
            )
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
