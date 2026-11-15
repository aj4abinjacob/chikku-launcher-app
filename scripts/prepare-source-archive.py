#!/usr/bin/env python3
"""Prepare a verified source archive; does not authorize publication/distribution."""
import argparse
import hashlib
import io
import json
from pathlib import Path
import subprocess
import tarfile


def sha(data):
    return hashlib.sha256(data).hexdigest()


def git(*args):
    return subprocess.check_output(['git', *args])


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--revision', default='HEAD')
    parser.add_argument('--staged-snapshot', action='store_true',
                        help='Export the exact reviewed index tree without creating a commit')
    args = parser.parse_args()
    root = Path(git('rev-parse', '--show-toplevel').decode().strip())
    if Path.cwd().resolve() != root.resolve():
        raise SystemExit('Run from repository root')
    base_revision = git('rev-parse', 'HEAD').decode().strip()
    if args.staged_snapshot:
        if args.revision != 'HEAD':
            raise SystemExit('A staged snapshot cannot select another commit')
        if git('diff', '--name-only') or git('ls-files', '--others', '--exclude-standard'):
            raise SystemExit('Stage every reviewed change; unstaged/untracked files are not allowed')
        revision = git('write-tree').decode().strip()
        revision_kind = 'staged-git-tree'
    else:
        if git('status', '--porcelain'):
            raise SystemExit('A clean reviewed checkout is required')
        revision = git('rev-parse', args.revision + '^{commit}').decode().strip()
        if revision != base_revision:
            raise SystemExit('Check out the requested revision before archiving its dependency report')
        revision_kind = 'git-commit'
    report = json.loads(Path('docs/release/runtime-dependencies.json').read_text())
    if report['exceptions']:
        raise SystemExit('Resolve dependency audit exceptions first')
    sources = Path('build/corresponding-dependency-sources')
    extra = []
    for row in report['modules']:
        if not row['artifacts']:
            continue
        record = row['sources']
        if record['status'] != 'retrieved':
            raise SystemExit('Missing source: ' + row['coordinate'])
        name = record['filename']
        if Path(name).name != name:
            raise SystemExit('Invalid source filename')
        path = sources / name
        if sha(path.read_bytes()) != record['sha256']:
            raise SystemExit('Source hash mismatch: ' + row['coordinate'])
        extra.append((path, 'third-party-sources/' + name))
    native = json.loads(Path('docs/release/graphics-path-native-source.json').read_text())
    for row in native['files']:
        path = sources / 'graphics-path-native-1.0.1' / row['path']
        if '..' in Path(row['path']).parts or sha(path.read_bytes()) != row['sha256']:
            raise SystemExit('Native source hash mismatch')
        extra.append((path, 'third-party-sources/graphics-path-native-1.0.1/' + row['path']))
    prefix = 'chikku-source-' + revision[:12] + '/'
    output = Path('build/release-source')
    output.mkdir(parents=True, exist_ok=True)
    archive_path = output / ('chikku-source-' + revision[:12] + '-PREPARATION.tar.gz')
    project_tar = git('archive', '--format=tar', '--prefix=' + prefix, revision)
    with tarfile.open(fileobj=io.BytesIO(project_tar)) as incoming, tarfile.open(archive_path, 'w:gz') as out:
        for member in incoming:
            out.addfile(member, incoming.extractfile(member) if member.isfile() else None)
        for path, name in extra:
            out.add(path, arcname=prefix + name, recursive=False)
    with tarfile.open(archive_path) as check:
        names = check.getnames()
        required = ['gradlew', 'gradle/wrapper/gradle-wrapper.jar', 'app/build.gradle.kts', 'LICENSE', 'NOTICE.md']
        assert all(prefix + x in names for x in required)
        assert check.getmember(prefix + 'gradlew').mode & 0o111
        for name in names:
            parts = Path(name).parts
            assert '.git' not in parts and '..' not in parts
            assert Path(name).name != 'local.properties'
            assert not name.lower().endswith(('.jks', '.keystore', '.p12', '.pem', '.key'))
        # The archive's project files must match the requested Git blobs, including all deliberate changes.
        for line in git('ls-tree', '-rz', revision).split(b'\0'):
            if not line:
                continue
            meta, name = line.split(b'\t', 1)
            mode, kind, blob = meta.decode().split()
            assert kind == 'blob'
            data = check.extractfile(prefix + name.decode()).read()
            assert hashlib.sha1(b'blob ' + str(len(data)).encode() + b'\0' + data).hexdigest() == blob
    manifest = {'status': 'PREPARATION ONLY - unresolved public distribution gates', 'revision': revision,
                'revision_kind': revision_kind, 'base_revision': base_revision,
                'tree': git('rev-parse', revision + '^{tree}').decode().strip(), 'archive': archive_path.name,
                'archive_sha256': sha(archive_path.read_bytes()), 'dependency_source_count': len(extra),
                'instructions': 'README.md; docs/release/SOURCE_DELIVERY.md; use JDK21+ and SDK37. No signing secrets included.'}
    manifest_path = output / 'source-manifest.json'
    manifest_path.write_text(json.dumps(manifest, indent=2) + '\n')
    print(json.dumps(manifest, indent=2))


if __name__ == '__main__':
    main()
