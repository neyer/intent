package com.intentevolved.com.intentevolved.terminal

import com.googlecode.lanterna.input.KeyStroke
import com.googlecode.lanterna.input.KeyType
import com.intentevolved.AddField
import com.intentevolved.FieldType
import com.intentevolved.Op
import com.intentevolved.CreateIntent
import com.intentevolved.SetFieldValue
import com.intentevolved.UpdateIntentText
import com.intentevolved.UpdateIntentParent
import com.intentevolved.com.intentevolved.CommandResult
import com.intentevolved.com.intentevolved.IntentStreamConsumer
import com.intentevolved.com.intentevolved.IntentStateProvider

enum class RedrawType {
    FULL_SCREEN,
    INPUT_LINE_ONLY
}

class InputHandler(
    private val consumer: IntentStreamConsumer,
    private val stateProvider: IntentStateProvider,
    val fileName: String? = null
) {
    val inputBuffer = StringBuilder()

    var keepGoing = true
    var commandResult: String = ""

    // this is the intent we are currently focused on
    // it's the default parent for all new intents
    var focalIntent: Long = 0

    private val executor = CommandExecutor(consumer, stateProvider, fileName)

    fun handleKeyStroke(key: KeyStroke): RedrawType {

        when (key.keyType) {
            KeyType.Enter -> {
                keepGoing = handleEnter()
                return RedrawType.FULL_SCREEN
            }
            KeyType.Backspace -> {
                if (inputBuffer.isNotEmpty()) {
                    inputBuffer.deleteCharAt(inputBuffer.length - 1)
                }
                return RedrawType.INPUT_LINE_ONLY
            }
            KeyType.Character -> {
                inputBuffer.append(key.character)
                return RedrawType.INPUT_LINE_ONLY
            }
            else -> {
                return RedrawType.INPUT_LINE_ONLY
            }
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
    abstract fun process(
        args: String,
        consumer: IntentStreamConsumer,
        stateProvider: IntentStateProvider,
        focalIntent: Long
    ): CommandResult

    fun matches(command: String): Boolean = command == keyword || command.startsWith("$keyword ")

    fun extractArgs(command: String): String =
        command.removePrefix(keyword).trim()
}

// Command implementations
class AddCommand : Command("add") {
    override fun process(
        args: String,
        consumer: IntentStreamConsumer,
        stateProvider: IntentStateProvider,
        focalIntent: Long
    ): CommandResult {
        val intentText = args.ifEmpty { "new intent at ${System.currentTimeMillis()}" }

        // Let the consumer (IntentServiceImpl) assign ids and timestamps.
        val create = CreateIntent.newBuilder()
            .setText(intentText)
            .setParentId(focalIntent)
            .build()

        val op = Op.newBuilder()
            .setCreateIntent(create)
            .build()

        return consumer.consume(op)
    }
}

class FocusCommand : Command("focus") {
    override fun process(args: String, consumer: IntentStreamConsumer, stateProvider: IntentStateProvider, focalIntent: Long): CommandResult {
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
    override fun process(args: String, consumer: IntentStreamConsumer, stateProvider: IntentStateProvider, focalIntent: Long): CommandResult {
        val parts = args.split(" ")

        if (parts.size > 1) {
            return CommandResult("Up takes no arguments.")
        }

        // For now, treat 0 as the root; no interaction with the service.
        if (focalIntent == 0L) {
            return CommandResult("At root intent, cannot go up ")
        }

        // We don't currently have parent linkage without querying the service,
        // so simply move focus back to the root.
        return CommandResult("Focusing on 0", newFocalIntent = 0L)
    }
}

class UpdateCommand : Command("update") {
    override fun process(args: String, consumer: IntentStreamConsumer, stateProvider: IntentStateProvider, focalIntent: Long): CommandResult {
        val parts = args.split(" ", limit = 2)

        if (parts.size != 2) {
            return CommandResult("Update command requires an id followed by the new text.")
        }

        val id = parts[0].toLongOrNull()
        return if (id == null) {
            CommandResult("Invalid intent id: ${parts[0]}")
        } else {
            val update = UpdateIntentText.newBuilder()
                .setId(id)
                .setNewText(parts[1])
                .build()

            val op = Op.newBuilder()
                .setUpdateIntent(update)
                .build()

            try {
                consumer.consume(op)
            } catch (e: IllegalArgumentException) {
                return CommandResult("Error: ${e.message}")
            }
        }
    }
}

class MoveCommand : Command("move") {
    override fun process(args: String, consumer: IntentStreamConsumer, stateProvider: IntentStateProvider, focalIntent: Long): CommandResult {
        val parts = args.split(" ")

        if (parts.size != 2) {
            return CommandResult("Move command requires two intent ids: the intent to move and the new parent id")
        }

        val intentId = parts[0].toLongOrNull()
        val newParentId = parts[1].toLongOrNull()

        return when {
            intentId == null -> CommandResult("Invalid intent id: ${parts[0]}")
            newParentId == null -> CommandResult("Invalid parent id: ${parts[1]}")
            else -> {
                val updateParent = UpdateIntentParent.newBuilder()
                    .setId(intentId)
                    .setParentId(newParentId)
                    .build()

                val op = Op.newBuilder()
                    .setUpdateIntentParent(updateParent)
                    .build()

                try {
                    consumer.consume(op)
                } catch (e: IllegalArgumentException) {
                    CommandResult("Error: ${e.message}")
                }
            }
        }
    }
}

class DoCommand : Command("do") {
    override fun process(args: String, consumer: IntentStreamConsumer, stateProvider: IntentStateProvider, focalIntent: Long): CommandResult {
        val intentId = args.trim().toLongOrNull()
            ?: return CommandResult("Do command requires an intent id")

        val intent = stateProvider.getById(intentId)
            ?: return CommandResult("No intent with id $intentId")

        try {
            // Add the 'done' field if it doesn't already exist
            if (!intent.fields().containsKey("done")) {
                val addFieldOp = Op.newBuilder()
                    .setAddField(
                        AddField.newBuilder()
                            .setIntentId(intentId)
                            .setFieldName("done")
                            .setFieldType(FieldType.FIELD_TYPE_BOOL)
                    )
                    .build()
                consumer.consume(addFieldOp)
            }

            // Set the 'done' field to true
            val setValueOp = Op.newBuilder()
                .setSetFieldValue(
                    SetFieldValue.newBuilder()
                        .setIntentId(intentId)
                        .setFieldName("done")
                        .setBoolValue(true)
                )
                .build()
            consumer.consume(setValueOp)

            return CommandResult("Marked intent $intentId as done")
        } catch (e: IllegalArgumentException) {
            return CommandResult("Error: ${e.message}")
        }
    }
}

// Command registry and executor
class CommandExecutor(
    private val consumer: IntentStreamConsumer,
    private val stateProvider: IntentStateProvider, writeFileName: String?) {
    private val commands = listOf(
        AddCommand(),
        FocusCommand(),
        UpdateCommand(),
        UpCommand(),
        MoveCommand(),
        DoCommand()
    )

    fun execute(command: String, currentFocalIntent: Long): Pair<String, Long> {
        val matchedCommand = commands.firstOrNull { it.matches(command) }

        if (matchedCommand == null) {
            return "Unknown command $command" to currentFocalIntent
        }
        val args = matchedCommand.extractArgs(command)
        val result = matchedCommand.process(args, consumer, stateProvider, currentFocalIntent)

        val newFocalIntent = result.newFocalIntent ?: currentFocalIntent
        return result.message to newFocalIntent
    }
}
