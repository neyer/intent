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

    val service = IntentServiceImpl()
    val handler = InputHandler(service)
    var mode : TerminalMode = TerminalMode.Command

    while (handler.keepGoing) {
        // Clear screen
        screen.clear()

        // Draw top line (input buffer)
        val tg = screen.newTextGraphics()
        tg.putString(0, 0, "Input: ${handler.inputBuffer}")
        tg.putString(0, 1, "Result: ${handler.commandResult}")

        val intentOffset = 3
        // Draw rest of application state
        val intents = service.getAll()
        intents.forEachIndexed { i, intent ->
            tg.putString(0, i + intentOffset, "${intent.id()} - ${intent.text()}")
        }

        screen.refresh()

        // Non-blocking poll for input
        val key: KeyStroke? = screen.pollInput()

        if (key != null) {
            handler.handleKeyStroke(key);
        }

        Thread.sleep(30) // avoid CPU spin
    }

    screen.stopScreen()
}
class InputHandler(
    val service: IntentServiceImpl
) {
    val inputBuffer = StringBuilder()

    var keepGoing = true ;

    var commandResult : String = ""

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
                    service.addIntent(parts[1])
                } else {
                    service.addIntent("new intent at ${System.currentTimeMillis()}")
                }
                commandResult = "added intent ${newIntent.id()}"
            }

            command.startsWith("update") -> {
                // Format: update <id> <new text>
                val parts = command.split(" ", limit = 3)
                if (parts.size == 3) {
                    val id = parts[1].toLongOrNull()
                    val newText = parts[2]
                    if (id != null) service.edit(id, newText)
                    commandResult = "updated intent $id"
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


enum class TerminalMode {
    Command,
    Edit
}