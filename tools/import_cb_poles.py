"""Bake original CB road pole modules. Usage: import_cb_poles.py CB_ASSET_ROOT."""
import json, math, struct, sys, shutil
from pathlib import Path
root=Path(sys.argv[1]);out=Path(__file__).resolve().parents[1]/'src/main/resources/assets/splineroads/models/road'
for target,source in [('cb_post','road_pole_longitudinal'),('cb_arm','road_pole_horizontal'),('cb_base','road_pole_foundations')]:
 src=root/'models/block'/f'{source}.json'; model=json.loads(src.read_text())
 shutil.copy2(src,out/f'{target}_source.json');faces=[]
 for e in model['elements']:
  x0,y0,z0=e['from'];x1,y1,z1=e['to']
  quads={'north':[(x0,y1,z0),(x1,y1,z0),(x1,y0,z0),(x0,y0,z0)],'south':[(x1,y1,z1),(x0,y1,z1),(x0,y0,z1),(x1,y0,z1)],'west':[(x0,y1,z1),(x0,y1,z0),(x0,y0,z0),(x0,y0,z1)],'east':[(x1,y1,z0),(x1,y1,z1),(x1,y0,z1),(x1,y0,z0)],'up':[(x0,y1,z1),(x1,y1,z1),(x1,y1,z0),(x0,y1,z0)],'down':[(x0,y0,z0),(x1,y0,z0),(x1,y0,z1),(x0,y0,z1)]}
  for direction,f in e['faces'].items():
   vs=[]
   for v in quads[direction]:
    r=e.get('rotation',{});origin=r.get('origin',[0,0,0]);angle=math.radians(r.get('angle',0))
    q=[v[i]-origin[i] for i in range(3)];a,b={'x':(1,2),'y':(2,0),'z':(0,1)}[r.get('axis','y')]
    q[a],q[b]=q[a]*math.cos(angle)-q[b]*math.sin(angle),q[a]*math.sin(angle)+q[b]*math.cos(angle)
    v=[(q[i]+origin[i])/16 for i in range(3)]
    if target=='cb_arm':v=[v[0]-.5,v[1]-.375,v[2]]
    else:v=[v[0]-.5,v[1],v[2]-.5]
    vs.append(v)
   faces.append(vs)
 with (out/f'{target}.mesh').open('wb') as f:
  f.write(struct.pack('>i',len(faces)))
  for vs in faces:
   for v in vs:f.write(struct.pack('>3f',*v))
 print(target,len(faces))
