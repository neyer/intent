package com.intentevolved.plans

import com.intentevolved.com.intentevolved.voluntas.ModuleLoader
import com.intentevolved.com.intentevolved.voluntas.VoluntasIntentService
import com.intentevolved.com.intentevolved.voluntas.VoluntasModule

/**
 * Generates the Voluntas self-describing build plan as an intent stream file.
 *
 * Loads both the software and standard modules to use typed intents:
 *   requirement → system → file → class → method
 * with notes describing purpose at each level.
 *
 * Every non-plans source file in the repository has a corresponding file intent,
 * with its classes and key methods captured as children. Plans-package files are
 * included but without a deep class/method hierarchy to avoid circularity.
 */
fun main() {
    val service = VoluntasIntentService.new("Voluntas — event-sourced relationship-graph intent runtime")

    // Load both modules so all types are available.
    val softwareModule = VoluntasModule.fromFile("modules/software.pb")
    val standardModule = VoluntasModule.fromFile("modules/standard.pb")
    val loader = ModuleLoader(service)
    loader.loadModule(softwareModule)
    loader.loadModule(standardModule)

    // Look up all type IDs by canonical name.
    val entities = service.getAllEntities()
    val reqTypeId    = entities.values.first { it.text() == "software/requirement" }.id()
    val sysTypeId    = entities.values.first { it.text() == "software/system"      }.id()
    val fileTypeId   = entities.values.first { it.text() == "software/file"        }.id()
    val classTypeId  = entities.values.first { it.text() == "software/class"       }.id()
    val methodTypeId = entities.values.first { it.text() == "software/method"      }.id()
    val noteTypeId   = entities.values.first { it.text() == "standard/note"        }.id()

    // Convenience wrappers.
    fun req(text: String, parentId: Long)    = service.addIntentOfType(reqTypeId,    text, parentId)
    fun sys(text: String, parentId: Long)    = service.addIntentOfType(sysTypeId,    text, parentId)
    fun file(text: String, parentId: Long)   = service.addIntentOfType(fileTypeId,   text, parentId)
    fun clazz(text: String, parentId: Long)  = service.addIntentOfType(classTypeId,  text, parentId)
    fun method(text: String, parentId: Long) = service.addIntentOfType(methodTypeId, text, parentId)
    fun note(text: String, parentId: Long)   = service.addIntentOfType(noteTypeId,   text, parentId)

    // =========================================================================
    // REQUIREMENT: Core intent data model
    // =========================================================================
    val reqDataModel = req("Represent any intent as a node in a typed, persistent relationship graph", 0)
    note("Foundation: every entity the system manages is an Intent with id, text, parent/children, optional typed fields, and timestamps", reqDataModel.id())

    val sysInterfaces = sys("Core intent abstractions and data structures", reqDataModel.id())
    note("Pure interfaces and data classes that define the conceptual model; no Voluntas-specific details leak into these types", sysInterfaces.id())

    val fIntent = file("Intent.kt", sysInterfaces.id())
    note("Read-only interface for an intent node; the primary type consumed by all rendering and command layers", fIntent.id())
    val cIntent = clazz("Intent", fIntent.id())
    note("Core interface: a node in the intent tree with identity, text, tree linkage, typed field schema, field values, and timestamps", cIntent.id())
    method("id(): Long — unique entity ID", cIntent.id())
    method("text(): String — display text", cIntent.id())
    method("parent(): Intent? — parent node; null for root (ID 0)", cIntent.id())
    method("children(): List<Intent> — direct children in insertion order", cIntent.id())
    method("fields(): Map<String, FieldDetails> — declared field schema (type, required, description)", cIntent.id())
    method("fieldValues(): Map<String, Any> — current field value map keyed by field name", cIntent.id())
    method("isMeta(): Boolean — true for system/type/macro entities hidden from end users", cIntent.id())
    method("participantIds(): List<Long> — raw participant IDs from the underlying Relationship op", cIntent.id())
    method("createdTimestamp(): Long? — epoch-nanos creation time, null if not recorded", cIntent.id())
    method("lastUpdatedTimestamp(): Long? — epoch-nanos of most recent mutation, null if not recorded", cIntent.id())

    val fIntentImpl = file("IntentImpl.kt", sysInterfaces.id())
    note("Mutable internal implementation of Intent; instances live in VoluntasIntentService.byId and are updated in-place during stream replay", fIntentImpl.id())
    val cMutableIntent = clazz("MutableIntent", fIntentImpl.id())
    note("Concrete mutable implementation of Intent used exclusively by the backend; all fields are var and updated as ops are replayed", cMutableIntent.id())
    method("All Intent interface methods — delegate to mutable backing properties", cMutableIntent.id())

    val fFocalScope = file("FocalScope.kt", sysInterfaces.id())
    note("Immutable snapshot of navigation context returned by getFocalScope; consumed by all display layers", fFocalScope.id())
    val cFocalScope = clazz("FocalScope", fFocalScope.id())
    note("Data class holding the focused intent, its full ancestor chain back to root, and its direct children", cFocalScope.id())
    method("focus: Intent — the currently focused intent", cFocalScope.id())
    method("ancestry: List<Intent> — parent chain from focus up to (not including) root, nearest-first", cFocalScope.id())
    method("children: List<Intent> — direct children of the focused intent", cFocalScope.id())

    val fCommandResult = file("CommandResult.kt", sysInterfaces.id())
    note("Wraps the outcome of any command execution; carries a message, optional navigation change, and optional new entity ID", fCommandResult.id())
    val cCommandResult = clazz("CommandResult", fCommandResult.id())
    note("Data class returned by every command; clients use newFocalIntent to update displayed focus and id to locate newly created entities", cCommandResult.id())
    method("message: String — human-readable result or error description", cCommandResult.id())
    method("newFocalIntent: Intent? — updated focal intent after navigation-changing commands (focus, up, add)", cCommandResult.id())
    method("id: Long? — ID of any newly created entity (e.g. after addIntent)", cCommandResult.id())

    val fIntentExt = file("IntentExt.kt", sysInterfaces.id())
    note("Kotlin extension functions on Intent; adds utility behaviour without modifying the interface", fIntentExt.id())
    method("Intent.getAncestry(): List<Intent> — walks parent chain to root; returns ancestors ordered nearest-first", fIntentExt.id())

    val fIntentService = file("IntentService.kt", sysInterfaces.id())
    note("Full read-write service interface combining mutation, query, and persistence; implemented by VoluntasIntentService", fIntentService.id())
    val cIntentService = clazz("IntentService", fIntentService.id())
    note("Interface extending IntentStateProvider with mutation and persistence operations", cIntentService.id())
    method("addIntent(text: String, parentId: Long): Intent — creates a plain STRING_INTENT_TYPE intent", cIntentService.id())
    method("edit(id: Long, newText: String): CommandResult — updates the text of an existing intent", cIntentService.id())
    method("moveParent(id: Long, newParentId: Long): CommandResult — re-parents an intent to a new parent", cIntentService.id())
    method("getAll(): List<Intent> — all non-meta user-visible intents", cIntentService.id())
    method("writeToFile(fileName: String) — persists the current op list to a .pb stream file", cIntentService.id())

    val fIntentStateProvider = file("IntentStateProvider.kt", sysInterfaces.id())
    note("Read-only sub-interface passed to web sessions and display layers; limits access to query operations only", fIntentStateProvider.id())
    val cIntentStateProvider = clazz("IntentStateProvider", fIntentStateProvider.id())
    note("Minimal query interface: just enough to render the tree and navigate without mutation access", cIntentStateProvider.id())
    method("getById(id: Long): Intent? — look up any intent by its entity ID", cIntentStateProvider.id())
    method("getFocalScope(id: Long): FocalScope — get focus + ancestry + children for the given ID", cIntentStateProvider.id())

    val fIntentStreamConsumer = file("IntentStreamConsumer.kt", sysInterfaces.id())
    note("Bridges the gRPC SubmitOp RPC to the service; all high-level mutations are expressed as SubmitOpRequest", fIntentStreamConsumer.id())
    val cIntentStreamConsumer = clazz("IntentStreamConsumer", fIntentStreamConsumer.id())
    note("Single-method interface: accepts a SubmitOpRequest oneof and routes it to the appropriate service operation", cIntentStreamConsumer.id())
    method("consume(request: SubmitOpRequest): CommandResult — dispatches create/update/move/macro/etc. ops", cIntentStreamConsumer.id())

    // =========================================================================
    // REQUIREMENT: Voluntas relationship-graph backend
    // =========================================================================
    val reqBackend = req("Store and replay the intent tree as an append-only protobuf relationship-graph stream", 0)
    note("All state is represented as Relationship and Literal ops; state is fully reconstructed by replaying ops in order from the stream file", reqBackend.id())

    val sysBackend = sys("Voluntas relationship-graph backend (Protobuf-based event store)", reqBackend.id())
    note("Low-level event-sourced storage engine: relationships, literals, bootstrap IDs, and the main replay and mutation loop", sysBackend.id())

    val fVoluntasIds = file("VoluntasIds.kt", sysBackend.id())
    note("Compile-time constants for all bootstrap entity IDs (0–14); these IDs are stable across all stream files — changing them would corrupt existing data", fVoluntasIds.id())
    val cVoluntasIds = clazz("VoluntasIds", fVoluntasIds.id())
    note("Object with named Long constants for every bootstrap relationship type, meta entity, and structural node", cVoluntasIds.id())
    method("DEFINES_TYPE = 1L — relationship op: entity A defines a new type identified as B", cVoluntasIds.id())
    method("DEFINES_FIELD = 2L — relationship op: type A declares a field with name literal B and type-ref C", cVoluntasIds.id())
    method("INSTANTIATES = 3L — relationship op: entity A is an instance of type B with text literal C under parent D", cVoluntasIds.id())
    method("SETS_FIELD = 4L — relationship op: sets field B of entity A to value literal C", cVoluntasIds.id())
    method("ADDS_PARTICIPANT = 5L — relationship op: appends a participant to entity A", cVoluntasIds.id())
    method("DEFINES_MACRO = 6L — relationship op: entity A is a macro with name literal B and param-names literal C", cVoluntasIds.id())
    method("STRING_INTENT_TYPE = 7L — the base string/text type; all visible intents instantiate this or a subtype of it", cVoluntasIds.id())
    method("DEFINES_MACRO_OP = 9L — relationship op: appends one body step to macro A's op list", cVoluntasIds.id())
    method("INVOKES_MACRO = 10L — relationship op: invokes macro A with arg list literal B under parent C", cVoluntasIds.id())
    method("NAME_TYPE = 11L — the type for name-path namespace nodes", cVoluntasIds.id())
    method("META_ROOT = 12L — root of the meta (type/macro) subtree, parent of all type entities", cVoluntasIds.id())
    method("NAMES_ROOT = 13L — root of the name-path namespace", cVoluntasIds.id())
    method("META_NAME_NODE = 14L — name node entity for the meta root", cVoluntasIds.id())
    method("FIRST_USER_ENTITY = 1000L — first entity ID available for user and module allocation", cVoluntasIds.id())

    val fVoluntasIntentService = file("VoluntasIntentService.kt", sysBackend.id())
    note("Central service class: holds the live in-memory state, implements IntentService + IntentStreamConsumer + VoluntasStreamConsumer, orchestrates all replay and mutation", fVoluntasIntentService.id())
    val cVoluntasIntentService = clazz("VoluntasIntentService", fVoluntasIntentService.id())
    note("Full implementation of IntentService; maintains byId map, instancesByType, literalStore, name-path namespace maps, and macro registry", cVoluntasIntentService.id())

    // Factory and lifecycle
    val cgrpFactory = clazz("Factory and lifecycle methods", fVoluntasIntentService.id())
    note("Static factory methods and stream load/replay entry points", cgrpFactory.id())
    method("new(rootText: String): VoluntasIntentService — creates a fresh stream with bootstrap ops and the given root intent", cgrpFactory.id())
    method("fromFile(fileName: String): VoluntasIntentService — parses stream from disk, replays ops, calls migrateBootstrap()", cgrpFactory.id())
    method("fromStream(stream: Stream): VoluntasIntentService — replays an in-memory Stream proto (used by ModuleLoader)", cgrpFactory.id())
    method("replayStream(stream: Stream) — iterates all Ops and dispatches to processRelationship() or literalStore.register()", cgrpFactory.id())
    method("processRelationship(rel: Relationship) — routes a single Relationship op to the appropriate handler by op type", cgrpFactory.id())

    // Bootstrap
    val cgrpBootstrap = clazz("Bootstrap and name-path namespace methods", fVoluntasIntentService.id())
    note("Emit and migrate the well-known bootstrap entities; maintain the '/segment/segment' path namespace for types and macros", cgrpBootstrap.id())
    method("emitBootstrap() — emits Relationship ops for bootstrap IDs 1–14 into a brand-new stream", cgrpBootstrap.id())
    method("migrateBootstrap() — idempotently emits any missing bootstrap entities when loading an older stream file", cgrpBootstrap.id())
    method("handleInstantiatesNameNode(rel) — updates nameNodeToPath, namesByPath, nameNodeByReferent maps from a NAME_TYPE instantiation", cgrpBootstrap.id())
    method("findOrCreateNameNode(path: String): Long — ensures the full '/a/b/c' path chain exists; creates missing nodes", cgrpBootstrap.id())
    method("getNamePath(entityId: Long): String? — returns the string path for an entity that has a name node", cgrpBootstrap.id())
    method("getNameNodeByPath(path: String): Long? — returns the entity ID whose name node has the given path", cgrpBootstrap.id())
    method("getNameNodePath(nameNodeId: Long): String? — returns path for a name-node entity directly", cgrpBootstrap.id())

    // Mutation
    val cgrpMutation = clazz("Mutation methods", fVoluntasIntentService.id())
    note("All methods that emit new Relationship ops and update in-memory state", cgrpMutation.id())
    method("addIntent(text: String, parentId: Long): Intent — creates a STRING_INTENT_TYPE intent; emits INSTANTIATES op", cgrpMutation.id())
    method("addIntentOfType(typeId: Long, text: String, parentId: Long): Intent — creates a typed intent; emits INSTANTIATES with given typeId", cgrpMutation.id())
    method("edit(id: Long, newText: String): CommandResult — emits SETS_FIELD for the text literal; updates byId", cgrpMutation.id())
    method("moveParent(id: Long, newParentId: Long): CommandResult — emits SETS_FIELD for parent; updates parent/child maps", cgrpMutation.id())
    method("deleteIntent(id: Long): CommandResult — soft-deletes: sets deleted=true field", cgrpMutation.id())
    method("emitRelationship(rel: Relationship) — appends to ops list and calls processRelationship()", cgrpMutation.id())

    // Query
    val cgrpQuery = clazz("Query methods", fVoluntasIntentService.id())
    note("Read-only access to in-memory state; no side effects", cgrpQuery.id())
    method("getById(id: Long): Intent? — looks up by entity ID in byId map; returns null if not found", cgrpQuery.id())
    method("getAll(): List<Intent> — all non-meta intents visible to users", cgrpQuery.id())
    method("getAllEntities(): Map<Long, Intent> — entire byId map including meta entities (types, macros, name nodes)", cgrpQuery.id())
    method("getInstancesOfType(typeId: Long): List<Long> — returns entity IDs of all instances of the given type", cgrpQuery.id())
    method("getFocalScope(id: Long): FocalScope — constructs FocalScope with focus, ancestry chain, and direct children", cgrpQuery.id())
    method("getCommandAnnotations(): List<Pair<String,Long>> — scans byId for '/interface/command' type instances; returns (keyword, macroEntityId) pairs", cgrpQuery.id())

    // Macro
    val cgrpMacro = clazz("Macro system methods", fVoluntasIntentService.id())
    note("Define, store, and expand parameterised command templates at invocation time", cgrpMacro.id())
    method("defineMacro(name: String, params: List<String>): Long — registers DEFINES_MACRO relationship; stores MacroDef in macros map; creates name node", cgrpMacro.id())
    method("addMacroOp(macroId: Long, opType: Long, participants: List<Long>) — appends a DEFINES_MACRO_OP body step; validates param refs at definition time", cgrpMacro.id())
    method("handleInvokesMacro(macroId: Long, args: List<Long>, parentId: Long) — expands macro: substitutes \$name/\$index param refs with actual arg literal IDs, emits each body op", cgrpMacro.id())
    method("paramRef(name: String): Long — returns a literal ID encoding a '\$name' substitution placeholder for use in macro body ops", cgrpMacro.id())
    method("getMacroByName(name: String): MacroDef? — looks up a macro by name path; used by ModuleLoader and command annotation scanning", cgrpMacro.id())

    // SubmitOp dispatch
    val cgrpConsume = clazz("SubmitOpRequest dispatch", fVoluntasIntentService.id())
    note("Implements IntentStreamConsumer.consume(): routes each SubmitOpRequest payload variant to the appropriate mutation method", cgrpConsume.id())
    method("consume(request: SubmitOpRequest): CommandResult — dispatches oneof payload: CreateIntent, UpdateIntentText, UpdateIntentParent, AddField, SetFieldValue, InvokeMacro, WriteNoGarbage", cgrpConsume.id())

    val fLiteralStore = file("LiteralStore.kt", sysBackend.id())
    note("Content-addressed literal storage: each unique value gets a stable ID (Long.MIN_VALUE | ordinal); the same value always maps to the same ID regardless of when it was first stored", fLiteralStore.id())
    val cLiteralStore = clazz("LiteralStore", fLiteralStore.id())
    note("Manages bidirectional maps between literal IDs and values; supports String, Long, Double, Boolean, ByteArray; IDs set the high bit (Long.MIN_VALUE | ordinal)", cLiteralStore.id())
    method("getOrCreate(value: String): Long — returns existing or allocates new literal ID for the string", cLiteralStore.id())
    method("getOrCreate(value: Long): Long — for 64-bit integer literals", cLiteralStore.id())
    method("getOrCreate(value: Double): Long — for floating-point literals", cLiteralStore.id())
    method("getOrCreate(value: Boolean): Long — for boolean literals", cLiteralStore.id())
    method("getOrCreate(value: ByteArray): Long — for raw byte array literals", cLiteralStore.id())
    method("register(literal: Literal) — loads a proto Literal message during stream replay; populates both forward and reverse maps", cLiteralStore.id())
    method("getValue(id: Long): Any? — retrieves the stored value for a literal ID; returns null if not registered", cLiteralStore.id())

    val fVoluntasStreamConsumer = file("VoluntasStreamConsumer.kt", sysBackend.id())
    note("Low-level interface for consuming raw Relationship protos; used by ModuleLoader and VoluntasServiceGrpcImpl for direct stream access", fVoluntasStreamConsumer.id())
    val cVoluntasStreamConsumer = clazz("VoluntasStreamConsumer", fVoluntasStreamConsumer.id())
    note("Single-method interface: receive a raw Relationship op and update state accordingly", cVoluntasStreamConsumer.id())
    method("consume(relationship: Relationship): CommandResult", cVoluntasStreamConsumer.id())

    // Backend tests
    val reqLiteralTest = req("LiteralStore must correctly deduplicate and retrieve all supported value types", sysBackend.id())
    val sysLiteralTest = sys("LiteralStore unit tests", reqLiteralTest.id())
    val fLiteralTest = file("LiteralStoreTest.kt", sysLiteralTest.id())
    note("Verifies literal creation, content-addressed deduplication, bootstrap ordinals, and all supported types", fLiteralTest.id())
    val cLiteralTest = clazz("LiteralStoreTest", fLiteralTest.id())
    method("testStringDeduplication() — same string returns same ID on repeated calls", cLiteralTest.id())
    method("testAllValueTypes() — string, long, double, bool, bytes all store and retrieve correctly", cLiteralTest.id())
    method("testHighBitPattern() — IDs use the Long.MIN_VALUE | ordinal pattern", cLiteralTest.id())

    val reqServiceTest = req("Core service operations and persistence must be verified by automated tests", sysBackend.id())
    val sysServiceTest = sys("VoluntasIntentService unit tests", reqServiceTest.id())
    val fServiceTest = file("VoluntasIntentServiceTest.kt", sysServiceTest.id())
    note("Tests root creation, addIntent, getById, edit, moveParent, writeToFile, fromFile round-trip, and field handling", fServiceTest.id())
    val cServiceTest = clazz("VoluntasIntentServiceTest", fServiceTest.id())
    method("testCreateRoot() — new() produces a service with root intent at ID 0", cServiceTest.id())
    method("testAddIntent() — addIntent creates child with correct text and parent", cServiceTest.id())
    method("testEditIntent() — edit() updates text and is visible via getById()", cServiceTest.id())
    method("testMoveParent() — moveParent() re-parents correctly", cServiceTest.id())
    method("testWriteAndReload() — writeToFile + fromFile produces identical state", cServiceTest.id())
    method("testFieldHandling() — AddField and SetFieldValue produce correct fieldValues()", cServiceTest.id())

    // =========================================================================
    // REQUIREMENT: Protobuf stream persistence
    // =========================================================================
    val reqPersistence = req("All state changes must be persisted as an append-only protobuf stream fully replayable from disk", 0)
    note("The stream is a sequence of Op messages (Relationship | Literal); replay rebuilds the entire in-memory state; no separate database is needed", reqPersistence.id())

    val sysPersistence = sys("Protobuf wire format and stream serialisation", reqPersistence.id())
    note("voluntas.proto defines all wire types; writeToFile/fromFile in VoluntasIntentService handle serialisation/deserialisation", sysPersistence.id())

    val fProto = file("voluntas.proto", sysPersistence.id())
    note("Protocol Buffer schema: stream wire format, gRPC service definitions, and all message types consumed by clients", fProto.id())
    val cStreamMessages = clazz("Stream messages", fProto.id())
    note("Literal (id, oneof value), Relationship (id, participants list), Op (oneof Relationship | Literal), Stream (repeated Op)", cStreamMessages.id())
    val cIntentMessages = clazz("Intent domain messages", fProto.id())
    note("IntentProto (id, text, isMeta, fields, fieldValues, participantIds, timestamps), FieldDetailsProto, FieldValueProto (oneof scalar types)", cIntentMessages.id())
    val cSubmitOpMessages = clazz("SubmitOpRequest and payload types", fProto.id())
    note("SubmitOpRequest with oneof payload: CreateIntent, UpdateIntentText, UpdateIntentParent, AddField, SetFieldValue, InvokeMacro, WriteNoGarbage", cSubmitOpMessages.id())
    val cGrpcServices = clazz("gRPC service definitions", fProto.id())
    note("IntentService: SubmitOp, GetIntent, GetFocalScope, GetCommands RPCs; VoluntasService: SubmitRelationship, SubmitLiteral RPCs", cGrpcServices.id())

    note("writeToFile(fileName) lives in VoluntasIntentService: serialises ops list to Stream proto, writes to file", sysPersistence.id())
    note("fromFile(fileName) lives in VoluntasIntentService: parses Stream proto, calls replayStream(), calls migrateBootstrap()", sysPersistence.id())

    // =========================================================================
    // REQUIREMENT: Module system
    // =========================================================================
    val reqModules = req("Users must be able to extend the type system with new types, commands, and macros via loadable module .pb files", 0)
    note("Modules are self-contained stream files that define types, macros, and command annotations; merged into the main stream at server startup", reqModules.id())

    val sysModuleDsl = sys("Declarative Kotlin DSL for authoring modules", reqModules.id())
    note("The DSL hides all Voluntas relationship ops behind an expressive builder; module authors never touch entity IDs directly", sysModuleDsl.id())

    val fModuleDsl = file("ModuleDsl.kt", sysModuleDsl.id())
    note("Entry point buildModule() and all DSL builder classes for module authoring", fModuleDsl.id())
    method("buildModule(name: String, block: ModuleBuilder.() -> Unit): VoluntasIntentService — creates module root, runs block, returns service with module ops", fModuleDsl.id())
    val cModuleBuilder = clazz("ModuleBuilder", fModuleDsl.id())
    note("Top-level builder: receives module name, creates root type, provides command/type/mutationCommand/builtinCommands builder methods", cModuleBuilder.id())
    method("command(keyword: String, block: TypeBuilder.() -> Unit) — creates a visible STRING subtype + macro + command annotation for CLI use", cModuleBuilder.id())
    method("type(keyword: String, block: TypeBuilder.() -> Unit) — creates a visible type without a CLI command or macro", cModuleBuilder.id())
    method("mutationCommand(keyword: String, block: MutationBuilder.() -> Unit) — creates a macro that operates on the currently focused intent", cModuleBuilder.id())
    method("builtinCommand(keyword: String) — documents a builtin command (no macro; for reference only)", cModuleBuilder.id())
    method("builtinCommands(vararg keywords: String) — batch version of builtinCommand", cModuleBuilder.id())
    val cTypeBuilder = clazz("TypeBuilder", fModuleDsl.id())
    note("Nested builder inside command/type blocks: declares fields with type shorthands and required flag", cTypeBuilder.id())
    method("field(name: String, type: FieldType, required: Boolean, description: String?) — adds a field to the type being defined", cTypeBuilder.id())
    method("string, long, bool, double, intentRef — pre-built FieldType constants for common field types", cTypeBuilder.id())
    val cMutationBuilder = clazz("MutationBuilder", fModuleDsl.id())
    note("Builds macro body ops for mutation commands that act on the currently focused intent", cMutationBuilder.id())
    method("setField(name: String, value: Any) — adds a SETS_FIELD op to the macro body", cMutationBuilder.id())

    val fGenStandard = file("GenerateStandardModule.kt", sysModuleDsl.id())
    note("Generates modules/standard.pb: defines note command, undelete mutation command, and documents 10 builtin terminal commands", fGenStandard.id())
    method("main() — builds the standard module via buildModule(\"standard\") and writes modules/standard.pb", fGenStandard.id())

    val fGenSoftware = file("GenerateSoftwareModule.kt", sysModuleDsl.id())
    note("Generates modules/software.pb: requirement (with token count fields), system, implementation, file, class, and method commands", fGenSoftware.id())
    method("main() — builds the software module via buildModule(\"software\") and writes modules/software.pb", fGenSoftware.id())

    val sysModuleLoader = sys("Module loader: merges a module stream into the main stream with ID remapping", reqModules.id())
    note("Loading remaps module entity IDs to avoid collision with main stream IDs; types and macros matched by name prevent duplication on reload", sysModuleLoader.id())

    val fVoluntasModule = file("VoluntasModule.kt", sysModuleLoader.id())
    note("Reads a .pb module file and exposes its type entities and the module's own replayed service for inspection by ModuleLoader", fVoluntasModule.id())
    val cVoluntasModule = clazz("VoluntasModule", fVoluntasModule.id())
    note("Holds the parsed module: rootText, typeEntities map (local IDs → ModuleTypeDefinition), and the replayed VoluntasIntentService snapshot", cVoluntasModule.id())
    method("fromFile(path: String): VoluntasModule — static factory: reads and replays the module stream file", cVoluntasModule.id())
    method("rootText: String — the module's root intent text used as its identifier (e.g. \"software\", \"standard\")", cVoluntasModule.id())
    method("typeEntities: Map<Long, ModuleTypeDefinition> — maps local type IDs to type name and field definitions", cVoluntasModule.id())

    val fModuleLoader = file("ModuleLoader.kt", sysModuleLoader.id())
    note("Merges a VoluntasModule into the main stream: remaps literals, remaps entity IDs, matches existing types by name", fModuleLoader.id())
    val cModuleLoader = clazz("ModuleLoader", fModuleLoader.id())
    note("Takes a target VoluntasIntentService and merges all module ops into it, building ID mapping tables as it goes; returns a manifest of created vs. existing IDs", cModuleLoader.id())
    method("loadModule(module: VoluntasModule): ModuleManifest — main entry point: builds mappings, replays ops into target, returns (newlyCreated, alreadyExisted) ID sets", cModuleLoader.id())
    method("buildLiteralMapping(module) — maps each module literal to an equivalent in the main stream (creating new literals as needed)", cModuleLoader.id())
    method("matchOrCreateType(name, localId) — finds existing type by name path or creates it; records the mapping in entityMapping", cModuleLoader.id())
    method("remapId(localId: Long): Long — translates a module-local entity ID to the corresponding main-stream ID using entityMapping", cModuleLoader.id())

    val reqModuleTest = req("Module loading must correctly remap IDs, match existing types by name, and detect field conflicts", sysModuleLoader.id())
    val sysModuleTest = sys("ModuleLoader unit tests", reqModuleTest.id())
    val fModuleTest = file("ModuleLoaderTest.kt", sysModuleTest.id())
    note("Tests type matching by name, literal ID remapping, entity ID remapping, conflict exceptions on field type mismatch, and idempotent reloads", fModuleTest.id())
    val cModuleTest = clazz("ModuleLoaderTest", fModuleTest.id())
    method("testTypeMatcher() — loading a module with a type that already exists by name must not create a duplicate", cModuleTest.id())
    method("testLiteralRemapping() — module literal IDs translate to main-stream IDs correctly", cModuleTest.id())
    method("testEntityRemapping() — module entity IDs translate to main-stream IDs", cModuleTest.id())
    method("testIdempotentReload() — loading the same module twice produces no new entities on the second load", cModuleTest.id())

    // =========================================================================
    // REQUIREMENT: Macro system
    // =========================================================================
    val reqMacros = req("Macros must let modules define reusable parameterised command templates that expand into relationship ops at invocation time", 0)
    note("Macros are defined as DEFINES_MACRO relationships; body ops are appended via DEFINES_MACRO_OP; invocation via INVOKES_MACRO substitutes positional or named param refs", reqMacros.id())

    val sysMacros = sys("Macro definition, body ops, and runtime expansion in VoluntasIntentService", reqMacros.id())
    note("All macro logic lives in VoluntasIntentService.kt; see the 'Macro system methods' class group under the backend requirement for method details", sysMacros.id())
    note("MacroDef data class: macroId, name, paramNames list, ops list (each op is a list of participant Long IDs)", sysMacros.id())
    note("Param refs are encoded as special literal IDs: getLiteralId(\"\$name\") or getLiteralId(\"\$0\") for positional", sysMacros.id())
    note("handleInvokesMacro() substitutes all param refs with actual argument literal IDs before emitting each body op", sysMacros.id())

    val reqMacroTest = req("Macro definition and invocation with positional and named parameter binding must be tested", reqMacros.id())
    val sysMacroTest = sys("Macro unit tests", reqMacroTest.id())
    val fMacroTest = file("MacroTest.kt", sysMacroTest.id())
    note("Tests macro definition, positional and named param refs, and DEFINES_FIELD/SETS_FIELD ops inside macro bodies", fMacroTest.id())
    val cMacroTest = clazz("MacroTest", fMacroTest.id())
    method("testMacroDefinition() — macro is registered and retrievable by name after definition", cMacroTest.id())
    method("testPositionalParams() — \$0 and \$1 substitution replaces correct arg values", cMacroTest.id())
    method("testNamedParams() — \$name substitution replaces correct named arg values", cMacroTest.id())
    method("testFieldOpMacro() — DEFINES_FIELD and SETS_FIELD ops work correctly inside an expanded macro", cMacroTest.id())

    // =========================================================================
    // REQUIREMENT: Command annotation system
    // =========================================================================
    val reqCommandAnnot = req("Commands defined in modules must be discoverable at runtime and automatically registered in every client interface", 0)
    note("command() in the DSL creates an instance of '[module]/interface/command' type as a child of each macro entity; getCommandAnnotations() scans for these and returns (keyword, macroId) pairs", reqCommandAnnot.id())

    val sysCommandAnnot = sys("Command annotation type, scanning, and registration in all client interfaces", reqCommandAnnot.id())
    note("Three client integration points: terminal (startup GetCommands RPC), web session manager (getOrCreate), and VoluntasRuntime (threads annotations from service to web server constructor)", sysCommandAnnot.id())
    note("DSL: ensureMacroCommandType() creates '[module]/interface/command' type with required 'command-name' STRING field if not yet present", sysCommandAnnot.id())
    note("DSL: command()/mutationCommand() instantiate the command type as a child of the macro and set 'command-name' to the keyword", sysCommandAnnot.id())
    note("VoluntasIntentService.getCommandAnnotations() scans byId for types whose text() ends with '/interface/command'; returns (keyword, macroEntityId) pairs", sysCommandAnnot.id())
    note("TerminalClient calls GetCommands gRPC on startup; registers a DynamicMacroCommand for each (keyword, macroId) pair", sysCommandAnnot.id())
    note("SessionManager.getOrCreate() iterates commandAnnotations list; calls executor.registerCommand(DynamicMacroCommand(keyword, macroId))", sysCommandAnnot.id())
    note("VoluntasRuntime.start() passes service.getCommandAnnotations() to IntentWebServer constructor after loadModules()", sysCommandAnnot.id())

    // =========================================================================
    // REQUIREMENT: gRPC service
    // =========================================================================
    val reqGrpc = req("A gRPC server must expose the full intent tree to any remote client over the network", 0)
    note("Uses grpc-kotlin-stub for coroutine-friendly server implementations; all state access is serialised through a single-thread Kotlin coroutine dispatcher", reqGrpc.id())

    val sysGrpcServer = sys("gRPC server implementation, thread dispatch, and lifecycle management", reqGrpc.id())

    val fVoluntasRuntime = file("VoluntasRuntime.kt", sysGrpcServer.id())
    note("Top-level server process: wires gRPC server, optional Ktor web server, module loading, and shutdown hook; also contains the two gRPC service implementations", fVoluntasRuntime.id())

    val cVoluntasRuntime = clazz("VoluntasRuntime", fVoluntasRuntime.id())
    note("Orchestrates the full server: builds gRPC Server with both service impls, starts IntentWebServer if webPort is set, loads modules from directory", cVoluntasRuntime.id())
    method("start() — starts gRPC server, calls loadModules(), starts web server if configured, wires broadcast callbacks on both gRPC impls", cVoluntasRuntime.id())
    method("loadModules(dir: String) — scans directory for .pb files sorted by name; loads each via ModuleLoader; logs manifest; warns if no command annotations found", cVoluntasRuntime.id())
    method("stop() — shuts down web server, gRPC server, and state dispatcher in order", cVoluntasRuntime.id())
    method("blockUntilShutdown() — blocks calling thread until gRPC server terminates", cVoluntasRuntime.id())
    method("main(args: Array<String>) — CLI entry: args are port, fileName, webPort, modulesDir; loads stream or creates new; starts server", cVoluntasRuntime.id())

    val cIntentSvcGrpc = clazz("VoluntasIntentServiceGrpcImpl", fVoluntasRuntime.id())
    note("Coroutine gRPC impl for IntentService; all calls run in stateDispatcher for serialised access; triggers onMutation broadcast callback after successful mutations", cIntentSvcGrpc.id())
    method("submitOp(request): SubmitOpResponse — routes to service.consume(); saves file; extracts new ID from result message; triggers onMutation", cIntentSvcGrpc.id())
    method("getIntent(request): GetIntentResponse — looks up intent by ID; converts to IntentProto via intentToProto()", cIntentSvcGrpc.id())
    method("getFocalScope(request): GetFocalScopeResponse — calls service.getFocalScope(); converts focus/ancestry/children to proto", cIntentSvcGrpc.id())
    method("getCommands(request): GetCommandsResponse — calls getCommandAnnotations(); returns list of CommandInfo (keyword + macroEntityId)", cIntentSvcGrpc.id())
    method("extractIdFromResult(message: String): Long — regex extracts 'intent <N>' from result message to populate SubmitOpResponse.id", cIntentSvcGrpc.id())

    val cVoluntasSvcGrpc = clazz("VoluntasServiceGrpcImpl", fVoluntasRuntime.id())
    note("Coroutine gRPC impl for the raw VoluntasService; provides direct Relationship and Literal submission without high-level routing through SubmitOpRequest", cVoluntasSvcGrpc.id())
    method("submitRelationship(request): SubmitRelationshipResponse — casts service to VoluntasStreamConsumer and calls consume(Relationship)", cVoluntasSvcGrpc.id())
    method("submitLiteral(request): SubmitLiteralResponse — calls service.literalStore.register(literal)", cVoluntasSvcGrpc.id())

    method("intentToProto(intent: Intent): IntentProto — top-level file function; converts Intent including fields, fieldValues (all scalar types), and participantIds", fVoluntasRuntime.id())

    val reqGrpcTest = req("gRPC server startup, module loading integration, and all RPC round-trips must be tested", reqGrpc.id())
    val sysGrpcTest = sys("VoluntasRuntime integration tests", reqGrpcTest.id())
    val fGrpcTest = file("VoluntasRuntimeTest.kt", sysGrpcTest.id())
    note("Integration tests: server start/stop, module loading, SubmitOp RPC round-trips, GetFocalScope, GetCommands after module load, WebSocket broadcast trigger", fGrpcTest.id())
    val cGrpcTest = clazz("VoluntasRuntimeTest", fGrpcTest.id())
    method("testServerStartStop() — server starts and stops cleanly", cGrpcTest.id())
    method("testSubmitOpRoundTrip() — submit CreateIntent op; verify via GetIntent", cGrpcTest.id())
    method("testModuleLoading() — load module; verify types visible via GetFocalScope", cGrpcTest.id())
    method("testGetCommandsAfterModuleLoad() — GetCommands returns module-defined keywords", cGrpcTest.id())

    // =========================================================================
    // REQUIREMENT: Web interface
    // =========================================================================
    val reqWeb = req("A browser-accessible web interface must allow intent navigation and command entry with real-time push updates", 0)
    note("Uses Ktor with embedded Netty; REST API for navigation and commands; WebSocket for server-push tree updates to all connected clients", reqWeb.id())

    val sysWebServer = sys("Ktor HTTP and WebSocket server with per-session state management", reqWeb.id())

    val fWebServer = file("WebServer.kt", sysWebServer.id())
    note("Ktor embedded server exposing a REST API and WebSocket endpoint for the browser-based intent tree UI", fWebServer.id())
    val cIntentWebServer = clazz("IntentWebServer", fWebServer.id())
    note("Wraps Ktor embeddedServer; manages a SessionManager; exposes broadcastAll() for push updates to all connected WebSocket clients; receives commandAnnotations from VoluntasRuntime", cIntentWebServer.id())
    method("start() — starts Ktor embedded Netty server on configured web port", cIntentWebServer.id())
    method("stop() — gracefully shuts down Ktor server", cIntentWebServer.id())
    method("broadcastAll() — suspending: sends updated scope JSON to all active WebSocket sessions", cIntentWebServer.id())
    method("GET /health — returns 200 OK for health checks", cIntentWebServer.id())
    method("GET /api/scope/{id} — returns FocalScope JSON for the given intent ID", cIntentWebServer.id())
    method("GET /api/intent/{id} — returns a single intent as JSON", cIntentWebServer.id())
    method("POST /api/command — parses {sessionId, command} body; routes to session's CommandExecutor; returns result", cIntentWebServer.id())
    method("WebSocket /ws — manages session connections; sends current scope on connect; receives scope updates on mutation", cIntentWebServer.id())

    val fWebSessionState = file("WebSessionState.kt", sysWebServer.id())
    note("Per-session state and session lifecycle management for the web interface", fWebSessionState.id())
    val cWebSessionState = clazz("WebSessionState", fWebSessionState.id())
    note("Data class holding a single web session's CommandExecutor, current focal intent ID, and session identifier string", cWebSessionState.id())
    method("sessionId: String — unique session identifier (e.g. UUID)", cWebSessionState.id())
    method("executor: CommandExecutor — command dispatcher for this session", cWebSessionState.id())
    method("focalIntentId: Long — currently focused intent for this session (mutable)", cWebSessionState.id())
    val cSessionManager = clazz("SessionManager", fWebSessionState.id())
    note("ConcurrentHashMap-based registry of all active sessions; creates sessions on first access with module command annotations pre-registered", cSessionManager.id())
    method("getOrCreate(sessionId: String): WebSessionState — returns existing or creates new session; iterates commandAnnotations; registers DynamicMacroCommand for each", cSessionManager.id())
    method("registerConnection(sessionId: String, channel: WebSocketChannel) — records a WebSocket channel for push updates", cSessionManager.id())
    method("removeConnection(sessionId: String) — removes WebSocket channel on disconnect", cSessionManager.id())
    method("getAllChannels(): List<WebSocketChannel> — returns all active WebSocket channels for broadcast", cSessionManager.id())

    val reqWebMacros = req("Web session CommandExecutors must include module-defined macro commands so that typed commands work in the browser", sysWebServer.id())
    note("Without this, the web interface accepts only builtin commands and cannot execute module-defined commands like 'requirement', 'note', etc.", reqWebMacros.id())
    val sysWebMacros = sys("Thread command annotations from VoluntasRuntime into every new web session", reqWebMacros.id())
    note("IntentWebServer constructor accepts commandAnnotations: List<Pair<String,Long>>; passes to SessionManager", sysWebMacros.id())
    note("SessionManager stores commandAnnotations; getOrCreate() calls executor.registerCommand(DynamicMacroCommand(keyword, macroId)) for each entry", sysWebMacros.id())
    note("VoluntasRuntime.start() calls service.getCommandAnnotations() after loadModules() finishes and passes the result to IntentWebServer", sysWebMacros.id())

    val reqWebTest = req("Web server endpoints, session lifecycle, command routing, and WebSocket broadcasting must be tested", reqWeb.id())
    val sysWebTest = sys("WebServer unit and integration tests", reqWebTest.id())
    val fWebTest = file("WebServerTest.kt", sysWebTest.id())
    note("Tests REST endpoints, session creation, POST /api/command execution with module commands, WebSocket message flow, and broadcast on mutation", fWebTest.id())
    val cWebTest = clazz("WebServerTest", fWebTest.id())
    method("testGetScope() — GET /api/scope/0 returns root scope JSON", cWebTest.id())
    method("testPostCommand() — POST /api/command executes a builtin command and returns result", cWebTest.id())
    method("testSessionCreation() — getOrCreate produces a valid session with a CommandExecutor", cWebTest.id())
    method("testWebSocketBroadcast() — mutation triggers broadcastAll and all clients receive updated scope", cWebTest.id())
    method("testModuleCommandsAvailableInSession() — after passing commandAnnotations, session executor has the module commands registered", cWebTest.id())

    // =========================================================================
    // REQUIREMENT: Terminal TUI interface
    // =========================================================================
    val reqTerminal = req("A keyboard-driven terminal interface must allow local interactive use without a browser", 0)
    note("Built on Lanterna; renders the intent tree with indentation, field values, and ancestry breadcrumbs; connects to a gRPC server", reqTerminal.id())

    val sysTui = sys("Lanterna-based terminal user interface and command processing", reqTerminal.id())

    val fTerminalClient = file("TerminalClient.kt", sysTui.id())
    note("Lanterna-based TUI application: renders intent tree, shows focal scope, accepts keyboard input, connects to gRPC server on startup", fTerminalClient.id())
    val cTerminalClient = clazz("TerminalClient", fTerminalClient.id())
    note("Manages Lanterna Screen, render loop, gRPC stubs, and startup command registration via GetCommands RPC", cTerminalClient.id())
    method("start() — initialises Lanterna screen, connects to gRPC, fetches GetCommands, enters render/input loop", cTerminalClient.id())
    method("render(scope: FocalScope) — draws ancestry breadcrumbs, current focus text, children with field values and indentation", cTerminalClient.id())
    method("main(args: Array<String>) — parses host/port args, constructs TerminalClient, calls start()", cTerminalClient.id())

    val fInputHandler = file("InputHandler.kt", sysTui.id())
    note("Keystroke processing, command registry, and all builtin command implementations for the terminal interface", fInputHandler.id())
    val cCommand = clazz("Command", fInputHandler.id())
    note("Interface for all terminal commands: one execute() method returning a CommandResult", cCommand.id())
    method("execute(input: String, executor: CommandExecutor): CommandResult", cCommand.id())
    val cCommandExecutor = clazz("CommandExecutor", fInputHandler.id())
    note("Holds the current focal intent ID and a keyword→Command dispatch map; processes parsed command input", cCommandExecutor.id())
    method("registerCommand(keyword: String, command: Command) — adds or replaces a command in the dispatch table", cCommandExecutor.id())
    method("execute(input: String): CommandResult — splits input into keyword + args; dispatches to matching Command", cCommandExecutor.id())
    method("focalIntentId: Long — mutable current focal intent ID; updated by navigation commands", cCommandExecutor.id())
    val cInputHandler = clazz("InputHandler", fInputHandler.id())
    note("Wraps Lanterna KeyStroke processing; builds input buffer character by character; on Enter dispatches to executor; handles Backspace and Escape", cInputHandler.id())
    method("handleKey(key: KeyStroke): CommandResult? — processes one keystroke; returns CommandResult on Enter, null otherwise", cInputHandler.id())
    val cBuiltins = clazz("Builtin command classes", fInputHandler.id())
    note("One class per builtin terminal command; all implement Command interface", cBuiltins.id())
    method("AddCommand — submits CreateIntent op with input as text and focal as parent; refocuses on new intent", cBuiltins.id())
    method("FocusCommand — changes focal intent to the ID given in input", cBuiltins.id())
    method("UpCommand — moves focus to parent of current focal intent", cBuiltins.id())
    method("UpdateCommand — submits UpdateIntentText op for the focal intent", cBuiltins.id())
    method("MoveCommand — submits UpdateIntentParent op to re-parent an intent", cBuiltins.id())
    method("DoCommand — submits SetFieldValue done=true on the focal intent", cBuiltins.id())
    method("DeleteCommand — soft-deletes the focused intent", cBuiltins.id())
    method("WriteCommand — triggers writeToFile on the server", cBuiltins.id())
    method("WriteNoGarbageCommand — writes stream excluding soft-deleted intents", cBuiltins.id())
    method("ImportCommand — submits an ImportPlan op to load a .pb file into the running server", cBuiltins.id())
    val cDynamicMacro = clazz("DynamicMacroCommand", fInputHandler.id())
    note("Registered at startup for each module-defined command; submits InvokeMacro op with the input as textArg and focal as parent", cDynamicMacro.id())
    method("execute(input, executor) — submits InvokeMacro request with input text and current focalIntentId as parent", cDynamicMacro.id())

    val reqInputTest = req("Keystroke handling, command parsing, focal intent navigation, and all builtin command behaviours must be tested", reqTerminal.id())
    val sysInputTest = sys("InputHandler unit tests", reqInputTest.id())
    val fInputTest = file("InputHandlerTest.kt", sysInputTest.id())
    note("Tests keystroke buffer management, Enter/Backspace handling, focus/up/add/update commands, and executor dispatch", fInputTest.id())
    val cInputTest = clazz("InputHandlerTest", fInputTest.id())
    method("testBufferAccumulation() — typing characters builds input buffer correctly", cInputTest.id())
    method("testBackspace() — Backspace removes last character from buffer", cInputTest.id())
    method("testCommandDispatch() — Enter dispatches to correct registered command", cInputTest.id())
    method("testFocusUp() — FocusCommand and UpCommand change focalIntentId correctly", cInputTest.id())

    // =========================================================================
    // REQUIREMENT: PrintPlan utility
    // =========================================================================
    val reqPrint = req("Developers must be able to inspect any .pb stream file as a human-readable intent tree from the command line", 0)
    val sysPrint = sys("PrintPlan command-line utility", reqPrint.id())
    val fPrintPlan = file("PrintPlan.kt", sysPrint.id())
    note("Standalone utility: loads a .pb stream file, replays it, and prints the intent tree with indentation and field values to stdout", fPrintPlan.id())
    method("main(args: Array<String>) — takes .pb file path as arg; calls VoluntasIntentService.fromFile(); recursively prints tree from root with depth-based indentation", fPrintPlan.id())

    // =========================================================================
    // REQUIREMENT: Claude worker
    // =========================================================================
    val reqWorker = req("An autonomous Claude worker must execute leaf intents via the Claude API using tools (read_file, write_file, bash)", 0)
    note("The worker recursively fetches the intent tree, selects incomplete leaf intents, executes each with Claude, and records timestamps and token counts", reqWorker.id())

    val sysWorkerOrch = sys("Worker orchestration, CLI entry point, and gRPC client", reqWorker.id())

    val fClaudeWorker = file("ClaudeWorker.kt", sysWorkerOrch.id())
    note("Main entry point for the autonomous worker; parses CLI args, orchestrates the fetch→execute→track loop, writes final token totals to the requirement intent", fClaudeWorker.id())
    val cClaudeWorker = clazz("ClaudeWorker", fClaudeWorker.id())
    note("Orchestrates the full worker lifecycle: fetch leaf intents → mark each started → execute via Claude API → mark finished → record token counts", cClaudeWorker.id())
    method("executeAll(intents: List<IntentProto>) — iterates leaf intents in order; marks start, calls executor, marks finish", cClaudeWorker.id())
    method("main(args: Array<String>) — CLI: --server and --intent args; creates gRPC client, fetcher, executor, tracker; calls executeAll(); writes total tokens to requirement intent", cClaudeWorker.id())

    val fWorkerGrpcClient = file("WorkerGrpcClient.kt", sysWorkerOrch.id())
    note("gRPC client wrapper used exclusively by the worker; simplifies connection lifecycle and operation submission with typed helper methods", fWorkerGrpcClient.id())
    val cWorkerGrpcClient = clazz("WorkerGrpcClient", fWorkerGrpcClient.id())
    note("Manages ManagedChannel and gRPC stub lifecycle; all helpers run inside the established channel connection", cWorkerGrpcClient.id())
    method("connect() — creates ManagedChannel to the configured server address", cWorkerGrpcClient.id())
    method("getIntent(id: Long): IntentProto — fetches a single intent by ID", cWorkerGrpcClient.id())
    method("getFocalScope(id: Long): FocalScopeProto — fetches focal scope for navigation and leaf detection", cWorkerGrpcClient.id())
    method("submitOp(request: SubmitOpRequest): SubmitOpResponse — submits any high-level op", cWorkerGrpcClient.id())
    method("close() — gracefully shuts down the ManagedChannel", cWorkerGrpcClient.id())

    val sysWorkerFetch = sys("Recursive intent tree fetching and incomplete leaf selection", reqWorker.id())
    val fIntentFetcher = file("IntentFetcher.kt", sysWorkerFetch.id())
    note("Recursively traverses the intent tree via getFocalScope RPCs to collect all incomplete leaf intents for execution", fIntentFetcher.id())
    val cIntentFetcher = clazz("IntentFetcher", fIntentFetcher.id())
    note("Uses depth-first traversal; a leaf is any intent with no incomplete children; filters out intents with done=true", cIntentFetcher.id())
    method("fetchLeaves(intentId: Long): List<IntentProto> — recursively collects all incomplete leaf intents under the given root", cIntentFetcher.id())
    method("isLeaf(scope: FocalScopeProto): Boolean — true if intent has no children or all children are already done", cIntentFetcher.id())

    val sysWorkerExec = sys("Intent execution via the Claude API with tool use loop", reqWorker.id())
    val fIntentExecutor = file("IntentExecutor.kt", sysWorkerExec.id())
    note("Executes a single intent by building a Claude prompt from the intent and its ancestors, then running the tool-use loop until Claude stops", fIntentExecutor.id())
    val cIntentExecutor = clazz("IntentExecutor", fIntentExecutor.id())
    note("Builds a system prompt from intent text and ancestor context; manages the Claude API message loop; processes tool calls (read_file, write_file, bash) until Claude stops with no tool use", cIntentExecutor.id())
    method("execute(intent: IntentProto, ancestry: List<IntentProto>): ExecutionResult — main execution: build prompt, start message loop, handle tool calls", cIntentExecutor.id())
    method("buildPrompt(intent: IntentProto, ancestry: List<IntentProto>): String — formats intent text + ancestor breadcrumbs into a Claude system prompt", cIntentExecutor.id())

    val fClaudeApiClient = file("ClaudeApiClient.kt", sysWorkerExec.id())
    note("Anthropic SDK wrapper: sends messages with tool definitions (read_file, write_file, bash), parses tool_use content blocks, accumulates token counts across all calls", fClaudeApiClient.id())
    val cClaudeApiClient = clazz("ClaudeApiClient", fClaudeApiClient.id())
    note("Manages the Anthropic client, message history, and running token totals; exposes a simple send/parse API for IntentExecutor", cClaudeApiClient.id())
    method("sendMessage(messages: List<Message>, tools: List<Tool>): ClaudeResponse — sends to Anthropic API with configured model; accumulates token counts", cClaudeApiClient.id())
    method("parseToolUseCalls(response): List<ToolUseCall> — extracts all tool_use content blocks from a response", cClaudeApiClient.id())
    method("totalInputTokens: Long — running total of input tokens across all API calls in this session", cClaudeApiClient.id())
    method("totalOutputTokens: Long — running total of output tokens across all API calls in this session", cClaudeApiClient.id())

    val sysWorkerTrack = sys("Timestamp and token-count tracking for executed intents", reqWorker.id())
    val fTimestampTracker = file("TimestampTracker.kt", sysWorkerTrack.id())
    note("Records start/end timestamps and Claude API token usage on intent entities via gRPC SetFieldValue ops", fTimestampTracker.id())
    val cTimestampTracker = clazz("TimestampTracker", fTimestampTracker.id())
    note("Uses the worker gRPC client to write INT64 field values; timestamps are epoch-nanoseconds; token fields match the requirement type's input_tokens/output_tokens fields", cTimestampTracker.id())
    method("markIntentStarted(id: Long) — writes start_timestamp field via SetFieldValue op", cTimestampTracker.id())
    method("markIntentFinished(id: Long) — writes end_timestamp field", cTimestampTracker.id())
    method("recordTokenUsage(id: Long, inputTokens: Long, outputTokens: Long) — writes input_tokens and output_tokens INT64 fields", cTimestampTracker.id())

    // =========================================================================
    // REQUIREMENT: Timeline visualiser
    // =========================================================================
    val reqVisualizer = req("Generate an interactive HTML timeline visualisation from a Voluntas stream file showing execution timing and duration", 0)
    note("Reads start/end timestamps from intent field values; renders a Gantt-style HTML timeline with depth-based colouring, hover details, and scrollable layout", reqVisualizer.id())

    val sysVisualizer = sys("HTML timeline generator from .pb stream files", reqVisualizer.id())
    val fTimelineVisualizer = file("TimelineVisualizer.kt", sysVisualizer.id())
    note("Reads a .pb stream file, extracts timing data from field values, and generates a self-contained HTML page with a Gantt-style execution timeline", fTimelineVisualizer.id())
    val cTimelineVisualizer = clazz("TimelineVisualizer", fTimelineVisualizer.id())
    note("Processes the intent tree to extract TimelineEntry records; renders scrollable HTML with inline CSS and JavaScript", cTimelineVisualizer.id())
    method("generate(inputFile: String, outputFile: String) — top-level entry: load stream, extract entries, write HTML file", cTimelineVisualizer.id())
    method("extractEntries(service: VoluntasIntentService): List<TimelineEntry> — traverses all intents; reads start_timestamp, end_timestamp, done fields; computes depth", cTimelineVisualizer.id())
    method("generateHtml(entries: List<TimelineEntry>): String — renders the full HTML/CSS/JS Gantt timeline page as a string", cTimelineVisualizer.id())
    val cTimelineEntry = clazz("TimelineEntry", fTimelineVisualizer.id())
    note("Data class for one row in the timeline: id, text, startTime, endTime, computed duration (ms), done flag, and tree depth for visual indentation", cTimelineEntry.id())

    // =========================================================================
    // REQUIREMENT: Self-describing plan system (plans package)
    // =========================================================================
    val reqPlans = req("The system must be able to describe its own build plan and any subsystem plan as a Voluntas intent stream", 0)
    note("Plan generator programs produce .pb stream files encoding hierarchical plans; used to bootstrap the intent tree for a new server instance", reqPlans.id())

    val sysPlans = sys("Plan generator programs (plans package)", reqPlans.id())
    note("Each generator produces a .pb stream file; the plans package is excluded from the per-file class/method hierarchy to avoid circularity", sysPlans.id())

    val fGenWebServerPlan = file("GenerateWebServerPlan.kt", sysPlans.id())
    note("Two-level requirement/system plan for the Ktor web interface, covering all server components and session management", fGenWebServerPlan.id())
    val fGenWorkerPlan = file("GenerateClaudeWorkerPlan.kt", sysPlans.id())
    note("Plan for the Claude worker including fetch, execute, tracking, and gRPC client subsystems", fGenWorkerPlan.id())
    val fGenTimelinePlan = file("GenerateTimelineVisualizerPlan.kt", sysPlans.id())
    note("Plan for the HTML timeline visualiser", fGenTimelinePlan.id())
    val fGenTicTacToePlan = file("GenerateTicTacToePlan.kt", sysPlans.id())
    note("Standalone demo plan: HTML+JS Tic Tac Toe game; demonstrates plan generation for a non-Voluntas deliverable", fGenTicTacToePlan.id())
    val fGenVoluntasPlan = file("GenerateVoluntasPlan.kt", sysPlans.id())
    note("This file: generates the Voluntas self-describing build plan with full requirement/system/file/class/method hierarchy for every source file", fGenVoluntasPlan.id())

    // =========================================================================
    // REQUIREMENT: Build system and tooling
    // =========================================================================
    val reqBuild = req("The project must be buildable, testable, and runnable via a consistent Gradle toolchain with shell script convenience wrappers", 0)
    note("Gradle build uses the protobuf plugin to generate Kotlin stubs from voluntas.proto; each runnable component has a dedicated JavaExec task", reqBuild.id())

    val sysBuild = sys("Gradle build, shell scripts, and protobuf code generation", reqBuild.id())

    val fBuildGradle = file("build.gradle.kts", sysBuild.id())
    note("Kotlin JVM Gradle build: plugin versions, all dependencies (gRPC, Ktor, Lanterna, Gson), protobuf code generation config, and all runnable tasks", fBuildGradle.id())
    method("runServer — starts VoluntasRuntime on port 50051 with web port 8080 and modules/ directory", fBuildGradle.id())
    method("runClient — starts TerminalClient connecting to localhost:50051", fBuildGradle.id())
    method("runGenerateStandard — regenerates modules/standard.pb", fBuildGradle.id())
    method("runGenerateSoftware — regenerates modules/software.pb", fBuildGradle.id())
    method("runGenerateVoluntasPlan — regenerates voluntas_plan.pb", fBuildGradle.id())
    method("printRuntimeClasspath — prints runtime classpath for use in shell scripts", fBuildGradle.id())

    val fServerSh = file("tools/server.sh", sysBuild.id())
    note("Shell wrapper: builds the project then starts VoluntasRuntime with port 50051, web port 8888, and modules/ directory", fServerSh.id())
    val fClientSh = file("tools/client.sh", sysBuild.id())
    note("Shell wrapper: builds and starts TerminalClient connecting to localhost:50051", fClientSh.id())
    val fWorkerSh = file("tools/worker.sh", sysBuild.id())
    note("Shell wrapper: builds and starts ClaudeWorker with configurable --server and --intent arguments", fWorkerSh.id())
    val fVisualizeSh = file("tools/visualize.sh", sysBuild.id())
    note("Shell wrapper: builds and runs TimelineVisualizer on a specified .pb stream file", fVisualizeSh.id())
    val fPrintPbSh = file("tools/print-pb-file.sh", sysBuild.id())
    note("Shell wrapper: prints any .pb stream file as a human-readable intent tree via PrintPlan.kt", fPrintPbSh.id())

    val fIntentGetSh = file("tools/intent-get.sh", sysBuild.id())
    note("grpcurl wrapper: calls GetFocalScope RPC and returns JSON; used by the Claude Code agent to navigate the intent tree", fIntentGetSh.id())
    val fIntentAddSh = file("tools/intent-add.sh", sysBuild.id())
    note("grpcurl wrapper: submits CreateIntent SubmitOp and returns JSON with the new intent's ID", fIntentAddSh.id())
    val fIntentMarkSh = file("tools/intent-mark.sh", sysBuild.id())
    note("grpcurl wrapper: marks an intent started, done, or failed by submitting SetFieldValue ops with timestamp fields", fIntentMarkSh.id())
    val fIntentSetTokensSh = file("tools/intent-set-tokens.sh", sysBuild.id())
    note("grpcurl wrapper: writes input_tokens and output_tokens INT64 fields on a requirement intent after a worker turn", fIntentSetTokensSh.id())

    val fClaudeMd = file("CLAUDE.md", sysBuild.id())
    note("Workflow instructions for the Claude Code agent: intent tree protocol, tree exploration procedure, requirement/system/implementation pattern, and token tracking steps", fClaudeMd.id())

    // Write to file
    service.writeToFile("voluntas_plan.pb")
    println("Generated voluntas_plan.pb")
}
