/**
 * JavaScript port of VoluntasIntentService.
 *
 * Mirrors the Kotlin implementation (VoluntasIntentService.kt + LiteralStore.kt)
 * so the browser can replay a stream and maintain its own local intent state.
 *
 * All entity/literal IDs are BigInt internally.  Public API methods accept and
 * return Number for entity IDs (which stay well under 2^53) and return BigInt
 * for literal IDs when needed.  Use Number(intent.id()) to convert for display.
 */

// ============================================================
// Bootstrap constants (must match VoluntasIds.kt)
// ============================================================

export const DEFINES_TYPE      = 1n;
export const DEFINES_FIELD     = 2n;
export const INSTANTIATES      = 3n;
export const SETS_FIELD        = 4n;
export const ADDS_PARTICIPANT  = 5n;
export const DEFINES_MACRO     = 6n;
export const STRING_INTENT_TYPE = 7n;
export const MARKS_META        = 8n;
export const DEFINES_MACRO_OP  = 9n;
export const INVOKES_MACRO     = 10n;
export const NAME_TYPE         = 11n;
export const META_ROOT         = 12n;
export const NAMES_ROOT        = 13n;
export const META_NAME_NODE    = 14n;
export const MARKS_META_OP     = 15n;
export const REMOVES_PARTICIPANT = 16n;

export const ROOT_INTENT       = 0n;
export const FIRST_USER_ENTITY = 1000n;
export const LITERAL_BIT       = 0x8000000000000000n;

export function isLiteral(id)   { return (BigInt(id) & LITERAL_BIT) !== 0n; }
export function isEntity(id)    { return (BigInt(id) & LITERAL_BIT) === 0n; }
export function literalOrdinal(id) { return BigInt(id) & ~LITERAL_BIT; }
export function literalId(ordinal) { return LITERAL_BIT | BigInt(ordinal); }

// ============================================================
// LiteralStore  (mirrors LiteralStore.kt)
// ============================================================

class LiteralStore {
  constructor() {
    // "type\0serializedValue" => literal object
    this._byValue = new Map();
    // BigInt id => literal object
    this._byId = new Map();
    this._nextOrdinal = 11n;

    this._bootstrap(1n,  'string', 'defines_type');
    this._bootstrap(2n,  'string', 'defines_field');
    this._bootstrap(3n,  'string', 'instantiates');
    this._bootstrap(4n,  'string', 'sets_field');
    this._bootstrap(5n,  'string', 'adds_participant');
    this._bootstrap(6n,  'string', 'depends_on');
    this._bootstrap(7n,  'string', 'text');
    this._bootstrap(8n,  'string', 'parent');
    this._bootstrap(9n,  'string', 'STRING');
    this._bootstrap(10n, 'string', 'INTENT_REF');
  }

  _bootstrap(ordinal, type, value) {
    const id  = literalId(ordinal);
    const lit = { id, type, value };
    this._byValue.set(this._key(type, value), lit);
    this._byId.set(id, lit);
  }

  // Stable key: type + NUL + JSON-serialised value (handles BigInt via String())
  _key(type, value) {
    return `${type}\0${typeof value === 'bigint' ? value.toString() : JSON.stringify(value)}`;
  }

  /** Returns the literal ID for value, creating a new literal if needed. */
  getOrCreate(value) {
    let type, cv;
    if (typeof value === 'string') {
      type = 'string'; cv = value;
    } else if (typeof value === 'boolean') {
      type = 'bool'; cv = value;
    } else if (typeof value === 'bigint') {
      type = 'int64'; cv = value;
    } else if (typeof value === 'number') {
      if (Number.isInteger(value)) { type = 'int64'; cv = BigInt(value); }
      else                          { type = 'double'; cv = value; }
    } else {
      throw new TypeError(`Unsupported literal type: ${typeof value} (${value})`);
    }

    const key = this._key(type, cv);
    const existing = this._byValue.get(key);
    if (existing) return existing.id;

    const id  = literalId(this._nextOrdinal++);
    const lit = { id, type, value: cv };
    this._byValue.set(key, lit);
    this._byId.set(id, lit);
    return id;
  }

  getById(id) { return this._byId.get(BigInt(id)); }

  getString(id) {
    const lit = this._byId.get(BigInt(id));
    return (lit && lit.type === 'string') ? lit.value : null;
  }

  /** Register an externally-created literal (e.g. during stream replay). */
  register(lit) {
    // lit must have { id: BigInt, type, value }
    const id = BigInt(lit.id);
    this._byValue.set(this._key(lit.type, lit.value), { ...lit, id });
    this._byId.set(id, { ...lit, id });
    const ord = literalOrdinal(id);
    if (ord >= this._nextOrdinal) this._nextOrdinal = ord + 1n;
  }

