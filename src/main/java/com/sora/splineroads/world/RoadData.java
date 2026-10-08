package com.sora.splineroads.world;

import com.sora.splineroads.SplineRoads;
import com.sora.splineroads.core.RoadGeometry;
import com.sora.splineroads.core.RoadConnectionChecks;
import com.sora.splineroads.core.RoadWaterPolicy;
import com.sora.splineroads.core.LanePoints;
import com.sora.splineroads.core.RoadGantry;
import com.sora.splineroads.core.RoadLaneLines;
import com.sora.splineroads.core.RoadAttachments;
import com.sora.splineroads.core.RetiredRoadSigns;
import com.sora.splineroads.core.RoadRaster;
import com.sora.splineroads.core.RoadInfrastructure;
import com.sora.splineroads.core.RoadStructures;
import com.sora.splineroads.core.RoadSigns;
import com.sora.splineroads.core.RoadGeometry.*;
import com.sora.splineroads.core.RoadTunnel;
import com.sora.splineroads.core.RoadTunnelFit;
import com.sora.splineroads.core.RoadNoses;
import com.sora.splineroads.core.RoadLimits;
import com.sora.splineroads.core.RoadPlanner;
import com.sora.splineroads.core.RoadProfile;
import com.sora.splineroads.core.RoadQueries;
import com.sora.splineroads.core.RoadTransitions;
import com.sora.splineroads.core.RoadEndpointSections;
import com.sora.splineroads.core.RoadTimings;
import com.sora.splineroads.net.RoadNetwork;
import java.util.*;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.saveddata.SavedData;

public final class RoadData extends SavedData {
  public final RoadIndex index = new RoadIndex();
  private ServerLevel owningLevel;
  boolean withinHeight(com.sora.splineroads.core.RoadGeometry.Mesh mesh){return owningLevel==null||mesh.min().y()-mesh.settings().thickness()>=owningLevel.getMinBuildHeight()&&mesh.max().y()+4<owningLevel.getMaxBuildHeight();}
  private Set<UUID> confirmedLaneDeletes=Set.of();
  void removeWithDependents(ServerLevel level,ServerPlayer player,UUID id,Set<UUID> dependents){for(var v:dependents)if(player!=null)requireOwner(player,index.roads.get(v).record.owner());confirmedLaneDeletes=Set.copyOf(dependents);try{remove(level,player,id);}finally{confirmedLaneDeletes=Set.of();}}
  final Map<UUID,RoadRecord> streets = new LinkedHashMap<>();
  final Map<UUID, CompoundTag> junctions = new HashMap<>();
  final Map<UUID, CompoundTag> interchanges = new HashMap<>();
  private final Map<Long,List<RoadStructures.Part>> retiredSignsByChunk=new HashMap<>();
  private final Long2ObjectOpenHashMap<BlockState> sidewalkPlaced = new Long2ObjectOpenHashMap<>();
  private final Long2ObjectOpenHashMap<BlockState> terrainOriginal = new Long2ObjectOpenHashMap<>();
  private final Long2ObjectOpenHashMap<BlockState> terrainFill = new Long2ObjectOpenHashMap<>();
  private final Map<Long, Set<UUID>> repairedChunks = new LinkedHashMap<>(128,.75f,true) {
    @Override protected boolean removeEldestEntry(Map.Entry<Long, Set<UUID>> e) { return size() > 4096; }
  };

  public static RoadData get(ServerLevel level) {
    var data=level.getDataStorage().computeIfAbsent(RoadData::load, RoadData::new, "splineroads");data.owningLevel=level;return data;
  }

  private static boolean rSign(RoadStructures.Part p){return p.material()==RoadStructures.Material.SIGN_GREEN||p.material()==RoadStructures.Material.SIGN_BLUE;}
  public static RoadData load(CompoundTag root) {
    if (root.getInt("Version") > 43)
      throw new IllegalStateException("Spline Roads save is newer than this mod");
    long loadStarted=System.nanoTime();
    RoadData data = new RoadData();
    Map<CompoundTag,BlockState> palette=new HashMap<>();
    for(Tag t:root.getList("Roads",Tag.TAG_COMPOUND)){
      var r=RoadRecord.load((CompoundTag)t);boolean hasSigns=!r.settings().options().infrastructure().signs().isEmpty()||r.structures().stream().anyMatch(p->p.material()==RoadStructures.Material.CB_SIGN||rSign(p));
      var removed=hasSigns?RetiredRoadSigns.removed(r.mesh(),r.structures()):List.<RoadStructures.Part>of();
      if(!removed.isEmpty()){
        for(var part:removed){double pad=part.halfExtent();for(int x=(int)Math.floor(Math.min(part.a().x(),part.b().x())-pad)>>4;x<=(int)Math.floor(Math.max(part.a().x(),part.b().x())+pad)>>4;x++)for(int z=(int)Math.floor(Math.min(part.a().z(),part.b().z())-pad)>>4;z<=(int)Math.floor(Math.max(part.a().z(),part.b().z())+pad)>>4;z++)data.retiredSignsByChunk.computeIfAbsent(net.minecraft.world.level.ChunkPos.asLong(x,z),k->new ArrayList<>()).add(part);}
        var settings=r.settings().options(r.settings().options().infrastructure(r.settings().options().infrastructure().signs(List.of())));
        r=new RoadRecord(r.id(),r.owner(),r.a(),r.b(),r.start(),r.end(),settings,r.automatic(),r.clearance(),r.structures().stream().filter(p->!removed.contains(p)).toList(),r.endCaps(),r.buildVersion(),r.assembly(),r.alignment(),r.furniturePhase(),r.junction());
      }data.index.put(RoadIndex.Built.loading(r));
    }
    for(Tag value:root.getList("RetiredSignCleanup",Tag.TAG_COMPOUND)){var tag=(CompoundTag)value;data.retiredSignsByChunk.computeIfAbsent(tag.getLong("Chunk"),k->new ArrayList<>()).addAll(RoadRecord.unpackStructures(tag.getByteArray("Parts")));}
    RoadBlockStorage.read(root.getList(root.contains("TerrainFillPalette")?"TerrainFillPalette":"OriginalPalette", Tag.TAG_COMPOUND), data.terrainFill);
    RoadBlockStorage.read(root.getList("SidewalkPalette", Tag.TAG_COMPOUND), data.sidewalkPlaced);
    RoadBlockStorage.read(root.getList("TerrainOriginalPalette", Tag.TAG_COMPOUND), data.terrainOriginal);
    for (Tag entry : root.getList("Interchanges", Tag.TAG_COMPOUND)) {
      CompoundTag t = (CompoundTag) entry;
      data.interchanges.put(t.getUUID("Id"), t.copy());
    }
    for (Tag entry : root.getList("Junctions", Tag.TAG_COMPOUND)) {
      CompoundTag t = (CompoundTag)entry; data.junctions.put(t.getUUID("Id"),t.copy());
    }
    for(Tag t:root.getList("LogicalStreets",Tag.TAG_COMPOUND)){var r=RoadRecord.load((CompoundTag)t);data.streets.put(r.id(),r);}
    if(root.getInt("Version")<18)for(Tag entry:root.getList("SidewalkBlocks",Tag.TAG_COMPOUND)) {var t=(CompoundTag)entry;data.sidewalkPlaced.put(t.getLong("Pos"),palette.computeIfAbsent(t.getCompound("State"),v->NbtUtils.readBlockState(BuiltInRegistries.BLOCK.asLookup(),v)));}
    if(!data.retiredSignsByChunk.isEmpty())data.setDirty();
    LaneTopology.initialize(data);
    RoadTimings.reportLoad(loadStarted,data.index.roads.size());
    return data;
  }

  @Override
  public CompoundTag save(CompoundTag root) {
    root.putInt("Version", 43);
    var cleanup=new ListTag();retiredSignsByChunk.forEach((chunk,parts)->{var t=new CompoundTag();t.putLong("Chunk",chunk);t.putByteArray("Parts",RoadRecord.packStructures(parts));cleanup.add(t);});root.put("RetiredSignCleanup",cleanup);
    ListTag logical=new ListTag();streets.values().forEach(r->logical.add(r.save()));root.put("LogicalStreets",logical);
    ListTag roads = new ListTag();
    index.roads.values().forEach(r -> roads.add(r.record.save()));
    ListTag groups = new ListTag();
    interchanges.values().forEach(t -> groups.add(t.copy()));
    root.put("Interchanges", groups);
    ListTag junctionTags = new ListTag(); junctions.values().forEach(t -> junctionTags.add(t.copy()));
    root.put("Junctions", junctionTags);
    root.put("TerrainOriginalPalette", RoadBlockStorage.write(terrainOriginal));
    root.put("TerrainFillPalette", RoadBlockStorage.write(terrainFill));
    root.remove("OriginalPalette");
    root.put("SidewalkPalette", RoadBlockStorage.write(sidewalkPlaced));
    root.remove("OriginalEntities");
    root.put("Roads", roads);
    root.remove("Originals");
    root.remove("SidewalkBlocks");
    return root;
  }

  public boolean linked(BlockPos pos) {
    return !index.atNode(pos).isEmpty();
  }

  public static void requireOwner(ServerPlayer player, UUID owner) {
    if (owner != null && !owner.equals(player.getUUID()) && !player.hasPermissions(2))
      throw new IllegalArgumentException("只能编辑自己创建的道路和端点");
  }

  public static NodeEntity requireNode(ServerLevel level, BlockPos pos, ServerPlayer player) {
    try (var work = RoadWorkChunks.open(level)) {
      work.node(pos);
      if (!(level.getBlockEntity(pos) instanceof NodeEntity node))
        throw new IllegalArgumentException("端点不存在，可能已被删除，请重新选择");
      if (player != null) {
        requireOwner(player, node.owner);
        if (!player.mayBuild() || !level.mayInteract(player, pos))
          throw new IllegalArgumentException("此处没有建设权限");
      }
      return node;
    }
  }

  public RoadRecord connect(
      ServerLevel level,
      ServerPlayer player,
      BlockPos a,
      BlockPos b,
      Settings settings,
      UUID replace) {
    return connect(level, player, a, b, settings, replace, false);
  }

  public RoadRecord connect(
      ServerLevel level,
      ServerPlayer player,
      BlockPos a,
      BlockPos b,
      Settings settings,
      UUID replace,
      boolean levelEnds) {
    return connect(level,player,a,b,settings,replace,levelEnds,false);
  }

