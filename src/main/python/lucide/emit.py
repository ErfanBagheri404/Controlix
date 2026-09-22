"""Emit LucideIcons.kt from the downloaded SVGs (run from the lucide dir)."""
import pathlib
import re
import gen

OUT = pathlib.Path('E:/dev/projects/Controlix/app/src/main/java/com/erfanbagheri/controlix/ui/icons/LucideIcons.kt')

# icon-file-name -> Kotlin property name
NAMES = {
    'power': 'Power', 'volume-1': 'VolumeDown', 'volume-2': 'VolumeUp', 'volume-x': 'VolumeMute',
    'chevron-up': 'ChevronUp', 'chevron-down': 'ChevronDown', 'chevron-right': 'ChevronRight',
    'chevron-left': 'ChevronLeft',
    'mic-off': 'MicOff', 'arrow-up': 'ArrowUp', 'arrow-down': 'ArrowDown',
    'arrow-left': 'ArrowLeft', 'arrow-right': 'ArrowRight',
    'check': 'Check', 'x': 'X', 'trash': 'Trash', 'star': 'Star', 'share-2': 'Share',
    'pencil': 'Pencil', 'list-filter': 'Filter', 'scan-line': 'ScanLine', 'settings': 'Settings',
    'menu': 'Menu', 'wind': 'Wind', 'droplet': 'Droplet', 'audio-lines': 'AudioLines',
    'house': 'House',
    'circle-play': 'Play', 'ellipsis-vertical': 'MoreVertical', 'ellipsis': 'Ellipsis', 'minus': 'Minus', 'plus': 'Plus',
    'tv': 'Tv', 'monitor': 'Monitor', 'fan': 'Fan', 'snowflake': 'Snowflake', 'heater': 'Heater',
    'projector': 'Projector', 'radio': 'Radio', 'speaker': 'Speaker', 'cast': 'Cast',
    'camera': 'Camera', 'disc': 'Disc', 'gamepad-2': 'Gamepad', 'clock': 'Clock',
    'flame': 'Flame', 'air-vent': 'AirVent', 'cpu': 'Cpu', 'zap': 'Zap',
    'toy-brick': 'ToyBrick', 'video': 'Video', 'music': 'Music', 'sun': 'Sun',
    'lamp': 'Lamp', 'thermometer': 'Thermometer',
    'qr-code': 'QrCode', 'workflow': 'Workflow', 'vibrate': 'Vibrate', 'moon': 'Moon',
    'palette': 'Palette', 'gauge': 'Gauge', 'info': 'Info', 'wifi': 'Wifi',
    'list-checks': 'ListChecks', 'undo-2': 'Undo', 'power-off': 'PowerOff',
    'circle-dot': 'CircleDot',
}


def main():
    header = pathlib.Path('header.kt.txt').read_text()
    body = []
    missing = []
    for fname, prop in sorted(NAMES.items(), key=lambda kv: kv[1]):
        p = pathlib.Path(fname + '.svg')
        if not p.exists():
            missing.append(fname)
            continue
        body.append(gen.to_kotlin(fname, p.read_text(), prop).replace('val ', '    val ').replace('\n    get()', '\n        get()').replace('lucideIcon(', '        lucideIcon('))
    OUT.parent.mkdir(parents=True, exist_ok=True)
    OUT.write_text(header + '\nobject Lucide {\n' + '\n'.join(body) + '}\n')
    print('wrote', OUT, len(body), 'icons; missing:', missing)


if __name__ == '__main__':
    main()
