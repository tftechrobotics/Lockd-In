import math, sys
INK="#2B251F"; FACE="#FBF8F2"; BG="#F3EEE5"
CX, CY = 54.0, 60.0
def ticks():
    out=[]
    for i in range(60):
        a=math.radians(i*6); hourtick=i%5==0; q=i%15==0
        if not hourtick:
            x1,y1=CX+math.sin(a)*12.6, CY-math.cos(a)*12.6
            x2,y2=CX+math.sin(a)*13.6, CY-math.cos(a)*13.6
            out.append((f"M{x1:.2f},{y1:.2f}L{x2:.2f},{y2:.2f}", 0.5)); continue
        r1=10.6 if q else 11.8; r2=13.6
        x1,y1=CX+math.sin(a)*r1, CY-math.cos(a)*r1
        x2,y2=CX+math.sin(a)*r2, CY-math.cos(a)*r2
        out.append((f"M{x1:.2f},{y1:.2f}L{x2:.2f},{y2:.2f}", 1.5 if q else 1.0))
    return out
shackle="M45.75,47V41A8.25,8.25 0 0 1 62.25,41V47"
ring_r, ring_w = 17.5, 5.0
hour_a=math.radians(120); hx,hy=CX+math.sin(hour_a)*7.6, CY-math.cos(hour_a)*7.6
minute=f"M{CX},{CY}L{CX},{CY-10.2}"
hour=f"M{CX},{CY}L{hx:.2f},{hy:.2f}"
def circle_path(r): return f"M{CX-r},{CY}A{r},{r} 0 1 0 {CX+r},{CY}A{r},{r} 0 1 0 {CX-r},{CY}Z"

def vector(mono):
    ink = "#FFFFFFFF" if mono else "#FF2B251F"
    tick_ink = "#FFFFFFFF" if mono else "#CC2B251F"
    p=[]
    p.append(f'<path android:strokeColor="{ink}" android:strokeWidth="5.5" android:strokeLineCap="butt" android:pathData="{shackle}"/>')
    if not mono: p.append(f'<path android:fillColor="#FFFBF8F2" android:pathData="{circle_path(15.2)}"/>')
    p.append(f'<path android:strokeColor="{ink}" android:strokeWidth="{ring_w}" android:pathData="{circle_path(ring_r)}"/>')
    for d,w in ticks(): p.append(f'<path android:strokeColor="{tick_ink}" android:strokeWidth="{w}" android:strokeLineCap="round" android:pathData="{d}"/>')
    p.append(f'<path android:strokeColor="{ink}" android:strokeWidth="2.2" android:strokeLineCap="round" android:pathData="{minute}"/>')
    p.append(f'<path android:strokeColor="{ink}" android:strokeWidth="2.5" android:strokeLineCap="round" android:pathData="{hour}"/>')
    p.append(f'<path android:fillColor="{ink}" android:pathData="{circle_path(1.7)}"/>')
    body="\n    ".join(p)
    note = "Monochrome layer for themed icons (Android 13+)." if mono else "Owner-approved icon (2026-11-02): padlock with a clock face, espresso on cream."
    return f'''<?xml version="1.0" encoding="utf-8"?>
<!-- {note} Geometry: tools in docs; fits the 66dp safe zone. -->
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="108dp" android:height="108dp" android:viewportWidth="108" android:viewportHeight="108">
    {body}
</vector>
'''
def svg(mask):
    p=[f'<rect width="108" height="108" fill="{BG}"/>']
    p.append(f'<path d="{shackle}" stroke="{INK}" stroke-width="5.5" fill="none"/>')
    p.append(f'<circle cx="{CX}" cy="{CY}" r="15.2" fill="{FACE}"/>')
    p.append(f'<circle cx="{CX}" cy="{CY}" r="{ring_r}" stroke="{INK}" stroke-width="{ring_w}" fill="none"/>')
    for d,w in ticks(): p.append(f'<path d="{d}" stroke="{INK}" stroke-opacity="0.8" stroke-width="{w}" stroke-linecap="round"/>')
    p.append(f'<path d="{minute}" stroke="{INK}" stroke-width="2.2" stroke-linecap="round"/>')
    p.append(f'<path d="{hour}" stroke="{INK}" stroke-width="2.5" stroke-linecap="round"/>')
    p.append(f'<circle cx="{CX}" cy="{CY}" r="1.7" fill="{INK}"/>')
    clip = '<clipPath id="m"><rect x="18" y="18" width="72" height="72" rx="20"/></clipPath>' if mask=="squircle" else '<clipPath id="m"><circle cx="54" cy="54" r="36"/></clipPath>'
    return f'<svg xmlns="http://www.w3.org/2000/svg" viewBox="18 18 72 72" width="360" height="360"><defs>{clip}</defs><g clip-path="url(#m)">{"".join(p)}</g></svg>'
open("fg.xml","w").write(vector(False)); open("mono.xml","w").write(vector(True))
open("sq.svg","w").write(svg("squircle")); open("ci.svg","w").write(svg("circle"))
