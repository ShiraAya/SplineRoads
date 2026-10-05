package com.sora.splineroads.client;
import com.sora.splineroads.core.*;
import com.sora.splineroads.core.RoadInfrastructure.Gantry;
import com.sora.splineroads.world.RoadRecord;
import com.sora.splineroads.net.RoadNetwork;
import java.util.Locale;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.*;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;

public final class GantryScreen extends Screen {
  private final CompoundTag payload;private RoadGantry.Edit edit;private EditBox offset,clearance;
  private Button apply,reset;private String status="";private boolean pending;private int x,y;
  public GantryScreen(CompoundTag tag){
    super(Component.literal("Spline Roads · 单架龙门架编辑"));payload=tag.copy();
    edit=RoadGantry.edit(RoadRecord.readSettings(tag.getCompound("Settings")).options().infrastructure(),tag.getInt("Slot"));
  }
  @Override protected void init(){
    x=(width-284)/2;y=Math.max(2,(height-246)/2);
    offset=field("沿路位移",Double.toString(edit.offset()),x+140,y+37);
    clearance=field("横梁基准净高",Double.toString(edit.clearance()),x+140,y+68);
    addRenderableWidget(Button.builder(Component.literal("样式："+edit.kind().label),b->{if(capture()){
      edit=new RoadGantry.Edit(edit.slot(),edit.offset(),edit.clearance(),Gantry.values()[(edit.kind().ordinal()+1)%Gantry.values().length],edit.reverse());rebuildWidgets();
    }}).bounds(x+10,y+99,264,20).build());
    apply=addRenderableWidget(Button.builder(Component.literal("应用到这一架"),b->send(false)).bounds(x+10,y+151,128,20).build());
    reset=addRenderableWidget(Button.builder(Component.literal("恢复这一架默认"),b->send(true)).bounds(x+146,y+151,128,20).build());
    addRenderableWidget(Button.builder(Component.literal("取消"),b->onClose()).bounds(x+10,y+175,264,20).build());
    apply.active=reset.active=!pending;
  }
  private EditBox field(String name,String value,int px,int py){var box=new EditBox(font,px,py,134,20,Component.literal(name));box.setMaxLength(16);box.setValue(value);addRenderableWidget(box);return box;}
  private boolean capture(){
    try{var next=new RoadGantry.Edit(edit.slot(),Double.parseDouble(offset.getValue()),Double.parseDouble(clearance.getValue()),edit.kind(),edit.reverse());
      if(Math.abs(next.offset())>payload.getDouble("MaxOffset"))throw new IllegalArgumentException(String.format(Locale.ROOT,"这架最多前后移动 %.1f 格",payload.getDouble("MaxOffset")));
      edit=next;status="";return true;
    }catch(IllegalArgumentException e){status=e instanceof NumberFormatException?"请输入完整数值":e.getMessage();return false;}
  }
  private void send(boolean restore){
    if(pending||!restore&&!capture())return;
    var t=new CompoundTag();t.putString("Action","gantry");t.putUUID("Id",payload.getUUID("Id"));t.putInt("Slot",edit.slot());t.putInt("Signature",payload.getInt("Signature"));t.putBoolean("Reset",restore);
    t.putDouble("Offset",edit.offset());t.putDouble("Clearance",edit.clearance());t.putString("Style",edit.kind().name());t.putBoolean("Reverse",edit.reverse());
    pending=true;apply.active=reset.active=false;status="正在更新这座龙门架…";RoadNetwork.CHANNEL.sendToServer(new RoadNetwork.Action(t));
  }
  public void failed(String message){pending=false;status=message;apply.active=reset.active=true;}
  @Override public boolean isPauseScreen(){return false;}
  @Override public void render(GuiGraphics g,int mx,int my,float dt){
    renderBackground(g);g.fill(x,y,x+284,y+246,0xf012212c);
    g.drawString(font,"龙门架 "+(edit.slot()+1)+" / "+payload.getInt("Count")+" · 单独编辑",x+10,y+12,0xffffff,false);
    g.drawString(font,"沿路位移 / 格",x+10,y+43,0xbbd3dc,false);g.drawString(font,"横梁基准净高 / 格",x+10,y+74,0xbbd3dc,false);
    super.render(g,mx,my,dt);
    String help=status.isEmpty()?"自动＝跟随整段设置。设备横梁高于此净高 1.5 格并向上对齐整格。右键道路可编辑已关闭的架位。":status;
    g.drawWordWrap(font,Component.literal(help),x+10,y+203,264,status.isEmpty()?0xbbd3dc:0xffcaa2);
  }
}
