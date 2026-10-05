"""Bake the supplied CityBuild horizontal signal model. Usage: script CB_ASSET_ROOT.
Only converts its authored JSON elements/UVs; no Minecraft or third party runtime code.
"""
import json, math, struct, sys, shutil
from pathlib import Path
from PIL import Image
root=Path(sys.argv[1]); out=Path(__file__).resolve().parents[1]/'src/main/resources/assets/splineroads'
source=root/'models/block/traffic_lights_black_horizontal.json'
model=json.loads(source.read_text())
(out/'models/road').mkdir(parents=True,exist_ok=True)
(out/'textures/road').mkdir(parents=True,exist_ok=True)
shutil.copy2(source,out/'models/road/cb_signal_source.json')
shutil.copy2(root/'textures/block/traffic_lights_black.png',out/'textures/road/cb_signal_housing.png')
im=Image.open(root/'textures/block/lights/auto_signal_ns.png')
for name,index in [('green',0),('amber',1),('red',2)]:
 im.crop((0,index*256,256,(index+1)*256)).save(out/f'textures/road/cb_signal_{name}.png')
faces=[]
for e in model['elements']:
 x0,y0,z0=e['from']; x1,y1,z1=e['to']
 quads={'north':[(x0,y1,z0),(x1,y1,z0),(x1,y0,z0),(x0,y0,z0)],
        'south':[(x1,y1,z1),(x0,y1,z1),(x0,y0,z1),(x1,y0,z1)],
        'west':[(x0,y1,z1),(x0,y1,z0),(x0,y0,z0),(x0,y0,z1)],
        'east':[(x1,y1,z0),(x1,y1,z1),(x1,y0,z1),(x1,y0,z0)],
        'up':[(x0,y1,z1),(x1,y1,z1),(x1,y1,z0),(x0,y1,z0)],
        'down':[(x0,y0,z0),(x1,y0,z0),(x1,y0,z1),(x0,y0,z1)]}
 for direction,f in e['faces'].items():
  vs=[]
  for v in quads[direction]:
   r=e.get('rotation',{}); axis=r.get('axis','y'); angle=math.radians(r.get('angle',0)); origin=r.get('origin',[0,0,0])
   q=[v[i]-origin[i] for i in range(3)]
   a,b={'x':(1,2),'y':(2,0),'z':(0,1)}[axis]
   q[a],q[b]=q[a]*math.cos(angle)-q[b]*math.sin(angle),q[a]*math.sin(angle)+q[b]*math.cos(angle)
   vs.append(tuple(q[i]+origin[i] for i in range(3)))
  u0,v0,u1,v1=f['uv']; uv=[(u0/16,v0/16),(u1/16,v0/16),(u1/16,v1/16),(u0/16,v1/16)]
  k=f.get('rotation',0)//90; uv=uv[k:]+uv[:k]
  faces.append((f['texture']=='#auto_signal',vs,uv))
points=[v for _,vs,_ in faces for v in vs]; lo=[min(v[i] for v in points) for i in range(3)]; hi=[max(v[i] for v in points) for i in range(3)]
pivot=[(lo[0]+hi[0])/2,lo[1],(lo[2]+hi[2])/2]
with (out/'models/road/cb_signal.mesh').open('wb') as f:
 f.write(struct.pack('>i',len(faces)))
 for lens,vs,uv in faces:
  f.write(struct.pack('>?',lens))
  for v,t in zip(vs,uv): f.write(struct.pack('>5f',*[(v[i]-pivot[i])/16 for i in range(3)],*t))
print('Baked',len(faces),'CB faces; size',[(hi[i]-lo[i])/16 for i in range(3)])