  reset() {
    this._byValue.clear();
    this._byId.clear();
    this._nextOrdinal = 7n;
    this._bootstrap(1n,  'string', 'defines_type');
    this._bootstrap(2n,  'string', 'defines_field');
    this._bootstrap(3n,  'string', 'instantiates');
    this._bootstrap(4n,  'string', 'sets_field');
    this._bootstrap(5n,  'string', 'adds_participant');
    this._bootstrap(6n,  'string', 'depends_on');
    this._bootstrap(7n,  'string', 'text');
    this._bootstrap(8n,  'string', 'parent');
    this._bootstrap(9n,  'string', 'STRING');
    this._bootstrap(10n, 'string', 'INTENT_REF');
    this._nextOrdinal = 11n;
  }
}

// ============================================================
// IntentImpl  (mirrors IntentImpl.kt)
// ============================================================

class IntentImpl {
  constructor({
    id,
    text,
    participantIds = [],
    createdTimestamp = null,
    lastUpdatedTimestamp = null,
    fields = {},
    values = {},
    isMeta = true,
    typeName = null,
  }) {
    this._id                   = BigInt(id);
    this._text                 = text;
    this._participantIds       = participantIds.map(x => BigInt(x));
    this._createdTimestamp     = createdTimestamp;
    this._lastUpdatedTimestamp = lastUpdatedTimestamp;
    this._fields               = { ...fields };
    this._values               = { ...values };
    this._isMeta               = isMeta;
    this._typeName             = typeName;
  }

  id()                  { return this._id; }
  text()                { return this._text; }
  participantIds()      { return [...this._participantIds]; }
  isMeta()              { return this._isMeta; }
  typeName()            { return this._typeName; }
  createdTimestamp()    { return this._createdTimestamp; }
  lastUpdatedTimestamp(){ return this._lastUpdatedTimestamp; }
  fields()              { return { ...this._fields }; }
  fieldValues()         { return { ...this._values }; }
  // children() always returns [] — the service owns the children index
  children()            { return []; }
}

// ============================================================
// VoluntasIntentService  (mirrors VoluntasIntentService.kt)
// ============================================================

export class VoluntasIntentService {

  /** @private — use VoluntasIntentService.new() or VoluntasIntentService.fromOps() */
  constructor(streamId) {
    this._streamId = streamId;
    this.literalStore = new LiteralStore();

    // id → IntentImpl
    this._byId = new Map();

    // parent BigInt id → Set of child BigInt ids
    this._childrenById = new Map();

    this._nextEntityId = FIRST_USER_ENTITY;

    // macro id → { name, paramNames: string[], bodyOps: [{opTypeEntityId, participants}] }
    this._macros = new Map();

    this._isReplaying = false;

    // Types whose instances are visible non-meta intents (starts with STRING_INTENT_TYPE)
    this._visibleTypes = new Set([STRING_INTENT_TYPE]);

    // type id → [instance ids]
    this._instancesByType = new Map();

    // name system
    this._nameNodeToPath = new Map([[NAMES_ROOT, '']]);
    this._namesByPath    = new Map();
    this._nameNodeByReferent = new Map();
    this._referentByNameNode = new Map();
    this._typeAutoNames  = new Set();

    // All emitted ops in serializable format (strings for BigInt IDs)
    this._ops = [];
  }

  // ----------------------------------------------------------
  // Factory methods
  // ----------------------------------------------------------

  /** Create a fresh service with a root intent. */
  static new(rootIntentText) {
    const svc = new VoluntasIntentService('intent-stream');
    svc._emitBootstrap(rootIntentText);
    return svc;
  }

  /**
   * Replay a stream expressed as an array of op objects:
   *   { type: 'literal',      id: BigInt|string, litType: string, value: any }
   *   { type: 'relationship', id: BigInt|string, participants: (BigInt|string)[], timestamp: BigInt|string|null }
   */
  static fromOps(ops, streamId = 'intent-stream') {
    const svc = new VoluntasIntentService(streamId);
    svc._replayOps(ops);
    return svc;
  }

  /** Returns all emitted ops in a JSON-serializable format (IDs as strings). */
  toOps() { return [...this._ops]; }

  // ----------------------------------------------------------
  // Public high-level API  (mirrors IntentService.kt)
  // ----------------------------------------------------------

  /** Add an intent under parentId. Returns the new IntentImpl. */
  addIntent(text, parentId) {
    const id = this._nextEntityId++;
    const textLitId = this.literalStore.getOrCreate(text);
    this._emitRelationship({
      id,
      participants: [INSTANTIATES, STRING_INTENT_TYPE, textLitId, BigInt(parentId)],
    });
    return this._byId.get(id);
  }

  /** Update the text of an intent. */
  edit(id, newText) {
    const bid = BigInt(id);
    if (!this._byId.has(bid)) throw new Error(`No intent with id ${id}`);
    const relId       = this._nextEntityId++;
    const fieldNameLit = this.literalStore.getOrCreate('text');
    const newTextLit   = this.literalStore.getOrCreate(newText);
    this._emitRelationship({
      id: relId,
      participants: [SETS_FIELD, bid, fieldNameLit, newTextLit],
    });
  }

