"""Mascot asset source: 모아(햄스터) & 토리(다람쥐) + props.

Every asset is built from paths only so it converts 1:1 to Android VectorDrawable.
Named groups (android:name) are animation hooks for Compose/AVD.
"""
from lib import *

# ---------- palette ----------
P = dict(
    # 모아 (hamster)
    h_fur='#F4B56E', h_light='#FFF4E4', h_ear='#F7A7A0', h_paw='#FAD7B5',
    # 토리 (squirrel)
    s_fur='#C9773D', s_dark='#A85E2B', s_light='#FBE2C2', s_tail='#B9662F', s_tail2='#E39B5C',
    # shared
    eye='#3A2418', white='#FFFFFF', blush='#FF8F8F', nose='#E9747A', mouth_in='#9C3F43',
    shadow='#3A2418',
    # props
    cap='#8A5A2B', cap2='#A87444', nut='#DB9550', nut_hi='#F4C58A',
    soil='#C29366', soil2='#A57A52', leaf='#78C166', leaf2='#57A34C',
    trunk='#A06F3F', canopy='#71C06D', canopy2='#96D58C',
    spark='#FFC83D', accent_line='#8FA6D6', accent_warm='#F0A060', gift='#FF8C7A', ribbon='#FFE08A',
)

def shadow(cx=120, cy=218, rx=64, ry=8):
    return S(ellipse(cx, cy, rx, ry), P['shadow'], alpha=0.10, name='shadow')

def sparkle(cx, cy, r, name=None):
    return S(star4(cx, cy, r), P['spark'], name=name)

def eyes_open(lx, rx_, y, rw=7, rh=9, hi=2.6, name='eyes'):
    return G([
        S(ellipse(lx, y, rw, rh), P['eye']), S(ellipse(rx_, y, rw, rh), P['eye']),
        S(circle(lx+2.2, y-3.2, hi), P['white']), S(circle(rx_+2.2, y-3.2, hi), P['white']),
    ], name=name, px=(lx+rx_)/2, py=y)

def eyes_happy(lx, rx_, y, w=8, name='eyes'):
    return G([
        L(f"M{lx-w},{y+3} Q{lx},{y-7} {lx+w},{y+3}", 3.4),
        L(f"M{rx_-w},{y+3} Q{rx_},{y-7} {rx_+w},{y+3}", 3.4),
    ], name=name, px=(lx+rx_)/2, py=y)

def eyes_sleep(lx, rx_, y, w=8, name='eyes'):
    return G([
        L(f"M{lx-w},{y} Q{lx},{y+7} {lx+w},{y}", 3.2),
        L(f"M{rx_-w},{y} Q{rx_},{y+7} {rx_+w},{y}", 3.2),
    ], name=name, px=(lx+rx_)/2, py=y)

def acorn(cx, cy, s=1.0, name='acorn'):
    """Acorn centred at (cx, cy); s=1 is ~26px tall."""
    k = s
    return G([
        O(ellipse(cx, cy+4*k, 10*k, 12*k), P['nut'], sw=2.4),
        S(ellipse(cx-3.5*k, cy+2*k, 3*k, 5*k, -15), P['nut_hi'], alpha=0.9),
        O(f"M{f(cx-13*k)},{f(cy-3*k)} Q{f(cx)},{f(cy-17*k)} {f(cx+13*k)},{f(cy-3*k)} "
          f"Q{f(cx)},{f(cy+1*k)} {f(cx-13*k)},{f(cy-3*k)} Z", P['cap'], sw=2.4),
        L(f"M{f(cx-6*k)},{f(cy-8*k)} L{f(cx-3*k)},{f(cy-3*k)} M{f(cx+1*k)},{f(cy-10*k)} L{f(cx+3*k)},{f(cy-3*k)} "
          f"M{f(cx+7*k)},{f(cy-8*k)} L{f(cx+8*k)},{f(cy-4*k)}", 1.6, color=P['cap2']),
        L(f"M{f(cx)},{f(cy-11*k)} Q{f(cx+1*k)},{f(cy-16*k)} {f(cx+5*k)},{f(cy-17*k)}", 2.4),
    ], name=name, px=cx, py=cy)

