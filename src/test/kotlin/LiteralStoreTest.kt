import com.intentevolved.com.intentevolved.voluntas.LiteralStore
import com.intentevolved.com.intentevolved.voluntas.VoluntasIds
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Assertions.*
import voluntas.v1.Literal

class LiteralStoreTest {

    private lateinit var store: LiteralStore

    @BeforeEach
    fun setup() {
        store = LiteralStore()
    }

    @Test
    fun `bootstrap literals are pre-registered`() {
        // "defines_type" should be at well-known literal ID
        val definesTypeLit = store.getById(VoluntasIds.literalId(1L))
        assertNotNull(definesTypeLit)
        assertEquals("defines_type", definesTypeLit!!.stringVal)

        val setsFieldLit = store.getById(VoluntasIds.literalId(4L))
        assertNotNull(setsFieldLit)
        assertEquals("sets_field", setsFieldLit!!.stringVal)
    }

    @Test
    fun `getOrCreate returns same id for same string`() {
        val id1 = store.getOrCreate("hello")
        val id2 = store.getOrCreate("hello")
        assertEquals(id1, id2)
        assertTrue(VoluntasIds.isLiteral(id1))
    }

    @Test
    fun `getOrCreate returns different ids for different strings`() {
        val id1 = store.getOrCreate("hello")
        val id2 = store.getOrCreate("world")
        assertNotEquals(id1, id2)
    }

    @Test
    fun `getOrCreate handles int values`() {
        val id = store.getOrCreate(42L)
        assertTrue(VoluntasIds.isLiteral(id))
        val lit = store.getById(id)
        assertNotNull(lit)
        assertEquals(42L, lit!!.intVal)
    }

    @Test
    fun `getOrCreate handles double values`() {
        val id = store.getOrCreate(3.14)
        val lit = store.getById(id)
        assertNotNull(lit)
        assertEquals(3.14, lit!!.doubleVal)
    }

    @Test
    fun `getOrCreate handles boolean values`() {
        val id = store.getOrCreate(true)
        val lit = store.getById(id)
        assertNotNull(lit)
        assertEquals(true, lit!!.boolVal)
    }

    @Test
    fun `getOrCreate handles byte arrays`() {
        val bytes = byteArrayOf(1, 2, 3)
        val id = store.getOrCreate(bytes)
        val lit = store.getById(id)
        assertNotNull(lit)
        assertArrayEquals(bytes, lit!!.bytesVal.toByteArray())
    }

    @Test
    fun `content-addressing works for byte arrays`() {
        val bytes1 = byteArrayOf(1, 2, 3)
        val bytes2 = byteArrayOf(1, 2, 3)
        val id1 = store.getOrCreate(bytes1)
        val id2 = store.getOrCreate(bytes2)
        assertEquals(id1, id2)
    }

    @Test
    fun `getString returns string value`() {
        val id = store.getOrCreate("test string")
        assertEquals("test string", store.getString(id))
    }

    @Test
    fun `getString returns null for non-string literal`() {
        val id = store.getOrCreate(42L)
        assertNull(store.getString(id))
    }

    @Test
    fun `getString returns null for unknown id`() {
        assertNull(store.getString(VoluntasIds.literalId(9999L)))
    }

    @Test
    fun `register adds external literal`() {
        val id = VoluntasIds.literalId(100L)
        val literal = Literal.newBuilder()
            .setId(id)
            .setStringVal("external")
            .build()
        store.register(literal)
        assertEquals("external", store.getString(id))
    }

    @Test
    fun `register updates nextOrdinal to avoid collisions`() {
        val id = VoluntasIds.literalId(100L)
        val literal = Literal.newBuilder()
            .setId(id)
            .setStringVal("external")
            .build()
        store.register(literal)

        // Next created literal should have ordinal > 100
        val newId = store.getOrCreate("new value")
        assertTrue(VoluntasIds.literalOrdinal(newId) > 100L)
    }

    @Test
    fun `allLiterals includes bootstrap and user literals`() {
        val initialSize = store.size()
        store.getOrCreate("user1")
        store.getOrCreate("user2")
        assertEquals(initialSize + 2, store.size())
    }

    @Test
    fun `bootstrap text and parent literals are pre-registered`() {
        val textLit = store.getById(VoluntasIds.literalId(7L))
        assertNotNull(textLit)
        assertEquals("text", textLit!!.stringVal)

        val parentLit = store.getById(VoluntasIds.literalId(8L))
        assertNotNull(parentLit)
        assertEquals("parent", parentLit!!.stringVal)
    }
}