  /** Move an intent to a new primary parent. */
  moveParent(id, newParentId) {
    const bid = BigInt(id);
    const npid = BigInt(newParentId);
    if (!this._byId.has(bid))  throw new Error(`No intent with id ${id}`);
    if (!this._byId.has(npid)) throw new Error(`No intent with id ${newParentId}`);
    const relId       = this._nextEntityId++;
    const fieldNameLit = this.literalStore.getOrCreate('parent');
    // For "parent", participants[3] is the entity ID directly (not a literal)
    this._emitRelationship({
      id: relId,
      participants: [SETS_FIELD, bid, fieldNameLit, npid],
    });
  }

  /** Set a typed field value on an intent. */
  setFieldValue(intentId, fieldName, value) {
    const bid = BigInt(intentId);
    if (!this._byId.has(bid)) throw new Error(`No intent with id ${intentId}`);
    const relId       = this._nextEntityId++;
    const fieldNameLit = this.literalStore.getOrCreate(fieldName);
    const valueLit     = this.literalStore.getOrCreate(value);
    this._emitRelationship({
      id: relId,
      participants: [SETS_FIELD, bid, fieldNameLit, valueLit],
    });
  }

  /** Add a secondary parent to an intent (appends to participantIds). */
  addIntentParent(intentId, newParentId) {
    const bid  = BigInt(intentId);
    const npid = BigInt(newParentId);
    if (!this._byId.has(bid))  throw new Error(`No intent with id ${intentId}`);
    if (!this._byId.has(npid)) throw new Error(`No intent with id ${newParentId}`);
    const intent = this._byId.get(bid);
    if (intent._participantIds.includes(npid))
      throw new Error(`Intent ${newParentId} is already a parent of ${intentId}`);
    this._addParticipant(intentId, newParentId);
  }

  /** Remove a secondary (non-primary) parent from an intent. */
  removeIntentParent(intentId, parentId) {
    const bid  = BigInt(intentId);
    const ppid = BigInt(parentId);
    const intent = this._byId.get(bid);
    if (!intent)  throw new Error(`No intent with id ${intentId}`);
    if (!this._byId.has(ppid)) throw new Error(`No intent with id ${parentId}`);
    if (!intent._participantIds.some(x => x === ppid))
      throw new Error(`Intent ${parentId} is not a parent of ${intentId}`);
    if (intent._participantIds.length <= 1)
      throw new Error(`Cannot remove the only parent of intent ${intentId}`);
    this._removeParticipant(intentId, parentId);
  }

  /**
   * Define a new intent type (mirrors VoluntasIntentService.kt#defineType).
   * @param {string} name  Full path name, e.g. '/browser/current-user'
   * @param {object} opts  { moduleEntityId?, parentTypeId?, autoNameInstances? }
   * @returns {BigInt} the new type entity id
   */
  defineType(name, opts = {}) {
    const { parentTypeId = null, autoNameInstances = false } = opts;
    // A module context is required whenever a parentTypeId is set (mirrors Kotlin).
    // Default to META_ROOT so callers don't have to create a module entity first.
    let { moduleEntityId = null } = opts;
    if (parentTypeId !== null && moduleEntityId === null) moduleEntityId = META_ROOT;
    const id = this._nextEntityId++;
    const participants = [DEFINES_TYPE];
    if (moduleEntityId !== null) participants.push(BigInt(moduleEntityId));
    if (parentTypeId  !== null) participants.push(BigInt(parentTypeId));
    this._emitRelationship({ id, participants });
    this.edit(id, name);
    this._findOrCreateNameNode(name, id);
    if (autoNameInstances) {
      const relId = this._nextEntityId++;
      const fnl   = this.literalStore.getOrCreate('auto-name-instances');
      const vl    = this.literalStore.getOrCreate(true);
      this._emitRelationship({ id: relId, participants: [SETS_FIELD, id, fnl, vl] });
    }
    return id;
  }

  /**
   * Add a typed field definition to an entity (type, macro, etc.).
   * @param {BigInt|number} entityId
   * @param {string} fieldName
   * @param {string} fieldType  e.g. 'STRING', 'BOOL', 'INT64', 'INTENT_REF'
   * @param {boolean} required
   * @param {string|null} description
   */
  addField(entityId, fieldName, fieldType, required = false, description = null) {
    const bid = BigInt(entityId);
    if (!this._byId.has(bid)) throw new Error(`No entity with id ${entityId}`);
    const relId       = this._nextEntityId++;
    const fieldNameLit = this.literalStore.getOrCreate(fieldName);
    const fieldTypeLit = this.literalStore.getOrCreate(fieldType);
    const parts = [DEFINES_FIELD, bid, fieldNameLit, fieldTypeLit];
    if (required || description !== null) parts.push(this.literalStore.getOrCreate(required));
    if (description !== null) parts.push(this.literalStore.getOrCreate(description));
    this._emitRelationship({ id: relId, participants: parts });
  }

