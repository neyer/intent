package com.intentevolved
import com.googlecode.lanterna.TerminalSize
import com.googlecode.lanterna.input.KeyStroke
import com.googlecode.lanterna.input.KeyType
import com.googlecode.lanterna.screen.Screen
import com.googlecode.lanterna.terminal.DefaultTerminalFactory
import com.intentevolved.com.intentevolved.IntentServiceImpl

fun main() {
    val terminalFactory = DefaultTerminalFactory()
    val screen: Screen = terminalFactory.createScreen()
    screen.startScreen()

    var running = true
    val inputBuffer = StringBuilder()

    val service = IntentServiceImpl()
    var mode : TerminalMode = TerminalMode.Command

    while (running) {
        // Clear screen
        screen.clear()

        // Draw top line (input buffer)
        val tg = screen.newTextGraphics()
        tg.putString(0, 0, "Input: $inputBuffer")

        // Draw rest of application state
        val intents = service.getAll()
        intents.forEachIndexed { i, intent ->
            tg.putString(0, i + 2, "${intent.id()} - ${intent.text()}")
        }

        screen.refresh()

        // Non-blocking poll for input
        val key: KeyStroke? = screen.pollInput()

        if (key != null) {
            when (key.keyType) {
                KeyType.Enter -> {
                    running = getInput(inputBuffer, service)
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

        Thread.sleep(30) // avoid CPU spin
    }

    screen.stopScreen()
}
class InputHandler(
    val service: IntentServiceImpl
) {
    val inputBuffer = StringBuilder()

    private fun addInputKey(keyType: KeyType): {

        if (keyType == KeyType.Enter)  {
            handleEnter()
        } else if (ke) {
            // add the key to the buffer
            keyType

        }


    }
    private fun handleEnter(): Boolean {
        val command = inputBuffer.toString().trim()
        inputBuffer.clear()

        when {
            command == "exit" -> return false
            command == "add" -> {
                // For demo: just add a dummy intent
                service.addIntent("new intent at ${System.currentTimeMillis()}")
            }

            command.startsWith("update") -> {
                // Format: update <id> <new text>
                val parts = command.split(" ", limit = 3)
                if (parts.size == 3) {
                    val id = parts[1].toLongOrNull()
                    val newText = parts[2]
                    if (id != null) service.edit(id, newText)
                }
            }
        }
        return true
    }

}
fun KeyStroke.toAscii(): Char? {
    return when (this.keyType) {
        KeyType.Character -> this.character  // already a Char
        KeyType.Enter -> '\n'
        KeyType.Tab -> '\t'
        KeyType.Backspace -> '\b'
        KeyType.Delete -> 0x7F.toChar() // ASCII DEL
        KeyType.Escape -> 0x1B.toChar() // ESC
        else -> null // Arrow keys, F-keys, etc. don't map to ASCII
    }
}


private fun getInput(
    inputBuffer: StringBuilder,
    service: IntentServiceImpl
): Boolean {
    val command = inputBuffer.toString().trim()
    inputBuffer.clear()

    when {
        command == "exit" -> return false
        command == "add" -> {
            // For demo: just add a dummy intent
            service.addIntent("new intent at ${System.currentTimeMillis()}")
        }

        command.startsWith("update") -> {
            // Format: update <id> <new text>
            val parts = command.split(" ", limit = 3)
            if (parts.size == 3) {
                val id = parts[1].toLongOrNull()
                val newText = parts[2]
                if (id != null) service.edit(id, newText)
            }
        }
    }
    return true
}

enum class TerminalMode {
    Command,
    Edit
}