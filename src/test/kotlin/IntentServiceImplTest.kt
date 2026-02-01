import com.intentevolved.com.intentevolved.Intent
import com.intentevolved.com.intentevolved.IntentService
import com.intentevolved.com.intentevolved.IntentServiceImpl
import com.intentevolved.com.intentevolved.IntentStreamConsumer
import com.intentevolved.com.intentevolved.IntentStateProvider
import com.intentevolved.com.intentevolved.terminal.DoCommand
import com.intentevolved.AddField
import com.intentevolved.FieldType
import com.intentevolved.Op
import com.intentevolved.SetFieldValue
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

    // AddField tests

    @Test
    fun `addField adds a field to an intent`() {
        val intent = service.addIntent("Test intent", parentId = 0)
        val consumer = service as IntentStreamConsumer

        val addFieldOp = Op.newBuilder()
            .setAddField(
                AddField.newBuilder()
                    .setIntentId(intent.id())
                    .setFieldName("priority")
                    .setFieldType(FieldType.FIELD_TYPE_INT32)
                    .setRequired(true)
                    .setDescription("The priority level")
            )
            .build()

        consumer.consume(addFieldOp)

        val updatedIntent = service.getById(intent.id())!!
        val fields = updatedIntent.fields()

        assertEquals(1, fields.size)
        assertTrue(fields.containsKey("priority"))
        assertEquals(FieldType.FIELD_TYPE_INT32, fields["priority"]!!.fieldType)
        assertEquals(true, fields["priority"]!!.required)
        assertEquals("The priority level", fields["priority"]!!.description)
    }

    @Test
    fun `addField with optional fields uses defaults`() {
        val intent = service.addIntent("Test intent", parentId = 0)
        val consumer = service as IntentStreamConsumer

        val addFieldOp = Op.newBuilder()
            .setAddField(
                AddField.newBuilder()
                    .setIntentId(intent.id())
                    .setFieldName("notes")
                    .setFieldType(FieldType.FIELD_TYPE_STRING)
            )
            .build()

        consumer.consume(addFieldOp)

        val fields = service.getById(intent.id())!!.fields()

        assertEquals(false, fields["notes"]!!.required)
        assertNull(fields["notes"]!!.description)
    }

    @Test
    fun `addField throws for non-existent intent`() {
        val consumer = service as IntentStreamConsumer

        val addFieldOp = Op.newBuilder()
            .setAddField(
                AddField.newBuilder()
                    .setIntentId(999L)
                    .setFieldName("priority")
                    .setFieldType(FieldType.FIELD_TYPE_INT32)
            )
            .build()

        assertThrows(IllegalArgumentException::class.java) {
            consumer.consume(addFieldOp)
        }
    }

    // SetFieldValue tests

    @Test
    fun `setFieldValue sets a string value`() {
        val intent = service.addIntent("Test intent", parentId = 0)
        val consumer = service as IntentStreamConsumer

        // First add the field
        consumer.consume(Op.newBuilder()
            .setAddField(AddField.newBuilder()
                .setIntentId(intent.id())
                .setFieldName("name")
                .setFieldType(FieldType.FIELD_TYPE_STRING))
            .build())

        // Then set the value
        consumer.consume(Op.newBuilder()
            .setSetFieldValue(SetFieldValue.newBuilder()
                .setIntentId(intent.id())
                .setFieldName("name")
                .setStringValue("Test Name"))
            .build())

        val values = service.getById(intent.id())!!.fieldValues()
        assertEquals("Test Name", values["name"])
    }

    @Test
    fun `setFieldValue sets an int32 value`() {
        val intent = service.addIntent("Test intent", parentId = 0)
        val consumer = service as IntentStreamConsumer

        consumer.consume(Op.newBuilder()
            .setAddField(AddField.newBuilder()
                .setIntentId(intent.id())
                .setFieldName("count")
                .setFieldType(FieldType.FIELD_TYPE_INT32))
            .build())

        consumer.consume(Op.newBuilder()
            .setSetFieldValue(SetFieldValue.newBuilder()
                .setIntentId(intent.id())
                .setFieldName("count")
                .setInt32Value(42))
            .build())

        val values = service.getById(intent.id())!!.fieldValues()
        assertEquals(42, values["count"])
    }

    @Test
    fun `setFieldValue sets an int64 value`() {
        val intent = service.addIntent("Test intent", parentId = 0)
        val consumer = service as IntentStreamConsumer

        consumer.consume(Op.newBuilder()
            .setAddField(AddField.newBuilder()
                .setIntentId(intent.id())
                .setFieldName("bigNumber")
                .setFieldType(FieldType.FIELD_TYPE_INT64))
            .build())

        consumer.consume(Op.newBuilder()
            .setSetFieldValue(SetFieldValue.newBuilder()
                .setIntentId(intent.id())
                .setFieldName("bigNumber")
                .setInt64Value(9999999999L))
            .build())

        val values = service.getById(intent.id())!!.fieldValues()
        assertEquals(9999999999L, values["bigNumber"])
    }

    @Test
    fun `setFieldValue sets a bool value`() {
        val intent = service.addIntent("Test intent", parentId = 0)
        val consumer = service as IntentStreamConsumer

        consumer.consume(Op.newBuilder()
            .setAddField(AddField.newBuilder()
                .setIntentId(intent.id())
                .setFieldName("active")
                .setFieldType(FieldType.FIELD_TYPE_BOOL))
            .build())

        consumer.consume(Op.newBuilder()
            .setSetFieldValue(SetFieldValue.newBuilder()
                .setIntentId(intent.id())
                .setFieldName("active")
                .setBoolValue(true))
            .build())

        val values = service.getById(intent.id())!!.fieldValues()
        assertEquals(true, values["active"])
    }

    @Test
    fun `setFieldValue sets a float value`() {
        val intent = service.addIntent("Test intent", parentId = 0)
        val consumer = service as IntentStreamConsumer

        consumer.consume(Op.newBuilder()
            .setAddField(AddField.newBuilder()
                .setIntentId(intent.id())
                .setFieldName("rating")
                .setFieldType(FieldType.FIELD_TYPE_FLOAT))
            .build())

        consumer.consume(Op.newBuilder()
            .setSetFieldValue(SetFieldValue.newBuilder()
                .setIntentId(intent.id())
                .setFieldName("rating")
                .setFloatValue(4.5f))
            .build())

        val values = service.getById(intent.id())!!.fieldValues()
        assertEquals(4.5f, values["rating"])
    }

    @Test
    fun `setFieldValue sets a double value`() {
        val intent = service.addIntent("Test intent", parentId = 0)
        val consumer = service as IntentStreamConsumer

        consumer.consume(Op.newBuilder()
            .setAddField(AddField.newBuilder()
                .setIntentId(intent.id())
                .setFieldName("precise")
                .setFieldType(FieldType.FIELD_TYPE_DOUBLE))
            .build())

        consumer.consume(Op.newBuilder()
            .setSetFieldValue(SetFieldValue.newBuilder()
                .setIntentId(intent.id())
                .setFieldName("precise")
                .setDoubleValue(3.14159265359))
            .build())

        val values = service.getById(intent.id())!!.fieldValues()
        assertEquals(3.14159265359, values["precise"])
    }

    @Test
    fun `setFieldValue sets a timestamp value`() {
        val intent = service.addIntent("Test intent", parentId = 0)
        val consumer = service as IntentStreamConsumer

        consumer.consume(Op.newBuilder()
            .setAddField(AddField.newBuilder()
                .setIntentId(intent.id())
                .setFieldName("dueDate")
                .setFieldType(FieldType.FIELD_TYPE_TIMESTAMP))
            .build())

        val timestamp = 1704067200000000000L // 2024-01-01 in nanos
        consumer.consume(Op.newBuilder()
            .setSetFieldValue(SetFieldValue.newBuilder()
                .setIntentId(intent.id())
                .setFieldName("dueDate")
                .setTimestampValue(timestamp))
            .build())

        val values = service.getById(intent.id())!!.fieldValues()
        assertEquals(timestamp, values["dueDate"])
    }

    @Test
    fun `setFieldValue sets an intent ref value`() {
        val intent1 = service.addIntent("Test intent 1", parentId = 0)
        val intent2 = service.addIntent("Test intent 2", parentId = 0)
        val consumer = service as IntentStreamConsumer

        consumer.consume(Op.newBuilder()
            .setAddField(AddField.newBuilder()
                .setIntentId(intent1.id())
                .setFieldName("relatedIntent")
                .setFieldType(FieldType.FIELD_TYPE_INTENT_REF))
            .build())

        consumer.consume(Op.newBuilder()
            .setSetFieldValue(SetFieldValue.newBuilder()
                .setIntentId(intent1.id())
                .setFieldName("relatedIntent")
                .setIntentRefValue(intent2.id()))
            .build())

        val values = service.getById(intent1.id())!!.fieldValues()
        assertEquals(intent2.id(), values["relatedIntent"])
    }

    @Test
    fun `setFieldValue throws for type mismatch`() {
        val intent = service.addIntent("Test intent", parentId = 0)
        val consumer = service as IntentStreamConsumer

        consumer.consume(Op.newBuilder()
            .setAddField(AddField.newBuilder()
                .setIntentId(intent.id())
                .setFieldName("count")
                .setFieldType(FieldType.FIELD_TYPE_INT32))
            .build())

        assertThrows(IllegalArgumentException::class.java) {
            consumer.consume(Op.newBuilder()
                .setSetFieldValue(SetFieldValue.newBuilder()
                    .setIntentId(intent.id())
                    .setFieldName("count")
                    .setStringValue("not a number"))
                .build())
        }
    }

    @Test
    fun `setFieldValue throws for non-existent field`() {
        val intent = service.addIntent("Test intent", parentId = 0)
        val consumer = service as IntentStreamConsumer

        assertThrows(IllegalArgumentException::class.java) {
            consumer.consume(Op.newBuilder()
                .setSetFieldValue(SetFieldValue.newBuilder()
                    .setIntentId(intent.id())
                    .setFieldName("nonExistent")
                    .setStringValue("value"))
                .build())
        }
    }

    @Test
    fun `setFieldValue throws for non-existent intent`() {
        val consumer = service as IntentStreamConsumer

        assertThrows(IllegalArgumentException::class.java) {
            consumer.consume(Op.newBuilder()
                .setSetFieldValue(SetFieldValue.newBuilder()
                    .setIntentId(999L)
                    .setFieldName("field")
                    .setStringValue("value"))
                .build())
        }
    }

    @Test
    fun `setFieldValue throws for non-existent intent ref`() {
        val intent = service.addIntent("Test intent", parentId = 0)
        val consumer = service as IntentStreamConsumer

        consumer.consume(Op.newBuilder()
            .setAddField(AddField.newBuilder()
                .setIntentId(intent.id())
                .setFieldName("relatedIntent")
                .setFieldType(FieldType.FIELD_TYPE_INTENT_REF))
            .build())

        assertThrows(IllegalArgumentException::class.java) {
            consumer.consume(Op.newBuilder()
                .setSetFieldValue(SetFieldValue.newBuilder()
                    .setIntentId(intent.id())
                    .setFieldName("relatedIntent")
                    .setIntentRefValue(999L))
                .build())
        }
    }

    @Test
    fun `fields and values persist after save and reload`(@TempDir tempDir: Path) {
        val testFile = tempDir.resolve("test_fields.pb").toString()
        val localService = IntentServiceImpl.new("Test fields persistence")
        val consumer = localService as IntentStreamConsumer

        val intent = localService.addIntent("Intent with fields", parentId = 0)

        // Add fields
        consumer.consume(Op.newBuilder()
            .setAddField(AddField.newBuilder()
                .setIntentId(intent.id())
                .setFieldName("priority")
                .setFieldType(FieldType.FIELD_TYPE_INT32)
                .setRequired(true)
                .setDescription("Priority level"))
            .build())

        consumer.consume(Op.newBuilder()
            .setAddField(AddField.newBuilder()
                .setIntentId(intent.id())
                .setFieldName("notes")
                .setFieldType(FieldType.FIELD_TYPE_STRING))
            .build())

        // Set values
        consumer.consume(Op.newBuilder()
            .setSetFieldValue(SetFieldValue.newBuilder()
                .setIntentId(intent.id())
                .setFieldName("priority")
                .setInt32Value(5))
            .build())

        consumer.consume(Op.newBuilder()
            .setSetFieldValue(SetFieldValue.newBuilder()
                .setIntentId(intent.id())
                .setFieldName("notes")
                .setStringValue("Important task"))
            .build())

        // Save and reload
        localService.writeToFile(testFile)
        val loadedService = IntentServiceImpl.fromFile(testFile)

        val loadedIntent = loadedService.getById(intent.id())!!

        // Verify fields
        val fields = loadedIntent.fields()
        assertEquals(2, fields.size)
        assertEquals(FieldType.FIELD_TYPE_INT32, fields["priority"]!!.fieldType)
        assertEquals(true, fields["priority"]!!.required)
        assertEquals("Priority level", fields["priority"]!!.description)
        assertEquals(FieldType.FIELD_TYPE_STRING, fields["notes"]!!.fieldType)

        // Verify values
        val values = loadedIntent.fieldValues()
        assertEquals(5, values["priority"])
        assertEquals("Important task", values["notes"])
    }

    // DoCommand tests

    @Test
    fun `do command adds done field and sets it to true`() {
        val localService = IntentServiceImpl.new("Test do command")
        val intent = localService.addIntent("Task to complete", parentId = 0)

        val doCommand = DoCommand()
        val result = doCommand.process(
            intent.id().toString(),
            localService as IntentStreamConsumer,
            localService as IntentStateProvider,
            0L
        )

        assertEquals("Marked intent ${intent.id()} as done", result.message)

        val updatedIntent = localService.getById(intent.id())!!

        // Verify field was added
        val fields = updatedIntent.fields()
        assertTrue(fields.containsKey("done"))
        assertEquals(FieldType.FIELD_TYPE_BOOL, fields["done"]!!.fieldType)

        // Verify value was set
        val values = updatedIntent.fieldValues()
        assertEquals(true, values["done"])
    }

    @Test
    fun `do command works when done field already exists`() {
        val localService = IntentServiceImpl.new("Test do command")
        val consumer = localService as IntentStreamConsumer
        val intent = localService.addIntent("Task to complete", parentId = 0)

        // Manually add the done field first
        consumer.consume(Op.newBuilder()
            .setAddField(AddField.newBuilder()
                .setIntentId(intent.id())
                .setFieldName("done")
                .setFieldType(FieldType.FIELD_TYPE_BOOL))
            .build())

        // Set it to false initially
        consumer.consume(Op.newBuilder()
            .setSetFieldValue(SetFieldValue.newBuilder()
                .setIntentId(intent.id())
                .setFieldName("done")
                .setBoolValue(false))
            .build())

        // Now run the do command
        val doCommand = DoCommand()
        val result = doCommand.process(
            intent.id().toString(),
            consumer,
            localService as IntentStateProvider,
            0L
        )

        assertEquals("Marked intent ${intent.id()} as done", result.message)

        // Verify value was set to true
        val values = localService.getById(intent.id())!!.fieldValues()
        assertEquals(true, values["done"])
    }

    @Test
    fun `do command returns error for non-existent intent`() {
        val localService = IntentServiceImpl.new("Test do command")

        val doCommand = DoCommand()
        val result = doCommand.process(
            "999",
            localService as IntentStreamConsumer,
            localService as IntentStateProvider,
            0L
        )

        assertEquals("No intent with id 999", result.message)
    }

    @Test
    fun `do command returns error for invalid intent id`() {
        val localService = IntentServiceImpl.new("Test do command")

        val doCommand = DoCommand()
        val result = doCommand.process(
            "not-a-number",
            localService as IntentStreamConsumer,
            localService as IntentStateProvider,
            0L
        )

        assertEquals("Do command requires an intent id", result.message)
    }
}
