#!/usr/bin/env python3
"""
Quick scan of Flipper-IRDB to count brands/files per category.
"""
import os, pathlib

def scan_irdb(root):
    """Scan irdb root directory structure."""
    root = pathlib.Path(root)
    stats = {}

    for category_dir in sorted(root.iterdir()):
        if not category_dir.is_dir() or category_dir.name.startswith('.'):
            continue

        cat_name = category_dir.name
        brands = 0
        files = 0
        buttons = 0

        for brand_dir in sorted(category_dir.iterdir()):
            if not brand_dir.is_dir():
                continue
            brands += 1

            for ir_file in brand_dir.rglob('*.ir'):
                files += 1
                # Count buttons in file
                try:
                    text = ir_file.read_text(encoding='utf-8', errors='ignore')
                    btn_count = text.count('name:') - text.count('# name:')
                    buttons += max(0, btn_count)
                except:
                    pass

        stats[cat_name] = {'brands': brands, 'files': files, 'buttons': buttons}

    return stats


def main():
    import sys
    if len(sys.argv) < 2:
        print("Usage: irdb_scanner.py <irdb-root>")
        sys.exit(1)

    root = sys.argv[1]
    stats = scan_irdb(root)

    print(f"{'Category':<20} {'Brands':<10} {'Files':<10} {'Buttons':<10}")
    print("-" * 50)

    total_brands = 0
    total_files = 0
    total_buttons = 0

    for cat, s in sorted(stats.items()):
        print(f"{cat:<20} {s['brands']:<10} {s['files']:<10} {s['buttons']:<10}")
        total_brands += s['brands']
        total_files += s['files']
        total_buttons += s['buttons']

    print("-" * 50)
    print(f"{'TOTAL':<20} {total_brands:<10} {total_files:<10} {total_buttons:<10}")


if __name__ == '__main__':
    main()
