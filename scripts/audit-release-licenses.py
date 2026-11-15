#!/usr/bin/env python3
"""Audit exact Gradle runtime artifacts/POMs and retain all embedded legal files.

Run Gradle with -I scripts/release-audit.init.gradle :app:releaseAuditInputs first.
Network use is limited to public Maven metadata/source archives; no credentials.
Never treats an absent licence as permission. Review generated exceptions before distribution.
"""
import argparse
from concurrent.futures import ThreadPoolExecutor
import hashlib
import io
import json
from pathlib import Path
import re
import subprocess
import urllib.error
import urllib.request
import xml.etree.ElementTree as ET
import zipfile

NS = {'m': 'http://maven.apache.org/POM/4.0.0'}
CACHE = Path.home() / '.gradle/caches/modules-2/files-2.1'


def digest(data):
    return hashlib.sha256(data).hexdigest()


def origin(coordinate, suffix):
    group, artifact, version = coordinate.split(':')
    base = 'https://dl.google.com/dl/android/maven2/' if group.startswith('androidx.') else 'https://repo.maven.apache.org/maven2/'
    return base + group.replace('.', '/') + '/' + artifact + '/' + version + '/' + artifact + '-' + version + suffix


def pom(coordinate, destination, seen=None):
    seen = set() if seen is None else seen
    if coordinate in seen:
        raise ValueError('Cyclic parent POM: ' + coordinate)
    seen.add(coordinate)
    group, artifact, version = coordinate.split(':')
    candidates = sorted((CACHE / group / artifact / version).glob('*/*.pom'))
    url = origin(coordinate, '.pom')
    data = candidates[0].read_bytes() if candidates else urllib.request.urlopen(url, timeout=8).read()
    (destination / (coordinate.replace(':', '_') + '.pom')).write_bytes(data)
    root = ET.fromstring(data)
    licenses = [{'name': x.findtext('m:name', '', NS), 'url': x.findtext('m:url', '', NS)} for x in root.findall('m:licenses/m:license', NS)]
    chain = [{'coordinate': coordinate, 'url': url, 'sha256': digest(data)}]
    if not licenses:
        parent = root.find('m:parent', NS)
        if parent is not None:
            parent_id = ':'.join(parent.findtext('m:' + k, '', NS) for k in ['groupId', 'artifactId', 'version'])
            _, licenses, inherited = pom(parent_id, destination, seen)
            chain += inherited
    return root, licenses, chain


