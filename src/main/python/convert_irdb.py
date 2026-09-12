#!/usr/bin/env python3
"""
Convert Flipper-IRDB into Controlix's bundled SQLite database.

Usage:
    python convert_irdb.py <flipper-irdb-root> <output.db>

Reads the category/brand tree (TVs/, ACs/, Fans/, ...) and writes a single
SQLite file per the schema in docs/DB.md.

Button payload encoding:
  * raw entries  -> BLOB of little-endian int32 microsecond durations.
                    Carrier comes from the file's `frequency:` field.
  * parsed entries -> BLOB of a fixed 12-byte header:
                        byte 0   protocol id (see PROTO_IDS)
                        byte 1   reserved
                        bytes 2-3 reserved
                        bytes 4-7  address (4 LE bytes as stored by Flipper)
                        bytes 8-11 command (4 LE bytes as stored by Flipper)
                    The app expands this into timings with its Kotlin encoders
                    (same ones covered by ProtocolEncoderTest).
"""
import sys
import os
import struct
import sqlite3
import pathlib

# Categories that are generated artifacts, not real data.
SKIP_DIRS = {'_Converted_', '.git', '.github'}

PROTO_IDS = {
    'NEC': 1,
    'NECext': 2,
    'NEC42': 3,
    'NEC42ext': 4,
    'Samsung32': 5,
    'RC5': 6,
    'RC5X': 7,
    'RC6': 8,
    'SIRC': 9,
    'SIRC15': 10,
    'SIRC20': 11,
    'Kaseikyo': 12,
    'RCA': 13,
    'Pioneer': 14,
    'JVC': 15,
}

# Protocol -> default carrier (Hz) when a parsed entry does not state one.
PROTO_CARRIER = {
    'NEC': 38000, 'NECext': 38000, 'NEC42': 38000, 'NEC42ext': 38000,
    'Samsung32': 38000, 'RC5': 36000, 'RC5X': 36000, 'RC6': 36000,
    'SIRC': 40000, 'SIRC15': 40000, 'SIRC20': 40000,
    'Kaseikyo': 37000, 'RCA': 56000, 'Pioneer': 40000,
}


def parse_ir_file(path):
    """Parse one Flipper .ir file -> list of button dicts."""
    try:
        text = pathlib.Path(path).read_text(encoding='utf-8', errors='ignore')
    except OSError:
        return []

    buttons = []
    cur = None
    collecting_data = False
    data_lines = []

    def flush():
        if cur and cur.get('name'):
            if collecting_data or 'data' in cur:
                cur['data_str'] = ' '.join(data_lines).strip()
            buttons.append(cur)

    for raw in text.splitlines():
        line = raw.strip()
        if not line or line.startswith('#'):
            continue
        if line.startswith('Filetype:') or line.startswith('Version:'):
            continue

        if line.startswith('name:'):
            flush()
            cur = {'name': line.split(':', 1)[1].strip()}
            collecting_data = False
            data_lines = []
            continue

        if cur is None:
            continue

        if line.startswith('type:'):
            cur['type'] = line.split(':', 1)[1].strip()
        elif line.startswith('protocol:'):
            cur['protocol'] = line.split(':', 1)[1].strip()
        elif line.startswith('address:'):
            toks = line.split(':', 1)[1].split()
            try:
                cur['address'] = bytes(int(t, 16) for t in toks[:4]).ljust(4, b'\x00')
            except ValueError:
                pass
        elif line.startswith('command:'):
            toks = line.split(':', 1)[1].split()
            try:
                cur['command'] = bytes(int(t, 16) for t in toks[:4]).ljust(4, b'\x00')
            except ValueError:
                pass
        elif line.startswith('frequency:'):
            try:
                cur['frequency'] = int(line.split(':', 1)[1].strip())
            except ValueError:
                pass
        elif line.startswith('duty_cycle:'):
            pass
        elif line.startswith('data:'):
            collecting_data = True
            data_lines = [line.split(':', 1)[1].strip()]
        elif collecting_data:
            # Continuation lines of a raw data block.
            if line.startswith(('name:', 'type:', 'protocol:')):
                continue
            data_lines.append(line)

    flush()
    return buttons


