import hashlib
import importlib.util
import io
import json
from pathlib import Path
import tarfile
import tempfile
import unittest

SPEC = importlib.util.spec_from_file_location('source_download', Path(__file__).parents[1] / 'prepare-source-download.py')
MODULE = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(MODULE)


class SourceDownloadTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)
        self.revision = 'a' * 40
        self.prefix = 'chikku-source-' + self.revision[:12] + '/'
        self.archive = self.root / 'original.tar.gz'
        self.files = ['gradlew', 'LICENSE', 'NOTICE.md', 'app/build.gradle.kts',
                      'design/branding/nose-notch-ink/master.svg',
                      'app/src/main/assets/weather/cities.index',
                      'data/weather/geonames-2026-10-15/cities500.zip', 'scripts/build-weather-cities.py',
                      'design/branding/chikku-app-icon-concept-v1.png',
                      'design/branding/chikku-app-icon-concept-v1.prompt.txt',
                      'design/branding/new-current-art.png',
                      'app/src/main/Example.kt', 'third-party-sources/library-sources.jar']
        self.write_archive()

    def write_archive(self, extra=None):
        with tarfile.open(self.archive, 'w:gz') as archive:
            directory = tarfile.TarInfo(self.prefix.rstrip('/'))
            directory.type = tarfile.DIRTYPE
            archive.addfile(directory)
            for name in self.files + (extra or []):
                data = ('fixture:' + name).encode()
                member = tarfile.TarInfo(self.prefix + name)
                member.size = len(data)
                member.mode = 0o755 if name == 'gradlew' else 0o644
                archive.addfile(member, io.BytesIO(data))
        self.manifest = self.root / 'source.json'
        self.manifest.write_text(json.dumps({'revision': self.revision, 'tree': 'b' * 40,
            'archive_sha256': hashlib.sha256(self.archive.read_bytes()).hexdigest()}))
        self.candidate = self.root / 'candidate.json'
        self.candidate.write_text(json.dumps({'source_revision': self.revision,
            'source_archive': {'path': str(self.archive)}, 'version_name': '0.1.0-beta.1',
            'version_code': 2, 'aab': {'sha256': 'c' * 64}, 'private_key': '/never/export/this'}))

    def prepared(self, excluded=False):
        output = self.root / ('filtered' if excluded else 'full')
        metadata = MODULE.stage(self.manifest, self.candidate, output, excluded, part_bytes=128)
        directory = output / self.revision[:12]
        joined = b''.join((directory / part['file']).read_bytes() for part in metadata['parts'])
        for part in metadata['parts']:
            self.assertEqual(hashlib.sha256((directory / part['file']).read_bytes()).hexdigest(), part['sha256'])
        self.assertEqual(hashlib.sha256(joined).hexdigest(), metadata['archive_sha256'])
        self.assertNotIn('/never/export', (directory / 'manifest.json').read_text())
        return metadata, joined

    def test_full_parts_join_to_exact_original_archive(self):
        metadata, joined = self.prepared()
        self.assertEqual(joined, self.archive.read_bytes())
        self.assertEqual(metadata['excluded_files'], [])

    def test_filtered_export_preserves_all_runtime_build_and_preferred_art_sources(self):
        metadata, joined = self.prepared(True)
        self.assertEqual({x['path'] for x in metadata['excluded_files']},
                         {'design/branding/chikku-app-icon-concept-v1.png',
                          'design/branding/chikku-app-icon-concept-v1.prompt.txt'})
        with tarfile.open(fileobj=io.BytesIO(joined)) as archive:
            for name in self.files:
                if name in {x['path'] for x in metadata['excluded_files']}:
                    self.assertNotIn(self.prefix + name, archive.getnames())
                else:
                    self.assertEqual(archive.extractfile(self.prefix + name).read(), ('fixture:' + name).encode())
            self.assertTrue(archive.getmember(self.prefix + 'gradlew').mode & 0o111)

    def test_staged_snapshot_provenance_is_explicit_in_page_manifest_and_archive(self):
        source = json.loads(self.manifest.read_text())
        source.update(revision_kind='staged-git-tree', base_revision='e' * 40)
        self.manifest.write_text(json.dumps(source))
        metadata, joined = self.prepared(True)
        self.assertEqual(metadata['source_revision_kind'], 'staged-git-tree')
        self.assertEqual(metadata['source_base_revision'], 'e' * 40)
        page = (self.root / 'filtered' / self.revision[:12] / 'index.html').read_text()
        self.assertIn('has not yet been committed', page)
        self.assertIn('Source snapshot tree:', page)
        with tarfile.open(fileobj=io.BytesIO(joined)) as archive:
            provenance = json.load(archive.extractfile(self.prefix + 'SOURCE_EXPORT.json'))
        self.assertEqual(provenance['source_revision_kind'], 'staged-git-tree')
        self.assertEqual(provenance['base_revision'], 'e' * 40)

    def test_tampering_or_candidate_revision_mismatch_is_rejected(self):
        original = self.archive.read_bytes()
        self.archive.write_bytes(original + b'changed')
        with self.assertRaisesRegex(ValueError, 'hash mismatch'):
            MODULE.stage(self.manifest, self.candidate, self.root / 'bad')
        self.archive.write_bytes(original)
        candidate = json.loads(self.candidate.read_text())
        candidate['source_revision'] = 'd' * 40
        self.candidate.write_text(json.dumps(candidate))
        with self.assertRaisesRegex(ValueError, 'revision mismatch'):
            MODULE.stage(self.manifest, self.candidate, self.root / 'bad')

    def test_traversal_is_rejected_before_export(self):
        self.write_archive(['../escape'])
        with self.assertRaisesRegex(ValueError, 'Unsafe'):
            MODULE.stage(self.manifest, self.candidate, self.root / 'bad')

    def test_a_new_release_keeps_the_old_version_page_and_download(self):
        output = self.root / 'history'
        first = MODULE.stage(self.manifest, self.candidate, output)
        first_part = output / self.revision[:12] / first['parts'][0]['file']
        original_part = first_part.read_bytes()
        self.revision = 'd' * 40
        self.prefix = 'chikku-source-' + self.revision[:12] + '/'
        self.write_archive()
        MODULE.stage(self.manifest, self.candidate, output)
        self.assertEqual(first_part.read_bytes(), original_part)
        self.assertIn('aaaaaaaaaaaa/', (output / 'index.html').read_text())
        old_page = (output / 'aaaaaaaaaaaa/index.html').read_text()
        self.assertIn('data-manifest="manifest.json"', old_page)
        self.assertIn('src="../download.js"', old_page)


if __name__ == '__main__':
    unittest.main()
