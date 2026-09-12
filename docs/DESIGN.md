# Controlix — Design Plan (anthropic-frontend-design pass)

Subject: an offline infrared remote. Audience: someone who just bought a
phone with an IR blaster and wants their TV/AC/fan under it tonight. Primary
job: pick a brand, confirm the codes work, have a pad you enjoy tapping.

## Concept — "the pulse"

IR is invisible light. The whole identity is about making the *signal* the
brand: a dark, living-room surface (you use this on the couch, at night, next
to a glowing screen) with one hot ember-amber accent that behaves like a
transmission — it fires from the power glyph in expanding rings every time a
code is sent. The pulse is the single memorable element; everything else is
quiet discipline.

This is also the anti-cliché check: no cream+terracotta, no near-black with
acid green, no broadsheet hairlines, no SaaS card kit. The darkness here is
a choice from the subject (couch/night/TV glow), not #111 standing in for
black — surfaces are layered ink-navy panels, and the accent is a warm ember,
not a neon.

## Color (named tokens)

| Token | Hex | Role |
|---|---|---|
| `ink` | `#0C1222` | screen base — deep navy-black, blue cast |
| `ink-raised` | `#141C31` | cards, sheets, nav |
| `ink-float` | `#1B2540` | pressed / elevated surfaces |
| `hairline` | `#2A3654` | strokes that encode structure (tile borders, dividers) |
| `ember` | `#FFB454` | THE accent: power glyph, active state, pulse, primary CTA text/icons |
| `ember-deep` | `#E8912A` | pressed ember, small-text on light |
| `confirm` | `#7FD1AE` | "yes it worked" — muted signal green, success only |
| `paper` | `#EEF2FB` | primary text on ink |
| `paper-dim` | `#94A3BE` | secondary text, labels |

Amber on navy is the entire palette. No gradients as decoration; the only
gradient anywhere is the fading alpha of the pulse rings.

## Type

One family: **Space Grotesk** (variable, bundled). Chosen for its hardware
character — squared bowls, the `1` with a flag, tall numerals that suit code
counts and big test prompts. Body UI strings are short; legibility at 14sp is
fine. Weights set via `fontVariationSettings`:

- Display 40/56 sp, wght 500 — the one big statement per screen ("Your TV", brand name in the ritual).
- Headline 24/32 sp, wght 500 — section headers.
- Label 13 sp, wght 500, normal case — counts, hints. (No ALL-CAPS eyebrows.)
- Body 15/22 sp, wght 400.
- Numeric 64/72 sp, wght 700 — the candidate counter during setup tests.

Alignment: left everywhere. Center only inside the ritual's question stack
where the eye is anchored on the device glyph.

## Layout

- **No TopAppBar anywhere.** Screens open with a large display title that
  pins on scroll (LazyColumn + collapsing header via offset animation).
- **Home** = saved devices as tall 2-column tiles (glyph + name + brand),
  plus one ghost tile "+ Add device". A quiet Tools row below: power-off
  sweep, macros.
- **Add flow** = one question per screen, full-bleed: category grid (3 cols,
  image tiles) → brand (search + A–Z list) → **the ritual** → done.
- **The ritual** = vertical stack: huge power glyph with the pulse, one
  sentence question, two oversized buttons ("Yes, it worked" / "Try another
  code — 12 of 40"), a hairline stepper.
- **Pad** = grouped remote control, not a flat button grid: power island on
  top-right, D-pad island center, media/volume rails — physical-remote
  mental model.

ASCII wireframe of the ritual (the signature screen):

```
┌──────────────────────────┐
│ ‹ Back        Samsung TV │
│                          │
│         ╭─────╮          │
│       (  ( ● )  )  pulse │   ← glyph fires rings on send
│         ╰─────╯          │
│                          │
│   Did your TV turn off?  │   ← display 28sp
│   Code 12 of 40          │   ← label, dim
│                          │
│ ┌──────────────────────┐ │
│ │  Yes, it worked      │ │   ← confirm tint
│ └──────────────────────┘ │
│ ┌──────────────────────┐ │
│ │  No — try another    │ │   ← ember outline
│ └──────────────────────┘ │
│ ────────●───────────     │   ← hairline stepper
└──────────────────────────┘
```

## Principles

1. **Spend boldness in one place**: the pulse. Lists, tiles, pads are flat
   ink + hairlines, no shadows.
2. **Motion answers action.** Pulse on every send, spring scale on press,
   staged fade-rise only on first entry of a screen. No idle animation.
3. **Structure from strokes, not shadows.** hairline 1dp borders; radius
   encodes hierarchy: tiles 20dp, buttons 14dp, pads/islands 28dp.
4. **Plain words, active voice.** "Add a device", "Did your TV turn off?",
   "Yes, it worked". Errors say what happened and the fix.
5. **Setup = brand + confirmations only.** Never ask the user for a model
   number. Codes cycle automatically; the user answers yes/no.
6. Reduced motion (developer setting / animationScale 0) → pulse becomes a
   static flash, springs become instant.

## Category imagery

Hand-drawn **vector line icons** (24dp grid, 2dp round strokes, ember on
ink-raised tiles with a faint hairline frame) — one per real category
(~26: TV, AC, fan, projector, soundbar, AVR, set-top, speaker, CD, DVD,
Blu-ray, camera, heater, fireplace, vacuum, monitor, console, streaming,
humidifier, air purifier, clock, CCTV, toy, VCR…) plus a generic-chip
fallback and a special *rays* glyph for the community DB. Line icons keep
the palette pure and the APK small; photos would fight the ember/ink system.
