package com.intentevolved
import com.googlecode.lanterna.TerminalSize
import com.googlecode.lanterna.input.KeyStroke
import com.googlecode.lanterna.input.KeyType
import com.googlecode.lanterna.screen.Screen
import com.googlecode.lanterna.terminal.DefaultTerminalFactory
import com.intentevolved.com.intentevolved.IntentServiceImpl
import com.intentevolved.com.intentevolved.terminal.InputHandler

fun main() {
    val terminalFactory = DefaultTerminalFactory()
    val screen: Screen = terminalFactory.createScreen()
    screen.startScreen()

    val service = IntentServiceImpl()
    val handler = InputHandler(service)

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