def gift(cx, cy, w=40, h=32, name='item'):
    x, y = cx-w/2, cy-h/2
    return G([
        O(rrect(x, y, w, h, 6), P['gift'], sw=3),
        S(f"M{f(cx-4)},{f(y)} H{f(cx+4)} V{f(y+h)} H{f(cx-4)} Z", P['ribbon']),
        O(f"M{f(cx)},{f(y)} C{f(cx-14)},{f(y-14)} {f(cx-18)},{f(y+2)} {f(cx)},{f(y)} Z", P['ribbon'], sw=2.4),
        O(f"M{f(cx)},{f(y)} C{f(cx+14)},{f(y-14)} {f(cx+18)},{f(y+2)} {f(cx)},{f(y)} Z", P['ribbon'], sw=2.4),
        L(rrect(x, y, w, h, 6), 3),
    ], name=name, px=cx, py=cy)

# =====================================================================
# 모아 (hamster) — 240 x 240, ground y≈214
# =====================================================================
def hamster(state):
    cheeks_big = state == 'stuffed'
    parts = [shadow()]
    # feet
    parts.append(G([O(ellipse(92, 208, 17, 9), P['h_paw']), O(ellipse(148, 208, 17, 9), P['h_paw'])], name='feet'))
    # ears
    parts.append(G([
        O(ellipse(76, 72, 21, 19, -25), P['h_fur']), S(ellipse(77, 74, 11, 10, -25), P['h_ear']),
    ], name='ear_left', px=84, py=86))
    parts.append(G([
        O(ellipse(164, 72, 21, 19, 25), P['h_fur']), S(ellipse(163, 74, 11, 10, 25), P['h_ear']),
    ], name='ear_right', px=156, py=86))
    # body (one round loaf)
    body = [O(ellipse(120, 140, 82, 74), P['h_fur'], sw=3.4),
            S(ellipse(120, 166, 52, 40), P['h_light']),
            S(ellipse(120, 88, 20, 12), P['h_light'], alpha=0.55)]  # forehead stripe
    parts.append(G(body, name='body', px=120, py=214))
    # cheek pouches
    if cheeks_big:
        parts.append(G([
            O(ellipse(70, 138, 32, 29), P['h_fur'], sw=3.2),
            O(ellipse(170, 138, 32, 29), P['h_fur'], sw=3.2),
            S(ellipse(74, 146, 22, 17), P['h_light']),
            S(ellipse(166, 146, 22, 17), P['h_light']),
            S(ellipse(64, 132, 12, 7.5), P['blush'], alpha=0.6),
            S(ellipse(176, 132, 12, 7.5), P['blush'], alpha=0.6),
        ], name='cheeks', px=120, py=136))
    else:
        parts.append(G([
            S(ellipse(80, 133, 12, 7.5), P['blush'], alpha=0.55),
            S(ellipse(160, 133, 12, 7.5), P['blush'], alpha=0.55),
        ], name='cheeks', px=120, py=133))
    # muzzle
    parts.append(S(ellipse(120, 130, 30, 21), P['h_light']))
    # eyes
    if state in ('happy',):
        parts.append(eyes_happy(95, 145, 114))
    elif state == 'sleep':
        parts.append(eyes_sleep(95, 145, 115))
    else:
        big = state == 'receive'
        parts.append(eyes_open(95, 145, 114, 7.5 if big else 7, 9.5 if big else 9, 3 if big else 2.6))
    # nose + mouth
    parts.append(S(ellipse(120, 124, 5.5, 3.8), P['nose'], name='nose'))
    if state == 'receive':
        parts.append(G([O(f"M111,131 Q120,146 129,131 Z", P['mouth_in'], sw=2.6)], name='mouth'))
    elif state == 'stuffed':
        parts.append(G([L("M114,134 Q120,131 126,134", 2.6)], name='mouth'))
    elif state == 'sleep':
        parts.append(G([L("M115,133 Q120,136 125,133", 2.4)], name='mouth'))
    else:
        parts.append(G([L("M110,131 Q115,137 120,131 Q125,137 130,131", 2.6)], name='mouth'))
    # paws / props
    if state == 'receive':
        parts.append(gift(120, 170, 44, 34))
        parts.append(G([O(ellipse(96, 168, 10, 9), P['h_paw']), O(ellipse(144, 168, 10, 9), P['h_paw'])], name='paws'))
        parts.append(G([sparkle(56, 70, 9), sparkle(186, 58, 11), sparkle(196, 100, 6)], name='sparkles'))
    elif state == 'happy':
        parts.append(G([O(ellipse(58, 118, 11, 10, -30), P['h_paw']), O(ellipse(182, 118, 11, 10, 30), P['h_paw'])], name='paws'))
        parts.append(G([sparkle(50, 72, 9), sparkle(192, 66, 10), sparkle(204, 150, 6), sparkle(38, 160, 6)], name='sparkles'))
    elif state == 'sleep':
        parts.append(G([O(ellipse(106, 180, 10, 8), P['h_paw']), O(ellipse(134, 180, 10, 8), P['h_paw'])], name='paws'))
        parts.append(G([
            L("M194,74 H208 L194,88 H208", 3.2, color=P['accent_line']),
            L("M212,50 H222 L212,60 H222", 2.8, color=P['accent_line']),
        ], name='zzz', px=208, py=70))
    elif state == 'stuffed':
        parts.append(G([O(ellipse(106, 180, 10, 8), P['h_paw']), O(ellipse(134, 180, 10, 8), P['h_paw'])], name='paws'))
        parts.append(G([  # sweat drop
            O("M196,86 Q204,100 196,106 Q188,100 196,86 Z", '#9FD3F5', sw=2.4),
        ], name='sweat'))
    else:  # idle
        parts.append(G([O(ellipse(106, 178, 10, 8.5), P['h_paw']), O(ellipse(134, 178, 10, 8.5), P['h_paw'])], name='paws'))
    return parts

