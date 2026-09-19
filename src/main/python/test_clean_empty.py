"""Regression coverage for additive recovery; no empty-record cleanup."""
import pathlib
import sqlite3
import struct
import unittest
from recover_kaseikyo import wire

ROOT = pathlib.Path(__file__).resolve().parents[3]

class RecoveryTests(unittest.TestCase):
    def test_kaseikyo_known_frame(self):
        # Panasonic vendor 0x2002, genre1=8, genre2=0, command=0x10.
        p = struct.unpack('<100i', wire(0x00200280, 0x10))
        frame = [sum((p[3 + (i*8+j)*2] == 1296) << j for j in range(8)) for i in range(6)]
        self.assertEqual(frame, [0x02, 0x20, 0x80, 0x00, 0x01, 0x81])
        self.assertEqual(p[:2], (3456,1728))
        self.assertEqual(p[-2:], (432,0))

    def test_original_records_unchanged(self):
        original = sqlite3.connect(ROOT / '.hermes/research/empty-cleanup/before-20260917-000719.db')
        current = sqlite3.connect(ROOT / 'app/src/main/assets/controlix.db')
        for table in ('category','brand','remote','remote_model','button'):
            before = set(original.execute('SELECT * FROM '+table).fetchall())
            after = set(current.execute('SELECT * FROM '+table).fetchall())
            self.assertTrue(before <= after, table + ' lost or changed original rows')
        self.assertEqual(current.execute('PRAGMA integrity_check').fetchone()[0], 'ok')
        self.assertEqual(current.execute('PRAGMA foreign_key_check').fetchall(), [])
        original.close()
        current.close()

if __name__ == '__main__':
    unittest.main()