def parse_models_from_header(path):
    """
    Flipper .ir files carry a header comment listing compatible models, e.g.
        # Compatible TV Models:
        # - Samsung UE32F5000
        # - UE40F6000
    Returns a list of model strings (may be empty).
    """
    models = []
    try:
        with open(path, 'r', encoding='utf-8', errors='ignore') as fh:
            in_model_block = False
            for _ in range(60):  # header is always near the top
                line = fh.readline()
                if not line:
                    break
                low = line.strip().lower()
                if not in_model_block:
                    if low.startswith('# compatible') and 'model' in low:
                        after = line.split(':', 1)[1] if ':' in line else ''
                        import re as _re
                        models.extend(m for m in _re.split(r'[\s,]+', after.strip()) if m)
                        in_model_block = True
                elif low.startswith('# - '):
                    models.append(line.strip()[4:].strip())
                elif low.startswith('#') and models:
                    break
    except OSError:
        pass
    return models


def encode_raw(frequency, data_str):
    try:
        vals = [int(x) for x in data_str.split()]
    except ValueError:
        return None
    if not vals:
        return None
    # Flipper raw arrays are recorded in "pronto raw" style and normally begin
    # with a long silence gap (e.g. 1000000 us). ConsumerIrManager takes a
    # plain on/off sequence starting with a mark, so drop a leading gap.
    if len(vals) > 2 and vals[0] > 100_000:
        vals = vals[1:]
    # ConsumerIrManager needs an even number of on/off entries; pad if odd.
    if len(vals) % 2 == 1:
        vals.append(0)
    return struct.pack(f'<{len(vals)}i', *vals)


def encode_parsed(protocol, address, command):
    proto_id = PROTO_IDS.get(protocol, 0)
    if proto_id == 0:
        return None
    addr = (address or b'\x00\x00\x00\x00')[:4].ljust(4, b'\x00')
    cmd = (command or b'\x00\x00\x00\x00')[:4].ljust(4, b'\x00')
    return struct.pack('<BBBB4s4s', proto_id, 0, 0, 0, addr, cmd)


SCHEMA = """
CREATE TABLE category (
  id         INTEGER PRIMARY KEY,
  slug       TEXT NOT NULL UNIQUE,
  name       TEXT NOT NULL
);
CREATE TABLE brand (
  id          INTEGER PRIMARY KEY,
  category_id INTEGER NOT NULL REFERENCES category(id),
  name        TEXT NOT NULL,
  UNIQUE(category_id, name)
);
CREATE TABLE remote (
  id          INTEGER PRIMARY KEY,
  brand_id    INTEGER NOT NULL REFERENCES brand(id),
  file_name   TEXT NOT NULL,
  model_name  TEXT,
  source      TEXT NOT NULL,
  source_path TEXT,
  UNIQUE(brand_id, file_name)
);
CREATE TABLE remote_model (
  remote_id INTEGER NOT NULL REFERENCES remote(id),
  model     TEXT NOT NULL,
  PRIMARY KEY (remote_id, model)
);
CREATE TABLE button (
  id         INTEGER PRIMARY KEY,
  remote_id  INTEGER NOT NULL REFERENCES remote(id),
  name       TEXT NOT NULL,
  carrier_hz INTEGER NOT NULL,
  pattern    BLOB NOT NULL,
  protocol   TEXT,
  UNIQUE(remote_id, name)
);
CREATE INDEX idx_remote_model ON remote_model(model);
CREATE INDEX idx_brand_cat ON brand(category_id);
CREATE INDEX idx_remote_brand ON remote(brand_id);
CREATE INDEX idx_button_remote ON button(remote_id);
"""


