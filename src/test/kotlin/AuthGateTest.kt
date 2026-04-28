import com.apxhard.voluntas.voluntas.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class AuthGateTest {

    private fun serviceWithAuth(): VoluntasIntentService {
        val service = buildModule("auth") {
            command("user") {
                field("auth-token", string, required = true)
            }
            command("provide-user-token")
            mutationCommand("change-auth-token") {
                defineField("auth-token", string)
                setFieldRef("auth-token", textArg)
            }
            type("auth/created-by") {
                field("user", intentRef, required = true)
            }
        }
        service.bootstrapRootUser()
        return service
    }

    private fun addUser(service: VoluntasIntentService, name: String, token: String): Long {
        val typeId = service.getEntityByPath("/auth/user")!!
        val user = service.addIntentOfType(typeId, name, VoluntasIds.ROOT_INTENT)
        service.setFieldValue(user.id(), "auth-token", token)
        return user.id()
    }

    // -------------------------------------------------------------------------
    // validate — auth module not loaded
    // -------------------------------------------------------------------------

    @Test
    fun `validate returns anonymous result when auth module not loaded`() {
        val gate = AuthGate(VoluntasIntentService.new("bare"))
        val result = gate.validate(null, null)
        assertNull(result.userId)
        assertNull(result.username)
    }

    @Test
    fun `validate returns anonymous result regardless of credentials when auth not loaded`() {
        val gate = AuthGate(VoluntasIntentService.new("bare"))
        val result = gate.validate("alice", "secret")
        assertNull(result.userId)
        assertNull(result.username)
    }

    // -------------------------------------------------------------------------
    // validate — auth module loaded, credentials absent
    // -------------------------------------------------------------------------

    @Test
    fun `validate throws AuthException when username is null and auth loaded`() {
        val gate = AuthGate(serviceWithAuth())
        val ex = assertThrows<AuthException> { gate.validate(null, null) }
        assertTrue(ex.message!!.contains("Not authenticated", ignoreCase = true))
    }

    @Test
    fun `validate throws AuthException when username is empty and auth loaded`() {
        val gate = AuthGate(serviceWithAuth())
        val ex = assertThrows<AuthException> { gate.validate("", "token") }
        assertTrue(ex.message!!.contains("Not authenticated", ignoreCase = true))
    }

    // -------------------------------------------------------------------------
    // validate — auth module loaded, credentials present but wrong
    // -------------------------------------------------------------------------

    @Test
    fun `validate throws AuthException for wrong token`() {
        val service = serviceWithAuth()
        val gate = AuthGate(service)
        val ex = assertThrows<AuthException> { gate.validate("root", "wrongtoken") }
        assertTrue(ex.message!!.contains("root"), "error should name the user")
    }

    @Test
    fun `validate throws AuthException for unknown username`() {
        val service = serviceWithAuth()
        val gate = AuthGate(service)
        val ex = assertThrows<AuthException> { gate.validate("nobody", "root") }
        assertTrue(ex.message!!.contains("nobody"), "error should name the user")
    }

    @Test
    fun `validate throws AuthException when auth token is null for known user`() {
        val service = serviceWithAuth()
        val gate = AuthGate(service)
        assertThrows<AuthException> { gate.validate("root", null) }
    }

    // -------------------------------------------------------------------------
    // validate — successful authentication
    // -------------------------------------------------------------------------

    @Test
    fun `validate returns userId and username for root with correct token`() {
        val service = serviceWithAuth()
        val gate = AuthGate(service)
        val result = gate.validate("root", "root")
        assertNotNull(result.userId)
        assertEquals("root", result.username)
    }

    @Test
    fun `validate returns correct userId for non-root user`() {
        val service = serviceWithAuth()
        val gate = AuthGate(service)
        val aliceId = addUser(service, "alice", "alice-secret")
        val result = gate.validate("alice", "alice-secret")
        assertEquals(aliceId, result.userId)
        assertEquals("alice", result.username)
    }

    @Test
    fun `validate distinguishes users with the same token value`() {
        val service = serviceWithAuth()
        val gate = AuthGate(service)
        addUser(service, "alice", "shared")
        addUser(service, "bob", "shared")
        // Both tokens are "shared" — alice authenticates as alice, bob as bob
        assertEquals("alice", gate.validate("alice", "shared").username)
        assertEquals("bob", gate.validate("bob", "shared").username)
        // Wrong username with correct token still fails
        assertThrows<AuthException> { gate.validate("alice", "root") }
    }

    // -------------------------------------------------------------------------
    // annotateCreatedBy
    // -------------------------------------------------------------------------

    @Test
    fun `annotateCreatedBy is no-op for anonymous result`() {
        val service = serviceWithAuth()
        val gate = AuthGate(service)
        val intent = service.addIntent("task", VoluntasIds.ROOT_INTENT)
        val sizeBefore = service.getAllEntities().size
        gate.annotateCreatedBy(intent.id(), AuthResult(null, null))
        assertEquals(sizeBefore, service.getAllEntities().size)
    }

    @Test
    fun `annotateCreatedBy is no-op for root user`() {
        val service = serviceWithAuth()
        val gate = AuthGate(service)
        val rootUserId = service.findUserByName("root")!!
        val intent = service.addIntent("task", VoluntasIds.ROOT_INTENT)
        val sizeBefore = service.getAllEntities().size
        gate.annotateCreatedBy(intent.id(), AuthResult(rootUserId, "root"))
        assertEquals(sizeBefore, service.getAllEntities().size)
    }

    @Test
    fun `annotateCreatedBy is no-op for intentId zero`() {
        val service = serviceWithAuth()
        val gate = AuthGate(service)
        val aliceId = addUser(service, "alice", "secret")
        val sizeBefore = service.getAllEntities().size
        gate.annotateCreatedBy(0L, AuthResult(aliceId, "alice"))
        assertEquals(sizeBefore, service.getAllEntities().size)
    }

    @Test
    fun `annotateCreatedBy attaches created-by for non-root user`() {
        val service = serviceWithAuth()
        val gate = AuthGate(service)
        val aliceId = addUser(service, "alice", "secret")
        val intent = service.addIntent("task", VoluntasIds.ROOT_INTENT)
        val sizeBefore = service.getAllEntities().size
        gate.annotateCreatedBy(intent.id(), AuthResult(aliceId, "alice"))
        assertTrue(service.getAllEntities().size > sizeBefore)
    }

    @Test
    fun `annotateCreatedBy records the correct user on the created-by instance`() {
        val service = serviceWithAuth()
        val gate = AuthGate(service)
        val aliceId = addUser(service, "alice", "secret")
        val intent = service.addIntent("task", VoluntasIds.ROOT_INTENT)
        gate.annotateCreatedBy(intent.id(), AuthResult(aliceId, "alice"))

        val createdByTypeId = service.getEntityByPath("/auth/created-by")!!
        val instance = service.getInstancesOfType(createdByTypeId)
            .mapNotNull { service.getById(it) }
            .first()
        assertEquals(aliceId, instance.fieldValues()["user"])
    }

    // -------------------------------------------------------------------------
    // extractNewIntentId
    // -------------------------------------------------------------------------

    @Test
    fun `extractNewIntentId parses id from standard result message`() {
        val gate = AuthGate(VoluntasIntentService.new("bare"))
        assertEquals(1234L, gate.extractNewIntentId("Added intent 1234"))
        assertEquals(42L,   gate.extractNewIntentId("Created intent 42 under root"))
    }

    @Test
    fun `extractNewIntentId returns 0 when no intent id in message`() {
        val gate = AuthGate(VoluntasIntentService.new("bare"))
        assertEquals(0L, gate.extractNewIntentId("Marked done"))
        assertEquals(0L, gate.extractNewIntentId(""))
    }

    @Test
    fun `extractNewIntentId returns first match when message contains multiple numbers`() {
        val gate = AuthGate(VoluntasIntentService.new("bare"))
        assertEquals(10L, gate.extractNewIntentId("Updated intent 10 parent to 20"))
    }
}
