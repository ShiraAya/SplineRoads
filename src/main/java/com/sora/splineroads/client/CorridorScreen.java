package com.sora.splineroads.client;

import com.sora.splineroads.core.*;
import com.sora.splineroads.core.RoadGeometry.*;
import com.sora.splineroads.core.CorridorPlanner.*;
import com.sora.splineroads.net.RoadNetwork;
import com.sora.splineroads.world.*;
import java.util.*;
import java.util.concurrent.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.*;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.nbt.*;
import net.minecraft.network.chat.Component;

/** Separate workflow for parallel roads; crossing-interchange controls do not apply. */
public final class CorridorScreen extends Screen {
  private static final Map<String,CompoundTag> DRAFTS=new HashMap<>();
  private static final ExecutorService WORKER=Executors.newSingleThreadExecutor(r->{var t=new Thread(r,"SR corridor preview");t.setDaemon(true);return t;});
  private static long generation;
  private final CompoundTag payload;
  private final Map<String,EditBox> fields=new LinkedHashMap<>();
  private Config config;
  private InterchangePlanner.Options options;
  private InterchangePlanner.Plan plan;
  private Button save,preview;
  private int x,y,w,h,page,delay,waitTicks;
  private long request;
  private boolean initializing,dirty=true,computing,pending;
  private String status="正在计算同侧出入口…",confirmation="";

