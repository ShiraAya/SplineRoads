package com.sora.splineroads;
import com.sora.splineroads.core.*;
import com.sora.splineroads.core.RoadGeometry.*;
import com.sora.splineroads.core.RoadStructures.*;
import java.util.*;
import java.nio.file.*;
public final class Hotfix372Validation {
 static int checks;
 static void check(boolean ok,String why){checks++;if(!ok)throw new AssertionError(why);}
 static Ground floor(double y){return Hotfix371Validation.floor(y);}
 static Mesh mesh(Style style,Structure structure,boolean cycle,boolean walk){return Hotfix371Validation.mesh(Hotfix371Validation.settings(style,Hotfix371Validation.options(cycle,walk,RoadStreetscape.Separator.RAIL,false),structure));}
 static void deck(){
  for(var style:List.of(Style.O2_ONE,Style.O4_GREEN,Style.H6_GREEN)){
   var m=mesh(style,Structure.BRIDGE,false,false);var geometry=RoadSurface.build(m,List.of(),List.of());
   int bottoms=0,walls=0;for(var f:geometry.pavement()){
    var n=RoadLighting.normal(f);if(f.points().stream().allMatch(v->v.y()<m.first().center().y()-.5)){bottoms++;check(n.y()<-.8&&f.texture()==RoadSurface.Texture.CONCRETE&&f.color()==0xB9B9AD,"underside matches girder concrete");}
    else if(Math.abs(n.y())<.1){walls++;check(f.texture()==RoadSurface.Texture.CONCRETE,"deck side wall is concrete");}
   }check(bottoms>0&&walls>0,"bottom and side geometry exercised");
   for(var piece:RoadRenderMesh.sections(geometry,List.of()).values()){
    check(piece.pavement().stream().allMatch(f->f.texture()==RoadSurface.Texture.PLAIN&&RoadLighting.normal(f).y()>.8),"asphalt render batch contains top faces only");
    for(var faces:List.of(piece.detail(),piece.distant()))check(faces.stream().anyMatch(f->f.texture()==RoadSurface.Texture.CONCRETE&&RoadLighting.normal(f).y()<-.8),"near/far concrete batch retains underside");
   }
   var rims=RoadStructures.edgeSlabs(m,floor(-8));check(!rims.isEmpty(),"ordinary bridge rim generated");
   for(int side:new int[]{-1,1}){var at=RoadStructures.sample(m,36);check(Revision37Validation.covered(rims,at.at(side*(at.halfWidth()+.2),.5)),"continuous outboard concrete slab side="+side);}
  }
  check(RoadStructures.edgeSlabs(mesh(Style.O4_GREEN,Structure.GROUND,false,false),floor(2)).isEmpty(),"ground roads do not gain bridge rims");
 }
 static void median(){
  for(var style:List.of(Style.O4_GREEN,Style.O6_GREEN,Style.H6_GREEN)){
   var m=mesh(style,Structure.BRIDGE,false,false);var l=RoadProfile.layout(m,m.first());check(l.median()==1,"green bridge median compacts to rail width");
   var paint=RoadSurface.build(m,List.of(),List.of()).markings();for(int side:new int[]{-1,1}){
    var p=RoadStructures.sample(m,5.1);check(paint.stream().anyMatch(f->JunctionPaint.inside(f.points(),p.at(side*.62,0))),"white line follows actual rail median");
    check(paint.stream().noneMatch(f->JunctionPaint.inside(f.points(),p.at(side*1.62,0))),"old green median paint removed");
   }
   double width=l.motorMax()-l.motorMin();check(Math.abs(width-l.median()-l.laneWidth()*l.catalog().lanes())<1e-8,"freed median width apportioned consistently among lanes");
   check(RoadProfile.layout(mesh(style,Structure.GROUND,false,false),m.first()).median()==3,"ground green median remains authored width");
  }
 }
 static void mounting(){
  for(double angle:new double[]{0,30,45,70,90})for(var style:List.of(Style.O4_YELLOW,Style.O3_ONE))for(var gantry:List.of(RoadInfrastructure.Gantry.SIGNS,RoadInfrastructure.Gantry.FRAME)){
   var original=mesh(style,Structure.BRIDGE,false,false);double a=Math.toRadians(angle);var dir=new V(Math.cos(a),0,Math.sin(a));
   var nodes=new ArrayList<Sample>();for(var s:original.samples())nodes.add(new Sample(dir.mul(s.distance()).add(new V(.3,12,.7)),dir.left(),s.distance(),s.halfWidth()));
   var o=original.settings().options().outerRail(RoadProfile.OuterRail.SOUND_BOTH).infrastructure(original.settings().options().infrastructure().gantry(gantry));
   var m=RoadRibbon.mesh(nodes,original.settings().options(o));var parts=new ArrayList<>(RoadStreetscape.plan(m,floor(-8),RoadFurniture.Phase.DEFAULT));parts.addAll(RoadGantry.plan(m,floor(-8)));
   int supports=0;for(var p:parts)if(p.material()==Material.CONCRETE){var base=p.base();double min=base.stream().mapToDouble(v->RoadQueries.horizontal(m,v).lateral()).min().orElseThrow(),max=base.stream().mapToDouble(v->RoadQueries.horizontal(m,v).lateral()).max().orElseThrow();
    for(int side:new int[]{-1,1}){double line=side*(m.first().halfWidth()-.3);if(min<line+.075&&max>line-.075){supports++;for(var v:base)check(v.y()+p.height()<RoadQueries.horizontal(m,v).sample().center().y()-.06,"mounting bracket recessed below edge stripe angle="+angle+" "+style+" "+gantry);}}
   }check(supports>0,"cross-deck lamp/gantry supports exercised");
  }
 }
 static void tactile()throws Exception{
  var cases=List.of(Hotfix371Validation.fixture(new double[]{0,100,235},new int[]{4,6,4},new int[]{5,0,7}),Hotfix371Validation.fixture(new double[]{0,65,180,260},new int[]{2,6,4,2},new int[]{5,7,5,7}),Hotfix371Validation.fixture(new double[]{0,40,180},new int[]{2,4,4},new int[]{7,7,7}),Hotfix371Validation.fixture(new double[]{0,90,220},new int[]{4,4,4},new int[]{3,7,0}));
  int id=0;Files.createDirectories(Path.of("build/validation372"));
  for(var spec:cases){var plan=JunctionPlanner.plan(spec);Hotfix353Validation.dump(plan,Path.of("build/validation372/junction-"+id+".svg"));
   var all=plan.pieces().stream().flatMap(p->p.structures().stream()).toList();var paving=all.stream().filter(p->p.material().name().startsWith("WALK_")).toList();var tactile=all.stream().filter(p->p.material()==Material.TACTILE&&p.height()<.018).toList();
   for(var p:tactile)for(var v:p.base())check(Revision37Validation.covered(paving,v),"full tactile width stays paved case="+id+" at="+v);
   var approaches=plan.pieces().subList(0,spec.arms().size()).stream().map(JunctionPlanner.Piece::mesh).toList();
   for(var trim:TactilePaths.trims(spec,approaches,plan.boundary()))for(double along:new double[]{-.025,.025})for(double across:new double[]{-.27,0,.27}){V probe=trim.point().add(trim.forward().mul(along)).add(trim.forward().left().mul(across));check(Revision37Validation.covered(tactile,probe),"tactile seam seals full width case="+id+" at="+probe);}
   var corner=TactilePaths.junction(spec,approaches,plan.boundary(),true).stream().filter(p->p.height()<.018&&!p.pier()).toList();
   double signed=0,absolute=0;Part previous=null;
   for(var p:corner){if(previous==null||previous.b().distance(p.a())>.001){check(Math.abs(signed)<Math.toRadians(30)||absolute-Math.abs(signed)<Math.toRadians(40),"corner has no reverse-turn hook case="+id);signed=0;absolute=0;}else{V a=previous.b().sub(previous.a()).horizontalUnit(),b=p.b().sub(p.a()).horizontalUnit();double angle=Math.atan2(a.x()*b.z()-a.z()*b.x(),a.dot(b));signed+=angle;absolute+=Math.abs(angle);}
    previous=p;}
   check(Math.abs(signed)<Math.toRadians(30)||absolute-Math.abs(signed)<Math.toRadians(40),"last corner has no reverse-turn hook case="+id);id++;
  }
 }
 public static void main(String[] args)throws Exception{deck();median();mounting();tactile();System.out.println("Hotfix372 PASS "+checks+" checks");}
}
