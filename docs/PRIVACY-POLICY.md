# Privacy Policy — Controlix

**Last updated:** 15 October 2026

## Summary

Controlix collects nothing. It does not send, store or share any personal data.
There is no server, no analytics, no advertising, no account and no crash
reporting. The app has no `INTERNET` permission at all, which means it is
technically incapable of transmitting anything off the device.

## Data we do not collect

- **No personal information.** The app has no sign-up, no login, no profile.
  It never asks for your name, email address, phone number, or payment details.
- **No usage analytics.** We do not track screen views, taps, session length,
  retention, or any behavioural telemetry. There is no third-party analytics
  SDK (Google Analytics, Firebase, Mixpanel, Amplitude, Facebook SDK, etc.)
  anywhere in the app or its dependencies.
- **No advertising identifiers.** The app contains no ads and no ad SDK. It
  never reads the Google Advertising ID, AAID, IDFA or any device identifier.
- **No device identifiers.** We do not read the IMEI, serial number, MAC
  address, Android ID or build fingerprint.
- **No location.** The app has no `ACCESS_FINE_LOCATION` or
  `ACCESS_COARSE_LOCATION` permission. It never knows where you are.
  (A coarse location permission is only ever requested by Android itself on
  some devices as a prerequisite for Wi-Fi name scanning — this app performs
  no Wi-Fi scanning at all.)
- **No crash logs.** Nothing reports crashes back to us. If the app crashes,
  only you see it.
- **No network access whatsoever in the published build.** The release manifest
  declares only `android.permission.TRANSMIT_IR` and
  `android.permission.CAMERA`. There is no `INTERNET` permission, so the
  operating system itself blocks any outbound connection.

## Where your data actually lives

Everything you create stays on your device, in the app's private storage
directory (`/data/data/com.erfanbagheri.controlix/`), which other apps cannot
read.

| Data | Where it is stored | Does it leave the device |
| --- | --- | --- |
| Remote configurations you pick | App-private database | No |
| Favourite devices / pins | App-private database | No |
| Macros you record | App-private database | No |
| Custom IR codes you import | App-private database | No |
| App settings and preferences | App-private shared preferences | No |

The remote-control code database bundled with the app is a read-only database
file inside the APK. It contains device categories and IR commands, nothing
about you.

## The two permissions we do use, and what they are for

**`android.permission.TRANSMIT_IR`** — Used for exactly one thing: emitting
infrared light through your phone's IR blaster to control a TV, set-top box,
fan, air conditioner or similar device. This is the entire purpose of the app.
It is granted automatically at install time and cannot be revoked. Your phone
can send IR only while you are actively pressing a control in the app, or
running an automation (Tasker / Shortcuts) you set up yourself.

**`android.permission.CAMERA`** — Used for one thing only: scanning a QR code
to import a shared remote configuration. The permission is requested at
runtime via the standard Android permission dialog, and only when you open
the scanner yourself. Images from the camera are processed entirely on-device
and are never saved, never uploaded and never stored. You can deny the
permission and use every other feature of the app.

The permission `android.hardware.camera` is declared `required="false"`, so
the app installs and works on tablets, TVs and phones without any camera.
Similarly `android.hardware.consumerir` is `required="false"`, so the app
installs on phones without an IR blaster — those screens stay fully browsable,
only transmission is unavailable.

## Sharing and exporting

Controlix can share a remote configuration you have explicitly chosen, via the
standard Android share sheet. That is always a conscious action by you: tapping
a share button, then choosing where to send it. The app itself never posts,
uploads or transmits anything on its own. An optional database refresh
feature, if you ever enable it, is the only code path that could contact a
network, and it is off by default in the published build.

## Third-party services

None. Controlix has no third-party services, SDKs, servers or APIs of any
kind. There is no one we could share data with even if we wanted to.

## Data retention and deletion

Because no data leaves your device, retention is entirely under your control:
**uninstalling the app deletes everything.** Every remote, macro, pin and
setting is stored in the app's private directory and is removed by Android on
uninstall. There is no cloud backup of your data anywhere.

## Children

Controlix is a universal remote control. It contains no ads, no in-app
purchases, no social features and no user-generated content, and it collects
no data of any kind from anyone, including children. The app is suitable for
all ages.

## Changes to this policy

If this policy ever changes, the revised version will be published on the
app's page and in the project's release notes, with an updated date.

## Contact

- **Developer:** Erfan Bagheri
- **Email:** <mrenmityerf@gmail.com>
- **Source repository:** <https://github.com/ErfanBagheri404/Controlix>
