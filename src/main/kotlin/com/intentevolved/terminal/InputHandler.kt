package com.intentevolved.com.intentevolved.terminal

import com.googlecode.lanterna.input.KeyStroke
import com.googlecode.lanterna.input.KeyType
import voluntas.v1.AddField
import voluntas.v1.FieldType
import voluntas.v1.SubmitOpRequest
import voluntas.v1.CreateIntent
import voluntas.v1.SetFieldValue
import voluntas.v1.UpdateIntentText
import voluntas.v1.UpdateIntentParent
import com.intentevolved.com.intentevolved.CommandResult
import com.intentevolved.com.intentevolved.IntentStreamConsumer
import com.intentevolved.com.intentevolved.IntentStateProvider
import com.intentevolved.com.intentevolved.voluntas.VoluntasIntentService
import java.io.File

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

        if (command.equals("exit", ignoreCase = true)) {
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

    fun matches(command: String): Boolean = command.equals(keyword, ignoreCase = true) || command.startsWith("$keyword ", ignoreCase = true)

    fun extractArgs(command: String): String =
        command.drop(keyword.length).trim()
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

        val request = SubmitOpRequest.newBuilder()
            .setCreateIntent(
                CreateIntent.newBuilder()
                    .setText(intentText)
                    .setParentId(focalIntent)
            )
            .build()

        return consumer.consume(request)
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

        if (focalIntent == 0L) {
            return CommandResult("At root intent, cannot go up")
        }

        val scope = stateProvider.getFocalScope(focalIntent)
        val parent = scope.ancestry.lastOrNull()
        val parentId = parent?.id() ?: 0L
        return CommandResult("Focusing on $parentId", newFocalIntent = parentId)
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
            val request = SubmitOpRequest.newBuilder()
                .setUpdateIntent(
                    UpdateIntentText.newBuilder()
                        .setId(id)
                        .setNewText(parts[1])
                )
                .build()

            try {
                consumer.consume(request)
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
                val request = SubmitOpRequest.newBuilder()
                    .setUpdateIntentParent(
                        UpdateIntentParent.newBuilder()
                            .setId(intentId)
                            .setParentId(newParentId)
                    )
                    .build()

                try {
                    consumer.consume(request)
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
                val addFieldRequest = SubmitOpRequest.newBuilder()
                    .setAddField(
                        AddField.newBuilder()
                            .setIntentId(intentId)
                            .setFieldName("done")
                            .setFieldType(FieldType.FIELD_TYPE_BOOL)
                    )
                    .build()
                consumer.consume(addFieldRequest)
            }

            // Set the 'done' field to true
            val setValueRequest = SubmitOpRequest.newBuilder()
                .setSetFieldValue(
                    SetFieldValue.newBuilder()
                        .setIntentId(intentId)
                        .setFieldName("done")
                        .setBoolValue(true)
                )
                .build()
            consumer.consume(setValueRequest)

            return CommandResult("Marked intent $intentId as done")
        } catch (e: IllegalArgumentException) {
            return CommandResult("Error: ${e.message}")
        }
    }
}

