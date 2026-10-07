package com.sora.splineroads.world;

import com.sora.splineroads.core.*;
import com.sora.splineroads.core.RoadGeometry.*;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.phys.shapes.*;

public final class RoadIndex {
  private static <K, V> Map<K, V> bounded(int limit) {
    return new LinkedHashMap<>(128, .75f, true) {
      protected boolean removeEldestEntry(Map.Entry<K, V> entry) {
        return size() > limit;
      }
    };
  }

  public static final class Built {
    public record Column(double minTop, double maxTop) {}

    public final RoadRecord record;
    public final Mesh mesh;
    public final Map<Long, List<RoadRaster.Box>> cells;
    public final Set<Long> chunks = new HashSet<>();
    public final Set<Long> lightCells = new HashSet<>();
    public final Map<Long,Double> walkTops=new HashMap<>();
    public final Set<Long> shellCells = new HashSet<>();
    public final Map<Long, Column> columns;
    private Map<Long,Column> columnData=new HashMap<>();
    private Map<Long,List<RoadRaster.Box>> cellData=new HashMap<>();
    private final Map<Long,List<RoadRaster.Box>> nearbyCells=bounded(1024);
    private final Map<Long,Column> nearbyColumns=bounded(1024);
    private final boolean deferred;
    private volatile boolean rasterized;
    // Compute clearance lazily from per-column ranges instead of retaining millions of boxed keys.
    public final Set<Long> clearanceCells =
        new AbstractSet<>() {
          public boolean contains(Object key) {
            if (!(key instanceof Long value) || effectiveClearance() == 0) return false;
            BlockPos p = BlockPos.of(value);
            var c = clearanceColumns().get(new BlockPos(p.getX(), 0, p.getZ()).asLong());
            return c != null && p.getY() >= clearMin(c) && p.getY() < clearEnd(c,new BlockPos(p.getX(),0,p.getZ()).asLong());
          }

          public int size() {
            if (effectiveClearance() == 0) return 0;
            int n = 0;
            for (var e : clearanceColumns().entrySet()) n += Math.max(0,clearEnd(e.getValue(),e.getKey())-clearMin(e.getValue()));
            return n;
          }

          public Iterator<Long> iterator() {
            if (effectiveClearance() == 0) return Collections.emptyIterator();
            var source = clearanceColumns().entrySet().iterator();
            return new Iterator<>() {
              BlockPos p;
              int y, end;

              public boolean hasNext() {
                while (y >= end && source.hasNext()) {
                  var e = source.next();
                  p = BlockPos.of(e.getKey());
                  y = clearMin(e.getValue());
                  end = clearEnd(e.getValue(),e.getKey());
                }
                return y < end;
              }

              public Long next() {
                if (!hasNext()) throw new NoSuchElementException();
                return new BlockPos(p.getX(), y++, p.getZ()).asLong();
              }
            };
          }
        };

    private int clearMin(Column c) {
      return (int) Math.floor(c.minTop() + 1e-7);
    }

    private final Map<Long,Column> nearbyClearance=bounded(1024);
    private Map<Long,Column> dryColumns;
    private Map<Long,Column> clearanceColumns(){
      ensureRaster();
      if(record.settings().structure()!=Structure.TUNNEL){
        if(dryColumns==null){
          dryColumns=new HashMap<>(columnData);
          for(var e:walkTops.entrySet()){
            var p=BlockPos.of(e.getKey());long key=BlockPos.asLong(p.getX(),0,p.getZ());double y=e.getValue();
            dryColumns.merge(key,new Column(y,y),(a,b)->new Column(Math.min(a.minTop(),b.minTop()),Math.max(a.maxTop(),b.maxTop())));
          }
        }
        return dryColumns;
      }
      // Do not mark the 1.65-block lining/exterior apron as invisible air clearance.
      // Wall and roof blocks already belong to shellCells/cellData and get their own exact body.
      return columnData;
    }
    private double effectiveClearance(){return Math.max(record.clearance(),RoadInfrastructure.clearance(record.settings()));}

