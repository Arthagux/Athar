package com.athar.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

data class ChatLine(val author: String, val text: String)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { MaterialTheme { AtharScreen() } }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AtharScreen() {
    var message by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    val messages = remember { mutableStateListOf<ChatLine>() }
    val engine = remember { AtharLocalEngine() }
    val scope = rememberCoroutineScope()

    Scaffold(topBar = { TopAppBar(title = { Text("ATHAR v0.2 • IA local") }) }) { pad ->
        Column(Modifier.padding(pad).padding(16.dp).fillMaxSize()) {
            Text(if (engine.isReady) "Motor local listo" else "Preparando motor local…", style = MaterialTheme.typography.labelLarge)
            Text("Internet se usa solo como herramienta para información externa.", style = MaterialTheme.typography.bodySmall)
            Spacer(Modifier.height(8.dp))
            LazyColumn(Modifier.weight(1f).fillMaxWidth()) {
                items(messages) { Text("${it.author}: ${it.text}", Modifier.padding(vertical = 6.dp)) }
            }
            if (busy) Text("ATHAR está pensando…")
            OutlinedTextField(value = message, onValueChange = { message = it }, label = { Text("Mensaje") }, modifier = Modifier.fillMaxWidth())
            Button(
                enabled = !busy && message.isNotBlank() && engine.isReady,
                modifier = Modifier.fillMaxWidth(),
                onClick = {
                    val sent = message.trim()
                    message = ""
                    messages.add(ChatLine("Tú", sent))
                    busy = true
                    engine.generate(sent) { answer ->
                        scope.launch {
                            messages.add(ChatLine("ATHAR", answer))
                            busy = false
                        }
                    }
                }
            ) { Text("Enviar") }
        }
    }
}