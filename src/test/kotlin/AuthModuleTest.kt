import com.apxhard.voluntas.voluntas.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import voluntas.v1.FieldType
import java.nio.file.Path

class AuthModuleTest {

    private fun buildAuthModule() = buildModule("auth") {
        command("user") {
            field("auth-token", string, required = true, description = "Authentication token for this user")
        }
        command("provide-user-token")
        mutationCommand("change-auth-token") {
            defineField("auth-token", string)
            setFieldRef("auth-token", textArg)
        }
        type("auth/created-by") {
            field("user", intentRef, required = true, description = "The user who created this intent")
        }
    }

    private fun loadedService(@TempDir tempDir: Path): VoluntasIntentService {
        val moduleService = buildAuthModule()
        val moduleFile = tempDir.resolve("auth.pb").toString()
        moduleService.writeToFile(moduleFile)
        val module = VoluntasModule.fromFile(moduleFile)
        val mainService = VoluntasIntentService.new("Test Main")
        ModuleLoader(mainService).loadModule(module)
        return mainService
    }

    // -------------------------------------------------------------------------
    // Module structure
    // -------------------------------------------------------------------------

    @Test
    fun `user command type exists at auth-user with required auth-token string field`() {
        val service = buildAuthModule()
        val typeId = service.getEntityByPath("/auth/user")
        assertNotNull(typeId, "auth/user type should exist")
        val fields = service.getById(typeId!!)!!.fields()
        assertTrue("auth-token" in fields, "user type should have auth-token field")
        assertEquals(FieldType.FIELD_TYPE_STRING, fields["auth-token"]!!.fieldType)
        assertTrue(fields["auth-token"]!!.required)
    }

    @Test
    fun `provide-user-token command type exists`() {
        val service = buildAuthModule()
        assertNotNull(service.getEntityByPath("/auth/provide-user-token"),
            "auth/provide-user-token type should exist")
    }

    @Test
    fun `created-by type exists at auth-created-by with required user intentRef field`() {
        val service = buildAuthModule()
        val typeId = service.getEntityByPath("/auth/created-by")
        assertNotNull(typeId, "auth/created-by type should exist")
        val fields = service.getById(typeId!!)!!.fields()
        assertTrue("user" in fields, "created-by type should have user field")
        assertEquals(FieldType.FIELD_TYPE_INTENT_REF, fields["user"]!!.fieldType)
        assertTrue(fields["user"]!!.required)
    }

    @Test
    fun `change-auth-token is registered as a command annotation`() {
        val service = buildAuthModule()
        val commands = service.getCommandAnnotations().map { it.first }
        assertTrue("change-auth-token" in commands,
            "change-auth-token should be a registered command, got: $commands")
    }

    @Test
    fun `change-auth-token does not create a visible subtype`() {
        val service = buildAuthModule()
        val allEntities = service.getAllEntities()
        val visibleTypes = allEntities.values.filter { it.isMeta() && it.text() == "auth/change-auth-token" }
        assertTrue(visibleTypes.isEmpty(),
            "change-auth-token should not produce a visible type (it is a mutationCommand)")
    }

    @Test
    fun `getCommandAnnotations returns user, provide-user-token, and change-auth-token`() {
        val service = buildAuthModule()
        val commands = service.getCommandAnnotations().map { it.first }.toSet()
        assertEquals(setOf("user", "provide-user-token", "change-auth-token"), commands)
    }

    // -------------------------------------------------------------------------
    // isAuthModuleLoaded
    // -------------------------------------------------------------------------

    @Test
    fun `isAuthModuleLoaded returns false on fresh service`() {
        assertFalse(VoluntasIntentService.new("bare").isAuthModuleLoaded())
    }

    @Test
    fun `isAuthModuleLoaded returns true after buildModule`() {
        assertTrue(buildAuthModule().isAuthModuleLoaded())
    }

    @Test
    fun `isAuthModuleLoaded returns true after loading via ModuleLoader`(@TempDir tempDir: Path) {
        assertTrue(loadedService(tempDir).isAuthModuleLoaded())
    }

    // -------------------------------------------------------------------------
    // bootstrapRootUser
    // -------------------------------------------------------------------------

    @Test
    fun `bootstrapRootUser creates root user with auth-token root`() {
        val service = buildAuthModule()
        service.bootstrapRootUser()
        assertNotNull(service.findUserByCredentials("root", "root"),
            "root user with token 'root' should exist after bootstrap")
    }