    public List<Mesh> automaticTubes(){
      if(autoTubes==null)autoTubes=RoadAutoTunnels.regions(record.structures()).stream().filter(r->r.to()<=mesh.length()+1e-6).map(r->RoadAutoTunnels.tube(mesh,r)).toList();
      return autoTubes;
    }
    private List<Mesh> autoTubes;
    public boolean hasTunnel(){return record.settings().structure()==Structure.TUNNEL||!automaticTubes().isEmpty();}
    public boolean tunnelAt(BlockPos p){
      if(record.settings().structure()==Structure.TUNNEL)return RoadTunnelSpace.intersects(mesh,p.getX(),p.getY(),p.getZ(),1);
      for(var tube:automaticTubes())if(RoadTunnelSpace.intersects(tube,p.getX(),p.getY(),p.getZ(),1))return true;
      return false;
    }
    private final Map<Long,Integer> tunnelEnds=bounded(2048);
    private int clearEnd(Column c,long key) {
      if(record.settings().structure()==Structure.TUNNEL) {
        var previous=tunnelEnds.get(key);if(previous!=null)return previous;
        var p=BlockPos.of(key);
        int result=(int)Math.ceil(RoadInfrastructure.excavationTop(mesh,p.getX(),p.getZ(),c.maxTop())-1e-7);
        tunnelEnds.put(key,result);return result;
      }
      var p=BlockPos.of(key);double roof=Double.NEGATIVE_INFINITY;
      for(var tube:automaticTubes())roof=Math.max(roof,RoadTunnelSpace.ceiling(tube,p.getX(),p.getZ()));
      return (int)Math.ceil((Double.isFinite(roof)?roof:c.maxTop()+effectiveClearance())-1e-7);
    }

    public final List<RoadStructures.Part> signalHeads;
    public final net.minecraft.world.phys.AABB signalBounds;
    private volatile List<RoadSurface.Face> mainSignalFaces, rampSignalFaces;

    public List<RoadSurface.Face> signalFaces(boolean mainGreen) {
      if (signalHeads.isEmpty()) return List.of();
      if (mainSignalFaces == null) {
        rampSignalFaces = RoadSignals.lights(signalHeads, false);
        mainSignalFaces = RoadSignals.lights(signalHeads, true);
      }
      return mainGreen ? mainSignalFaces : rampSignalFaces;
    }

    private RoadSignalFrames junctionSignalFrames;
    public List<RoadSurface.Face> signalFaces(long time) {
      if(record.junction()==null)return signalFaces(RoadSignals.mainGreen(time));
      if(junctionSignalFrames==null)junctionSignalFrames=new RoadSignalFrames(signalHeads,record.junction()::headState,record.junction().signals().resolutionTicks());
      return junctionSignalFrames.at(time);
    }

    private final boolean lazy;
    private volatile RoadRaster.Local local;
    private volatile Mesh rendered;
    private Map<Long, List<RoadRaster.Box>> deckCells = new HashMap<>();

    public Built(RoadRecord record) {
      this(record, false);
    }

    public Built(RoadRecord record, boolean lazy) {this(record,lazy,true,null);}
    public static Built loading(RoadRecord record){return new Built(record,false,true,null);}
    public boolean rasterized(){return rasterized;}

    /** Point markers do not change the road ribbon, openings, furniture or collision. */
    public Built lanePoints(LanePoints.Data value) {
      var before = record.settings().options().lanePoints();
      if (!Objects.equals(before.link(), value.link()) || !before.openings().equals(value.openings())
          || !before.additions().equals(value.additions()) || !before.cuts().equals(value.cuts()) || !LaneMerge.sameDefinitions(before,value))
        throw new IllegalArgumentException("几何变更必须通过道路建造流程");
      var next = new Built(record.withLanePoints(value), lazy, true, this);
      next.local = local;
      next.nearbyCells.putAll(nearbyCells);
      next.nearbyColumns.putAll(nearbyColumns);
      return next;
    }


    public Built caps(int mask) {
      // Ordinary road end caps extend the physical deck; sampled interchange paths ignore them.
      boolean sameGeometry = record.endCaps() == mask || !record.alignment().isEmpty();
      return new Built(record.caps(mask), lazy, deferred, sameGeometry ? this : null);
    }

    public Built structures(List<RoadStructures.Part> parts) {
      return record.structures().equals(parts)?this:new Built(record.structures(parts), lazy, true, this);
    }

    public Built planned(RoadRecord next){return next==record?this:new Built(next,lazy,true,this);}

