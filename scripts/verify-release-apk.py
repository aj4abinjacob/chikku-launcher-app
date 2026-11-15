#!/usr/bin/env python3
"""Inspect the real packaged APK, permissions, offline notices and native LOAD alignment.

This static inspection does not establish native runtime or device acceptance.
"""
import argparse
import hashlib
import json
from pathlib import Path
import re
import struct
import subprocess
import zipfile

ALLOWED = {
    'android.permission.ACCESS_NETWORK_STATE', 'android.permission.INTERNET',
    'android.permission.BLUETOOTH_CONNECT', 'android.permission.BLUETOOTH',
    'android.permission.READ_CONTACTS', 'android.permission.EXPAND_STATUS_BAR',
    'android.permission.REQUEST_DELETE_PACKAGES', 'android.permission.USE_BIOMETRIC',
}


def elf(data):
    if data[:4] != b'\x7fELF' or data[4] not in (1, 2) or data[5] not in (1, 2):
        raise ValueError('Invalid ELF header')
    bits64 = data[4] == 2
    endian = '<' if data[5] == 1 else '>'
    offset = struct.unpack_from(endian + ('Q' if bits64 else 'I'), data, 32 if bits64 else 28)[0]
    size, count = struct.unpack_from(endian + 'HH', data, 54 if bits64 else 42)
    fmt = endian + ('IIQQQQQQ' if bits64 else 'IIIIIIII')
    if size < struct.calcsize(fmt) or offset + size * count > len(data):
        raise ValueError('Invalid ELF program-header bounds')
    loads, relro = [], []
    for index in range(count):
        header = struct.unpack_from(fmt, data, offset + index * size)
        if header[0] == 1:
            loads.append(header[-1])
        elif header[0] == 0x6474e552:
            address = header[3] if bits64 else header[2]
            memory = header[6] if bits64 else header[5]
            relro.append({'virtual_address': address, 'memory_size': memory, 'end_mod_16384': (address + memory) % 16384})
    return {'load_alignments': loads, 'load_alignment_at_least_16k': bool(loads) and all(x >= 16384 for x in loads),
            'relro': relro, 'scope': 'Header inspection only; 16KB runtime and RELRO behavior need separate acceptance.'}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--apk', type=Path, default=Path('app/build/outputs/apk/release/app-release-unsigned.apk'))
    parser.add_argument('--build-tools', type=Path, required=True)
    parser.add_argument('--expected-version-code', type=int, default=4)
    parser.add_argument('--expected-version-name', default='0.1.0-beta.3')
    args = parser.parse_args()
    badging = subprocess.check_output([str(args.build_tools/'aapt2'), 'dump', 'badging', str(args.apk)], text=True)
    assert "package: name='io.github.aj4abinjacob.chikku'" in badging
    assert f"versionCode='{args.expected_version_code}'" in badging and f"versionName='{args.expected_version_name}'" in badging
    assert 'application-debuggable' not in badging
    assert re.search(r"^(?:minSdkVersion|sdkVersion):'29'$", badging, re.M) and "targetSdkVersion:'36'" in badging
    permissions = set(re.findall(r"uses-permission[^:]*: name='([^']+)'", badging))
    allowed = ALLOWED | {'io.github.aj4abinjacob.chikku.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION'}
    assert permissions <= allowed, permissions - allowed
    assert ALLOWED <= permissions, ALLOWED - permissions
    # Fail if dependency coordinates/bytes drift beyond the reviewed licence inventory.
    inputs = json.loads(Path('build/release-audit-inputs.json').read_text())
    reviewed = json.loads(Path('docs/release/runtime-dependencies.json').read_text())
    assert not reviewed['exceptions'], 'Resolve licence/source exceptions before packaging'
    assert {x for x in inputs['modules'] if not x.endswith(':unspecified')} == {
        x['coordinate'] for x in reviewed['modules']
    }, 'Regenerate and review the release dependency inventory'
    actual_artifacts = {
        (x['coordinate'], Path(x['file']).name, hashlib.sha256(Path(x['file']).read_bytes()).hexdigest())
        for x in inputs['artifacts'] if not x['coordinate'].endswith(':unspecified')
    }
    expected_artifacts = {
        (row['coordinate'], item['filename'], item['sha256'])
        for row in reviewed['modules'] for item in row['artifacts']
    }
    assert actual_artifacts == expected_artifacts, 'Resolved runtime bytes differ from the reviewed audit'
    natives = []
    documents = []
    with zipfile.ZipFile(args.apk) as archive:
        for file in sorted(Path('app/src/main/assets/legal').glob('*.txt')):
            path = 'assets/legal/' + file.name
            assert archive.read(path) == file.read_bytes(), 'Missing/stale shipped document: ' + path
            documents.append({'path': path, 'sha256': hashlib.sha256(archive.read(path)).hexdigest()})
        assert len(documents) >= 8
        city_manifest = json.loads(Path('data/weather/geonames-2026-10-15/manifest.json').read_text())
        city_index = archive.read('assets/weather/cities.index')
        assert hashlib.sha256(city_index).hexdigest() == city_manifest['sha256'], 'Missing/changed offline cities'
        assert city_index[:2] == bytes([0x1f, 0x8b]), 'Offline city index must retain gzip bytes'
        for name, digest in city_manifest['inputs'].items():
            assert hashlib.sha256((Path('data/weather/geonames-2026-10-15') / name).read_bytes()).hexdigest() == digest
        assert not any('discoverbridge' in x for x in archive.namelist()), 'Companion code is not a production artifact'
        for name in archive.namelist():
            if name.endswith('.so'):
                details = elf(archive.read(name))
                assert details['load_alignment_at_least_16k'], name
                natives.append({'path': name, 'sha256': hashlib.sha256(archive.read(name)).hexdigest(), **details})
    alignment = subprocess.run([str(args.build_tools/'zipalign'), '-c', '-P', '16', '4', str(args.apk)], capture_output=True, text=True)
    assert alignment.returncode == 0, alignment.stdout + alignment.stderr
    report = {'apk': args.apk.name, 'sha256': hashlib.sha256(args.apk.read_bytes()).hexdigest(),
              'application_id': 'io.github.aj4abinjacob.chikku', 'version_code': args.expected_version_code, 'version_name': args.expected_version_name,
              'debuggable': False, 'min_sdk': 29, 'target_sdk': 36, 'permissions': sorted(permissions),
              'legal_documents': documents, 'native_libraries': natives, 'zipalign_16k': 'PASS',
              'signature': 'Unsigned inspection artifact; not a signed AAB or distributable release.',
              'runtime_acceptance': 'Not established by this static report'}
    output = Path('build/release-verification.json')
    output.write_text(json.dumps(report, indent=2) + '\n')
    print(json.dumps(report, indent=2))


if __name__ == '__main__':
    main()
