# Controlix plan

## Product definition

An Android IR remote that is offline-first, permission-minimal, and
automation-friendly. Target user: someone with an IR-equipped phone (mostly
Xiaomi/Redmi/Poco, Huawei/Honor) who is tired of Mi Remote's ads, account
requirement, location permission, and content bloat.

## Non-goals

iOS, learning mode (hardware impossible), accounts, telemetry, ads, content feeds.

## Differentiators

| Feature | Mi Remote | irplus | SURE | IReDroid | Controlix |
|---|---|---|---|---|---|
| Macros (multi-device + delays) | no | no | partial | no | yes |
| Quick-settings tile | no | no | no | no | yes |
| Intent/Tasker API | no | partial | no | no | yes |
| QR/URI remote sharing | no | no | no | no | yes |
| Zero permissions beyond TRANSMIT_IR | no | yes | no | yes | yes |
| No ads / account / location | no | yes | no | yes | yes |
| Community-updatable codes | no | no | no | partial | yes |
| Exact-model search | no | no | no | no | yes |

## Milestones

### M0 — Scaffold and CI
- [x] Gradle + Kotlin + Compose project
- [x] `ConsumerIrManager` wrapper
- [x] Pronto Hex parser + unit tests
- [x] GitHub Actions: build, test, artifact
- [x] Release workflow with tag trigger

### M1 — IR transmit verified on hardware
- [ ] Install debug APK on Poco X3 Pro (vayu)
- [ ] Confirm `hasIrEmitter()` returns true
- [ ] Self-test burst, verify with a real device (phone camera shows IR)
- [ ] Transmit a real NEC code at a TV

### M2 — Database and picker
- [ ] Protocol encoders: NEC, Samsung32, RC5, RC6, SIRC
- [ ] Flipper-IRDB converter script
- [ ] SQLite schema + Room (or raw SQLite) data layer
- [ ] Category → brand → remote → button UI
- [ ] Exact-model search

### M3 — Daily-driver features
- [ ] AC remote layouts (temp/mode/fan)
- [ ] Favourites and recents
- [ ] Custom button layouts
- [ ] Macros engine

### M4 — Integration
- [ ] Quick-settings tile
- [ ] `controlix://` intent API
- [ ] QR export/import

### M5 — Distribution
- [ ] Optional CDN DB refresh
- [ ] Contribution docs and PR flow
- [ ] F-Droid metadata
- [ ] License decision

### M6 — Design pass
- [ ] Apply the `anthropic-frontend-design` skill for the visual layer

## Open decisions

- **License.** GPLv3 matches the ecosystem and guarantees the code stays open.
  Apache-2.0 is friendlier to reuse. Decide before first release.
- **Room vs raw SQLite.** Room adds a compile-time dependency and annotation
  processing. A read-mostly DB of static data may not need it. Leaning raw
  SQLite with hand-written queries.
- **Bundled DB vs download-on-first-run.** Bundling means a bigger APK but a
  working app the instant it installs. Leaning bundle, with refresh on top.
- **INTERNET permission.** Only needed for refresh. Options: omit entirely in
  the main flavour, or declare it and document it. Leaning omit from the
  default build, ship a separate flavour with refresh enabled.
