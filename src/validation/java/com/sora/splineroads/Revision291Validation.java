package com.sora.splineroads;
import com.sora.splineroads.core.*;
import com.sora.splineroads.core.RoadGeometry.*;
import com.sora.splineroads.core.RoadInfrastructure.*;
import com.sora.splineroads.core.RoadStructures.*;
import com.sora.splineroads.core.RoadTunnelFit.Adjustment;
import com.sora.splineroads.world.RoadRecord;
import java.util.*;

public final class Revision291Validation {
  static int checks;
  static void check(boolean ok,String message){checks++;if(!ok)throw new AssertionError(message);}
  static Settings settings(Structure kind,Config c){return Revision28Validation.settings(Style.O4_YELLOW,kind,c);}
  static Node node(double x,double y){return new Node(new V(x,y,.5),-90,0);}
  public static void main(String[] args)throws Exception{
    var plain=Config.DEFAULT.grade(.2).gantry(Gantry.OFF);
    // Same 210-block mouths: old shape accepts 10 but rejects 11, new shape accepts both.
    for(double depth:new double[]{10,11}){
      var s=settings(Structure.TUNNEL,plain.depth(depth));
      var m=RoadGeometry.build(node(.5,2),node(210.5,2),s);
      check(RoadGrades.maximum(m)<=.2001,"efficient approach stays below 20%");
      check(Math.abs(m.samples().stream().mapToDouble(p->p.center().y()).min().orElseThrow()-(2-depth))<1e-7,"specified depth retained");
    }
    var legacy=settings(Structure.TUNNEL,plain.depth(11).profile(false));boolean failed=false;
    try{RoadGeometry.build(node(.5,2),node(210.5,2),legacy);}catch(IllegalArgumentException e){failed=true;}check(failed,"old 10/11 cutoff reproduced");
    var original=settings(Structure.TUNNEL,plain.depth(8).profile(false));
    var oldTag=RoadRecord.writeSettings(original);oldTag.getCompound("Infrastructure").remove("DipProfile");oldTag.getCompound("Infrastructure").remove("TunnelAdjustment");
    var loaded=RoadRecord.readSettings(oldTag);
    check(!loaded.options().infrastructure().efficientDip(),"legacy save retains quintic dip geometry");
    check(RoadGeometry.build(node(.5,2),node(210.5,2),original).samples().equals(RoadGeometry.build(node(.5,2),node(210.5,2),loaded).samples()),"legacy mesh reload is exact");
    for(Adjustment mode:new Adjustment[]{Adjustment.BOTH,Adjustment.START,Adjustment.END})for(Mode route:new Mode[]{Mode.STRAIGHT,Mode.AUTO}){
      var s=RoadPlanner.mode(settings(Structure.TUNNEL,plain.depth(11).adjustment(mode)),route,90);
      var a=RoadPlanner.Hint.free(node(.5,2));var b=RoadPlanner.Hint.free(node(100.5,4));
      var p=RoadTunnelFit.plan(a,b,s);
      check(RoadGrades.maximum(p.mesh())<=.2001,"fitted dip grade");
      check(p.start().position().y()==2&&p.end().position().y()==4,"fit preserves mouth elevations");
      check(Math.abs(p.mesh().samples().stream().mapToDouble(q->q.center().y()).min().orElseThrow()+9)<1e-7,"fit preserves target minimum");
      if(mode==Adjustment.START)check(p.end().equals(b.node()),"end remains exactly fixed");
      if(mode==Adjustment.END)check(p.start().equals(a.node()),"start remains exactly fixed");
      check(RoadTunnelFit.plan(RoadPlanner.Hint.free(p.start()),RoadPlanner.Hint.free(p.end()),s).mesh().samples().equals(p.mesh().samples()),"reapplying fit does not drift endpoints");
      var again=RoadRecord.readSettings(RoadRecord.writeSettings(s));check(again.equals(s),"fit mode persists in settings");
    }
    Ground rough=new Ground(){public double top(double x,double z,double y){return Math.min(y,x>11.4&&x<12.4&&z<.5?9:0);}public boolean blocked(Part p){return false;}public boolean joined(V p){return false;}};
    var m=RoadGeometry.build(node(.5,18),node(72.5,18),settings(Structure.BRIDGE,plain));
    var parts=RoadStructures.plan(m,rough);
    var shafts=parts.stream().filter(p->p.pier()&&p.material()==Material.CONCRETE&&p.height()>3).toList();
    check(!shafts.isEmpty(),"ordinary elevated piers generated");
    for(var p:shafts){check(p.width()==1.5&&p.a().equals(p.b()),"rough terrain never splits shafts into narrow strips");
      check(parts.stream().anyMatch(q->!q.pier()&&q.material()==Material.CONCRETE&&q.a().sub(q.b()).horizontalLength()>m.settings().width()*.7&&Math.abs(q.a().y()-p.a().y()-p.height())<.01),"each standard shaft meets its load-spreading transverse cap");}
    Revision28Validation.export("STANDARD_291",m,parts);
    for(Bridge style:Bridge.values())if(style!=Bridge.STANDARD){
      var bridge=RoadGeometry.build(node(.5,18),node(96.5,18),settings(Structure.BRIDGE,plain.bridge(style)));
      var out=RoadStructures.plan(bridge,Revision28Validation.ground(0));
      var slabs=out.stream().filter(p->!p.pier()&&p.material()==Material.CONCRETE&&Math.abs(p.height()-bridge.settings().thickness())<1e-7&&Math.abs(p.a().y()-17)<1e-7).toList();
      check(!slabs.isEmpty(),"bridge edge slabs generated");
      for(var slab:slabs)for(V v:slab.base())if(Math.abs(v.x()-.5)<1e-7||Math.abs(v.x()-96.5)<1e-7)
        check(Math.abs(v.z()-.5)<=bridge.first().halfWidth()+.001,"bridge edge returns to exact approach width at both ends: "+style);
      if(style==Bridge.SUSPENSION)Revision28Validation.export("SUSPENSION_291",bridge,out);
    }
    for(Gantry type:new Gantry[]{Gantry.FRAME,Gantry.SIGNS}){
      var groundRoad=RoadGeometry.build(node(.5,2.25),node(96.5,2.25),settings(Structure.GROUND,plain.gantry(type)));
      var out=RoadGantry.parts(groundRoad,RoadGantry.station(groundRoad,0),Revision28Validation.ground(2));
      check(out.stream().allMatch(p->p.a().y()>=2-1e-7&&p.b().y()>=2-1e-7),"ground gantry has no underground bracket or sunken base");
      if(type==Gantry.FRAME){
        var beams=out.stream().filter(p->p.material()==Material.GANTRY_FRAME).toList();
        check(beams.size()>10&&beams.stream().noneMatch(p->p.height()>.2),"equipment uses open truss chords and diagonals");
        Revision28Validation.export("EQUIPMENT_291",groundRoad,out);
      }
    }
    System.out.println("Revision291 PASS "+checks+" checks");
  }
}
