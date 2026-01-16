package com.intentevolved.com.intentevolved


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
