package com.sora.splineroads;
import com.sora.splineroads.core.*;
import com.sora.splineroads.core.RoadGeometry.*;
import com.sora.splineroads.core.RoadStructures.*;
import com.sora.splineroads.core.JunctionSpec.*;
import java.util.*;
import java.nio.file.*;
public final class Hotfix353Validation {
 static int checks;static void check(boolean ok,String text){checks++;if(!ok)throw new AssertionError(text);}
 static final Ground FLOOR=Hotfix352Validation.FLOOR;
 static Settings settings(Style style){return new Settings(Mode.STRAIGHT,style,style.defaultWidth(),1,.4,90);}
 static Mesh mesh(Settings s){return RoadGeometry.build(new Node(new V(0,12,0),-90,0),new Node(new V(100,12,0),-90,0),s);}
 public static JunctionSpec junction(boolean skew,boolean left){
  var arms=new ArrayList<Arm>();var angles=skew?new double[]{0,100,235}:new double[]{0,90,180,270};
  for(int i=0;i<angles.length;i++){
   double angle=Math.toRadians(angles[i]);V outward=new V(Math.cos(angle),0,Math.sin(angle));
   var s=settings(i==1?Style.O6_RAIL:Style.O4_YELLOW);s=s.options(s.options().sidewalk(new RoadSidewalks.Config(i!=1,RoadSidewalks.Side.BOTH,i==2?7:5,"minecraft:stone_bricks")));
   arms.add(JunctionSpec.arm(new Node(outward.mul(100).add(new V(0,2,0)),0,0),outward.mul(-1),s,true,i));
  }
  return new JunctionSpec(new V(0,2,0),Kind.INTERSECTION,left,8,12,1,4,1,Control.SIGNALS,20,3,1,0,true,true,arms);
 }
 public static void main(String[] args)throws Exception{
  for(boolean skew:new boolean[]{false,true})for(boolean left:new boolean[]{false,true}){
   var spec=junction(skew,left);var plan=JunctionPlanner.plan(spec);var approaches=plan.pieces().subList(0,spec.arms().size()).stream().map(JunctionPlanner.Piece::mesh).toList();int bases=0;
   for(var piece:plan.pieces())for(var p:piece.structures())if(p.material()==Material.CB_BASE){bases++;
    for(V v:p.base()){check(!JunctionPaint.inside(plan.boundary(),v),"signal base stays outside junction "+skew+" "+v);for(var m:approaches)check(!RoadQueries.contains(m,v,.02,.6),"signal base stays outside approach skew="+skew+" left="+left+" base="+p+" corner="+v+" projection="+RoadQueries.horizontal(m,v));}
   }check(bases>=spec.arms().size(),"signals generated");
   var walks=plan.pieces().stream().flatMap(p->p.structures().stream()).filter(p->p.material().name().startsWith("WALK_")).toList();
   check(!walks.isEmpty(),"mixed sidewalks exist");
   var corner=RoadSidewalks.taperedJunctionParts(spec,approaches,plan.boundary()).stream().filter(p->p.material().name().startsWith("WALK_")).toList();
   check(corner.stream().anyMatch(p->p.frameA()!=null&&p.frameB()!=null&&Math.abs(p.frameA().horizontalLength()-p.frameB().horizontalLength())>.01),"corner actually tapers");
   check(corner.stream().anyMatch(p->p.frameA().horizontalLength()<1e-8||p.frameB().horizontalLength()<1e-8),"disabled mouth ends at zero width");
   for(var p:walks)for(var m:approaches)check(!RoadSidewalks.overlapsDeck(p,m),"walkway never enters approach");
   if(skew&&!left&&args.length>0)dump(plan,Path.of(args[0]));
  }
  for(var rail:List.of(RoadProfile.OuterRail.SOUND_LEFT,RoadProfile.OuterRail.SOUND_RIGHT,RoadProfile.OuterRail.SOUND_BOTH)){
   var s=settings(Style.O6_GREEN);s=s.options(s.options().outerRail(rail));var m=mesh(s);var parts=RoadStructures.plan(m,FLOOR);int lamps=0;
   for(var p:parts)if(p.material()==Material.CONCRETE&&p.width()==.4&&p.height()==.18){lamps++;var q=RoadQueries.horizontal(m,p.a().add(p.b()).mul(.5));int side=q.lateral()<0?-1:1;
    if(rail.sound(side))check(Math.abs(q.lateral())>=q.sample().halfWidth()+.49,"lamp base outside selected noise wall");
    else check(Math.abs(q.lateral())<q.sample().halfWidth(),"unselected lamp stays on ordinary edge");
   }check(lamps>=4,"streetlamps not omitted to hide clipping");
   for(var panel:parts)if(panel.material()==Material.CB_NOISE)for(var p:parts)if(p.material()==Material.DARK_STEEL&&p.a().y()<14.6){
    double area=Math.abs(JunctionPaint.area(p.base()));double remaining=RoadSurface.subtract(p.base(),panel.base()).stream().mapToDouble(v->Math.abs(JunctionPaint.area(v))).sum();check(area-remaining<1e-6,"lamp shaft does not overlap barrier envelope");
   }
  }
  var m=mesh(settings(Style.O4_RAIL));var normal=RoadStructures.supports(m,FLOOR,RoadFurniture.Phase.DEFAULT);check(normal.stream().filter(p->p.pier()&&p.height()>2).count()>=4,"unobstructed automatic road keeps single piers");
  Ground blocked=new Ground(){public double top(double x,double z,double y){return 1;}public boolean joined(V p){return false;}public boolean blocked(Part p){return Math.abs(p.a().x()-12)<2&&p.pier();}};
  var shifted=RoadStructures.supports(m,blocked,RoadFurniture.Phase.DEFAULT);check(shifted.stream().anyMatch(p->p.pier()&&p.height()>2&&Math.abs(p.a().x()-8)<.01),"obstruction moves shaft along road");
  for(var p:shifted)if(p.pier())check(Math.abs(p.a().z())<.01,"automatic supports never escape sideways into portal legs");
  Ground allBlocked=new Ground(){public double top(double x,double z,double y){return 1;}public boolean joined(V p){return false;}public boolean blocked(Part p){return p.pier()&&Math.abs(p.a().z())<20;}};
  check(RoadStructures.supports(m,allBlocked,RoadFurniture.Phase.DEFAULT).isEmpty(),"blocked station omitted instead of 40m portal");
  for(var style:List.of(Style.O2_YELLOW,Style.O6_RAIL,Style.H6_RAIL,Style.O2_ONE)){
   var s=settings(style);var shown=mesh(s);var hidden=mesh(s.options(s.options().hideArrows(true)));
   check(!RoadJunction.arrows(shown,List.of()).isEmpty(),"baseline arrows exist "+style);check(RoadJunction.arrows(hidden,List.of()).isEmpty(),"hidden arrows absent "+style);
   check(shown.samples().equals(hidden.samples()),"arrow switch preserves geometry");
   var o=hidden.settings().options();check(o.traffic(true).extras(true,true,true).sidewalk(RoadSidewalks.Config.DEFAULT).outerRail(RoadProfile.OuterRail.ON).lift(.5,0).ends(o.ends()).route(o.routing()).infrastructure(o.infrastructure()).laneLines(List.of()).cycleFinish(RoadProfile.Options.CycleFinish.ASPHALT).hideArrows(),"all settings copies retain switch");
  }
  var road=mesh(settings(Style.O6_RAIL));
  var pad=RoadGeometry.build(new Node(new V(50,12,-30),0,0),new Node(new V(50,12,30),0,0),new Settings(Mode.STRAIGHT,Style.UNMARKED,20,1,.4,90));
  var hidden=mesh(road.settings().options(road.settings().options().hideArrows(true)));
  check(RoadSignals.approaches(hidden,List.of(pad)).size()==2,"crossing fixture has two stop bars");
  check(RoadJunction.arrows(hidden,List.of(pad)).size()==2,"hiding arrows preserves both stop bars");
  check(RoadJunction.markings(road,List.of()).equals(RoadJunction.markings(hidden,List.of())),"arrow toggle preserves divider and edge markings");
  var rs=new Settings(Mode.CURVE,Style.R2,Style.R2.defaultWidth(),1,.4,90);
  var r1=RoadGeometry.build(new Node(new V(0,12,0),-90,0),new Node(new V(100,12,30),-70,0),rs);
  var r2=RoadGeometry.build(new Node(new V(0,12,0),-90,0),new Node(new V(100,12,-30),-110,0),rs);
  var rh=new Mesh(r1.samples(),rs.options(rs.options().hideArrows(true)),r1.min(),r1.max(),r1.length(),r1.closed());
  check(!RoadJunction.dividerZones(r1,List.of(r2)).isEmpty(),"fork fixture contains divider suppression zones");
  check(RoadJunction.dividerZones(r1,List.of(r2)).equals(RoadJunction.dividerZones(rh,List.of(r2))),"hidden arrows preserve ramp divider suppression zones");
  System.out.println("Hotfix353 PASS "+checks+" checks");
 }
 static void dump(JunctionPlanner.Plan plan,Path file)throws Exception{
  var shapes=new ArrayList<String>();
  for(var piece:plan.pieces()){
   var samples=piece.mesh().samples();for(int i=1;i<samples.size();i++){var a=samples.get(i-1);var b=samples.get(i);shape(shapes,"#444a50",List.of(a.at(a.halfWidth(),0),a.at(-a.halfWidth(),0),b.at(-b.halfWidth(),0),b.at(b.halfWidth(),0)));}
   for(var p:piece.structures())if(p.material().name().startsWith("WALK_")||p.material()==Material.TACTILE||p.material()==Material.CB_BASE)shape(shapes,p.material()==Material.TACTILE?"#f3cc49":p.material()==Material.CB_BASE?"#ed5a4b":"#aaaaaa",p.base());
  }
  Files.createDirectories(file.getParent());Files.writeString(file,"<svg xmlns=\"http://www.w3.org/2000/svg\" width=\"1000\" height=\"1000\" viewBox=\"-120 -120 240 240\"><rect x=\"-120\" y=\"-120\" width=\"240\" height=\"240\" fill=\"#789860\"/>"+String.join("",shapes)+"</svg>");
 }
 static void shape(List<String> out,String color,List<V> points){out.add("<polygon fill=\""+color+"\" points=\""+String.join(" ",points.stream().map(p->String.format(Locale.ROOT,"%.4f,%.4f",p.x(),p.z())).toList())+"\"/>");}
}
