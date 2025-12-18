package com.intentevolved.com.intentevolved.terminal

import com.googlecode.lanterna.input.KeyStroke
import com.googlecode.lanterna.input.KeyType
import com.intentevolved.com.intentevolved.IntentServiceImpl

class InputHandler(
    val service: IntentServiceImpl
) {
    val inputBuffer = StringBuilder()

    var keepGoing = true ;
    var commandResult : String = ""

    // this is the intent we are currently focused on
    // it's the default parent for all new intents
    var focalIntent: Long = 0

    fun handleKeyStroke(key: KeyStroke)  {

        when (key.keyType) {
            KeyType.Enter -> {
                keepGoing = handleEnter()
            }
            KeyType.Backspace -> {
                if (inputBuffer.isNotEmpty()) {
                    inputBuffer.deleteCharAt(inputBuffer.length - 1)
                }
            }
            KeyType.Character -> {
                inputBuffer.append(key.character)
            }
            else -> {}
        }
    }


    // returns false if the exit command is encountered
    // otherwise, either adds or updates the intent
    private fun handleEnter(): Boolean {
        val command = inputBuffer.toString().trim()
        inputBuffer.clear()

        when {
            command == "exit" -> return false
            command.startsWith("add ") -> {
                // For demo: just add a dummy intent
                var parts = command.split(" ", limit=2)
                val newIntent = if (parts.size == 2) {
                    service.addIntent(parts[1], focalIntent)
                } else {
                    service.addIntent("new intent at ${System.currentTimeMillis()}", focalIntent)
                }
                commandResult = "added intent ${newIntent.id()}"
            }
            command.startsWith("focus ") -> {
                val parts = command.split(" ")
                if (parts.size == 2) {
                    val newFocus = parts[1].toLongOrNull()
                    if (newFocus == null) {
                        commandResult = "cannot focus on invalid intent id ${parts[1]}"
                    } else {
                        focalIntent = newFocus
                        commandResult = "Focusing on $newFocus"
                    }
                } else {
                    commandResult = "Focus takes a single intent id"
                }
            }
            command.startsWith("update") -> {
                // Format: update <id> <new text>
                val parts = command.split(" ", limit = 3)
                if (parts.size == 3) {
                    val id = parts[1].toLongOrNull()
                    val newText = parts[2]
                    if (id != null) service.edit(id, newText)
                    commandResult = "updated intent $id"
                } else {
                    commandResult = "Update command requires an id followed by the new text."

                }
            }
        }
        return true
    }

}
