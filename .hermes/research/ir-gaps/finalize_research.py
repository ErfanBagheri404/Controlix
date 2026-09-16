import json
import pathlib
import sqlite3

ROOT = pathlib.Path(__file__).resolve().parent
DB = ROOT.parents[2] / 'app/src/main/assets/controlix.db'
conn = sqlite3.connect(DB)
# Remove only the incomplete reference profile inserted during this research.
row = conn.execute("SELECT r.id,b.id FROM remote r JOIN brand b ON b.id=r.brand_id WHERE b.name='Whirlpool' AND r.file_name='ir_Whirlpool_test.cpp' AND r.source='IRremoteESP8266' AND r.model_name='DG11J13A reference captures'").fetchone()
if row:
    rid, bid = row
    names = {r[0] for r in conn.execute('SELECT name FROM button WHERE remote_id=?', (rid,))}
    assert names == {'Auto 25C reference', 'Dry 25C timer'}, names
    with conn:
        conn.execute('DELETE FROM button WHERE remote_id=?', (rid,))
        conn.execute('DELETE FROM remote_model WHERE remote_id=?', (rid,))
        conn.execute('DELETE FROM remote WHERE id=?', (rid,))
        conn.execute('DELETE FROM brand WHERE id=? AND NOT EXISTS (SELECT 1 FROM remote WHERE brand_id=?)', (bid,bid))
    print('Removed only incomplete Whirlpool research profile; validated .ir files retained.')
assert conn.execute('PRAGMA integrity_check').fetchone()[0] == 'ok'
assert not conn.execute('PRAGMA foreign_key_check').fetchall()
print('DB integrity: OK; buttons:',conn.execute('SELECT COUNT(*) FROM button').fetchone()[0])

def profiles(brand):
    return [dict(zip(['category','model','source','path','buttons'],r)) for r in conn.execute('''SELECT c.slug,r.model_name,r.source,r.source_path,(SELECT COUNT(*) FROM button x WHERE x.remote_id=r.id) FROM remote r JOIN brand b ON b.id=r.brand_id JOIN category c ON c.id=b.category_id WHERE lower(b.name)=lower(?)''',(brand,))]