class WriteCommand : Command("write") {
    override fun process(
        args: String,
        consumer: IntentStreamConsumer,
        stateProvider: IntentStateProvider,
        focalIntent: Long
    ): CommandResult {
        val filePath = args.trim()
        if (filePath.isEmpty()) {
            return CommandResult("write requires a file path")
        }

        val scope = stateProvider.getFocalScope(focalIntent)
        val sb = StringBuilder()

        // Context section: why we're doing this, derived from ancestry
        sb.appendLine("# Context")
        sb.appendLine()
        if (scope.ancestry.isNotEmpty()) {
            sb.appendLine("This is why we're doing this:")
            sb.appendLine()
            for (ancestor in scope.ancestry) {
                sb.appendLine("- [${ancestor.id()}] ${ancestor.text()}")
            }
            sb.appendLine()
        }

        // Focus section
        sb.appendLine("# Current Focus")
        sb.appendLine()
        sb.appendLine("[${scope.focus.id()}] ${scope.focus.text()}")
        val focusFields = scope.focus.fieldValues()
        if (focusFields.isNotEmpty()) {
            for ((name, value) in focusFields) {
                sb.appendLine("  $name: $value")
            }
        }
        sb.appendLine()

        // Plan section: full subtree of children
        val nonMetaChildren = scope.children.filter { !it.isMeta() }
        if (nonMetaChildren.isNotEmpty()) {
            sb.appendLine("# Plan")
            sb.appendLine()

            fun writeSubtree(intentId: Long, depth: Int) {
                val childScope = stateProvider.getFocalScope(intentId)
                val indent = "  ".repeat(depth)
                sb.appendLine("$indent- [${childScope.focus.id()}] ${childScope.focus.text()}")
                val fields = childScope.focus.fieldValues()
                if (fields.isNotEmpty()) {
                    for ((name, value) in fields) {
                        sb.appendLine("$indent    $name: $value")
                    }
                }
                for (grandchild in childScope.children.filter { !it.isMeta() }) {
                    writeSubtree(grandchild.id(), depth + 1)
                }
            }

            for (child in nonMetaChildren) {
                writeSubtree(child.id(), 0)
            }
            sb.appendLine()
        }

        File(filePath).writeText(sb.toString())
        return CommandResult("Wrote plan to $filePath")
    }
}

class ImportCommand : Command("import") {
    override fun process(
        args: String,
        consumer: IntentStreamConsumer,
        stateProvider: IntentStateProvider,
        focalIntent: Long
    ): CommandResult {
        val filePath = args.trim()
        if (filePath.isEmpty()) {
            return CommandResult("import requires a file path")
        }

        val file = File(filePath)
        if (!file.exists()) {
            return CommandResult("File not found: $filePath")
        }

        val sourceService = VoluntasIntentService.fromFile(filePath)
        val oldToNew = mutableMapOf<Long, Long>()
        var importedCount = 0

        fun importIntent(oldId: Long, newParentId: Long) {
            val intent = sourceService.getById(oldId) ?: return

            // Create the intent on the target
            val createRequest = SubmitOpRequest.newBuilder()
                .setCreateIntent(
                    CreateIntent.newBuilder()
                        .setText(intent.text())
                        .setParentId(newParentId)
                )
                .build()
            val createResult = consumer.consume(createRequest)
            val newId = createResult.id ?: return
            oldToNew[oldId] = newId
            importedCount++

            // Recreate field definitions
            for ((fieldName, details) in intent.fields()) {
                val addFieldBuilder = AddField.newBuilder()
                    .setIntentId(newId)
                    .setFieldName(fieldName)
                    .setFieldType(details.fieldType)
                    .setRequired(details.required)
                if (details.description != null) {
                    addFieldBuilder.setDescription(details.description)
                }
                val addFieldRequest = SubmitOpRequest.newBuilder()
                    .setAddField(addFieldBuilder)
                    .build()
                consumer.consume(addFieldRequest)
            }

            // Recreate field values
            for ((fieldName, value) in intent.fieldValues()) {
                val sfvBuilder = SetFieldValue.newBuilder()
                    .setIntentId(newId)
                    .setFieldName(fieldName)
                when (value) {
                    is String -> sfvBuilder.setStringValue(value)
                    is Int -> sfvBuilder.setInt32Value(value)
                    is Long -> sfvBuilder.setInt64Value(value)
                    is Float -> sfvBuilder.setFloatValue(value)
                    is Double -> sfvBuilder.setDoubleValue(value)
                    is Boolean -> sfvBuilder.setBoolValue(value)
                }
                val setValueRequest = SubmitOpRequest.newBuilder()
                    .setSetFieldValue(sfvBuilder)
                    .build()
                consumer.consume(setValueRequest)
            }

            // Recurse into children
            val scope = sourceService.getFocalScope(oldId)
            for (child in scope.children) {
                if (!child.isMeta()) {
                    importIntent(child.id(), newId)
                }
            }
        }

        // Start from the root (id=0) of the source file
        val rootScope = sourceService.getFocalScope(0L)
        val rootIntent = rootScope.focus

        // Import the root intent as a child of the current focalIntent
        importIntent(rootIntent.id(), focalIntent)

        return CommandResult("Imported $importedCount intents from $filePath")
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
        DoCommand(),
        WriteCommand(),
        ImportCommand()
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
