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
const FILTER_MASK_TYPE_PATH = '/browser/filter-mask';

export class BrowserStream {
  #svc;
  #userTypeId       = null;
  #userInstanceId   = null;
  #filterMaskTypeId = null;
  #filterMaskId     = null;
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
  // Filter mask
  // ----------------------------------------------------------

  /**
   * Returns the current filter mask as a plain object, e.g. { done: true }.
   * An intent is hidden from view when ALL its field values equal the
   * corresponding values in the mask.
   */
  getFilterMask() {
    if (this.#filterMaskId === null) return {};
    const intent = this.#svc.getById(this.#filterMaskId);
    if (!intent) return {};
    const json = intent.fieldValues()['mask-json'] ?? '{}';
    try { return JSON.parse(json); } catch { return {}; }
  }

  /**
   * Add or update a field in the filter mask.
   * e.g. setFilterField('done', true) → hide intents where done===true
   */
  setFilterField(name, value) {
    if (this.#filterMaskId === null) return;
    const mask = this.getFilterMask();
    mask[name] = value;
    this.#svc.setFieldValue(this.#filterMaskId, 'mask-json', JSON.stringify(mask));
    this.save();
  }

  /**
   * Remove a field from the filter mask, so it no longer contributes to filtering.
   */
  clearFilterField(name) {
    if (this.#filterMaskId === null) return;
    const mask = this.getFilterMask();
    delete mask[name];
    this.#svc.setFieldValue(this.#filterMaskId, 'mask-json', JSON.stringify(mask));
    this.save();
  }

  /**
   * Returns true if a server-side intent object (with plain fieldValues map)
   * matches every field in the filter mask and should therefore be hidden.
   */
  intentIsFiltered(serverIntent) {
    const mask = this.getFilterMask();
    const keys = Object.keys(mask);
    if (keys.length === 0) return false;
    const fv = serverIntent.fieldValues || {};
    return keys.every(k => fv[k] === mask[k]);
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
    this.#userTypeId       = null;
    this.#userInstanceId   = null;
    this.#filterMaskTypeId = null;
    this.#filterMaskId     = null;
    this.#initialize();
  }

  /** The underlying VoluntasIntentService (for advanced use). */
  get service() { return this.#svc; }

  // ----------------------------------------------------------
  // Internal setup
  // ----------------------------------------------------------

  #initialize() {
    let needsSave = false;

    // --- /browser/current-user type ---
    const existingUserTypeId = this.#svc.getEntityByPath(USER_TYPE_PATH);
    if (existingUserTypeId !== null) {
      this.#userTypeId = existingUserTypeId;
      const instances = this.#svc.getInstancesOfType(existingUserTypeId);
      if (instances.length > 0) this.#userInstanceId = instances[0].id();
    } else {
      this.#userTypeId = this.#svc.defineType(USER_TYPE_PATH, {
        parentTypeId: STRING_INTENT_TYPE,
      });
      this.#svc.addField(this.#userTypeId, 'auth-token', 'STRING');
      needsSave = true;
    }

    // --- /browser/filter-mask type + singleton instance ---
    const existingFMTypeId = this.#svc.getEntityByPath(FILTER_MASK_TYPE_PATH);
    if (existingFMTypeId !== null) {
      this.#filterMaskTypeId = existingFMTypeId;
      const instances = this.#svc.getInstancesOfType(existingFMTypeId);
      if (instances.length > 0) this.#filterMaskId = instances[0].id();
    } else {
      this.#filterMaskTypeId = this.#svc.defineType(FILTER_MASK_TYPE_PATH, {
        parentTypeId: STRING_INTENT_TYPE,
      });
      this.#svc.addField(this.#filterMaskTypeId, 'mask-json', 'STRING');
      needsSave = true;
    }

    // Create the singleton filter-mask instance if not yet present
    if (this.#filterMaskTypeId !== null && this.#filterMaskId === null) {
      const inst = this.#svc.addIntentOfType(this.#filterMaskTypeId, 'filter-mask', META_ROOT);
      this.#filterMaskId = inst?.id() ?? null;
      if (this.#filterMaskId !== null) {
        this.#svc.setFieldValue(this.#filterMaskId, 'mask-json', '{}');
      }
      needsSave = true;
    }

    if (needsSave) this.save();
  }
}
