"""Lucide SVG -> Compose ImageVector generator.

Lucide icons: 24x24 viewBox, stroke-only, no fill, round caps/joins.
Emits MaterialIcon-style builders so the app can use them as ImageVector.
"""
import pathlib
import re
import sys

SRC = pathlib.Path('C:/Users/mrenm/AppData/Local/Temp/lucide')
NUM = r'[-+]?(?:\d*\.\d+|\d+\.?)(?:[eE][-+]?\d+)?'


def parse_path(d):
    """SVG path -> list of subpaths; each subpath is a list of segments.

    Supports M m L l H h V v C c S s Q q T t A a Z z (what Lucide uses).
    """
    tokens = re.findall(r'[MmLlHhVvCcSsQqTtAaZz]|' + NUM, d)
    i = 0
    subs, cur = [], None
    x = y = 0.0
    start = (0.0, 0.0)
    prev_ctrl = None
    prev_qctrl = None
    cmd = None

    def num():
        nonlocal i
        v = float(tokens[i]); i += 1
        return v

    def flag():
        """Arc flags may be packed without separators ('00', '01')."""
        nonlocal i
        t = tokens[i]
        if len(t) == 1:
            i += 1
            return float(t)
        tokens[i] = t[1:]
        return float(t[0])

    def push(seg):
        nonlocal cur
        if cur is None:
            cur = []
            subs.append(cur)
        cur.append(seg)

    while i < len(tokens):
        t = tokens[i]
        if re.match(r'[A-Za-z]', t):
            cmd = t; i += 1
            if cmd in 'Zz':
                push(('Z',))
                x, y = start
                prev_ctrl = prev_qctrl = None
                cur = None
                continue
        if cmd is None:
            raise ValueError('path starts without a command: ' + d)
        rel = cmd.islower()
        c = cmd.upper()
        if c == 'M':
            nx, ny = num(), num()
            if rel: nx, ny = x + nx, y + ny
            x, y = nx, ny
            start = (x, y)
            cur = None
            push(('M', x, y))
            cmd = 'l' if rel else 'L'
            prev_ctrl = prev_qctrl = None
        elif c == 'L':
            nx, ny = num(), num()
            if rel: nx, ny = x + nx, y + ny
            push(('L', nx, ny)); x, y = nx, ny
            prev_ctrl = prev_qctrl = None
        elif c == 'H':
            nx = num()
            if rel: nx = x + nx
            push(('L', nx, y)); x = nx
            prev_ctrl = prev_qctrl = None
        elif c == 'V':
            ny = num()
            if rel: ny = y + ny
            push(('L', x, ny)); y = ny
            prev_ctrl = prev_qctrl = None
        elif c == 'C':
            x1, y1, x2, y2, nx, ny = (num() for _ in range(6))
            if rel:
                x1, y1, x2, y2, nx, ny = x + x1, y + y1, x + x2, y + y2, x + nx, y + ny
            push(('C', x1, y1, x2, y2, nx, ny))
            x, y = nx, ny
            prev_ctrl = (x2, y2); prev_qctrl = None
        elif c == 'S':
            x2, y2, nx, ny = (num() for _ in range(4))
            if rel:
                x2, y2, nx, ny = x + x2, y + y2, x + nx, y + ny
            x1, y1 = (2 * x - prev_ctrl[0], 2 * y - prev_ctrl[1]) if prev_ctrl else (x, y)
            push(('C', x1, y1, x2, y2, nx, ny))
            x, y = nx, ny
            prev_ctrl = (x2, y2); prev_qctrl = None
        elif c in 'QT':
            if c == 'Q':
                qx, qy, nx, ny = (num() for _ in range(4))
                if rel: qx, qy, nx, ny = x + qx, y + qy, x + nx, y + ny
            else:
                nx, ny = num(), num()
                if rel: nx, ny = x + nx, y + ny
                qx, qy = (2 * x - prev_qctrl[0], 2 * y - prev_qctrl[1]) if prev_qctrl else (x, y)
            # Quadratic -> cubic
            x1, y1 = x + 2 / 3 * (qx - x), y + 2 / 3 * (qy - y)
            x2, y2 = nx + 2 / 3 * (qx - nx), ny + 2 / 3 * (qy - ny)
            push(('C', x1, y1, x2, y2, nx, ny))
            x, y = nx, ny
            prev_qctrl = (qx, qy); prev_ctrl = (x2, y2)
        elif c == 'A':
            rx, ry, rot = num(), num(), num()
            laf, sf = flag(), flag()
            nx, ny = num(), num()
            if rel: nx, ny = x + nx, y + ny
            for seg in arc_to_cubics(x, y, rx, ry, rot, laf, sf, nx, ny):
                push(seg)
            x, y = nx, ny
            prev_ctrl = prev_qctrl = None
        else:
            raise ValueError('unsupported command ' + cmd)
    return subs


