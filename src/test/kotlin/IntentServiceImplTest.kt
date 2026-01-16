import com.intentevolved.com.intentevolved.IntentService
import com.intentevolved.com.intentevolved.IntentServiceImpl
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Assertions.*

class IntentServiceImplTest {

    private lateinit var service: IntentService

    @BeforeEach
    fun setup() {
        service = IntentServiceImpl("Test some things")
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
}
