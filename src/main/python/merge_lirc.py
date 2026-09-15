#!/usr/bin/env python3
"""
Merge the lirc-remotes XML database (probonopd/lirc-remotes, the SourceForge
lirc-remotes CVS export) into Controlix's bundled controlix.db.

Layout: <brand_dir>/<MODEL>.xml, each containing <remote name=...> with
<code name="KEY_X"><decoding protocol=... device=... obc=.../><ccf>pronto</ccf>

Strategy per button, in priority order:
  1. <decoding> protocol maps in irdb PROTO_MAP -> compact 12-byte parsed blob
     (the app's encoders reproduce the waveform at transmit time; smallest DB).
  2. Otherwise fall back to the <ccf> Pronto-NX hex -> decode to microsecond
     on/off pairs -> RAW blob (N x LE int32). Every LIRC file has ccf, so
     nothing supported is lost to exotic protocols (Gap-*/Async*/AirB*...).
     A sanity gate decodes the ccf head and cross-checks carrier vs lircdata
     freq when both exist.

Brand dir + filename become remote_model search strings. Category inferred
from folder/file naming keywords, same rules as merge_irdb (CAT_KEYWORDS).

License: lirc-remotes is GPL-2.0 data collection; each conf file credits its
original contributor. Attribution note goes in README/About.

Usage:
    python merge_lirc.py <lirc-remotes-root> <controlix.db> [--dry]
"""
import sys, os, re, struct, sqlite3, glob
from collections import Counter

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from merge_irdb import PROTO_MAP, CAT_KEYWORDS, encode_parsed_12, guess_category

RE_CODE = re.compile(r'<code\s+name="([^"]+)"[^>]*>(.*?)</code>', re.S)
RE_DECODING = re.compile(
    r'<decoding\s+protocol="([^"]*)"(?:\s+device="(-?\d+)")?'
    r'(?:\s+subdevice="(-?\d+)")?(?:\s+obc="(-?\d+)")?')
RE_CCF = re.compile(r'<ccf>([0-9a-fA-F ]+)</ccf>')
RE_REMOTE = re.compile(r'<remote name="([^"]*)"')
RE_LIRCDATA = re.compile(r'<lircdata[^>]*freq="(\d+)"')


def decode_ccf(ccf):
    """Pronto-NX hex -> (carrier_hz, [us durations]) or None.

    Matches the app's ProntoParser exactly: word1 is a frequency code with
    carrier = 4145146 / N; type-0 timing words are counted in CARRIER CYCLES,
    so us = word * (1e6 / carrier). Supports type 0 (raw learned) only —
    type-1 resident codes (~a handful of files) are skipped.
    """
    words = [int(w, 16) for w in ccf.split()]
    if len(words) < 4 or words[0] != 0x0000:
        return None
    freq_code = words[1]
    if freq_code == 0:
        return None
    carrier = int(round(4145146.0 / freq_code))
    if not (10000 <= carrier <= 100000):
        return None
    once_pairs, repeat_pairs = words[2], words[3]
    need = 4 + 2 * (once_pairs + repeat_pairs)
    if len(words) < need or once_pairs == 0 or once_pairs > 100:
        return None
    cycle_us = 1_000_000.0 / carrier
    seq = words[4:4 + 2 * once_pairs]
    us = [int(round(v * cycle_us)) for v in seq]
    return carrier, us


def raw_blob(durations_us):
    return struct.pack('<' + 'i' * len(durations_us), *durations_us)


def map_decoding(protocol, device, subdevice, obc):
    """<decoding> attrs -> (blob, carrier) via the irdb PROTO_MAP, or None."""
    info = PROTO_MAP.get(protocol) or PROTO_MAP.get(protocol.upper()) or PROTO_MAP.get(protocol.lower())
    if info is None:
        if re.match(r'(?i)^RC6(-M-|-6-20)', protocol or ''):
            info = (8, 36000, 0xFF, 0xFF)
    if not info:
        return None
    proto_id, carrier, addr_mask, cmd_mask = info
    dev, sub, fun = int(device), int(subdevice), int(obc)
    if sub < 0:
        sub = 0
    if proto_id in (2, 4):
        addr = (dev & 0xFF) | ((sub & 0xFF) << 8)
    elif proto_id == 3:
        addr = dev & 0x1FFF
    elif proto_id in (6, 7):
        addr = dev & 0x1F
    elif proto_id == 8:
        addr = dev & 0xFF
    elif proto_id == 9:
        addr = dev & 0x1F
    elif proto_id == 10:
        addr = dev & 0xFF
    elif proto_id == 11:
        addr = dev & 0x1FFF
    elif proto_id == 12:
        addr = (dev & 0xFF) | ((sub & 0xFF) << 8)
    elif proto_id == 13:
        addr = dev & 0xF
    elif proto_id == 16:
        addr = (dev & 0xFF) | ((sub & 0x1F) << 8)
    elif proto_id in (17, 18):
        addr = dev & 0x1F
    else:
        addr = dev & 0xFF
    cmd = fun & cmd_mask
    return encode_parsed_12(proto_id, addr, cmd), carrier


