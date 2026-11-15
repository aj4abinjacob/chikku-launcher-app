#!/usr/bin/env python3
"""Deterministically derive the bundled offline city index from the recorded GeoNames dump."""
import gzip
import hashlib
import io
import json
from pathlib import Path
import unicodedata
import zipfile

ROOT = Path(__file__).resolve().parents[1]
SOURCE = ROOT / 'data/weather/geonames-2026-10-15'
OUTPUT = ROOT / 'app/src/main/assets/weather/cities.index'

def normalize(value):
    return ' '.join(''.join(c for c in unicodedata.normalize('NFKD', value.lower())
                            if not unicodedata.category(c).startswith('M')).replace(',', ' ').split())

def main():
    countries = {}
    for line in (SOURCE / 'countryInfo.txt').read_text().splitlines():
        if line and not line.startswith('#'):
            fields = line.split('\t')
            countries[fields[0]] = fields[4]
    admins = {f[0]: f[1] for line in (SOURCE / 'admin1CodesASCII.txt').read_text().splitlines()
              if len(f := line.split('\t')) >= 2}
    rows = []
    with zipfile.ZipFile(SOURCE / 'cities500.zip') as archive:
        for line in io.TextIOWrapper(archive.open('cities500.txt'), encoding='utf-8'):
            f = line.rstrip('\n').split('\t')
            assert len(f) == 19
            name, country = f[1], countries.get(f[8], f[8])
            admin = admins.get(f[8] + '.' + f[10], '')
            label = ', '.join(dict.fromkeys(x for x in (name, admin, country) if x))[:160]
            aliases = sorted(set(normalize(x) for x in [f[1], f[2], *f[3].split(',')] if x))
            # Keep countries/admins searchable without expanding every alias with their names.
            values = [label, f[4], f[5], '|'.join(aliases), normalize(country + ' ' + f[8] + ' ' + admin)]
            assert all('\t' not in x and '\n' not in x and '\r' not in x for x in values)
            rows.append((int(f[14]), int(f[0]), '\t'.join(values)))
    rows.sort(key=lambda row: (-row[0], row[1]))
    data = ('\n'.join(row[2] for row in rows) + '\n').encode()
    OUTPUT.parent.mkdir(parents=True, exist_ok=True)
    with OUTPUT.open('wb') as raw:
        with gzip.GzipFile(filename='', fileobj=raw, mode='wb', mtime=0, compresslevel=9) as zipped:
            zipped.write(data)
    manifest = {'source_date': '2026-10-15', 'entries': len(rows), 'format':
                'UTF-8 TSV: label,latitude,longitude,normalized aliases joined by |,normalized country/code/admin; population descending, GeoNames ID tie-break',
                'inputs': {p.name: hashlib.sha256(p.read_bytes()).hexdigest() for p in sorted(SOURCE.iterdir()) if p.is_file() and p.name != 'manifest.json'},
                'output': str(OUTPUT.relative_to(ROOT)), 'sha256': hashlib.sha256(OUTPUT.read_bytes()).hexdigest()}
    (SOURCE / 'manifest.json').write_text(json.dumps(manifest, indent=2) + '\n')
    print(json.dumps({'entries': len(rows), 'compressed_bytes': OUTPUT.stat().st_size, 'uncompressed_bytes': len(data)}))

if __name__ == '__main__':
    main()
