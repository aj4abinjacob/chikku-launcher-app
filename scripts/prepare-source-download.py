#!/usr/bin/env python3
"""Stage a public source download locally. Never deploys or changes repository visibility."""
import argparse
import gzip
import hashlib
import html
import io
import json
from pathlib import Path
import tarfile

HISTORICAL_ART = {
    'design/branding/' + name for name in (
        'chikku-app-icon-concept-v1.png', 'chikku-app-icon-concept-v1.prompt.txt',
        'chikku-app-icon-minimal-v3.png', 'chikku-app-icon-minimal-v3.prompt.txt',
        'chikku-app-icon-portrait-v2.png', 'chikku-app-icon-portrait-v2.prompt.txt',
        'chikku-app-icon-soft-v4.png', 'chikku-app-icon-soft-v4.prompt.txt',
        'chikku-icon-mask-preview.png', 'chikku-play-icon-512.png',
        'chikku-tail-c-master-v1.png', 'chikku-tail-c-v1.prompt.txt',
    )
}


def digest(data):
    return hashlib.sha256(data).hexdigest()


def stage(manifest_path, candidate_path, output, exclude_historical=False, part_bytes=20 * 1024 * 1024):
    source = json.loads(manifest_path.read_text())
    candidate = json.loads(candidate_path.read_text())
    revision = source['revision']
    if len(revision) != 40 or any(c not in '0123456789abcdef' for c in revision):
        raise ValueError('Invalid revision')
    if candidate['source_revision'] != revision:
        raise ValueError('Candidate/source revision mismatch')
    archive = Path(candidate['source_archive']['path'])
    if digest(archive.read_bytes()) != source['archive_sha256']:
        raise ValueError('Source archive hash mismatch')
    if not 1 <= part_bytes <= 20 * 1024 * 1024:
        raise ValueError('Parts must be at most20 MiB')
    release = output / revision[:12]
    if release.exists():
        raise ValueError('Use a fresh staging directory; never overwrite a release')
    release.mkdir(parents=True)
    prefix = 'chikku-source-' + revision[:12] + '/'
    exclusions = []
    # These are historical design explorations, not inputs to the selected nose SVG or app build.
    # The private repository and original full archive are retained unchanged.
    with tarfile.open(archive) as incoming:
        for member in incoming:
            if member.isdir() and member.name.rstrip('/') == prefix.rstrip('/'):
                continue
            parts = Path(member.name).parts
            if '..' in parts or member.name.startswith('/') or member.issym() or member.islnk():
                raise ValueError('Unsafe source archive member')
            relative = member.name.removeprefix(prefix)
            if not member.name.startswith(prefix):
                raise ValueError('Unexpected archive root')
            if exclude_historical and member.isfile() and relative in HISTORICAL_ART:
                data = incoming.extractfile(member).read()
                exclusions.append({'path': relative, 'sha256': digest(data),
                                   'reason': 'Unused historical branding exploration; rights unrecorded; not a build input'})
    filename = 'chikku-source-' + revision[:12] + '.tar.gz'
    prepared = release / filename
    if exclusions:
        excluded = {prefix + item['path'] for item in exclusions}
        # Fixed gzip and tar metadata makes this derivative reproducible from the recorded full archive.
        with prepared.open('wb') as raw, gzip.GzipFile(fileobj=raw, mode='wb', filename='', mtime=0) as gz:
            with tarfile.open(fileobj=gz, mode='w') as outgoing, tarfile.open(archive) as incoming:
                for member in incoming:
                    if member.name not in excluded:
                        outgoing.addfile(member, incoming.extractfile(member) if member.isfile() else None)
                notice = json.dumps({'source_revision': revision, 'source_revision_kind': source.get('revision_kind', 'git-commit'),
                    'base_revision': source.get('base_revision', revision), 'base_tree': source['tree'],
                    'original_full_archive_sha256': source['archive_sha256'], 'excluded_files': exclusions,
                    'scope': 'App, core, build, current artwork and dependency sources unchanged; only listed unused historical art excluded'}, indent=2).encode()
                member = tarfile.TarInfo(prefix + 'SOURCE_EXPORT.json')
                member.size = len(notice)
                member.mode = 0o644
                outgoing.addfile(member, io.BytesIO(notice))
    else:
        prepared.write_bytes(archive.read_bytes())
    with tarfile.open(prepared) as checked:
        names = set(checked.getnames())
        required = ['gradlew', 'LICENSE', 'NOTICE.md', 'app/build.gradle.kts',
                    'design/branding/nose-notch-ink/master.svg',
                    'app/src/main/assets/weather/cities.index',
                    'data/weather/geonames-2026-10-15/cities500.zip', 'scripts/build-weather-cities.py']
        if any(prefix + name not in names for name in required):
            raise ValueError('Required source/build/asset input missing')
        if not checked.getmember(prefix + 'gradlew').mode & 0o111:
            raise ValueError('Executable wrapper mode missing')
    parts = []
    with prepared.open('rb') as stream:
        while data := stream.read(part_bytes):
            name = filename + '.part' + str(len(parts) + 1).zfill(2)
            (release / name).write_bytes(data)
            parts.append({'file': name, 'bytes': len(data), 'sha256': digest(data)})
    metadata = {'application': 'Chikku Launcher', 'version_name': candidate['version_name'],
                'version_code': candidate['version_code'], 'source_revision': revision,
                'source_tree': source['tree'], 'source_revision_kind': source.get('revision_kind', 'git-commit'),
                'source_base_revision': source.get('base_revision', revision), 'archive_file': filename,
                'archive_bytes': prepared.stat().st_size, 'archive_sha256': digest(prepared.read_bytes()),
                'original_full_archive_sha256': source['archive_sha256'],
                'profile': 'Corresponding Source with explicit non-build historical-art exclusions' if exclusions else 'Full reviewed source archive',
                'excluded_files': exclusions, 'parts': parts,
                'candidate_aab_sha256': candidate['aab']['sha256'],
                'status': 'Test candidate; no production release or repository-publication claim',
                'licence': 'GPL-3.0-or-later project code; third-party licences/notices retained',
                'build': 'JDK21+, Android SDK platform37/build-tools36.0.0; set sdk.dir in local.properties; ./gradlew assembleDebug. Release signing uses your own external key. See README.md and docs/release/SOURCE_DELIVERY.md.'}
    (release / 'manifest.json').write_text(json.dumps(metadata, indent=2) + '\n')
    sums = '\n'.join(item['sha256'] + '  ' + item['file'] for item in parts)
    sums += '\n' + metadata['archive_sha256'] + '  ' + filename + '\n'
    (release / 'SHA256SUMS.txt').write_text(sums)
    (release / 'REBUILD.txt').write_text(metadata['build'] + '\n\n'
        'Download all parts into one directory. Join them in numerical order:\n'
        'macOS/Linux: cat ' + filename + '.part* > ' + filename + '\n'
        'PowerShell: $out=[IO.File]::Create("' + filename + '"); try { Get-ChildItem "' + filename + '.part*" | Sort-Object Name | ForEach-Object { $bytes=[IO.File]::ReadAllBytes($_.FullName); $out.Write($bytes,0,$bytes.Length) } } finally { $out.Dispose() }\n'
        'Verify SHA-256 against SHA256SUMS.txt before extracting with tar -xzf.\n'
        'An exact-byte reproducible binary is not claimed. No signing secret is supplied.\n'
        'The manifest lists every deliberate non-build historical-art exclusion; all app/core/build and current-branding inputs are retained.\n')
    # The whole file is assembled by the browser; no individual hosted asset exceeds the static limit.
    prepared.unlink()
    links = ''.join('<li><a download href="' + revision[:12] + '/' + html.escape(item['file']) + '">Part '
                    + str(i + 1) + '</a></li>' for i, item in enumerate(parts))
    output.joinpath('index.html').write_text('''<!doctype html><html lang="en"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1"><title>Chikku Launcher source downloads</title><style>body{font:18px/1.6 system-ui,sans-serif;max-width:800px;margin:2rem auto;padding:0 1rem;color:#20262e;background:#fff}h1{line-height:1.2}a{color:#204fb4}button,a.download{min-height:48px;padding:.65rem 1rem;font:inherit}button{background:#204fb4;color:white;border:0;border-radius:.6rem;cursor:pointer}button:disabled{opacity:.6;cursor:wait}code{overflow-wrap:anywhere}details{margin:1.5rem 0}li{margin:.5rem 0}@media(prefers-color-scheme:dark){body{background:#111820;color:#eef3f9}a{color:#9dc2ff}}</style><script src="download.js" defer></script></head><body><main><h1>Chikku Launcher source downloads</h1><p>Source code, build instructions, current artwork, dependency sources and licence notices for Chikku Launcher. No sign-in or payment is required.</p><h2>0.1.0-beta.1 · build 2</h2><p>This is a test candidate. Availability depends on the selected Play test track and Google review; this page does not announce a production release. Project code is GPL-3.0-or-later; third-party licences and credits are included. You may study, modify and redistribute it under those terms.</p><p><button id="download" data-manifest="''' + revision[:12] + '''/manifest.json">Download source archive</button></p><p id="status" role="status" aria-live="polite">The download is assembled and checked in your browser. No data is uploaded.</p><p>Source revision: <code>''' + revision + '''</code></p><details><summary>Checksums, separate parts and build instructions</summary><p><a href="''' + revision[:12] + '''/manifest.json">Manifest and explicit exclusions</a> · <a href="''' + revision[:12] + '''/SHA256SUMS.txt">SHA-256 checksums</a> · <a href="''' + revision[:12] + '''/REBUILD.txt">Build and manual download instructions</a></p><ul>''' + links + '''</ul><p><a href="tools/prepare-source-download.py">Download-page generator source</a> · <a href="tools/source-download-client.js">Browser client source</a> · <a href="tools/GPL-3.0.txt">GPL licence</a></p><p>The browser needs enough memory to join the archive. If that fails, download the parts and follow the manual instructions. General-purpose build tools and Android system libraries are installed separately. The package includes the selected nose artwork's editable SVG and the GeoNames inputs/generator. Unused historical design concepts with unrecorded rights are listed explicitly if excluded.</p></details><p>Before distributing an app build, its exact source must be listed here and linked from the app and download instructions. This page does not grant a Play installation/update signature or declare a candidate accepted.</p><p><a href="/chikku/privacy/">Privacy policy</a> · <a href="/chikku/accessibility/">Optional accessibility demonstration</a></p></main></body></html>''')
    output.joinpath('download.js').write_bytes(Path(__file__).with_name('source-download-client.js').read_bytes())
    tools = output / 'tools'
    tools.mkdir(exist_ok=True)
    tools.joinpath('prepare-source-download.py').write_bytes(Path(__file__).read_bytes())
    tools.joinpath('source-download-client.js').write_bytes(Path(__file__).with_name('source-download-client.js').read_bytes())
    tools.joinpath('GPL-3.0.txt').write_bytes(Path(__file__).resolve().parents[1].joinpath('LICENSE').read_bytes())
    page = output.joinpath('index.html').read_text().replace(
        '0.1.0-beta.1 · build 2', html.escape(str(metadata['version_name'])) + ' · build ' + str(int(metadata['version_code'])))
    if metadata['source_revision_kind'] == 'staged-git-tree':
        page = page.replace('<p>Source revision:', '<p>This source is an exact reviewed staged Git tree; it has not yet been committed. Base commit: <code>' + html.escape(metadata['source_base_revision']) + '</code>.</p><p>Source snapshot tree:')
    # Keep a stable page for every earlier build, even after the source index moves to a new one.
    version_page = page.replace('src="download.js"', 'src="../download.js"')
    version_page = version_page.replace('data-manifest="' + revision[:12] + '/manifest.json"', 'data-manifest="manifest.json"')
    version_page = version_page.replace('href="' + revision[:12] + '/', 'href="')
    version_page = version_page.replace('href="tools/', 'href="../tools/')
    release.joinpath('index.html').write_text(version_page)
    previous = []
    for record in sorted(output.glob('*/manifest.json')):
        if record.parent == release:
            continue
        if len(record.parent.name) != 12 or any(c not in '0123456789abcdef' for c in record.parent.name):
            raise ValueError('Unexpected earlier-release directory')
        item = json.loads(record.read_text())
        previous.append('<li><a href="' + record.parent.name + '/">' + html.escape(str(item['version_name']))
                        + ' · build ' + str(int(item['version_code'])) + ' · ' + record.parent.name + '</a></li>')
    if previous:
        page = page.replace('</main>', '<h2>Earlier source versions</h2><ul>' + ''.join(previous) + '</ul></main>')
    output.joinpath('index.html').write_text(page)
    return metadata


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--source-manifest', type=Path, required=True)
    parser.add_argument('--candidate-manifest', type=Path, required=True)
    parser.add_argument('--output', type=Path, required=True)
    parser.add_argument('--exclude-historical-art', action='store_true')
    args = parser.parse_args()
    result = stage(args.source_manifest, args.candidate_manifest, args.output, args.exclude_historical_art)
    print(json.dumps({key: result[key] for key in ('source_revision', 'archive_bytes', 'archive_sha256', 'profile', 'excluded_files')}, indent=2))


if __name__ == '__main__':
    main()
