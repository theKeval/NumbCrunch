#!/usr/bin/env python3
"""Original vector artwork + Console import data. Run from any directory; Python standard library only."""
import csv
import json
import math
from pathlib import Path
import subprocess
import zipfile
from xml.sax.saxutils import escape

ROOT = Path(__file__).resolve().parent
GOLD, CREAM, DARK, MINT, COPPER = '#D5A54E', '#FFF0CD', '#3B2415', '#8ACBAA', '#EA9A72'
shapes = []
def shape(kind, **kwargs): shapes.append(dict(kind=kind, **kwargs))
def circle(x,y,r,fill,stroke='none',width=0): shape('circle',x=x,y=y,r=r,fill=fill,stroke=stroke,width=width)
def rect(x,y,w,h,fill,rx=0,stroke='none',width=0): shape('rect',x=x,y=y,w=w,h=h,rx=rx,fill=fill,stroke=stroke,width=width)
def line(points,color=CREAM,width=15): shape('line',points=points,fill='none',stroke=color,width=width)
def poly(points,fill=GOLD,stroke='none',width=0): shape('polygon',points=points,fill=fill,stroke=stroke,width=width)
def star(x,y,r,fill=CREAM):
    poly([(round(x+math.cos(-math.pi/2+i*math.pi/5)*(r if i%2==0 else r*.45),2),
           round(y+math.sin(-math.pi/2+i*math.pi/5)*(r if i%2==0 else r*.45),2)) for i in range(10)],fill)
def badge():
    circle(256,256,228,'#24170F')
    circle(256,250,218,GOLD)
    circle(256,250,202,'#77502B')
    circle(256,250,188,DARK)
    circle(256,250,174,'none','#684526',3)
    # Quiet grain confined to the medallion, behind bold pictograms.
    for y,span in [(130,60),(158,110),(352,110),(380,60)]:
        line([(256-span,y),(256+span,y+7)],'#4D311D',4)
    circle(256,67,5,CREAM);circle(256,433,5,GOLD)
def art(key):
    global shapes
    shapes=[];badge()
    if key=='learning_the_ropes':
        poly([(130,164),(236,181),(256,202),(276,181),(382,164),(382,326),(276,344),(256,363),(236,344),(130,326)],GOLD)
        poly([(144,181),(232,195),(243,207),(243,334),(228,326),(144,313)],CREAM)
        poly([(269,207),(280,195),(368,181),(368,313),(284,326),(269,334)],'#E4BD75')
        line([(164,220),(216,228)],DARK,9);line([(164,249),(216,257)],DARK,9)
        line([(292,263),(311,282),(349,237)],DARK,12)
    elif key=='first_solo_solve':
        poly([(183,299),(169,390),(225,363),(258,388),(271,303)],COPPER)
        circle(256,229,104,GOLD);circle(256,229,85,DARK);star(256,229,67)
    elif key=='getting_the_hang_of_it':
        for x,y in [(124,235),(206,184),(288,133)]:
            rect(x,y,98,140,GOLD,16);rect(x+12,y+12,74,116,CREAM,9)
        circle(173,305,12,DARK)
        for y in [237,270]:circle(255,y,10,DARK)
        for y in [177,207,237]:circle(337,y,9,DARK)
    elif key=='double_digits':
        rect(122,142,268,224,GOLD,18);rect(142,162,228,184,DARK,9)
        for y in [208,299]:
            line([(146,y),(366,y)],CREAM,7)
            for x in [170,213,256,299,342]:rect(x-13,y-25,26,50,MINT if y==208 else GOLD,10)
    elif key=='persistent_puzzler':
        for x,y,c in [(143,143,CREAM),(265,143,GOLD),(143,265,GOLD),(265,265,COPPER)]:rect(x,y,104,104,c,12)
        circle(247,195,24,CREAM);circle(317,247,24,GOLD)
        circle(265,317,24,COPPER);circle(195,265,24,GOLD)
        circle(255,255,18,DARK)
    elif key=='century_solver':
        poly([(132,183),(200,237),(256,142),(312,237),(380,183),(348,325),(164,325)],GOLD)
        rect(164,338,184,28,CREAM,10);star(256,270,40,CREAM)
        for x,y in [(132,181),(256,140),(380,181)]:circle(x,y,15,CREAM)
    elif key=='medium_mastered':
        line([(222,146),(222,120),(290,120),(290,146)],GOLD,14)
        poly([(198,161),(314,161),(343,337),(169,337)],MINT)
        poly([(218,183),(294,183),(310,311),(202,311)],DARK)
        poly([(256,206),(229,265),(256,295),(283,265)],CREAM)
        rect(181,341,150,25,GOLD,8);rect(193,147,126,25,GOLD,8)
    elif key=='hard_earned':
        poly([(170,157),(342,157),(394,235),(256,375),(118,235)],COPPER)
        line([(170,157),(218,235),(256,375),(294,235),(342,157)],DARK,10)
        line([(118,235),(394,235)],DARK,10)
        poly([(170,157),(218,235),(256,157)],CREAM)
        poly([(256,157),(294,235),(342,157)],GOLD)
    elif key=='hard_regular':
        poly([(110,342),(200,192),(251,273),(304,164),(405,342)],COPPER)
        poly([(200,192),(167,246),(204,235),(227,236)],CREAM)
        poly([(304,164),(262,251),(304,227),(348,248)],CREAM)
        line([(131,363),(382,363)],GOLD,19);star(175,142,24,GOLD)
    elif key=='unaided_deduction':
        poly([(108,254),(174,183),(256,156),(338,183),(404,254),(338,325),(256,352),(174,325)],MINT)
        poly([(130,254),(187,205),(256,182),(325,205),(382,254),(325,303),(256,326),(187,303)],DARK)
        circle(256,254,62,CREAM);circle(256,254,32,DARK);circle(272,237,10,GOLD)
        for p in [[(204,129),(192,108)],[(256,117),(256,92)],[(308,129),(320,108)]]:line(p,GOLD,10)
    else:
        line([(175,158),(123,158),(123,220),(170,252)],GOLD,22)
        line([(337,158),(389,158),(389,220),(342,252)],GOLD,22)
        poly([(168,137),(344,137),(328,260),(294,294),(218,294),(184,260)],GOLD)
        rect(242,285,28,59,CREAM);rect(192,340,128,30,GOLD,10);star(256,216,45,DARK)
    return shapes
