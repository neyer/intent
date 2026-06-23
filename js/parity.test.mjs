/**
 * Parity tests: run the same operations through both the JavaScript
 * VoluntasIntentService and the running Kotlin gRPC server, then assert
 * that both produce identical tree structure and field values.
 *
 * Requirements:
 *   - A Kotlin gRPC server at VOLUNTAS_GRPC_ADDR (default localhost:50051)
 *     with no auth, OR with credentials via VOLUNTAS_USER / VOLUNTAS_TOKEN.
 *   - grpcurl on PATH.
 *   - The server may have pre-existing state; each test isolates itself by
 *     creating a unique container intent and comparing only within that subtree.
 *
 * Run:  node --test js/parity.test.mjs
 */

import { test, describe, before } from 'node:test';
import assert from 'node:assert/strict';
import { execFileSync } from 'node:child_process';
import { VoluntasIntentService } from './voluntas.mjs';

// ── Configuration ─────────────────────────────────────────────

const GRPC_ADDR  = process.env.VOLUNTAS_GRPC_ADDR ?? 'localhost:50051';
const USERNAME   = process.env.VOLUNTAS_USER  ?? null;
const AUTH_TOKEN = process.env.VOLUNTAS_TOKEN ?? null;

// ── gRPC helpers ──────────────────────────────────────────────

function grpcCall(method, data) {
  const args = ['-plaintext', '-d', JSON.stringify(data), GRPC_ADDR,
                `voluntas.v1.IntentService/${method}`];
  const raw = execFileSync('grpcurl', args, { encoding: 'utf8' });
  return JSON.parse(raw);
}

function submitOp(payload) {
  const req = { ...payload };
  if (USERNAME)   req.username   = USERNAME;
  if (AUTH_TOKEN) req.auth_token = AUTH_TOKEN;
  return grpcCall('SubmitOp', req);
}

function ktGetScope(id) {
  return grpcCall('GetFocalScope', { id: String(id) });
}

// Unwrap a FieldValueProto oneof into a plain JS value.
// gRPC JSON serialises it as e.g. { "string_value": "foo" } or { "bool_value": true }.
function unwrapFieldValue(fv) {
  if (!fv || typeof fv !== 'object') return fv;
  if ('string_value'    in fv) return fv.string_value;
  if ('bool_value'      in fv) return fv.bool_value;
  if ('int64_value'     in fv) return Number(fv.int64_value);   // may lose precision for huge ints
  if ('int32_value'     in fv) return fv.int32_value;
  if ('double_value'    in fv) return fv.double_value;
  if ('float_value'     in fv) return fv.float_value;
  if ('timestamp_value' in fv) return Number(fv.timestamp_value);
  if ('intent_ref_value' in fv) return Number(fv.intent_ref_value);
  return fv;
}

// ── Availability check ────────────────────────────────────────

let serverAvailable = false;

before(async () => {
  try {
    // Light probe — just get the root intent.
    const r = grpcCall('GetFocalScope', { id: '0' });
    if (r.found) {
      serverAvailable = true;
      console.log(`Kotlin server reachable at ${GRPC_ADDR}`);
    }
  } catch {
    console.log(`Kotlin server not reachable at ${GRPC_ADDR}; parity tests will be skipped.`);
  }
});

// ── Summarise helpers ─────────────────────────────────────────

function summariseKtScope(scope) {
  const rawFv = scope.focus?.field_values ?? {};
  const fieldValues = Object.fromEntries(
    Object.entries(rawFv).map(([k, v]) => [k, unwrapFieldValue(v)])
  );
  return {
    text:          scope.focus?.text ?? null,
    isMeta:        scope.focus?.is_meta ?? false,
    typeName:      scope.focus?.type_name ?? null,
    fieldValues,
    childrenTexts: (scope.children ?? []).map(c => c.text).sort(),
    ancestryTexts: (scope.ancestry  ?? []).map(a => a.text),
  };
}

