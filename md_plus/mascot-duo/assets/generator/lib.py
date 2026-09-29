import math
from xml.sax.saxutils import escape

OUT = '#5B3A22'   # shared outline

def f(v):
    s = f"{v:.2f}".rstrip('0').rstrip('.')
    return '0' if s in ('-0', '') else s

def ellipse(cx, cy, rx, ry, rot=0):
    a = math.radians(rot)
    dx, dy = rx*math.cos(a), rx*math.sin(a)
    x1, y1, x2, y2 = cx+dx, cy+dy, cx-dx, cy-dy
    return (f"M{f(x1)},{f(y1)} A{f(rx)},{f(ry)} {f(rot)} 1,1 {f(x2)},{f(y2)} "
            f"A{f(rx)},{f(ry)} {f(rot)} 1,1 {f(x1)},{f(y1)} Z")

def circle(cx, cy, r): return ellipse(cx, cy, r, r)

def rrect(x, y, w, h, r):
    return (f"M{f(x+r)},{f(y)} H{f(x+w-r)} A{f(r)},{f(r)} 0 0,1 {f(x+w)},{f(y+r)} V{f(y+h-r)} "
            f"A{f(r)},{f(r)} 0 0,1 {f(x+w-r)},{f(y+h)} H{f(x+r)} A{f(r)},{f(r)} 0 0,1 {f(x)},{f(y+h-r)} "
            f"V{f(y+r)} A{f(r)},{f(r)} 0 0,1 {f(x+r)},{f(y)} Z")

def star4(cx, cy, r):
    k = r*0.28
    return (f"M{f(cx)},{f(cy-r)} Q{f(cx+k)},{f(cy-k)} {f(cx+r)},{f(cy)} Q{f(cx+k)},{f(cy+k)} {f(cx)},{f(cy+r)} "
            f"Q{f(cx-k)},{f(cy+k)} {f(cx-r)},{f(cy)} Q{f(cx-k)},{f(cy-k)} {f(cx)},{f(cy-r)} Z")

class S:  # shape
    def __init__(self, d, fill=None, stroke=None, sw=0, alpha=1.0, name=None):
        self.d, self.fill, self.stroke, self.sw, self.alpha, self.name = d, fill, stroke, sw, alpha, name

class G:  # group
    def __init__(self, children, tx=0, ty=0, rot=0, px=0, py=0, sx=1, sy=1, name=None):
        self.children, self.tx, self.ty, self.rot, self.px, self.py, self.sx, self.sy, self.name = children, tx, ty, rot, px, py, sx, sy, name

def O(d, fill, sw=3, **k):   # outlined shape
    return S(d, fill, OUT, sw, **k)

def L(d, sw=3, color=OUT, **k):  # line only
    return S(d, None, color, sw, **k)

def _svg(node, ind):
    p = '  '*ind
    if isinstance(node, S):
        a = [f'd="{node.d}"']
        a.append(f'fill="{node.fill}"' if node.fill else 'fill="none"')
        if node.alpha != 1 and node.fill: a.append(f'fill-opacity="{f(node.alpha)}"')
        if node.stroke:
            a += [f'stroke="{node.stroke}"', f'stroke-width="{f(node.sw)}"', 'stroke-linecap="round"', 'stroke-linejoin="round"']
        if node.name: a.insert(0, f'id="{node.name}"')
        return f'{p}<path {" ".join(a)}/>\n'
    t = []
    if node.tx or node.ty: t.append(f"translate({f(node.tx)} {f(node.ty)})")
    if node.rot or node.sx != 1 or node.sy != 1:
        t.append(f"translate({f(node.px)} {f(node.py)})")
        if node.rot: t.append(f"rotate({f(node.rot)})")
        if node.sx != 1 or node.sy != 1: t.append(f"scale({f(node.sx)} {f(node.sy)})")
        t.append(f"translate({f(-node.px)} {f(-node.py)})")
    attrs = (f' id="{node.name}"' if node.name else '') + (f' transform="{" ".join(t)}"' if t else '')
    return f'{p}<g{attrs}>\n' + ''.join(_svg(c, ind+1) for c in node.children) + f'{p}</g>\n'

def to_svg(root, w, h, title):
    return (f'<svg xmlns="http://www.w3.org/2000/svg" width="{w}" height="{h}" viewBox="0 0 {w} {h}">\n'
            f'  <title>{escape(title)}</title>\n' + ''.join(_svg(c, 1) for c in root) + '</svg>\n')

def _hex(c, alpha=1.0):
    c = c.lstrip('#')
    if alpha != 1: return '#%02X%s' % (round(alpha*255), c.upper())
    return '#' + c.upper()

def _xml(node, ind):
    p = '    '*ind
    if isinstance(node, S):
        a = []
        if node.name: a.append(f'android:name="{node.name}"')
        a.append(f'android:pathData="{node.d}"')
        if node.fill:
            a.append(f'android:fillColor="{_hex(node.fill)}"')
            if node.alpha != 1: a.append(f'android:fillAlpha="{f(node.alpha)}"')
        if node.stroke:
            a += [f'android:strokeColor="{_hex(node.stroke)}"', f'android:strokeWidth="{f(node.sw)}"',
                  'android:strokeLineCap="round"', 'android:strokeLineJoin="round"']
        sep = '\n' + p + '    '
        return f'{p}<path{sep}' + sep.join(a) + ' />\n'
    a = []
    if node.name: a.append(f'android:name="{node.name}"')
    if node.px or node.py: a += [f'android:pivotX="{f(node.px)}"', f'android:pivotY="{f(node.py)}"']
    if node.rot: a.append(f'android:rotation="{f(node.rot)}"')
    if node.sx != 1: a.append(f'android:scaleX="{f(node.sx)}"')
    if node.sy != 1: a.append(f'android:scaleY="{f(node.sy)}"')
    if node.tx: a.append(f'android:translateX="{f(node.tx)}"')
    if node.ty: a.append(f'android:translateY="{f(node.ty)}"')
    head = f'{p}<group' + ((' ' + ' '.join(a)) if a else '') + '>\n'
    return head + ''.join(_xml(c, ind+1) for c in node.children) + f'{p}</group>\n'

def to_vector(root, w, h, dp):
    dh = round(dp*h/w)
    return ('<?xml version="1.0" encoding="utf-8"?>\n'
            '<!-- Generated from the mascot handoff source. Edit the generator, not this file. -->\n'
            f'<vector xmlns:android="http://schemas.android.com/apk/res/android"\n'
            f'    android:width="{dp}dp"\n    android:height="{dh}dp"\n'
            f'    android:viewportWidth="{w}"\n    android:viewportHeight="{h}">\n'
            + ''.join(_xml(c, 1) for c in root) + '</vector>\n')
