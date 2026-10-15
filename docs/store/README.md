# Controlix — Store Publishing Checklist

Status as of **16 October 2026**, before the v0.0.32 release.
Everything here has been verified against Myket's published rules
(<https://myket.ir/kb/topics/myket-app-publishing-rules-fa/>).

## Files in this folder

| File | What it is |
| --- | --- |
| `LISTING.md` | Ready-to-paste Persian + English title, tagline, description, category, contact info, and the panel fields for Myket. |
| `../PRIVACY-POLICY.md` | Full privacy policy. Publish it somewhere reachable by URL (GitHub Pages, your site, or a GitHub blob link) and paste that URL into Myket's privacy field. |
| `../img/icon-512.png` | 512x512 store icon, rendered from `docs/img/icon.png` (1024x1024 source). |
| `screenshots/*.png` | Seven real in-app screenshots, 1344x2992 (Pixel 8 Pro class), captured from the running app on an emulator. Myket requires at least 3. |

## Technical verdict

| Requirement | Value in the project | Verdict |
| --- | --- | --- |
| targetSdk (Myket: **≥ 34, enforced 1 Aban 1405 = 23 Oct 2026**) | **35** | PASS |
| minSdk | 26 | PASS |
| Signing | `signingConfigs.release` wired to `KEYSTORE_FILE` / `KEYSTORE_PASSWORD` / `KEY_ALIAS` / `KEY_PASSWORD`, all four present as GitHub Actions secrets; release build runs R8 minification | PASS |
| Release APK is signed | enforced by a new gate in `release.yml` that fails the job on any `*unsigned*` APK | PASS |
| Permissions vs functionality | only `android.permission.TRANSMIT_IR` and `android.permission.CAMERA` (plus the platform-generated `DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION`) | PASS |
| INTERNET permission | **not declared** — the app cannot transmit anything | PASS |
| Ads / analytics / tracking SDKs | none in the dependency tree | PASS |
| Package name | `com.erfanbagheri.controlix` | PASS |
| Age rating / price / category | 3+ / free / ابزارهای کاربردی (Utilities) | PASS |
| Farsi + RTL | whole UI localised, RTL-first | PASS |
| Open-source licence | GPL-3.0 | PASS |

## Still needed from you (manual)

1. **Developer account** on <https://myket.ir/> — complete the profile, phone
   verification and legal info, enable 2FA.
2. **Reserve the app page** and the package name before uploading anything.
3. **Publish the privacy policy** at a stable URL and paste that URL into the
   Myket panel (the `راهنمای حریم خصوصی` section).
4. **Upload the signed APK** from the latest GitHub release
   (<https://github.com/ErfanBagheri404/Controlix/releases>) — never an
   unsigned local build. Local `./gradlew assembleRelease` on a machine with
   no keystore produces `app-release-unsigned.apk`; that file is **not**
   publishable, which is why the workflow now hard-fails on it.
5. **Back up the release keystore offline** (outside this repo, off the cloud
   drive). If it is lost, no future update can ever be published to the same
   package name.
6. In Myket's "data sharing / privacy security" questionnaire, everything is
   "no": the app collects no data, shares no data with third parties, and has
   no encryption-in-transit requirement because it has no transit at all.

## Other Iranian stores

The same APK, description and privacy policy apply to Cafe Bazaar, Iran Apps,
Jhoobin, Sakht Afzar and Market Up. Cafe Bazaar additionally wants an "app
authorization" declaration; answer that with the same two permissions
(`TRANSMIT_IR` for control, `CAMERA` for QR import) and note that both are
core features and that `INTERNET` is absent.
