# Lucide icon pipeline

Controlix draws its action and category glyphs with [Lucide](https://lucide.dev) —
stroke-only 24px icons, **ISC licensed** (redistributable, unlike the proprietary
remote-app icon sets).

## Regenerating

```bash
python src/main/python/lucide/fetch.py          # download upstream SVGs
python src/main/python/lucide/emit.py           # write ui/icons/LucideIcons.kt
```

`emit.py` imports `gen.py` and reads the SVGs from the working directory, so run
both from a scratch dir holding `gen.py`, `emit.py`, `header.kt.txt` and the
downloaded `*.svg` files.

Output: `app/src/main/java/com/erfanbagheri/controlix/ui/icons/LucideIcons.kt`
(`object Lucide`, one `val <Name>: ImageVector` per glyph).

**Do not hand-edit `LucideIcons.kt`** — re-run the generator.

## Converter notes (`gen.py`)

`parse_path` supports the full command set Lucide uses: `M m L l H h V v C c
S s Q q T t A a Z z`. Two things that bite:

- **Packed arc flags.** SVG allows `a 9 9 0 1 1-12.77.04` — the two flags have no
  separators, and `-12.77.04` is two numbers. `flag()` consumes one character at a
  time and rewrites the token; using `float()` on the token raises
  `could not convert string to float: 'l'`.
- **Subpath flattening.** `parse_path` returns a list of subpaths; `parse_svg` must
  `extend`, not `append`, or every icon silently emits an empty body.

Arcs are converted to cubic Béziers via the W3C endpoint-parameterisation
formula (`arc_to_cubics`). `<circle>`, `<line>`, `<polyline>` and `<polygon>`
primitives are converted to equivalent path commands so one `ImageVector`
`PathBuilder` covers everything.

## Adding an icon

1. Add `lucide-name: 'KotlinName'` to `NAMES` in `emit.py`.
2. Re-run the generator.
3. Map it in `ui/ActionIcons.kt` (`actionVector`) or
   `ui/LucideIcon.kt` (`LucideCategoryIcons`).
