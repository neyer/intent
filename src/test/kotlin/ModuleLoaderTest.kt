import com.intentevolved.com.intentevolved.FieldDetails
import com.intentevolved.com.intentevolved.voluntas.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import voluntas.v1.FieldType
import java.nio.file.Path

class ModuleLoaderTest {

    private lateinit var mainService: VoluntasIntentService

    @BeforeEach
    fun setup() {
        mainService = VoluntasIntentService.new("Main Stream Root")
    }

    private fun createSoftwareDevModule(@TempDir tempDir: Path): VoluntasModule {
        val moduleService = VoluntasIntentService.new("Software Development Module")
        val moduleRoot = 0L

        val reqType = moduleService.defineType("requirement", moduleRoot)
        moduleService.addField(reqType, "name", FieldType.FIELD_TYPE_STRING, required = true, description = "The name of the requirement")
        moduleService.addField(reqType, "description", FieldType.FIELD_TYPE_STRING, required = false, description = "Detailed description of the requirement")

        val sysType = moduleService.defineType("system", moduleRoot)
        moduleService.addField(sysType, "name", FieldType.FIELD_TYPE_STRING, required = true, description = "The name of the system")
        moduleService.addField(sysType, "fulfills", FieldType.FIELD_TYPE_INTENT_REF, required = false, description = "The requirement this system fulfills")

        val implType = moduleService.defineType("implementation", moduleRoot)
        moduleService.addField(implType, "name", FieldType.FIELD_TYPE_STRING, required = true, description = "The name of the implementation")
        moduleService.addField(implType, "implements", FieldType.FIELD_TYPE_INTENT_REF, required = false, description = "The system this implementation implements")

        val moduleFile = tempDir.resolve("software_dev.pb").toString()
        moduleService.writeToFile(moduleFile)

        return VoluntasModule.fromFile(moduleFile)
    }

    @Test
    fun `loading module into fresh stream creates all entities`(@TempDir tempDir: Path) {
        val module = createSoftwareDevModule(tempDir)
        val loader = ModuleLoader(mainService)
        val manifest = loader.loadModule(module)

        // Module root should exist
        val moduleRoot = mainService.getById(manifest.moduleEntityId)
        assertNotNull(moduleRoot)
        assertEquals("Software Development Module", moduleRoot!!.text())

        // All three types should exist
        val allEntities = mainService.getAllEntities()
        val typeNames = allEntities.values
            .filter { it.isMeta() && it.id() >= VoluntasIds.FIRST_USER_ENTITY }
            .filter { !it.text().startsWith("DefinesField:") && !it.text().startsWith("SetsField:") && !it.text().startsWith("Instance:") }
            .map { it.text() }
            .toSet()

        assertTrue("requirement" in typeNames, "Should have requirement type, got: $typeNames")
        assertTrue("system" in typeNames, "Should have system type, got: $typeNames")
        assertTrue("implementation" in typeNames, "Should have implementation type, got: $typeNames")

        // Verify fields on requirement type
        val reqEntity = allEntities.values.find { it.text() == "requirement" }!!
        val reqFields = reqEntity.fields()
        assertEquals(2, reqFields.size)
        assertEquals(FieldType.FIELD_TYPE_STRING, reqFields["name"]!!.fieldType)
        assertTrue(reqFields["name"]!!.required)
        assertEquals(FieldType.FIELD_TYPE_STRING, reqFields["description"]!!.fieldType)
        assertFalse(reqFields["description"]!!.required)

        // Verify fields on system type
        val sysEntity = allEntities.values.find { it.text() == "system" }!!
        assertEquals(FieldType.FIELD_TYPE_INTENT_REF, sysEntity.fields()["fulfills"]!!.fieldType)

        // Verify newly created count
        assertTrue(manifest.newlyCreated.isNotEmpty())
        assertTrue(manifest.alreadyExisted.isEmpty())
    }

    @Test
    fun `loading same module twice is idempotent`(@TempDir tempDir: Path) {
        val module = createSoftwareDevModule(tempDir)
        val loader = ModuleLoader(mainService)

        val manifest1 = loader.loadModule(module)
        val entityCountAfterFirst = mainService.getAllEntities().size

        // Load again — should reload from same file
        val module2 = createSoftwareDevModule(tempDir)
        val manifest2 = loader.loadModule(module2)
        val entityCountAfterSecond = mainService.getAllEntities().size

        // No new entities should have been created
        assertEquals(entityCountAfterFirst, entityCountAfterSecond,
            "Second load should not create new entities")
        assertTrue(manifest2.newlyCreated.isEmpty(),
            "Second load should create nothing new")
        assertTrue(manifest2.alreadyExisted.isNotEmpty(),
            "Second load should find everything existing")

        // Mappings should be logically equivalent (same main-stream IDs for the same types)
        assertEquals(manifest1.moduleEntityId, manifest2.moduleEntityId)
    }

