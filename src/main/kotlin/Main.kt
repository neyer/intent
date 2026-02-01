package com.intentevolved
import com.googlecode.lanterna.graphics.TextGraphics
import com.googlecode.lanterna.input.KeyStroke
import com.googlecode.lanterna.screen.Screen
import com.googlecode.lanterna.terminal.DefaultTerminalFactory
import com.googlecode.lanterna.TerminalPosition
import com.googlecode.lanterna.TerminalSize
import com.intentevolved.com.intentevolved.IntentServiceImpl
import com.intentevolved.com.intentevolved.Intent
import com.intentevolved.com.intentevolved.terminal.InputHandler
import com.intentevolved.com.intentevolved.terminal.RedrawType
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

fun main() {
    val terminalFactory = DefaultTerminalFactory()
    val screen: Screen = terminalFactory.createScreen()
    screen.startScreen()

    // todo: make this come from args
    val fileName = "current.pb"
    val service = IntentServiceImpl.fromFile(fileName)
    val handler = InputHandler(service, service, fileName)

    // Initial full screen draw
    drawFullScreen(screen, handler, service)
    screen.refresh()

    while (handler.keepGoing) {
        // Non-blocking poll for input
        val key: KeyStroke? = screen.pollInput()

        if (key != null) {
            val redrawType = handler.handleKeyStroke(key)
            when (redrawType) {
                RedrawType.FULL_SCREEN -> {
                    drawFullScreen(screen, handler, service)
                }
                RedrawType.INPUT_LINE_ONLY -> {
                    // Only update the input line
                    val tg = screen.newTextGraphics()
                    clearLine(tg, 0)
                    tg.putString(0, 0, "Input: ${handler.inputBuffer}|")
                }
            }
            screen.refresh()
        }

        Thread.sleep(30) // avoid CPU spin
    }

    screen.stopScreen()
}

fun drawFullScreen(screen: Screen, handler: InputHandler, service: IntentServiceImpl) {
    // Clear screen
    screen.clear()

    // Draw top line (input buffer)
    val tg = screen.newTextGraphics()
    tg.putString(0, 0, "Input: ${handler.inputBuffer}|")
    tg.putString(0, 1, "Result: ${handler.commandResult}")

    val intentOffset = 3
    // Draw rest of application state

    val scope = service.getFocalScope(handler.focalIntent)
    // if there are ancestors, put them here
    var thisRow = intentOffset;

    scope.ancestry.forEachIndexed { ancestorNo, intent ->
        val spaces = " ".repeat(ancestorNo)
        thisRow += renderIntentRow(tg, intent, thisRow, spaces)
    }

    ++thisRow
    // then put the intent itself
    thisRow += renderIntentRow(tg, scope.focus, thisRow)

    ++thisRow

    // then put children
    scope.children.forEach { intent ->
        thisRow += renderIntentRow(tg, intent, thisRow, " ")
    }
}

fun clearLine(tg: TextGraphics, row: Int) {
    val size = tg.size
    tg.fillRectangle(TerminalPosition(0, row), TerminalSize(size.columns, 1), ' ')
}

/**
 * Renders an intent row and its field values.
 * Returns the number of rows used.
 */
fun renderIntentRow(tg: TextGraphics, intent: Intent, row: Int, prefix: String=""): Int {
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
    // epoch_nanos -> Instant -> local formatted time
    val seconds = Math.floorDiv(epochNanos, 1_000_000_000L)
    val nanos = Math.floorMod(epochNanos, 1_000_000_000L).toInt()
    return INTENT_TIME_FORMATTER.format(Instant.ofEpochSecond(seconds, nanos.toLong()))
}