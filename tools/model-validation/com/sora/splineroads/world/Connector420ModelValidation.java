package com.sora.splineroads.world;
import com.sora.splineroads.core.*;
import com.sora.splineroads.core.RoadGeometry.*;
import java.util.*;

/** Production model/paint/infrastructure with explicit NBT/world adapters. NOT a game test. */
public final class Connector420ModelValidation {
  static int checks,cases;static long sequence=420000;
  static UUID id(){return new UUID(420,++sequence);}
  static void check(boolean v,String m){checks++;if(!v)throw new AssertionError(m);}
  static RoadRecord road(V a,V b,Style style,boolean left){return Arrival417ModelValidation.road(a,b,style,left);}
  static LanePoints.Ref point(Map<UUID,RoadRecord> all,RoadRecord r,double d,int slot){return Arrival417ModelValidation.point(all,r,d,slot);}
  static final RoadStructures.Ground GROUND=new RoadStructures.Ground(){public double top(double x,double z,double y){return 99;}public boolean blocked(RoadStructures.Part p){return false;}public boolean joined(V p){return false;}};
  static double area(List<V> p){double a=0;for(int i=0;i<p.size();i++){var x=p.get(i);var y=p.get((i+1)%p.size());a+=x.x()*y.z()-x.z()*y.x();}return Math.abs(a)*.5;}
  public static void main(String[]args){
    for(boolean left:new boolean[]{false,true})for(Style style:List.of(Style.O2_ONE,Style.O4_RAIL,Style.H4_RAIL))for(int side:new int[]{-1,1})for(var arrival:List.of(LanePoints.Arrival.MERGE,LanePoints.Arrival.EXTRA)){
      var all=new LinkedHashMap<UUID,RoadRecord>();var target=road(new V(0,100,0),new V(0,100,1400),style,left);
      int n=RoadProfile.catalog(style).lanes();int slot=RoadProfile.catalog(style).twoWay()?(side<0?n/2-1:n-1):(side<0?0:n-1);
      var lane=LanePoints.lane(target.rawMesh(),900,slot);V begin=lane.position().sub(lane.direction().mul(400)).add(lane.direction().left().mul(200));
      var source=road(begin.sub(lane.direction().mul(100)),begin,RoadProfile.highway(style)?Style.H1_ONE:Style.O1_ONE,left);all.put(source.id(),source);all.put(target.id(),target);
      var a=point(all,source,80,0);var b=point(all,target,900,slot);var options=new LanePoints.Options(LanePoints.Path.AUTO,LanePoints.Departure.BRANCH,arrival,32,32,LanePoints.Elevation.AUTO,LanePoints.Landing.EXACT);
      var rid=id();var connector=LaneRamps.generate(null,all,rid,id(),new LanePoints.Link(a,b,options,null));all.put(rid,connector);LaneCrossSections.reconcile(all);var mesh=all.get(rid).mesh();var hosts=List.of(all.get(source.id()).mesh(),all.get(target.id()).mesh());cases++;
      check(mesh.settings().style().connectorRamp()&&RoadProfile.catalog(mesh.settings().style()).type()==RoadProfile.Type.RAMP,"not an independent ramp kind");
      check(RoadProfile.highway(mesh.settings().style())==RoadProfile.highway(style),"highway lineage lost");
      check(LanePoints.supported(mesh.settings()),"manual lane points no longer work on generated ramp");
      check(mesh.settings().options().infrastructure().gantry()==RoadInfrastructure.Gantry.OFF,"default gantry not OFF");check(RoadGantry.plan(mesh,GROUND).isEmpty(),"default gantry parts present");
      var arrows=RoadJunction.arrows(mesh,hosts);check(arrows.size()==(arrival==LanePoints.Arrival.EXTRA?3:0),"wrong connector arrow count "+style+" left="+left+" side="+side+" mode="+arrival+" count="+arrows.size());
      for(var paint:arrows)for(V p:paint.points()){check(RoadQueries.contains(mesh,p,-.1,.1),"arrow outside actual connector");check(!RoadQueries.contains(hosts.get(1),p,.1,.15),"arrow intrudes target through lane");}
      if(!arrows.isEmpty()){
        var link=mesh.settings().options().lanePoints().link();var o=link.options();var without=new LanePoints.Options(o.path(),o.departure(),LanePoints.Arrival.MERGE,o.radius(),o.transition(),o.elevation(),o.landing());
        var plain=RoadRibbon.mesh(mesh.samples(),mesh.settings().options(mesh.settings().options().lanePoints(mesh.settings().options().lanePoints().link(new LanePoints.Link(link.from(),link.to(),without,null,link.targetOffset(),link.protectedMerge(),link.rectangularClosure())))));
        var painted=RoadSurface.build(mesh,hosts,hosts);var unpainted=RoadSurface.build(plain,hosts,hosts);
        double extra=painted.markings().stream().mapToDouble(f->area(f.points())).sum()-unpainted.markings().stream().mapToDouble(f->area(f.points())).sum();
        double expected=arrows.stream().mapToDouble(p->area(p.points())).sum();check(Math.abs(extra-expected)<1e-5,"arrow lost under ownership/road clipping: "+extra+" != "+expected);
        var at=RoadQueries.horizontal(mesh,arrows.get(2).points().get(0));var hp=RoadQueries.horizontal(hosts.get(1),at.sample().center());
        double toward=hp.sample().center().sub(at.sample().center()).dot(at.sample().left());
        check(Math.signum(at.lateral())==Math.signum(toward),"merge head points away from target");
      }
      var decoded=RoadRecord.load(connector.header());check(decoded.settings().style()==connector.settings().style(),"style lost in roundtrip");check(decoded.mesh().samples().equals(connector.mesh().samples()),"roundtrip changes lane geometry");
      var requested=decoded.settings().options(decoded.settings().options().infrastructure(decoded.settings().options().infrastructure().gantry(RoadInfrastructure.Gantry.FRAME)));
      var changed=LaneRamps.reconfigure(decoded,requested,all);check(changed.settings().style()==decoded.settings().style(),"road edit changes category");check(changed.settings().options().infrastructure().gantry()==RoadInfrastructure.Gantry.FRAME,"explicit gantry override lost");check(!RoadGantry.plan(changed.mesh(),GROUND).isEmpty(),"explicit gantry cannot be enabled");
      System.out.println("  "+style+" left="+left+" side="+side+" "+arrival+" kind="+mesh.settings().style()+" guideFaces="+arrows.size());
      all.remove(rid);LaneCrossSections.reconcile(all);check(LaneTopology.metadata(all.get(target.id())).cuts().isEmpty(),"deleting independent ramp leaves host reservation");
    }
    legacyAndChain();
    System.out.println("Connector420ModelValidation: "+cases+" cases / "+checks+" checks; actual LaneRamps/paint/gantry/record/closure, explicit world and NBT adapters; NO Minecraft/GPU/network test.");
  }
  static void legacyAndChain(){
    var all=new LinkedHashMap<UUID,RoadRecord>();var a=road(new V(0,100,0),new V(0,100,600),Style.H1_ONE,false);var b=road(new V(-200,100,800),new V(-200,100,1800),Style.H1_ONE,false);all.put(a.id(),a);all.put(b.id(),b);
    var from=point(all,a,450,0);var to=point(all,b,700,0);var o=new LanePoints.Options(LanePoints.Path.AUTO,LanePoints.Departure.BRANCH,LanePoints.Arrival.MERGE,32,32,LanePoints.Elevation.AUTO,LanePoints.Landing.EXACT);
    var id=id();var ramp=LaneRamps.generate(null,all,id,id(),new LanePoints.Link(from,to,o,null));all.put(id,ramp);
    // Build a literal pre-upgrade record; load must not silently migrate geometry or gantries.
    var t=ramp.header();t.getCompound("Settings").putString("Style","H1_ONE");
    // header format stores settings nested; assert fixture actually changed before judging migration.
    var settings=RoadRecord.writeSettings(ramp.settings());settings.putString("Style","H1_ONE");
    var oldSettings=RoadRecord.readSettings(settings);var legacy=new RoadRecord(ramp.id(),ramp.owner(),ramp.a(),ramp.b(),ramp.start(),ramp.end(),oldSettings).alignment(null,RoadRibbon.mesh(ramp.rawMesh().samples(),oldSettings));
    var loaded=RoadRecord.load(legacy.header());check(loaded.settings().style()==Style.H1_ONE,"old load eagerly changes style");
    all.put(id,loaded);var upgraded=LaneRamps.reconfigure(loaded,loaded.settings(),all);check(upgraded.settings().style()==Style.C1_HIGHWAY_RAMP,"explicit edit does not migrate old road category");cases++;
    all.put(id,upgraded);var branchPoint=point(all,upgraded,upgraded.mesh().length()*.45,0);
    var c=road(new V(400,110,1400),new V(550,110,1400),Style.O1_ONE,false);all.put(c.id(),c);var into=point(all,c,40,0);
    var child=LaneRamps.generate(null,all,id(),id(),new LanePoints.Link(branchPoint,into,o,null));check(child.settings().style()==Style.C1_HIGHWAY_RAMP,"chained ramp loses highway context");cases++;
    // Ordinary road and historical automatic ramp conventions are not globally switched.
    check(!RoadJunction.arrows(a.mesh(),List.of()).isEmpty(),"ordinary one-way arrows globally removed");
    check(RoadProfile.catalog(Style.R1).type()==RoadProfile.Type.RAMP&&!Style.R1.connectorRamp(),"legacy interchange type reclassified");
    check(!LanePoints.supported(new Settings(Mode.CURVE,Style.R1,5,1,.35,90)),"automatic interchange ramp promoted to lane-point host");
  }
}