def main():
    if len(sys.argv) < 3:
        print(__doc__)
        return 1

    root = pathlib.Path(sys.argv[1])
    out = sys.argv[2]
    if not root.is_dir():
        print(f'error: {root} is not a directory')
        return 1
    if os.path.exists(out):
        os.remove(out)

    conn = sqlite3.connect(out)
    conn.executescript(SCHEMA)

    stats = {'categories': 0, 'brands': 0, 'remotes': 0, 'buttons': 0,
             'raw': 0, 'parsed': 0, 'skipped_proto': 0, 'models': 0}

    for cat_dir in sorted(root.iterdir()):
        if not cat_dir.is_dir() or cat_dir.name in SKIP_DIRS:
            continue
        cat_name = cat_dir.name.replace('_', ' ')
        cur = conn.execute('INSERT INTO category (slug, name) VALUES (?, ?)',
                           (cat_dir.name.lower(), cat_name))
        cat_id = cur.lastrowid
        stats['categories'] += 1
        cat_brands = cat_remotes = cat_buttons = 0

        for brand_dir in sorted(cat_dir.iterdir()):
            if not brand_dir.is_dir() or brand_dir.name.startswith('.'):
                continue
            cur = conn.execute('INSERT INTO brand (category_id, name) VALUES (?, ?)',
                               (cat_id, brand_dir.name))
            brand_id = cur.lastrowid
            stats['brands'] += 1
            cat_brands += 1

            for ir_file in sorted(brand_dir.rglob('*.ir')):
                buttons = parse_ir_file(ir_file)
                if not buttons:
                    continue
                model = ir_file.stem.replace('_', ' ')
                cur = conn.execute(
                    'INSERT INTO remote (brand_id, file_name, model_name, source, source_path)'
                    ' VALUES (?, ?, ?, ?, ?)',
                    (brand_id, ir_file.name, model, 'flipper',
                     str(ir_file.relative_to(root)).replace('\\', '/')))
                remote_id = cur.lastrowid
                stats['remotes'] += 1
                cat_remotes += 1

                for m in parse_models_from_header(ir_file):
                    conn.execute('INSERT OR IGNORE INTO remote_model (remote_id, model) VALUES (?, ?)',
                                 (remote_id, m))
                    stats['models'] += 1

                for btn in buttons:
                    name = btn.get('name')
                    if not name:
                        continue
                    btype = btn.get('type', '')
                    blob = None
                    carrier = 38000
                    proto = None

                    if btype == 'raw':
                        carrier = btn.get('frequency', 38000)
                        blob = encode_raw(carrier, btn.get('data_str', ''))
                        if blob:
                            stats['raw'] += 1
                    elif btype == 'parsed':
                        proto = btn.get('protocol', '')
                        carrier = btn.get('frequency') or PROTO_CARRIER.get(proto, 38000)
                        blob = encode_parsed(proto, btn.get('address'), btn.get('command'))
                        if blob:
                            stats['parsed'] += 1
                        else:
                            stats['skipped_proto'] += 1
                    if not blob:
                        continue

                    conn.execute(
                        'INSERT OR IGNORE INTO button (remote_id, name, carrier_hz, pattern, protocol)'
                        ' VALUES (?, ?, ?, ?, ?)',
                        (remote_id, name, carrier, blob, proto))
                    stats['buttons'] += 1
                    cat_buttons += 1

        print(f'{cat_name:<26} brands={cat_brands:<5} remotes={cat_remotes:<5} buttons={cat_buttons}')

    conn.commit()
    conn.execute('VACUUM')
    conn.close()

    size_mb = os.path.getsize(out) / (1024 * 1024)
    print('\n=== totals ===')
    for k, v in stats.items():
        print(f'{k:<16} {v}')
    print(f'size_mb          {size_mb:.1f}')
    return 0


if __name__ == '__main__':
    sys.exit(main())
