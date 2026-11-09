import assert from 'node:assert/strict';
import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';
import { execFileSync } from 'node:child_process';
import test from 'node:test';
import { readMetadata, verifyArtifacts, assertUploadedAssets, assertReleaseWritable, validateGeneratedNotes, generateNotes } from '../release-ci.mjs';

function temporary(t) {
  const directory = fs.mkdtempSync(path.join(os.tmpdir(), 'vrcm-release-tests-'));
  t.after(() => {
    const resolved = fs.realpathSync(directory);
    if (path.dirname(resolved) !== fs.realpathSync(os.tmpdir()) || !path.basename(resolved).startsWith('vrcm-release-tests-')) throw new Error('Unexpected test cleanup path.');
    fs.rmSync(resolved, { recursive: true, force: true });
  });
  return directory;
}

function repository(t, overrides = {}) {
  const root = temporary(t);
  const files = {
    'gradle/libs.versions.toml': 'app-version = "1.2.3"\napp-code = "7"\n',
    'composeApp/src/commonMain/kotlin/core/shared/AppConst.kt': 'const val APP_VERSION = "1.2.3"\n',
    'installer/VRCM.iss': '#define AppVersion "1.2.3"\n',
    'iosApp/iosApp.xcodeproj/project.pbxproj': 'MARKETING_VERSION = 1.2.3;\nCURRENT_PROJECT_VERSION = 7;\nMARKETING_VERSION = 1.2.3;\nCURRENT_PROJECT_VERSION = 7;\n',
    'iosApp/iosApp/Info.plist': '<key>CFBundleShortVersionString</key><string>$(MARKETING_VERSION)</string>\n<key>CFBundleVersion</key><string>$(CURRENT_PROJECT_VERSION)</string>',
    ...overrides,
  };
  for (const [name, content] of Object.entries(files)) {
    fs.mkdirSync(path.dirname(path.join(root, name)), { recursive: true });
    fs.writeFileSync(path.join(root, name), content);
  }
  const git = (...args) => execFileSync('git', ['-c', 'commit.gpgsign=false', '-c', `core.hooksPath=${path.join(root, 'no-hooks')}`, ...args], { cwd: root, stdio: 'pipe', encoding: 'utf8' }).trim();
  git('init', '--initial-branch=main');
  git('config', 'user.name', 'Release Test');
  git('config', 'user.email', 'release-test@example.invalid');
  git('add', '.');
  git('commit', '-m', 'test: release fixture');
  git('tag', '1.2.3');
  git('update-ref', 'refs/remotes/origin/main', 'HEAD');
  return { root, git };
}

test('release metadata comes from the tag, not later edits in the checkout', t => {
  const { root, git } = repository(t);
  fs.writeFileSync(path.join(root, 'gradle/libs.versions.toml'), 'app-version = "9.9.9"\napp-code = "99"\n');
  const metadata = readMetadata(root, '1.2.3');
  assert.equal(metadata.version, '1.2.3');
  assert.equal(metadata.code, '7');
  assert.equal(metadata.sha, git('rev-parse', 'refs/tags/1.2.3'));
});

test('an installer with a different version stops the release', t => {
  const { root } = repository(t, { 'installer/VRCM.iss': '#define AppVersion "1.2.2"\n' });
  assert.throws(() => readMetadata(root, '1.2.3'), /installer\/VRCM\.iss/);
});

test('a mismatched second Xcode configuration stops the release', t => {
  const { root } = repository(t, {
    'iosApp/iosApp.xcodeproj/project.pbxproj': 'MARKETING_VERSION = 1.2.3;\nCURRENT_PROJECT_VERSION = 7;\nMARKETING_VERSION = 1.2.3;\nCURRENT_PROJECT_VERSION = 6;\n',
  });
  assert.throws(() => readMetadata(root, '1.2.3'), /CURRENT_PROJECT_VERSION/);
});

test('a release commit outside main is rejected', t => {
  const { root, git } = repository(t);
  fs.writeFileSync(path.join(root, 'unreviewed.txt'), 'unreviewed source');
  git('add', '.');
  git('commit', '-m', 'test: unreviewed fixture');
  git('tag', '1.2.4');
  assert.throws(() => readMetadata(root, '1.2.4'), /already on origin\/main/);
});

test('unsafe or nonstandard tag inputs are rejected before accessing Git', t => {
  const root = temporary(t);
  for (const tag of ['1.2.3\n', '../main', '--help', 'v1.2.3', 'main']) assert.throws(() => readMetadata(root, tag), /tag format/);
});

