# Controlix

An Android IR remote app that stays out of your way: offline, no account, no ads, no
location, no TV guide. Point the phone, press the button, the device responds.

Built for phones with an IR blaster (Xiaomi/Redmi/Poco, Huawei/Honor, vivo, older
Samsung/HTC and others).

## Status

Early. Milestone 0 (scaffold + CI) complete. Milestone 1 (IR transmit verified on
real hardware) in progress.

## Non-goals

- **iOS.** No iPhone has ever shipped an IR blaster. An iOS build would be a
  Wi-Fi-only companion app with no reason to exist.
- **Learning mode.** Android's `ConsumerIrManager` is transmit-only on every
  consumer phone. The hardware cannot receive or decode an incoming IR signal,
  so "aim your old remote at the phone" is not implementable. Codes come from
  databases plus manual raw-timing entry.
- **Accounts, telemetry, ads, location, content feeds.** Permanent.

## Features

### Parity with existing remote apps

- Brand → device type → remote picker
- Layouts for TV, AC, set-top box, projector, audio
- AC remotes with temperature, mode, fan speed
- Favourites, recently used
- Fully offline once a remote is added

### What Controlix adds

- **Macros** — multi-device sequences with per-step delays ("movie time" =
  TV on, receiver to HDMI 2, AC to 24 °C).
- **Quick-settings tile** — the most-used remote one swipe from anywhere.
- **Intent API** — `controlix://transmit?remote=<id>&key=Vol_up` for Tasker,
  MacroDroid, and automation apps.
- **QR / URI remote sharing** — export a configured remote as a scannable code.
- **Community-updatable code database** — contribute codes by pull request,
  the same model that made irdb work.
- **Exact-model search across the whole database** — type "UN55D6000", get the
  remote whose header lists that model.
- **Zero network permission by default.** DB refresh is opt-in and is the only
  reason the app ever opens a socket.

## Building

Requires JDK 17+ and an Android SDK with platform 35.

```bash
./gradlew assembleDebug          # debug APK
./gradlew testDebugUnitTest      # unit tests
./gradlew assembleRelease        # release APK (unsigned)
```

`local.properties` must point at your SDK:

```properties
sdk.dir=/path/to/Android/Sdk
```

## Architecture

```
app/src/main/java/com/erfanbagheri/controlix/
├── MainActivity.kt              Compose UI entry
└── ir/
    ├── IrTransmitter.kt         ConsumerIrManager wrapper (the only hardware surface)
    ├── ProntoParser.kt          Pronto Hex -> carrier + microsecond pattern
    └── (planned) protocols/     NEC, Samsung32, RC5/RC6, SIRC encoders
```

The app's entire hardware dependency is `ConsumerIrManager.transmit(carrierHz, pattern)`.
Everything else is data and UI.

## Data

Codes come from crowd-sourced, permissively licensed databases:

- [Flipper-IRDB](https://github.com/logickworkshop/Flipper-IRDB) — ~50 device
  categories, 118 TV brands alone, `.ir` files in parsed or raw form.
- [irdb](https://github.com/probonopd/irdb) — protocol/device/function notation,
  3205 manufacturers. Imported with `src/main/python/merge_irdb.py`.
- [lirc-remotes](https://github.com/probonopd/lirc-remotes) — the classic LIRC
  community database (2646 remotes), same author as irdb. Structured
  `<decoding protocol/device/obc>` tags become parsed blobs via the irdb
  protocol map; exotic `Gap-*`/`Async*` timings decode from their
  Pronto-NX `<ccf>` waveforms to raw blobs. Categories come from a
  button-name fingerprint (LIRC folders are brands, not device types).
  Imported with `src/main/python/merge_lirc.py`.

Contains data from irdb and lirc-remotes by Simon Peter and contributors,
used under permission. For licensing details and for information on how
to contribute to the database, see https://github.com/probonopd/irdb

See [docs/DB.md](docs/DB.md) for the pipeline.

## License

TBD before first release.
