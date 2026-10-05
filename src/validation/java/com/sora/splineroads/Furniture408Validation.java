package com.sora.splineroads;
import com.sora.splineroads.core.*;
import com.sora.splineroads.core.RoadGeometry.*;
import com.sora.splineroads.core.RoadStructures.*;
import java.util.*;

/** Production prism collision and complete noise-barrier assembly; NO world/GPU test. */
public final class Furniture408Validation {
  static int checks;static void check(boolean b,String m){checks++;if(!b)throw new AssertionError(m);}
  static Part box(double x,double y,double z,double width,double length,double height){return new Part(new V(x,y,z),new V(x,y,z+length),width,height,false,Material.STEEL).frames(new V(width/2,0,0),new V(width/2,0,0));}
  static void collision(){
    var random=new Random(408);
    for(int i=0;i<1500;i++){
      double x=random.nextDouble()*8,y=random.nextDouble()*8,z=random.nextDouble()*8,w=.2+random.nextDouble()*4,l=.2+random.nextDouble()*4,h=.2+random.nextDouble()*4;
      var a=box(0,2,1,3,4,2);var b=box(x-4,y,z-3,w,l,h);
      boolean exact=(x-4-w/2<1.5-1e-7&&x-4+w/2>-1.5+1e-7&&y<4-1e-7&&y+h>2+1e-7&&z-3<5-1e-7&&z-3+l>1+1e-7);
      check(RoadSolidOverlap.intersects(a,b)==exact,"axis-aligned prism vs analytic boxes");check(RoadSolidOverlap.intersects(b,a)==exact,"symmetric intersection");
    }
    for(double origin:new double[]{0,-1000000,20000000})for(double angle:new double[]{0,.3,.9,1.57,2.2}){
      V along=new V(Math.sin(angle),0,Math.cos(angle)),side=along.left(),a=new V(origin,100,-origin),b=a.add(along.mul(8)).add(new V(0,4,0));
      var walk=new Part(a,b,4,.5,false,Material.WALK_STONE_BRICKS).frames(side.mul(2),side.mul(2));
      var hit=box(origin,98,-origin,1,1,3.2);var miss=box(origin,103,-origin,1,1,.2);
      check(RoadSolidOverlap.intersects(walk,hit),"lamp/upper sloping sidewalk intersection");
      check(!RoadSolidOverlap.intersects(walk,miss),"global sloped Y bounds must not create false collision");
      var shifted=new Part(a.add(new V(0,1,0)),b.add(along.mul(0)).add(new V(0,1,0)),4,.5,false,Material.WALK_STONE_BRICKS).frames(side.mul(2),side.mul(2));
      check(!RoadSolidOverlap.intersects(walk,shifted),"parallel sloped slabs with disjoint actual heights");
      var index=new RoadSolidOverlap.Index(List.of(walk,shifted));check(index.intersects(hit),"spatial index includes external walkway");check(!index.intersects(box(origin+100,100,-origin,1,1,1)),"unrelated column not blocked");
    }
    check(!RoadSolidOverlap.intersects(box(0,0,0,2,2,1),box(0,1,0,2,2,1)),"touching foundation is not penetration");
    var upper=box(15,8,0,4,8,.5);var post=box(15,0,2,.2,.2,8.3);
    check(RoadSolidOverlap.intersects(upper,post),"upper sidewalk outside a hypothetical 9m road deck is still solid");
  }
  static void noise(){
    for(double sign:new double[]{-1,1})for(double slope:new double[]{0,.08,-.08}){
      V a=new V(-100,100,40),b=new V(-98,100+2*slope,40),frame=new V(0,0,sign*.35);
      var panel=new Part(a,b,.7,RoadNoiseModel.HEIGHT,false,Material.CB_NOISE).frames(frame,frame);
      var assembly=RoadNoiseModel.assembly(panel);check(assembly.size()==2,"one concrete base + one authored panel");
      var base=assembly.get(0);var raised=assembly.get(1);
      check(base.material()==Material.CONCRETE&&Math.abs(base.height()-.5)<1e-10,"half-block solid plinth");
      check(!RoadSolidOverlap.intersects(base,raised),"base/panel just touch without hidden overlap");
      check(raised.a().distance(a.add(new V(0,.5,0)))<1e-10&&raised.b().distance(b.add(new V(0,.5,0)))<1e-10,"whole panel lifts without slope or frame changes");
      for(boolean far:new boolean[]{false,true}){
        var old=RoadNoiseModel.faces(panel,far);var now=RoadNoiseModel.faces(raised,far);check(old.size()==now.size(),"panel LOD coverage unchanged");
        for(int i=0;i<old.size();i++){check(old.get(i).uv().equals(now.get(i).uv()),"authored UV/material stays original");for(int j=0;j<4;j++)check(old.get(i).points().get(j).add(new V(0,.5,0)).distance(now.get(i).points().get(j))<1e-8,"only panel elevation changed");}
      }
      check(!RoadRaster.structures(assembly,null).isEmpty(),"foundation included in physical collider path");
    }
    var o=RoadProfile.Options.DEFAULT.outerRail(RoadProfile.OuterRail.SOUND_BOTH);var s=new Settings(Mode.STRAIGHT,Style.O4_RAIL,RoadProfile.width(Style.O4_RAIL,o,4),1,.35,90).options(o).structure(Structure.BRIDGE);
    var mesh=RoadGeometry.build(new Node(new V(0,20,0),0,0),new Node(new V(0,20,48),0,0),s);
    Ground normal=new Ground(){public double top(double x,double z,double y){return 0;}public boolean blocked(Part p){return false;}public boolean joined(V p){return false;}};
    var parts=RoadStructures.plan(mesh,normal);int panels=0;for(var p:parts)if(p.material()==Material.CB_NOISE){panels++;check(parts.stream().anyMatch(b->b.material()==Material.CONCRETE&&Math.abs(b.height()-.5)<1e-9&&b.a().add(new V(0,.5,0)).distance(p.a())<1e-8&&b.b().add(new V(0,.5,0)).distance(p.b())<1e-8),"every emitted panel retains its matching physical base");}
    check(panels>0,"integration fixture actually emits barriers");
    Ground rejectBase=new Ground(){public double top(double x,double z,double y){return 0;}public boolean blocked(Part p){return p.material()==Material.CONCRETE&&Math.abs(p.height()-.5)<1e-8;}public boolean joined(V p){return false;}};
    check(RoadStructures.plan(mesh,rejectBase).stream().noneMatch(p->p.material()==Material.CB_NOISE),"base rejection cannot leave a floating panel");
  }
  public static void main(String[]args){collision();noise();System.out.println("Furniture408Validation: "+checks+" checks PASS; real prism/index/panel/UV/near-far/collider core. No Minecraft placement, lamps in user save, GPU or FPS claim.");}
}