def embedded(data, path=''):
    found = []
    with zipfile.ZipFile(io.BytesIO(data)) as archive:
        for name in sorted(archive.namelist()):
            if name.endswith('/'):
                continue
            if re.search(r'(^|/)(?:[^/]*(?:licen[sc]e|notice|copyright)[^/]*)$', name, re.I):
                value = archive.read(name)
                found.append({'path': path + name, 'sha256': digest(value), 'text': value.decode('utf-8', errors='replace')})
            elif name.endswith('.jar'):
                found += embedded(archive.read(name), path + name + '!/')
    return found


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--inputs', type=Path, default=Path('build/release-audit-inputs.json'))
    parser.add_argument('--fetch-sources', action='store_true')
    args = parser.parse_args()
    inputs = json.loads(args.inputs.read_text())
    output = Path('docs/release')
    output.mkdir(parents=True, exist_ok=True)
    metadata = output / 'dependency-poms'
    metadata.mkdir(exist_ok=True)
    source_dir = Path('build/corresponding-dependency-sources')
    source_dir.mkdir(parents=True, exist_ok=True)
    rows = []
    exceptions = []
    def inspect(coordinate):
        exceptions = []
        try:
            root, licenses, chain = pom(coordinate, metadata)
        except Exception as error:
            licenses, chain = [], []
            root = ET.Element('project')
            exceptions.append(coordinate + ': metadata unavailable (' + type(error).__name__ + ')')
        apache = bool(licenses) and all('apache' in (x['name'] + x['url']).lower() for x in licenses)
        if not apache:
            exceptions.append(coordinate + ': manual licence review required')
        artifacts = []
        seen_files = set()
        for artifact in inputs['artifacts']:
            if artifact['coordinate'] != coordinate:
                continue
            file = Path(artifact['file'])
            if str(file) in seen_files:
                continue
            seen_files.add(str(file))
            data = file.read_bytes()
            with zipfile.ZipFile(io.BytesIO(data)) as archive:
                native = [x for x in archive.namelist() if x.endswith('.so')]
            artifacts.append({'filename': file.name, 'url': origin(coordinate, file.suffix), 'sha256': digest(data),
                              'bytes': len(data), 'native_libraries': native, 'legal_files': embedded(data)})
        source = {'url': origin(coordinate, '-sources.jar'), 'status': 'no-runtime-artifact' if not artifacts else 'not-fetched'}
        if args.fetch_sources and artifacts:
            source_path = source_dir / (coordinate.replace(':', '_') + '-sources.jar')
            try:
                candidates = sorted((CACHE / Path(*coordinate.split(':'))).glob('*/*-sources.jar'))
                if source_path.exists():
                    data = source_path.read_bytes()
                elif candidates:
                    data = candidates[0].read_bytes()
                else:
                    result = subprocess.run(['curl', '--fail', '--location', '--silent', '--show-error', '--http1.1', '--retry', '1', '--max-time', '30', source['url']], check=True, capture_output=True)
                    data = result.stdout
                with zipfile.ZipFile(io.BytesIO(data)) as archive:
                    assert archive.testzip() is None
                    source['entries'] = len(archive.namelist())
                    source['legal_files'] = embedded(data)
                source_path.write_bytes(data)
                source.update(status='retrieved', sha256=digest(data), filename=source_path.name)
            except Exception as error:
                source['status'] = 'unavailable-' + type(error).__name__
                exceptions.append(coordinate + ': matching source archive unavailable')
        row = {'coordinate': coordinate, 'spdx': 'Apache-2.0' if apache else 'NOASSERTION', 'licenses': licenses,
                     'metadata_chain': chain, 'project_url': root.findtext('m:url', '', NS),
                     'artifacts': artifacts, 'sources': source}
        print('Audited ' + coordinate, flush=True)
        return row, exceptions

    coordinates = [x for x in inputs['modules'] if not x.endswith(':unspecified')]
    with ThreadPoolExecutor(max_workers=4) as pool:
        for row, problems in pool.map(inspect, coordinates):
            rows.append(row)
            exceptions.extend(problems)
    report = {'configuration': ':app:releaseRuntimeClasspath', 'modules': rows, 'exceptions': sorted(set(exceptions)),
              'scope': 'Resolved artifacts, nested JAR legal files, POM licences including parents, source JAR legal files. Metadata is evidence, not an authorship or trademark clearance.'}
    (output / 'runtime-dependencies.json').write_text(json.dumps(report, indent=2, ensure_ascii=False) + '\n')
    lines = ['# Resolved release dependency audit', '', 'Generated from `:app:releaseRuntimeClasspath`; regenerate after any dependency change.', '',
             '| Exact component | Licence | Runtime artifacts | Matching sources |', '|---|---|---|---|']
    for row in rows:
        lines.append('| ' + row['coordinate'] + ' | ' + row['spdx'] + ' | ' + str(len(row['artifacts'])) + ' | ' + row['sources']['status'] + ' |')
    lines += ['', '## Exceptions', ''] + (['- ' + x for x in sorted(set(exceptions))] or ['None in this metadata/source-availability pass. See the separate provenance and native-library review.'])
    (output / 'RUNTIME_DEPENDENCIES.md').write_text('\n'.join(lines) + '\n')
    notices = ['Third-party runtime dependencies', '', 'These components retain their own licences; the project GPL does not replace them.',
               'Full Apache-2.0 terms are available separately in this app. Exact artifacts and source hashes are in the release source audit.', '']
    for row in rows:
        notices += [row['coordinate'], row['spdx'], row['project_url']]
        for artifact in row['artifacts']:
            for legal in artifact['legal_files']:
                notices += [artifact['filename'] + '!/' + legal['path'], legal['text']]
        for legal in row['sources'].get('legal_files', []):
            notices += ['Source archive: ' + legal['path'], legal['text']]
        notices += ['']
    Path('app/src/main/assets/legal/dependencies.txt').write_text('\n'.join(notices) + '\n')
    print(str(len(rows)) + ' external components; ' + str(sum(len(x['artifacts']) for x in rows)) + ' artifacts; ' + str(len(set(exceptions))) + ' exceptions')
    for problem in sorted(set(exceptions)):
        print(problem)
    return 1 if exceptions else 0


if __name__ == '__main__':
    raise SystemExit(main())