test('a missing or empty promised package prevents publication', t => {
  const directory = temporary(t);
  const metadata = { assets: ['one.apk', 'two.exe'] };
  fs.writeFileSync(path.join(directory, 'one.apk'), 'package bytes');
  assert.throws(() => verifyArtifacts(directory, metadata), /two\.exe/);
  fs.writeFileSync(path.join(directory, 'two.exe'), '');
  assert.throws(() => verifyArtifacts(directory, metadata), /two\.exe/);
});

test('an uploaded package with changed bytes cannot pass the publishing gate', t => {
  const directory = temporary(t);
  fs.writeFileSync(path.join(directory, 'one.apk'), 'original package');
  const expected = verifyArtifacts(directory, { assets: ['one.apk'] });
  fs.writeFileSync(path.join(directory, 'one.apk'), 'changed package!');
  const changed = verifyArtifacts(directory, { assets: ['one.apk'] });
  const uploaded = changed.map(item => ({ ...item, state: 'uploaded' }));
  assert.throws(() => assertUploadedAssets(expected, uploaded), /integrity verification/);
  assert.doesNotThrow(() => assertUploadedAssets(changed, uploaded));
});

test('failed drafts are resumable, published releases require explicit replacement', () => {
  assert.doesNotThrow(() => assertReleaseWritable(null, false));
  assert.doesNotThrow(() => assertReleaseWritable({ draft: true }, false));
  assert.throws(() => assertReleaseWritable({ draft: false }, false), /already published/);
  assert.doesNotThrow(() => assertReleaseWritable({ draft: false }, true));
});

test('incomplete translations and malformed AI notes are rejected', () => {
  const notes = { new_zh: ['好友列表支持搜索。'], improvements_zh: [], fixes_zh: [], new_en: ['Search the friends list.'], improvements_en: [], fixes_en: [] };
  assert.doesNotThrow(() => validateGeneratedNotes(notes));
  assert.throws(() => validateGeneratedNotes({ ...notes, new_en: [] }), /same number/);
  assert.throws(() => validateGeneratedNotes({ ...notes, new_zh: ['Search the friends list.'] }), /Chinese text/);
  assert.throws(() => validateGeneratedNotes({ ...notes, fixes_zh: 'not an array' }), /field/);
});

test('committed manual notes take precedence without requiring an AI or GitHub request', async t => {
  const manual = '## 新增\n\n- 经人工审核的更新说明。\n';
  const { root } = repository(t, { '.github/release-notes/1.2.3.md': manual });
  t.mock.method(globalThis, 'fetch', async () => { throw new Error('Manual notes must not require network access.'); });
  const output = path.join(root, 'release-notes.md');
  await generateNotes(root, readMetadata(root, '1.2.3'), [], output, 'deepseek-flash', 'high');
  assert.equal(fs.readFileSync(output, 'utf8'), manual);
});

test('truncated AI output falls back to real GitHub notes and retains promised downloads', async t => {
  const { root } = repository(t);
  const previousKey = process.env.DEEPSEEK_API_KEY;
  process.env.DEEPSEEK_API_KEY = 'release-test-placeholder';
  t.after(() => {
    if (previousKey === undefined) delete process.env.DEEPSEEK_API_KEY;
    else process.env.DEEPSEEK_API_KEY = previousKey;
  });
  t.mock.method(globalThis, 'fetch', async url => {
    if (new URL(url).hostname === 'api.deepseek.com') return Response.json({ choices: [{ finish_reason: 'length', message: { content: '{"new_zh":["未完成' } }] });
    if (new URL(url).pathname.endsWith('/releases/generate-notes')) return Response.json({ body: '## What changed\n\n- A verified GitHub change.' });
    if (new URL(url).pathname.endsWith('/releases')) return Response.json([]);
    throw new Error(`Unexpected release API request: ${url}`);
  });
  const metadata = readMetadata(root, '1.2.3');
  const output = path.join(root, 'release-notes.md');
  await generateNotes(root, metadata, metadata.assets.map(name => ({ name })), output, 'deepseek-flash', 'high');
  const notes = fs.readFileSync(output, 'utf8');
  assert.match(notes, /A verified GitHub change\./);
  assert.doesNotMatch(notes, /未完成/);
  for (const name of metadata.assets) assert.ok(notes.includes(`/releases/download/1.2.3/${name}`));
  assert.match(notes, /\[SHA256SUMS\]/);
});
