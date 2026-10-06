package com.sora.splineroads;
import com.sora.splineroads.core.*;
import com.sora.splineroads.core.RoadGeometry.*;
import com.sora.splineroads.core.RoadStructures.*;
import java.util.*;

/** Real deck, painting, terrain classification and solid geometry. NO Minecraft world. */
public final class Closure418Validation {
 static int checks,cases;
 static void check(boolean v,String why){checks++;if(!v)throw new AssertionError(why);}
 static void near(double a,double b,String why){check(Math.abs(a-b)<1e-6,why+": "+a+" / "+b);}
 static Mesh road(Style style,Structure structure,boolean left,boolean curve,double y){
  var o=RoadProfile.Options.DEFAULT.traffic(left);var s=new Settings(curve?Mode.CURVE:Mode.STRAIGHT,style,RoadProfile.width(style,o,4),1,.35,90).options(o).structure(structure);
  return RoadGeometry.build(new Node(new V(100000,y,-100000),0,0),new Node(new V(100000+(curve?80:0),y+(curve?8:0),-99600),curve?35:0,curve?.03:0),s);
 }
 static Mesh apply(Mesh raw,List<LaneSections.Cut> cuts){return LaneSections.apply(RoadRibbon.mesh(raw.samples(),raw.settings().options(raw.settings().options().lanePoints(LanePoints.Data.EMPTY.cuts(cuts)))));}
 static Ground terrain(double gap){return new Ground(){public double top(double x,double z,double y){return y-gap;}public boolean blocked(Part p){return false;}public boolean joined(V p){return false;}};}
 static void rectangular(){
  for(var style:List.of(Style.O1_ONE,Style.O3_ONE,Style.O6_RAIL,Style.H6_RAIL))for(boolean left:new boolean[]{false,true})for(boolean curved:new boolean[]{false,true})for(boolean arrival:new boolean[]{false,true}){
   var raw=road(style,Structure.BRIDGE,left,curved,curved?340:-32);int count=RoadProfile.catalog(style).lanes();
   for(int slot=0;slot<count;slot++){
    cases++;int sign=LanePoints.lane(raw,200,slot).sign();double begin=sign>0?80.25:300.75,end=sign>0?300.75:80.25;
    var cut=new LaneSections.Cut(new UUID(418,cases),slot,sign,begin,end,32,null,true,arrival,true);var mesh=apply(raw,List.of(cut));
    for(double d:new double[]{begin+sign*.01,begin+sign*16,(begin+end)/2,end-sign*.01}){
     near(cut.removed(d),1,"new rectangular width must not taper");check(!RoadQueries.contains(mesh,LanePoints.lane(raw,d,slot).position(),0,.01),"closed lane retains road collision");
     for(int other=0;other<count;other++)if(other!=slot)check(RoadQueries.contains(mesh,LanePoints.lane(raw,d,other).position(),0,.01),"other lane removed");
    }
    for(double d:new double[]{begin-sign*.01,end+sign*.01})check(RoadQueries.contains(mesh,LanePoints.lane(raw,d,slot).position(),0,.01),"road outside closure disappeared");
    int quads=0;for(int i=1;i<mesh.samples().size();i++)for(var q:LaneDeck.holeQuads(mesh,mesh.samples().get(i-1),mesh.samples().get(i))){
     near(q.get(0).distance(q.get(1)),4,"first section of hole narrows into a tip");near(q.get(2).distance(q.get(3)),4,"last section of hole narrows into a tip");quads++;
    }
    check(quads>0,"no physical hole strips");check(LaneDeck.caps(mesh).size()==2,"missing transverse slab caps");
    near(LaneDeck.caps(mesh).get(0).a().distance(LaneDeck.caps(mesh).get(0).b()),4,"cap spans other slots");
    check(LaneClosureLandscape.plan(mesh,terrain(.1)).isEmpty(),"bridge hole filled with vegetation");
    for(var slice:LaneDeck.rasterPieces(mesh,96))for(var sample:slice.samples())check(sample.distance()>=slice.first().distance(),"raster slices rebase stations");
    if(slot==0){var surface=RoadSurface.build(mesh,List.of(),List.of());check(!surface.pavement().isEmpty(),"all pavement disappeared");
     for(var cap:LaneDeck.caps(mesh))check(surface.pavement().stream().anyMatch(f->f.texture()==RoadSurface.Texture.CONCRETE&&f.points().stream().anyMatch(v->v.distance(cap.a())<1e-6)&&f.points().stream().anyMatch(v->v.distance(cap.b())<1e-6)),"slab cap not present in renderer geometry");}
   }
  }
 }
 static void ground(){
  for(boolean curve:new boolean[]{false,true})for(double y:new double[]{-40,80,350}){
   var raw=road(Style.O6_RAIL,Structure.AUTO,false,curve,y);
   for(int slot:new int[]{0,1,5}){
    var lane=LanePoints.lane(raw,150,slot);int sign=lane.sign();var cut=new LaneSections.Cut(new UUID(419,slot+1),slot,sign,sign>0?80.25:260.75,sign>0?260.75:80.25,32,null,true,false,true);var mesh=apply(raw,List.of(cut));
    var plants=LaneClosureLandscape.plan(mesh,terrain(.3));check(!plants.isEmpty()&&plants.size()%2==0,"supported ground has no real planting");
    for(var p:plants){check(p.material()==Material.SOIL||p.material()==Material.OAK_LEAVES,"closure not soil/leaf solids");for(V v:p.base()){
      var q=RoadQueries.horizontal(raw,v);var l=LanePoints.lane(raw,q.sample().distance(),slot);check(Math.abs(v.sub(l.position()).dot(q.sample().left()))<=l.width()/2+.02,"planting moved into another lane");
      check(q.sample().distance()>=80.24&&q.sample().distance()<=260.76,"planting extends past closure");}
    }
    var plant=plants.get(plants.size()/2);var pos=plant.a().add(plant.b()).mul(.5);var cell=new RoadRaster.Cell((int)Math.floor(pos.x()),(int)Math.floor(pos.y()),(int)Math.floor(pos.z()));
    check(!RoadRaster.structures(plants,cell).isEmpty(),"planting has no collision raster");check(LaneClosureLandscape.plan(mesh,terrain(20)).isEmpty(),"AUTO unsupported ground has floating planting");
    check(LaneClosureLandscape.plan(mesh,terrain(Double.NaN)).isEmpty(),"unknown original terrain fabricated as ground");
    var obstacle=new Part(LanePoints.lane(raw,160,slot).position().add(new V(0,-1,0)),LanePoints.lane(raw,190,slot).position(),4,2,false,Material.CONCRETE);
    Ground obstructed=new Ground(){public double top(double x,double z,double zY){return zY-.3;}public boolean blocked(Part p){return RoadSolidOverlap.intersects(obstacle,p);}public boolean joined(V p){return false;}};
    var clipped=LaneClosureLandscape.plan(mesh,obstructed);check(clipped.size()<plants.size(),"actual overlapping ramp/structure does not reserve space");for(var p:clipped)check(!RoadSolidOverlap.intersects(obstacle,p),"plant penetrates reserved ramp volume");
    check(LaneClosureLandscape.plan(apply(raw,List.of()),terrain(.3)).isEmpty(),"deleted reservation leaves plants");
   }
  }
 }
 static void classification(){
  var raw=road(Style.O6_RAIL,Structure.AUTO,false,false,350);int slot=1;var lane=LanePoints.lane(raw,150,slot);
  Ground spine=new Ground(){public double top(double x,double z,double y){return Math.abs(x-lane.position().x())<.45?y-.1:y-20;}public boolean blocked(Part p){return false;}public boolean joined(V p){return false;}};
  check(LaneClosureLandscape.raised(raw,slot,150,spine),"one-block layout spine classified as supported lane");
  Ground hole=new Ground(){public double top(double x,double z,double y){return Math.abs(x-lane.position().x())<.4&&Math.abs(z-lane.position().z())<.4?Double.NaN:y-.3;}public boolean blocked(Part p){return false;}public boolean joined(V p){return false;}};
  check(!LaneClosureLandscape.raised(raw,slot,150,hole),"single marker hole classified whole lane elevated");
  Ground trench=new Ground(){public double top(double x,double z,double y){return Math.abs(x-lane.position().x())<2.1?y-20:y-.3;}public boolean blocked(Part p){return false;}public boolean joined(V p){return false;}};
  check(!RoadStructures.elevated(raw,RoadStructures.sample(raw,150),trench),"fixture must reproduce road-wide ground majority");
  check(LaneClosureLandscape.raised(raw,slot,150,trench),"unrelated supported half hides unsupported selected lane");
  Ground ledge=new Ground(){public double top(double x,double z,double y){return Math.abs(x-lane.position().x())<2.1?y-.3:y-20;}public boolean blocked(Part p){return false;}public boolean joined(V p){return false;}};
  check(!LaneClosureLandscape.raised(raw,slot,150,ledge),"unrelated suspended half hides supported selected lane");
  for(var type:List.of(Structure.GROUND,Structure.BRIDGE,Structure.TUNNEL)){
   var mesh=road(Style.O6_RAIL,type,false,false,350);check(LaneClosureLandscape.raised(mesh,slot,150,terrain(type==Structure.GROUND?30:.1))==(type!=Structure.GROUND),"explicit structure selection ignored");
  }
 }
 static void compatibility(){
  var legacy=new LaneSections.Cut(new UUID(420,1),0,1,80,300,32,null,true,false);near(legacy.removed(96),.5,"old saved taper changed on load");
  var detached=new LaneSections.Cut(new UUID(420,2),0,1,80,300,32);near(detached.removed(96),.5,"DETACH shape restriction changed");
  boolean rejected=false;try{new LaneSections.Cut(new UUID(420,3),0,1,80,300,32,null,false,false,true);}catch(IllegalArgumentException e){rejected=true;}check(rejected,"DETACH must not accept rectangular temporary flag");
  var raw=road(Style.O3_ONE,Structure.GROUND,false,false,100);check(LaneClosureLandscape.plan(apply(raw,List.of(legacy)),terrain(.3)).isEmpty(),"legacy load creates new landscaping silently");
  var a=new LaneSections.Cut(new UUID(420,4),1,1,80,150,32,null,true,false,true);var b=new LaneSections.Cut(new UUID(420,5),1,1,150,250,32,null,true,true,true);var mesh=apply(raw,List.of(a,b));
  check(LaneDeck.caps(mesh).size()==2,"adjacent holes gain an internal phantom wall");
 }
 public static void main(String[]args){rectangular();ground();classification();compatibility();System.out.println("Closure418Validation: "+cases+" rectangular lane/sign/style/curve cases, "+checks+" checks; real deck/paint/solid/classification, NOT Minecraft writes or GPU.");}
}
