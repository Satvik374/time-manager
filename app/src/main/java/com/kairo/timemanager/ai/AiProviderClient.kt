package com.kairo.timemanager.ai

import com.kairo.timemanager.data.prefs.AiConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.add
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.inject.Inject
import javax.inject.Singleton

/** Wire dialect of the configured provider. Both are widely implemented by self-hosted gateways. */
enum class ProviderFormat(val key: String, val label: String, val endpointSuffix: String) {
    OPENAI("openai", "OpenAI-compatible (/chat/completions)", "chat/completions"),
    ANTHROPIC("anthropic", "Anthropic Messages (/messages)", "messages");

    companion object {
        fun fromKey(key: String?): ProviderFormat =
            entries.firstOrNull { it.key == key } ?: OPENAI
    }
}

data class ToolCall(
    val id: String,
    val name: String,
    val argumentsJson: String
)

/** Provider-neutral conversation entry. */
data class AiMessage(
    val role: String,
    val content: String = "",
    val toolCalls: List<ToolCall> = emptyList(),
    val toolCallId: String? = null,
    val toolName: String? = null
)

data class AiTurn(
    val text: String,
    val toolCalls: List<ToolCall>,
    val finishReason: String?
) {
    val wantsTools: Boolean get() = toolCalls.isNotEmpty()
}

sealed interface AiError {
    data class Network(val message: String) : AiError
    data class Http(val code: Int, val body: String) : AiError
    data class Malformed(val message: String) : AiError
    data object NotConfigured : AiError

    fun readable(): String = when (this) {
        is Network -> "Couldn't reach the provider: $message"
        is Http -> when (code) {
            401, 403 -> "Provider rejected the API key (HTTP $code). Check the key in Settings."
            404 -> "Endpoint not found (HTTP 404). Check the base URL and wire format in Settings."
            429 -> "Rate limited by the provider (HTTP 429). Try again in a moment."
            in 500..599 -> "Provider error (HTTP $code). Try again shortly."
            else -> "Provider returned HTTP $code: ${body.take(300)}"
        }
        is Malformed -> "Couldn't read the provider's response: $message"
        NotConfigured -> "Add a base URL, API key and model in Settings → AI Coach first."
    }
}

