import voluntas.v1.FieldType
import com.intentevolved.com.intentevolved.Intent
import com.intentevolved.com.intentevolved.IntentService
import com.intentevolved.com.intentevolved.voluntas.VoluntasIntentService
import com.intentevolved.com.intentevolved.voluntas.VoluntasStreamConsumer
import com.intentevolved.com.intentevolved.voluntas.VoluntasIds
import org.junit.jupiter.api.io.TempDir
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Assertions.*
import voluntas.v1.Relationship
import java.nio.file.Path

class VoluntasIntentServiceTest {

    private lateinit var service: VoluntasIntentService

    @BeforeEach
    fun setup() {
        service = VoluntasIntentService.new("Test some things")
    }

    @Test
    fun `root intent exists at id 0`() {
        val root = service.getById(0L)
        assertNotNull(root)
        assertEquals(0L, root!!.id())
        assertEquals("Test some things", root.text())
        assertFalse(root.isMeta())
    }

    @Test
    fun `addIntent returns a valid Intent`() {
        val result = service.addIntent("Test intent", 0)

        assertNotNull(result)
        assertEquals("Test intent", result.text())
        assertFalse(result.isMeta())
    }

    @Test
    fun `addIntent assigns sequential ids starting at FIRST_USER_ENTITY`() {
        val r1 = service.addIntent("First", 0)
        val r2 = service.addIntent("Second", 0)
        assertEquals(VoluntasIds.FIRST_USER_ENTITY, r1.id())
        assertEquals(VoluntasIds.FIRST_USER_ENTITY + 1, r2.id())
    }

    @Test
    fun `getById returns the correct Intent`() {
        val added = service.addIntent("Hello", 0)
        val result = service.getById(added.id())

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
        val added = service.addIntent("Hi this is me", 0)
        service.edit(added.id(), "New text")
        val intent = service.getById(added.id())!!

        assertEquals("New text", intent.text())
    }

    @Test
    fun `edit throws for non-existent intent`() {
        assertThrows(IllegalArgumentException::class.java) {
            service.edit(999L, "New text")
        }
    }

    @Test
    fun `create intent stream, add intents, edit, save to file, and reload`(@TempDir tempDir: Path) {
        val testFile = tempDir.resolve("test_voluntas.pb").toString()

        val svc = VoluntasIntentService.new("Build a todo app")

        val intent1 = svc.addIntent("Design the UI", parentId = 0)
        val intent2 = svc.addIntent("Implement backend API", parentId = 0)
        val intent3 = svc.addIntent("Create database schema", parentId = intent2.id())

        svc.edit(intent1.id(), "Design the mobile UI")

        // Verify state before saving
        val allIntents = svc.getAll()
        assertEquals(4, allIntents.size) // root + 3
        assertEquals("Design the mobile UI", svc.getById(intent1.id())?.text())
        assertEquals("Implement backend API", svc.getById(intent2.id())?.text())
        assertEquals("Create database schema", svc.getById(intent3.id())?.text())

        // Save to file
        svc.writeToFile(testFile)

        // Load from file
        val loadedService = VoluntasIntentService.fromFile(testFile)

        // Verify loaded content matches original
        val loadedIntents = loadedService.getAll()
        assertEquals(4, loadedIntents.size)

        val loadedIntent1 = loadedService.getById(intent1.id())!!
        assertEquals("Design the mobile UI", loadedIntent1.text())

        val loadedIntent2 = loadedService.getById(intent2.id())!!
        assertEquals("Implement backend API", loadedIntent2.text())

        val loadedIntent3 = loadedService.getById(intent3.id())!!
        assertEquals("Create database schema", loadedIntent3.text())
    }

