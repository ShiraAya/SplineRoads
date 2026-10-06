package com.sora.splineroads.world;

import com.sora.splineroads.core.*;
import com.sora.splineroads.core.RoadGeometry.*;
import java.util.*;
import java.nio.charset.StandardCharsets;

/** Stages point topology and dependent connectors before RoadData writes any world cells. */
public final class LaneTopology {
  public static LanePoints.Data metadata(RoadRecord r){return r.settings().options().lanePoints();}
  public static LanePoints.Point point(RoadRecord r,UUID id){return metadata(r).points().stream().filter(p->p.id().equals(id)).findFirst().orElseThrow(()->new IllegalArgumentException("车道点已不存在，请重新选择"));}
  static boolean independent(RoadRecord r){return r.junction()==null;}
  static boolean normal(RoadRecord r){return r.junction()==null&&metadata(r).link()==null;}
  private static Map<net.minecraft.core.BlockPos,Set<UUID>> endpointOwners(Collection<RoadRecord> roads){
    var owners=new HashMap<net.minecraft.core.BlockPos,Set<UUID>>();
    for(var r:roads)if(metadata(r).link()==null){
      owners.computeIfAbsent(r.a(),k->new HashSet<>()).add(r.id());
      owners.computeIfAbsent(r.b(),k->new HashSet<>()).add(r.id());
    }
    return owners;
  }
  private static boolean free(RoadRecord r,Mesh mesh,boolean first,Map<net.minecraft.core.BlockPos,Set<UUID>> owners){
    if(mesh.closed()||r.junction()!=null||metadata(r).link()!=null)return false;
    var ids=owners.getOrDefault(first?r.a():r.b(),Set.of());
    return ids.isEmpty()||ids.size()==1&&ids.contains(r.id());
  }
  public static List<UUID> references(Collection<RoadRecord> roads,LanePoints.Ref ref){return roads.stream().filter(r->{var link=metadata(r).link();return link!=null&&(link.from().equals(ref)||link.to().equals(ref));}).map(RoadRecord::id).sorted().toList();}
  public static Set<UUID> dependents(RoadData data,Set<UUID> initial){
    Set<UUID> result=new LinkedHashSet<>(initial);boolean changed;
    do{
      changed=false;
      for(var b:data.index.roads.values()){
        for(var cut:metadata(b.record).cuts())if(result.contains(cut.connection())&&cut.replacement()!=null)
          changed|=result.add(cut.replacement());
        var l=metadata(b.record).link();if(l==null||result.contains(b.record.id()))continue;
        boolean hit=result.contains(l.from().road())||l.to().road()!=null&&result.contains(l.to().road())
          ||l.to().junction()!=null&&result.stream().map(data.index.roads::get).filter(Objects::nonNull)
            .anyMatch(v->l.to().junction().equals(v.record.assembly()));
        if(hit)changed|=result.add(b.record.id());
      }
    }while(changed);
    result.removeAll(initial);return result;
  }
  static Map<UUID,RoadRecord> records(RoadData data){Map<UUID,RoadRecord> all=new LinkedHashMap<>();data.index.roads.forEach((id,b)->all.put(id,b.record));return all;}
  public static void initialize(RoadData data){var all=records(data);LaneCrossSections.reconcile(all);assignPriorities(all);var ends=endpointOwners(all.values());for(var r:new ArrayList<>(all.values()))if(independent(r)){var next=automatic(r,ends);if(!next.equals(data.index.roads.get(r.id()).record)){data.index.put(RoadIndex.Built.loading(next));if(data.streets.containsKey(r.id()))data.streets.put(r.id(),data.streets.get(r.id()).withLanePoints(metadata(next)));data.setDirty();}}}
  private static RoadRecord automatic(RoadRecord r,Map<net.minecraft.core.BlockPos,Set<UUID>> ends){
    var md=metadata(r);List<LanePoints.Point> points=new ArrayList<>();for(var p:md.points())if(!p.automatic())points.add(LanePoints.snap(r.mesh(),p));
    Mesh mesh=r.mesh();int lanes=RoadProfile.catalog(r.settings()).lanes();
    for(boolean first:new boolean[]{true,false})if(free(r,mesh,first,ends))for(int i=0;i<lanes;i++){
      double station=first?0:mesh.length();if(!LaneSections.active(mesh,station,i))continue;
      var origin=first?LanePoints.Origin.AUTOMATIC_START:LanePoints.Origin.AUTOMATIC_END;int lane=i;
      UUID id=md.points().stream().filter(p->p.origin()==origin&&p.lane()==lane).map(LanePoints.Point::id).findFirst().orElse(UUID.nameUUIDFromBytes((r.id()+":lane:"+origin+":"+i).getBytes(StandardCharsets.UTF_8)));
      points.add(LanePoints.point(id,origin,mesh,station,i));
    }return r.withLanePoints(md.points(points));
  }
  static boolean needsRefresh(Collection<RoadIndex.Built> planning){
    return needsRefresh(planning,null);
  }
  /** Only the transaction's records may request another terrain/port stabilization pass. */
  static boolean needsRefresh(Collection<RoadIndex.Built> planning,Collection<UUID> edited){return needsRefresh(null,planning,edited);}
  static boolean needsRefresh(RoadData data,Collection<RoadIndex.Built> planning,Collection<UUID> edited){
    var all=new LinkedHashMap<UUID,RoadRecord>();for(var b:planning)all.put(b.record.id(),b.record);
    var ids=edited==null?all.keySet():new HashSet<>(edited);
    for(UUID id:ids){var r=all.get(id);if(r==null)continue;
      for(var p:metadata(r).points())if(LanePoints.lane(r.mesh(),p).position().distance(p.position())>1e-5)return true;
      var l=metadata(r).link();
      if(l!=null&&!portsMatch(r,all)){
        var old=data==null?null:data.index.roads.get(id);
        if(old==null||!sameDeck(r,old.record)||!Objects.equals(l,metadata(old.record).link())||!samePort(data,all,l.from())||!samePort(data,all,l.to()))return true;
      }
    }return false;
  }
  /** Explicit dependency closure, not an all-world "try to regenerate every connector" pass. */
  static Set<UUID> editScope(RoadData data,Map<UUID,RoadRecord> all,Collection<RoadIndex.Built> built,Set<UUID> removed){
    var scope=new LinkedHashSet<UUID>(removed);
    for(var b:built){var old=data.index.roads.get(b.record.id());
      if(old==null||topologyChanged(old.record,b.record))scope.add(b.record.id());else scope.remove(b.record.id());
    }
    var nodes=new HashSet<net.minecraft.core.BlockPos>();var groups=new HashSet<UUID>();
    for(UUID id:new ArrayList<>(scope)){
      var old=data.index.roads.get(id);addScopeRecord(old==null?null:old.record,scope,nodes,groups);
      addScopeRecord(all.get(id),scope,nodes,groups);
    }
    for(var r:all.values())if(nodes.contains(r.a())||nodes.contains(r.b())||r.assembly()!=null&&groups.contains(r.assembly()))scope.add(r.id());
    boolean added;
    do {added=false;for(var r:all.values()){
      var l=metadata(r).link();if(l==null)continue;
      if(scope.contains(l.from().road())||l.to().road()!=null&&scope.contains(l.to().road())||l.to().junction()!=null&&groups.contains(l.to().junction()))added|=scope.add(r.id());
    }}while(added);
    // A changed connector can reserve/release its host's lane even if the host was not
    // explicitly in the input batch. Do not flood back from these hosts through a road network.
    for(UUID id:new ArrayList<>(scope)){
      var old=data.index.roads.get(id);addLinkHosts(old==null?null:old.record,scope);
      addLinkHosts(all.get(id),scope);
    }
    return scope;
  }
  private static void addLinkHosts(RoadRecord r,Set<UUID> scope){
    if(r==null)return;var l=metadata(r).link();if(l==null)return;
    if(l.from().road()!=null)scope.add(l.from().road());if(l.to().road()!=null)scope.add(l.to().road());
  }
  private static void addScopeRecord(RoadRecord r,Set<UUID> scope,Set<net.minecraft.core.BlockPos> nodes,Set<UUID> groups){
    if(r==null)return;nodes.add(r.a());nodes.add(r.b());if(r.assembly()!=null)groups.add(r.assembly());
    addLinkHosts(r,scope);var l=metadata(r).link();if(l!=null&&l.to().junction()!=null)groups.add(l.to().junction());
  }
  private static boolean topologyChanged(RoadRecord a,RoadRecord b){
    return !a.a().equals(b.a())||!a.b().equals(b.b())||!a.start().equals(b.start())||!a.end().equals(b.end())
        ||!a.settings().equals(b.settings())||!a.alignment().equals(b.alignment())
        ||!Objects.equals(a.assembly(),b.assembly())||!Objects.equals(a.junction(),b.junction());
  }
  private static boolean sameDeck(RoadRecord a,RoadRecord b){
    return a.settings().thickness()==b.settings().thickness()&&a.settings().style()==b.settings().style()
        &&a.settings().options().leftTraffic()==b.settings().options().leftTraffic()
        &&a.mesh().samples().equals(b.mesh().samples());
  }
  private static boolean portsMatch(RoadRecord r,Map<UUID,RoadRecord> all){var l=metadata(r).link();try{var a=LaneRamps.host(all,l.from());V first=LaneRamps.port(a,point(a,l.from().point())).position();V last=l.junctionMouth();if(l.to().road()!=null){var b=LaneRamps.host(all,l.to());last=LaneRamps.targetPort(b,point(b,l.to().point()),l.targetOffset()).position();}return LaneRampAlignment.axis(r.mesh(),true).distance(first)<1e-5&&LaneRampAlignment.axis(r.mesh(),false).distance(last)<1e-5;}catch(IllegalArgumentException e){return false;}}
  static void reconcile(RoadData data,List<RoadIndex.Built> built,Set<UUID> removed){
    Map<UUID,RoadRecord> all=records(data);removed.forEach(all::remove);for(var b:built)all.put(b.record.id(),b.record);
    Set<UUID> scope=editScope(data,all,built,removed);
    Map<LanePoints.Ref,LanePoints.Ref> migrations=new HashMap<>();
    for(var r:new ArrayList<>(all.values()))if(independent(r)&&scope.contains(r.id())){
      var md=metadata(r);List<LanePoints.Point> points=new ArrayList<>();Mesh mesh=r.rawMesh();
      for(var p:md.points()){
        if(p.automatic()) {points.add(p);continue;}
        V anchor=p.anchor()==null?p.position():p.anchor();var q=RoadQueries.project(mesh,anchor);V tangent=q.tangent().horizontalUnit();double beyond=anchor.sub(q.sample().center()).dot(tangent);
        RoadRecord target=r;
        if((q.sample().distance()<1e-6&&beyond<-.05||q.sample().distance()>mesh.length()-1e-6&&beyond>.05)&&data.index.roads.containsKey(r.id())){
          var old=data.index.roads.get(r.id()).record;
          for(var candidate:all.values())if(!candidate.id().equals(r.id())&&normal(candidate)&&adjacentBefore(data,candidate,old)&&p.lane()<RoadProfile.catalog(candidate.settings()).lanes()){
            var v=RoadQueries.horizontal(candidate.rawMesh(),p.position());double along=p.position().sub(v.sample().center()).dot(v.tangent().horizontalUnit());if(v.horizontalDistance()<v.sample().halfWidth()&&!(v.sample().distance()<1e-6&&along<-.05||v.sample().distance()>candidate.mesh().length()-1e-6&&along>.05)){target=candidate;break;}
          }
        }
        var oldRecord=data.index.roads.get(r.id());
        var moved=target.id().equals(r.id())?LanePoints.snap(target.mesh(),p):LanePoints.migrate(oldRecord==null?r.mesh():oldRecord.mesh,target.mesh(),p);
        if(target.id().equals(r.id()))points.add(moved);else {var targetData=metadata(all.get(target.id()));var list=new ArrayList<>(targetData.points());list.add(moved);all.put(target.id(),all.get(target.id()).withLanePoints(targetData.points(list)));scope.add(target.id());migrations.put(LanePoints.Ref.lane(r.id(),p.id()),LanePoints.Ref.lane(target.id(),p.id()));}
      }
      // Keep points migrated into this record by an earlier record in this pass.
      for(var p:metadata(all.get(r.id())).points())if(md.points().stream().noneMatch(x->x.id().equals(p.id())))points.add(p);
      all.put(r.id(),r.withLanePoints(md.points(points)));
    }
    for(var r:new ArrayList<>(all.values())){var l=metadata(r).link();if(l==null)continue;var from=migrations.getOrDefault(l.from(),l.from());var to=migrations.getOrDefault(l.to(),l.to());if(!from.equals(l.from())||!to.equals(l.to()))all.put(r.id(),r.withLanePoints(metadata(r).link(new LanePoints.Link(from,to,l.options(),l.junctionMouth(),l.targetOffset(),l.protectedMerge(),l.rectangularClosure()))));}
    LaneCrossSections.reconcile(all,scope);
    RoadContinuations.reconcile(records(data),all,scope);
    var ends=endpointOwners(all.values());
    for(var r:new ArrayList<>(all.values()))if(independent(r)&&scope.contains(r.id()))all.put(r.id(),automatic(r,ends));
    // A missing used automatic point is a normal-road closure, never a ramp unlock.
    for(var r:all.values()){var link=metadata(r).link();if(link==null||!scope.contains(r.id()))continue;for(var ref:List.of(link.from(),link.to()))if(ref.road()!=null){var host=all.get(ref.road());if(host==null)throw new IllegalArgumentException("道路仍被匝道引用，请确认级联删除依赖匝道");if(metadata(host).points().stream().noneMatch(p->p.id().equals(ref.point())))throw new IllegalArgumentException("该自动尽头车道点正在使用中，不能正常续接此端；或手动点仍有依赖匝道");}}
    LaneCrossSections.reconcile(all,scope);
    Set<UUID> done=new HashSet<>(),visiting=new HashSet<>();for(var id:new ArrayList<>(all.keySet()))rebuild(data,id,all,done,visiting,scope);
    LaneCrossSections.reconcile(all,scope);
    for(var r:new ArrayList<>(all.values()))if(independent(r)&&scope.contains(r.id()))all.put(r.id(),automatic(r,ends));
    assignPriorities(all);
    // Refresh only openings owned by affected connectors; preserve unrelated road records
    // byte-for-byte, including any old geometry that this edit did not ask to repair.
    var connectors=new HashSet<UUID>();
    for(UUID id:scope){var now=all.get(id);var old=data.index.roads.get(id);
      if(now!=null&&metadata(now).link()!=null||old!=null&&metadata(old.record).link()!=null)connectors.add(id);
    }
    var before=records(data);var reverseHosts=new HashMap<UUID,Set<UUID>>();
    for(UUID id:connectors)for(var context:List.of(before,all)){
      var road=context.get(id);if(road==null||metadata(road).link()==null)continue;var link=metadata(road).link();
      var hosts=reverseHosts.computeIfAbsent(id,key->new HashSet<>());
      hosts.addAll(LaneRamps.contactRoads(context,link.from()));hosts.addAll(LaneRamps.contactRoads(context,link.to()));
    }
    Map<UUID,List<LanePoints.Opening>> openings=new HashMap<>();
    for(var road:all.values()){
      var oldHosts=reverseHosts.getOrDefault(road.id(),Set.of());
      var kept=new ArrayList<>(metadata(road).openings().stream()
          .filter(o->!connectors.contains(o.connection())&&!oldHosts.contains(o.connection())).toList());
      openings.put(road.id(),kept);
    }
    for(var road:all.values())if(connectors.contains(road.id())){
      var link=metadata(road).link();if(link==null)continue;
      var hosts=new LinkedHashSet<UUID>();hosts.addAll(LaneRamps.contactRoads(all,link.from()));hosts.addAll(LaneRamps.contactRoads(all,link.to()));
      for(UUID id:hosts){var host=all.get(id);if(host==null)continue;
        openings.computeIfAbsent(id,k->new ArrayList<>()).addAll(contactOpenings(road,host));
        openings.get(road.id()).addAll(contactOpenings(host,road));
      }
    }
    for(var road:new ArrayList<>(all.values()))if(independent(road))
      all.put(road.id(),road.withLanePoints(metadata(road).openings(openings.get(road.id()))));
    RampJunctions.reconcile(data,all,scope);
    // Do not replan existing unrelated ramps around new roads. Only check the newly
    // changed obstacle decks against saved geometry; pre-existing errors elsewhere are
    // not a reason to veto an unrelated create/edit/delete operation.
    var changedDecks=new HashSet<UUID>();
    for(var r:all.values()){var old=data.index.roads.get(r.id());if(old==null||!sameDeck(r,old.record))changedDecks.add(r.id());}
    if(!changedDecks.isEmpty())for(var r:all.values())if(metadata(r).link()!=null&&!changedDecks.contains(r.id())){
      boolean near=false;for(UUID changedId:changedDecks)if(!changedId.equals(r.id())&&RoadIndex.overlapXZ(r.mesh(),all.get(changedId).mesh(),0)){near=true;break;}
      if(near)try{LaneRamps.validateChanges(r.mesh(),all,r.id(),metadata(r).link(),changedDecks);}
      catch(IllegalArgumentException e){throw new IllegalArgumentException("本次道路修改影响既有匝道 "+r.id()+"："+e.getMessage(),e);}
    }
    for(var r:all.values()){var old=data.index.roads.get(r.id());boolean authored=built.stream().anyMatch(b->b.record.id().equals(r.id()));if(authored||old==null||!old.record.equals(r)){if(old!=null)removed.add(r.id());built.removeIf(b->b.record.id().equals(r.id()));built.add(new RoadIndex.Built(r.structures(List.of())));}}
    Set<UUID> finalIds=all.keySet();built.removeIf(b->!finalIds.contains(b.record.id()));
    for(var old:data.index.roads.values())if(!all.containsKey(old.record.id()))removed.add(old.record.id());
  }
  /** Store only contiguous physical contact runs. Never join disjoint runs with a
   * fictitious chord and never copy a whole kilometre-long ramp into every host header. */
  private static List<LanePoints.Opening> contactOpenings(RoadRecord path,RoadRecord surface){
    Mesh road=path.mesh(),host=surface.mesh();var samples=road.samples();boolean[] near=new boolean[samples.size()];
    double half=samples.stream().mapToDouble(Sample::halfWidth).max().orElse(path.settings().width()/2);
    for(int i=0;i<samples.size();i++){
      var s=samples.get(i);
      if(s.center().x()<host.min().x()-half-1||s.center().x()>host.max().x()+half+1||s.center().z()<host.min().z()-half-1||s.center().z()>host.max().z()+half+1)continue;
      var q=RoadQueries.horizontal(host,s.center());near[i]=q.horizontalDistance()<=q.sample().halfWidth()+s.halfWidth()+.6&&Math.abs(q.sample().center().y()-s.center().y())<2.2;
    }
    var result=new ArrayList<LanePoints.Opening>();var run=new ArrayList<V>();
    for(int i=0;i<samples.size();i++){
      boolean included=near[i]||i>0&&near[i-1]||i+1<near.length&&near[i+1];
      if(included)run.add(samples.get(i).center());
      if(!included||i==samples.size()-1){if(run.size()>1)result.add(new LanePoints.Opening(path.id(),run,Math.min(16,half)));run=new ArrayList<>();}
    }
    return result;
  }
  private static boolean adjacentBefore(RoadData data,RoadRecord candidate,RoadRecord old){var b=data.index.roads.get(candidate.id());var r=b==null?candidate:b.record;return r.a().equals(old.a())||r.a().equals(old.b())||r.b().equals(old.a())||r.b().equals(old.b());}
  private static boolean sameGeometry(RoadData data,Map<UUID,RoadRecord> all,LanePoints.Ref ref){if(ref.road()==null)return true;var old=data.index.roads.get(ref.road());var now=all.get(ref.road());return old!=null&&now!=null&&old.mesh.samples().equals(now.mesh().samples());}
  private static boolean samePort(RoadData data,Map<UUID,RoadRecord> all,LanePoints.Ref ref){if(ref.road()==null)return true;var old=data.index.roads.get(ref.road());var now=all.get(ref.road());if(old==null||now==null||old.record.settings().style()!=now.settings().style())return false;try{return LaneRamps.port(old.record,point(old.record,ref.point())).equals(LaneRamps.port(now,point(now,ref.point())));}catch(IllegalArgumentException e){return false;}}
  private static void rebuild(RoadData data,UUID id,Map<UUID,RoadRecord> all,Set<UUID> done,Set<UUID> visiting,Set<UUID> scope){if(done.contains(id)||!scope.contains(id))return;var r=all.get(id);if(r==null)throw new IllegalArgumentException("依赖道路已不存在："+id);var l=metadata(r).link();if(l==null){done.add(id);return;}if(!visiting.add(id))throw new IllegalArgumentException("匝道引用出现循环依赖");rebuild(data,l.from().road(),all,done,visiting,scope);if(l.to().road()!=null)rebuild(data,l.to().road(),all,done,visiting,scope);
    var existing=data.index.roads.get(id);
    if(existing!=null&&sameDeck(r,existing.record)&&Objects.equals(l,metadata(existing.record).link())
        &&samePort(data,all,l.from())&&samePort(data,all,l.to())
        &&(!l.options().sourceExtra()||sameGeometry(data,all,l.from()))
        &&(!(l.options().targetExtra()||l.targetOffset()!=0)||sameGeometry(data,all,l.to()))){visiting.remove(id);done.add(id);return;}
    if(existing==null&&!r.alignment().isEmpty()&&portsMatch(r,all)){LaneRamps.validate(r.mesh(),all,id,l);visiting.remove(id);done.add(id);return;}if(existing!=null&&portsMatch(r,all)&&Objects.equals(l,metadata(existing.record).link())&&samePort(data,all,l.from())&&samePort(data,all,l.to())&&(!l.options().sourceExtra()||sameGeometry(data,all,l.from()))&&(!(l.options().targetExtra()||l.targetOffset()!=0)||sameGeometry(data,all,l.to()))){if(!sameDeck(r,existing.record))LaneRamps.validate(r.mesh(),all,id,l);visiting.remove(id);done.add(id);return;}
    RoadRecord next;try{next=LaneRamps.generate(data,all,r.id(),r.owner(),l);}catch(IllegalArgumentException e){throw new IllegalArgumentException("本次修改需要重建关联匝道 "+id+"，但该连接无法成立："+e.getMessage(),e);}var moved=new ArrayList<LanePoints.Point>();for(var p:metadata(r).points())if(!p.automatic())moved.add(LanePoints.snap(next.mesh(),p));next=next.withLanePoints(metadata(next).points(moved));
    // Preserve attached points/paint and user road options on dependent connectors.
    next=next.withAttachments(r.settings().options().attachments()).furniturePhase(r.furniturePhase());var options=next.settings().options().infrastructure(r.settings().options().infrastructure()).laneLines(r.settings().options().laneLines()).hideArrows(r.settings().options().hideArrows());next=next.settings(next.settings().options(options));LaneRamps.validate(next.mesh(),all,id,metadata(next).link());all.put(id,next);visiting.remove(id);done.add(id);
  }
  static void assignPriorities(Map<UUID,RoadRecord> all){
    var depths=new HashMap<UUID,Integer>();var active=new HashSet<UUID>();
    for(UUID id:all.keySet())priorityDepth(id,all,depths,active);
    for(var r:new ArrayList<>(all.values()))all.put(r.id(),r.withLanePoints(metadata(r).priorityDepth(depths.get(r.id()))));
  }
  private static int priorityDepth(UUID id,Map<UUID,RoadRecord> all,Map<UUID,Integer> depths,Set<UUID> active){
    if(depths.containsKey(id))return depths.get(id);
    var r=all.get(id);if(r==null)return 0;
    if(!active.add(id)||active.size()>1024)throw new IllegalArgumentException("匝道依赖成环或超过 1024 层");
    var link=metadata(r).link();int depth=0;
    if(link!=null)depth=1+Math.max(priorityDepth(link.from().road(),all,depths,active),link.to().road()==null?0:priorityDepth(link.to().road(),all,depths,active));
    active.remove(id);depths.put(id,depth);return depth;
  }
  private LaneTopology(){}
}
