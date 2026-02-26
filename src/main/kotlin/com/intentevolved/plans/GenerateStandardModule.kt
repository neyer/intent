package com.intentevolved.plans

import com.intentevolved.com.intentevolved.voluntas.VoluntasIds
import com.intentevolved.com.intentevolved.voluntas.VoluntasIntentService
import voluntas.v1.FieldType  // needed for addField on commandInterfaceType
import java.io.File

/**
 * Generates the "standard" module — a built-in module that ships with the runtime and
 * provides base types, macros, and the command-interface annotation used to expose macros
 * as named commands in the terminal client.
 *
 * Types defined here use slash-path names (e.g. "/standard/note") to avoid collisions with
 * user-defined type names.
 *
 * Run this once to regenerate modules/standard.pb whenever the standard module changes.
 */
fun main() {
    val service = VoluntasIntentService.new("/standard")
    val moduleRoot = 0L

    // -------------------------------------------------------------------------
    // /standard/note  — a visible string-subtype for freeform notes
    // -------------------------------------------------------------------------
    // Declaring STRING_INTENT_TYPE as the parent makes instances non-meta intents
    // with text, so they appear in the intent tree like regular intents.
    val noteType = service.defineType(
        name = "/standard/note",
        moduleEntityId = moduleRoot,
        parentTypeId = VoluntasIds.STRING_INTENT_TYPE,
        autoNameInstances = true
    )

    // -------------------------------------------------------------------------
    // add-note macro
    // Params: textLit (literal ID of the note text), parentId (entity to attach to)
    // Body: one INSTANTIATES op that creates a /standard/note visible intent.
    // -------------------------------------------------------------------------
    val addNoteMacro = service.defineMacro("/standard/add-note", listOf("textLit", "parentId"))
    service.addMacroOp(
        addNoteMacro,
        VoluntasIds.INSTANTIATES,
        listOf(
            noteType,                       // participants[1]: the /standard/note type
            service.paramRef("textLit"),    // participants[2]: text literal (substituted at call)
            service.paramRef("parentId")    // participants[3]: parent entity (substituted at call)
        )
    )

    // -------------------------------------------------------------------------
    // /standard/interface/command  — annotation type that links a macro to a CLI command name
    // Instances of this type are children of the macro entity they annotate.
    // -------------------------------------------------------------------------
    val commandInterfaceType = service.defineType(
        name = "/standard/interface/command",
        moduleEntityId = moduleRoot,
        parentTypeId = null
    )
    service.addField(
        commandInterfaceType,
        "command-name",
        FieldType.FIELD_TYPE_STRING,
        required = true,
        description = "The CLI keyword that invokes the associated macro"
    )

    // -------------------------------------------------------------------------
    // Command annotation for add-note: command keyword = "note"
    // The annotation is an instance of /standard/interface/command, parented to
    // the add-note macro so getCommandAnnotations() can find the macro from its parent.
    // -------------------------------------------------------------------------
    val noteAnnotationId = service.instantiateType(commandInterfaceType, parentId = addNoteMacro)
    service.setFieldValue(noteAnnotationId, "command-name", "note")

    // -------------------------------------------------------------------------
    // Write output
    // -------------------------------------------------------------------------
    File("modules").mkdirs()
    service.writeToFile("modules/standard.pb")
    println("Generated modules/standard.pb")
    println("  noteType entity (module-local):              $noteType")
    println("  addNoteMacro entity (module-local):          $addNoteMacro")
    println("  commandInterfaceType entity (module-local):  $commandInterfaceType")
    println("  noteCommandAnnotation entity (module-local): $noteAnnotationId")
}
