package com.sora.splineroads.client;
import com.sora.splineroads.core.*;
import com.sora.splineroads.core.RoadSigns.*;
import com.sora.splineroads.world.RoadSignCodec;
import com.sora.splineroads.net.RoadNetwork;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.*;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import java.util.*;
public final class RoadSignScreen extends Screen {
  private final CompoundTag payload;private Attachment value;private int page=0,textPage=0,x,y,w;private String status="";private boolean pending;private int previewX,previewWidth;
  private final Map<String,EditBox> fields=new LinkedHashMap<>();
  public RoadSignScreen(CompoundTag payload){super(Component.literal("SR 路牌与市政设施"));this.payload=payload.copy();value=RoadSignCodec.read(payload.getCompound("Attachment"));}
  private Button button(String text,int px,int py,int width,Runnable action){var b=addRenderableWidget(Button.builder(Component.literal(text),v->action.run()).bounds(px,py,width,20).build());b.active=!pending;return b;}
  private void field(String key,String val,int px,int py,int width){var e=new EditBox(font,px,py,width,18,Component.literal(key));e.setMaxLength(key.startsWith("text")?80:16);e.setValue(val);e.setEditable(!pending);fields.put(key,e);addRenderableWidget(e);}
  @Override protected void init(){fields.clear();w=Math.min(420,width-16);previewWidth=width>=560?Math.min(210,width-w-24):0;x=(width-w-previewWidth)/2+previewWidth;previewX=x-previewWidth;y=Math.max(2,(height-232)/2);
    int tab=(w-32)/3;
    button("位置",x+8,y+25,tab,()->{if(capture()){page=0;rebuildWidgets();}});button("文字",x+16+tab,y+25,tab,()->{if(capture()){page=1;rebuildWidgets();}});button("预览",x+24+tab*2,y+25,tab,()->{if(capture()){page=2;rebuildWidgets();}});
    button(font.plainSubstrByWidth("样式："+RoadSignCatalog.get(value.model()).label()+" ▸",w-26),x+8,y+50,w-16,()->{if(capture())minecraft.setScreen(new Picker(this));});
    if(page==0){
      button("安装："+value.mount().label,x+8,y+76,w-16,()->{if(capture()){Mount m=value.mount()==Mount.POLE&&payload.getInt("Count")>0?Mount.GANTRY:Mount.POLE;value=new Attachment(value.id(),value.model(),m,value.station(),value.position(),m==Mount.POLE?1.5:0,m==Mount.POLE?3:5.6,value.scale(),value.reverse(),value.text());rebuildWidgets();}});
      int col=(w-24)/2;
      field("position",value.mount()==Mount.GANTRY?Integer.toString(value.station()+1):String.format(Locale.ROOT,"%.2f",value.position()*100),x+96,y+103,col-88);
      field("lateral",Double.toString(value.lateral()),x+col+110,y+103,col-98);
      field("height",Double.toString(value.height()),x+96,y+130,col-88);field("scale",Double.toString(value.scale()),x+col+110,y+130,col-98);
      button("朝向："+(value.reverse()?"翻转":"顺行"),x+8,y+155,w-16,()->{if(capture()){value=new Attachment(value.id(),value.model(),value.mount(),value.station(),value.position(),value.lateral(),value.height(),value.scale(),!value.reverse(),value.text());rebuildWidgets();}});
    }else if(page==1){
      var spec=RoadSignCatalog.get(value.model());int count=spec.fields().size();textPage=Math.min(textPage,Math.max(0,(count-1)/3));
      for(int i=textPage*3;i<Math.min(count,textPage*3+3);i++)field("text"+i,i<value.text().size()?value.text().get(i):spec.fields().get(i).initial(),x+86,y+78+(i%3)*26,w-96);
      if(count>3){button("上一页",x+8,y+155,(w-24)/2,()->{if(capture()){textPage=Math.max(0,textPage-1);rebuildWidgets();}});button("下一页",x+16+(w-24)/2,y+155,(w-24)/2,()->{if(capture()){textPage=Math.min((count-1)/3,textPage+1);rebuildWidgets();}});}
    }
    int bw=(w-32)/3;button("保存",x+8,y+180,bw,()->send(false));button("移除设施",x+16+bw,y+180,bw,()->send(true)).active=!payload.getBoolean("New")&&!pending;button("取消",x+24+bw*2,y+180,bw,this::onClose);
  }
  private boolean capture(){try{
    if(page==0&&fields.containsKey("position")){double pos=Double.parseDouble(fields.get("position").getValue());int station=value.mount()==Mount.GANTRY?(int)pos-1:value.station();if(value.mount()==Mount.GANTRY&&(station<0||station>=payload.getInt("Count")||pos!=Math.rint(pos)))throw new IllegalArgumentException("龙门架序号超出范围");value=new Attachment(value.id(),value.model(),value.mount(),station,value.mount()==Mount.POLE?pos/100:value.position(),number("lateral"),number("height"),number("scale"),value.reverse(),value.text());}
    else if(page==1){var list=new ArrayList<>(value.text());int count=RoadSignCatalog.get(value.model()).fields().size();while(list.size()<count)list.add(RoadSignCatalog.get(value.model()).fields().get(list.size()).initial());for(var e:fields.entrySet())if(e.getKey().startsWith("text"))list.set(Integer.parseInt(e.getKey().substring(4)),e.getValue().getValue());value=new Attachment(value.id(),value.model(),value.mount(),value.station(),value.position(),value.lateral(),value.height(),value.scale(),value.reverse(),list);}
    status="";return true;
  }catch(IllegalArgumentException e){status=e instanceof NumberFormatException?"请输入完整数值":e.getMessage();return false;}}
  private double number(String k){return Double.parseDouble(fields.get(k).getValue());}
  private void send(boolean delete){if(pending||!capture())return;var t=new CompoundTag();t.putString("Action","sign");t.putUUID("Id",payload.getUUID("Id"));t.putInt("Signature",payload.getInt("Signature"));t.putBoolean("New",payload.getBoolean("New"));t.putBoolean("Delete",delete);t.put("Attachment",RoadSignCodec.write(value));pending=true;status="正在保存…";rebuildWidgets();RoadNetwork.CHANNEL.sendToServer(new RoadNetwork.Action(t));}
  public void failed(String s){pending=false;status=s;rebuildWidgets();}
  @Override public boolean isPauseScreen(){return false;}
  @Override public void render(GuiGraphics g,int mx,int my,float dt){renderBackground(g);g.fill(previewX,y,x+w,y+230,0xf012212c);g.drawString(font,"SR 路牌编辑器 · "+(payload.getBoolean("New")?"新增":"编辑 #"+(value.id()+1)),x+8,y+8,0xffffff,false);super.render(g,mx,my,dt);
    if(page==0){int col=(w-24)/2;g.drawString(font,value.mount()==Mount.GANTRY?"龙门架序号":"沿路位置 %",x+8,y+108,0xbbd3dc,false);g.drawString(font,value.mount()==Mount.GANTRY?"横向位置":"离路边 ±",x+col+22,y+108,0xbbd3dc,false);g.drawString(font,"底缘高度",x+8,y+135,0xbbd3dc,false);g.drawString(font,"缩放",x+col+22,y+135,0xbbd3dc,false);}
    else if(page==1){var spec=RoadSignCatalog.get(value.model());if(spec.fields().isEmpty())g.drawWordWrap(font,Component.literal("此样式为固定图案或设备，没有可编辑文字。"),x+10,y+88,w-20,0xbbd3dc);for(int i=textPage*3;i<Math.min(spec.fields().size(),textPage*3+3);i++)g.drawString(font,(i+1)+" "+spec.fields().get(i).label(),x+8,y+83+(i%3)*26,0xbbd3dc,false);}
    if(previewWidth>0){g.fill(previewX+6,y+25,x-6,y+200,0xff23323e);RoadSignPreview.draw(g,RoadSignCatalog.get(value.model()),previewText(),previewX+8,y+28,previewWidth-16,165,true);g.drawWordWrap(font,Component.literal("牌面预览 · 数字对应文字项"),previewX+8,y+206,previewWidth-16,0xbbd3dc);}
    if(page==2){RoadSignPreview.draw(g,RoadSignCatalog.get(value.model()),previewText(),x+10,y+76,w-20,96,true);}
    g.drawWordWrap(font,Component.literal(status.isEmpty()?"右键标牌编辑；Shift＋右键增加。路侧杆正数右侧，负数左侧。":status),x+8,y+206,w-16,status.isEmpty()?0xbbd3dc:0xffb497);
  }
  private List<String> previewText(){var m=RoadSignCatalog.get(value.model());var text=new ArrayList<String>();for(int i=0;i<m.fields().size();i++){var box=fields.get("text"+i);text.add(box!=null?box.getValue():i<value.text().size()?value.text().get(i):m.fields().get(i).initial());}return text;}
  private static final class Picker extends Screen {
    private final RoadSignScreen parent;private String query="";private int page;private List<RoadSignCatalog.Model> matches=List.of();private int x,y,w;
    Picker(RoadSignScreen p){super(Component.literal("路牌样式与市政设施"));parent=p;}
    @Override protected void init(){w=Math.min(620,width-16);x=(width-w)/2;y=Math.max(2,(height-234)/2);matches=RoadSignCatalog.MODELS.stream().filter(m->m.label().contains(query)||m.id().contains(query.toLowerCase(Locale.ROOT))).toList();page=Math.min(page,Math.max(0,(matches.size()-1)/6));
      var search=new EditBox(font,x+8,y+25,w-16,18,Component.literal("搜索样式"));search.setValue(query);search.setResponder(s->{query=s;page=0;rebuildWidgets();});addRenderableWidget(search);setInitialFocus(search);
      for(int i=page*6;i<Math.min(matches.size(),page*6+6);i++){var m=matches.get(i);int cell=(w-24)/2,index=i%6;var b=addRenderableWidget(Button.builder(Component.empty(),v->{parent.value=new Attachment(parent.value.id(),m.id(),parent.value.mount(),parent.value.station(),parent.value.position(),parent.value.lateral(),parent.value.height(),parent.value.scale(),parent.value.reverse(),List.of());parent.textPage=0;minecraft.setScreen(parent);}).bounds(x+8+(index%2)*(cell+8),y+49+(index/2)*46,cell,42).build());b.setTooltip(Tooltip.create(Component.literal(m.label()+"\n"+m.id()+(m.fields().isEmpty()?"":"\n可编辑文字"))));}
      addRenderableWidget(Button.builder(Component.literal("上一页"),b->{page=Math.max(0,page-1);rebuildWidgets();}).bounds(x+8,y+191,90,20).build());addRenderableWidget(Button.builder(Component.literal("返回"),b->minecraft.setScreen(parent)).bounds(x+w/2-45,y+191,90,20).build());addRenderableWidget(Button.builder(Component.literal("下一页"),b->{page=Math.min(Math.max(0,(matches.size()-1)/6),page+1);rebuildWidgets();}).bounds(x+w-98,y+191,90,20).build());
    }
    @Override public void onClose(){minecraft.setScreen(parent);}
    @Override public void render(GuiGraphics g,int mx,int my,float dt){renderBackground(g);g.fill(x,y,x+w,y+230,0xf012212c);g.drawString(font,"路牌样式 · "+matches.size()+" 项 · 第 "+(page+1)+" 页",x+8,y+8,0xffffff,false);super.render(g,mx,my,dt);
      for(int i=page*6;i<Math.min(matches.size(),page*6+6);i++){var m=matches.get(i);int cell=(w-24)/2,index=i%6,px=x+8+(index%2)*(cell+8),py=y+49+(index/2)*46;RoadSignPreview.draw(g,m,List.of(),px+3,py+3,42,36,false);g.drawWordWrap(font,Component.literal(m.label()+(m.fields().isEmpty()?"":" ✎")),px+49,py+6,cell-54,0xffffff);}
      g.drawString(font,"✎ 可编辑文字；支持中文名称、英文 ID 搜索",x+8,y+217,0xbbd3dc,false);}
    @Override public boolean isPauseScreen(){return false;}
  }
}
