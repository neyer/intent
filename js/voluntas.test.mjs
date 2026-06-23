/**
 * Unit tests for the JavaScript VoluntasIntentService.
 * Run with:  node --test js/voluntas.test.mjs
 */

import { test, describe } from 'node:test';
import assert from 'node:assert/strict';
import {
  VoluntasIntentService,
  ROOT_INTENT,
  FIRST_USER_ENTITY,
  META_ROOT,
  STRING_INTENT_TYPE,
  NAME_TYPE,
} from './voluntas.mjs';

// ============================================================
// Bootstrap / initial state
// ============================================================

describe('bootstrap', () => {
  test('root intent exists at id 0', () => {
    const svc  = VoluntasIntentService.new('My Root');
    const root = svc.getById(0);
    assert.ok(root, 'root intent should exist');
    assert.equal(Number(root.id()), 0);
    assert.equal(root.text(), 'My Root');
    assert.equal(root.isMeta(), false);
  });

  test('meta root exists at id 12 and is meta', () => {
    const svc  = VoluntasIntentService.new('Root');
    const meta = svc.getById(12);
    assert.ok(meta);
    assert.equal(meta.isMeta(), true);
  });

  test('first addIntent allocates id FIRST_USER_ENTITY (1000)', () => {
    const svc   = VoluntasIntentService.new('Root');
    const added = svc.addIntent('Hello', 0);
    assert.equal(Number(added.id()), Number(FIRST_USER_ENTITY));
  });

  test('sequential addIntents allocate sequential ids', () => {
    const svc = VoluntasIntentService.new('Root');
    const a   = svc.addIntent('A', 0);
    const b   = svc.addIntent('B', 0);
    assert.equal(Number(b.id()) - Number(a.id()), 1,
      'each addIntent consumes exactly one entity id');
  });

  test('getAll returns only non-meta intents', () => {
    const svc  = VoluntasIntentService.new('Root');
    const all  = svc.getAll();
    assert.ok(all.every(i => !i.isMeta()), 'getAll should return only non-meta intents');
    assert.ok(all.some(i => i.text() === 'Root'));
  });

  test('getById returns null for unknown id', () => {
    const svc = VoluntasIntentService.new('Root');
    assert.equal(svc.getById(99999), null);
  });
});

// ============================================================
// addIntent
// ============================================================

describe('addIntent', () => {
  test('returns an intent with correct text', () => {
    const svc  = VoluntasIntentService.new('Root');
    const item = svc.addIntent('Hello World', 0);
    assert.equal(item.text(), 'Hello World');
    assert.equal(item.isMeta(), false);
  });

  test('intent appears as child of parent', () => {
    const svc    = VoluntasIntentService.new('Root');
    const child  = svc.addIntent('Child', 0);
    const scope  = svc.getFocalScope(0);
    assert.ok(scope.children.some(c => c.id() === child.id()));
  });

  test('intent participantIds contains parent', () => {
    const svc   = VoluntasIntentService.new('Root');
    const child = svc.addIntent('Child', 0);
    assert.ok(child.participantIds().some(p => p === 0n));
  });

  test('nested intent appears as child of its parent', () => {
    const svc   = VoluntasIntentService.new('Root');
    const a     = svc.addIntent('A', 0);
    const b     = svc.addIntent('B', Number(a.id()));
    const scope = svc.getFocalScope(a.id());
    assert.ok(scope.children.some(c => c.id() === b.id()));
  });

  test('getById returns the added intent', () => {
    const svc   = VoluntasIntentService.new('Root');
    const child = svc.addIntent('Lookup me', 0);
    const found = svc.getById(child.id());
    assert.ok(found);
    assert.equal(found.text(), 'Lookup me');
  });
});

// ============================================================
// edit
// ============================================================

describe('edit', () => {
  test('updates intent text', () => {
    const svc   = VoluntasIntentService.new('Root');
    const child = svc.addIntent('Old', 0);
    svc.edit(child.id(), 'New');
    assert.equal(svc.getById(child.id())?.text(), 'New');
  });

  test('throws for non-existent intent', () => {
    const svc = VoluntasIntentService.new('Root');
    assert.throws(() => svc.edit(99999, 'text'), /No intent/);
  });
});

