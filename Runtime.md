# Voluntas Runtime

## Overview

The Voluntas runtime is an append-only event-sourced system. All state is derived
by replaying a **stream** of operations from the beginning. There is no mutation of
past state — every change is expressed as a new op appended to the stream. This
makes the stream an authoritative, auditable log of everything that has ever
happened.

The runtime's job is to interpret that stream into queryable in-memory state: a
graph of **entities**, each with an ID, optional fields, and optional field values.

---

## The Stream

A stream is a sequence of `Op` messages. Each op is either:

- **Literal** — an immutable value (string, int, bool, double, bytes) with a
  content-addressed ID.
- **Relationship** — a tuple of the form `(id, participants[])` where `id` is the
  entity being defined or affected, and `participants` is an ordered list of
  entity or literal IDs that describe the relationship.

Ops are always appended; never modified or deleted. The stream is replayed in
order on startup to reconstruct the current state.

---

## The Literal Store

Relationships reference values by ID, not by value. The **literal store** is the
registry that maps values to their IDs and back.

### Why a literal store?

Relationships are tuples of 64-bit IDs. To embed a string like `"done"` or a
boolean `true` into a relationship, you need a way to refer to that value by ID.
The literal store assigns each unique value a stable ID with the **high bit set**
(`0x8000000000000000`), distinguishing literals from entity IDs at a glance.

The store is **content-addressed**: the same value always gets the same ID.
Storing `"done"` twice returns the same literal ID both times. This deduplication
keeps the stream compact and makes equality checks cheap — comparing two literal
references is just comparing two `Long` values.

Literals are immutable. Once a literal ID is written into a relationship in the
stream, the value it refers to never changes.

### Bootstrap literals

The store pre-registers a set of well-known strings at fixed ordinals so that
replay always produces consistent IDs:

| Ordinal | Literal string       |
|---------|----------------------|
| 1       | `"defines_type"`     |
| 2       | `"defines_field"`    |
| 3       | `"instantiates"`     |
| 4       | `"sets_field"`       |
| 5       | `"adds_participant"` |
| 6       | `"depends_on"`       |
| 7       | `"text"`             |
| 8       | `"parent"`           |
| 9       | `"STRING"`           |
| 10      | `"INTENT_REF"`       |

User literals are allocated starting at ordinal 11.

---

## The Entity ID Space

Entity IDs are 64-bit integers with the **high bit clear**. Literal IDs have the
high bit set. This single-bit distinction means any participant in a relationship
can be classified as "entity" or "value" by inspecting one bit.

### Reserved entity IDs

Low-numbered entity IDs are reserved for built-in relationship types. These
entities exist implicitly — they are never instantiated through a relationship;
the runtime recognises them by their fixed ID.

| ID  | Name                | Purpose                                         |
|-----|---------------------|-------------------------------------------------|
| 0   | `ROOT_INTENT`       | The root of the intent tree                     |
| 1   | `DEFINES_TYPE`      | Declares a new type entity                      |
| 2   | `DEFINES_FIELD`     | Adds a field to a type or instance              |
| 3   | `INSTANTIATES`      | Creates an instance of a type                   |
| 4   | `SETS_FIELD`        | Sets a field value on an entity                 |
| 5   | `ADDS_PARTICIPANT`  | Adds a participant to an entity                 |
| 6   | `DEFINES_FUNCTION`  | Declares a named, parameterised function        |
| 7   | `STRING_INTENT_TYPE`| The built-in "string intent" type               |
| 8   | *(text field def)*  | The `text` field on STRING_INTENT_TYPE          |
| 9   | `DEFINES_BODY_OP`   | Appends a template op to a function's body      |
| 10  | `INVOKES_FUNCTION`  | Invokes a function, expanding its body          |

User entities are allocated starting at ID **1000**.

---

## Relationship Types

### DEFINES_TYPE (1)

```
Relationship(id=typeId, participants=[DEFINES_TYPE, (moduleEntityId)?])
```

