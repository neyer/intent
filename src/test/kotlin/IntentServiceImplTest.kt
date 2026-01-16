import com.intentevolved.com.intentevolved.IntentService
import com.intentevolved.com.intentevolved.IntentServiceImpl
import org.junit.jupiter.api.io.TempDir
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Assertions.*
import java.nio.file.Path

class IntentServiceImplTest {

    private lateinit var service: IntentService

    @BeforeEach
    fun setup() {
        service = IntentServiceImpl.new("Test some things")
    }

    @Test
    fun `addIntent returns a valid Intent`() {
        val result = service.addIntent("Test intent", 0)

        assertNotNull(result)
        assertEquals(1L, result.id())
        assertEquals("Test intent", result.text())
    }

    @Test
    fun `getById returns the correct Intent`() {
        service.addIntent("Hello", 0)
        val result = service.getById(1L)

        assertNotNull(result)
        assertEquals("Hello", result?.text())
    }

    @Test
    fun `getById returns null for unknown id`() {
        val result = service.getById(42L)

        assertNull(result)
    }

    @Test
    fun `edit updates the text`() {
        service.addIntent("Hi this is me", 0)
        service.edit(1L, "New text")
        val intent = service.getById(1)!!

        assertEquals("New text", intent.text())
    }

    @Test
    fun `create intent stream, add intents, edit, save to file, and reload`(@TempDir tempDir: Path) {
        val testFile = tempDir.resolve("test_intents.pb").toString()

        // Create a new intent stream
        val service = IntentServiceImpl.new("Build a todo app")

        // Add some intents
        val intent1 = service.addIntent("Design the UI", parentId = 0)
        val intent2 = service.addIntent("Implement backend API", parentId = 0)
        val intent3 = service.addIntent("Create database schema", parentId = intent2.id())

        // Edit an intent
        service.edit(intent1.id(), "Design the mobile UI")

        // Verify state before saving
        val allIntents = service.getAll()
        assertEquals(3, allIntents.size)
        assertEquals("Design the mobile UI", service.getById(intent1.id())?.text())
        assertEquals("Implement backend API", service.getById(intent2.id())?.text())
        assertEquals("Create database schema", service.getById(intent3.id())?.text())

        // Save to file
        service.writeToFile(testFile)

        // Load from file
        val loadedService = IntentServiceImpl.fromFile(testFile)

        // Verify loaded content matches original
        val loadedIntents = loadedService.getAll()
        assertEquals(3, loadedIntents.size)

        val loadedIntent1 = loadedService.getById(intent1.id())!!
        assertEquals("Design the mobile UI", loadedIntent1.text())
        assertEquals(intent1.id(), loadedIntent1.id())

        val loadedIntent2 = loadedService.getById(intent2.id())!!
        assertEquals("Implement backend API", loadedIntent2.text())
        assertEquals(intent2.id(), loadedIntent2.id())

        val loadedIntent3 = loadedService.getById(intent3.id())!!
        assertEquals("Create database schema", loadedIntent3.text())
        assertEquals(intent3.id(), loadedIntent3.id())
    }
}
