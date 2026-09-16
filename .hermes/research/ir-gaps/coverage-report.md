# Controlix IR coverage hunt

All 11 names from previous gap list, not an exhaustive global IR-code search.

No proprietary Mi Remote codes used. No new production profiles shipped. Two Whirlpool research captures validated in software, not on hardware.

## Whirlpool — New validated captures; not production-ready

Two DG11J13A AC reference captures, 343 timings each at 38 kHz. Independent decode, checksums, converter roundtrip and SQLite smoke test passed. Neither is power toggle; one sets timers. Needs stateful AC controls or better captures before setup can use it. Not hardware-tested.

- https://github.com/crankyoldgit/IRremoteESP8266/blob/master/test/ir_Whirlpool_test.cpp
- https://github.com/crankyoldgit/IRremoteESP8266/blob/master/LICENSE.txt

## Gorenje — Existing under community category

Two profiles, 85 buttons total, under GORENJE / iodn_irblaster. Not enough model evidence to assign both to ACs.

- https://github.com/iodn/android-ir-blaster

## OnePlus — Unresolved

No matching code files in inspected Flipper-IRDB tree. Bluetooth listed in Y-series specs does not rule out IR power or other models. Exact TV model and a capture/source still needed. Do not substitute arbitrary Android TV codes.

- https://www.oneplus.in/tv-y-series
- https://github.com/Lucaslhm/Flipper-IRDB

## Realme — Unresolved

No matching code files in inspected Flipper-IRDB tree; attempted official TV specification URL was unavailable. IR capability and codes remain unverified, not declared absent.

- https://github.com/Lucaslhm/Flipper-IRDB
- https://www.realme.com/in/realme-smart-tv/specs

## Google — Hardware-specific, not a generic IR brand

Google TV Streamer voice remote uses Bluetooth for streamer control and IR for external TVs/receivers/soundbars. This does not provide streamer-receive IR codes. No new suitable code set found.

- https://support.google.com/chromecast/answer/3046409?hl=en

## Chromecast — Not a generic missing IR set

Chromecast voice remote IR is for the attached TV/receiver/soundbar, not evidence of an IR receiver in the Chromecast. Use attached equipment codes; direct streamer navigation needs a different transport.

- https://support.google.com/chromecast/answer/3046409?hl=en

## Fire TV — Fire TV television codes already present; distinguish sticks

Amazon Omni (33 buttons), Toshiba FireTV (1 and 17 buttons), Amazon soundbar EVG487 (12) already present. A four-button Stick-labelled file also exists, but capture naming does not prove the stick receives IR; do not advertise stick navigation support from it.

- https://github.com/Lucaslhm/Flipper-IRDB/blob/main/TVs/Amazon/FireTV_Omni_Series_4K.ir
- https://developer.amazon.com/docs/device-specs/device-specifications-fire-tv-streaming-media-player.html

## Kodi — Host/receiver-specific

Kodi is software. IR codes belong to the host hardware or USB IR receiver and its mappings, not a universal Kodi brand. Existing MCE-family records are not guaranteed to match a particular receiver.

- https://kodi.wiki/view/Remote_controls

## Dish — Existing

Dish Network: 30 cable-box profiles. Also three Dishnet profiles currently miscategorized as DVD players. No new code import needed for those records.

- https://github.com/irdb/irdb/tree/master/codes/Dish%20Network
- https://github.com/probonopd/lirc-remotes/tree/master/dishnet

## Verizon — Existing OEM profiles; discoverability gap

Motorola QIP2500 and QIP6200-2 records exist. QIP2500 is filed under TV/miscellaneous rather than a Verizon cable-box entry. A specifically named Verizon FIOS QIP2500 capture also exists in Flipper converted Pronto data. Matching model only; no blanket compatibility promise. No alias migration applied in this research.

- https://github.com/Lucaslhm/Flipper-IRDB/blob/main/_Converted_/Pronto/M/Motorola/QIP2500_-_Verizon_FIOS.ir
- https://github.com/probonopd/lirc-remotes/blob/master/motorola/QIP2500.xml
- https://github.com/probonopd/lirc-remotes/blob/master/motorola/QIP6200-2.xml

## Spectrum — Unresolved exact receiver model

DIGITAL SPECTRUM is not Charter/Spectrum cable. Existing Time Warner Cable and OEM records may help with legacy equipment, but brand lineage alone is not compatibility proof. No newly verified Spectrum-labelled set found in inspected sources. Need box/remote model.

- https://github.com/probonopd/lirc-remotes/tree/master/time_warner_cable
- https://github.com/Lucaslhm/Flipper-IRDB

## Validation artifacts

Run `python .hermes/research/ir-gaps/validate_whirlpool.py` from the repository root. This tests the two upstream captures in isolation and does not modify the bundled DB.

Files: `RealExampleDecode.ir`, `RealTimerExample.ir`, `whirlpool-validation.json`.