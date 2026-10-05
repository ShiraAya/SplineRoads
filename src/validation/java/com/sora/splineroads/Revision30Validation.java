package com.sora.splineroads;
import com.sora.splineroads.core.*;
import com.sora.splineroads.core.RoadGeometry.*;
import com.sora.splineroads.core.RoadInfrastructure.*;
import com.sora.splineroads.core.RoadStructures.*;
import com.sora.splineroads.world.RoadRecord;
import java.util.*;

public final class Revision30Validation {
  static int checks;
  static void check(boolean v,String m){checks++;if(!v)throw new AssertionError(m);}
  static Node node(double x,double y){return new Node(new V(x,y,.5),-90,0);}
  static Settings settings(Structure kind,Config c){return Revision28Validation.settings(Style.O4_YELLOW,kind,c);}
  public static void main(String[] args)throws Exception{
    for(boolean rise:new boolean[]{false,true})for(var mode:RoadTunnelFit.Adjustment.values()){
      var c=Config.DEFAULT.gantry(Gantry.OFF).adjustment(mode);c=rise?c.rise(11):c.depth(11);
      var s=settings(rise?Structure.BRIDGE:Structure.TUNNEL,c);
      var a=RoadPlanner.Hint.free(node(.5,18));var b=RoadPlanner.Hint.free(node(100.5,20));
      if(mode==RoadTunnelFit.Adjustment.OFF){boolean rejected=false;try{RoadTunnelFit.plan(a,b,s);}catch(IllegalArgumentException e){rejected=true;}check(rejected,"short fixed approaches reject 6% violation");continue;}
      var p=RoadTunnelFit.plan(a,b,s);check(RoadGrades.maximum(p.mesh())<=.0601,"configured maximum grade holds on the actual curve");
      check(p.start().position().y()==18&&p.end().position().y()==20,"endpoint elevations retained");
      if(mode==RoadTunnelFit.Adjustment.START)check(p.end().equals(b.node()),"end exactly fixed");
      if(mode==RoadTunnelFit.Adjustment.END)check(p.start().equals(a.node()),"start exactly fixed");
      double extreme=rise?p.mesh().samples().stream().mapToDouble(v->v.center().y()).max().orElseThrow():p.mesh().samples().stream().mapToDouble(v->v.center().y()).min().orElseThrow();
      check(Math.abs(extreme-(rise?31:7))<1e-7,"chosen extreme reached without overshoot");
      check(RoadRecord.readSettings(RoadRecord.writeSettings(s)).equals(s),"all new settings round trip");
      check(RoadTunnelFit.plan(RoadPlanner.Hint.free(p.start()),RoadPlanner.Hint.free(p.end()),s).mesh().samples().equals(p.mesh().samples()),"re-saving does not drift");
    }
    var legacy=settings(Structure.TUNNEL,Config.DEFAULT.grade(0).autoSpan(false).depth(8).profile(false));
    var tag=RoadRecord.writeSettings(legacy);var infra=tag.getCompound("Infrastructure");infra.remove("BridgeRise");infra.remove("MaxGrade");infra.remove("AutoSpan");
    check(RoadGeometry.build(node(.5,18),node(210.5,18),legacy).samples().equals(RoadGeometry.build(node(.5,18),node(210.5,18),RoadRecord.readSettings(tag)).samples()),"old saved steep tunnel keeps exact geometry");
    for(var mode:new Structure[]{Structure.AUTO,Structure.BRIDGE,Structure.GROUND}){
      var m=RoadGeometry.build(node(.5,18),node(480.5,18),settings(mode,Config.DEFAULT.gantry(Gantry.OFF)));
      var parts=RoadStructures.plan(m,Revision28Validation.ground(0));
      var shafts=parts.stream().filter(p->p.pier()&&p.material()==Material.CONCRETE&&p.width()==1.5&&p.height()>3).toList();
      check(shafts.size()==20,"unobstructed 480 m has a full pier every 24 m in "+mode);
      for(int i=1;i<shafts.size();i++)check(Math.abs(shafts.get(i).a().sub(shafts.get(i-1).a()).horizontalLength()-24)<1e-6,"no long unsupported gap");
    }
    for(var style:new Bridge[]{Bridge.CABLE,Bridge.SUSPENSION}){
      double previous=0;
      for(double length:new double[]{128,384,640}){
        var m=RoadGeometry.build(node(.5,18),node(length+.5,18),settings(Structure.BRIDGE,Config.DEFAULT.bridge(style).gantry(Gantry.OFF)));
        var parts=RoadStructures.plan(m,Revision28Validation.ground(0));double top=parts.stream().mapToDouble(p->Math.max(p.a().y(),p.b().y())+p.height()).max().orElseThrow();
        check(top>previous+10,"automatic full-span bridge grows its towers with total span");previous=top;
        check(parts.stream().filter(p->p.pier()&&p.material()==Material.CONCRETE&&p.width()==1.5).map(p->p.a().x()).distinct().count()==2,"two tower stations, including tall segmented towers");
        for(var f:RoadRenderMesh.structureFaces(parts,false))check(RoadRenderMesh.vertexCount(f)<=8192,"each upload face fits the GPU batch cap");
        if(length==640)Revision28Validation.export("AUTO_"+style,m,parts);
      }
    }
    System.out.println("Revision30 PASS "+checks+" checks");
  }
}
