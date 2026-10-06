package com.sora.splineroads;
import com.sora.splineroads.core.*;
import com.sora.splineroads.core.RoadGeometry.*;
import com.sora.splineroads.core.RoadStructures.*;
import java.util.*;
/** Real core geometry; no GPU, shader compilation or Minecraft client claims. */
public final class Q2Close422Validation {
 static int checks,cases;static void check(boolean v,String m){checks++;if(!v)throw new AssertionError(m);}static void near(double a,double b,double e,String m){check(Math.abs(a-b)<e,m+" "+a+" / "+b);}
 static Settings settings(Structure structure){var o=RoadProfile.Options.DEFAULT;return new Settings(Mode.STRAIGHT,Style.O4_RAIL,RoadProfile.width(Style.O4_RAIL,o,4),1,.35,90).options(o).structure(structure);}
 static Mesh road(double y,Structure structure){return RoadGeometry.build(new Node(new V(0,y,0),0,0),new Node(new V(0,y,300),0,0),settings(structure));}
 public static void main(String[]args){fog();profiles();classification();tactile();overlap();System.out.println("Q2Close422Validation: "+cases+" cases / "+checks+" checks; mixed tactile paving, high-Y fog math, local tangents, bank classification and actual stripe faces. NO Minecraft/GPU.");}
 static void fog(){for(double x:new double[]{0,170000,-170000})for(double y:new double[]{-60,0,350,1800})for(boolean cylinder:new boolean[]{false,true}){
  var camera=new V(x,y,x);var p=camera.add(new V(70,2,30));double expected=cylinder?Math.hypot(70,30):Math.sqrt(70*70+30*30+4);near(RoadFog.distance(p,camera,cylinder),expected,1e-8,"absolute origin leaked into fog");cases++;}
  var camera=new V(0,350,0);var p=new V(70,352,30);double legacy=Math.max(Math.sqrt(p.x()*p.x()+p.z()*p.z()+camera.y()*camera.y()),Math.abs(p.y()-camera.y()));check(legacy>350&&RoadFog.distance(p,camera,true)<80,"high Y symptom not reproduced");System.out.println("  high-Y same-relative point: old entity cylinder="+legacy+", world-relative="+RoadFog.distance(p,camera,true));}
 static void profiles(){for(double length:new double[]{40,100,1000})for(double a:new double[]{-.12,0,.12})for(double b:new double[]{-.12,0,.12}){
  double lo=100,hi=100;near(LaneRampProfile.height(lo,hi,a,b,length,0),100,1e-8,"A moved");near(LaneRampProfile.height(lo,hi,a,b,length,length),100,1e-8,"B moved");double eps=1e-4;near((LaneRampProfile.height(lo,hi,a,b,length,eps)-100)/eps,a,1e-5,"A tangent lost");near((100-LaneRampProfile.height(lo,hi,a,b,length,length-eps))/eps,b,1e-5,"B tangent lost");for(double d=0;d<=length;d+=.5)check(Math.abs(LaneRampProfile.height(lo,hi,a,b,length,d)-100)<.44,"local tangent makes long-span deep sag");cases++;}
  double oldMin=100,newMin=100;for(double t=0;t<=1;t+=.001){double old=(2*t*t*t-3*t*t+1)*100+(t*t*t-2*t*t+t)*(-.12*1000)+(-2*t*t*t+3*t*t)*100;oldMin=Math.min(oldMin,old);newMin=Math.min(newMin,LaneRampProfile.height(100,100,-.12,0,1000,t*1000));}check(oldMin<83&&newMin>99.5,"unnecessary downhill tangent sag not eliminated");System.out.println("  1000-block tangent span: old min="+oldMin+", local min="+newMin);}
 interface Height{double at(double x,double z,double y);}
 static Ground ground(Height h){return new Ground(){public double top(double x,double z,double y){return h.at(x,z,y);}public boolean joined(V p){return false;}public boolean blocked(Part p){return false;}};}
 static void classification(){for(double y:new double[]{2,400})for(int side:new int[]{-1,1}){var mesh=road(y,Structure.AUTO);var at=RoadStructures.sample(mesh,150);
 check(!RoadStructures.elevated(mesh,at,ground((x,z,v)->y-1)),"supported high-Y classified as bridge");
 check(RoadStructures.elevated(mesh,at,ground((x,z,v)->y-10)),"low absolute Y suspended classified ground");
 check(RoadStructures.elevated(mesh,at,ground((x,z,v)->side*x>0?y-1:y-10)),"opposite bank masks unsupported half");
 check(RoadStructures.elevated(mesh,at,ground((x,z,v)->Math.abs(x)<.6?y-1:y-10)),"narrow construction spine treated as full support");
 check(!RoadStructures.elevated(mesh,at,ground((x,z,v)->Math.abs(z-150)<.2?y-10:y-1)),"one longitudinal marker hole classified bridge");
 check(RoadStructures.elevated(road(y,Structure.BRIDGE),at,ground((x,z,v)->y)),"explicit bridge ignored");check(!RoadStructures.elevated(road(y,Structure.GROUND),at,ground((x,z,v)->y-20)),"explicit ground ignored");cases+=7;}}
 static void tactile(){for(double angle:new double[]{80,95,130})for(int width:new int[]{3,8})for(boolean mirror:new boolean[]{false,true}){
  var spec=TactileTunnel409Validation.fixture(new double[]{0,angle,180,270},5,8,0,mirror);var arms=new ArrayList<>(spec.arms());var a=arms.get(0);var opts=a.external().options().sidewalk(a.external().options().sidewalk().width(width));arms.set(0,a.node(a.endpoint(),a.inward(),a.external().options(opts)));spec=spec.arms(arms);var plan=JunctionPlanner.plan(spec);
  var approaches=plan.pieces().subList(0,4).stream().map(JunctionPlanner.Piece::mesh).toList();var parts=plan.pieces().stream().flatMap(p->p.structures().stream()).toList();var rows=parts.stream().filter(p->p.material()==Material.TACTILE&&p.height()<.018).toList();var corners=rows.stream().filter(p->!p.pier()).toList();check(!rows.isEmpty(),"deleted tactile rows to hide defect");check(TactileTunnel409Validation.reversal(corners)<.1,"mixed widths still produce inward S-notch");var floor=new TactileSurface(parts);
  for(var row:rows)for(var vertex:row.base()){check(Double.isFinite(floor.height(vertex)),"unsupported tactile vertex");check(!JunctionPaint.inside(plan.boundary(),vertex),"tactile/backing crosses asphalt boundary");}
  for(var trim:TactilePaths.trims(spec,approaches,plan.boundary()))for(double d=0;d<=2;d+=.1)check(TactileSupport411Validation.covered(rows,trim.point().sub(trim.forward().mul(d))),"corner disconnects from approach strip");cases++;System.out.println("  mixed width="+width+" angle="+angle+" mirrored="+mirror+" reversal="+TactileTunnel409Validation.reversal(corners));
 }}
 static double dividerArea(RoadSurface.Geometry geometry,double x){double area=0;for(var f:geometry.markings())if(f.points().stream().allMatch(p->Math.abs(p.x()-x)<.2)){var ps=f.points();double a=0;for(int i=0;i<ps.size();i++){V p=ps.get(i),q=ps.get((i+1)%ps.size());a+=p.x()*q.z()-q.x()*p.z();}area+=Math.abs(a)/2;}return area;}
 static void overlap(){var host=road(100,Structure.GROUND);var point=LanePoints.point(new UUID(2,1),LanePoints.Origin.MANUAL,host,150,1);var metadata=LanePoints.Data.EMPTY.points(List.of(point));host=RoadRibbon.mesh(host.samples(),host.settings().options(host.settings().options().lanePoints(metadata)));
 var options=LanePoints.Options.DEFAULT;var link=new LanePoints.Link(LanePoints.Ref.lane(new UUID(1,1),point.id()),LanePoints.Ref.lane(new UUID(1,2),new UUID(2,2)),options,null);var base=new Settings(Mode.STRAIGHT,Style.C1_RAMP,4,1,.35,90);var overlap=RoadGeometry.build(new Node(new V(0,100,80),0,0),new Node(new V(0,100,220),0,0),base);double line=RoadProfile.layout(host,RoadStructures.sample(host,150)).dividers().get(0);var moved=overlap.samples().stream().map(s->new Sample(s.center().add(new V(-line,0,0)),s.left(),s.distance(),s.halfWidth())).toList();overlap=RoadRibbon.mesh(moved,base.options(base.options().lanePoints(LanePoints.Data.EMPTY.link(link))));
 var original=RoadSurface.build(host,List.of(),List.of());var clipped=RoadSurface.build(host,List.of(),List.of(overlap));check(dividerArea(clipped,-line)<dividerArea(original,-line)*.75,"real link overlap leaves original divider unchanged");
 var unlinked=RoadRibbon.mesh(overlap.samples(),base);near(dividerArea(RoadSurface.build(host,List.of(),List.of(unlinked)), -line),dividerArea(original,-line),1e-7,"unlinked neighbour erases host dividers");
 var custom=RoadRibbon.mesh(host.samples(),host.settings().options(host.settings().options().laneLines(List.of(new RoadLaneLines.Edit("divider:0",RoadLaneLines.Pattern.WHITE_DASHED,.12)))));
 near(dividerArea(RoadSurface.build(custom,List.of(),List.of(overlap)),-line),dividerArea(original,-line),1e-7,"explicit dashed override lost");
 var raised=RoadRibbon.mesh(overlap.samples().stream().map(s->new Sample(s.center().add(new V(0,8,0)),s.left(),s.distance(),s.halfWidth())).toList(),overlap.settings());check(RoadSurface.build(host,List.of(),List.of(raised)).markings().equals(original.markings()),"overpass clips markings through height");cases+=3;
 }
}
