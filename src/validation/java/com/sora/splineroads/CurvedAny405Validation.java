package com.sora.splineroads;
import com.sora.splineroads.core.*;
import com.sora.splineroads.core.RoadGeometry.*;
import java.util.*;

/** Actual curved/graded authored station and lane-hole regression; no world adapters. */
public final class CurvedAny405Validation {
  static int checks,cases;
  static void check(boolean ok,String why){checks++;if(!ok)throw new AssertionError(why);}
  public static void main(String[]args){
    for(var style:List.of(Style.O1_ONE,Style.O3_ONE,Style.O6_RAIL))for(boolean left:new boolean[]{false,true})for(int turn:new int[]{-1,1}){
      var settings=AnyLane405Validation.settings(style,left);var samples=new ArrayList<Sample>();
      for(int i=0;i<=210;i++){double d=i*2.0,theta=d/300;V tangent=new V(turn*Math.sin(theta),0,Math.cos(theta));
        samples.add(new Sample(new V(-500+turn*300*(1-Math.cos(theta)),100+3*Math.sin(d/90),-700+300*Math.sin(theta)),tangent.left(),d,settings.width()/2));}
      var raw=RoadRibbon.mesh(samples,settings);
      for(int slot=0;slot<RoadProfile.catalog(style).lanes();slot++){
        var mesh=AnyLane405Validation.cut(raw,slot);var local=new RoadRaster.Local(mesh,List.of());cases++;
        for(double d:new double[]{32,145.25,209.35,273.43,389}){
          boolean closed=LaneSections.removed(mesh,d,slot)>.999;
          V point=LanePoints.lane(raw,d,slot).position();
          check(RoadQueries.contains(mesh,point,0,.02)!=closed,"curved lane query disagrees with opening");
          check(Double.isFinite(RoadQueries.ray(mesh,point.add(new V(0,12,0)),new V(0,-1,0),24))!=closed,"curved lane ray hits ghost pavement");
          V body=point.add(new V(.021,-.3,.029));int x=(int)Math.floor(body.x()),y=(int)Math.floor(body.y()),z=(int)Math.floor(body.z());var cell=new RoadRaster.Cell(x,y,z);
          var eager=RoadRaster.raster(mesh,cell).getOrDefault(cell,List.of());var lazy=local.boxes(cell);
          check(AnyLane405Validation.covers(eager,x,y,z,body)==AnyLane405Validation.covers(lazy,x,y,z,body),"curved long-road batch station mismatch");
          check(AnyLane405Validation.covers(lazy,x,y,z,body)!=closed,"curved opening collision incorrect");
          for(int other=0;other<RoadProfile.catalog(style).lanes();other++)if(other!=slot){V kept=LanePoints.lane(raw,d,other).position();check(RoadQueries.contains(mesh,kept,0,.02),"curved retained lane deleted");check(kept.distance(LanePoints.lane(mesh,d,other).position())<1e-8,"curved retained lane axis moved");}
        }
      }
    }
    System.out.println("CurvedAny405Validation: "+cases+" curved/graded/all-slot cases, "+checks+" queries/rays/physical-raster/retained-axis checks PASS. Actual pure geometry, no Minecraft/GPU.");
  }
}
