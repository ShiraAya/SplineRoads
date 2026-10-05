"""Bake CB gray vertical / pavement models with authored rotations and UVs.
Usage: python tools/import_cb_junction_signals.py CB_SRC_MAIN_RESOURCES_ASSETS
Assets from CityBuild's Yunbei models (MIT); no runtime dependency.
"""
from pathlib import Path
import json, math, struct, shutil, sys
from PIL import Image
root=Path(sys.argv[1])/'citybuild';out=Path(__file__).resolve().parents[1]/'src/main/resources/assets/splineroads'
atlas=Image.new('RGBA',(1024,128))
for i,name in enumerate(['traffic_lights_gray','traffic_lights_pavement_gray']):
 im=Image.open(root/'textures/block'/f'{name}.png').convert('RGBA');atlas.paste(im.resize((128,128),Image.Resampling.NEAREST),(i*128,0))
for name,tiles in [('auto_signal_ns',[(3,2),(4,1),(5,0)]),('auto_pavement_signal_ns',[(6,1),(7,0)])]:
 im=Image.open(root/'textures/block/lights'/f'{name}.png').convert('RGBA');w=im.width
 for tile,frame in tiles:atlas.paste(im.crop((0,frame*w,w,(frame+1)*w)).resize((128,128),Image.Resampling.NEAREST),(tile*128,0))
atlas.save(out/'textures/road/cb_signal_atlas.png')
for kind,filename,key in [('vehicle','traffic_lights_gray_vertical',0),('pedestrian','traffic_lights_pavement_gray',1)]:
 source=root/'models/block'/f'{filename}.json';model=json.loads(source.read_text());shutil.copy2(source,out/'models/road'/f'cb_{kind}_source.json');faces=[]
 for e in model['elements']:
  x0,y0,z0=e['from'];x1,y1,z1=e['to']
  quads={'north':[(x0,y1,z0),(x1,y1,z0),(x1,y0,z0),(x0,y0,z0)],'south':[(x1,y1,z1),(x0,y1,z1),(x0,y0,z1),(x1,y0,z1)],'west':[(x0,y1,z1),(x0,y1,z0),(x0,y0,z0),(x0,y0,z1)],'east':[(x1,y1,z0),(x1,y1,z1),(x1,y0,z1),(x1,y0,z0)],'up':[(x0,y1,z1),(x1,y1,z1),(x1,y1,z0),(x0,y1,z0)],'down':[(x0,y0,z0),(x1,y0,z0),(x1,y0,z1),(x0,y0,z1)]}
  for direction,f in e['faces'].items():
   vs=[]
   for v in quads[direction]:
    r=e.get('rotation',{});o=r.get('origin',[0,0,0]);a,b={'x':(1,2),'y':(2,0),'z':(0,1)}[r.get('axis','y')];angle=math.radians(r.get('angle',0));q=[v[i]-o[i] for i in range(3)]
    q[a],q[b]=q[a]*math.cos(angle)-q[b]*math.sin(angle),q[a]*math.sin(angle)+q[b]*math.cos(angle);vs.append(tuple(q[i]+o[i] for i in range(3)))
   u0,v0,u1,v1=f['uv'];uv=[(u0/16,v0/16),(u1/16,v0/16),(u1/16,v1/16),(u0/16,v1/16)];k=f.get('rotation',0)//90;uv=uv[k:]+uv[:k]
   faces.append(((3 if kind=='vehicle' else 6) if f['texture']=='#auto_signal' else key,vs,uv))
 points=[v for _,vs,_ in faces for v in vs];lo=[min(v[i] for v in points) for i in range(3)];hi=[max(v[i] for v in points) for i in range(3)];pivot=[(lo[0]+hi[0])/2,lo[1],hi[2]]
 with (out/'models/road'/f'cb_{kind}.mesh').open('wb') as f:
  f.write(struct.pack('>i',len(faces)))
  for key,vs,uv in faces:
   f.write(struct.pack('>i',key))
   for v,t in zip(vs,uv):f.write(struct.pack('>5f',*[(v[i]-pivot[i])/16 for i in range(3)],*t))
 print(kind,len(faces),'faces; dimensions',[round((hi[i]-lo[i])/16,6) for i in range(3)])