  public RoadRecord connect(ServerLevel level,ServerPlayer player,BlockPos a,BlockPos b,Settings settings,UUID replace,boolean levelEnds,boolean forceJunction) {return connect(level,player,a,b,settings,replace,levelEnds,forceJunction,forceJunction);}
  public RoadRecord connect(ServerLevel level,ServerPlayer player,BlockPos a,BlockPos b,Settings settings,UUID replace,boolean levelEnds,boolean forceA,boolean forceB) {
    var savedAttachments=replace==null?RoadAttachments.Data.EMPTY:streets.containsKey(replace)?streets.get(replace).settings().options().attachments():index.roads.containsKey(replace)?index.roads.get(replace).record.settings().options().attachments():RoadAttachments.Data.EMPTY;
    var existingLane=replace==null?null:index.roads.get(replace);
    settings=settings.options(settings.options().attachments(savedAttachments).lanePoints(existingLane==null?LanePoints.Data.EMPTY:existingLane.record.settings().options().lanePoints()));
    if(replace!=null){var current=streets.containsKey(replace)?streets.get(replace):index.roads.containsKey(replace)?index.roads.get(replace).record:null;
      if(current==null||!current.a().equals(a)||!current.b().equals(b))throw new IllegalArgumentException("编辑目标与端点不一致，请重新右键目标道路");}
    try (var timing=RoadTimings.start("connect",index.roads.size(),1);
         var workChunks = RoadWorkChunks.open(level)) {
      var roadType=RoadProfile.catalog(settings.style()).type();
      if(roadType==RoadProfile.Type.HIGHWAY){forceA=false;forceB=false;}
      boolean throughRoad=roadType==RoadProfile.Type.ORDINARY||roadType==RoadProfile.Type.HIGHWAY;
      if(streets.containsKey(replace)&&!throughRoad)throw new IllegalArgumentException("路口接路不能直接改为匝道，请先删除此支路");
      boolean simpleVertical=com.sora.splineroads.core.RoadTunnelFit.enabled(settings)&&AutoJunctions.center(this,a)==null&&AutoJunctions.center(this,b)==null&&AutoJunctions.degree(this,a,replace)<2&&AutoJunctions.degree(this,b,replace)<2&&!forceA&&!forceB;
      if(throughRoad && !simpleVertical && (AutoJunctions.handles(this,a,b,replace,forceA||forceB)
          ||roadType==RoadProfile.Type.ORDINARY&&AutoJunctions.incompatible(this,a,b,replace,settings)))
        return AutoJunctions.connect(level,player,a,b,settings,replace,forceA,forceB,levelEnds);
      if (a.equals(b)) throw new IllegalArgumentException("请选择两个不同的端点");
      if (Math.hypot((double) a.getX() - b.getX(), (double) a.getZ() - b.getZ())
          > com.sora.splineroads.core.RoadLimits.MAX_ENDPOINT_DISTANCE + 24)
        throw new IllegalArgumentException("端点距离超过 2048 格，请增加中间端点");
      NodeEntity na = requireNode(level, a, player), nb = requireNode(level, b, player);
      RoadRecord previous =
          replace == null
              ? null
              : Optional.ofNullable(index.roads.get(replace))
                  .orElseThrow(() -> new IllegalArgumentException("道路已经被删除"))
                  .record;
      UUID owner =
          previous == null
              ? (player == null ? new UUID(0, 0) : player.getUUID())
              : previous.owner();
      if (previous != null) {
        if (previous.assembly() != null) throw new IllegalArgumentException("自动立交需要使用立交连接器整体编辑");
        if (player != null) requireOwner(player, owner);
        if (!previous.a().equals(a) || !previous.b().equals(b))
          throw new IllegalArgumentException("道路端点已改变");
      }
      for (var road : index.roads.values())
        if (!road.record.id().equals(replace) && !(settings.laneRamp() && road.record.settings().laneRamp())
            && ((road.record.a().equals(a) && road.record.b().equals(b))
                || (road.record.a().equals(b) && road.record.b().equals(a))))
          throw new IllegalArgumentException("这两个端点已经有道路，右键路面即可编辑");

      var ah =
          constructionHint(level, a, true, replace, nb.constructionNode().position(), levelEnds);
      var bh =
          constructionHint(level, b, false, replace, na.constructionNode().position(), levelEnds);
      boolean linkedA = ah.linked(), linkedB = bh.linked();
      var seamA=ah;var seamB=bh;
      if (settings.mode() != Mode.AUTO) {
        Node ma =
            previous == null
                ? na.constructionNode()
                : new Node(
                    na.constructionNode().position(),
                    previous.start().yaw(),
                    previous.start().grade());
        Node mb =
            previous == null
                ? nb.constructionNode()
                : new Node(
                    nb.constructionNode().position(), previous.end().yaw(), previous.end().grade());
        if(liveEndpoint(a,replace))ma=new Node(seamA.node().position(),ma.yaw(),ma.grade());
        if(liveEndpoint(b,replace))mb=new Node(seamB.node().position(),mb.yaw(),mb.grade());
        if (levelEnds) {
          ma = atGround(ma, a);
          mb = atGround(mb, b);
        }
        ah = RoadPlanner.Hint.free(ma);
        bh = RoadPlanner.Hint.free(mb);
      }
      settings = joinWidths(settings, endpointWidth(a, replace), endpointWidth(b, replace));
      CompoundTag joins=new CompoundTag();jointPayload(joins,a,b,replace);
      settings = joinSections(settings,joins);
      var plan = RoadTunnelFit.plan(ah, bh, settings);
      RoadConnectionChecks.require(plan,seamA,seamB);
      RoadRecord record =
          new RoadRecord(
              replace == null ? UUID.randomUUID() : replace,
              owner,
              a,
              b,
              plan.start(),
              plan.end(),
              plan.settings(),
              settings.mode() == Mode.AUTO,
              4);
      List<RoadIndex.Built> changes = new ArrayList<>();
      Set<UUID> removed = new HashSet<>();
      if (replace != null) removed.add(replace);
      Node movedA = new Node(plan.start().position(), na.yaw, levelEnds ? 0 : na.grade),
          movedB = new Node(plan.end().position(), nb.yaw, levelEnds ? 0 : nb.grade);
      Map<BlockPos, Node> updates = new LinkedHashMap<>();
      if (!liveEndpoint(a,replace) && (movedA.position().distance(na.node().position()) > 1e-8 || levelEnds))
        updates.put(a, movedA);
      if (!liveEndpoint(b,replace) && (movedB.position().distance(nb.node().position()) > 1e-8 || levelEnds))
        updates.put(b, movedB);
      // Migrate the connected legacy-default cluster together; no new half-level seam is
      // introduced.
      ArrayDeque<BlockPos> queue = new ArrayDeque<>(updates.keySet());
      while (!queue.isEmpty()) {
        BlockPos pos = queue.removeFirst();
        for (var built : index.roads.values()) {
          var r = built.record;
          if (!r.a().equals(pos) && !r.b().equals(pos)) continue;
          BlockPos other = r.a().equals(pos) ? r.b() : r.a();
          if (updates.containsKey(other)) continue;
          NodeEntity entity = requireNode(level, other, player);
          Node next = entity.constructionNode();
          if (next.position().distance(entity.node().position()) > 1e-8) {
            updates.put(other, next);
            queue.add(other);
          }
        }
      }
      for (var built : index.roads.values()) {
        RoadRecord r = built.record;
        if (r.id().equals(replace) || (!updates.containsKey(r.a()) && !updates.containsKey(r.b())))
          continue;
        if (RoadTunnelFit.enabled(settings) && r.assembly()!=null)
          throw new IllegalArgumentException("要移动的桥隧端点属于路口或立交；请固定该端，仅调整另一端");
        if (player != null) requireOwner(player, r.owner());
        var ha = hint(level, r.a(), true, r.id());
        var hb = hint(level, r.b(), false, r.id());
        Node aa = r.start(), bb = r.end();
        if (updates.containsKey(r.a())) {
          Node before = requireNode(level, r.a(), null).node();
          ha = editedHint(ha, updates.get(r.a()), before);
          aa =
              editedHint(new RoadPlanner.Hint(aa, true, true, true), updates.get(r.a()), before)
                  .node();
        }
        if (updates.containsKey(r.b())) {
          Node before = requireNode(level, r.b(), null).node();
          hb = editedHint(hb, updates.get(r.b()), before);
          bb =
              editedHint(new RoadPlanner.Hint(bb, true, true, true), updates.get(r.b()), before)
                  .node();
        }
        Settings oldSettings = r.settings();
 if (r.automatic()) {
          var adjusted =
              RoadPlanner.plan(
                  ha, hb, RoadPlanner.mode(oldSettings, Mode.AUTO, oldSettings.arcDegrees()));
          aa = adjusted.start();
          bb = adjusted.end();
          oldSettings = adjusted.settings();
        }
        changes.add(
            new RoadIndex.Built(
                new RoadRecord(
                    r.id(),
                    r.owner(),
                    r.a(),
                    r.b(),
                    aa,
                    bb,
                    oldSettings,
                    r.automatic(),
                    r.clearance())));
        removed.add(r.id());
      }
      changes.add(new RoadIndex.Built(record));
      Map<BlockPos,BlockPos> relocated=new HashMap<>();List<NodeMove> moves=new ArrayList<>();
      if(RoadTunnelFit.enabled(settings))for(var entry:updates.entrySet()){
        BlockPos from=entry.getKey();Node target=entry.getValue();var marker=requireNode(level,from,player);
        if(target.position().sub(marker.node().position()).horizontalLength()<1e-7)continue;
        V p=target.position();BlockPos to=BlockPos.containing(p.x(),p.y(),p.z());
        relocated.put(from,to);moves.add(new NodeMove(from,to,target,marker.saveWithoutMetadata()));
      }
      if(!moves.isEmpty()){
        for(int i=0;i<changes.size();i++){
          var r=changes.get(i).record;
          changes.set(i,new RoadIndex.Built(new RoadRecord(r.id(),r.owner(),relocated.getOrDefault(r.a(),r.a()),relocated.getOrDefault(r.b(),r.b()),
              r.start(),r.end(),r.settings(),r.automatic(),r.clearance(),r.structures(),r.endCaps(),r.buildVersion(),r.assembly(),r.alignment(),r.furniturePhase(),r.junction())));
        }
        replaceBatch(level,player,changes,removed,false,updates.keySet(),moves);
      }else replaceBatch(level, player, changes, removed);
      updates.forEach((pos, n) -> requireNode(level, relocated.getOrDefault(pos,pos), null).apply(n));
      na=requireNode(level,relocated.getOrDefault(a,a),null);nb=requireNode(level,relocated.getOrDefault(b,b),null);
      na.apply(!linkedA && !na.headingLocked ? plan.start() : movedA);
      nb.apply(!linkedB && !nb.headingLocked ? plan.end() : movedB);
      if (levelEnds) {
        na.heightExplicit = false;
        nb.heightExplicit = false;
        na.gradeLocked = false;
        nb.gradeLocked = false;
        na.setChanged();
        nb.setChanged();
      }
      return index.roads.get(record.id()).record;
    }
  }

  public static Node atGround(Node n, BlockPos p) {
    return new Node(new V(n.position().x(), p.getY(), n.position().z()), n.yaw(), 0);
  }

  public RoadPlanner.Hint constructionHint(
      ServerLevel level, BlockPos pos, boolean start, UUID exclude, V towards, boolean levelEnds) {
    var base = hint(level, pos, start, exclude, towards);
    Node n = requireNode(level, pos, null).constructionNode();
    // A physical merge port may be laterally shifted from its logical marker.
    // Keep the marker and authored host axis unchanged; only the new road starts at the live port.
    Node v = new Node(liveEndpoint(pos,exclude)?base.node().position():n.position(), base.node().yaw(), base.node().grade());
    if (levelEnds) v = atGround(v, pos);
    return new RoadPlanner.Hint(
        v, base.headingLocked(), levelEnds || base.gradeLocked(), base.linked());
  }

  public RoadPlanner.Hint hint(ServerLevel level, BlockPos pos, boolean start, UUID exclude) {
    var current = exclude == null ? null : index.roads.get(exclude);
    V towards =
        current == null
            ? null
            : (current.record.a().equals(pos)
                ? current.record.end().position()
                : current.record.start().position());
    return hint(level, pos, start, exclude, towards);
  }

  public RoadPlanner.Hint hint(
      ServerLevel level, BlockPos pos, boolean start, UUID exclude, V towards) {
    NodeEntity entity = requireNode(level, pos, null);
    Node n = entity.node();
    RoadIndex.Built best = null;
    double bestScore = -Double.MAX_VALUE;
    for (var built : index.roads.values()) {
      if (built.record.id().equals(exclude)
          || (!built.record.a().equals(pos) && !built.record.b().equals(pos))
          || !transitionEndpoint(built,pos)&&!built.record.settings().style().ramp()) continue;
      double score =
          towards == null
              ? built.record.settings().width()
              : -away(built, pos).horizontalUnit().dot(towards.sub(n.position()).horizontalUnit());
      if (best == null
          || score > bestScore + 1e-8
          || (Math.abs(score - bestScore) < 1e-8
              && built.record.id().compareTo(best.record.id()) < 0)) {
        best = built;
        bestScore = score;
      }
    }
    if (best != null && best.mesh.closed()) {
      // Diameter markers are points ON a closed carriageway, not its start/end caps.
      // Normal feeders meet the ring at grade.
      var q = RoadQueries.project(best.mesh, n.position());
      return new RoadPlanner.Hint(
          new Node(q.sample().center(), n.yaw(), 0), entity.headingLocked, true, true);
    }
    if (best != null) {
      V d = away(best, pos).mul(start ? -1 : 1);
      return new RoadPlanner.Hint(
          new Node(RoadEndpointSections.changed(best.mesh,best.record.a().equals(pos))?com.sora.splineroads.core.RoadMedianAnchor.position(best.record.caps(0).mesh(),best.record.a().equals(pos)):n.position(), RoadPlanner.yaw(d), d.y()), true, true, true);
    }
    return new RoadPlanner.Hint(n, entity.headingLocked, entity.gradeLocked, false);
  }