# =====================================================================
# 토리 (squirrel) — 240 x 240, ground y≈214
# =====================================================================
def squirrel_tail():
    return G([
        O("M150,204 C206,210 232,160 220,112 C210,70 172,40 146,62 "
          "C128,78 140,104 162,100 C182,96 190,122 180,146 C170,170 146,176 150,204 Z", P['s_tail'], sw=3.4),
        S("M160,190 C198,190 214,152 206,118 C198,86 172,64 156,74 "
          "C170,70 192,88 196,118 C200,150 184,178 160,190 Z", P['s_tail2'], alpha=0.85),
    ], name='tail', px=160, py=200)

def squirrel(state):
    parts = [shadow(122, 218, 70, 8), squirrel_tail()]
    arms_up = state == 'cheer'
    # feet
    parts.append(G([O(ellipse(96, 208, 18, 9), P['s_dark']), O(ellipse(134, 208, 18, 9), P['s_dark'])], name='feet'))
    # body
    lean = -6 if state == 'bury' else 0
    body = G([
        O(ellipse(114, 162, 44, 48), P['s_fur'], sw=3.4),
        S(ellipse(112, 172, 27, 33), P['s_light']),
    ], name='body', px=114, py=210, rot=lean)
    parts.append(body)
    # head group
    head = []
    head.append(G([  # ears with tufts
        O("M76,82 C66,56 72,40 82,36 C90,48 96,62 96,76 Z", P['s_fur'], sw=3),
        S("M80,74 C74,58 78,48 83,45 C88,54 91,64 90,74 Z", P['h_ear']),
        O("M82,36 C80,28 86,24 88,30 C86,32 84,34 82,36 Z", P['s_dark'], sw=2.4),
    ], name='ear_left', px=86, py=78))
    head.append(G([
        O("M140,82 C150,56 144,40 134,36 C126,48 120,62 120,76 Z", P['s_fur'], sw=3),
        S("M136,74 C142,58 138,48 133,45 C128,54 125,64 126,74 Z", P['h_ear']),
        O("M134,36 C136,28 130,24 128,30 C130,32 132,34 134,36 Z", P['s_dark'], sw=2.4),
    ], name='ear_right', px=130, py=78))
    head.append(O(ellipse(108, 104, 46, 40), P['s_fur'], sw=3.4))
    head.append(S(ellipse(108, 118, 25, 17), P['s_light']))
    head.append(S(ellipse(80, 116, 10, 6.5), P['blush'], alpha=0.5))
    head.append(S(ellipse(136, 116, 10, 6.5), P['blush'], alpha=0.5))
    if state in ('cheer', 'wave'):
        head.append(eyes_happy(92, 124, 101, 7))
    else:
        big = state == 'present'
        head.append(eyes_open(92, 124, 101, 6.5 if not big else 7, 8.5 if not big else 9.5, 2.4 if not big else 2.9))
    head.append(S(ellipse(108, 112, 5, 3.6), P['eye'], name='nose'))
    if state == 'cheer':
        head.append(G([O("M100,119 Q108,132 116,119 Z", P['mouth_in'], sw=2.4)], name='mouth'))
    else:
        head.append(G([L("M100,119 Q104,124 108,119 Q112,124 116,119", 2.4)], name='mouth'))
    parts.append(G(head, name='head', px=108, py=140, rot=(-8 if state == 'bury' else 0)))
    # arms / props
    if state == 'idle':
        parts.append(G([O(ellipse(100, 150, 9, 8), P['s_dark']), O(ellipse(124, 150, 9, 8), P['s_dark'])], name='paws'))
    elif state == 'present':
        parts.append(acorn(112, 150, 1.25))
        parts.append(G([O(ellipse(94, 158, 8.5, 8), P['s_dark']), O(ellipse(130, 158, 8.5, 8), P['s_dark'])], name='paws'))
        parts.append(G([sparkle(62, 132, 8), sparkle(160, 140, 7), sparkle(150, 40, 6)], name='sparkles'))
    elif state == 'wave':
        parts.append(G([O(ellipse(104, 152, 9, 8), P['s_dark'])], name='paw_left'))
        parts.append(G([
            L("M132,150 C146,140 154,124 158,108", 9, color=P['s_fur']),
            O(ellipse(160, 98, 10, 11, 20), P['s_dark']),
        ], name='paw_wave', px=140, py=140))
        parts.append(G([L("M176,82 Q184,92 178,104", 2.8, color=P['accent_warm']), L("M186,74 Q198,92 188,110", 2.8, color=P['accent_warm'])], name='motion'))
    elif state == 'cheer':
        parts.append(G([O(ellipse(62, 96, 10, 11, -25), P['s_dark']), O(ellipse(156, 92, 10, 11, 25), P['s_dark'])], name='paws'))
        parts.append(G([
            S(rrect(40, 50, 8, 14, 2), '#FF6B73'), S(rrect(184, 40, 8, 14, 2), P['spark']),
            S(rrect(206, 96, 8, 14, 2), '#7CC5F0'), S(rrect(24, 120, 8, 14, 2), P['leaf']),
            sparkle(60, 30, 8), sparkle(170, 22, 7),
        ], name='confetti'))
    elif state == 'bury':
        parts.append(G([
            O("M18,212 C24,178 76,172 86,212 Z", P['soil'], sw=3),
            L("M34,198 Q40,192 46,196 M58,192 Q64,188 70,194", 2.2, color=P['soil2']),
        ], name='mound'))
        parts.append(acorn(52, 176, 0.8, name='acorn'))
        parts.append(G([O(ellipse(80, 170, 9, 8), P['s_dark']), O(ellipse(98, 158, 9, 8), P['s_dark'])], name='paws'))
        parts.append(G([L("M44,150 L40,140 M56,146 L56,136 M68,150 L72,140", 2.8, color=P['accent_warm'])], name='pat_lines'))
    return parts

