"""Bake CityBuild/Yunbei (MIT) signs and road-mounted equipment; no CB runtime.
Usage: python tools/import_cb_signs.py PATH_TO_CB_SOURCE
"""
from pathlib import Path
from PIL import Image
import json,math,re,struct,sys,shutil,hashlib
src=Path(sys.argv[1]); assets=src/'src/main/resources/assets'; root=assets/'citybuild'
out=Path(__file__).resolve().parents[1]/'src/main/resources/assets/splineroads'; refs=Path(__file__).resolve().parents[1]/'tools/cb_sign_sources';refs.mkdir(parents=True,exist_ok=True)
java=src/'src/main/java/com/sora/citybuild/urban'
registry=(java/'block/SignBlocks.java').read_text()
names=re.findall(r'BLOCKS.register\("([^\"]+)"',registry)
# Image-dependent notice boards are not road guidance signs.
names=[n for n in names if not n.startswith('zones_board')]
names+=['road_detection_camera','road_lighting_lamp','road_radar_speed_detector','road_pole_text_display','road_solar_panel']
lang=json.loads((root/'lang/zh_cn.json').read_text());models={};textures={};provenance=[]
def load_model(id):
 ns,path=id.split(':');p=assets/ns/'models'/f'{path}.json';m=json.loads(p.read_text());provenance.append(p)
 if 'parent' in m and m['parent'].startswith('citybuild:'):
  parent=load_model(m['parent']);m={**parent,**m,'textures':{**parent.get('textures',{}),**m.get('textures',{})}}
 return m
for n in names:
 state=json.loads((root/'blockstates'/f'{n}.json').read_text());variants=state.get('variants',{});key=next((k for k in variants if 'facing=north' in k and ('type=normal' in k or 'type=' not in k) and 'lowered=true' not in k and 'lit=false' not in k),next(iter(variants)))
 v=variants[key];v=v[0] if isinstance(v,list) else v;m=load_model(v['model']);models[n]=m
 for value in m['textures'].values():
  if ':' in value:
   ns,t=value.split(':');p=assets/ns/'textures'/f'{t}.png'
   if p.exists():textures[value]=p
# Native originals retained as reproducible inputs; runtime atlas limits large reference textures to 128px.
atlas=Image.new('RGBA',(4096,2048));placements={};x=y=row=0
for id,p in sorted(textures.items()):
 im=Image.open(p).convert('RGBA');scale=min(1,128/max(im.size));im=im.resize((max(1,round(im.width*scale)),max(1,round(im.height*scale))),Image.Resampling.LANCZOS);w,h=im.size
 if x+w+4>atlas.width:x=0;y+=row;row=0
 assert y+h+4<=atlas.height,(id,y)
 atlas.paste(im,(x+2,y+2));atlas.paste(im.crop((0,0,1,h)).resize((2,h)),(x,y+2));atlas.paste(im.crop((w-1,0,w,h)).resize((2,h)),(x+w+2,y+2));atlas.paste(im.crop((0,0,w,1)).resize((w,2)),(x+2,y));atlas.paste(im.crop((0,h-1,w,h)).resize((w,2)),(x+2,y+h+2));placements[id]=(x+2,y+2,w,h);x+=w+4;row=max(row,h+4);provenance.append(p)
(out/'textures/road').mkdir(parents=True,exist_ok=True);atlas.save(out/'textures/road/cb_sign_atlas.png')
entityreg=(java/'entity/ModBlockEntities.java').read_text();bindings={}
for line in entityreg.splitlines():
 c=re.search(r'Builder.of\((\w+)::new',line)
 if c:
  for n in re.findall(r'SignBlocks\.(\w+)\.get',line):bindings[n.lower()]=c.group(1)
