const assert = require('node:assert/strict');
const {matches} = require('../web/js/team-profile-history.js');
const indices = [1, 3, 4, 7, 8, 9]; // Team skipped tournaments; never use its participation STT.
const select = filters => indices.filter(index => matches(index, 3, filters));
assert.deepEqual(select({phase: '2'}), [4]);
assert.deepEqual(select({phase: '3'}), [7, 8, 9]);
assert.deepEqual(select({window: '8'}), [7, 8]);
assert.deepEqual(select({window: '2'}), [1]);
assert.deepEqual(select({from: '3', to: '8'}), [3, 4, 7, 8]);
assert.deepEqual(select({phase: '3', window: '8', from: '8', to: '9'}), [8]);
assert.deepEqual(select({from: '8', to: '3'}), []);
assert.deepEqual(select({}), indices);
assert.equal(matches(8, 3, {tier: 'S'}, 'S'), true);
assert.equal(matches(8, 3, {tier: 'S'}, 'A'), false);
assert.equal(matches(8, 3, {tier: 'A'}, null), true);
assert.equal(matches(8, 3, {tier: 's', phase: '3', window: '8', from: '7', to: '9'}, 'S'), true);
assert.equal(matches(8, 3, {tier: 'S', phase: '2'}, 'S'), false);
console.log('PASS: Phase, rolling window, inclusive range, combined filters and skipped tournaments');
