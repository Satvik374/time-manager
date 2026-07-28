package com.kairo.timemanager.data.repo

import com.kairo.timemanager.ai.AiMessage
import com.kairo.timemanager.ai.ToolCall
import com.kairo.timemanager.data.local.dao.ChatDao
import com.kairo.timemanager.data.local.entity.ChatMessageEntity
import com.kairo.timemanager.data.local.entity.ChatRole
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.add
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ChatRepository @Inject constructor(
    private val dao: ChatDao,
    private val json: Json
) {

    fun observeMessages(): Flow<List<ChatMessageEntity>> = dao.observeAll()

    suspend fun appendUser(text: String): Long =
        dao.insert(ChatMessageEntity(role = ChatRole.USER, content = text))

    suspend fun appendAssistant(text: String, toolCalls: List<ToolCall>): Long =
        dao.insert(
            ChatMessageEntity(
                role = ChatRole.ASSISTANT,
                content = text,
                toolCallsJson = toolCalls.takeIf { it.isNotEmpty() }?.let { encodeToolCalls(it) }
            )
        )

    suspend fun appendToolResult(call: ToolCall, resultJson: String, succeeded: Boolean): Long =
        dao.insert(
            ChatMessageEntity(
                role = ChatRole.TOOL,
                content = resultJson,
                toolCallId = call.id,
                toolName = call.name,
                isError = !succeeded
            )
        )

    suspend fun appendError(text: String): Long =
        dao.insert(ChatMessageEntity(role = ChatRole.ASSISTANT, content = text, isError = true))

    suspend fun clear() = dao.clear()

    /**
     * Rebuilds the provider-facing history. Tool results are only meaningful next to the
     * assistant turn that requested them, so the window is trimmed to a boundary where every
     * retained tool result still has its matching assistant call.
     */
    suspend fun buildHistory(maxMessages: Int): List<AiMessage> {
        val recent = dao.recent(maxMessages.coerceIn(4, 200)).reversed()
        val trimmed = trimToValidStart(recent)
        return trimmed.map { entity ->
            AiMessage(
                role = entity.role,
                content = entity.content,
                toolCalls = entity.toolCallsJson?.let { decodeToolCalls(it) } ?: emptyList(),
                toolCallId = entity.toolCallId,
                toolName = entity.toolName
            )
        }
    }

    private fun trimToValidStart(messages: List<ChatMessageEntity>): List<ChatMessageEntity> {
        val pendingIds = HashSet<String>()
        var startIndex = 0
        for ((index, message) in messages.withIndex()) {
            when (message.role) {
                ChatRole.ASSISTANT -> message.toolCallsJson?.let { raw ->
                    decodeToolCalls(raw).forEach { pendingIds += it.id }
                }
                ChatRole.TOOL -> {
                    // An orphaned tool result would confuse the provider — drop everything up to here.
                    if (message.toolCallId !in pendingIds) startIndex = index + 1
                }
                else -> Unit
            }
        }
        return if (startIndex == 0) messages else messages.drop(startIndex)
    }

    private fun encodeToolCalls(calls: List<ToolCall>): String = buildJsonArray {
        calls.forEach { call ->
            addJsonObject {
                put("id", call.id)
                put("name", call.name)
                put("arguments", call.argumentsJson)
            }
        }
    }.toString()

    fun decodeToolCalls(raw: String): List<ToolCall> = runCatching {
        (json.parseToJsonElement(raw) as JsonArray).map { element ->
            val obj = element.jsonObject
            ToolCall(
                id = obj["id"]?.jsonPrimitive?.contentOrNull.orEmpty(),
                name = obj["name"]?.jsonPrimitive?.contentOrNull.orEmpty(),
                argumentsJson = obj["arguments"]?.jsonPrimitive?.contentOrNull ?: "{}"
            )
        }
    }.getOrDefault(emptyList())
}
