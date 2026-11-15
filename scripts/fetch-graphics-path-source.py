#!/usr/bin/env python3
"""Retrieve native sources omitted from graphics-path's published source JAR.

Revision is the end of the official AndroidX graphics-path 1.0.1 release commit range:
https://developer.android.com/jetpack/androidx/releases/graphics#1.0.1
Git blob identities are verified; this is not binary reproducibility evidence.
"""
import base64
from concurrent.futures import ThreadPoolExecutor
import hashlib
import json
from pathlib import Path
import subprocess

REVISION = '8a05a22af450d589ef911d772a001a49dcb05b71'
BASE = 'https://android.googlesource.com/platform/frameworks/support/+/' + REVISION + '/'
DEST = Path('build/corresponding-dependency-sources/graphics-path-native-1.0.1')


def get(url):
    return subprocess.run(['curl', '--fail', '--silent', '--show-error', '--retry', '1', '--max-time', '20', url], check=True, capture_output=True).stdout


def tree(path):
    payload = get(BASE + path + '?format=JSON').decode()
    result = []
    for entry in json.loads(payload[payload.index('\n') + 1:])['entries']:
        name = path + entry['name']
        if entry['type'] == 'tree':
            result += tree(name + '/')
        else:
            result.append((name, entry['id']))
    return result


def fetch(entry):
    path, expected = entry
    target = DEST / path
    data = target.read_bytes() if target.exists() else base64.b64decode(get(BASE + path + '?format=TEXT'))
    blob = hashlib.sha1(b'blob ' + str(len(data)).encode() + b'\0' + data).hexdigest()
    if expected is not None:
        assert blob == expected, path
    target.parent.mkdir(parents=True, exist_ok=True)
    target.write_bytes(data)
    return {'path': path, 'blob': blob, 'sha256': hashlib.sha256(data).hexdigest(), 'url': BASE + path}


def main():
    entries = tree('graphics/graphics-path/src/main/cpp/')
    entries += [('graphics/graphics-path/build.gradle', None), ('LICENSE.txt', None)]
    with ThreadPoolExecutor(max_workers=4) as pool:
        files = list(pool.map(fetch, entries))
    report = {'component': 'androidx.graphics:graphics-path:1.0.1', 'revision': REVISION,
              'source': 'Official AndroidX release commit range end; matches release attribution, not a byte-reproducible build proof.', 'files': files}
    Path('docs/release/graphics-path-native-source.json').write_text(json.dumps(report, indent=2) + '\n')
    (DEST / 'SOURCE-MANIFEST.json').write_text(json.dumps(report, indent=2) + '\n')
    print('Retrieved and verified', len(files), 'native/build/licence files')


if __name__ == '__main__':
    main()