@Singleton
class AiProviderClient @Inject constructor(
    private val httpClient: OkHttpClient,
    private val json: Json
) {

    private val mediaType = "application/json; charset=utf-8".toMediaType()

    suspend fun complete(
        config: AiConfig,
        messages: List<AiMessage>,
        tools: List<ToolSpec>
    ): Result<AiTurn> = withContext(Dispatchers.IO) {
        if (!config.isConfigured) {
            return@withContext Result.failure(AiException(AiError.NotConfigured))
        }
        val body = when (config.format) {
            ProviderFormat.OPENAI -> buildOpenAiBody(config, messages, tools)
            ProviderFormat.ANTHROPIC -> buildAnthropicBody(config, messages, tools)
        }
        val request = Request.Builder()
            .url(endpointFor(config))
            .post(body.toString().toRequestBody(mediaType))
            .apply {
                header("Content-Type", "application/json")
                header("Accept", "application/json")
                when (config.format) {
                    ProviderFormat.OPENAI ->
                        if (config.apiKey.isNotBlank()) header("Authorization", "Bearer ${config.apiKey}")
                    ProviderFormat.ANTHROPIC -> {
                        if (config.apiKey.isNotBlank()) header("x-api-key", config.apiKey)
                        header("anthropic-version", ANTHROPIC_VERSION)
                    }
                }
            }
            .build()

        try {
            httpClient.newCall(request).execute().use { response ->
                val raw = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    return@withContext Result.failure(AiException(AiError.Http(response.code, raw)))
                }
                val parsed = runCatching {
                    val root = json.parseToJsonElement(raw).jsonObject
                    when (config.format) {
                        ProviderFormat.OPENAI -> parseOpenAi(root)
                        ProviderFormat.ANTHROPIC -> parseAnthropic(root)
                    }
                }
                parsed.fold(
                    onSuccess = { Result.success(it) },
                    onFailure = {
                        Result.failure(AiException(AiError.Malformed(it.message ?: "unexpected shape")))
                    }
                )
            }
        } catch (e: UnknownHostException) {
            Result.failure(AiException(AiError.Network("host not found (${e.message})")))
        } catch (e: SocketTimeoutException) {
            Result.failure(AiException(AiError.Network("timed out")))
        } catch (e: IOException) {
            Result.failure(AiException(AiError.Network(e.message ?: "connection failed")))
        }
    }

    /** Lightweight round-trip used by the "Test connection" button in Settings. */
    suspend fun testConnection(config: AiConfig): Result<String> {
        val probe = listOf(
            AiMessage(role = "user", content = "Reply with exactly: OK")
        )
        return complete(config.copy(systemPrompt = "You are a connectivity probe.", maxTokens = 32), probe, emptyList())
            .map { it.text.ifBlank { "(empty response, but the endpoint answered)" } }
    }

    internal fun endpointFor(config: AiConfig): String {
        val base = config.baseUrl.trim().trimEnd('/')
        val suffix = config.format.endpointSuffix
        return if (base.endsWith(suffix)) base else "$base/$suffix"
    }

    // ---- OpenAI-compatible ----------------------------------------------

    internal fun buildOpenAiBody(
        config: AiConfig,
        messages: List<AiMessage>,
        tools: List<ToolSpec>
    ): JsonObject = buildJsonObject {
        put("model", config.model)
        put("temperature", config.temperature)
        put("max_tokens", config.maxTokens)
        put("stream", false)
        putJsonArray("messages") {
            add(buildJsonObject {
                put("role", "system")
                put("content", config.systemPrompt)
            })
            messages.forEach { message ->
                add(buildJsonObject {
                    put("role", message.role)
                    if (message.role == "tool") {
                        put("tool_call_id", message.toolCallId ?: "")
                        put("content", message.content)
                    } else {
                        put("content", message.content)
                        if (message.toolCalls.isNotEmpty()) {
                            putJsonArray("tool_calls") {
                                message.toolCalls.forEach { call ->
                                    addJsonObject {
                                        put("id", call.id)
                                        put("type", "function")
                                        putJsonObject("function") {
                                            put("name", call.name)
                                            put("arguments", call.argumentsJson)
                                        }
                                    }
                                }
                            }
                        }
                    }
                })
            }
        }
        if (tools.isNotEmpty()) {
            put("tools", buildJsonArray {
                tools.forEach { tool ->
                    addJsonObject {
                        put("type", "function")
                        putJsonObject("function") {
                            put("name", tool.name)
                            put("description", tool.description)
                            put("parameters", tool.parameters)
                        }
                    }
                }
            })
            put("tool_choice", "auto")
        }
    }

    internal fun parseOpenAi(root: JsonObject): AiTurn {
        val choice = root["choices"]?.jsonArray?.firstOrNull()?.jsonObject
            ?: error("no choices in response")
        val message = choice["message"]?.jsonObject ?: error("no message in choice")
        val text = message["content"]?.let { element ->
            when {
                element is JsonPrimitive -> element.contentOrNull.orEmpty()
                element is JsonArray -> element.mapNotNull {
                    it.jsonObject["text"]?.jsonPrimitive?.contentOrNull
                }.joinToString("\n")
                else -> ""
            }
        }.orEmpty()
        val calls = message["tool_calls"]?.jsonArray.orEmpty().mapIndexedNotNull { index, element ->
            val call = element.jsonObject
            val fn = call["function"]?.jsonObject ?: return@mapIndexedNotNull null
            ToolCall(
                id = call["id"]?.jsonPrimitive?.contentOrNull ?: "call_$index",
                name = fn["name"]?.jsonPrimitive?.contentOrNull ?: return@mapIndexedNotNull null,
                argumentsJson = fn["arguments"]?.let {
                    if (it is JsonPrimitive) it.contentOrNull.orEmpty() else it.toString()
                }.orEmpty().ifBlank { "{}" }
            )
        }
        return AiTurn(
            text = text.trim(),
            toolCalls = calls,
            finishReason = choice["finish_reason"]?.jsonPrimitive?.contentOrNull
        )
    }

    // ---- Anthropic Messages ---------------------------------------------

    internal fun buildAnthropicBody(
        config: AiConfig,
        messages: List<AiMessage>,
        tools: List<ToolSpec>
    ): JsonObject = buildJsonObject {
        put("model", config.model)
        put("max_tokens", config.maxTokens)
        put("temperature", config.temperature)
        put("system", config.systemPrompt)
        put("messages", buildJsonArray {
            // Anthropic requires tool results to travel in a user turn, so consecutive tool
            // results are merged into one block list rather than emitted as separate messages.
            var index = 0
            while (index < messages.size) {
                val message = messages[index]
                when (message.role) {
                    "tool" -> {
                        val batch = mutableListOf<AiMessage>()
                        while (index < messages.size && messages[index].role == "tool") {
                            batch += messages[index]
                            index++
                        }
                        addJsonObject {
                            put("role", "user")
                            putJsonArray("content") {
                                batch.forEach { result ->
                                    addJsonObject {
                                        put("type", "tool_result")
                                        put("tool_use_id", result.toolCallId ?: "")
                                        put("content", result.content)
                                    }
                                }
                            }
                        }
                    }

                    else -> {
                        addJsonObject {
                            put("role", if (message.role == "assistant") "assistant" else "user")
                            putJsonArray("content") {
                                if (message.content.isNotBlank()) {
                                    addJsonObject {
                                        put("type", "text")
                                        put("text", message.content)
                                    }
                                }
                                message.toolCalls.forEach { call ->
                                    addJsonObject {
                                        put("type", "tool_use")
                                        put("id", call.id)
                                        put("name", call.name)
                                        put(
                                            "input",
                                            runCatching {
                                                json.parseToJsonElement(call.argumentsJson).jsonObject
                                            }.getOrElse { buildJsonObject { } }
                                        )
                                    }
                                }
                            }
                        }
                        index++
                    }
                }
            }
        })
        if (tools.isNotEmpty()) {
            put("tools", buildJsonArray {
                tools.forEach { tool ->
                    addJsonObject {
                        put("name", tool.name)
                        put("description", tool.description)
                        put("input_schema", tool.parameters)
                    }
                }
            })
        }
    }

    internal fun parseAnthropic(root: JsonObject): AiTurn {
        val content = root["content"]?.jsonArray ?: error("no content in response")
        val textParts = mutableListOf<String>()
        val calls = mutableListOf<ToolCall>()
        content.forEachIndexed { index, element ->
            val block = element.jsonObject
            when (block["type"]?.jsonPrimitive?.contentOrNull) {
                "text" -> block["text"]?.jsonPrimitive?.contentOrNull?.let { textParts += it }
                "tool_use" -> calls += ToolCall(
                    id = block["id"]?.jsonPrimitive?.contentOrNull ?: "call_$index",
                    name = block["name"]?.jsonPrimitive?.contentOrNull ?: return@forEachIndexed,
                    argumentsJson = (block["input"] ?: buildJsonObject { }).toString()
                )
            }
        }
        return AiTurn(
            text = textParts.joinToString("\n").trim(),
            toolCalls = calls,
            finishReason = root["stop_reason"]?.jsonPrimitive?.contentOrNull
        )
    }

    private companion object {
        const val ANTHROPIC_VERSION = "2023-06-01"
    }
}

class AiException(val error: AiError) : Exception(error.readable())

/** Convenience so call sites can pattern-match without unwrapping the exception every time. */
fun Throwable.asAiError(): AiError =
    (this as? AiException)?.error ?: AiError.Network(message ?: "unknown failure")

private fun JsonArray?.orEmpty(): List<kotlinx.serialization.json.JsonElement> = this ?: emptyList()
