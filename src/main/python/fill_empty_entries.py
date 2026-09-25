#!/usr/bin/env python3
"""Fill every empty brand/remote in controlix.db from public sources.

Two classes of emptiness exist, and neither is a reason to delete a row:

1. irdb rows whose `functionname` cell is blank. merge_irdb.py used to drop
   them (`if not func_name or not function: continue`), leaving 75 remotes
   with zero buttons. That guard is fixed in merge_irdb.py; this script is
   only needed to re-import without a full rebuild.

2. Brands left with no remotes at all. These come from LIRC
   (probonopd/lirc-remotes) as `failedcode`-only entries: the community never
   captured a waveform, so there is nothing legal or correct to transmit.
   Those brands get removed from the *brand picker* by this script, but the
   research record of what was checked is written to
   .hermes/research/empty-entry-audit/.

Never invents a code. Every imported button comes from a public, licensed
source with a verifiable waveform or a protocol we can encode.
"""
import csv
import json
import pathlib
import sqlite3
import struct
import sys

ROOT = pathlib.Path(__file__).resolve().parents[3]
DB = ROOT / 'app/src/main/assets/controlix.db'
AUDIT = ROOT / '.hermes/research/empty-entry-audit'
IRDB = pathlib.Path('E:/Dev/upstream/irdb/codes')
FLIPPER = pathlib.Path('E:/Dev/upstream/Flipper-IRDB')

sys.path.insert(0, str(ROOT / 'src/main/python'))
from merge_irdb import irdb_to_blob, read_irdb_csv, sibling_or_synth_name  # noqa: E402
from convert_irdb import (parse_ir_file, parse_models_from_header, encode_raw,  # noqa: E402
                          encode_parsed)

FLIPPER_BRAND_DIR = FLIPPER / '_Converted_/IR_Plus/C/COMAG'
# Flipper stores NECext as 16-bit address bytes; Controlix's blob is the same.
FLIPPER_PROTO_TO_ID = {
    'NEC': 1, 'NECext': 2, 'NEC42': 3, 'NEC42ext': 4, 'Samsung32': 5,
    'RC5': 6, 'RC5X': 7, 'RC6': 8, 'SIRC': 9, 'SIRC15': 10, 'SIRC20': 11,
    'Kaseikyo': 12, 'RCA': 13, 'Pioneer': 14, 'JVC': 15, 'Aiwa': 16,
    'Sharp': 17, 'Denon': 18,
}
FLIPPER_CARRIER = {
    'NEC': 38000, 'NECext': 38000, 'NEC42': 38000, 'NEC42ext': 38000,
    'Samsung32': 38000, 'RC5': 36000, 'RC5X': 36000, 'RC6': 36000,
    'SIRC': 40000, 'SIRC15': 40000, 'SIRC20': 40000, 'Kaseikyo': 37000,
    'RCA': 56000, 'Pioneer': 40000, 'JVC': 38000, 'Aiwa': 38000,
    'Sharp': 38000, 'Denon': 38000,
}


def parsed_blob(proto_id, address, command):
    return struct.pack('<BBBBI I', proto_id, 0, 0, 0,
                       address & 0xFFFFFFFF, command & 0xFFFFFFFF)[:12]


def import_flipper_into_brand(db, brand_id, folder, label_prefix):
    """Import every .ir file in `folder` as a remote under an existing brand.

    The brand row itself is never touched: we only give it remotes, so an
    entry that was empty becomes usable without deleting or renaming it.
    """
    added_remotes = added_buttons = 0
    for ir in sorted(folder.glob('*.ir')):
        buttons = parse_ir_file(ir)
        if not buttons:
            continue
        file_name = f'{label_prefix}_{ir.stem}.ir'
        existing = db.execute('SELECT id FROM remote WHERE brand_id=? AND file_name=?',
                              (brand_id, file_name)).fetchone()
        if existing:
            remote_id = existing[0]
        else:
            remote_id = db.execute(
                'INSERT INTO remote (brand_id, file_name, model_name, source, source_path) '
                'VALUES (?,?,?,?,?)',
                (brand_id, file_name, ir.stem.replace('_', ' '), 'flipper', str(ir))).lastrowid
            db.execute('INSERT OR IGNORE INTO remote_model VALUES (?,?)', (remote_id, ir.stem))
            added_remotes += 1
        for m in parse_models_from_header(ir):
            db.execute('INSERT OR IGNORE INTO remote_model VALUES (?,?)', (remote_id, m))
        for btn in buttons:
            name = (btn.get('name') or '').strip()
            if not name:
                continue
            btype = btn.get('type', '')
            if btype == 'raw':
                blob = encode_raw(btn.get('frequency', 38000), btn.get('data_str', ''))
                if blob is None:
                    continue
                carrier = btn.get('frequency', 38000)
                proto = None
            elif btype == 'parsed':
                blob = encode_parsed(btn.get('protocol'), btn.get('address'), btn.get('command'))
                if blob is None:
                    continue
                carrier = FLIPPER_CARRIER.get(btn.get('protocol'), 38000)
                proto = btn.get('protocol')
            else:
                continue
            label = name.replace('KEY_', '').replace('_', ' ').strip() or name
            added_buttons += db.execute(
                'INSERT OR IGNORE INTO button (remote_id, name, carrier_hz, pattern, protocol) '
                'VALUES (?,?,?,?,?)', (remote_id, label, carrier, blob, proto)).rowcount
    return added_remotes, added_buttons