def svg(items):
    result=['<svg xmlns="http://www.w3.org/2000/svg" width="512" height="512" viewBox="0 0 512 512">']
    for s in items:
        style=f'fill="{s["fill"]}" stroke="{s["stroke"]}" stroke-width="{s["width"]}" stroke-linecap="round" stroke-linejoin="round"'
        if s['kind']=='circle': result.append(f'<circle cx="{s["x"]}" cy="{s["y"]}" r="{s["r"]}" {style}/>')
        elif s['kind']=='rect': result.append(f'<rect x="{s["x"]}" y="{s["y"]}" width="{s["w"]}" height="{s["h"]}" rx="{s["rx"]}" {style}/>')
        else:
            points=' '.join(f'{x},{y}' for x,y in s['points'])
            tag='polyline' if s['kind']=='line' else 'polygon'
            result.append(f'<{tag} points="{points}" {style}/>')
    return ''.join(result)+'</svg>\n'

catalog=json.loads((ROOT/'catalog.json').read_text())
for folder in ['source','upload','preview']:(ROOT/folder).mkdir(exist_ok=True)
assert len(catalog['achievements'])==10
assert sum(a['points'] for a in catalog['achievements'])==450
all_keys=[a['key'] for a in catalog['achievements']]+[catalog['leaderboard']['key']]
for key in all_keys:
    items=art(key)
    (ROOT/'source'/f'{key}.svg').write_text(svg(items))
    (ROOT/'source'/f'{key}.json').write_text(json.dumps(items,separators=(',',':'))+'\n')
with (ROOT/'upload'/'AchievementsMetadata.csv').open('w',newline='') as f:
    writer=csv.writer(f,lineterminator='\n')
    for order,a in enumerate(catalog['achievements'],1):
        assert len(a['name'])<=100 and len(a['description'])<=500 and ',' not in a['name']+a['description']
        writer.writerow([a['name'],a['description'],'True' if a['steps']>1 else 'False',a['steps'] if a['steps']>1 else '', 'Revealed',a['points'],order])
with (ROOT/'upload'/'AchievementsIconsMappings.csv').open('w',newline='') as f:
    csv.writer(f,lineterminator='\n').writerows((a['name'],a['key']+'.png') for a in catalog['achievements'])
strings=['<?xml version="1.0" encoding="utf-8"?>','<resources>']
for a in catalog['achievements']:
    for field in ['name','description']:
        strings.append(f'    <string name="achievement_{a["key"]}_{field}">{escape(a[field])}</string>')
strings.append('</resources>')
(ROOT.parents[1]/'gameplay/src/main/res/values/strings-achievements.xml').write_text('\n'.join(strings)+'\n')
subprocess.run(['swift','-module-cache-path','/private/tmp/numbcrunch-icon-module-cache',str(ROOT/'render_assets.swift'),str(ROOT)],check=True)
for key in all_keys:(ROOT/'source'/f'{key}.json').unlink()
with zipfile.ZipFile(ROOT/'achievements-v1.zip','w',zipfile.ZIP_DEFLATED) as z:
    for name in ['AchievementsMetadata.csv','AchievementsIconsMappings.csv']+[a['key']+'.png' for a in catalog['achievements']]:
        p=ROOT/'upload'/name
        assert p.stat().st_size<1_000_000
        entry=zipfile.ZipInfo(name,(2026,11,13,0,0,0));entry.compress_type=zipfile.ZIP_DEFLATED
        z.writestr(entry,p.read_bytes())
print('Exported 10 achievement icons, leaderboard icon, SVG sources, previews and Console import ZIP.')
