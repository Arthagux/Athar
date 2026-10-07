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
import java.net.HttpURLConnection
import java.net.URL
import kotlin.concurrent.thread

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { MaterialTheme { AtharScreen() } }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AtharScreen() {
    var ip by remember { mutableStateOf("192.168.1.2") }
    var message by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    val messages = remember { mutableStateListOf<String>() }

    Scaffold(topBar = { TopAppBar(title = { Text("ATHAR v0.1") }) }) { pad ->
        Column(Modifier.padding(pad).padding(16.dp).fillMaxSize()) {
            OutlinedTextField(
                value = ip,
                onValueChange = { ip = it },
                label = { Text("IP del PC") },
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(8.dp))
            LazyColumn(Modifier.weight(1f).fillMaxWidth()) {
                items(messages) { Text(it, Modifier.padding(vertical = 6.dp)) }
            }
            if (busy) Text("ATHAR está pensando...")
            OutlinedTextField(
                value = message,
                onValueChange = { message = it },
                label = { Text("Mensaje") },
                modifier = Modifier.fillMaxWidth()
            )
            Button(
                enabled = !busy && message.isNotBlank(),
                modifier = Modifier.fillMaxWidth(),
                onClick = {
                    val sent = message.trim()
                    message = ""
                    messages.add("Tú: $sent")
                    busy = true
                    thread {
                        val result = sendToAthar(ip, sent)
                        messages.add("ATHAR: $result")
                        busy = false
                    }
                }
            ) { Text("Enviar") }
        }
    }
}

private fun sendToAthar(ip: String, text: String): String = try {
    val c = URL("http://$ip:8765/chat").openConnection() as HttpURLConnection
    c.requestMethod = "POST"
    c.connectTimeout = 10000
    c.readTimeout = 120000
    c.doOutput = true
    c.setRequestProperty("Content-Type", "application/json")
    val safe = text
        .replace("\\", "\\\\")
        .replace("\"", "\\\"")
        .replace("\n", "\\n")
    c.outputStream.use {
        it.write("{\"message\":\"$safe\"}".toByteArray())
    }
    val stream = if (c.responseCode in 200..299) c.inputStream else c.errorStream
    stream.bufferedReader().use { it.readText() }
} catch (e: Exception) {
    "No pude conectar con ATHAR Core: " + (e.message ?: "error de red")
}