    public Built phase(RoadFurniture.Phase phase) {
      return Objects.equals(record.furniturePhase(),phase)?this:new Built(record.furniturePhase(phase), lazy, deferred, this);
    }

    private Built(RoadRecord record, boolean lazy, boolean deferred, Built reuse) {
      // A settings change can move the effective ribbon AND its authored slot frame.
      // Wrapping already narrowed samples without Mesh.reference turned a 3->2
      // road into a fresh three-lane road and moved markers onto its dividers.
      // Derived raised medians also change the holes used by the collision raster.
      if (reuse != null && !record.settings().equals(reuse.record.settings())) reuse = null;
      this.record = record;
      signalHeads = record.structures().stream().filter(RoadSignals::signal).toList();
      net.minecraft.world.phys.AABB signalBox = null;
      for (var head : signalHeads) {
        // Include authored lens overhangs outside the collision envelope of the CB housing.
        var a = head.a(); var b = head.b();
        var box = new net.minecraft.world.phys.AABB(
            Math.min(a.x(),b.x()), Math.min(a.y(),b.y()), Math.min(a.z(),b.z()),
            Math.max(a.x(),b.x()), Math.max(a.y(),b.y())+head.height(), Math.max(a.z(),b.z()))
            .inflate(Math.max(1,head.width()));
        signalBox = signalBox == null ? box : signalBox.minmax(box);
      }
      signalBounds = signalBox;
      this.lazy=lazy;this.deferred=deferred;
      cells=lazy?nearbyCells:new DeferredMap<>(()->{ensureRaster();return cellData;});
      columns=lazy?columnData:new DeferredMap<>(()->{ensureRaster();return columnData;});
      mesh = reuse == null ? record.mesh() : reuse.mesh;
      chunks.addAll(RoadCoverage.chunks(mesh, 0));
      for (var part : record.structures()) {
        double pad = part.halfExtent();
        for (int x = (int) Math.floor(Math.min(part.a().x(), part.b().x()) - pad) >> 4;
            x <= (int) Math.floor(Math.max(part.a().x(), part.b().x()) + pad) >> 4;
            x++)
          for (int z = (int) Math.floor(Math.min(part.a().z(), part.b().z()) - pad) >> 4;
              z <= (int) Math.floor(Math.max(part.a().z(), part.b().z()) + pad) >> 4;
              z++) chunks.add(ChunkPos.asLong(x, z));
        if (!lazy&&!deferred&&part.luminous())
          RoadRaster.structures(List.of(part), null)
              .keySet()
              .forEach(c -> lightCells.add(new BlockPos(c.x(), c.y(), c.z()).asLong()));
      }
      if(lazy||deferred)return;
      if(reuse!=null&&reuse.rasterized){cellData.putAll(reuse.deckCells);columnData.putAll(reuse.columnData);}
      buildRaster(reuse!=null&&reuse.rasterized);
    }
    private synchronized void ensureRaster(){if(!rasterized)buildRaster(false);}
    /** Full edit rasters are temporary. Movement uses the small local collision index. */
    public synchronized void releaseEditRaster() {
      if (lazy || !rasterized) return;
      cellData = new HashMap<>(); deckCells = new HashMap<>(); columnData = new HashMap<>(); nearbyCells.clear();
      rasterized = false; dryColumns=null;walkTops.clear();nearbyClearance.clear();
    }
    private void buildRaster(boolean reused){
      if(!reused)(Boolean.getBoolean("sr.raster.valueCache")?RoadRaster.cachedRaster(mesh):RoadRaster.raster(mesh)).forEach((cell,boxes)->{
        BlockPos p=new BlockPos(cell.x(),cell.y(),cell.z());cellData.put(p.asLong(),boxes);
        long column=new BlockPos(cell.x(),0,cell.z()).asLong();
        for(var box:boxes){double low=cell.y()+box.y0()+record.settings().thickness(),high=cell.y()+box.y1();
          Column previous=columnData.get(column);columnData.put(column,previous==null?new Column(low,high):new Column(Math.min(previous.minTop,low),Math.max(previous.maxTop,high)));
        }
      });
      deckCells.putAll(cellData);
      var furniture=RoadStructureRaster.build(record.structures());
      furniture.cells().forEach((c,b)->cellData.compute(BlockPos.asLong(c.x(),c.y(),c.z()),(key,existing)->{
        var combined=new ArrayList<RoadRaster.Box>(existing==null?List.of():existing);combined.addAll(b);return combined;
      }));
      if(deferred)for(var c:furniture.lights())lightCells.add(BlockPos.asLong(c.x(),c.y(),c.z()));
      for(var c:furniture.shells())shellCells.add(BlockPos.asLong(c.x(),c.y(),c.z()));
      walkTops.clear();
      furniture.walkTops().forEach((c,top)->walkTops.put(BlockPos.asLong(c.x(),c.y(),c.z()),top));
      nearbyCells.clear();rasterized=true;
    }