  public long connectionsAt(BlockPos p) {
    return index.atNode(p).size();
  }

  public double endpointWidth(BlockPos p, UUID exclude) {
    double width = 0;
    for (var built : index.roads.values())
      if (!built.record.id().equals(exclude)
          && (transitionEndpoint(built,p)||built.record.settings().style().ramp())
          && (built.record.a().equals(p) || built.record.b().equals(p)))
        width =
            Math.max(
                width,
                2
                    * (built.record.a().equals(p) ? built.mesh.first() : built.mesh.last())
                        .halfWidth());
    return width;
  }

  public Settings endpointSettings(BlockPos p, UUID exclude) {
    return index.atNode(p).stream()
        .filter(id -> !id.equals(exclude))
        .map(index.roads::get)
        .filter(r -> r != null && transitionEndpoint(r,p) && !r.record.settings().style().ramp())
        .sorted(Comparator.comparing(r -> r.record.id()))
        .map(r -> RoadEndpointSections.changed(r.mesh,r.record.a().equals(p))?endpointSection(r,p):r.record.assembly() == null && !r.record.settings().options().ends().persistent() ? authoredSection(r.record.settings()) : endpointSection(r, p))
        .findFirst()
        .orElse(null);
  }

  private boolean liveEndpoint(BlockPos p,UUID exclude){
    return index.atNode(p).stream().filter(id->!id.equals(exclude)).map(index.roads::get)
        .anyMatch(r->transitionEndpoint(r,p)&&RoadEndpointSections.changed(r.mesh,r.record.a().equals(p)));
  }
  private Settings endpointSettings(BlockPos p,UUID exclude,boolean start){
    var donor=index.atNode(p).stream().filter(id->!id.equals(exclude)).map(index.roads::get)
        .filter(r->r!=null&&transitionEndpoint(r,p)&&!r.record.settings().style().ramp())
        .sorted(Comparator.comparing(r->r.record.id())).findFirst();
    if(donor.isEmpty())return null;var r=donor.get();boolean first=r.record.a().equals(p);
    var section=RoadEndpointSections.changed(r.mesh,first)?endpointSection(r,p):
        r.record.assembly()==null&&!r.record.settings().options().ends().persistent()?authoredSection(r.record.settings()):endpointSection(r,p);
    return RoadEndpointSections.orient(section,first==start);
  }

  /** Never feed an automatically derived seam back into the next seam calculation. */
  static Settings authoredSection(Settings s) {
    return s.taper(s.width(),s.width()).options(s.options().ends(RoadTransitions.Ends.NONE));
  }

  /** Junction pads have no traffic profile. Only an approach's real outside port can be joined. */
  static boolean transitionEndpoint(RoadIndex.Built road,BlockPos pos){
    var r=road.record;
    if(LaneTopology.metadata(r).link()!=null)return false;
    if(r.junction()!=null)return r.junction().get().arm()>=0&&r.a().equals(pos)&&!r.b().equals(pos);
    // A trimmed street retains the logical junction marker, but its physical port is
    // at the cutback. The junction owns that seam, not other streets at the marker.
    var ends=r.settings().options().ends();
    if(r.a().equals(pos)&&ends.trimmedStart()>1e-6
        ||r.b().equals(pos)&&ends.trimmedEnd()>1e-6)return false;
    // Standalone legacy unmarked roads still have ordinary endpoints; assembly infill does not.
    return r.assembly()==null||r.settings().style()!=Style.UNMARKED;
  }

  /** Read the section actually built at this end, including assembly grid padding/tapers. */
  static Settings endpointSection(RoadIndex.Built road, BlockPos pos) {
    boolean first = road.record.a().equals(pos);
    if(RoadEndpointSections.changed(road.mesh,first))return RoadEndpointSections.section(road.record.caps(0).mesh(),first,false);
    var at = first ? road.mesh.first() : road.mesh.last();
    var base = road.record.junction()!=null&&road.record.junction().get().arm()>=0
        ? JunctionRoads.editable(road.record.junction().spec().arms().get(road.record.junction().get().arm()))
        : road.record.settings();
    var end = first ? base.options().ends().start() : base.options().ends().end();
    // Junction approach settings describe its own outer port. External end tapers
    // belong to the original street and may describe a different, remote road.
    if(road.record.junction()!=null) end=null;
    var s = end == null ? base : end.settings(base.options().leftTraffic());
    var options = s.options().ends(RoadTransitions.Ends.NONE)
        .infrastructure(base.options().infrastructure()).cycleFinish(s.options().cycle() ? (base.options().cycleAsphalt() ? RoadProfile.Options.CycleFinish.ASPHALT : RoadProfile.Options.CycleFinish.GREEN) : RoadProfile.Options.CycleFinish.NONE);
    // Old section records predate sidewalks. Missing is inheritance, never "disabled".
    if(end == null || end.sidewalk() == null) options=options.sidewalk(base.options().sidewalk());
    var result=new Settings(Mode.STRAIGHT,s.style(),at.halfWidth()*2,base.thickness(),
        base.tension(),base.arcDegrees()).structure(base.structure()).options(options);
    result.validate();
    return result;
  }

  public void jointPayload(CompoundTag payload, BlockPos a, BlockPos b, UUID exclude) {
    payload.remove("JoinSectionA");payload.remove("JoinSectionB");
    Settings start = endpointSettings(a, exclude,true), end = endpointSettings(b, exclude,false);
    if (start != null) payload.put("JoinSectionA", RoadRecord.writeSettings(start));
    if (end != null) payload.put("JoinSectionB", RoadRecord.writeSettings(end));
    payload.putBoolean("FixedSectionA",fixedEndpoint(a,exclude));
    payload.putBoolean("LiveSectionA",liveEndpoint(a,exclude));
    payload.putBoolean("FixedSectionB",fixedEndpoint(b,exclude));
    payload.putBoolean("LiveSectionB",liveEndpoint(b,exclude));
  }

  private boolean fixedEndpoint(BlockPos p,UUID exclude){return index.atNode(p).stream().filter(id->!id.equals(exclude)).map(index.roads::get).anyMatch(r->(r.record.assembly()!=null||RoadEndpointSections.changed(r.mesh,r.record.a().equals(p)))&&transitionEndpoint(r,p)&&!r.record.settings().style().ramp());}

  public static Settings joinSections(Settings s, CompoundTag payload) {
    Settings joined=RoadTransitions.join(
        s,
        payload.contains("JoinSectionA")
            ? RoadRecord.readSettings(payload.getCompound("JoinSectionA"))
            : null,
        payload.contains("JoinSectionB")
            ? RoadRecord.readSettings(payload.getCompound("JoinSectionB"))
            : null);
    var ends=joined.options().ends();
    if(s.style().ramp()||!RoadProfile.modern(s.style())||!payload.getBoolean("FixedSectionA")&&!payload.getBoolean("FixedSectionB"))return joined;
    return RoadTransitions.ends(joined,
        payload.getBoolean("FixedSectionA")&&payload.contains("JoinSectionA")?RoadTransitions.Section.of(RoadRecord.readSettings(payload.getCompound("JoinSectionA"))):ends.start(),
        payload.getBoolean("FixedSectionB")&&payload.contains("JoinSectionB")?RoadTransitions.Section.of(RoadRecord.readSettings(payload.getCompound("JoinSectionB"))):ends.end());
  }

  public static Settings joinWidths(Settings s, double a, double b) {
    return s.style().ramp() && !s.laneRamp()
        ? s.taper(Math.max(s.width(), a), Math.max(s.width(), b))
        : s;
  }

  public static CompoundTag writeHint(RoadPlanner.Hint hint) {
    CompoundTag t = RoadRecord.writeNode(hint.node());
    t.putBoolean("HeadingLocked", hint.headingLocked());
    t.putBoolean("GradeLocked", hint.gradeLocked());
    t.putBoolean("Linked", hint.linked());
    return t;
  }

  public static RoadPlanner.Hint readHint(CompoundTag t) {
    return new RoadPlanner.Hint(
        RoadRecord.readNode(t),
        t.getBoolean("HeadingLocked"),
        t.getBoolean("GradeLocked"),
        t.getBoolean("Linked"));
  }

  public double inheritedWidth(BlockPos a, BlockPos b, double fallback, UUID exclude) {
    for (var r : index.roads.values())
      if (!r.record.id().equals(exclude)
          && (r.record.a().equals(a)
              || r.record.b().equals(a)
              || r.record.a().equals(b)
              || r.record.b().equals(b))) return r.record.settings().width();
    return fallback;
  }

  public void editNode(ServerLevel level, ServerPlayer player, BlockPos pos, Node node) {
    try (var timing=RoadTimings.start("edit_node",index.roads.size(),index.atNode(pos).size());
         var workChunks = RoadWorkChunks.open(level)) {
      if (index.atNode(pos).stream().anyMatch(id -> index.roads.get(id).record.assembly() != null))
        throw new IllegalArgumentException("路口或立交的端点请使用对应连接器整体编辑，或先删除该整体");
      NodeEntity entity = requireNode(level, pos, player);
      V p = node.position();
      if (!RoadGeometry.finite(p.x(), p.y(), p.z(), node.yaw(), node.grade())
          || Math.abs(p.x() - pos.getX() - .5) > 8
          || Math.abs(p.z() - pos.getZ() - .5) > 8
          || p.y() - pos.getY() < -8
          || p.y() - pos.getY() > 16
          || Math.abs(node.grade()) > .5)
        throw new IllegalArgumentException("端点偏移 X/Z：±8，Y：-8–16；坡度：±50%");
      List<RoadIndex.Built> replacements = new ArrayList<>();
      Set<UUID> ids = new HashSet<>();
      for (var built : index.roads.values()) {
        RoadRecord r = built.record;
        if (r.a().equals(pos) || r.b().equals(pos)) {
          if (player != null) requireOwner(player, r.owner());
          ids.add(r.id());
          replacements.add(
              new RoadIndex.Built(
                  planNodeEdit(
                      r,
                      pos,
                      node,
                      entity.node(),
                      hint(level, r.a(), true, r.id()),
                      hint(level, r.b(), false, r.id()))));
        }
      }
      replaceBatch(level, player, replacements, ids);
      if (Math.abs(node.yaw() - entity.yaw) > 1e-7) entity.headingLocked = true;
      if (Math.abs(node.grade() - entity.grade) > 1e-7) entity.gradeLocked = true;
      if (Math.abs(node.position().y() - entity.node().position().y()) > 1e-8)
        entity.heightExplicit = true;
      entity.apply(node);
    }
  }

  /** Shared with the client: edits use exactly the same endpoint constraints as the preview. */
  public static RoadRecord planNodeEdit(
      RoadRecord r,
      BlockPos pos,
      Node edited,
      Node original,
      RoadPlanner.Hint ha,
      RoadPlanner.Hint hb) {
    Node aa = r.start(), bb = r.end();
    Settings settings = r.settings();
 if (r.automatic()) {
      if (r.a().equals(pos)) ha = editedHint(ha, edited, original);
      if (r.b().equals(pos)) hb = editedHint(hb, edited, original);
      var plan =
          RoadPlanner.plan(ha, hb, RoadPlanner.mode(settings, Mode.AUTO, settings.arcDegrees()));
      aa = plan.start();
      bb = plan.end();
      settings = plan.settings();
    } else {
      if (r.a().equals(pos))
        aa = editedHint(new RoadPlanner.Hint(aa, true, true, true), edited, original).node();
      if (r.b().equals(pos))
        bb = editedHint(new RoadPlanner.Hint(bb, true, true, true), edited, original).node();
    }
    return new RoadRecord(
        r.id(), r.owner(), r.a(), r.b(), aa, bb, settings, r.automatic(), r.clearance());
  }

