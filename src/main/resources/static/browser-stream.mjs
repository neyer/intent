/**
 * BrowserStream — a VoluntasIntentService instance that lives entirely in the
 * browser and persists its full op log to localStorage.
 *
 * Auth credentials are stored as a "/browser/current-user" intent in the
 * meta section of this local stream rather than as raw localStorage strings.
 */

import { VoluntasIntentService, META_ROOT, STRING_INTENT_TYPE } from './voluntas.mjs';

const DEFAULT_KEY = 'voluntas-browser-stream';
const USER_TYPE_PATH = '/browser/current-user';

export class BrowserStream {
  #svc;
  #userTypeId    = null;
  #userInstanceId = null;
  #storageKey;

  /** @private — use BrowserStream.load() */
  constructor(svc, storageKey = DEFAULT_KEY) {
    this.#svc = svc;
    this.#storageKey = storageKey;
    this.#initialize();
  }

  /**
   * Load from localStorage (or start fresh if nothing is saved / data is corrupt).
   * @param {string} [key]  localStorage key to use
   */
  static load(key = DEFAULT_KEY) {
    try {
      const raw = localStorage.getItem(key);
      if (raw) {
        const saved = JSON.parse(raw);
        const svc = VoluntasIntentService.fromOps(saved.ops ?? [], 'browser-stream');
        return new BrowserStream(svc, key);
      }
    } catch (e) {
      console.warn('[BrowserStream] Failed to load from localStorage:', e);
    }
    const svc = VoluntasIntentService.new('Browser');
    return new BrowserStream(svc, key);
  }

  // ----------------------------------------------------------
  // Auth helpers
  // ----------------------------------------------------------

  /**
   * Store the current user's credentials in the browser stream.
   * Creates the intent on first call; updates it on subsequent calls.
   */
  setCurrentUser(username, authToken) {
    if (this.#userInstanceId !== null) {
      this.#svc.edit(this.#userInstanceId, username);
      this.#svc.setFieldValue(this.#userInstanceId, 'auth-token', authToken);
    } else {
      const instance = this.#svc.addIntentOfType(this.#userTypeId, username, META_ROOT);
      this.#userInstanceId = instance?.id() ?? null;
      if (this.#userInstanceId !== null) {
        this.#svc.setFieldValue(this.#userInstanceId, 'auth-token', authToken);
      }
    }
    this.save();
  }

  /**
   * Returns { username, authToken } if a user is stored, otherwise null.
   */
  getCurrentUser() {
    if (this.#userInstanceId === null) return null;
    const intent = this.#svc.getById(this.#userInstanceId);
    if (!intent) return null;
    const username  = intent.text();
    const authToken = intent.fieldValues()['auth-token'];
    if (!username || !authToken) return null;
    return { username, authToken };
  }

  /**
   * Clear the stored credentials (keeps the intent but blanks auth-token).
   */
  clearCurrentUser() {
    if (this.#userInstanceId !== null) {
      try { this.#svc.edit(this.#userInstanceId, ''); } catch {}
      this.#svc.setFieldValue(this.#userInstanceId, 'auth-token', '');
    }
    this.save();
  }

  // ----------------------------------------------------------
  // Persistence
  // ----------------------------------------------------------

  /** Serialize the full op log to localStorage. */
  save() {
    try {
      localStorage.setItem(this.#storageKey, JSON.stringify({ v: 1, ops: this.#svc.toOps() }));
    } catch (e) {
      console.warn('[BrowserStream] Failed to save to localStorage:', e);
    }
  }

  /**
   * Wipe localStorage and reset to a fresh stream.
   * Useful for logout or debugging.
   */
  clear() {
    try { localStorage.removeItem(this.#storageKey); } catch {}
    this.#svc = VoluntasIntentService.new('Browser');
    this.#userTypeId    = null;
    this.#userInstanceId = null;
    this.#initialize();
  }

  /** The underlying VoluntasIntentService (for advanced use). */
  get service() { return this.#svc; }

  // ----------------------------------------------------------
  // Internal setup
  // ----------------------------------------------------------

  #initialize() {
    // Check if the type was already defined (e.g. loaded from localStorage)
    const existingTypeId = this.#svc.getEntityByPath(USER_TYPE_PATH);
    if (existingTypeId !== null) {
      this.#userTypeId = existingTypeId;
      const instances = this.#svc.getInstancesOfType(existingTypeId);
      if (instances.length > 0) this.#userInstanceId = instances[0].id();
    } else {
      // Define /browser/current-user as a visible subtype of STRING_INTENT_TYPE
      // (placed under META_ROOT later, so instances will be isMeta=true)
      this.#userTypeId = this.#svc.defineType(USER_TYPE_PATH, {
        parentTypeId: STRING_INTENT_TYPE,
      });
      this.#svc.addField(this.#userTypeId, 'auth-token', 'STRING');
      this.save();
    }
  }
}
