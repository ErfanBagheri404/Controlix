"""Stage upstream recorded captures; never modify the bundled database."""
import hashlib
import json
import pathlib
import re
import sqlite3
import struct
import sys
from functools import reduce
from operator import xor

ROOT = pathlib.Path(__file__).resolve().parent
sys.path.insert(0, str(ROOT.parents[2] / 'src/main/python'))
from convert_irdb import parse_ir_file, encode_raw, SCHEMA

text = (ROOT / 'ir_Whirlpool_test.cpp').read_text()
captures = []
for case, label, note in [
    ('RealExampleDecode', 'Auto_25C_reference_state', 'Auto 25C; fan auto; light on; no power toggle; clock 17:31.'),
    ('RealTimerExample', 'Dry_25C_timer_reference_state', 'Dry 25C; on timer 07:40; off timer 08:05; clock 07:35. Do not expose as generic power.'),
]:
    body = text.split('TEST(TestDecodeWhirlpoolAC, ' + case + ') {', 1)[1].split('\nTEST(', 1)[0]
    match = re.search(r'uint16_t rawData\[(\d+)\] = \{(.*?)\};', body, re.S)
    durations = [int(n) for n in re.findall(r'\d+', match.group(2))]
    assert len(durations) == int(match.group(1)) == 343
    assert min(durations) > 0 and sum(durations) < 2_000_000
    state_match = re.search(r'uint8_t expectedState\[.*?\] = \{(.*?)\};', body, re.S)
    expected = bytes(int(n, 16) for n in re.findall(r'0x([0-9a-fA-F]+)', state_match.group(1)))
    # Decode measured timing independently: header, 6/8/7-byte LSB-first blocks.
    assert 8000 < durations[0] < 10000 and 4000 < durations[1] < 5000
    pos = 2
    decoded = []
    for block in (6, 8, 7):
        for _ in range(block):
            value = 0
            for bit in range(8):
                mark, space = durations[pos:pos+2]
                assert 450 < mark < 750
                assert 400 < space < 750 or 1400 < space < 1900
                value |= (space > 1100) << bit
                pos += 2
            decoded.append(value)
        assert 450 < durations[pos] < 750
        pos += 1
        if block != 7:
            assert 7000 < durations[pos] < 9000
            pos += 1
    assert pos == len(durations)
    assert bytes(decoded) == expected
    assert reduce(xor, decoded[2:13], 0) == decoded[13]
    assert reduce(xor, decoded[14:20], 0) == decoded[20]
    path = ROOT / (case + '.ir')
    path.write_text('Filetype: IR signals file\nVersion: 1\n# Research candidate only; not hardware-tested.\n# ' + note + '\nname: ' + label + '\ntype: raw\nfrequency: 38000\nduty_cycle: 0.500000\ndata: ' + ' '.join(map(str, durations)) + '\n')
    parsed = parse_ir_file(path)
    assert len(parsed) == 1 and parsed[0]['frequency'] == 38000
    blob = encode_raw(38000, parsed[0]['data_str'])
    assert struct.unpack('<344i', blob) == tuple(durations + [0])
    captures.append({'case': case, 'name': label, 'note': note, 'timings': len(durations), 'duration_us': sum(durations), 'state_hex': expected.hex(), 'blob': blob, 'file': path.name})

# Isolated SQLite smoke test using the app converter's real schema.
db = sqlite3.connect(':memory:')
db.executescript(SCHEMA)
db.execute("INSERT INTO category VALUES (1,'acs','ACs')")
db.execute("INSERT INTO brand VALUES (1,1,'Whirlpool')")
db.execute("INSERT INTO remote VALUES (1,1,'Whirlpool_research.ir','DG11J13A reference captures','IRremoteESP8266','test/ir_Whirlpool_test.cpp')")
for c in captures:
    db.execute('INSERT INTO button(remote_id,name,carrier_hz,pattern,protocol) VALUES (1,?,38000,?,NULL)', (c['name'], c['blob']))
assert db.execute('SELECT count(*) FROM button').fetchone()[0] == 2
assert db.execute('PRAGMA integrity_check').fetchone()[0] == 'ok'
assert db.execute('PRAGMA foreign_key_check').fetchall() == []
for c in captures:
    c.pop('blob')
report = {'status': 'validated research candidates, NOT bundled', 'hardware_tested': False,
          'license': 'LGPL-2.1 upstream; preserve copyright David Conran 2018 and license on distribution',
          'source': 'https://github.com/crankyoldgit/IRremoteESP8266/blob/master/test/ir_Whirlpool_test.cpp',
          'source_sha256': hashlib.sha256((ROOT/'ir_Whirlpool_test.cpp').read_bytes()).hexdigest(),
          'captures': captures, 'tests': ['343 measured timings each', 'independent 168-bit decode matches upstream bytes', 'both checksums', 'converter roundtrip', 'SQLite insert/read/count/integrity/foreign-keys']}
(ROOT/'whirlpool-validation.json').write_text(json.dumps(report, indent=2))
print(json.dumps(report, indent=2))

if __name__ == '__main__':
    print('PASS: 2 real Whirlpool captures validated; production DB unchanged.')
