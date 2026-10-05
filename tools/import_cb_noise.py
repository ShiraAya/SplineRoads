"""Bake CB's authored green type-1 sound barrier; no CB runtime dependency."""
import json,math,struct,sys,shutil,hashlib
from pathlib import Path
from PIL import Image
root=Path(sys.argv[1]);project=Path(__file__).resolve().parents[1]
src=root/'models/block/sound_barrier_1_green_normal.json';tex=root/'textures/block/sound_barrier_1_green.png'
out=project/'src/main/resources/assets/splineroads/models/road';out.mkdir(parents=True,exist_ok=True)
model=json.loads(src.read_text());faces=[];alpha=Image.open(tex).convert('RGBA').getchannel('A')
for element in model['elements']:
 x0,y0,z0=element['from'];x1,y1,z1=element['to']
 quads={'north':[(x0,y1,z0),(x1,y1,z0),(x1,y0,z0),(x0,y0,z0)],'south':[(x1,y1,z1),(x0,y1,z1),(x0,y0,z1),(x1,y0,z1)],'west':[(x0,y1,z1),(x0,y1,z0),(x0,y0,z0),(x0,y0,z1)],'east':[(x1,y1,z0),(x1,y1,z1),(x1,y0,z1),(x1,y0,z0)],'up':[(x0,y1,z1),(x1,y1,z1),(x1,y1,z0),(x0,y1,z0)],'down':[(x0,y0,z0),(x1,y0,z0),(x1,y0,z1),(x0,y0,z1)]}
 for direction,f in element['faces'].items():
  u0,v0,u1,v1=f['uv'];uv=[(u0/16,v0/16),(u1/16,v0/16),(u1/16,v1/16),(u0/16,v1/16)]
  turns=f.get('rotation',0)//90;uv=uv[turns:]+uv[:turns];vertices=[]
  for point,coord in zip(quads[direction],uv):
   r=element.get('rotation',{});origin=r.get('origin',[0,0,0]);angle=math.radians(r.get('angle',0));q=[point[i]-origin[i] for i in range(3)];a,b={'x':(1,2),'y':(2,0),'z':(0,1)}[r.get('axis','y')]
   q[a],q[b]=q[a]*math.cos(angle)-q[b]*math.sin(angle),q[a]*math.sin(angle)+q[b]*math.cos(angle)
   if r.get('rescale',False) and angle:
    q[a]/=math.cos(angle);q[b]/=math.cos(angle)
   p=[q[i]+origin[i] for i in range(3)]
   vertices.append(((p[0]+16)/32,(p[1]+16)/16,(p[2]-8)/16,*coord))
  u=sum(v[3] for v in vertices)/4;v=sum(v[4] for v in vertices)/4
  glass=0<alpha.getpixel((min(alpha.width-1,int(u*alpha.width)),min(alpha.height-1,int(v*alpha.height))))<128
  faces.append((glass,vertices))
with (out/'cb_noise.mesh').open('wb') as f:
 f.write(struct.pack('>i',len(faces)))
 for glass,face in faces:
  f.write(struct.pack('>?',glass))
  for v in face:f.write(struct.pack('>5f',*v))
shutil.copy2(src,out/'cb_noise_source.json');shutil.copy2(tex,project/'src/main/resources/assets/splineroads/textures/road/cb_noise.png')
(out/'cb_noise_provenance.json').write_text(json.dumps({'source_model':src.name,'model_sha256':hashlib.sha256(src.read_bytes()).hexdigest(),'source_texture':tex.name,'texture_sha256':hashlib.sha256(tex.read_bytes()).hexdigest(),'faces':len(faces)},indent=2)+'\n')
print('CB noise faces',len(faces),'bounds',[(min(v[i] for _,f in faces for v in f),max(v[i] for _,f in faces for v in f)) for i in range(3)])
