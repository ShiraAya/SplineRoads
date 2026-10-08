package com.sora.splineroads.core;

import com.sora.splineroads.core.RoadGeometry.*;
import com.sora.splineroads.core.RoadStructures.*;
import java.util.*;

/** Intact shafts and capitals: uneven terrain never slices a pier into thin strips. */
public final class RoadSupports {
  private static double foundation(V center,double top,double width,Ground ground){
    double floor=Double.POSITIVE_INFINITY;
    for(double dx:new double[]{-.49,0,.49})for(double dz:new double[]{-.49,0,.49}){
      double y=ground.top(center.x()+dx*width,center.z()+dz*width,top);
      if(!Double.isFinite(y))return Double.NaN;floor=Math.min(floor,y);
    }return floor;
  }
  public static boolean shaft(List<Part> out,V center,double top,double width,Ground ground){
    double floor=foundation(center,top,width,ground);
    if(!Double.isFinite(floor))return false;
    double height=top-floor;
    if(height<.4)return true;
    if(height>RoadStructures.MAX_DROP)return false;
    post(out,center,floor,width,height);
    // A low plinth and a stepped capital give the shaft a deliberate silhouette.
    post(out,center,floor,width+.4,Math.min(.35,height));
    if(height>2)for(int i=0;i<3;i++)post(out,center,top-.6+i*.2,width+.25*(i+1),.2);
    return true;
  }
  private static void post(List<Part> out,V c,double y,double width,double height){
    V p=new V(c.x(),y,c.z());out.add(new Part(p,p,width,height,true,Material.CONCRETE));
  }
  public static List<Part> standard(Sample s,double thickness,Ground ground){
    double underside=s.center().y()-thickness,cap=.8,reach=Math.max(1.4,s.halfWidth()*.78);
    var out=new ArrayList<Part>();
    double floor=foundation(s.center(),underside,1.5,ground);
    if(Double.isFinite(floor)&&underside-floor<2)return List.of();
    // Keep the narrow median-compatible shaft. The cap spreads its load under the deck.
    if(!shaft(out,s.center(),underside-cap,1.5,ground)||out.isEmpty())return List.of();
    out.add(new Part(s.at(-reach,thickness+cap),s.at(reach,thickness+cap),2.1,cap,false,Material.CONCRETE));
    return List.copyOf(out);
  }
  public static List<Part> ramp(Sample s,double thickness,Ground ground){
    var out=new ArrayList<Part>();double top=s.center().y()-thickness;
    double floor=foundation(s.center(),top,1.5,ground);
    if(!Double.isFinite(floor)||top-floor<2)return List.of();
    V foot=new V(s.center().x(),floor,s.center().z());
    var shaft=new Part(foot,foot,1.5,top-floor,true,Material.CONCRETE);
    if(ground.blocked(shaft))return List.of();out.add(shaft);return List.copyOf(out);
  }
  public static List<Part> ramp(Mesh mesh,double station,Ground ground){
    var s=RoadStructures.sample(mesh,station);double thickness=mesh.settings().thickness();
    var before=RoadStructures.sample(mesh,Math.max(0,station-.75));
    var after=RoadStructures.sample(mesh,Math.min(mesh.length(),station+.75));
    V delta=after.center().sub(before.center());double grade=delta.y()/Math.max(1e-6,delta.horizontalLength());
    V tangent=delta.horizontalUnit();double gx=grade*tangent.x(),gz=grade*tangent.z();
    double top=s.center().y()-thickness-.006;
    double floor=foundation(s.center(),top,1.5,ground);
    if(!Double.isFinite(floor)||top-floor<2)return List.of();
    // Bury the sloped bottom entirely below the foundation; all four top corners
    // follow the deck underside instead of ending at the lowest sampled height.
    double height=top-floor+.75*(Math.abs(gx)+Math.abs(gz))+.02;
    V a=new V(s.center().x(),top-height-.75*gz,s.center().z());
    V b=new V(s.center().x(),top-height+.75*gz,s.center().z());
    V frame=new V(.75,.75*gx,0);
    var shaft=new Part(a,b,1.5,height,true,Material.CONCRETE).frames(frame,frame);
    return ground.blocked(shaft)?List.of():List.of(shaft);
  }
  public static List<Part> portal(Sample s,double thickness,double depth,double offset,double width,Ground ground){
    double top=s.center().y()-thickness-depth;
    var out=new ArrayList<Part>();double[] feet=new double[2];int i=0;
    for(int side:new int[]{-1,1}){
      boolean found=false;
      for(double reach=offset;reach<=Math.max(offset+64,80);reach+=.5){
        var shaft=new ArrayList<Part>();
        if(!shaft(shaft,s.at(side*reach,0),top,width,ground)||shaft.isEmpty())continue;
        if(shaft.stream().anyMatch(ground::blocked))continue;
        out.addAll(shaft);feet[i++]=side*reach;found=true;break;
      }
      if(!found)return List.of();
    }
    var beam=new Part(s.at(feet[0]-(width+.75)/2,thickness+depth),s.at(feet[1]+(width+.75)/2,thickness+depth),width+.75,depth,false,Material.CONCRETE);
    if(ground.blocked(beam))return List.of();out.add(beam);return List.copyOf(out);
  }
  public static List<Part> clearStandard(Sample s,double thickness,Ground ground){
    var simple=standard(s,thickness,ground);
    if(!simple.isEmpty()&&simple.stream().noneMatch(ground::blocked))return simple;
    // An obstructed automatic support is skipped/moved along the road. Only an
    // explicitly selected overpass bridge uses a portal and its side foundations.
    return List.of();
  }
  private RoadSupports(){}
}
