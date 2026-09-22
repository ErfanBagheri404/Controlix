"""Download the Lucide SVGs Controlix needs (ISC licensed).

Run from the directory containing gen.py/emit.py/header.kt.txt.
"""
import pathlib
import urllib.request

BASE = 'https://raw.githubusercontent.com/lucide-icons/lucide/main/icons/'

NAMES = [
    # action glyphs
    'power', 'power-off', 'volume-1', 'volume-2', 'volume-x', 'mic-off',
    'chevron-up', 'chevron-down', 'chevron-right', 'chevron-left', 'arrow-up', 'arrow-down',
    'arrow-left', 'arrow-right', 'check', 'x', 'trash', 'star', 'share-2',
    'pencil', 'list-filter', 'list-checks', 'scan-line', 'settings', 'menu',
    'circle-play', 'circle-dot', 'ellipsis-vertical', 'ellipsis', 'minus', 'plus',
    'qr-code', 'workflow', 'vibrate', 'moon', 'palette', 'gauge', 'info',
    'wifi', 'undo-2', 'house',
    # category glyphs
    'tv', 'monitor', 'fan', 'snowflake', 'heater', 'projector', 'radio',
    'speaker', 'cast', 'camera', 'disc', 'gamepad-2', 'clock', 'flame',
    'air-vent', 'cpu', 'zap', 'toy-brick', 'video', 'music', 'sun', 'lamp',
    'thermometer', 'wind', 'droplet', 'audio-lines',
]


def main():
    out = pathlib.Path('.')
    ok, bad = [], []
    for n in NAMES:
        p = out / (n + '.svg')
        if p.exists() and p.stat().st_size > 100:
            ok.append(n)
            continue
        try:
            p.write_bytes(urllib.request.urlopen(BASE + n + '.svg', timeout=30).read())
            ok.append(n)
        except Exception as exc:
            bad.append((n, str(exc)[:50]))
    print(f'fetched {len(ok)} icons; failed: {bad}')


if __name__ == '__main__':
    main()