    public RoadRaster.Local prepareCollision() {
      var value = local;
      if (value == null)
        synchronized (this) {
          if (local == null) local = new RoadRaster.Local(mesh, record.structures());
          value = local;
        }
      return value;
    }

    public Column column(BlockPos pos) {
      long key=BlockPos.asLong(pos.getX(),0,pos.getZ());
      if(rasterized)return columnData.get(key);
      if(nearbyColumns.containsKey(key))return nearbyColumns.get(key);
      Column result=null;
      for(var entry:prepareCollision().deckColumn(pos.getX(),pos.getZ()).entrySet())for(var box:entry.getValue()) {
        double low=entry.getKey().y()+box.y0()+record.settings().thickness(),high=entry.getKey().y()+box.y1();
        result=result==null?new Column(low,high):new Column(Math.min(result.minTop(),low),Math.max(result.maxTop(),high));
      }
      nearbyColumns.put(key,result);return result;
    }

    /** Same clearance semantics as the full edit raster, evaluated at one touched column. */
    public boolean clearanceAt(BlockPos p) {
      if(effectiveClearance()==0)return false;
      if(rasterized)return clearanceCells.contains(p.asLong());
      long key=BlockPos.asLong(p.getX(),0,p.getZ());Column c;
      if(nearbyClearance.containsKey(key))c=nearbyClearance.get(key);
      else {
        if(record.settings().structure()==Structure.TUNNEL) {
          c=column(p);
        } else {
          c=column(p);
          for(var e:prepareCollision().structureColumn(p.getX(),p.getZ(),a->a.material().name().startsWith("WALK_")).entrySet()) {
            double top=e.getValue().stream().mapToDouble(b->e.getKey().y()+b.y1()).max().orElse(e.getKey().y());
            c=c==null?new Column(top,top):new Column(Math.min(c.minTop(),top),Math.max(c.maxTop(),top));
          }
        }
        nearbyClearance.put(key,c);
      }
      return c!=null&&p.getY()>=clearMin(c)&&p.getY()<clearEnd(c,key);
    }
    public double walkTopAt(BlockPos p) {
      if(rasterized)return walkTops.getOrDefault(p.asLong(),Double.NEGATIVE_INFINITY);
      var cell=new RoadRaster.Cell(p.getX(),p.getY(),p.getZ());
      var column=prepareCollision().structureColumn(p.getX(),p.getZ(),a->a.material().name().startsWith("WALK_"));
      return column.getOrDefault(cell,List.of()).stream().mapToDouble(b->p.getY()+b.y1()).max().orElse(Double.NEGATIVE_INFINITY);
    }

    public boolean lightAt(BlockPos p) {
      return rasterized?lightCells.contains(p.asLong()):prepareCollision().structureAt(new RoadRaster.Cell(p.getX(),p.getY(),p.getZ()),RoadStructures.Part::luminous);
    }
    public boolean shellAt(BlockPos p) {
      return rasterized?shellCells.contains(p.asLong()):prepareCollision().structureAt(new RoadRaster.Cell(p.getX(),p.getY(),p.getZ()),part->part.material()==RoadStructures.Material.TUNNEL);
    }

    public Mesh renderMesh() {
      if (record.junction() != null) return mesh;
      var value = rendered;
      if (value == null)
        synchronized (this) {
          if (rendered == null) rendered = RoadRenderMesh.simplify(mesh);
          value = rendered;
        }
      return value;
    }

