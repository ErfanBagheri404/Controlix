#!/usr/bin/env python3
"""
Import probonopd/irdb crowd-sourced IR codes into Controlix's bundled DB.

Reads the irdb codes/ directory (manufacturer/DeviceType/Device,Subdevice.csv)
and appends rows to an existing controlix.db, mapping irdb protocol names to
Controlix's 12-byte parsed blob format (protoId + address + command).

Only imports rows whose protocol maps to an encoder we ship (NEC family,
Samsung32, RC5/5X/6, Sony SIRC, Kaseikyo, RCA, Pioneer, JVC). The rest
are skipped with a count.

irdb license: open commercial use with attribution per LICENSE.md.

Usage:
    python merge_irdb.py <irdb-codes-dir> <controlix.db>
"""
import sys
import os
import csv
import struct
import sqlite3
import glob
from collections import Counter

# irdb protocol name -> (protoId for 12-byte blob, carrier Hz, addr mask, cmd mask)
# Masks: (max_address_bits, max_command_bits) — used to pack device+subdevice → addr
PROTO_MAP = {
    # NEC family
    'NEC':      (1, 38000, 0xFF, 0xFF),
    'NEC1':     (1, 38000, 0xFF, 0xFF),
    'NEC2':     (2, 38000, 0xFFFF, 0xFF),  # NECext-style: device = low, subdevice = high
    'NECx1':    (1, 38000, 0xFF, 0xFF),
    'NECx2':    (2, 38000, 0xFFFF, 0xFF),
    'nec1':     (1, 38000, 0xFF, 0xFF),    # case variant
    'nec2':     (2, 38000, 0xFFFF, 0xFF),
    'NEC1-y1':  (1, 38000, 0xFF, 0xFF),    # NEC with y-bit masking — treat as standard NEC
    'NEC1-y2':  (1, 38000, 0xFF, 0xFF),
    'NEC1-y3':  (1, 38000, 0xFF, 0xFF),
    'NEC1-f16': (1, 38000, 0xFF, 0xFF),    # 16-bit function NEC — use cmd low byte
    'NEC2-f16': (2, 38000, 0xFFFF, 0xFFFF),
    'NECx1-f16': (1, 38000, 0xFF, 0xFF),
    'NECx2-f16': (2, 38000, 0xFFFF, 0xFFFF),
    # Samsung
    'Samsung32': (5, 38000, 0xFF, 0xFF),
    # RC5/RC6
    'RC5':       (6, 36000, 0x1F, 0x3F),
    'RC5x':      (7, 36000, 0x1F, 0x7F),
    'RC5-7F':    (7, 36000, 0x1F, 0x7F),   # RC5x with 7-bit command
    'RC6':       (8, 36000, 0xFF, 0xFF),
    'RC6-6-20':  (8, 36000, 0xFF, 0xFF),   # RC6 mode 0 standard
    # Sony SIRC
    'Sony12':    (9, 40000, 0x1F, 0x7F),   # 5-bit addr, 7-bit cmd
    'Sony15':    (10, 40000, 0xFF, 0x7F),  # 8-bit addr, 7-bit cmd
    'Sony20':    (11, 40000, 0x1FFF, 0x7F),# 13-bit addr, 7-bit cmd
    'Sony8':     (9, 40000, 0x1F, 0x7F),   # 8-bit Sony = SIRC12 variant
    # Kaseikyo (Panasonic)
    'Kaseikyo':  (12, 37000, 0xFFFF, 0x3FF),
    'Panasonic': (12, 37000, 0xFFFF, 0x3FF),
    'Panasonic_Old': (12, 37000, 0xFFFF, 0x3FF),
    # RCA
    'RCA':       (13, 56000, 0xF, 0xFF),
    'RCA-38':    (13, 38000, 0xF, 0xFF),
    'RCA(Old)':  (13, 56000, 0xF, 0xFF),
    # Pioneer
    'Pioneer':   (14, 40000, 0xFF, 0xFF),
    # JVC
    'JVC':       (15, 38000, 0xFF, 0xFF),
    'JVC{2}':    (15, 38000, 0xFF, 0xFF),
    'JVC-48':    (15, 38000, 0xFFFF, 0xFFFF),  # 48-bit = extended
    # Mitsubishi (NEC-based)
    'Mitsubishi': (1, 38000, 0xFF, 0xFF),
    # Denon (NEC-based variants)
    
    
    
    
    # Sharp (NEC-based in irdb, not the raw Sharp protocol)
    
    
    
    # Akai (NEC-based)
    'Akai':      (1, 38000, 0xFF, 0xFF),
    # Fujitsu (NEC-based)
    'Fujitsu':   (1, 38000, 0xFF, 0xFF),
    'Fujitsu_Aircon': (1, 38000, 0xFF, 0xFF),
    # Aiwa (NEC-like with different address mask)
    
    # Nokia (RC5-based)
    'Nokia':     (6, 36000, 0x1F, 0x3F),
    'Nokia12':   (6, 36000, 0x1F, 0x3F),
    'Nokia32':   (8, 36000, 0xFF, 0xFF),   # Nokia32 uses RC6 framing
    # RECS80
    'RECS80':    (6, 36000, 0x1F, 0x3F),   # RECS80 is RC5-compatible
    # Thomson (NEC-like)
    'Thomson':   (1, 38000, 0xFF, 0xFF),
    'Thomson7':  (1, 38000, 0xFF, 0xFF),
    # Proton (NEC-based)
    'Proton':    (1, 38000, 0xFF, 0xFF),
    # G.I.Cable
    'G.I.Cable': (1, 38000, 0xFF, 0xFF),
    # MCE (NEC-based)
    'MCE':       (1, 38000, 0xFF, 0xFF),
    # Blaupunkt (NEC-based)
    'Blaupunkt': (1, 38000, 0xFF, 0xFF),
    # Emerson (NEC-based)
    'Emerson':   (1, 38000, 0xFF, 0xFF),
    # Konka (NEC-based)
    'Konka':     (1, 38000, 0xFF, 0xFF),
    # === Batch 3: real encoders ===
    'Aiwa':       (16, 38000, 0xFFFF, 0xFF),   # 42-bit: D:8 S:5 ~D:8 ~S:5 F:8 ~F:8
    'Sharp':      (17, 38000, 0x1F, 0xFF),      # 264us MSB: D:5 F:8 ID:2
    'Sharp{1}':   (17, 38000, 0x1F, 0xFF),
    'Sharp{2}':   (18, 38000, 0x1F, 0xFF),      # Sharp{2} = Denon framing
    'Denon':      (18, 38000, 0x1F, 0xFF),
    'Denon{1}':   (18, 38000, 0x1F, 0xFF),
    'Denon{2}':   (17, 38000, 0x1F, 0xFF),      # Denon{2} = Sharp framing
    # Velleman
    'Velleman':  (1, 38000, 0xFF, 0xFF),
    # Dgtec
    'Dgtec':     (1, 38000, 0xFF, 0xFF),
    # StreamZap
    'StreamZap': (1, 38000, 0xFF, 0xFF),
    # Replay
    'Replay':    (1, 38000, 0xFF, 0xFF),
    # Apple (NEC-based)
    'Apple':     (1, 38000, 0xFF, 0xFF),
    # DirecTV (NEC-based variants)
    'DirecTV':   (1, 38000, 0xFF, 0xFF),
    'DirecTV_3FG': (1, 38000, 0xFF, 0xFF),
    # Dish_Network (NEC-based)
    'Dish_Network': (1, 38000, 0xFF, 0xFF),
    # Barco
    'Barco':     (1, 38000, 0xFF, 0xFF),
    # Kathrein
    'Kathrein':  (1, 38000, 0xFF, 0xFF),
    # Sejin
    'Sejin-1-38': (1, 38000, 0xFF, 0xFF),
    'Sejin-1-56': (1, 38000, 0xFF, 0xFF),
    # CanalSat (NEC-based)
    'CanalSat':  (2, 38000, 0xFFFF, 0xFF),
    'CanalSatLD': (2, 38000, 0xFFFF, 0xFF),
    # ScAtl
    'ScAtl-6':   (1, 38000, 0xFF, 0xFF),
    # NRC17
    'NRC17':     (1, 38000, 0xFF, 0xFF),
    # 48-NEC2
    '48-NEC2':   (4, 38000, 0x3FFFFFF, 0xFFFF),  # Nec42ext
    # Skips from first pass, grouped by correction type
    'nec':       (1, 38000, 0xFF, 0xFF),      # lowercase NEC1
    'denon':     (1, 38000, 0xFF, 0xFF),      # lowercase Denon
    'Samsung36': (5, 38000, 0xFF, 0xFF),      # Samsung36 = Samsung32 framing
    'Panasonic2': (12, 37000, 0xFFFF, 0x3FF),
    'RCA-38(Old)': (13, 38000, 0xF, 0xFF),
    'Tivo unit=0': (1, 38000, 0xFF, 0xFF),    # Tivo uses NEC framing
    'Tivo unit=1': (1, 38000, 0xFF, 0xFF),
    'Tivo unit=2': (1, 38000, 0xFF, 0xFF),
    'Tivo unit=3': (1, 38000, 0xFF, 0xFF),
    'F12':       (1, 38000, 0xFF, 0xFF),
    'Zenith':    (1, 38000, 0xFF, 0xFF),
    '48-NEC1':   (3, 38000, 0x1FFF, 0xFF),        # Nec42
    # === Batch 1: MakeHex IRP transcriptions ===
    'Denon-K':      (19, 37000, 0xFF, 0xFFF),    # D:4 S:4 F:12 C:8
    'Jerrold':      (20, 38000, 0x00, 0x1F),     # F:5, no address
    'G.I.4DTV':     (21, 37700, 0xFF, 0xFF),     # B=D*64+F
    'Lumagen':      (22, 38000, 0x0F, 0x7F),     # D:4 F:7, MSB
    'Samsung20':    (23, 38400, 0xFFF, 0xFF),    # D:6 S:6 F:8
    'Teac-K':       (24, 37900, 0xFFF, 0xFF),    # D:4 S:8 F:8
    'Dishplayer':   (25, 57600, 0x3FF, 0x3F),    # S:5 D:5 F:6
    'DishPlayer_Network': (25, 57600, 0x3FF, 0x3F),
}


