import assert from 'node:assert/strict';
import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';
import { execFileSync } from 'node:child_process';
import test from 'node:test';
import { readMetadata, verifyArtifacts, assertUploadedAssets, assertReleaseWritable, validateGeneratedNotes, generateNotes, publishRelease, validateBuildRun } from '../release-ci.mjs';

// The public Markdown contract used by the historical 1.1.3/1.1.4 releases.
const HISTORICAL_NOTES = `## 新增

- 新增好友搜索。

## 优化

- 优化资料加载。

## 修复

- 修复页面崩溃。

****************

## New Features

- Added friend search.

## Optimizations

- Improved profile loading.

## Bug Fixes

- Fixed a page crash.

详情 / Details: <https://github.com/vrcm-team/VRCM/tree/main/docs/releases/1.2.3.md>
`;

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
    'docs/releases/1.2.3.md': '# Release details\n',
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

test('build-only validation uses committed main without requiring a tag or release notes', t => {
  const { root, git } = repository(t);
  git('tag', '-d', '1.2.3');
  git('rm', 'docs/releases/1.2.3.md');
  git('commit', '-m', 'test: source before release preparation');
  git('update-ref', 'refs/remotes/origin/main', 'HEAD');
  fs.writeFileSync(path.join(root, 'gradle/libs.versions.toml'), 'app-version = "9.9.9"\napp-code = "99"\n');
  const metadata = readMetadata(root, '', { buildOnly: true });
  assert.equal(metadata.sha, git('rev-parse', 'HEAD'));
  assert.equal(metadata.version, '1.2.3');
  assert.equal(metadata.tag, '');
  assert.equal(metadata.buildOnly, true);
});

test('build-only validation rejects another branch or inconsistent platform versions', t => {
  const { root, git } = repository(t, { 'installer/VRCM.iss': '#define AppVersion "1.2.2"\n' });
  assert.throws(() => readMetadata(root, '', { buildOnly: true }), /installer\/VRCM\.iss/);
  fs.writeFileSync(path.join(root, 'unreviewed.txt'), 'unreviewed source');
  git('add', '.');
  git('commit', '-m', 'test: source outside main');
  assert.throws(() => readMetadata(root, '', { buildOnly: true }), /current origin\/main/);
  assert.throws(() => readMetadata(root, '1.2.3', { buildOnly: true }), /does not accept a release tag/);
});

