package com.apxhard.voluntas.jirabot

/**
 * Configuration for the Jira bot.
 * Load from JSON with Gson: Gson().fromJson(File("jira-bot.json").readText(), JiraConfig::class.java)
 *
 * depthToIssueType maps tree depth (as string key) to Jira issue type name.
 * Depth 1 = direct children of rootIntentId, depth 2 = grandchildren, etc.
 * Intents deeper than maxIssueDepth are added as comments on the nearest ancestor issue.
 *
 * parentLinkField:
 *   "parent"             → links issues via the standard parent field (team-managed / next-gen, default)
 *
 * epicLinkField (optional):
 *   null                 → use parentLinkField for every depth (default)
 *   "customfield_10014"  → use this custom field when linking depth-2 issues to their Epic parent
 *                          (needed for classic / company-managed Jira projects)
 *   Run ./tools/jira-bot.sh --list-fields to discover custom field names for your project.
 */
data class JiraConfig(
    val serverAddress: String = "localhost:50051",
    val voluntasUser: String = "",
    val voluntasToken: String = "",
    val jiraBaseUrl: String = "",
    val jiraUserEmail: String = "",
    val jiraApiToken: String = "",
    val jiraProjectKey: String = "",
    val maxIssueDepth: Int = 3,
    val depthToIssueType: Map<String, String> = mapOf(
        "1" to "Epic",
        "2" to "Story",
        "3" to "Sub-task"
    ),
    val parentLinkField: String = "parent",
    val epicLinkField: String? = null,
    val rootIntentId: Long = 0,
    val skipDone: Boolean = false,
    val pollIntervalSeconds: Int = 30
) {
    fun issueTypeForDepth(depth: Int): String? = depthToIssueType[depth.toString()]

    /** Returns the field name to use when linking a child at [childDepth] to its parent issue. */
    fun parentFieldFor(childDepth: Int): String =
        if (childDepth == 2 && epicLinkField != null) epicLinkField else parentLinkField

    fun validate(): List<String> {
        val errors = mutableListOf<String>()
        if (jiraBaseUrl.isBlank())    errors += "jiraBaseUrl is required"
        if (jiraUserEmail.isBlank())  errors += "jiraUserEmail is required"
        if (jiraApiToken.isBlank())   errors += "jiraApiToken is required"
        if (jiraProjectKey.isBlank()) errors += "jiraProjectKey is required"
        return errors
    }
}
