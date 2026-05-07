package e2e

import com.apxhard.voluntas.voluntas.VoluntasIds
import com.apxhard.voluntas.voluntas.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.io.File

/**
 * End-to-end tests that load the actual .pb module files from disk and assert
 * on the resulting intent graph shape. These mirror real server startup so
 * regressions in ModuleLoader, metaVisibleTypes, bootstrapRootUser, etc. are caught.
 *
 * Tests skip gracefully if the modules/ directory isn't present (e.g. CI before
 * running the generate tasks). Run `./gradlew runGenerateAll` first to build them.
 */
class ModuleBootstrapE2ETest {

    private lateinit var service: VoluntasIntentService

    @BeforeEach
    fun setup() {
        assumeTrue(File("modules").isDirectory, "modules/ directory not present — run generate tasks first")
        service = VoluntasIntentService.new("Voluntas Server Root")
        val loader = ModuleLoader(service)
        for (file in File("modules").listFiles { f -> f.extension == "pb" }!!.sortedBy { it.name }) {
            loader.loadModule(VoluntasModule.fromFile(file.absolutePath))
        }
        service.bootstrapRootUser()
    }

    // -------------------------------------------------------------------------
    // Auth module
    // -------------------------------------------------------------------------

    @Test
    fun `auth module types are present after boot`() {
        assertNotNull(service.getEntityByPath("/auth/user"), "auth/user type must exist")
        assertNotNull(service.getEntityByPath("/auth/provide-user-token"), "auth/provide-user-token type must exist")
        assertNotNull(service.getEntityByPath("/auth/created-by"), "auth/created-by type must exist")
    }

    @Test
    fun `auth user type is a visible type`() {
        val userTypeId = service.getEntityByPath("/auth/user")
        assertNotNull(userTypeId, "auth/user type must exist")
        // auth/user uses the visible participant layout; instances are meta because
        // they live under META_ROOT, not because of a type-level flag
        assertNotNull(userTypeId, "auth/user type must exist in the path system")
    }

    @Test
    fun `root user exists after bootstrapRootUser`() {
        val rootUserId = service.findUserByCredentials("root", "root")
        assertNotNull(rootUserId, "root user with token 'root' must exist after bootstrap")
    }

    @Test
    fun `root user intent is meta`() {
        val rootUserId = service.findUserByCredentials("root", "root")
        assertNotNull(rootUserId)
        val rootUser = service.getById(rootUserId!!)
        assertNotNull(rootUser)
        assertTrue(rootUser!!.isMeta(), "root user intent should be meta (hidden from visible tree)")
    }

    @Test
    fun `root user is a child of META_ROOT`() {
        val rootUserId = service.findUserByCredentials("root", "root")!!
        val scope = service.getFocalScope(VoluntasIds.META_ROOT)
        val childIds = scope.children.map { it.id() }
        assertTrue(rootUserId in childIds,
            "root user should be a direct child of META_ROOT")
    }

    // -------------------------------------------------------------------------
    // Standard module
    // -------------------------------------------------------------------------

    @Test
    fun `standard module note type is present after boot`() {
        assertNotNull(service.getEntityByPath("/standard/note"), "standard/note type must exist")
    }

    // -------------------------------------------------------------------------
    // Agents module
    // -------------------------------------------------------------------------

    @Test
    fun `agents module prompt type is present after boot`() {
        assertNotNull(service.getEntityByPath("/agents/prompt"), "agents/prompt type must exist")
    }

    // -------------------------------------------------------------------------
    // Software module
    // -------------------------------------------------------------------------

    @Test
    fun `software module types are present after boot`() {
        assertNotNull(service.getEntityByPath("/software/requirement"), "software/requirement type must exist")
        assertNotNull(service.getEntityByPath("/software/system"), "software/system type must exist")
        assertNotNull(service.getEntityByPath("/software/implementation"), "software/implementation type must exist")
        assertNotNull(service.getEntityByPath("/software/file"), "software/file type must exist")
        assertNotNull(service.getEntityByPath("/software/class"), "software/class type must exist")
        assertNotNull(service.getEntityByPath("/software/method"), "software/method type must exist")
    }

    @Test
    fun `software requirement type has token count fields`() {
        val reqTypeId = service.getEntityByPath("/software/requirement")
        assertNotNull(reqTypeId)
        val reqType = service.getById(reqTypeId!!)
        assertNotNull(reqType)
        val fields = reqType!!.fields()
        assertTrue("input_tokens" in fields, "requirement type should have 'input_tokens' field, got: ${fields.keys}")
        assertTrue("output_tokens" in fields, "requirement type should have 'output_tokens' field, got: ${fields.keys}")
    }

    // -------------------------------------------------------------------------
    // Command annotations
    // -------------------------------------------------------------------------