function summariseJsScope(scope) {
  const fv = scope.focus.fieldValues();
  const fieldValues = Object.fromEntries(
    Object.entries(fv).map(([k, v]) => [k, typeof v === 'bigint' ? Number(v) : v])
  );
  return {
    text:          scope.focus.text(),
    isMeta:        scope.focus.isMeta(),
    typeName:      scope.focus.typeName() ?? null,
    fieldValues,
    childrenTexts: scope.children.map(c => c.text()).sort(),
    ancestryTexts: scope.ancestry.map(a => a.text()),
  };
}

// ── parityTest harness ────────────────────────────────────────

/**
 * Run `scenario` against both JS and Kotlin, then call `checkFn` to assert.
 *
 * The scenario callback receives an `api` object with:
 *   addIntent(text, parentRef)    → symbolic ref (index into creation order)
 *   edit(ref, newText)
 *   moveParent(ref, newParentRef)
 *   setFieldValue(ref, field, value)
 *   addIntentParent(ref, parentRef)
 *   removeIntentParent(ref, parentRef)
 *
 * parentRef values:
 *   'root'       — the server's root intent (id 0)
 *   number n     — the n-th intent created by this scenario (0-indexed)
 *
 * checkFn receives:
 *   jsSvc        — the JS VoluntasIntentService
 *   jsIds[]      — BigInt ids of created intents in order
 *   ktIds[]      — Number ids of created intents in order
 *   getJsScope(ref)  — get JS getFocalScope for the given ref
 *   getKtScope(ref)  — get Kotlin GetFocalScope for the given ref
 */
function parityTest(name, scenario, checkFn) {
  test(name, { skip: !serverAvailable }, async () => {

    // ── JS side ──────────────────────────────────────────────
    const jsSvc = VoluntasIntentService.new('Root');
    const jsIds = [];

    const jsResolve = (ref) => ref === 'root' ? 0n : jsIds[ref];

    const jsApi = {
      addIntent(text, parentRef) {
        const intent = jsSvc.addIntent(text, jsResolve(parentRef));
        jsIds.push(intent.id());
        return jsIds.length - 1;
      },
      edit(ref, newText)           { jsSvc.edit(jsResolve(ref), newText); },
      moveParent(ref, parentRef)   { jsSvc.moveParent(jsResolve(ref), jsResolve(parentRef)); },
      setFieldValue(ref, f, v)     { jsSvc.setFieldValue(jsResolve(ref), f, v); },
      addIntentParent(ref, pRef)   { jsSvc.addIntentParent(jsResolve(ref), jsResolve(pRef)); },
      removeIntentParent(ref, pRef){ jsSvc.removeIntentParent(jsResolve(ref), jsResolve(pRef)); },
    };

    // ── Kotlin side ───────────────────────────────────────────
    const ktIds = [];

    const ktResolve = (ref) => ref === 'root' ? 0 : ktIds[ref];

    const ktApi = {
      addIntent(text, parentRef) {
        const resp = submitOp({ create_intent: { text, parentId: String(ktResolve(parentRef)) } });
        if (!resp.success) throw new Error(`Kotlin addIntent failed: ${JSON.stringify(resp)}`);
        ktIds.push(Number(resp.id));
        return ktIds.length - 1;
      },
      edit(ref, newText) {
        const resp = submitOp({ update_intent: { id: String(ktResolve(ref)), new_text: newText } });
        if (!resp.success) throw new Error(`Kotlin edit failed: ${JSON.stringify(resp)}`);
      },
      moveParent(ref, parentRef) {
        const resp = submitOp({ update_intent_parent: { id: String(ktResolve(ref)), parentId: String(ktResolve(parentRef)) } });
        if (!resp.success) throw new Error(`Kotlin moveParent failed: ${JSON.stringify(resp)}`);
      },
      setFieldValue(ref, field, value) {
        const id = String(ktResolve(ref));
        let payload;
        if (typeof value === 'string')  payload = { string_value: value };
        else if (typeof value === 'boolean') payload = { bool_value: value };
        else if (typeof value === 'number')  payload = { int64_value: String(value) };
        else throw new Error(`Unsupported value type: ${typeof value}`);
        const resp = submitOp({ set_field_value: { intentId: id, fieldName: field, ...payload } });
        if (!resp.success) throw new Error(`Kotlin setFieldValue failed: ${JSON.stringify(resp)}`);
      },
      addIntentParent(ref, parentRef) {
        const resp = submitOp({ add_intent_parent: { intentId: String(ktResolve(ref)), parentId: String(ktResolve(parentRef)) } });
        if (!resp.success) throw new Error(`Kotlin addIntentParent failed: ${JSON.stringify(resp)}`);
      },
      removeIntentParent(ref, parentRef) {
        const resp = submitOp({ remove_intent_parent: { intentId: String(ktResolve(ref)), parentId: String(ktResolve(parentRef)) } });
        if (!resp.success) throw new Error(`Kotlin removeIntentParent failed: ${JSON.stringify(resp)}`);
      },
    };

    // Run scenario on both sides
    scenario(jsApi);
    scenario(ktApi);

    assert.equal(jsIds.length, ktIds.length, 'both sides created the same number of intents');

    await checkFn({
      jsSvc,
      jsIds,
      ktIds,
      getJsScope: (ref) => jsSvc.getFocalScope(jsResolve(ref)),
      getKtScope: (ref) => ktGetScope(ktResolve(ref)),
    });
  });
}

