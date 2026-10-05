package com.sora.splineroads.world;

import com.sora.splineroads.SplineRoads;
import com.sora.splineroads.client.ClientRoads;
import com.sora.splineroads.core.*;
import com.sora.splineroads.core.RoadGeometry.*;
import com.sora.splineroads.core.RoadStructures.*;
import java.util.*;
import java.nio.file.*;
import java.util.concurrent.CompletableFuture;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Difficulty;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Build, edit, delete, persist and photograph production geometry in a real integrated world. */
@Mod.EventBusSubscriber(modid=SplineRoads.ID,value=Dist.CLIENT)
public final class Visual38Suite {
 static boolean started,finished;static int ticks,index,view,wait,viewSince;
 static CompletableFuture<Scene> building;static Scene current;static final Path OUT=Path.of("visual038");
 static final List<String> CASES=List.of(System.getProperty("road.visual38Cases","clearance,oneway-junction,mixed-sidewalks,uneven-corners,support-crossing,edit-chain").split(","));
 record Scene(UUID road,V at,V eye,V detail,V detailEye){}
 static void require(boolean condition,String reason){if(!condition)throw new AssertionError(reason);}
 @SubscribeEvent public static void tick(TickEvent.ClientTickEvent event){
  if(!Boolean.getBoolean("road.visual38")||event.phase!=TickEvent.Phase.END||finished)return;
  var mc=Minecraft.getInstance();ticks++;
  try{
   if(!started&&mc.screen!=null&&mc.getOverlay()==null&&ticks>80){
    started=true;Files.createDirectories(OUT);mc.options.hideGui=true;mc.options.renderDistance().set(12);mc.options.simulationDistance().set(5);mc.options.enableVsync().set(false);mc.options.framerateLimit().set(30);mc.options.fov().set(75);mc.options.pauseOnLostFocus=false;mc.options.cloudStatus().set(net.minecraft.client.CloudStatus.OFF);
    String reload=System.getProperty("road.visual38Reload","");
    if(!reload.isEmpty())mc.createWorldOpenFlows().loadLevel(new TitleScreen(),reload);
    else mc.createWorldOpenFlows().createFreshLevel("sr-038-qa-"+System.currentTimeMillis(),new LevelSettings("SR 0.38 regression",GameType.CREATIVE,false,Difficulty.PEACEFUL,true,new GameRules(),WorldDataConfiguration.DEFAULT),new WorldOptions(38L,false,false),registry->registry.registryOrThrow(Registries.WORLD_PRESET).getOrThrow(WorldPresets.FLAT).createWorldDimensions());return;
   }
   if(mc.level==null||mc.player==null||mc.getSingleplayerServer()==null)return;
   if(building==null&&current==null){
    if(index>=CASES.size()){finished=true;Files.writeString(OUT.resolve("complete.txt"),String.join("\n",CASES));mc.stop();return;}
    String label=CASES.get(index);System.out.println("SR038 BUILD "+label);building=new CompletableFuture<>();
    mc.getSingleplayerServer().execute(()->{try{building.complete(build(mc.getSingleplayerServer().overworld(),label));}catch(Throwable e){building.completeExceptionally(e);}});return;
   }
   if(building!=null){if(!building.isDone())return;current=building.join();building=null;view=0;camera(mc);wait=60;return;}
   if(wait-->0)return;
   if(!ClientRoads.error.isEmpty())throw new IllegalStateException(ClientRoads.error);
   if(!ClientRoads.INDEX.roads.containsKey(current.road())||!ready()){wait=1;return;}
   if(interactionTick(mc))return;
   if(CASES.get(index).equals("editor")&&mc.screen==null){
    var road=ClientRoads.INDEX.roads.get(current.road()).record;var payload=new CompoundTag();payload.putString("Kind","road");payload.putUUID("Id",road.id());payload.putLong("A",road.a().asLong());payload.putLong("B",road.b().asLong());payload.put("StartNode",RoadRecord.writeNode(road.start()));payload.put("EndNode",RoadRecord.writeNode(road.end()));payload.put("Settings",RoadRecord.writeSettings(road.settings()));payload.putBoolean("ExtraPage",view==1);payload.putInt("OptionsPage",1);
    mc.setScreen(new com.sora.splineroads.client.RoadScreen(payload));require(ClientRoads.preview!=null,"ordinary road editor still previews");wait=30;return;
   }
   if(CASES.get(index).equals("endpoint-v2")&&view==1&&mc.screen==null){
    var r=ClientRoads.INDEX.roads.get(current.road()).record;var p=r.settings().options().attachments().points().get(0);var t=new CompoundTag();t.putString("Kind","attachedPoint");t.put("Road",r.header());t.putUUID("Id",r.id());t.putUUID("Point",p.id());t.putInt("Signature",GantryTool.signature(r));t.put("Position",RoadRecord.writeNode(new Node(p.position(),0,0)));t.putBoolean("XYZ",true);mc.setScreen(new com.sora.splineroads.client.AttachedPointScreen(t));wait=30;return;
   }
   if(CASES.get(index).equals("lane-point-ui")&&view==1&&mc.screen==null){var r=ClientRoads.INDEX.roads.get(current.road()).record;var p=LaneTopology.metadata(r).points().stream().filter(v->!v.automatic()).findFirst().orElseThrow();var t=new CompoundTag();t.putString("Kind","lanePoint");t.put("Road",r.header());t.putUUID("Id",r.id());t.putUUID("Point",p.id());t.putInt("Signature",r.header().hashCode());t.putBoolean("Supported",true);mc.setScreen(new com.sora.splineroads.client.LanePointScreen(t));wait=30;return;}
   if(CASES.get(index).equals("point-items")&&mc.screen==null){mc.options.hideGui=false;mc.setScreen(new net.minecraft.client.gui.screens.inventory.InventoryScreen(mc.player));wait=30;return;}
   if(CASES.get(index).equals("lane-ramp-ui")&&mc.screen==null){
    var link=LaneTopology.metadata(ClientRoads.INDEX.roads.get(current.road()).record).link();var payload=new CompoundTag();payload.put("From",LanePointCodec.ref(link.from()));payload.put("To",LanePointCodec.ref(link.to()));
    var screen=new com.sora.splineroads.client.LaneRampScreen(payload);mc.setScreen(screen);
    var buttons=screen.children().stream().filter(v->v instanceof net.minecraft.client.gui.components.Button).map(v->(net.minecraft.client.gui.components.Button)v).toList();
    require(buttons.get(0).getMessage().getString().equals("● 自动"),"connector UI default is auto");
    for(var choice:LanePoints.Path.values()){var button=screen.children().stream().filter(v->v instanceof net.minecraft.client.gui.components.Button b&&b.getMessage().getString().endsWith(choice.label)).map(v->(net.minecraft.client.gui.components.Button)v).findFirst().orElseThrow();button.onPress();require(field(screen,"path")==choice,"UI exposes each explicit path");}
    screen.children().stream().filter(v->v instanceof net.minecraft.client.gui.components.Button b&&b.getMessage().getString().endsWith("左转回环")).map(v->(net.minecraft.client.gui.components.Button)v).findFirst().orElseThrow().onPress();
    LaneRampWorkflowVisual.press(screen,"汇出：");LaneRampWorkflowVisual.press(screen,"汇出：");require(field(screen,"departure")==LanePoints.Departure.EXTRA&&field(screen,"arrival")==LanePoints.Arrival.MERGE,"departure and arrival choices are independent");
    var fields=screen.children().stream().filter(v->v instanceof net.minecraft.client.gui.components.EditBox).map(v->(net.minecraft.client.gui.components.EditBox)v).toList();fields.get(0).setValue("40");fields.get(1).setValue("48");screen.resize(mc,mc.getWindow().getGuiScaledWidth(),mc.getWindow().getGuiScaledHeight());
    var resized=screen.children().stream().filter(v->v instanceof net.minecraft.client.gui.components.Button).map(v->(net.minecraft.client.gui.components.Button)v).toList();require(field(screen,"path")==LanePoints.Path.LEFT_LOOP&&field(screen,"departure")==LanePoints.Departure.EXTRA&&field(screen,"arrival")==LanePoints.Arrival.MERGE,"screen resize preserves path and independent connection modes");
    var values=screen.children().stream().filter(v->v instanceof net.minecraft.client.gui.components.EditBox).map(v->((net.minecraft.client.gui.components.EditBox)v).getValue()).toList();require(values.equals(List.of("40","48")),"screen resize preserves numeric inputs");Files.writeString(OUT.resolve("lane-ramp-ui-controls.txt"),"All five path controls, independent connection modes and resize state passed using real screen widgets.");wait=30;return;
   }
   String name=CASES.get(index)+(view==0?"-overview.png":"-detail.png");
   try(var shot=Screenshot.takeScreenshot(mc.getMainRenderTarget())){shot.writeToFile(OUT.resolve(name));}System.out.println("SR038 SCREENSHOT "+name);
   if(CASES.get(index).equals("lane-point-map")&&view==1&&pointUiStage==2){var screen=(com.sora.splineroads.client.LanePointScreen)mc.screen;((net.minecraft.client.gui.components.Button)field(screen,"apply")).onPress();pointUiStage=3;wait=15;return;}
   if(++view<2){camera(mc);wait=45;return;}current=null;index++;
  }catch(Throwable e){finished=true;e.printStackTrace();try{Files.createDirectories(OUT);Files.writeString(OUT.resolve("failure.txt"),e.toString());}catch(Exception ignored){}mc.stop();}
 }
 static BlockPos marker(ServerLevel l,int x,double y,int z,double yaw){var p=new BlockPos(x,(int)Math.floor(y),z);l.getChunkAt(p);l.setBlock(p,SplineRoads.NODE.get().defaultBlockState(),3);var n=(NodeEntity)l.getBlockEntity(p);n.apply(new Node(new V(x+.5,y,z+.5),yaw,0));n.heightExplicit=true;return p;}
 static Settings settings(Style style,boolean walk,boolean cycle){
  var o=RoadProfile.Options.DEFAULT.sidewalk(new RoadSidewalks.Config(walk,RoadSidewalks.Side.BOTH,5,"minecraft:stone_bricks",true,true)).cycleFinish(cycle?RoadProfile.Options.CycleFinish.GREEN:RoadProfile.Options.CycleFinish.NONE).streetscape(RoadStreetscape.Config.DEFAULT.separator(RoadStreetscape.Separator.RAIL)).infrastructure(RoadInfrastructure.Config.DEFAULT.gantry(RoadInfrastructure.Gantry.OFF));
  return new Settings(Mode.STRAIGHT,style,RoadProfile.width(style,o,4),1,.4,90).structure(Structure.GROUND).options(o);
 }
 static Scene build(ServerLevel l,String label)throws Exception{
  l.setDayTime(6000);l.getGameRules().getRule(GameRules.RULE_DAYLIGHT).set(false,l.getServer());l.getGameRules().getRule(GameRules.RULE_WEATHER_CYCLE).set(false,l.getServer());
  require(SplineRoads.RAMP_CONNECTOR.get() instanceof LaneRampTool,"connector uses lane-point implementation");
  var data=RoadData.get(l);Scene scene=switch(label){
   case "clearance", "editor"->clearance(l,data);
   case "ramp39-compound"->Ramp39VisualScene.build(l,data);
   case "endpoint-v2"->endpoint(l,data);
   case "lane-direct","lane-left","lane-point-ui","point-items"->laneScene(l,data,label);
   case "lane-junction"->laneJunction(l,data);
   case "lane-hotfix"->laneHotfix(l,data);
   case "lane-response"->laneResponse(l,data);
   case "attached-relative"->AttachedRelativeVisual.build(l,data);
   case "ramp-crossing"->AttachedRelativeVisual.crossing(l,data);
   case "lane-workflow","lane-workflow-reload"->LaneRampWorkflowVisual.build(l,data,label.endsWith("reload"));
   case "lane-right-tight"->laneRightTight(l,data);
   case "lane-point-map"->lanePointMap(l,data);
   case "lane-hotfix-reload"->laneHotfixReload(l,data);
   case "lane-interaction-reload"->laneInteractionReload(l,data);
   case "lane-reload","lane-ramp-ui"->laneReload(l,data);
   case "green-v2"->green(l,data);
   case "oneway-junction"->oneway(l,data);
   case "mixed-sidewalks"->junction(l,data,500,true,false);
   case "uneven-corners"->junction(l,data,800,false,true);
   case "support-crossing"->supports(l,data);
   case "edit-chain"->chain(l,data);
   default->throw new IllegalArgumentException(label);
  };
  var saved=data.save(new CompoundTag());require(!saved.contains("OriginalPalette")&&!saved.contains("OriginalEntities"),"no obstacle restoration palette");
  var loaded=RoadData.load(saved);require(loaded.index.roads.keySet().equals(data.index.roads.keySet()),"roundtrip preserves all road IDs");
  NbtIo.writeCompressed(saved,OUT.resolve(label+"-roads.nbt").toFile());Files.writeString(OUT.resolve(label+"-passed.txt"),"Production world transaction, assertions, and save/load passed.");return scene;
 }
 // This fixture runs several player commands in one server task; simulate the normal five-tick input interval.
 static void advanceCommandClock(ServerLevel l){l.getServer().getWorldData().overworldData().setGameTime(l.getGameTime()+5);}
 static int responseStage,pointUiStage;static long clientPointRevision;static volatile long pointRequestStart;static UUID uiPoint;static int uiOriginalLane;
 static Object field(Object object,String name)throws Exception{var f=object.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(object);}
 static void rawPointClick(net.minecraft.server.level.ServerPlayer p,V at){var stack=new net.minecraft.world.item.ItemStack(SplineRoads.LANE_POINT.get());p.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND,stack);aim(p,at);var hit=new net.minecraft.world.phys.BlockHitResult(new net.minecraft.world.phys.Vec3(at.x(),at.y(),at.z()),net.minecraft.core.Direction.UP,BlockPos.containing(at.x(),at.y()-.05,at.z()),false);p.gameMode.useItemOn(p,p.serverLevel(),stack,net.minecraft.world.InteractionHand.MAIN_HAND,hit);}
 static long manualCount(RoadRecord r){return LaneTopology.metadata(r).points().stream().filter(p->!p.automatic()).count();}
 static Scene laneResponse(ServerLevel l,RoadData data)throws Exception{
  var player=l.getServer().getPlayerList().getPlayers().get(0);int x=6000;var a=marker(l,x,-60,0,0);var b=marker(l,x,-60,200,0);var r=data.connect(l,player,a,b,settings(Style.O6_RAIL,true,false),null);
  long oldStart=System.nanoTime();data.replaceAssembly(l,player,List.of(new RoadIndex.Built(r)),Set.of(r.id()),Set.of(a,b));double oldMs=(System.nanoTime()-oldStart)/1e6;
  r=data.index.roads.get(r.id()).record;var first=LanePoints.lane(r.mesh(),70,5).position();long start=System.nanoTime();rawPointClick(player,first);UUID token=LanePointTool.pendingToken(player);
  rawPointClick(player,LanePoints.lane(r.mesh(),90,5).position());rawPointClick(player,LanePoints.lane(r.mesh(),110,5).position());
  require(manualCount(data.index.roads.get(r.id()).record)==1,"queued clicks before UI acknowledgement do not create hidden extra points");LanePointTool.acknowledge(player,UUID.randomUUID());require(token.equals(LanePointTool.pendingToken(player)),"unrelated acknowledgement cannot release pending interaction");LanePointTool.acknowledge(player,token);
  rawPointClick(player,LanePoints.lane(r.mesh(),130,5).position());LanePointTool.acknowledge(player,LanePointTool.pendingToken(player));require(manualCount(data.index.roads.get(r.id()).record)==2,"acknowledged new click creates exactly one point");double pointMs=(System.nanoTime()-start)/1e6;
  var snapshot=data.index.roads.get(r.id());long revision=data.index.revision();start=System.nanoTime();for(int i=0;i<12;i++)LanePointTool.create(l,player,r.id(),LanePoints.lane(snapshot.mesh,10+i*12,2).position());double batchMs=(System.nanoTime()-start)/1e6;
  require(data.index.revision()==revision&&snapshot.mesh.samples()==data.index.roads.get(r.id()).mesh.samples(),"point batch does not rebuild or invalidate road geometry");
  Files.writeString(OUT.resolve("lane-response-timing.txt"),"Same six-lane 200-block host, same process. Legacy full replacement: "+oldMs+" ms; two acknowledged tool creations + ignored burst: "+pointMs+" ms; 12 metadata creations: "+batchMs+" ms.\n");
  responseStage=0;return new Scene(r.id(),first,first.add(new V(12,55,40)),first,first.add(new V(12,24,20)));
 }
 static Scene laneRightTight(ServerLevel l,RoadData data){
  var player=l.getServer().getPlayerList().getPlayers().get(0);int x=6800;
  var a=marker(l,x+140,-60,0,90);var b=marker(l,x,-60,0,90);var source=data.connect(l,player,a,b,settings(Style.O6_RAIL,true,false),null);
  var c=marker(l,x-140,-60,120,180);var d=marker(l,x-140,-60,-120,180);var target=data.connect(l,player,c,d,settings(Style.O6_RAIL,true,false),null);
  var pa=LaneTopology.metadata(data.index.roads.get(source.id()).record).points().stream().filter(p->p.origin()==LanePoints.Origin.AUTOMATIC_END&&p.lane()==5).findFirst().orElseThrow();
  var pb=LanePointTool.create(l,player,target.id(),LanePoints.lane(target.mesh(),160,5).position());
  var stack=new net.minecraft.world.item.ItemStack(SplineRoads.RAMP_CONNECTOR.get());var tool=(LaneRampTool)stack.getItem();player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND,stack);
  aim(player,pa.position());tool.use(l,player,net.minecraft.world.InteractionHand.MAIN_HAND);aim(player,pb.position());tool.use(l,player,net.minecraft.world.InteractionHand.MAIN_HAND);
  var command=new CompoundTag();command.putString("Action","laneRampPreview");command.put("From",stack.getTag().getCompound("LaneFrom").copy());command.put("To",stack.getTag().getCompound("LaneTo").copy());command.putLong("Request",1);
  for(var mode:List.of(LanePoints.Path.AUTO,LanePoints.Path.RIGHT)){command.put("Options",LanePointCodec.options(new LanePoints.Options(mode,false,false,24,32)));var reply=LaneRamps.preview(player,stack,command);require(reply.getString("ResolvedPath").equals("RIGHT"),"auto and manual resolve the pictured layout to genuine RIGHT");}
  var preview=stack.getTag().getCompound("LanePreview");UUID id=RoadRecord.load(preview.getCompound("Road")).id();command.putString("Action","laneRamp");command.putUUID("Token",preview.getUUID("Token"));advanceCommandClock(l);com.sora.splineroads.net.RoadNetwork.perform(player,command);var built=data.index.roads.get(id);require(built!=null&&!built.cells.isEmpty(),"actual right-turn ramp construction succeeds");
  V at=built.mesh.min().add(built.mesh.max()).mul(.5);return new Scene(id,at,at.add(new V(12,130,30)),at,at.add(new V(18,100,35)));
 }
 static Scene lanePointMap(ServerLevel l,RoadData data){
  var player=l.getServer().getPlayerList().getPlayers().get(0);var a=marker(l,7300,-60,0,0);var b=marker(l,7300,-60,140,0);var r=data.connect(l,player,a,b,settings(Style.O6_RAIL,true,false),null);
  var p=LanePointTool.create(l,player,r.id(),LanePoints.lane(r.mesh(),70,5).position());uiPoint=p.id();uiOriginalLane=p.lane();pointUiStage=0;
  return new Scene(r.id(),p.position(),p.position().add(new V(12,45,35)),p.position(),p.position().add(new V(12,25,20)));
 }
 static boolean interactionTick(Minecraft mc)throws Exception{
  String label=CASES.get(index);
  if(LaneRampWorkflowVisual.tick(mc)||AttachedRelativeVisual.tick(mc))return true;
  if(label.equals("lane-response")&&view==1){
   if(responseStage==0){clientPointRevision=ClientRoads.INDEX.revision();responseStage=1;UUID id=current.road();mc.getSingleplayerServer().execute(()->{var l=mc.getSingleplayerServer().overworld();var p=l.getServer().getPlayerList().getPlayers().get(0);var r=RoadData.get(l).index.roads.get(id).record;pointRequestStart=System.nanoTime();rawPointClick(p,LanePoints.lane(r.mesh(),180,5).position());});return true;}
   if(responseStage==1){if(!(mc.screen instanceof com.sora.splineroads.client.LanePointScreen)){if(pointRequestStart!=0&&System.nanoTime()-pointRequestStart>5_000_000_000L)throw new AssertionError("point UI response timed out");return true;}
    double millis=(System.nanoTime()-pointRequestStart)/1e6;require(millis<1500,"loaded road point UI responds under 1.5 seconds in this client");require(ClientRoads.INDEX.revision()==clientPointRevision,"small point delta does not invalidate client geometry");require(manualCount(ClientRoads.INDEX.roads.get(current.road()).record)==15,"all and only accepted points are visible before opening UI");Files.writeString(OUT.resolve("lane-response-roundtrip.txt"),"Server item click to actual client LanePointScreen: "+millis+" ms. 15 accepted points visible; rejected queued clicks did not reappear; client geometry revision unchanged.\n");responseStage=2;mc.setScreen(null);camera(mc);wait=30;return true;}
  }
  if(label.equals("lane-point-map")&&view==1){
   if(pointUiStage==0){var r=ClientRoads.INDEX.roads.get(current.road()).record;var p=LaneTopology.point(r,uiPoint);var t=new CompoundTag();t.putString("Kind","lanePoint");t.put("Road",r.header());t.putUUID("Id",r.id());t.putUUID("Point",p.id());t.putInt("Signature",r.header().hashCode());t.putBoolean("Supported",true);mc.setScreen(new com.sora.splineroads.client.LanePointScreen(t));pointUiStage=1;wait=30;return true;}
   if(pointUiStage==1){var screen=(com.sora.splineroads.client.LanePointScreen)mc.screen;var map=(LanePointPreview.Image)field(screen,"image");if(map==null)return true;int vx=(int)field(screen,"viewX"),vy=(int)field(screen,"viewY");var target=map.markers().get(0);screen.mouseClicked(vx+target.x(),vy+target.y(),0);require((int)field(screen,"lane")==0,"actual mouse click selects the opposite lane");screen.mouseClicked(vx+2,vy+map.height()-2,0);require((int)field(screen,"lane")==0,"non-motor region click cannot change selected lane");screen.mouseClicked(vx+target.x(),vy+target.y(),0);screen.resize(mc,mc.getWindow().getGuiScaledWidth(),mc.getWindow().getGuiScaledHeight());require((int)field(screen,"lane")==0,"resize preserves chosen lane");pointUiStage=2;wait=30;return true;}
   if(pointUiStage==3){if(mc.screen!=null)return true;var p=LaneTopology.point(ClientRoads.INDEX.roads.get(current.road()).record,uiPoint);require(p.lane()==0,"Apply persisted mouse-selected lane through actual packet");Files.writeString(OUT.resolve("lane-point-map-widgets.txt"),"Actual lane click, invalid-region click, resize and Apply packet passed. Point ID unchanged; selected lane=0 (opposite to original lane 5).\n");var saved=mc.getSingleplayerServer().submit(()->RoadData.get(mc.getSingleplayerServer().overworld()).save(new CompoundTag())).join();NbtIo.writeCompressed(saved,OUT.resolve("lane-interaction-final-roads.nbt").toFile());current=null;index++;return true;}
  }
  if(label.equals("lane-right-tight")&&view==1&&mc.screen==null){var link=LaneTopology.metadata(ClientRoads.INDEX.roads.get(current.road()).record).link();var t=new CompoundTag();t.put("From",LanePointCodec.ref(link.from()));t.put("To",LanePointCodec.ref(link.to()));var screen=new com.sora.splineroads.client.LaneRampScreen(t);mc.setScreen(screen);
   for(var choice:LanePoints.Path.values()){var button=screen.children().stream().filter(v->v instanceof net.minecraft.client.gui.components.Button b&&b.getMessage().getString().endsWith(choice.label)).map(v->(net.minecraft.client.gui.components.Button)v).findFirst().orElseThrow();button.onPress();require(field(screen,"path")==choice,"each explicit path button is selectable");}
   screen.children().stream().filter(v->v instanceof net.minecraft.client.gui.components.Button b&&b.getMessage().getString().endsWith("右转")).map(v->(net.minecraft.client.gui.components.Button)v).findFirst().orElseThrow().onPress();screen.resize(mc,mc.getWindow().getGuiScaledWidth(),mc.getWindow().getGuiScaledHeight());require(field(screen,"path")==LanePoints.Path.RIGHT,"right choice retained after resize");wait=30;return true;}
  return false;
 }

 static Scene laneHotfix(ServerLevel l,RoadData data){
  var player=l.getServer().getPlayerList().getPlayers().get(0);int x=4800;
  var a=marker(l,x,-60,0,0);var b=marker(l,x+100,-60,180,-90);
  var curved=new RoadRecord(UUID.randomUUID(),player.getUUID(),a,b,RoadData.requireNode(l,a,null).node(),RoadData.requireNode(l,b,null).node(),RoadPlanner.mode(settings(Style.O6_RAIL,false,false),Mode.CURVE,90),false,4).caps(3);
  var c=marker(l,x+260,-60,0,0);var e=marker(l,x+260,-60,120,0);
  var straight=new RoadRecord(UUID.randomUUID(),player.getUUID(),c,e,RoadData.requireNode(l,c,null).node(),RoadData.requireNode(l,e,null).node(),settings(Style.O6_RAIL,false,false),false,4).caps(3);
  var legacy=new CompoundTag();legacy.putInt("Version",30);var list=new ListTag();for(var r:List.of(curved,straight)){var t=r.save();t.getCompound("Settings").remove("LanePointsV2");list.add(t);}legacy.put("Roads",list);var loaded=RoadData.load(legacy);loaded.index.roads.values().forEach(r->data.index.put(RoadIndex.Built.loading(r.record)));
  player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND,new net.minecraft.world.item.ItemStack(SplineRoads.CONNECTOR.get()));
  for(var r:List.of(straight,curved)){var t=new CompoundTag();t.putString("Action","connect");t.putUUID("Id",r.id());t.putLong("A",r.a().asLong());t.putLong("B",r.b().asLong());t.put("Settings",RoadRecord.writeSettings(r.settings()));advanceCommandClock(l);com.sora.splineroads.net.RoadNetwork.perform(player,t);require(!data.index.roads.get(r.id()).cells.isEmpty(),"old road saved through real player network action");}
  for(int i=0;i<3;i++){var host=data.index.roads.get(straight.id()).record;var selected=LanePoints.lane(host.mesh(),25+i*30,2);click(player,SplineRoads.LANE_POINT.get(),selected.position().add(new V(i%2==0?1.3:-1.3,0,0)));}
  var host=data.index.roads.get(straight.id()).record;var manual=LaneTopology.metadata(host).points().stream().filter(p->!p.automatic()).toList();require(manual.size()==3,"real off-center clicks create three lane-center points");for(var p:manual)require(p.anchor()!=null&&p.position().distance(LanePoints.lane(host.mesh(),p).position())<1e-7,"all blue points sit on exact selected lane axis");
  var p=manual.get(1);click(player,SplineRoads.LANE_POINT.get(),p.position());require(LaneTopology.metadata(data.index.roads.get(host.id()).record).points().stream().filter(v->!v.automatic()).count()==3,"repeat click opens UI without duplication");
  var stack=new net.minecraft.world.item.ItemStack(SplineRoads.RAMP_CONNECTOR.get());var tool=(LaneRampTool)stack.getItem();player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND,stack);aim(player,p.position());tool.use(l,player,net.minecraft.world.InteractionHand.MAIN_HAND);require(stack.getOrCreateTag().contains("LaneFrom"),"normal right click selects A");tool.use(l,player,net.minecraft.world.InteractionHand.MAIN_HAND);require(stack.getTag().contains("LaneFrom"),"reclicking A no longer cancels selection");
  for(boolean block:new boolean[]{false,true}){
   var direction=LanePoints.lane(host.mesh(),p).direction();var target=manual.stream().filter(v->v.position().sub(p.position()).dot(direction)>20).findFirst().orElseThrow();aim(player,target.position());tool.use(l,player,net.minecraft.world.InteractionHand.MAIN_HAND);require(stack.getTag().contains("LaneTo"),"normal right click selects distinct B");
   var command=new CompoundTag();command.putString("Action","laneRampPreview");command.put("From",stack.getTag().getCompound("LaneFrom").copy());command.put("To",stack.getTag().getCompound("LaneTo").copy());command.put("Options",LanePointCodec.options(new LanePoints.Options(LanePoints.Path.DIRECT,false,false,24,32)));command.putLong("Request",1);advanceCommandClock(l);com.sora.splineroads.net.RoadNetwork.perform(player,command);require(stack.getTag().getCompound("LanePreview").hasUUID("Token"),"real server preview exists before cancellation");command.putUUID("Token",stack.getTag().getCompound("LanePreview").getUUID("Token"));var before=data.save(new CompoundTag());player.setShiftKeyDown(true);
   if(block){var hit=new net.minecraft.world.phys.BlockHitResult(new net.minecraft.world.phys.Vec3(p.position().x(),p.position().y(),p.position().z()),net.minecraft.core.Direction.UP,BlockPos.containing(p.position().x(),p.position().y()-.05,p.position().z()),false);tool.useOn(new net.minecraft.world.item.context.UseOnContext(player,net.minecraft.world.InteractionHand.MAIN_HAND,hit));}else{player.setXRot(-90);tool.use(l,player,net.minecraft.world.InteractionHand.MAIN_HAND);}
   require(!stack.getTag().contains("LaneFrom")&&!stack.getTag().contains("LaneTo")&&!stack.getTag().contains("LanePreview")&&!stack.getTag().contains("LaneDimension"),"Shift right click clears all state for both air and block entry points");boolean staleRejected=false;try{LaneRamps.build(player,stack,command);}catch(IllegalArgumentException expected){staleRejected=true;}require(staleRejected&&before.equals(data.save(new CompoundTag())),"canceled preview cannot build or modify the world");player.setShiftKeyDown(false);aim(player,p.position());tool.use(l,player,net.minecraft.world.InteractionHand.MAIN_HAND);require(stack.getTag().contains("LaneFrom"),"selection can restart after cancel");
  }
  player.setShiftKeyDown(true);tool.use(l,player,net.minecraft.world.InteractionHand.MAIN_HAND);player.setShiftKeyDown(false);V at=p.position();return new Scene(host.id(),at,at.add(new V(12,70,40)),at,at.add(new V(14,24,22)));
 }
 static Scene laneInteractionReload(ServerLevel l,RoadData data)throws Exception{
  var previous=RoadData.load(NbtIo.readCompressed(OUT.resolve("lane-interaction-final-roads.nbt").toFile()));require(previous.index.roads.keySet().equals(data.index.roads.keySet()),"all road IDs survive restart");
  for(var e:previous.index.roads.entrySet())require(LaneTopology.metadata(e.getValue().record).equals(LaneTopology.metadata(data.index.roads.get(e.getKey()).record)),"point-only updates and real right-turn references persist exactly");
  var host=data.index.roads.values().stream().filter(b->Math.abs(b.record.start().position().x()-7300.5)<1).findFirst().orElseThrow();var p=LaneTopology.metadata(host.record).points().stream().filter(v->!v.automatic()).findFirst().orElseThrow();require(p.lane()==0,"clicked opposite lane survives program restart");return new Scene(host.record.id(),p.position(),p.position().add(new V(12,45,35)),p.position(),p.position().add(new V(12,25,20)));
 }
 static Scene laneHotfixReload(ServerLevel l,RoadData data)throws Exception{
  var previous=RoadData.load(NbtIo.readCompressed(OUT.resolve("lane-point-ui-roads.nbt").toFile()));require(data.index.roads.keySet().equals(previous.index.roads.keySet()),"restart preserves all old road and ramp identities");
  for(var entry:previous.index.roads.entrySet()){var before=entry.getValue().record;var after=data.index.roads.get(entry.getKey()).record;require(LaneTopology.metadata(before).equals(LaneTopology.metadata(after)),"restart retains exact point IDs, anchors and ramp references");for(var p:LaneTopology.metadata(after).points())require(p.anchor()!=null&&p.position().distance(LanePoints.lane(after.mesh(),p).position())<1e-7,"restart retains exact lane center positions");}
  var host=data.index.roads.values().stream().filter(b->LaneTopology.metadata(b.record).points().stream().filter(p->!p.automatic()).count()==3).findFirst().orElseThrow();require(!host.cells.isEmpty(),"old road collision rebuilt on restart");var p=LaneTopology.metadata(host.record).points().stream().filter(v->!v.automatic()).toList().get(1);V at=p.position();return new Scene(host.record.id(),at,at.add(new V(12,70,40)),at,at.add(new V(14,24,22)));
 }
 static Scene laneReload(ServerLevel l,RoadData data)throws Exception{
  var previous=RoadData.load(NbtIo.readCompressed(OUT.resolve("point-items-roads.nbt").toFile()));
  require(data.index.roads.keySet().equals(previous.index.roads.keySet()),"program restart retains every road identity");
  for(var entry:previous.index.roads.entrySet()){
   var before=entry.getValue().record;var after=data.index.roads.get(entry.getKey()).record;
   require(LaneTopology.metadata(before).equals(LaneTopology.metadata(after)),"program restart retains exact point origins, lanes, positions, IDs and link options");
   require(before.settings().style()==after.settings().style()&&Objects.equals(before.assembly(),after.assembly()),"program restart retains connector road identity");
   for(var point:LaneTopology.metadata(after).points())require(LanePoints.lane(before.mesh(),point).direction().distance(LanePoints.lane(after.mesh(),point).direction())<1e-6,"program restart retains lane direction");
   var link=LaneTopology.metadata(after).link();if(link!=null){
    require(LaneTopology.metadata(data.index.roads.get(link.from().road()).record).points().stream().anyMatch(p->p.id().equals(link.from().point())),"source reference survives program restart");
    if(link.to().road()!=null)require(LaneTopology.metadata(data.index.roads.get(link.to().road()).record).points().stream().anyMatch(p->p.id().equals(link.to().point())),"target reference survives program restart");
    require(!data.index.roads.get(after.id()).cells.isEmpty(),"collision cells rebuilt after program restart");
   }
  }
  var built=data.index.roads.values().stream().filter(b->{var link=LaneTopology.metadata(b.record).link();return link!=null&&link.options().path()==LanePoints.Path.LEFT_LOOP;}).findFirst().orElseThrow();
  require(built.record.structures().stream().noneMatch(p->RoadInteractions.selfSupportBlocked(p,built.mesh)),"loop support remains clear after restart");
  V at=built.mesh.min().add(built.mesh.max()).mul(.5),detail=built.mesh.samples().get(built.mesh.samples().size()/2).center();
  return new Scene(built.record.id(),at,at.add(new V(5,130,25)),detail,detail.add(new V(18,38,32)));
 }
 static Scene endpoint(ServerLevel l,RoadData data){
  int x=1200,z=0;var a=marker(l,x,-60,z,-90);var b=marker(l,x+100,-60,z,-90);var s=RoadPlanner.mode(settings(Style.O4_YELLOW,true,false),Mode.CURVE,90);var r=data.connect(l,null,a,b,s,null);
  var p=new RoadAttachments.Point(UUID.randomUUID(),new V(x+30.5,-60,z+.5),new V(x+30.5,-60,z+.5),false);var q=new RoadAttachments.Point(UUID.randomUUID(),new V(x+70.5,-60,z+.5),new V(x+70.5,-60,z+.5),false);data.updatePointMetadata(l,null,r,new RoadAttachments.Data(List.of(p,q),List.of()));r=data.index.roads.get(r.id()).record;
  var t=new CompoundTag();t.putUUID("Id",r.id());t.putUUID("Point",p.id());t.putInt("Signature",GantryTool.signature(r));t.put("Position",RoadRecord.writeNode(new Node(new V(x+30.5,-58,z+4.5),0,0)));AttachedPointTool.edit(l,null,t);
  r=data.index.roads.get(r.id()).record;t=new CompoundTag();t.putUUID("Id",r.id());t.putInt("Signature",GantryTool.signature(r));t.putDouble("Station",50);t.putString("Key","divider:0");t.putString("Pattern","YELLOW_SOLID");t.putDouble("Width",.3);data.editLaneLine(l,null,t);
  require(data.index.roads.get(r.id()).record.settings().options().attachments().points().size()==2,"two points remain on one road");V at=new V(x+50,-59,z);return new Scene(r.id(),at,at.add(new V(10,50,62)),new V(x+30.5,-58,z+4.5),new V(x+40,-42,z+22));
 }
 static void aim(net.minecraft.server.level.ServerPlayer p,V at){p.setGameMode(GameType.CREATIVE);p.teleportTo(p.serverLevel(),at.x(),at.y()+3,at.z(),0,90);p.setXRot(90);p.setYRot(0);}
 static void click(net.minecraft.server.level.ServerPlayer p,Item item,V at){var stack=new net.minecraft.world.item.ItemStack(item);p.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND,stack);aim(p,at);var hit=new net.minecraft.world.phys.BlockHitResult(new net.minecraft.world.phys.Vec3(at.x(),at.y(),at.z()),net.minecraft.core.Direction.UP,BlockPos.containing(at.x(),at.y()-.05,at.z()),false);p.gameMode.useItemOn(p,p.serverLevel(),stack,net.minecraft.world.InteractionHand.MAIN_HAND,hit);LanePointTool.acknowledge(p,LanePointTool.pendingToken(p));}
 static Scene laneScene(ServerLevel l,RoadData data,String label){
  int x=label.equals("lane-left")?2600:label.equals("lane-point-ui")?3200:label.equals("point-items")?3600:2000;var player=l.getServer().getPlayerList().getPlayers().get(0);var a=marker(l,x,-60,80,180);var b=marker(l,x,-60,0,180);var host=data.connect(l,player,a,b,settings(Style.O1_ONE,false,false),null);
  if(label.equals("lane-point-ui")||label.equals("point-items")){
    var lane=LanePoints.lane(host.mesh(),40,0);click(player,SplineRoads.LANE_POINT.get(),lane.position());host=data.index.roads.get(host.id()).record;require(LaneTopology.metadata(host).points().stream().anyMatch(v->!v.automatic()),"actual lane tool creates a manual point");click(player,SplineRoads.LANE_POINT.get(),lane.position());require(LaneTopology.metadata(data.index.roads.get(host.id()).record).points().stream().filter(v->!v.automatic()).count()==1,"second click opens UI without duplicate point");
    player.getInventory().setItem(1,new net.minecraft.world.item.ItemStack(SplineRoads.ATTACHED_POINT.get()));player.getInventory().setItem(2,new net.minecraft.world.item.ItemStack(SplineRoads.LANE_POINT.get()));player.getInventory().setItem(3,new net.minecraft.world.item.ItemStack(SplineRoads.RAMP_CONNECTOR.get()));player.getInventory().setItem(4,new net.minecraft.world.item.ItemStack(SplineRoads.LANE_LINES.get()));
    return new Scene(host.id(),lane.position(),lane.position().add(new V(12,15,17)),lane.position(),lane.position().add(new V(8,10,12)));
  }
  boolean loop=label.equals("lane-left");var c=marker(l,loop?x-130:x+24,loop?-60:-54,-140,loop?90:180);var d=marker(l,loop?x-230:x+24,loop?-60:-54,loop?-140:-240,loop?90:180);var target=data.connect(l,player,c,d,settings(Style.O1_ONE,false,false),null);
  var pa=LaneTopology.metadata(data.index.roads.get(host.id()).record).points().stream().filter(v->v.origin()==LanePoints.Origin.AUTOMATIC_END).findFirst().orElseThrow();var pb=LaneTopology.metadata(data.index.roads.get(target.id()).record).points().stream().filter(v->v.origin()==LanePoints.Origin.AUTOMATIC_START).findFirst().orElseThrow();var stack=new net.minecraft.world.item.ItemStack(SplineRoads.RAMP_CONNECTOR.get());player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND,stack);
  aim(player,pa.position());((LaneRampTool)stack.getItem()).use(l,player,net.minecraft.world.InteractionHand.MAIN_HAND);require(stack.getOrCreateTag().contains("LaneFrom"),"actual connector selects source A first");aim(player,pb.position());((LaneRampTool)stack.getItem()).use(l,player,net.minecraft.world.InteractionHand.MAIN_HAND);require(stack.getTag().contains("LaneTo"),"actual connector selects target B");
  var command=new CompoundTag();command.putString("Action","laneRampPreview");command.put("From",stack.getTag().getCompound("LaneFrom").copy());command.put("To",stack.getTag().getCompound("LaneTo").copy());command.put("Options",LanePointCodec.options(new LanePoints.Options(loop?LanePoints.Path.LEFT_LOOP:LanePoints.Path.DIRECT,false,false,24,32)));command.putLong("Request",1);com.sora.splineroads.net.RoadNetwork.perform(player,command);var preview=stack.getTag().getCompound("LanePreview");require(preview.hasUUID("Token"),"server checked actual geometry");UUID ramp=RoadRecord.load(preview.getCompound("Road")).id();command.putString("Action","laneRamp");command.putUUID("Token",preview.getUUID("Token"));com.sora.splineroads.net.RoadNetwork.perform(player,command);require(data.index.roads.containsKey(ramp),"real player authoritative build succeeded");require(!data.index.roads.get(ramp).cells.isEmpty(),"real collision cells installed");
  var path=data.index.roads.get(ramp).mesh;V at=path.min().add(path.max()).mul(.5);return new Scene(ramp,at,at.add(new V(loop?5:30,loop?130:95,loop?25:60)),loop?path.samples().get(path.samples().size()/2).center():pa.position(),loop?path.samples().get(path.samples().size()/2).center().add(new V(18,38,32)):pa.position().add(new V(12,16,15)));
 }
 static Scene laneJunction(ServerLevel l,RoadData data){int x=4100;var center=marker(l,x,-60,0,0);var spec=new JunctionSpec(new V(x+.5,-60,.5),JunctionSpec.Kind.ROUNDABOUT,false,6,12,1,4,1,JunctionSpec.Control.YIELD,20,3,1,0,true,true,List.of());AutoJunctions.createRing(l,null,center,spec);UUID group=data.junctions.keySet().stream().filter(id->JunctionCodec.read(data.junctions.get(id).getCompound("Spec")).center().equals(spec.center())).findFirst().orElseThrow();var a=marker(l,x-20,-60,250,180);var b=marker(l,x-20,-60,150,180);var host=data.connect(l,null,a,b,settings(Style.O1_ONE,false,false),null);var p=LanePointTool.create(l,null,host.id(),LanePoints.lane(host.mesh(),50,0).position());V mouth=RampJunctions.mouth(data,group,p.position(),host.settings());var link=new LanePoints.Link(LanePoints.Ref.lane(host.id(),p.id()),LanePoints.Ref.junction(group),LanePoints.Options.DEFAULT,mouth);var ramp=LaneRamps.generate(data,LaneTopology.records(data),UUID.randomUUID(),new UUID(0,0),link);LaneRamps.build(data,l,null,ramp);require(JunctionCodec.read(data.junctions.get(group).getCompound("Spec")).arms().size()==1,"roundabout receives real arm");return new Scene(ramp.id(),new V(x,-60,85),new V(x+100,85,170),mouth,mouth.add(new V(25,38,40)));}
 static Scene green(ServerLevel l,RoadData data){
  int x=1450,z=0;var a=marker(l,x,-60,z,-90);var b=marker(l,x+90,-60,z,-90);var c=marker(l,x+180,-60,z,-90);var green=settings(Style.O4_YELLOW,true,true);var asphalt=settings(Style.O6_YELLOW,true,false);
  var r=data.connect(l,null,a,b,green,null);data.connect(l,null,b,c,asphalt,null);V at=new V(x+90,-60,z);return new Scene(r.id(),at,at.add(new V(-25,45,48)),new V(x+80,-60,z+8),new V(x+69,-52,z+24));
 }
 static Scene clearance(ServerLevel l,RoadData data){
  var a=marker(l,-40,-60,0,-90);var b=marker(l,40,-60,0,-90);var s=settings(Style.O2_ONE,true,false);
  var deck=new BlockPos(0,-58,0);var walk=new BlockPos(0,-57,7);l.setBlock(deck,Blocks.OAK_LOG.defaultBlockState(),3);l.setBlock(walk,Blocks.STONE.defaultBlockState(),3);
  var road=data.connect(l,null,a,b,s,null);require(l.getBlockState(deck).isAir(),"road headroom cleared");require(l.getBlockState(walk).isAir(),"sidewalk headroom cleared");
  data.remove(l,null,road.id());require(l.getBlockState(deck).isAir()&&l.getBlockState(walk).isAir(),"delete never restores cleared obstacles");
  road=data.connect(l,null,a,b,s,null);var at=new V(0,-60,0);return new Scene(road.id(),at,at.add(new V(22,40,42)),new V(0,-59,7),new V(8,-53,16));
 }
 static List<RoadRecord> install(ServerLevel l,RoadData data,JunctionSpec spec,List<BlockPos> ports,BlockPos center){
  UUID id=UUID.randomUUID(),owner=new UUID(0,0);var plan=JunctionPlanner.plan(spec);var roads=new ArrayList<RoadRecord>();var built=new ArrayList<RoadIndex.Built>();
  for(int i=0;i<plan.pieces().size();i++){var piece=plan.pieces().get(i);var m=piece.mesh();var r=new RoadRecord(UUID.randomUUID(),owner,piece.arm()>=0?ports.get(piece.arm()):center,center,new Node(m.first().center(),0,0),new Node(m.last().center(),0,0),m.settings(),false,4).junction(id,new JunctionPlanner.Ref(spec,i));roads.add(r);built.add(new RoadIndex.Built(r));}
  var selected=new HashSet<>(ports);selected.add(center);data.replaceAssembly(l,null,built,Set.of(),selected);
  var tag=new CompoundTag();tag.putUUID("Id",id);tag.putUUID("Owner",owner);tag.put("Spec",JunctionCodec.write(spec));long[] points=new long[ports.size()+1];points[0]=center.asLong();for(int i=0;i<ports.size();i++)points[i+1]=ports.get(i).asLong();tag.putLongArray("Points",points);data.junctions.put(id,tag);return roads;
 }
 static Scene oneway(ServerLevel l,RoadData data){
  int x=250;var center=marker(l,x,-60,0,0);var ports=new ArrayList<BlockPos>();var arms=new ArrayList<JunctionSpec.Arm>();int[][] offsets={{70,0},{-70,0},{0,-70}};
  for(int i=0;i<3;i++){V outward=new V(offsets[i][0],0,offsets[i][1]).horizontalUnit(),in=outward.mul(-1);var p=marker(l,x+offsets[i][0],-60,offsets[i][1],RoadPlanner.yaw(in));ports.add(p);var s=settings(Style.O2_ONE,true,false);
   if(i==0)s=s.options(s.options().ends(new RoadTransitions.Ends(RoadTransitions.Section.of(settings(Style.O4_YELLOW,false,true)),null)));
   arms.add(JunctionSpec.arm(((NodeEntity)l.getBlockEntity(p)).node(),in,s,i!=0,i).attached(true));
  }
  var spec=new JunctionSpec(new V(x+.5,-60,.5),JunctionSpec.Kind.INTERSECTION,false,4,12,1,4,1,JunctionSpec.Control.NONE,20,3,1,0,true,true,arms);
  var roads=install(l,data,spec,ports,center);var section=data.endpointSettings(ports.get(0),null);
  require(section.style()==Style.O2_ONE&&!section.options().cycle(),"port uses selected one-way arm, not remote two-way cycle taper");
  var end=marker(l,x+130,-60,0,-90);var link=data.connect(l,null,ports.get(0),end,settings(Style.O2_ONE,true,false),null);
  require(!link.settings().options().cycle(),"no phantom cycle transition");data.remove(l,null,link.id());require(!data.index.roads.containsKey(link.id()),"one-way road deletion succeeds");
  link=data.connect(l,null,ports.get(0),end,settings(Style.O2_ONE,true,false),null);
  V at=spec.center();return new Scene(link.id(),at,at.add(new V(18,90,80)),new V(x+72,-60,.5),new V(x+95,-40,30));
 }
 static Scene junction(ServerLevel l,RoadData data,int x,boolean mixed,boolean heights){
  var center=marker(l,x,-60,0,0);var ports=new ArrayList<BlockPos>();var arms=new ArrayList<JunctionSpec.Arm>();double[] angles={0,38,180,265};
  for(int i=0;i<angles.length;i++){double angle=Math.toRadians(angles[i]);V outward=new V(Math.cos(angle),0,Math.sin(angle)),in=outward.mul(-1);var p=marker(l,x+(int)Math.round(outward.x()*80),-60+(heights&&i==1?.8:0),(int)Math.round(outward.z()*80),RoadPlanner.yaw(in));ports.add(p);
   var s=settings(Style.O4_YELLOW,!mixed||i!=1,true);arms.add(JunctionSpec.arm(((NodeEntity)l.getBlockEntity(p)).node(),in,s,true,i));}
  var spec=new JunctionSpec(new V(x+.5,-60,.5),JunctionSpec.Kind.INTERSECTION,false,4,12,1,4,1,JunctionSpec.Control.NONE,20,3,1,0,true,true,arms);var roads=install(l,data,spec,ports,center);
  V at=spec.center();return new Scene(roads.get(0).id(),at,at.add(new V(0,90,35)),at.add(new V(40,0,14)),at.add(new V(54,15,30)));
 }
 static Scene supports(ServerLevel l,RoadData data){
  int x=1200;var a=marker(l,x-65,-44,0,-90);var b=marker(l,x+65,-44,0,-90);var s=new Settings(Mode.STRAIGHT,Style.R1,5,1,.4,90).structure(Structure.AUTO);
  var record=new RoadRecord(UUID.randomUUID(),new UUID(0,0),a,b,((NodeEntity)l.getBlockEntity(a)).node(),((NodeEntity)l.getBlockEntity(b)).node(),s,false,4);
  data.replaceAssembly(l,null,List.of(new RoadIndex.Built(record)),Set.of(),Set.of(a,b));var before=data.index.roads.get(record.id());
  var pier=before.record.structures().stream().filter(p->p.pier()&&p.material()==Material.CONCRETE&&p.height()>2).min(Comparator.comparingDouble(p->Math.abs(p.a().x()-x))).orElseThrow(()->new AssertionError("existing elevated ramp has piers"));
  int crossing=(int)Math.round(pier.a().x());var c=marker(l,crossing,-60,-60,0);var d=marker(l,crossing,-60,60,0);var lower=data.connect(l,null,c,d,settings(Style.O4_YELLOW,false,false),null);var lowerMesh=data.index.roads.get(lower.id()).mesh;
  require(data.index.roads.get(record.id()).record.structures().stream().filter(p->p.pier()&&p.material()==Material.CONCRETE).noneMatch(p->RoadInteractions.invades(p,lowerMesh)),"existing ramp piers move or disappear from new carriageway");
  V at=new V(crossing,-55,0);return new Scene(record.id(),at,at.add(new V(40,40,55)),at,at.add(new V(20,6,30)));
 }
 static Scene chain(ServerLevel l,RoadData data){
  int z=350;var west=marker(l,0,-60,z,-90);var a=marker(l,100,-60,z,-90);var b=marker(l,300,-60,z,-90);var east=marker(l,400,-60,z,-90);var n=marker(l,100,-60,z-80,0);var south=marker(l,300,-60,z+80,0);
  var s=settings(Style.O4_YELLOW,true,false);var left=data.connect(l,null,west,a,s,null);var middle=data.connect(l,null,a,b,s,null);data.connect(l,null,a,n,settings(Style.O2_ONE,true,false),null);data.connect(l,null,b,east,s,null);data.connect(l,null,b,south,settings(Style.O2_ONE,true,false),null);
  var before=data.index.roads.get(middle.id()).mesh.last().center();left=data.connect(l,null,west,a,s.options(s.options().streetscape(s.options().streetscape().lampSpacing(32))),left.id());
  require(data.index.roads.get(middle.id()).mesh.last().center().distance(before)<.001,"editing near junction preserves far trim");
  var branch=data.streets.values().stream().filter(r->r.a().equals(a)&&r.b().equals(n)).findFirst().orElseThrow();data.remove(l,null,branch.id());require(!data.index.roads.containsKey(branch.id()),"delete auto one-way branch independently");
  V at=new V(200,-60,z);return new Scene(middle.id(),at,at.add(new V(0,140,120)),new V(100,-60,z),new V(135,-24,z+42));
 }
 static Object field(Class<?> c,Object owner,String name)throws Exception{var f=c.getDeclaredField(name);f.setAccessible(true);return f.get(owner);}
 static boolean ready()throws Exception{
  var mc=Minecraft.getInstance();if(ticks-viewSince<240&&!mc.levelRenderer.hasRenderedAllChunks())return false;
  Class<?> c=com.sora.splineroads.client.RoadRenderer.class;
  if(!((Map<?,?>)field(c,null,"jobs")).isEmpty()||!((Set<?>)field(c,null,"dirty")).isEmpty())return false;
  if(field(c,null,"pendingUpload")!=null)return false;
  for(Object section:(List<?>)field(c,null,"visible"))if((Boolean)field(section.getClass(),section,"dirty"))return false;
  return true;
 }
 static void camera(Minecraft mc){
  viewSince=ticks;mc.setScreen(null);V at=view==0?current.at():current.detail(),eye=view==0?current.eye():current.detailEye();
  V direction=at.sub(eye);float yaw=(float)RoadPlanner.yaw(direction),pitch=(float)-Math.toDegrees(Math.atan2(direction.y(),direction.horizontalLength()));
  mc.player.getAbilities().flying=true;mc.player.noPhysics=true;mc.player.setPos(eye.x(),eye.y(),eye.z());mc.player.setYRot(yaw);mc.player.setXRot(pitch);mc.player.yRotO=yaw;mc.player.xRotO=pitch;
  mc.getSingleplayerServer().execute(()->{var p=mc.getSingleplayerServer().getPlayerList().getPlayer(mc.player.getUUID());if(p!=null){p.setGameMode(GameType.CREATIVE);if(CASES.get(index).startsWith("lane")&&!CASES.get(index).startsWith("lane-workflow"))p.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND,new net.minecraft.world.item.ItemStack(SplineRoads.LANE_POINT.get()));else if(CASES.get(index).equals("endpoint-v2"))p.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND,new net.minecraft.world.item.ItemStack(SplineRoads.ATTACHED_POINT.get()));p.getAbilities().flying=true;p.onUpdateAbilities();p.teleportTo(p.serverLevel(),eye.x(),eye.y(),eye.z(),yaw,pitch);}});
 }
}
