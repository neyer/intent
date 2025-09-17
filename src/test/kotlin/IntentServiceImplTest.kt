import com.intentevolved.com.intentevolved.IntentService
import com.intentevolved.com.intentevolved.IntentServiceImpl
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Assertions.*

class IntentServiceImplTest {

    private lateinit var service: IntentService

    @BeforeEach
    fun setup() {
        service = IntentServiceImpl()
    }

    @Test
    fun `addIntent returns a valid Intent`() {
        val result = service.addIntent("Test intent")

        assertNotNull(result)
        assertEquals(0L, result.id())
        assertEquals("Test intent", result.text())
    }

    @Test
    fun `getById returns the correct Intent`() {
        service.addIntent("Hello")
        val result = service.getById(0L)

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
        service.addIntent("Hi this is me")
        service.edit(0L, "New text")
        val intent = service.getById(0)!!

        assertEquals("New text", intent.text())
    }
}
