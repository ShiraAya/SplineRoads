package com.sora.splineroads.core;
import com.sora.splineroads.core.RoadGeometry.*;
import java.util.*;
/** Authored local lane drop anchored to a manual lane point, not a fake ramp link.
 * Existing DEPART/REPLACE geometry supplies the taper and reversible empty slot. */
public final class LaneMerge {
  public static LaneSections.Event event(Mesh mesh,LanePoints.Point point){
    var raw=LaneSections.reference(mesh);var lane=LanePoints.lane(raw,point);var c=RoadProfile.catalog(raw.settings().style());
    if(point.automatic()||point.mergeLength()<8||!LanePoints.supported(raw.settings()))throw new IllegalArgumentException("请选择独立道路上的手动外侧车道点");
    int per=c.twoWay()?c.lanes()/2:c.lanes();
    if(per<2)throw new IllegalArgumentException("该方向至少两条车道才能向同向邻道合流，不能汇入对向车道");
    boolean outer=c.twoWay()?point.lane()==per-1||point.lane()==c.lanes()-1:point.lane()==0||point.lane()==c.lanes()-1;
    if(!outer)throw new IllegalArgumentException("合流缩减仅支持最外侧车道，不能挖走内部车道");
    double remaining=lane.sign()>0?raw.length()-lane.station():lane.station();
    if(remaining<point.mergeLength()+2)throw new IllegalArgumentException("下游长度不足以完成合流渐变，请前移此车道点");
    int neighbor=c.twoWay()?point.lane()-1:point.lane()==0?1:point.lane()-1;
    for(double d=0;d<=point.mergeLength();d+=.5){double at=lane.station()+lane.sign()*d;
      if(!LaneSections.active(mesh,at,neighbor))throw new IllegalArgumentException("同向接收车道在合流区已封闭，不能把车流导入空位");
    }
    return new LaneSections.Event(point.id(),LaneSections.Kind.DEPART,point.lane(),lane.sign(),lane.station(),point.mergeLength());
  }
  public static boolean allowed(Mesh mesh,LanePoints.Point point){try{event(mesh,point.merge(32));return true;}catch(IllegalArgumentException e){return false;}}
  public static boolean sameDefinitions(LanePoints.Data a,LanePoints.Data b){
    return a.points().stream().filter(p->p.mergeLength()>0).toList().equals(b.points().stream().filter(p->p.mergeLength()>0).toList());
  }
  public static boolean linkedTo(Mesh host,Mesh candidate){
    var l=candidate.settings().options().lanePoints().link();if(l==null)return false;
    for(var point:host.settings().options().lanePoints().points())if(point.id().equals(l.from().point())||point.id().equals(l.to().point()))return true;
    return false;
  }
  private LaneMerge(){}
}
