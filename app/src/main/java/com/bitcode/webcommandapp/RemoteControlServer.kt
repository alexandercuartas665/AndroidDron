package com.bitcode.webcommandapp

import android.util.Log
import fi.iki.elonen.NanoHTTPD
import org.json.JSONObject
import java.io.File
import java.util.Locale

class RemoteControlServer(
    port: Int,
    private val expectedApiKey: String,
    private val onNavigate: (String) -> Unit,
    private val onExecute: (String) -> String,
    private val onStatus: () -> String,
    private val onDbPing: (RunFlowRequest) -> String,
    private val onRunFlow: (RunFlowRequest) -> String
) : NanoHTTPD(port) {

    override fun serve(session: IHTTPSession): Response {
        return try {
            val path = (session.uri ?: "/").lowercase(Locale.US)
            Log.d("WebCommandApp", "HTTP ${session.method} $path")
            val postBody = parsePostBody(session)
            val mergedParams = mergeParams(session, postBody)

            if (!isAuthorized(mergedParams, session)) {
                return textResponse(Response.Status.UNAUTHORIZED, "Unauthorized: missing or invalid API key")
            }

            when (path) {
                "/" -> {
                    if (mergedParams["paso"].orEmpty().isNotBlank()) {
                        handleRun(mergedParams)
                    } else {
                        textResponse(Response.Status.OK, helpText())
                    }
                }
                "/help" -> textResponse(Response.Status.OK, helpText())
                "/health" -> textResponse(Response.Status.OK, onStatus())
                "/navigate" -> handleNavigate(mergedParams)
                "/execute" -> handleExecute(mergedParams)
                "/dbping" -> handleDbPing(mergedParams)
                "/run" -> handleRun(mergedParams)
                else -> textResponse(Response.Status.NOT_FOUND, "Endpoint not found")
            }
        } catch (ex: Exception) {
            Log.e("WebCommandApp", "HTTP serve error", ex)
            textResponse(Response.Status.INTERNAL_ERROR, "Internal server error: ${ex.message}")
        }
    }

    private fun handleNavigate(params: Map<String, String>): Response {
        val url = params["url"].orEmpty().trim()
        if (url.isEmpty()) {
            return textResponse(Response.Status.BAD_REQUEST, "Missing 'url' parameter")
        }

        onNavigate(url)
        return textResponse(Response.Status.OK, "Navigating to: $url")
    }

    private fun handleExecute(params: Map<String, String>): Response {
        val script = params["script"].orEmpty().trim()
        if (script.isEmpty()) {
            return textResponse(Response.Status.BAD_REQUEST, "Missing 'script' parameter")
        }

        val result = onExecute(script)
        return textResponse(Response.Status.OK, result)
    }

    private fun handleRun(params: Map<String, String>): Response {
        val request = buildRunRequest(params)
        val resultJson = onRunFlow(request)
        return newFixedLengthResponse(Response.Status.OK, "application/json; charset=utf-8", resultJson)
    }

    private fun handleDbPing(params: Map<String, String>): Response {
        val request = buildRunRequest(params)
        val resultJson = onDbPing(request)
        return newFixedLengthResponse(Response.Status.OK, "application/json; charset=utf-8", resultJson)
    }

    private fun buildRunRequest(params: Map<String, String>): RunFlowRequest {
        val paso = params["paso"].orEmpty().ifBlank { "1" }
        val clientToken = params["clientToken"].orEmpty().ifBlank { params["clienttoken"].orEmpty() }
        val soapUrl = params["soapUrl"].orEmpty().ifBlank { params["soapurl"].orEmpty() }
        val base = params["base"].orEmpty()
        val soapToken = params["soapToken"].orEmpty().ifBlank { params["soaptoken"].orEmpty() }
        val bodyJson = params["body"].orEmpty()
        val queryParams = params.filterKeys {
            val key = it.lowercase(Locale.US)
            key !in RESERVED_KEYS
        }

        return RunFlowRequest(
            paso = paso,
            clientToken = clientToken,
            soapUrl = soapUrl,
            base = base,
            soapToken = soapToken,
            queryParams = queryParams,
            bodyJson = bodyJson
        )
    }

    private fun isAuthorized(params: Map<String, String>, session: IHTTPSession): Boolean {
        if (expectedApiKey.isBlank()) {
            return true
        }

        val headerKey = session.headers["x-api-key"]?.trim().orEmpty()
        val queryKey = params["key"]?.trim().orEmpty()
        if (headerKey.isBlank() && queryKey.isBlank()) {
            return true
        }
        return headerKey == expectedApiKey || queryKey == expectedApiKey
    }

    private fun parsePostBody(session: IHTTPSession): String {
        if (session.method != Method.POST) return ""
        return try {
            val bodyFiles = HashMap<String, String>()
            session.parseBody(bodyFiles)
            val tempPath = bodyFiles["postData"].orEmpty()
            if (tempPath.isBlank()) return ""
            File(tempPath).readText(Charsets.UTF_8)
        } catch (_: Exception) {
            ""
        }
    }

    private fun mergeParams(session: IHTTPSession, postBody: String): Map<String, String> {
        val merged = linkedMapOf<String, String>()
        session.parameters.forEach { (k, v) ->
            merged[k] = v.firstOrNull().orEmpty()
        }

        if (postBody.isNotBlank() && postBody.trimStart().startsWith("{")) {
            try {
                val json = JSONObject(postBody)
                json.keys().forEach { key ->
                    val value = json.opt(key)
                    if (value != null && value !is JSONObject) {
                        merged[key] = value.toString()
                    }
                }

                val qp = json.optJSONObject("queryParams")
                if (qp != null) {
                    qp.keys().forEach { k ->
                        merged[k] = qp.opt(k)?.toString().orEmpty()
                    }
                }
            } catch (_: Exception) {
            }
        }

        return merged
    }

    private fun textResponse(status: Response.Status, body: String): Response {
        return newFixedLengthResponse(status, "text/plain; charset=utf-8", body)
    }

    private fun helpText(): String {
        return buildString {
            appendLine("WebCommandApp remote API")
            appendLine("GET /?paso=0")
            appendLine("GET /health")
            appendLine("GET|POST /navigate?url=https://example.com")
            appendLine("GET|POST /execute?script=document.title")
            appendLine("GET|POST /dbping (prueba SOAP)")
            appendLine("GET|POST /run?paso=1&clientToken=...")
            appendLine("Opcional /run: soapUrl, base, soapToken")
            appendLine("""POST /run JSON: {"paso":"1","clientToken":"...","queryParams":{"K":"V"}}""")
            appendLine("Auth: opcional (si se envia key, debe ser valida)")
        }
    }

    companion object {
        private val RESERVED_KEYS = setOf(
            "key",
            "x-api-key",
            "paso",
            "clienttoken",
            "soapurl",
            "base",
            "soaptoken",
            "script",
            "url",
            "queryparams"
        )
    }
}

data class RunFlowRequest(
    val paso: String,
    val clientToken: String,
    val soapUrl: String,
    val base: String,
    val soapToken: String,
    val queryParams: Map<String, String>,
    val bodyJson: String = ""
)
