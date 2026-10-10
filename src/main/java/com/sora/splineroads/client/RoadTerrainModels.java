package com.sora.splineroads.client;

import com.sora.splineroads.SplineRoads;
import com.sora.splineroads.config.RoadClientConfig;
import com.sora.splineroads.core.*;
import com.sora.splineroads.core.RoadTerrainMesh.*;
import java.util.*;
import java.util.concurrent.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.*;
import net.minecraft.core.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.ChunkRenderTypeSet;
import net.minecraftforge.client.event.ModelEvent;
import net.minecraftforge.client.model.BakedModelWrapper;
import net.minecraftforge.client.model.data.*;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Terrain models consume immutable snapshots. Streaming is per road/chunk, never a road
 * geometry invalidation. Active tiles live with loaded chunks; unloaded tiles use bounded LRU.
 */
@Mod.EventBusSubscriber(modid=SplineRoads.ID,value=Dist.CLIENT,bus=Mod.EventBusSubscriber.Bus.MOD)
public final class RoadTerrainModels {
  private static final ModelProperty<List<BakedQuad>> QUADS=new ModelProperty<>();
  private static final ChunkRenderTypeSet SOLID=ChunkRenderTypeSet.of(RenderType.solid());
  private static final Map<Section,Map<Integer,ModelData>> snapshots=new ConcurrentHashMap<>();
  // Only snapshots/enabled/assets are read outside the client thread.
  private static final Map<Section,Map<UUID,Map<Integer,List<BakedQuad>>>> parts=new HashMap<>();
  private static final Map<UUID,Set<Section>> published=new HashMap<>();
  private static final Set<Section> pending=new LinkedHashSet<>();
  private static final Map<UUID,Source> sources=new HashMap<>();
  private static final Map<Chunk,Set<UUID>> chunkRoads=new HashMap<>();
  private static final Map<Tile,Prepared> active=new HashMap<>();
  private static final LinkedHashMap<Tile,Prepared> cache=new LinkedHashMap<>(16,.75f,true);
  private static final Set<Tile> waiting=new LinkedHashSet<>();
  private static final Map<Tile,TileJob> jobs=new LinkedHashMap<>();
  private static final Set<Chunk> streamDirty=new LinkedHashSet<>();
  private static final int CACHE_TILES=2048,MAX_JOBS=2;
  private static int cachedQuads,lastCacheMiB=-1;
  private static boolean sortWaiting;
  private static long preparedTiles,cacheHits,discardedTiles;
  private static volatile Assets loadedAssets;
  private static volatile long resourceRevision;
  private static long observedResource=Long.MIN_VALUE;
  private static Assets observedAssets;
  private static volatile boolean enabled;
  private static String shaderStatus="not queried";

