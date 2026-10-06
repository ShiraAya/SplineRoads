package com.sora.splineroads;
import com.sora.splineroads.core.*;
import com.sora.splineroads.core.RoadGeometry.*;
import com.sora.splineroads.core.RoadStructures.*;
import java.util.*;

/** Actual pure geometry/parts. Ground providers are explicit test fixtures, not a game world. */
public final class Rail419Validation {
 static int checks,cases;
 static void check(boolean v,String m){checks++;if(!v)throw new AssertionError(m);}
 static void near(double a,double b,String m){check(Math.abs(a-b)<2e-5,m+" "+a+" != "+b);}
 static Settings config(Style style){var o=RoadProfile.Options.DEFAULT.outerRail(RoadProfile.OuterRail.ON);o=o.infrastructure(o.infrastructure().gantry(RoadInfrastructure.Gantry.OFF));return new Settings(Mode.STRAIGHT,style,RoadProfile.width(style,o,4),1,.35,90).options(o).structure(Structure.BRIDGE);}
 static Mesh strip(List<V> points,double width){var samples=new ArrayList<Sample>();double d=0;for(int i=0;i<points.size();i++){if(i>0)d+=points.get(i).sub(points.get(i-1)).horizontalLength();V tangent=(i+1<points.size()?points.get(i+1).sub(points.get(i)):points.get(i).sub(points.get(i-1))).horizontalUnit();samples.add(new Sample(points.get(i),tangent.left(),d,width/2));}return RoadRibbon.mesh(samples,config(Style.O1_ONE));}
 static double length(List<RoadRailJoin.Span> spans){return spans.stream().mapToDouble(s->s.a().distance(s.b())).sum();}
 static V rotate(V p,double r,int sign,double y){return new V(100000+sign*(p.x()*Math.cos(r)-p.z()*Math.sin(r)),y+p.y(),-100000+p.x()*Math.sin(r)+p.z()*Math.cos(r));}
 static RoadStructures.Ground ground(Mesh current,Mesh other,boolean precise,boolean owner){
  var index=new RoadRailJoin(List.of(new RoadRailJoin.Neighbor(other,owner)));
  return new RoadStructures.Ground(){public double top(double x,double z,double y){return y-12;}public boolean blocked(Part p){return false;}public boolean joined(V p){return LanePoints.opening(current,p)||RoadQueries.joins(current,other,p);}public V railJoint(V p,V direction){return precise?index.joint(p,direction):null;}public boolean railPost(V p){return !precise||index.ownsPost(p);}public List<RoadRailJoin.Span> railSpans(V a,V b,V outside){return precise?index.exposed(a,b):RoadStructures.Ground.super.railSpans(a,b,outside);}};
 }
 public static void main(String[]args){
  for(double rotation:new double[]{0,.7,2.4})for(int mirror:new int[]{-1,1})for(double y:new double[]{-30,300}){
   // Coplanar overlapping straight/curved contour segments must not erase the
   // actual outer edge merely because it lies inside a padded furniture opening.
   var points=new ArrayList<V>();for(double d=0;d<=80;d+=.5)points.add(rotate(new V(0,0,d),rotation,mirror,y));
   var host=strip(points,12);var rampPoints=new ArrayList<V>();for(double d=0;d<=80;d+=.5)rampPoints.add(rotate(new V(7.85,0,d),rotation,mirror,y));
   var ramp=strip(rampPoints,4);var inside=RoadStructures.sample(ramp,20);var at=RoadStructures.sample(host,20);
   var opening=new LanePoints.Opening(new UUID(419,1),host.samples().stream().map(Sample::center).toList(),6);
   var opt=ramp.settings().options();ramp=new Mesh(ramp.samples(),ramp.settings().options(opt.lanePoints(opt.lanePoints().openings(List.of(opening)))),ramp.min(),ramp.max(),ramp.length(),false,null);
   var e1=RoadStructures.sample(ramp,30);var e2=RoadStructures.sample(ramp,40);
   int outside=e1.left().dot(e1.center().sub(at.center()))>0?1:-1;
   V a=e1.at(outside*(e1.halfWidth()-.16),0),b=e2.at(outside*(e2.halfWidth()-.16),0);
   near(length(new RoadRailJoin(List.of(new RoadRailJoin.Neighbor(host,true))).exposed(a,b)),10,"exposed parallel outer side lost");
   V i=e1.at(-outside*(e1.halfWidth()-.16),0),j=e2.at(-outside*(e2.halfWidth()-.16),0);
   // The overlap is only .15: inward rail lines do not overlap, preserving both
   // sides until the real offset contours meet instead of a 0.4-radius capsule.
   check(length(new RoadRailJoin(List.of(new RoadRailJoin.Neighbor(host,true))).exposed(i,j))>9.99,"nearby disjoint rail contours falsely erased");
   var same=new RoadRailJoin(List.of(new RoadRailJoin.Neighbor(ramp,true)));
   near(length(same.exposed(a,b)),0,"lower priority duplicate retained");
   near(length(new RoadRailJoin(List.of(new RoadRailJoin.Neighbor(ramp,false))).exposed(a,b)),10,"both coincident owners erased");
   var higher=strip(points.stream().map(p->p.add(new V(0,4,0))).toList(),12);
   near(length(new RoadRailJoin(List.of(new RoadRailJoin.Neighbor(higher,true))).exposed(i,j)),10,"grade separated road erases rail");cases++;
  }
  // A slanted inset boundary cuts both roads' rail axes at the SAME non-grid point.
  for(int reverse:new int[]{-1,1}){
   var host=strip(List.of(new V(0,100,0),new V(0,100,100)),12);
   var ramp=strip(reverse>0?List.of(new V(0,100,10),new V(20,100,90)):List.of(new V(20,100,90),new V(0,100,10)),4);
   var a=ramp.first();var b=ramp.last();int side=a.left().x()>0?1:-1;
   V ra=a.at(side*(a.halfWidth()-.16),0),rb=b.at(side*(b.halfWidth()-.16),0);
   var cut=new RoadRailJoin(List.of(new RoadRailJoin.Neighbor(host,true))).exposed(ra,rb);
   check(cut.size()==1,"unexpected taper split");V seam=reverse>0?cut.get(0).a():cut.get(0).b();near(seam.x(),5.84,"rail seam clipped at road edge rather than common inset");
   var outer=new RoadRailJoin(List.of(new RoadRailJoin.Neighbor(ramp,false))).exposed(new V(5.84,100,0),new V(5.84,100,100));
   check(outer.size()==2,"host did not split at tapered branch");check(outer.stream().anyMatch(s->s.a().distance(seam)<2e-5||s.b().distance(seam)<2e-5),"host/ramp rail ends do not meet");
   var hostIndex=new RoadRailJoin(List.of(new RoadRailJoin.Neighbor(ramp,false)));var rampIndex=new RoadRailJoin(List.of(new RoadRailJoin.Neighbor(host,true)));
   V hFrame=hostIndex.joint(seam,new V(0,0,1)),rFrame=rampIndex.joint(seam,rb.sub(ra));
   check(hFrame!=null&&rFrame!=null,"missing cross-record miter");
   near(Math.min(hFrame.sub(rFrame).horizontalLength(),hFrame.add(rFrame).horizontalLength()),0,"reciprocal miter cut faces disagree");
   check(hostIndex.ownsPost(seam)&&!rampIndex.ownsPost(seam),"terminal post does not have one stable owner");
   var hostParts=RoadStructures.plan(host,ground(host,ramp,true,false));var rampParts=RoadStructures.plan(ramp,ground(ramp,host,true,true));
   var hostEnds=hostParts.stream().filter(p->p.material()==Material.STEEL&&p.height()==.12&&(p.a().sub(seam).horizontalLength()<2e-5||p.b().sub(seam).horizontalLength()<2e-5)).toList();
   check(!hostEnds.isEmpty(),"actual host Part did not end at calculated seam");
   check(rampParts.stream().anyMatch(p->p.material()==Material.STEEL&&p.height()==.12&&(p.a().sub(seam).horizontalLength()<2e-5||p.b().sub(seam).horizontalLength()<2e-5)),"actual branch Part did not meet host seam");
   for(var part:hostEnds){V frame=part.a().sub(seam).horizontalLength()<2e-5?part.frameA():part.frameB();check(frame!=null,"host Part lacks its cross-record miter frame");near(Math.min(frame.sub(hFrame.mul(part.width()/2)).horizontalLength(),frame.add(hFrame.mul(part.width()/2)).horizontalLength()),0,"actual Part frame differs from seam");}cases++;
  }
  // Original metadata uses the MAX width of a taper for its whole run. Reproduce
  // exposed rail deletion without changing the saved opening or weakening checks.
  var host=strip(List.of(new V(0,100,0),new V(0,100,80)),12);
  var branch=strip(List.of(new V(7,100,0),new V(7,100,80)),4);
  var op=new LanePoints.Opening(new UUID(419,2),branch.samples().stream().map(Sample::center).toList(),3);
  var o=host.settings().options();host=new Mesh(host.samples(),host.settings().options(o.lanePoints(o.lanePoints().openings(List.of(op)))),host.min(),host.max(),host.length(),false,null);
  // With the neighbour a little ABOVE rather than coplanar, old capsule still hides
  // both raised edges. The true .12 height policy must keep them.
  var high=strip(List.of(new V(7,101.5,0),new V(7,101.5,80)),4);
  var old=RoadStructures.plan(host,ground(host,high,false,true));var now=RoadStructures.plan(host,ground(host,high,true,true));
  long oldRails=old.stream().filter(p->p.material()==Material.STEEL&&p.height()==.12).count(),newRails=now.stream().filter(p->p.material()==Material.STEEL&&p.height()==.12).count();
  check(newRails>oldRails,"old capsule suppression not reproduced/repaired");System.out.println("  broad-opening regression: old longitudinal rails="+oldRails+", new="+newRails);
  System.out.println("Rail419Validation: "+cases+" contour scenarios, "+checks+" checks; actual core/parts, fake Ground, NO Minecraft/GPU.");
 }
}
