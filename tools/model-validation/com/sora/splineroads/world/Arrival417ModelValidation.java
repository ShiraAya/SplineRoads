package com.sora.splineroads.world;
import com.sora.splineroads.core.*;
import com.sora.splineroads.core.RoadGeometry.*;
import java.util.*;
/** Real planner/deck/record/reconciliation; explicit NBT and world adapters, not game tests. */
public final class Arrival417ModelValidation {
 static int checks,cases;static long serial=50000;
 static UUID id(){return new UUID(417,++serial);}static void check(boolean b,String m){checks++;if(!b)throw new AssertionError(m);}
 static RoadRecord road(V a,V b,Style style,boolean left){var o=RoadProfile.Options.DEFAULT.traffic(left);var s=new Settings(Mode.STRAIGHT,style,RoadProfile.width(style,o,4),1,.35,90).options(o);double yaw=RoadPlanner.yaw(b.sub(a).horizontalUnit());return new RoadRecord(id(),id(),RampJunctions.at(a),RampJunctions.at(b),new Node(a,yaw,0),new Node(b,yaw,0),s);}
 static LanePoints.Ref point(Map<UUID,RoadRecord> all,RoadRecord road,double station,int slot){var r=all.get(road.id());var d=LaneTopology.metadata(r);var ps=new ArrayList<>(d.points());var p=LanePoints.point(id(),LanePoints.Origin.MANUAL,r.rawMesh(),station,slot);ps.add(p);all.put(r.id(),r.withLanePoints(d.points(ps)));return LanePoints.Ref.lane(r.id(),p.id());}
 public static void main(String[]args){
  for(var style:List.of(Style.O1_ONE,Style.O3_ONE,Style.O6_RAIL))for(boolean left:new boolean[]{false,true})for(int slot=0;slot<RoadProfile.catalog(style).lanes();slot++)for(int side:new int[]{-1,1}){
   var all=new LinkedHashMap<UUID,RoadRecord>();var target=road(new V(0,100,0),new V(0,100,1400),style,left);int sign=LanePoints.lane(target.rawMesh(),700,slot).sign();
   double z=700-sign*350;var source=road(new V(side*360,100,z),new V(side*280,100,z),Style.O1_ONE,left);all.put(source.id(),source);all.put(target.id(),target);
   var from=point(all,source,60,0);var to=point(all,target,700,slot);var o=new LanePoints.Options(LanePoints.Path.AUTO,LanePoints.Departure.BRANCH,LanePoints.Arrival.MERGE,32,32,LanePoints.Elevation.AUTO,LanePoints.Landing.EXACT);
   var rid=id();RoadRecord ramp;
   try{ramp=LaneRamps.generate(null,all,rid,id(),new LanePoints.Link(from,to,o,null));}catch(IllegalArgumentException e){throw new AssertionError(style+" left="+left+" slot="+slot+" side="+side+" "+e.getMessage(),e);}
   all.put(rid,ramp);LaneCrossSections.reconcile(all);var host=all.get(target.id());var link=LaneTopology.metadata(ramp).link();cases++;
   check(link.protectedMerge()&&link.closesTarget(),"new merge policy not stored");var cuts=LaneTopology.metadata(host).cuts().stream().filter(c->c.connection().equals(rid)&&c.arrival()).toList();check(cuts.size()==1,"missing target closure");var cut=cuts.get(0);
   check(cut.lane()==slot&&cut.sign()==sign&&Math.abs(cut.end()-700)<1e-6,"wrong slot/station/direction");check(sign*(cut.end()-cut.begin())>=64,"closure lacks upstream room");
   double mid=700-sign*16;check(!LaneSections.active(host.mesh(),mid,slot),"selected lane not closed before B");check(LaneSections.active(host.mesh(),700,slot)&&LaneSections.active(host.mesh(),700+sign*8,slot),"lane not restored at/after B");
   for(int other=0;other<RoadProfile.catalog(style).lanes();other++)if(other!=slot){check(LaneSections.active(host.mesh(),mid,other),"other lane closed");check(LanePoints.lane(host.mesh(),mid,other).position().distance(LanePoints.lane(target.rawMesh(),mid,other).position())<1e-7,"other lane moved");}
   var protectedHost=LaneDeck.excludingSlot(LaneDeck.motorOnly(host.mesh()),slot);check(RoadClearance.contacts(ramp.mesh(),protectedHost).stream().noneMatch(RoadClearance.Contact::blocked),"ramp intrudes live adjacent lane or median");
   if(style==Style.O3_ONE&&(slot==0&&side==1||slot==2&&side==-1))check(ramp.mesh().samples().stream().allMatch(s->Math.abs(s.center().y()-100)<1e-7),"unobstructed same-height outside merge has needless height excursion");
   var end=LanePoints.lane(host.rawMesh(),700,slot);check(ramp.mesh().last().center().distance(end.position())<1e-5,"does not end at selected slot center");check(Math.abs(ramp.mesh().last().halfWidth()*2-end.width())<1e-6,"target mouth exceeds selected lane width");
   var round=RoadRecord.load(ramp.header());check(LaneTopology.metadata(round).link().protectedMerge(),"link marker lost in record codec");var rt=RoadRecord.load(host.header());check(LaneTopology.metadata(rt).cuts().equals(LaneTopology.metadata(host).cuts()),"arrival cut lost in codec");
   var again=new LinkedHashMap<>(all);LaneCrossSections.reconcile(again);check(LaneTopology.metadata(again.get(target.id())).cuts().equals(LaneTopology.metadata(host).cuts()),"repeated reconciliation unstable");
   all.remove(rid);LaneCrossSections.reconcile(all);check(LaneTopology.metadata(all.get(target.id())).cuts().isEmpty(),"deleted connector leaves target closed");
   System.out.printf(Locale.ROOT,"  %s left=%s slot=%d side=%d closed=%.1f..%.1f ramp=%.1f%n",style,left,slot,side,cut.begin(),cut.end(),ramp.mesh().length());
  }
  curves();codecAndGuards();multipleIntakes();departuresAndExtras();
  System.out.println("Arrival417ModelValidation: "+cases+" cases, "+checks+" checks; production model + explicit adapters, NO game world/GPU.");
 }
 static void curves(){
  for(boolean left:new boolean[]{false,true})for(var style:List.of(Style.O3_ONE,Style.O6_RAIL))for(int slot=0;slot<RoadProfile.catalog(style).lanes();slot++){
   var original=road(new V(0,100,0),new V(0,100,1400),style,left);var points=new ArrayList<Sample>();double radius=1800;
   for(double d=0;d<=1400;d+=2){double angle=d/radius;V dir=new V(Math.sin(angle),0,Math.cos(angle));points.add(new Sample(new V(100000+radius*(1-Math.cos(angle)),100+d*.01,-100000+radius*Math.sin(angle)),dir.left(),d,original.settings().width()/2));}
   var mesh=RoadRibbon.mesh(points,original.settings());var a=RoadRibbon.start(mesh);var b=RoadRibbon.end(mesh);
   var target=new RoadRecord(id(),id(),RampJunctions.at(a.position()),RampJunctions.at(b.position()),a,b,original.settings()).alignment(null,mesh);
   double end=mesh.length()/2;var lane=LanePoints.lane(target.rawMesh(),end,slot);V outside=lane.direction().left();V start=lane.position().sub(lane.direction().mul(320)).add(outside.mul(300));
   var source=road(start.add(outside.mul(80)),start,Style.O1_ONE,left);var all=new LinkedHashMap<UUID,RoadRecord>();all.put(source.id(),source);all.put(target.id(),target);
   var from=point(all,source,60,0);var to=point(all,target,end,slot);var options=new LanePoints.Options(LanePoints.Path.AUTO,LanePoints.Departure.BRANCH,LanePoints.Arrival.MERGE,32,32,LanePoints.Elevation.AUTO,LanePoints.Landing.EXACT);
   var rid=id();var ramp=LaneRamps.generate(null,all,rid,id(),new LanePoints.Link(from,to,options,null));all.put(rid,ramp);LaneCrossSections.reconcile(all);var result=all.get(target.id());cases++;
   var cut=LaneTopology.metadata(result).cuts().stream().filter(c->c.arrival()).findFirst().orElseThrow();check(Math.abs(cut.end()-end)<1e-5,"curved B station changed");
   check(RoadClearance.contacts(ramp.mesh(),LaneDeck.excludingSlot(LaneDeck.motorOnly(result.mesh()),slot)).stream().noneMatch(RoadClearance.Contact::blocked),"curved neighbour invasion");
   check(ramp.mesh().last().center().distance(lane.position())<1e-5,"curved merge axis mismatch");
   for(int other=0;other<RoadProfile.catalog(style).lanes();other++)if(other!=slot)check(LanePoints.lane(result.mesh(),end,other).position().distance(LanePoints.lane(target.rawMesh(),end,other).position())<1e-7,"curved neighbor moved");
   System.out.printf(Locale.ROOT,"  curve %s left=%s slot=%d sign=%d upstream=%.1f%n",style,left,slot,lane.sign(),lane.sign()*(cut.end()-cut.begin()));
  }
 }
 static void denied(Runnable action,String reason){boolean rejected=false;try{action.run();}catch(IllegalArgumentException expected){rejected=true;}check(rejected,reason);}
 static void codecAndGuards(){
  var host=road(new V(0,100,0),new V(0,100,800),Style.O3_ONE,false);var feeder=road(new V(-350,100,300),new V(-250,100,300),Style.O1_ONE,false);var all=new LinkedHashMap<UUID,RoadRecord>();all.put(host.id(),host);all.put(feeder.id(),feeder);
  var from=point(all,feeder,80,0);var to=point(all,host,600,1);var opt=new LanePoints.Options(LanePoints.Path.AUTO,LanePoints.Departure.BRANCH,LanePoints.Arrival.MERGE,32,32,LanePoints.Elevation.KEEP,LanePoints.Landing.EXACT);
  denied(()->LaneRamps.generate(null,all,id(),id(),new LanePoints.Link(from,to,opt,null)),"KEEP allowed same-level transverse inner-lane merge");
  var legacy=new LanePoints.Link(from,to,opt,null);check(!legacy.closesTarget(),"legacy links silently migrated");var encoded=LanePointCodec.link(legacy.withProtectedMerge());check(LanePointCodec.link(encoded).protectedMerge(),"marker codec failed");encoded.remove("ProtectedMerge");check(!LanePointCodec.link(encoded).protectedMerge(),"legacy missing marker not false");
  for(var arrival:List.of(LanePoints.Arrival.EXTRA,LanePoints.Arrival.REPLACE)){var o=new LanePoints.Options(opt.path(),opt.departure(),arrival,32,32,opt.elevation(),opt.landing());check(!new LanePoints.Link(from,to,o,null).withProtectedMerge().closesTarget(),"extra/replacement incorrectly assigned MERGE closure");}
  var junction=new LanePoints.Link(from,LanePoints.Ref.junction(id()),opt,new V(0,100,0)).withProtectedMerge();check(!junction.closesTarget(),"junction incorrectly closed a target lane");
  var lane=LanePoints.lane(host.rawMesh(),600,1);var obstruction=road(lane.position().add(new V(-10,0,10)),lane.position().add(new V(10,0,10)),Style.O1_ONE,false);
  denied(()->LaneReopening.closeBeforeStation(host.rawMesh(),1,600,obstruction.mesh(),List.of(),32),"intrusion after B was allowed");
  var exact=road(lane.position().add(new V(0,8,-200)),lane.position(),Style.O1_ONE,false);var part=new RoadStructures.Part(lane.position().add(new V(0,-2,12)),lane.position().add(new V(0,-2,14)),1,6,false);
  denied(()->LaneReopening.closeBeforeStation(host.rawMesh(),1,600,exact.mesh(),List.of(part),32),"post-B pier was allowed");
  var conflict=List.of(new LaneSections.Event(id(),LaneSections.Kind.ARRIVE,1,1,600,32,450),new LaneSections.Event(id(),LaneSections.Kind.ARRIVE,1,1,620,32,470));denied(()->LaneSections.derive(host.rawMesh(),conflict),"overlapping reservations silently overwritten");
 }