  public record Assets(TextureAtlasSprite asphalt,TextureAtlasSprite paint) {}
  public record Prepared(Map<Section,Map<Integer,List<BakedQuad>>> sections,int quads) {
    public static Prepared empty(){return new Prepared(Map.of(),0);}
  }
  private record Tile(UUID road,Chunk chunk) {}
  private record Source(RoadTerrainMesh.Source mesh,Assets assets) {}
  private record TileJob(Source source,CompletableFuture<Prepared> future) {}
  private static double distance(Chunk chunk) {
    var p=Minecraft.getInstance().gameRenderer.getMainCamera().getPosition();
    double dx=chunk.x()*16.0+8-p.x,dz=chunk.z()*16.0+8-p.z;return dx*dx+dz*dz;
  }
  private static boolean loaded(Chunk chunk) {
    var level=Minecraft.getInstance().level;
    return level!=null&&level.hasChunkAt(new BlockPos(chunk.x()<<4,0,chunk.z()<<4));
  }
  @SubscribeEvent public static void wrap(ModelEvent.ModifyBakingResult event) {
    event.getModels().replaceAll((id,original)->{
      if(!(id instanceof ModelResourceLocation location)||!id.getNamespace().equals(SplineRoads.ID)
          ||location.getVariant().equals("inventory"))return original;
      String name=id.getPath();
      if(!Set.of("road_collision","road_infill","road_node").contains(name))return original;
      return new TerrainModel(original,name.equals("road_collision"));
    });
  }
  @SubscribeEvent public static void baked(ModelEvent.BakingCompleted event) {
    var atlas=event.getModelManager().getAtlas(InventoryMenu.BLOCK_ATLAS);
    var next=new Assets(atlas.getSprite(ResourceLocation.fromNamespaceAndPath(SplineRoads.ID,"block/terrain_asphalt")),
        atlas.getSprite(ResourceLocation.fromNamespaceAndPath(SplineRoads.ID,"block/terrain_paint")));
    var old=loadedAssets;loadedAssets=next;
    if(old==null||old.asphalt()!=next.asphalt()||old.paint()!=next.paint())resourceRevision++;
  }
  /** Called on the ordinary world pass only. API reports false on disabled/failed shaders. */
  public static void beginFrame() {
    var shader=ShaderPackState.current();
    if(shader.shadowPass())return;
    shaderStatus=shader.active()?"shader active":"shader off";
    var choice=RoadClientConfig.SURFACE_BACKEND.get();
    boolean wanted=loadedAssets!=null&&(choice==RoadClientConfig.SurfaceBackend.TERRAIN||
        choice==RoadClientConfig.SurfaceBackend.AUTO&&shader.active());
    boolean initialized=observedResource!=Long.MIN_VALUE,changed=observedResource!=resourceRevision,toggle=enabled!=wanted;
    observedResource=resourceRevision;RoadRenderer.shaderMode(ShaderPackState.extendedVertices(shader));
    if(lastCacheMiB!=RoadClientConfig.TERRAIN_CACHE_MIB.get())trimCache();
    Set<Section> dirty=new HashSet<>();
    if(initialized&&(changed||toggle))dirty.addAll(snapshots.keySet());
    if(changed){reloadAtlas();observedAssets=loadedAssets;}enabled=wanted;
    if(toggle&&!wanted)suspend();
    if(wanted&&(toggle||changed))for(var e:chunkRoads.entrySet())if(loaded(e.getKey()))for(UUID road:e.getValue())request(new Tile(road,e.getKey()));
    // Only road-bearing sections need rebaking. Do not initiate another global
    // world rebuild on top of the shader pack's own resource reload.
    if(Minecraft.getInstance().level!=null)for(var section:dirty)
      Minecraft.getInstance().levelRenderer.setSectionDirty(section.x(),section.y(),section.z());
  }
  public static boolean enabled(){return enabled;}
  public static Assets assets(){return enabled?loadedAssets:null;}
  private static void reloadAtlas(){
    jobs.values().forEach(j->j.future().cancel(false));jobs.clear();waiting.clear();
    // A new atlas object is not new road geometry. Rebind immutable quads and
    // transform their UVs if packing changed; retain the clipped tile and LRU.
    if(observedAssets!=null){
      active.replaceAll((tile,data)->rebind(data,observedAssets,loadedAssets));
      cache.replaceAll((tile,data)->rebind(data,observedAssets,loadedAssets));
    }
    pending.clear();published.clear();parts.clear();snapshots.clear();
    sources.replaceAll((road,source)->new Source(source.mesh(),loadedAssets));
    active.forEach((tile,data)->data.sections().forEach((section,quads)->putPart(tile.road(),section,quads)));
    cache.forEach((tile,data)->{if(loaded(tile.chunk()))data.sections().forEach((section,quads)->putPart(tile.road(),section,quads));});
    // Resource reload already rebuilds chunk meshes. Publish the retained data
    // now, so that rebuild sees the current atlas, not empty incremental snapshots.
    for(var section:new ArrayList<>(pending))publishSection(section);
    pending.clear();
  }
  static Prepared rebind(Prepared data,Assets old,Assets next){
    Map<Section,Map<Integer,List<BakedQuad>>> sections=new LinkedHashMap<>();
    data.sections().forEach((section,cells)->{
      Map<Integer,List<BakedQuad>> replaced=new LinkedHashMap<>();
      cells.forEach((cell,quads)->replaced.put(cell,quads.stream().map(q->{
        var before=q.getSprite();var after=before==old.paint()?next.paint():next.asphalt();
        boolean sameUV=before.getU(0)==after.getU(0)&&before.getU(16)==after.getU(16)
            &&before.getV(0)==after.getV(0)&&before.getV(16)==after.getV(16);
        int[] v=sameUV?q.getVertices():q.getVertices().clone();
        if(!sameUV)for(int i=0;i<4;i++){
          double u=(Float.intBitsToFloat(v[i*8+4])-before.getU(0))/(before.getU(16)-before.getU(0));
          double t=(Float.intBitsToFloat(v[i*8+5])-before.getV(0))/(before.getV(16)-before.getV(0));
          v[i*8+4]=Float.floatToRawIntBits(after.getU(u*16));v[i*8+5]=Float.floatToRawIntBits(after.getV(t*16));
        }
        return new BakedQuad(v,q.getTintIndex(),q.getDirection(),after,q.isShade());
      }).toList()));sections.put(section,Collections.unmodifiableMap(replaced));
    });return new Prepared(Collections.unmodifiableMap(sections),data.quads());
  }
  private static void suspend(){
    waiting.clear();sortWaiting=false;var order=new ArrayList<>(active.keySet());order.sort(Comparator.comparingDouble((Tile t)->distance(t.chunk())).reversed());
    for(var t:order)remember(t,active.remove(t));
    // Keep visible snapshots for retained loaded tiles. enabled gates their use;
    // eviction, road edits and chunk unload still invalidate their contributions.
  }
  public static void clear(){
    jobs.values().forEach(job->job.future().cancel(false));jobs.clear();waiting.clear();
    sources.clear();chunkRoads.clear();active.clear();cache.clear();cachedQuads=0;
    pending.clear();published.clear();parts.clear();snapshots.clear();streamDirty.clear();sortWaiting=false;
    preparedTiles=cacheHits=discardedTiles=0;
  }
  /** Install a newly changed road's immutable face index. Atlas/road changes invalidate old tiles. */
  public static void install(UUID road,RoadTerrainMesh.Source mesh) {
    remove(road);
    if(mesh==null)return;
    sources.put(road,new Source(mesh,loadedAssets));
    for(var chunk:mesh.chunks().keySet()){
      chunkRoads.computeIfAbsent(chunk,key->new LinkedHashSet<>()).add(road);
      if(enabled&&loadedAssets!=null&&loaded(chunk))request(new Tile(road,chunk));
    }
  }
  private static void request(Tile tile){
    if(active.containsKey(tile)||jobs.containsKey(tile)||waiting.contains(tile))return;
    Prepared retained=cache.remove(tile);
    if(retained!=null){cachedQuads-=retained.quads();cacheHits++;activate(tile,retained);return;}
    waiting.add(tile);sortWaiting=true;
  }
  /** Compatibility/testing hook for an already prepared full-road publication. */
  public static void submit(UUID road,Prepared geometry) {
    remove(road);geometry.sections().forEach((section,data)->putPart(road,section,data));
  }
  private static void putPart(UUID road,Section section,Map<Integer,List<BakedQuad>> data){
    if(parts.computeIfAbsent(section,key->new HashMap<>()).put(road,data)==data)return;
    published.computeIfAbsent(road,key->new HashSet<>()).add(section);pending.add(section);
  }
  private static void dropPart(UUID road,Section section){
    var bucket=parts.get(section);if(bucket!=null){bucket.remove(road);if(bucket.isEmpty())parts.remove(section);}
    var keys=published.get(road);if(keys!=null){keys.remove(section);if(keys.isEmpty())published.remove(road);}
    pending.add(section);
  }
  public static void remove(UUID road){
    var source=sources.remove(road);
    if(source!=null)for(var chunk:source.mesh().chunks().keySet()){
      var ids=chunkRoads.get(chunk);if(ids!=null){ids.remove(road);if(ids.isEmpty())chunkRoads.remove(chunk);}
    }
    waiting.removeIf(tile->tile.road().equals(road));
    for(var it=jobs.entrySet().iterator();it.hasNext();){var e=it.next();if(e.getKey().road().equals(road)){e.getValue().future().cancel(false);it.remove();}}
    for(var it=cache.entrySet().iterator();it.hasNext();){var e=it.next();if(e.getKey().road().equals(road)){cachedQuads-=e.getValue().quads();it.remove();}}
    active.keySet().removeIf(tile->tile.road().equals(road));
    for(var section:new ArrayList<>(published.getOrDefault(road,Set.of())))dropPart(road,section);
  }
  private static void activate(Tile tile,Prepared data){
    active.put(tile,data);data.sections().forEach((section,quads)->putPart(tile.road(),section,quads));
  }
  private static void unload(Tile tile){
    waiting.remove(tile);var job=jobs.remove(tile);if(job!=null)job.future().cancel(false);
    var data=active.remove(tile);if(data==null)return;
    for(var section:data.sections().keySet()){dropPart(tile.road(),section);snapshots.remove(section);}
    remember(tile,data);
  }
  private static void remember(Tile tile,Prepared data){var old=cache.put(tile,data);if(old!=null)cachedQuads-=old.quads();cachedQuads+=data.quads();trimCache();}
  private static void trimCache(){
    lastCacheMiB=RoadClientConfig.TERRAIN_CACHE_MIB.get();long limit=lastCacheMiB*1048576L/256L;
    var it=cache.entrySet().iterator();while(it.hasNext()&&(cache.size()>CACHE_TILES||cachedQuads>limit)){
      var e=it.next();cachedQuads-=e.getValue().quads();it.remove();
      if(!active.containsKey(e.getKey()))for(var section:e.getValue().sections().keySet()){
        dropPart(e.getKey().road(),section);publishSection(section);pending.remove(section);
      }
    }
  }
  /** Chunk lifecycle events are coalesced; do NOT mark road geometry or infrastructure dirty. */
  static void chunkChanged(Chunk chunk){streamDirty.add(chunk);}
  private static void stream(){
    long deadline=System.nanoTime()+500_000;
    for(int n=0;n<16&&!streamDirty.isEmpty()&&(n==0||System.nanoTime()<deadline);n++){
      var it=streamDirty.iterator();var chunk=it.next();it.remove();boolean present=loaded(chunk);
      for(UUID road:chunkRoads.getOrDefault(chunk,Set.of())){
        var tile=new Tile(road,chunk);if(present&&enabled)request(tile);else unload(tile);
      }
    }
    int consumed=0;
    for(var it=jobs.entrySet().iterator();it.hasNext()&&consumed<2;){
      var entry=it.next();var job=entry.getValue();if(!job.future().isDone())continue;it.remove();consumed++;
      var tile=entry.getKey();
      if(sources.get(tile.road())!=job.source()){discardedTiles++;continue;}
      try{var data=job.future().join();if(enabled&&loaded(tile.chunk()))activate(tile,data);else remember(tile,data);preparedTiles++;}
      catch(CancellationException ignored){discardedTiles++;}
      catch(CompletionException failure){ClientRoads.error="道路 terrain 分块构建失败："+failure.getCause();}
    }
    if(sortWaiting&&!waiting.isEmpty()){
      var order=new ArrayList<>(waiting);order.sort(Comparator.comparingDouble(tile->distance(tile.chunk())));
      waiting.clear();waiting.addAll(order);sortWaiting=false;
    }
    for(var it=waiting.iterator();it.hasNext()&&jobs.size()<MAX_JOBS;){
      Tile tile=it.next();it.remove();Source source=sources.get(tile.road());
      if(source==null||source.assets()==null||!enabled||!loaded(tile.chunk()))continue;
      var geometry=source.mesh().chunks().get(tile.chunk());if(geometry==null)continue;
      jobs.put(tile,new TileJob(source,CompletableFuture.supplyAsync(
          ()->prepare(geometry,source.assets(),Set.of(tile.chunk())),RoadRenderer.terrainExecutor())));
    }
  }
  /** Merge changed contributors once per section, even when many roads touch that section. */
  public static void publish() {
    var mc=Minecraft.getInstance();if(mc.level==null)return;
    stream();
    int allowance=RoadClientConfig.TERRAIN_SECTIONS_PER_FRAME.get();long deadline=System.nanoTime()+900_000;
    while(allowance-->0&&!pending.isEmpty()&&System.nanoTime()<deadline) {
      var it=pending.iterator();Section section=it.next();it.remove();
      publishSection(section);
    }
  }
  private static void publishSection(Section section){
      var mc=Minecraft.getInstance();var bucket=parts.get(section);boolean present=loaded(new Chunk(section.x(),section.z()));
      if(bucket==null||bucket.isEmpty()||!present)snapshots.remove(section);
      else {
        Map<Integer,List<BakedQuad>> combined=new HashMap<>();
        bucket.entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(road->
            road.getValue().forEach((cell,quads)->combined.computeIfAbsent(cell,k->new ArrayList<>()).addAll(quads)));
        Map<Integer,ModelData> data=new HashMap<>();
        combined.forEach((cell,quads)->data.put(cell,ModelData.builder().with(QUADS,List.copyOf(quads)).build()));
        snapshots.put(section,Collections.unmodifiableMap(data));
      }
      if(present)mc.levelRenderer.setSectionDirty(section.x(),section.y(),section.z());
  }
  public static String status(){return (enabled?"terrain":"VBO")+" ("+shaderStatus+"), "+snapshots.size()+" sections, "+
      (waiting.size()+jobs.size())+" terrain tiles queued, "+pending.size()+" publish, "+cache.size()+" cached, "+preparedTiles+" baked / "+cacheHits+" reused";}
  record Stats(int queued,int active,int cached,int cachedQuads,long baked,long hits,long discarded){}
  static Stats stats(){return new Stats(waiting.size()+jobs.size(),active.size(),cache.size(),cachedQuads,preparedTiles,cacheHits,discardedTiles);}

