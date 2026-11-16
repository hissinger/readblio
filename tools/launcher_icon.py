#!/usr/bin/python3
"""
앱 아이콘 앞면 `app/src/main/res/drawable/ic_launcher_foreground.xml` 을 만든다.
검은 바탕(ic_launcher.xml)에 흰 글자 `R`(좌우로 뒤집음)·`B` 를 세로획끼리 맞대 펼친 책처럼 둔다.
글꼴은 JetBrains Mono ExtraBold(SIL OFL 1.1, Android Studio 에 들어 있다)의 글자 모양을 선으로 바꾼 것이고,
같은 색 테두리(STROKE)를 더해 한 단계 더 굵게 그린다. /usr/bin/python3 (fontTools) 로 돌린다.
"""
import os
from fontTools.ttLib import TTFont
from fontTools.pens.svgPathPen import SVGPathPen
from fontTools.pens.transformPen import TransformPen
from fontTools.pens.boundsPen import BoundsPen

FONT = "/Applications/Android Studio.app/Contents/jbr/Contents/Home/lib/fonts/JetBrainsMono-ExtraBold.ttf"
OUT = os.path.join(os.path.dirname(__file__), "../app/src/main/res/drawable/ic_launcher_foreground.xml")
SIZE = 32       # 글자 크기(108 칸 기준, 보이는 원은 가운데 72)
STROKE = 0.7    # 글자 바깥으로 더하는 굵기
OVERLAP = 0.2   # 가운데 선에서 두 글자가 서로 넘어가는 폭(테두리 포함, 두 세로획이 붙어 굵은 책등 하나로 보이게)
CX, CY = 54, 54

font = TTFont(FONT)
glyphs = font.getGlyphSet()
cmap = font.getBestCmap()
scale = SIZE / font["head"].unitsPerEm


def bounds(ch):
    pen = BoundsPen(glyphs)
    glyphs[cmap[ord(ch)]].draw(pen)
    return pen.bounds  # xmin, ymin, xmax, ymax (글꼴 단위, y 는 위로)


def path(ch, mirror, edge):
    """[edge]: 뒤집은 R 은 테두리를 더한 오른쪽 끝, B 는 왼쪽 끝이 놓일 x"""
    xmin, ymin, xmax, ymax = bounds(ch)
    sx = -scale if mirror else scale
    # 테두리를 더한 끝이 edge 에 오게
    dx = edge - STROKE - (-xmin * scale) if mirror else edge + STROKE - xmin * scale
    dy = CY + (ymin + ymax) / 2 * scale  # 위아래 가운데
    svg = SVGPathPen(glyphs, ntos=lambda v: ("%.2f" % v).rstrip("0").rstrip("."))
    glyphs[cmap[ord(ch)]].draw(TransformPen(svg, (sx, 0, 0, -scale, dx, dy)))
    return svg.getCommands()


# 글자마다 따로 둔다: 뒤집은 R 은 감는 방향이 반대라 한 경로로 합치면 겹친 곳이 비어 보인다
r = path("R", True, CX + OVERLAP)
b = path("B", False, CX - OVERLAP)
xml = f'''<?xml version="1.0" encoding="utf-8"?>
<!-- Readblio: 검은 바탕에 흰 글자 R(뒤집음)·B 를 맞대 펼친 책처럼. JetBrains Mono ExtraBold(SIL OFL 1.1)의 글자 모양을 선으로 바꾼 것, tools/launcher_icon.py -->
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="108dp" android:height="108dp"
    android:viewportWidth="108" android:viewportHeight="108">
{"".join(f"""    <path android:fillColor="#FFFFFF" android:strokeColor="#FFFFFF"
        android:strokeWidth="{STROKE * 2:g}" android:strokeLineJoin="round"
        android:pathData="{d}" />
""" for d in (r, b))}</vector>
'''
open(OUT, "w").write(xml)
print(OUT)