IRD_COLUMNS = ['functionname', 'protocol', 'device', 'subdevice', 'function']


def read_irdb_csv(path):
    """Rows of an irdb CSV, whether or not the file carries a header line.

    Most irdb CSVs start with a `functionname,protocol,device,subdevice,function`
    header, but a sizeable minority are headerless — feeding those to
    csv.DictReader silently promotes the first data row to the header, so
    every lookup of 'protocol'/'function' misses and the whole remote ends up
    with zero buttons. Sniff the first row: a header only if cell 0 is the
    literal 'functionname'.
    """
    with open(path, encoding='utf-8', errors='ignore') as f:
        text = f.read()
    if not text.strip():
        return []
    first = text.split('\n', 1)[0]
    if first.split(',')[0].strip().strip('"').lower() == 'functionname':
        return list(csv.DictReader(text.splitlines()))
    return list(csv.DictReader(text.splitlines(), fieldnames=IRD_COLUMNS))


def sibling_or_synth_name(csv_path, function, protocol, device, subdevice):
    """Label for a row whose functionname cell is blank.

    irdb uses blank names for single-row address markers and rows a
    contributor left unnamed. The same (protocol, device, subdevice,
    function) tuple in a sibling file of the same manufacturer is the same
    button, so reuse that label; otherwise return None and the caller
    synthesizes one. Never drops the row.
    """
    manufacturer = os.path.dirname(os.path.dirname(csv_path))
    wanted = (function, protocol, device, subdevice)
    for sib in sorted(glob.glob(os.path.join(manufacturer, '*', '*.csv'))):
        if sib == csv_path:
            continue
        try:
            rows = read_irdb_csv(sib)
        except OSError:
            continue
        for row in rows:
            key = ((row.get('function') or '').strip(),
                   (row.get('protocol') or '').strip(),
                   (row.get('device') or '').strip(),
                   (row.get('subdevice') or '').strip())
            if key != wanted:
                continue
            name = (row.get('functionname') or '').strip()
            if name:
                return name
    return None