# =====================================================================
# Props — 96 x 96
# =====================================================================
def mound(cx=48, base=84, w=36, h=22):
    return G([
        O(f"M{cx-w},{base} C{cx-w+6},{base-h-4} {cx+w-6},{base-h-4} {cx+w},{base} Z", P['soil'], sw=2.8),
        L(f"M{cx-18},{base-10} q5,-5 10,-1 M{cx+6},{base-12} q5,-5 10,-1", 2, color=P['soil2']),
    ], name='mound')

def prop_buried():
    return [mound(), G([acorn(48, 62, 0.85)], name='acorn_peek')]

def prop_sprout():
    return [mound(), G([
        L("M48,66 C48,52 48,44 48,36", 3.2, color=P['leaf2']),
        O("M48,44 C36,44 28,36 28,26 C40,26 48,34 48,44 Z", P['leaf'], sw=2.4),
        O("M48,40 C58,40 68,32 68,20 C56,20 48,28 48,40 Z", P['leaf'], sw=2.4),
    ], name='sprout', px=48, py=66)]

def prop_ready():
    return [mound(48, 88, 34, 14), acorn(48, 50, 1.45, name='acorn'),
            G([sparkle(18, 26, 7), sparkle(80, 20, 8), sparkle(82, 58, 5)], name='sparkles')]