  public static void clear(){DRAFTS.clear();generation++;}
  public static void open(CompoundTag fresh){
    if(fresh.getString("Kind").equals("corridorCheck")){if(Minecraft.getInstance().screen instanceof CorridorScreen screen)screen.checked(fresh);return;}
    String mode=fresh.contains("GeneratorMode")?fresh.getString("GeneratorMode"):fresh.getCompound("Corridor").getString("Mode");
    String kind=fresh.getString("Kind");var old=DRAFTS.get(mode);var mc=Minecraft.getInstance();
    if(kind.equals("corridorReset")){DRAFTS.remove(mode);generation++;ClientRoads.preview=null;ClientRoads.nodePreviews=List.of();return;}
    if(kind.equals("corridorResume")){if(old!=null)mc.setScreen(new CorridorScreen(old));return;}
    CompoundTag merged=fresh.copy();
    if(old!=null&&Arrays.equals(old.getLongArray("Points"),fresh.getLongArray("Points"))
        &&old.hasUUID("Id")==fresh.hasUUID("Id")&&(!old.hasUUID("Id")||old.getUUID("Id").equals(fresh.getUUID("Id"))))
      for(String key:List.of("Corridor","Options","Main1","Main2"))merged.put(key,old.getCompound(key).copy());
    mc.setScreen(new CorridorScreen(merged));
  }
  public CorridorScreen(CompoundTag t){
    super(Component.literal(t.getCompound("Corridor").getString("Mode").equals("FRONTAGE")?"Spline Roads · 主辅路":"Spline Roads · 双层同侧出入口"));
    payload=t.copy();HostAxes.normalizeCorridor(payload);config=Corridors.read(payload.getCompound("Corridor"));options=Interchanges.read(t.getCompound("Options"));
    if(t.getBoolean("Partial"))status="已有路段被删除；整体更新会补回所选布局";
  }
  private void invalidate(){dirty=true;delay=8;plan=null;if(save!=null)save.active=false;if(preview!=null)preview.active=false;}
  private Button button(String label,int bx,int by,int bw,Runnable action){
    return addRenderableWidget(Button.builder(Component.literal(label),b->{if(!capture())return;action.run();stash();invalidate();rebuildWidgets();}).bounds(bx,by,bw,20).build());
  }
  private void field(String key,String label,double value,int bx,int by,int bw){
    EditBox f=new EditBox(font,bx,by+10,bw,18,Component.literal(label));f.setMaxLength(9);f.setValue(String.format(Locale.ROOT,"%.2f",value));
    f.setResponder(v->{if(!initializing)invalidate();});fields.put(key,f);addRenderableWidget(f);
  }
  private double number(String key,double fallback){return fields.containsKey(key)?Double.parseDouble(fields.get(key).getValue()):fallback;}
  private Settings settings(int axis){return RoadRecord.readSettings(payload.getCompound("Main"+axis));}
  private void style(int axis){
    var old=settings(axis);List<Style> styles=config.kind()==Kind.FRONTAGE&&axis==2
        ?List.of(Style.O1_ONE,Style.O2_ONE,Style.O3_ONE,Style.O4_ONE)
        :List.of(Style.O2_YELLOW,Style.O4_YELLOW,Style.O6_GREEN,Style.H4_RAIL,Style.H6_RAIL);
    Style next=styles.get((styles.indexOf(old.style())+1)%styles.size());
    payload.put("Main"+axis,RoadRecord.writeSettings(new Settings(Mode.STRAIGHT,next,next.defaultWidth(),old.thickness(),.4,90).options(old.options())));
  }
  @Override protected void init(){
    initializing=true;fields.clear();w=Math.min(680,width-12);h=Math.min(312,height-8);x=(width-w)/2;y=(height-h)/2;
    int bx=x+12,by=y+27,c=w>=540?286:w-24,half=(c-6)/2;
    int tab=(c-12)/3;
    button((page==0?"• ":"")+"出入口",bx,by,tab,()->page=0);
    button((page==1?"• ":"")+"端点调整",bx+tab+6,by,tab,()->page=1);
    button((page==2?"• ":"")+"道路样式",bx+2*(tab+6),by,tab,()->page=2);by+=28;
    if(page==0){
      button(config.sides().label+" ▸",bx,by,half,()->config=new Config(config.kind(),Sides.values()[(config.sides().ordinal()+1)%3],config.access(),config.gap(),config.frontageY(),config.adjustment()));
      if(!HostAxes.fixedFrontage(payload))button(config.access().label+" ▸",bx+half+6,by,half,()->config=new Config(config.kind(),config.sides(),Access.values()[(config.access().ordinal()+1)%4],config.gap(),config.frontageY(),config.adjustment()));
      else button("仅建道路（主路有高差）",bx+half+6,by,half,()->{}).active=false;by+=25;
      button(options.leftTraffic()?"靠左行驶":"靠右行驶",bx,by,half,()->options=changed(!options.leftTraffic(),options.lanes()));
      button("连接匝道："+options.lanes()+" 车道",bx+half+6,by,half,()->options=changed(options.leftTraffic(),3-options.lanes()));by+=26;
      field("gap",config.kind()==Kind.FRONTAGE?"主辅路净间隔":"匝道外绕净间隔",config.gap(),bx,by,half);
      if(config.kind()==Kind.FRONTAGE&&!HostAxes.fixedFrontage(payload))field("height","辅路路面 Y",config.frontageY(),bx+half+6,by,half);
      else if(config.kind()==Kind.FRONTAGE)button("辅路 Y = A："+config.frontageY(),bx+half+6,by+10,half,()->{}).active=false;by+=39;
      field("transition","两端过渡长度",options.transition(),bx,by,half);field("radius","最小半径",options.radius(),bx+half+6,by,half);by+=39;
    }else if(page==1){
      button("调整端点："+(options.adjustEndpoints()?"允许":"关闭"),bx,by,c,()->options=options.adjust(!options.adjustEndpoints()));by+=26;
      button("端点范围："+config.adjustment().label+" ▸",bx,by,c,()->config=config.adjustment(Adjustment.values()[(config.adjustment().ordinal()+1)%Adjustment.values().length]));by+=30;
      field("clearance","交叠道路净高",options.clearance(),bx,by,c);
    }else{
      for(int axis=1;axis<=2;axis++){
        final int selected=axis;var s=settings(axis);
        button((axis==1?"AB 主路：":config.kind()==Kind.FRONTAGE?"同向辅路：":"CD 第二层：")+(RoadProfile.catalog(s.style()).type()==RoadProfile.Type.HIGHWAY?"高速":"普通")+RoadProfile.catalog(s.style()).name()+" ▸",bx,by,c,()->style(selected));by+=25;
        field("width"+axis,"道路总宽",s.width(),bx,by,half);field("thickness"+axis,"路板厚度",s.thickness(),bx+half+6,by,half);by+=42;
      }
    }
    int n=payload.hasUUID("Id")?5:3,bw=(w-24-6*(n-1))/n,footer=y+h-26;
    save=addRenderableWidget(Button.builder(Component.literal(payload.hasUUID("Id")?"整体更新":"整体建造"),b->submit("interchange")).bounds(bx,footer,bw,20).build());
    preview=addRenderableWidget(Button.builder(Component.literal("实景预览"),b->{if(capture()){stash();minecraft.setScreen(null);}}).bounds(bx+bw+6,footer,bw,20).build());
    addRenderableWidget(Button.builder(Component.literal("关闭"),b->onClose()).bounds(bx+2*(bw+6),footer,bw,20).build());
    if(n==5)for(int i=0;i<2;i++){
      String action=i==0?"interchangeDeleteRamps":"interchangeDelete";boolean ramps=i==0;
      addRenderableWidget(Button.builder(Component.literal(ramps?"仅删匝道":"删除组合"),b->{
        if(!action.equals(confirmation)){confirmation=action;b.setMessage(Component.literal("再次点击确认"));status=ramps?"保留主路与辅路/第二层道路，解除组合关系":"删除此组合的所有道路，保留端点";}else submit(action);
      }).bounds(bx+(3+i)*(bw+6),footer,bw,20).build());
    }
    save.active=preview.active=plan!=null&&!dirty&&!computing&&!pending;
    initializing=false;delay=1;
  }
  private InterchangePlanner.Options changed(boolean left,int lanes){return new InterchangePlanner.Options(options.preset(),left,lanes,options.transition(),options.radius(),options.clearance(),1,0,options.adjustEndpoints());}
  private boolean capture(){
    try{
      config=new Config(config.kind(),config.sides(),config.access(),number("gap",config.gap()),number("height",config.frontageY()),config.adjustment());
      options=new InterchangePlanner.Options(options.preset(),options.leftTraffic(),options.lanes(),number("transition",options.transition()),number("radius",options.radius()),number("clearance",options.clearance()),1,options.rampWidth(),options.adjustEndpoints());
      for(int i=1;i<=2;i++){var old=settings(i);var next=new Settings(Mode.STRAIGHT,old.style(),number("width"+i,old.width()),number("thickness"+i,old.thickness()),.4,90).options(old.options());next.validate();payload.put("Main"+i,RoadRecord.writeSettings(next));}
      payload.put("Corridor",Corridors.write(config));HostAxes.normalizeCorridor(payload);config=Corridors.read(payload.getCompound("Corridor"));
      return true;
    }catch(IllegalArgumentException e){status=e instanceof NumberFormatException?"请输入有效数字":e.getMessage();return false;}
  }
  private void stash(){payload.put("Corridor",Corridors.write(config));payload.put("Options",Interchanges.write(options));DRAFTS.put(config.kind().name(),payload.copy());}
  @Override public void tick(){super.tick();if(delay>0)delay--;if(computing&&++waitTicks>400){computing=false;request=++generation;status="预览检查超时，请调整参数或重新打开后重试";}if(delay==0&&dirty&&!computing)calculate();}
  private void calculate(){
    dirty=false;if(!capture())return;stash();computing=true;waitTicks=0;request=++generation;
    CompoundTag command=new CompoundTag();for(String key:List.of("Id","Points","Main1","Main2","Options","Corridor"))if(payload.contains(key))command.put(key,payload.get(key).copy());
    command.putString("Action","interchangePreview");command.putLong("Request",request);status="正在核对既有道路、端点占用与可调整方向…";
    RoadNetwork.CHANNEL.sendToServer(new RoadNetwork.Action(command));
  }
  private void checked(CompoundTag reply){
    if(reply.getLong("Request")!=request||!computing||minecraft.level==null||!minecraft.level.dimension().location().toString().equals(reply.getString("Dimension")))return;
    if(dirty){computing=false;delay=1;return;}
    if(reply.contains("Error")){computing=false;status=reply.getString("Error");ClientRoads.preview=null;ClientRoads.nodePreviews=List.of();return;}
    var inputs=reply.getCompound("Inputs").copy();payload.put("Nodes",inputs.getList("Nodes",Tag.TAG_COMPOUND).copy());payload.putString("ResolvedFit",inputs.getString("ResolvedFit"));payload.put("HostAxes",inputs.getList("HostAxes",Tag.TAG_COMPOUND).copy());payload.put("Corridor",inputs.getCompound("Corridor").copy());config=Corridors.read(payload.getCompound("Corridor"));
    long token=request;waitTicks=0;status="检查通过，正在生成道路预览…";
    CompletableFuture.supplyAsync(()->Interchanges.plan(inputs),WORKER).whenComplete((result,error)->Minecraft.getInstance().execute(()->{
      if(token!=generation)return;computing=false;if(dirty){delay=1;return;}
      if(error!=null){Throwable cause=error.getCause()==null?error:error.getCause();status=cause.getMessage();ClientRoads.preview=null;ClientRoads.nodePreviews=List.of();return;}
      plan=result;ClientRoads.nodePreviews=result.legs().stream().map(l->RoadRenderMesh.simplify(l.mesh())).toList();ClientRoads.preview=ClientRoads.nodePreviews.get(0);
      status=(options.adjustEndpoints()?"端点："+Adjustment.valueOf(inputs.getString("ResolvedFit")).label+"；":"")+result.movements()+" 条同侧出入口；最大纵坡 "+String.format(Locale.ROOT,"%.1f%%",result.legs().stream().mapToDouble(l->RoadGrades.maximum(l.mesh())).max().orElse(0)*100)+"。左右以 A→B 为准。";
      int moved=0;double distance=0;var source=payload.getList("Nodes",Tag.TAG_COMPOUND);
      for(int i=0;i<result.anchors().size();i++){double d=result.anchors().get(i).position().distance(RoadRecord.readNode(source.getCompound(i)).position());if(d>1e-6){moved++;distance=Math.max(distance,d);}}
      if(HostAxes.fixedFrontage(payload))status="主路有高差：仅建辅路，不设出入口；辅路固定在 A 端高度 "+config.frontageY()+"。";
      if(moved>0)status+=" 自动调整 "+moved+" 个端点，最大移动 "+String.format(Locale.ROOT,"%.1f",distance)+" 格（实景预览可查看）。";
      if(payload.getBoolean("Partial"))status+=" 整体更新会补回已删除路段。";
      save.active=preview.active=!pending;
    }));
  }
  private void submit(String action){
    if(pending||action.equals("interchange")&&(plan==null||dirty||computing))return;
    if(!capture())return;stash();CompoundTag command=new CompoundTag();
    for(String key:List.of("Id","Points","Main1","Main2","Options","Corridor"))if(payload.contains(key))command.put(key,payload.get(key).copy());
    command.putInt("SourceAxesHash",com.sora.splineroads.world.HostAxes.signature(payload));
    command.put("SourceNodes",payload.getList("Nodes",Tag.TAG_COMPOUND).copy());
    if(plan!=null){ListTag fitted=new ListTag();plan.anchors().forEach(n->fitted.add(RoadRecord.writeNode(n)));command.put("FittedNodes",fitted);}
    command.putString("Action",action);pending=true;save.active=false;preview.active=false;status="正在检查并提交道路组合…";
    RoadNetwork.CHANNEL.sendToServer(new RoadNetwork.Action(command));
  }
  public void failed(String reason){pending=false;status=reason;save.active=plan!=null&&!dirty;preview.active=save.active;}
  @Override public boolean isPauseScreen(){return false;}
  @Override public void onClose(){generation++;if(capture())stash();ClientRoads.preview=null;ClientRoads.nodePreviews=List.of();super.onClose();}
  @Override public void render(GuiGraphics g,int mx,int my,float partial){
    renderBackground(g);g.fill(x,y,x+w,y+h,0xEF182530);g.drawString(font,title,x+12,y+10,0xFFFFFF,false);super.render(g,mx,my,partial);
    for(var f:fields.values())g.drawString(font,f.getMessage(),f.getX(),f.getY()-9,0xC8DAE5,false);
    if(w>=540){int px=x+312,py=y+55,pw=w-324,ph=h-123;g.fill(px,py,px+pw,py+ph,0xFF09161F);
      if(plan!=null){
        V origin=plan.anchors().get(0).position(),axis=plan.anchors().get(1).position().sub(origin).horizontalUnit();
        double length=plan.anchors().get(1).position().distance(origin),extent=plan.legs().stream().flatMap(l->l.mesh().samples().stream()).mapToDouble(s->Math.abs(s.center().sub(origin).dot(axis.left()))+s.halfWidth()).max().orElse(20);
        double scale=Math.min((pw-16)/length,(ph-30)/(extent*2));
        for(var leg:plan.legs())for(int i=0;i<leg.mesh().samples().size();i+=2){Sample s=leg.mesh().samples().get(i);V d=s.center().sub(origin);int xx=px+8+(int)(d.dot(axis)*scale),zz=py+ph/2+(int)(d.dot(axis.left())*scale),r=Math.max(1,(int)(s.halfWidth()*scale));g.fill(xx-r,zz-r,xx+r+1,zz+r+1,leg.mesh().settings().style().ramp()?0xFFFFCD6D:leg.name().contains("辅路")?0xFF71C7A5:0xFF579CC9);}
        g.drawString(font,"A → B 俯视 · 黄色为连接匝道",px+6,py+6,0xFFFFFF,false);
      }
    }
    var lines=font.split(Component.literal(status==null?"":status),w-24);for(int i=0;i<Math.min(2,lines.size());i++)g.drawString(font,lines.get(i),x+12,y+h-51+i*10,plan==null?0xFFFFB6A3:0xFFC8E9DA,false);
    if(lines.size()>2&&my>=y+h-52&&my<y+h-29)g.renderTooltip(font,Component.literal(status),mx,my);
  }
}
