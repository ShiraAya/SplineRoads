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
    final IdentityHashMap<Mesh,Map<List<Integer>,Mesh>> protectedDecks=new IdentityHashMap<>();
    final IdentityHashMap<Map<UUID,RoadRecord>,Map<LanePoints.Ref,Set<UUID>>> connected=new IdentityHashMap<>();
    final IdentityHashMap<Map<UUID,RoadRecord>,Map<LanePoints.Ref,LaneRoadChain>> chains=new IdentityHashMap<>();
    V junctionDirection;double minimumHeight=Double.NEGATIVE_INFINITY,maximumHeight=Double.POSITIVE_INFINITY;
    Planning(Map<RoadRecord,Mesh> seeds,V direction,double minHeight,double maxHeight){root=CURRENT.get()==null;if(root){CURRENT.set(this);meshes.putAll(seeds);junctionDirection=direction;minimumHeight=minHeight;maximumHeight=maxHeight;}}
    final boolean root;final long started=System.nanoTime();long clearanceNanos;int routeCount,contactQueries;
    Planning(RoadData data){root=CURRENT.get()==null;if(root){CURRENT.set(this);if(data!=null)for(var b:data.index.roads.values())meshes.put(b.record,b.mesh);}}
    public void close(){if(root){
      long elapsed=System.nanoTime()-started;
      if(elapsed>1_000_000_000L||Boolean.getBoolean("sr.profile"))System.getLogger("SplineRoads/planner").log(System.Logger.Level.INFO,
          String.format(Locale.ROOT,"SR route plan total_ms=%.3f clearance_ms=%.3f candidates=%d contact_queries=%d cached_meshes=%d",elapsed/1e6,clearanceNanos/1e6,routeCount,contactQueries,meshes.size()));
      CURRENT.remove();}}
  }
  private static Mesh mesh(RoadRecord road){var plan=CURRENT.get();if(plan==null)return road.mesh();if(plan.meshes.size()>512)plan.meshes.clear();return plan.meshes.computeIfAbsent(road,RoadRecord::mesh);}
  private static List<RoadClearance.Contact> contacts(Mesh proposed,Mesh existing){
    var plan=CURRENT.get();if(plan==null)return RoadClearance.contacts(proposed,existing);
    if(plan.decks.size()>32)plan.decks.clear();
    long start=System.nanoTime();plan.contactQueries++;
    try{return RoadClearance.contacts(proposed,plan.decks.computeIfAbsent(existing,RoadClearance::prepare));}
    finally{plan.clearanceNanos+=System.nanoTime()-start;}
  }
  public static RoadRecord host(Map<UUID,RoadRecord> all,LanePoints.Ref ref){var r=all.get(ref.road());if(r==null)throw new IllegalArgumentException("所选道路已不存在");if(r.assembly()!=null||r.junction()!=null||!LanePoints.supported(r.settings()))throw new IllegalArgumentException("连接器仅支持独立普通道路／高速／自由匝道的地面、自动高架、标准小河桥、梁式高架与跨线桥；不支持立交内部道路");LaneTopology.point(r,ref.point());return r;}
  public static LaneRampPaths.Port port(RoadRecord road,LanePoints.Point p){var mesh=mesh(road);var l=LanePoints.lane(mesh,p);var sample=RoadStructures.sample(mesh,l.station());var layout=RoadProfile.layout(mesh,sample);double lateral=l.position().sub(sample.center()).dot(sample.left());int side=layout.catalog().twoWay()?(lateral<layout.medianCenter()?-1:1):layout.outside();double edge=side<0?-layout.motorMin():layout.motorMax();double distance=Math.max(l.width(),edge-side*lateral+l.width()/2);double step=Math.min(.5,mesh.length()/10);var before=RoadStructures.sample(mesh,Math.max(0,l.station()-step));var after=RoadStructures.sample(mesh,Math.min(mesh.length(),l.station()+step));V delta=after.center().sub(before.center());double grade=delta.y()/Math.max(.001,delta.horizontalLength())*l.sign();return new LaneRampPaths.Port(l.position(),l.direction(),sample.left().mul(side),distance,grade);}
  public static LaneRampPaths.Port targetPort(RoadRecord road,LanePoints.Point point,double offset){
    var lane=LanePoints.lane(mesh(road),point);double station=lane.station()+lane.sign()*offset;
    if(station<-.00001||station>mesh(road).length()+.00001)throw new IllegalArgumentException("汇入口超出所选道路，不能换路或换车道");
    return port(road,LanePoints.point(point.id(),point.origin(),mesh(road),station,point.lane()));
  }
  public static LaneRampPaths.Port arrivalPort(RoadRecord road,LanePoints.Point point,double offset,LanePoints.Link link,UUID connection){
    if(link.options().arrival()!=LanePoints.Arrival.ADD)return targetPort(road,point,offset);
    var at=LanePoints.lane(mesh(road),point);var added=LaneAdditions.owned(mesh(road),connection);
    return port(road,LanePoints.point(point.id(),point.origin(),mesh(road),at.station()+at.sign()*offset,added.slot()));
  }
  public static double targetReach(LanePoints.Options options){return options.landing()==LanePoints.Landing.EXACT?0:Math.max(32,Math.min(128,options.radius()+options.transition()*2));}
  /** Restoration policy does not move a previously validated motor path. */
  private static boolean sameRoute(LanePoints.Link a,LanePoints.Link b){
    if(Objects.equals(a,b))return true;
    if(a==null||b==null||!a.options().separatesLane()||!b.options().separatesLane())return false;
    var o=a.options();var n=b.options();
    return a.equals(new LanePoints.Link(b.from(),b.to(),new LanePoints.Options(n.path(),o.departure(),n.arrival(),n.radius(),n.transition(),n.elevation(),n.landing(),n.gradeOverride()),b.junctionMouth(),b.targetOffset(),b.protectedMerge(),b.rectangularClosure()));
  }
  private static LaneRampAlignment.Mouth[] contactMouths(Map<UUID,RoadRecord> all,LanePoints.Link link,UUID id){
    var source=host(all,link.from());var point=LaneTopology.point(source,link.from().point());var lane=LanePoints.lane(source.mesh(),point);
    var from=link.options().sourceExtra()?new LaneRampAlignment.Mouth(lane.width(),0,0):LaneRampAlignment.mouth(source.mesh(),lane);
    var to=new LaneRampAlignment.Mouth(lane.width(),0,0);
    if(link.to().road()!=null){
      var position=chain(all,link.to()).at(link.targetOffset());var target=position.road().mesh();var targetLane=LanePoints.lane(target,position.point());
      if(link.options().arrival()==LanePoints.Arrival.ADD){var added=LaneAdditions.owned(target,id);targetLane=LanePoints.lane(target,added.station(),added.slot());}
      to=link.options().targetExtra()?new LaneRampAlignment.Mouth(targetLane.width(),0,0):LaneRampAlignment.mouth(target,targetLane);
    }
    return new LaneRampAlignment.Mouth[]{from,to};
  }
  private record Generated(RoadRecord road,LanePoints.Path path) {}
  static RoadRecord generate(RoadData data,Map<UUID,RoadRecord> all,UUID id,UUID owner,LanePoints.Link link){
    return generateChoice(data,all,id,owner,link,null).road();
  }
  public static RoadRecord reconfigure(RoadRecord old,Settings requested,Map<UUID,RoadRecord> all){return reconfigure(null,old,requested,all);}
  private static RoadRecord reconfigure(RoadData data,RoadRecord old,Settings requested,Map<UUID,RoadRecord> all){
    if(RoadProfile.catalog(requested.style()).lanes()!=1||(!LanePoints.supported(requested)&&requested.structure()!=Structure.TUNNEL))
      throw new IllegalArgumentException("连接器匝道须保持独立匝道类型、单车道及支持的道路结构");
    return generateChoice(data,all,old.id(),old.owner(),LaneTopology.metadata(old).link(),requested).road();
  }
  private static Generated generateChoice(RoadData data,Map<UUID,RoadRecord> all,UUID id,UUID owner,LanePoints.Link link,Settings edited){
    try(var budget=RoadPlanningBudget.cancellable("匝道路线/净空搜索");var ignored=new Planning(data)){return generatePlanned(data,all,id,owner,link,edited);}
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
    // Rechecking an unchanged connector must first validate its saved alignment.
    // Searching from scratch can choose a different family or reject an old valid
    // layout after a candidate ordering update. This still runs current clearance.
    if(old!=null&&(edited==null||old.settings().options().ends().start()!=null&&old.settings().options().ends().end()!=null)&&sameRoute(LaneTopology.metadata(old).link(),link)&&base.width()==old.settings().width()
        &&base.thickness()==old.settings().thickness()&&base.style()==old.settings().style()
        &&(link.options().elevation()==LanePoints.Elevation.KEEP||!LaneTopology.metadata(old).link().options().equals(link.options())||!verticalKink(old.mesh()))
        &&(link.options().elevation()!=LanePoints.Elevation.AUTO||monotone(old.mesh())))try{
      var kept=old.settings(base);var candidate=kept.mesh();var context=LaneCrossSections.staged(all,id,link,candidate);
      var mouths=contactMouths(context,link,id);
      if(!LaneRampAlignment.matches(candidate,mouths[0],mouths[1])){
        candidate=LaneRampAlignment.refit(candidate,mouths[0],mouths[1]);
        var start=RoadRibbon.start(candidate);var end=RoadRibbon.end(candidate);
        kept=new RoadRecord(id,owner,RampJunctions.at(start.position()),RampJunctions.at(end.position()),start,end,candidate.settings(),false,4).alignment(null,candidate).furniturePhase(old.furniturePhase());
        context=LaneCrossSections.staged(all,id,link,candidate);
      }
      var fitted=fitHostContacts(candidate,context,link);
      if(!fitted.samples().equals(candidate.samples())){
        candidate=fitted;var start=RoadRibbon.start(candidate);var end=RoadRibbon.end(candidate);
        kept=new RoadRecord(id,owner,RampJunctions.at(start.position()),RampJunctions.at(end.position()),start,end,candidate.settings(),false,4).alignment(null,candidate).furniturePhase(old.furniturePhase());
        context=LaneCrossSections.staged(all,id,link,candidate);
      }
      var from=port(host(context,link.from()),LaneTopology.point(host(context,link.from()),link.from().point()));
      var to=link.to().road()==null?null:resolvedArrival(context,link,id);
      if(LaneRampAlignment.axis(candidate,true).distance(from.position())<1e-5
          &&LaneRampAlignment.axis(candidate,false).distance(to==null?link.junctionMouth():to.position())<1e-5){
        validate(candidate,context,id,link);
        for(var road:context.values())for(var cut:LaneTopology.metadata(road).cuts())if(cut.connection().equals(id)&&!cut.arrival()&&cut.temporary())LaneReopening.validateRestored(road.mesh(),cut.lane(),candidate,id);
        return new Generated(kept,link.options().path());
      }
    }catch(IllegalArgumentException ignored){/* changed world: run the normal constrained search */}
    var a=port(source,p);if(link.options().sourceExtra())a=approach(source,p,0,true,base,link.options().transition());
    var offsets=new LinkedHashSet<Double>();if(Math.abs(link.targetOffset())<=targetReach(link.options()))offsets.add(link.targetOffset());offsets.add(0d);
    if(link.to().road()!=null&&link.options().landing()!=LanePoints.Landing.EXACT){double reach=targetReach(link.options());for(double step=8;step<reach;step+=8){offsets.add(step);offsets.add(-step);}offsets.add(reach);offsets.add(-reach);}
    var stagedOffsets=new HashMap<Double,Map<UUID,RoadRecord>>();
    var candidateMemo=new CandidateMemo();
    var errors=new EnumMap<LanePoints.Path,String>(LanePoints.Path.class);String error="所选范围内没有可用汇入口";LaneRampPaths.Port target=null;
    // A locally shifted short turn must be tried before committing to a long
    // loop. Previously a valid loop in pass one hid a much shorter lead/tail
    // solution in pass two (e.g. a 540m route instead of a 403m route).
    double targetY=link.to().road()==null?link.junctionMouth().y():port(host(all,link.to()),LaneTopology.point(host(all,link.to()),link.to().point())).position().y();
    boolean preferOver=link.options().elevation()==LanePoints.Elevation.AUTO&&targetY>=a.position().y()-1e-7;
    // Exhaust all horizontal routes and same-lane landing positions above first.
    // A cheap underpass on the first route must not preempt a feasible overpass.
    // Search monotone candidates before allowing any crest/valley. OVER preference
    // applies only after both monotone layer choices have been exhausted.
    boolean auto=link.options().elevation()==LanePoints.Elevation.AUTO;
    for(int profilePass=0;profilePass<(auto?2:1);profilePass++)
    for(int elevationPass=0;elevationPass<(preferOver&&(!auto||profilePass>0)?2:1);elevationPass++)
    for(int stage=0;stage<3;stage++)for(double offset:offsets)try{
      RoadPlanningBudget.check();
      var actual=link.targetOffset(offset);
      var context=stagedOffsets.get(offset);
      if(context==null){context=LaneCrossSections.staged(all,id,actual);stagedOffsets.put(offset,context);}
      var currentSource=host(context,actual.from());
      if(!LaneSections.active(mesh(currentSource),lane.station(),p.lane()))throw new IllegalArgumentException("汇出车道已在此位置分离，不能从车道空位再次汇出");
      LaneRampPaths.Port b;double targetLaneWidth=lane.width();
      LaneRoadChain.Position targetPosition=null;
      if(link.to().road()!=null){
        targetPosition=chain(context,link.to()).at(offset);
        var road=targetPosition.road();var point=targetPosition.point();var selectedLane=LanePoints.lane(mesh(road),point);
        if((link.options().arrival()==LanePoints.Arrival.MERGE||link.options().arrival()==LanePoints.Arrival.FLOW)&&!LaneSections.active(mesh(road),selectedLane.station(),point.lane()))throw new IllegalArgumentException("B 位于已分离的车道空位，不能当作开放车道汇入");
        targetLaneWidth=selectedLane.width();
        b=link.options().targetExtra()?approach(road,point,0,false,base,link.options().transition()):arrivalPort(road,point,0,actual,id);
      }else{
        if(link.options().targetExtra())throw new IllegalArgumentException("路口中心接入口不设置沿主路的额外汇入车道");
        V direction=data!=null?RampJunctions.spec(data,link.to().junction()).center().sub(link.junctionMouth()).horizontalUnit():CURRENT.get()!=null&&CURRENT.get().junctionDirection!=null?CURRENT.get().junctionDirection:old!=null?old.end().direction():null;
        if(direction==null)throw new IllegalArgumentException("缺少路口接入方向");
        b=new LaneRampPaths.Port(link.junctionMouth(),direction,direction.left(),lane.width(),0);
      }
      target=b;
      var md=(old==null?LanePoints.Data.EMPTY:LaneTopology.metadata(old)).link(actual).openings(List.of());
      var settings=base.options(base.options().lanePoints(md));
      for(var candidate:routeCandidates(a,b,settings,link.options(),source,p,targetPosition==null?null:targetPosition.road(),targetPosition==null?null:targetPosition.point(),0,maxGrade,errors,stage,candidateMemo,offset))try{
        RoadPlanningBudget.check();
        if(CURRENT.get()!=null)CURRENT.get().routeCount++;
        var sourceMouth=actual.options().sourceExtra()?new LaneRampAlignment.Mouth(lane.width(),0,0):LaneRampAlignment.mouth(currentSource.mesh(),LanePoints.lane(currentSource.mesh(),p));
        var targetMouth=targetPosition==null||actual.options().targetExtra()?new LaneRampAlignment.Mouth(targetLaneWidth,0,0):LaneRampAlignment.mouth(targetPosition.road().mesh(),LanePoints.lane(targetPosition.road().mesh(),targetPosition.point()));
        // ADD has a new outer slot, not the old selected slot.
        if(actual.options().arrival()==LanePoints.Arrival.ADD){var host=targetPosition.road().mesh();var added=LaneAdditions.owned(host,id);targetMouth=LaneRampAlignment.mouth(host,LanePoints.lane(host,added.station(),added.slot()));}
        var baseMesh=fitHostContacts(LaneRampAlignment.fit(candidate.mesh(),sourceMouth,targetMouth),context,actual);
        for(Mesh mesh:heightCandidates(baseMesh,context,id,actual,errors,candidate.path(),preferOver&&elevationPass==0&&(!auto||profilePass>0),auto&&profilePass==0))try{
          if(data!=null&&!data.withinHeight(mesh)||data==null&&CURRENT.get()!=null&&(mesh.min().y()-mesh.settings().thickness()<CURRENT.get().minimumHeight||mesh.max().y()+4>=CURRENT.get().maximumHeight))throw new IllegalArgumentException("上跨／下穿超出世界高度范围");
          var finalContext=actual.options().departure()==LanePoints.Departure.TEMPORARY||actual.closesTarget()?
              LaneCrossSections.staged(all,id,actual,mesh):context;
          validate(mesh,finalContext,id,actual);
          if(actual.options().departure()==LanePoints.Departure.TEMPORARY)
            for(var road:finalContext.values())for(var cut:LaneTopology.metadata(road).cuts())if(cut.connection().equals(id)&&!cut.arrival())
              LaneReopening.validateRestored(road.mesh(),cut.lane(),mesh,id);
          var start=RoadRibbon.start(mesh);var end=RoadRibbon.end(mesh);
          var record=new RoadRecord(id,owner,RampJunctions.at(start.position()),RampJunctions.at(end.position()),start,end,mesh.settings(),false,4).alignment(null,mesh);
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
  /** Generate expensive fallback ribbons only when the preceding candidate actually failed.
   * RC1 eagerly built the entire source-lead x target-tail cross product even when
   * the first direct candidate was valid. No route or safety check is removed. */
  private record CandidateKey(double offset,double lead,double tail,LanePoints.Path kind,boolean automatic,boolean smooth,boolean constrained){}
  /** Horizontal search is identical across vertical passes. Replay the same lazy
   * groups, including rejections, within this one immutable planning request.
   * Bounded by samples and entries; never retain roads after the request. */
  private static final class CandidateMemo {
    private record Entry(List<LaneRampPaths.Candidate> candidates,String error,int weight){}
    private final LinkedHashMap<CandidateKey,Entry> entries=new LinkedHashMap<>(64,.75f,true);
    private int weight;
    List<LaneRampPaths.Candidate> get(CandidateKey key,java.util.function.Supplier<List<LaneRampPaths.Candidate>> build){
      var value=entries.get(key);
      if(value==null){
        try{var made=List.copyOf(build.get());value=new Entry(made,null,Math.max(1,made.stream().mapToInt(c->c.mesh().samples().size()).sum()));}
        catch(IllegalArgumentException e){value=new Entry(List.of(),e.getMessage(),1);}
        if(value.weight()<=300_000){entries.put(key,value);weight+=value.weight();var it=entries.values().iterator();
          while(weight>300_000||entries.size()>256){weight-=it.next().weight();it.remove();}}
      }
      if(value.error()!=null)throw new IllegalArgumentException(value.error());return value.candidates();
    }
  }
  private static Iterable<LaneRampPaths.Candidate> routeCandidates(LaneRampPaths.Port a,LaneRampPaths.Port b,Settings settings,LanePoints.Options options,RoadRecord source,LanePoints.Point point,RoadRecord target,LanePoints.Point targetPoint,double targetOffset,double maxGrade,Map<LanePoints.Path,String> errors,int stage,CandidateMemo memo,double landingOffset){
    if(stage==2&&options.path()!=LanePoints.Path.AUTO)return List.of();
    double[] leads=options.separatesLane()?new double[]{0,32,64,96,128}:new double[]{0};
    boolean tail=target!=null&&(options.arrival()==LanePoints.Arrival.MERGE||options.arrival()==LanePoints.Arrival.FLOW||options.arrival()==LanePoints.Arrival.ADD);
    double[] tails=tail?new double[]{0,16,32,48,64,96,128}:new double[]{0};
    return ()->new Iterator<>(){
      int li,ti,ki;final List<LanePoints.Path> kinds=options.path()==LanePoints.Path.AUTO?
          stage==2?List.of(LanePoints.Path.LEFT_LOOP):List.of(LanePoints.Path.DIRECT,LanePoints.Path.RIGHT,LanePoints.Path.LEFT):List.of(options.path());
      Iterator<LaneRampPaths.Candidate> ready=Collections.emptyIterator();
      public boolean hasNext(){
        while(!ready.hasNext()&&ti<tails.length){
          RoadPlanningBudget.check();
          double lead=leads[li],endLength=tails[ti];var kind=kinds.get(ki++);if(ki==kinds.size()){ki=0;if(++li==leads.length){li=0;ti++;}}
          if(stage==0&&(lead!=0||endLength!=0)||stage==1&&lead==0&&endLength==0)continue;
          var specific=new LanePoints.Options(kind,options.departure(),options.arrival(),options.radius(),options.transition(),options.elevation(),options.landing(),options.gradeOverride());
          try{var key=new CandidateKey(landingOffset,lead,endLength,kind,options.path()==LanePoints.Path.AUTO&&kind==LanePoints.Path.LEFT_LOOP,kind==LanePoints.Path.RIGHT||options.path()==LanePoints.Path.LEFT,options.path()!=LanePoints.Path.AUTO);ready=memo.get(key,()->routeGroup(a,b,settings,specific,source,point,target,targetPoint,targetOffset,maxGrade,lead,endLength,key.automatic(),key.smooth(),key.constrained())).iterator();}
          catch(IllegalArgumentException failure){errors.putIfAbsent(kind,failure.getMessage());ready=Collections.emptyIterator();}
        }
        return ready.hasNext();
      }
      public LaneRampPaths.Candidate next(){if(!hasNext())throw new NoSuchElementException();return ready.next();}
    };
  }
  private static List<LaneRampPaths.Candidate> routeGroup(LaneRampPaths.Port a,LaneRampPaths.Port b,Settings settings,LanePoints.Options options,RoadRecord source,LanePoints.Point point,RoadRecord target,LanePoints.Point targetPoint,double targetOffset,double maxGrade,double leadLength,double tailLength,boolean automaticTurns,boolean smooth,boolean constrainSmooth){
    var before=new ArrayList<Sample>();var after=new ArrayList<Sample>();
    if(leadLength>0){
      var raw=source.rawMesh();var lane=LanePoints.lane(raw,point);double end=lane.station()+lane.sign()*leadLength;
      if(end<=0||end>=raw.length())return List.of();
      for(double d=0;d<=leadLength;d+=.5){var q=LanePoints.lane(raw,lane.station()+lane.sign()*d,point.lane());before.add(new Sample(q.position(),q.direction().left(),d,settings.width()/2));}
      var last=LanePoints.lane(raw,end,point.lane());V delta=last.position().sub(before.get(before.size()-2).center());
      a=new LaneRampPaths.Port(last.position(),last.direction(),a.outside(),a.extraWidth(),delta.y()/Math.max(.001,delta.horizontalLength()));
    }
    if(tailLength>0){
      var raw=target.rawMesh();var lane=LanePoints.lane(raw,targetPoint);double end=lane.station()+lane.sign()*targetOffset,start=end-lane.sign()*tailLength;
      if(start<=.5||start>=raw.length()-.5)return List.of();
      int slot=targetPoint.lane();
      if(options.arrival()==LanePoints.Arrival.ADD){
        // Match the newly added arrival axis, not the old clicked lane.
        var best=raw.settings().options().lanePoints().additions().stream().filter(v->v.sign()==lane.sign()&&Math.abs(v.station()-end)<1e-5).reduce((x,y)->y);
        if(best.isEmpty())return List.of();slot=best.get().slot();
        // A lead inside a widening taper is not a stable full-width incoming lane.
        if(best.get().fraction(start)<1-1e-6)return List.of();
      }
      var q=LanePoints.lane(raw,start,slot);var prev=LanePoints.lane(raw,start-lane.sign()*.5,slot);V delta=q.position().sub(prev.position());
      b=new LaneRampPaths.Port(q.position(),q.direction(),b.outside(),b.extraWidth(),delta.y()/Math.max(.001,delta.horizontalLength()));
      for(double d=.5;d<=tailLength+.001;d+=.5){var at=LanePoints.lane(raw,start+lane.sign()*d,slot);after.add(new Sample(at.position(),at.direction().left(),d,settings.width()/2));}
    }
    var smoothCandidates=smooth?LaneRampPaths.smoothTurns(a,b,settings,options,maxGrade).stream()
        .filter(c->!constrainSmooth||matchesTurn(c.mesh(),options.path()))
        .map(c->constrainSmooth?new LaneRampPaths.Candidate(options.path(),c.mesh()):c).toList():List.<LaneRampPaths.Candidate>of();
    var candidates=new ArrayList<LaneRampPaths.Candidate>();
    if(automaticTurns)candidates.addAll(LaneRampPaths.automaticTurns(a,b,settings,options,maxGrade));
    try{candidates.addAll(LaneRampPaths.candidates(a,b,settings,options,maxGrade));}
    catch(IllegalArgumentException failure){if(candidates.isEmpty()&&smoothCandidates.isEmpty())throw failure;}
    candidates.sort(Comparator.comparingDouble(c->c.mesh().length()));
    // Smoothness may modestly increase length, but must not double the footprint.
    double shortest=candidates.stream().mapToDouble(c->c.mesh().length()).min().orElse(Double.POSITIVE_INFINITY);
    candidates.addAll(0,smoothCandidates.stream().filter(c->c.mesh().length()<=shortest*1.35).toList());
    if(before.isEmpty()&&after.isEmpty())return candidates;
    var out=new ArrayList<LaneRampPaths.Candidate>();
    for(var c:candidates)try{
      var samples=new ArrayList<>(before);samples.addAll(before.isEmpty()?c.mesh().samples():c.mesh().samples().subList(1,c.mesh().samples().size()));samples.addAll(after);
      var mesh=RoadRibbon.mesh(samples,settings);RoadRibbon.checkSelfIntersections(mesh,4);out.add(new LaneRampPaths.Candidate(c.path(),mesh));
    }catch(IllegalArgumentException ignored){}
    return out;
  }
  static boolean matchesTurn(Mesh mesh,LanePoints.Path path){
    double turn=0;var samples=mesh.samples();
    for(int i=1;i<samples.size();i++){V a=samples.get(i-1).left(),b=samples.get(i).left();turn+=Math.atan2(a.x()*b.z()-a.z()*b.x(),a.dot(b));}
    return path==LanePoints.Path.RIGHT?turn>=-.001&&turn<=Math.PI+.001:
        path==LanePoints.Path.LEFT?turn<=.001&&turn>=-Math.PI-.001:true;
  }
  private static LaneRampPaths.Port approach(RoadRecord road,LanePoints.Point point,double offset,boolean source,Settings settings,double transition){
    var selected=LanePoints.lane(mesh(road),point);double station=selected.station()+selected.sign()*offset;
    var port=targetPort(road,point,offset);var samples=LaneRampApproach.build(mesh(road),point.lane(),station,source,settings,transition);
    return new LaneRampPaths.Port(port.position(),port.direction(),port.outside(),port.extraWidth(),port.grade(),samples);
  }
  static Set<UUID> contactRoads(Map<UUID,RoadRecord> all,LanePoints.Ref ref){
    var plan=CURRENT.get();if(plan==null)return findContactRoads(all,ref);
    if(plan.connected.size()>64)plan.connected.clear();
    return plan.connected.computeIfAbsent(all,k->new HashMap<>()).computeIfAbsent(ref,k->findContactRoads(all,ref));
  }
  private static LaneRoadChain chain(Map<UUID,RoadRecord> all,LanePoints.Ref ref){
    var p=CURRENT.get();if(p==null)return LaneRoadChain.of(all,ref);
    if(p.chains.size()>64)p.chains.clear();
    return p.chains.computeIfAbsent(all,k->new HashMap<>()).computeIfAbsent(ref,k->LaneRoadChain.of(all,k));
  }
  private static Set<UUID> findContactRoads(Map<UUID,RoadRecord> all,LanePoints.Ref ref){
    if(ref.road()==null)return Set.of();
    return chain(all,ref).ids();
  }

  static int contactEnd(Mesh mesh,Map<UUID,RoadRecord> all,Set<UUID> hosts,boolean first){
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
    // Auxiliary tapers and closed-slot connectors keep their independent profile.
    // Only ordinary BRANCH/FLOW shares the selected lane (or single-lane parent)
    // until its paved throat separates. A whole multi-lane host would incorrectly
    // flatten an overpass across unrelated or opposite traffic.
    if(link.protectedMerge()){
      if(link.options().departure()==LanePoints.Departure.BRANCH)mesh=LaneRampThroat.fit(mesh,normalHosts(all,link,true),true);
      if(link.options().arrival()==LanePoints.Arrival.FLOW)mesh=LaneRampThroat.fit(mesh,normalHosts(all,link,false),false);
      return mesh;
    }
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
  private static List<Mesh> normalHosts(Map<UUID,RoadRecord> all,LanePoints.Link link,boolean first){
    var ref=first?link.from():link.to();if(ref.road()==null)return List.of();
    var out=new ArrayList<Mesh>();
    // Single-lane parents (including free ramps) share their full paved ribbon.
    // For a multi-lane host use only the selected lane: opposite lanes remain obstacles.
    for(var leg:chain(all,ref).legs()){
      var host=mesh(leg.road());
      if(RoadProfile.catalog(host.settings()).lanes()==1)out.add(host);
      else {var points=new ArrayList<Sample>();for(var at:host.samples()){
        var lane=LanePoints.lane(host,at.distance(),leg.slot());
        points.add(new Sample(lane.position(),at.left(),at.distance(),lane.width()/2));
      }out.add(RoadRibbon.mesh(points,new Settings(Mode.CURVE,Style.C1_RAMP,4,host.settings().thickness(),.35,90)));}
    }
    for(var road:all.values()){
      var other=LaneTopology.metadata(road).link();if(other==null)continue;
      boolean sibling=first?other.from().equals(link.from())&&!other.to().equals(link.to())&&other.options().departure()==LanePoints.Departure.BRANCH
          :other.to().equals(link.to())&&!other.from().equals(link.from())&&other.options().arrival()==LanePoints.Arrival.FLOW;
      if(sibling)out.add(mesh(road));
    }
    return List.copyOf(out);
  }
  private static double hostHeightDelta(Sample sample,List<Mesh> hosts){
    double distance=Double.POSITIVE_INFINITY,delta=0;
    for(var host:hosts){var q=RoadQueries.horizontal(host,sample.center());if(q.horizontalDistance()<distance){distance=q.horizontalDistance();delta=q.sample().center().y()-sample.center().y();}}
    return delta;
  }
  private record Obstacle(UUID road,RoadClearance.Contact contact,boolean structure){Obstacle(UUID road,RoadClearance.Contact contact){this(road,contact,false);}}
  private static List<Obstacle> crossings(Mesh mesh,Map<UUID,RoadRecord> all,UUID id,LanePoints.Link link){return crossings(mesh,all,id,link,null);}
  private static List<Obstacle> crossings(Mesh mesh,Map<UUID,RoadRecord> all,UUID id,LanePoints.Link link,Set<UUID> changed){
    var out=new ArrayList<Obstacle>();var sourceHosts=contactRoads(all,link.from());var targetHosts=contactRoads(all,link.to());
    double sourceLimit=mesh.samples().get(contactEnd(mesh,all,sourceHosts,true)).distance();
    double targetLimit=mesh.samples().get(contactEnd(mesh,all,targetHosts,false)).distance();
    for(var road:all.values()){
      RoadPlanningBudget.check();
      if(changed!=null&&!changed.contains(road.id()))continue;
      if(road.id().equals(id)||road.junction()!=null&&Objects.equals(road.assembly(),link.to().junction()))continue;
      var other=LaneTopology.metadata(road).link();
      // A dependent branch is regenerated after this parent. Its own validator then checks the
      // new parent outside its local throat; unrelated siblings are NOT globally exempted.
      if(other!=null&&(id.equals(other.from().road())||id.equals(other.to().road())))continue;
      Mesh old=mesh(road);
      double sharedStart=-1,sharedEnd=mesh.length()+1;
      if(other!=null&&link.from().equals(other.from()))sharedStart=mesh.samples().get(contactEnd(mesh,Map.of(road.id(),road),Set.of(road.id()),true)).distance();
      if(other!=null&&link.to().equals(other.to()))sharedEnd=mesh.samples().get(contactEnd(mesh,Map.of(road.id(),road),Set.of(road.id()),false)).distance();
      if(sharedStart>mesh.length()*.9||sharedEnd<mesh.length()*.1)throw new IllegalArgumentException("同一车道点的两条匝道几乎全程重合，请使用不同汇入方向");
      // Parent ramps and siblings remain obstacles outside their actual local
      // throat. Exempting an entire related ramp hid later furniture collisions
      // from the solver and left only a construction-time veto.
      if(other!=null&&RoadIndex.overlapXZ(mesh,old,8))
        for(var part:road.structures())for(var c:RoadClearance.structureContacts(part,mesh,4.25)){
          if(sourceHosts.contains(road.id())&&c.to()<=sourceLimit+.01||targetHosts.contains(road.id())&&c.from()>=targetLimit-.01
              ||c.to()<=sharedStart+.01||c.from()>=sharedEnd-.01)continue;
          out.add(new Obstacle(road.id(),c,true));
        }
      if(!RoadIndex.overlapXZ(mesh,old,0))continue;
      var exactSlots=new LinkedHashSet<Integer>();
      if(link.protectedMerge()){
        for(var leg:chain(all,link.from()).legs())if(leg.road().id().equals(road.id()))exactSlots.add(leg.slot());
        if(link.to().road()!=null)for(var leg:chain(all,link.to()).legs())if(leg.road().id().equals(road.id())){
          if(link.options().arrival()==LanePoints.Arrival.ADD){for(var added:LaneTopology.metadata(road).additions())if(added.connection().equals(id))exactSlots.add(added.slot());}
          else exactSlots.add(leg.slot());
        }
      }
      if(!exactSlots.isEmpty()){
        Mesh protectedDeck=protectedDeck(old,exactSlots);
        for(var c:contacts(mesh,protectedDeck))out.add(new Obstacle(road.id(),c));
      }
      for(var c:contacts(mesh,old)){
        boolean sourceJoin=sourceHosts.contains(road.id())&&c.to()<=sourceLimit+.01;
        if(sourceJoin&&link.options().separatesLane())sourceJoin=separationThroat(c,road,link,all);
        if(sourceJoin||targetHosts.contains(road.id())&&c.from()>=targetLimit-.01)continue;
        if(c.to()<=sharedStart+.01||c.from()>=sharedEnd-.01)continue;
        out.add(new Obstacle(road.id(),c));
      }
    }return out;
  }
  /** A slot exclusion depends only on the immutable host mesh and the selected
   * slot set, not on a candidate's height. Reuse exactly the same protected deck. */
  private static Mesh protectedDeck(Mesh host,Set<Integer> slots){
    var key=List.copyOf(slots);var plan=CURRENT.get();
    if(plan==null)return makeProtectedDeck(host,key);
    if(plan.protectedDecks.size()>128)plan.protectedDecks.clear();
    return plan.protectedDecks.computeIfAbsent(host,k->new HashMap<>()).computeIfAbsent(key,k->makeProtectedDeck(host,k));
  }
  private static Mesh makeProtectedDeck(Mesh host,List<Integer> slots){
    var result=LaneDeck.motorOnly(host);for(int slot:slots)result=LaneDeck.excludingSlot(result,slot);return result;
  }
  /** Only the selected slot joins; opposite traffic is not exempt merely because it has the same host ID. */
  private static boolean separationThroat(RoadClearance.Contact contact,RoadRecord road,LanePoints.Link link,Map<UUID,RoadRecord> all){
    for(var leg:chain(all,link.from()).legs())if(leg.road().id().equals(road.id())){
      var raw=road.rawMesh();var q=RoadQueries.horizontal(raw,contact.ours());var lane=LanePoints.lane(raw,q.sample().distance(),leg.slot());
      return leg.coordinate(lane.station())>=-.25&&Math.abs(contact.ours().sub(lane.position()).dot(q.sample().left()))<=lane.width()/2+.55;
    }return false;
  }
  static LaneRampPaths.Port resolvedArrival(Map<UUID,RoadRecord> all,LanePoints.Link link,UUID id){
    var position=chain(all,link.to()).at(link.targetOffset());
    return arrivalPort(position.road(),position.point(),0,link,id);
  }
  private static List<Mesh> heightCandidates(Mesh base,Map<UUID,RoadRecord> all,UUID id,LanePoints.Link link,Map<LanePoints.Path,String> errors,LanePoints.Path path,boolean overFirstPass,boolean monotoneOnly){
    var mode=overFirstPass?LanePoints.Elevation.OVER:link.options().elevation();var out=new ArrayList<Mesh>();
    List<Obstacle> contacts=crossings(base,all,id,link);
    boolean clear=contacts.stream().noneMatch(c->c.contact().blocked()||existingLayerConflict(c,all));
    if(mode==LanePoints.Elevation.KEEP)return List.of(base);
    if(mode==LanePoints.Elevation.AUTO&&clear&&(!monotoneOnly||monotone(base)))return List.of(base);
    if(contacts.isEmpty()&&(!monotoneOnly||monotone(base)))return List.of(base);
    double from=link.protectedMerge()?fixedApproach(base,all,link,true):link.options().separatesLane()?0:base.samples().get(contactEnd(base,all,contactRoads(all,link.from()),true)).distance();
    double to=link.protectedMerge()?base.length()-fixedApproach(base,all,link,false):link.closesTarget()?base.length():base.samples().get(contactEnd(base,all,contactRoads(all,link.to()),false)).distance();
    for(boolean over:mode==LanePoints.Elevation.UNDER?new boolean[]{false}:mode==LanePoints.Elevation.OVER?new boolean[]{true}:new boolean[]{true,false}){
      // A new AUTO ramp may not invalidate a saved explicit OVER/UNDER crossing.
      if(contacts.stream().anyMatch(o->{var r=all.get(o.road());var l=r==null?null:LaneTopology.metadata(r).link();return !o.structure()&&l!=null&&(over&&l.options().elevation()==LanePoints.Elevation.OVER||!over&&l.options().elevation()==LanePoints.Elevation.UNDER);}))continue;
      var constraints=new ArrayList<LaneRampHeights.Constraint>();
      for(var obstacle:contacts){var c=obstacle.contact();double amount=over?c.raise():c.lower();if(!c.blocked()&&(over?c.ours().y()>c.other().y():c.ours().y()<c.other().y()))amount=0;constraints.add(new LaneRampHeights.Constraint(c.from(),c.to(),amount));}
      try{out.add(LaneRampCorridor.solveMixed(base,from,to,constraints.stream().map(c->new LaneRampCorridor.Bound(c.from(),c.to(),c.amount(),over)).toList(),gradeLimit(all,link),monotoneOnly));}
      catch(IllegalArgumentException e){errors.put(path,e.getMessage());}
    }
    if(mode==LanePoints.Elevation.AUTO){
      // Uniform OVER/UNDER is not sufficient when roads on different levels bound
      // the same connector. Choose the locally reachable side of each real contact.
      var mixed=new ArrayList<LaneRampCorridor.Bound>();double grade=gradeLimit(all,link);
      for(var obstacle:contacts){
        var c=obstacle.contact();var other=all.get(obstacle.road());var saved=other==null||obstacle.structure()?null:LaneTopology.metadata(other).link();
        double d=Math.max(0,Math.min(base.length(),(c.from()+c.to())/2));var at=RoadStructures.sample(base,d);
        double reachLow=Math.max(base.first().center().y()-grade*d,base.last().center().y()-grade*(base.length()-d));
        double reachHigh=Math.min(base.first().center().y()+grade*d,base.last().center().y()+grade*(base.length()-d));
        boolean up=at.center().y()+c.raise()<=reachHigh+1e-6,down=at.center().y()-c.lower()>=reachLow-1e-6;
        boolean over=saved!=null&&saved.options().elevation()==LanePoints.Elevation.UNDER||
            !(saved!=null&&saved.options().elevation()==LanePoints.Elevation.OVER)&&
            (up&&!down||up==down&&(c.blocked()?c.raise()<=c.lower():c.ours().y()>=c.other().y()));
        mixed.add(new LaneRampCorridor.Bound(c.from(),c.to(),over?c.raise():c.lower(),over));
      }
      try{out.add(LaneRampCorridor.solveMixed(base,from,to,mixed,grade,monotoneOnly));}catch(IllegalArgumentException e){
        if(out.isEmpty())errors.put(path,e.getMessage()+obstacleSummary(base,contacts));
      }
    }
    if(mode==LanePoints.Elevation.AUTO&&out.isEmpty())out.addAll(mixedCandidates(base,from,to,contacts,all,gradeLimit(all,link),monotoneOnly));
    if(out.isEmpty()&&!contacts.isEmpty())errors.put(path,errors.getOrDefault(path,"没有满足净空的纵坡方案")+obstacleSummary(base,contacts));
    if(out.isEmpty()&&!errors.containsKey(path))errors.put(path,"没有满足端点、坡度及净空的自动跨越方案");
    if(mode==LanePoints.Elevation.AUTO)out.sort(Comparator.comparingDouble(LaneRamps::verticalEffort));
    return out;
  }
  private static String obstacleSummary(Mesh base,List<Obstacle> contacts){
    if(contacts.isEmpty())return "";
    // Name real obstacle identities and both vertical alternatives, not just a
    // propagated solver sample that can be several blocks away from the obstacle.
    var c=contacts.stream().filter(v->v.contact().blocked()).min(Comparator.comparingDouble(v->Math.min(v.contact().from(),base.length()-v.contact().to()))).orElse(contacts.get(0));
    var v=c.contact();return String.format(Locale.ROOT,"；实际冲突道路 %s，交叠区在候选路线距 A 沿线 %.1f–%.1f 格（路线全长 %.1f 格，AB 水平直距 %.1f 格），%s；要求净空 %.2f 格，上跨需抬升 %.2f 格，下穿需降低 %.2f 格",c.road(),v.from(),v.to(),base.length(),base.last().center().sub(base.first().center()).horizontalLength(),RoadClearance.clearanceLabel(v.usableClearance()),v.required(),v.raise(),v.lower());
  }
  static boolean monotone(Mesh m){return verticalEffort(m)<=Math.abs(m.last().center().y()-m.first().center().y())+1e-5;}
  /** An unchanged legacy preview must not keep a sharp grade corner forever.
   * Smooth saved routes retain the fast path; restoration-only edits keep their
   * exact validated alignment, and KEEP remains an explicit height-preservation mode. */
  static boolean verticalKink(Mesh mesh){
    var points=mesh.samples();double previous=0,previousRun=0;
    for(int i=1;i<points.size();i++){
      V delta=points.get(i).center().sub(points.get(i-1).center());double run=delta.horizontalLength();if(run<1e-6)continue;
      double grade=delta.y()/run,change=Math.abs(grade-previous);
      if(previousRun>0&&change>.02&&change/((run+previousRun)/2)>.03)return true;
      previous=grade;previousRun=run;
    }
    return false;
  }
  /** Keep several feasible layer assignments. A locally cheaper underpass must not
   * rule out an overpass required by the NEXT ramp. Triangle contacts share a group. */
  /** The running maximum is exactly the old group's max(to). Rescanning all
   * previous triangle contacts for every insertion made this step quadratic. */
  private static List<List<Obstacle>> groupedContacts(List<Obstacle> contacts){
    var groups=new ArrayList<List<Obstacle>>();double groupEnd=0;
    for(var obstacle:contacts.stream().sorted(Comparator.comparing((Obstacle o)->o.road().toString()).thenComparingDouble(o->o.contact().from())).toList()){
      List<Obstacle> group=groups.isEmpty()?null:groups.get(groups.size()-1);
      if(group==null||!group.get(0).road().equals(obstacle.road())||group.get(0).structure()!=obstacle.structure()
          ||obstacle.contact().from()>groupEnd+2){group=new ArrayList<>();groups.add(group);groupEnd=Double.NEGATIVE_INFINITY;}
      group.add(obstacle);groupEnd=Math.max(groupEnd,obstacle.contact().to());
    }
    groups.sort(Comparator.comparingDouble(g->g.get(0).contact().from()));
    return groups;
  }
  private static List<Mesh> mixedCandidates(Mesh base,double from,double to,List<Obstacle> contacts,Map<UUID,RoadRecord> all,double grade,boolean strict){
    var groups=groupedContacts(contacts);
    record Choice(List<LaneRampCorridor.Bound> bounds,Mesh mesh){}
    var beam=new ArrayList<Choice>();beam.add(new Choice(List.of(),base));
    for(var group:groups){var next=new ArrayList<Choice>();
      for(var choice:beam)for(boolean over:new boolean[]{true,false}){
        RoadPlanningBudget.check();var road=all.get(group.get(0).road());var saved=road==null||group.get(0).structure()?null:LaneTopology.metadata(road).link();
        if(saved!=null&&(over&&saved.options().elevation()==LanePoints.Elevation.OVER||!over&&saved.options().elevation()==LanePoints.Elevation.UNDER))continue;
        var bounds=new ArrayList<>(choice.bounds());
        for(var o:group){var c=o.contact();bounds.add(new LaneRampCorridor.Bound(c.from(),c.to(),over?c.raise():c.lower(),over));}
        try{next.add(new Choice(List.copyOf(bounds),LaneRampCorridor.solveMixed(base,from,to,bounds,grade,strict)));}catch(IllegalArgumentException ignored){}
      }
      next.sort(Comparator.comparingDouble(c->verticalEffort(c.mesh())));
      beam=new ArrayList<>(next.subList(0,Math.min(16,next.size())));if(beam.isEmpty())break;
    }
    return beam.stream().map(Choice::mesh).toList();
  }
  private static double verticalEffort(Mesh m){double total=0;for(int i=1;i<m.samples().size();i++)total+=Math.abs(m.samples().get(i).center().y()-m.samples().get(i-1).center().y());return total;}
  private static boolean existingLayerConflict(Obstacle o,Map<UUID,RoadRecord> all){
    if(o.structure())return false;var r=all.get(o.road());var link=r==null?null:LaneTopology.metadata(r).link();if(link==null)return false;
    var mode=link.options().elevation();return mode==LanePoints.Elevation.OVER&&o.contact().ours().y()>o.contact().other().y()||mode==LanePoints.Elevation.UNDER&&o.contact().ours().y()<o.contact().other().y();
  }
  private static double fixedApproach(Mesh ramp,Map<UUID,RoadRecord> all,LanePoints.Link link,boolean source){
    if(source&&link.options().departure()==LanePoints.Departure.BRANCH||!source&&link.options().arrival()==LanePoints.Arrival.FLOW){
      int end=LaneRampThroat.end(ramp,normalHosts(all,link,source),source);
      return source?ramp.samples().get(end).distance():ramp.length()-ramp.samples().get(end).distance();
    }
    if(source?!link.options().sourceExtra():!link.options().targetExtra())return 0;
    var ref=source?link.from():link.to();if(ref.road()==null)return 0;
    var location=LaneRoadChain.of(all,ref).at(source?0:link.targetOffset());var host=location.road();var point=location.point();
    var lane=LanePoints.lane(mesh(host),point);
    var samples=LaneRampApproach.build(mesh(host),point.lane(),lane.station(),source,ramp.settings(),link.options().transition());
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
    for(var other:all.values()){
      var saved=LaneTopology.metadata(other).link();
      if(saved==null||other.id().equals(id)||other.id().equals(link.from().road())||other.id().equals(link.to().road())
          ||saved.from().equals(link.from())||saved.to().equals(link.to())||!RoadIndex.overlapXZ(mesh,mesh(other),2))continue;
      for(var part:other.structures())if(RoadClearance.structureInvades(part,mesh,4.25))
        throw new IllegalArgumentException("候选路线侵入已建匝道 "+other.id()+" 的设施，不能通过重建旧匝道腾出空间");
    }
    for(var obstacle:crossings(mesh,all,id,link)){
      var c=obstacle.contact();var mode=link.options().elevation();
      boolean wrongLayer=!obstacle.structure()&&(mode==LanePoints.Elevation.OVER&&c.ours().y()<c.other().y()||mode==LanePoints.Elevation.UNDER&&c.ours().y()>c.other().y());
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
  /** Immutable route snapshot. The worker never touches a ServerLevel, RoadData or ItemStack. */
  public static final class PreviewWork {
    private final Map<UUID,RoadRecord> all;
    private final Map<RoadRecord,Mesh> seeds;
    private final UUID id,owner;private final LanePoints.Link link;private final V junctionDirection;
    public final long revision;private final double minHeight,maxHeight;
    private PreviewWork(Map<UUID,RoadRecord> all,Map<RoadRecord,Mesh> seeds,UUID id,UUID owner,LanePoints.Link link,V direction,long revision,double minHeight,double maxHeight){
      this.all=Collections.unmodifiableMap(new LinkedHashMap<>(all));this.seeds=Collections.unmodifiableMap(new IdentityHashMap<>(seeds));
      this.id=id;this.owner=owner;this.link=link;this.junctionDirection=direction;this.revision=revision;this.minHeight=minHeight;this.maxHeight=maxHeight;
    }
    public boolean alternative(int attempt){return attempt==0||link.options().elevation()==LanePoints.Elevation.AUTO&&attempt<=2;}
    public PreviewRoute compute(){return compute(0);}
    public PreviewRoute compute(int attempt){try(var scope=new Planning(seeds,junctionDirection,minHeight,maxHeight)){
      if(!alternative(attempt))throw new IllegalArgumentException("没有更多高程候选");
      var trial=link;
      if(attempt>0){var o=link.options();var options=new LanePoints.Options(o.path(),o.departure(),o.arrival(),o.radius(),o.transition(),attempt==1?LanePoints.Elevation.OVER:LanePoints.Elevation.UNDER,o.landing(),o.gradeOverride());
        trial=new LanePoints.Link(link.from(),link.to(),options,link.junctionMouth(),link.targetOffset(),link.protectedMerge(),link.rectangularClosure());}
      var generated=generateChoice(null,all,id,owner,trial,null);var road=generated.road();
      if(attempt>0){var data=LaneTopology.metadata(road);var selected=data.link();
        road=road.withLanePoints(data.link(new LanePoints.Link(selected.from(),selected.to(),link.options(),selected.junctionMouth(),selected.targetOffset(),selected.protectedMerge(),selected.rectangularClosure())));}
      return new PreviewRoute(road,generated.path());
    }}
    public <T> T resolve(java.util.function.Function<PreviewRoute,T> validate){
      CandidateRejected rejection=null;
      for(int attempt=0;alternative(attempt);attempt++){
        PreviewRoute route;
        try{route=compute(attempt);}catch(IllegalArgumentException e){if(rejection==null)throw e;continue;}
        try{return validate.apply(route);}catch(CandidateRejected e){rejection=e;}
      }
      throw rejection;
    }
  }
  /** A geometrically valid candidate can still conflict after terrain-dependent lining is added. */
  public static final class CandidateRejected extends IllegalArgumentException {
    public CandidateRejected(String message){super(message);}
  }
  public record PreviewRoute(RoadRecord road,LanePoints.Path path){}
  /** Include actual roads even when outside the client's normal subscription radius. */
  public static void conflictRoads(CompoundTag reply,ServerPlayer player,String message){
    var records=new ListTag();var data=RoadData.get(player.serverLevel());
    for(UUID id:RoadConflictIds.read(message)){var road=data.index.roads.get(id);if(road!=null)records.add(road.record.header());}
    reply.put("ConflictRoads",records);
  }
  public static PreviewWork preparePreview(ServerPlayer player,ItemStack tool,CompoundTag t){
    selection(player,tool,t);var data=RoadData.get(player.serverLevel());var from=LanePointCodec.ref(t.getCompound("From"));var to=LanePointCodec.ref(t.getCompound("To"));var options=LanePointCodec.options(t.getCompound("Options"));var all=LaneTopology.records(data);var source=host(all,from);RoadData.requireOwner(player,source.owner());
    if(to.road()!=null)RoadData.requireOwner(player,host(all,to).owner());else RoadData.requireOwner(player,data.junctions.getOrDefault(to.junction(),new CompoundTag()).getUUID("Owner"));
    V mouth=to.junction()==null?null:RampJunctions.mouth(data,to.junction(),LaneTopology.point(source,from.point()).position(),new Settings(Mode.STRAIGHT,Style.O1_ONE,Math.max(4,LanePoints.lane(mesh(source),LaneTopology.point(source,from.point())).width()+1),1,.35,90));
    V direction=to.junction()==null?null:RampJunctions.spec(data,to.junction()).center().sub(mouth).horizontalUnit();
    UUID id=t.hasUUID("Id")?t.getUUID("Id"):UUID.randomUUID();double previousOffset=t.hasUUID("Id")?LaneTopology.metadata(all.get(id)).link().targetOffset():0;
    var seeds=new IdentityHashMap<RoadRecord,Mesh>();for(var built:data.index.roads.values())seeds.put(built.record,built.mesh);
    return new PreviewWork(all,seeds,id,t.hasUUID("Id")?all.get(id).owner():player.getUUID(),new LanePoints.Link(from,to,options,mouth,previousOffset),direction,data.index.revision(),player.serverLevel().getMinBuildHeight(),player.serverLevel().getMaxBuildHeight());
  }
  public static CompoundTag preview(ServerPlayer player,ItemStack tool,CompoundTag t){
    var work=preparePreview(player,tool,t);return work.resolve(route->finishPreview(player,tool,t,work,route));
  }
  /** Main-thread validation/publish. Reject stale work before touching any live state. */
  public static CompoundTag finishPreview(ServerPlayer player,ItemStack tool,CompoundTag t,PreviewWork work,PreviewRoute generated){
    selection(player,tool,t);var data=RoadData.get(player.serverLevel());
    if(data.index.revision()!=work.revision)throw new IllegalArgumentException("计算期间道路已改变，旧预览已丢弃，请重新预览");
    var all=work.all;var id=work.id;var from=work.link.from();var to=work.link.to();var options=work.link.options();
    // Publish the actual normalized link selected by the worker, including the
    // rectangular closure and flexible B offset. The raw request has neither.
    RoadRecord r=generated.road();if(!data.withinHeight(r.mesh()))throw new IllegalArgumentException("上跨／下穿超出世界高度范围");
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
    return new AssemblyRequest(contactEndpoints(LaneTopology.records(data),r),List.copyOf(moves));
  }
  static Set<BlockPos> contactEndpoints(Map<UUID,RoadRecord> all,RoadRecord r){
    var endpoints=new HashSet<BlockPos>();endpoints.add(r.a());endpoints.add(r.b());
    var pending=new ArrayDeque<LanePoints.Ref>();var seen=new HashSet<UUID>();seen.add(r.id());
    var link=LaneTopology.metadata(r).link();if(link!=null){pending.add(link.from());pending.add(link.to());}
    while(!pending.isEmpty())for(UUID id:contactRoads(all,pending.removeFirst()))if(seen.add(id)){
      var host=all.get(id);endpoints.add(host.a());endpoints.add(host.b());
      // Opening a child branch also replans its saved parent's furniture.
      // That parent's original host markers remain legitimate contacts.
      var parent=LaneTopology.metadata(host).link();if(parent!=null){pending.add(parent.from());pending.add(parent.to());}
    }
    return Set.copyOf(endpoints);
  }
  public static void build(RoadData data,ServerLevel level,ServerPlayer player,RoadRecord r){
    var request=assemblyRequest(data,r);
    data.replaceAssembly(level,player,List.of(new RoadIndex.Built(r)),data.index.roads.containsKey(r.id())?Set.of(r.id()):Set.of(),request.endpoints(),request.moves());RampJunctions.sync(data);data.setDirty();
  }
  private LaneRamps(){}
}
