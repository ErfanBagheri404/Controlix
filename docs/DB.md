# Code database pipeline

## Decision: local, git-versioned, no server

Codes are static public data that changes slowly. A hosted database
(Supabase or otherwise) would add auth, cost, network latency, and an online
dependency to an app whose entire pitch is "works offline with no account."

Instead:

```
Flipper-IRDB + irdb  ->  converter (CI)  ->  controlix.db  ->  bundled in APK
                                                  |
                                                  +->  optional CDN refresh
```

- **Bundled SQLite** ships in the APK. App is fully functional with no network.
- **Refresh** is opt-in and pulls a newer `controlix.db` from GitHub Releases or
  jsdelivr. This is the only reason `INTERNET` would ever be requested, and the
  permission is only declared in the flavour that enables it.
- **Contribution** is a pull request against Controlix's own `codes/` directory.
  That is exactly how irdb and Flipper-IRDB grew.

### Why not Supabase

| Concern | Bundled SQLite | Supabase |
|---|---|---|
| Offline | Yes, by construction | Needs local cache layer anyway |
| Cost | 0 | Free tier then paid |
| Latency to first transmit | 0 ms | Network round trip |
| Auth | None | Account, keys, RLS |
| F-Droid reproducible build | Yes | Remote dependency |
| Data versioning / review | Git history, PRs | Row history, harder to review |

A server is the right answer for user-generated per-user data. Remote control
codes are neither per-user nor mutable.

## Source format: Flipper `.ir`

Two entry types:

```yaml
name: Power
type: parsed
protocol: Samsung32
address: 07 00 00 00
command: 02 00 00 00
```

```yaml
name: Fan_2
type: raw
frequency: 38000
duty_cycle: 0.330000
data: 8983 3497 180 797 563 567 ...
```

- `type: raw` maps **directly** to `ConsumerIrManager.transmit(frequency, data)`.
  No work needed beyond parsing.
- `type: parsed` needs a protocol encoder: turn (protocol, address, command)
  into an on/off timing pattern. This is the real engineering task.

## Required protocol encoders

Ranked by how many database entries depend on them:

1. **NEC** and **NECext** — the majority of consumer devices
2. **Samsung32** — Samsung TVs and monitors, very common
3. **RC5**, **RC6** — Philips and many European brands
4. **SIRC** (Sony 12/15/20-bit)
5. **RCMM**, **Kaseikyo/Panasonic**, **JVC**, **NEC42**
6. **Pioneer**, **Denon**, **Sharp**, **RC5X**

Each encoder is a pure function `(address, command) -> IntArray` of
microseconds, which makes them unit-testable without hardware.

## Conversion steps

1. Sparse-clone Flipper-IRDB and irdb (CI job, cached).
2. Walk the category/brand tree, parse `.ir` files.
3. For `raw` entries: emit `(carrier, pattern)` directly.
4. For `parsed` entries: run the matching protocol encoder.
5. Parse the `# Compatible TV Models:` header comment into a model list so
   exact-model search works.
6. Write to SQLite.

## Proposed schema

```sql
CREATE TABLE category (
  id        INTEGER PRIMARY KEY,
  slug      TEXT NOT NULL UNIQUE,     -- "tv", "ac", "stb"
  name      TEXT NOT NULL
);

CREATE TABLE brand (
  id        INTEGER PRIMARY KEY,
  category_id INTEGER NOT NULL REFERENCES category(id),
  name      TEXT NOT NULL,
  UNIQUE(category_id, name)
);

CREATE TABLE remote (
  id        INTEGER PRIMARY KEY,
  brand_id  INTEGER NOT NULL REFERENCES brand(id),
  file_name TEXT NOT NULL,            -- "Samsung_AA59-00443A.ir"
  model_name TEXT,                    -- "AA59-00443A"
  source    TEXT NOT NULL,            -- "flipper" | "irdb" | "controlix"
  source_path TEXT
);

CREATE TABLE remote_model (           -- one remote, many compatible TV models
  remote_id INTEGER NOT NULL REFERENCES remote(id),
  model     TEXT NOT NULL,
  PRIMARY KEY (remote_id, model)
);

CREATE TABLE button (
  id        INTEGER PRIMARY KEY,
  remote_id INTEGER NOT NULL REFERENCES remote(id),
  name      TEXT NOT NULL,            -- "Power", "Vol_up"
  carrier_hz INTEGER NOT NULL,
  pattern   BLOB NOT NULL,            -- varint-encoded IntArray, microseconds
  protocol  TEXT,                     -- NULL for raw entries
  UNIQUE(remote_id, name)
);

CREATE INDEX idx_remote_model ON remote_model(model);
CREATE INDEX idx_remote_brand ON remote(remote_id, brand_id);
```

`pattern` as a packed blob keeps the DB small; a typical NEC pattern is 68
integers, which is 136 bytes as a blob versus ~600 as JSON.

## Size estimates

