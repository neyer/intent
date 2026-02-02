package com.intentevolved.worker

import com.intentevolved.IntentProto

/**
 * Intent 4: Implement intent and sub-intent fetching logic.
 */
class IntentFetcher(private val grpcClient: WorkerGrpcClient) {

    /**
     * Intent 28: Fetch the target intent and all descendants.
     * Intent 31: Flatten tree into ordered list for sequential execution.
     * Intent 33: Sort sub-intents by their ID for consistent execution order.
     */
    fun fetchIntentTree(intentId: Long): List<IntentProto> {
        val result = mutableListOf<IntentProto>()
        fetchRecursive(intentId, result)
        // Intent 33: Sort by ID for consistent order
        return result.sortedBy { it.id }
    }

    /**
     * Intent 29: Use GetFocalScope RPC to get immediate children.
     * Intent 30: Recursively fetch children to build complete sub-intent tree.
     */
    private fun fetchRecursive(intentId: Long, result: MutableList<IntentProto>) {
        val scope = grpcClient.getFocalScope(intentId) ?: return

        // Add the focus intent itself (but not the root if it's id 0)
        if (intentId > 0) {
            result.add(scope.focus)
        }

        // Recursively fetch all children
        for (child in scope.childrenList) {
            fetchRecursive(child.id, result)
        }
    }

    /**
     * Intent 32: Filter out already-completed intents (where done=true).
     */
    fun filterIncomplete(intents: List<IntentProto>): List<IntentProto> {
        return intents.filter { intent ->
            val doneValue = intent.fieldValuesMap["done"]
            doneValue?.boolValue != true
        }
    }

    /**
     * Get just the leaf intents (those without children) for execution.
     */
    fun getLeafIntents(intentId: Long): List<IntentProto> {
        val allIntents = fetchIntentTree(intentId)
        val parentIds = allIntents.map { it.parentId }.toSet()
        return allIntents.filter { it.id !in parentIds }
    }
}