  /**
   * Create an instance of a custom type.
   * @param {BigInt|number} typeId
   * @param {string} text  Initial text value
   * @param {BigInt|number|null} parentId
   * @returns {IntentImpl|null}
   */
  addIntentOfType(typeId, text, parentId = null) {
    const id       = this._nextEntityId++;
    const textLitId = this.literalStore.getOrCreate(text);
    const parts    = [INSTANTIATES, BigInt(typeId), textLitId];
    if (parentId !== null) parts.push(BigInt(parentId));
    this._emitRelationship({ id, participants: parts });
    return this._byId.get(id) ?? null;
  }

  /**
   * Return all instances of the given type.
   * @param {BigInt|number} typeId
   * @returns {IntentImpl[]}
   */
  getInstancesOfType(typeId) {
    const instances = this._instancesByType.get(BigInt(typeId)) ?? [];
    return instances.map(id => this._byId.get(id)).filter(Boolean);
  }

  // ----------------------------------------------------------
  // Query API
  // ----------------------------------------------------------

  getById(id) { return this._byId.get(BigInt(id)) ?? null; }

  /** Returns ids of direct children. */
  childrenOf(id) {
    return [...(this._childrenById.get(BigInt(id)) ?? [])];
  }

  getFocalScope(id) {
    const bid    = BigInt(id);
    const intent = this._byId.get(bid);
    if (!intent) throw new Error(`No intent with id ${id}`);

    const childIds = [...(this._childrenById.get(bid) ?? [])];
    const children = childIds.map(cid => this._byId.get(cid)).filter(Boolean);

    return {
      focus: intent,
      ancestry: this._getAncestry(bid),
      ancestryPaths: this._getAncestryPaths(bid),
      children,
    };
  }

  /** All non-meta intents. */
  getAll() {
    return [...this._byId.values()].filter(i => !i.isMeta());
  }

  searchIntents(query, limit = 20) {
    if (!query) return [];
    const lower = query.toLowerCase();
    return [...this._byId.values()]
      .filter(i => !i.isMeta() && i.text().toLowerCase().includes(lower))
      .sort((a, b) => a.text().length - b.text().length || Number(a.id() - b.id()))
      .slice(0, limit);
  }

  /** Returns the entity ID registered at a name path (e.g. '/standard/note'). */
  getEntityByPath(path) {
    const normalised = path.startsWith('/') ? path : '/' + path;
    const nodeId = this._namesByPath.get(normalised);
    if (nodeId === undefined) return null;
    return this._referentByNameNode.get(nodeId) ?? null;
  }

  getNamePath(entityId) {
    const nodeId = this._nameNodeByReferent.get(BigInt(entityId));
    if (nodeId === undefined) return null;
    return this._nameNodeToPath.get(nodeId) ?? null;
  }

  // ----------------------------------------------------------
  // Bootstrap
  // ----------------------------------------------------------

  _emitBootstrap(rootIntentText) {
    // 1. Entity 7 = STRING_INTENT_TYPE
    this._emitRelationship({ id: STRING_INTENT_TYPE, participants: [DEFINES_TYPE] });

    // 2. Entity 8 = "text" field of STRING_INTENT_TYPE
    const textLit      = this.literalStore.getOrCreate('text');
    const stringTypeLit = this.literalStore.getOrCreate('STRING');
    this._emitRelationship({
      id: 8n,
      participants: [DEFINES_FIELD, STRING_INTENT_TYPE, textLit, stringTypeLit],
    });

    // 3. Entity 0 = ROOT_INTENT
    const rootTextLit = this.literalStore.getOrCreate(rootIntentText);
    this._emitRelationship({
      id: ROOT_INTENT,
      participants: [INSTANTIATES, STRING_INTENT_TYPE, rootTextLit],
    });

    // 4. Entity 11 = NAME_TYPE
    this._emitRelationship({ id: NAME_TYPE, participants: [DEFINES_TYPE] });

    // 5. Entity 12 = META_ROOT
    const metaTextLit = this.literalStore.getOrCreate('meta');
    this._emitRelationship({
      id: META_ROOT,
      participants: [INSTANTIATES, STRING_INTENT_TYPE, metaTextLit, ROOT_INTENT],
    });

    // 6. Entity 13 = NAMES_ROOT (NAME_TYPE instance, child of META_ROOT)
    const namesTextLit = this.literalStore.getOrCreate('names');
    this._emitRelationship({
      id: NAMES_ROOT,
      participants: [INSTANTIATES, NAME_TYPE, namesTextLit, META_ROOT],
    });

    // 7. Entity 14 = /meta name node
    this._emitRelationship({
      id: META_NAME_NODE,
      participants: [INSTANTIATES, NAME_TYPE, metaTextLit, NAMES_ROOT, META_ROOT],
    });

    // 8. Mark META_ROOT as meta
    this._emitRelationship({
      id: MARKS_META_OP,
      participants: [MARKS_META, META_ROOT],
    });
  }

