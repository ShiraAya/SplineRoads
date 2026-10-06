package com.sora.splineroads.client;

import com.sora.splineroads.core.RoadLanes;
import com.sora.splineroads.core.RoadProfile.Type;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** Transactional lane editor: cancel never changes the parent draft or live road. */
public final class RoadLaneConfigScreen extends Screen {
  private final RoadScreen parent;
  private Type type;
  private int forward,reverse,savedReverse;
  private int x,y;
  RoadLaneConfigScreen(RoadScreen parent,Type type,RoadLanes.Counts counts){
    super(Component.literal("道路车道配置"));this.parent=parent;this.type=type;
    forward=counts.forward();reverse=counts.reverse();savedReverse=reverse>0?reverse:forward;
  }
  @Override protected void init(){
    x=Math.max(8,(width-280)/2);y=Math.max(4,(height-194)/2);
    addRenderableWidget(Button.builder(Component.literal(type==Type.HIGHWAY?"高速道路":"普通道路"),b->{type=type==Type.HIGHWAY?Type.ORDINARY:Type.HIGHWAY;rebuildWidgets();}).bounds(x,y+24,136,20).build());
    addRenderableWidget(Button.builder(Component.literal(reverse>0?"双向":"单向"),b->{if(reverse>0){savedReverse=reverse;reverse=0;}else reverse=savedReverse;rebuildWidgets();}).bounds(x+144,y+24,136,20).build());
    addRenderableWidget(new CountSlider(x,y+54,forward,true));
    if(reverse>0)addRenderableWidget(new CountSlider(x,y+82,reverse,false));
    addRenderableWidget(Button.builder(Component.literal("应用配置"),b->{parent.applyLaneConfiguration(type,new RoadLanes.Counts(forward,reverse),false);minecraft.setScreen(parent);}).bounds(x,y+162,136,20).build());
    addRenderableWidget(Button.builder(Component.literal("取消"),b->onClose()).bounds(x+144,y+162,136,20).build());
  }
  private final class CountSlider extends AbstractSliderButton {
    final boolean along;
    CountSlider(int x,int y,int count,boolean along){super(x,y,280,20,Component.empty(),(count-1)/3.0);this.along=along;updateMessage();}
    int count(){return Math.max(1,Math.min(4,1+(int)Math.round(value*3)));}
    @Override protected void updateMessage(){setMessage(Component.literal((along?"A → B":"B → A")+"："+count()+" 车道（1–4）"));}
    @Override protected void applyValue(){if(along)forward=count();else{reverse=count();savedReverse=reverse;}}
  }
  @Override public void render(GuiGraphics g,int mx,int my,float dt){
    renderBackground(g);g.fill(x-8,y-6,x+288,y+190,0xEF12212C);
    g.drawString(font,title,x,y+6,0xFFFFFF,false);
    g.drawWordWrap(font,Component.literal("数量按 A→B / B→A 分别保存。合并后的续接端口以当前位置实际断面为准；未修改的旧路保留原配置。"),x,y+114,280,0xBDD5DE);
    super.render(g,mx,my,dt);
  }
  @Override public void onClose(){minecraft.setScreen(parent);}
  @Override public boolean isPauseScreen(){return false;}
}