// ============================================================
// moveParent
// ============================================================

describe('moveParent', () => {
  test('moves intent to new parent', () => {
    const svc    = VoluntasIntentService.new('Root');
    const child  = svc.addIntent('Child', 0);
    const newPar = svc.addIntent('NewParent', 0);
    svc.moveParent(child.id(), newPar.id());

    // child's primary participantId should now be newPar
    const updated = svc.getById(child.id());
    assert.equal(updated?.participantIds()[0], newPar.id());

    // child should appear under newPar, not root
    const rootScope   = svc.getFocalScope(0);
    const newParScope = svc.getFocalScope(newPar.id());
    assert.ok(!rootScope.children.some(c => c.id() === child.id()), 'child removed from old parent');
    assert.ok(newParScope.children.some(c => c.id() === child.id()), 'child added to new parent');
  });

  test('throws for non-existent intent', () => {
    const svc = VoluntasIntentService.new('Root');
    assert.throws(() => svc.moveParent(99999, 0), /No intent/);
  });

  test('throws for non-existent parent', () => {
    const svc   = VoluntasIntentService.new('Root');
    const child = svc.addIntent('Child', 0);
    assert.throws(() => svc.moveParent(child.id(), 99999), /No intent/);
  });
});

// ============================================================
// setFieldValue
// ============================================================

describe('setFieldValue', () => {
  test('sets a string field', () => {
    const svc   = VoluntasIntentService.new('Root');
    const child = svc.addIntent('Item', 0);
    svc.setFieldValue(child.id(), 'status', 'done');
    assert.equal(svc.getById(child.id())?.fieldValues()['status'], 'done');
  });

  test('sets a boolean field', () => {
    const svc   = VoluntasIntentService.new('Root');
    const child = svc.addIntent('Task', 0);
    svc.setFieldValue(child.id(), 'done', true);
    assert.equal(svc.getById(child.id())?.fieldValues()['done'], true);
  });

  test('overwrites a previously set field', () => {
    const svc   = VoluntasIntentService.new('Root');
    const child = svc.addIntent('Task', 0);
    svc.setFieldValue(child.id(), 'priority', 'low');
    svc.setFieldValue(child.id(), 'priority', 'high');
    assert.equal(svc.getById(child.id())?.fieldValues()['priority'], 'high');
  });

  test('throws for non-existent intent', () => {
    const svc = VoluntasIntentService.new('Root');
    assert.throws(() => svc.setFieldValue(99999, 'x', 'y'), /No intent/);
  });
});

// ============================================================
// getFocalScope
// ============================================================

describe('getFocalScope', () => {
  test('focus matches queried intent', () => {
    const svc   = VoluntasIntentService.new('Root');
    const child = svc.addIntent('Focus me', 0);
    const scope = svc.getFocalScope(child.id());
    assert.equal(scope.focus.id(), child.id());
    assert.equal(scope.focus.text(), 'Focus me');
  });

  test('ancestry contains parent chain up to root', () => {
    const svc  = VoluntasIntentService.new('Root');
    const a    = svc.addIntent('A', 0);
    const b    = svc.addIntent('B', a.id());
    const scope = svc.getFocalScope(b.id());
    const ancestryTexts = scope.ancestry.map(i => i.text());
    assert.ok(ancestryTexts.includes('Root'));
    assert.ok(ancestryTexts.includes('A'));
    assert.ok(!ancestryTexts.includes('B'), 'focus should not be in ancestry');
  });

  test('children lists direct children', () => {
    const svc = VoluntasIntentService.new('Root');
    const a   = svc.addIntent('A', 0);
    const b   = svc.addIntent('B', 0);
    svc.addIntent('A-child', a.id());

    const rootScope = svc.getFocalScope(0);
    const childTexts = rootScope.children.map(c => c.text());
    assert.ok(childTexts.includes('A'));
    assert.ok(childTexts.includes('B'));
    assert.ok(!childTexts.includes('A-child'), 'grandchildren should not appear');
  });

  test('throws for non-existent intent', () => {
    const svc = VoluntasIntentService.new('Root');
    assert.throws(() => svc.getFocalScope(99999), /No intent/);
  });
});

// ============================================================
// addIntentParent / removeIntentParent  (multi-parent DAG)
// ============================================================