def arc_to_cubics(x0, y0, rx, ry, rot_deg, large_arc, sweep, x1, y1):
    """Endpoint-parameterised arc -> cubic Beziers (W3C SVG spec F.6)."""
    import math
    if rx == 0 or ry == 0 or (x0 == x1 and y0 == y1):
        return [('L', x1, y1)]
    rx, ry = abs(rx), abs(ry)
    phi = math.radians(rot_deg)
    cosp, sinp = math.cos(phi), math.sin(phi)
    dx2, dy2 = (x0 - x1) / 2, (y0 - y1) / 2
    x1p = cosp * dx2 + sinp * dy2
    y1p = -sinp * dx2 + cosp * dy2
    lam = x1p ** 2 / rx ** 2 + y1p ** 2 / ry ** 2
    if lam > 1:
        s = math.sqrt(lam)
        rx *= s; ry *= s
    num = rx ** 2 * ry ** 2 - rx ** 2 * y1p ** 2 - ry ** 2 * x1p ** 2
    den = rx ** 2 * y1p ** 2 + ry ** 2 * x1p ** 2
    co = math.sqrt(max(0.0, num / den)) if den else 0.0
    if large_arc == sweep:
        co = -co
    cxp = co * rx * y1p / ry
    cyp = -co * ry * x1p / rx
    cx = cosp * cxp - sinp * cyp + (x0 + x1) / 2
    cy = sinp * cxp + cosp * cyp + (y0 + y1) / 2

    def angle(ux, uy, vx, vy):
        dot = ux * vx + uy * vy
        n = math.hypot(ux, uy) * math.hypot(vx, vy)
        a = math.acos(max(-1.0, min(1.0, dot / n))) if n else 0.0
        return -a if ux * vy - uy * vx < 0 else a

    theta1 = angle(1, 0, (x1p - cxp) / rx, (y1p - cyp) / ry)
    dtheta = angle((x1p - cxp) / rx, (y1p - cyp) / ry, (-x1p - cxp) / rx, (-y1p - cyp) / ry)
    if not sweep and dtheta > 0:
        dtheta -= 2 * math.pi
    elif sweep and dtheta < 0:
        dtheta += 2 * math.pi
    n = max(1, int(math.ceil(abs(dtheta) / (math.pi / 2))))
    delta = dtheta / n
    t = 4 / 3 * math.tan(delta / 4)
    out = []
    for k in range(n):
        a0 = theta1 + k * delta
        a1 = a0 + delta
        p0 = (cosp * rx * math.cos(a0) - sinp * ry * math.sin(a0) + cx,
              sinp * rx * math.cos(a0) + cosp * ry * math.sin(a0) + cy)
        p1 = (cosp * rx * math.cos(a1) - sinp * ry * math.sin(a1) + cx,
              sinp * rx * math.cos(a1) + cosp * ry * math.sin(a1) + cy)
        d0 = (-cosp * rx * math.sin(a0) - sinp * ry * math.cos(a0),
              -sinp * rx * math.sin(a0) + cosp * ry * math.cos(a0))
        d1 = (-cosp * rx * math.sin(a1) - sinp * ry * math.cos(a1),
              -sinp * rx * math.sin(a1) + cosp * ry * math.cos(a1))
        out.append(('C', p0[0] + t * d0[0], p0[1] + t * d0[1],
                    p1[0] - t * d1[0], p1[1] - t * d1[1], p1[0], p1[1]))
    return out