    public List<RoadRaster.Box> boxes(BlockPos pos) {
      if(rasterized)return cellData.getOrDefault(pos.asLong(),List.of());
      // Clients rasterize only cells actually queried by nearby collision/raycast operations.
      return nearbyCells.computeIfAbsent(
          pos.asLong(),
          k -> {
            var c = new RoadRaster.Cell(pos.getX(), pos.getY(), pos.getZ());
            return prepareCollision().boxes(c);
          });
    }
  }

  private long revision;

  public long revision() {
    return revision;
  }

  public final Map<UUID, Built> roads = new LinkedHashMap<>();
  private final Map<Long, Set<UUID>> chunkRoads = new HashMap<>(), nodeRoads = new HashMap<>();
  private final Map<Long, Set<UUID>> signalChunks = new HashMap<>();
  private long signalRevision = -1, signalChunk;
  private List<Built> nearbySignals = List.of();
  public List<Built> signalsNear(int chunkX, int chunkZ) {
    long chunk = ChunkPos.asLong(chunkX, chunkZ);
    if (signalRevision == revision && signalChunk == chunk) return nearbySignals;
    Set<UUID> ids = new HashSet<>();
    for (int x = chunkX - 8; x <= chunkX + 8; x++)
      for (int z = chunkZ - 8; z <= chunkZ + 8; z++)
        ids.addAll(signalChunks.getOrDefault(ChunkPos.asLong(x,z), Set.of()));
    nearbySignals = ids.stream().map(roads::get).toList();
    signalRevision = revision; signalChunk = chunk;
    return nearbySignals;
  }
  private static Set<Long> signalLocations(Built road) {
    Set<Long> chunks = new HashSet<>();
    for (var head : road.signalHeads)
      chunks.add(ChunkPos.asLong((int)Math.floor(head.a().x()) >> 4, (int)Math.floor(head.a().z()) >> 4));
    return chunks;
  }
  private final Map<Long, Set<UUID>> signChunks = new HashMap<>();
  private long signRevision = -1, signChunk;
  private List<Built> nearbySigns = List.of();
  public List<Built> signsNear(int chunkX, int chunkZ) {
    long chunk = ChunkPos.asLong(chunkX, chunkZ);
    if (signRevision == revision && signChunk == chunk) return nearbySigns;
    Set<UUID> ids = new HashSet<>();
    for (int x = chunkX - 8; x <= chunkX + 8; x++)
      for (int z = chunkZ - 8; z <= chunkZ + 8; z++)
        ids.addAll(signChunks.getOrDefault(ChunkPos.asLong(x,z), Set.of()));
    nearbySigns = ids.stream().map(roads::get).toList();
    signRevision = revision; signChunk = chunk;
    return nearbySigns;
  }
  private static Set<Long> signLocations(Built road) {
    Set<Long> chunks = new HashSet<>();
    for(var a:road.record.settings().options().infrastructure().signs()){
      var p=RoadSigns.place(road.mesh,a).part().a();chunks.add(ChunkPos.asLong((int)Math.floor(p.x())>>4,(int)Math.floor(p.z())>>4));
    }
    return chunks;
  }
  private final Map<Long,Set<Long>> shapeChunks=new HashMap<>();
  private final Map<Long,VoxelShape> shapes=new LinkedHashMap<>(128,.75f,true){
    protected boolean removeEldestEntry(Map.Entry<Long,VoxelShape> e){if(size()<=32768)return false;untrack(e.getKey());return true;}
  };
  private static long chunkAt(long key){BlockPos p=BlockPos.of(key);return ChunkPos.asLong(p.getX()>>4,p.getZ()>>4);}
  private void untrack(long key){var set=shapeChunks.get(chunkAt(key));if(set!=null){set.remove(key);if(set.isEmpty())shapeChunks.remove(chunkAt(key));}}
  private void invalidate(Set<Long> chunks){for(long c:chunks){var keys=shapeChunks.remove(c);if(keys!=null)keys.forEach(shapes::remove);}}

  private final boolean lazy;

  public RoadIndex() {
    this(false);
  }

  public RoadIndex(boolean lazy) {
    this.lazy = lazy;
  }

