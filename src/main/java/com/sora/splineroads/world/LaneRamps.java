package com.sora.splineroads.world;
import com.sora.splineroads.core.*;
import com.sora.splineroads.core.RoadGeometry.*;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.*;
import net.minecraft.server.level.*;
import net.minecraft.world.item.ItemStack;

/** Server authoritative build, preview and dependency regeneration for lane-point ramps. */
public final class LaneRamps {
  /** Immutable records make identity caching safe within one preview/build computation.
   * Never retain a whole world or query grid after the request returns. */
  private static final ThreadLocal<Planning> CURRENT=new ThreadLocal<>();
  private static final class Planning implements AutoCloseable {
    final IdentityHashMap<RoadRecord,Mesh> meshes=new IdentityHashMap<>();
    final IdentityHashMap<Mesh,RoadClearance.Prepared> decks=new IdentityHashMap<>();
    final boolean root;
    Planning(RoadData data){root=CURRENT.get()==null;if(root){CURRENT.set(this);if(data!=null)for(var b:data.index.roads.values())meshes.put(b.record,b.mesh);}}
    public void close(){if(root)CURRENT.remove();}
  }
  private static Mesh mesh(RoadRecord road){var plan=CURRENT.get();return plan==null?road.mesh():plan.meshes.computeIfAbsent(road,RoadRecord::mesh);}
  private static List<RoadClearance.Contact> contacts(Mesh proposed,Mesh existing){
    var plan=CURRENT.get();if(plan==null)return RoadClearance.contacts(proposed,existing);
    if(plan.decks.size()>32)plan.decks.clear();
    return RoadClearance.contacts(proposed,plan.decks.computeIfAbsent(existing,RoadClearance::prepare));
  }
  public static RoadRecord host(Map<UUID,RoadRecord> all,LanePoints.Ref ref){var r=all.get(ref.road());if(r==null)throw new IllegalArgumentException("所选道路已不存在");if(r.assembly()!=null||r.junction()!=null||!LanePoints.supported(r.settings()))throw new IllegalArgumentException("连接器仅支持独立普通道路／高速／自由匝道的地面、自动高架、标准小河桥、梁式高架与跨线桥；不支持立交内部道路");LaneTopology.point(r,ref.point());return r;}
  public static LaneRampPaths.Port port(RoadRecord road,LanePoints.Point p){var mesh=mesh(road);var l=LanePoints.lane(mesh,p);var sample=RoadStructures.sample(mesh,l.station());var layout=RoadProfile.layout(mesh,sample);double lateral=l.position().sub(sample.center()).dot(sample.left());int side=layout.catalog().twoWay()?(lateral<layout.medianCenter()?-1:1):layout.outside();double edge=side<0?-layout.motorMin():layout.motorMax();double distance=Math.max(l.width(),edge-side*lateral+l.width()/2);double step=Math.min(.5,mesh.length()/10);var before=RoadStructures.sample(mesh,Math.max(0,l.station()-step));var after=RoadStructures.sample(mesh,Math.min(mesh.length(),l.station()+step));V delta=after.center().sub(before.center());double grade=delta.y()/Math.max(.001,delta.horizontalLength())*l.sign();return new LaneRampPaths.Port(l.position(),l.direction(),sample.left().mul(side),distance,grade);}
  public static LaneRampPaths.Port targetPort(RoadRecord road,LanePoints.Point point,double offset){
    var lane=LanePoints.lane(mesh(road),point);double station=lane.station()+lane.sign()*offset;
    if(station<-.00001||station>mesh(road).length()+.00001)throw new IllegalArgumentException("汇入口超出所选道路，不能换路或换车道");
    return port(road,LanePoints.point(point.id(),point.origin(),mesh(road),station,point.lane()));
  }
  public static double targetReach(LanePoints.Options options){return options.landing()==LanePoints.Landing.EXACT?0:Math.max(32,Math.min(128,options.radius()+options.transition()*2));}
  private record Generated(RoadRecord road,LanePoints.Path path) {}
  static RoadRecord generate(RoadData data,Map<UUID,RoadRecord> all,UUID id,UUID owner,LanePoints.Link link){
    return generateChoice(data,all,id,owner,link,null).road();
  }
  public static RoadRecord reconfigure(RoadRecord old,Settings requested,Map<UUID,RoadRecord> all){return reconfigure(null,old,requested,all);}
  private static RoadRecord reconfigure(RoadData data,RoadRecord old,Settings requested,Map<UUID,RoadRecord> all){
    if(RoadProfile.catalog(requested.style()).lanes()!=1||!LanePoints.supported(requested))
      throw new IllegalArgumentException("连接器匝道须保持独立匝道类型、单车道及支持的道路结构");
    return generateChoice(data,all,old.id(),old.owner(),LaneTopology.metadata(old).link(),requested).road();
  }
  private static Generated generateChoice(RoadData data,Map<UUID,RoadRecord> all,UUID id,UUID owner,LanePoints.Link link,Settings edited){
    try(var ignored=new Planning(data)){return generatePlanned(data,all,id,owner,link,edited);}
  }
  private static Generated generatePlanned(RoadData data,Map<UUID,RoadRecord> all,UUID id,UUID owner,LanePoints.Link link,Settings edited){
    link=link.withProtectedMerge().withRectangularClosure();
    var source=host(all,link.from());var p=LaneTopology.point(source,link.from().point());var lane=LanePoints.lane(mesh(source),p);
    double maxGrade=gradeLimit(all,link);
    var previous=all.get(id);var old=previous!=null&&LaneTopology.metadata(previous).link()!=null?previous:null;
    Style style=RoadProfile.highway(source.settings().style())?Style.C1_HIGHWAY_RAMP:Style.C1_RAMP;
    Settings base=edited!=null?edited:old!=null?old.settings():new Settings(Mode.CURVE,style,Math.max(4,lane.width()),source.settings().thickness(),.35,90).structure(Structure.AUTO).options(RoadProfile.Options.DEFAULT.traffic(source.settings().options().leftTraffic()).lanePoints(LanePoints.Data.EMPTY.link(link)));
    boolean migrating=old!=null&&!old.settings().style().connectorRamp();
    Style kind=(edited!=null?RoadProfile.highway(edited.style()):old!=null?RoadProfile.highway(old.settings().style()):RoadProfile.highway(style))?Style.C1_HIGHWAY_RAMP:Style.C1_RAMP;
    var options=base.options().lanePoints(base.options().lanePoints().link(link)).hideArrows(true);
    // New connectors and old AUTO defaults start without gantries. Explicit saved/edited
    // equipment choices survive; ordinary roads and automatic interchanges are untouched.
    if(old==null&&edited==null||migrating&&options.infrastructure().gantry()==RoadInfrastructure.Gantry.AUTO)
      options=options.infrastructure(options.infrastructure().gantry(RoadInfrastructure.Gantry.OFF));
    base=new Settings(base.mode(),kind,base.width(),base.thickness(),base.tension(),base.arcDegrees(),base.startWidth(),base.endWidth(),base.structure(),base.taperVersion(),base.rampTurn(),options);
    base.validate();
    var a=port(source,p);if(link.options().sourceExtra())a=approach(source,p,0,true,base,link.options().transition());
    var offsets=new LinkedHashSet<Double>();if(Math.abs(link.targetOffset())<=targetReach(link.options()))offsets.add(link.targetOffset());offsets.add(0d);
    if(link.to().road()!=null&&link.options().landing()!=LanePoints.Landing.EXACT){double reach=targetReach(link.options());for(double step=8;step<reach;step+=8){offsets.add(step);offsets.add(-step);}offsets.add(reach);offsets.add(-reach);}
    var errors=new EnumMap<LanePoints.Path,String>(LanePoints.Path.class);String error="所选范围内没有可用汇入口";LaneRampPaths.Port target=null;
    for(double offset:offsets)try{
      var actual=link.targetOffset(offset);
      var context=LaneCrossSections.staged(all,id,actual);
      var currentSource=host(context,actual.from());
      if(!LaneSections.active(mesh(currentSource),lane.station(),p.lane()))throw new IllegalArgumentException("汇出车道已在此位置分离，不能从车道空位再次汇出");
      LaneRampPaths.Port b;double targetLaneWidth=lane.width();
      if(link.to().road()!=null){var road=host(context,link.to());var point=LaneTopology.point(road,link.to().point());var selectedLane=LanePoints.lane(mesh(road),point);
        if((link.options().arrival()==LanePoints.Arrival.MERGE||link.options().arrival()==LanePoints.Arrival.FLOW)&&!LaneSections.active(mesh(road),selectedLane.station()+selectedLane.sign()*offset,point.lane()))throw new IllegalArgumentException("B 位于已分离的车道空位，请选择补入车道空位而非普通并线");targetLaneWidth=LanePoints.lane(mesh(road),selectedLane.station()+selectedLane.sign()*offset,point.lane()).width();
        b=link.options().targetExtra()?approach(road,point,offset,false,base,link.options().transition()):targetPort(road,point,offset);
      }else{
        if(link.options().targetExtra())throw new IllegalArgumentException("路口中心接入口不设置沿主路的额外汇入车道");
        V direction=data!=null?RampJunctions.spec(data,link.to().junction()).center().sub(link.junctionMouth()).horizontalUnit():old!=null?old.end().direction():null;
        if(direction==null)throw new IllegalArgumentException("缺少路口接入方向");
        b=new LaneRampPaths.Port(link.junctionMouth(),direction,direction.left(),lane.width(),0);
      }
      target=b;
      var md=(old==null?LanePoints.Data.EMPTY:LaneTopology.metadata(old)).link(actual).openings(List.of());
      var settings=base.options(base.options().lanePoints(md));
      for(var candidate:routeCandidates(a,b,settings,link.options(),source,p,link.to().road()==null?null:host(context,link.to()),link.to().road()==null?null:LaneTopology.point(host(context,link.to()),link.to().point()),offset,maxGrade))try{
        var baseMesh=fitHostContacts(LaneRampAlignment.fit(candidate.mesh(),lane.width(),targetLaneWidth,link.options().transition()),context,actual);
        for(Mesh mesh:heightCandidates(baseMesh,context,id,actual,errors,candidate.path()))try{
          if(data!=null&&!data.withinHeight(mesh))throw new IllegalArgumentException("上跨／下穿超出世界高度范围");
          var finalContext=actual.options().departure()==LanePoints.Departure.TEMPORARY||actual.closesTarget()?
              LaneCrossSections.staged(all,id,actual,mesh):context;
          validate(mesh,finalContext,id,actual);
          if(actual.options().departure()==LanePoints.Departure.TEMPORARY)
            LaneReopening.validateRestored(host(finalContext,actual.from()).mesh(),p.lane(),mesh,id);
          var start=RoadRibbon.start(mesh);var end=RoadRibbon.end(mesh);
          var record=new RoadRecord(id,owner,RampJunctions.at(start.position()),RampJunctions.at(end.position()),start,end,settings,false,4).alignment(null,mesh);
          if(old!=null)record=record.furniturePhase(old.furniturePhase());
          return new Generated(record,candidate.path());
        }catch(IllegalArgumentException e){errors.put(candidate.path(),e.getMessage());error=e.getMessage();}
      }catch(IllegalArgumentException e){errors.put(candidate.path(),e.getMessage());error=e.getMessage();}
    }catch(IllegalArgumentException e){if(offset==link.targetOffset())error=e.getMessage();}
    if(!errors.isEmpty()&&target!=null)error=LaneRampPaths.failure(a,target,link.options(),errors);
    throw new IllegalArgumentException(error+(link.to().road()!=null?"；已检查 B 点前后 "+(int)targetReach(link.options())+" 格的同车道范围":""));
  }
  /** Try a same-slot lead before a lateral turn, giving an inner lane enough room
   * to rise/drop BEFORE crossing its neighbours. These are automatic candidates,
   * not manually editable control points and not a clearance exemption. */
  private static List<LaneRampPaths.Candidate> routeCandidates(LaneRampPaths.Port a,LaneRampPaths.Port b,Settings settings,LanePoints.Options options,RoadRecord source,LanePoints.Point point,RoadRecord target,LanePoints.Point targetPoint,double targetOffset,double maxGrade){
    var result=new ArrayList<LaneRampPaths.Candidate>();String error="无法建立匝道候选";
    try{result.addAll(LaneRampPaths.candidates(a,b,settings,options,maxGrade));}catch(IllegalArgumentException e){error=e.getMessage();}
    if(options.departure()==LanePoints.Departure.TEMPORARY){
      var raw=source.rawMesh();var lane=LanePoints.lane(raw,point);
      for(double length:new double[]{64,96,128}){
        double end=lane.station()+lane.sign()*length;if(end<=0||end>=raw.length())continue;
        var last=LanePoints.lane(raw,end,point.lane());var lead=new ArrayList<Sample>();
        for(double d=0;d<=length;d+=.5){var q=LanePoints.lane(raw,lane.station()+lane.sign()*d,point.lane());lead.add(new Sample(q.position(),q.direction().left(),d,settings.width()/2));}
        var before=lead.get(lead.size()-2).center();double grade=(last.position().y()-before.y())/Math.max(.001,last.position().sub(before).horizontalLength());
        var next=new LaneRampPaths.Port(last.position(),last.direction(),a.outside(),a.extraWidth(),grade);
        try{for(var c:LaneRampPaths.candidates(next,b,settings,options,maxGrade)){
          var samples=new ArrayList<>(lead);samples.addAll(c.mesh().samples().subList(1,c.mesh().samples().size()));
          try{var mesh=RoadRibbon.mesh(samples,settings);RoadRibbon.checkSelfIntersections(mesh,4);result.add(new LaneRampPaths.Candidate(c.path(),mesh));}catch(IllegalArgumentException ignored){}
        }}catch(IllegalArgumentException ignored){}
      }
    }
    if(target!=null&&options.arrival()==LanePoints.Arrival.MERGE){
      var raw=target.rawMesh();var lane=LanePoints.lane(raw,targetPoint);double end=lane.station()+lane.sign()*targetOffset;
      for(double length:new double[]{64,96,128}){
        double start=end-lane.sign()*length;if(start<=0||start>=raw.length())continue;
        var q=LanePoints.lane(raw,start,targetPoint.lane());var before=LanePoints.lane(raw,start-lane.sign()*.5,targetPoint.lane());
        double grade=(q.position().y()-before.position().y())/Math.max(.001,q.position().sub(before.position()).horizontalLength());
        var port=new LaneRampPaths.Port(q.position(),q.direction(),b.outside(),b.extraWidth(),grade);
        // No tail recursion: target null below. This still includes the source's same-slot lead.
        try{for(var c:routeCandidates(a,port,settings,options,source,point,null,null,0,maxGrade)){
          var samples=new ArrayList<>(c.mesh().samples());
          for(double d=.5;d<=length+.001;d+=.5){var at=LanePoints.lane(raw,start+lane.sign()*d,targetPoint.lane());samples.add(new Sample(at.position(),at.direction().left(),d,settings.width()/2));}
          try{var m=RoadRibbon.mesh(samples,settings);RoadRibbon.checkSelfIntersections(m,4);result.add(new LaneRampPaths.Candidate(c.path(),m));}catch(IllegalArgumentException ignored){}
        }}catch(IllegalArgumentException ignored){}
      }
    }
    if(result.isEmpty())throw new IllegalArgumentException(error);return result;
  }
  private static LaneRampPaths.Port approach(RoadRecord road,LanePoints.Point point,double offset,boolean source,Settings settings,double transition){
    var selected=LanePoints.lane(mesh(road),point);double station=selected.station()+selected.sign()*offset;
    var port=targetPort(road,point,offset);var samples=LaneRampApproach.build(mesh(road),point.lane(),station,source,settings,transition);
    return new LaneRampPaths.Port(port.position(),port.direction(),port.outside(),port.extraWidth(),port.grade(),samples);
  }
  static Set<UUID> contactRoads(Map<UUID,RoadRecord> all,LanePoints.Ref ref){Set<UUID> result=new LinkedHashSet<>();if(ref.road()==null)return result;result.add(ref.road());boolean added;do{added=false;for(UUID id:new ArrayList<>(result)){var a=all.get(id);if(a==null||!RoadContinuity.eligible(mesh(a)))continue;for(var b:all.values()){if(result.contains(b.id())||b.assembly()!=null||b.junction()!=null||LaneTopology.metadata(b).link()!=null)continue;for(BlockPos node:List.of(a.a(),a.b()))if(b.a().equals(node)||b.b().equals(node)){long degree=all.values().stream().filter(v->LaneTopology.metadata(v).link()==null&&(v.a().equals(node)||v.b().equals(node))).count();if(degree==2&&RoadContinuity.compatible(mesh(a),mesh(b),a.a().equals(node)?a.start().position():a.end().position()))added|=result.add(b.id());}}}if(result.size()>256)throw new IllegalArgumentException("连续接头范围过大");}while(added);return result;}
  private static int contactEnd(Mesh mesh,Map<UUID,RoadRecord> all,Set<UUID> hosts,boolean first){
    int n=mesh.samples().size(),i=first?0:n-1;
    var roads=hosts.stream().map(all::get).filter(Objects::nonNull).map(LaneRamps::mesh).toList();
    while(i>=0&&i<n){var sample=mesh.samples().get(i);boolean touching=false;
      for(var host:roads){var q=RoadQueries.horizontal(host,sample.center());
        if(q.horizontalDistance()<q.sample().halfWidth()+sample.halfWidth()+.15){touching=true;break;}}
      if(!touching)break;i+=first?1:-1;
    }
    return Math.max(0,Math.min(n-1,i+(first?2:-2)));
  }
  /** A declared joining throat follows its existing host elevation before it becomes free.
   * Only the contiguous first/last contact is eligible; later crossings are still obstacles. */
  private static Mesh fitHostContacts(Mesh mesh,Map<UUID,RoadRecord> all,LanePoints.Link link){
    // New lane connectors already have exact host-sampled auxiliary tapers and port
    // elevations. A whole-host XZ contact is NOT a joining throat: fitting it to the
    // host erases an overpass, creates a sag after docking and exempts a transverse
    // crossing of the opposite carriageway. Preserve legacy saved links only.
    if(link.protectedMerge())return mesh;
    var sourceIds=contactRoads(all,link.from());var targetIds=contactRoads(all,link.to());
    var source=sourceIds.stream().map(all::get).filter(Objects::nonNull).map(LaneRamps::mesh).toList();
    var target=targetIds.stream().map(all::get).filter(Objects::nonNull).map(LaneRamps::mesh).toList();
    double from=link.options().separatesLane()?0:mesh.samples().get(contactEnd(mesh,all,sourceIds,true)).distance();
    double to=link.closesTarget()?mesh.length():mesh.samples().get(contactEnd(mesh,all,targetIds,false)).distance();
    if(from>=to)return mesh;
    double ease=Math.min(200,Math.max(2,(to-from)/3));var samples=new ArrayList<Sample>();
    for(var sample:mesh.samples()){
      double ws=source.isEmpty()||link.options().separatesLane()?0:1-Settings.smooth(Math.max(0,Math.min(1,(sample.distance()-from)/ease)));
      double wt=target.isEmpty()||link.closesTarget()?0:1-Settings.smooth(Math.max(0,Math.min(1,(to-sample.distance())/ease)));
      double dy=((ws>0?ws*hostHeightDelta(sample,source):0)+(wt>0?wt*hostHeightDelta(sample,target):0))/Math.max(1,ws+wt);
      samples.add(new Sample(sample.center().add(new V(0,dy,0)),sample.left(),sample.distance(),sample.halfWidth()));
    }
    return RoadRibbon.mesh(samples,mesh.settings());
  }
  private static double hostHeightDelta(Sample sample,List<Mesh> hosts){
    double distance=Double.POSITIVE_INFINITY,delta=0;
    for(var host:hosts){var q=RoadQueries.horizontal(host,sample.center());if(q.horizontalDistance()<distance){distance=q.horizontalDistance();delta=q.sample().center().y()-sample.center().y();}}
    return delta;
  }
  private record Obstacle(UUID road,RoadClearance.Contact contact){}
  private static List<Obstacle> crossings(Mesh mesh,Map<UUID,RoadRecord> all,UUID id,LanePoints.Link link){return crossings(mesh,all,id,link,null);}
  private static List<Obstacle> crossings(Mesh mesh,Map<UUID,RoadRecord> all,UUID id,LanePoints.Link link,Set<UUID> changed){
    var out=new ArrayList<Obstacle>();var sourceHosts=contactRoads(all,link.from());var targetHosts=contactRoads(all,link.to());
    double sourceLimit=mesh.samples().get(contactEnd(mesh,all,sourceHosts,true)).distance();
    double targetLimit=mesh.samples().get(contactEnd(mesh,all,targetHosts,false)).distance();
    for(var road:all.values()){
      if(changed!=null&&!changed.contains(road.id()))continue;
      if(road.id().equals(id)||road.junction()!=null&&Objects.equals(road.assembly(),link.to().junction()))continue;
      var other=LaneTopology.metadata(road).link();
      // A dependent branch is regenerated after this parent. Its own validator then checks the
      // new parent outside its local throat; unrelated siblings are NOT globally exempted.
      if(other!=null&&(id.equals(other.from().road())||id.equals(other.to().road())))continue;
      Mesh old=mesh(road);if(!RoadIndex.overlapXZ(mesh,old,0))continue;
      double sharedStart=-1,sharedEnd=mesh.length()+1;
      if(other!=null&&link.from().equals(other.from()))sharedStart=mesh.samples().get(contactEnd(mesh,Map.of(road.id(),road),Set.of(road.id()),true)).distance();
      if(other!=null&&link.to().equals(other.to()))sharedEnd=mesh.samples().get(contactEnd(mesh,Map.of(road.id(),road),Set.of(road.id()),false)).distance();
      if(sharedStart>mesh.length()*.9||sharedEnd<mesh.length()*.1)throw new IllegalArgumentException("同一车道点的两条匝道几乎全程重合，请使用不同汇入方向");
      var exactSlots=new LinkedHashSet<Integer>();
      if(link.protectedMerge()&&road.id().equals(link.from().road()))exactSlots.add(LaneTopology.point(road,link.from().point()).lane());
      if(link.protectedMerge()&&road.id().equals(link.to().road()))exactSlots.add(LaneTopology.point(road,link.to().point()).lane());
      if(!exactSlots.isEmpty()){
        Mesh protectedDeck=LaneDeck.motorOnly(old);for(int slot:exactSlots)protectedDeck=LaneDeck.excludingSlot(protectedDeck,slot);
        for(var c:contacts(mesh,protectedDeck))out.add(new Obstacle(road.id(),c));
      }
      for(var c:contacts(mesh,old)){
        boolean sourceJoin=sourceHosts.contains(road.id())&&c.to()<=sourceLimit+.01;
        if(sourceJoin&&link.options().separatesLane())sourceJoin=separationThroat(c,road,link);
        if(sourceJoin||targetHosts.contains(road.id())&&c.from()>=targetLimit-.01)continue;
        if(c.to()<=sharedStart+.01||c.from()>=sharedEnd-.01)continue;
        out.add(new Obstacle(road.id(),c));
      }
    }return out;
  }
  /** Only the selected slot joins; opposite traffic is not exempt merely because it has the same host ID. */
  private static boolean separationThroat(RoadClearance.Contact contact,RoadRecord road,LanePoints.Link link){
    if(!road.id().equals(link.from().road()))return false;
    var raw=road.rawMesh();var point=LaneTopology.point(road,link.from().point());var start=LanePoints.lane(raw,point);
    var q=RoadQueries.horizontal(raw,contact.ours());var lane=LanePoints.lane(raw,q.sample().distance(),point.lane());
    return start.sign()*(lane.station()-start.station())>=-.25&&
        Math.abs(contact.ours().sub(lane.position()).dot(q.sample().left()))<=lane.width()/2+.55;
  }
  private static List<Mesh> heightCandidates(Mesh base,Map<UUID,RoadRecord> all,UUID id,LanePoints.Link link,Map<LanePoints.Path,String> errors,LanePoints.Path path){
    var mode=link.options().elevation();var out=new ArrayList<Mesh>();
    List<Obstacle> contacts=crossings(base,all,id,link);
    boolean clear=contacts.stream().noneMatch(c->c.contact().blocked()||existingLayerConflict(c,all));
    if(mode==LanePoints.Elevation.KEEP)return List.of(base);
    if(mode==LanePoints.Elevation.AUTO&&clear)return List.of(base);
    if(contacts.isEmpty())return List.of(base);
    double from=link.protectedMerge()?fixedApproach(base,all,link,true):link.options().separatesLane()?0:base.samples().get(contactEnd(base,all,contactRoads(all,link.from()),true)).distance();
    double to=link.protectedMerge()?base.length()-fixedApproach(base,all,link,false):link.closesTarget()?base.length():base.samples().get(contactEnd(base,all,contactRoads(all,link.to()),false)).distance();
    for(boolean over:mode==LanePoints.Elevation.UNDER?new boolean[]{false}:mode==LanePoints.Elevation.OVER?new boolean[]{true}:new boolean[]{true,false}){
      // A new AUTO ramp may not invalidate a saved explicit OVER/UNDER crossing.
      if(contacts.stream().anyMatch(o->{var r=all.get(o.road());var l=r==null?null:LaneTopology.metadata(r).link();return l!=null&&(over&&l.options().elevation()==LanePoints.Elevation.OVER||!over&&l.options().elevation()==LanePoints.Elevation.UNDER);}))continue;
      var constraints=new ArrayList<LaneRampHeights.Constraint>();
      for(var obstacle:contacts){var c=obstacle.contact();double amount=over?c.raise():c.lower();if(!c.blocked()&&(over?c.ours().y()>c.other().y():c.ours().y()<c.other().y()))amount=0;constraints.add(new LaneRampHeights.Constraint(c.from(),c.to(),amount));}
      try{out.add(LaneRampHeights.solve(base,from,to,constraints,over,gradeLimit(all,link)));}
      catch(IllegalArgumentException e){errors.put(path,e.getMessage());}
    }
    if(out.isEmpty()&&!errors.containsKey(path))errors.put(path,"没有满足端点、坡度及净空的自动跨越方案");
    if(mode==LanePoints.Elevation.AUTO)out.sort(Comparator.comparingDouble(LaneRamps::verticalEffort));
    return out;
  }
  private static double verticalEffort(Mesh m){double total=0;for(int i=1;i<m.samples().size();i++)total+=Math.abs(m.samples().get(i).center().y()-m.samples().get(i-1).center().y());return total;}
  private static boolean existingLayerConflict(Obstacle o,Map<UUID,RoadRecord> all){
    var r=all.get(o.road());var link=r==null?null:LaneTopology.metadata(r).link();if(link==null)return false;
    var mode=link.options().elevation();return mode==LanePoints.Elevation.OVER&&o.contact().ours().y()>o.contact().other().y()||mode==LanePoints.Elevation.UNDER&&o.contact().ours().y()<o.contact().other().y();
  }
  private static double fixedApproach(Mesh ramp,Map<UUID,RoadRecord> all,LanePoints.Link link,boolean source){
    if(source?!link.options().sourceExtra():!link.options().targetExtra())return 0;
    var ref=source?link.from():link.to();if(ref.road()==null)return 0;
    var host=host(all,ref);var point=LaneTopology.point(host,ref.point());
    var lane=LanePoints.lane(mesh(host),point);
    var samples=LaneRampApproach.build(mesh(host),point.lane(),lane.station()+(source?0:lane.sign()*link.targetOffset()),source,ramp.settings(),link.options().transition());
    double span=0;for(int i=1;i<samples.size();i++)span+=samples.get(i).center().distance(samples.get(i-1).center());
    return Math.min(ramp.length(),span);
  }
  /** Validate only new/changed obstacle decks against an unchanged saved connector. */
  static void validateChanges(Mesh mesh,Map<UUID,RoadRecord> all,UUID id,LanePoints.Link link,Set<UUID> changed){
    for(var obstacle:crossings(mesh,all,id,link,changed)){
      var c=obstacle.contact();var mode=link.options().elevation();
      if(c.blocked()||mode==LanePoints.Elevation.OVER&&c.ours().y()<c.other().y()||mode==LanePoints.Elevation.UNDER&&c.ours().y()>c.other().y())
        throw new IllegalArgumentException("与本次修改道路 "+obstacle.road()+" 的净空或上下关系冲突");
    }
  }
  static void validate(Mesh mesh,Map<UUID,RoadRecord> all,UUID id,LanePoints.Link link){
    RoadRibbon.checkSelfIntersections(mesh,4);LaneRampPaths.checkVolume(mesh);
    LaneRampGrade.validate(mesh,gradeLimit(all,link));
    for(var obstacle:crossings(mesh,all,id,link)){
      var c=obstacle.contact();var mode=link.options().elevation();
      boolean wrongLayer=mode==LanePoints.Elevation.OVER&&c.ours().y()<c.other().y()||mode==LanePoints.Elevation.UNDER&&c.ours().y()>c.other().y();
      if(c.blocked()||wrongLayer)throw new IllegalArgumentException("与道路 "+obstacle.road()+" 冲突："+(wrongLayer?"不符合指定的上下关系；":"")+new RoadClearance.Conflict(c).getMessage());
    }
  }
  /** Classify from real source/target hosts, never from the edited ramp skin. An
   * ordinary-looking connector chained from a highway cannot raise its limit to 25%.
   * Junction arms come from their actual saved spec, not an invented client flag. */
  static double gradeLimit(Map<UUID,RoadRecord> all,LanePoints.Link link){
    var roads=new ArrayDeque<UUID>();var junctions=new LinkedHashSet<UUID>();
    for(var ref:List.of(link.from(),link.to())){if(ref.road()!=null)roads.add(ref.road());else junctions.add(ref.junction());}
    var seen=new HashSet<UUID>();boolean highway=false;
    while(!roads.isEmpty()){
      UUID id=roads.removeFirst();if(!seen.add(id))continue;
      var road=all.get(id);if(road==null)throw new IllegalArgumentException("坡比判定的关联道路已不存在，请重新选择");
      highway|=RoadProfile.highway(road.settings().style());
      var parent=LaneTopology.metadata(road).link();if(parent!=null)for(var ref:List.of(parent.from(),parent.to())){
        if(ref.road()!=null)roads.add(ref.road());else junctions.add(ref.junction());
      }
    }
    for(UUID junction:junctions){boolean found=false;
      for(var road:all.values())if(junction.equals(road.assembly())&&road.junction()!=null){
        found=true;for(var arm:road.junction().spec().arms())highway|=RoadProfile.highway(arm.external().style());
      }
      // Missing classification must not silently grant the more permissive ordinary cap.
      if(!found)highway=true;
    }
    return LaneRampGrade.limit(highway,link.options().gradeOverride());
  }
  private static boolean contact(Sample s,Mesh host,double limit,V end){var q=RoadQueries.horizontal(host,s.center());return q.horizontalDistance()<q.sample().halfWidth()+s.halfWidth()+1&&Math.abs(q.sample().center().y()-s.center().y())<.18;}
  private static int signature(RoadData data,LanePoints.Ref ref){if(ref.junction()!=null)return data.junctions.getOrDefault(ref.junction(),new CompoundTag()).hashCode();var b=data.index.roads.get(ref.road());if(b==null)throw new IllegalArgumentException("道路已不存在");return b.record.header().hashCode();}
  private static void selection(ServerPlayer p,ItemStack tool,CompoundTag t){
    if(t.hasUUID("Id")){
      var built=RoadData.get(p.serverLevel()).index.roads.get(t.getUUID("Id"));
      if(built==null||LaneTopology.metadata(built.record).link()==null||built.record.assembly()!=null)throw new IllegalArgumentException("所选独立匝道已不存在");
      RoadData.requireOwner(p,built.record.owner());
      if(t.getInt("Signature")!=built.record.header().hashCode())throw new IllegalArgumentException("匝道已改变，请重新打开");
      var link=LaneTopology.metadata(built.record).link();
      if(!LanePointCodec.ref(link.from()).equals(t.getCompound("From"))||!LanePointCodec.ref(link.to()).equals(t.getCompound("To")))throw new IllegalArgumentException("不能在编辑时替换车道点引用");
      return;
    }
    var state=tool.getOrCreateTag();
    if(!state.contains("LaneFrom")||!state.contains("LaneTo")||!state.getCompound("LaneFrom").equals(t.getCompound("From"))||!state.getCompound("LaneTo").equals(t.getCompound("To"))||!p.level().dimension().location().toString().equals(state.getString("LaneDimension")))throw new IllegalArgumentException("匝道选择已失效，请先选汇出点，再选汇入点");
  }
  public static CompoundTag payload(RoadRecord road,boolean roadEditor){
    var link=LaneTopology.metadata(road).link();if(link==null)throw new IllegalArgumentException("请选择连接器生成的独立匝道");
    var t=new CompoundTag();t.putString("Kind",roadEditor?"laneRampRoad":"laneRamp");t.putUUID("Id",road.id());t.putInt("Signature",road.header().hashCode());
    t.put("From",LanePointCodec.ref(link.from()));t.put("To",LanePointCodec.ref(link.to()));t.put("Options",LanePointCodec.options(link.options()));t.put("Road",road.header());
    t.put("Settings",RoadRecord.writeSettings(road.settings()));t.putLong("A",road.a().asLong());t.putLong("B",road.b().asLong());t.put("StartNode",RoadRecord.writeNode(road.start()));t.put("EndNode",RoadRecord.writeNode(road.end()));
    return t;
  }
  public static CompoundTag preview(ServerPlayer player,ItemStack tool,CompoundTag t){
    selection(player,tool,t);var data=RoadData.get(player.serverLevel());var from=LanePointCodec.ref(t.getCompound("From"));var to=LanePointCodec.ref(t.getCompound("To"));var options=LanePointCodec.options(t.getCompound("Options"));var all=LaneTopology.records(data);var source=host(all,from);RoadData.requireOwner(player,source.owner());
    if(to.road()!=null)RoadData.requireOwner(player,host(all,to).owner());else RoadData.requireOwner(player,data.junctions.getOrDefault(to.junction(),new CompoundTag()).getUUID("Owner"));
    V mouth=to.junction()==null?null:RampJunctions.mouth(data,to.junction(),LaneTopology.point(source,from.point()).position(),new Settings(Mode.STRAIGHT,Style.O1_ONE,Math.max(4,LanePoints.lane(mesh(source),LaneTopology.point(source,from.point())).width()+1),1,.35,90));
    UUID id=t.hasUUID("Id")?t.getUUID("Id"):UUID.randomUUID();double previousOffset=t.hasUUID("Id")?LaneTopology.metadata(all.get(id)).link().targetOffset():0;
    var link=new LanePoints.Link(from,to,options,mouth,previousOffset);var generated=generateChoice(data,all,id,t.hasUUID("Id")?all.get(id).owner():player.getUUID(),link,null);RoadRecord r=generated.road();
    var planned=new ArrayList<RoadIndex.Built>();planned.add(new RoadIndex.Built(r));
    var removed=new HashSet<UUID>();if(all.containsKey(id))removed.add(id);
    var request=assemblyRequest(data,r);
    planned=new ArrayList<>(data.previewAssembly(player.serverLevel(),player,planned,removed,request.endpoints(),request.moves()));
    var staging=new LinkedHashMap<>(all);removed.forEach(staging::remove);for(var built:planned)staging.put(built.record.id(),built.record);r=staging.get(id);
    var checked=new CompoundTag();checked.put("Road",r.header());checked.putLong("WorldRevision",data.index.revision());checked.putInt("FromSignature",signature(data,from));checked.putInt("ToSignature",signature(data,to));checked.putUUID("Token",UUID.randomUUID());
    if(t.hasUUID("Id")){checked.putUUID("Id",id);checked.putInt("Signature",t.getInt("Signature"));}
    tool.getOrCreateTag().put("LanePreview",checked);
    var reply=new CompoundTag();reply.putString("Kind","laneRampCheck");reply.putString("ResolvedPath",generated.path().name());reply.putLong("Request",t.getLong("Request"));reply.put("Road",r.header());reply.putUUID("Token",checked.getUUID("Token"));reply.putDouble("TargetOffset",LaneTopology.metadata(r).link().targetOffset());reply.putDouble("GradeLimit",gradeLimit(staging,LaneTopology.metadata(r).link()));
    var finalLink=LaneTopology.metadata(r).link();var finalMesh=r.mesh();
    var metrics=LaneRampGrade.report(finalMesh,fixedApproach(finalMesh,staging,finalLink,true),
        finalMesh.length()-fixedApproach(finalMesh,staging,finalLink,false),gradeLimit(staging,finalLink));
    reply.putDouble("GradeHorizontal",metrics.horizontal());reply.putDouble("GradeAvailable",metrics.available());
    reply.putDouble("GradeMinimum",metrics.minimum());reply.putDouble("ActualGrade",metrics.maximum());
    var changed=new ListTag();for(var next:staging.values())if(next.id().equals(id)||all.containsKey(next.id())&&!next.equals(all.get(next.id())))changed.add(next.header());reply.put("ChangedRoads",changed);
    if(options.departure()==LanePoints.Departure.TEMPORARY)for(var cut:LaneTopology.metadata(staging.get(from.road())).cuts())if(cut.connection().equals(id)){
      double length=cut.sign()*(cut.end()-cut.begin());
      reply.putBoolean("TemporaryClosure",true);reply.putBoolean("RectangularClosure",cut.rectangular());
      reply.putDouble("ReopenAfter",cut.rectangular()?length:length-cut.transition());reply.putDouble("RestoredAfter",length);break;
    }
    if(LaneTopology.metadata(r).link().closesTarget())for(var cut:LaneTopology.metadata(staging.get(to.road())).cuts())if(cut.connection().equals(id)&&cut.arrival()){
      reply.putBoolean("TargetClosure",true);reply.putDouble("TargetClosedBefore",cut.sign()*(cut.end()-Math.max(0,Math.min(staging.get(to.road()).rawMesh().length(),cut.begin()))));break;
    }
    return reply;
  }
  public static String build(ServerPlayer player,ItemStack tool,CompoundTag t){
    selection(player,tool,t);var checked=tool.getOrCreateTag().getCompound("LanePreview");
    if(!checked.hasUUID("Token")||!t.hasUUID("Token")||!checked.getUUID("Token").equals(t.getUUID("Token"))||checked.hasUUID("Id")!=t.hasUUID("Id")||t.hasUUID("Id")&&!checked.getUUID("Id").equals(t.getUUID("Id")))throw new IllegalArgumentException("请先检查当前设置的预览");
    var r=RoadRecord.load(checked.getCompound("Road"));var link=LaneTopology.metadata(r).link();var data=RoadData.get(player.serverLevel());
    if(checked.getLong("WorldRevision")!=data.index.revision())throw new IllegalArgumentException("道路几何已在预览后改变，请重新预览完整接头");
    if(t.hasUUID("Id")&&checked.getInt("Signature")!=t.getInt("Signature")||!LanePointCodec.options(link.options()).equals(t.getCompound("Options"))||checked.getInt("FromSignature")!=signature(data,link.from())||checked.getInt("ToSignature")!=signature(data,link.to()))throw new IllegalArgumentException("道路或选项已变化，请重新预览");
    build(data,player.serverLevel(),player,r);LaneRampTool.clearSelection(tool);return t.hasUUID("Id")?"匝道已更新，身份与依赖引用保留":"匝道已建成，车道点引用生效";
  }
  public static String editRoad(ServerLevel level,ServerPlayer player,CompoundTag t){
    var data=RoadData.get(level);var b=data.index.roads.get(t.getUUID("Id"));
    if(b==null||LaneTopology.metadata(b.record).link()==null)throw new IllegalArgumentException("所选匝道已不存在");
    if(player!=null)RoadData.requireOwner(player,b.record.owner());
    if(t.getInt("Signature")!=b.record.header().hashCode())throw new IllegalArgumentException("道路已改变，请重新打开编辑器");
    var next=reconfigure(data,b.record,RoadRecord.readSettings(t.getCompound("Settings")),LaneTopology.records(data));
    build(data,level,player,next);return "匝道路面与附属设置已保存，车道点引用保留";
  }
  private record AssemblyRequest(Set<BlockPos> endpoints,List<RoadData.NodeMove> moves){}
  private static AssemblyRequest assemblyRequest(RoadData data,RoadRecord r){
    var link=LaneTopology.metadata(r).link();if(link==null)throw new IllegalArgumentException("缺少真实车道点引用");
    var moves=new ArrayList<RoadData.NodeMove>();if(link.to().junction()!=null){var n=new Node(link.junctionMouth(),RoadPlanner.yaw(RampJunctions.spec(data,link.to().junction()).center().sub(link.junctionMouth()).horizontalUnit()),0);var snapshot=new CompoundTag();snapshot.putUUID("Owner",r.owner());snapshot.put("Node",RoadRecord.writeNode(n));snapshot.putBoolean("HeightExplicit",true);moves.add(new RoadData.NodeMove(null,RampJunctions.at(n.position()),n,snapshot));}
    // Contact may contain an existing host marker. On first construction that host is
    // rebuilt for its new opening; subsequent edits may leave it unchanged. Its own
    // markers are still legitimate contacts, never unrelated obstacles or deletion targets.
    var endpoints=new HashSet<BlockPos>();endpoints.add(r.a());endpoints.add(r.b());var all=LaneTopology.records(data);
    for(var ref:List.of(link.from(),link.to()))for(UUID host:contactRoads(all,ref)){var road=all.get(host);endpoints.add(road.a());endpoints.add(road.b());}
    return new AssemblyRequest(Set.copyOf(endpoints),List.copyOf(moves));
  }
  public static void build(RoadData data,ServerLevel level,ServerPlayer player,RoadRecord r){
    var request=assemblyRequest(data,r);
    data.replaceAssembly(level,player,List.of(new RoadIndex.Built(r)),data.index.roads.containsKey(r.id())?Set.of(r.id()):Set.of(),request.endpoints(),request.moves());RampJunctions.sync(data);data.setDirty();
  }
  private LaneRamps(){}
}
