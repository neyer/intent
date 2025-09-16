package com.intentevolved

import com.intentevolved.com.intentevolved.intent.Intent
import java.io.File
import java.io.FileOutputStream

//TIP To <b>Run</b> code, press <shortcut actionId="Run"/> or
// click the <icon src="AllIcons.Actions.Execute"/> icon in the gutter.
fun main() {




    val streamBuilder = IntentStream.newBuilder()

    var nextId = 0L
    var exit = false

    while (!exit) {
        println("Enter intent.")
        val text = readLine();
        if (text == "exit") {
            exit = true
            continue
        }
        else if (text == "add") {
            val op = Op.newBuilder()

            val createBuilder = op.createIntentBuilder
            println("Enter Intent: ")
            createBuilder.setText(readlnOrNull())
            createBuilder.setId(nextId)
            op.setCreateIntent(createBuilder.build())
            streamBuilder.addOps(op)
        } else if (text == "edit") {
            println("Enter Id: ")
        }
        ++nextId
    }

    val intentStream = streamBuilder.build()
    println(intentStream.toString())
    val file = File("intent_stream.bin")
    FileOutputStream(file).use { output ->
        intentStream.writeTo(output)
    }

    println("IntentStream written to ${file.absolutePath}")

}