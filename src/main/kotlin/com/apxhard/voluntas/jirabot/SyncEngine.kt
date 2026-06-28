package com.apxhard.voluntas.jirabot

import com.apxhard.voluntas.worker.WorkerGrpcClient
import voluntas.v1.FieldType
import voluntas.v1.FieldValueProto

class SyncEngine(
    private val config: JiraConfig,
    private val jira: JiraClient,
    private val walker: TreeWalker,
    private val grpc: WorkerGrpcClient
) {
    private data class PassResult(val issues: Int, val comments: Int)

    /**
     * Walk the full intent tree and sync anything not yet in [state].
     * Safe to call repeatedly — already-synced intents are skipped.
     * Returns counts of new items created this pass.
     */
    fun syncOnce(): SyncResult {
        val topLevel = walker.buildTree(config.rootIntentId, config.skipDone)
        var issues = 0
        var comments = 0
        for (node in topLevel) {
            val r = processNode(node, nearestIssueKey = null)
            issues += r.issues
            comments += r.comments
        }
        return SyncResult(issues, comments)
    }

    private fun processNode(node: IntentNode, nearestIssueKey: String?): PassResult {
        val existingIssueKey = node.intent.fieldValuesMap["jira_key"]?.stringValue?.takeIf { it.isNotBlank() }
        val existingCommentAncestor = node.intent.fieldValuesMap["jira_comment_ancestor"]?.stringValue?.takeIf { it.isNotBlank() }

        // Already synced as an issue — just pass its key to children
        if (existingIssueKey != null) {
            return recurseChildren(node.children, existingIssueKey)
        }

        // Already synced as a comment — children route to the same ancestor issue
        if (existingCommentAncestor != null) {
            return recurseChildren(node.children, existingCommentAncestor)
        }

        // New intent — determine what to create
        val issueType = config.issueTypeForDepth(node.depth)

        return if (issueType != null) {
            val key = createIssue(node, issueType, nearestIssueKey)
            recordIssue(node.intent.id, key)
            val childResult = recurseChildren(node.children, key)
            PassResult(1 + childResult.issues, childResult.comments)
        } else if (nearestIssueKey != null) {
            val text = buildSubtreeText(node, indent = 0)
            val indent = "  ".repeat(config.maxIssueDepth)
            print("${indent}[comment on $nearestIssueKey] ${node.intent.text.take(60)}")
            jira.addComment(nearestIssueKey, text)
            println(" → done")
            Thread.sleep(150)
            recordComment(node.intent.id, nearestIssueKey)
            // Children captured in the comment text; mark them so future passes
            // route any new grandchildren to the same ancestor issue.
            markSubtreeAsCommented(node.children, nearestIssueKey)
            PassResult(0, 1)
        } else {
            PassResult(0, 0)
        }
    }

    private fun recurseChildren(children: List<IntentNode>, issueKey: String?): PassResult {
        var issues = 0
        var comments = 0
        for (child in children) {
            val r = processNode(child, issueKey)
            issues += r.issues
            comments += r.comments
        }
        return PassResult(issues, comments)
    }

    private fun markSubtreeAsCommented(nodes: List<IntentNode>, ancestorKey: String) {
        for (node in nodes) {
            val alreadyProcessed = node.intent.fieldValuesMap["jira_key"]?.stringValue?.isNotBlank() == true ||
                node.intent.fieldValuesMap["jira_comment_ancestor"]?.stringValue?.isNotBlank() == true
            if (!alreadyProcessed) {
                recordComment(node.intent.id, ancestorKey)
            }
            markSubtreeAsCommented(node.children, ancestorKey)
        }
    }

    private fun recordIssue(intentId: Long, jiraKey: String) {
        grpc.addField(intentId, "jira_key", FieldType.FIELD_TYPE_STRING)
        grpc.setStringField(intentId, "jira_key", jiraKey)
    }

    private fun recordComment(intentId: Long, ancestorKey: String) {
        grpc.addField(intentId, "jira_comment_ancestor", FieldType.FIELD_TYPE_STRING)
        grpc.setStringField(intentId, "jira_comment_ancestor", ancestorKey)
    }

    private fun createIssue(node: IntentNode, issueType: String, parentKey: String?): String {
        val fieldNotes = formatFieldValues(node)
        val indent = "  ".repeat(node.depth - 1)
        val linkField = config.parentFieldFor(node.depth)
        print("${indent}[$issueType] ${node.intent.text.take(70)}")

        // Attempt 1: preferred link field for this depth
        try {
            val key = jira.createIssue(
                summary = node.intent.text,
                issueType = issueType,
                parentKey = parentKey,
                parentLinkField = linkField,
                intentId = node.intent.id,
                fieldNotes = fieldNotes
            )
            println(" → $key")
            Thread.sleep(150)
            return key
        } catch (e: Exception) {
            if (parentKey == null) throw e
            println("\n${indent}  [warn: parent link via '$linkField' failed]")
            println("${indent}  ${e.message}")
        }

        // Attempt 2: if epicLinkField wasn't already tried, try the other field
        val altField = if (linkField == config.parentLinkField) config.epicLinkField else config.parentLinkField
        if (altField != null && altField != linkField) {
            print("${indent}  retrying with '$altField'...")
            try {
                val key = jira.createIssue(
                    summary = node.intent.text,
                    issueType = issueType,
                    parentKey = parentKey,
                    parentLinkField = altField,
                    intentId = node.intent.id,
                    fieldNotes = fieldNotes
                )
                println(" → $key")
                Thread.sleep(150)
                return key
            } catch (e: Exception) {
                println("\n${indent}  [warn: also failed with '$altField']")
                println("${indent}  ${e.message}")
            }
        }

        // Attempt 3: create without parent (orphan) rather than drop the intent entirely
        print("${indent}  creating without parent link (set epicLinkField in config to fix)...")
        val key = jira.createIssue(
            summary = node.intent.text,
            issueType = issueType,
            parentKey = null,
            parentLinkField = config.parentLinkField,
            intentId = node.intent.id,
            fieldNotes = fieldNotes
        )
        println(" → $key (orphan)")
        Thread.sleep(150)
        return key
    }

    private fun buildSubtreeText(node: IntentNode, indent: Int): String {
        val prefix = "  ".repeat(indent)
        val fields = formatFieldValues(node)
        val sb = StringBuilder("${prefix}- [#${node.intent.id}] ${node.intent.text}")
        if (fields.isNotEmpty()) sb.append("  ($fields)")
        sb.append("\n")
        for (child in node.children) {
            sb.append(buildSubtreeText(child, indent + 1))
        }
        return sb.toString()
    }

    private fun formatFieldValues(node: IntentNode): String =
        node.intent.fieldValuesMap.entries
            .filter { it.key !in setOf("jira_key", "jira_comment_ancestor") }
            .joinToString(", ") { (k, v) -> "$k=${v.displayString()}" }

    private fun FieldValueProto.displayString(): String = when (valueCase) {
        FieldValueProto.ValueCase.STRING_VALUE     -> stringValue
        FieldValueProto.ValueCase.BOOL_VALUE       -> boolValue.toString()
        FieldValueProto.ValueCase.INT64_VALUE      -> int64Value.toString()
        FieldValueProto.ValueCase.INT32_VALUE      -> int32Value.toString()
        FieldValueProto.ValueCase.FLOAT_VALUE      -> floatValue.toString()
        FieldValueProto.ValueCase.DOUBLE_VALUE     -> doubleValue.toString()
        FieldValueProto.ValueCase.INTENT_REF_VALUE -> "ref:$intentRefValue"
        FieldValueProto.ValueCase.TIMESTAMP_VALUE  -> "ts:$timestampValue"
        else                                       -> "?"
    }
}

data class SyncResult(val issuesCreated: Int, val commentsAdded: Int) {
    val hasChanges get() = issuesCreated > 0 || commentsAdded > 0
    override fun toString() = "$issuesCreated issue(s), $commentsAdded comment(s)"
}
