#!/usr/bin/env python3
"""
Merge iodn/android-ir-blaster's GPL-3.0 database into Controlix's bundled DB.

Design (from structural analysis of irblaster.sql):
  - 9,388 remotes; models is a (brand, model) -> remote_id map with up to
    thousands of models per remote, and 3,934 remotes shared across brands.
    So: ONE Controlix remote per iodn remote_id; canonical brand = most
    frequent brand among its model rows; ALL model strings go into
    remote_model for exact-model search.
  - Only NEC-family keys are imported, and only when the 32-bit hex frame
    passes byte-exact addr,~addr,cmd,~cmd validation (169,691/274,082 rows
    do). RC5/LIRC-style hexes have ambiguous bit-packing: skipped.
  - Device type per remote is unknown (iodn has no category link), so these
    remotes are deliberately EXCLUDED from the power-off sweep, which stays
    on type-known Flipper TVs.

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
                if proto.upper() == 'NEC':
                    keys_by_rid[int(rid)].append((label, hexcode))
    return brands, models_by_rid, model_strings, keys_by_rid


def nec_frame(hexcode):
    """Validate iodn NEC frame -> (address, command) or None."""
    try:
        raw = bytes.fromhex(hexcode)
    except ValueError:
        return None
    if len(raw) != 4:
        return None
    if raw[1] != (raw[0] ^ 0xFF) or raw[3] != (raw[2] ^ 0xFF):
        return None
    return raw[0], raw[2]


# UI labels that are junk; keep everything else. iodn uses '-/--' for unknown
# and '??' for unlabeled buttons (3,995 rows).
JUNK_LABELS = {'-/--', '', '-', '--', '?', '??', 'nan', 'none'}


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
        for label, hexcode in entries:
            f = nec_frame(hexcode)
            if f is None:
                dropped += 1
                continue
            addr, cmd = f
            rows.append((label, struct.pack('<III', 1, addr, cmd)))
        if rows:
            usable[rid] = rows
    for rows in usable.values():
        for label, _ in rows:
            label_hist[label] += 1
    kept = sum(len(v) for v in usable.values())
    junk = sum(n for lab, n in label_hist.items() if lab.strip() in JUNK_LABELS)
    print(f"  NEC validated={kept} dropped={dropped} junk-labels={junk}")
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
        for label, blob in rows:
            lab = label.strip()
            if not lab or lab in JUNK_LABELS or lab in seen_labels:
                continue
            seen_labels.add(lab)
            c.execute("INSERT INTO button(remote_id,name,carrier_hz,pattern,protocol) VALUES(?,?,?,?,?)",
                      (new_rid, lab, 38000, blob, 'NEC'))
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
    print(f"TOTAL DB buttons     : {tb}")
    db.close()
    print(f"DB size: {os.path.getsize(controlix_path)/1e6:.1f} MB")


if __name__ == '__main__':
    if len(sys.argv) != 3:
        print(__doc__)
        sys.exit(1)
    merge(sys.argv[1], sys.argv[2])