  private static RoadPlanner.Hint editedHint(RoadPlanner.Hint hint, Node edited, Node original) {
    boolean yawChanged = Math.abs(edited.yaw() - original.yaw()) > 1e-7,
        gradeChanged = Math.abs(edited.grade() - original.grade()) > 1e-7;
    double sign = hint.node().direction().dot(original.direction()) < 0 ? -1 : 1;
    V offset = hint.node().position().sub(original.position());
    double angle = Math.toRadians(edited.yaw() - original.yaw());
    V rotated =
        new V(
            offset.x() * Math.cos(angle) - offset.z() * Math.sin(angle),
            offset.y(),
            offset.x() * Math.sin(angle) + offset.z() * Math.cos(angle));
    Node n =
        new Node(
            edited.position().add(rotated),
            hint.node().yaw() + (yawChanged ? edited.yaw() - original.yaw() : 0),
            gradeChanged ? sign * edited.grade() : hint.node().grade());
    return new RoadPlanner.Hint(
        n, hint.headingLocked() || yawChanged, hint.gradeLocked() || gradeChanged, hint.linked());
  }

  public void remove(ServerLevel level, ServerPlayer player, UUID id) {
    var road = index.roads.get(id);
    if (road == null) throw new IllegalArgumentException("道路已经不存在");
    if (player != null) requireOwner(player, road.record.owner());
    if(streets.containsKey(id)&&road.record.assembly()==null){AutoJunctions.removeStreet(level,player,id);return;}
    if (road.record.junction() != null) { Junctions.remove(level,player,road.record.assembly()); return; }
    if (road.record.assembly() != null) {
      if(interchanges.getOrDefault(road.record.assembly(),new CompoundTag()).getBoolean("YJunction")){Interchanges.remove(level,player,road.record.assembly());return;}
      Interchanges.removePart(level, player, id);
      return;
    }
    replaceBatch(level, player, List.of(), Set.of(id));
  }

  public void updatePointMetadata(ServerLevel level,ServerPlayer player,RoadRecord r,RoadAttachments.Data value){
    if(player!=null){requireOwner(player,r.owner());for(var p:value.points())if(!level.mayInteract(player,BlockPos.containing(p.position().x(),p.position().y(),p.position().z())))throw new IllegalArgumentException("附属点位于受保护区域");}
    var next=r.withAttachments(value);
    index.put(RoadIndex.Built.loading(next));if(streets.containsKey(r.id()))streets.put(r.id(),streets.get(r.id()).settings(next.settings()));setDirty();RoadNetwork.broadcastRoad(level,next);
  }

  void removeAttachedPoint(ServerLevel level,ServerPlayer player,RoadRecord road,RoadAttachments.Data value,UUID point){
    var next=road.withAttachments(value).structures(List.of());var proposed=new RoadIndex.Built(next);
    for(var other:index.roads.values())if(!other.record.id().equals(road.id())&&RoadIndex.overlapXZ(proposed.mesh,other.mesh,1))Interchanges.checkExternal(proposed.mesh,other.mesh,road.mesh());
    replaceBatch(level,player,List.of(proposed),Set.of(road.id()),true,Set.of(road.a(),road.b()),List.of(),RoadLimits.MAX_EDIT_CELLS,Set.of(point));
  }

  /** Unreferenced lane markers are metadata, not a road construction transaction. */
  void updateLanePointMetadata(ServerLevel level, ServerPlayer player, RoadRecord r, LanePoints.Data value) {
    value=value.points(java.util.stream.Stream.concat(value.points().stream().filter(p->!p.automatic()),value.points().stream().filter(LanePoints.Point::automatic)).toList());
    if (player != null) {
      requireOwner(player, r.owner());
      if (!player.mayBuild() || player.isSpectator()) throw new IllegalArgumentException("没有建设权限");
      for (var point : value.points())
        if (!level.mayInteract(player, BlockPos.containing(point.position().x(), point.position().y(), point.position().z())))
          throw new IllegalArgumentException("车道点位于受保护区域");
    }
    var before = LaneTopology.metadata(r);
    for (var point : before.points()) {
      var next = value.points().stream().filter(p -> p.id().equals(point.id())).findFirst().orElse(null);
      if (!point.equals(next) && !LaneTopology.references(index.roads.values().stream().map(b -> b.record).toList(), LanePoints.Ref.lane(r.id(), point.id())).isEmpty())
        throw new IllegalArgumentException("已使用的车道点必须同步重建依赖匝道");
    }
    var next = index.lanePoints(r.id(), value).record;
    if (streets.containsKey(r.id())) streets.put(r.id(), streets.get(r.id()).withLanePoints(value));
    setDirty();
    RoadNetwork.broadcastLanePoints(level, r, next);
  }

  public void editLaneLine(ServerLevel level,ServerPlayer player,CompoundTag t){
    var built=index.roads.get(t.getUUID("Id"));if(built==null||built.record.junction()!=null)throw new IllegalArgumentException("路段已改变，请重新选择");
    var r=built.record;if(player!=null)requireOwner(player,r.owner());
    if(t.getInt("Signature")!=GantryTool.signature(r))throw new IllegalArgumentException("道路已更新，请重新选择");
    var options=r.settings().options();
    if(t.contains("Station")){
      double station=t.getDouble("Station");if(!Double.isFinite(station)||station<0||station>built.mesh.length())throw new IllegalArgumentException("标线区间无效");
      var range=RoadAttachments.range(built.mesh,station);var paint=RoadAttachments.paint(built.mesh,station);
      if(t.contains("HideArrows"))paint=new RoadAttachments.Paint(paint.lines(),t.getBoolean("HideArrows"));
      else {String key=t.getString("Key");if(RoadLaneLines.lines(built.mesh).stream().noneMatch(l->l.key().equals(key)))throw new IllegalArgumentException("这根车道线已不存在");paint=new RoadAttachments.Paint(RoadLaneLines.with(paint.lines(),new RoadLaneLines.Edit(key,RoadLaneLines.Pattern.valueOf(t.getString("Pattern")),t.getDouble("Width"))),paint.hideArrows());}
      updatePointMetadata(level,player,r,RoadAttachments.edit(built.mesh,range,paint));return;
    }
    if(!options.attachments().points().isEmpty())throw new IllegalArgumentException("请重新选择标线编辑区间");
    if(t.contains("HideArrows"))options=options.hideArrows(t.getBoolean("HideArrows"));
    else {
      String key=t.getString("Key");if(RoadLaneLines.lines(built.mesh).stream().noneMatch(l->l.key().equals(key)))throw new IllegalArgumentException("这根车道线已不存在");
      var e=new RoadLaneLines.Edit(key,RoadLaneLines.Pattern.valueOf(t.getString("Pattern")),t.getDouble("Width"));
      options=options.laneLines(RoadLaneLines.with(options.laneLines(),e));
    }
    var settings=r.settings().options(options);
    // Paint-only edit: preserve every stored sample, support, neighboring record and world block.
    var next=new RoadRecord(r.id(),r.owner(),r.a(),r.b(),r.start(),r.end(),settings,r.automatic(),r.clearance(),r.structures(),r.endCaps(),r.buildVersion(),r.assembly(),r.alignment(),r.furniturePhase(),r.junction());
    index.put(RoadIndex.Built.loading(next));if(streets.containsKey(r.id()))streets.put(r.id(),streets.get(r.id()).settings(settings));setDirty();RoadNetwork.broadcastRoad(level,next);
  }

  public void editGantry(ServerLevel level,ServerPlayer player,CompoundTag command) {
    var built=index.roads.get(command.getUUID("Id"));
    if(built==null||built.record.junction()!=null)throw new IllegalArgumentException("道路已改变，请重新选择龙门架");
    var r=built.record;if(player!=null)requireOwner(player,r.owner());
    if(command.getInt("Signature")!=GantryTool.signature(r))throw new IllegalArgumentException("道路已更新，请重新打开龙门架编辑器");
    int slot=command.getInt("Slot");RoadGantry.station(built.mesh,slot);
    var c=r.settings().options().infrastructure();
    c=command.getBoolean("Reset")?c.reset(slot):c.edit(new RoadGantry.Edit(slot,command.getDouble("Offset"),command.getDouble("Clearance"),RoadInfrastructure.Gantry.valueOf(command.getString("Style")),command.getBoolean("Reverse")));
    var settings=r.settings().options(r.settings().options().infrastructure(c));
    var next=new RoadRecord(r.id(),r.owner(),r.a(),r.b(),r.start(),r.end(),settings,r.automatic(),r.clearance(),List.of(),r.endCaps(),r.buildVersion(),r.assembly(),r.alignment(),r.furniturePhase(),r.junction());
    replaceBatch(level,player,List.of(new RoadIndex.Built(next)),Set.of(r.id()));
  }

  public void editSign(ServerLevel level,ServerPlayer player,CompoundTag command){
    var built=index.roads.get(command.getUUID("Id"));if(built==null||built.record.junction()!=null)throw new IllegalArgumentException("道路已改变，请重新选择路牌");
    var r=built.record;if(player!=null){requireOwner(player,r.owner());if(!player.mayBuild()||player.isSpectator())throw new IllegalArgumentException("没有建设权限");}
    if(command.getInt("Signature")!=GantryTool.signature(r))throw new IllegalArgumentException("道路已更新，请重新打开路牌编辑器");
    var a=RoadSignCodec.read(command.getCompound("Attachment"));var c=r.settings().options().infrastructure();
    if(player!=null){var sample=RoadStructures.sample(built.mesh,a.mount()==RoadSigns.Mount.GANTRY?RoadGantry.station(built.mesh,a.station()).distance():a.position()*built.mesh.length());if(player.position().distanceToSqr(new net.minecraft.world.phys.Vec3(sample.center().x(),sample.center().y(),sample.center().z()))>256*256)throw new IllegalArgumentException("离所选路牌太远");}
    var signs=new ArrayList<>(c.signs());boolean existed=signs.removeIf(v->v.id()==a.id());
    if(command.getBoolean("New")==existed)throw new IllegalArgumentException("路牌选择已失效，请重新打开");
    if(!command.getBoolean("Delete")){
      if(a.mount()==RoadSigns.Mount.GANTRY){var st=RoadGantry.station(built.mesh,a.station());if(st.kind()==RoadInfrastructure.Gantry.OFF||st.kind()==RoadInfrastructure.Gantry.AUTO)c=c.edit(new RoadGantry.Edit(st.slot(),st.edit().offset(),st.edit().clearance(),RoadInfrastructure.Gantry.FRAME,st.edit().reverse()));}
      signs.add(a);
    }
    c=c.signs(signs);var settings=r.settings().options(r.settings().options().infrastructure(c));
    var next=new RoadRecord(r.id(),r.owner(),r.a(),r.b(),r.start(),r.end(),settings,r.automatic(),r.clearance(),List.of(),r.endCaps(),r.buildVersion(),r.assembly(),r.alignment(),r.furniturePhase(),r.junction());
    replaceBatch(level,player,List.of(new RoadIndex.Built(next)),Set.of(r.id()));
  }

