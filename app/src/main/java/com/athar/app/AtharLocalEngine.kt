package com.athar.app

import android.content.Context
import com.arm.aichat.AiChat
import com.arm.aichat.InferenceEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class AtharLocalEngine(private val context: Context) {
    private val engine: InferenceEngine = AiChat.getInferenceEngine(context)
    private val scope = CoroutineScope(Dispatchers.IO + Job())

    @Volatile var isReady: Boolean = false
        private set

    suspend fun loadModel(model: File) {
        isReady = false
        engine.loadModel(model.absolutePath)
        engine.setSystemPrompt(
            "Eres ATHAR, un asistente de inteligencia artificial que se ejecuta localmente en el teléfono. " +
            "Responde de manera clara, útil y razonada. Tu idioma principal con este usuario es español."
        )
        isReady = true
    }

    fun generate(
        prompt: String,
        onToken: (String) -> Unit,
        onDone: () -> Unit,
        onError: (String) -> Unit
    ) {
        scope.launch {
            var failed = false
            engine.sendUserPrompt(prompt)
                .catch {
                    failed = true
                    withContext(Dispatchers.Main) {
                        onError(it.message ?: "Error de inferencia local")
                    }
                }
                .collect { token ->
                    withContext(Dispatchers.Main) { onToken(token) }
                }

            if (!failed) {
                withContext(Dispatchers.Main) { onDone() }
            }
        }
    }

    fun destroy() = engine.destroy()
}