  /** All allocations/triangulation/atlas lookup happen per missing tile, off the render thread. */
  public static Prepared prepare(RoadSurface.Geometry geometry,Assets assets,Set<Chunk> loaded) {
    var mesh=RoadTerrainMesh.build(geometry,loaded);Map<Section,Map<Integer,List<BakedQuad>>> sections=new LinkedHashMap<>();int count=0;
    for(var entry:mesh.cells().entrySet()) {
      var cell=entry.getKey();List<BakedQuad> quads=new ArrayList<>();
      for(var polygon:entry.getValue()) {
        var v=polygon.vertices();
        if(v.size()==4)quads.add(quad(cell,polygon,List.of(v.get(0),v.get(1),v.get(2),v.get(3)),assets));
        else for(int i=1;i+1<v.size();i++)quads.add(quad(cell,polygon,List.of(v.get(0),v.get(i),v.get(i+1),v.get(i+1)),assets));
      }
      count+=quads.size();sections.computeIfAbsent(cell.section(),k->new LinkedHashMap<>()).put(cell.localKey(),List.copyOf(quads));
    }
    sections.replaceAll((key,value)->Collections.unmodifiableMap(value));
    return new Prepared(Collections.unmodifiableMap(sections),count);
  }
  private static BakedQuad quad(Cell cell,Polygon polygon,List<RoadGeometry.V> points,Assets assets) {
    var sprite=polygon.paint()?assets.paint():assets.asphalt();int[] data=new int[32];
    var face=new RoadSurface.Face(points,polygon.color());var n=RoadLighting.normal(face);
    int normal=((byte)Math.round(n.x()*127)&255)|(((byte)Math.round(n.y()*127)&255)<<8)|(((byte)Math.round(n.z()*127)&255)<<16);
    int rgb=polygon.color(),abgr=0xff000000|((rgb&255)<<16)|(rgb&0xff00)|((rgb>>16)&255);
    double baseU=Math.floor(cell.x()/4.0),baseV=Math.floor(cell.z()/4.0);
    for(int i=0;i<4;i++) {
      var p=points.get(i);int j=i*8;
      // Tiny render-only lifts also prevent coplanar overlap with retained terrain infill. The owning
      // collision cell is computed from the original plane, so no air-cell model is required.
      data[j]=Float.floatToRawIntBits((float)(p.x()-cell.x()));
      data[j+1]=Float.floatToRawIntBits((float)(p.y()-cell.y()+(polygon.paint()?.003:.001)));
      data[j+2]=Float.floatToRawIntBits((float)(p.z()-cell.z()));data[j+3]=abgr;
      double u=polygon.paint()?.5:p.x()/4-baseU,v=polygon.paint()?.5:p.z()/4-baseV;
      data[j+4]=Float.floatToRawIntBits(sprite.getU(Math.max(0,Math.min(1,u))*16));
      data[j+5]=Float.floatToRawIntBits(sprite.getV(Math.max(0,Math.min(1,v))*16));
      data[j+6]=0; // Vanilla/Forge applies actual per-vertex sky/block lighting, not fullbright.
      data[j+7]=normal;
    }
    return new BakedQuad(data,-1,Direction.UP,sprite,true);
  }
  private static List<RoadGeometry.V> vertices(BakedQuad q){
    var v=q.getVertices();var out=new ArrayList<RoadGeometry.V>(4);
    for(int i=0;i<4;i++)out.add(new RoadGeometry.V(Float.intBitsToFloat(v[i*8]),Float.intBitsToFloat(v[i*8+1]),Float.intBitsToFloat(v[i*8+2])));
    return out;
  }
  /** Retained terrain must remain outside the road, but must not also draw
   * underneath it. Clip only coplanar upward fill faces, preserving sidewalls. */
  static List<BakedQuad> exposedFill(List<BakedQuad> base,List<BakedQuad> roads){
    if(base.isEmpty()||roads==null||roads.isEmpty())return base;
    var out=new ArrayList<BakedQuad>();
    for(var q:base){
      if(q.getDirection()!=Direction.UP){out.add(q);continue;}
      var original=vertices(q);List<List<RoadGeometry.V>> pieces=List.of(original);
      for(var road:roads){
        var boundary=vertices(road);
        if(boundary.stream().anyMatch(p->Math.abs(p.y()-RoadTerrainMesh.height(original,p))>.025))continue;
        var next=new ArrayList<List<RoadGeometry.V>>();
        for(var piece:pieces)next.addAll(RoadSurface.subtract(piece,boundary));pieces=next;
        if(pieces.isEmpty())break;
      }
      if(pieces.size()==1&&pieces.get(0).equals(original)){out.add(q);continue;}
      for(var piece:pieces)for(int i=1;i+1<piece.size();i++){
        int[] packed=q.getVertices().clone();var triangle=List.of(piece.get(0),piece.get(i),piece.get(i+1),piece.get(i+1));
        for(int j=0;j<4;j++){
          var v=triangle.get(j);int k=j*8;
          packed[k]=Float.floatToRawIntBits((float)v.x());packed[k+1]=Float.floatToRawIntBits((float)v.y());packed[k+2]=Float.floatToRawIntBits((float)v.z());
          var a=original.get(0);var b=original.get(1).sub(a);var c=original.get(2).sub(a);var d=v.sub(a);
          double det=b.x()*c.z()-b.z()*c.x();
          if(Math.abs(det)>1e-12){
            double u=(d.x()*c.z()-d.z()*c.x())/det,t=(b.x()*d.z()-b.z()*d.x())/det;
            for(int component=4;component<=5;component++){
              var data=q.getVertices();double first=Float.intBitsToFloat(data[component]);
              packed[k+component]=Float.floatToRawIntBits((float)(first+u*(Float.intBitsToFloat(data[8+component])-first)+t*(Float.intBitsToFloat(data[16+component])-first)));
            }
          }
        }
        out.add(new BakedQuad(packed,q.getTintIndex(),q.getDirection(),q.getSprite(),q.isShade()));
      }
    }return List.copyOf(out);
  }

