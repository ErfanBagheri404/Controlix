# Controlix — Store listing (Myket, Cafe Bazar, other Iranian stores)

Prepared for: Myket Publishing Rules of 15 October 2026, checked against
<https://myket.ir/kb/pages/...>.

---

## ⚠️ Myket hard requirement — Target SDK

Myket's notice *"الزام به‌روزرسانی Target SDK Version به API Level 34"*
(published 1405/04/21) says: **from 1 Aban 1405 (= 23 October 2026) Myket
will not publish new apps or new updates whose targetSdk is below 34.**

**Status: PASS.** `targetSdk = 35`, `compileSdk = 35`, `buildToolsVersion = 35.0.0`.
No action needed. (This is 8 days away, so we are safe.)

---

## ✓ Verified good to go

| Check | Requirement | Our value | Verdict |
| --- | --- | --- | --- |
| targetSdk | ≥ 34 (Myket, from 23 Oct 2026) | **35** | PASS |
| minSdk | no rule | 26 (Android 8.0) | PASS |
| Signing | release key + `zipalign` | Gradle `release` build, minified (R8), enums | PASS |
| Permissions match function | Myket rejects apps requesting unrelated permissions | only `TRANSMIT_IR` + `CAMERA`, both core features | PASS |
| INTERNET permission | must match functionality | **none declared at all** | PASS |
| Ads / analytics / tracking SDKs | must be disclosed, none present | none — no SDKs beyond AndroidX/Compose/ZXing | PASS |
| Spyware / malware classification | Myket runs Kaspersky, Bitdefender, McAfee before publishing | offline-first, no network | PASS |
| Users' sensitive data handling | privacy rules | nothing collected, nothing stored off-device | PASS |
| Copyright | must own or license content | GPL-3.0 project; IrpTransmogrifier used **read-only**, no Mi Remote codes | PASS |
| App title (fa + en) | required | `Controlix` | PASS |
| App icon | square, high quality, no copyright marks, no excessive text | adaptive icon, 1024×1024 foreground, themed, monochrome layer for Android 13 themed icons | PASS |
| Descriptions (fa + en) | required | below | PASS |
| Screenshots | **at least 3, from inside the app** | must be captured | ACTION |
| Package name | reserved, unique | `com.erfanbagheri.controlix` | PASS |
| Age rating | required | 3+ (no violence, no social content) | PASS |
| Price | required | Free | PASS |
| Category | required | ابزار / ابزارهای کاربردی (Tools / Utilities) | PASS |

## 🔴 Action needed before publishing

1. **Screenshots (mine).** Myket requires ≥3 real screenshots taken from
   inside the app. I will generate these by driving the app on an emulator.
2. **Signing keystore.** Confirm the release build is signed with your
   production keystore and **back it up offline** — if you lose it you can
   never publish an update again.
3. **Store account + developer profile** — Myket requires 2FA and a completed
   developer profile (phone verification + legal information).
4. **Sign in on panel:** <https://myket.ir/apps/> → ثبت نام → «برنامه جدید»
   → reserve the package name `com.erfanbagheri.controlix`.

---

## Titles

| Language | Title |
| --- | --- |
| فارسی | **Controlix — کنترلر هوشمند** |
| English | **Controlix — Smart Remote** |

Myket requires a **Persian title and an English title** (choose-app-title rules:
must be relevant, must not be too long, must not contain copyright marks).

## Tagline / short description

**فارسی:** کنترلر اینفرارد را روی گوشی ات ببر — بدون حساب، بدون تبلیغ، بدون اینترنت.

**English:** Universal IR remote on your phone — no account, no ads, no internet.

Myket's tagline guidance: a short one-line summary shown under the title.
Keep under ~80 chars. ✓

## Full description

### فارسی (Persian)

Controlix یک کنترلر اینفراردی است که روی گوشی ایران راه می‌اندازد — بدون حساب کاربری، بدون تبلیغ، بدون دسترسی به اینترنت، بدون ردیابی. کدهای دستگاه‌ها را از پایگاه داده حمل‌شده به صورت محلی جستجو کنید، روی ریموت بزنید و دستگاه روشن یا خاموش می‌شود. ادامهٔ کار همان نیاز است: یک گوشی دارای فلش مادون‌قرمز.

مایکیت یا هر فروشگاه دیگر، حساب کاربری نمی‌خواهد. هیچ‌چیز از گوشی شما بیرون نمی‌رود.