    @Test
    fun `getFocalScope returns ancestry and immediate children`() {
        val parent = service.addIntent("Parent intent", parentId = 0)
        val middle = service.addIntent("Middle intent", parentId = parent.id())
        val child1 = service.addIntent("Child 1", parentId = middle.id())
        val child2 = service.addIntent("Child 2", parentId = middle.id())
        val sibling = service.addIntent("Sibling intent", parentId = parent.id())

        val scope = service.getFocalScope(middle.id())

        assertEquals(2, scope.children.size)
        assertEquals(2, scope.ancestry.size)

        fun List<Intent>.toIds(): Set<Long> = map { it.id() }.toSet()

        assertTrue(scope.ancestry.toIds().contains(parent.id()), "Should include parent in ancestry")
        assertEquals(middle.id(), scope.focus.id(), "Should include the intent itself")
        assertTrue(scope.children.toIds().contains(child1.id()), "Should include child1")
        assertTrue(scope.children.toIds().contains(child2.id()), "Should include child2")
        assertFalse(scope.children.toIds().contains(sibling.id()), "Should not include sibling")
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

        assertEquals(1, scope.ancestry.size)
        assertEquals(rootChild.id(), scope.focus.id())
        assertEquals(2, scope.children.size)

        fun List<Intent>.toIds(): Set<Long> = map { it.id() }.toSet()
        assertTrue(scope.children.toIds().contains(grandchild1.id()))
        assertTrue(scope.children.toIds().contains(grandchild2.id()))
    }

    @Test
    fun `getFocalScope returns only ancestry when intent has no children`() {
        val parent = service.addIntent("Parent", parentId = 0)
        val child = service.addIntent("Child", parentId = parent.id())

        val scope = service.getFocalScope(child.id())

        assertEquals(2, scope.ancestry.size)
        assertEquals(child.id(), scope.focus.id())
        assertEquals(0, scope.children.size)

        fun List<Intent>.toIds(): Set<Long> = map { it.id() }.toSet()
        assertTrue(scope.ancestry.toIds().contains(parent.id()))
    }

