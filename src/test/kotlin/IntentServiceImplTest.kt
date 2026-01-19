import com.intentevolved.com.intentevolved.Intent
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
        // this includes the root intent
        assertEquals(4, allIntents.size)
        assertEquals("Design the mobile UI", service.getById(intent1.id())?.text())
        assertEquals("Implement backend API", service.getById(intent2.id())?.text())
        assertEquals("Create database schema", service.getById(intent3.id())?.text())

        // Save to file
        service.writeToFile(testFile)

        // Load from file
        val loadedService = IntentServiceImpl.fromFile(testFile)

        // Verify loaded content matches original
        val loadedIntents = loadedService.getAll()
        assertEquals(4, loadedIntents.size)

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

    @Test
    fun `getFocalScope returns ancestry and immediate children`() {
        // Create a hierarchy:
        // Root -> parent (ID 1) -> middle (ID 2) -> child1 (ID 3), child2 (ID 4)
        //                      -> sibling (ID 5)
        val parent = service.addIntent("Parent intent", parentId = 0)
        val middle = service.addIntent("Middle intent", parentId = parent.id())
        val child1 = service.addIntent("Child 1", parentId = middle.id())
        val child2 = service.addIntent("Child 2", parentId = middle.id())
        val sibling = service.addIntent("Sibling intent", parentId = parent.id())

        val scope = service.getFocalScope(middle.id())

        // Should include: ancestry (root, parent), the intent itself (middle), and immediate children (child1, child2)
        // Should NOT include: sibling (not a child of middle)
        assertEquals(2, scope.children.size)
        assertEquals(2, scope.ancestry.size)

        fun List<Intent>.toIds(): Set<Long> {
            return map {it.id()}.toSet()
        }

        //val relevantIds = relevant.map { it.id() }.toSet()
        assertTrue(scope.ancestry.toIds().contains(parent.id()), "Should include parent in ancestry")
        assertTrue(scope.focus.id() == middle.id(), "Should include the intent itself")
        assertTrue(scope.children.toIds().contains(child1.id()), "Should include child1")
        assertTrue(scope.children.toIds().contains(child2.id()), "Should include child2")
    }

    @Test
    fun `getFocalScope throws for unknown id`() {
        assertThrows(NullPointerException::class.java) {
            service.getFocalScope(999L)
        }
    }

    @Test
    fun `getFocalScope returns only children when intent has no parent`() {
        val rootChild = service.addIntent("Root child", parentId = 0)
        val grandchild1 = service.addIntent("Grandchild 1", parentId = rootChild.id())
        val grandchild2 = service.addIntent("Grandchild 2", parentId = rootChild.id())

        val scope = service.getFocalScope(rootChild.id())

        // Should have just the root ancestor
        assertEquals(1, scope.ancestry.size)
        assertEquals(rootChild.id(), scope.focus.id())
        
        fun List<Intent>.toIds(): Set<Long> {
            return map { it.id() }.toSet()
        }
        
        assertEquals(2, scope.children.size)
        assertTrue(scope.children.toIds().contains(grandchild1.id()))
        assertTrue(scope.children.toIds().contains(grandchild2.id()))
    }

    @Test
    fun `getFocalScope returns only ancestry when intent has no children`() {
        val parent = service.addIntent("Parent", parentId = 0)
        val child = service.addIntent("Child", parentId = parent.id())

        val scope = service.getFocalScope(child.id())

        // Should have ancestry (root, parent) but no children
        assertEquals(2, scope.ancestry.size)
        assertEquals(child.id(), scope.focus.id())
        assertEquals(0, scope.children.size)
        
        fun List<Intent>.toIds(): Set<Long> {
            return map { it.id() }.toSet()
        }
        
        assertTrue(scope.ancestry.toIds().contains(parent.id()))
    }

    @Test
    fun `moveParent moves intent to new parent`() {
        // Create a hierarchy: root -> parent1 -> child1
        //                               -> parent2
        val parent1 = service.addIntent("Parent 1", parentId = 0)
        val parent2 = service.addIntent("Parent 2", parentId = 0)
        val child1 = service.addIntent("Child 1", parentId = parent1.id())

        // Verify initial state
        assertEquals(parent1.id(), child1.parent()!!.id())
        val scope1 = service.getFocalScope(parent1.id())
        assertTrue(scope1.children.map { it.id() }.contains(child1.id()))

        // Move child1 from parent1 to parent2
        service.moveParent(child1.id(), parent2.id())

        // Verify move worked
        val movedChild = service.getById(child1.id())!!
        assertEquals(parent2.id(), movedChild.parent()!!.id())
        
        val scope2 = service.getFocalScope(parent2.id())
        assertTrue(scope2.children.map { it.id() }.contains(child1.id()))
        
        val scope1After = service.getFocalScope(parent1.id())
        assertFalse(scope1After.children.map { it.id() }.contains(child1.id()))
    }

    @Test
    fun `moveParent throws for invalid intent id`() {
        assertThrows(IllegalArgumentException::class.java) {
            service.moveParent(999L, 0L)
        }
    }

    @Test
    fun `moveParent throws for invalid parent id`() {
        val intent = service.addIntent("Test", parentId = 0)
        assertThrows(IllegalArgumentException::class.java) {
            service.moveParent(intent.id(), 999L)
        }
    }
}