def encode_parsed_12(proto_id, address, command):
    """12-byte parsed blob: [protoId, rsvd×3, addr(4 LE), cmd(4 LE)]."""
    addr = address & 0xFFFFFFFF
    cmd = command & 0xFFFFFFFF
    return struct.pack('<BBBBI I', proto_id, 0, 0, 0, addr, cmd)[:12]


def irdb_to_blob(protocol, device_str, subdevice_str, function_str):
    """Convert irdb CSV row (protocol, device, subdevice, function) → blob + carrier_hz, or None."""
    info = PROTO_MAP.get(protocol) or PROTO_MAP.get(protocol.upper()) or PROTO_MAP.get(protocol.lower())
    if info is None:
        import re as _re
        # RC6-M-* variants (16/24/28/32/56): RC6 framing resolved elsewhere
        if _re.match(r'(?i)^RC6(-M-|-6-20)', protocol or ''):
            info = (8, 36000, 0xFF, 0xFF)
    if not info:
        return None

    proto_id, carrier, addr_mask, cmd_mask = info

    try:
        device = int(device_str) if device_str.strip() else 0
        subdevice = int(subdevice_str) if subdevice_str.strip() else -1
        function = int(function_str) if function_str.strip() else 0
    except (ValueError, AttributeError):
        return None

    if subdevice < 0:
        subdevice = 0

    # Pack address based on protocol family
    if proto_id in (2, 4):  # NECext / Nec42ext — 16-bit address
        addr = (device & 0xFF) | ((subdevice & 0xFF) << 8)
    elif proto_id in (3,):  # Nec42 — 13-bit address
        addr = device & 0x1FFF
    elif proto_id in (5,):  # Samsung32 — 16-bit (device repeated)
        addr = device & 0xFF
    elif proto_id in (6, 7):  # RC5/RC5x — 5-bit address
        addr = device & 0x1F
    elif proto_id in (8,):  # RC6 — 8-bit address
        addr = device & 0xFF
    elif proto_id in (9,):  # SIRC12 — 5-bit address
        addr = device & 0x1F
    elif proto_id in (10,):  # SIRC15 — 8-bit address
        addr = device & 0xFF
    elif proto_id in (11,):  # SIRC20 — 13-bit address
        addr = device & 0x1FFF
    elif proto_id in (12,):  # Kaseikyo — 16-bit address
        addr = (device & 0xFF) | ((subdevice & 0xFF) << 8)
    elif proto_id in (13,):  # RCA — 4-bit address
        addr = device & 0xF
    elif proto_id in (14,):  # Pioneer
        addr = device & 0xFF
    elif proto_id in (15,):  # JVC
        addr = device & 0xFF
    elif proto_id in (16,):  # Aiwa — D:8 + S:5 packed
        addr = (device & 0xFF) | ((subdevice & 0x1F) << 8)
    elif proto_id in (17, 18):  # Sharp / Denon — 5-bit address
        addr = device & 0x1F
    elif proto_id in (19,):  # Denon-K — D:4 low, S:4 above
        addr = (device & 0xF) | ((subdevice & 0xF) << 4)
    elif proto_id in (20,):  # Jerrold — function-only frame
        addr = 0
    elif proto_id in (21,):  # G.I.4DTV — B = D*64+F
        addr = device & 0xFF
    elif proto_id in (22,):  # Lumagen — 4-bit address, MSB-first
        addr = device & 0xF
    elif proto_id in (23,):  # Samsung20 — D:6 low, S:6 above
        addr = (device & 0x3F) | ((subdevice & 0x3F) << 6)
    elif proto_id in (24,):  # Teac-K — D:4 low, S:8 above
        addr = (device & 0xF) | ((subdevice & 0xFF) << 4)
    elif proto_id in (25,):  # DishPlayer — D:5 low, S:5 above
        addr = (device & 0x1F) | ((subdevice & 0x1F) << 5)
    else:  # NEC1, Samsung32, etc
        addr = device & 0xFF

    cmd = function & cmd_mask

    blob = encode_parsed_12(proto_id, addr, cmd)
    if not blob or len(blob) < 12:
        return None

    return blob, carrier