 static void multipleIntakes(){
  var all=new LinkedHashMap<UUID,RoadRecord>();var main=road(new V(0,100,0),new V(0,100,1600),Style.O3_ONE,false);all.put(main.id(),main);
  var options=new LanePoints.Options(LanePoints.Path.AUTO,LanePoints.Departure.BRANCH,LanePoints.Arrival.MERGE,32,32,LanePoints.Elevation.AUTO,LanePoints.Landing.EXACT);
  for(double station:new double[]{400,1200}){
   var source=road(new V(-360,100,station-250),new V(-280,100,station-250),Style.O1_ONE,false);all.put(source.id(),source);
   var from=point(all,source,60,0);var to=point(all,main,station,2);var id=id();var ramp=LaneRamps.generate(null,all,id,id(),new LanePoints.Link(from,to,options,null));all.put(id,ramp);LaneCrossSections.reconcile(all);
  }
  check(LaneTopology.metadata(all.get(main.id())).cuts().stream().filter(LaneSections.Cut::arrival).count()==2,"disjoint same-lane intakes conflict during provisional planning");
  var clean=new LinkedHashMap<UUID,RoadRecord>();var small=road(new V(0,100,0),new V(0,100,300),Style.O1_ONE,false);var feed=road(new V(-200,100,-100),new V(-100,100,-100),Style.O1_ONE,false);clean.put(small.id(),small);clean.put(feed.id(),feed);
  var from=point(clean,feed,80,0);var to=point(clean,small,20,0);var id=id();var ramp=LaneRamps.generate(null,clean,id,id(),new LanePoints.Link(from,to,options,null));clean.put(id,ramp);LaneCrossSections.reconcile(clean);
  var h=clean.get(small.id());check(!LaneSections.active(h.mesh(),0,0)&&LaneSections.active(h.mesh(),20,0),"free start should be entirely closed until near-start B");
  var predecessor=road(new V(0,100,-200),new V(0,100,0),Style.O1_ONE,false);clean.put(predecessor.id(),predecessor);
  denied(()->LaneCrossSections.reconcile(clean),"silently closes previous physical road when upstream taper cannot fit");
 }