// ── Scenarios ─────────────────────────────────────────────────
// Each scenario starts by creating a unique container intent under root.
// All comparisons are scoped to that container, so pre-existing server
// state from other tests does not interfere.

describe('parity: JS vs Kotlin', () => {

  parityTest(
    'addIntent text and children',
    (api) => {
      api.addIntent('Container', 'root');  // ref 0
      api.addIntent('Child A', 0);         // ref 1
      api.addIntent('Child B', 0);         // ref 2
    },
    ({ getJsScope, getKtScope }) => {
      const js = summariseJsScope(getJsScope(0));
      const kt = summariseKtScope(getKtScope(0));
      assert.equal(js.text, kt.text,                        'container text matches');
      assert.deepEqual(js.childrenTexts, kt.childrenTexts,  'children texts match');
    },
  );

  parityTest(
    'edit updates text',
    (api) => {
      api.addIntent('Container', 'root');  // ref 0
      api.addIntent('Original', 0);        // ref 1
      api.edit(1, 'Renamed');
    },
    ({ getJsScope, getKtScope }) => {
      const js = summariseJsScope(getJsScope(1));
      const kt = summariseKtScope(getKtScope(1));
      assert.equal(js.text, 'Renamed',    'JS text updated');
      assert.equal(kt.text, 'Renamed',    'Kotlin text updated');
      assert.equal(js.text, kt.text,      'both match');
    },
  );

  parityTest(
    'moveParent relocates intent in tree',
    (api) => {
      api.addIntent('Container', 'root');  // ref 0
      api.addIntent('Child',     0);       // ref 1
      api.addIntent('NewParent', 0);       // ref 2
      api.moveParent(1, 2);               // Child moves under NewParent
    },
    ({ getJsScope, getKtScope }) => {
      // NewParent should now have Child
      const jsNP = summariseJsScope(getJsScope(2));
      const ktNP = summariseKtScope(getKtScope(2));
      assert.deepEqual(jsNP.childrenTexts, ktNP.childrenTexts, 'NewParent children match');

      // Container should only have NewParent, not Child
      const jsCont = summariseJsScope(getJsScope(0));
      const ktCont = summariseKtScope(getKtScope(0));
      assert.deepEqual(jsCont.childrenTexts, ktCont.childrenTexts, 'Container children match');
    },
  );

  parityTest(
    'setFieldValue (string)',
    (api) => {
      api.addIntent('Container', 'root');  // ref 0
      api.addIntent('Task', 0);            // ref 1
      api.setFieldValue(1, 'status', 'in-progress');
    },
    ({ getJsScope, getKtScope }) => {
      const js = summariseJsScope(getJsScope(1));
      const kt = summariseKtScope(getKtScope(1));
      assert.equal(js.fieldValues.status, 'in-progress', 'JS field set');
      assert.equal(kt.fieldValues.status, 'in-progress', 'Kotlin field set');
      assert.equal(js.fieldValues.status, kt.fieldValues.status, 'both match');
    },
  );

  parityTest(
    'setFieldValue (boolean)',
    (api) => {
      api.addIntent('Container', 'root');  // ref 0
      api.addIntent('Task', 0);            // ref 1
      api.setFieldValue(1, 'done', true);
    },
    ({ getJsScope, getKtScope }) => {
      const js = summariseJsScope(getJsScope(1));
      const kt = summariseKtScope(getKtScope(1));
      assert.equal(js.fieldValues.done, true, 'JS bool field set');
      assert.equal(kt.fieldValues.done, true, 'Kotlin bool field set');
      assert.equal(js.fieldValues.done, kt.fieldValues.done, 'both match');
    },
  );

  parityTest(
    'ancestry chain matches for deeply nested intent',
    (api) => {
      api.addIntent('Container', 'root');  // ref 0
      api.addIntent('Level1', 0);          // ref 1
      api.addIntent('Level2', 1);          // ref 2
      api.addIntent('Level3', 2);          // ref 3
    },
    ({ getJsScope, getKtScope }) => {
      const jsL3 = summariseJsScope(getJsScope(3));
      const ktL3 = summariseKtScope(getKtScope(3));
      // Ancestry texts should be: [Root/Container, Level1, Level2] in some order
      // Compare ignoring root (which differs between fresh JS and persistent Kotlin)
      const jsAncestors = jsL3.ancestryTexts.filter(t => t !== 'Root');
      const ktAncestors = ktL3.ancestryTexts.filter(t => t !== 'Voluntas Server Root');
      assert.deepEqual(jsAncestors, ktAncestors, 'ancestry texts match (excluding root name)');
    },
  );

  parityTest(
    'multi-parent DAG: addIntentParent and removeIntentParent',
    (api) => {
      api.addIntent('Container', 'root');  // ref 0
      api.addIntent('Child', 0);           // ref 1
      api.addIntent('Parent2', 0);         // ref 2
      api.addIntentParent(1, 2);           // Child gains Parent2 as secondary parent
    },
    ({ getJsScope, getKtScope }) => {
      const jsP2 = summariseJsScope(getJsScope(2));
      const ktP2 = summariseKtScope(getKtScope(2));
      assert.deepEqual(jsP2.childrenTexts, ktP2.childrenTexts,
        'Parent2 children match after addIntentParent');
    },
  );

  parityTest(
    'combined: create, edit, nest, set fields, move',
    (api) => {
      api.addIntent('Container',   'root');  // ref 0
      api.addIntent('Project',     0);       // ref 1
      api.addIntent('Task A',      1);       // ref 2
      api.addIntent('Task B',      1);       // ref 3
      api.edit(2, 'Task A (revised)');
      api.setFieldValue(2, 'priority', 'high');
      api.setFieldValue(3, 'done', false);
      api.addIntent('Subtask', 2);           // ref 4
      api.moveParent(4, 3);                 // move Subtask under Task B
      api.setFieldValue(4, 'notes', 'moved');
    },
    ({ getJsScope, getKtScope }) => {
      // Task A (ref 2)
      const jsA = summariseJsScope(getJsScope(2));
      const ktA = summariseKtScope(getKtScope(2));
      assert.equal(jsA.text, ktA.text,                              'Task A text matches');
      assert.deepEqual(jsA.childrenTexts, ktA.childrenTexts,        'Task A children match');
      assert.equal(jsA.fieldValues.priority, ktA.fieldValues.priority, 'priority matches');

      // Task B (ref 3) — should have Subtask after moveParent
      const jsB = summariseJsScope(getJsScope(3));
      const ktB = summariseKtScope(getKtScope(3));
      assert.deepEqual(jsB.childrenTexts, ktB.childrenTexts,        'Task B children match');
      assert.equal(jsB.fieldValues.done, ktB.fieldValues.done,      'done flag matches');

      // Subtask (ref 4)
      const jsSub = summariseJsScope(getJsScope(4));
      const ktSub = summariseKtScope(getKtScope(4));
      assert.equal(jsSub.fieldValues.notes, ktSub.fieldValues.notes, 'notes field matches');
    },
  );
});