  public void put(Built built) {
    remove(built.record.id());
    revision++;
    roads.put(built.record.id(), built);
    signLocations(built).forEach(c->signChunks.computeIfAbsent(c,k->new HashSet<>()).add(built.record.id()));
    signalLocations(built).forEach(c -> signalChunks.computeIfAbsent(c, k -> new HashSet<>()).add(built.record.id()));
    invalidate(built.chunks);
    built.chunks.forEach(
        c -> chunkRoads.computeIfAbsent(c, k -> new HashSet<>()).add(built.record.id()));
    for (var p : List.of(built.record.a(), built.record.b()))
      nodeRoads.computeIfAbsent(p.asLong(), k -> new HashSet<>()).add(built.record.id());

  }

  /** Preserve the spatial index, shape cache and render revision for point-only changes. */
  public Built lanePoints(UUID id, LanePoints.Data value) {
    var previous = roads.get(id);
    if (previous == null) throw new IllegalArgumentException("道路已不存在");
    var next = previous.lanePoints(value);
    roads.put(id, next);
    return next;
  }

  public Built remove(UUID id) {
    Built old = roads.remove(id);
    if (old == null) return null;
    revision++;
    invalidate(old.chunks);
    signLocations(old).forEach(c->removeFrom(signChunks,c,id));
    signalLocations(old).forEach(c -> removeFrom(signalChunks, c, id));
    old.chunks.forEach(p -> removeFrom(chunkRoads, p, id));
    removeFrom(nodeRoads, old.record.a().asLong(), id);
    removeFrom(nodeRoads, old.record.b().asLong(), id);

    return old;
  }

  private static void removeFrom(Map<Long, Set<UUID>> map, long key, UUID id) {
    var ids = map.get(key);
    if (ids != null) {
      ids.remove(id);
      if (ids.isEmpty()) map.remove(key);
    }
  }

  public Set<UUID> at(long pos) {
    BlockPos p = BlockPos.of(pos);
    Set<UUID> result = new HashSet<>();
    for (UUID id : inChunk(new ChunkPos(p).toLong()))
      if (!roads.get(id).boxes(p).isEmpty())
        result.add(id);
    return result;
  }

  public boolean occupied(long pos) {
    return !at(pos).isEmpty();
  }

  public Set<UUID> inChunk(long chunk) {
    return chunkRoads.getOrDefault(chunk, Set.of());
  }

  public Set<UUID> atNode(BlockPos p) {
    return nodeRoads.getOrDefault(p.asLong(), Set.of());
  }

  public List<Built> neighbors(Built built) {
    Set<UUID> ids = new HashSet<>();
    for (long c : built.chunks) ids.addAll(inChunk(c));
    ids.remove(built.record.id());
    return ids.stream().map(roads::get).filter(b -> overlapXZ(b.mesh, built.mesh, 1)).toList();
  }

  public static boolean overlapXZ(Mesh a, Mesh b, double margin) {
    return a.min().x() <= b.max().x() + margin
        && a.max().x() + margin >= b.min().x()
        && a.min().z() <= b.max().z() + margin
        && a.max().z() + margin >= b.min().z();
  }

  public Built pick(V origin, V direction, double range) {
    Set<UUID> ids = new HashSet<>();
    for (double t = 0; t <= range; t += 8) {
      V p = origin.add(direction.mul(t));
      ids.addAll(
          inChunk(ChunkPos.asLong((int) Math.floor(p.x()) >> 4, (int) Math.floor(p.z()) >> 4)));
    }
    Built best = null;
    double distance = range;
    for (UUID id : ids) {
      Built b = roads.get(id);
      double hit = RoadQueries.ray(b.mesh, origin, direction, distance);
      if (hit < distance) {
        distance = hit;
        best = b;
      }
    }
    return best;
  }

  public VoxelShape shape(BlockPos pos) {
    long key = pos.asLong();
    return shapes.computeIfAbsent(
        key,
        p -> {
          List<RoadRaster.Box> boxes = new ArrayList<>();
          for(UUID id:inChunk(chunkAt(p)))boxes.addAll(roads.get(id).boxes(pos));
          var shape=RoadCollisionShape.build(boxes);shapeChunks.computeIfAbsent(chunkAt(p),c->new HashSet<>()).add(p);return shape;
        });
  }

  public void clear() {
    revision++;
    roads.clear();
    chunkRoads.clear();
    nodeRoads.clear();
    shapes.clear();shapeChunks.clear();signalChunks.clear();nearbySignals=List.of();
    signChunks.clear();nearbySigns=List.of();signalRevision=signRevision=-1;
  }
}
