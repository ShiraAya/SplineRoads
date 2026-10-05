package com.sora.splineroads.client;
import com.sora.splineroads.core.*;
import com.sora.splineroads.core.RoadGeometry.*;
import com.sora.splineroads.world.RoadRecord;
import com.sora.splineroads.net.RoadNetwork;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.*;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.nbt.*;
import net.minecraft.network.chat.Component;
import java.util.*;
public final class AttachedPointScreen extends Screen {
  private final CompoundTag payload;private final V original,origin;private EditBox xBox,yBox,zBox;private Button apply;private String status="";private int x,y;
  public AttachedPointScreen(CompoundTag t){super(Component.literal("道路附属端点"));payload=t.copy();original=RoadRecord.readNode(t.getCompound("Position")).position();var r=RoadRecord.load(t.getCompound("Road"));origin=r.settings().options().attachments().points().stream().filter(p->p.id().equals(t.getUUID("Point"))).findFirst().map(RoadAttachments.Point::origin).orElse(original);}
  @Override protected void init(){x=(width-300)/2;y=(height-206)/2;String sx=xBox==null?Double.toString(original.x()-origin.x()):xBox.getValue(),sy=yBox==null?Double.toString(original.y()-origin.y()):yBox.getValue(),sz=zBox==null?Double.toString(original.z()-origin.z()):zBox.getValue();xBox=box(x+36,y+55,sx);yBox=box(x+36,y+83,sy);zBox=box(x+36,y+111,sz);boolean marks=payload.getBoolean("MarkingsOnly");xBox.setEditable(!marks&&payload.getBoolean("XYZ"));zBox.setEditable(!marks&&payload.getBoolean("XYZ"));yBox.setEditable(!marks);
    apply=addRenderableWidget(Button.builder(Component.literal("应用"),b->send(false)).bounds(x+12,y+144,86,20).build());apply.active=!marks;
    addRenderableWidget(Button.builder(Component.literal("删除附属点"),b->send(true)).bounds(x+106,y+144,100,20).build());addRenderableWidget(Button.builder(Component.literal("关闭"),b->onClose()).bounds(x+214,y+144,74,20).build());}
  private EditBox box(int x,int y,String value){var e=new EditBox(font,x,y,248,20,Component.literal("坐标"));e.setMaxLength(24);e.setValue(value);addRenderableWidget(e);e.setResponder(s->preview());return e;}
  private V offset(){return new V(Double.parseDouble(xBox.getValue()),Double.parseDouble(yBox.getValue()),Double.parseDouble(zBox.getValue()));}
  private V value(){return origin.add(offset());}
  private void preview(){if(xBox==null||yBox==null||zBox==null||payload.getBoolean("MarkingsOnly"))return;try{var r=RoadRecord.load(payload.getCompound("Road"));var d=r.settings().options().attachments();var points=d.points().stream().map(p->p.id().equals(payload.getUUID("Point"))?p.at(value()):p).toList();ClientRoads.preview=r.withAttachments(d.points(points)).mesh();status="";}catch(IllegalArgumentException e){ClientRoads.preview=null;status="坐标无效或曲率/坡度超过限制";}}
  private void send(boolean delete){try{var t=new CompoundTag();t.putString("Action","attachedPoint");t.putUUID("Id",payload.getUUID("Id"));t.putUUID("Point",payload.getUUID("Point"));t.putInt("Signature",payload.getInt("Signature"));t.putBoolean("Delete",delete);if(!delete)t.put("Offset",RoadRecord.writeNode(new Node(offset(),0,0)));RoadNetwork.CHANNEL.sendToServer(new RoadNetwork.Action(t));apply.active=false;}catch(IllegalArgumentException e){status="请输入有效坐标";}}
  public void failed(String s){status=s;apply.active=!payload.getBoolean("MarkingsOnly");}
  @Override public void onClose(){ClientRoads.preview=null;super.onClose();}
  @Override public boolean isPauseScreen(){return false;}
  @Override public void render(GuiGraphics g,int mx,int my,float dt){renderBackground(g);g.fill(x,y,x+300,y+206,0xf015222e);g.drawString(font,"附属点 · 放置位置为 (0, 0, 0)",x+12,y+12,0xffffff,false);g.drawString(font,payload.getBoolean("MarkingsOnly")?"立交主路：仅标线分段":payload.getBoolean("XYZ")?"平滑曲线经过控制点，可调整 XYZ":"此道路仅可调整 Y",x+12,y+33,0xbbd3dc,false);g.drawString(font,"ΔX",x+10,y+60,0xffffff,false);g.drawString(font,"ΔY",x+10,y+88,0xffffff,false);g.drawString(font,"ΔZ",x+10,y+116,0xffffff,false);super.render(g,mx,my,dt);g.drawWordWrap(font,Component.literal(status),x+12,y+174,276,0xffbb88);}
}
