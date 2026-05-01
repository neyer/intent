package e2e

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
    fun `auth user type is meta-visible`() {
        val userTypeId = service.getEntityByPath("/auth/user")
        assertNotNull(userTypeId, "auth/user type must exist")
        assertTrue(service.isMetaVisibleType(userTypeId!!), "auth/user should be a meta-visible type")
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
    fun `module roots appear in visible tree after boot`() {
        val visibleTexts = service.getAll().map { it.text() }.toSet()
        // Module roots are loaded as visible intents under the meta anchor entity
        assertTrue("agents" in visibleTexts, "agents module root should be visible, got: $visibleTexts")
        assertTrue("auth" in visibleTexts, "auth module root should be visible, got: $visibleTexts")
        assertTrue("software" in visibleTexts, "software module root should be visible, got: $visibleTexts")
        assertTrue("standard" in visibleTexts, "standard module root should be visible, got: $visibleTexts")
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
    fun `no user-created intents appear in visible tree after fresh boot`() {
        // Only structural entities (module roots, meta anchor) should be visible.
        // No tasks, notes, or other user content should be present.
        val visible = service.getAll()
        val moduleRootNames = setOf("agents", "auth", "software", "standard")
        val unexpected = visible.filter { it.text() !in moduleRootNames && !it.isMeta() && it.id() > 0 }
            .filter { it.text() != "meta" }
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
        assertNotNull(userTypeId)
        assertTrue(reloaded.isMetaVisibleType(userTypeId!!),
            "auth/user should still be meta-visible after round-trip")
    }
}
