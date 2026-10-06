package com.sora.splineroads;
import com.sora.splineroads.core.*;
import com.sora.splineroads.core.RoadGeometry.*;
import com.sora.splineroads.core.RoadStructures.*;
import java.util.*;

/** Regression for boundaries which are not eligible to own a rail/post. Real core,
 * explicit terrain/provider fixtures only; no world writes, GPU or NBT binary codec. */
public final class Rail419EdgeValidation {
 static int checks,cases;
 static void check(boolean b,String why){checks++;if(!b)throw new AssertionError(why);}
 static void near(double a,double b,String why){check(Math.abs(a-b)<1e-5,why+": "+a+" / "+b);}
 static Mesh deck(Style style,Structure structure,RoadProfile.OuterRail rail,List<V> path,double width){
  var o=RoadProfile.Options.DEFAULT.outerRail(rail);o=o.infrastructure(o.infrastructure().gantry(RoadInfrastructure.Gantry.OFF));
  var settings=new Settings(Mode.STRAIGHT,style,width,1,.35,90).options(o).structure(structure);
  var samples=new ArrayList<Sample>();double d=0;
  for(int i=0;i<path.size();i++){if(i>0)d+=path.get(i).sub(path.get(i-1)).horizontalLength();var dir=(i+1<path.size()?path.get(i+1).sub(path.get(i)):path.get(i).sub(path.get(i-1))).horizontalUnit();samples.add(new Sample(path.get(i),dir.left(),d,width/2));}
  return RoadRibbon.mesh(samples,settings);
 }
 static Ground ground(RoadRailJoin index,boolean noiseBlocked){return new Ground(){
  public double top(double x,double z,double y){return y-20;}public boolean blocked(Part p){return noiseBlocked&&p.material()==Material.CB_NOISE;}
  public boolean joined(V p){return true;} // Broad capsule deliberately covers everything.
  public List<RoadRailJoin.Span> railSpans(V a,V b,V p){return index.exposed(a,b);}
  public V railJoint(V p,V d,boolean h,boolean r){return index.joint(p,d,h,r);}
  public boolean railPost(V p,boolean h,boolean r){return index.ownsPost(p,h,r);}
 };
 }
 public static void main(String[] args){
  for(double y:new double[]{-30,100,340})for(double angle:new double[]{0,.73}){
   List<V> points=List.of(Rail419Validation.rotate(new V(0,0,0),angle,1,y),Rail419Validation.rotate(new V(0,0,80),angle,1,y));
   var disabled=deck(Style.O1_ONE,Structure.BRIDGE,RoadProfile.OuterRail.OFF,points,12);
   var enabled=deck(Style.O1_ONE,Structure.BRIDGE,RoadProfile.OuterRail.ON,points,12);
   var at=RoadStructures.sample(enabled,20);var end=RoadStructures.sample(enabled,30);V a=at.at(at.halfWidth()-.16,0),b=end.at(end.halfWidth()-.16,0);
   var index=new RoadRailJoin(List.of(new RoadRailJoin.Neighbor(disabled,true)));
   near(Rail419Validation.length(index.exposed(a,b)),10,"rail OFF neighbour cannot erase coincident active rail");
   near(Rail419Validation.length(index.exposed(at.center(),end.center())),0,"disabled rail does NOT exempt the neighbour's actual pavement interior");
   check(index.ownsPost(a,false,true),"rail OFF neighbour stole an endpoint post");check(index.joint(a,new V(.3,0,1))==null,"miter joined to nonexistent rail");
   var on=new RoadRailJoin(List.of(new RoadRailJoin.Neighbor(enabled,true)));
   near(Rail419Validation.length(on.exposed(a,b)),0,"enabled coincident owner must still remove duplicate rail");check(!on.ownsPost(a,false,true),"matching profile not single-owner");
   check(on.ownsPost(a,true,false),"different-height/type highway beam loses its own end support");
   var higher=deck(Style.O1_ONE,Structure.BRIDGE,RoadProfile.OuterRail.ON,points.stream().map(p->p.add(new V(0,.08,0))).toList(),12);
   var stepped=new RoadRailJoin(List.of(new RoadRailJoin.Neighbor(higher,true)));
   check(stepped.ownsPost(a,false,true),"non-coincident vertical endpoints cannot share one post");check(stepped.joint(a,new V(.3,0,1))==null,"step in Y incorrectly shares miter cut face");
   cases++;
  }
  for(Style style:List.of(Style.O1_ONE,Style.H2_ONE))for(boolean blocked:new boolean[]{false,true}){
   var road=deck(style,Structure.BRIDGE,RoadProfile.OuterRail.SOUND_BOTH,List.of(new V(0,100,0),new V(0,100,40)),12);
   var other=deck(Style.O1_ONE,Structure.BRIDGE,RoadProfile.OuterRail.ON,List.of(new V(0,100,1),new V(20,100,39)),4);
   var join=new RoadRailJoin(List.of(new RoadRailJoin.Neighbor(other,true)));
   var parts=RoadStructures.plan(road,ground(join,blocked));var panels=parts.stream().filter(p->p.material()==Material.CB_NOISE).toList();
   if(blocked){check(panels.isEmpty(),"blocked panel must be omitted");check(parts.stream().noneMatch(p->p.material()==Material.CONCRETE&&p.height()==RoadNoiseModel.BASE_HEIGHT),"blocked panel leaves orphan footing");}
   else {check(!panels.isEmpty(),"clipped sound wall lost all exposed panels");
    for(var p:panels){V base=p.a().add(new V(0,-RoadNoiseModel.BASE_HEIGHT,0));
     check(parts.stream().anyMatch(q->q.material()==Material.CONCRETE&&q.height()==RoadNoiseModel.BASE_HEIGHT&&q.a().distance(base)<1e-6&&q.b().distance(p.b().add(new V(0,-RoadNoiseModel.BASE_HEIGHT,0)))<1e-6),"panel missing matching real footing");
     near(Rail419Validation.length(join.exposed(base,p.b().add(new V(0,-RoadNoiseModel.BASE_HEIGHT,0)))),base.distance(p.b().add(new V(0,-RoadNoiseModel.BASE_HEIGHT,0))),"sound module bridges a suppressed opening");
     check(RoadNoiseModel.faces(p,false).stream().allMatch(f->f.uv()!=null&&f.uv().size()==f.points().size()),"panel mesh/UV lost");
    }
   }
   var owner=new RoadRailJoin(List.of(new RoadRailJoin.Neighbor(road,true)));V edge=RoadStructures.sample(road,20).at(5.84,0);
   check(owner.ownsPost(edge,false,true)&&owner.ownsPost(edge,true,false),"noise wall has no matching steel terminal post to own");cases++;
  }
  var host=deck(Style.O3_ONE,Structure.BRIDGE,RoadProfile.OuterRail.ON,List.of(new V(0,100,0),new V(0,100,200)),13);
  var cut=new LaneSections.Cut(new UUID(419,501),1,1,30,170,32,null,true,true,true);
  var opt=host.settings().options();host=LaneSections.apply(RoadRibbon.mesh(host.samples(),host.settings().options(opt.lanePoints(opt.lanePoints().cuts(List.of(cut))))));
  var index=new RoadRailJoin(List.of(new RoadRailJoin.Neighbor(host,true)));var s=RoadStructures.sample(host,100);var layout=RoadProfile.layout(host,s);
  var boundaries=LaneDeck.spans(host,s);check(boundaries.size()>1,"fixture has no actual cut");
  for(var band:boundaries)for(double side:new double[]{band.low(),band.high()}){
   if(Math.abs(Math.abs(side)-s.halfWidth())<1e-6)continue;
   V inner=s.at(side+(side<0?-.16:.16),0);
   check(index.joint(inner,new V(.3,0,1))==null,"invented guardrail miter on an unguarded internal hole edge");
   check(index.ownsPost(inner,false,true),"unguarded hole edge stole real end support");
  }cases++;
  System.out.println("Rail419EdgeValidation: "+cases+" profile/height/rail-OFF/noise-wall/hole scenarios, "+checks+" checks. Real Parts/mesh/UV; fake Ground, NO game/GPU.");
 }
}
