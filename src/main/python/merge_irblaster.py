#!/usr/bin/env python3
"""
Merge iodn/android-ir-blaster's GPL-3.0 database into Controlix's bundled DB.

Design (from structural analysis of irblaster.sql):
  - 9,388 remotes; models is a (brand, model) -> remote_id map with up to
    thousands of models per remote, and 3,934 remotes shared across brands.
    So: ONE Controlix remote per iodn remote_id; canonical brand = most
    frequent brand among its model rows; ALL model strings go into
    remote_model for exact-model search.
  - NEC + NEC2 keys validated byte-exact (addr,~addr,cmd,~cmd).
  - SONY12/15/20: packed frame = cmd(7 LSB) | addr << 7, hex width 3/4/5.
    Stored as Controlix SIRC (9) / SIRC15 (10) / SIRC20 (11).
  - RC5: 3 hex digits = 12-bit packed frame addr(5)<<7 | cmd(7)...[truncated]
  - Device type per remote is unknown (iodn has no category link), so the
    power-off sweep may fire a few non-TV POWER codes from this category
    (AVRs/consoles). Accepted trade-off: the dump is overwhelmingly TVs
    and the side effect is standby-only. Sweep totals 4,191 codes.

Blob format matches convert_irdb.py parsed convention:
    struct.pack('<III', proto_id, address, command)   proto NEC -> 1

Usage:
    python merge_irblaster.py <irblaster.sql> <controlix.db>
"""
import sys, os, re, struct, sqlite3, collections

TABLE_RE = re.compile(r"INSERT INTO `?(\w+)`? VALUES\((.*)\);\s*$")


def split_fields(s):
    """Split a SQL VALUES tuple body, respecting '' and \\' quote escapes."""
    out = []
    i, n = 0, len(s)
    while i < n:
        while i < n and s[i] in ' ,':
            i += 1
        if i >= n:
            break
        if s[i] == "'":
            i += 1
            buf = []
            while i < n:
                ch = s[i]
                if ch == '\\' and i + 1 < n and s[i + 1] == "'":
                    buf.append("'"); i += 2; continue
                if ch == "'":
                    if i + 1 < n and s[i + 1] == "'":
                        buf.append("'"); i += 2; continue
                    i += 1; break
                buf.append(ch); i += 1
            out.append(''.join(buf))
        else:
            j = i
            while j < n and s[j] != ',':
                j += 1
            out.append(s[i:j].strip())
            i = j
    return out


def load_dump(path):
    brands = []                       # brand_id (1-based) -> name
    models_by_rid = collections.defaultdict(collections.Counter)  # rid -> Counter(brand_name)
    model_strings = collections.defaultdict(set)                  # rid -> {model,...}
    keys_by_rid = collections.defaultdict(list)                   # rid -> [(label, hexcode)]
    with open(path, encoding='utf-8', errors='ignore') as f:
        for line in f:
            if not line.startswith('INSERT INTO'):
                continue
            m = TABLE_RE.match(line)
            if not m:
                continue
            table, body = m.group(1), m.group(2)
            if table == 'brands':
                brands.append(split_fields(body)[0])
            elif table == 'models':
                b, mo, rid = split_fields(body)
                rid = int(rid)
                models_by_rid[rid][b] += 1
                model_strings[rid].add(mo)
            elif table == 'keys':
                rid, label, hexcode, proto = split_fields(body)
                p = proto.upper()
                if p in ('NEC', 'NEC2', 'SONY12', 'SONY15', 'SONY20',
                         'RC5', 'RC6', 'JVC'):
                    keys_by_rid[int(rid)].append((label, hexcode, p))
    return brands, models_by_rid, model_strings, keys_by_rid


def nec_frame(hexcode):
    """Validate iodn NEC frame -> (address, command) or None.

    Most iodn NEC rows are the plain 4-byte addr,~addr,cmd,~cmd frame, but a
    minority (e.g. ROMTELECOM DOLCE) carry a two-byte preamble in front of an
    otherwise standard NEC pair — 12 24 30 CF. Accept both: try the whole
    value, then the trailing pair.
    """
    try:
        raw = bytes.fromhex(hexcode)
    except ValueError:
        return None
    for cand in (raw, raw[-4:] if len(raw) == 6 else raw):
        if len(cand) == 4 and cand[1] == (cand[0] ^ 0xFF) and cand[3] == (cand[2] ^ 0xFF):
            return cand[0], cand[2]
    return None