— ایمن و بسته: برنامه هیچ مجوز اینترنتی ندارد، پس از نظر فنی ارسال داده از دستگاه شما غیرممکن است. بدون آنالیز، بدون تبلیغ، بدون شناسه دستگاه.

— فارسی کامل و از راست به چپ: کل محیط برنامه فارسی است و چیدمان کاملاً راست‌به‌چپ است. انگلیسی هم در دسترس است، با کم‌رنگ‌سازی فارسی.

— پایگاه داده محلی بزرگ: دسته‌ها و کدهای دستگاه‌ها (تلویزیون، گیرنده، پخش‌کننده، کولر، فن، پروژکتور) همراه با خود اپ عرضه می‌شود. جستجو از پوشه یا نام دستگاه.

— کدهای سفارشی: کدهای IR را به صورت دستی وارد کنید یا با اسکن QR کد وارد کنید. همهٔ چیزار اپ-محلی می‌ماند.

— ماکرو و کاشی‌ها: چند فرمان را در یک ماکرو هماهنگ کنید و آن را روی کاشی سریع یا ویجت خانهٔ خود بگذارید. یک ویجت نمایشگر و یک ویجت ماکرو هم موجود است.

— خودکارسازی: با Tasker یا Intent_dispatcher اندروید دستگاه‌ها را کنترل کنید. پیش‌فرض شما خودتان کنترل می‌کنید.

— همه روی دستگاه بدون فلش IR هم کار می‌کند: همهٔ صفحه‌ها باز و قابل مرور است، فقط ارسال خاموش است. یک باگ یا خطای بازگشتی وجود ندارد.

— منبع باز تحت GPL-3.0: کد برنامه را ببینید و با آن نسخهٔ خودتان را بسازید.

### English (full)

Controlix is an infrared remote that runs on your phone — no account, no ads, no internet, no tracking. Search the bundled device database locally, press a button on the remote, and the device responds. Nothing leaves your phone.

— Private and closed: the app declares no INTERNET permission, so transmitting anything off your device is technically impossible. No analytics, no ads, no device identifiers.

— Farsi-first with real RTL: the whole interface is Persian and fully right-to-left. English is available too.

— Large local database: categories and codes for TVs, set-top boxes, players, air conditioners, fans and projectors ship inside the APK. Search by folder or by device name.

— Custom codes: enter IR codes manually or scan a QR code to import a shared remote. Everything stays local.

— Macros and widgets: chain several commands into one macro, pin it to a quick control or a home-screen widget. A remote widget and a macro widget are included.

— Automation: drive devices from Tasker or the Android shortcut dispatcher. Off by default — you are always in control.

— Works on phones without an IR blaster: every screen stays fully browsable, only transmission is unavailable.

— Open source under GPL-3.0.

## App page details to fill in the Myket panel

| Field | Value |
| --- | --- |
| عنوان فارسی | Controlix — کنترلر هوشمند |
| عنوان انگلیسی | Controlix — Smart Remote |
| نوع و دسته‌بندی | ابزار ← ابزارهای کاربردی (Utilities) |
| قیمت | رایگان (Free) |
| رده سنی | 3+ |
| وضعیت قیمت‌گذاری | بدون پرداخت درون‌برنامه‌ای |
| نام بسته (reserve now) | `com.erfanbagheri.controlix` |
| توضیحات نسخه فارسی | نسخهٔ ۱٫۰٫۰: انتشار اول |
| توضیحات نسخه انگلیسی | 1.0.0: first release |

## Contact info (Myket requires it)

- Email: <mrenmityerf@gmail.com>
- Website: <https://github.com/ErfanBagheri404/Controlix>
- Telegram: *(fill in if you have one)*

## Release checklist before the first upload

- [ ] Download the signed APK from the GitHub release — **do not** upload an
      unsigned debug build.
- [ ] Sign the APK with the production keystore (`keytool -list -v -keystore ...`).
- [ ] `zipalign -p -v 4 in.apk out.apk` and verify with `apksigner verify`.
- [ ] Confirm `targetSdkVersion` is 34+ in the merged manifest.
- [ ] Screenshots taken from inside the app (≥3, 1080×1920 or larger).
- [ ] Store icon 512×512 PNG square.
- [ ] Developer profile complete + 2FA enabled.
- [ ] Store notice: no INTERNET permission → the "data collection" questions
      are all "no", including "does your app share data with third parties".
