"""Fill empty brand records from upstream LIRC Pronto waveforms, never aliases."""
import concurrent.futures
import hashlib
import json
import pathlib
import sqlite3
import struct
import urllib.request
import urllib.parse
import xml.etree.ElementTree as ET
from merge_lirc import decode_ccf

ROOT = pathlib.Path(__file__).resolve().parents[3]
OUT = ROOT / '.hermes/research/lirc-empty-brand-recovery'
LOCAL = pathlib.Path('E:/dev/data/lirc-remotes')
BASE = 'https://raw.githubusercontent.com/probonopd/lirc-remotes/xml/'


def fetch(job):
    brand, path = job
    rel = path.relative_to(LOCAL).as_posix()
    url = BASE + urllib.parse.quote(rel, safe='/')
    data = urllib.request.urlopen(url, timeout=30).read()
    dest = OUT / rel
    dest.parent.mkdir(parents=True, exist_ok=True)
    dest.write_bytes(data)
    sets = []
    tree = ET.fromstring(data)
    for remote in tree.findall('remote'):
        buttons = []
        for code in remote.findall('code'):
            text = code.findtext('ccf')
            if not text or not code.get('name'):
                continue
            words = [int(x, 16) for x in text.split()]
            if len(words) < 4 or len(words) != 4 + 2 * (words[2] + words[3]):
                continue
            decoded = decode_ccf(text)
            if decoded is None:
                continue
            hz, durations = decoded
            if len(durations) < 4 or len(durations) % 2 or any(x <= 0 for x in durations) or sum(durations) > 2_000_000:
                continue
            buttons.append((code.get('name').removeprefix('KEY_').replace('_', ' '), hz,
                            struct.pack('<' + 'i' * len(durations), *durations)))
        if buttons:
            sets.append((remote.get('name') or path.stem, buttons))
    return brand, rel, sets, {'brand':brand['name'], 'url':url, 'sha256':hashlib.sha256(data).hexdigest(),
                              'profiles_with_waveforms':len(sets), 'failedcode_count':len(tree.findall('.//failedcode'))}


def main():
    OUT.mkdir(parents=True, exist_ok=True)
    db = sqlite3.connect(ROOT / 'app/src/main/assets/controlix.db')
    db.row_factory = sqlite3.Row
    brands = [dict(r) for r in db.execute('SELECT * FROM brand WHERE NOT EXISTS(SELECT 1 FROM remote WHERE remote.brand_id=brand.id)')]
    jobs = [(b, p) for b in brands for p in (LOCAL / b['name'].lower().replace(' ', '_')).glob('*.xml')]
    results, errors = [], []
    with concurrent.futures.ThreadPoolExecutor(max_workers=6) as pool:
        tasks = {pool.submit(fetch, j): j for j in jobs}
        for future in concurrent.futures.as_completed(tasks):
            try:
                results.append(future.result())
            except Exception as e:
                errors.append({'file':str(tasks[future][1]),'error':str(e)})
    backups = sqlite3.connect(OUT / 'before.db')
    db.backup(backups)
    backups.close()
    db.execute('PRAGMA foreign_keys=ON')
    added, profiles, filled = 0, 0, set()
    with db:
        for brand, rel, sets, proof in results:
            for model, buttons in sets:
                filename = rel + '::' + model
                db.execute('INSERT OR IGNORE INTO remote(brand_id,file_name,model_name,source,source_path) VALUES(?,?,?,?,?)',
                           (brand['id'], filename, model, 'lirc', rel))
                rid = db.execute('SELECT id FROM remote WHERE brand_id=? AND file_name=?', (brand['id'],filename)).fetchone()[0]
                db.execute('INSERT OR IGNORE INTO remote_model VALUES(?,?)', (rid,model))
                for name,hz,blob in buttons:
                    added += db.execute('INSERT OR IGNORE INTO button(remote_id,name,carrier_hz,pattern,protocol) VALUES(?,?,?,?,NULL)', (rid,name,hz,blob)).rowcount
                profiles += 1
                filled.add(brand['name'])
        assert db.execute('PRAGMA integrity_check').fetchone()[0]=='ok'
        assert not db.execute('PRAGMA foreign_key_check').fetchall()
    report = {'source':'probonopd/lirc-remotes; GPL-2.0 collection, original contributor attribution retained in source',
              'brands_searched':len(brands),'files_fetched':len(results),'filled_brands':sorted(filled),
              'new_profiles':profiles,'new_buttons':added,'errors':errors,
              'unresolved_brands':[b['name'] for b in brands if b['name'] not in filled],
              'sources':[r[3] for r in results], 'hardware_tested':False}
    (OUT / 'report.json').write_text(json.dumps(report,indent=2))
    print(json.dumps({k:v for k,v in report.items() if k!='sources'},indent=2))
    db.close()

if __name__ == '__main__':
    main()
