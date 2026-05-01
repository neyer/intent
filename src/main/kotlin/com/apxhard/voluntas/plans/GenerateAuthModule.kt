package com.apxhard.voluntas.plans

import com.apxhard.voluntas.voluntas.buildModule
import java.io.File

/**
 * Generates the "auth" module using the Module DSL.
 *
 * Defines user management and session authentication commands:
 *
 *   user <name>                     → creates a user intent (auth-token field must be set separately)
 *   provide-user-token <token>      → authenticates for the session (server intercepts this macro)
 *   change-auth-token <new-token>   → sets auth-token on the focused user intent
 *
 * The "created-by" meta type is used by the server to annotate intents created by non-root users.
 *
 * Bootstrap: the server creates a root user with auth-token="root" on first startup.
 */
fun main() {
    val service = buildModule("auth") {
        // "user <name>" — creates a meta user intent (hidden from visible tree); auth-token set via change-auth-token
        command("user", meta = true) {
            field("auth-token", string, required = true, description = "Authentication token for this user")
        }

        // "provide-user-token <token>" — client calls this while focused on their user intent
        // The server intercepts this macro invocation to validate credentials and set session auth state.
        // An instance is also created in the stream as an audit record.
        command("provide-user-token")

        // "change-auth-token <new-token>" — sets the auth-token field on the focused user intent
        mutationCommand("change-auth-token") {
            defineField("auth-token", string)
            setFieldRef("auth-token", textArg)
        }

        // Meta type used by the server to tag intents created by non-root users
        type("auth/created-by") {
            field("user", intentRef, required = true, description = "The user who created this intent")
        }
    }

    File("modules").mkdirs()
    service.writeToFile("modules/auth.pb")
    println("Generated modules/auth.pb")
}