test('build-only metadata cannot generate notes or publish, even if a tag is supplied later', async t => {
  const { root } = repository(t);
  t.mock.method(globalThis, 'fetch', async () => { throw new Error('Build-only validation must not access release APIs.'); });
  const metadata = { ...readMetadata(root, '', { buildOnly: true }), tag: '1.2.3' };
  await assert.rejects(generateNotes(root, metadata, [], 'unused', 'deepseek-flash', 'high'), /Build-only metadata/);
  await assert.rejects(publishRelease(root, metadata, [], 'unused', true, false), /Build-only metadata/);
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

test('a missing release-details document cannot produce a broken historical Details link', t => {
  const { root } = repository(t, { 'docs/releases/1.2.3.md': '' });
  assert.throws(() => readMetadata(root, '1.2.3'), /Missing or empty release details/);
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

function publishingFixture(t, { existingDraft = false, corruptUpload = false, uploaded = false, corruptExisting = false } = {}) {
  const { root } = repository(t);
  const metadata = readMetadata(root, '1.2.3');
  const notes = path.join(root, 'notes.md');
  fs.writeFileSync(notes, HISTORICAL_NOTES);
  for (const name of metadata.assets) fs.writeFileSync(path.join(root, name), `Built package: ${name}`);
  const artifacts = verifyArtifacts(root, metadata);
  const makeDraft = () => ({
    id: 42, tag_name: metadata.tag, draft: true, assets: [],
    upload_url: 'https://uploads.github.com/repos/vrcm-team/VRCM/releases/42/assets{?name,label}',
  });
  let release = existingDraft ? makeDraft() : null;
  if (uploaded || corruptExisting) release.assets = artifacts.map((item, index) => ({ ...item, id: index + 1, state: 'uploaded' }));
  if (corruptExisting) release.assets[0].digest = 'sha256:old-failed-upload';
  t.mock.method(globalThis, 'fetch', async (url, options = {}) => {
    const endpoint = new URL(url);
    const method = options.method || 'GET';
    if (endpoint.pathname.endsWith(`/git/ref/tags/${metadata.tag}`)) {
      return Response.json({ object: { type: 'commit', sha: metadata.sha } });
    }
    if (endpoint.pathname.endsWith(`/releases/tags/${metadata.tag}`)) {
      return Response.json({ message: 'Drafts are not available by tag' }, { status: 404 });
    }
    if (endpoint.pathname.endsWith('/releases') && method === 'GET') {
      // A resumable draft may be behind a full page of older releases.
      if (existingDraft && endpoint.searchParams.get('page') === '1') {
        return Response.json(Array.from({ length: 100 }, (_, index) => ({ id: 100 + index, tag_name: `0.0.${index}` })));
      }
      return Response.json(release ? [release] : []);
    }
    if (endpoint.pathname.endsWith('/releases') && method === 'POST') {
      if (release) return Response.json({ message: 'Release already exists' }, { status: 422 });
      release = { ...makeDraft(), ...JSON.parse(options.body) };
      return Response.json(release);
    }
    if (endpoint.pathname.endsWith('/releases/42') && release) {
      if (method === 'PATCH') {
        // Draft edits can return GitHub's internal untagged ID if the tag is omitted.
        const changes = JSON.parse(options.body);
        release.tag_name = changes.tag_name || 'untagged-internal-id';
        Object.assign(release, changes);
      }
      return Response.json(release);
    }
    if (endpoint.hostname === 'uploads.github.com' && endpoint.pathname.endsWith('/releases/42/assets') && method === 'POST') {
      if (uploaded) throw new Error('Matching uploaded packages must be reused.');
      const artifact = artifacts.find(item => item.name === endpoint.searchParams.get('name'));
      assert.ok(artifact, 'Upload must belong to the validated package set.');
      assert.deepEqual(Buffer.from(options.body), fs.readFileSync(artifact.file));
      const asset = { name: artifact.name, id: release.assets.length + 1, size: artifact.size, digest: artifact.digest, state: 'uploaded' };
      if (corruptUpload && artifact === artifacts[0]) asset.digest = 'sha256:changed-on-server';
      release.assets.push(asset);
      return Response.json(asset);
    }
    if (endpoint.pathname.endsWith('/releases/assets/1') && method === 'DELETE' && corruptExisting) {
      release.assets = release.assets.filter(asset => asset.id !== 1);
      return new Response(null, { status: 204 });
    }
    throw new Error(`Unexpected publishing request: ${method} ${url}`);
  });
  return { root, metadata, notes, artifacts, get release() { return release; } };
}

test('a fresh draft is verified by ID before it becomes public when lookup by tag returns 404', async t => {
  const fixture = publishingFixture(t);
  await publishRelease(fixture.root, fixture.metadata, fixture.artifacts, fixture.notes, false, false);
  assert.equal(fixture.release.draft, false);
  assert.equal(fixture.release.body, HISTORICAL_NOTES);
  assert.deepEqual(fixture.release.assets.map(item => item.name), fixture.metadata.assets);
});

test('a draft found on a later release-list page is resumed without creating another release', async t => {
  const fixture = publishingFixture(t, { existingDraft: true });
  await publishRelease(fixture.root, fixture.metadata, fixture.artifacts, fixture.notes, false, false);
  assert.equal(fixture.release.id, 42);
  assert.equal(fixture.release.draft, false);
  assert.equal(fixture.release.body, HISTORICAL_NOTES);
  assert.equal(fixture.release.tag_name, fixture.metadata.tag);
});

test('publication recovery preserves the tag and reuses already verified assets while correcting notes', async t => {
  const fixture = publishingFixture(t, { existingDraft: true, uploaded: true });
  await publishRelease(fixture.root, fixture.metadata, fixture.artifacts, fixture.notes, false, false);
  assert.equal(fixture.release.tag_name, fixture.metadata.tag);
  assert.equal(fixture.release.draft, false);
  assert.equal(fixture.release.body, HISTORICAL_NOTES);
  assert.deepEqual(fixture.release.assets.map(item => item.digest), fixture.artifacts.map(item => item.digest));
});

test('publication recovery replaces a corrupt prior upload and verifies its new bytes', async t => {
  const fixture = publishingFixture(t, { existingDraft: true, corruptExisting: true });
  await publishRelease(fixture.root, fixture.metadata, fixture.artifacts, fixture.notes, false, false);
  assert.equal(fixture.release.draft, false);
  assertUploadedAssets(fixture.artifacts, fixture.release.assets);
  assert.equal(fixture.release.assets.length, fixture.artifacts.length);
});

test('a draft stays private when its uploaded package fails integrity verification', async t => {
  const fixture = publishingFixture(t, { corruptUpload: true });
  await assert.rejects(publishRelease(fixture.root, fixture.metadata, fixture.artifacts, fixture.notes, false, false), /integrity verification/);
  assert.equal(fixture.release.draft, true);
});

test('publication recovery accepts successful builds even when their final publish job failed', async t => {
  const metadata = { tag: '1.2.3', sha: 'a'.repeat(40) };
  t.mock.method(globalThis, 'fetch', async url => {
    const endpoint = new URL(url).pathname;
    if (endpoint.endsWith('/actions/runs/1234')) return Response.json({
      status: 'completed', conclusion: 'failure', head_sha: metadata.sha, path: '.github/workflows/release.yml',
    });
    if (endpoint.endsWith('/actions/runs/1234/jobs')) return Response.json({ total_count: 6, jobs: [
      ...['prepare', 'android', 'windows', 'apple', 'ios'].map(name => ({ name, conclusion: 'success' })),
      { name: 'publish', conclusion: 'failure' },
    ] });
    throw new Error(`Unexpected recovery request: ${url}`);
  });
  await assert.doesNotReject(validateBuildRun(metadata, '1234'));
});

test('publication recovery rejects unfinished, unrelated, incomplete or unsuccessful builds', async t => {
  const metadata = { tag: '1.2.3', sha: 'a'.repeat(40) };
  const run = { status: 'completed', head_sha: metadata.sha, path: '.github/workflows/release.yml' };
  const jobs = ['prepare', 'android', 'windows', 'apple', 'ios'].map(name => ({ name, conclusion: 'success' }));
  for (const scenario of [
    { name: 'unfinished run', run: { ...run, status: 'in_progress' } },
    { name: 'different source commit', run: { ...run, head_sha: 'b'.repeat(40) } },
    { name: 'different workflow', run: { ...run, path: '.github/workflows/test.yml' } },
    { name: 'failed Android build', jobs: jobs.map(job => job.name === 'android' ? { ...job, conclusion: 'failure' } : job) },
    { name: 'missing iOS build', jobs: jobs.filter(job => job.name !== 'ios') },
    { name: 'ambiguous duplicate build', jobs: [...jobs, jobs[1]] },
  ]) {
    await t.test(scenario.name, async subtest => {
      subtest.mock.method(globalThis, 'fetch', async url => {
        if (new URL(url).pathname.endsWith('/jobs')) return Response.json({ total_count: (scenario.jobs || jobs).length, jobs: scenario.jobs || jobs });
        return Response.json(scenario.run || run);
      });
      await assert.rejects(validateBuildRun(metadata, '1234'), /must be completed|did not complete/);
    });
  }
});

test('publication recovery requires a release tag and a numeric run ID before requesting packages', async t => {
  t.mock.method(globalThis, 'fetch', async () => { throw new Error('Invalid recovery inputs must not access GitHub.'); });
  await assert.rejects(validateBuildRun({ buildOnly: true, tag: '1.2.3' }, '1234'), /requires a release tag/);
  await assert.rejects(validateBuildRun({ tag: '' }, '1234'), /requires a release tag/);
  for (const runId of ['0', '-1', '1234/jobs', '1\n', '9007199254740992']) {
    await assert.rejects(validateBuildRun({ tag: '1.2.3' }, runId), /Invalid build run ID/);
  }
});

test('incomplete translations and malformed AI notes are rejected', () => {
  const notes = { new_zh: ['好友列表支持搜索。'], improvements_zh: [], fixes_zh: [], new_en: ['Search the friends list.'], improvements_en: [], fixes_en: [] };
  assert.doesNotThrow(() => validateGeneratedNotes(notes));
  assert.throws(() => validateGeneratedNotes({ ...notes, new_en: [] }), /same number/);
  assert.throws(() => validateGeneratedNotes({ ...notes, new_zh: ['Search the friends list.'] }), /Chinese text/);
  assert.throws(() => validateGeneratedNotes({ ...notes, fixes_zh: 'not an array' }), /field/);
});

test('committed manual notes take precedence without requiring an AI or GitHub request', async t => {
  const { root } = repository(t, { '.github/release-notes/1.2.3.md': HISTORICAL_NOTES });
  t.mock.method(globalThis, 'fetch', async () => { throw new Error('Manual notes must not require network access.'); });
  const output = path.join(root, 'release-notes.md');
  await generateNotes(root, readMetadata(root, '1.2.3'), [], output, 'deepseek-flash', 'high');
  assert.equal(fs.readFileSync(output, 'utf8'), HISTORICAL_NOTES);
});

test('publication recovery can use corrected manual notes without moving or editing the original tag', async t => {
  const { root, git } = repository(t, { '.github/release-notes/1.2.3.md': HISTORICAL_NOTES });
  const metadata = readMetadata(root, '1.2.3');
  const corrected = HISTORICAL_NOTES.replace('新增好友搜索。', '新增账号切换。').replace('Added friend search.', 'Added account switching.');
  const manualNotes = path.join(temporary(t), 'manual-notes.md');
  fs.writeFileSync(manualNotes, corrected);
  t.mock.method(globalThis, 'fetch', async () => { throw new Error('Recovery notes must not require network access.'); });
  const output = path.join(root, 'release-notes.md');
  await generateNotes(root, metadata, [], output, 'deepseek-flash', 'high', { manualNotes });
  assert.equal(fs.readFileSync(output, 'utf8'), corrected);
  assert.equal(git('show', '1.2.3:.github/release-notes/1.2.3.md'), HISTORICAL_NOTES.trim());
  assert.equal(git('rev-parse', '1.2.3'), metadata.sha);
});

test('manual notes with an extra download section cannot bypass the historical format', async t => {
  const manual = HISTORICAL_NOTES.replace('详情 / Details:', '## 下载\n\n- APK\n\n详情 / Details:');
  const { root } = repository(t, { '.github/release-notes/1.2.3.md': manual });
  await assert.rejects(generateNotes(root, readMetadata(root, '1.2.3'), [], path.join(root, 'notes.md'), 'deepseek-flash', 'high'), /single-line bullets/);
});

function mockDeepSeek(t, response) {
  const previousKey = process.env.DEEPSEEK_API_KEY;
  process.env.DEEPSEEK_API_KEY = 'release-test-placeholder';
  t.after(() => {
    if (previousKey === undefined) delete process.env.DEEPSEEK_API_KEY;
    else process.env.DEEPSEEK_API_KEY = previousKey;
  });
  t.mock.method(globalThis, 'fetch', async url => {
    if (new URL(url).hostname === 'api.deepseek.com') return Response.json(response);
    if (new URL(url).pathname.endsWith('/releases')) return Response.json([]);
    throw new Error(`Unexpected release API request: ${url}`);
  });
}

test('AI output is rendered in exactly the historical Markdown format', async t => {
  const { root } = repository(t);
  mockDeepSeek(t, { choices: [{ finish_reason: 'stop', message: { content: JSON.stringify({
    new_zh: ['新增好友搜索。'], improvements_zh: ['优化资料加载。'], fixes_zh: ['修复页面崩溃。'],
    new_en: ['Added friend search.'], improvements_en: ['Improved profile loading.'], fixes_en: ['Fixed a page crash.'],
  }) } }] });
  const metadata = readMetadata(root, '1.2.3');
  const output = path.join(root, 'release-notes.md');
  await generateNotes(root, metadata, metadata.assets.map(name => ({ name })), output, 'deepseek-flash', 'high');
  assert.equal(fs.readFileSync(output, 'utf8'), HISTORICAL_NOTES);
});

test('truncated AI output stops publication instead of changing the announcement format', async t => {
  const { root } = repository(t);
  mockDeepSeek(t, { choices: [{ finish_reason: 'length', message: { content: '{"new_zh":["未完成' } }] });
  const output = path.join(root, 'release-notes.md');
  await assert.rejects(generateNotes(root, readMetadata(root, '1.2.3'), [], output, 'deepseek-flash', 'high'), /publication is stopped/);
  assert.equal(fs.existsSync(output), false);
});
