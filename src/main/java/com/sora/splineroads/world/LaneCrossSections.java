package com.sora.splineroads.world;

import com.sora.splineroads.core.*;
import java.util.*;

/** Computes reversible host lane reservations from real connector links, never from paint. */
public final class LaneCrossSections {
  public static Map<UUID,RoadRecord> staged(Map<UUID,RoadRecord> records,UUID edited,LanePoints.Link proposal){
    return staged(records,edited,proposal,null);
  }
  public static Map<UUID,RoadRecord> staged(Map<UUID,RoadRecord> records,UUID edited,LanePoints.Link proposal,RoadGeometry.Mesh candidate){
    var result=new LinkedHashMap<>(records);Set<UUID> hosts=new HashSet<>();hosts.add(proposal.from().road());
    if(proposal.to().road()!=null)hosts.add(proposal.to().road());
    var old=records.get(edited);var oldLink=old==null?null:LaneTopology.metadata(old).link();
    if(oldLink!=null){hosts.add(oldLink.from().road());if(oldLink.to().road()!=null)hosts.add(oldLink.to().road());}
    derive(result,edited,proposal,hosts,candidate);return result;
  }
  public static void reconcile(Map<UUID,RoadRecord> records){derive(records,null,null,null,null);}
  /** A local edit must not repair or validate reservations elsewhere in the save. */
  static void reconcile(Map<UUID,RoadRecord> records,Set<UUID> hosts){derive(records,null,null,hosts,null);}
  private static void derive(Map<UUID,RoadRecord> records,UUID edited,LanePoints.Link proposal,Set<UUID> hosts,RoadGeometry.Mesh candidate){
    if(hosts!=null){
      // Include adjacent hosts and old reservations so edits/deletes clear the
      // complete previous footprint, including a closure across a road seam.
      var expanded=new HashSet<>(hosts);var removedConnections=new HashSet<UUID>();
      for(UUID host:hosts){var r=records.get(host);if(r!=null)for(var cut:LaneTopology.metadata(r).cuts())if(!records.containsKey(cut.connection()))removedConnections.add(cut.connection());}
      for(var r:records.values())if(LaneTopology.metadata(r).cuts().stream().anyMatch(c->removedConnections.contains(c.connection())))expanded.add(r.id());
      for(var road:records.values()){
        var link=LaneTopology.metadata(road).link();
        if(link!=null&&(expanded.contains(link.from().road())||expanded.contains(link.to().road())||road.id().equals(edited))){
          expanded.addAll(LaneRoadChain.of(records,link.from()).ids());
          if(link.to().road()!=null)expanded.addAll(LaneRoadChain.of(records,link.to()).ids());
        }
        for(var cut:LaneTopology.metadata(road).cuts())if(cut.connection().equals(edited))expanded.add(road.id());
      }
      if(proposal!=null){expanded.addAll(LaneRoadChain.of(records,proposal.from()).ids());if(proposal.to().road()!=null)expanded.addAll(LaneRoadChain.of(records,proposal.to()).ids());}
      hosts=expanded;
    }
    deriveAdditions(records,edited,proposal,hosts);
    var events=new HashMap<UUID,List<LaneSections.Event>>();
    for(var road:records.values())if(!road.id().equals(edited)){
      var link=LaneTopology.metadata(road).link();if(link==null)continue;
      if(hosts==null||hosts.contains(link.from().road())||hosts.contains(link.to().road()))
        add(events,records,road.id(),link,hosts,link.options().departure()==LanePoints.Departure.TEMPORARY||link.closesTarget()?road.rawMesh():null,road.structures());
    }
    for(var road:records.values())if(hosts==null||hosts.contains(road.id()))for(var point:LaneTopology.metadata(road).points())if(point.mergeLength()>0){
      var event=LaneMerge.event(road.rawMesh(),point);
      events.computeIfAbsent(road.id(),k->new ArrayList<>()).add(event);
    }
    if(proposal!=null)add(events,records,edited,proposal,hosts,candidate,List.of());
    for(var road:new ArrayList<>(records.values())){
      if(hosts!=null&&!hosts.contains(road.id()))continue;
      var md=LaneTopology.metadata(road);var values=events.getOrDefault(road.id(),List.of());
      var cuts=values.isEmpty()?List.<LaneSections.Cut>of():LaneSections.derive(road.rawMesh(),values);
      if(!cuts.equals(md.cuts()))records.put(road.id(),road.withLanePoints(md.cuts(cuts)));
    }
    for(var road:records.values())if((hosts==null||hosts.contains(road.id()))&&!LaneTopology.metadata(road).additions().isEmpty())LaneAdditions.validate(road.mesh());
    // Materialize now: all checks happen before the first world cell is written.
    for(UUID id:events.keySet()){
      var host=records.get(id);var effective=host.mesh();
      for(var point:LaneTopology.metadata(host).points())if(point.mergeLength()>0)LaneMerge.event(effective,point);
    }
  }
  private static void deriveAdditions(Map<UUID,RoadRecord> records,UUID edited,LanePoints.Link proposal,Set<UUID> hosts){
    var requests=new LinkedHashMap<UUID,LanePoints.Link>();
    for(var road:records.values())if(!road.id().equals(edited)){
      var l=LaneTopology.metadata(road).link();if(l!=null&&l.options().arrival()==LanePoints.Arrival.ADD)requests.put(road.id(),l);
    }
    if(proposal!=null&&proposal.options().arrival()==LanePoints.Arrival.ADD)requests.put(edited,proposal);
    var additions=new HashMap<UUID,List<LaneAdditions.Addition>>();
    for(var entry:requests.entrySet()){
      var link=entry.getValue();if(link.to().road()==null)throw new IllegalArgumentException("新增外侧车道需要选择道路车道点，不能使用路口中心");
      if(hosts!=null&&!hosts.contains(link.to().road()))continue;
      var resolved=LaneRoadChain.of(records,link.to()).at(link.targetOffset());var road=resolved.road();
      var point=resolved.point();var raw=road.rawMesh();var lane=LanePoints.lane(raw,point);
      double station=resolved.station();
      if(station<-.001||station>raw.length()+.001)throw new IllegalArgumentException("新增车道汇入口超出所选道路");
      var list=additions.computeIfAbsent(road.id(),k->new ArrayList<>());
      var previous=LaneTopology.metadata(road).additions().stream().filter(a->a.connection().equals(entry.getKey())).findFirst().orElse(null);
      int slot=previous==null?8:previous.slot();
      if(previous==null){var reserved=new HashSet<Integer>();for(var a:LaneTopology.metadata(road).additions())reserved.add(a.slot());for(var a:list)reserved.add(a.slot());while(reserved.contains(slot))slot++;}
      if(slot>31)throw new IllegalArgumentException("此路段新增车道身份数量达到上限，请分段建设");
      list.add(new LaneAdditions.Addition(entry.getKey(),slot,lane.sign(),station,link.options().transition()));
    }
    for(var road:new ArrayList<>(records.values()))if(hosts==null||hosts.contains(road.id())){
      var list=additions.getOrDefault(road.id(),List.of());var md=LaneTopology.metadata(road);
      if(!list.equals(md.additions()))records.put(road.id(),road.withLanePoints(md.additions(list)));
    }
  }
  private static void add(Map<UUID,List<LaneSections.Event>> events,Map<UUID,RoadRecord> all,UUID connection,LanePoints.Link link,Set<UUID> hosts,RoadGeometry.Mesh candidate,List<RoadStructures.Part> parts){
    if(link==null)return;
    if(link.options().separatesLane()&&(hosts==null||hosts.contains(link.from().road()))) {
      if(link.options().departure()==LanePoints.Departure.DETACH)add(events,all,connection,link.from(),LaneSections.Kind.DEPART,0,link.options().transition());
      else if(candidate!=null) {
        // No invented chain-wide provisional cut: real ranges require a candidate.
        // Slot-specific collision guards remain active during route search.
        LaneRoadChain.of(all,link.from()).reserve(events,connection,candidate,parts,false,0,link.options().transition(),link.rectangularClosure());
      }
    }
    if(candidate!=null&&link.closesTarget()&&(hosts==null||hosts.contains(link.to().road()))){
      LaneRoadChain.of(all,link.to()).reserve(events,connection,candidate,parts,true,link.targetOffset(),link.options().transition(),link.rectangularClosure());
    }

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
  /** After structures were planned, rederive only temporary hosts touched by this transaction. */
  static boolean needsRestoreRefresh(List<RoadIndex.Built> planning,Collection<UUID> changed){
    Set<UUID> changes=new HashSet<>(changed),hosts=new HashSet<>();var all=new LinkedHashMap<UUID,RoadRecord>();
    for(var b:planning)all.put(b.record.id(),b.record);
    for(var r:all.values()){
      var l=LaneTopology.metadata(r).link();if(l==null)continue;
      if(l.options().departure()==LanePoints.Departure.TEMPORARY&&(changes.contains(r.id())||changes.contains(l.from().road())))hosts.add(l.from().road());
      if(l.closesTarget()&&(changes.contains(r.id())||changes.contains(l.to().road())))hosts.add(l.to().road());
    }
    if(hosts.isEmpty())return false;var staged=new LinkedHashMap<>(all);derive(staged,null,null,hosts,null);
    for(UUID host:all.keySet())if(!LaneTopology.metadata(all.get(host)).cuts().equals(LaneTopology.metadata(staged.get(host)).cuts()))return true;
    return false;
  }
  private LaneCrossSections(){}
}
