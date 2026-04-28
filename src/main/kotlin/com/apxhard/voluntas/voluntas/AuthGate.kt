package com.apxhard.voluntas.voluntas

import com.apxhard.voluntas.IntentStreamConsumer
import voluntas.v1.SubmitOpRequest

class AuthException(message: String) : Exception(message)

data class AuthResult(val userId: Long?, val username: String?)

/**
 * Shared authentication layer used by both the gRPC handler and the web handler.
 *
 * Validates per-request credentials against the loaded auth module and annotates
 * newly created intents with a CreatedBy record for non-root users.
 */
class AuthGate(private val service: VoluntasIntentService) {

    /**
     * Validates [username]/[authToken] against the loaded auth module.
     * - Auth module not loaded → returns AuthResult(null, null), all callers allowed.
     * - Credentials absent → throws [AuthException] with a "not authenticated" message.
     * - Credentials present but wrong → throws [AuthException] with an "invalid credentials" message.
     * - Credentials valid → returns AuthResult with the matching userId and username.
     */
    fun validate(username: String?, authToken: String?): AuthResult {
        if (!service.isAuthModuleLoaded()) return AuthResult(null, null)
        if (username.isNullOrEmpty()) throw AuthException(
            "Not authenticated. Set VOLUNTAS_USER/VOLUNTAS_TOKEN, or run: provide-user-token <token>"
        )
        val userId = service.findUserByCredentials(username, authToken ?: "")
            ?: throw AuthException("Invalid credentials for user '$username'")
        return AuthResult(userId, username)
    }

    /**
     * Attaches a CreatedBy annotation to [intentId] when [authResult] identifies a
     * non-root user. No-op for root, anonymous sessions, or zero IDs.
     */
    fun annotateCreatedBy(intentId: Long, authResult: AuthResult) {
        val userId = authResult.userId ?: return
        if (authResult.username != "root" && intentId > 0L) {
            service.createCreatedBy(intentId, userId)
        }
    }

    /**
     * Extracts the entity ID from a command-result message of the form "… intent N …".
     * Returns 0 if no match (e.g. the command didn't create a new intent).
     */
    fun extractNewIntentId(message: String): Long =
        Regex("intent (\\d+)").find(message)?.groupValues?.get(1)?.toLongOrNull() ?: 0L

    /**
     * Returns a [IntentStreamConsumer] that validates [getCredentials] on every [consume]
     * call and annotates newly created intents with CreatedBy for non-root users.
     *
     * Commands that never call [consume] (e.g. focus, up, write) are unaffected.
     */
    fun authenticatingConsumer(
        delegate: IntentStreamConsumer,
        getCredentials: () -> Pair<String?, String?>
    ): IntentStreamConsumer = object : IntentStreamConsumer {
        override fun consume(request: SubmitOpRequest): com.apxhard.voluntas.CommandResult {
            val (username, authToken) = getCredentials()
            val authResult = validate(username, authToken)
            val result = delegate.consume(request)
            annotateCreatedBy(extractNewIntentId(result.message), authResult)
            return result
        }
    }
}
