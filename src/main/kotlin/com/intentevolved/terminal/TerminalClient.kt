package com.intentevolved.com.intentevolved.terminal

import com.googlecode.lanterna.graphics.TextGraphics
import com.googlecode.lanterna.input.KeyStroke
import com.googlecode.lanterna.screen.Screen
import com.googlecode.lanterna.terminal.DefaultTerminalFactory
import com.googlecode.lanterna.TerminalPosition
import com.googlecode.lanterna.TerminalSize
import com.intentevolved.*
import com.intentevolved.com.intentevolved.CommandResult
import com.intentevolved.com.intentevolved.FieldDetails
import com.intentevolved.com.intentevolved.FocalScope
import com.intentevolved.com.intentevolved.Intent
import com.intentevolved.com.intentevolved.IntentStateProvider
import com.intentevolved.com.intentevolved.IntentStreamConsumer
import io.grpc.ManagedChannel
import io.grpc.ManagedChannelBuilder
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.concurrent.TimeUnit

fun main(args: Array<String>) {
    val host = args.getOrNull(0) ?: "localhost"
    val port = args.getOrNull(1)?.toIntOrNull() ?: 50051

    val channel = ManagedChannelBuilder
        .forAddress(host, port)
        .usePlaintext()
        .build()

    try {
        val client = IntentGrpcClient(channel)
        runTerminal(client)
    } finally {
        channel.shutdown().awaitTermination(5, TimeUnit.SECONDS)
    }
}

fun runTerminal(client: IntentGrpcClient) {
    val terminalFactory = DefaultTerminalFactory()
    val screen: Screen = terminalFactory.createScreen()
    screen.startScreen()

    val handler = InputHandler(client, client)

    // Initial full screen draw
    drawFullScreen(screen, handler, client)
    screen.refresh()

    while (handler.keepGoing) {
        val key: KeyStroke? = screen.pollInput()

        if (key != null) {
            val redrawType = handler.handleKeyStroke(key)
            when (redrawType) {
                RedrawType.FULL_SCREEN -> {
                    drawFullScreen(screen, handler, client)
                }
                RedrawType.INPUT_LINE_ONLY -> {
                    val tg = screen.newTextGraphics()
                    clearLine(tg, 0)
                    tg.putString(0, 0, "Input: ${handler.inputBuffer}|")
                }
            }
            screen.refresh()
        }

        Thread.sleep(30)
    }

    screen.stopScreen()
}

fun drawFullScreen(screen: Screen, handler: InputHandler, client: IntentGrpcClient) {
    screen.clear()

    val tg = screen.newTextGraphics()
    tg.putString(0, 0, "Input: ${handler.inputBuffer}|")
    tg.putString(0, 1, "Result: ${handler.commandResult}")

    val intentOffset = 3
    var thisRow = intentOffset

    try {
        val scope = client.getFocalScope(handler.focalIntent)

        scope.ancestry.forEachIndexed { ancestorNo, intent ->
            val spaces = " ".repeat(ancestorNo)
            thisRow += renderIntentRow(tg, intent, thisRow, spaces)
        }

        ++thisRow
        thisRow += renderIntentRow(tg, scope.focus, thisRow)

        ++thisRow

        scope.children.forEach { intent ->
            thisRow += renderIntentRow(tg, intent, thisRow, " ")
        }
    } catch (e: Exception) {
        tg.putString(0, thisRow, "Error: ${e.message}")
    }
}

fun clearLine(tg: TextGraphics, row: Int) {
    val size = tg.size
    tg.fillRectangle(TerminalPosition(0, row), TerminalSize(size.columns, 1), ' ')
}

fun renderIntentRow(tg: TextGraphics, intent: Intent, row: Int, prefix: String = ""): Int {
    val epochNanos = intent.lastUpdatedTimestamp() ?: intent.createdTimestamp()
    val timestamp = epochNanos?.let { formatEpochNanosAsLocalMinute(it) } ?: "unknown time"
    tg.putString(0, row, "$prefix${intent.id()} - ${intent.text()} (at $timestamp)")

    var rowsUsed = 1
    val fieldIndent = "$prefix    "
    intent.fieldValues().forEach { (name, value) ->
        tg.putString(0, row + rowsUsed, "$fieldIndent$name: $value")
        rowsUsed++
    }

    return rowsUsed
}

