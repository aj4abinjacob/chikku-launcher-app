import { test } from 'node:test';
import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
import { createHash, webcrypto } from 'node:crypto';
import vm from 'node:vm';

const source = await readFile(new URL('../source-download-client.js', import.meta.url), 'utf8');
const sha = bytes => createHash('sha256').update(bytes).digest('hex');
const payload = Buffer.from('verified source fixture');

async function run({ partHash = sha(payload), archiveHash = sha(payload), statusCode = 200 } = {}) {
  let click;
  let saved;
  let clicked = 0;
  const button = { disabled: false, dataset: { manifest: 'revision/manifest.json' },
    addEventListener: (_, callback) => { click = callback; } };
  const status = { textContent: '' };
  const manifest = { archive_file: 'source.tar.gz', archive_bytes: payload.length,
    archive_sha256: archiveHash, parts: [{ file: 'source.tar.gz.part01', bytes: payload.length, sha256: partHash }] };
  class DownloadURL extends URL {
    static createObjectURL(blob) { saved = blob; return 'blob:test'; }
    static revokeObjectURL() {}
  }
  const context = vm.createContext({
    document: { querySelector: selector => selector === '#download' ? button : status,
      createElement: () => ({ click: () => { clicked++; } }) },
    location: { href: 'https://example.invalid/chikku/source/' },
    crypto: webcrypto, URL: DownloadURL, Blob, Uint8Array,
    setTimeout: () => 0,
    fetch: async url => url.pathname.endsWith('manifest.json')
      ? { ok: true, json: async () => manifest }
      : { ok: statusCode === 200, arrayBuffer: async () => Uint8Array.from(payload).buffer },
  });
  vm.runInContext(source, context);
  await click();
  assert.equal(button.disabled, false);
  return { status: status.textContent, saved, clicked };
}

test('joins and verifies bytes before making a download', async () => {
  const result = await run();
  assert.equal(result.clicked, 1);
  assert.deepEqual(Buffer.from(await result.saved.arrayBuffer()), payload);
  assert.match(result.status, /verified/);
});

test('a corrupt part never creates a download', async () => {
  const result = await run({ partHash: '0'.repeat(64) });
  assert.equal(result.clicked, 0);
  assert.equal(result.saved, undefined);
  assert.match(result.status, /part failed its checksum/);
});

test('a wrong assembled hash never creates a download', async () => {
  const result = await run({ archiveHash: '0'.repeat(64) });
  assert.equal(result.clicked, 0);
  assert.match(result.status, /assembled source archive failed/);
});

test('a failed response offers manual download without a false success', async () => {
  const result = await run({ statusCode: 503 });
  assert.equal(result.clicked, 0);
  assert.match(result.status, /could not be downloaded/);
  assert.match(result.status, /manual instructions/);
});