Declares `typeId` as a new type. An optional second participant links the type to
a module entity (its parent in the tree). The runtime creates a meta-intent for
the type entity.

### DEFINES_FIELD (2)

```
Relationship(id=fieldId, participants=[DEFINES_FIELD, targetId, nameLit, fieldTypeLit, (requiredLit)?, (descLit)?])
```

Registers a named, typed field on `targetId`. The field name and type are literal
strings (`"done"`, `"BOOL"`, etc.). Required and description are optional. The
runtime calls `addField` on the target entity.

### INSTANTIATES (3)

```
Relationship(id=entityId, participants=[INSTANTIATES, typeId, textLitId, (parentId)?])
```

Creates an instance of `typeId`. For `STRING_INTENT_TYPE` (7) this creates a
regular user intent: the third participant is the text literal, the optional
fourth is the parent entity. For any other type, a generic meta-intent is created.

### SETS_FIELD (4)

```
Relationship(id=relId, participants=[SETS_FIELD, targetId, fieldNameLit, valueOrEntityId])
```

Sets a field on `targetId`. The field name `"text"` updates the intent's display
text. The field name `"parent"` reparents the intent. All other field names set
arbitrary typed values via the literal store.

### ADDS_PARTICIPANT (5)

```
Relationship(id=relId, participants=[ADDS_PARTICIPANT, targetId, participantId, (indexLit)?])
```

Appends (or inserts at `indexLit`) a new participant to `targetId`'s participant
list, also linking `targetId` as a child of `participantId` in the tree.

### DEFINES_FUNCTION (6)

```
Relationship(id=funcId, participants=[DEFINES_FUNCTION, nameLit, param0Lit, param1Lit, ...])
```

Declares `funcId` as a function entity. `nameLit` is the string name of the
function. The remaining participants are string literals naming each parameter in
order. The body is empty at this point; body ops are attached with
`DEFINES_BODY_OP`.

### DEFINES_BODY_OP (9)

```
Relationship(id=bodyOpId, participants=[DEFINES_BODY_OP, funcId, opTypeEntityId, p0, p1, ...])
```

Appends one template step to the body of `funcId`. `opTypeEntityId` is the
reserved entity for the relationship type to emit when the function is invoked
(e.g. `DEFINES_FIELD` = 2, `SETS_FIELD` = 4). `p0`, `p1`, ... are the template
participants, which may be concrete entity/literal IDs or **parameter reference**
literals (see below). Body ops accumulate in the order they appear in the stream.

### INVOKES_FUNCTION (10)

```
Relationship(id=invocationId, participants=[INVOKES_FUNCTION, funcId, arg0, arg1, ...])
```

Invokes `funcId` with the supplied arguments. The runtime expands the function
body: for each body op template, it allocates a fresh entity ID, substitutes
parameter references with the corresponding arguments, and emits the resulting
concrete relationship into the stream. Each emitted op is then immediately
interpreted by the normal handlers. Invocation is a no-op during stream replay
because the expanded ops are already present in the stream and will be interpreted
when replayed in sequence.

---

## Parameter References

A parameter reference is a string literal whose value starts with `$` followed
by either a zero-based integer index or the declared parameter name.

| Form        | Example      | Resolves to                              |
|-------------|--------------|------------------------------------------|
| Positional  | `$0`, `$1`   | The argument at that index               |
| Named       | `$intentId`  | The argument for the parameter with that name |

Both forms are equivalent and can be mixed within the same function body.
`$0` and `$intentId` refer to the same argument if `intentId` is the first
declared parameter.

During invocation, `handleInvokesFunction` inspects each template participant.
If it is a literal starting with `$`, the runtime first tries to parse the
suffix as an integer (positional). If that fails — e.g. `$intentId` —
`toIntOrNull()` returns null and the runtime falls through to a name lookup
against the function's declared parameter list. Concrete participants (entity
IDs or non-`$` literals) pass through unchanged.

`VoluntasIntentService` provides two `paramRef` helpers for building body op
participant lists:

```kotlin
service.paramRef(0)          // positional: returns literal ID for "$0"
service.paramRef("intentId") // named: returns literal ID for "$intentId"
```

If a body op template contains a `$`-prefixed literal that does not match any
declared parameter by index or name, `addBodyOp` throws `IllegalArgumentException`
at definition time.

---

## Types and Instantiation

The type system is itself expressed in the stream. A type is an entity created by
`DEFINES_TYPE`. Fields are attached to it with `DEFINES_FIELD`. Instances are
created with `INSTANTIATES`.

There is one built-in type: **STRING_INTENT_TYPE** (entity 7). Every user-visible
intent in the tree is an instance of this type. Its `text` field (entity 8) holds
the display string. This bootstrap is hardcoded in `emitBootstrap()` and is
always the first thing written to a new stream.

Custom types can be created at runtime (e.g. by a module) and then instantiated
the same way. An instance of a custom type becomes a meta-intent — it exists in
the entity map but is filtered out of the visible intent tree by the `isMeta()`
flag.

---

## State Derivation

On startup, the runtime calls `replayStream()`, which processes every op in order:

1. **Literal ops** are registered in the literal store, restoring the value→ID
   mapping without side effects.
2. **Relationship ops** are passed to `interpretRelationship()`, which dispatches
   on `participants[0]` to one of the handlers above.

The `isReplaying` flag is set to `true` for the duration of replay. The
`INVOKES_FUNCTION` handler checks this flag and skips expansion during replay,
because the expanded ops are already present in the stream ahead of it.

The result is a consistent in-memory map of entities (`byId`) and their child
relationships (`childrenById`). No op is ever skipped or re-ordered. The state at
any point in time is fully determined by the prefix of the stream up to that
point.

---

## Function Definition and Invocation

Functions allow a sequence of ops to be named, parameterised, and reused.

**The key invariant: invoking a function has exactly one observable effect —
additional ops are appended to the stream.** There are no other side effects. The
newly appended ops are themselves interpreted by the normal relationship handlers,
so functions compose with the rest of the system for free.

### Defining a function

```kotlin
val funcId = service.defineFunction("do", listOf("intentId"))

// Body op 0: DEFINES_FIELD $intentId "done" "BOOL"  (named ref)
service.addBodyOp(funcId, VoluntasIds.DEFINES_FIELD,
    listOf(service.paramRef("intentId"),
           service.literalStore.getOrCreate("done"),
           service.literalStore.getOrCreate("BOOL")))

// Body op 1: SETS_FIELD $0 "done" true  (positional ref — same parameter)
service.addBodyOp(funcId, VoluntasIds.SETS_FIELD,
    listOf(service.paramRef(0),
           service.literalStore.getOrCreate("done"),
           service.literalStore.getOrCreate(true)))
```

### Invoking a function

```kotlin
service.invokeFunction(funcId, listOf(targetIntentId))
```

This emits an `INVOKES_FUNCTION` relationship into the stream. The runtime then
expands the body, substituting `$0` with `targetIntentId`, and emits two concrete
relationships: a `DEFINES_FIELD` and a `SETS_FIELD`. Those are interpreted
immediately, adding the `done` field and setting it to `true` on the target
intent.

### Limitations: no recursive functions

The current implementation does not support arbitrary recursive function calls.
There are two reasons:

1. **No conditional / base case.** The function body is a flat, unconditional list
   of template ops. There is no branching primitive, so there is no way to express
   "stop recursing when a condition holds." A function that invokes itself would
   expand indefinitely.

2. **Eager, synchronous expansion.** Expansion happens inline inside
   `handleInvokesFunction`. A body op whose `opTypeEntityId` is
   `INVOKES_FUNCTION` would immediately re-enter the handler, consuming JVM stack
   rather than just appending to the stream.

Support for recursion may be added in the future. It would require at minimum a
conditional guard primitive — an op that checks a field value on an entity and
only proceeds with the remaining body ops if the condition holds.