    @Test
    fun `moveParent moves intent to new parent`() {
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

    @Test
    fun `getAll returns only non-meta intents`() {
        service.addIntent("Intent 1", parentId = 0)
        service.addIntent("Intent 2", parentId = 0)

        val all = service.getAll()
        // root + 2 user intents = 3
        assertEquals(3, all.size)
        assertTrue(all.none { it.isMeta() })
    }

    @Test
    fun `raw relationship consumption via VoluntasStreamConsumer`() {
        val consumer = service as VoluntasStreamConsumer
        val textLitId = service.literalStore.getOrCreate("Direct relationship intent")

        val rel = Relationship.newBuilder()
            .setId(100L)
            .addParticipants(VoluntasIds.INSTANTIATES)
            .addParticipants(VoluntasIds.STRING_INTENT_TYPE)
            .addParticipants(textLitId)
            .addParticipants(0L) // parent = root
            .build()

        val result = consumer.consume(rel)
        assertTrue(result.message.contains("created entity 100"))

        val intent = service.getById(100L)
        assertNotNull(intent)
        assertEquals("Direct relationship intent", intent!!.text())
    }

    @Test
    fun `persistence round-trip preserves parent relationships`(@TempDir tempDir: Path) {
        val testFile = tempDir.resolve("test_parents.pb").toString()

        val svc = VoluntasIntentService.new("Round trip test")
        val parent = svc.addIntent("Parent", parentId = 0)
        val child = svc.addIntent("Child", parentId = parent.id())

        svc.writeToFile(testFile)
        val loaded = VoluntasIntentService.fromFile(testFile)

        val loadedChild = loaded.getById(child.id())!!
        assertNotNull(loadedChild.parent())
        assertEquals(parent.id(), loadedChild.parent()!!.id())

        // Verify focal scope works after reload
        val scope = loaded.getFocalScope(parent.id())
        assertTrue(scope.children.map { it.id() }.contains(child.id()))
    }

    @Test
    fun `persistence round-trip preserves edits`(@TempDir tempDir: Path) {
        val testFile = tempDir.resolve("test_edits.pb").toString()

        val svc = VoluntasIntentService.new("Edit round trip")
        val intent = svc.addIntent("Original text", parentId = 0)
        svc.edit(intent.id(), "Edited text")

        svc.writeToFile(testFile)
        val loaded = VoluntasIntentService.fromFile(testFile)

        assertEquals("Edited text", loaded.getById(intent.id())!!.text())
    }

    @Test
    fun `persistence round-trip preserves moved parents`(@TempDir tempDir: Path) {
        val testFile = tempDir.resolve("test_moves.pb").toString()

        val svc = VoluntasIntentService.new("Move round trip")
        val p1 = svc.addIntent("Parent 1", parentId = 0)
        val p2 = svc.addIntent("Parent 2", parentId = 0)
        val child = svc.addIntent("Child", parentId = p1.id())
        svc.moveParent(child.id(), p2.id())

        svc.writeToFile(testFile)
        val loaded = VoluntasIntentService.fromFile(testFile)

        val loadedChild = loaded.getById(child.id())!!
        assertEquals(p2.id(), loadedChild.parent()!!.id())
    }

    @Test
    fun `bootstrap creates meta intents for type and field definitions`() {
        // Bootstrap creates entities 7 (type), 8 (text field)
        val typeEntity = service.getById(VoluntasIds.STRING_INTENT_TYPE)
        assertNotNull(typeEntity)
        assertTrue(typeEntity!!.isMeta())

        val textFieldEntity = service.getById(8L)
        assertNotNull(textFieldEntity)
        assertTrue(textFieldEntity!!.isMeta())
    }

    @Test
    fun `adding many intents and reloading preserves all`(@TempDir tempDir: Path) {
        val testFile = tempDir.resolve("test_many.pb").toString()
        val svc = VoluntasIntentService.new("Many intents")

        val ids = (1..10).map { i ->
            svc.addIntent("Intent $i", parentId = 0).id()
        }

        svc.writeToFile(testFile)
        val loaded = VoluntasIntentService.fromFile(testFile)

        // root + 10
        assertEquals(11, loaded.getAll().size)
        for (i in ids.indices) {
            val intent = loaded.getById(ids[i])!!
            assertEquals("Intent ${i + 1}", intent.text())
        }
    }

    @Test
    fun `setFieldValue preserves text and persists correctly`(@TempDir tempDir: Path) {
        val testFile = tempDir.resolve("test_setfield.pb").toString()
        val svc = VoluntasIntentService.new("Field value test")

        val intent = svc.addIntent("Task to complete", parentId = 0)
        svc.addField(intent.id(), "done", FieldType.FIELD_TYPE_BOOL)
        svc.setFieldValue(intent.id(), "done", true)

        // Verify text preserved in memory
        assertEquals("Task to complete", svc.getById(intent.id())!!.text())
        assertEquals(true, svc.getById(intent.id())!!.fieldValues()["done"])

        // Verify text preserved after round-trip
        svc.writeToFile(testFile)
        val loaded = VoluntasIntentService.fromFile(testFile)

        val loadedIntent = loaded.getById(intent.id())!!
        assertEquals("Task to complete", loadedIntent.text())
        assertEquals(true, loadedIntent.fieldValues()["done"])

        // Verify root text also preserved
        assertEquals("Field value test", loaded.getById(0L)!!.text())
    }

    // --- addParticipant tests ---

    @Test
    fun `addParticipant appends participant to end by default`() {
        val intent = service.addIntent("Target", parentId = 0)
        val other1 = service.addIntent("Participant 1", parentId = 0)
        val other2 = service.addIntent("Participant 2", parentId = 0)

        // intent already has participantIds = [0] (root as parent)
        service.addParticipant(intent.id(), other1.id())
        service.addParticipant(intent.id(), other2.id())

        val result = service.getById(intent.id())!!
        val pids = result.participantIds()
        assertEquals(3, pids.size)
        assertEquals(0L, pids[0])           // original parent
        assertEquals(other1.id(), pids[1])  // appended first
        assertEquals(other2.id(), pids[2])  // appended second
    }

    @Test
    fun `addParticipant inserts at specific index`() {
        val intent = service.addIntent("Target", parentId = 0)
        val other1 = service.addIntent("Participant 1", parentId = 0)
        val other2 = service.addIntent("Participant 2", parentId = 0)

        // intent starts with participantIds = [0]
        service.addParticipant(intent.id(), other1.id())  // [0, other1]
        service.addParticipant(intent.id(), other2.id(), index = 1) // [0, other2, other1]

        val result = service.getById(intent.id())!!
        val pids = result.participantIds()
        assertEquals(3, pids.size)
        assertEquals(0L, pids[0])
        assertEquals(other2.id(), pids[1])  // inserted at index 1
        assertEquals(other1.id(), pids[2])
    }

    @Test
    fun `addParticipant at index 0 prepends`() {
        val intent = service.addIntent("Target", parentId = 0)
        val other = service.addIntent("New first participant", parentId = 0)

        service.addParticipant(intent.id(), other.id(), index = 0)

        val pids = service.getById(intent.id())!!.participantIds()
        assertEquals(2, pids.size)
        assertEquals(other.id(), pids[0])  // prepended
        assertEquals(0L, pids[1])          // original parent shifted
    }

    @Test
    fun `addParticipant throws for non-existent intent`() {
        assertThrows(IllegalArgumentException::class.java) {
            service.addParticipant(999L, 0L)
        }
    }

    @Test
    fun `addParticipant persists across save and reload`(@TempDir tempDir: Path) {
        val testFile = tempDir.resolve("test_participants.pb").toString()
        val svc = VoluntasIntentService.new("Participant persistence")

        val intent = svc.addIntent("Target", parentId = 0)
        val p1 = svc.addIntent("P1", parentId = 0)
        val p2 = svc.addIntent("P2", parentId = 0)

        svc.addParticipant(intent.id(), p1.id())
        svc.addParticipant(intent.id(), p2.id())

        svc.writeToFile(testFile)
        val loaded = VoluntasIntentService.fromFile(testFile)

        val loadedIntent = loaded.getById(intent.id())!!
        val pids = loadedIntent.participantIds()
        assertEquals(3, pids.size)
        assertEquals(0L, pids[0])
        assertEquals(p1.id(), pids[1])
        assertEquals(p2.id(), pids[2])

        // Text should be preserved
        assertEquals("Target", loadedIntent.text())
    }

    @Test
    fun `addParticipant with index persists across save and reload`(@TempDir tempDir: Path) {
        val testFile = tempDir.resolve("test_participants_idx.pb").toString()
        val svc = VoluntasIntentService.new("Participant index persistence")

        val intent = svc.addIntent("Target", parentId = 0)
        val p1 = svc.addIntent("P1", parentId = 0)
        val p2 = svc.addIntent("P2", parentId = 0)

        svc.addParticipant(intent.id(), p1.id())
        svc.addParticipant(intent.id(), p2.id(), index = 1) // insert before p1

        svc.writeToFile(testFile)
        val loaded = VoluntasIntentService.fromFile(testFile)

        val pids = loaded.getById(intent.id())!!.participantIds()
        assertEquals(3, pids.size)
        assertEquals(0L, pids[0])
        assertEquals(p2.id(), pids[1])  // inserted at index 1
        assertEquals(p1.id(), pids[2])
    }

    @Test
    fun `participantIds returns first participant as parent`() {
        val parent = service.addIntent("Parent", parentId = 0)
        val child = service.addIntent("Child", parentId = parent.id())

        val pids = service.getById(child.id())!!.participantIds()
        assertEquals(1, pids.size)
        assertEquals(parent.id(), pids[0])

        // parent() should return the same as first participant
        assertEquals(parent.id(), service.getById(child.id())!!.parent()!!.id())
    }

    @Test
    fun `addParticipant updates childrenById for focal scope`() {
        val intent = service.addIntent("Target", parentId = 0)
        val other = service.addIntent("Other", parentId = 0)

        service.addParticipant(intent.id(), other.id())

        // intent should now appear as a child of other in focal scope
        val scope = service.getFocalScope(other.id())
        assertTrue(scope.children.map { it.id() }.contains(intent.id()))
    }
}
