package com.sora.splineroads.world;

import com.sora.splineroads.SplineRoads;
import com.sora.splineroads.core.*;
import com.sora.splineroads.core.RoadGeometry.*;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;

/** Opt-in real Minecraft fixture. NOT included in the mod JAR and NOT claimed executed offline. */
final class Ramp39VisualScene {
  private static final double SCALE=.5,X=12000,Z=12000,Y=-60;
  private static V at(double x,double z){return new V(X+x*SCALE+.5,Y,Z+z*SCALE+.5);}
  private static BlockPos marker(ServerLevel level,V v,double yaw){
    var p=BlockPos.containing(v.x(),v.y(),v.z());level.getChunkAt(p);
    level.setBlock(p,SplineRoads.NODE.get().defaultBlockState(),3);
    var node=(NodeEntity)level.getBlockEntity(p);node.apply(new Node(v,yaw,0));node.heightExplicit=true;return p;
  }
  private static RoadRecord road(ServerLevel level,RoadData data,double x,double z,double x2,double z2,Style style){
    V a=at(x,z),b=at(x2,z2);double yaw=RoadPlanner.yaw(b.sub(a).horizontalUnit());
    var settings=new Settings(Mode.STRAIGHT,style,RoadProfile.width(style,RoadProfile.Options.DEFAULT,4),1,.35,90).structure(Structure.AUTO);
    return data.connect(level,null,marker(level,a,yaw),marker(level,b,yaw),settings,null);
  }
  private static LanePoints.Ref point(ServerLevel level,RoadData data,UUID id,double station,int lane){
    var r=data.index.roads.get(id).record;var p=LanePointTool.create(level,null,id,LanePoints.lane(r.mesh(),station,lane).position());return LanePoints.Ref.lane(id,p.id());
  }
  private static RoadRecord ramp(ServerLevel level,RoadData data,LanePoints.Ref a,LanePoints.Ref b,LanePoints.Departure d,LanePoints.Arrival ar,LanePoints.Elevation e){
    var settings=new LanePoints.Options(LanePoints.Path.AUTO,d,ar,ar==LanePoints.Arrival.REPLACE?16:48,20,e,LanePoints.Landing.EXACT);
    var generated=LaneRamps.generate(data,LaneTopology.records(data),UUID.randomUUID(),new UUID(0,0),new LanePoints.Link(a,b,settings,null));
    LaneRamps.build(data,level,null,generated);return data.index.roads.get(generated.id()).record;
  }
  static Visual38Suite.Scene build(ServerLevel level,RoadData data){
    // Ordinary road tools provide the upstream 3 -> 2 transition. Ramp ports remain lane-only.
    road(level,data,0,-300,0,0,Style.O3_ONE);
    var main=road(level,data,0,0,0,2000,Style.O2_ONE);
    var outII=road(level,data,-850,1050,-950,1050,Style.O1_ONE);
    var outI=road(level,data,700,1100,800,1100,Style.O1_ONE);
    var feeder=road(level,data,-950,500,-45,500,Style.O1_ONE);
    var a=point(level,data,main.id(),75,1);var b=point(level,data,outII.id(),10,0);
    // Place the future replacement point before departing the lane; the manual point survives.
    var targetIV=point(level,data,main.id(),750,1);
    var ii=ramp(level,data,a,b,LanePoints.Departure.DETACH,LanePoints.Arrival.MERGE,LanePoints.Elevation.OVER);
    var crest=ii.mesh().samples().stream().filter(s->s.center().y()>Y+5.099999).findFirst().orElseThrow(()->new AssertionError("II did not rise above feeder IV"));
    var branch=point(level,data,ii.id(),crest.distance(),0);
    var i=ramp(level,data,branch,point(level,data,outI.id(),10,0),LanePoints.Departure.BRANCH,LanePoints.Arrival.MERGE,LanePoints.Elevation.OVER);
    var iv=ramp(level,data,point(level,data,feeder.id(),440,0),targetIV,LanePoints.Departure.BRANCH,LanePoints.Arrival.REPLACE,LanePoints.Elevation.AUTO);
    var all=LaneTopology.records(data);for(var r:List.of(ii,i,iv))LaneRamps.validate(all.get(r.id()).mesh(),all,r.id(),LaneTopology.metadata(r).link());
    var host=all.get(main.id());Visual38Suite.require(!LaneSections.active(host.mesh(),400,1)&&LaneSections.active(host.mesh(),850,1),"departure and replacement affect real mainline cross-sections");
    for(var crossing:RoadClearance.contacts(i.mesh(),host.mesh()))Visual38Suite.require(!crossing.blocked()&&crossing.ours().y()>crossing.other().y(),"I above A");
    for(var crossing:RoadClearance.contacts(ii.mesh(),all.get(feeder.id()).mesh()))Visual38Suite.require(!crossing.blocked()&&crossing.ours().y()>crossing.other().y(),"II above IV");
    var saved=data.save(new CompoundTag());Visual38Suite.require(saved.getInt("Version")==34,"save uses lane-section format 34");
    var restored=RoadData.load(saved);Visual38Suite.require(!LaneTopology.needsRefresh(restored.index.roads.values()),"real NBT roundtrip retains lane ports");
    var dependencies=LaneTopology.dependents(data,Set.of(ii.id()));Visual38Suite.require(dependencies.contains(i.id())&&dependencies.contains(iv.id()),"deletion confirmation includes both branches and replacement");
    V center=at(-100,850),detail=crest.center();
    return new Visual38Suite.Scene(ii.id(),center,center.add(new V(70,550,250)),detail,detail.add(new V(70,130,60)));
  }
  private Ramp39VisualScene(){}
}
