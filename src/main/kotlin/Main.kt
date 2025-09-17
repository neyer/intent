package com.intentevolved

import com.intentevolved.com.intentevolved.Intent
import com.intentevolved.com.intentevolved.IntentServiceImpl
import java.io.File
import java.io.FileOutputStream

//TIP To <b>Run</b> code, press <shortcut actionId="Run"/> or
// click the <icon src="AllIcons.Actions.Execute"/> icon in the gutter.
fun main() {




    val service = IntentServiceImpl()

    var exit = false

    while (!exit) {
        println("Enter Op:")
        val text = readLine();
        if (text == "exit") {
            exit = true
            continue
        }
        else if (text == "add") {
            println("Enter Intent: ")
            service.addIntent(readlnOrNull()!!)
        } else if (text == "update") {
            println("Enter Id: ")
            val id = readln().toLong()
            println("Enter new text")
            val text = readln()
            service.edit(id, text)
        }
    }

    service.print()
    service.writeToFile("intent_stream.bin")
    println("IntentStream written.")

}