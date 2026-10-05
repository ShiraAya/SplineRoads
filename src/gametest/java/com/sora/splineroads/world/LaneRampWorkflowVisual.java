package com.sora.splineroads.world;
import com.sora.splineroads.SplineRoads;
import com.sora.splineroads.client.*;
import com.sora.splineroads.core.*;
import com.sora.splineroads.core.RoadGeometry.*;
import net.minecraft.client.*;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.nbt.*;
import net.minecraft.server.level.*;
import net.minecraft.world.*;
import net.minecraft.world.item.*;
import java.util.*;
import java.nio.file.*;
import static com.sora.splineroads.world.Visual38Suite.*;

final class LaneRampWorkflowVisual {
 static int stage,reloadStage;static long deadline;static UUID source,target,ramp;static LanePoints.Point a,b;static double editedWidth;
 static Visual38Suite.Scene scene(UUID id){V at=new V(8015,-60,-160),detail=new V(8030,-60,-326);return new Visual38Suite.Scene(id,at,at.add(new V(15,110,50)),detail,detail.add(new V(25,88,54)));}
 static Visual38Suite.Scene build(ServerLevel l,RoadData data,boolean reload)throws Exception{
  if(reload){var expected=RoadData.load(NbtIo.readCompressed(OUT.resolve(Files.exists(OUT.resolve("ramp-crossing-roads.nbt"))?"ramp-crossing-roads.nbt":"lane-workflow-final.nbt").toFile()));require(expected.index.roads.keySet().equals(data.index.roads.keySet()),"disk restart preserves IDs");for(var e:expected.index.roads.entrySet()){var r=data.index.roads.get(e.getKey()).record;require(LaneTopology.metadata(r).equals(LaneTopology.metadata(e.getValue().record)),"disk restart preserves actual landing offset and points");require(r.settings().width()==e.getValue().record.settings().width(),"disk restart preserves road editor width");}var r=data.index.roads.values().stream().filter(v->LaneTopology.metadata(v.record).link()!=null&&LaneTopology.metadata(v.record).link().options().sourceExtra()).findFirst().orElseThrow();ramp=r.record.id();reloadStage=0;require(!LaneTopology.needsRefresh(data.index.roads.values()),"disk restart produces stable geometry");return scene(r.record.id());}
  var p=l.getServer().getPlayerList().getPlayers().get(0);var s=new Settings(Mode.STRAIGHT,Style.O2_ONE,8,1,.35,90).structure(Structure.GROUND);
  var aa=marker(l,8000,-60,0,180);var ab=marker(l,8000,-60,-220,180);var ar=data.connect(l,p,aa,ab,s,null);source=ar.id();
  var ba=marker(l,8030,-60,-260,180);var bb=marker(l,8030,-60,-500,180);var br=data.connect(l,p,ba,bb,s,null);target=br.id();
  a=LanePointTool.create(l,p,source,LanePoints.lane(ar.mesh(),40,1).position());b=LanePointTool.create(l,p,target,LanePoints.lane(br.mesh(),110,1).position());
  var stack=new ItemStack(SplineRoads.RAMP_CONNECTOR.get());p.setItemInHand(InteractionHand.MAIN_HAND,stack);var tool=(LaneRampTool)stack.getItem();aim(p,a.position());tool.use(l,p,InteractionHand.MAIN_HAND);aim(p,b.position());tool.use(l,p,InteractionHand.MAIN_HAND);
  require(stack.getTag().contains("LaneFrom")&&stack.getTag().contains("LaneTo"),"actual item selects both lane points");stage=0;deadline=System.nanoTime()+180_000_000_000L;return scene(source);
 }
 static void server(Minecraft mc,java.util.function.Consumer<ServerPlayer> action){mc.getSingleplayerServer().execute(()->{try{action.accept(mc.getSingleplayerServer().getPlayerList().getPlayers().get(0));}catch(Throwable e){building=new java.util.concurrent.CompletableFuture<>();building.completeExceptionally(e);}});}
 static void air(ServerPlayer p,boolean shift){p.setXRot(-90);p.setShiftKeyDown(shift);((LaneRampTool)p.getMainHandItem().getItem()).use(p.level(),p,InteractionHand.MAIN_HAND);p.setShiftKeyDown(false);}
 static void press(net.minecraft.client.gui.screens.Screen screen,String label){var button=screen.children().stream().filter(v->v instanceof Button x&&x.getMessage().getString().contains(label)).map(v->(Button)v).findFirst().orElseThrow();require(button.active,"button enabled: "+label);button.onPress();}
 static void photo(Minecraft mc,String name)throws Exception{try(var shot=Screenshot.takeScreenshot(mc.getMainRenderTarget())){shot.writeToFile(OUT.resolve(name+".png"));}}
 static boolean tick(Minecraft mc)throws Exception{
  if(CASES.get(index).equals("lane-workflow-reload")&&view==0){
    if(reloadStage==0){deadline=System.nanoTime()+180_000_000_000L;server(mc,p->{var r=RoadData.get(p.serverLevel()).index.roads.get(ramp).record;click(p,SplineRoads.RAMP_CONNECTOR.get(),RoadStructures.sample(r.mesh(),r.mesh().length()/2).center());});reloadStage=1;return true;}
    if(reloadStage==1&&mc.screen instanceof LaneRampScreen screen){press(screen,"实景预览");reloadStage=2;return true;}
    if(reloadStage==2&&mc.screen==null&&ClientRoads.preview!=null){camera(mc);reloadStage=3;wait=30;return true;}
    if(reloadStage==3){require(mc.screen==null&&ClientRoads.preview!=null,"reloaded draft renders in world with no UI");photo(mc,"lane-workflow-world-preview-reloaded");server(mc,p->air(p,true));reloadStage=4;wait=10;return true;}
    if(reloadStage==4){camera(mc);reloadStage=5;wait=25;return true;}
    if(reloadStage<5){if(System.nanoTime()>deadline)throw new AssertionError("reloaded preview timed out at stage "+reloadStage);return true;}
  }
  if(!CASES.get(index).equals("lane-workflow"))return false;
  if(stage==20){if(mc.player.getXRot()<0){camera(mc);wait=20;return true;}return false;}
  if(System.nanoTime()>deadline)throw new AssertionError("workflow timed out at stage "+stage+" screen="+mc.screen+" status="+(mc.screen instanceof LaneRampScreen?field(mc.screen,"status"):""));
  if(stage==0){server(mc,p->air(p,false));stage=1;return true;}
  if(stage==1&&mc.screen instanceof LaneRampScreen screen){press(screen,"直接连接");press(screen,"汇出：");press(screen,"汇出：");press(screen,"汇入：");press(screen,"汇入：");press(screen,"实景预览");stage=2;return true;}
  if(stage==2&&mc.screen==null&&ClientRoads.preview!=null){require(!ClientRoads.nodePreviews.isEmpty(),"shared multi-road preview includes actual affected hosts");stage=3;camera(mc);wait=35;return true;}
  if(stage==3){if(mc.player.getXRot()<0){camera(mc);wait=20;return true;}photo(mc,"lane-workflow-world-preview");server(mc,p->air(p,false));stage=4;return true;}
  if(stage==4&&mc.screen instanceof LaneRampScreen screen){require(field(screen,"token")!=null,"reopening retains checked preview token");screen.onClose();require(mc.screen==null&&ClientRoads.preview!=null,"Escape/return preserves world preview");server(mc,p->air(p,false));stage=5;return true;}
  if(stage==5&&mc.screen instanceof LaneRampScreen screen){press(screen,"建造 A");stage=6;return true;}
  if(stage==6&&mc.screen==null){var found=ClientRoads.INDEX.roads.values().stream().filter(v->{var k=LaneTopology.metadata(v.record).link();return k!=null&&source.equals(k.from().road());}).findFirst();if(found.isEmpty())return true;ramp=found.get().record.id();require(Math.abs(LaneTopology.metadata(found.get().record).link().targetOffset())<=88,"actual UI build stores a bounded landing offset");current=scene(ramp);camera(mc);server(mc,p->{var r=RoadData.get(p.serverLevel()).index.roads.get(ramp).record;click(p,SplineRoads.RAMP_CONNECTOR.get(),RoadStructures.sample(r.mesh(),r.mesh().length()/2).center());});stage=7;return true;}
  if(stage==7&&mc.screen instanceof LaneRampScreen screen){require(((CompoundTag)field(screen,"payload")).getUUID("Id").equals(ramp),"connector opens existing road ID");require(field(screen,"departure")==LanePoints.Departure.EXTRA&&field(screen,"arrival")==LanePoints.Arrival.EXTRA,"editor loads persisted expansion options");((EditBox)field(screen,"radius")).setValue("28");press(screen,"实景预览");stage=8;return true;}
  if(stage==8&&mc.screen==null&&ClientRoads.preview!=null){server(mc,p->air(p,false));stage=9;return true;}
  if(stage==9&&mc.screen instanceof LaneRampScreen screen){press(screen,"保存匝道");stage=10;return true;}
  if(stage==10&&mc.screen==null){var r=ClientRoads.INDEX.roads.get(ramp).record;if(LaneTopology.metadata(r).link().options().radius()!=28)return true;server(mc,p->{var road=RoadData.get(p.serverLevel()).index.roads.get(ramp).record;click(p,SplineRoads.CONNECTOR.get(),RoadStructures.sample(road.mesh(),road.mesh().length()/2).center());});stage=11;return true;}
  if(stage==11&&mc.screen instanceof RoadScreen screen){require(((CompoundTag)field(screen,"payload")).getString("Kind").equals("laneRampRoad"),"normal road editor supports stored ramp without NodeEntities");require(ClientRoads.preview!=null,"road editor uses editable alignment preview");press(screen,"+");editedWidth=((Number)field(screen,"roadWidth")).doubleValue();photo(mc,"lane-workflow-road-editor");press(screen,"建造 / 保存");stage=12;return true;}
  if(stage==12&&mc.screen==null){var r=ClientRoads.INDEX.roads.get(ramp).record;if(Math.abs(r.settings().width()-editedWidth)>1e-6)return true;require(LaneTopology.metadata(r).link().options().radius()==28,"both editors preserve each other's settings");server(mc,p->click(p,SplineRoads.RAMP_CONNECTOR.get(),RoadStructures.sample(RoadData.get(p.serverLevel()).index.roads.get(ramp).mesh,170).center()));stage=13;return true;}
  if(stage==13&&mc.screen instanceof LaneRampScreen screen){press(screen,"实景预览");stage=14;return true;}
  if(stage==14&&mc.screen==null&&ClientRoads.preview!=null){server(mc,p->air(p,true));stage=15;return true;}
  if(stage==15&&ClientRoads.preview==null){var saved=mc.getSingleplayerServer().submit(()->{var l=mc.getSingleplayerServer().overworld();var p=l.getServer().getPlayerList().getPlayers().get(0);require(!p.getMainHandItem().getTag().contains("LaneEdit")&&!p.getMainHandItem().getTag().contains("LanePreview"),"Shift right click clears edit and preview while UI hidden");return RoadData.get(l).save(new CompoundTag());}).join();NbtIo.writeCompressed(saved,OUT.resolve("lane-workflow-final.nbt").toFile());Files.writeString(OUT.resolve("lane-workflow-interactions.txt"),"Actual item selection -> real screen widgets -> server preview packet -> hidden world preview -> air-click resume -> close/resume -> build -> existing connector edit -> normal road editor width edit -> Shift-right-click clear all passed. Stable ramp ID; stored B offset and both expansion flags retained.\n");stage=20;current=scene(ramp);camera(mc);wait=45;return true;}
  return true;
 }
}
