#!/usr/bin/env python3
"""Original Game Stats art and reproducible Console ZIP; Python stdlib + macOS Swift."""
import csv
import json
from pathlib import Path
import subprocess
import zipfile

ROOT = Path(__file__).resolve().parent
GOLD, CREAM, DARK, MINT = '#D5A54E', '#FFF0CD', '#3B2415', '#8ACBAA'
shapes = []
def circle(x,y,r,fill): shapes.append(dict(kind='circle',x=x,y=y,r=r,fill=fill,stroke='none',width=0))
def rect(x,y,w,h,fill,rx=0): shapes.append(dict(kind='rect',x=x,y=y,w=w,h=h,rx=rx,fill=fill,stroke='none',width=0))
def line(points,color=CREAM,width=18): shapes.append(dict(kind='line',points=points,fill='none',stroke=color,width=width))
def poly(points,fill=GOLD): shapes.append(dict(kind='polygon',points=points,fill=fill,stroke='none',width=0))
def art(index):
    shapes.clear()
    circle(256,256,228,'#24170F');circle(256,250,218,GOLD);circle(256,250,202,DARK)
    if index == 0: # Finished: clipboard with one decisive check.
        rect(152,144,208,230,CREAM,20);rect(208,128,96,40,GOLD,12)
        line([(192,250),(239,298),(321,205)],DARK,24)
        rect(195,330,120,12,GOLD,6)
    elif index == 1: # Solved: a key, distinct from the achievement medal.
        circle(209,209,76,GOLD);circle(209,209,37,DARK)
        poly([(245,245),(275,219),(380,324),(350,354)],CREAM)
        poly([(309,282),(337,254),(364,281),(336,309)],GOLD)
        poly([(340,313),(368,285),(395,312),(367,340)],GOLD)
    elif index == 2: # Hard solo: a flame around a focused core.
        poly([(254,118),(294,198),(333,165),(371,266),(351,333),(303,374),(226,384),(163,350),(137,290),(161,228),(188,255),(204,185)],'#EA9A72')
        poly([(253,213),(282,270),(307,247),(323,302),(298,344),(254,357),(213,340),(194,305),(222,269)],GOLD)
        poly([(256,279),(278,319),(256,338),(234,319)],CREAM)
    elif index == 3: # Unaided: compass, a self-directed solve.
        circle(256,256,118,MINT);circle(256,256,94,DARK)
        poly([(302,185),(273,273),(210,327),(239,239)],CREAM)
        poly([(302,185),(273,273),(256,256)],GOLD)
        circle(256,256,15,GOLD)
        for a,b in [([(256,110),(256,136)]), ([(256,376),(256,402)]), ([(110,256),(136,256)]), ([(376,256),(402,256)])]: line([a,b],GOLD,12)
    else: # Guess activity: four tally marks and a diagonal fifth.
        for x in [164,225,286,347]: line([(x,159),(x,354)],CREAM,24)
        line([(131,320),(381,202)],MINT,24)
    return list(shapes)
def svg(items):
    output=['<svg xmlns="http://www.w3.org/2000/svg" width="512" height="512" viewBox="0 0 512 512">']
    for s in items:
        style=f'fill="{s["fill"]}" stroke="{s["stroke"]}" stroke-width="{s["width"]}" stroke-linecap="round" stroke-linejoin="round"'
        if s['kind']=='circle': output.append(f'<circle cx="{s["x"]}" cy="{s["y"]}" r="{s["r"]}" {style}/>')
        elif s['kind']=='rect': output.append(f'<rect x="{s["x"]}" y="{s["y"]}" width="{s["w"]}" height="{s["h"]}" rx="{s["rx"]}" {style}/>')
        else:
            points=' '.join(f'{x},{y}' for x,y in s['points']);tag='polyline' if s['kind']=='line' else 'polygon'
            output.append(f'<{tag} points="{points}" {style}/>')
    return ''.join(output)+'</svg>\n'

catalog=json.loads((ROOT/'catalog.json').read_text())
for folder in ['source','upload','preview']: (ROOT/folder).mkdir(exist_ok=True)
for index,stat in enumerate(catalog['stats']):
    items=art(index)
    (ROOT/'source'/f'{stat["key"]}.svg').write_text(svg(items))
    (ROOT/'source'/f'{stat["key"]}.json').write_text(json.dumps(items))
with (ROOT/'upload'/'PlayerGameEvent.csv').open('w',newline='') as f:
    writer=csv.writer(f,lineterminator='\n');writer.writerow(['Event Name','Property Name','Property Type'])
    writer.writerows((catalog['event'],key,kind) for key,kind in [('playerMode','STRING'),('difficulty','STRING'),('outcome','STRING'),('guessCount','INT64'),('hintUsed','BOOL'),('hardSoloWin','BOOL'),('unaidedMediumSoloWin','BOOL')])
with (ROOT/'upload'/'RepetitiveStatsConfig.csv').open('w',newline='') as f:
    writer=csv.writer(f,lineterminator='\n')
    writer.writerow(['Stat Id','Event Name','Event Property Name','Aggregation Type','Stat Display Name','Stat Description','Filter Property','Filter Operator','Filter Value','Is Competitive','Min limit','Max limit','Icon File Name','Good value direction','Unit'])
    for s in catalog['stats']:
        writer.writerow([s['key'],catalog['event'],s['property'],s['aggregation'],s['name'],s['description'],*(s['filter'] or ['','','']),str(s['competitive']).lower(),s.get('minimumPerHour',''),s.get('maximumPerHour',''),s['key']+'.png','INCREASING','UNITLESS'])
subprocess.run(['swift','-module-cache-path','/private/tmp/numbcrunch-icon-module-cache',str(ROOT.parent/'play_games'/'render_assets.swift'),str(ROOT)],check=True)
for s in catalog['stats']: (ROOT/'source'/f'{s["key"]}.json').unlink()
with zipfile.ZipFile(ROOT/'game-stats-v1.zip','w',zipfile.ZIP_DEFLATED) as archive:
    for name in ['RepetitiveStatsConfig.csv']+[s['key']+'.png' for s in catalog['stats']]:
        entry=zipfile.ZipInfo(name,(2026,11,13,0,0,0));entry.compress_type=zipfile.ZIP_DEFLATED
        archive.writestr(entry,(ROOT/'upload'/name).read_bytes())
print('Prepared event CSV, five distinct icons, preview and repetitive-only Game Stats ZIP.')
