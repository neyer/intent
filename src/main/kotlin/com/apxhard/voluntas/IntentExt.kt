package com.apxhard.voluntas


// gets the list of parent intents
fun Intent.getAncestry(): List<Intent> {

    val parentList = mutableListOf<Intent>()

    var cursor = parent()
    while (cursor != null) {
        parentList.addFirst(cursor)
        cursor = cursor.parent()
    }

    return parentList
}

/**
 * Returns one ancestry chain per direct parent.
 * Each chain is [root, ..., grandparent, directParent].
 * Only follows primary parents (parent()) to avoid path explosion.
 */
fun Intent.getAncestryPaths(): List<List<Intent>> {
    val directParents = parents()
    if (directParents.isEmpty()) return listOf(emptyList())
    return directParents.map { directParent ->
        val path = mutableListOf<Intent>()
        val visited = mutableSetOf<Long>()
        var cursor: Intent? = directParent
        while (cursor != null && cursor.id() !in visited) {
            path.addFirst(cursor)
            visited.add(cursor.id())
            cursor = cursor.parent()
        }
        path
    }
}