    @Test
    fun `bootstrapRootUser is idempotent`() {
        val service = buildAuthModule()
        service.bootstrapRootUser()
        service.bootstrapRootUser()
        val userTypeId = service.getEntityByPath("/auth/user")!!
        val rootCount = service.getInstancesOfType(userTypeId).count { id ->
            service.getById(id)?.text() == "root"
        }
        assertEquals(1, rootCount, "root user should only be created once")
    }

    @Test
    fun `bootstrapRootUser is no-op without auth module`() {
        val service = VoluntasIntentService.new("bare")
        val sizeBefore = service.getAllEntities().size
        assertDoesNotThrow { service.bootstrapRootUser() }
        assertEquals(sizeBefore, service.getAllEntities().size)
    }

    // -------------------------------------------------------------------------
    // findUserByCredentials
    // -------------------------------------------------------------------------

    @Test
    fun `findUserByCredentials returns null without auth module`() {
        assertNull(VoluntasIntentService.new("bare").findUserByCredentials("alice", "secret"))
    }

    @Test
    fun `findUserByCredentials returns user id when credentials match`() {
        val service = buildAuthModule()
        val userTypeId = service.getEntityByPath("/auth/user")!!
        val user = service.addIntentOfType(userTypeId, "alice", VoluntasIds.ROOT_INTENT)
        service.setFieldValue(user.id(), "auth-token", "secret")

        assertEquals(user.id(), service.findUserByCredentials("alice", "secret"))
    }

    @Test
    fun `findUserByCredentials returns null for wrong token`() {
        val service = buildAuthModule()
        val userTypeId = service.getEntityByPath("/auth/user")!!
        val user = service.addIntentOfType(userTypeId, "alice", VoluntasIds.ROOT_INTENT)
        service.setFieldValue(user.id(), "auth-token", "secret")

        assertNull(service.findUserByCredentials("alice", "wrongtoken"))
    }

    @Test
    fun `findUserByCredentials returns null for unknown username`() {
        val service = buildAuthModule()
        service.bootstrapRootUser()
        assertNull(service.findUserByCredentials("nobody", "root"))
    }

    @Test
    fun `findUserByCredentials distinguishes users with different tokens`() {
        val service = buildAuthModule()
        val userTypeId = service.getEntityByPath("/auth/user")!!
        val alice = service.addIntentOfType(userTypeId, "alice", VoluntasIds.ROOT_INTENT)
        val bob = service.addIntentOfType(userTypeId, "bob", VoluntasIds.ROOT_INTENT)
        service.setFieldValue(alice.id(), "auth-token", "alice-token")
        service.setFieldValue(bob.id(), "auth-token", "bob-token")

        assertEquals(alice.id(), service.findUserByCredentials("alice", "alice-token"))
        assertEquals(bob.id(), service.findUserByCredentials("bob", "bob-token"))
        assertNull(service.findUserByCredentials("alice", "bob-token"))
    }

    // -------------------------------------------------------------------------
    // findUserByName
    // -------------------------------------------------------------------------

    @Test
    fun `findUserByName returns null without auth module`() {
        assertNull(VoluntasIntentService.new("bare").findUserByName("alice"))
    }

    @Test
    fun `findUserByName returns user id for known user`() {
        val service = buildAuthModule()
        val userTypeId = service.getEntityByPath("/auth/user")!!
        val user = service.addIntentOfType(userTypeId, "alice", VoluntasIds.ROOT_INTENT)
        assertEquals(user.id(), service.findUserByName("alice"))
    }

    @Test
    fun `findUserByName returns null for unknown user`() {
        val service = buildAuthModule()
        service.bootstrapRootUser()
        assertNull(service.findUserByName("nobody"))
    }

    // -------------------------------------------------------------------------
    // createCreatedBy
    // -------------------------------------------------------------------------

    @Test
    fun `createCreatedBy attaches a created-by meta intent to the intent`() {
        val service = buildAuthModule()
        val userTypeId = service.getEntityByPath("/auth/user")!!
        val user = service.addIntentOfType(userTypeId, "alice", VoluntasIds.ROOT_INTENT)
        val intent = service.addIntent("some task", VoluntasIds.ROOT_INTENT)

        val sizeBefore = service.getAllEntities().size
        service.createCreatedBy(intent.id(), user.id())

        assertTrue(service.getAllEntities().size > sizeBefore,
            "createCreatedBy should add a new created-by meta entity")
    }