def clean_key(name):
    """KEY_POWER -> power, TV_VOLUME_UP -> volume up, 1 -> 1."""
    n = name.strip()
    n = re.sub(r'(?i)^KEY[_-]', '', n)
    n = n.replace('_', ' ').replace('-', ' ').strip().lower()
    return n or name.strip().lower()


# LIRC folders are BRANDS, not device types — so category comes from the
# button-name fingerprint of each remote (what keys it has), filename hints
# only as a tiebreak.
AC_H = {'temp', 'temp+', 'temp-', 'cool', 'heat', 'dry', 'swing', 'mode',
        'fan speed', 'timer on', 'timer off'}
FAN_H = {'fan', 'osc', 'oscillate', 'speed', 'ventilation', 'breeze'}
LED_H = {'rgb', 'warm', 'cool white', 'flash', 'strobe', 'fade', 'smooth',
         'jump', 'night light', 'color', 'dim', 'brightness'}
DISC_H = {'eject', 'angle', 'a-b', 'repeat', 'random', 'program', 'subtitle',
          'pbc', 'next track', 'prev track', 'skip f', 'skip r', 'play',
          'pause', 'ffwd', 'rew', 'track +', 'display'}
AMP_H = {'spkr a', 'spkr b', 'surround', 'tuner', 'phono', 'tape', 'cd',
         'dvd', 'aux', 'movie', 'music', 'dialog', 'bass', 'treble',
         'loudness', 'straight', 'dsp', 'multi', 'stereo', 'am/fm', 'fm',
         'cd/sa', 'sa', 'video', 'vcr', 'input a', 'input b'}
TV_H = {'ch', 'ch+', 'ch-', 'channel', 'channelup', 'ch up', 'vol', 'vol+',
        'volumeup', 'mute', 'power', 'tv', 'cbl', 'guide', 'menu', 'ok',
        'up', 'down', 'left', 'right', 'exit', 'info', 'list', 'sat', 'av',
        'source', 'input', 'sleep', 'text', 'cc'}


def fingerprint_category(key_names, filename_hint):
    """Pick a category slug from the remote's button-name set."""
    ns = set(key_names)
    hint = filename_hint.lower()
    if ns & AC_H:
        return 'acs'
    if (ns & FAN_H) and not ns & TV_H:
        return 'fans'
    if (ns & LED_H) and not ns & TV_H:
        return 'led_lighting'
    if 'projector' in hint or 'proj' in hint:
        return 'projectors'
    if ns & DISC_H and not ns & AMP_H:
        if 'vcr' in hint or 'vhsvcr' in hint or 'video cassette' in hint:
            return 'vcr'
        if 'cd' in hint and 'dvd' not in hint and 'bluray' not in hint:
            return 'cd_players'
        return 'dvd_players'
    if ns & AMP_H:
        return 'audio_and_video_receivers'
    if ns & TV_H:
        if any(k in hint for k in ('stb', 'sat', 'cable', 'receiver box', 'digita', 'hd box', 'pvr', 'dvb')):
            return 'cable_boxes'
        return 'tvs'
    return 'miscellaneous'


