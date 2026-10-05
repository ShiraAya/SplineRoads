package com.sora.splineroads;
import com.sora.splineroads.core.*;
import com.sora.splineroads.core.RoadGeometry.*;
import com.sora.splineroads.core.RoadStructures.*;
import java.util.*;
public final class Revision38Validation {
 static void require(boolean ok,String why){if(!ok)throw new AssertionError(why);}
 static JunctionSpec fixture(boolean mixed,boolean heights){
  var arms=new ArrayList<JunctionSpec.Arm>();double[] angles={0,38,180,265};
  for(int i=0;i<4;i++){
   double a=Math.toRadians(angles[i]);V out=new V(Math.cos(a),0,Math.sin(a));
   var walk=new RoadSidewalks.Config(!mixed||i!=1,RoadSidewalks.Side.BOTH,5,"minecraft:stone_bricks",true,true);
   var options=RoadProfile.Options.DEFAULT.sidewalk(walk).cycleFinish(RoadProfile.Options.CycleFinish.GREEN).streetscape(RoadStreetscape.Config.DEFAULT.separator(RoadStreetscape.Separator.RAIL));
   var s=new Settings(Mode.STRAIGHT,Style.O4_YELLOW,RoadProfile.width(Style.O4_YELLOW,options,4),1,.4,90).options(options).structure(Structure.GROUND);
   V p=out.mul(80).add(new V(0,heights&&i==1?.8:0,0));V in=out.mul(-1);
   arms.add(JunctionSpec.arm(new Node(p,RoadPlanner.yaw(in),0),in,s,true,i));
  }
  return new JunctionSpec(new V(0,0,0),JunctionSpec.Kind.INTERSECTION,false,4,12,1,4,1,JunctionSpec.Control.NONE,20,3,1,0,true,true,arms);
 }
 public static void main(String[] args){
  sharedTriangleWalls();
  for(boolean mixed:new boolean[]{false,true})for(boolean heights:new boolean[]{false,true}){
   var spec=fixture(mixed,heights);var plan=JunctionPlanner.plan(spec);var parts=plan.pieces().stream().flatMap(p->p.structures().stream()).toList();var surface=new TactileSurface(parts);
   int ribbons=0;for(var p:parts)if(p.material()==Material.TACTILE&&p.height()<.018){ribbons++;for(V v:p.base()){
    double top=surface.height(v);require(Double.isFinite(top),"Tactile ribbon must remain on paving: "+v+" mixed="+mixed);
    require(v.y()>=top-.002&&v.y()<top+.03,"Tactile paving follows surface without burial or gap");
   }}require(ribbons>0,"fixture includes tactile corners");
   var arm=spec.arms().get(0);var approach=plan.pieces().get(0);var mesh=approach.mesh();
   double crossing=mesh.length()-arm.crossingSetback()-arm.crossingWidth()/2;var at=RoadStructures.sample(mesh,crossing);
   double motor=arm.motorEdge(true,false);boolean cycleCrossing=false;
   for(var f:approach.paint())if(f.color()==JunctionPaint.WHITE)for(var v:f.points())if(Math.abs(v.sub(at.center()).dot(at.left().left()))<arm.crossingWidth()/2+.1&&v.sub(at.center()).dot(at.left())>motor+.5)cycleCrossing=true;
   require(cycleCrossing,"zebra reaches across cycle lane");
  }
  var low=new Part(new V(-3,-.3,0),new V(0,-.3,0),2,.5,false,Material.WALK_STONE_BRICKS);
  var high=new Part(new V(0,.3,0),new V(3,.3,0),2,.5,false,Material.WALK_STONE_BRICKS);
  var paving=new TactileSurface(List.of(low,high)).gradePaving(List.of(low,high));var surface=new TactileSurface(paving);
  require(Math.abs(surface.height(new V(-.001,.5,0))-surface.height(new V(.001,.5,0)))<.01,"height step becomes continuous paving");
  var lamp=RoadStreetscape.lamp(new V(0,0,0),new V(1,0,0),3);
  require(lamp.stream().filter(p->p.width()==.07).allMatch(p->p.a().x()<=0&&p.b().x()<=0),"single-arm brace faces away from carriageway");
  System.out.println("Revision38 geometry PASS");
 }
 static void sharedTriangleWalls(){
  var source=fixture(false,false);V shift=new V(800.5,-60,.5);
  var spec=new JunctionSpec(source.center().add(shift),source.kind(),source.leftTraffic(),source.cornerRadius(),source.islandRadius(),source.ringLanes(),source.ringLaneWidth(),source.thickness(),source.control(),20,3,1,0,true,true,
      source.arms().stream().map(a->a.node(new Node(a.endpoint().position().add(shift),a.endpoint().yaw(),a.endpoint().grade()),a.inward(),a.external())).toList());
  var meshes=JunctionPlanner.plan(spec).pieces().stream().map(JunctionPlanner.Piece::mesh).toList();
  for(var mesh:meshes){var neighbors=meshes.stream().filter(m->m!=mesh).toList();
   for(var face:RoadSurface.build(mesh,List.of(),neighbors).pavement()){
    if(face.texture()!=RoadSurface.Texture.CONCRETE||Math.abs(RoadLighting.normal(face).y())>.1)continue;
    V a=face.points().get(0),b=face.points().get(1);if(a.distance(b)<3)continue;
    V mid=a.add(b).mul(.5),side=b.sub(a).horizontalUnit().left().mul(.005);
    boolean left=meshes.stream().anyMatch(m->RoadQueries.contains(m,mid.add(side),.0001,.05));
    boolean right=meshes.stream().anyMatch(m->RoadQueries.contains(m,mid.sub(side),.0001,.05));
    require(!(left&&right),"shared junction triangles have no internal wall away from the origin");
   }
  }
 }
}
