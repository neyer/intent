package com.intentevolved.com.intentevolved


// represents a point of focus an in intent graph
// along with some immediately relevant intents
data class FocalScope (
    val focus: Intent,
    val ancestry: List<Intent>,
    val children: List<Intent>
)