# Read CB's named fields and native text locations, including shared block variants.
def layout(n,lo,hi):
 cls=bindings.get(n);files=list((java/'render').glob((cls or 'None')+'Renderer.java'))
 if not files:return []
 source=files[0].read_text();provenance.extend(files);body=source[source.index('public void render('):];body=body[:body.index('\n    private ') if '\n    private ' in body else len(body)]
 # Resolve complete if/else-if chains, including OR conditions, before extracting calls.
 def end_balanced(text,start,left,right):
  depth=0
  for i in range(start,len(text)):
   if text[i]==left:depth+=1
   elif text[i]==right:
    depth-=1
    if depth==0:return i+1
  raise ValueError('unbalanced CB renderer')
 pos=0
 while True:
  match=re.search(r'\bif\s*\(',body[pos:])
  if not match:break
  begin=pos+match.start();op=body.index('(',begin);close=end_balanced(body,op,'(',')');condition=body[op+1:close-1]
  if 'currentBlock' not in condition:pos=close;continue
  cursor=begin;chosen='';matched=False
  while True:
   op=body.index('(',cursor);close=end_balanced(body,op,'(',')');condition=body[op+1:close-1];brace=body.index('{',close);end=end_balanced(body,brace,'{','}')
   names_in=re.findall(r'currentBlock == SignBlocks\.(\w+)\.get\(\)',condition)
   if not matched and n in [v.lower() for v in names_in]:chosen=body[brace+1:end-1];matched=True
   tail=re.match(r'\s*else\s*',body[end:])
   if not tail:break
   cursor=end+tail.end()
   if body.startswith('if',cursor):continue
   brace=body.index('{',cursor);end=end_balanced(body,brace,'{','}')
   if not matched:chosen=body[brace+1:end-1]
   break
  body=body[:begin]+chosen+body[end:];pos=begin+len(chosen)
 result={}
 for call in re.finditer(r'this\.(render\w*Text)\(([^;]+)\);',body):
  method,args=call.groups();args=[a.strip() for a in args.split(',')]
  if len(args)<8:continue
  field=args[4]
  initial=''
  if field.startswith(chr(34)) and field.endswith(chr(34)):
   initial=field[1:-1];field='unit'+str(len(result))
  elif not re.fullmatch(r'[a-zA-Z]+\d*',field):continue
  try:xx=float(args[6].rstrip('f'));yy=float(args[7].rstrip('f'))
  except ValueError:continue
  methodbody=source[source.find('private void '+method+'('):];methodbody=methodbody[:methodbody.find('\n    private ',10)] if '\n    private ' in methodbody[10:] else methodbody
  scales=re.findall(r'([\d.]+)f',methodbody.split('scaleValue =',1)[-1].split(';',1)[0]);scale=float(scales[0] if len(scales)==1 or len(args)>8 and args[8]=='true' else scales[-1]) if scales else .035
  align='right' if 'renderRight' in method or re.search(r'drawInBatch[^\n]*, \(float\)\(-textWidth\),',methodbody) else 'left' if 'renderLeft' in method or 'float leftX =' in methodbody else 'center'
  color_match=re.search(r'drawInBatch[^\n]*?,\s*(0x[0-9A-Fa-f]+|[0-9]{4,})\s*,\s*false',methodbody)
  color=int(args[8],16) if len(args)>8 and args[8].startswith('0x') else int(color_match[1],0) if color_match else 0xFFFFFF
  label=('里程 ' if field.startswith('length') else '路线编号 ' if field.startswith('expressway') else '文字 ')+re.sub(r'\D','',field)
  result[field]=dict(key=field,initial=initial,label=('单位' if initial else label),x=(8-xx-(lo[0]+hi[0])/2)/16,y=(8+yy-lo[1])/16,scale=scale,align=align,color=color)
 return list(result.values())