  // ----------------------------------------------------------
  // Emit & interpret
  // ----------------------------------------------------------

  /** Emit a relationship: track in _ops (when not replaying) and interpret it. */
  _emitRelationship(rel) {
    const { id, participants, timestamp = null } = rel;
    const bId    = BigInt(id);
    const bParts = participants.map(BigInt);
    if (!this._isReplaying) {
      for (const pid of bParts) {
        if (isLiteral(pid)) {
          const lit = this.literalStore.getById(pid);
          if (lit) this._ops.push(this._litToOp(lit));
        }
      }
      this._ops.push({ type: 'relationship', id: String(bId), participants: bParts.map(String) });
    }
    this._interpretRelationship(bId, bParts, timestamp);
  }

  _litToOp(lit) {
    const value = lit.type === 'int64' ? String(lit.value) : lit.value;
    return { type: 'literal', id: String(lit.id), litType: lit.type, value };
  }

  _interpretRelationship(id, participants, timestamp) {
    if (!participants.length) return;
    const type = participants[0];

    if      (type === DEFINES_TYPE)       this._handleDefinesType(id, participants);
    else if (type === DEFINES_FIELD)      this._handleDefinesField(id, participants, timestamp);
    else if (type === INSTANTIATES)       this._handleInstantiates(id, participants, timestamp);
    else if (type === SETS_FIELD)         this._handleSetsField(id, participants, timestamp);
    else if (type === ADDS_PARTICIPANT)   this._handleAddsParticipant(id, participants, timestamp);
    else if (type === REMOVES_PARTICIPANT) this._handleRemovesParticipant(id, participants, timestamp);
    else if (type === DEFINES_MACRO)      this._handleDefinesMacro(id, participants);
    else if (type === DEFINES_MACRO_OP)   this._handleDefinesMacroOp(id, participants);
    else if (type === INVOKES_MACRO)      this._handleInvokesMacro(id, participants, timestamp);
    else if (type === MARKS_META)         this._handleMarksMeta(id, participants);
  }

  // ----------------------------------------------------------
  // Relationship handlers
  // ----------------------------------------------------------

  _handleDefinesType(id, participants) {
    const moduleEntityId = participants.length >= 2 ? participants[1] : null;
    const parentTypeId   = participants.length >= 3 ? participants[2] : null;

    if (parentTypeId !== null && this._visibleTypes.has(parentTypeId)) {
      this._visibleTypes.add(id);
    }

    if (!this._byId.has(id)) {
      this._byId.set(id, new IntentImpl({
        id,
        text: `Type:${id}`,
        participantIds: moduleEntityId !== null ? [moduleEntityId] : [],
        isMeta: true,
      }));
    }

    if (moduleEntityId !== null) this._linkChild(moduleEntityId, id);
    this._trackEntityId(id);
  }

  _handleDefinesField(id, participants, timestamp) {
    if (!this._byId.has(id)) {
      const nameLit   = participants.length >= 3 ? this.literalStore.getString(participants[2]) : null;
      const targetId  = participants.length >= 2 ? participants[1] : null;
      this._byId.set(id, new IntentImpl({
        id,
        text: nameLit ? `DefinesField:${nameLit}` : `DefinesField:${id}`,
        participantIds: targetId !== null ? [targetId] : [],
        createdTimestamp: timestamp,
        isMeta: true,
      }));
    }

    if (participants.length >= 4) {
      const targetId   = participants[1];
      const fieldName  = this.literalStore.getString(participants[2]);
      if (!fieldName) { this._trackEntityId(id); return; }
      const fieldTypStr = this.literalStore.getString(participants[3]);
      const fieldType   = fieldTypStr ?? 'UNSPECIFIED';
      const required    = participants.length >= 5
        ? (() => { const l = this.literalStore.getById(participants[4]); return l && l.type === 'bool' ? l.value : false; })()
        : false;
      const description = participants.length >= 6
        ? this.literalStore.getString(participants[5])
        : null;

      const target = this._byId.get(targetId);
      if (target) {
        target._fields[fieldName] = { fieldType, required, description };
      }
    }

    this._trackEntityId(id);
  }