    @Test
    fun `loading module into stream with conflicting type throws`(@TempDir tempDir: Path) {
        // Manually create a "requirement" type with different fields
        val reqTypeId = mainService.defineType("requirement")
        mainService.addField(reqTypeId, "title", FieldType.FIELD_TYPE_STRING, required = true)
        // This has "title" instead of "name", so it should conflict

        val module = createSoftwareDevModule(tempDir)
        val loader = ModuleLoader(mainService)

        val ex = assertThrows(ModuleConflictException::class.java) {
            loader.loadModule(module)
        }
        assertTrue(ex.message!!.contains("requirement"), "Exception should mention the type name")
    }

    @Test
    fun `module entities survive persistence round-trip`(@TempDir tempDir: Path) {
        val module = createSoftwareDevModule(tempDir)
        val loader = ModuleLoader(mainService)
        loader.loadModule(module)

        // Save and reload
        val mainFile = tempDir.resolve("main_stream.pb").toString()
        mainService.writeToFile(mainFile)
        val reloadedService = VoluntasIntentService.fromFile(mainFile)

        // Load module again into reloaded service — should be idempotent
        val module2 = createSoftwareDevModule(tempDir)
        val loader2 = ModuleLoader(reloadedService)
        val manifest = loader2.loadModule(module2)

        assertTrue(manifest.newlyCreated.isEmpty(),
            "After round-trip, module should already exist")
        assertTrue(manifest.alreadyExisted.isNotEmpty())

        // Verify types still exist
        val reqEntity = reloadedService.getAllEntities().values.find { it.text() == "requirement" }
        assertNotNull(reqEntity)
        assertEquals(2, reqEntity!!.fields().size)
    }

    @Test
    fun `ID remapping produces valid references`(@TempDir tempDir: Path) {
        // Add some user entities first to push IDs forward
        mainService.addIntent("User intent 1", parentId = 0)
        mainService.addIntent("User intent 2", parentId = 0)
        mainService.addIntent("User intent 3", parentId = 0)

        val module = createSoftwareDevModule(tempDir)
        val loader = ModuleLoader(mainService)
        val manifest = loader.loadModule(module)

        // All mapped main-stream IDs should point to valid entities
        for ((_, mainId) in manifest.idMapping) {
            if (mainId < VoluntasIds.FIRST_USER_ENTITY && mainId != VoluntasIds.ROOT_INTENT) continue
            val entity = mainService.getById(mainId) ?: mainService.getAllEntities()[mainId]
            assertNotNull(entity, "Mapped ID $mainId should exist in main stream")
        }

        // Module entities should not conflict with pre-existing user entities
        val userIntents = mainService.getAll()
        assertTrue(userIntents.any { it.text() == "User intent 1" })
        assertTrue(userIntents.any { it.text() == "User intent 2" })
        assertTrue(userIntents.any { it.text() == "User intent 3" })
    }

    @Test
    fun `module root is created as intent in main stream`(@TempDir tempDir: Path) {
        val module = createSoftwareDevModule(tempDir)
        val loader = ModuleLoader(mainService)
        val manifest = loader.loadModule(module)

        val moduleRoot = mainService.getById(manifest.moduleEntityId)
        assertNotNull(moduleRoot)
        assertEquals("Software Development Module", moduleRoot!!.text())
        assertFalse(moduleRoot.isMeta())
    }

    @Test
    fun `type entities have module as participant`(@TempDir tempDir: Path) {
        val module = createSoftwareDevModule(tempDir)
        val loader = ModuleLoader(mainService)
        val manifest = loader.loadModule(module)

        val allEntities = mainService.getAllEntities()
        val typeEntities = allEntities.values.filter {
            it.text() in listOf("requirement", "system", "implementation")
        }

        assertEquals(3, typeEntities.size, "Should have 3 type entities")
        for (typeEntity in typeEntities) {
            val participantIds = typeEntity.participantIds()
            assertTrue(participantIds.contains(manifest.moduleEntityId),
                "Type '${typeEntity.text()}' should have module entity ${manifest.moduleEntityId} as participant, " +
                "but has participants: $participantIds")
        }
    }
}