# Authored multi-element plates contain coplanar overlaps. Partition those faces
# without moving vertices or inventing replacement geometry: shader depth precision
# must not decide which overlapping element supplies the sign's colour.
def disjoint_faces(faces):
 def cross(a,b):return [a[1]*b[2]-a[2]*b[1],a[2]*b[0]-a[0]*b[2],a[0]*b[1]-a[1]*b[0]]
 def sub(a,b):return [a[i]-b[i] for i in range(3)]
 def signed(poly,ax):return sum(poly[i][ax[0]]*poly[(i+1)%len(poly)][ax[1]]-poly[(i+1)%len(poly)][ax[0]]*poly[i][ax[1]] for i in range(len(poly)))/2
 def clip(poly,a,b,ax,inside):
  if not poly:return []
  def distance(p):return (b[ax[0]]-a[ax[0]])*(p[ax[1]]-a[ax[1]])-(b[ax[1]]-a[ax[1]])*(p[ax[0]]-a[ax[0]])
  out=[];p=poly[-1];dp=distance(p)
  for q in poly:
   dq=distance(q);pi=dp>=-1e-9 if inside else dp<=1e-9;qi=dq>=-1e-9 if inside else dq<=1e-9
   if pi!=qi:
    t=dp/(dp-dq);out.append([p[i]*(1-t)+q[i]*t for i in range(5)])
   if qi:out.append(q)
   p=q;dp=dq
  return out
 def difference(poly,cut,ax):
  if any(max(v[a] for v in poly)<=min(v[a] for v in cut)+1e-9 or min(v[a] for v in poly)>=max(v[a] for v in cut)-1e-9 for a in ax):return [poly]
  if signed(cut,ax)<0:cut=list(reversed(cut))
  remaining=poly;pieces=[]
  for i,a in enumerate(cut):
   b=cut[(i+1)%len(cut)];outside=clip(remaining,a,b,ax,False)
   if len(outside)>=3 and abs(signed(outside,ax))>1e-8:pieces.append(outside)
   remaining=clip(remaining,a,b,ax,True)
   if len(remaining)<3:break
  return pieces
 groups={};alpha=atlas.getchannel('A');opaque_cache={}
 for vs,uv in faces:
  n=cross(sub(vs[1],vs[0]),sub(vs[2],vs[0]));length=math.sqrt(sum(x*x for x in n))
  if length<1e-9:continue
  n=[v/length for v in n];d=sum(n[i]*vs[0][i] for i in range(3));key=tuple(round(v,6) for v in n+[d]);axis=max(range(3),key=lambda i:abs(n[i]));ax=tuple(i for i in range(3) if i!=axis);poly=[list(v)+list(t) for v,t in zip(vs,uv)]
  previous=groups.get(key,[])
  rect=(max(0,math.floor(min(t[0] for t in uv)*atlas.width+1e-7)),max(0,math.floor(min(t[1] for t in uv)*atlas.height+1e-7)),min(atlas.width,math.ceil(max(t[0] for t in uv)*atlas.width-1e-7)),min(atlas.height,math.ceil(max(t[1] for t in uv)*atlas.height-1e-7)))
  if rect not in opaque_cache:opaque_cache[rect]=rect[2]>rect[0] and rect[3]>rect[1] and alpha.crop(rect).getextrema()==(255,255)
  if opaque_cache[rect]:groups[key]=[piece for old in previous for piece in difference(old,poly,ax)]+[poly]
  else:
   # Transparent authored overlays must keep the underlying face visible. Give
   # this overlay a tiny depth bias, instead of cutting away its transparent holes.
   shift=.01 if previous else 0
   poly=[[v[i]+n[i]*shift if i<3 else v[i] for i in range(5)] for v in poly]
   groups[key]=previous+[poly]
 result=[]
 for group in groups.values():
  for poly in group:
   # All pieces are convex; remove collinear duplicate cut points before triangulation.
   cleaned=[]
   for v in poly:
    if not cleaned or sum((v[i]-cleaned[-1][i])**2 for i in range(3))>1e-16:cleaned.append(v)
   if len(cleaned)>2 and sum((cleaned[0][i]-cleaned[-1][i])**2 for i in range(3))<1e-16:cleaned.pop()
   changed=True
   while changed and len(cleaned)>3:
    changed=False
    for i,b in enumerate(cleaned):
     a=cleaned[i-1];c=cleaned[(i+1)%len(cleaned)];normal=cross(sub(b,a),sub(c,b))
     if sum(x*x for x in normal)<1e-16:
      cleaned.pop(i);changed=True;break
   if len(cleaned)<3:continue
   quads=[cleaned] if len(cleaned)==4 else [[cleaned[0],cleaned[i],cleaned[i+1],cleaned[i+1]] for i in range(1,len(cleaned)-1)]
   for q in quads:
    normal=cross(sub(q[1],q[0]),sub(q[2],q[0]))
    if sum(x*x for x in normal)<1e-16:continue
    result.append(([v[:3] for v in q],[v[3:] for v in q]))
 return result
