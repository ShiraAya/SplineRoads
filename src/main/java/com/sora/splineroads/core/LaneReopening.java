package com.sora.splineroads.core;

import com.sora.splineroads.core.RoadGeometry.*;
import java.util.*;

/** A temporary lane cut reopens beyond the LAST 3-D obstruction, not a fixed A offset. */
public final class LaneReopening {
  private static final double SIDE_MARGIN=.30, END_MARGIN=1.0;
  public static double restoreStation(Mesh host,int slot,double begin,Mesh ramp,List<RoadStructures.Part> parts,double transition){
    var raw=LaneSections.reference(host);int sign=LanePoints.lane(raw,begin,slot).sign();var sweep=laneSweep(raw,slot);
    double last=sign*begin;
    last=lastBlocked(sweep,ramp,begin,sign,last);
    for(var part:parts)last=lastBlocked(sweep,envelope(part,ramp.settings()),begin,sign,last);
    double reopen=Math.max(sign*begin+2*transition,last+END_MARGIN),end=(reopen+transition)*sign;
    if(end<.001||end>raw.length()-.001)throw new IllegalArgumentException("匝道/结构未在本路段让出通行空间，或不足以渐变恢复车道；请延长主路或调整汇入位置/高程");
    return end;
  }
  private static double lastBlocked(Mesh sweep,Mesh obstacle,double begin,int sign,double last){
    for(var c:RoadClearance.contacts(sweep,obstacle))if(c.blocked()){
      double d=sign>0?c.to():-c.from();if(d>=sign*begin-.01)last=Math.max(last,d);
    }return last;
  }
  /** Authored stations are retained even for curved roads. */
  public static Mesh laneSweep(Mesh host,int slot){
    var raw=LaneSections.reference(host);var samples=new ArrayList<Sample>();int count=RoadProfile.catalog(raw.settings().style()).lanes();
    for(var s:raw.samples()){
      if(RoadProfile.layout(raw,s).catalog().lanes()!=count)throw new IllegalArgumentException("自动恢复暂不跨车道数变化接缝，请在同一稳定断面内设置分离范围");
      var lane=LanePoints.lane(raw,s.distance(),slot);samples.add(new Sample(lane.position(),s.left(),s.distance(),lane.width()/2+SIDE_MARGIN));
    }
    var bounds=RoadRibbon.mesh(samples,raw.settings());return new Mesh(List.copyOf(samples),raw.settings(),bounds.min(),bounds.max(),raw.length(),raw.closed(),null);
  }
  /** Conservative prism envelope also catches piers and tilted/offset beams after structure planning.
   * The mesh's slab thickness describes the entire prism, not a paper-thin top. */
  private static Mesh envelope(RoadStructures.Part part,Settings settings){
    double frame=part.verticalFrame(),half=part.halfExtent();V a=part.a(),b=part.b();
    double bottom=Math.min(a.y(),b.y())-frame,top=Math.max(a.y(),b.y())+part.height()+frame;
    V direction=b.sub(a).horizontalLength()<1e-6?new V(0,0,1):b.sub(a).horizontalUnit();
    // Include all frame orientations by padding the ends too; never underestimate a tilted part.
    a=a.sub(direction.mul(half));b=b.add(direction.mul(half));V side=direction.left();
    a=new V(a.x(),top,a.z());b=new V(b.x(),top,b.z());double length=b.distance(a);
    var settingsVolume=new Settings(settings.mode(),settings.style(),settings.width(),top-bottom,settings.tension(),settings.arcDegrees());
    var points=List.of(new Sample(a,side,0,half),new Sample(b,side,length,half));
    double minX=Double.POSITIVE_INFINITY,minZ=minX,maxX=-minX,maxZ=-minX;
    for(var p:points)for(int sign:new int[]{-1,1}){V v=p.at(sign*half,0);minX=Math.min(minX,v.x());maxX=Math.max(maxX,v.x());minZ=Math.min(minZ,v.z());maxZ=Math.max(maxZ,v.z());}
    // Collision-only prism, not an authored RoadRibbon: structural height is allowed to exceed road slab limits.
    return new Mesh(points,settingsVolume,new V(minX,bottom,minZ),new V(maxX,top,maxZ),length,false,null);
  }

  public static void validateRestored(Mesh host,int slot,Mesh ramp,UUID connection){
    var cut=host.settings().options().lanePoints().cuts().stream().filter(c->c.connection().equals(connection)&&c.lane()==slot).findFirst();
    if(cut.isEmpty())return; // A free road end has no downstream lane to restore.
    var c=cut.get();double reopen=c.sign()*c.end()-c.transition();
    if(lastBlocked(laneSweep(host,slot),ramp,c.begin(),c.sign(),c.sign()*c.begin())>=reopen-.01)
      throw new IllegalArgumentException("车道恢复范围仍有匝道净空冲突，已取消建造");
  }
  private LaneReopening(){}
}