  private void checkJoints(List<RoadIndex.Built> replacements, Set<UUID> removed) {
    List<RoadIndex.Built> all = new ArrayList<>();
    for (var r : index.roads.values()) if (!removed.contains(r.record.id())) all.add(r);
    all.addAll(replacements);
    Set<BlockPos> checked = new HashSet<>();
    for (var road : replacements)
      for (BlockPos p : List.of(road.record.a(), road.record.b())) {
        if (!checked.add(p)) continue;
        var links =
            all.stream().filter(r -> r.record.a().equals(p) || r.record.b().equals(p)).toList();
        // Automatic approaches share a logical center marker but have different trimmed
        // physical endpoints. The junction planner owns those seams, not this point check.
        if(links.stream().anyMatch(r->r.record.junction()!=null))continue;
        links=links.stream().filter(r->transitionEndpoint(r,p)).toList();
        UUID sameGroup = links.isEmpty() ? null : links.get(0).record.assembly();
        if (sameGroup != null
            && links.stream().allMatch(r -> sameGroup.equals(r.record.assembly()))) continue;
        long laneRamps = links.stream().filter(r -> r.record.settings().laneRamp()).count();
        if (laneRamps > 16) throw new IllegalArgumentException("同一端点最多连接 16 条匝道，请分组增加端点");
        links = links.stream().filter(r -> !r.record.settings().laneRamp()).toList();
        if (links.size() > 3) throw new IllegalArgumentException("一个端点支持主路和两条支路；更多分叉请增加端点");
        if (links.size() < 2) continue;
        var rings = links.stream().filter(r -> r.mesh.closed()).toList();
        if (!rings.isEmpty()) {
          for (var ring : rings)
            for (var feeder : links) {
              if (feeder == ring) continue;
              V point =
                  (feeder.record.a().equals(p) ? feeder.mesh.first() : feeder.mesh.last()).center();
              var q = RoadQueries.project(ring.mesh, point);
              if (q.horizontalDistance() > .05
                  || Math.abs(point.y() - q.sample().center().y()) > .01)
                throw new IllegalArgumentException("接环道路必须与环道路面等高并接在环道上");
            }
          continue;
        }
        boolean ramp = links.stream().anyMatch(r -> r.record.settings().style().ramp());
        if (links.size() == 3 && !ramp) throw new IllegalArgumentException("Y 字分叉请选择单车道或双车道匝道样式");
        var first = links.get(0);
        V reference = away(first, p);
        var origin = first.record.a().equals(p) ? first.mesh.first() : first.mesh.last();
        boolean opposite = false;
        for (int i = 1; i < links.size(); i++) {
          var r = links.get(i);
          V d = away(r, p);
          double dot = reference.horizontalUnit().dot(d.horizontalUnit());
          double sign = dot < 0 ? -1 : 1;
          var sample = r.record.a().equals(p) ? r.mesh.first() : r.mesh.last();
          if (first.record.junction() == null && r.record.junction() == null)
            RoadTransitions.requireCompatible(endpointSection(first,p), RoadEndpointSections.orient(endpointSection(r,p),origin.left().dot(sample.left())<0));
          if (Math.abs(origin.center().y()-sample.center().y()) > 1e-6)
            throw new IllegalArgumentException(String.format(java.util.Locale.ROOT,"接点高程不一致：两侧实际路面 Y=%.3f / %.3f（差 %.3f 格），请检查接头高程",origin.center().y(),sample.center().y(),Math.abs(origin.center().y()-sample.center().y())));
          if (origin.center().sub(sample.center()).horizontalLength() > 1e-6)
            throw new IllegalArgumentException(String.format(java.util.Locale.ROOT,"接点横向断面未对齐（并非高程不同）：两侧中心偏移 %.3f 格；合并后的实际断面需要续接匹配",origin.center().sub(sample.center()).horizontalLength()));
          if (Math.abs(origin.halfWidth() - sample.halfWidth()) > 1e-6)
            throw new IllegalArgumentException("接缝过渡宽度不一致，请重新连接此接点");
          if (Math.abs(dot) < .99999999
              || (!ramp && dot > 0)
              || Math.abs(d.y() - sign * reference.y()) > 1e-5)
            throw new IllegalArgumentException("接缝方向或坡度不连续，请切回智能模式或调整端点");
          if (dot < 0) opposite = true;
        }
        if (links.size() == 3 && !opposite)
          throw new IllegalArgumentException("Y 字节点需要一条主路和两条支路，不能三条都朝同侧");
      }
  }

  private static V away(RoadIndex.Built b, BlockPos p) {
    boolean first = b.record.a().equals(p);
    var sample = first ? b.mesh.first() : b.mesh.last();
    V forward = sample.left().left().mul(-1);
    double grade = first ? b.record.start().grade() : b.record.end().grade();
    return new V(forward.x(), grade, forward.z()).mul(first ? 1 : -1);
  }

  /** Validates every affected cell before writing any block or index entry. */
  private void replaceBatch(
      ServerLevel level, ServerPlayer player, List<RoadIndex.Built> built, Set<UUID> removed) {
    replaceBatch(level, player, built, removed, false, Set.of(), List.of());
  }

  /** Rebuild both sides in the same transaction; no stale taper after an edit or deletion. */
  private void normalizeTransitions(
      List<RoadIndex.Built> built, Set<UUID> removed, ServerPlayer player, boolean deleting) {
    Map<UUID, RoadIndex.Built> all = new LinkedHashMap<>(index.roads);
    Set<BlockPos> affected = new HashSet<>();
    for (UUID id : removed) {
      var old = all.remove(id);
      if (old != null) {
        affected.add(old.record.a());
        affected.add(old.record.b());
      }
    }
    for (var r : built) {
      all.put(r.record.id(), r);
      affected.add(r.record.a());
      affected.add(r.record.b());
    }
    Map<BlockPos, List<RoadIndex.Built>> joints = new HashMap<>();
    for (var r : all.values()) {
      if (r.record.settings().style().ramp() || r.mesh.closed()) continue;
      for (BlockPos pos : List.of(r.record.a(), r.record.b()))
        if(transitionEndpoint(r,pos))joints.computeIfAbsent(pos, p -> new ArrayList<>()).add(r);
    }
    for (var r : all.values()) {
      var record = r.record;
      if (record.assembly() != null
          || record.settings().style().ramp()
          || r.mesh.closed()
          || !RoadProfile.modern(record.settings().style())
          || !affected.contains(record.a()) && !affected.contains(record.b())) continue;
      var a = affected.contains(record.a()) && transitionEndpoint(r,record.a()) ? jointSection(r, record.a(), joints.getOrDefault(record.a(), List.of()),deleting) : record.settings().options().ends().start();
      var b = affected.contains(record.b()) && transitionEndpoint(r,record.b()) ? jointSection(r, record.b(), joints.getOrDefault(record.b(), List.of()),deleting) : record.settings().options().ends().end();
      Settings s = RoadTransitions.ends(record.settings(), a, b);
      if (s.equals(record.settings())) continue;
      if (player != null) requireOwner(player, record.owner());
      // User-created mainlines are resampled from their saved curve; assembly alignments stay
      // fixed.
      var next = new RoadIndex.Built(record.settings(s));
      int position = -1;
      for (int i = 0; i < built.size(); i++)
        if (built.get(i).record.id().equals(record.id())) position = i;
      if (position >= 0) built.set(position, next);
      else built.add(next);
      if (index.roads.containsKey(record.id())) removed.add(record.id());
    }
  }

  private static RoadTransitions.Section jointSection(
      RoadIndex.Built road, BlockPos pos, List<RoadIndex.Built> links, boolean deleting) {
    boolean first=road.record.a().equals(pos);
    if(RoadEndpointSections.changed(road.mesh,first))return first?road.record.settings().options().ends().start():road.record.settings().options().ends().end();
    var fixed=links.stream().filter(r->!r.record.id().equals(road.record.id())&&(r.record.assembly()!=null||RoadEndpointSections.changed(r.mesh,r.record.a().equals(pos)))).findFirst();
    if(fixed.isPresent()){
      Settings target=RoadEndpointSections.orient(endpointSection(fixed.get(),pos),(fixed.get().record.a().equals(pos)?fixed.get().mesh.first():fixed.get().mesh.last()).left().dot((first?road.mesh.first():road.mesh.last()).left())<0);if(deleting&&!RoadTransitions.compatible(road.record.settings(),target))return null;RoadTransitions.requireCompatible(road.record.settings(),target);
      return RoadTransitions.Section.of(target);
    }
    RoadTransitions.Section section = null;
    for (RoadIndex.Built other : links) {
      if (other.record.id().equals(road.record.id())) continue;
      if(deleting&&!RoadTransitions.compatible(road.record.settings(),other.record.settings()))continue;
      var next = RoadTransitions.common(authoredSection(road.record.settings()), RoadEndpointSections.orient(authoredSection(other.record.settings()),(other.record.a().equals(pos)?other.mesh.first():other.mesh.last()).left().dot((first?road.mesh.first():road.mesh.last()).left())<0));
      section =
          section == null
              ? next
              : RoadTransitions.common(section.settings(false), next.settings(false));
    }
    return section;
  }

  void replaceAssembly(
      ServerLevel level,
      ServerPlayer player,
      List<RoadIndex.Built> built,
      Set<UUID> removed,
      Set<BlockPos> selectedNodes) {
    replaceAssembly(level, player, built, removed, selectedNodes, List.of());
  }

  public record NodeMove(BlockPos from, BlockPos to, Node target, CompoundTag snapshot) {
    static NodeMove retire(BlockPos from){return new NodeMove(from,null,null,null);}
  }

  void replaceAssembly(
      ServerLevel level,
      ServerPlayer player,
      List<RoadIndex.Built> built,
      Set<UUID> removed,
      Set<BlockPos> selectedNodes,
      List<NodeMove> moves) {
    replaceBatch(level, player, built, removed, true, selectedNodes, moves);
  }

