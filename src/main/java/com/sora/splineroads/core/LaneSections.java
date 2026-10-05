package com.sora.splineroads.core;

import com.sora.splineroads.core.RoadGeometry.*;
import java.util.*;

/**
 * A reversible lane reservation: the lane departs at begin and is optionally replaced at end.
 * Raw road samples remain authored data; rendering AND collision use the same effective ribbon.
 * Slot IDs never renumber when an outer lane is absent. An absent slot remains a REPLACE target.
 */
public final class LaneSections {
  public record Cut(UUID connection,int lane,int sign,double begin,double end,double transition,UUID replacement) {
    public Cut(UUID connection,int lane,int sign,double begin,double end,double transition){this(connection,lane,sign,begin,end,transition,null);}
    public Cut {
      if(connection==null||lane<0||lane>31||(sign!=1&&sign!=-1)||!RoadGeometry.finite(begin,end,transition)
          ||transition<2||transition>256||sign*(end-begin)<-.0001)
        throw new IllegalArgumentException("车道分离区间无效");
    }
    public double removed(double station){
      double distance=sign*(end-begin);if(distance<1e-6)return 0;
      double span=Math.min(transition,distance/2);
      return Math.min(smooth(sign*(station-begin)/span),smooth(sign*(end-station)/span));
    }
    private static double smooth(double t){return Settings.smooth(Math.max(0,Math.min(1,t)));}
  }
  public enum Kind { DEPART, REPLACE }
  public record Event(UUID connection,Kind kind,int lane,int sign,double station,double transition){}
  /** Derive from the complete live link set, so deleting a branch restores authored pavement. */
  public static List<Cut> derive(Mesh mesh,List<Event> events){
    var raw=reference(mesh);var cuts=new ArrayList<Cut>();var used=new HashSet<UUID>();
    var ordered=new ArrayList<>(events);ordered.sort(Comparator.comparingDouble(e->e.station()*e.sign()));
    for(Event event:ordered)if(event.kind()==Kind.DEPART){
      var lane=LanePoints.lane(raw,event.station(),event.lane());
      var layout=RoadProfile.layout(raw,RoadStructures.sample(raw,event.station()));
      var c=layout.catalog();int per=c.lanes()/2;
      if(event.sign()!=lane.sign())throw new IllegalArgumentException("分离方向与所选车道的实际行驶方向不一致");
      boolean outer=c.twoWay()?event.lane()==per-1||event.lane()==c.lanes()-1:event.lane()==0||event.lane()==c.lanes()-1;
      if(!outer)throw new IllegalArgumentException(c.twoWay()?"双向整车道分离请选择该方向最外侧车道，不能挖走内部车道或中央隔离":"整车道分离须选择单向主线的一侧边缘车道");
      double remainder=event.sign()>0?raw.length()-event.station():event.station();
      if(remainder<.02)continue; // A free road end already has no downstream continuation.
      if(layout.catalog().lanes()==1)throw new IllegalArgumentException("单车道内部一分二请选择保留原车道分流；整车道分离会清空主路");
      Event replacement=null;
      for(Event candidate:ordered)if(candidate.kind()==Kind.REPLACE&&candidate.lane()==event.lane()&&candidate.sign()==event.sign()
          &&candidate.sign()*(candidate.station()-event.station())>1e-4){replacement=candidate;break;}
      double end=replacement==null?(event.sign()>0?raw.length()+2*event.transition():-2*event.transition()):replacement.station();
      for(Cut old:cuts)if(old.lane()==event.lane()&&old.sign()==event.sign()
          &&event.sign()*(event.station()-old.begin())>=0&&event.sign()*(event.station()-old.end())<-.01)
        throw new IllegalArgumentException("同一车道空位内重复整车道分离；Y 分叉请使用保留原车道分流");
      if(replacement!=null&&!used.add(replacement.connection()))throw new IllegalArgumentException("补入车道同时匹配多个分离接头");
      cuts.add(new Cut(event.connection(),event.lane(),event.sign(),event.station(),end,event.transition(),replacement==null?null:replacement.connection()));
    }
    for(Event event:ordered)if(event.kind()==Kind.REPLACE&&!used.contains(event.connection()))
      throw new IllegalArgumentException("补入模式需要同一车道上游已经整车道分离形成空位；普通汇入请选并入现有车道");
    return List.copyOf(cuts);
  }
  public static Mesh reference(Mesh mesh){return mesh.reference()==null?mesh:mesh.reference();}
  public static double removed(Mesh mesh,double station,int slot){
    double removed=0;for(Cut cut:mesh.settings().options().lanePoints().cuts())if(cut.lane()==slot)removed=Math.max(removed,cut.removed(station));return removed;
  }
  public static boolean active(Mesh mesh,double station,int slot){return removed(mesh,station,slot)<.5;}
  private record Section(double shift,double half,RoadProfile.Layout layout){}
  private static Section section(Mesh raw,Sample s){
    var layout=RoadProfile.layout(raw,s);var c=layout.catalog();int count=c.lanes();

    double[] removal=new double[count];
    for(Cut cut:raw.settings().options().lanePoints().cuts()){
      if(cut.lane()>=count){if(cut.removed(s.distance())>.001)throw new IllegalArgumentException("分离车道穿过了车道数变化接缝，请在同一断面路段内设置接头");continue;}
      removal[cut.lane()]=Math.max(removal[cut.lane()],cut.removed(s.distance()));
    }
    double trimLow=0,trimHigh=0;
    if(c.twoWay()) {
      int per=count/2;
      for(int i=0;i<count;i++)if(removal[i]>.000001) {
        if(i!=per-1&&i!=count-1)throw new IllegalArgumentException("双向整车道分离只支持各方向最外侧车道");
        if(count!=RoadProfile.catalog(raw.settings().style()).lanes())
          throw new IllegalArgumentException("分离区间不能跨越车道数变化接缝，请在同一稳定断面内设置接头");
        if(i<per)trimLow+=layout.laneWidth()*removal[i];else trimHigh+=layout.laneWidth()*removal[i];
      }
      if(layout.medianEdge(-1)-layout.motorMin()-trimLow<-.001||
          layout.motorMax()-layout.medianEdge(1)-trimHigh<-.001)
        throw new IllegalArgumentException("分离范围超出相应半幅机动车道");
    } else {
      int low=0,high=count-1;
      while(low<count&&removal[low]>.000001){trimLow+=layout.laneWidth()*removal[low];low++;}
      while(high>=low&&removal[high]>.000001){trimHigh+=layout.laneWidth()*removal[high];high--;}
      for(int i=low;i<=high;i++)if(removal[i]>.000001)throw new IllegalArgumentException("不能在保留两侧车道时挖走内部车道；请先通过普通分流接头将它引至外侧");
    }
    double motorWidth=layout.motorMax()-layout.motorMin()-layout.median()-trimLow-trimHigh;
    if(motorWidth<layout.laneWidth()*.99)throw new IllegalArgumentException("整车道分离后至少保留一条主路通行车道，不能挖空整条道路");
    double shift=(trimLow-trimHigh)/2,half=s.halfWidth()-(trimLow+trimHigh)/2;
    double min=layout.motorMin()+trimLow-shift,max=layout.motorMax()-trimHigh-shift;
    int retained=count;for(double value:removal)if(value>=.5)retained--;
    var dividers=new ArrayList<Double>();for(double d:layout.dividers())dividers.add(Math.max(min,Math.min(max,d-shift)));
    var catalog=c.twoWay()?c:new RoadProfile.Catalog(c.type(),Math.max(1,retained),false,c.median(),c.shoulder());
    return new Section(shift,half,new RoadProfile.Layout(catalog,layout.laneWidth(),layout.median(),min,max,
      layout.cycleWidth(),layout.curbWidth(),layout.shoulderWidth(),layout.outside(),List.copyOf(dividers),layout.medianCenter()-shift));
  }
  public static Mesh apply(Mesh mesh){
    if(mesh.settings().options().lanePoints().cuts().isEmpty()||mesh.reference()!=null)return mesh;
    var stations=new TreeSet<Double>();for(var s:mesh.samples())stations.add(s.distance());
    for(var cut:mesh.settings().options().lanePoints().cuts())for(double center:new double[]{cut.begin(),cut.end()})
      for(double d=center-cut.transition();d<=center+cut.transition()+.001;d+=.5)if(d>0&&d<mesh.length())stations.add(d);
    if(stations.size()>RoadLimits.MAX_SAMPLES)throw new IllegalArgumentException("车道过渡采样数量超限，请缩短路段");
    var samples=new ArrayList<Sample>();
    for(double station:stations){if(!samples.isEmpty()&&station-samples.get(samples.size()-1).distance()<1e-6)continue;var s=RoadStructures.sample(mesh,station);var section=section(mesh,s);
      samples.add(new Sample(s.center().add(s.left().mul(section.shift())),s.left(),s.distance(),section.half()));}
    Mesh physical=RoadRibbon.mesh(samples,mesh.settings());
    // Keep authored longitudinal stations. The reference describes the unshifted slot axes.
    return new Mesh(List.copyOf(samples),mesh.settings(),physical.min(),physical.max(),mesh.length(),mesh.closed(),mesh.controlPoint(),mesh.controls(),mesh);
  }
  public static RoadProfile.Layout layout(Mesh mesh,Sample sample){
    if(mesh.reference()==null)return null;
    return section(mesh.reference(),RoadStructures.sample(mesh.reference(),sample.distance())).layout();
  }
  public static V anchor(Mesh mesh,double station){return RoadStructures.sample(reference(mesh),station).center();}
  private LaneSections(){}
}