def parse_svg(text):
    paths = re.findall(r'<path[^>]*\sd="([^"]+)"', text)
    circles = re.findall(r'<circle[^>]*>', text)
    lines = re.findall(r'<line[^>]*>', text)
    rects = re.findall(r'<rect[^>]*>', text)
    polys = re.findall(r'<poly(?:line|gon)[^>]*>', text)
    segs = []
    for d in paths:
        segs.extend(parse_path(d))
    return segs, circles, lines, rects, polys


def fmt(v):
    s = f'{v:.4f}'.rstrip('0').rstrip('.')
    return '0f' if s in ('', '-0') else s + 'f'


def emit_subpath(sub, indent=' ' * 20):
    out = []
    for seg in sub:
        if seg[0] == 'M':
            out.append(f'{indent}moveTo({fmt(seg[1])}, {fmt(seg[2])})')
        elif seg[0] == 'L':
            out.append(f'{indent}lineTo({fmt(seg[1])}, {fmt(seg[2])})')
        elif seg[0] == 'C':
            out.append(f'{indent}curveTo({fmt(seg[1])}, {fmt(seg[2])}, {fmt(seg[3])}, {fmt(seg[4])}, {fmt(seg[5])}, {fmt(seg[6])})')
        elif seg[0] == 'Z':
            out.append(f'{indent}close()')
    return out


def to_kotlin(name, svg_text, prop_name, stroke=2.0):
    segs, circles, lines, rects, polys = parse_svg(svg_text)
    body = []
    for sub in segs:
        if not sub:
            continue
        body += emit_subpath(sub)
    # Simple primitives -> equivalent path commands
    for c in circles:
        cx = float(re.search(r'cx="([^"]+)"', c).group(1))
        cy = float(re.search(r'cy="([^"]+)"', c).group(1))
        r = float(re.search(r'r="([^"]+)"', c).group(1))
        k = 0.5522847498 * r
        body.append(f'{" "*20}moveTo({fmt(cx + r)}, {fmt(cy)})')
        body.append(f'{" "*20}curveTo({fmt(cx + r)}, {fmt(cy + k)}, {fmt(cx + k)}, {fmt(cy + r)}, {fmt(cx)}, {fmt(cy + r)})')
        body.append(f'{" "*20}curveTo({fmt(cx - k)}, {fmt(cy + r)}, {fmt(cx - r)}, {fmt(cy + k)}, {fmt(cx - r)}, {fmt(cy)})')
        body.append(f'{" "*20}curveTo({fmt(cx - r)}, {fmt(cy - k)}, {fmt(cx - k)}, {fmt(cy - r)}, {fmt(cx)}, {fmt(cy - r)})')
        body.append(f'{" "*20}curveTo({fmt(cx + k)}, {fmt(cy - r)}, {fmt(cx + r)}, {fmt(cy - k)}, {fmt(cx + r)}, {fmt(cy)})')
        body.append(f'{" "*20}close()')
    for l in lines:
        x1 = float(re.search(r'x1="([^"]+)"', l).group(1)); y1 = float(re.search(r'y1="([^"]+)"', l).group(1))
        x2 = float(re.search(r'x2="([^"]+)"', l).group(1)); y2 = float(re.search(r'y2="([^"]+)"', l).group(1))
        body.append(f'{" "*20}moveTo({fmt(x1)}, {fmt(y1)})')
        body.append(f'{" "*20}lineTo({fmt(x2)}, {fmt(y2)})')
    for p in polys:
        pts = re.search(r'points="([^"]+)"', p).group(1)
        coords = [float(v) for v in re.findall(NUM, pts)]
        pairs = list(zip(coords[0::2], coords[1::2]))
        body.append(f'{" "*20}moveTo({fmt(pairs[0][0])}, {fmt(pairs[0][1])})')
        for px, py in pairs[1:]:
            body.append(f'{" "*20}lineTo({fmt(px)}, {fmt(py)})')
        if p.startswith('<polygon'):
            body.append(f'{" "*20}close()')
    return f'''val {prop_name}: ImageVector
    get() = lucideIcon("{name}") {{
{chr(10).join(body)}
    }}'''


if __name__ == '__main__':
    names = sorted(p.stem for p in SRC.glob('*.svg'))
    print(len(names), 'icons:', ' '.join(names))
