package com.intentevolved.com.intentevolved.terminal

import com.googlecode.lanterna.input.KeyStroke
import com.googlecode.lanterna.input.KeyType
import com.intentevolved.com.intentevolved.IntentService

class InputHandler(
    val service: IntentService
) {
    val inputBuffer = StringBuilder()

    var keepGoing = true ;
    var commandResult : String = ""

    // this is the intent we are currently focused on
    // it's the default parent for all new intents
    var focalIntent: Long = 0

    val executor = CommandExecutor(service)

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

        if (command == "exit") {
            return false;
        }
        else {
            val (result, newFocalIntent) = executor.execute(command, focalIntent)
            commandResult = result
            focalIntent = newFocalIntent
        }
        return true
    }

}

// Base command class
abstract class Command(val keyword: String) {
    abstract fun process(args: String, service: IntentService, focalIntent: Long): CommandResult

    fun matches(command: String): Boolean = command == keyword || command.startsWith("$keyword ")

    fun extractArgs(command: String): String =
        command.removePrefix("$keyword ").trim()
}

// Result wrapper to handle both output and state changes
data class CommandResult(
    val message: String,
    val newFocalIntent: Long? = null // null means no change
)

// Command implementations
class AddCommand : Command("add") {
    override fun process(args: String, service: IntentService, focalIntent: Long): CommandResult {
        val intentText = args.ifEmpty { "new intent at ${System.currentTimeMillis()}" }
        val newIntent = service.addIntent(intentText, focalIntent)
        return CommandResult("added intent ${newIntent.id()}")
    }
}

class FocusCommand : Command("focus") {
    override fun process(args: String, service: IntentService, focalIntent: Long): CommandResult {
        val parts = args.split(" ")

        if (parts.size != 1) {
            return CommandResult("Focus takes a single intent id")
        }

        val newFocus = parts[0].toLongOrNull()
        return if (newFocus == null) {
            CommandResult("cannot focus on invalid intent id ${parts[0]}")
        } else {
            CommandResult("Focusing on $newFocus", newFocalIntent = newFocus)
        }
    }
}

class UpCommand : Command("up") {
    override fun process(args: String, service: IntentService, focalIntent: Long): CommandResult {
        val parts = args.split(" ")

        if (parts.size > 1) {
            return CommandResult("Up takes no arguments.")
        }

        val existingFocus = service.getById(focalIntent)!!
        val parent = existingFocus.parent()
        if (parent == null) {
            check(focalIntent == 0L) {
                "Intent id $focalIntent had no parent but was not root intent."
            }
            return   CommandResult("At root intent, cannot go up ")
        }
        val newFocus = parent.id()
        return   CommandResult("Focusing on $newFocus: ${parent.text()}" , newFocalIntent = newFocus)
    }
}

class UpdateCommand : Command("update") {
    override fun process(args: String, service: IntentService, focalIntent: Long): CommandResult {
        val parts = args.split(" ", limit = 2)

        if (parts.size != 2) {
            return CommandResult("Update command requires an id followed by the new text.")
        }

        val id = parts[0].toLongOrNull()
        return if (id == null) {
            CommandResult("Invalid intent id: ${parts[0]}")
        } else {
            service.edit(id, parts[1])
            CommandResult("updated intent $id")
        }
    }
}

class WriteCommand : Command("write") {
    override fun process(args: String, service: IntentService, focalIntent: Long): CommandResult {
        service.writeToFile(args)
        return CommandResult("wrote to file: $args")
    }
}

// Command registry and executor
class CommandExecutor(private val service: IntentService) {
    private val commands = listOf(
        AddCommand(),
        FocusCommand(),
        UpdateCommand(),
        WriteCommand(),
        UpCommand()
    )

    fun execute(command: String, currentFocalIntent: Long): Pair<String, Long> {
        val matchedCommand = commands.firstOrNull { it.matches(command) }

        if (matchedCommand == null) {
            return "Unknown command $command" to currentFocalIntent
        }
        val args = matchedCommand.extractArgs(command)
        val result = matchedCommand.process(args, service, currentFocalIntent)

        val newFocalIntent = result.newFocalIntent ?: currentFocalIntent
        return result.message to newFocalIntent
    }
}