# Category mapping: irdb device types (free text) -> Controlix's 47 category slugs.
# Keywords checked in order; anything unrecognized lands in miscellaneous
# so the category list stays at 47.
CAT_KEYWORDS = [
    (['tv', 'television', 'hdtv'], 'tvs'),
    (['projector'], 'projectors'),
    (['soundbar', 'sound_bar', 'sound bar'], 'soundbars'),
    (['avr', 'receiver', 'audio_and_video', 'av_receiver', 'amplifier', 'amp'], 'audio_and_video_receivers'),
    (['speaker'], 'speakers'),
    (['dvd', 'blu-ray', 'bluray', 'bd_', 'laserdisc', 'minidisc'], 'dvd_players'),
    (['vcr', 'vhs', 'betamax'], 'vcr'),
    (['cd_player', 'cdplayer', 'cd_'], 'cd_players'),
    (['set_top', 'settop', 'stb', 'satellite', 'sat_', 'dvb', 'cable', 'dvr', 'pvr', 'tivo', 'dish', 'directv',
      'converter', 'dta', 'tuner'], 'cable_boxes'),
    (['streaming', 'roku', 'fire_tv', 'appletv', 'apple_tv', 'chromecast', 'android_tv', 'kodi', 'htpc',
      'media_player', 'mediaplayer', 'thinbox', 'popcorn'], 'streaming_devices'),
    (['console', 'xbox', 'playstation', 'ps3', 'ps4', 'ps5', 'wii', 'switch_game', 'nintendo'], 'consoles'),
    (['monitor'], 'monitors'),
    (['computer', 'pc_', 'laptop', 'keyboard', 'mouse', 'kvm'], 'computers'),
    (['air_condition', 'aircon', 'air_con', '_ac', 'ac_', 'hvac', 'heat_pump'], 'acs'),
    (['fan'], 'fans'),
    (['heater'], 'heaters'),
    (['humidifier'], 'humidifiers'),
    (['air_purifier', 'airpurifier'], 'air_purifiers'),
    (['vacuum', 'roomba'], 'vacuum_cleaners'),
    (['light', 'lamp', 'led_', 'bulb'], 'led_lighting'),
    (['camera', 'cctv', 'dslr', 'camcorder', 'webcam'], 'cameras'),
    (['car_', 'auto_', 'head_unit'], 'car_multimedia'),
    (['clock', 'alarm'], 'clocks'),
    (['fireplace'], 'fireplaces'),
    (['bidet'], 'bidet'),
    (['toy', 'drone', 'rc_car'], 'toys'),
    (['universal'], 'universal_tv_remotes'),
    (['whiteboard', 'smart_board'], 'whiteboards'),
    (['videoconferenc', 'video_conferenc', 'webex', 'zoom_room'], 'videoconferencing'),
    (['digital_sign', 'signage'], 'digital_signs'),
    (['touchscreen', 'touch_panel'], 'touchscreen_displays'),
    (['picture_frame'], 'picture_frames'),
    (['multimedia', 'multimedia_player'], 'multimedia'),
    (['dust_collector'], 'dust_collectors'),
    (['ceiling_lift', 'handicap'], 'handicap_ceiling_lifts'),
    (['window_cleaner'], 'window_cleaners'),
    (['tv_tuner'], 'tv_tuner'),
]