  _handleInstantiatesNameNode(id, participants, timestamp) {
    const textLitId = participants.length >= 3 ? participants[2] : null;
    if (textLitId === null) return;
    const text = this.literalStore.getString(textLitId);
    if (text === null) return;

    if (id === NAMES_ROOT) {
      const intentTreeParentId = participants.length >= 4 ? participants[3] : null;
      if (!this._byId.has(id)) {
        this._byId.set(id, new IntentImpl({
          id,
          text: 'NameRoot',
          participantIds: intentTreeParentId !== null ? [intentTreeParentId] : [],
          createdTimestamp: timestamp,
          isMeta: true,
        }));
      }
      if (intentTreeParentId !== null) this._linkChild(intentTreeParentId, id);
    } else {
      const parentNameNodeId = participants.length >= 4 ? participants[3] : null;
      if (parentNameNodeId === null) return;
      const parentPath = this._nameNodeToPath.get(parentNameNodeId);
      if (parentPath === undefined) return;
      const path = `${parentPath}/${text}`;

      this._nameNodeToPath.set(id, path);
      this._namesByPath.set(path, id);

      if (participants.length >= 5) {
        const referentId = participants[4];
        this._nameNodeByReferent.set(referentId, id);
        this._referentByNameNode.set(id, referentId);
      }

      if (!this._byId.has(id)) {
        this._byId.set(id, new IntentImpl({
          id,
          text: `NameNode:${path}`,
          participantIds: [parentNameNodeId],
          createdTimestamp: timestamp,
          isMeta: true,
        }));
      }
      this._linkChild(parentNameNodeId, id);
    }
    this._trackEntityId(id);
  }

  _handleInstantiates(id, participants, timestamp) {
    const typeId = participants.length >= 2 ? participants[1] : null;
    if (typeId === null) return;

    if (typeId === NAME_TYPE) {
      this._handleInstantiatesNameNode(id, participants, timestamp);
      return;
    }

    if (this._visibleTypes.has(typeId)) {
      const textLitId      = participants.length >= 3 ? participants[2] : null;
      const parentEntityId = participants.length >= 4 ? participants[3] : null;
      const text = textLitId !== null ? (this.literalStore.getString(textLitId) ?? '') : '';

      const visibleTypeName = typeId === STRING_INTENT_TYPE
        ? null
        : (this.getNamePath(typeId)?.split('/').pop() ?? null);

      const parentIsMeta = parentEntityId !== null
        ? (this._byId.get(parentEntityId)?.isMeta() ?? false)
        : false;

      this._byId.set(id, new IntentImpl({
        id,
        text,
        participantIds: parentEntityId !== null ? [parentEntityId] : [],
        createdTimestamp: timestamp,
        isMeta: parentIsMeta,
        typeName: visibleTypeName,
      }));

      if (parentEntityId !== null) this._linkChild(parentEntityId, id);
    } else {
      const parentEntityId = participants.length >= 3 ? participants[2] : null;
      const typeName = this.getNamePath(typeId)?.split('/').pop() ?? 'instance';
      this._byId.set(id, new IntentImpl({
        id,
        text: `${typeName}:${id}`,
        participantIds: parentEntityId !== null ? [parentEntityId] : [],
        createdTimestamp: timestamp,
        isMeta: true,
      }));
      if (parentEntityId !== null) this._linkChild(parentEntityId, id);
    }

    const instances = this._instancesByType.get(typeId) ?? [];
    instances.push(id);
    this._instancesByType.set(typeId, instances);
    this._trackEntityId(id);

    if (!this._isReplaying && this._typeAutoNames.has(typeId)) {
      const typePath = this.getNamePath(typeId);
      if (typePath !== null) {
        this._findOrCreateNameNode(`${typePath}/${id}`, id);
      }
    }
  }

  _handleMarksMeta(id, participants) {
    const targetId = participants.length >= 2 ? participants[1] : null;
    if (targetId === null) return;
    const existing = this._byId.get(targetId);
    if (existing) existing._isMeta = true;
    this._trackEntityId(id);
  }

  _handleSetsField(id, participants, timestamp) {
    if (participants.length < 4) return;

    const targetId    = participants[1];
    const fieldNameLitId = participants[2];
    const valueLitId  = participants[3];
    const fieldName   = this.literalStore.getString(fieldNameLitId);
    if (!fieldName) { this._trackEntityId(id); return; }

    const existing = this._byId.get(targetId);
    if (!existing) { this._trackEntityId(id); return; }

    switch (fieldName) {
      case 'text': {
        const newText = this.literalStore.getString(valueLitId);
        if (newText === null) break;
        existing._text = newText;
        existing._lastUpdatedTimestamp = timestamp;
        break;
      }
      case 'parent': {
        // participants[3] is a raw entity ID (not a literal)
        const newParentId = valueLitId;
        const oldParentId = existing._participantIds[0];

        if (oldParentId !== undefined) this._unlinkChild(oldParentId, targetId);
        this._linkChild(newParentId, targetId);

        if (existing._participantIds.length > 0) {
          existing._participantIds[0] = newParentId;
        } else {
          existing._participantIds.push(newParentId);
        }

        existing._lastUpdatedTimestamp = timestamp;
        const newParentIsMeta = this._byId.get(newParentId)?.isMeta() ?? false;
        existing._isMeta = newParentIsMeta;
        break;
      }
      default: {
        const lit = this.literalStore.getById(valueLitId);
        if (!lit) break;
        const value = lit.type === 'int64' ? lit.value : lit.value;

        if (fieldName === 'auto-name-instances' && value === true) {
          this._typeAutoNames.add(targetId);
        } else {
          existing._values[fieldName] = value;
          if (fieldName === 'command-name' && typeof value === 'string' && value) {
            const displayType = existing._text.split(':')[0] || 'instance';
            existing._text = `${displayType} ${value}`;
            existing._lastUpdatedTimestamp = timestamp;
          }
        }
        break;
      }
    }

    // Meta-intent for the SETS_FIELD relationship itself
    if (!this._byId.has(id)) {
      this._byId.set(id, new IntentImpl({
        id,
        text: `SetsField:${fieldName} on ${targetId}`,
        participantIds: [targetId],
        createdTimestamp: timestamp,
        isMeta: true,
      }));
    }
    this._trackEntityId(id);
  }

