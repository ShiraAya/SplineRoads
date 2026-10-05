"""Deterministic point-tool sprites and native pixel models matching SR's item system."""
from pathlib import Path
import json
from PIL import Image,ImageDraw
root=Path(__file__).resolve().parents[1]/'src/main/resources/assets/splineroads'
palette={'k':(15,19,21,255),'d':(71,79,83,255),'w':(230,238,234,255),'r':(225,47,51,255),'b':(44,163,241,255),'y':(236,188,51,255)}
display=json.loads((root/'models/item/lane_line_editor.json').read_text())['display']
for name,accent in [('endpoint_creator','r'),('lane_point_tool','b')]:
 im=Image.new('RGBA',(32,32));d=ImageDraw.Draw(im)
 # Tapered engineering-tool handle, road strip and a distinct point reticle.
 d.polygon([(4,27),(11,27),(27,7),(27,3),(22,3)],fill=palette['k'])
 d.polygon([(7,26),(10,26),(25,6),(25,4),(23,4)],fill=palette['w'])
 d.line([(9,24),(22,7)],fill=palette['d'],width=3)
 for a,b in [((11,22),(13,19)),((16,16),(18,13))]:d.line([a,b],fill=palette['y'],width=1)
 d.rectangle((3,25,11,29),fill=palette['k']);d.rectangle((5,25,9,28),fill=palette[accent])
 if accent=='r':
  d.rectangle((2,5,15,18),fill=palette['k']);d.rectangle((4,7,13,16),fill=palette['w']);d.rectangle((6,9,11,14),fill=palette['r'])
  d.line((8,2,8,6),fill=palette['r'],width=2);d.line((0,11,4,11),fill=palette['r'],width=2)
 else:
  d.ellipse((1,4,16,19),fill=palette['k']);d.ellipse((3,6,14,17),fill=palette['w']);d.ellipse((5,8,12,15),fill=palette['b'])
  d.polygon([(22,16),(28,16),(28,12),(31,18),(28,24),(28,20),(22,20)],fill=palette['k']);d.polygon([(23,17),(29,17),(28,15),(30,18),(28,21),(28,19),(23,19)],fill=palette['b'])
 im.save(root/'textures/item'/f'{name}.png')
 elements=[]
 for y in range(32):
  x=0
  while x<32:
   color=im.getpixel((x,y));start=x;x+=1
   if not color[3]:continue
   while x<32 and im.getpixel((x,y))==color:x+=1
   key=next(k for k,v in palette.items() if v==color)
   elements.append({'from':[start/2,(31-y)/2,7.5],'to':[x/2,(32-y)/2,8.5],'shade':False,'faces':{face:{'texture':'#'+key} for face in ['north','south','east','west','up','down']}})
 textures={k:'minecraft:block/'+v+'_concrete' for k,v in {'k':'black','d':'gray','w':'white','r':'red','b':'light_blue','y':'yellow'}.items()};textures['particle']=textures[accent]
 (root/'models/item'/f'{name}.json').write_text(json.dumps({'parent':'minecraft:block/block','gui_light':'front','textures':textures,'display':display,'elements':elements},separators=(',',':'))+'\n')
