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

fun main() {
    val terminalFactory = DefaultTerminalFactory()
    val screen: Screen = terminalFactory.createScreen()
    screen.startScreen()

    // todo: make this come from args
    val fileName = "current.pb"
    val service = IntentServiceImpl.fromFile(fileName)
    val handler = InputHandler(service, fileName)

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
        renderIntentRow(tg, intent, thisRow, spaces)
        ++thisRow
    }

    ++thisRow
    // then put the intent itself
    renderIntentRow(tg, scope.focus, thisRow)


    ++thisRow

    // then put children
    scope.children.forEach { intent ->
        renderIntentRow(tg, intent, thisRow, " ")
        ++thisRow
    }
}

fun clearLine(tg: TextGraphics, row: Int) {
    val size = tg.size
    tg.fillRectangle(TerminalPosition(0, row), TerminalSize(size.columns, 1), ' ')
}

fun renderIntentRow(tg: TextGraphics, intent: Intent, row: Int, prefix: String="") {
    tg.putString(0, row, "$prefix${intent.id()} - ${intent.text()}")
}