rows = [
 {'name':'Whirlpool','status':'New validated captures; not production-ready','details':'Two DG11J13A AC reference captures, 343 timings each at 38 kHz. Independent decode, checksums, converter roundtrip and SQLite smoke test passed. Neither is power toggle; one sets timers. Needs stateful AC controls or better captures before setup can use it. Not hardware-tested.','sources':['https://github.com/crankyoldgit/IRremoteESP8266/blob/master/test/ir_Whirlpool_test.cpp','https://github.com/crankyoldgit/IRremoteESP8266/blob/master/LICENSE.txt'],'license':'Upstream LGPL-2.1; preserve license and attribution when distributing.'},
 {'name':'Gorenje','status':'Existing under community category','details':'Two profiles, 85 buttons total, under GORENJE / iodn_irblaster. Not enough model evidence to assign both to ACs.','profiles':profiles('GORENJE'),'sources':['https://github.com/iodn/android-ir-blaster']},
 {'name':'OnePlus','status':'Unresolved','details':'No matching code files in inspected Flipper-IRDB tree. Bluetooth listed in Y-series specs does not rule out IR power or other models. Exact TV model and a capture/source still needed. Do not substitute arbitrary Android TV codes.','sources':['https://www.oneplus.in/tv-y-series','https://github.com/Lucaslhm/Flipper-IRDB']},
 {'name':'Realme','status':'Unresolved','details':'No matching code files in inspected Flipper-IRDB tree; attempted official TV specification URL was unavailable. IR capability and codes remain unverified, not declared absent.','sources':['https://github.com/Lucaslhm/Flipper-IRDB','https://www.realme.com/in/realme-smart-tv/specs']},
 {'name':'Google','status':'Hardware-specific, not a generic IR brand','details':'Google TV Streamer voice remote uses Bluetooth for streamer control and IR for external TVs/receivers/soundbars. This does not provide streamer-receive IR codes. No new suitable code set found.','sources':['https://support.google.com/chromecast/answer/3046409?hl=en']},
 {'name':'Chromecast','status':'Not a generic missing IR set','details':'Chromecast voice remote IR is for the attached TV/receiver/soundbar, not evidence of an IR receiver in the Chromecast. Use attached equipment codes; direct streamer navigation needs a different transport.','sources':['https://support.google.com/chromecast/answer/3046409?hl=en']},
 {'name':'Fire TV','status':'Fire TV television codes already present; distinguish sticks','details':'Amazon Omni (33 buttons), Toshiba FireTV (1 and 17 buttons), Amazon soundbar EVG487 (12) already present. A four-button Stick-labelled file also exists, but capture naming does not prove the stick receives IR; do not advertise stick navigation support from it.','sources':['https://github.com/Lucaslhm/Flipper-IRDB/blob/main/TVs/Amazon/FireTV_Omni_Series_4K.ir','https://developer.amazon.com/docs/device-specs/device-specifications-fire-tv-streaming-media-player.html']},
 {'name':'Kodi','status':'Host/receiver-specific','details':'Kodi is software. IR codes belong to the host hardware or USB IR receiver and its mappings, not a universal Kodi brand. Existing MCE-family records are not guaranteed to match a particular receiver.','sources':['https://kodi.wiki/view/Remote_controls']},
 {'name':'Dish','status':'Existing','details':'Dish Network: 30 cable-box profiles. Also three Dishnet profiles currently miscategorized as DVD players. No new code import needed for those records.','profiles':profiles('Dish Network'),'sources':['https://github.com/irdb/irdb/tree/master/codes/Dish%20Network','https://github.com/probonopd/lirc-remotes/tree/master/dishnet']},
 {'name':'Verizon','status':'Existing OEM profiles; discoverability gap','details':'Motorola QIP2500 and QIP6200-2 records exist. QIP2500 is filed under TV/miscellaneous rather than a Verizon cable-box entry. A specifically named Verizon FIOS QIP2500 capture also exists in Flipper converted Pronto data. Matching model only; no blanket compatibility promise. No alias migration applied in this research.','sources':['https://github.com/Lucaslhm/Flipper-IRDB/blob/main/_Converted_/Pronto/M/Motorola/QIP2500_-_Verizon_FIOS.ir','https://github.com/probonopd/lirc-remotes/blob/master/motorola/QIP2500.xml','https://github.com/probonopd/lirc-remotes/blob/master/motorola/QIP6200-2.xml']},
 {'name':'Spectrum','status':'Unresolved exact receiver model','details':'DIGITAL SPECTRUM is not Charter/Spectrum cable. Existing Time Warner Cable and OEM records may help with legacy equipment, but brand lineage alone is not compatibility proof. No newly verified Spectrum-labelled set found in inspected sources. Need box/remote model.','sources':['https://github.com/probonopd/lirc-remotes/tree/master/time_warner_cable','https://github.com/Lucaslhm/Flipper-IRDB']},
]
assert len(rows)==11 and len({r['name'] for r in rows})==11
report={'scope':'All 11 names from previous gap list, not an exhaustive global IR-code search','production_additions':0,'hardware_tested':False,'mi_remote_codes_used':False,'results':rows}
(ROOT/'coverage-report.json').write_text(json.dumps(report,indent=2))
md=['# Controlix IR coverage hunt','',report['scope']+'.','', 'No proprietary Mi Remote codes used. No new production profiles shipped. Two Whirlpool research captures validated in software, not on hardware.','']
for r in rows:
    md += ['## '+r['name']+' — '+r['status'],'',r['details'],'']
    md += ['- '+s for s in r['sources']]+['']
md += ['## Validation artifacts','','Run `python .hermes/research/ir-gaps/validate_whirlpool.py` from the repository root. This tests the two upstream captures in isolation and does not modify the bundled DB.','', 'Files: `RealExampleDecode.ir`, `RealTimerExample.ir`, `whirlpool-validation.json`.']
(ROOT/'coverage-report.md').write_text('\n'.join(md))
print('Coverage report verified: 11/11 names. Production additions: 0. Research captures: 2.')