catalog=[]
for n,m in models.items():
 faces=[]
 for e in m.get('elements',[]):
  x0,y0,z0=e['from'];x1,y1,z1=e['to'];quads={'north':[(x0,y1,z0),(x1,y1,z0),(x1,y0,z0),(x0,y0,z0)],'south':[(x1,y1,z1),(x0,y1,z1),(x0,y0,z1),(x1,y0,z1)],'west':[(x0,y1,z1),(x0,y1,z0),(x0,y0,z0),(x0,y0,z1)],'east':[(x1,y1,z0),(x1,y1,z1),(x1,y0,z1),(x1,y0,z0)],'up':[(x0,y1,z1),(x1,y1,z1),(x1,y1,z0),(x0,y1,z0)],'down':[(x0,y0,z0),(x1,y0,z0),(x1,y0,z1),(x0,y0,z1)]}
  for direction,f in e['faces'].items():
   texture=f['texture']
   while texture.startswith('#'):texture=m['textures'][texture[1:]]
   if texture not in placements:continue
   vs=[]
   for v in quads[direction]:
    r=e.get('rotation',{});o=r.get('origin',[0,0,0]);a,b={'x':(1,2),'y':(2,0),'z':(0,1)}[r.get('axis','y')];angle=math.radians(r.get('angle',0));q=[v[i]-o[i] for i in range(3)];q[a],q[b]=q[a]*math.cos(angle)-q[b]*math.sin(angle),q[a]*math.sin(angle)+q[b]*math.cos(angle);factor=1/math.cos(angle) if r.get('rescale',False) else 1;q[a]*=factor;q[b]*=factor;vs.append([q[i]+o[i] for i in range(3)])
   u0,v0,u1,v1=f.get('uv',[0,0,16,16]);uv=[(u1/16,v0/16),(u0/16,v0/16),(u0/16,v1/16),(u1/16,v1/16)] if direction in ['north','south','east','west'] else [(u0/16,v0/16),(u1/16,v0/16),(u1/16,v1/16),(u0/16,v1/16)];k=f.get('rotation',0)//90;uv=uv[k:]+uv[:k]
   px,py,w,h=placements[texture]
   uv=[((px+max(0,min(1,u))*w)/4096,(py+max(0,min(1,v))*h)/2048) for u,v in uv]
   faces.append((vs,uv))
 faces=disjoint_faces(faces)
 if n=='road_pole_text_display':faces=[([[v[2],v[1],-v[0]] for v in vs],uv) for vs,uv in faces]
 points=[v for vs,uv in faces for v in vs];lo=[min(v[i] for v in points) for i in range(3)];hi=[max(v[i] for v in points) for i in range(3)];pivot=[(lo[0]+hi[0])/2,lo[1],(lo[2]+hi[2])/2]
 # CB normal boards face north. Rotate the whole authored model 180 degrees
 # so all SR attachments share +Z as their readable front.
 fields=layout(n,lo,hi)
 for field in fields:field['x']=-field['x']
 if n=='road_pole_text_display':fields=[dict(key='text',initial='',label='显示文字',x=0,y=(hi[1]-lo[1])/32,scale=.035,align='center',color=0xFFBA35)]
 modeldir=out/'models/road/cb_signs';modeldir.mkdir(parents=True,exist_ok=True)
 with (modeldir/f'{n}.mesh').open('wb') as f:
  f.write(struct.pack('>i',len(faces)))
  for vs,uv in faces:
   # Preserve authored outward winding and UV order.
   for v,t in list(zip(vs,uv)):f.write(struct.pack('>5f',*[(v[i]-pivot[i])/16*(-1 if i in [0,2] and n!='road_pole_text_display' else 1) for i in range(3)],*t))
 catalog.append(dict(id=n,label=lang.get('block.citybuild.'+n,n),width=(hi[0]-lo[0])/16,height=(hi[1]-lo[1])/16,depth=max(.04,(hi[2]-lo[2])/16),fields=fields))
(out/'models/road/cb_signs.json').write_text(json.dumps(catalog,ensure_ascii=False,indent=2))
for p in sorted(set(provenance)):
 dest=refs/p.relative_to(src);dest.parent.mkdir(parents=True,exist_ok=True);shutil.copy2(p,dest)
(refs/'provenance.json').write_text(json.dumps([dict(path=str(p.relative_to(src)),sha256=hashlib.sha256(p.read_bytes()).hexdigest()) for p in sorted(set(provenance))],indent=2))
print('Imported',len(catalog),'models;',sum(bool(x['fields']) for x in catalog),'editable signs;',sum(len(x['fields']) for x in catalog),'text fields; atlas',y+row,'of 2048 rows')

for kind in ['national','provicial']:
 for i in [1,2]:
  p=root/'textures/block/sign'/f'sign_expressway_{kind}_logo_{i}.png';shutil.copy2(p,out/'textures/road'/f'cb_logo_{kind}_{i}.png');dest=refs/p.relative_to(src);dest.parent.mkdir(parents=True,exist_ok=True);shutil.copy2(p,dest)