  /** Do not bake the off-screen kilometres of a long road merely because one chunk is loaded. */
  public static Set<Chunk> loadedChunks(com.sora.splineroads.world.RoadIndex.Built road) {
    var level=Minecraft.getInstance().level;Set<Chunk> result=new HashSet<>();
    if(level!=null)for(long packed:road.chunks) {
      int x=(int)packed,z=(int)(packed>>32);
      if(level.hasChunkAt(new BlockPos(x<<4,0,z<<4)))result.add(new Chunk(x,z));
    }
    return Set.copyOf(result);
  }
  @Mod.EventBusSubscriber(modid=SplineRoads.ID,value=Dist.CLIENT)
  public static final class Streaming {
    @SubscribeEvent public static void loaded(net.minecraftforge.event.level.ChunkEvent.Load event){changed(event);}
    @SubscribeEvent public static void unloaded(net.minecraftforge.event.level.ChunkEvent.Unload event){changed(event);}
    private static void changed(net.minecraftforge.event.level.ChunkEvent event) {
      if(!(event.getLevel() instanceof net.minecraft.client.multiplayer.ClientLevel level))return;
      long packed=event.getChunk().getPos().toLong();var mc=Minecraft.getInstance();
      mc.execute(()->{if(mc.level==level)chunkChanged(new Chunk((int)packed,(int)(packed>>32)));});
    }
  }

