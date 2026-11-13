#!/usr/bin/env python3
"""Validate Console handoff artifacts and rehearse the four-achievement early route."""
import csv
import hashlib
import itertools
import json
from pathlib import Path
import re
import struct
import zipfile

ROOT=Path(__file__).resolve().parent
catalog=json.loads((ROOT/'catalog.json').read_text())
rows=catalog['achievements']
assert len(rows)==10 and len({a['key'] for a in rows})==10
assert len({a['name'] for a in rows})==10 and len({a['description'] for a in rows})==10
assert sum(a['points'] for a in rows)==450
kotlin=(ROOT.parents[1]/'domain/src/main/java/io/keval/apps/numbcrunch/domain/engagement/AchievementCatalog.kt').read_text()
definitions={key.lower():(int(steps),int(points)) for key,steps,points in
             re.findall(r'AchievementDefinition\(AchievementKey\.(\w+), (\d+), (\d+)\)',kotlin)}
assert definitions=={a['key']:(a['steps'],a['points']) for a in rows}, 'Domain/catalog drift'
metadata=list(csv.reader((ROOT/'upload'/'AchievementsMetadata.csv').open()))
assert metadata==[[a['name'],a['description'],'True' if a['steps']>1 else 'False',
                   str(a['steps']) if a['steps']>1 else '', 'Revealed',str(a['points']),str(i)] for i,a in enumerate(rows,1)]
hashes=set()
for key in [a['key'] for a in rows]+[catalog['leaderboard']['key']]:
    data=(ROOT/'upload'/f'{key}.png').read_bytes()
    assert data[:8]==b'\x89PNG\r\n\x1a\n' and len(data)<1_000_000
    width,height,depth,color=struct.unpack('>IIBB',data[16:26])
    assert (width,height,depth,color)==(512,512,8,6), (key,width,height,depth,color)
    digest=hashlib.sha256(data).hexdigest();assert digest not in hashes;hashes.add(digest)
    assert '<text' not in (ROOT/'source'/f'{key}.svg').read_text()
with zipfile.ZipFile(ROOT/'achievements-v1.zip') as archive:
    expected={'AchievementsMetadata.csv','AchievementsIconsMappings.csv'}|{a['key']+'.png' for a in rows}
    assert set(archive.namelist())==expected
    for name in expected:assert archive.read(name)==(ROOT/'upload'/name).read_bytes()

codes=[''.join(p) for p in itertools.permutations('0123456789',3)]
def feedback(secret,guess,detail):
    if detail:return tuple(2 if a==b else 1 if b in secret else 0 for a,b in zip(secret,guess))
    exact=sum(a==b for a,b in zip(secret,guess))
    return exact,len(set(secret)&set(guess))-exact
maxima=[]
for mode in ['Easy','Medium']:
    counts=[]
    for secret in codes:
        candidates=codes[:];count=0
        while True:
            guess=candidates[0];count+=1
            if guess==secret:break
            detail=mode=='Easy' or count==1
            expected=feedback(secret,guess,detail)
            candidates=[c for c in candidates if feedback(c,guess,detail)==expected]
        counts.append(count)
    maxima.append(max(counts))
    print(f'{mode}: {len(counts)} secrets, mean {sum(counts)/len(counts):.3f} guesses, maximum {max(counts)}')
seconds=(maxima[0]*2+maxima[1])*30+300
assert seconds<3600
print(f'Assumed pacing: {seconds//60} minutes for two Easy wins, one Medium win and Tutorial; not measured human play time.')
print('Validated ten unique transparent RGBA icons plus leaderboard icon, source, CSV, ZIP and domain/catalog consistency.')