 static void departuresAndExtras(){
  for(var style:List.of(Style.O3_ONE,Style.O6_RAIL))for(boolean left:new boolean[]{false,true})for(int slot=0;slot<RoadProfile.catalog(style).lanes();slot++){
   var all=new LinkedHashMap<UUID,RoadRecord>();var source=road(new V(0,100,0),new V(0,100,1400),style,left);int sign=LanePoints.lane(source.rawMesh(),700,slot).sign();double start=sign>0?200:1200;
   var target=road(new V(-280,100,start+sign*420),new V(-400,100,start+sign*420),Style.O1_ONE,left);all.put(source.id(),source);all.put(target.id(),target);var from=point(all,source,start,slot);var to=point(all,target,30,0);
   var o=new LanePoints.Options(LanePoints.Path.AUTO,LanePoints.Departure.TEMPORARY,LanePoints.Arrival.MERGE,32,32,LanePoints.Elevation.AUTO,LanePoints.Landing.EXACT);var id=id();
   var ramp=LaneRamps.generate(null,all,id,id(),new LanePoints.Link(from,to,o,null));all.put(id,ramp);LaneCrossSections.reconcile(all);cases++;
   var changed=all.get(source.id());check(!LaneSections.active(changed.mesh(),start+sign*40,slot),"source TEMPORARY slot not closed");
   check(RoadClearance.contacts(ramp.mesh(),LaneDeck.excludingSlot(LaneDeck.motorOnly(changed.mesh()),slot)).stream().noneMatch(RoadClearance.Contact::blocked),"departing ramp intrudes live neighboring source lane");
   LaneReopening.validateRestored(changed.mesh(),slot,ramp.mesh(),id);checks++;
  }
  for(boolean atSource:new boolean[]{false,true}){
   var all=new LinkedHashMap<UUID,RoadRecord>();var a=road(new V(0,100,0),new V(0,100,600),Style.O2_ONE,false);var b=road(new V(-120,100,700),new V(-120,100,1200),Style.O2_ONE,false);all.put(a.id(),a);all.put(b.id(),b);var from=point(all,a,200,1);var to=point(all,b,250,1);
   var o=new LanePoints.Options(LanePoints.Path.AUTO,atSource?LanePoints.Departure.EXTRA:LanePoints.Departure.BRANCH,atSource?LanePoints.Arrival.MERGE:LanePoints.Arrival.EXTRA,32,32,LanePoints.Elevation.AUTO,LanePoints.Landing.EXACT);var id=id();var ramp=LaneRamps.generate(null,all,id,id(),new LanePoints.Link(from,to,o,null));all.put(id,ramp);LaneCrossSections.reconcile(all);
   check(LaneTopology.metadata(all.get(b.id())).cuts().stream().anyMatch(LaneSections.Cut::arrival)==atSource,"EXTRA incoming should not close a target slot");cases++;
  }
 }

}
