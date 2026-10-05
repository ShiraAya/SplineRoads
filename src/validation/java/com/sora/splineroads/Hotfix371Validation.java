package com.sora.splineroads;
import com.sora.splineroads.core.*;
import com.sora.splineroads.core.RoadGeometry.*;
import com.sora.splineroads.core.RoadStructures.*;
import com.sora.splineroads.core.JunctionSpec.*;
import java.util.*;
import java.nio.file.*;
public final class Hotfix371Validation {
 static int checks;
 static void check(boolean ok,String why){checks++;if(!ok)throw new AssertionError(why);}
 static Ground floor(double y){return Revision37Validation.floor(y);}
 static Settings settings(Style style,RoadProfile.Options o,Structure structure){return Revision37Validation.setting(style,o,structure);}
 static Mesh mesh(Settings s){return Revision37Validation.mesh(s);}
 static List<Part> bases(List<Part> parts){return Revision37Validation.bases(parts);}
 static List<Part> heads(List<Part> parts,V base){var out=new ArrayList<Part>();boolean match=false;for(var p:parts){if(p.material()==Material.CONCRETE&&p.width()==.38&&p.height()==.16)match=p.a().equals(base);else if(match&&p.material()==Material.LAMP)out.add(p);}return out;}
 static RoadProfile.Options options(boolean cycle,boolean walk,RoadStreetscape.Separator separator,boolean pedestrian){
  return RoadProfile.Options.DEFAULT.cycleFinish(cycle?RoadProfile.Options.CycleFinish.GREEN:RoadProfile.Options.CycleFinish.NONE)
    .sidewalk(RoadSidewalks.Config.DEFAULT.enabled(walk)).streetscape(RoadStreetscape.Config.DEFAULT.separator(separator).walkLamps(pedestrian).walkLampSpacing(32));
 }
 static void lamps(){
  for(var structure:List.of(Structure.GROUND,Structure.BRIDGE))for(boolean cycle:new boolean[]{false,true})for(boolean walk:new boolean[]{false,true})for(var separator:RoadStreetscape.Separator.values())for(boolean pedestrian:new boolean[]{false,true}){
   var o=options(cycle,walk,separator,pedestrian);var m=mesh(settings(Style.O4_YELLOW,o,structure));var parts=RoadStreetscape.plan(m,floor(structure==Structure.GROUND?2:-5),RoadFurniture.Phase.DEFAULT);var at=RoadStructures.sample(m,8);var feet=bases(parts).stream().filter(p->Math.abs(RoadQueries.horizontal(m,p.a()).sample().distance()-8)<.01).toList();
   boolean raised=structure==Structure.BRIDGE;boolean separate=pedestrian&&cycle&&walk&&separator!=RoadStreetscape.Separator.LINE&&!raised;
   check(feet.size()==2+(raised&&cycle?1:0)+(separate?2:0),"lamp coexistence "+structure+" "+cycle+" "+walk+" "+separator+" "+pedestrian+" feet="+feet.size());
   for(var base:feet){var q=RoadQueries.horizontal(m,base.a());double lateral=Math.abs(q.lateral());if(lateral<.1){check(heads(parts,base.a()).size()==2,"bridge central style one");continue;}
    boolean walkLamp=walk&&lateral>at.halfWidth()+1.5;
    int style=walkLamp?1:raised?(cycle?(walk?2:4):(walk?1:4)):cycle?(separator==RoadStreetscape.Separator.LINE?1:separator==RoadStreetscape.Separator.RAIL&&separate?3:2):(walk?1:3);
    check(heads(parts,base.a()).size()==(style<=2?2:1),"correct head count style="+style+" "+structure+" "+separator);
    if(!walkLamp){double expected=!raised&&cycle&&separator!=RoadStreetscape.Separator.LINE?Math.abs(RoadProfile.layout(m,at).outer(1))+(separator==RoadStreetscape.Separator.GREEN?.75:0):at.halfWidth()+(walk?.65:raised?.45:.55);check(Math.abs(lateral-expected)<1e-6,"main lamp location correct");}
   }
   check(m.settings().options().streetscape().walkLamps()==separate,"impossible pedestrian setting locked off");
  }
  for(boolean left:new boolean[]{false,true})for(var structure:List.of(Structure.GROUND,Structure.BRIDGE))for(boolean curb:new boolean[]{false,true}){
   var o=RoadProfile.Options.DEFAULT.traffic(left).extras(true,true,curb).sidewalk(RoadSidewalks.Config.DEFAULT.enabled(true));var m=mesh(settings(Style.O2_ONE,o,structure));var p=RoadStreetscape.plan(m,floor(structure==Structure.GROUND?2:-5),RoadFurniture.Phase.DEFAULT);int side=RoadProfile.layout(m,m.first()).outside();
   for(var base:bases(p)){var q=RoadQueries.horizontal(m,base.a());double c=structure==Structure.GROUND?RoadProfile.curbExtent(RoadProfile.layout(m,q.sample()),q.sample(),side):0;check(q.lateral()*side>0,"one-way traffic side retained");check(Math.abs(Math.abs(q.lateral())-(q.sample().halfWidth()-(c>0?c/2:.28)))<1e-6,"one-way 353 edge retained");check(heads(p,base.a()).size()==1,"one-way single arm");}
  }
  for(int style=1;style<=4;style++)for(boolean far:new boolean[]{false,true}){
   var parts=RoadStreetscape.lamp(new V(63.8,2,63.8),new V(.6,0,.8),style);var sections=RoadRenderMesh.sections(new RoadSurface.Geometry(List.of(),List.of()),parts);
   long undersides=sections.values().stream().flatMap(s->(far?s.distant():s.detail()).stream()).filter(f->f.color()==0x235994&&RoadLighting.normal(f).y()<-.95).count();
   check(undersides>=1,"lamp housing underside retained after LOD and spatial clipping");
  }
 }
 static void parkingAndBridge(){
  var o=options(true,true,RoadStreetscape.Separator.GREEN,true).cycleFinish(RoadProfile.Options.CycleFinish.PARKING);var m=mesh(settings(Style.O4_YELLOW,o,Structure.GROUND));
  var paint=RoadSurface.build(m,List.of(),List.of()).markings();var stations=new TreeSet<Double>();double edge=m.first().halfWidth()-.3;
  for(var f:paint){double x0=f.points().stream().mapToDouble(V::x).min().orElseThrow(),x1=f.points().stream().mapToDouble(V::x).max().orElseThrow(),z0=f.points().stream().mapToDouble(V::z).min().orElseThrow(),z1=f.points().stream().mapToDouble(V::z).max().orElseThrow();if(x1-x0<.15&&z1-z0>2&&z0>0)stations.add((x0+x1)/2);}
  check(stations.size()>10,"actual transverse parking lines found");double last=-1;for(double station:stations){if(last>=0)check(station-last>=7.99,"parking bay at least seven blocks long");last=station;}
  V probe=RoadStructures.sample(m,5.17).at(edge,0);int borders=0;for(var f:paint)if(JunctionPaint.inside(f.points(),probe))borders++;check(borders==1,"one outer bay / road boundary owner");
  double oldExtra=RoadProfile.layout(m,m.first()).outer(1)+RoadProfile.layout(m,m.first()).cycleWidth()-.15;check(paint.stream().noneMatch(f->JunctionPaint.inside(f.points(),RoadStructures.sample(m,5.17).at(oldExtra,0))),"former duplicate outer stripe removed");
  var auto=mesh(settings(Style.O4_YELLOW,o,Structure.AUTO));Ground half=new Ground(){public double top(double x,double z,double y){return x<64?2:-8;}public boolean blocked(Part p){return false;}public boolean joined(V p){return false;}};
  var resolved=RoadStreetscape.resolve(auto,half);check(!resolved.settings().options().streetscape().raisedSpans().isEmpty(),"automatic raised span classified");
  var all=RoadStructures.plan(resolved,half);var markings=RoadSurface.build(resolved,List.of(),List.of()).markings();
  check(markings.stream().filter(f->f.color()==0xFAC136).flatMap(f->f.points().stream()).noneMatch(v->v.x()>65),"bridge yellow center lines replaced by rail boundaries");
  check(markings.stream().noneMatch(f->{double min=f.points().stream().mapToDouble(V::x).min().orElseThrow(),max=f.points().stream().mapToDouble(V::x).max().orElseThrow();return min>70&&max-min<.15&&f.points().stream().mapToDouble(V::z).max().orElseThrow()-f.points().stream().mapToDouble(V::z).min().orElseThrow()>2;}),"no parking bays on automatic bridge span");
  for(var style:List.of(Style.O4_YELLOW,Style.O4_GREEN)){
   var green=options(true,true,RoadStreetscape.Separator.GREEN,true);var bridge=mesh(settings(style,green,Structure.BRIDGE));var p=RoadStructures.plan(bridge,floor(-5));check(p.stream().noneMatch(x->x.material()==Material.GREEN||x.material()==Material.SOIL),"no green median/separator on bridge");
   check(p.stream().anyMatch(x->x.material()==Material.STEEL&&Math.abs(RoadQueries.horizontal(bridge,x.a()).lateral())<.2),"central bridge barrier exists for "+style);
  }
  for(var style:List.of(Style.O4_YELLOW,Style.O4_GREEN))for(var finish:List.of(RoadProfile.Options.CycleFinish.GREEN,RoadProfile.Options.CycleFinish.PARKING)){
   var opts=options(true,true,RoadStreetscape.Separator.GREEN,true).cycleFinish(finish);var s=settings(style,opts,Structure.BRIDGE);
   var arms=new ArrayList<Arm>();for(int i=0;i<3;i++){double t=i*Math.PI*2/3;V d=new V(Math.cos(t),0,Math.sin(t));arms.add(JunctionSpec.arm(new Node(d.mul(110).add(new V(0,2,0)),RoadPlanner.yaw(d.mul(-1)),0),d.mul(-1),s,true,i));}
   var spec=new JunctionSpec(new V(0,2,0),Kind.INTERSECTION,false,8,24,2,4,1,Control.SIGNALS,20,3,1,0,false,false,arms);
   for(var piece:JunctionPlanner.plan(spec).pieces().subList(0,3)){
    check(piece.structures().stream().noneMatch(p->p.material()==Material.GREEN||p.material()==Material.SOIL),"explicit bridge junction approach converts green separators");
    check(piece.paint().stream().noneMatch(f->f.color()==JunctionPaint.YELLOW),"explicit bridge junction approach removes painted center median");
    check(piece.structures().stream().anyMatch(p->p.material()==Material.STEEL&&Math.abs(RoadQueries.horizontal(piece.mesh(),p.a()).lateral())<.2),"explicit bridge junction approach retains central rail");
   }
  }
 }
 static void models()throws Exception{
  var o=options(true,true,RoadStreetscape.Separator.GREEN,true);var m=mesh(settings(Style.O4_GREEN,o,Structure.GROUND));var parts=RoadStructures.plan(m,floor(2));
  for(var base:bases(parts)){
   var q=RoadQueries.horizontal(m,base.a());if(Math.abs(q.lateral())>m.first().halfWidth())continue;
   V next=base.a().add(new V(.75,0,0));check(parts.stream().filter(p->p.material()==Material.GREEN).anyMatch(p->Revision37Validation.covered(List.of(p),next)),"hedge retained .75m beside each lamp pedestal");
  }
  var dumps=new ArrayList<String>();
  for(var type:RoadStreetscape.Planting.values())if(type!=RoadStreetscape.Planting.NONE){var p=RoadStreetscape.plant(new V(type.ordinal()*7,0,0),type,new V(1,0,0));
   check(p.size()>25,"detailed planting model "+type);check(p.stream().anyMatch(x->x.material()==Material.SOIL),"tree pool / flower soil exists");
   if(type!=RoadStreetscape.Planting.FLOWERS)check(p.stream().filter(x->x.material()==Material.DARK_STEEL).count()>=16,"tree grate retains trunk opening");
   for(var part:p)for(var face:part.faces()){dumps.add(String.format(Locale.ROOT,"%06x ",face.color())+String.join(" ",face.points().stream().map(v->v.x()+","+v.y()+","+v.z()).toList()));}
  }
  Files.createDirectories(Path.of("build/validation371"));Files.write(Path.of("build/validation371/plant-models.faces"),dumps);
 }
 static JunctionSpec fixture(double[] angles,int[] lanes,int[] walks){
  var arms=new ArrayList<Arm>();for(int i=0;i<angles.length;i++){double t=Math.toRadians(angles[i]);V dir=new V(Math.cos(t),0,Math.sin(t));Style style=lanes[i]==2?Style.O2_YELLOW:lanes[i]==6?Style.O6_RAIL:Style.O4_YELLOW;
   var o=RoadProfile.Options.DEFAULT.sidewalk(RoadSidewalks.Config.DEFAULT.enabled(walks[i]>0).width(Math.max(3,walks[i])));var settings=settings(style,o,Structure.GROUND);arms.add(JunctionSpec.arm(new Node(dir.mul(110).add(new V(0,2,0)),RoadPlanner.yaw(dir.mul(-1)),0),dir.mul(-1),settings,true,i));}
  return new JunctionSpec(new V(0,2,0),Kind.INTERSECTION,false,8,24,2,4,1,Control.SIGNALS,20,3,1,0,true,true,arms);
 }
 static void junctions()throws Exception{
  int id=0;for(var spec:List.of(fixture(new double[]{0,100,235},new int[]{4,6,4},new int[]{5,0,7}),fixture(new double[]{0,135,270},new int[]{6,2,2},new int[]{0,0,0}),fixture(new double[]{0,65,180,260},new int[]{2,6,4,2},new int[]{5,7,5,7}),fixture(new double[]{0,90,180,270},new int[]{4,4,4,4},new int[]{5,0,5,0}))){
   var plan=JunctionPlanner.plan(spec);Hotfix353Validation.dump(plan,Path.of("build/validation371/junction-"+id+++".svg"));
   var parts=plan.pieces().stream().flatMap(p->p.structures().stream()).toList();var paving=parts.stream().filter(p->p.material().name().startsWith("WALK_")).toList();var tactile=parts.stream().filter(p->p.material()==Material.TACTILE&&p.height()<.018).toList();
   for(var p:tactile)for(double t:new double[]{0,.5,1}){V at=p.a().mul(1-t).add(p.b().mul(t));check(Revision37Validation.covered(paving,at),"tactile has paving below case "+(id-1)+" at "+at);}
   for(var trim:TactilePaths.trims(spec,plan.pieces().subList(0,spec.arms().size()).stream().map(JunctionPlanner.Piece::mesh).toList(),plan.boundary()))check(Revision37Validation.covered(tactile,trim.point()),"tactile reaches supported crossing terminal");
   var boundary=plan.boundary();for(int arm=0;arm<spec.arms().size();arm++){var mouth=plan.pieces().get(arm).mesh().last();for(int side:new int[]{-1,1}){V edge=mouth.at(side*mouth.halfWidth(),0);int at=0;for(int k=0;k<boundary.size();k++)if(boundary.get(k).distance(edge)<boundary.get(at).distance(edge))at=k;
     V forward=mouth.left().left().mul(-1);for(int direction:new int[]{-1,1}){V near=boundary.get(Math.floorMod(at+direction,boundary.size())).sub(edge);if(Math.abs(near.dot(mouth.left()))>mouth.halfWidth())continue;check(near.dot(forward)>-.025,"no curb backtracking at unequal-width mouth");}}}
  }
 }
 public static void main(String[] args)throws Exception{lamps();parkingAndBridge();models();junctions();System.out.println("Hotfix371 PASS "+checks+" checks");}
}
