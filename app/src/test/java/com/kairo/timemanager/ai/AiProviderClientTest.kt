package com.kairo.timemanager.ai

import com.kairo.timemanager.data.prefs.AiConfig
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AiProviderClientTest {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }
    private val client = AiProviderClient(OkHttpClient(), json)

    private fun config(
        format: ProviderFormat = ProviderFormat.OPENAI,
        baseUrl: String = "https://provider.example/v1"
    ) = AiConfig(
        baseUrl = baseUrl,
        apiKey = "secret",
        model = "some-model",
        format = format,
        systemPrompt = "You are Kairo.",
        temperature = 0.5f,
        maxTokens = 512
    )

    private fun parse(raw: String): JsonObject = json.parseToJsonElement(raw).jsonObject

    // ---- endpoints -------------------------------------------------------

    @Test
    fun `endpoint appends the format suffix`() {
        assertEquals(
            "https://provider.example/v1/chat/completions",
            client.endpointFor(config())
        )
        assertEquals(
            "https://provider.example/v1/messages",
            client.endpointFor(config(ProviderFormat.ANTHROPIC))
        )
    }

    @Test
    fun `endpoint tolerates a trailing slash and a preset full path`() {
        assertEquals(
            "https://provider.example/v1/chat/completions",
            client.endpointFor(config(baseUrl = "https://provider.example/v1/"))
        )
        assertEquals(
            "https://provider.example/v1/chat/completions",
            client.endpointFor(config(baseUrl = "https://provider.example/v1/chat/completions"))
        )
    }

    @Test
    fun `unknown format keys fall back to the OpenAI dialect`() {
        assertEquals(ProviderFormat.OPENAI, ProviderFormat.fromKey(null))
        assertEquals(ProviderFormat.OPENAI, ProviderFormat.fromKey("nonsense"))
        assertEquals(ProviderFormat.ANTHROPIC, ProviderFormat.fromKey("anthropic"))
    }

    // ---- OpenAI dialect --------------------------------------------------

    @Test
    fun `openai request carries the system prompt first and the tools list`() {
        val body = client.buildOpenAiBody(
            config(),
            listOf(AiMessage(role = "user", content = "hi")),
            AiTools.all.take(2)
        )
        val messages = body["messages"]!!.jsonArray
        assertEquals("system", messages[0].jsonObject["role"]!!.jsonPrimitive.content)
        assertEquals("You are Kairo.", messages[0].jsonObject["content"]!!.jsonPrimitive.content)
        assertEquals("user", messages[1].jsonObject["role"]!!.jsonPrimitive.content)

        val tools = body["tools"]!!.jsonArray
        assertEquals(2, tools.size)
        assertEquals("function", tools[0].jsonObject["type"]!!.jsonPrimitive.content)
        assertEquals(
            AiTools.all[0].name,
            tools[0].jsonObject["function"]!!.jsonObject["name"]!!.jsonPrimitive.content
        )
        assertEquals("auto", body["tool_choice"]!!.jsonPrimitive.content)
    }

    @Test
    fun `openai request replays assistant tool calls and their results`() {
        val call = ToolCall("call_1", AiTools.CREATE_TASK, """{"title":"Gym"}""")
        val body = client.buildOpenAiBody(
            config(),
            listOf(
                AiMessage(role = "user", content = "add gym"),
                AiMessage(role = "assistant", content = "", toolCalls = listOf(call)),
                AiMessage(role = "tool", content = """{"ok":true}""", toolCallId = "call_1")
            ),
            emptyList()
        )
        val messages = body["messages"]!!.jsonArray
        val assistant = messages[2].jsonObject
        val emitted = assistant["tool_calls"]!!.jsonArray[0].jsonObject
        assertEquals("call_1", emitted["id"]!!.jsonPrimitive.content)
        assertEquals(
            """{"title":"Gym"}""",
            emitted["function"]!!.jsonObject["arguments"]!!.jsonPrimitive.content
        )

        val toolResult = messages[3].jsonObject
        assertEquals("tool", toolResult["role"]!!.jsonPrimitive.content)
        assertEquals("call_1", toolResult["tool_call_id"]!!.jsonPrimitive.content)
    }

    @Test
    fun `openai response yields plain text when no tools are requested`() {
        val turn = client.parseOpenAi(
            parse(
                """
                {"choices":[{"finish_reason":"stop",
                  "message":{"role":"assistant","content":"Moved Gym to 7:00 PM."}}]}
                """
            )
        )
        assertEquals("Moved Gym to 7:00 PM.", turn.text)
        assertFalse(turn.wantsTools)
        assertEquals("stop", turn.finishReason)
    }

    @Test
    fun `openai response yields tool calls with their raw arguments`() {
        val turn = client.parseOpenAi(
            parse(
                """
                {"choices":[{"finish_reason":"tool_calls","message":{
                  "role":"assistant","content":null,
                  "tool_calls":[
                    {"id":"call_a","type":"function",
                     "function":{"name":"get_schedule","arguments":"{\"days\":3}"}},
                    {"id":"call_b","type":"function",
                     "function":{"name":"get_progress","arguments":"{}"}}]}}]}
                """
            )
        )
        assertTrue(turn.wantsTools)
        assertEquals(2, turn.toolCalls.size)
        assertEquals("get_schedule", turn.toolCalls[0].name)
        assertEquals("""{"days":3}""", turn.toolCalls[0].argumentsJson)
        assertEquals("call_b", turn.toolCalls[1].id)
        assertEquals("", turn.text)
    }

    @Test
    fun `openai parser survives providers that send content as an array of parts`() {
        val turn = client.parseOpenAi(
            parse(
                """
                {"choices":[{"message":{"role":"assistant",
                  "content":[{"type":"text","text":"line one"},{"type":"text","text":"line two"}]}}]}
                """
            )
        )
        assertEquals("line one\nline two", turn.text)
    }

    @Test
    fun `openai parser defaults empty arguments to an empty object`() {
        val turn = client.parseOpenAi(
            parse(
                """
                {"choices":[{"message":{"role":"assistant","content":"",
                  "tool_calls":[{"id":"x","function":{"name":"list_rewards","arguments":""}}]}}]}
                """
            )
        )
        assertEquals("{}", turn.toolCalls.single().argumentsJson)
    }

    // ---- Anthropic dialect -----------------------------------------------

    @Test
    fun `anthropic request hoists the system prompt out of the message list`() {
        val body = client.buildAnthropicBody(
            config(ProviderFormat.ANTHROPIC),
            listOf(AiMessage(role = "user", content = "hi")),
            AiTools.all.take(1)
        )
        assertEquals("You are Kairo.", body["system"]!!.jsonPrimitive.content)
        val messages = body["messages"]!!.jsonArray
        assertEquals(1, messages.size)
        assertEquals("user", messages[0].jsonObject["role"]!!.jsonPrimitive.content)

        val tool = body["tools"]!!.jsonArray[0].jsonObject
        assertEquals(AiTools.all[0].name, tool["name"]!!.jsonPrimitive.content)
        // Anthropic names the schema field differently from the OpenAI dialect.
        assertTrue(tool.containsKey("input_schema"))
        assertNull(tool["parameters"])
    }

    @Test
    fun `anthropic request merges consecutive tool results into a single user turn`() {
        val body = client.buildAnthropicBody(
            config(ProviderFormat.ANTHROPIC),
            listOf(
                AiMessage(role = "user", content = "plan my week"),
                AiMessage(
                    role = "assistant",
                    content = "Looking now.",
                    toolCalls = listOf(
                        ToolCall("t1", AiTools.GET_SCHEDULE, """{"days":7}"""),
                        ToolCall("t2", AiTools.GET_ANALYTICS, """{"period":"weekly"}""")
                    )
                ),
                AiMessage(role = "tool", content = """{"a":1}""", toolCallId = "t1"),
                AiMessage(role = "tool", content = """{"b":2}""", toolCallId = "t2")
            ),
            emptyList()
        )
        val messages = body["messages"]!!.jsonArray
        assertEquals(3, messages.size)

        val assistant = messages[1].jsonObject
        assertEquals("assistant", assistant["role"]!!.jsonPrimitive.content)
        val blocks = assistant["content"]!!.jsonArray
        assertEquals("text", blocks[0].jsonObject["type"]!!.jsonPrimitive.content)
        assertEquals("tool_use", blocks[1].jsonObject["type"]!!.jsonPrimitive.content)
        // Arguments must arrive as a real object, not a JSON-encoded string.
        assertEquals(7, blocks[1].jsonObject["input"]!!.jsonObject["days"]!!.jsonPrimitive.content.toInt())

        val results = messages[2].jsonObject
        assertEquals("user", results["role"]!!.jsonPrimitive.content)
        val resultBlocks = results["content"]!!.jsonArray
        assertEquals(2, resultBlocks.size)
        assertEquals("t1", resultBlocks[0].jsonObject["tool_use_id"]!!.jsonPrimitive.content)
        assertEquals("t2", resultBlocks[1].jsonObject["tool_use_id"]!!.jsonPrimitive.content)
    }

    @Test
    fun `anthropic request tolerates unparseable tool arguments`() {
        val body = client.buildAnthropicBody(
            config(ProviderFormat.ANTHROPIC),
            listOf(
                AiMessage(
                    role = "assistant",
                    content = "",
                    toolCalls = listOf(ToolCall("t1", AiTools.GET_PROGRESS, "not json at all"))
                )
            ),
            emptyList()
        )
        val block = body["messages"]!!.jsonArray[0].jsonObject["content"]!!.jsonArray[0].jsonObject
        assertEquals(0, block["input"]!!.jsonObject.size)
    }

    @Test
    fun `anthropic response splits text and tool use blocks`() {
        val turn = client.parseAnthropic(
            parse(
                """
                {"stop_reason":"tool_use","content":[
                  {"type":"text","text":"Checking your week."},
                  {"type":"tool_use","id":"tu_1","name":"get_analytics","input":{"period":"weekly"}}]}
                """
            )
        )
        assertEquals("Checking your week.", turn.text)
        assertEquals("tool_use", turn.finishReason)
        assertEquals(1, turn.toolCalls.size)
        assertEquals("tu_1", turn.toolCalls[0].id)
        assertEquals("get_analytics", turn.toolCalls[0].name)
        assertTrue(turn.toolCalls[0].argumentsJson.contains("weekly"))
    }

    @Test
    fun `anthropic response with only text requests no tools`() {
        val turn = client.parseAnthropic(
            parse("""{"stop_reason":"end_turn","content":[{"type":"text","text":"All done."}]}""")
        )
        assertEquals("All done.", turn.text)
        assertFalse(turn.wantsTools)
    }

    // ---- errors ----------------------------------------------------------

    @Test
    fun `http errors explain themselves in terms the user can act on`() {
        assertTrue(AiError.Http(401, "").readable().contains("API key"))
        assertTrue(AiError.Http(404, "").readable().contains("base URL"))
        assertTrue(AiError.Http(429, "").readable().contains("Rate limited"))
        assertTrue(AiError.NotConfigured.readable().contains("Settings"))
    }

    @Test
    fun `an unconfigured provider fails without attempting a request`() = kotlinx.coroutines.runBlocking {
        val result = client.complete(AiConfig(), emptyList(), emptyList())
        assertTrue(result.isFailure)
        assertEquals(AiError.NotConfigured, result.exceptionOrNull()!!.asAiError())
    }
}