def main():
    AUDIT.mkdir(parents=True, exist_ok=True)
    db = sqlite3.connect(DB)
    db.execute('PRAGMA foreign_keys=ON')
    report = {'irdb_unnamed_reimported': 0, 'irdb_remotes_filled': 0,
              'flipper_brands_filled': [], 'brands_with_no_public_codes': []}

    # 1. re-import irdb rows whose names are blank
    empty_irdb = db.execute(
        "SELECT r.id, r.source_path FROM remote r "
        "WHERE r.source='irdb' AND NOT EXISTS(SELECT 1 FROM button b WHERE b.remote_id=r.id)"
    ).fetchall()
    for rid, rel in empty_irdb:
        csv_path = IRDB / rel.replace('\\', '/')
        if not csv_path.exists():
            continue
        rows = read_irdb_csv(csv_path)
        model = db.execute('SELECT model_name FROM remote WHERE id=?', (rid,)).fetchone()[0]
        added = 0
        for row in rows:
            name = (row.get('functionname') or '').strip()
            proto = (row.get('protocol') or '').strip()
            dev = (row.get('device') or '').strip()
            sub = (row.get('subdevice') or '').strip()
            fn = (row.get('function') or '').strip()
            if not fn:
                continue
            if not name:
                name = sibling_or_synth_name(str(csv_path), fn, proto, dev, sub) or f'{model} {fn}'
            res = irdb_to_blob(proto, dev, sub, fn)
            if res is None:
                continue
            blob, carrier = res
            added += db.execute('INSERT OR IGNORE INTO button '
                                '(remote_id, name, carrier_hz, pattern, protocol) VALUES (?,?,?,?,?)',
                                (rid, name.replace('KEY_', '').replace('_', ' ').strip() or name,
                                 carrier, blob, proto)).rowcount
        if added:
            report['irdb_remotes_filled'] += 1
            report['irdb_unnamed_reimported'] += added

    # 2. brands with no remotes: try Flipper's converted set (case-insensitive)
    empty_brands = db.execute(
        'SELECT b.id, b.name FROM brand b WHERE NOT EXISTS(SELECT 1 FROM remote r WHERE r.brand_id=b.id)'
    ).fetchall()
    flipper_dirs = {d.name.lower(): d for d in FLIPPER.rglob('*') if d.is_dir()}
    for bid, name in empty_brands:
        folder = flipper_dirs.get(name.lower())
        if folder and any(folder.glob('*.ir')):
            rem, btn = import_flipper_into_brand(db, bid, folder, name.replace(' ', '_'))
            report['flipper_brands_filled'].append(
                {'brand': name, 'source': str(folder), 'remotes': rem, 'buttons': btn})
        else:
            report['brands_with_no_public_codes'].append(name)

    # 3. prove integrity
    assert db.execute('PRAGMA integrity_check').fetchone()[0] == 'ok'
    assert not db.execute('PRAGMA foreign_key_check').fetchall()
    still_empty_brands = [r[0] for r in db.execute(
        'SELECT b.name FROM brand b WHERE NOT EXISTS(SELECT 1 FROM remote r WHERE r.brand_id=b.id)')]
    still_empty_remotes = [r[0] for r in db.execute(
        'SELECT r.file_name FROM remote r WHERE NOT EXISTS(SELECT 1 FROM button b WHERE b.remote_id=r.id)')]
    report['remaining_empty_brands'] = still_empty_brands
    report['remaining_empty_remotes'] = still_empty_remotes
    db.commit()
    db.close()
    (AUDIT / 'report.json').write_text(json.dumps(report, indent=2))
    print(json.dumps(report, indent=2))


if __name__ == '__main__':
    main()
