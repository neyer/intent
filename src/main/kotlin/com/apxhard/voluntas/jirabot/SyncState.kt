package com.apxhard.voluntas.jirabot

import com.google.gson.Gson
import java.io.File

/**
 * Persists which intents have already been synced to Jira so the bot can
 * run incrementally: only new intents are touched on each pass.
 *
 * issues          : intentId (string) → Jira issue key ("PROJ-42")
 *                   for intents created as actual issues
 * commentAncestors: intentId (string) → nearest ancestor Jira issue key
 *                   for intents that were too deep and became comments;
 *                   stored so we can route NEW children of those intents
 *                   to the same ancestor issue as additional comments
 *
 * JSON keys are always strings; Long IDs are stored as decimal strings.
 */
data class SyncState(
    val issues: MutableMap<String, String> = mutableMapOf(),
    val commentAncestors: MutableMap<String, String> = mutableMapOf()
) {
    fun getIssueKey(intentId: Long): String? = issues[intentId.toString()]
    fun getCommentAncestor(intentId: Long): String? = commentAncestors[intentId.toString()]

    fun recordIssue(intentId: Long, jiraKey: String) {
        issues[intentId.toString()] = jiraKey
    }

    fun recordComment(intentId: Long, ancestorKey: String) {
        commentAncestors[intentId.toString()] = ancestorKey
    }

    fun isProcessed(intentId: Long): Boolean {
        val s = intentId.toString()
        return issues.containsKey(s) || commentAncestors.containsKey(s)
    }

    fun save(file: File) {
        file.writeText(gson.toJson(this))
    }

    companion object {
        private val gson = Gson()

        fun load(file: File): SyncState =
            if (file.exists()) gson.fromJson(file.readText(), SyncState::class.java)
            else SyncState()
    }
}
