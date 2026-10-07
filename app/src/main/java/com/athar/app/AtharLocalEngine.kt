package com.athar.app

import kotlin.concurrent.thread

/**
 * Punto único de entrada del modelo local.
 * La v0.2 elimina por completo la dependencia del PC/servidor.
 * El siguiente paso conecta aquí el runtime nativo GGUF/llama.cpp.
 */
class AtharLocalEngine {
    val isReady: Boolean = true

    fun generate(prompt: String, onResult: (String) -> Unit) {
        thread {
            onResult(
                "El núcleo local de ATHAR está activo en este teléfono. " +
                "Falta cargar el modelo GGUF para generar respuestas reales. Mensaje recibido: $prompt"
            )
        }
    }
}