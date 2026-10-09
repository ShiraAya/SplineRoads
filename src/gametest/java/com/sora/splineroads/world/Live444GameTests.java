package com.sora.splineroads.world;
import com.sora.splineroads.SplineRoads;
import com.sora.splineroads.core.*;
import com.sora.splineroads.core.RoadGeometry.*;
import com.sora.splineroads.core.RoadStructures.*;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.InteractionHand;
import net.minecraftforge.gametest.*;
import net.minecraftforge.common.util.FakePlayerFactory;
import java.util.*;
@GameTestHolder("splineroads_live444") @PrefixGameTestTemplate(false)
public final class Live444GameTests {
 static boolean covered(Part part,V p){
  if(Math.abs(part.a().y()-p.y())>.3)return false;
  var polygon=part.base();if(JunctionPaint.inside(polygon,p))return true;
  // A joint belongs to both closed rail polygons; the ray test alone omits
  // boundary points depending on floating-point roundoff at a curve sample.
  for(int i=0;i<polygon.size();i++){var a=polygon.get(i);var b=polygon.get((i+1)%polygon.size());var v=b.sub(a);
   double u=Math.max(0,Math.min(1,((p.x()-a.x())*v.x()+(p.z()-a.z())*v.z())/Math.max(1e-20,v.x()*v.x()+v.z()*v.z())));
   if(p.sub(a.add(v.mul(u))).horizontalLength()<1e-6)return true;
  }return false;
 }
 static RoadRecord road(V a,V b,Settings settings){
  var start=new Node(a,RoadPlanner.yaw(b.sub(a).horizontalUnit()),0);var end=new Node(b,start.yaw(),0);
  return new RoadRecord(UUID.randomUUID(),new UUID(444,1),RampJunctions.at(a),RampJunctions.at(b),start,end,settings,false,4);
 }
 static LanePoints.Ref point(Map<UUID,RoadRecord> all,RoadRecord r,double station,int slot){
  var p=LanePoints.point(UUID.randomUUID(),LanePoints.Origin.MANUAL,r.mesh(),station,slot);
  all.put(r.id(),r.withLanePoints(LaneTopology.metadata(r).points(List.of(p))));return LanePoints.Ref.lane(r.id(),p.id());
 }
 @GameTest(batch="splineroads_live444",template="empty",templateNamespace="splineroads_live444",timeoutTicks=12000)
 public static void actualForkAndExtraPerimeters(GameTestHelper h){
  for(boolean extra:new boolean[]{false,true}){
   int x=232000+(extra?1000:0),z=232000;var style=extra?Style.O3_ONE:Style.C1_RAMP;
   var opts=RoadProfile.Options.DEFAULT.outerRail(RoadProfile.OuterRail.ON).hideArrows(true);
   var settings=new Settings(Mode.STRAIGHT,style,RoadProfile.width(style,opts,4),1,.35,90).structure(Structure.BRIDGE).options(opts);
   var source=road(new V(x,200,z),new V(x,200,z+(extra?900:260)),settings);var target=road(new V(x+(extra?-140:80),200,z+(extra?950:180)),new V(x+(extra?-140:80),200,z+(extra?1700:540)),settings);
   var all=new LinkedHashMap<UUID,RoadRecord>();all.put(source.id(),source);all.put(target.id(),target);
   var a=point(all,source,extra?200:60,extra?2:0);var b=point(all,target,extra?250:220,extra?2:0);
   var options=new LanePoints.Options(LanePoints.Path.AUTO,LanePoints.Departure.BRANCH,extra?LanePoints.Arrival.EXTRA:LanePoints.Arrival.FLOW,24,32,LanePoints.Elevation.AUTO,LanePoints.Landing.EXACT);
   var ramp=LaneRamps.generate(null,all,UUID.randomUUID(),source.owner(),new LanePoints.Link(a,b,options,null));all.put(ramp.id(),ramp);LaneCrossSections.reconcile(all);
   var floor=new HashSet<BlockPos>();
   for(var r:all.values())for(var sample:r.mesh().samples())for(int dx=-3;dx<=3;dx++)for(int dz=-3;dz<=3;dz++)floor.add(BlockPos.containing(sample.center().x()+dx,188,sample.center().z()+dz));
   for(var p:floor){h.getLevel().getChunkAt(p);h.getLevel().setBlock(p,Blocks.STONE.defaultBlockState(),2);}
   var built=all.values().stream().map(RoadIndex.Built::new).toList();var parts=new ArrayList<Part>();
   for(var r:built)parts.addAll(StructurePlanner.plan(h.getLevel(),r,built,Map.of(),new HashMap<>()).structures());
   int checked=0;
   for(var r:built){var m=r.mesh;var others=built.stream().filter(o->o!=r).toList();
    var join=new RoadRailJoin(m,others.stream().map(o->new RoadRailJoin.Neighbor(o.mesh,RoadSurface.higherPriority(o.record.id(),o.mesh,r.record.id(),m))).toList());
    for(double d=2;d<m.length()-2;d+=1)for(int side:new int[]{-1,1}){
     if(LaneDeck.outerOpening(m,d,side))continue;
     var at=RoadStructures.sample(m,d);double inset=RoadRailJoin.inset(m,at,side);V p=at.at(side*(at.halfWidth()-inset),0),delta=at.left().left().mul(-.02);
     if(join.exposed(p.sub(delta),p.add(delta),p.add(at.left().mul(side)),inset).stream().noneMatch(span->span.a().sub(p).dot(delta)<=1e-9&&span.b().sub(p).dot(delta)>=-1e-9))continue;
     h.assertTrue(parts.stream().anyMatch(t->t.material()==Material.CONCRETE&&Math.abs(t.height()-.45)<1e-8&&covered(t,p)),"actual exposed perimeter has no rail extra="+extra+" at="+p);checked++;
    }
   }
   h.assertTrue(checked>1000,"insufficient perimeter fixture");
   System.out.println("LIVE444 REAL_SERVER PASS generated perimeter extra="+extra+" guarded_samples="+checked);
  }
  h.succeed();
 }
 @GameTest(batch="splineroads_live444",template="empty",templateNamespace="splineroads_live444",timeoutTicks=12000)
 public static void lateShellRejectionSelectsAnotherElevation(GameTestHelper h){
  var level=h.getLevel();var data=RoadData.get(level);var player=FakePlayerFactory.getMinecraft(level);player.setGameMode(net.minecraft.world.level.GameType.CREATIVE);
  int x=236000,z=236000;
  for(int xx=x-8;xx<=x+98;xx++)for(int zz=z;zz<=z+360;zz++){var p=new BlockPos(xx,198,zz);level.getChunkAt(p);level.setBlock(p,Blocks.GRASS_BLOCK.defaultBlockState(),2);}
  var settings=RoadLanes.configure(Revision32GameTests.road(Style.O1_ONE,Structure.AUTO),RoadProfile.Type.ORDINARY,new RoadLanes.Counts(1,0),4);
  settings=settings.options(settings.options().route(settings.options().routing().fit(false)));
  var source=data.connect(level,player,marker(h,player,x,200,z),marker(h,player,x,200,z+100),settings,null);
  var target=data.connect(level,player,marker(h,player,x+90,208,z+200),marker(h,player,x+90,208,z+360),settings,null);
  var a=Build429GameTests.point(data,source,40,0);var b=Build429GameTests.point(data,target,60,0);
  var options=new LanePoints.Options(LanePoints.Path.AUTO,LanePoints.Departure.BRANCH,LanePoints.Arrival.EXTRA,24,32,LanePoints.Elevation.AUTO,LanePoints.Landing.FLEXIBLE);
  var tool=new ItemStack(SplineRoads.RAMP_CONNECTOR.get());player.setItemInHand(InteractionHand.MAIN_HAND,tool);
  var command=new CompoundTag();command.put("From",LanePointCodec.ref(a));command.put("To",LanePointCodec.ref(b));command.put("Options",LanePointCodec.options(options));command.putLong("Request",444);
  tool.getOrCreateTag().put("LaneFrom",command.getCompound("From").copy());tool.getOrCreateTag().put("LaneTo",command.getCompound("To").copy());tool.getOrCreateTag().putString("LaneDimension",level.dimension().location().toString());
  var work=LaneRamps.preparePreview(player,tool,command);long revision=data.index.revision();int[] attempts={0};
  var reply=work.resolve(route->{
   if(attempts[0]++==0){
    // Deterministic late-stage fixture: the real tunnel validator rejects a newly
    // planned wall in the host lane. Retry then runs the full real transaction.
    V at=RoadStructures.sample(source.mesh(),40).center();var wall=new Part(at,at.add(new V(0,0,1)),.4,3,false,Material.TUNNEL);
    var tube=new RoadIndex.Built(route.road().settings(route.road().settings().structure(Structure.TUNNEL)).structures(List.of(wall)));
    TunnelShellValidation.check(List.of(tube,data.index.roads.get(source.id())),Set.of(tube.record.id()),Map.of());
    throw new AssertionError("real shell validator did not reject colliding wall");
   }
   return LaneRamps.finishPreview(player,tool,command,work,route);
  });
  h.assertTrue(attempts[0]>=2,"AUTO did not try next elevation");h.assertTrue(data.index.revision()==revision,"failed candidate wrote roads");
  var saved=RoadRecord.load(reply.getCompound("Road"));h.assertTrue(LaneTopology.metadata(saved).link().options().equals(options),"retry changed user's AUTO options");
  command.putUUID("Token",reply.getUUID("Token"));LaneRamps.build(player,tool,command);
  h.assertTrue(data.index.roads.containsKey(saved.id()),"retry result could not build");
  h.assertTrue(RoadRecord.load(saved.save()).alignment().equals(saved.alignment()),"selected elevation changed through NBT");
  System.out.println("LIVE444 REAL_SERVER PASS late shell rejection, AUTO alternate, full publication/build and Mojang NBT");h.succeed();
 }
 static BlockPos marker(GameTestHelper h,net.minecraft.server.level.ServerPlayer player,int x,int y,int z){var p=Revision32GameTests.marker(h,x,y,z,0);((NodeEntity)h.getLevel().getBlockEntity(p)).owner=player.getUUID();return p;}
}
