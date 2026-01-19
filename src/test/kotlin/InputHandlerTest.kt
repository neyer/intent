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
        val service = IntentServiceImpl.new("testing")
        val h = InputHandler(service)

        h.handleKeyStroke(ch('a'))
        h.handleKeyStroke(ch('b'))

        assertEquals("ab", h.inputBuffer.toString())
        assertTrue(h.keepGoing)
    }

    @Test
    fun `Backspace removes last char when buffer non-empty`() {
        val service = IntentServiceImpl.new("testing")
        val h = InputHandler(service)

        h.handleKeyStroke(ch('a'))
        h.handleKeyStroke(ch('b'))
        h.handleKeyStroke(backspace())

        assertEquals("a", h.inputBuffer.toString())
    }

    @Test
    fun `Backspace on empty buffer does nothing`() {
        val service = IntentServiceImpl.new("testing")
        val h = InputHandler(service)

        h.handleKeyStroke(backspace())

        assertEquals("", h.inputBuffer.toString())
    }

    @Test
    fun `Enter on exit command sets keepGoing false and clears buffer`() {
        val service = IntentServiceImpl.new("testing")
        val h = InputHandler(service)

        "exit".forEach { h.handleKeyStroke(ch(it)) }
        h.handleKeyStroke(enter())

        assertFalse(h.keepGoing)
        assertEquals("", h.inputBuffer.toString())
        assertEquals("", h.commandResult) // unchanged
    }

    @Test
    fun `Enter on add command calls service and sets commandResult`() {
        val service = IntentServiceImpl.new("testing")
        val h = InputHandler(service)

        "add buy milk".forEach { h.handleKeyStroke(ch(it)) }
        h.handleKeyStroke(enter())

        assertTrue(h.keepGoing)
        assertEquals("", h.inputBuffer.toString())
        assertEquals("buy milk", service.getById(1)!!.text())
        assertEquals("added intent 1", h.commandResult)
    }

    @Test
    fun `Enter on update command calls edit and sets commandResult`() {
        val service = IntentServiceImpl.new("testing")
        val h = InputHandler(service)

        "add buy milk".forEach { h.handleKeyStroke(ch(it)) }
        h.handleKeyStroke(enter())

        assertTrue(h.keepGoing)
        assertEquals("", h.inputBuffer.toString())
        assertEquals("added intent 1", h.commandResult)

        "update 1 buy eggs".forEach { h.handleKeyStroke(ch(it)) }
        h.handleKeyStroke(enter())
        assertEquals("buy eggs", service.getById(1)!!.text())
        assertEquals("updated intent 1", h.commandResult)
    }

    @Test
    fun `Enter on move command moves intent to new parent`() {
        val service = IntentServiceImpl.new("testing")
        val h = InputHandler(service)

        // Create parent intent
        "add parent intent".forEach { h.handleKeyStroke(ch(it)) }
        h.handleKeyStroke(enter())
        assertEquals("added intent 1", h.commandResult)

        // Create child intent
        "add child intent".forEach { h.handleKeyStroke(ch(it)) }
        h.handleKeyStroke(enter())
        assertEquals("added intent 2", h.commandResult)

        // Move child from root (0) to parent (1)
        "move 2 1".forEach { h.handleKeyStroke(ch(it)) }
        h.handleKeyStroke(enter())
        assertEquals("moved intent 2 to parent 1", h.commandResult)
        
        // Verify the move worked
        val child = service.getById(2)!!
        assertEquals(1L, child.parent()!!.id())
    }

    @Test
    fun `Move command with invalid intent id returns error`() {
        val service = IntentServiceImpl.new("testing")
        val h = InputHandler(service)

        "move 999 0".forEach { h.handleKeyStroke(ch(it)) }
        h.handleKeyStroke(enter())
        
        assertTrue(h.commandResult.contains("Error"))
        assertTrue(h.commandResult.contains("No intent with id"))
    }

    @Test
    fun `Move command with invalid parent id returns error`() {
        val service = IntentServiceImpl.new("testing")
        val h = InputHandler(service)

        // Create an intent
        "add test intent".forEach { h.handleKeyStroke(ch(it)) }
        h.handleKeyStroke(enter())

        // Try to move to invalid parent
        "move 1 999".forEach { h.handleKeyStroke(ch(it)) }
        h.handleKeyStroke(enter())
        
        assertTrue(h.commandResult.contains("Error"))
        assertTrue(h.commandResult.contains("No intent with id"))
    }

    @Test
    fun `Move command with wrong number of arguments returns error`() {
        val service = IntentServiceImpl.new("testing")
        val h = InputHandler(service)

        "move 1".forEach { h.handleKeyStroke(ch(it)) }
        h.handleKeyStroke(enter())
        
        assertEquals("Move command requires two intent ids: the intent to move and the new parent id", h.commandResult)

        "move 1 2 3".forEach { h.handleKeyStroke(ch(it)) }
        h.handleKeyStroke(enter())
        
        assertEquals("Move command requires two intent ids: the intent to move and the new parent id", h.commandResult)
    }
}
