package com.intentevolved.com.intentevolved.voluntas

import voluntas.v1.FieldType

/**
 * Entry point for the Module DSL. Builds a Voluntas module by running [block]
 * in a [ModuleBuilder] scope and returns the resulting service.
 *
 * The returned service can be serialised with [VoluntasIntentService.writeToFile].
 *
 * Example — the standard module:
 *
 *     val service = buildModule("/standard") {
 *         command("note")
 *         command("task") {
 *             field("done", bool)
 *             field("priority", string, required = true)
 *         }
 *         builtinCommands("add", "focus", "up", "update", "move", "do", "write", "import")
 *     }
 *     service.writeToFile("modules/standard.pb")
 *
 * Example — a domain module with plain types:
 *
 *     val service = buildModule("Software Development Module") {
 *         type("requirement") {
 *             field("name", string, required = true)
 *             field("description", string)
 *         }
 *         type("system") {
 *             field("name", string, required = true)
 *             field("fulfills", intentRef)
 *         }
 *     }
 */
fun buildModule(name: String, block: ModuleBuilder.() -> Unit): VoluntasIntentService {
    val service = VoluntasIntentService.new(name)
    ModuleBuilder(service, name).apply(block)
    return service
}

/** Marks DSL receivers so Kotlin prevents accidentally calling outer-scope methods inside nested lambdas. */
@DslMarker
annotation class ModuleDsl

/**
 * DSL receiver for declaring the contents of a Voluntas module.
 *
 * Method names mirror terminal commands, so reading a module definition gives
 * an intuitive sense of what commands and types the module provides.
 *
 *   command("note")           → type + macro + registers "note <text>" as a terminal command
 *   type("requirement") { }  → meta type with fields, no terminal command
 *   builtinCommand("add")    → documents the hardcoded "add" command in the intent tree
 */
@ModuleDsl
class ModuleBuilder(
    private val service: VoluntasIntentService,
    private val moduleName: String
) {
    /** Lazily-created command-interface type ID (for macro-backed commands). */
    private var macroCommandTypeId: Long? = null

    /** Lazily-created builtin-command documentation type ID. */
    private var builtinCommandTypeId: Long? = null

    /**
     * Define a macro-backed terminal command.
     *
     * Creates:
     * - A visible string-subtype at "[moduleName]/[keyword]" — instances appear in the intent tree
     * - Any fields declared in [block] on that type
     * - A macro at "[moduleName]/add-[keyword]" with params `textLit`, `parentId`
     * - A command annotation that links the macro to the CLI keyword "[keyword]"
     *
     * Terminal usage after loading this module:  `note Some freeform text`
     */
    fun command(keyword: String, block: TypeBuilder.() -> Unit = {}) {
        val builder = TypeBuilder()
        builder.block()

        val typeId = service.defineType(
            name = "$moduleName/$keyword",
            moduleEntityId = VoluntasIds.ROOT_INTENT,
            parentTypeId = VoluntasIds.STRING_INTENT_TYPE,
            autoNameInstances = true
        )

        for ((name, type, required, description) in builder.fields) {
            service.addField(typeId, name, type, required, description)
        }

        val macroId = service.defineMacro(
            "$moduleName/add-$keyword",
            listOf("textLit", "parentId")
        )
        service.addMacroOp(
            macroId,
            VoluntasIds.INSTANTIATES,
            listOf(typeId, service.paramRef("textLit"), service.paramRef("parentId"))
        )

        val commandTypeId = ensureMacroCommandType()
        val annotationId = service.instantiateType(commandTypeId, parentId = macroId)
        service.setFieldValue(annotationId, "command-name", keyword)
    }

    /**
     * Define a plain meta type (no terminal command backing).
     *
     * Creates a type at [name] (bare names like "requirement" become "/requirement";
     * slash-prefixed names like "/standard/note" are used as-is).
     * Instances of this type are meta intents and won't appear in the visible intent tree.
     */
    fun type(name: String, block: TypeBuilder.() -> Unit = {}) {
        val builder = TypeBuilder()
        builder.block()

        val typeId = service.defineType(
            name = name,
            moduleEntityId = VoluntasIds.ROOT_INTENT
        )

        for ((fieldName, type, required, description) in builder.fields) {
            service.addField(typeId, fieldName, type, required, description)
        }
    }

    /**
     * Document a hardcoded terminal command in the intent tree.
     *
     * Builtin commands are implemented in [CommandExecutor] and don't use macros.
     * This creates a "[moduleName]/interface/builtin-command" annotation so they are
     * discoverable alongside macro-backed commands.
     */
    fun builtinCommand(keyword: String) {
        val typeId = ensureBuiltinCommandType()
        val annotationId = service.instantiateType(typeId, parentId = VoluntasIds.ROOT_INTENT)
        service.setFieldValue(annotationId, "command-name", keyword)
    }

    /** Convenience: document multiple builtin commands at once. */
    fun builtinCommands(vararg keywords: String) {
        for (keyword in keywords) builtinCommand(keyword)
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    /** Returns the ID of the "[moduleName]/interface/command" type, creating it if needed. */
    private fun ensureMacroCommandType(): Long = macroCommandTypeId ?: run {
        val id = service.defineType(
            name = "$moduleName/interface/command",
            moduleEntityId = VoluntasIds.ROOT_INTENT
        )
        service.addField(
            id, "command-name", FieldType.FIELD_TYPE_STRING,
            required = true,
            description = "The CLI keyword that invokes the associated macro"
        )
        macroCommandTypeId = id
        id
    }

    /** Returns the ID of the "[moduleName]/interface/builtin-command" type, creating it if needed. */
    private fun ensureBuiltinCommandType(): Long = builtinCommandTypeId ?: run {
        val id = service.defineType(
            name = "$moduleName/interface/builtin-command",
            moduleEntityId = VoluntasIds.ROOT_INTENT
        )
        service.addField(
            id, "command-name", FieldType.FIELD_TYPE_STRING,
            required = true,
            description = "The CLI keyword of a hardcoded terminal command"
        )
        builtinCommandTypeId = id
        id
    }
}

/**
 * DSL receiver for declaring fields on a type inside [ModuleBuilder.command] or
 * [ModuleBuilder.type] blocks.
 *
 * Field type shorthand properties are in scope:
 *
 *     command("task") {
 *         field("done", bool)
 *         field("priority", string, required = true)
 *         field("due", timestamp)
 *         field("parent-task", intentRef)
 *     }
 */
@ModuleDsl
class TypeBuilder {
    internal data class FieldDef(
        val name: String,
        val type: FieldType,
        val required: Boolean = false,
        val description: String? = null
    )

    internal val fields = mutableListOf<FieldDef>()

    fun field(name: String, type: FieldType, required: Boolean = false, description: String? = null) {
        fields.add(FieldDef(name, type, required, description))
    }

    // Field type shorthand — avoids importing FieldType.FIELD_TYPE_* everywhere
    val string: FieldType    get() = FieldType.FIELD_TYPE_STRING
    val int: FieldType       get() = FieldType.FIELD_TYPE_INT32
    val long: FieldType      get() = FieldType.FIELD_TYPE_INT64
    val float: FieldType     get() = FieldType.FIELD_TYPE_FLOAT
    val double: FieldType    get() = FieldType.FIELD_TYPE_DOUBLE
    val bool: FieldType      get() = FieldType.FIELD_TYPE_BOOL
    val timestamp: FieldType get() = FieldType.FIELD_TYPE_TIMESTAMP
    val intentRef: FieldType get() = FieldType.FIELD_TYPE_INTENT_REF
}