> Actual built DB (2026-09-13, `src/main/python/convert_irdb.py` against
> Flipper-IRDB @ dev): 46 categories, 1,019 brands, 1,950 remotes,
> 35,908 buttons (7,793 raw + 28,115 parsed), 83 model strings,
> 7.1 MB packed (2.1 MB inside the APK's compressed assets).

> **iodn merge** (`src/main/python/merge_irblaster.py`, same date): adds
> the GPL-3.0 database bundled in [iodn/android-ir-blaster]'s APK assets
> (`assets/db_src/irblaster.sql`, 413k keys). Only structurally validated
> frames import: NEC/NEC2 (byte-exact complement), SONY12/15/20 (packed
> `cmd(7) | addr<<7`, width-checked), RC5 (12-bit `field|addr5|cmd6`,
> field=1 only), RC6 (16-bit payload, byte-reversed to match this repo's
> LSB-first encoder — verified segment-by-segment against iodn's own
> dart encoder output), JVC (16-bit addr|cmd). Ambiguous families
> (RECS80/REC80/RCC*/proprietary learned codes) are skipped: a wrong
> code is worse than a missing code. Merged totals land at 192k+ buttons,
> 2,100+ brands, 56k+ model strings, ~22.6 MB raw / ~9 MB in-APK.

[iodn/android-ir-blaster]: https://github.com/iodn/android-ir-blaster

> **lirc-remotes merge** (`src/main/python/merge_lirc.py`, 2026-09-15): the
> classic LIRC community database ([probonopd/lirc-remotes], 2646 XML files,
> 547 brand folders). Each `<code>` carries both a structured `<decoding
> protocol/device/subdevice/obc>` tag and a learned Pronto-NX `<ccf>`
> waveform. Where the protocol is in the irdb `PROTO_MAP` the decoding tags
> become compact 12-byte parsed blobs; everything else (Gap-*, Async*, IODATA,
> AirB* — mostly exotic A/V brands) falls back to its ccf waveform decoded to
> raw microsecond pairs. 113,307 buttons land; merged totals: **450,325
> buttons, 13,110 remotes, 3,811 brands, 157,453 model strings, ~50 MB
> (VACUUMed)**. Category assignment is a button-name fingerprint (LIRC folders
> are brands, not device types): key sets like {temp, cool, swing} → ACs,
> {eject, angle, subtitle} → DVD players, and so on.

[probonopd/lirc-remotes]: https://github.com/probonopd/lirc-remotes

Flipper-IRDB is ~42 MB as source text. After stripping comments, deduplicating
identical patterns, and packing to blobs, expect roughly 8-15 MB. That is
acceptable to bundle, and the refresh path exists for anyone who wants the
newest codes without an app update.

## Contributing codes back

"Missing a code?" (drawer, or the ritual's missing-button link) opens a flat
form — brand, category, remote name, button, carrier Hz, microsecond pattern,
source note — with live typed validation. A valid form is bundled as JSON and
handed to the system share sheet (`ACTION_SEND`, `.json` attachment, prefilled
body). That is the whole pipeline: no background network, no repo API, no token
— the app's main flavor requests no `INTERNET` permission at all. A maintainer
merges accepted bundles into the next DB build.

Consumer-IR phones only transmit, so Controlix cannot learn a code. The source
note must name a real, redistributable origin (your own remote, a public irdb
dump). The app refuses to package a submission whose provenance is blank,
unknown, or proprietary — Mi Remote data is proprietary and never redistributed
(see "Legal note"). A refusal names the reason in the rejection message itself.

Maintainer JSON shape (`format: controlix-contribution-v1`):

```json
{
  "format": "controlix-contribution-v1",
  "brand": "Sony",
  "category": "tvs",
  "remoteName": "RM-ED009",
  "provenance": "Public irdb dump; retested on the owner's own remote",
  "appVersion": "0.1.0",
  "buttons": [
    { "buttonName": "Power", "carrierHz": 38000, "pattern": "900 451 560 451 ..." }
  ]
}
```

Validation bounds follow the bundled DB: carrier 20k–60k Hz (observed
31,886–58,382), even element count ≥ 4, durations 10µs–60s.

## Known-empty brands: why they stay

Ten brands carry no remotes or buttons. This is a hardware limit, not a
data gap, and the rows are kept so the coverage checklist stays honest.

**Not transmittable by an IR LED at all** — the remotes are RF, serial, or
virtual devices:

| Brand | Device | Why no phone can send it |
|-------|--------|--------------------------|
| Atiusb | ATI Remote Wonder | 433 MHz RF, works through walls (AMD install guide) |
| X10 | MouseRemote mk19a, MP3 Anywhere UR81A | X10 310/433 MHz RF into a serial-port receiver |
| Shannon | Se Jin SWK-8630 IR keyboard | 1200-baud serial protocol, no consumer-IR waveform |
| Devinput | — | not a device: Linux input-event numbers, no waveform exists |

**Genuine IR, hardware-decoded on receive** — the conf publishes only
scancodes, never a pulse/space table, so there is nothing to encode:

| Brand | Device | Published data |
|-------|--------|----------------|
| Bestbuy | Easy TV (BT848/BT878) tuner remote | 5-bit scancodes, `one 0 0` |
| Chronos | Video Shuttle II (BTTV id 0x23) | 5-bit scancodes, no timing lines at all |
| Hercules | SmartTV Stereo (lirc_i2c PV951) | 16-bit `0x61D6`+key, timing undocumented |
| Kanam Accent | IR-100 | 1200 8N1 serial frames, not IR framing |
| Knc One | Anubis Typhoon TView | 8-bit scancodes, `one 0 0` |
| Tekram | M230 (Mach64) | conf states "commands are hardware-decoded" |

`one 0 0` in an lirc conf means the receiver chip or a kernel driver decodes
the signal and lircd is only told the resulting code. There is no waveform to
transmit, so importing one would mean inventing timings that cannot work.

The fix, if it ever comes, is user-supplied codes through the contribute flow
above — not a fabricated table.

## Legal note

Only permissively licensed sources go in this database. Flipper-IRDB and irdb
are crowd-sourced and redistributable. Mi Remote's code database is
proprietary and must not be extracted or redistributed. Mi Remote's brand list
may be used as a *coverage checklist* — a note of which brands matter to users
— but never as a source of code data.
