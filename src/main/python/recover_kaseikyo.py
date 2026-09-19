"""Recover archived empty Kaseikyo profiles from live upstream data, additively.
No guessed protocol aliases. No deletion of categories, brands, remotes or buttons.
"""
import concurrent.futures
import hashlib
import json
import pathlib
import sqlite3
import struct
import sys
import urllib.parse
import urllib.request
from convert_irdb import parse_ir_file

BASE = 'https://raw.githubusercontent.com/Lucaslhm/Flipper-IRDB/main/'
ROOT = pathlib.Path(__file__).resolve().parents[3]
OUT = ROOT / '.hermes/research/kaseikyo-recovery'


def wire(address, command):
    if not 0 <= address <= 0x3ffffff or not 0 <= command <= 0x3ff:
        raise ValueError('Out-of-range Kaseikyo fields')
    vendor = (address >> 8) & 0xffff
    a, b = vendor & 255, vendor >> 8
    parity = (a ^ b) ^ ((a ^ b) >> 4)
    frame = [a, b, (parity & 15) | (((address >> 4) & 15) << 4),
             (address & 15) | ((command & 15) << 4),
             (((address >> 24) & 3) << 6) | (command >> 4)]
    frame.append(frame[2] ^ frame[3] ^ frame[4])
    durations = [3456, 1728]
    for byte in frame:
        for bit in range(8):
            durations.extend([432, 1296 if byte & (1 << bit) else 432])
    durations.extend([432, 0])
    # Independently unpack the emitted frame and verify all source fields.
    decoded = []
    for i in range(6):
        decoded.append(sum((durations[3 + (i * 8 + j) * 2] == 1296) << j for j in range(8)))
    va = decoded[0] | (decoded[1] << 8)
    recovered_a = ((decoded[4] >> 6) << 24) | (va << 8) | ((decoded[2] >> 4) << 4) | (decoded[3] & 15)
    recovered_c = (decoded[3] >> 4) | ((decoded[4] & 63) << 4)
    assert (recovered_a, recovered_c) == (address, command)
    assert decoded[5] == decoded[2] ^ decoded[3] ^ decoded[4]
    return struct.pack('<100i', *durations)


def fetch(row):
    url = BASE + urllib.parse.quote(row['source_path'].replace('\\', '/'), safe='/')
    data = urllib.request.urlopen(url, timeout=40).read()
    path = OUT / (str(row['id']) + '.ir')
    path.write_bytes(data)
    result = []
    for b in parse_ir_file(path):
        if b.get('type') != 'parsed' or b.get('protocol') != 'Kaseikyo':
            raise ValueError('Unexpected entry; skipped entire profile')
        if len(b.get('address', b'')) != 4 or len(b.get('command', b'')) != 4:
            raise ValueError('Missing address/command')
        a, c = int.from_bytes(b['address'], 'little'), int.from_bytes(b['command'], 'little')
        result.append((row['id'], b['name'], 37000, wire(a, c), None))
    if not result:
        raise ValueError('No validated buttons')
    return row, result, {'remote_id': row['id'], 'url': url, 'sha256': hashlib.sha256(data).hexdigest(), 'buttons': len(result)}


def main():
    OUT.mkdir(parents=True, exist_ok=True)
    archive = json.loads((ROOT / '.hermes/research/empty-cleanup/archive-20260917-000719.json').read_text())
    local = pathlib.Path('C:/Users/mrenm/flipper-irdb-scan')
    candidates = [r for r in archive['remotes'] if r['source'] == 'flipper' and
                  {b.get('protocol') for b in parse_ir_file(local / r['source_path'])} == {'Kaseikyo'}]
    results, errors = [], []
    with concurrent.futures.ThreadPoolExecutor(max_workers=6) as pool:
        futures = {pool.submit(fetch, r): r for r in candidates}
        for future in concurrent.futures.as_completed(futures):
            try:
                results.append(future.result())
            except Exception as exc:
                errors.append({'id': futures[future]['id'], 'error': str(exc)})
    db = sqlite3.connect(ROOT / 'app/src/main/assets/controlix.db')
    backup_path = OUT / 'before.db'
    if not backup_path.exists():
        backup = sqlite3.connect(backup_path)
        db.backup(backup)
        backup.close()
    db.execute('PRAGMA foreign_keys=ON')
    added = 0
    with db:
        for row, buttons, proof in sorted(results, key=lambda x: x[0]['id']):
            assert db.execute('SELECT source_path FROM remote WHERE id=?', (row['id'],)).fetchone()[0] == row['source_path']
            for button in buttons:
                added += db.execute('INSERT OR IGNORE INTO button(remote_id,name,carrier_hz,pattern,protocol) VALUES(?,?,?,?,?)', button).rowcount
        assert db.execute('PRAGMA integrity_check').fetchone()[0] == 'ok'
        assert not db.execute('PRAGMA foreign_key_check').fetchall()
    report = {'upstream':'Lucaslhm/Flipper-IRDB (CC0)',
              'reference':'https://github.com/flipperdevices/flipperzero-firmware/tree/dev/lib/infrared/encoder_decoder/kaseikyo',
              'candidate_count':len(candidates), 'recovered_profiles':len(results), 'added_buttons':added,
              'hardware_tested':False, 'errors':errors,
              'sources':[r[2] for r in sorted(results, key=lambda x: x[0]['id'])],
              'empty_remotes_remaining':db.execute('SELECT COUNT(*) FROM remote r WHERE NOT EXISTS(SELECT 1 FROM button b WHERE b.remote_id=r.id)').fetchone()[0]}
    (OUT / 'report.json').write_text(json.dumps(report,indent=2))
    print(json.dumps({k:v for k,v in report.items() if k!='sources'},indent=2))
    db.close()

if __name__ == '__main__':
    main()
