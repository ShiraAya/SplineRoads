package com.sora.splineroads;
import com.sora.splineroads.core.*;
import com.sora.splineroads.core.RoadGeometry.*;
import com.sora.splineroads.core.RoadStructures.*;
import java.util.*;
public final class EndpointV2Validation {
 static void check(boolean ok,String why){if(!ok)throw new AssertionError(why);}
 static Settings settings(Style style){return new Settings(Mode.STRAIGHT,style,style.defaultWidth(),1,.4,90).structure(Structure.GROUND);}
 static Mesh straight(double a,double b,Settings s){return RoadGeometry.build(new Node(new V(a,64,0),-90,0),new Node(new V(b,64,0),-90,0),s);}
 public static void main(String[] args){
  var s=settings(Style.O4_YELLOW);var a=straight(0,100,s);var b=straight(100,200,settings(Style.H4_RAIL));
  check(RoadContinuity.compatible(a,b,new V(100,64,0)),"mixed ordinary/highway accepted with equal directional lanes");
  check(!RoadContinuity.compatible(a,straight(100,200,settings(Style.O2_YELLOW)),new V(100,64,0)),"different lane counts rejected");
  var hump=s.options(s.options().lift(.5,3));check(!RoadContinuity.eligible(straight(0,100,hump)),"interior height variation rejected despite equal endpoints");
  var slope=RoadGeometry.build(new Node(new V(100,64,0),-90,0),new Node(new V(200,66,0),-90,0),s);check(!RoadContinuity.eligible(slope),"constant slope rejected");
  for(Structure structure:List.of(Structure.AUTO,Structure.GROUND))check(RoadContinuity.eligible(straight(0,100,s.structure(structure))),"scope "+structure);
  for(var bridge:RoadInfrastructure.Bridge.values()){var ss=s.structure(Structure.BRIDGE).options(s.options().infrastructure(s.options().infrastructure().bridge(bridge)));check(RoadContinuity.eligible(straight(0,100,ss))==(bridge==RoadInfrastructure.Bridge.STANDARD||bridge==RoadInfrastructure.Bridge.BEAM||bridge==RoadInfrastructure.Bridge.OVERPASS),"bridge scope "+bridge);}
  var p1=new RoadAttachments.Point(UUID.randomUUID(),new V(30,64,0),new V(30,64,0),false);var p2=new RoadAttachments.Point(UUID.randomUUID(),new V(70,64,0),new V(70,64,0),false);
  var meta=new RoadAttachments.Data(List.of(p1,p2),List.of());s=s.options(s.options().attachments(meta));a=straight(0,100,s);check(a.samples().equals(straight(0,100,settings(Style.O4_YELLOW)).samples()),"creating markers does not change geometry");
  var r=RoadAttachments.range(a,50);check(r.start()==30&&r.end()==70,"point-delimited middle range");
  var paint=new RoadAttachments.Paint(List.of(new RoadLaneLines.Edit("divider:0",RoadLaneLines.Pattern.NONE,.12)),true);
  s=s.options(s.options().attachments(RoadAttachments.edit(a,r,paint)));a=straight(0,100,s);
  check(!RoadAttachments.paint(a,20).hideArrows()&&RoadAttachments.paint(a,50).hideArrows()&&!RoadAttachments.paint(a,80).hideArrows(),"paint edit does not leak");
  check(s.options().sidewalk(RoadSidewalks.Config.DEFAULT).lift(.5,0).cycleFinish(RoadProfile.Options.CycleFinish.GREEN).streetscape(RoadStreetscape.Config.DEFAULT).attachments().points().equals(meta.points()),"immutable setters preserve point identities");
  var curve=RoadPlanner.mode(s,Mode.CURVE,90);var moved=meta.points(List.of(p1.at(new V(30,65,4)),p2.at(new V(70,64,-3))));curve=curve.options(curve.options().attachments(moved));var mesh=straight(0,100,curve);
  for(var p:moved.points())check(mesh.samples().stream().anyMatch(sample->sample.center().distance(p.position())<1e-6),"curve interpolates every control point");
  var green=settings(Style.O4_YELLOW);green=green.options(green.options().cycleFinish(RoadProfile.Options.CycleFinish.GREEN));green=RoadTransitions.ends(green,RoadTransitions.Section.of(settings(Style.O4_YELLOW)),null);var taper=straight(0,100,green);
  for(var sample:taper.samples())if(RoadProfile.layout(taper,sample).cycleWidth()>.05)check(RoadTransitions.greenCycle(taper,sample),"green taper keeps green until width is zero");
  triangleWalls();System.out.println("EndpointV2 core PASS: continuity, scope, paint isolation, identity, control interpolation, green taper, sidewalk walls");
 }
 static void triangleWalls(){
  V a=new V(1000,64,0),b=new V(1010,64,0),c=new V(1010,64,10),d=new V(1000,64,10);
  var p=new Part(a.add(b).mul(.5),c,8,.4,false,Material.WALK_STONE_BRICKS).frames(a.sub(b).mul(.5),new V(0,0,0));
  var q=new Part(a.add(c).mul(.5),d,8,.4,false,Material.WALK_STONE_BRICKS).frames(a.sub(c).mul(.5),new V(0,0,0));
  for(boolean distant:new boolean[]{false,true})for(var f:RoadRenderMesh.structureFaces(List.of(p,q),distant)){
   if(Math.abs(RoadLighting.normal(f).y())>.5)continue;V mid=f.points().stream().reduce(new V(0,0,0),V::add).mul(1.0/f.points().size());check(!(Math.abs(mid.x()-1000-mid.z())<1e-6&&mid.z()>1&&mid.z()<9),"internal diagonal wall omitted at both LODs");
  }
 }
}