def main():
    if len(sys.argv) < 3:
        print(__doc__)
        return 1
    root, db_path = sys.argv[1], sys.argv[2]
    dry = '--dry' in sys.argv

    xmls = sorted(glob.glob(os.path.join(root, '*', '*.xml')))
    print(f'Found {len(xmls)} LIRC xml remotes under {root}')

    conn = sqlite3.connect(db_path)
    cat_by_slug = dict(conn.execute('SELECT slug, id FROM category'))
    stats = Counter()
    cats_used = Counter()
    proto_used = Counter()

    for path in xmls:
        rel = os.path.relpath(path, root).replace(os.sep, '/')
        brand_dir = rel.split('/')[0]
        model_file = os.path.basename(path)[:-4]  # strip .xml
        try:
            txt = open(path, encoding='utf-8', errors='replace').read()
        except OSError:
            stats['file_error'] += 1
            continue
        m = RE_REMOTE.search(txt)
        remote_label = m.group(1) if m else model_file
        fm = RE_LIRCDATA.search(txt)
        file_freq = int(fm.group(1)) if fm else None

        # ---- two-pass: collect cleaned key names first for fingerprint ----
        preliminary = [clean_key(m.group(1)) for m in RE_CODE.finditer(txt)]
        slug = fingerprint_category(preliminary, model_file + ' ' + remote_label + ' ' + rel)
        if slug not in cat_by_slug:
            slug = 'miscellaneous'
        cat_id = cat_by_slug[slug]

        # brand display: Title Case the dir
        brand_name = brand_dir.replace('_', ' ').strip().title()

        # remote dedupe key: (brand, file) already UNIQUE in schema
        cur = conn.execute('SELECT id FROM brand WHERE category_id=? AND name=?', (cat_id, brand_name))
        row = cur.fetchone()
        if row:
            brand_id = row[0]
        else:
            brand_id = conn.execute('INSERT INTO brand (category_id, name) VALUES (?,?)',
                                    (cat_id, brand_name)).lastrowid

        file_name = f'lirc-{brand_dir}-{model_file}.ir'
        cur = conn.execute('SELECT id FROM remote WHERE brand_id=? AND file_name=?', (brand_id, file_name))
        row = cur.fetchone()
        if row:
            remote_id = row[0]
            stats['remote_replaced'] += 1
            conn.execute('DELETE FROM button WHERE remote_id=?', (remote_id,))
        else:
            remote_id = conn.execute(
                'INSERT INTO remote (brand_id, file_name, model_name, source, source_path) VALUES (?,?,?,?,?)',
                (brand_id, file_name, remote_label, 'lirc', rel)).lastrowid
            stats['remotes_added'] += 1
        cats_used[slug] += 1

        # search strings
        for s in {model_file.replace('_', ' '), remote_label.replace('_', ' ')}:
            if s.strip():
                conn.execute('INSERT OR IGNORE INTO remote_model (remote_id, model) VALUES (?,?)',
                             (remote_id, s))

        n_btn = 0
        for key_name, code_body in RE_CODE.findall(txt):
            dm = RE_DECODING.search(code_body)
            cm = RE_CCF.search(code_body)
            proto, dev, sub, obc = (dm.group(1), dm.group(2), dm.group(3), dm.group(4)) if dm else (None, None, None, None)
            ccf = cm.group(1) if cm else None
            blob_carrier = None
            if proto and dev is not None and obc is not None:
                try:
                    blob_carrier = map_decoding(proto, dev, sub, obc)
                    if blob_carrier:
                        proto_used['parsed:' + proto] += 1
                except (ValueError, TypeError):
                    blob_carrier = None
            if blob_carrier is None and ccf:
                d = decode_ccf(ccf)
                if d is None:
                    stats['code_unencodable'] += 1
                    continue
                carrier, us = d
                if file_freq and abs(carrier - file_freq) > 3000:
                    carrier = file_freq
                blob_carrier = (raw_blob(us), carrier)
                proto_used['raw:' + (proto or '?')] += 1
            if blob_carrier is None:
                stats['code_unencodable'] += 1
                continue
            blob, carrier = blob_carrier
            name = clean_key(key_name)
            if not name:
                continue
            try:
                conn.execute(
                    'INSERT OR IGNORE INTO button (remote_id, name, carrier_hz, pattern, protocol) VALUES (?,?,?,?,?)',
                    (remote_id, name, carrier, blob, proto))
                n_btn += 1
            except sqlite3.IntegrityError:
                stats['button_conflict'] += 1
        stats['buttons_added'] += n_btn
        if n_btn == 0:
            stats['remote_empty'] += 1
            if not dry:
                conn.execute('DELETE FROM remote WHERE id=?', (remote_id,))

    print('---- stats ----')
    for k, v in sorted(stats.items()):
        print(f'  {k}: {v}')
    print('---- categories ----')
    for k, v in cats_used.most_common():
        print(f'  {k}: {v} remotes')
    print('---- protocol split ----')
    fam = Counter()
    for k, v in proto_used.items():
        fam[k.split(':')[0]] += v
    for k, v in fam.most_common():
        print(f'  {k}: {v}')
    if dry:
        print('DRY RUN — rolling back')
        conn.rollback()
    else:
        conn.commit()
        n = conn.execute('SELECT COUNT(*) FROM button').fetchone()[0]
        r = conn.execute('SELECT COUNT(*) FROM remote').fetchone()[0]
        b = conn.execute('SELECT COUNT(*) FROM brand').fetchone()[0]
        print(f'DB now: {n:,} buttons / {r:,} remotes / {b:,} brands')
    conn.close()
    return 0


if __name__ == '__main__':
    sys.exit(main())