  public static BakedModel markerModel(BakedModel model){return model instanceof TerrainModel terrain?terrain.baseModel:model;}

  private static final class TerrainModel extends BakedModelWrapper<BakedModel> {
    private final boolean originalVisible;
    private final BakedModel baseModel;
    TerrainModel(BakedModel original,boolean originalVisible){super(original);this.baseModel=original;this.originalVisible=originalVisible;}
    @Override public ModelData getModelData(BlockAndTintGetter level,BlockPos pos,BlockState state,ModelData input) {
      if(!enabled)return withoutRoads(input);
      var section=new Section(pos.getX()>>4,pos.getY()>>4,pos.getZ()>>4);
      var map=snapshots.get(section);if(map==null)return withoutRoads(input);
      ModelData data=map.get((pos.getX()&15)|((pos.getZ()&15)<<4)|((pos.getY()&15)<<8));
      if(data==null)return withoutRoads(input);
      return input==ModelData.EMPTY?data:input.derive().with(QUADS,data.get(QUADS)).build();
    }
    private static ModelData withoutRoads(ModelData input) {
      return input.get(QUADS)==null?input:input.derive().with(QUADS,List.of()).build();
    }
    @Override public List<BakedQuad> getQuads(BlockState state,Direction side,RandomSource random) {
      return originalVisible?super.getQuads(state,side,random):List.of();
    }
    @Override public List<BakedQuad> getQuads(BlockState state,Direction side,RandomSource random,ModelData data,RenderType layer) {
      List<BakedQuad> base=originalVisible?super.getQuads(state,side,random,data,layer):List.of();
      var roads=data.get(QUADS);
      if(enabled)base=exposedFill(base,roads);
      if(!enabled||side!=null||(layer!=null&&layer!=RenderType.solid())||roads==null||roads.isEmpty())return base;
      if(base.isEmpty())return roads;
      var fill=base;
      return new AbstractList<>() {public int size(){return fill.size()+roads.size();}
        public BakedQuad get(int i){return i<fill.size()?fill.get(i):roads.get(i-fill.size());}};
    }
    @Override public ChunkRenderTypeSet getRenderTypes(BlockState state,RandomSource random,ModelData data) {
      if(!originalVisible)return SOLID;
      var base=super.getRenderTypes(state,random,data);return base.contains(RenderType.solid())?base:ChunkRenderTypeSet.union(base,SOLID);
    }
    @Override public boolean isCustomRenderer(){return false;}
    @Override public boolean useAmbientOcclusion(){return true;}
    @Override public boolean useAmbientOcclusion(BlockState state){return true;}
    @Override public boolean useAmbientOcclusion(BlockState state,RenderType layer){return true;}
  }
  private RoadTerrainModels(){}
}