# Device types that carry zero signal — brand-specific model codes, not device
# classes. Their remotes still import (buttons matter), bucketed misc.
SKIP_DEVTYPE_AS_CATEGORY = {
    'unknown_2wire', 'dvd_windvd', 'sat_8776', 'converter_dta1080u', 'thinbox',
}


def guess_category(devtype, valid_slugs):
    """Map irdb device type (free text) to one of Controlix's existing category slugs."""
    low = devtype.lower().replace('-', '_').replace(' ', '_')
    for keywords, slug in CAT_KEYWORDS:
        if slug not in valid_slugs:
            continue
        for kw in keywords:
            if kw in low:
                return slug
    return 'miscellaneous'


def main():
    if len(sys.argv) < 3:
        print(__doc__)
        return 1

    codes_dir = sys.argv[1]
    db_path = sys.argv[2]

    if not os.path.isdir(codes_dir):
        print(f"error: {codes_dir} is not a directory")
        return 1

    conn = sqlite3.connect(db_path)

    # Ensure categories exist
    existing_cats = {row[0]: row[1] for row in conn.execute("SELECT slug, id FROM category")}

    stats = Counter()
    skipped_proto = Counter()
    skipped_empty = 0
    imported = 0
    brands_added = Counter()

    # Walk irdb CSVs
    csvs = glob.glob(os.path.join(codes_dir, '*', '*', '*.csv'))
    print(f"Found {len(csvs)} irdb CSVs")

    for csv_path in sorted(csvs):
        rel = os.path.relpath(csv_path, codes_dir)
        parts = rel.replace(os.sep, '/').split('/')
        if len(parts) != 3:
            continue
        manufacturer, devtype, device_file = parts
        model_name = device_file.replace('.csv', '')

        # Get or create category
        cat_slug = guess_category(devtype, existing_cats)
        cat_id = existing_cats.get(cat_slug)
        if cat_id is None:
            cat_slug = 'miscellaneous'
            cat_id = existing_cats[cat_slug]

        # Get or create brand
        brand_key = (cat_id, manufacturer)
        cur = conn.execute("SELECT id FROM brand WHERE category_id=? AND name=?",
                          (cat_id, manufacturer))
        row = cur.fetchone()
        if row:
            brand_id = row[0]
        else:
            cur = conn.execute("INSERT INTO brand (category_id, name) VALUES (?, ?)",
                              (cat_id, manufacturer))
            brand_id = cur.lastrowid
            brands_added[manufacturer] += 1

        # Read CSV
        try:
            rows = read_irdb_csv(csv_path)
        except Exception:
            skipped_empty += 1
            continue

        if not rows:
            skipped_empty += 1
            continue

        # Create remote (one per irdb CSV)
        file_name = f"{manufacturer}_{devtype}_{model_name}.irdb"
        cur = conn.execute(
            "SELECT id FROM remote WHERE brand_id=? AND file_name=?",
            (brand_id, file_name))
        existing = cur.fetchone()
        if existing:
            remote_id = existing[0]
        else:
            cur = conn.execute(
                "INSERT INTO remote (brand_id, file_name, model_name, source, source_path) "
                "VALUES (?, ?, ?, ?, ?)",
                (brand_id, file_name, model_name, 'irdb', rel))
            remote_id = cur.lastrowid

        # Add model string for search
        if model_name:
            conn.execute("INSERT OR IGNORE INTO remote_model (remote_id, model) VALUES (?, ?)",
                        (remote_id, model_name))

        # Convert buttons
        btn_count = 0
        unnamed_idx = 0
        for row in rows:
            func_name = (row.get('functionname') or '').strip()
            protocol = (row.get('protocol') or '').strip()
            device = (row.get('device') or '').strip()
            subdevice = (row.get('subdevice') or '').strip()
            function = (row.get('function') or '').strip()

            if not function:
                skipped_empty += 1
                continue
            if not func_name:
                # Single-row address markers + unnamed rows: derive a label
                # from a sibling row's name at the same function slot,
                # else synthesize "<device> <function>" — never drop them.
                func_name = (sibling_or_synth_name(csv_path, function, protocol, device, subdevice)
                             or f"{model_name} {function}")
                unnamed_idx += 1

            result = irdb_to_blob(protocol, device, subdevice, function)
            if result is None:
                skipped_proto[protocol] += 1
                continue

            blob, carrier = result

            # Clean up function name for display
            display_name = func_name.replace('KEY_', '').replace('_', ' ').strip()
            if not display_name:
                display_name = func_name

            conn.execute(
                "INSERT OR IGNORE INTO button (remote_id, name, carrier_hz, pattern, protocol) "
                "VALUES (?, ?, ?, ?, ?)",
                (remote_id, display_name, carrier, blob, protocol))
            btn_count += 1
            imported += 1

        if btn_count > 0:
            stats['remotes'] += 1

        stats['brands'] = len(brands_added)

    conn.commit()
    conn.execute('VACUUM')
    conn.close()

    print(f"\n=== irdb import complete ===")
    print(f"buttons imported:   {imported}")
    print(f"remotes created:    {stats['remotes']}")
    print(f"brands added:       {stats['brands']}")
    print(f"categories new:     {stats.get('categories_new', 0)}")
    print(f"skipped (no proto): {sum(skipped_proto.values())}")
    print(f"skipped (empty):    {skipped_empty}")
    print(f"\nprotocols skipped:")
    for proto, count in skipped_proto.most_common(20):
        print(f"  {proto:22s} {count:6d}")
    print(f"\nDB size: {os.path.getsize(db_path) / (1024*1024):.1f} MB")
    return 0


if __name__ == '__main__':
    sys.exit(main())
