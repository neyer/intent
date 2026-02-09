package com.intentevolved


import com.intentevolved.com.intentevolved.voluntas.VoluntasIntentService
import com.intentevolved.com.intentevolved.voluntas.VoluntasIds
import com.intentevolved.com.intentevolved.terminal.InputHandler
import java.io.File

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
        val service = VoluntasIntentService.new("testing")
        val h = InputHandler(service,service)

        h.handleKeyStroke(ch('a'))
        h.handleKeyStroke(ch('b'))

        assertEquals("ab", h.inputBuffer.toString())
        assertTrue(h.keepGoing)
    }

    @Test
    fun `Backspace removes last char when buffer non-empty`() {
        val service = VoluntasIntentService.new("testing")
        val h = InputHandler(service,service)

        h.handleKeyStroke(ch('a'))
        h.handleKeyStroke(ch('b'))
        h.handleKeyStroke(backspace())

        assertEquals("a", h.inputBuffer.toString())
    }

    @Test
    fun `Backspace on empty buffer does nothing`() {
        val service = VoluntasIntentService.new("testing")
        val h = InputHandler(service,service)

        h.handleKeyStroke(backspace())

        assertEquals("", h.inputBuffer.toString())
    }

    @Test
    fun `Enter on exit command sets keepGoing false and clears buffer`() {
        val service = VoluntasIntentService.new("testing")
        val h = InputHandler(service,service)

        "exit".forEach { h.handleKeyStroke(ch(it)) }
        h.handleKeyStroke(enter())

        assertFalse(h.keepGoing)
        assertEquals("", h.inputBuffer.toString())
        assertEquals("", h.commandResult) // unchanged
    }

    @Test
    fun `Enter on add command calls service and sets commandResult`() {
        val service = VoluntasIntentService.new("testing")
        val h = InputHandler(service,service)
        val id1 = VoluntasIds.FIRST_USER_ENTITY

        "add buy milk".forEach { h.handleKeyStroke(ch(it)) }
        h.handleKeyStroke(enter())

        assertTrue(h.keepGoing)
        assertEquals("", h.inputBuffer.toString())
        assertEquals("buy milk", service.getById(id1)!!.text())
        assertEquals("added intent $id1", h.commandResult)
    }

    @Test
    fun `Enter on update command calls edit and sets commandResult`() {
        val service = VoluntasIntentService.new("testing")
        val h = InputHandler(service,service)
        val id1 = VoluntasIds.FIRST_USER_ENTITY

        "add buy milk".forEach { h.handleKeyStroke(ch(it)) }
        h.handleKeyStroke(enter())

        assertTrue(h.keepGoing)
        assertEquals("", h.inputBuffer.toString())
        assertEquals("added intent $id1", h.commandResult)

        "update $id1 buy eggs".forEach { h.handleKeyStroke(ch(it)) }
        h.handleKeyStroke(enter())
        assertEquals("buy eggs", service.getById(id1)!!.text())
        assertEquals("updated intent $id1", h.commandResult)
    }

    @Test
    fun `Enter on move command moves intent to new parent`() {
        val service = VoluntasIntentService.new("testing")
        val h = InputHandler(service,service)
        val id1 = VoluntasIds.FIRST_USER_ENTITY
        val id2 = VoluntasIds.FIRST_USER_ENTITY + 1

        // Create parent intent
        "add parent intent".forEach { h.handleKeyStroke(ch(it)) }
        h.handleKeyStroke(enter())
        assertEquals("added intent $id1", h.commandResult)

        // Create child intent
        "add child intent".forEach { h.handleKeyStroke(ch(it)) }
        h.handleKeyStroke(enter())
        assertEquals("added intent $id2", h.commandResult)

        // Move child from root (0) to parent (id1)
        "move $id2 $id1".forEach { h.handleKeyStroke(ch(it)) }
        h.handleKeyStroke(enter())
        assertEquals("moved intent $id2 to parent $id1", h.commandResult)

        // Verify the move worked
        val child = service.getById(id2)!!
        assertEquals(id1, child.parent()!!.id())
    }

    @Test
    fun `Move command with invalid intent id returns error`() {
        val service = VoluntasIntentService.new("testing")
        val h = InputHandler(service,service)

        "move 999 0".forEach { h.handleKeyStroke(ch(it)) }
        h.handleKeyStroke(enter())
        
        assertTrue(h.commandResult.contains("Error"))
        assertTrue(h.commandResult.contains("No intent with id"))
    }

    @Test
    fun `Move command with invalid parent id returns error`() {
        val service = VoluntasIntentService.new("testing")
        val h = InputHandler(service,service)
        val id1 = VoluntasIds.FIRST_USER_ENTITY

        // Create an intent
        "add test intent".forEach { h.handleKeyStroke(ch(it)) }
        h.handleKeyStroke(enter())

        // Try to move to invalid parent
        "move $id1 999".forEach { h.handleKeyStroke(ch(it)) }
        h.handleKeyStroke(enter())

        assertTrue(h.commandResult.contains("Error"))
        assertTrue(h.commandResult.contains("No intent with id"))
    }

    @Test
    fun `Move command with wrong number of arguments returns error`() {
        val service = VoluntasIntentService.new("testing")
        val h = InputHandler(service,service)

        "move 1".forEach { h.handleKeyStroke(ch(it)) }
        h.handleKeyStroke(enter())
        
        assertEquals("Move command requires two intent ids: the intent to move and the new parent id", h.commandResult)

        "move 1 2 3".forEach { h.handleKeyStroke(ch(it)) }
        h.handleKeyStroke(enter())
        
        assertEquals("Move command requires two intent ids: the intent to move and the new parent id", h.commandResult)
    }

    @Test
    fun `Import command recreates intent tree from pb file`() {
        // Build a source intent tree and save to a temp file
        val source = VoluntasIntentService.new("imported root")
        val child1 = source.addIntent("child one", 0L)
        val child2 = source.addIntent("child two", 0L)
        val grandchild = source.addIntent("grandchild", child1.id())

        val tempFile = File.createTempFile("import-test", ".pb")
        tempFile.deleteOnExit()
        source.writeToFile(tempFile.absolutePath)

        // Set up a destination service and import via command
        val dest = VoluntasIntentService.new("destination root")
        val h = InputHandler(dest, dest)

        "import ${tempFile.absolutePath}".forEach { h.handleKeyStroke(ch(it)) }
        h.handleKeyStroke(enter())

        assertTrue(h.commandResult.startsWith("Imported "))
        assertTrue(h.commandResult.contains("4 intents"))

        // The imported root should be a child of the destination root (id=0)
        val destRoot = dest.getFocalScope(0L)
        val importedRoots = destRoot.children.filter { it.text() == "imported root" }
        assertEquals(1, importedRoots.size)

        // Check the imported root has 2 children
        val importedRootScope = dest.getFocalScope(importedRoots[0].id())
        val importedChildren = importedRootScope.children.filter { !it.isMeta() }
        assertEquals(2, importedChildren.size)

        val childOneImported = importedChildren.find { it.text() == "child one" }!!
        val childTwoImported = importedChildren.find { it.text() == "child two" }!!
        assertNotNull(childOneImported)
        assertNotNull(childTwoImported)

        // Check grandchild is under child one
        val childOneScope = dest.getFocalScope(childOneImported.id())
        val grandchildren = childOneScope.children.filter { !it.isMeta() }
        assertEquals(1, grandchildren.size)
        assertEquals("grandchild", grandchildren[0].text())
    }
}
