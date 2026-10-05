package com.sora.splineroads;
import com.sora.splineroads.core.*;
import com.sora.splineroads.core.RoadGeometry.*;
import com.sora.splineroads.core.RoadStructures.*;
import java.util.*;
/** Real shaft index + actual support relocation. No ServerLevel/engineering simulation. */
public final class PierSpacing410Validation {
  static int checks;static void check(boolean b,String why){checks++;if(!b)throw new AssertionError(why);}
  static Part shaft(double x,double z,double bottom,double top){var p=new V(x,bottom,z);return new Part(p,p,1.5,top-bottom,true,Material.CONCRETE);}
  static boolean brute(Part p,List<Part> old){if(!p.pier()||p.material()!=Material.CONCRETE||p.height()<=2)return false;
    for(var q:old)if(q.pier()&&q.material()==Material.CONCRETE&&q.height()>2&&Math.abs(p.a().y()+p.height()-q.a().y()-q.height())<=2
      &&Math.min(p.a().y()+p.height(),q.a().y()+q.height())>Math.max(p.a().y(),q.a().y())+.1&&p.a().sub(q.a()).horizontalLength()<8-1e-8)return true;return false;}
  public static void main(String[]args){
    var old=new ArrayList<Part>(List.of(shaft(0,0,0,20)));var index=new RoadPierSpacing(old);
    check(index.tooClose(shaft(2,0,0,20)),"unconnected record shaft pair still excluded");
    check(index.tooClose(shaft(7.99,0,3,21)),"sloped local support tops, not remote endpoint height");
    check(!index.tooClose(shaft(8,0,0,20)),"minimum boundary accepted");
    check(!index.tooClose(shaft(1,0,15,40)),"distinct deck elevations not falsely coalesced");
    check(!index.tooClose(new Part(new V(0,0,0),new V(0,0,0),2,.3,true,Material.CONCRETE)),"plinth isn't a separate main shaft");
    check(!index.tooClose(new Part(new V(0,0,0),new V(0,0,0),1,20,true,Material.LAMP)),"lamp isn't a main pier");
    old.clear();check(index.tooClose(shaft(0,0,0,20)),"index snapshot immutable");
    var random=new Random(410);for(double translation:new double[]{0,1000000,-1000000}){
      var references=new ArrayList<Part>();for(int i=0;i<600;i++)references.add(shaft(translation+random.nextDouble()*800-400,translation+random.nextDouble()*800-400,0,10+random.nextDouble()*60));
      var lookup=new RoadPierSpacing(references);for(int i=0;i<5000;i++){var p=shaft(translation+random.nextDouble()*800-400,translation+random.nextDouble()*800-400,0,10+random.nextDouble()*60);check(lookup.tooClose(p)==brute(p,references),"grid agrees with exhaustive nearby shaft test");}
    }
    var blockers=List.of(shaft(0,12,0,18.2));var lookup=new RoadPierSpacing(blockers);
    Ground clear=new Ground(){public double top(double x,double z,double y){return 0;}public boolean blocked(Part p){return false;}public boolean joined(V p){return false;}};
    Ground guarded=new Ground(){public double top(double x,double z,double y){return 0;}public boolean blocked(Part p){return lookup.tooClose(p);}public boolean joined(V p){return false;}};
    var s=new Settings(Mode.STRAIGHT,Style.O4_RAIL,RoadProfile.width(Style.O4_RAIL,RoadProfile.Options.DEFAULT,4),1,.35,90).structure(Structure.BRIDGE);
    var m=RoadGeometry.build(new Node(new V(0,20,0),0,0),new Node(new V(0,20,100),0,0),s);
    var before=RoadStructures.supports(m,clear,RoadFurniture.Phase.DEFAULT);var after=RoadStructures.supports(m,guarded,RoadFurniture.Phase.DEFAULT);
    check(before.stream().anyMatch(lookup::tooClose),"fixture reproduces old close support");
    check(after.stream().noneMatch(lookup::tooClose),"candidate relocated before adding its complete assembly");
    long a=before.stream().filter(p->p.pier()&&p.height()>2).count(),b=after.stream().filter(p->p.pier()&&p.height()>2).count();
    check(a==b&&b>0,"relocation fixture retains same number of main supports");
    check(after.equals(RoadStructures.supports(m,guarded,RoadFurniture.Phase.DEFAULT)),"repeat planning deterministic");
    check(after.stream().filter(p->p.pier()&&p.height()>2).allMatch(p->p.width()==1.5&&p.a().y()==0),"no skinny replacement or floating shaft");
    System.out.println("PierSpacing410Validation: "+checks+" checks; indexed vs exhaustive 15000 queries, physical complete support relocation. No actual game placement/load-bearing certification.");
  }
}