  _handleAddsParticipant(id, participants, timestamp) {
    if (participants.length < 3) return;

    const targetId       = participants[1];
    const participantToAdd = participants[2];
    const existing = this._byId.get(targetId);
    if (!existing) { this._trackEntityId(id); return; }

    const index = participants.length >= 4
      ? (() => { const l = this.literalStore.getById(participants[3]); return l && l.type === 'int64' ? Number(l.value) : null; })()
      : null;

    if (index !== null && index >= 0 && index <= existing._participantIds.length) {
      existing._participantIds.splice(index, 0, participantToAdd);
    } else {
      existing._participantIds.push(participantToAdd);
    }

    this._linkChild(participantToAdd, targetId);

    if (!this._byId.has(id)) {
      this._byId.set(id, new IntentImpl({
        id,
        text: `AddsParticipant:${participantToAdd} to ${targetId}`,
        participantIds: [targetId],
        createdTimestamp: timestamp,
        isMeta: true,
      }));
    }
    this._trackEntityId(id);
  }

  _handleRemovesParticipant(id, participants, timestamp) {
    if (participants.length < 3) return;

    const targetId         = participants[1];
    const participantToRemove = participants[2];
    const existing = this._byId.get(targetId);
    if (!existing) { this._trackEntityId(id); return; }

    const idx = existing._participantIds.findIndex(x => x === participantToRemove);
    if (idx !== -1) existing._participantIds.splice(idx, 1);
    this._unlinkChild(participantToRemove, targetId);

    if (!this._byId.has(id)) {
      this._byId.set(id, new IntentImpl({
        id,
        text: `RemovesParticipant:${participantToRemove} from ${targetId}`,
        participantIds: [targetId],
        createdTimestamp: timestamp,
        isMeta: true,
      }));
    }
    this._trackEntityId(id);
  }

  _handleDefinesMacro(id, participants) {
    const name = participants.length >= 2
      ? (this.literalStore.getString(participants[1]) ?? `macro:${id}`)
      : `macro:${id}`;
    const paramNames = participants.slice(2).map(p => this.literalStore.getString(p)).filter(Boolean);
    this._macros.set(id, { name, paramNames, bodyOps: [] });

    if (!this._byId.has(id)) {
      this._byId.set(id, new IntentImpl({ id, text: `Macro:${name}`, isMeta: true }));
    }
    this._trackEntityId(id);
  }

  _handleDefinesMacroOp(id, participants) {
    if (participants.length < 3) return;
    const macroId          = participants[1];
    const opTypeEntityId   = participants[2];
    const templateParticipants = participants.slice(3);

    const macro = this._macros.get(macroId);
    if (macro) macro.bodyOps.push({ opTypeEntityId, participants: templateParticipants });

    if (!this._byId.has(id)) {
      this._byId.set(id, new IntentImpl({
        id,
        text: `MacroOp:${id} of macro:${macroId}`,
        participantIds: [macroId],
        isMeta: true,
      }));
    }
    this._trackEntityId(id);
  }

  _handleInvokesMacro(id, participants, timestamp) {
    if (participants.length < 2) return;
    const macroId = participants[1];
    const args    = participants.slice(2);
    const macro   = this._macros.get(macroId);

    if (!this._byId.has(id)) {
      this._byId.set(id, new IntentImpl({
        id,
        text: macro ? `Invoke:${macro.name}` : `Invoke:macro:${macroId}`,
        isMeta: true,
      }));
    }
    this._trackEntityId(id);

    // During replay the expanded ops are already in the stream — don't re-expand.
    if (this._isReplaying || !macro) return;

    for (const template of macro.bodyOps) {
      const concrete = template.participants.map(pid => {
        if (isLiteral(pid)) {
          const strVal = this.literalStore.getString(pid);
          if (strVal !== null && strVal.startsWith('$')) {
            const ref = strVal.slice(1);
            const index = Number.isInteger(+ref) ? +ref : -1;
            if (index >= 0 && index < args.length) return args[index];
            const nameIdx = macro.paramNames.indexOf(ref);
            if (nameIdx >= 0) return args[nameIdx];
          }
        }
        return pid;
      });

      const newId = this._nextEntityId++;
      this._emitRelationship({
        id: newId,
        participants: [template.opTypeEntityId, ...concrete],
        timestamp,
      });
    }
  }

