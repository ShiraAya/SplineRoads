package com.sora.splineroads;
import com.sora.splineroads.core.*;
import com.sora.splineroads.core.RoadGeometry.*;
import com.sora.splineroads.core.RoadStructures.*;
import com.sora.splineroads.core.JunctionSpec.*;
import java.util.*;
import java.nio.file.*;
public final class Revision37Validation {
 static int checks;
 static void check(boolean ok,String text){checks++;if(!ok)throw new AssertionError(text);}
 static boolean covered(List<Part> parts,V point){
  for(var p:parts){var polygon=p.base();if(JunctionPaint.inside(polygon,point))return true;
   for(int k=0;k<polygon.size();k++){V a=polygon.get(k),b=polygon.get((k+1)%polygon.size()),d=b.sub(a),q=point.sub(a);double t=Math.max(0,Math.min(1,(q.x()*d.x()+q.z()*d.z())/Math.max(1e-12,d.x()*d.x()+d.z()*d.z())));if(point.sub(a.add(d.mul(t))).horizontalLength()<.0001)return true;}
  }return false;
 }
 static Ground floor(double height){return new Ground(){public double top(double x,double z,double y){return height;}public boolean blocked(Part p){return false;}public boolean joined(V p){return false;}};}
 static Settings setting(Style style,RoadProfile.Options o,Structure structure){return new Settings(Mode.STRAIGHT,style,RoadProfile.width(style,o,4),1,.4,90).options(o).structure(structure);}
 static Mesh mesh(Settings s){return RoadGeometry.build(new Node(new V(0,2,0),-90,0),new Node(new V(140,2,0),-90,0),s);}
 static boolean foliage(Part p){return p.material().name().endsWith("_LEAVES")||p.material()==Material.FLOWERS;}
 static List<Part> bases(List<Part> p){return p.stream().filter(x->x.material()==Material.CONCRETE&&Math.abs(x.width()-.38)<1e-9&&x.height()==.16).toList();}
 public static void main(String[] args)throws Exception{
  var walk=RoadSidewalks.Config.DEFAULT.enabled(true).width(2);
  var o=RoadProfile.Options.DEFAULT.cycleFinish(RoadProfile.Options.CycleFinish.GREEN).sidewalk(walk).streetscape(RoadStreetscape.Config.DEFAULT.separator(RoadStreetscape.Separator.GREEN).planting(RoadStreetscape.Planting.OAK).walkLamps(true).lampSpacing(32).walkLampSpacing(40));
  check(o.sidewalk().width()>=7,"automatically reserves separate tree, walking lamp and tactile rows");
  check(o.hideArrows(true).traffic(true).lift(.5,3).sidewalk(o.sidewalk()).outerRail(o.outerRail()).ends(o.ends()).infrastructure(o.infrastructure()).route(o.routing()).laneLines(List.of()).streetscape().equals(o.streetscape()),"settings copies retain all amenities");
  for(var planting:RoadStreetscape.Planting.values()){
   var options=o.streetscape(o.streetscape().planting(planting));var m=mesh(setting(Style.O4_YELLOW,options,Structure.GROUND));
   var parts=RoadStructures.plan(m,floor(2));check(!bases(parts).isEmpty(),"ground lamps generated");
   if(planting!=RoadStreetscape.Planting.NONE)check(parts.stream().anyMatch(Revision37Validation::foliage),"planting generated "+planting);
   var tactile=parts.stream().filter(p->p.material()==Material.TACTILE&&p.height()<.018).toList();check(!tactile.isEmpty(),"outer tactile generated");
   for(var p:tactile){var q=RoadQueries.horizontal(m,p.a().add(p.b()).mul(.5));check(Math.abs(q.lateral())>q.sample().halfWidth()+options.sidewalk().width()-1.2,"tactile at outside");}
   for(var p:parts)if(foliage(p)||bases(List.of(p)).size()>0){double bottom=p.a().y(),top=bottom+p.height();if(bottom<4&&top>2.2)for(var t:tactile){double overlap=RoadSurface.area(p.base())-RoadSurface.subtract(p.base(),t.base()).stream().mapToDouble(RoadSurface::area).sum();check(overlap<1e-6,"amenity clears tactile "+p.material());}}
   var bridge=RoadStructures.plan(mesh(setting(Style.O4_YELLOW,options,Structure.BRIDGE)),floor(-2));check(bridge.stream().noneMatch(Revision37Validation::foliage),"no sidewalk planting on bridges");
   check(!bases(bridge).isEmpty(),"bridge lamps retained");
   check(RoadStreetscape.plan(mesh(setting(Style.O4_YELLOW,options,Structure.TUNNEL)),floor(2),RoadFurniture.Phase.DEFAULT).isEmpty(),"no street/walk lamps or plants in tunnel");
  }
  for(double angle:new double[]{0,30,45,70,90})for(var kind:List.of(RoadInfrastructure.Gantry.AUTO,RoadInfrastructure.Gantry.FRAME)){
   double t=Math.toRadians(angle);V direction=new V(Math.cos(t),0,Math.sin(t));
   var options=RoadProfile.Options.DEFAULT.infrastructure(RoadInfrastructure.Config.DEFAULT.gantry(kind));
   var s=setting(Style.O4_YELLOW,options,Structure.BRIDGE);
   var m=RoadGeometry.build(new Node(new V(.37,12,.19),RoadPlanner.yaw(direction),0),new Node(new V(.37,12,.19).add(direction.mul(140)),RoadPlanner.yaw(direction),0),s);
   var parts=RoadStructures.plan(m,floor(1));int feet=0;
   for(var p:parts)if(p.pier()&&p.material()==Material.CONCRETE&&(p.width()==1.2&&p.height()==.45||p.width()==.38&&p.height()==.16)){
    feet++;for(V v:p.base()){var q=RoadQueries.horizontal(m,v);check(Math.abs(q.lateral())>q.sample().halfWidth()+.02,"whole gantry/lamp pedestal outside edge line at "+angle);}
   }check(feet>=2,"gantry feet and bridge lamps retained at "+angle);
  }
  var green=RoadProfile.Options.DEFAULT.cycleFinish(RoadProfile.Options.CycleFinish.GREEN).streetscape(RoadStreetscape.Config.DEFAULT.separator(RoadStreetscape.Separator.GREEN));
  var plain=RoadProfile.Options.DEFAULT.cycleFinish(RoadProfile.Options.CycleFinish.GREEN);
  check(RoadProfile.width(Style.O4_YELLOW,green,4)-RoadProfile.width(Style.O4_YELLOW,plain,4)==3,"separator expands total width without stealing motor/cycle lane width");
  var both=mesh(setting(Style.O4_GREEN,green,Structure.GROUND));var bparts=RoadStructures.plan(both,floor(2));
  check(bases(bparts).stream().anyMatch(p->Math.abs(RoadQueries.horizontal(both,p.a()).lateral())<.1),"style one in central green");
  check(bases(bparts).stream().anyMatch(p->Math.abs(RoadQueries.horizontal(both,p.a()).lateral())>2),"style two retained alongside central lamps");
  var parking=green.cycleFinish(RoadProfile.Options.CycleFinish.PARKING);
  check(!parking.cycleRail()&&parking.cycleAsphalt()&&parking.streetscape().separator()==RoadStreetscape.Separator.LINE,"parking locks asphalt and white separation");
  var parkMesh=mesh(setting(Style.O4_YELLOW,parking,Structure.GROUND));var paint=RoadSurface.build(parkMesh,List.of(),List.of());
  check(paint.markings().stream().noneMatch(f->f.color()==0x536F61),"parking has no green pavement");
  check(paint.markings().size()>RoadSurface.build(mesh(setting(Style.O4_YELLOW,plain.cycleFinish(RoadProfile.Options.CycleFinish.ASPHALT),Structure.GROUND)),List.of(),List.of()).markings().size(),"parking bay lines actually rendered");
  check(RoadProfile.layout(setting(Style.O4_YELLOW,green,Structure.GROUND),RoadProfile.width(Style.O4_YELLOW,green,4)).laneWidth()==4,"motor width preserved");
  for(int style=1;style<=4;style++){var lamp=RoadStreetscape.lamp(new V(0,0,0),new V(1,0,0),style);long heads=lamp.stream().filter(p->p.material()==Material.LAMP).count();check(heads==(style<=2?2:1),"lamp head count style "+style);check(lamp.stream().flatMap(p->p.faces().stream()).allMatch(f->f.points().stream().allMatch(v->RoadGeometry.finite(v.x(),v.y(),v.z()))),"finite lamp geometry");}
  for(var style:List.of(Style.H4_RAIL,Style.H6_GREEN))check(bases(RoadStructures.plan(mesh(setting(style,o,Structure.GROUND)),floor(2))).isEmpty(),"highway has no lamps");
  var phase=new RoadFurniture.Phase(0,1).shift(75);check(Math.abs(phase.first(8,32)-29)<1e-8,"custom spacing phase survives beyond 24m");
  for(var pattern:List.of(RoadLaneLines.Pattern.LEFT_SOLID_RIGHT_DASHED,RoadLaneLines.Pattern.LEFT_DASHED_RIGHT_SOLID)){
   var line=List.of(new RoadLaneLines.Edit("divider:0",pattern,.12));var m=mesh(setting(Style.O4_YELLOW,RoadProfile.Options.DEFAULT.laneLines(line),Structure.GROUND));
   double center=RoadProfile.layout(m,RoadStructures.sample(m,4)).dividers().get(0);var faces=RoadSurface.build(m,List.of(),List.of()).markings();
   for(int side:new int[]{-1,1}){V point=RoadStructures.sample(m,4).at(center+side*.18,0);boolean covered=faces.stream().anyMatch(f->JunctionPaint.inside(f.points(),point));check(covered!=pattern.dashedSide(side),"solid-dashed orientation "+pattern+" side "+side);}
  }
  int caseId=0;
  for(boolean skew:new boolean[]{false,true})for(boolean round:new boolean[]{false,true}){
   var spec=Hotfix353Validation.junction(skew,false);var arms=new ArrayList<Arm>();for(var arm:spec.arms()){var ex=arm.external().options(arm.external().options().sidewalk(RoadSidewalks.Config.DEFAULT.enabled(true).width(5)));arms.add(new Arm(arm.endpoint(),arm.inward(),ex,arm.incoming(),arm.outgoing(),arm.width(),arm.median(),arm.medianKind(),arm.cycleWidth(),arm.curbWidth(),true,4,3,1.5,arm.phase(),arm.lanes(),false));}
   spec=new JunctionSpec(spec.center(),round?Kind.ROUNDABOUT:Kind.INTERSECTION,false,8,24,2,4,1,Control.SIGNALS,20,3,1,0,true,true,arms);
   var plan=JunctionPlanner.plan(spec);Hotfix353Validation.dump(plan,Path.of("build/validation37/junction-"+caseId+++".svg"));
   var approaches=plan.pieces().subList(0,arms.size()).stream().map(JunctionPlanner.Piece::mesh).toList();
   for(var p:plan.pieces())for(var part:p.structures())if(part.material()==Material.CB_BASE){check(!JunctionPaint.inside(plan.boundary(),part.a()),"signal foot outside central union");for(var a:approaches)check(!RoadSidewalks.overlapsDeck(part,a),"complete signal pedestal clears all arms");}
   var tactile=plan.pieces().stream().flatMap(p->p.structures().stream()).filter(p->p.material()==Material.TACTILE&&p.height()<.018).toList();
   for(int i=0;i<arms.size();i++)for(int side:new int[]{-1,1}){var sample=approaches.get(i).last();V seam=sample.at(side*(sample.halfWidth()+4.2),-.2);{for(var trim:TactilePaths.trims(spec,approaches,plan.boundary()))if(trim.arm()==i&&trim.side()==side)seam=trim.point().add(new V(0,.2,0));}check(covered(tactile,seam),"tactile join physically covered "+round+" skew "+skew+" arm "+i+" side "+side+" at "+seam);
     for(double d=0;d<=2;d+=.1){V p=seam.add(sample.left().left().mul(d));check(covered(tactile,p),"continuous approach to tactile join");}}
  }
  System.out.println("Revision37 PASS "+checks+" checks");
 }
}