  /** A complete five/six-way layout may exceed a normal road edit; all checks remain atomic. */
  void replaceAssembly(ServerLevel level,ServerPlayer player,List<RoadIndex.Built> built,
      Set<UUID> removed,Set<BlockPos> selectedNodes,List<NodeMove> moves,int limit){
    if(limit<RoadLimits.MAX_EDIT_CELLS||limit>RoadLimits.MAX_MULTI_INTERCHANGE_EDIT_CELLS)throw new IllegalArgumentException("修改范围上限无效");
    replaceBatch(level,player,built,removed,true,selectedNodes,moves,limit);
  }
  private void replaceBatch(ServerLevel level,ServerPlayer player,List<RoadIndex.Built> built,
      Set<UUID> removed,boolean assembly,Set<BlockPos> selectedNodes,List<NodeMove> moves){
    replaceBatch(level,player,built,removed,assembly,selectedNodes,moves,RoadLimits.MAX_EDIT_CELLS);
  }
  private void replaceBatch(
      ServerLevel level,
      ServerPlayer player,
      List<RoadIndex.Built> built,
      Set<UUID> removed,
      boolean assembly,
      Set<BlockPos> selectedNodes,
      List<NodeMove> moves,int editCellLimit) {
    replaceBatch(level,player,built,removed,assembly,selectedNodes,moves,editCellLimit,Set.of());
  }
  private void replaceBatch(ServerLevel level,ServerPlayer player,List<RoadIndex.Built> built,Set<UUID> removed,boolean assembly,Set<BlockPos> selectedNodes,List<NodeMove> moves,int editCellLimit,Set<UUID> deletedPoints) {
    replaceBatch(level,player,built,removed,assembly,selectedNodes,moves,editCellLimit,deletedPoints,null);
  }
  List<RoadIndex.Built> previewAssembly(ServerLevel level,ServerPlayer player,List<RoadIndex.Built> built,
      Set<UUID> removed,Set<BlockPos> selectedNodes,List<NodeMove> moves){
    var result=new ArrayList<RoadIndex.Built>();
    replaceBatch(level,player,built,removed,true,selectedNodes,moves,RoadLimits.MAX_EDIT_CELLS,Set.of(),result);
    return List.copyOf(result);
  }
  private void replaceBatch(ServerLevel level,ServerPlayer player,List<RoadIndex.Built> built,Set<UUID> removed,boolean assembly,Set<BlockPos> selectedNodes,List<NodeMove> moves,int editCellLimit,Set<UUID> deletedPoints,List<RoadIndex.Built> previewResult) {
    try (var budget=com.sora.splineroads.core.RoadPlanningBudget.cancellable("道路事务预检查");
         var timing=RoadTimings.start("edit",index.roads.size(),built.size());
         var workChunks = RoadWorkChunks.open(level)) {
      boolean deleting=built.isEmpty();
      built = new ArrayList<>(built);
      removed = new HashSet<>(removed);
      removed.addAll(confirmedLaneDeletes);
      built.removeIf(b->confirmedLaneDeletes.contains(b.record.id()));
      if(built.stream().noneMatch(r->r.record.junction()!=null))normalizeTransitions(built, removed, player,deleting);
      timing.stage("normalize_transitions");com.sora.splineroads.core.RoadPlanningBudget.phase("normalize_transitions");
      List<RoadIndex.Built> requested = new ArrayList<>(built);
      if(!deleting)for(var old:index.roads.values())if(!removed.contains(old.record.id())&&LaneTopology.metadata(old.record).link()!=null)
        for(var proposed:built)RoadInteractions.influences(proposed,old);
      for (UUID id : removed) if (index.roads.containsKey(id)) requested.add(index.roads.get(id));
      // Re-plan neighboring elevated structures when adding a ground road or a junction.
      for (var old : index.roads.values())
        if (!removed.contains(old.record.id())
            && LaneTopology.metadata(old.record).link()==null
            && requested.stream().anyMatch(r -> RoadInteractions.influences(r, old))) {
          built.add(old.structures(List.of()));
          removed.add(old.record.id());
        }
      // Central junction supports have one shared owner across its fill triangles.
      // If a lower road changes any part of the pad, rebuild the complete junction.
      var supportGroups=new HashSet<UUID>();
      for(var r:built)if(r.record.junction()!=null)supportGroups.add(r.record.assembly());
      for(var old:index.roads.values())if(old.record.junction()!=null&&supportGroups.contains(old.record.assembly())&&!removed.contains(old.record.id())&&built.stream().noneMatch(r->r.record.id().equals(old.record.id()))){built.add(old.structures(List.of()));removed.add(old.record.id());}
      timing.stage("affected_structures");com.sora.splineroads.core.RoadPlanningBudget.phase("affected_structures");
      if(!deleting)AttachedPoints.reconcile(this,built,removed,false,deletedPoints);
      timing.stage("attached_points");com.sora.splineroads.core.RoadPlanningBudget.phase("attached_points");
      if(deleting)LaneTopology.reconcileDeletion(this,built,removed);else LaneTopology.reconcile(this,built,removed);
      timing.stage("topology");com.sora.splineroads.core.RoadPlanningBudget.phase("topology");
      built.sort(Comparator.comparing(r -> r.record.id()));
      List<RoadIndex.Built> planning = new ArrayList<>();
      for (var old : index.roads.values())
        if (!removed.contains(old.record.id())) planning.add(old);
      planning.addAll(built);
      for (int i = 0; i < built.size(); i++) {
        var old = built.get(i);
        int mask = 0;
        if (!old.mesh.closed() && !old.record.settings().style().ramp() && LaneTopology.metadata(old.record).link()==null) {
          for (boolean first : new boolean[] {true, false}) {
            BlockPos pos = first ? old.record.a() : old.record.b();
            boolean shared =
                planning.stream()
                    .anyMatch(
                        r ->
                            !r.record.id().equals(old.record.id())
                                && LaneTopology.metadata(r.record).link()==null
                                && (r.record.a().equals(pos) || r.record.b().equals(pos)));
            if (!shared) mask |= first ? 1 : 2;
          }
        }
        var next =
            old.record.endCaps() == mask && old.record.buildVersion() == 17 ? old : old.caps(mask);
        built.set(i, next);
        planning.set(planning.indexOf(old), next);
      }
      List<RoadIndex.Built> terrainRoads = new ArrayList<>(built);
      for (UUID id : removed)
        if (index.roads.containsKey(id)) terrainRoads.add(index.roads.get(id));
      boolean largeAssembly=terrainRoads.stream().anyMatch(r->{
        var descriptor=r.record.assembly()==null?null:interchanges.get(r.record.assembly());
        return descriptor!=null&&descriptor.getLongArray("Points").length>=5;
      });
      if(largeAssembly){workChunks.multiInterchange();editCellLimit=Math.max(editCellLimit,RoadLimits.MAX_MULTI_INTERCHANGE_EDIT_CELLS);}
      timing.stage("caps_and_dependencies");com.sora.splineroads.core.RoadPlanningBudget.phase("caps_and_dependencies");
      workChunks.roads(terrainRoads);
      timing.stage("terrain_chunk_access");com.sora.splineroads.core.RoadPlanningBudget.phase("terrain_chunk_access");
      Map<BlockPos, List<net.minecraft.world.phys.AABB>> terrainCache = new HashMap<>();
      // Keep saved origins; anchor newly added sections to the already-built connected road.
      List<RoadIndex.Built> spacingReferences = new ArrayList<>(index.roads.values());
      for (int i = 0; i < built.size(); i++) {
        var r = built.get(i);
        var next = r.phase(FurnitureSpacing.resolve(r.record, r.mesh, spacingReferences));
        built.set(i, next);
        planning.set(planning.indexOf(r), next);
        spacingReferences.removeIf(o -> o.record.id().equals(r.record.id()));
        spacingReferences.add(next);
      }
      timing.stage("furniture_phase");com.sora.splineroads.core.RoadPlanningBudget.phase("furniture_phase");
      var structureLookup=new RoadPlanningIndex(planning,built.size());
      timing.stage("structure_candidate_index");com.sora.splineroads.core.RoadPlanningBudget.phase("structure_candidate_index");
      for (int i = 0; i < built.size(); i++) {
        var r = built.get(i);
        RoadRecord planned = StructurePlanner.plan(level, r, planning, terrainFill, terrainOriginal, terrainCache,structureLookup);
        var next = r.planned(planned);
        built.set(i, next);
        int slot=planning.indexOf(r);planning.set(slot,next);structureLookup.replace(slot,next);
      }
      timing.stage("structure_plan");com.sora.splineroads.core.RoadPlanningBudget.phase("structure_plan");
      // Terrain-derived raised medians can change a lane center after planning. Resolve
      // dependent ports again before any world write; never save an off-center marker.
      for(int pass=0;!deleting&&(LaneTopology.needsRefresh(this,planning,built.stream().map(b->b.record.id()).toList())||LaneCrossSections.needsRestoreRefresh(planning,built.stream().map(b->b.record.id()).toList()));pass++){
        if(pass>=3)throw new IllegalArgumentException("断面与车道点未能稳定，请调整道路样式后重试");
        LaneTopology.reconcile(this,built,removed);
        planning.clear();for(var old:index.roads.values())if(!removed.contains(old.record.id()))planning.add(old);planning.addAll(built);
        structureLookup=new RoadPlanningIndex(planning,built.size());
        for(int i=0;i<built.size();i++){var r=built.get(i);var next=r.planned(StructurePlanner.plan(level,r,planning,terrainFill,terrainOriginal,terrainCache,structureLookup));built.set(i,next);int slot=planning.indexOf(r);planning.set(slot,next);structureLookup.replace(slot,next);}
      }
      timing.stage("dependent_replanning");com.sora.splineroads.core.RoadPlanningBudget.phase("dependent_replanning");
      var noseCaps =
          RoadNoses.connectors(
              built.stream().map(r -> r.mesh).toList(),
              built.stream().map(r -> r.record.structures()).toList());
      for (var entry : noseCaps.entrySet()) {
        var old = built.get(entry.getKey());
        var parts = new ArrayList<>(old.record.structures());
        parts.addAll(entry.getValue());
        built.set(entry.getKey(), old.structures(parts));
      }
      workChunks.roads(built);
      // Check the FINAL list (nose caps may have replaced entries since planning).
      // Replaying an unchanged saved conflict must not prevent removing an unrelated road.
      var shellFinal=new ArrayList<RoadIndex.Built>();
      for(var old:index.roads.values())if(!removed.contains(old.record.id()))shellFinal.add(old);
      shellFinal.addAll(built);
      var shellChanged=new HashSet<UUID>();for(var changed:built)shellChanged.add(changed.record.id());
      if(!deleting)TunnelShellValidation.check(shellFinal,shellChanged,index.roads);
      if(!deleting)checkJoints(built, removed);
      timing.stage("caps_shell_and_joint_validation");com.sora.splineroads.core.RoadPlanningBudget.phase("caps_shell_and_joint_validation");
      final List<RoadIndex.Built> committed = built;
      final Set<UUID> removedIds = removed;
      Set<Long> touched = new it.unimi.dsi.fastutil.longs.LongOpenHashSet();
      int scanLimit=editCellLimit==RoadLimits.MAX_MULTI_INTERCHANGE_EDIT_CELLS?RoadLimits.MAX_MULTI_INTERCHANGE_SCAN_CELLS:editCellLimit;
      for (UUID id : removed) {
        var old = index.roads.get(id);
        if (old != null) {
          touched.addAll(old.cells.keySet());
          touched.addAll(old.clearanceCells);
        }
      }
      for (var r : built) {
        touched.addAll(r.cells.keySet());
        touched.addAll(r.clearanceCells);
      }
      Set<Long> moveSources = new HashSet<>(), moveTargets = new HashSet<>();
      for (var move : moves) {
        if (move.from() != null) moveSources.add(move.from().asLong());
        if (move.from() != null) touched.add(move.from().asLong());
        if(move.to()!=null){
          if (!moveTargets.add(move.to().asLong())) throw new IllegalArgumentException("端点目标重叠");
          touched.add(move.to().asLong());
        }
      }
      if (touched.size() > scanLimit)
        throw new IllegalArgumentException("一次修改范围过大，请拆成多段道路");
      List<RoadIndex.Built> finalRoads = new ArrayList<>();
      for (var r : index.roads.values()) if (!removed.contains(r.record.id())) finalRoads.add(r);
      finalRoads.addAll(built);
      timing.stage("edit_raster");com.sora.splineroads.core.RoadPlanningBudget.phase("edit_raster");
      Map<Long,BlockState> sidewalks=SmartSidewalks.plan(finalRoads,level,terrainFill,sidewalkPlaced,built,terrainRoads);
      for(var e:sidewalkPlaced.entrySet())if(!e.getValue().equals(sidewalks.get(e.getKey())))touched.add(e.getKey());
      for(var e:sidewalks.entrySet())if(!e.getValue().equals(sidewalkPlaced.get(e.getKey())))touched.add(e.getKey());
      if(touched.size()>scanLimit)throw new IllegalArgumentException("包含人行道的修改范围过大，请分段建造");
      timing.stage("sidewalks");com.sora.splineroads.core.RoadPlanningBudget.phase("sidewalks");
      Map<Long, List<RoadIndex.Built>> body = new HashMap<>();
      Set<Long> air = new it.unimi.dsi.fastutil.longs.LongOpenHashSet();
      Set<Long> dry = new it.unimi.dsi.fastutil.longs.LongOpenHashSet();
      Set<Long> touchedChunks = new HashSet<>();
      for (long key : touched)
        touchedChunks.add(new net.minecraft.world.level.ChunkPos(BlockPos.of(key)).toLong());
      workChunks.load(touchedChunks);
      Map<Long,List<BlockPos>> touchedByChunk=new HashMap<>();
      Map<Long,List<RoadIndex.Built>> finalByChunk=new HashMap<>();
      for(long key:touched){var pos=BlockPos.of(key);touchedByChunk.computeIfAbsent(net.minecraft.world.level.ChunkPos.asLong(pos.getX()>>4,pos.getZ()>>4),k->new ArrayList<>()).add(pos);}
      for (var r : finalRoads) {
        if (Collections.disjoint(r.chunks, touchedChunks)) continue;
        for(long chunk:r.chunks)if(touchedChunks.contains(chunk))finalByChunk.computeIfAbsent(chunk,k->new ArrayList<>()).add(r);
        if(r.rasterized()) {
          for (long key : r.cells.keySet())if (touched.contains(key))body.computeIfAbsent(key,k->new ArrayList<>()).add(r);
          for (long key : r.clearanceCells)if(touched.contains(key)){air.add(key);if(r.tunnelAt(BlockPos.of(key)))dry.add(key);}
        } else {
          // A short edit next to a kilometres-long unchanged road must not materialize its
          // entire deck, furniture and clearance volume. Query only touched local columns.
          for(long chunk:r.chunks)for(var pos:touchedByChunk.getOrDefault(chunk,List.of())) {
            long key=pos.asLong();
            if(!r.boxes(pos).isEmpty())body.computeIfAbsent(key,k->new ArrayList<>()).add(r);
            if(r.clearanceAt(pos)){air.add(key);if(r.tunnelAt(BlockPos.of(key)))dry.add(key);}
          }
        }
      }
      timing.stage("local_collision");com.sora.splineroads.core.RoadPlanningBudget.phase("local_collision");
      Set<BlockPos> endpoints = new HashSet<>(selectedNodes);
      // Explicit node moves are validated below and committed atomically with the road.
      // Their old markers may lie inside the extended deck until that transaction commits.
      for(long source:moveSources)endpoints.add(BlockPos.of(source));
      for (var r : built) {
        endpoints.add(r.record.a());
        endpoints.add(r.record.b());
      }
      if (assembly)
        for (UUID id : removed) {
          var old = index.roads.get(id);
          if (old != null) {
            endpoints.add(old.record.a());
            endpoints.add(old.record.b());
          }
        }
      // Only markers belonging to a rebuilt, already existing assembly are exempt.
      // Unrelated markers in a new road's footprint remain errors.
      for(var center:junctions.values())if(center.getBoolean("GeneratedRing") && built.stream().anyMatch(r->center.getUUID("Id").equals(r.record.assembly())))
        for(long port:center.getLongArray("Ports"))endpoints.add(BlockPos.of(port));
      Map<Long, BlockState> writes = new HashMap<>();
      for (long key : touched) {
        BlockPos p = BlockPos.of(key);
        if (level.isOutsideBuildHeight(p) || !level.getWorldBorder().isWithinBounds(p))
          throw new IllegalArgumentException("道路超出世界高度或边界");
        if (!level.hasChunkAt(p)) throw new IllegalArgumentException("道路区块加载失败，请稍后重试");
        if (player != null && !level.mayInteract(player, p))
          throw new IllegalArgumentException("道路经过受保护区域");
        BlockState state = level.getBlockState(p);
        boolean roadBody = body.containsKey(key), headroom = air.contains(key);
        boolean walkway=sidewalks.containsKey(key);
        if (!roadBody && !headroom && !walkway) continue;
        if (state.is(SplineRoads.NODE.get())) {
          if (!endpoints.contains(p)
              && !deleting
              && (walkway || committed.stream()
                  .anyMatch(r -> r.cells.containsKey(key) || r.clearanceCells.contains(key))))
            throw new IllegalArgumentException("道路范围内有其他端点：" + p.toShortString());
          continue;
        }
        // A road always owns its slab collision, including a deck flush with terrain.
        // Existing infrastructure above the new driving corridor cannot be silently erased.
        if (!deleting && headroom
            && !roadBody
            && RoadBlocks.isCollider(state)
            && index.at(key).stream().anyMatch(id -> !removedIds.contains(id)))
          throw new IllegalArgumentException("通行空间与另一条道路相交");
        // Restored host clearance must not erase a surviving road or the user's
        // blocks while deleting. Its existing geometry is allowed to remain.
        if(deleting&&!roadBody&&!walkway)continue;
        if(deleting&&!RoadBlocks.isCollider(state)&&(state.hasBlockEntity()||state.getDestroySpeed(level,p)<0))continue;
        BlockState target =
            roadBody ? collisionState(key, state, body.get(key), sidewalks.get(key),dry.contains(key),finalByChunk.getOrDefault(new net.minecraft.world.level.ChunkPos(p).toLong(),List.of())) : walkway ? sidewalks.get(key) : dry.contains(key)?SplineRoads.TUNNEL_AIR.get().defaultBlockState():Blocks.AIR.defaultBlockState();
        if (state.equals(target) && !(target.is(SplineRoads.FILLED_COLLIDER.get())
            && level.getBlockEntity(p) instanceof RoadFillEntity fill
            && !fill.fill().equals(retainedSource(key,state,sidewalks.get(key))))) continue;
        if (!RoadBlocks.isCollider(state) && !state.is(SplineRoads.TUNNEL_AIR.get()) && !state.equals(sidewalkPlaced.get(key)) && state.getDestroySpeed(level, p) < 0)
          throw new IllegalArgumentException("无法清除不可破坏方块：" + p.toShortString());
        if(state.hasBlockEntity() && !RoadBlocks.isCollider(state))
          throw new IllegalArgumentException("道路或隧道范围被方块实体占用，无法清除："+p.toShortString());
        writes.put(key, target);
      }
      for (var move : moves) {
        if (move.from() != null) requireNode(level, move.from(), player);
        if(move.to()==null){
          if(move.from()==null||finalRoads.stream().anyMatch(r->r.record.a().equals(move.from())||r.record.b().equals(move.from())))throw new IllegalArgumentException("仍在使用的端点不能清理");
          long key=move.from().asLong();
          writes.put(key,body.containsKey(key)?collisionState(key,Blocks.AIR.defaultBlockState(),body.get(key),sidewalks.get(key),dry.contains(key),finalByChunk.getOrDefault(new net.minecraft.world.level.ChunkPos(move.from()).toLong(),List.of())):dry.contains(key)?SplineRoads.TUNNEL_AIR.get().defaultBlockState():Blocks.AIR.defaultBlockState());
          continue;
        }
        BlockState state = level.getBlockState(move.to());
        boolean movableMarker = moveSources.contains(move.to().asLong());
        boolean replacedRoad =
            RoadBlocks.isCollider(state)
                && index.at(move.to().asLong()).stream().allMatch(removedIds::contains);
        // The transaction has already approved these terrain writes, but has not
        // applied them yet. A fitted marker inside the new road must use that
        // same clearing decision instead of treating the old ground as occupied.
        // Do not extend this exemption to containers or unrelated road colliders.
        long targetKey = move.to().asLong();
        boolean clearedByConstruction =
            writes.containsKey(targetKey)
                && !RoadBlocks.isCollider(state)
                && !state.hasBlockEntity()
                && level.getBlockEntity(move.to()) == null
                && committed.stream().anyMatch(r ->
                    r.cells.containsKey(targetKey) || r.clearanceCells.contains(targetKey));
        if (!movableMarker
            && !replacedRoad
            && !clearedByConstruction
            && (!state.canBeReplaced() || state.hasBlockEntity() || level.getBlockEntity(move.to()) != null))
          throw new IllegalArgumentException("端点目标被占用：" + move.to().toShortString()
              + "（" + state.getBlock().getName().getString() + " / "
              + BuiltInRegistries.BLOCK.getKey(state.getBlock()) + "）");
        long source = move.from() == null ? 0 : move.from().asLong();
        if (move.from() != null && !moveTargets.contains(source))
          writes.put(
              source,
              body.containsKey(source)
                  ? collisionState(source, Blocks.AIR.defaultBlockState(), body.get(source),sidewalks.get(source),dry.contains(source),finalByChunk.getOrDefault(new net.minecraft.world.level.ChunkPos(BlockPos.of(source)).toLong(),List.of()))
                  : dry.contains(source)?SplineRoads.TUNNEL_AIR.get().defaultBlockState():Blocks.AIR.defaultBlockState());
        writes.put(move.to().asLong(), SplineRoads.NODE.get().defaultBlockState());
      }
      // Count only world writes. Cleared headroom has no restoration history.
      long changed=writes.size();
      for(long key:touched) {
        if(body.containsKey(key)||air.contains(key)||sidewalks.containsKey(key)||moveTargets.contains(key))continue;
        BlockState current=level.getBlockState(BlockPos.of(key));
        if(RoadBlocks.isCollider(current)||current.is(SplineRoads.TUNNEL_AIR.get())||current.equals(sidewalkPlaced.get(key)))changed++;
      }
      if(changed>editCellLimit)throw new IllegalArgumentException("实际需修改 "+changed+" 个方块，超过本次上限 "+editCellLimit);
      if(previewResult!=null){previewResult.addAll(built);return;}
      com.sora.splineroads.core.RoadPlanningBudget.check();
      com.sora.splineroads.core.RoadPlanningBudget.committing();
      // All range/permission/geometry checks completed before any block is cleared.
      for (UUID id : removed) index.remove(id);
      for (var r : built) {
        index.put(r);
        var logical=streets.get(r.record.id());
        if(logical!=null&&r.record.junction()==null) {
          // A continuation may be built with the ordinary road tool even when the far
          // end belongs to an auto junction. Persist its section in the full logical path.
          var settings=r.record.settings();var ends=settings.options().ends();
          var full=settings.options(settings.options().ends(new RoadTransitions.Ends(ends.start(),ends.end(),ends.persistent()).paintPhase(ends.paintPhase())));
          // An untrimmed fitted tunnel owns its newly moved endpoints. Retaining the old
          // logical path here resurrected the short, over-steep tunnel on the next edit/delete.
          streets.put(logical.id(),ends.trimmedStart()==0&&ends.trimmedEnd()==0
              ?r.record.settings(full):logical.settings(full).caps(r.record.endCaps()));
        }
      }
      for (var entry : writes.entrySet()) {
        long key = entry.getKey();
        BlockPos p = BlockPos.of(key);
        BlockState old = level.getBlockState(p);
        BlockState target=entry.getValue();
        // Preserve terrain replaced by the deck/sidewalk; headroom obstacles remain cleared.
        if(!terrainOriginal.containsKey(key)&&!RoadBlocks.isCollider(old)&&!old.is(SplineRoads.TUNNEL_AIR.get())&&!old.is(SplineRoads.NODE.get())&&!old.equals(sidewalkPlaced.get(key))) {
          boolean ground=body.containsKey(key)||sidewalks.containsKey(key);
          if(!ground&&air.contains(key)&&old.canOcclude())for(var road:committed){
            var q=RoadQueries.horizontal(road.mesh,new V(p.getX()+.5,p.getY(),p.getZ()+.5));
            if(p.getY()<=q.sample().center().y()+.001&&q.horizontalDistance()<=q.sample().halfWidth()+road.record.settings().options().sidewalk().width()+1){ground=true;break;}
          }
          if(ground)terrainOriginal.put(key,old);
        }
        BlockState fill=retainedSource(key,old,sidewalks.get(key));
        boolean retained=RoadBlocks.isCollider(target)&&(target.is(SplineRoads.FILLED_COLLIDER.get())||target.getValue(RoadBlocks.Road.FILL)!=RoadBlocks.Fill.NONE);
        if(retained&&!fill.isAir()&&!RoadBlocks.isCollider(fill))terrainFill.put(key,fill);
        else terrainFill.remove(key);
        level.setBlock(p, entry.getValue(), 2);
        if(level.getBlockEntity(p) instanceof RoadFillEntity filled)filled.fill(sidewalks.getOrDefault(key,terrainFill.getOrDefault(key,Blocks.AIR.defaultBlockState())));
      }
      for (var move : moves) {
        if(move.to()==null)continue;
        NodeEntity marker = (NodeEntity) level.getBlockEntity(move.to());
        marker.load(move.snapshot().copy());
        marker.heightExplicit = true;
        marker.apply(move.target());
      }
      for (long key : touched) {
        if (body.containsKey(key) || !deleting&&air.contains(key) || sidewalks.containsKey(key) || moveTargets.contains(key)) continue;
        BlockPos p = BlockPos.of(key);
        BlockState current = level.getBlockState(p);
        BlockState fill = terrainFill.remove(key);
        BlockState original = terrainOriginal.remove(key);
        if (RoadBlocks.isCollider(current)) {
          // Only infill visibly retained inside a live collider survives removal.
          boolean retained=current.is(SplineRoads.FILLED_COLLIDER.get())||current.getValue(RoadBlocks.Road.FILL)!=RoadBlocks.Fill.NONE;
          level.setBlock(p,original!=null?original:retained&&fill!=null&&!sidewalkPlaced.containsKey(key)?fill:Blocks.AIR.defaultBlockState(),2);
        } else if(current.isAir()||current.is(SplineRoads.TUNNEL_AIR.get())||current.equals(sidewalkPlaced.get(key)))level.setBlock(p,original!=null?original:Blocks.AIR.defaultBlockState(),2);
      }
      sidewalkPlaced.clear();sidewalkPlaced.putAll(sidewalks);
      RampJunctions.sync(this);
      for(var r:built)if(streets.containsKey(r.record.id())){var logical=streets.get(r.record.id());streets.put(logical.id(),logical.withLanePoints(LaneTopology.metadata(r.record)));}
      setDirty();
      for (UUID id : removed)
        if (committed.stream().noneMatch(b -> b.record.id().equals(id)))
          RoadNetwork.broadcastDelete(level, id);
      for (var r : built) RoadNetwork.broadcastRoad(level, r.record);
      timing.stage("validation_commit_sync");
    } finally {
      // Also release old roads materialized by an edit that was rejected.
      for (var road : index.roads.values()) road.releaseEditRaster();
    }
  }

