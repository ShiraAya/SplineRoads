package com.sora.splineroads;
import com.sora.splineroads.core.*;
import com.sora.splineroads.core.RoadGeometry.*;
import com.sora.splineroads.core.RoadStructures.*;
import com.sora.splineroads.world.*;
import java.util.*;
import net.minecraft.nbt.*;
import net.minecraft.core.BlockPos;
public final class Revision33Validation {
 static int checks;static void check(boolean b,String s){checks++;if(!b)throw new AssertionError(s);}
 static Settings settings(Style style){return Revision32Validation.settings(style);}
 static void polygon(StringBuilder out,List<V> points,String color){out.append("<polygon fill=\"").append(color).append("\" points=\"");for(var v:points)out.append(v.x()).append(',').append(v.z()).append(' ');out.append("\"/>");}
 static Mesh mesh(Settings s,double yaw){V f=new Node(new V(0,0,0),yaw,0).direction();return RoadGeometry.build(new Node(new V(0,2,0),yaw,0),new Node(f.mul(80).add(new V(0,2,0)),yaw,0),s);}
 public static void main(String[] args)throws Exception{
  var walk=new RoadSidewalks.Config(true,RoadSidewalks.Side.BOTH,5,"minecraft:stone_bricks");
  var s=settings(Style.O4_YELLOW).options(settings(Style.O4_YELLOW).options().sidewalk(walk));
  for(boolean on:List.of(false,true)){
   var t=s.options(s.options().sidewalk(walk.tactile(on)));check(RoadRecord.readSettings(RoadRecord.writeSettings(t)).equals(t),"tactile toggle persists");
   check(RoadSidewalks.parts(mesh(t,-90),t.options().sidewalk()).stream().anyMatch(p->p.material()==Material.TACTILE)==on,"toggle controls all tactile pieces");
  }
  var off=s.options(s.options().sidewalk(walk.tactile(false)));var section=RoadTransitions.common(s,off);var joined=RoadTransitions.ends(s,null,section);var jm=mesh(joined,-90);
  check(RoadSidewalks.tactileScale(jm,walk,jm.last(),1)==0,"enabled tactile closes before disabled seam");check(RoadRecord.readSettings(RoadRecord.writeSettings(joined)).equals(joined),"transition toggle persists");
  for(boolean left:List.of(false,true)){
   var main=s.options(s.options().traffic(left).extras(true,true,true));var one=settings(Style.O2_ONE).options(settings(Style.O2_ONE).options().traffic(left).sidewalk(walk).extras(false,false,false));int sign=left?-1:1;
   var plan=YJunctionPlanner.plan(new Node(new V(0,2,0),180,0),new Node(new V(sign*28,2,-100),180,0),new Node(new V(-sign*28,2,-100),0,0),main,one,one,.4);
   var out=plan.outbound();var in=plan.inbound();
   check(Math.abs(RoadProfile.layout(out,out.first()).cycleWidth()-2.5)<1e-8,"Y cycle begins at stem width");check(RoadProfile.layout(out,out.last()).cycleWidth()==0,"Y cycle fades to branch setting");
   check(RoadSidewalks.walkWidth(out,walk,out.first(),-sign)==0,"Y inner walk starts with zero width");check(RoadSidewalks.walkWidth(out,walk,out.last(),-sign)==5,"Y inner walk reaches B sidewalk");
   check(RoadSidewalks.walkWidth(in,walk,in.last(),-sign)==0,"inbound inner walk closes at throat");
   check(RoadRecord.readSettings(RoadRecord.writeSettings(out.settings())).equals(out.settings()),"Y sidewalk sections persist");
  }
  // Diagonal plinth fits within a 3 m median, even with the 45-degree square footprint.
  for(double yaw:new double[]{0,45,90,135}){
   var lower=mesh(settings(Style.O8_GREEN),yaw);var at=RoadStructures.sample(lower,40).center();
   var foot=new Part(new V(at.x(),2,at.z()),new V(at.x(),2,at.z()),1.9,.35,true,Material.CONCRETE);
   check(RoadStructures.fitsMedian(foot,lower,List.of(lower)),"median plinth preserved at "+yaw);
  }
  var road=mesh(s,-90);var mid=RoadStructures.sample(road,40);V center=mid.at(mid.halfWidth()+2.4,0);var pier=new Part(center,center,2.2,.35,true,Material.CONCRETE);
  check(RoadSidewalks.blocksTactile(pier,road),"low pier plinth cannot cover tactile edge");
  var shifted=new Part(center.add(mid.left().mul(1.5)),center.add(mid.left().mul(1.5)),2.2,.35,true,Material.CONCRETE);check(!RoadSidewalks.blocksTactile(shifted,road),"outer sidewalk placement remains available");
  V obstruction=mid.at(mid.halfWidth()+1.25,-.2);var obstacle=new Part(obstruction,obstruction,3,4,true,Material.CONCRETE);
  var detour=RoadSidewalks.parts(road,walk,null,List.of(obstacle));
  check(detour.stream().filter(p->p.material()==Material.TACTILE).anyMatch(p->RoadQueries.horizontal(road,p.a()).lateral()>mid.halfWidth()+3),"wide obstacle retains required tactile detour beyond half sidewalk width");
  var gs=s.structure(Structure.BRIDGE).options(s.options().infrastructure(s.options().infrastructure().gantry(RoadInfrastructure.Gantry.FRAME)));var gm=mesh(gs,-90);var gp=RoadGantry.parts(gm,RoadGantry.station(gm,0),Revision28Validation.ground(-20));
  check(gp.stream().filter(p->p.pier()&&p.material()==Material.CONCRETE).allMatch(p->p.a().y()>=2.2-1e-8),"gantry footing sits on sidewalk top");
  var ramp=RoadSupports.ramp(new Sample(new V(0,15,0),new V(1,0,0),20,3),1,Revision28Validation.ground(0));check(ramp.size()==1&&ramp.get(0).pier()&&ramp.get(0).a().x()==0,"ramp keeps one shaft, no beam or lateral shift");
  for(var adjust:List.of(RoadTunnelFit.Adjustment.BOTH,RoadTunnelFit.Adjustment.START,RoadTunnelFit.Adjustment.END)){
   var ts=settings(Style.O2_YELLOW).structure(Structure.TUNNEL);ts=ts.options(ts.options().infrastructure(ts.options().infrastructure().depth(8).grade(.06).adjustment(adjust)));
   var a=new Node(new V(0,2,0),-90,0);var b=new Node(new V(40,2,0),-90,0);var fit=RoadTunnelFit.plan(RoadPlanner.Hint.free(a),RoadPlanner.Hint.free(b),ts);
   check(fit.mesh().length()>40,"tunnel fitter extends short segment");check(adjust!=RoadTunnelFit.Adjustment.START||fit.end().position().equals(b.position()),"start-only fixes end");check(adjust!=RoadTunnelFit.Adjustment.END||fit.start().position().equals(a.position()),"end-only fixes start");
  }
  // Actual offset-loop elimination and smooth bend, not repeated triangle clipping.
  var loop=List.of(new V(0,0,0),new V(4,0,4),new V(0,0,4),new V(4,0,0),new V(8,0,0));var clean=TactilePaths.removeLoops(loop);check(clean.size()<loop.size(),"offset loop removed");check(TactilePaths.rounded(clean,1.5).size()>clean.size(),"sharp crossing replaced by curved points");
  var graph=new CompoundTag();var roads=new ListTag();var profile=settings(Style.O2_YELLOW);var owner=new UUID(0,0);
  for(int i=0;i<2;i++){var a=new BlockPos(i==0?-100:160,2,0);var b=new BlockPos(i==0?0:260,2,0);roads.add(new RoadRecord(UUID.randomUUID(),owner,a,b,new Node(new V(a.getX()+.5,2,.5),-90,0),new Node(new V(b.getX()+.5,2,.5),-90,0),profile,false,4).save());}
  graph.put("Streets",roads);var command=new CompoundTag();command.put("JunctionGraph",graph);command.putLong("A",new BlockPos(0,2,0).asLong());command.putLong("B",new BlockPos(160,2,0).asLong());command.put("StartNode",RoadRecord.writeNode(new Node(new V(.5,2,.5),-90,0)));command.put("EndNode",RoadRecord.writeNode(new Node(new V(160.5,2,.5),-90,0)));command.put("Settings",RoadRecord.writeSettings(profile));command.putInt("DegreeA",1);command.putInt("DegreeB",1);
  for(int mask=0;mask<4;mask++){command.putBoolean("ForceJunctionA",(mask&1)!=0);command.putBoolean("ForceJunctionB",(mask&2)!=0);var draft=AutoJunctions.preview(command);check(draft.centers().size()==Integer.bitCount(mask),"independent endpoint choice "+mask);}
  var spec=Junction22Validation.fixture(Style.O4_YELLOW,false,0,35,180);var arms=new ArrayList<>(spec.arms());
  for(int k=0;k<arms.size();k++){var a=arms.get(k);arms.set(k,JunctionSpec.arm(a.endpoint(),a.inward(),a.external().options(a.external().options().sidewalk(walk)),true,k));}
  var junction=JunctionPlanner.plan(spec.arms(arms));
  java.nio.file.Path folder=java.nio.file.Path.of("validation/0.33.0");java.nio.file.Files.createDirectories(folder);
  var svg=new StringBuilder("<svg xmlns='http://www.w3.org/2000/svg' viewBox='-160 -160 320 320'>");svg.append("<rect x='-160' y='-160' width='320' height='320' fill='#426044'/>");
  for(var piece:junction.pieces())for(var face:RoadSurface.build(piece.mesh(),List.of(),List.of()).pavement())polygon(svg,face.points(),"#41474c");
  for(var piece:junction.pieces())for(var part:piece.structures())if(RoadSidewalks.smoothPart(part))polygon(svg,part.base(),part.material()==Material.TACTILE?"#f3ca36":"#a5adb1");
  java.nio.file.Files.writeString(folder.resolve("tactile-corner.svg"),svg.append("</svg>").toString());
  System.out.println("Revision33 PASS "+checks+" regression checks");
 }
}
