#!/usr/bin/env python3
"""Check the actual Console archive, property contract and unique upload images."""
import csv
import hashlib
import json
from pathlib import Path
import re
import struct
import zipfile

root=Path(__file__).resolve().parent
catalog=json.loads((root/'catalog.json').read_text())
events=list(csv.DictReader((root/'upload'/'PlayerGameEvent.csv').open()))
properties={row['Property Name']:row['Property Type'] for row in events}
assert properties==dict(playerMode='STRING',difficulty='STRING',outcome='STRING',guessCount='INT64',hintUsed='BOOL',hardSoloWin='BOOL',unaidedMediumSoloWin='BOOL')
assert len(events)==len(properties) and {e['Event Name'] for e in events}=={catalog['event']}
adapter=(root.parents[1]/'numbcrunch/src/main/java/io/keval/apps/numbcrunch/playgames/AndroidGameStatsGateway.kt').read_text()
assert set(re.findall(r'\.addProperty\("(\w+)"',adapter))==properties.keys()
stats=list(csv.DictReader((root/'upload'/'RepetitiveStatsConfig.csv').open()))
assert len(stats)==5 and len({s['Stat Id'] for s in stats})==5
assert [s['Stat Id'] for s in stats if s['Is Competitive']=='true']==['hard_solo_puzzles_solved_v1']
assert [s['Aggregation Type'] for s in stats]==['COUNT']*4+['SUM']
with zipfile.ZipFile(root/'game-stats-v1.zip') as archive:
    assert set(archive.namelist())=={'RepetitiveStatsConfig.csv'}|{s['Icon File Name'] for s in stats}
    assert sum(f.file_size for f in archive.infolist())<10*1024*1024
    hashes=[]
    for s in stats:
        assert s['Event Name']==catalog['event'] and s['Event Property Name'] in properties
        assert s['Filter Property'] in properties or s['Filter Property']==''
        assert len(s['Stat Display Name'])<=50 and len(s['Stat Description'])<=500
        data=archive.read(s['Icon File Name']);assert data==(root/'upload'/s['Icon File Name']).read_bytes()
        assert data[:8]==b'\x89PNG\r\n\x1a\n' and len(data)<1024*1024
        assert struct.unpack('>IIBB',data[16:26])==(512,512,8,6)
        hashes.append(hashlib.sha256(data).hexdigest())
    assert len(set(hashes))==5
print('Validated five stats, seven SDK properties, unique512RGBAicons, and repetitive-onlyZIP.')
