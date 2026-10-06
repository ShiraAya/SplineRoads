package com.sora.splineroads.client;
import com.sora.splineroads.core.*;
import com.sora.splineroads.world.*;
import com.sora.splineroads.net.RoadNetwork;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.*;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.nbt.*;
import net.minecraft.network.chat.Component;
import java.util.*;

/** Uses the shared world preview. Hiding a screen does not discard its checked draft. */
public final class LaneRampScreen extends Screen {
  private static LaneRampScreen active;
  private static long sequence;
  private final CompoundTag payload;
  private LanePoints.Path path;private boolean pending,gradeOverride;
  private LanePoints.Departure departure;private LanePoints.Arrival arrival;
  private LanePoints.Elevation elevation;private LanePoints.Landing landing;
  private List<RoadGeometry.Mesh> checkedRoads=List.of();
  private String radiusText,transitionText;
  private EditBox radius,transition;private Button build,previewButton;private UUID token;private RoadGeometry.Mesh checkedMesh;
  private long request=++sequence;private int x,y;
  private String status="预览后进入实景；右键空气返回设置。Shift＋右键清除选点。";
  public LaneRampScreen(CompoundTag t){
    super(Component.literal(t.hasUUID("Id")?"编辑匝道":"匝道连接器"));payload=t.copy();
    var o=LanePointCodec.options(t.getCompound("Options"));path=o.path();departure=o.departure();arrival=o.arrival();elevation=o.elevation();landing=o.landing();gradeOverride=o.gradeOverride();radiusText=number(o.radius());transitionText=number(o.transition());
  }
  private static String number(double value){return String.format(Locale.ROOT,"%s",value==(int)value?Integer.toString((int)value):Double.toString(value));}
  public static void open(CompoundTag t){
    if(active==null||!t.hasUUID("Id")||!active.payload.hasUUID("Id")||!t.getUUID("Id").equals(active.payload.getUUID("Id"))||t.getInt("Signature")!=active.payload.getInt("Signature")){
      clear();active=new LaneRampScreen(t);
    }
    resume();
  }
  public static void resume(){if(active!=null){ClientRoads.preview=active.checkedMesh;ClientRoads.nodePreviews=active.checkedRoads;Minecraft.getInstance().setScreen(active);}}
  public static void resume(CompoundTag t){if(active==null&&t.contains("Fallback"))open(t.getCompound("Fallback"));else resume();}
  public static void clear(){active=null;++sequence;ClientRoads.preview=null;ClientRoads.nodePreviews=List.of();}
  public static void checkedReply(CompoundTag t){if(active!=null)active.checked(t);}
  public static void failedPending(String message){if(active!=null&&active.pending&&active.token!=null)active.failed(message);}
  @Override protected void init(){
    x=(width-354)/2;y=(height-306)/2;
    for(var choice:LanePoints.Path.values())addRenderableWidget(Button.builder(Component.literal((choice==path?"● ":"")+choice.label),b->{path=choice;invalidate();rebuildWidgets();}).bounds(x+12+choice.ordinal()*66,y+51,64,20).build());
    addRenderableWidget(Button.builder(Component.literal("汇出："+departure.label),b->{departure=LanePoints.Departure.values()[(departure.ordinal()+1)%LanePoints.Departure.values().length];invalidate();rebuildWidgets();}).bounds(x+12,y+81,160,20).build());
    addRenderableWidget(Button.builder(Component.literal("汇入："+arrival.label),b->{arrival=LanePoints.Arrival.values()[(arrival.ordinal()+1)%LanePoints.Arrival.values().length];invalidate();rebuildWidgets();}).bounds(x+182,y+81,160,20).build());
    addRenderableWidget(Button.builder(Component.literal(elevation.label),b->{elevation=LanePoints.Elevation.values()[(elevation.ordinal()+1)%LanePoints.Elevation.values().length];invalidate();rebuildWidgets();}).bounds(x+12,y+108,160,20).build());
    addRenderableWidget(Button.builder(Component.literal(landing.label),b->{landing=LanePoints.Landing.values()[(landing.ordinal()+1)%LanePoints.Landing.values().length];invalidate();rebuildWidgets();}).bounds(x+182,y+108,160,20).build());
    radius=new EditBox(font,x+72,y+144,90,20,Component.literal("半径"));radius.setMaxLength(6);radius.setValue(radiusText);radius.setResponder(s->{radiusText=s;invalidate();});addRenderableWidget(radius);
    transition=new EditBox(font,x+252,y+144,90,20,Component.literal("过渡长度"));transition.setMaxLength(6);transition.setValue(transitionText);transition.setResponder(s->{transitionText=s;invalidate();});addRenderableWidget(transition);
    addRenderableWidget(Button.builder(Component.literal(gradeOverride?"坡比超限：开（普通25% / 涉高速20%）":"坡比超限：关（普通20% / 涉高速15%）"),b->{gradeOverride=!gradeOverride;invalidate();rebuildWidgets();}).bounds(x+12,y+173,330,20).build());
    previewButton=addRenderableWidget(Button.builder(Component.literal("实景预览"),b->preview()).bounds(x+12,y+209,105,20).build());previewButton.active=!pending;
    build=addRenderableWidget(Button.builder(Component.literal(payload.hasUUID("Id")?"保存匝道":"建造 A → B"),b->submit()).bounds(x+125,y+209,110,20).build());build.active=token!=null&&!pending;
    addRenderableWidget(Button.builder(Component.literal("返回实景"),b->onClose()).bounds(x+243,y+209,99,20).build());
  }
  private void invalidate(){token=null;checkedMesh=null;checkedRoads=List.of();pending=false;if(build!=null)build.active=false;if(previewButton!=null)previewButton.active=true;ClientRoads.preview=null;ClientRoads.nodePreviews=List.of();request=++sequence;status=switch(departure){
      case TEMPORARY -> "保留车道分离：直连匝道，主路暂时关闭该车道；净空安全后恢复。地面封闭绿化、高架直角孔区。请预览。";
      case DETACH -> "整车道分离：直连匝道，主路下游取消该车道。请预览。";
      case BRANCH -> "普通分流：原车道继续直行；不是先关闭再恢复。请预览。";
      case EXTRA -> "额外扩出：保持既有车道，另拓出匝道。请预览。";
    };}
  private CompoundTag command(String action){var t=new CompoundTag();t.putString("Action",action);t.put("From",payload.getCompound("From").copy());t.put("To",payload.getCompound("To").copy());if(payload.hasUUID("Id")){t.putUUID("Id",payload.getUUID("Id"));t.putInt("Signature",payload.getInt("Signature"));}t.put("Options",LanePointCodec.options(new LanePoints.Options(path,departure,arrival,Double.parseDouble(radiusText),Double.parseDouble(transitionText),elevation,landing,gradeOverride)));t.putLong("Request",request);return t;}
  private void preview(){try{invalidate();var t=command("laneRampPreview");pending=true;previewButton.active=false;RoadNetwork.CHANNEL.sendToServer(new RoadNetwork.Action(t));status="正在检查方向、汇入范围与净空…";}catch(IllegalArgumentException e){failed(e.getMessage());}}
  public void checked(CompoundTag t){
    if(t.getLong("Request")!=request)return;pending=false;
    if(t.contains("Error")){failed(t.getString("Error"));return;}
    try{var r=RoadRecord.load(t.getCompound("Road"));checkedMesh=r.mesh();var views=new ArrayList<RoadGeometry.Mesh>();for(Tag value:t.getList("ChangedRoads",Tag.TAG_COMPOUND))views.add(RoadRecord.load((CompoundTag)value).mesh());checkedRoads=views.isEmpty()?List.of(checkedMesh):List.copyOf(views);ClientRoads.preview=checkedMesh;ClientRoads.nodePreviews=checkedRoads;token=t.getUUID("Token");build.active=true;previewButton.active=true;
      double offset=t.getDouble("TargetOffset");String landing=payload.getCompound("To").hasUUID("Junction")?"":String.format(Locale.ROOT," 汇入口：沿 B 行驶方向 %+.1f 格。",offset);
      String grade=t.contains("GradeLimit")?" 坡比上限 "+LaneRampGrade.label(t.getDouble("GradeLimit"))+"。":"";
      if(t.contains("GradeAvailable"))grade=String.format(Locale.ROOT," 水平 %.1f / 可布坡 %.1f 格，当前变程需≥%.1f 格；实际最大 %.2f%%，上限 %s。",t.getDouble("GradeHorizontal"),t.getDouble("GradeAvailable"),t.getDouble("GradeMinimum"),t.getDouble("ActualGrade")*100,LaneRampGrade.label(t.getDouble("GradeLimit")));
      String closure=t.getBoolean("TemporaryClosure")?(t.getBoolean("RectangularClosure")?String.format(Locale.ROOT," 主路从 A 暂时关闭此车道，下游 %.1f 格处恢复；封闭区不收尖。",t.getDouble("RestoredAfter")):String.format(Locale.ROOT," 主路从A暂时关闭此车道，下游 %.1f 格开始恢复、%.1f 格恢复完整。",t.getDouble("ReopenAfter"),t.getDouble("RestoredAfter"))):"";
      String targetClosure=t.getBoolean("TargetClosure")?String.format(Locale.ROOT," 目标车道在汇入口前 %.1f 格开始封闭，到匝道完整接入后开放；其他车道保持通行。",t.getDouble("TargetClosedBefore")):"";
      status=String.format(Locale.ROOT,"预览有效：%s，长 %.1f 格。%s%s%s%s 右键空气返回并建造。",t.contains("ResolvedPath")?LanePoints.Path.valueOf(t.getString("ResolvedPath")).label:path.label,r.mesh().length(),landing,closure,targetClosure,grade);
      if(Minecraft.getInstance().screen==this)Minecraft.getInstance().setScreen(null);
    }catch(IllegalArgumentException e){failed(e.getMessage());}
  }
  private void submit(){try{if(token==null||pending)return;var t=command("laneRamp");t.putUUID("Token",token);pending=true;build.active=false;previewButton.active=false;RoadNetwork.CHANNEL.sendToServer(new RoadNetwork.Action(t));}catch(IllegalArgumentException e){failed(e.getMessage());}}
  public void failed(String s){status=s;pending=false;if(build!=null)build.active=false;if(previewButton!=null)previewButton.active=true;token=null;checkedMesh=null;checkedRoads=List.of();ClientRoads.preview=null;ClientRoads.nodePreviews=List.of();}
  @Override public void onClose(){super.onClose();}
  @Override public boolean isPauseScreen(){return false;}
  @Override public void render(GuiGraphics g,int mx,int my,float dt){g.fill(x,y,x+354,y+306,0xea15222e);g.drawString(font,payload.hasUUID("Id")?"编辑匝道 · A 汇出 → B 汇入":"匝道连接器 · A 汇出 → B 汇入",x+12,y+12,0x66b5ff,false);g.drawString(font,payload.getCompound("To").hasUUID("Junction")?"目标：所选路口的实际新增接入口":landing==LanePoints.Landing.EXACT?"目标：精确锁定 B，不移动通道口":"目标：B 点附近同车道，保持行驶方向",x+12,y+32,0xd2e2ed,false);g.drawString(font,"半径",x+12,y+150,0xffffff,false);g.drawString(font,"过渡长度",x+184,y+150,0xffffff,false);g.drawWordWrap(font,Component.literal(status),x+12,y+242,330,0xffcf8c);super.render(g,mx,my,dt);}
}