private val INTENT_TIME_FORMATTER: DateTimeFormatter =
    DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm").withZone(ZoneId.systemDefault())

private fun formatEpochNanosAsLocalMinute(epochNanos: Long): String {
    val seconds = Math.floorDiv(epochNanos, 1_000_000_000L)
    val nanos = Math.floorMod(epochNanos, 1_000_000_000L).toInt()
    return INTENT_TIME_FORMATTER.format(Instant.ofEpochSecond(seconds, nanos.toLong()))
}

/**
 * gRPC client that implements IntentStreamConsumer and IntentStateProvider.
 */
class IntentGrpcClient(
    channel: ManagedChannel
) : IntentStreamConsumer, IntentStateProvider {

    private val stub = IntentServiceGrpc.newBlockingStub(channel)

    override fun consume(op: Op): CommandResult {
        val request = opToSubmitRequest(op)
        val response = stub.submitOp(request)

        return if (response.success) {
            CommandResult(response.message)
        } else {
            throw IllegalArgumentException(response.message)
        }
    }

    override fun getById(id: Long): Intent? {
        val request = GetIntentRequest.newBuilder().setId(id).build()
        val response = stub.getIntent(request)

        return if (response.found) {
            IntentProtoWrapper(response.intent)
        } else {
            null
        }
    }

    override fun getFocalScope(id: Long): FocalScope {
        val request = GetFocalScopeRequest.newBuilder().setId(id).build()
        val response = stub.getFocalScope(request)

        if (!response.found) {
            throw NullPointerException("No intent found with id $id: ${response.error}")
        }

        return FocalScope(
            focus = IntentProtoWrapper(response.focus),
            ancestry = response.ancestryList.map { IntentProtoWrapper(it) },
            children = response.childrenList.map { IntentProtoWrapper(it) }
        )
    }

    private fun opToSubmitRequest(op: Op): SubmitOpRequest {
        val builder = SubmitOpRequest.newBuilder()

        when {
            op.hasCreateIntent() -> builder.setCreateIntent(op.createIntent)
            op.hasUpdateIntent() -> builder.setUpdateIntent(op.updateIntent)
            op.hasUpdateIntentParent() -> builder.setUpdateIntentParent(op.updateIntentParent)
            op.hasAddField() -> builder.setAddField(op.addField)
            op.hasSetFieldValue() -> builder.setSetFieldValue(op.setFieldValue)
        }

        return builder.build()
    }
}

/**
 * Wrapper around IntentProto that implements the Intent interface.
 */
class IntentProtoWrapper(
    private val proto: IntentProto
) : Intent {

    override fun text(): String = proto.text

    override fun id(): Long = proto.id

    override fun createdTimestamp(): Long? =
        if (proto.hasCreatedTimestamp()) proto.createdTimestamp else null

    override fun lastUpdatedTimestamp(): Long? =
        if (proto.hasLastUpdatedTimestamp()) proto.lastUpdatedTimestamp else null

    override fun parent(): Intent? = null  // Not needed for display

    override fun participantIds(): List<Long> = proto.participantIdsList

    override fun children(): List<Intent> = emptyList()  // Not needed for display

    override fun fields(): Map<String, FieldDetails> {
        return proto.fieldsMap.mapValues { (_, details) ->
            FieldDetails(
                fieldType = details.fieldType,
                required = details.required,
                description = if (details.hasDescription()) details.description else null
            )
        }
    }

    override fun fieldValues(): Map<String, Any> {
        return proto.fieldValuesMap.mapValues { (_, value) ->
            when (value.valueCase) {
                FieldValueProto.ValueCase.STRING_VALUE -> value.stringValue
                FieldValueProto.ValueCase.INT32_VALUE -> value.int32Value
                FieldValueProto.ValueCase.INT64_VALUE -> value.int64Value
                FieldValueProto.ValueCase.FLOAT_VALUE -> value.floatValue
                FieldValueProto.ValueCase.DOUBLE_VALUE -> value.doubleValue
                FieldValueProto.ValueCase.BOOL_VALUE -> value.boolValue
                FieldValueProto.ValueCase.TIMESTAMP_VALUE -> value.timestampValue
                FieldValueProto.ValueCase.INTENT_REF_VALUE -> value.intentRefValue
                FieldValueProto.ValueCase.VALUE_NOT_SET -> "unset"
            }
        }
    }

    override fun isMeta(): Boolean = proto.isMeta
}
