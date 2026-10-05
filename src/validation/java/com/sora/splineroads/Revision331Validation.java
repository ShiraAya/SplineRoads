package com.sora.splineroads;
import com.sora.splineroads.core.*;
import com.sora.splineroads.core.RoadGeometry.*;
import com.sora.splineroads.core.RoadStructures.*;
import java.util.*;
public final class Revision331Validation {
 static int checks;static void check(boolean b,String s){checks++;if(!b)throw new AssertionError(s);}
 public static void main(String[] args)throws Exception {
  for(int angle:new int[]{25,35,45,60,90})for(int sign:new int[]{-1,1}){
   double a=Math.toRadians(180-angle);V u=new V(1,0,0),v=new V(Math.cos(a),0,sign*Math.sin(a));
   var route=TactilePaths.insetBend(List.of(u.mul(-20),new V(0,0,0),v.mul(20)),u,v,2.5);
   check(route.get(0).equals(u.mul(-20))&&route.get(route.size()-1).equals(v.mul(20)),"bend keeps both connections");
   check(route.stream().mapToDouble(V::horizontalLength).min().orElseThrow()>1,"bend turns before island tip");
   for(int i=1;i<route.size()-1;i++){V before=route.get(i).sub(route.get(i-1)).horizontalUnit(),after=route.get(i+1).sub(route.get(i)).horizontalUnit();check(before.dot(after)>.99,"smooth tactile tangent");}
  }
  var walk=new RoadSidewalks.Config(true,RoadSidewalks.Side.BOTH,5,"minecraft:stone_bricks");
  java.nio.file.Path folder=java.nio.file.Path.of("validation/0.33.1");java.nio.file.Files.createDirectories(folder);
  for(int angle:new int[]{25,35,60,90}){
   var spec=Junction22Validation.fixture(Style.O4_YELLOW,false,0,angle,180);var arms=new ArrayList<>(spec.arms());
   for(int k=0;k<arms.size();k++){var arm=arms.get(k);arms.set(k,JunctionSpec.arm(arm.endpoint(),arm.inward(),arm.external().options(arm.external().options().sidewalk(walk)),true,k));}
   spec=spec.arms(arms);
   for(int version:new int[]{33,34}){
    var method=JunctionPlanner.class.getDeclaredMethod("plan",JunctionSpec.class,int.class);method.setAccessible(true);var plan=(JunctionPlanner.Plan)method.invoke(null,spec,version);
    if(version==34){
     var approaches=plan.pieces().subList(0,spec.arms().size()).stream().map(JunctionPlanner.Piece::mesh).toList();
     if(angle<=35)check(!TactilePaths.trims(spec,approaches,plan.boundary()).isEmpty(),"acute bend extends into approach sidewalks");
     var slabs=plan.pieces().stream().flatMap(piece->piece.structures().stream()).filter(part->part.material().name().startsWith("WALK_")).toList();
     for(var piece:plan.pieces())if(piece.arm()<0)for(var part:piece.structures())if(part.material()==Material.TACTILE){
      V mid=part.a().add(part.b()).mul(.5);check(slabs.stream().anyMatch(slab->JunctionPaint.inside(slab.base(),mid)),"corner tactile stays on sidewalk at "+angle+" "+mid);
     }
    }
    var svg=new StringBuilder("<svg xmlns='http://www.w3.org/2000/svg' viewBox='-100 -100 200 200'><rect x='-100' y='-100' width='200' height='200' fill='#426044'/>");
    for(var piece:plan.pieces())for(var face:RoadSurface.build(piece.mesh(),List.of(),List.of()).pavement())Revision33Validation.polygon(svg,face.points(),"#41474c");
    for(var piece:plan.pieces())for(var part:piece.structures())if(part.material().name().startsWith("WALK_"))Revision33Validation.polygon(svg,part.base(),"#a5adb1");
    for(var piece:plan.pieces())for(var part:piece.structures())if(part.material()==Material.TACTILE)Revision33Validation.polygon(svg,part.base(),"#f3ca36");
    java.nio.file.Files.writeString(folder.resolve("corner-"+angle+"-v"+version+".svg"),svg.append("</svg>").toString());
   }
  }
  System.out.println("Revision331 PASS "+checks+" bend geometry checks");
 }
}
