package com.example.gemini.data.agent.universal

import android.content.Context
import com.example.gemini.domain.model.ChatMessage
import com.example.gemini.domain.model.Conversation
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * Persists a vendor-neutral handoff snapshot before an agent switch. Credentials and attachment
 * bytes are deliberately excluded. This is task context, not a claim that vendor-internal memory
 * or session IDs can be migrated between agents.
 */
class SharedTaskStateStore(context: Context) {
    private val file = File(File(context.applicationContext.filesDir, "universal-workspace"), "task-handoff.json")

    @Synchronized
    fun capture(conversation: Conversation?, messages: List<ChatMessage>): String? {
        if (conversation == null) {
            file.delete()
            return null
        }
        val root = JSONObject()
            .put("schemaVersion", 1)
            .put("savedAt", System.currentTimeMillis())
            .put("sourceAgent", conversation.agent.name)
            .put("conversationId", conversation.id)
            .put("title", conversation.title)
            .put("workspaceUri", conversation.workspaceUri)
            .put("approvedPlanOrSummary", conversation.summary ?: "")
        val recent = JSONArray()
        messages.takeLast(24).forEach { message ->
            if (message.content.isNotBlank()) {
                recent.put(JSONObject()
                    .put("role", message.role.name)
                    .put("content", message.content.take(6000))
                    .put("modelId", message.modelId ?: ""))
            }
        }
        root.put("recentMessages", recent)
        val toolStates = JSONArray()
        messages.takeLast(12).flatMap { it.toolCalls }.takeLast(40).forEach { tool ->
            toolStates.put(JSONObject()
                .put("name", tool.name)
                .put("status", tool.status)
                .put("command", tool.command.take(1000))
                .put("exitCode", tool.exitCode ?: JSONObject.NULL)
                .put("outputTail", tool.output.takeLast(1800)))
        }
        root.put("recentToolStates", toolStates)
        file.parentFile?.mkdirs()
        val tmp = File(file.parentFile, file.name + ".tmp")
        tmp.writeText(root.toString(2))
        if (file.exists() && !file.delete()) throw IllegalStateException("Could not replace task handoff snapshot")
        if (!tmp.renameTo(file)) throw IllegalStateException("Could not commit task handoff snapshot")
        return toResumePrompt(root)
    }

    @Synchronized
    fun latestResumePrompt(): String? = runCatching {
        if (!file.isFile) return null
        val root = JSONObject(file.readText())
        val savedAt = root.optLong("savedAt", 0L)
        // Avoid unexpectedly injecting an old task into a completely unrelated chat.
        if (savedAt <= 0L || System.currentTimeMillis() - savedAt > 7L * 24 * 60 * 60 * 1000) {
            file.delete()
            return null
        }
        toResumePrompt(root)
    }.getOrNull()

    @Synchronized
    fun clear() { file.delete() }

    private fun toResumePrompt(root: JSONObject): String {
        val messages = root.optJSONArray("recentMessages") ?: JSONArray()
        val history = (0 until messages.length()).joinToString("\n") { i ->
            val m = messages.optJSONObject(i) ?: JSONObject()
            "${m.optString("role")}: ${m.optString("content")}"
        }
        val tools = root.optJSONArray("recentToolStates") ?: JSONArray()
        val toolSummary = (0 until tools.length()).joinToString("\n") { i ->
            val t = tools.optJSONObject(i) ?: JSONObject()
            "- ${t.optString("name")} [${t.optString("status")}] exit=${t.opt("exitCode")}: ${t.optString("outputTail")}"
        }
        return buildString {
            appendLine("[AntiGem shared task handoff — context only]")
            appendLine("Previous agent: ${root.optString("sourceAgent")}")
            appendLine("Task: ${root.optString("title")}")
            appendLine("Workspace: ${root.optString("workspaceUri")}")
            appendLine("Saved plan/summary: ${root.optString("approvedPlanOrSummary")}")
            appendLine("Recent conversation (may be incomplete):")
            appendLine(history.takeLast(18000))
            appendLine("Recent tool/build states (verify against the current files before acting):")
            appendLine(toolSummary.takeLast(7000))
            appendLine("Continue from the current project state. Inspect files and verify unfinished work; do not assume the previous agent's private session or memory transferred.")
        }.take(28000)
    }
}