  private BlockState collisionState(long key, BlockState previous, List<RoadIndex.Built> roads) {
    return collisionState(key,previous,roads,null);
  }
  private BlockState collisionState(long key, BlockState previous, List<RoadIndex.Built> roads,BlockState sidewalk) {
    var pos=BlockPos.of(key);
    var nearby=index.inChunk(new net.minecraft.world.level.ChunkPos(pos).toLong()).stream()
        .map(index.roads::get).filter(Objects::nonNull).toList();
    boolean dryInterior=nearby.stream().anyMatch(r->r.tunnelAt(pos)&&r.clearanceAt(pos));
    return collisionState(key,previous,roads,sidewalk,dryInterior,nearby);
  }
  private BlockState collisionState(long key, BlockState previous, List<RoadIndex.Built> roads,BlockState sidewalk,boolean dryInterior,List<RoadIndex.Built> nearby) {
    BlockPos p = BlockPos.of(key);
    BlockState terrain =
        sidewalk!=null ? sidewalk : RoadBlocks.isCollider(previous)||previous.equals(sidewalkPlaced.get(key))
            ? terrainFill.getOrDefault(key, Blocks.AIR.defaultBlockState())
            : previous;
    RoadBlocks.Fill fill = RoadBlocks.Fill.of(terrain);
    boolean generic=fill==RoadBlocks.Fill.NONE&&!terrain.isAir()&&(sidewalk!=null||previous.is(SplineRoads.FILLED_COLLIDER.get()));
    boolean deck = false;
    boolean gantryFill=previous.is(SplineRoads.FILLED_COLLIDER.get())&&roads.stream().anyMatch(r->RoadBlocks.gantryCell(r,new RoadRaster.Cell(p.getX(),p.getY(),p.getZ())));
    for (var r : roads) {
      var c = r.column(p);
      if (c == null) continue;
      deck = true;
      if (p.getY() + fill.top() > c.minTop() + 1e-7) fill = RoadBlocks.Fill.NONE;
      if(!gantryFill&&p.getY()+1>c.minTop()+1e-7)generic=false;
    }
    if (!deck && fill != RoadBlocks.Fill.NONE) {
      double lowest =
          roads.stream()
              .flatMap(r -> r.boxes(p).stream())
              .mapToDouble(com.sora.splineroads.core.RoadRaster.Box::y0)
              .min()
              .orElse(0);
      if (fill.top() > lowest + 1e-7) fill = RoadBlocks.Fill.NONE;
    }
    // Preserve only full original terrain that does not enter ANY final travel volume.
    // A global maximum arch height falsely classified roof/portal exterior as interior.
    // Use the final neighboring roads, not only owners of this exact structural cell.
    boolean exteriorShell=roads.stream().anyMatch(r->r.shellAt(p));
    if(exteriorShell&&TunnelTerrainSpace.safe(p,nearby)){
      BlockState original=terrain;
      if(original.isAir()&&RoadBlocks.isCollider(previous))original=terrainOriginal.getOrDefault(key,Blocks.AIR.defaultBlockState());
      if(!original.hasBlockEntity()&&original.getFluidState().isEmpty()){
        fill=RoadBlocks.Fill.of(original);
        generic=fill==RoadBlocks.Fill.NONE&&!original.isAir()&&original.canOcclude();
      }
    }
    // Retain the full original terrain under/along a diagonal sidewalk cell.
    // The continuous SR surface sits above it; only actual elevated voids stay empty.
    double walkTop=roads.stream().mapToDouble(r->r.walkTopAt(p)).max().orElse(Double.NEGATIVE_INFINITY);
    if(!deck&&Double.isFinite(walkTop)&&!terrain.hasBlockEntity()&&terrain.getFluidState().isEmpty()&&!terrain.isAir()){
      var retained=RoadBlocks.Fill.of(terrain);double top=retained==RoadBlocks.Fill.NONE?1:retained.top();
      if(p.getY()+top<=walkTop+1e-6){fill=retained;generic=fill==RoadBlocks.Fill.NONE&&terrain.canOcclude();}
    }
    // Keep water only in exterior partial-shell cells, never in an interior clearance
    // cell or the actual road slab. The caller supplies the union of ALL tunnel air
    // reservations, including tunnels not owning this particular body cell.
    boolean deckHere=roads.stream().anyMatch(r->{var c=r.column(p);return c!=null
        &&p.getY()+1>c.minTop()-r.record.settings().thickness()+1e-7&&p.getY()<c.maxTop()-1e-7;});
    boolean tunnelOwner=roads.stream().anyMatch(RoadIndex.Built::hasTunnel);
    boolean shell=roads.stream().anyMatch(r->r.shellAt(p));
    boolean permeable=RoadWaterPolicy.permeable(deckHere,generic||fill!=RoadBlocks.Fill.NONE,tunnelOwner,shell,dryInterior);
    boolean originalWater=terrainOriginal.getOrDefault(key,Blocks.AIR.defaultBlockState()).getFluidState().is(net.minecraft.tags.FluidTags.WATER);
    boolean water=RoadWaterPolicy.waterlogged(permeable,terrain.getFluidState().is(net.minecraft.tags.FluidTags.WATER),
        previous.getFluidState().is(net.minecraft.tags.FluidTags.WATER),originalWater);
    return (generic?SplineRoads.FILLED_COLLIDER:SplineRoads.COLLIDER)
        .get()
        .defaultBlockState()
        .setValue(RoadBlocks.Road.FILL, fill)
        .setValue(RoadBlocks.Road.LIT, roads.stream().anyMatch(r -> r.lightAt(p)))
        .setValue(RoadBlocks.Road.PERMEABLE,permeable)
        .setValue(RoadBlocks.Road.WATERLOGGED,water)
        .setValue(RoadBlocks.Road.SEALED, shell);
  }