    @Test
    fun `createCreatedBy records the correct user reference`() {
        val service = buildAuthModule()
        val userTypeId = service.getEntityByPath("/auth/user")!!
        val user = service.addIntentOfType(userTypeId, "alice", VoluntasIds.ROOT_INTENT)
        val intent = service.addIntent("some task", VoluntasIds.ROOT_INTENT)

        service.createCreatedBy(intent.id(), user.id())

        val createdByTypeId = service.getEntityByPath("/auth/created-by")!!
        val instances = service.getInstancesOfType(createdByTypeId)
        assertTrue(instances.isNotEmpty(), "a created-by instance should exist")
        val createdByEntity = service.getById(instances.first())!!
        assertEquals(user.id(), createdByEntity.fieldValues()["user"],
            "created-by instance should reference alice's user id")
    }

    @Test
    fun `createCreatedBy is no-op without auth module`() {
        val service = VoluntasIntentService.new("bare")
        val intent = service.addIntent("task", VoluntasIds.ROOT_INTENT)
        val sizeBefore = service.getAllEntities().size
        assertDoesNotThrow { service.createCreatedBy(intent.id(), 99999L) }
        assertEquals(sizeBefore, service.getAllEntities().size)
    }

    // -------------------------------------------------------------------------
    // change-auth-token mutation
    // -------------------------------------------------------------------------

    @Test
    fun `change-auth-token macro updates auth-token on target user`() {
        val service = buildAuthModule()
        val userTypeId = service.getEntityByPath("/auth/user")!!
        val user = service.addIntentOfType(userTypeId, "alice", VoluntasIds.ROOT_INTENT)
        service.setFieldValue(user.id(), "auth-token", "old-token")

        val macroId = service.getCommandAnnotations()
            .find { it.first == "change-auth-token" }!!.second
        val newTokenLitId = service.literalStore.getOrCreate("new-token")
        service.invokeMacro(macroId, listOf(newTokenLitId, user.id()))

        assertEquals("new-token", service.getById(user.id())!!.fieldValues()["auth-token"])
        assertNull(service.findUserByCredentials("alice", "old-token"),
            "old token should no longer work")
        assertNotNull(service.findUserByCredentials("alice", "new-token"),
            "new token should now work")
    }

    // -------------------------------------------------------------------------
    // Module loading via ModuleLoader
    // -------------------------------------------------------------------------

    @Test
    fun `auth module loads cleanly into a fresh main service`(@TempDir tempDir: Path) {
        val service = loadedService(tempDir)
        assertNotNull(service.getEntityByPath("/auth/user"))
        assertNotNull(service.getEntityByPath("/auth/provide-user-token"))
        assertNotNull(service.getEntityByPath("/auth/created-by"))
        val commands = service.getCommandAnnotations().map { it.first }.toSet()
        assertEquals(setOf("user", "provide-user-token", "change-auth-token"), commands)
    }

    @Test
    fun `loading auth module is idempotent`(@TempDir tempDir: Path) {
        val moduleService = buildAuthModule()
        val moduleFile = tempDir.resolve("auth.pb").toString()
        moduleService.writeToFile(moduleFile)

        val mainService = VoluntasIntentService.new("Test Main")
        val loader = ModuleLoader(mainService)

        val manifest1 = loader.loadModule(VoluntasModule.fromFile(moduleFile))
        val sizeAfterFirst = mainService.getAllEntities().size

        val manifest2 = loader.loadModule(VoluntasModule.fromFile(moduleFile))
        assertEquals(sizeAfterFirst, mainService.getAllEntities().size,
            "second load should not create new entities")
        assertTrue(manifest2.newlyCreated.isEmpty())
        assertTrue(manifest1.alreadyExisted.isEmpty())
        assertTrue(manifest2.alreadyExisted.isNotEmpty())
    }

    @Test
    fun `service methods work after round-trip serialization`(@TempDir tempDir: Path) {
        val service = buildAuthModule()
        service.bootstrapRootUser()

        val file = tempDir.resolve("stream.pb").toString()
        service.writeToFile(file)
        val reloaded = VoluntasIntentService.fromFile(file)

        assertTrue(reloaded.isAuthModuleLoaded())
        assertNotNull(reloaded.findUserByCredentials("root", "root"))
        assertNotNull(reloaded.findUserByName("root"))
    }
}