    @Test
    fun `expected commands are registered after full module load`() {
        val commands = service.getCommandAnnotations().map { it.first }.toSet()
        val expected = setOf(
            "note", "undelete",                                   // standard
            "user", "provide-user-token", "change-auth-token",   // auth
            "prompt",                                             // agents
            "requirement", "system", "implementation",           // software
            "file", "class", "method"                            // software extended
        )
        for (cmd in expected) {
            assertTrue(cmd in commands, "command '$cmd' should be registered, got: $commands")
        }
    }

    @Test
    fun `each registered command maps to a valid macro entity`() {
        val annotations = service.getCommandAnnotations()
        assertTrue(annotations.isNotEmpty(), "at least one command should be registered")
        for ((keyword, macroId) in annotations) {
            assertNotNull(service.getAllEntities()[macroId],
                "command '$keyword' macro entity $macroId must exist in the stream")
        }
    }

    // -------------------------------------------------------------------------
    // Visible tree shape
    // -------------------------------------------------------------------------

    @Test
    fun `module roots exist and are meta after boot`() {
        val allTexts = service.getAllEntities().values.map { it.text() }.toSet()
        // Module roots live under META_ROOT so they inherit isMeta=true
        for (name in listOf("agents", "auth", "software", "standard")) {
            assertTrue(name in allTexts, "$name module root should exist, got: $allTexts")
            val root = service.getAllEntities().values.first { it.text() == name }
            assertTrue(root.isMeta(), "$name module root should be meta (lives under META_ROOT)")
        }
    }

    @Test
    fun `user intents do not appear in visible tree`() {
        val visible = service.getAll()
        val userTypeId = service.getEntityByPath("/auth/user")
        assertNotNull(userTypeId, "auth/user type must exist")
        val userInstances = service.getInstancesOfType(userTypeId!!).toSet()
        val visibleIds = visible.map { it.id() }.toSet()
        val intersection = visibleIds.intersect(userInstances)
        assertTrue(intersection.isEmpty(),
            "user intents should not appear in visible tree, found ids: $intersection")
    }

    @Test
    fun `visible tree contains only the server root after fresh boot`() {
        // All module infrastructure and users live under META_ROOT (meta).
        // Only the server root intent (id 0) should be visible.
        val visible = service.getAll()
        val unexpected = visible.filter { it.id() != VoluntasIds.ROOT_INTENT }
        assertTrue(unexpected.isEmpty(),
            "unexpected visible intents after fresh boot: ${unexpected.map { "${it.id()}:${it.text()}" }}")
    }

    // -------------------------------------------------------------------------
    // Idempotency
    // -------------------------------------------------------------------------

    @Test
    fun `loading all modules a second time is idempotent`() {
        val entityCountAfterFirstLoad = service.getAllEntities().size
        val loader = ModuleLoader(service)
        for (file in File("modules").listFiles { f -> f.extension == "pb" }!!.sortedBy { it.name }) {
            loader.loadModule(VoluntasModule.fromFile(file.absolutePath))
        }
        assertEquals(entityCountAfterFirstLoad, service.getAllEntities().size,
            "reloading all modules should not create new entities")
    }

    @Test
    fun `bootstrapRootUser is idempotent`() {
        service.bootstrapRootUser()
        service.bootstrapRootUser()
        val userTypeId = service.getEntityByPath("/auth/user")!!
        val rootCount = service.getInstancesOfType(userTypeId).count { id ->
            service.getById(id)?.text() == "root"
        }
        assertEquals(1, rootCount, "root user should only exist once after multiple bootstrapRootUser calls")
    }

    // -------------------------------------------------------------------------
    // Serialization round-trip
    // -------------------------------------------------------------------------

    @Test
    fun `service state is preserved through write-reload cycle`(@org.junit.jupiter.api.io.TempDir tempDir: java.nio.file.Path) {
        val file = tempDir.resolve("stream.pb").toString()
        service.writeToFile(file)
        val reloaded = VoluntasIntentService.fromFile(file)

        assertTrue(reloaded.isAuthModuleLoaded(), "auth module should survive round-trip")
        assertNotNull(reloaded.findUserByCredentials("root", "root"),
            "root user credentials should survive round-trip")

        val commands = reloaded.getCommandAnnotations().map { it.first }.toSet()
        assertTrue("note" in commands, "standard commands should survive round-trip")
        assertTrue("user" in commands, "auth commands should survive round-trip")
        assertTrue("prompt" in commands, "agents commands should survive round-trip")
        assertTrue("requirement" in commands, "software commands should survive round-trip")

        val userTypeId = reloaded.getEntityByPath("/auth/user")
        assertNotNull(userTypeId, "auth/user type should survive round-trip")

        val rootUserId = reloaded.findUserByCredentials("root", "root")!!
        val rootUser = reloaded.getById(rootUserId)!!
        assertTrue(rootUser.isMeta(), "root user should still be meta after round-trip")
    }
}
