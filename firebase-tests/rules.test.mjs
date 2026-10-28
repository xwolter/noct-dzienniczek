// Testy reguł Firestore dla grupy roku. Uruchomienie (z emulatorem):
//   npx firebase-tools emulators:exec --only firestore --project demo-noct "node --test firebase-tests/rules.test.mjs"
// Wymaga @firebase/rules-unit-testing i firebase (ścieżka do modułów w NODE_PATH albo obok projektu).
import fs from 'node:fs';
import { test, before, after, beforeEach } from 'node:test';
import { createRequire } from 'node:module';

const require = createRequire(process.env.FIREBASE_TESTING_DIR ? `${process.env.FIREBASE_TESTING_DIR}/package.json` : import.meta.url);
const { initializeTestEnvironment, assertFails, assertSucceeds } = require('@firebase/rules-unit-testing');
const { doc, setDoc, getDoc, updateDoc, deleteDoc, collection, getDocs } = require('firebase/firestore');

let env;
const exam = (author, extra = {}) => ({
  kind: 'kolokwium', courseId: 'ASD', courseName: 'Algorytmy', date: '2026-11-03', time: '10:00',
  room: 's. 1.40', note: 'rozdz. 1-4', authorUid: author, author: author === 'anna' ? 'Anna' : 'Jan', updatedAt: 1, ...extra,
});

before(async () => {
  env = await initializeTestEnvironment({
    projectId: 'demo-noct',
    firestore: { rules: fs.readFileSync(new URL('../firestore.rules', import.meta.url), 'utf8') },
  });
});
after(async () => { await env.cleanup(); });
beforeEach(async () => {
  await env.clearFirestore();
  await env.withSecurityRulesDisabled(async (ctx) => {
    const db = ctx.firestore();
    await setDoc(doc(db, 'usosGroups/ABCDEFGH'), { name: 'Rok 1', createdBy: 'anna', createdAt: 1 });
    await setDoc(doc(db, 'usosGroups/ABCDEFGH/members/anna'), { name: 'Anna', joinedAt: 1 });
    await setDoc(doc(db, 'usosGroups/ABCDEFGH/members/jan'), { name: 'Jan', joinedAt: 1 });
    await setDoc(doc(db, 'usosGroups/ABCDEFGH/exams/e1'), exam('anna'));
  });
});

const as = (uid) => env.authenticatedContext(uid).firestore();

test('członek grupy dodaje termin jako on sam, ale nie jako ktoś inny', async () => {
  await assertSucceeds(setDoc(doc(as('jan'), 'usosGroups/ABCDEFGH/exams/e2'), exam('jan')));
  await assertFails(setDoc(doc(as('jan'), 'usosGroups/ABCDEFGH/exams/e3'), exam('anna')));
});

test('edycja terminu nie zmienia autora', async () => {
  await assertSucceeds(setDoc(doc(as('jan'), 'usosGroups/ABCDEFGH/exams/e1'), exam('anna', { room: 's. 2.10' })));
  await assertFails(updateDoc(doc(as('jan'), 'usosGroups/ABCDEFGH/exams/e1'), { authorUid: 'jan' }));
  await assertFails(updateDoc(doc(as('jan'), 'usosGroups/ABCDEFGH/exams/e1'), { author: 'Ktoś' }));
});

test('usunąć termin może tylko autor', async () => {
  await assertFails(deleteDoc(doc(as('jan'), 'usosGroups/ABCDEFGH/exams/e1')));
  await assertSucceeds(deleteDoc(doc(as('anna'), 'usosGroups/ABCDEFGH/exams/e1')));
});

test('pola mają limity długości, a obcych pól nie da się dodać', async () => {
  await assertFails(setDoc(doc(as('jan'), 'usosGroups/ABCDEFGH/exams/e4'), exam('jan', { note: 'x'.repeat(1001) })));
  await assertFails(setDoc(doc(as('jan'), 'usosGroups/ABCDEFGH/exams/e5'), exam('jan', { admin: true })));
  await assertFails(setDoc(doc(as('ola'), 'usosGroups/ABCDEFGH/members/ola'), { name: 'x'.repeat(61), joinedAt: 1 }));
});

test('poza grupą nie widać terminów ani członków, a grup nie da się wylistować', async () => {
  await assertFails(getDoc(doc(as('ola'), 'usosGroups/ABCDEFGH/exams/e1')));
  await assertFails(getDocs(collection(as('ola'), 'usosGroups/ABCDEFGH/members')));
  await assertFails(getDocs(collection(as('ola'), 'usosGroups')));
  await assertSucceeds(getDoc(doc(as('jan'), 'usosGroups/ABCDEFGH/exams/e1')));
});

test('dokument ucznia widzi i zmienia tylko jego właściciel', async () => {
  await assertSucceeds(setDoc(doc(as('anna'), 'usosStudent/anna'), { settings: {} }));
  await assertFails(getDoc(doc(as('jan'), 'usosStudent/anna')));
  await assertFails(setDoc(doc(env.unauthenticatedContext().firestore(), 'usosStudent/anna'), { x: 1 }));
});