# --- per-protocol decode gates, all verified against iodn's dart encoders ---

def sony_frame(hexcode, addr_bits):
    """iodn SONYxx: packed = cmd(7) | addr << 7 (LSB-first on wire).
    Returns (addr, cmd) or None. addr_bits is 5, 8, or 13."""
    v = int(hexcode, 16)
    cmd = v & 0x7F
    addr = v >> 7
    max_a = (1 << addr_bits) - 1
    if addr > max_a:
        return None
    return addr, cmd


def rc5_frame(hexcode):
    """iodn RC5: 3 hex digits, 12-bit packed field(1) | addr(5) | cmd(6).
    Field=0 means start-bit-2 cleared, a framing Controlix does not emit
    (633 rows, 0.2%) — dropped. Returns (addr, cmd) or None."""
    v = int(hexcode, 16)
    if v > 0xFFF or (v >> 11) == 0: return None
    addr = (v >> 6) & 0x1F
    cmd = v & 0x3F
    return addr, cmd


def rc6_frame(hexcode):
    """iodn RC6: 4 hex digits = raw 16-bit wire payload, emitted MSB-first
    by their encoder. Controlix's RC6 encoder is Flipper/spec LSB-first, so
    bit-reverse each byte at import to reproduce the exact wire frame."""
    v = int(hexcode, 16)
    hi, lo = (v >> 8) & 0xFF, v & 0xFF
    rev = lambda b: int(f"{b:08b}"[::-1], 2)
    return rev(hi), rev(lo)


# UI labels that are junk; keep everything else. iodn uses '-/--' for unknown
# and '??' for unlabeled buttons (3,995 rows).
JUNK_LABELS = {'-/--', '', '-', '--', '?', '??', 'nan', 'none'}


def decode_key(label, hexcode, proto):
    """Return (controlix_proto_id, addr, cmd, carrier, name) or None to drop.
    Every rule here was verified against iodn's own dart encoders."""
    try:
        if proto in ('NEC', 'NEC2'):
            f = nec_frame(hexcode)
            if f is None: return None
            return (1, f[0], f[1], 38000, 'NEC')
        if proto == 'SONY12':
            f = sony_frame(hexcode, 5)
            if f is None: return None
            return (9, f[0], f[1], 40000, 'SIRC')
        if proto == 'SONY15':
            f = sony_frame(hexcode, 8)
            if f is None: return None
            return (10, f[0], f[1], 40000, 'SIRC15')
        if proto == 'SONY20':
            f = sony_frame(hexcode, 13)
            if f is None: return None
            return (11, f[0], f[1], 40000, 'SIRC20')
        if proto == 'RC5':
            f = rc5_frame(hexcode)
            if f is None: return None
            return (6, f[0], f[1], 36000, 'RC5')
        if proto == 'RC6':
            f = rc6_frame(hexcode)
            return (8, f[0], f[1], 36000, 'RC6')
        if proto == 'JVC':
            v = int(hexcode, 16)
            return (15, (v >> 8) & 0xFF, v & 0xFF, 38000, 'JVC')
    except (ValueError, IndexError):
        return None
    return None


