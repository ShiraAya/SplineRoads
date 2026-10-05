package com.sora.splineroads.core;

import com.sora.splineroads.core.RoadGeometry.*;
import java.util.*;

/** Lane-specific ramp ports. No attached-road-point conversion exists. */
public final class LanePoints {
  public enum Origin { AUTOMATIC_START, AUTOMATIC_END, MANUAL }
  /** Anchor is the longitudinal position on the road spine; position is a derived lane-center cache. */
  public record Point(UUID id,Origin origin,int lane,V position,V anchor) {
    public Point {Objects.requireNonNull(id);Objects.requireNonNull(origin);if(lane<0||lane>31||position==null||!RoadGeometry.finite(position.x(),position.y(),position.z())||anchor!=null&&!RoadGeometry.finite(anchor.x(),anchor.y(),anchor.z()))throw new IllegalArgumentException("车道点数据无效");}
    /** Read compatibility for pre-anchor saves. Canonicalize once against the actual road mesh. */
    public Point(UUID id,Origin origin,int lane,V position){this(id,origin,lane,position,null);}
    public boolean automatic(){return origin!=Origin.MANUAL;}
    public Point at(Lane selected,Mesh mesh){return new Point(id,origin,selected.index(),selected.position(),LaneSections.anchor(mesh,selected.station()));}
  }
  public record Ref(UUID road,UUID point,UUID junction) {
    public Ref {if((junction!=null)==(road!=null||point!=null)||junction==null&&(road==null||point==null))throw new IllegalArgumentException("连接目标必须为具体车道点或路口中心");}
    public static Ref lane(UUID road,UUID point){return new Ref(road,point,null);}
    public static Ref junction(UUID junction){return new Ref(null,null,junction);}
  }
  public enum Path { AUTO("自动"),RIGHT("右转"),LEFT_LOOP("左转回环"),DIRECT("直接连接"),LEFT("定向左转");public final String label;Path(String label){this.label=label;} }
  public enum Departure { BRANCH("保留原车道分流"), DETACH("整车道分离"), EXTRA("额外扩出"); public final String label; Departure(String label){this.label=label;} }
  public enum Arrival { MERGE("并入现有车道"), REPLACE("补入车道空位"), EXTRA("额外扩入"); public final String label; Arrival(String label){this.label=label;} }
  public enum Elevation { AUTO("自动避让"), OVER("上跨既有道路"), UNDER("下穿既有道路"), KEEP("保持原高程"); public final String label; Elevation(String label){this.label=label;} }
  public enum Landing { FLEXIBLE("同车道弹性落点"), EXACT("精确锁定 B"); public final String label; Landing(String label){this.label=label;} }
  public record Options(Path path,Departure departure,Arrival arrival,double radius,double transition,Elevation elevation,Landing landing) {
    // Existing saves and ordinary branching keep their original meaning. DETACH is explicit.
    public static final Options DEFAULT=new Options(Path.AUTO,Departure.BRANCH,Arrival.MERGE,24,32,Elevation.AUTO,Landing.FLEXIBLE);
    public Options(Path path,boolean sourceExtra,boolean targetExtra,double radius,double transition){this(path,sourceExtra?Departure.EXTRA:Departure.BRANCH,targetExtra?Arrival.EXTRA:Arrival.MERGE,radius,transition,Elevation.AUTO,Landing.FLEXIBLE);}
    public Options {if(path==null||departure==null||arrival==null||elevation==null||landing==null||!RoadGeometry.finite(radius,transition)||radius<8||radius>256||transition<8||transition>256)throw new IllegalArgumentException("半径与过渡长度须为 8–256 格，且连接模式有效");}
    public boolean sourceExtra(){return departure==Departure.EXTRA;}
    public boolean targetExtra(){return arrival==Arrival.EXTRA;}
    public Options withoutApproaches(){return new Options(path,Departure.BRANCH,Arrival.MERGE,radius,transition,elevation,landing);}
  }
  /** Target offset is measured along the selected lane's driving direction, never another lane. */
  public record Link(Ref from,Ref to,Options options,V junctionMouth,double targetOffset) {
    public Link(Ref from,Ref to,Options options,V junctionMouth){this(from,to,options,junctionMouth,0);}
    public Link {Objects.requireNonNull(from);Objects.requireNonNull(to);Objects.requireNonNull(options);if(from.junction()!=null||from.equals(to))throw new IllegalArgumentException("匝道须从车道点汇出，且不能接回同一点");if(to.junction()!=null&&junctionMouth==null)throw new IllegalArgumentException("缺少路口实际接入口");if(!Double.isFinite(targetOffset)||Math.abs(targetOffset)>128||to.junction()!=null&&targetOffset!=0)throw new IllegalArgumentException("汇入偏移超出允许范围");}
    public Link targetOffset(double value){return new Link(from,to,options,junctionMouth,value);}
  }
  /** Actual connector centerline at a contact, used to clear crossing rail/curb geometry. */
  public record Opening(UUID connection,List<V> centerline,double halfWidth) {
    public Opening {centerline=List.copyOf(centerline);if(centerline.size()>16000||halfWidth<=0||halfWidth>16)throw new IllegalArgumentException("匝道接头范围无效");}
  }
  public record Data(List<Point> points,Link link,List<Opening> openings,int priorityDepth,List<LaneSections.Cut> cuts) {
    public static final Data EMPTY=new Data(List.of(),null,List.of(),0,List.of());
    public Data(List<Point> points,Link link,List<Opening> openings){this(points,link,openings,link==null?0:1,List.of());}
    public Data(List<Point> points,Link link,List<Opening> openings,int priorityDepth){this(points,link,openings,priorityDepth,List.of());}
    public Data {points=List.copyOf(points);openings=List.copyOf(openings);cuts=List.copyOf(cuts);if(cuts.size()>128||points.size()>512||openings.size()>128||priorityDepth<0||priorityDepth>1024||points.stream().map(Point::id).distinct().count()!=points.size())throw new IllegalArgumentException("车道点数量超限、身份重复或依赖深度无效");}
    public Data points(List<Point> value){return new Data(value,link,openings,priorityDepth,cuts);}
    public Data link(Link value){return new Data(points,value,openings,value==null?0:Math.max(1,priorityDepth),cuts);}
    public Data openings(List<Opening> value){return new Data(points,link,value,priorityDepth,cuts);}
    public Data priorityDepth(int value){return new Data(points,link,openings,value,cuts);}
    public Data cuts(List<LaneSections.Cut> value){return new Data(points,link,openings,priorityDepth,value);}
  }
  public record Lane(int index,V position,V direction,double width,double station,int sign){}
  public static Lane lane(Mesh mesh,double station,int index){
    mesh=LaneSections.reference(mesh);Sample sample=RoadStructures.sample(mesh,Math.max(0,Math.min(mesh.length(),station)));var l=RoadProfile.layout(mesh,sample);var c=l.catalog();
    if(index<0||index>=c.lanes())throw new IllegalArgumentException("所选车道已不存在");
    double offset;if(c.twoWay()){int per=c.lanes()/2,side=index<per?-1:1;offset=side*(l.median()/2+(index%per+.5)*l.laneWidth());}else offset=l.motorMin()+(index+.5)*l.laneWidth();
    int sign=c.twoWay()&&(offset<0?-1:1)!=l.outside()?-1:1;
    return new Lane(index,sample.at(offset,0),sample.left().left().mul(-sign),l.laneWidth(),sample.distance(),sign);
  }
  public static Lane lane(Mesh mesh,Point point){return lane(mesh,RoadQueries.project(LaneSections.reference(mesh),point.anchor()==null?point.position():point.anchor()).sample().distance(),point.lane());}
  public static Point snap(Mesh mesh,Point point){var selected=lane(mesh,point);V anchor=LaneSections.anchor(mesh,selected.station());if(point.anchor()!=null&&point.position().distance(selected.position())<1e-7&&point.anchor().distance(anchor)<1e-7)return point;return point.at(selected,mesh);}
  public static Point migrate(Mesh previous,Mesh target,Point point){
    Lane old=lane(previous,point);target=LaneSections.reference(target);var q=RoadQueries.horizontal(target,point.position());
    Lane best=null;double distance=Double.POSITIVE_INFINITY;
    int count=RoadProfile.layout(target,q.sample()).catalog().lanes();
    for(int index=0;index<count;index++){
      Lane candidate=lane(target,q.sample().distance(),index);
      if(candidate.direction().dot(old.direction())<.999)continue;
      double d=candidate.position().sub(point.position()).horizontalLength();
      if(d<distance){distance=d;best=candidate;}
    }
    if(best==null||distance>Math.max(.1,old.width()*.25))
      throw new IllegalArgumentException("相邻道路找不到保持位置及通行方向的对应车道，已取消归属迁移");
    return point.at(best,target);
  }
  public static Point point(UUID id,Origin origin,Mesh mesh,double station,int index){var selected=lane(mesh,station,index);return new Point(id,origin,index,selected.position(),LaneSections.anchor(mesh,selected.station()));}
  public static Lane clicked(Mesh mesh,V hit){
    var raw=LaneSections.reference(mesh);var q=RoadQueries.project(raw,hit);var l=RoadProfile.layout(raw,q.sample());
    Lane best=null;double distance=Double.POSITIVE_INFINITY;
    for(int i=0;i<l.catalog().lanes();i++){if(!LaneSections.active(mesh,q.sample().distance(),i))continue;var lane=lane(mesh,q.sample().distance(),i);double d=lane.position().sub(hit).horizontalLength();if(d<distance){distance=d;best=lane;}}
    if(best==null||distance>best.width()/2+.05||Math.abs(hit.y()-best.position().y())>2)throw new IllegalArgumentException("请点击机动车道内，不能在中央隔离带、人行道或非机动车道放点");return best;
  }
  public static String label(Mesh mesh,int index){var lane=lane(mesh,mesh.length()/2,index);var c=RoadProfile.catalog(mesh.settings().style());return (c.twoWay()?(lane.sign()>0?"正向":"反向")+" ":"单向 ")+(c.twoWay()?index%(c.lanes()/2)+1:index+1)+" 车道";}
  public static boolean supported(Settings s){var type=RoadProfile.catalog(s.style()).type();if(type!=RoadProfile.Type.ORDINARY&&type!=RoadProfile.Type.HIGHWAY)return false;if(s.structure()==Structure.TUNNEL)return false;if(s.structure()!=Structure.BRIDGE)return true;return switch(s.options().infrastructure().bridge()){case STANDARD,BEAM,OVERPASS->true;default->false;};}
  public static boolean opening(Mesh mesh,V location){
    for(var opening:mesh.settings().options().lanePoints().openings())for(int i=1;i<opening.centerline().size();i++){
      V a=opening.centerline().get(i-1),b=opening.centerline().get(i),d=b.sub(a);double length=d.x()*d.x()+d.z()*d.z();if(length<1e-9)continue;double t=Math.max(0,Math.min(1,(location.x()-a.x())*d.x()/length+(location.z()-a.z())*d.z()/length));V p=a.add(d.mul(t));
      if(Math.abs(location.y()-p.y())<2.2&&location.sub(p).horizontalLength()<opening.halfWidth()+.4)return true;
    }return false;
  }
  private LanePoints(){}
}
