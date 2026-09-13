# Controlix — Design System v2 "Night Ink"

The redesign brief: a remote app for the couch in the dark. Not a Material
demo, not a gradient SaaS page — an instrument. Flat, editorial, one accent,
two typefaces, motion only where physics happens.

## Concept

Controlix is a tool that disappears into the task. The visual authority is
the physical IR remote itself: the pad screen is that remote flattened onto
glass — power crown at top, vol/mute and channel rails as thumb columns,
the D-pad diamond centered, DB extras below. The home is a single editorial
deck: masthead, your devices, add, tools. Nothing floats; depth comes from
space, type scale, and hairlines.

## Palette

| Token | Value | Role |
|---|---|---|
| Ink | `#0B1020` | background — the only surface |
| InkRaised | `#131A2E` | fields, pressed states |
| Paper | `#EEF2FB` | primary text |
| PaperDim | `#9AABC B` | secondary text (exact: `#9AABCB`) |
| Ember | `#FFB454` | the one accent: power, pulse, selection |
| Circuit | `#5FE0C0` | signal green: confirmations, working states |
| Hairline | `#232C47` | 1px strokes — structure without boxes |

Rules: no gradients, no elevation shadows, no third accent color. Ember is
reserved for "energy" (power, emit, primary action); Circuit only for
success/working states. Text never sits below 4.5:1 on Ink.

## Type

- **Sora** — display through label. Tight tracking on large sizes
  (`displaySmall` 30sp/-0.5). Distinctly geometric; reads as designed, not
  as system default.
- **JetBrains Mono** — every number that moves: code counters, "1 / 4"
  step positions, button counts, brand remote counts. Tabular figures stop
  counters from jittering mid-ritual.

## Motion (Motion.kt)

Grammar, not decoration:

- **Press contract** — everything clickable scales to 0.97 over 120ms
  ease-out and returns 160ms. Scale on the layer means icon + label sink
  together like a real key.
- **EmitPulse** — the signature moment. On every code send, three ember
  arcs expand from the emitter icon (620ms ease-out, staggered 18%). It
  makes the invisible (IR light) visible. Canvas alpha only, off the
  layout path, zero cost when idle.
- **Route transitions** — forward: slide in 1/5 width + fade over 240ms,
  previous exits 160ms. Back reverses. Never full-width slides (too loud
  for 4 levels of depth).
- Curves: strong ease-out (0.215,0.61,0.355,1). Ease-in never used on UI.
- All durations < 300ms except the pulse (which is a performance, not a
  response).

## Components

- **hairlineTile** — 1px outline, 14-20dp radius, no shadow. The pad cap
  and extras-tile treatment.
- **BigPressButton** — 88dp ember-outlined bar; the one "button" shape.
- **AnswerChip** — pill with icon + label; the only question UI the ritual
  asks ("did it work?").
- **BackRow** — back affordance as text+chevron, left aligned, same press
  contract.
- **Canvas icon family** (Icons.kt) — every glyph drawn from strokes at a
  48-unit grid, 3.2 stroke weight. Zero icon-library dependency; the
  power/vol/mute/D-pad set is shared between action icons and pad caps.

## The Ritual

The setup flow is the app's soul and stays mechanically quiet: one question
per screen, the phone sends codes automatically, you press the *real*
button on your physical remote when asked. Power cycles through up to 5
candidates; once one locks, the flow tests the actual volume-up /
volume-down / mute buttons of that code set (up to 4 tests), falling back
to the next power candidate if a follow-up fails. No model numbers, no
"Yes/No did you see a popup" theater.

## Long-press removal

Home device rows remove on long-press (haptic + 500ms). No edit mode, no
trash icons cluttering the list — discovery by failure is acceptable for a
destructive-but-cheap action (re-adding takes the 30-second ritual).

## Screens

Home (deck) → Add (category rows → brand search) → Ritual (sealed state
machine) → Pad. Tools: Power-off sweep (counter + brand readout, mono),
IR self-test (single burst + camera hint). Route list is sealed in Nav.kt;
no navigation library.

## Craft floor compliance

- No AI-default tells: no purple gradient, no glassmorphism, no shadow
  stacks, no emoji, no library icons, no Inter.
- One background color site-wide (Ink). Hierarchy via type scale + spacing.
- 96dp+ comfortable tap targets verified geometrically on all pad
  controls (uiautomator bounds audit: zero small targets, zero overlaps,
  rails symmetric at x=221/1123 around center 672).
- Motion respects reduced-motion availability (Compose animates nothing
  when system animations are off).
