package com.bitcode.webcommandapp

import org.json.JSONArray
import org.json.JSONObject
import java.net.URLDecoder

/**
 * Simple handler for drone instructions from SignalR bridge.
 * Callbacks are handled by MainActivity for UI updates.
 */
class DroneInstructionHandler {

    fun handleInstruction(
        droneId: String,
        instruction: String,
        paramsJson: String,
        correlationId: String,
        timestamp: String,
        onMaybeProcessFlow: (request: RunFlowRequest, paso: String) -> Unit,
        onLogAppend: (String) -> Unit
    ) {
        val requiresResponse = correlationId.isNotBlank()
        val prefix = if (requiresResponse) "[REQ]" else "[MSG]"
        onLogAppend("$prefix $timestamp $instruction para $droneId")
        
        if (paramsJson.isNotBlank()) {
            onLogAppend("Parametros: $paramsJson")
        }

        if (requiresResponse) {
            onLogAppend("CorrelationId pendiente: ${correlationId.take(8)}...")
        }

        if (!instruction.equals("CARGAR_DRON_BRUTO", ignoreCase = true)) {
            return
        }

        extractAndProcessFlow(paramsJson, onMaybeProcessFlow, onLogAppend)
    }

    private fun extractAndProcessFlow(
        paramsJson: String,
        onMaybeProcessFlow: (request: RunFlowRequest, paso: String) -> Unit,
        onLogAppend: (String) -> Unit
    ) {
        val payload = try {
            JSONObject(paramsJson)
        } catch (ex: Exception) {
            onLogAppend("JSON de instruccion invalido: ${ex.message}")
            return
        }

        val tramiteId = payload.opt("tramiteId")?.toString().orEmpty()
        val tokenHijo = payload.opt("tokenHijo")?.toString().orEmpty()
        val paso = payload.opt("paso")?.toString().orEmpty().ifBlank { "1" }
        val urlCommand = payload.opt("urlcommand")?.toString().orEmpty()
        val bodyValue = payload.opt("body")
        val bodyJson = when (bodyValue) {
            is JSONObject, is JSONArray -> bodyValue.toString()
            null -> ""
            else -> bodyValue.toString()
        }

        onLogAppend("Ejecutando CARGAR_DRON_BRUTO tramite=$tramiteId paso=$paso")

        val queryParams = mutableMapOf<String, String>()
        queryParams.putAll(parseQueryParams(urlCommand))
        queryParams.putAll(flattenBodyParams(bodyValue))
        if (tramiteId.isNotBlank()) {
            queryParams["tramiteId"] = tramiteId
        }

        val request = RunFlowRequest(
            paso = paso,
            clientToken = tokenHijo,
            soapUrl = "",
            base = "",
            soapToken = "",
            queryParams = queryParams,
            bodyJson = bodyJson
        )

        onMaybeProcessFlow(request, paso)
    }

    private fun parseQueryParams(rawQuery: String): Map<String, String> {
        val trimmed = rawQuery.trim()
        if (trimmed.isBlank()) return emptyMap()
        val query = when {
            trimmed.startsWith("http://", ignoreCase = true) || trimmed.startsWith("https://", ignoreCase = true) ->
                trimmed.substringAfter('?', "")
            trimmed.startsWith("?") -> trimmed.removePrefix("?")
            else -> trimmed
        }
        if (query.isBlank()) return emptyMap()

        val result = linkedMapOf<String, String>()
        query.split("&")
            .filter { it.isNotBlank() }
            .forEach { part ->
                val pieces = part.split("=", limit = 2)
                val key = URLDecoder.decode(pieces[0], "UTF-8")
                val value = if (pieces.size > 1) URLDecoder.decode(pieces[1], "UTF-8") else ""
                result[key] = value
            }
        return result
    }

    private fun flattenBodyParams(bodyValue: Any?): Map<String, String> {
        if (bodyValue == null) return emptyMap()
        val result = linkedMapOf<String, String>()
        when (bodyValue) {
            is JSONObject -> {
                val keys = bodyValue.keys()
                while (keys.hasNext()) {
                    val key = keys.next()
                    result[key] = bodyValue.opt(key)?.toString().orEmpty()
                }
                result["body"] = bodyValue.toString()
            }
            is JSONArray -> {
                result["body"] = bodyValue.toString()
            }
            else -> {
                val raw = bodyValue.toString()
                result["body"] = raw
                if (raw.startsWith("{") && raw.endsWith("}")) {
                    try {
                        val obj = JSONObject(raw)
                        val keys = obj.keys()
                        while (keys.hasNext()) {
                            val key = keys.next()
                            result[key] = obj.opt(key)?.toString().orEmpty()
                        }
                    } catch (_: Exception) {
                    }
                }
            }
        }
        return result
    }
}