  // ----------------------------------------------------------
  // Name system
  // ----------------------------------------------------------

  _findOrCreateNameNode(path, referentId) {
    const normalised = path.startsWith('/') ? path : '/' + path;
    const segments   = normalised.replace(/^\//, '').split('/');

    let currentParentId = NAMES_ROOT;
    let currentPath     = '';

    for (let i = 0; i < segments.length; i++) {
      const segment = segments[i];
      const isLast  = i === segments.length - 1;
      currentPath   = `${currentPath}/${segment}`;

      const existingId = this._namesByPath.get(currentPath);
      if (existingId !== undefined) {
        if (isLast && !this._referentByNameNode.has(existingId)) {
          this._nameNodeByReferent.set(BigInt(referentId), existingId);
          this._referentByNameNode.set(existingId, BigInt(referentId));
        }
        currentParentId = existingId;
      } else {
        const newId   = this._nextEntityId++;
        const textLit = this.literalStore.getOrCreate(segment);
        const parts   = [INSTANTIATES, NAME_TYPE, textLit, currentParentId];
        if (isLast) parts.push(BigInt(referentId));
        this._emitRelationship({ id: newId, participants: parts });
        currentParentId = newId;
      }
    }
    return currentParentId;
  }

  // ----------------------------------------------------------
  // Internal participant helpers
  // ----------------------------------------------------------

  _addParticipant(intentId, participantId, index = null) {
    if (!this._byId.has(BigInt(intentId))) throw new Error(`No intent with id ${intentId}`);
    const relId = this._nextEntityId++;
    const parts = [ADDS_PARTICIPANT, BigInt(intentId), BigInt(participantId)];
    if (index !== null) parts.push(this.literalStore.getOrCreate(BigInt(index)));
    this._emitRelationship({ id: relId, participants: parts });
  }

  _removeParticipant(intentId, participantId) {
    if (!this._byId.has(BigInt(intentId))) throw new Error(`No intent with id ${intentId}`);
    const relId = this._nextEntityId++;
    this._emitRelationship({
      id: relId,
      participants: [REMOVES_PARTICIPANT, BigInt(intentId), BigInt(participantId)],
    });
  }

  // ----------------------------------------------------------
  // Children index helpers
  // ----------------------------------------------------------

  _linkChild(parentId, childId) {
    let set = this._childrenById.get(parentId);
    if (!set) { set = new Set(); this._childrenById.set(parentId, set); }
    set.add(childId);
  }

  _unlinkChild(parentId, childId) {
    this._childrenById.get(parentId)?.delete(childId);
  }

  _trackEntityId(id) {
    const bid = BigInt(id);
    if (bid >= this._nextEntityId) this._nextEntityId = bid + 1n;
  }

  // ----------------------------------------------------------
  // Ancestry
  // ----------------------------------------------------------

  _getAncestry(bid) {
    const ancestry = [];
    let current = this._byId.get(bid);
    while (current) {
      const parentId = current._participantIds[0];
      if (parentId === undefined) break;
      const parent = this._byId.get(parentId);
      if (!parent) break;
      ancestry.unshift(parent);
      current = parent;
    }
    return ancestry;
  }

  _getAncestryPaths(bid) {
    const intent = this._byId.get(bid);
    if (!intent) return [];
    const parents = intent._participantIds;
    if (!parents.length) return [[]];
    return parents.map(pid => {
      const parentAncestry = this._getAncestry(pid);
      const parentIntent = this._byId.get(pid);
      return parentIntent ? [...parentAncestry, parentIntent] : parentAncestry;
    });
  }

  // ----------------------------------------------------------
  // Replay
  // ----------------------------------------------------------

  /**
   * Replay ops in the format emitted by the stream endpoint or produced by
   * a tool that serialises the Voluntas protobuf stream to JSON.
   *
   * Each op is one of:
   *   { type: 'literal', id: string|BigInt, litType: 'string'|'int64'|'bool'|'double', value: any }
   *   { type: 'relationship', id: string|BigInt, participants: (string|BigInt)[], timestamp: string|BigInt|null }
   */
  _replayOps(ops) {
    this._isReplaying = true;
    try {
      for (const op of ops) {
        this._ops.push(op);
        if (op.type === 'literal') {
          this.literalStore.register({
            id: BigInt(op.id),
            type: op.litType,
            value: op.litType === 'int64' ? BigInt(op.value)
                 : op.litType === 'bool'   ? Boolean(op.value)
                 : op.value,
          });
        } else if (op.type === 'relationship') {
          const ts = op.timestamp != null ? BigInt(op.timestamp) : null;
          this._interpretRelationship(
            BigInt(op.id),
            (op.participants ?? []).map(p => BigInt(p)),
            ts,
          );
        }
      }
    } finally {
      this._isReplaying = false;
    }
  }
}
