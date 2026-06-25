package com.apxhard.voluntas.jirabot

import com.google.gson.Gson
import com.google.gson.JsonObject
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.util.Base64

class JiraClient(private val config: JiraConfig) {
    private val http = HttpClient.newHttpClient()
    private val gson = Gson()
    private val authHeader = "Basic " + Base64.getEncoder().encodeToString(
        "${config.jiraUserEmail}:${config.jiraApiToken}".toByteArray()
    )

    private val base get() = config.jiraBaseUrl.trimEnd('/')

    // ----------------------------------------------------------
    // Public API
    // ----------------------------------------------------------

    fun testConnection(): String {
        val obj = get("/rest/api/3/myself")
        return obj.get("displayName")?.asString ?: obj.get("emailAddress")?.asString ?: "unknown"
    }

    /** Print all custom fields for this Jira instance — useful for finding epicLinkField. */
    fun printCustomFields() {
        val arr = getRaw("/rest/api/3/field")
        for (el in arr.asJsonArray) {
            val f = el.asJsonObject
            val id = f.get("id")?.asString ?: continue
            val name = f.get("name")?.asString ?: ""
            val custom = f.get("custom")?.asBoolean ?: false
            if (custom) println("  $id  →  $name")
        }
    }

    /**
     * Create a Jira issue and return its key (e.g. "PROJ-42").
     * [parentKey] is linked via [parentLinkField]:
     *   "parent"            → standard parent field  {parent: {key: "..."}}
     *   "customfield_10014" → Epic Link field        {customfield_10014: "..."}  (classic projects)
     */
    fun createIssue(
        summary: String,
        issueType: String,
        parentKey: String? = null,
        parentLinkField: String = config.parentLinkField,
        intentId: Long? = null,
        fieldNotes: String = ""
    ): String {
        val fields = mutableMapOf<String, Any>(
            "project"   to mapOf("key" to config.jiraProjectKey),
            "summary"   to summary.take(255),
            "issuetype" to mapOf("name" to issueType)
        )

        if (parentKey != null) {
            // "parent" field takes {key: "..."}, custom fields take the key string directly
            if (parentLinkField == "parent") {
                fields["parent"] = mapOf("key" to parentKey)
            } else {
                fields[parentLinkField] = parentKey
            }
        }

        val descLines = buildList {
            if (intentId != null) add("Intent #$intentId")
            if (fieldNotes.isNotBlank()) add(fieldNotes)
        }
        if (descLines.isNotEmpty()) {
            fields["description"] = adfDoc(descLines.joinToString("\n"))
        }

        val result = post("/rest/api/3/issue", gson.toJson(mapOf("fields" to fields)))
        return result.get("key")?.asString
            ?: throw RuntimeException("No 'key' in create-issue response: $result")
    }

    /**
     * Add a plain-text comment to an issue; returns the comment ID.
     */
    fun addComment(issueKey: String, text: String): String {
        val body = gson.toJson(mapOf("body" to adfDoc(text)))
        val result = post("/rest/api/3/issue/$issueKey/comment", body)
        return result.get("id")?.asString ?: ""
    }

    // ----------------------------------------------------------
    // HTTP helpers
    // ----------------------------------------------------------

    private fun get(path: String): JsonObject = getRaw(path).asJsonObject

    private fun getRaw(path: String): com.google.gson.JsonElement {
        val req = HttpRequest.newBuilder()
            .uri(URI.create("$base$path"))
            .header("Authorization", authHeader)
            .header("Accept", "application/json")
            .GET()
            .build()
        return sendRaw(req)
    }

    private fun post(path: String, jsonBody: String): JsonObject {
        val req = HttpRequest.newBuilder()
            .uri(URI.create("$base$path"))
            .header("Authorization", authHeader)
            .header("Accept", "application/json")
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
            .build()
        return send(req)
    }

    private fun send(req: HttpRequest): JsonObject = sendRaw(req).asJsonObject

    private fun sendRaw(req: HttpRequest): com.google.gson.JsonElement {
        val resp = http.send(req, HttpResponse.BodyHandlers.ofString())
        val body = resp.body() ?: ""
        if (resp.statusCode() !in 200..299) {
            throw RuntimeException("Jira ${resp.statusCode()} ${req.method()} ${req.uri()}\n$body")
        }
        return if (body.isNotBlank()) gson.fromJson(body, com.google.gson.JsonElement::class.java)
               else JsonObject()
    }

    // ----------------------------------------------------------
    // Atlassian Document Format helpers
    // ----------------------------------------------------------

    private fun adfDoc(text: String): Map<String, Any> {
        val paragraphs = text.split("\n").map { line ->
            mapOf(
                "type" to "paragraph",
                "content" to if (line.isNotEmpty())
                    listOf(mapOf("type" to "text", "text" to line))
                else
                    emptyList<Any>()
            )
        }
        return mapOf("type" to "doc", "version" to 1, "content" to paragraphs)
    }
}
