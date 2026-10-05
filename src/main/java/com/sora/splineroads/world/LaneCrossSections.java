package com.sora.splineroads.world;

import com.sora.splineroads.core.*;
import java.util.*;

/** Computes reversible host lane reservations from real connector links, never from paint. */
public final class LaneCrossSections {
  public static Map<UUID,RoadRecord> staged(Map<UUID,RoadRecord> records,UUID edited,LanePoints.Link proposal){
    var result=new LinkedHashMap<>(records);derive(result,edited,proposal,null);return result;
  }
  public static void reconcile(Map<UUID,RoadRecord> records){derive(records,null,null,null);}
  /** A local edit must not repair or validate reservations elsewhere in the save. */
  static void reconcile(Map<UUID,RoadRecord> records,Set<UUID> hosts){derive(records,null,null,hosts);}
  private static void derive(Map<UUID,RoadRecord> records,UUID edited,LanePoints.Link proposal,Set<UUID> hosts){
    var events=new HashMap<UUID,List<LaneSections.Event>>();
    for(var road:records.values())if(!road.id().equals(edited))add(events,records,road.id(),LaneTopology.metadata(road).link(),hosts);
    if(proposal!=null)add(events,records,edited,proposal,hosts);
    for(var road:new ArrayList<>(records.values())){
      if(hosts!=null&&!hosts.contains(road.id()))continue;
      var md=LaneTopology.metadata(road);var values=events.getOrDefault(road.id(),List.of());
      var cuts=values.isEmpty()?List.<LaneSections.Cut>of():LaneSections.derive(road.rawMesh(),values);
      if(!cuts.equals(md.cuts()))records.put(road.id(),road.withLanePoints(md.cuts(cuts)));
    }
    // Materialize now: all checks happen before the first world cell is written.
    for(UUID id:events.keySet())records.get(id).mesh();
  }
  private static void add(Map<UUID,List<LaneSections.Event>> events,Map<UUID,RoadRecord> all,UUID connection,LanePoints.Link link,Set<UUID> hosts){
    if(link==null)return;
    if(link.options().departure()==LanePoints.Departure.DETACH&&(hosts==null||hosts.contains(link.from().road())))add(events,all,connection,link.from(),LaneSections.Kind.DEPART,0,link.options().transition());
    if(link.options().arrival()==LanePoints.Arrival.REPLACE&&(hosts==null||hosts.contains(link.to().road()))){
      if(link.to().road()==null)throw new IllegalArgumentException("路口中心不能作为车道空位补入目标");
      add(events,all,connection,link.to(),LaneSections.Kind.REPLACE,link.targetOffset(),link.options().transition());
    }
  }
  private static void add(Map<UUID,List<LaneSections.Event>> events,Map<UUID,RoadRecord> all,UUID connection,LanePoints.Ref ref,LaneSections.Kind kind,double offset,double transition){
    var road=all.get(ref.road());if(road==null)throw new IllegalArgumentException("车道接头引用的道路已不存在");
    var point=LaneTopology.point(road,ref.point());var lane=LanePoints.lane(road.rawMesh(),point);
    double station=lane.station()+offset*lane.sign();
    if(station<-.001||station>road.rawMesh().length()+.001)throw new IllegalArgumentException("车道补入位置超出所属路段");
    events.computeIfAbsent(road.id(),key->new ArrayList<>()).add(new LaneSections.Event(connection,kind,point.lane(),lane.sign(),station,transition));
  }
  private LaneCrossSections(){}
}