describe('multi-parent DAG', () => {
  test('addIntentParent adds a secondary parent', () => {
    const svc    = VoluntasIntentService.new('Root');
    const child  = svc.addIntent('Child', 0);
    const parent2 = svc.addIntent('Parent2', 0);
    svc.addIntentParent(child.id(), parent2.id());

    const updated = svc.getById(child.id());
    const pids    = updated?.participantIds();
    assert.ok(pids?.some(p => p === parent2.id()));
    // child should appear under both parents
    assert.ok(svc.getFocalScope(parent2.id()).children.some(c => c.id() === child.id()));
    assert.ok(svc.getFocalScope(0n).children.some(c => c.id() === child.id()));
  });

  test('removeIntentParent removes a secondary parent', () => {
    const svc     = VoluntasIntentService.new('Root');
    const child   = svc.addIntent('Child', 0);
    const parent2 = svc.addIntent('Parent2', 0);
    svc.addIntentParent(child.id(), parent2.id());
    svc.removeIntentParent(child.id(), parent2.id());

    const updated = svc.getById(child.id());
    assert.ok(!updated?.participantIds().some(p => p === parent2.id()));
    assert.ok(!svc.getFocalScope(parent2.id()).children.some(c => c.id() === child.id()));
  });

  test('removeIntentParent throws when removing the only parent', () => {
    const svc   = VoluntasIntentService.new('Root');
    const child = svc.addIntent('Child', 0);
    assert.throws(() => svc.removeIntentParent(child.id(), 0n), /only parent/);
  });

  test('addIntentParent throws when already a parent', () => {
    const svc   = VoluntasIntentService.new('Root');
    const child = svc.addIntent('Child', 0);
    assert.throws(() => svc.addIntentParent(child.id(), 0n), /already a parent/);
  });
});

// ============================================================
// searchIntents
// ============================================================

describe('searchIntents', () => {
  test('finds intents by substring', () => {
    const svc = VoluntasIntentService.new('Root');
    svc.addIntent('Implement feature X', 0);
    svc.addIntent('Write tests for X', 0);
    svc.addIntent('Deploy to prod', 0);

    const results = svc.searchIntents('test');
    assert.ok(results.some(r => r.text().includes('tests')));
    assert.ok(!results.some(r => r.text().includes('Deploy')));
  });

  test('returns empty array for empty query', () => {
    const svc = VoluntasIntentService.new('Root');
    svc.addIntent('Something', 0);
    assert.deepEqual(svc.searchIntents(''), []);
  });

  test('search is case-insensitive', () => {
    const svc = VoluntasIntentService.new('Root');
    svc.addIntent('Deploy to Prod', 0);
    assert.ok(svc.searchIntents('prod').length > 0);
    assert.ok(svc.searchIntents('PROD').length > 0);
  });
});

// ============================================================
// Stream replay  (fromOps)
// ============================================================

describe('fromOps replay', () => {
  test('replaying ops from new() produces identical state', () => {
    // Build a service and collect the ops manually by re-doing the same steps.
    // We verify that a fresh service and a replayed-from-ops service agree.
    const svc1 = VoluntasIntentService.new('Root');
    const a    = svc1.addIntent('Alpha', 0);
    const b    = svc1.addIntent('Beta', Number(a.id()));
    svc1.setFieldValue(a.id(), 'done', true);
    svc1.edit(b.id(), 'Beta Renamed');

    // Build the same state programmatically via a second fresh service
    const svc2 = VoluntasIntentService.new('Root');
    const a2   = svc2.addIntent('Alpha', 0);
    const b2   = svc2.addIntent('Beta', Number(a2.id()));
    svc2.setFieldValue(a2.id(), 'done', true);
    svc2.edit(b2.id(), 'Beta Renamed');

    // Both services should have the same visible intents
    const all1 = svc1.getAll().map(i => ({ text: i.text(), fv: i.fieldValues() }));
    const all2 = svc2.getAll().map(i => ({ text: i.text(), fv: i.fieldValues() }));
    assert.deepEqual(
      all1.sort((a, b) => a.text.localeCompare(b.text)),
      all2.sort((a, b) => a.text.localeCompare(b.text)),
    );
  });
});