def prop_acorn():
    return [acorn(48, 50, 1.6)]

def prop_tree(big=False):
    k = 1.0 if big else 0.78
    cy = 40 if big else 48
    return [
        S(ellipse(48, 88, 26*k, 5), P['shadow'], alpha=0.1),
        O(f"M{f(48-6*k)},88 L{f(48-4*k)},{f(cy+10)} H{f(48+4*k)} L{f(48+6*k)},88 Z", P['trunk'], sw=2.6),
        G([
            O(circle(48, cy, 26*k), P['canopy'], sw=2.8),
            S(circle(40, cy-7*k, 10*k), P['canopy2'], alpha=0.9),
            S(circle(58, cy+6*k, 4*k), P['canopy2'], alpha=0.8),
        ], name='canopy', px=48, py=cy+26*k),
    ] + ([acorn(62, cy+12, 0.5, name='acorn_fruit')] if big else [])

def prop_gift():
    return [gift(48, 54, 50, 40)]

# composite onboarding scene 360 x 240
def duo_scene():
    return [
        G(hamster('receive'), tx=-10, ty=10, sx=0.82, sy=0.82, px=120, py=214, name='moa'),
        G(squirrel('present'), tx=138, ty=10, sx=0.82, sy=0.82, px=120, py=214, name='tori'),
    ]

ASSETS = {
    # name: (builder, w, h, dp, title)
    'mascot_moa_idle':     (lambda: hamster('idle'), 240, 240, 160, '모아 · 기본'),
    'mascot_moa_receive':  (lambda: hamster('receive'), 240, 240, 160, '모아 · 받았다!'),
    'mascot_moa_stuffed':  (lambda: hamster('stuffed'), 240, 240, 160, '모아 · 볼 빵빵'),
    'mascot_moa_happy':    (lambda: hamster('happy'), 240, 240, 160, '모아 · 기뻐요'),
    'mascot_moa_sleep':    (lambda: hamster('sleep'), 240, 240, 160, '모아 · 쿨쿨(빈 상태)'),
    'mascot_tori_idle':    (lambda: squirrel('idle'), 240, 240, 160, '토리 · 기본'),
    'mascot_tori_present': (lambda: squirrel('present'), 240, 240, 160, '토리 · 이거 기억나?'),
    'mascot_tori_bury':    (lambda: squirrel('bury'), 240, 240, 160, '토리 · 묻어 둘게'),
    'mascot_tori_wave':    (lambda: squirrel('wave'), 240, 240, 160, '토리 · 잘 가~'),
    'mascot_tori_cheer':   (lambda: squirrel('cheer'), 240, 240, 160, '토리 · 축하해!'),
    'prop_acorn_buried':   (prop_buried, 96, 96, 48, '도토리 1단계 · 묻힘'),
    'prop_acorn_sprout':   (prop_sprout, 96, 96, 48, '도토리 2단계 · 새싹'),
    'prop_acorn_ready':    (prop_ready, 96, 96, 48, '도토리 3단계 · 다시 볼 시간'),
    'prop_acorn':          (prop_acorn, 96, 96, 48, '도토리 아이콘'),
    'prop_tree_small':     (lambda: prop_tree(False), 96, 96, 48, '절약 숲 · 어린 나무'),
    'prop_tree_big':       (lambda: prop_tree(True), 96, 96, 48, '절약 숲 · 큰 나무'),
    'prop_gift':           (prop_gift, 96, 96, 48, '담긴 물건(상자)'),
    'scene_duo_onboarding':(duo_scene, 360, 240, 300, '온보딩 · 모아와 토리'),
}