def merge(sql_path, controlix_path):
    print(f"Parsing {sql_path} ...")
    brands, models_by_rid, model_strings, keys_by_rid = load_dump(sql_path)
    print(f"  brands={len(brands)} remotes_with_models={len(models_by_rid)} "
          f"remotes_with_nec_keys={len(keys_by_rid)}")

    # Validate NEC keys
    usable = {}   # rid -> [(label, blob)]
    dropped = 0
    label_hist = collections.Counter()
    for rid, entries in keys_by_rid.items():
        rows = []
        for label, hexcode, proto in entries:
            r = decode_key(label, hexcode, proto)
            if r is None:
                dropped += 1
                continue
            pid, addr, cmd, carrier, name = r
            rows.append((label, carrier, struct.pack('<III', pid, addr, cmd), name))
        if rows:
            usable[rid] = rows
    for rows in usable.values():
        for label, _carrier, _blob, _name in rows:
            label_hist[label] += 1
    kept = sum(len(v) for v in usable.values())
    junk = sum(n for lab, n in label_hist.items() if lab.strip() in JUNK_LABELS)
    print(f"  validated={kept} dropped={dropped} junk-labels={junk}")
    print(f"  top labels: {label_hist.most_common(12)}")

    db = sqlite3.connect(controlix_path)
    c = db.cursor()
    c.execute("BEGIN")

    c.execute("SELECT id FROM category WHERE slug='iodn_irblaster'")
    row = c.fetchone()
    if row:
        cat_id = row[0]
        print(f"  category exists: id={cat_id} — clearing previous import")
        c.execute("""DELETE FROM button WHERE remote_id IN
                     (SELECT r.id FROM remote r JOIN brand b ON b.id=r.brand_id
                      WHERE b.category_id=?)""", (cat_id,))
        c.execute("""DELETE FROM remote_model WHERE remote_id IN
                     (SELECT r.id FROM remote r JOIN brand b ON b.id=r.brand_id
                      WHERE b.category_id=?)""", (cat_id,))
        c.execute("""DELETE FROM remote WHERE brand_id IN
                     (SELECT id FROM brand WHERE category_id=?)""", (cat_id,))
        c.execute("DELETE FROM brand WHERE category_id=?", (cat_id,))
    else:
        c.execute("INSERT INTO category(name,slug) VALUES('IR Blaster (community)','iodn_irblaster')")
        cat_id = c.lastrowid

    brand_ids = {}    # brand name -> controlix brand id (within this category)
    n_remote = n_button = n_model = 0
    proto_hist = collections.Counter()

    for rid, rows in usable.items():
        # Canonical brand: most frequent among model rows, fallback 'Unknown'
        cnt = models_by_rid.get(rid)
        bname = cnt.most_common(1)[0][0] if cnt else 'Unknown'
        bid = brand_ids.get(bname)
        if bid is None:
            c.execute("INSERT INTO brand(category_id,name) VALUES(?,?)", (cat_id, bname))
            bid = c.lastrowid
            brand_ids[bname] = bid

        c.execute("""INSERT INTO remote(brand_id,file_name,model_name,source,source_path)
                     VALUES(?,?,?,?,?)""",
                  (bid, f"irblaster-{rid}.ir", bname, 'irblaster', f"iodn/android-ir-blaster@master keys.{rid}"))
        new_rid = c.lastrowid
        n_remote += 1

        seen_labels = set()
        # A remote whose every label is junk ('??') still holds real codes —
        # LUNEAU's whole remote is unlabeled RC5. Rather than ship an empty
        # remote, name those buttons by their protocol and command number, and
        # let the UI's own "Button N" convention carry the rest.
        usable = [r for r in rows if r[0].strip() and r[0].strip() not in JUNK_LABELS]
        fallback = not usable
        for label, carrier, blob, name in rows:
            lab = label.strip()
            if not lab or lab in JUNK_LABELS:
                if not fallback:
                    continue
                lab = f"{name} {blob[8] | (blob[9] << 8)}"
            if lab in seen_labels:
                continue
            seen_labels.add(lab)
            c.execute("INSERT INTO button(remote_id,name,carrier_hz,pattern,protocol) VALUES(?,?,?,?,?)",
                      (new_rid, lab, carrier, blob, name))
            proto_hist[name] += 1
            n_button += 1

        for mo in model_strings.get(rid, ()):
            if mo.strip():
                c.execute("INSERT OR IGNORE INTO remote_model(remote_id,model) VALUES(?,?)",
                          (new_rid, mo))
                n_model += 1

    db.commit()

    c.execute("SELECT COUNT(*) FROM brand WHERE category_id=?", (cat_id,))
    ib = c.fetchone()[0]
    c.execute("SELECT COUNT(*) FROM button")
    tb = c.fetchone()[0]
    c.execute("SELECT COUNT(*) FROM remote_model")
    tm = c.fetchone()[0]
    print(f"\n=== merge results ===")
    print(f"iodn brands imported : {ib}")
    print(f"remotes created      : {n_remote}")
    print(f"buttons created      : {n_button}")
    print(f"model strings indexed: {n_model} (table now {tm})")
    print(f"protocols imported     : {dict(proto_hist.most_common())}")
    print(f"TOTAL DB buttons     : {tb}")
    db.close()
    print(f"DB size: {os.path.getsize(controlix_path)/1e6:.1f} MB")


if __name__ == '__main__':
    if len(sys.argv) != 3:
        print(__doc__)
        sys.exit(1)
    merge(sys.argv[1], sys.argv[2])
