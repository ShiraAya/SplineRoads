package com.sora.splineroads;
import com.sora.splineroads.core.*;
import com.sora.splineroads.core.RoadGeometry.*;
import com.sora.splineroads.core.JunctionSpec.*;
import java.util.*;
public final class Separated35Validation {
 static int checks;static void check(boolean ok,String s){checks++;if(!ok)throw new AssertionError(s);}
 static V rotated(V v,double degrees){double t=Math.toRadians(degrees);return new V(v.x()*Math.cos(t)-v.z()*Math.sin(t),v.y(),v.x()*Math.sin(t)+v.z()*Math.cos(t));}
 static Settings road(Style style,boolean walk){var s=new Settings(Mode.STRAIGHT,style,style.defaultWidth(),1,.4,90);return s.options(s.options().sidewalk(new RoadSidewalks.Config(walk,RoadSidewalks.Side.BOTH,5,"minecraft:stone_bricks")));}
 static JunctionSpec spec(int extras,boolean left,boolean walk,double angle){
  var arms=new ArrayList<Arm>();V center=new V(3161,2,1030);int side=left?-1:1;
  var positions=new ArrayList<>(List.of(new V(0,0,150),new V(side*22,0,-150),new V(-side*22,0,-150)));
  var inward=new ArrayList<>(List.of(new V(0,0,-1),new V(0,0,1),new V(0,0,1)));
  var styles=new ArrayList<>(List.of(Style.O6_YELLOW,Style.O1_ONE,Style.O2_ONE));
  if(extras>=1){positions.add(new V(-150,0,0));inward.add(new V(1,0,0));styles.add(Style.O4_YELLOW);}
  if(extras>=2){positions.add(new V(150,0,0));inward.add(new V(-1,0,0));styles.add(Style.O2_YELLOW);}
  for(int i=0;i<positions.size();i++){V p=center.add(rotated(positions.get(i),angle)),dir=rotated(inward.get(i),angle);arms.add(JunctionSpec.arm(new Node(p,YJunctionPlanner.yaw(dir),0),dir,road(styles.get(i),walk),i!=1,i).attached(true));}
  return new JunctionSpec(center,Kind.INTERSECTION,left,6,12,1,4,1,Control.SIGNALS,20,3,1,0,true,true,arms);
 }
 public static void main(String[] args)throws Exception{
  var folder=java.nio.file.Path.of("validation/0.35.0");java.nio.file.Files.createDirectories(folder);
  for(int extra:new int[]{0,1,2})for(boolean left:new boolean[]{false,true})for(boolean walk:new boolean[]{false,true})for(double angle:new double[]{0,37}){
   var spec=spec(extra,left,walk,angle);var p=JunctionPlanner.plan(spec);check(JunctionPlanner.separatedEntrances(spec),"paired mouths detected");
   check(p.pieces().stream().filter(piece->piece.arm()>=0).count()==3+extra,"each road has separate mouth");
   Mesh b=p.pieces().get(1).mesh(),c=p.pieces().get(2).mesh();
   check(b.last().left().dot(c.last().left())>.999999,"B C stay parallel at junction");
   check(b.first().center().sub(b.last().center()).horizontalUnit().dot(b.last().left().left().mul(-1))<-.999999,"B stays straight");
   check(p.movements().stream().anyMatch(m->m.from()==0&&m.to()==1&&m.turn()==JunctionSpec.STRAIGHT),"A to B straight");
   check(p.movements().stream().anyMatch(m->m.from()==2&&m.to()==0&&m.turn()==JunctionSpec.STRAIGHT),"C to A straight");
   check(p.movements().stream().noneMatch(m->m.from()==1||m.to()==2),"one-way directions respected");
   check(p.movements().stream().noneMatch(m->m.from()==2&&m.to()==1),"no implicit C to B U-turn");
   for(var m:p.movements())for(int i=1;i<m.path().size()-1;i++)check(JunctionPaint.inside(p.boundary(),m.path().get(i)),"movement inside junction "+m.from()+" -> "+m.to());
   double area=Math.abs(JunctionPaint.area(p.boundary())),triangles=JunctionPaint.triangulate(p.boundary()).stream().mapToDouble(t->Math.abs(JunctionPaint.area(t))).sum();check(Math.abs(area-triangles)<1e-4,"fill triangulation has exact area");
   for(var piece:p.pieces()){RoadGrades.validate(piece.mesh());for(var part:piece.structures())check(Double.isFinite(part.width())&&part.width()>0,"valid structures");}
   if(!left&&walk&&angle==0){var svg=new StringBuilder("<svg xmlns='http://www.w3.org/2000/svg' viewBox='2990 860 342 342'><rect x='2990' y='860' width='342' height='342' fill='#506647'/>");
    for(var piece:p.pieces()){for(var face:RoadSurface.build(piece.mesh(),List.of(),List.of()).pavement())Revision33Validation.polygon(svg,face.points(),"#454b50");for(var part:piece.structures())if(RoadSidewalks.smoothPart(part))Revision33Validation.polygon(svg,part.base(),part.material()==RoadStructures.Material.TACTILE?"#f4c54a":"#aeb3b7");for(var face:piece.paint())Revision33Validation.polygon(svg,face.points(),String.format("#%06x",face.color()&0xffffff));}
    java.nio.file.Files.writeString(folder.resolve("separated-"+(extra+3)+"-arms.svg"),svg.append("</svg>").toString());
   }
  }
  System.out.println("Separated35 PASS "+checks+" checks");
 }
}
