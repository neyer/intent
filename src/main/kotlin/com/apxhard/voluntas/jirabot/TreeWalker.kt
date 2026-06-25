package com.apxhard.voluntas.jirabot

import com.apxhard.voluntas.worker.WorkerGrpcClient
import voluntas.v1.IntentProto

data class IntentNode(
    val intent: IntentProto,
    val depth: Int,
    val children: MutableList<IntentNode> = mutableListOf()
)

class TreeWalker(private val client: WorkerGrpcClient) {

    /**
     * Walk the intent tree rooted at [rootId] and return the top-level nodes
     * (direct non-meta children of root) with their full subtrees attached.
     * depth 1 = direct children of rootId, depth 2 = grandchildren, etc.
     */
    fun buildTree(rootId: Long, skipDone: Boolean = false): List<IntentNode> {
        val scope = client.getFocalScope(rootId) ?: return emptyList()
        return scope.childrenList
            .filter { keepIntent(it, skipDone) }
            .map { child ->
                val node = IntentNode(child, 1)
                loadChildren(node, depth = 2, skipDone = skipDone)
                node
            }
    }

    private fun loadChildren(parent: IntentNode, depth: Int, skipDone: Boolean) {
        val scope = client.getFocalScope(parent.intent.id) ?: return
        scope.childrenList
            .filter { keepIntent(it, skipDone) }
            .forEach { child ->
                val node = IntentNode(child, depth)
                loadChildren(node, depth + 1, skipDone)
                parent.children += node
            }
    }

    private fun keepIntent(intent: IntentProto, skipDone: Boolean): Boolean {
        if (intent.isMeta) return false
        if (skipDone && intent.fieldValuesMap["done"]?.boolValue == true) return false
        return true
    }

    fun countNodes(nodes: List<IntentNode>): Int =
        nodes.sumOf { 1 + countNodes(it.children) }
}
