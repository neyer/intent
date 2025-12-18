package com.intentevolved


import com.intentevolved.com.intentevolved.IntentServiceImpl
import com.intentevolved.com.intentevolved.terminal.InputHandler

import com.googlecode.lanterna.input.KeyStroke
import com.googlecode.lanterna.input.KeyType
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test


private fun ch(c: Char) = KeyStroke(c, false, false)
private fun enter() = KeyStroke(KeyType.Enter)
private fun backspace() = KeyStroke(KeyType.Backspace)

class InputHandlerTest {

    @Test
    fun `Character appends to inputBuffer`() {
        val service = IntentServiceImpl()
        val h = InputHandler(service)

        h.handleKeyStroke(ch('a'))
        h.handleKeyStroke(ch('b'))

        assertEquals("ab", h.inputBuffer.toString())
        assertTrue(h.keepGoing)
    }

    @Test
    fun `Backspace removes last char when buffer non-empty`() {
        val service = IntentServiceImpl()
        val h = InputHandler(service)

        h.handleKeyStroke(ch('a'))
        h.handleKeyStroke(ch('b'))
        h.handleKeyStroke(backspace())

        assertEquals("a", h.inputBuffer.toString())
    }

    @Test
    fun `Backspace on empty buffer does nothing`() {
        val service = IntentServiceImpl()
        val h = InputHandler(service)

        h.handleKeyStroke(backspace())

        assertEquals("", h.inputBuffer.toString())
    }

    @Test
    fun `Enter on exit command sets keepGoing false and clears buffer`() {
        val service = IntentServiceImpl()
        val h = InputHandler(service)

        "exit".forEach { h.handleKeyStroke(ch(it)) }
        h.handleKeyStroke(enter())

        assertFalse(h.keepGoing)
        assertEquals("", h.inputBuffer.toString())
        assertEquals("", h.commandResult) // unchanged
    }

    @Test
    fun `Enter on add command calls service and sets commandResult`() {
        val service = IntentServiceImpl()
        val h = InputHandler(service)

        "add buy milk".forEach { h.handleKeyStroke(ch(it)) }
        h.handleKeyStroke(enter())

        assertTrue(h.keepGoing)
        assertEquals("", h.inputBuffer.toString())
        assertEquals("buy milk", service.getAll()[0].text())
        assertEquals("added intent 0", h.commandResult)
    }

    @Test
    fun `Enter on update command calls edit and sets commandResult`() {
        val service = IntentServiceImpl()
        val h = InputHandler(service)

        "add buy milk".forEach { h.handleKeyStroke(ch(it)) }
        h.handleKeyStroke(enter())

        assertTrue(h.keepGoing)
        assertEquals("", h.inputBuffer.toString())
        assertEquals("added intent 0", h.commandResult)

        "update 0 buy eggs".forEach { h.handleKeyStroke(ch(it)) }
        h.handleKeyStroke(enter())
        assertEquals("buy eggs", service.getAll()[0].text())
        assertEquals("updated intent 0", h.commandResult)
    }
}
