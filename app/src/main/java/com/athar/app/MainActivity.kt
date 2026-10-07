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

private const val BUNDLED_MODEL_ASSET = "athar-model.gguf"
private const val BUNDLED_MODEL_FILE = "athar-model.gguf"

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { MaterialTheme { AtharScreen() } }
    }
}

private fun modelFile(context: android.content.Context): File {
    return File(File(context.filesDir, "models").apply { mkdirs() }, BUNDLED_MODEL_FILE)
}

private fun installBundledModel(context: android.content.Context, target: File) {
    if (target.exists() && target.length() > 100_000_000L) return

    val tmp = File(target.parentFile, target.name + ".part")
    try {
        context.assets.open(BUNDLED_MODEL_ASSET).buffered().use { input ->
            tmp.outputStream().buffered().use { output ->
                input.copyTo(output, 1024 * 1024)
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
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AtharScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val engine = remember { AtharLocalEngine(context.applicationContext) }
    var message by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(true) }
    var status by remember { mutableStateOf("Iniciando IA local…") }
    val messages = remember { mutableStateListOf<ChatLine>() }

    DisposableEffect(Unit) { onDispose { engine.destroy() } }

    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            try {
                val local = modelFile(context)
                withContext(Dispatchers.Main) {
                    status = if (local.exists()) "Cargando cerebro de ATHAR…" else "Preparando cerebro integrado de ATHAR…"
                }
                installBundledModel(context, local)
                engine.loadModel(local)
                withContext(Dispatchers.Main) {
                    status = "IA local lista • cerebro integrado activo"
                    busy = false
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    status = "No pude iniciar el modelo integrado: " + (e.message ?: "error")
                    busy = false
                }
            }
        }
    }

    val modelPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            busy = true
            status = "Cambiando modelo local…"
            scope.launch(Dispatchers.IO) {
                try {
                    val model = modelFile(context)
                    context.contentResolver.openInputStream(uri)!!.use { input ->
                        model.outputStream().use { output -> input.copyTo(output) }
                    }
                    engine.loadModel(model)
                    withContext(Dispatchers.Main) {
                        status = "IA local lista • modelo alternativo cargado"
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

    Scaffold(topBar = { TopAppBar(title = { Text("ATHAR v0.4 • IA local integrada") }) }) { pad ->
        Column(Modifier.padding(pad).padding(16.dp).fillMaxSize()) {
            Text(status, style = MaterialTheme.typography.labelLarge)
            Text(
                "ATHAR funciona con su modelo de IA dentro de la propia aplicación. No necesita descargar un cerebro aparte.",
                style = MaterialTheme.typography.bodySmall
            )
            Spacer(Modifier.height(8.dp))

            OutlinedButton(
                onClick = { modelPicker.launch(arrayOf("*/*")) },
                enabled = !busy,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Usar otro modelo GGUF (opcional)")
            }

            Spacer(Modifier.height(8.dp))
            LazyColumn(Modifier.weight(1f).fillMaxWidth()) {
                items(messages) { Text("${it.author}: ${it.text}", Modifier.padding(vertical = 6.dp)) }
            }

            if (busy) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(8.dp))
                Text("ATHAR está preparando la IA local…")
            }

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