  /** Preserve the material, including uncommon full terrain, when repairing a shell
   * cell whose old collision state deliberately contained no visible infill. */
  private BlockState retainedSource(long key,BlockState previous,BlockState sidewalk){
    if(sidewalk!=null)return sidewalk;
    var fill=terrainFill.getOrDefault(key,previous);
    return RoadBlocks.isCollider(fill)||fill.isAir()&&RoadBlocks.isCollider(previous)
        ?terrainOriginal.getOrDefault(key,Blocks.AIR.defaultBlockState()):fill;
  }

  /** Fill the unused part of a deck cell without replacing the independent road collision. */
  public boolean infill(ServerLevel level,BlockPos p,BlockState fill,ServerPlayer player) {
    if(!RoadBlocks.canInfill(level,p,fill))return false;
    if(player!=null&&(!player.mayBuild()||!level.mayInteract(player,p)))return false;
    for(UUID id:index.at(p.asLong()))if(player!=null&&!player.hasPermissions(2)
        &&index.roads.get(id).record.owner()!=null&&!index.roads.get(id).record.owner().equals(player.getUUID()))return false;
    BlockState old=level.getBlockState(p);
    terrainFill.put(p.asLong(),fill);
    terrainOriginal.put(p.asLong(),fill);
    level.setBlock(p,SplineRoads.FILLED_COLLIDER.get().defaultBlockState().setValue(RoadBlocks.Road.LIT,old.getValue(RoadBlocks.Road.LIT)).setValue(RoadBlocks.Road.SEALED,old.getValue(RoadBlocks.Road.SEALED)),3);
    ((RoadFillEntity)level.getBlockEntity(p)).fill(fill);setDirty();return true;
  }

  /** Dig the retained terrain while leaving the independent road slab in the same block cell. */
  public boolean excavateSupport(ServerLevel level, BlockPos p, ServerPlayer player) {
    BlockState current = level.getBlockState(p);
    if (!RoadBlocks.isCollider(current)
        || current.getValue(RoadBlocks.Road.FILL) == RoadBlocks.Fill.NONE && !current.is(SplineRoads.FILLED_COLLIDER.get())) return false;
    if (player != null && (!player.mayBuild() || !level.mayInteract(player, p))) return false;
    BlockState terrain =
        terrainFill.getOrDefault(p.asLong(), current.getValue(RoadBlocks.Road.FILL).state());
    if (player != null && !player.isCreative() && player.hasCorrectToolForDrops(terrain))
      net.minecraft.world.level.block.Block.dropResources(
          terrain, level, p, null, player, player.getMainHandItem());
    terrainFill.put(p.asLong(), Blocks.AIR.defaultBlockState());
    terrainOriginal.put(p.asLong(), Blocks.AIR.defaultBlockState());

    level.setBlock(p, SplineRoads.COLLIDER.get().defaultBlockState().setValue(RoadBlocks.Road.LIT,current.getValue(RoadBlocks.Road.LIT)).setValue(RoadBlocks.Road.SEALED,current.getValue(RoadBlocks.Road.SEALED)), 3);
    setDirty();
    return true;
  }

  /** Upgrade legacy flush decks as their chunks are watched; geometry stays unchanged. */
  public void repairChunk(ServerLevel level, net.minecraft.world.level.ChunkPos chunk) {
    var retired=retiredSignsByChunk.remove(chunk.toLong());
    if(retired!=null){
      for(var cell:RoadRaster.structures(retired,null).keySet()){
        if(cell.x()>>4!=chunk.x||cell.z()>>4!=chunk.z)continue;
        var p=new BlockPos(cell.x(),cell.y(),cell.z());long key=p.asLong();var state=level.getBlockState(p);
        if(!RoadBlocks.isCollider(state))continue;
        var owners=index.inChunk(chunk.toLong()).stream().map(index.roads::get).filter(r->!r.boxes(p).isEmpty()).toList();
        if(owners.isEmpty()){terrainFill.remove(key);level.setBlock(p,Blocks.AIR.defaultBlockState(),3);}
        else {level.setBlock(p,collisionState(key,state,owners),3);if(level.getBlockEntity(p) instanceof RoadFillEntity fill)fill.fill(terrainFill.getOrDefault(key,Blocks.AIR.defaultBlockState()));}
      }setDirty();
    }
    // Walking is not an edit transaction. Preserve saved geometry, load no remote chunks,
    // and leave geometry upgrades to an explicit editor save.
    for (UUID id : List.copyOf(index.inChunk(chunk.toLong()))) {
      var built = index.roads.get(id);
      // Current transactions persist colliders with their road. Only legacy saves need this scan.
      // Re-scanning modern roads on every chunk watch would eagerly rasterize an entire city.
      if (built == null || built.record.buildVersion() >= 17) continue;
      var done = repairedChunks.computeIfAbsent(chunk.toLong(), ignored -> new HashSet<>());
      if (done.contains(id)) continue;
      for (var cell : built.prepareCollision().cellsInChunk(chunk.x, chunk.z)) {
        BlockPos p = new BlockPos(cell.x(),cell.y(),cell.z());
        long key = p.asLong();
        BlockState state = level.getBlockState(p);
        if (state.is(SplineRoads.NODE.get())
            || RoadBlocks.isCollider(state)
            || state.getDestroySpeed(level, p) < 0 || state.hasBlockEntity()) continue;
        var owners = index.at(key).stream().map(index.roads::get).toList();
        level.setBlock(p, collisionState(key, state, owners), 2);
        setDirty();
      }
      done.add(id);
    }
  }
}
