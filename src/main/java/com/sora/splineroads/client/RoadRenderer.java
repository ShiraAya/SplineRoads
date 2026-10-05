package com.sora.splineroads.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import com.sora.splineroads.SplineRoads;
import com.sora.splineroads.core.*;
import com.sora.splineroads.core.RoadGeometry.*;
import com.sora.splineroads.core.RoadRenderMesh.*;
import com.sora.splineroads.world.RoadIndex;
import java.util.*;
import java.util.concurrent.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix4f;

/**
 * Region VBOs. No face traversal, triangulation, normals or per-face light reads in steady frames.
 */
@Mod.EventBusSubscriber(modid = SplineRoads.ID, value = Dist.CLIENT)
public final class RoadRenderer {
  private static final ResourceLocation
      ASPHALT = ResourceLocation.fromNamespaceAndPath(SplineRoads.ID, "textures/road/asphalt.png"),
      WHITE = ResourceLocation.fromNamespaceAndPath(SplineRoads.ID, "textures/road/white.png");
  private static final RenderType SURFACE = RoadRenderTypes.surface(ASPHALT, false),
      MARKING = RoadRenderTypes.surface(WHITE, true),
      GHOST = RenderType.entityTranslucent(WHITE);
  private static final Map<RoadSurface.Texture, RenderType> MATERIALS =
      new java.util.HashMap<>(Map.of(
          RoadSurface.Texture.CONCRETE,
              RoadRenderTypes.solid(
                  ResourceLocation.fromNamespaceAndPath(
                      "minecraft", "textures/block/smooth_stone.png")),
          RoadSurface.Texture.METAL,
              RoadRenderTypes.surface(
                  ResourceLocation.fromNamespaceAndPath(
                      "minecraft", "textures/block/iron_block.png"),
                  true),
          RoadSurface.Texture.SOIL,
              RoadRenderTypes.surface(
                  ResourceLocation.fromNamespaceAndPath("minecraft", "textures/block/dirt.png"),
                  true),
          RoadSurface.Texture.LEAVES,
              RoadRenderTypes.surface(
                  ResourceLocation.fromNamespaceAndPath(
                      "minecraft", "textures/block/oak_leaves.png"),
                  true),
          RoadSurface.Texture.SIGNAL_HOUSING, signalType("housing"),
          RoadSurface.Texture.SIGNAL_GREEN, signalType("green"),
          RoadSurface.Texture.SIGNAL_RED, signalType("red"),
          RoadSurface.Texture.SIGNAL_AMBER, signalType("amber"),
          RoadSurface.Texture.SIGNAL_ATLAS, signalType("atlas")));

  static {
    for(String name:List.of("oak_log","birch_log","spruce_log","cherry_log","oak_leaves","birch_leaves","spruce_leaves","cherry_leaves"))MATERIALS.put(RoadSurface.Texture.valueOf(name.toUpperCase(java.util.Locale.ROOT)),RoadRenderTypes.surface(ResourceLocation.fromNamespaceAndPath("minecraft","textures/block/"+name+".png"),true));
    MATERIALS.put(RoadSurface.Texture.FLOWERS,RoadRenderTypes.surface(ResourceLocation.fromNamespaceAndPath("minecraft","textures/block/flowering_azalea_top.png"),true));
    MATERIALS.put(RoadSurface.Texture.CB_NOISE_GLASS,RoadRenderTypes.glass(ResourceLocation.fromNamespaceAndPath("splineroads","textures/road/cb_noise.png")));
    MATERIALS.put(RoadSurface.Texture.CB_NOISE,RoadRenderTypes.surface(ResourceLocation.fromNamespaceAndPath("splineroads","textures/road/cb_noise.png"),true));
    MATERIALS.put(RoadSurface.Texture.CB_SIGNS,RoadRenderTypes.surface(ResourceLocation.fromNamespaceAndPath("splineroads","textures/road/cb_sign_atlas.png"),true));
    for(var finish:com.sora.splineroads.core.RoadSidewalks.Finish.values())MATERIALS.put(RoadSurface.Texture.valueOf("WALK_"+finish.name()),RoadRenderTypes.solid(ResourceLocation.fromNamespaceAndPath("minecraft","textures/block/"+finish.id.substring(10)+".png")));
  }
  private static RenderType signalType(String name) {
    return RoadRenderTypes.surface(ResourceLocation.fromNamespaceAndPath(
        SplineRoads.ID, "textures/road/cb_signal_" + name + ".png"), true);
  }
  private static final ExecutorService WORKER =
      Executors.newSingleThreadExecutor(
          r -> {
            Thread t = new Thread(r, "SplineRoads mesh builder");
            t.setDaemon(true);
            t.setPriority(Thread.NORM_PRIORITY-1);
            return t;
          });
  private static final Map<UUID, Integer> versions = new ConcurrentHashMap<>();
  private static final Set<UUID> dirty = new LinkedHashSet<>();
  private static boolean dirtyNeedsSort;
  private static final Map<UUID, Job> jobs = new HashMap<>();
  private static final Map<UUID, Set<Section>> roadSections = new HashMap<>();
  private static Map<Section,GpuSection> regions=new LinkedHashMap<>(),dormant=new LinkedHashMap<>();
  private static boolean extendedEncoding;
  private static long useClock,retainedBytes,lastBudget=-1;
  private static V cameraPosition = new V(0, 0, 0);
  private static volatile long epoch;
  private static int lastDraws, lastUploads, lastDistant;
  private static long lastVertices;
  private static double averageCpuMs;
  private static final List<GpuSection> visible = new ArrayList<>();
  // BufferBuilder owns native memory; discarding the Java object does not free it in 1.20.1.
  // Road uploads share one arena across frames; preview has a separate reusable arena.
  private static final RoadUploadArena uploadArena = new RoadUploadArena();
  private static final RoadUploadArena previewArena = new RoadUploadArena();
  private static Upload pendingUpload;
  private record Prepared(Piece piece,List<RoadSurface.Face> paint,AABB bounds,Map<RoadSurface.Texture,List<RoadSurface.Face>> near,
      Map<RoadSurface.Texture,List<RoadSurface.Face>> far) {
    static Prepared of(Layers layers) {
      var piece=new Piece(RoadUploadBudget.faces(layers.surface().pavement()),RoadUploadBudget.faces(layers.shared().detail()),RoadUploadBudget.faces(layers.shared().distant()));
      var paint=RoadUploadBudget.faces(layers.surface().detail());
      var near=new EnumMap<RoadSurface.Texture,List<RoadSurface.Face>>(RoadSurface.Texture.class);
      var far=new EnumMap<RoadSurface.Texture,List<RoadSurface.Face>>(RoadSurface.Texture.class);
      for(var f:piece.detail())near.computeIfAbsent(f.texture(),k->new ArrayList<>()).add(f);
      for(var f:piece.distant())far.computeIfAbsent(f.texture(),k->new ArrayList<>()).add(f);
      var bound=new ArrayList<>(piece.detail());bound.addAll(paint);return new Prepared(piece,paint,RoadRenderer.bounds(new Piece(piece.pavement(),bound,piece.distant())),near,far);
    }
  }
  private record SignalCache(List<RoadSurface.Face> faces, RoadSignalBatch batch) {}
  private static final Map<UUID, SignalCache> signalBatches = new HashMap<>();
  private static final Set<RenderType> signalTypesUsed = new LinkedHashSet<>();

  static RenderType signalMaterial(RoadSurface.Texture texture) {
    return texture == RoadSurface.Texture.PLAIN ? MARKING : MATERIALS.get(texture);
  }

  static Executor terrainExecutor(){return WORKER;}

  private record MeshResult(Map<Section,Prepared> regions, RoadTerrainMesh.Source terrain) {}

  private record Job(int version, long epoch, CompletableFuture<MeshResult> future) {}

  private static final Map<net.minecraft.core.SectionPos,RoadLightSnapshot> lightSnapshots=new HashMap<>();
  private static long lightSnapshotTick=Long.MIN_VALUE;
  private static RoadLightSnapshot lightPair(net.minecraft.core.SectionPos pos) {
    var level=Minecraft.getInstance().level;
    long tick=level.getGameTime();if(tick!=lightSnapshotTick){lightSnapshotTick=tick;lightSnapshots.clear();}
    var cached=lightSnapshots.get(pos);if(cached!=null)return cached;
    var engine = level.getLightEngine();
    var snapshot=RoadLightSnapshot.capture(
        engine.getLayerListener(net.minecraft.world.level.LightLayer.SKY).getDataLayerData(pos),
        engine.getLayerListener(net.minecraft.world.level.LightLayer.BLOCK).getDataLayerData(pos));
    lightSnapshots.put(pos,snapshot);return snapshot;
  }

  private static boolean lightChanged(GpuSection s) {
    long tick = Minecraft.getInstance().level.getGameTime();
    if (tick - s.checkedLightAt < 20) return false;
    s.checkedLightAt = tick;
    for (var entry : s.lighting.entrySet())
      if (!entry.getValue().equals(lightPair(entry.getKey()))) return true;
    return false;
  }

  private static final class GpuSection {
    final Section key;
    final Map<UUID, Prepared> pieces = new HashMap<>();
    final List<VertexBuffer> pavement=new ArrayList<>(),paint=new ArrayList<>();
    final Map<RoadSurface.Texture, List<VertexBuffer>> near = new EnumMap<>(RoadSurface.Texture.class),
        far = new EnumMap<>(RoadSurface.Texture.class);
    boolean distant;
    long nearVertices, farVertices,surfaceVertices,lastUsed;
    final Matrix4f transform = new Matrix4f();
    AABB bounds;
    boolean dirty = true;
    long revision;
    long checkedLightAt;
    final Map<net.minecraft.core.SectionPos, RoadLightSnapshot> lighting = new HashMap<>();

    GpuSection(Section key) {
      this.key = key;
    }

    void close() {
      pavement.forEach(VertexBuffer::close);pavement.clear();paint.forEach(VertexBuffer::close);paint.clear();
      near.values().forEach(list->list.forEach(VertexBuffer::close));
      far.values().forEach(list->list.forEach(VertexBuffer::close));
      near.clear();
      far.clear();
    }
  }

  public static void changed(Collection<UUID> ids) {
    if(!ids.isEmpty())dirtyNeedsSort=true;
    for (UUID id : ids) {
      versions.merge(id, 1, Integer::sum);
      dirty.add(id);
    }
  }

  public static void reset() {
    if(pendingUpload!=null){pendingUpload.cancel();pendingUpload=null;}
    clearPreviewBuffer();
    furniturePreview = null;
    furnitureFaces = List.of();
    epoch++;
    RoadTerrainModels.clear();
    lightSnapshots.clear();
    dirty.clear();
    dirtyNeedsSort=false;
    versions.clear();
    jobs.values().forEach(j -> j.future.cancel(false));
    jobs.clear();
    roadSections.clear();
    for (var s : regions.values()) s.close();
    regions.clear();for(var s:dormant.values())s.close();dormant.clear();retainedBytes=0;
    signalBatches.clear();
    signalTypesUsed.clear();
    visible.clear();
    lastDraws = lastUploads = lastDistant = 0;
    lastVertices = 0;
    averageCpuMs = 0;
  }

  /** Encoding is distinct from the selected surface backend; never bind the other layout. */
  static void shaderMode(boolean extended){
    if(extendedEncoding==extended){if(lastBudget!=com.sora.splineroads.config.RoadClientConfig.INACTIVE_VBO_CACHE_MIB.get())trimDormant();return;}
    extendedEncoding=extended;if(pendingUpload!=null){pendingUpload.cancel();pendingUpload=null;}clearPreviewBuffer();
    for(var e:regions.entrySet())if(!dormant.containsKey(e.getKey())){var next=new GpuSection(e.getKey());next.pieces.putAll(e.getValue().pieces);next.bounds=e.getValue().bounds;next.revision=e.getValue().revision;dormant.put(e.getKey(),next);}
    var old=regions;regions=dormant;dormant=old;visible.clear();
    for(var section:regions.values())section.checkedLightAt=Long.MIN_VALUE/2;
    trimDormant();
  }
  private static long gpuBytes(GpuSection s){return (s.nearVertices+s.farVertices)*64L;}
  private static void trimDormant(){
    lastBudget=com.sora.splineroads.config.RoadClientConfig.INACTIVE_VBO_CACHE_MIB.get();long limit=lastBudget*1048576L;
    retainedBytes=0;for(var s:dormant.values())retainedBytes+=gpuBytes(s);
    if(retainedBytes<=limit)return;var ordered=new ArrayList<>(dormant.values());ordered.sort(Comparator.comparingLong(s->s.lastUsed));
    for(var s:ordered){if(retainedBytes<=limit)break;retainedBytes-=gpuBytes(s);s.close();s.nearVertices=s.farVertices=s.surfaceVertices=0;s.lighting.clear();s.dirty=true;}
  }
  private static void remove(UUID id){
    RoadTerrainModels.remove(id);signalBatches.remove(id);
    for(var map:List.of(regions,dormant))for(var key:roadSections.getOrDefault(id,Set.of())){var s=map.get(key);if(s==null)continue;
      s.pieces.remove(id);s.dirty=true;s.revision++;if(map==dormant){s.close();s.nearVertices=s.farVertices=s.surfaceVertices=0;}
      if(s.pieces.isEmpty()){s.close();map.remove(key);}}
    roadSections.remove(id);trimDormant();
  }
  private static void installPieces(Map<Section,GpuSection> map,UUID id,Set<Section> old,Map<Section,Prepared> pieces,boolean inactive){
    for(var key:old)if(!pieces.containsKey(key)){var s=map.get(key);if(s==null)continue;s.pieces.remove(id);s.dirty=true;s.revision++;
      if(inactive){s.close();s.nearVertices=s.farVertices=s.surfaceVertices=0;}if(s.pieces.isEmpty()){s.close();map.remove(key);}}
    pieces.forEach((key,p)->{var s=inactive?map.get(key):map.computeIfAbsent(key,GpuSection::new);if(s==null)return;
      s.pieces.put(id,p);s.bounds=s.bounds==null?p.bounds():s.bounds.minmax(p.bounds());s.dirty=true;s.revision++;
      if(inactive){s.close();s.nearVertices=s.farVertices=s.surfaceVertices=0;}});
  }

  private static double buildDistance(UUID id){var road=ClientRoads.INDEX.roads.get(id);if(road==null)return -1;var a=road.mesh.min();var b=road.mesh.max();double x=Math.max(a.x(),Math.min(b.x(),cameraPosition.x()))-cameraPosition.x(),y=Math.max(a.y(),Math.min(b.y(),cameraPosition.y()))-cameraPosition.y(),z=Math.max(a.z(),Math.min(b.z(),cameraPosition.z()))-cameraPosition.z();return x*x+y*y+z*z;}

  private static void update() {
    int completed = 0;long deadline=System.nanoTime()+750_000;
    for (var it = jobs.entrySet().iterator(); it.hasNext() && completed < 1 && System.nanoTime()<deadline; ) {
      var entry = it.next();
      var job = entry.getValue();
      if (!job.future.isDone()) continue;
      it.remove();
      completed++;
      if (job.epoch != epoch || job.version != versions.getOrDefault(entry.getKey(), 0)) continue;
      try {
        var result = job.future.join();
        var pieces=result.regions();
        RoadTerrainModels.install(entry.getKey(),result.terrain());
        signalBatches.remove(entry.getKey());
        var old=roadSections.getOrDefault(entry.getKey(),Set.of());
        installPieces(regions,entry.getKey(),old,pieces,false);installPieces(dormant,entry.getKey(),old,pieces,true);
        roadSections.put(entry.getKey(),pieces.keySet());trimDormant();
      } catch (CompletionException e) {
        ClientRoads.error = "道路网格构建失败：" + e.getCause().getMessage();
      }
    }
    if(jobs.size()<4&&dirtyNeedsSort&&!dirty.isEmpty()){var ordered=new ArrayList<>(dirty);ordered.sort(Comparator.comparingDouble(RoadRenderer::buildDistance));dirty.clear();dirty.addAll(ordered);dirtyNeedsSort=false;}
    int scheduled=0;
    for (var it = dirty.iterator(); it.hasNext() && jobs.size() < 4 && (scheduled==0||System.nanoTime()<deadline); ) {
      UUID id = it.next();
      if (jobs.containsKey(id)) continue;
      it.remove();scheduled++;
      var built = ClientRoads.INDEX.roads.get(id);
      if (built == null) {
        remove(id);
        continue;
      }
      var neighbors = ClientRoads.INDEX.neighbors(built);
      List<com.sora.splineroads.world.RoadIndex.Built> joined = new ArrayList<>(),
          higher = new ArrayList<>();
      for (var other : neighbors) {
        if (other.mesh.min().y() > built.mesh.max().y() + .2
            || other.mesh.max().y() < built.mesh.min().y() - .2) continue;
        joined.add(other);
        boolean priority = RoadSurface.higherPriority(other.record.id(),other.mesh,id,built.mesh);
        if (priority) higher.add(other);
      }
      int version = versions.getOrDefault(id, 0);
      long generation = epoch;
      jobs.put(
          id,
          new Job(
              version,
              generation,
              CompletableFuture.supplyAsync(
                  () -> {
                    if(generation!=epoch || version!=versions.getOrDefault(id,0))return new MeshResult(Map.of(),null);
                    Mesh reduced = built.renderMesh();
                    var surface =
                        RoadSurface.build(
                            reduced,
                            higher.stream()
                                .map(com.sora.splineroads.world.RoadIndex.Built::renderMesh)
                                .toList(),
                            joined.stream()
                                .map(com.sora.splineroads.world.RoadIndex.Built::renderMesh)
                                .toList());
                    if (built.record.junction() != null) surface = RoadSurface.custom(surface,reduced,built.record.junction().get().paint());
                    var terrain=RoadTerrainMesh.source(surface);
                    var result=new LinkedHashMap<Section,Prepared>();
                    RoadRenderMesh.layers(surface,built.record.structures()).forEach((key,piece)->result.put(key,Prepared.of(piece)));
                    return new MeshResult(result,terrain);
                  },
                  WORKER)));
    }
  }

  @SubscribeEvent
  public static void render(RenderLevelStageEvent event) {
    if(event.getStage()==RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS){
      if(Minecraft.getInstance().level==null)return;
      var camera=event.getCamera().getPosition();cameraPosition=new V(camera.x,camera.y,camera.z);
      visible.sort(java.util.Comparator.comparingDouble((GpuSection s)->s.bounds.distanceToSqr(camera)).reversed());
      for(var s:visible){var origin=s.key.origin();s.transform.set(event.getPoseStack().last().pose()).translate((float)(origin.x()-camera.x),(float)(origin.y()-camera.y),(float)(origin.z()-camera.z));}
      draw(visible,MATERIALS.get(RoadSurface.Texture.CB_NOISE_GLASS),RoadSurface.Texture.CB_NOISE_GLASS,event);return;
    }
    if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_BLOCK_ENTITIES) return;
    var mc = Minecraft.getInstance();
    if (mc.level == null) return;
    long started = System.nanoTime();
    lastDraws = lastDistant = 0;
    lastVertices = 0;
    boolean shadowPass=ShaderPackState.current().shadowPass();
    if(!shadowPass)RoadTerrainModels.beginFrame();
    var currentCamera=event.getCamera().getPosition();
    cameraPosition=new V(currentCamera.x,currentCamera.y,currentCamera.z);
    if(!shadowPass){update();RoadTerrainModels.publish();}
    var camera = event.getCamera().getPosition();
    cameraPosition = new V(camera.x, camera.y, camera.z);
    long deadline = System.nanoTime() + com.sora.splineroads.config.RoadClientConfig.VBO_UPLOAD_MICROS.get()*1000L;
    int uploads = 0;
    visible.clear();
    int renderChunks=mc.options.getEffectiveRenderDistance();
    // Distance and frustum checks also precede lighting and uploads, including newly loaded roads.
    for (var s : regions.values()) {
      if (s.bounds == null) continue;
      double d = s.bounds.distanceToSqr(camera);
      if (!RoadVisibility.within(s.bounds.minX,s.bounds.minZ,s.bounds.maxX,s.bounds.maxZ,
          camera.x,camera.z,renderChunks)||!event.getFrustum().isVisible(s.bounds)) continue;
      if (d > 112 * 112) s.distant = true;
      else if (d < 88 * 88) s.distant = false; // hysteresis, no flicker near a LOD boundary
      V origin = s.key.origin();
      s.transform
          .set(event.getPoseStack().last().pose())
          .translate(
              (float) (origin.x() - camera.x),
              (float) (origin.y() - camera.y),
              (float) (origin.z() - camera.z));
      visible.add(s);s.lastUsed=++useClock;
      if (s.distant) lastDistant++;
      lastVertices += (s.distant ? s.farVertices : s.nearVertices)-(RoadTerrainModels.enabled()?s.surfaceVertices:0);
    }
    if(!shadowPass){
    if(pendingUpload!=null && (!visible.contains(pendingUpload.section)
        ||pendingUpload.revision!=pendingUpload.section.revision)) {pendingUpload.cancel();pendingUpload=null;}
    if(pendingUpload==null) {
      GpuSection candidate=null;double closest=Double.POSITIVE_INFINITY;
      for(var s:visible){
        if(!s.dirty&&lightChanged(s))s.dirty=true;
        if(s.dirty){double d=s.bounds.distanceToSqr(camera);if(d<closest){closest=d;candidate=s;}}
      }
      if(candidate!=null)pendingUpload=new Upload(candidate);
    }
    if(pendingUpload!=null){
      try {uploads=pendingUpload.step(deadline);if(pendingUpload.complete)pendingUpload=null;}
      catch(RuntimeException failure){pendingUpload.cancel();pendingUpload=null;ClientRoads.error="道路网格上传失败："+failure.getMessage();}
    }
    }
    cameraPosition = new V(camera.x, camera.y, camera.z);
    var pose = event.getPoseStack();
    var buffers = mc.renderBuffers().bufferSource();
    if (!visible.isEmpty()) buffers.endBatch();
    if(!RoadTerrainModels.enabled()){draw(visible,SURFACE,null,event);drawLayer(visible,MARKING,null,event,true);}
    draw(visible, MARKING, RoadSurface.Texture.PLAIN, event);
    for (var entry : MATERIALS.entrySet()) if(entry.getKey()!=RoadSurface.Texture.CB_NOISE_GLASS)draw(visible, entry.getValue(), entry.getKey(), event);
    cameraPosition = new V(camera.x, camera.y, camera.z);
    pose.pushPose();
    if (mc.level != null) {
      signalTypesUsed.clear();
      for (var road : ClientRoads.INDEX.signalsNear((int)Math.floor(camera.x) >> 4, (int)Math.floor(camera.z) >> 4)) {
        if (road.signalBounds == null || road.signalBounds.distanceToSqr(camera) >= 128 * 128
            || !event.getFrustum().isVisible(road.signalBounds)) continue;
        var faces = road.signalFaces(mc.level.getGameTime());
        var cached = signalBatches.get(road.record.id());
        if (cached == null || cached.faces() != faces) {
          cached = new SignalCache(faces, new RoadSignalBatch(faces));
          signalBatches.put(road.record.id(), cached);
        }
        cached.batch().emit(pose, buffers, cameraPosition, RoadRenderer::signalMaterial, signalTypesUsed);
      }
      for (var type : signalTypesUsed) buffers.endBatch(type);
    }
    RoadSignTextRenderer.render(event);
    drawPreview(event);
    pose.popPose();
    lastUploads = uploads;
    double elapsed = (System.nanoTime() - started) / 1e6;
    averageCpuMs = averageCpuMs == 0 ? elapsed : averageCpuMs * .9 + elapsed * .1;
  }

  private static Mesh ghostMesh;
  private static List<Mesh> ghostRoads;
  private static List<RoadSurface.Face> ghostFaces;
  private static VertexBuffer ghostBuffer;
  private static V ghostOrigin;
  private static boolean ghostDelete, ghostJunction;
  private static void clearPreviewBuffer() {
    if (ghostBuffer != null) ghostBuffer.close();
    ghostBuffer = null; ghostMesh = null; ghostRoads = null; ghostFaces = null;
  }
  private static void drawPreview(RenderLevelStageEvent event) {
    if (ClientRoads.preview == null) { clearPreviewBuffer(); return; }
    boolean deleting = Minecraft.getInstance().screen instanceof RoadDeleteScreen;
    boolean junction = ClientRoads.preview == JunctionScreen.previewMesh;
    if (ghostMesh != ClientRoads.preview || ghostRoads != ClientRoads.nodePreviews
        || ghostFaces != JunctionScreen.previewFaces || ghostDelete != deleting || ghostJunction != junction) {
      clearPreviewBuffer();
      ghostOrigin = ClientRoads.preview.first().center();
      V camera = cameraPosition;
      var builder = previewArena.begin();
      cameraPosition = ghostOrigin;
      try { emitPreview(new PoseStack(), builder); }
      finally { cameraPosition = camera; }
      var buffer = new VertexBuffer(VertexBuffer.Usage.STATIC);
      try {
        buffer.bind(); buffer.upload(builder.end()); ghostBuffer = buffer;
      } catch (RuntimeException e) { buffer.close(); throw e; }
      finally { VertexBuffer.unbind(); }
      ghostMesh = ClientRoads.preview; ghostRoads = ClientRoads.nodePreviews;
      ghostFaces = JunctionScreen.previewFaces; ghostDelete = deleting; ghostJunction = junction;
    }
    GHOST.setupRenderState();
    var shader = RenderSystem.getShader();
    var transform = new Matrix4f(event.getPoseStack().last().pose()).translate(
        (float)(ghostOrigin.x()-cameraPosition.x()), (float)(ghostOrigin.y()-cameraPosition.y()),
        (float)(ghostOrigin.z()-cameraPosition.z()));
    ghostBuffer.bind(); setupShader(shader, event);
    if (shader.MODEL_VIEW_MATRIX != null) { shader.MODEL_VIEW_MATRIX.set(transform); shader.MODEL_VIEW_MATRIX.upload(); }
    ghostBuffer.draw(); lastDraws++;
    shader.clear(); VertexBuffer.unbind(); GHOST.clearRenderState();
  }
  private static void emitPreview(PoseStack pose, VertexConsumer c) {
    var mc = Minecraft.getInstance();
      var previews =
          ClientRoads.nodePreviews.isEmpty()
              ? List.of(ClientRoads.preview)
              : ClientRoads.nodePreviews;
      for (var mesh : previews)
        for (int i = 1; i < mesh.samples().size(); i++) {
          Sample a = mesh.samples().get(i - 1), b = mesh.samples().get(i);
          V l = a.at(a.halfWidth(), -.025),
              r = a.at(-a.halfWidth(), -.025),
              ll = b.at(b.halfWidth(), -.025),
              rr = b.at(-b.halfWidth(), -.025);
          triangle(
              pose,
              c,
              l,
              r,
              rr,
              (mc.screen instanceof RoadDeleteScreen ? 0xFF5D45 : 0x3FDCDD),
              105,
              LightTexture.FULL_BRIGHT,
              false,
              true);
          triangle(
              pose,
              c,
              l,
              rr,
              ll,
              (mc.screen instanceof RoadDeleteScreen ? 0xFF5D45 : 0x3FDCDD),
              105,
              LightTexture.FULL_BRIGHT,
              false,
              true);
        }
      for(var mesh:previews)if(mesh.settings().options().sidewalk().enabled()) {
        var walk=mesh.settings().options().sidewalk();
        if(walk.smooth()){
          for(var part:com.sora.splineroads.core.RoadSidewalks.parts(mesh,walk))if(part.material()!=com.sora.splineroads.core.RoadStructures.Material.TACTILE){var v=part.base().stream().map(p->p.add(new V(0,part.height()+.015,0))).toList();triangle(pose,c,v.get(0),v.get(1),v.get(2),0x9DE69A,105,LightTexture.FULL_BRIGHT,false,true);triangle(pose,c,v.get(0),v.get(2),v.get(3),0x9DE69A,105,LightTexture.FULL_BRIGHT,false,true);}
        }else for(var block:com.sora.splineroads.core.RoadSidewalks.cells(mesh,walk)) {
          V a=new V(block.x(),block.y()+1.03,block.z()),b=a.add(new V(1,0,0)),d=a.add(new V(0,0,1)),e=a.add(new V(1,0,1));
          triangle(pose,c,a,d,e,0x9DE69A,105,LightTexture.FULL_BRIGHT,false,true);
          triangle(pose,c,a,e,b,0x9DE69A,105,LightTexture.FULL_BRIGHT,false,true);
        }
      }
      if (ClientRoads.preview == JunctionScreen.previewMesh)
        for (var face : JunctionScreen.previewFaces) {
          var vertices=face.points();
          for(int i=1;i<vertices.size()-1;i++)
            triangle(pose,c,vertices.get(0).add(new V(0,.035,0)),vertices.get(i).add(new V(0,.035,0)),
                vertices.get(i+1).add(new V(0,.035,0)),face.color(),205,LightTexture.FULL_BRIGHT,false,true);
        }
      if (!(mc.screen instanceof RoadDeleteScreen) && ClientRoads.nodePreviews.size() < 3)
        for (var face : previewFurniture(ClientRoads.preview)) {
          var vertices = face.points();
          for (int i = 1; i < vertices.size() - 1; i++)
            triangle(
                pose,
                c,
                vertices.get(0),
                vertices.get(i),
                vertices.get(i + 1),
                face.color(),
                155,
                LightTexture.FULL_BRIGHT,
                false,
                true);
        }
      if (!(mc.screen instanceof RoadDeleteScreen) && !ClientRoads.preview.settings().laneRamp()) for(V control:ClientRoads.preview.controls()) {
        V point = control.add(new V(0, .12, 0));
        int color=0xCD7BFF;
        var marker =
            new com.sora.splineroads.core.RoadStructures.Part(point, point, .45, .45, true);
        for (var face : marker.faces()) {
          var vertices = face.points();
          triangle(
              pose,
              c,
              vertices.get(0),
              vertices.get(1),
              vertices.get(2),
              color,
              230,
              LightTexture.FULL_BRIGHT,
              false,
              true);
          triangle(
              pose,
              c,
              vertices.get(0),
              vertices.get(2),
              vertices.get(3),
              color,
              230,
              LightTexture.FULL_BRIGHT,
              false,
              true);
        }
      }
  }

  @SubscribeEvent
  public static void debug(
      net.minecraftforge.client.event.CustomizeGuiOverlayEvent.DebugText event) {
    if (Minecraft.getInstance().level == null) return;
    event
        .getLeft()
        .add(
            String.format(
                Locale.ROOT,
                "SR: %d regions (%d far), %d draws, %,d vertices",
                visible.size(),
                lastDistant,
                lastDraws,
                lastVertices));
    event
        .getLeft()
        .add(
            String.format(
                Locale.ROOT,
                "SR CPU: %.2f ms, %d uploads, %d road meshes queued",
                averageCpuMs,
                lastUploads,
                dirty.size() + jobs.size()));
    event.getLeft().add("SR surface: "+RoadTerrainModels.status());
    event.getLeft().add("SR inactive VBO cache: "+(retainedBytes/1048576)+" MiB estimated");
  }

  private static void draw(
      List<GpuSection> visible,
      RenderType type,
      RoadSurface.Texture texture,
      RenderLevelStageEvent event) {drawLayer(visible,type,texture,event,false);}
  private static void drawLayer(List<GpuSection> visible,RenderType type,RoadSurface.Texture texture,RenderLevelStageEvent event,boolean paint){
    if (visible.isEmpty()) return;
    boolean present=false;
    for(var s:visible){var list=paint?s.paint:texture==null?s.pavement:(s.distant?s.far:s.near).get(texture);if(list!=null&&!list.isEmpty()){present=true;break;}}
    if(!present)return;
    type.setupRenderState();
    ShaderInstance shader = RenderSystem.getShader();
    boolean applied = false;
    for (var s : visible) {
      var buffers = paint?s.paint:texture == null ? s.pavement : (s.distant ? s.far : s.near).get(texture);
      if (buffers == null || buffers.isEmpty()) continue;
      if (!applied) {
        setupShader(shader, event);
        applied = true;
      }
      if (shader.MODEL_VIEW_MATRIX != null) {
        shader.MODEL_VIEW_MATRIX.set(s.transform);
        shader.MODEL_VIEW_MATRIX.upload();
      }
      for(var buffer:buffers){buffer.bind();buffer.draw();lastDraws++;}
    }
    if (applied) shader.clear();
    VertexBuffer.unbind();
    type.clearRenderState();
  }

  /** Shared 1.20.1 shader state is uploaded once per material, not once per city region. */
  private static void setupShader(ShaderInstance shader, RenderLevelStageEvent event) {
    for (int i = 0; i < 12; i++) shader.setSampler("Sampler" + i, RenderSystem.getShaderTexture(i));
    if (shader.MODEL_VIEW_MATRIX != null)
      shader.MODEL_VIEW_MATRIX.set(event.getPoseStack().last().pose());
    if (shader.PROJECTION_MATRIX != null) shader.PROJECTION_MATRIX.set(event.getProjectionMatrix());
    if (shader.INVERSE_VIEW_ROTATION_MATRIX != null)
      shader.INVERSE_VIEW_ROTATION_MATRIX.set(RenderSystem.getInverseViewRotationMatrix());
    if (shader.COLOR_MODULATOR != null) shader.COLOR_MODULATOR.set(RenderSystem.getShaderColor());
    if (shader.GLINT_ALPHA != null) shader.GLINT_ALPHA.set(RenderSystem.getShaderGlintAlpha());
    if (shader.FOG_START != null) shader.FOG_START.set(RenderSystem.getShaderFogStart());
    if (shader.FOG_END != null) shader.FOG_END.set(RenderSystem.getShaderFogEnd());
    if (shader.FOG_COLOR != null) shader.FOG_COLOR.set(RenderSystem.getShaderFogColor());
    if (shader.FOG_SHAPE != null) shader.FOG_SHAPE.set(RenderSystem.getShaderFogShape().getIndex());
    if (shader.TEXTURE_MATRIX != null) shader.TEXTURE_MATRIX.set(RenderSystem.getTextureMatrix());
    if (shader.GAME_TIME != null) shader.GAME_TIME.set(RenderSystem.getShaderGameTime());
    if (shader.SCREEN_SIZE != null) {
      var window = Minecraft.getInstance().getWindow();
      shader.SCREEN_SIZE.set((float) window.getWidth(), (float) window.getHeight());
    }
    RenderSystem.setupShaderLights(shader);
    shader.apply();
  }

  private static AABB bounds(Piece piece) {
    double x0 = Double.POSITIVE_INFINITY, y0 = x0, z0 = x0, x1 = -x0, y1 = -x0, z1 = -x0;
    for (var list : List.of(piece.pavement(), piece.detail(), piece.distant()))
      for (var face : list)
        for (V p : face.points()) {
          x0 = Math.min(x0, p.x());
          y0 = Math.min(y0, p.y());
          z0 = Math.min(z0, p.z());
          x1 = Math.max(x1, p.x());
          y1 = Math.max(y1, p.y());
          z1 = Math.max(z1, p.z());
        }
    return new AABB(x0, y0, z0, x1, y1, z1).inflate(.1);
  }

  /** CPU packing yields between faces; the previous VBO stays live until every material is ready. */
  private static final class Upload {
    final GpuSection section;final long revision;
    final List<Prepared> pieces;
    final Map<BlockPos,Integer> lights=new HashMap<>();
    final GpuSection staged;
    final PoseStack pose=new PoseStack();
    final List<Batch> batches=new ArrayList<>();
    BufferBuilder builder;
    int batchIndex,pieceIndex,faceIndex;
    long vertices;
    boolean complete;
    record Batch(int kind,RoadSurface.Texture texture){}
    Upload(GpuSection s){
      section=s;revision=s.revision;pieces=List.copyOf(s.pieces.values());staged=new GpuSection(s.key);
      batches.add(new Batch(0,null));batches.add(new Batch(3,RoadSurface.Texture.PLAIN));
      for(int kind:new int[]{1,2})for(var texture:RoadSurface.Texture.values()){
        boolean present=false;for(var p:pieces)if((kind==1?p.near:p.far).containsKey(texture)){present=true;break;}
        if(present)batches.add(new Batch(kind,texture));
      }
    }
    List<RoadSurface.Face> faces(Prepared p,Batch b){return b.kind==0?p.piece.pavement():b.kind==3?p.paint:(b.kind==1?p.near:p.far).getOrDefault(b.texture,List.of());}
    int step(long deadline){
      cameraPosition=section.key.origin();int uploads=0,packed=0;
      while(batchIndex<batches.size()){
        if(System.nanoTime()>=deadline)return uploads;
        Batch batch=batches.get(batchIndex);
        if(builder==null)builder=uploadArena.begin();
        packing: while(pieceIndex<pieces.size()){
          var faces=faces(pieces.get(pieceIndex),batch);
          while(faceIndex<faces.size()){
            var f=faces.get(faceIndex);
            if(vertices>0&&vertices+RoadRenderMesh.vertexCount(f)>RoadUploadBudget.MAX_VERTICES)break packing;
            faceIndex++;emitPacked(f,batch.kind!=0,staged,lights,builder,pose);
            vertices+=RoadRenderMesh.vertexCount(f);
            if((++packed&15)==0&&System.nanoTime()>=deadline)return uploads;
          }
          faceIndex=0;pieceIndex++;
        }
        VertexBuffer buffer=null;
        if(vertices>0){
          buffer=new VertexBuffer(VertexBuffer.Usage.STATIC);
          try {buffer.bind();buffer.upload(builder.end());}
          catch(RuntimeException|Error failure){buffer.close();throw failure;}
          finally {VertexBuffer.unbind();}
          uploads++;
        } else builder.end().release();
        if(buffer!=null){
          if(batch.kind==0){staged.pavement.add(buffer);staged.nearVertices+=vertices;staged.farVertices+=vertices;staged.surfaceVertices+=vertices;}
          else if(batch.kind==3){staged.paint.add(buffer);staged.nearVertices+=vertices;staged.farVertices+=vertices;staged.surfaceVertices+=vertices;}
          else if(batch.kind==1){staged.near.computeIfAbsent(batch.texture,k->new ArrayList<>()).add(buffer);staged.nearVertices+=vertices;}
          else {staged.far.computeIfAbsent(batch.texture,k->new ArrayList<>()).add(buffer);staged.farVertices+=vertices;}
        }
        builder=null;vertices=0;
        if(pieceIndex==pieces.size()){pieceIndex=faceIndex=0;batchIndex++;}
        // One bounded upload per frame; buffer packing still observes the time budget.
        if(uploads>=1&&batchIndex<batches.size())return uploads;
      }
      section.close();section.pavement.addAll(staged.pavement);section.paint.addAll(staged.paint);section.near.putAll(staged.near);section.far.putAll(staged.far);
      section.nearVertices=staged.nearVertices;section.farVertices=staged.farVertices;section.surfaceVertices=staged.surfaceVertices;
      section.lighting.clear();section.lighting.putAll(staged.lighting);
      section.bounds=null;for(var p:pieces)section.bounds=section.bounds==null?p.bounds:section.bounds.minmax(p.bounds);
      section.dirty=false;section.checkedLightAt=Minecraft.getInstance().level.getGameTime()-Math.floorMod(section.key.hashCode(),20);
      complete=true;return uploads;
    }
    void cancel(){
      if(builder!=null&&builder.building())builder.end().release();
      builder=null;staged.close();
    }
  }

  private static Mesh furniturePreview;
  private static List<RoadSurface.Face> furnitureFaces = List.of();

  private static List<RoadSurface.Face> previewFurniture(Mesh mesh) {
    if (furniturePreview == mesh) return furnitureFaces;
    furniturePreview = mesh;
    if (!com.sora.splineroads.core.RoadProfile.modern(mesh.settings().style()))
      return furnitureFaces = List.of();
    var nearby =
        ClientRoads.INDEX.roads.values().stream()
            .filter(r -> RoadIndex.overlapXZ(r.mesh, mesh, 1))
            .filter(
                r ->
                    ClientRoads.draft == null
                        || !ClientRoads.draft.hasUUID("Id")
                        || !r.record.id().equals(ClientRoads.draft.getUUID("Id")))
            .map(r -> r.mesh)
            .toList();
    try {
      var phase = com.sora.splineroads.core.RoadFurniture.Phase.DEFAULT;
      var draft = ClientRoads.draft;
      if (draft != null && draft.contains("A") && draft.contains("B")) {
        var start = new Node(mesh.first().center(),
            com.sora.splineroads.core.RoadPlanner.yaw(mesh.first().left().left().mul(-1)), 0);
        var end = new Node(mesh.last().center(),
            com.sora.splineroads.core.RoadPlanner.yaw(mesh.last().left().left().mul(-1)), 0);
        if (draft.contains("StartNode")) start = com.sora.splineroads.world.RoadRecord.readNode(draft.getCompound("StartNode"));
        if (draft.contains("EndNode")) end = com.sora.splineroads.world.RoadRecord.readNode(draft.getCompound("EndNode"));
        var record = new com.sora.splineroads.world.RoadRecord(
            draft.hasUUID("Id") ? draft.getUUID("Id") : new UUID(0, 0), new UUID(0, 0),
            BlockPos.of(draft.getLong("A")), BlockPos.of(draft.getLong("B")), start, end, mesh.settings());
        record = record.furniturePhase(com.sora.splineroads.world.FurnitureSpacing.resolve(
            record, mesh, ClientRoads.INDEX.roads.values()));
        phase = com.sora.splineroads.world.FurnitureSpacing.onMesh(record, mesh);
      }
      var parts =
          com.sora.splineroads.core.RoadStructures.plan(
              mesh,
              new com.sora.splineroads.core.RoadStructures.Ground() {
                // Basic cross-section preview; terrain-dependent support generation is
                // authoritative
                // on save.
                public double top(double x, double z, double y) {
                  return y;
                }

                public boolean blocked(com.sora.splineroads.core.RoadStructures.Part part) {
                  return false;
                }

                public boolean joined(V p) {
                  return nearby.stream()
                      .anyMatch(r -> com.sora.splineroads.core.RoadQueries.joins(mesh, r, p));
                }
              }, phase);
      List<RoadSurface.Face> faces = new ArrayList<>();
      for (var part : parts) faces.addAll(part.faces());
      return furnitureFaces = List.copyOf(faces);
    } catch (IllegalArgumentException e) {
      furnitureFaces = List.of();
      ClientRoads.error = "设施预览无效：" + e.getMessage();
      if (Minecraft.getInstance().screen instanceof RoadScreen screen)
        screen.previewFailed(ClientRoads.error);
      return furnitureFaces;
    }
  }

  private static void emitPacked(RoadSurface.Face face,boolean detail,GpuSection section,
      Map<BlockPos,Integer> lights,BufferBuilder builder,PoseStack pose) {
    if(face.points().size()<3)return;
    int[] vertexLights=new int[face.points().size()];
    for(int i=0;i<vertexLights.length;i++)vertexLights[i]=vertexLight(face,face.points().get(i),section,lights,false);
    emitLitFace(face,detail,vertexLights,builder,pose);
  }

  /** Shared final GPU submission. Solid undersides retain their authored outward winding. */
  static void emitLitFace(RoadSurface.Face face,boolean detail,int[] lights,BufferBuilder builder,PoseStack pose){
    var p=face.points();if(p.size()<3)return;
    if(!face.uv().isEmpty()){texturedFace(pose,builder,face,lights);return;}
    if(p.size()==4&&face.texture()!=RoadSurface.Texture.PLAIN){furnitureFace(pose,builder,face,lights);return;}
    V n=RoadLighting.normal(face);
    if(p.size()==4){for(int i=0;i<4;i++)vertex(pose,builder,p.get(i),face.color(),255,lights[i],detail,(float)n.x(),(float)n.y(),(float)n.z(),face.texture());}
    else for(int i=1;i<p.size()-1;i++)for(int k:new int[]{0,i,i+1,i+1})
      vertex(pose,builder,p.get(k),face.color(),255,lights[k],detail,(float)n.x(),(float)n.y(),(float)n.z(),face.texture());
  }

  private static void triangle(
      PoseStack pose,
      VertexConsumer consumer,
      V a,
      V b,
      V c,
      int color,
      int alpha,
      int light,
      boolean marking,
      boolean up) {
    quad(pose, consumer, a, b, c, c, color, alpha, light, marking, up, RoadSurface.Texture.PLAIN);
  }

  private static void triangle(
      PoseStack pose,
      VertexConsumer consumer,
      V a,
      V b,
      V c,
      int color,
      int alpha,
      int light,
      boolean marking,
      boolean up,
      RoadSurface.Texture texture) {
    quad(pose, consumer, a, b, c, c, color, alpha, light, marking, up, texture);
  }

  private static void quad(
      PoseStack pose,
      VertexConsumer consumer,
      V a,
      V b,
      V c,
      V d,
      int color,
      int alpha,
      int light,
      boolean marking,
      boolean up,
      RoadSurface.Texture texture) {
    V ab = b.sub(a), ac = c.sub(a);
    double nx = ab.y() * ac.z() - ab.z() * ac.y(),
        ny = ab.z() * ac.x() - ab.x() * ac.z(),
        nz = ab.x() * ac.y() - ab.y() * ac.x(),
        length = Math.sqrt(nx * nx + ny * ny + nz * nz);
    if (length < 1e-10) {
      // A clipped quad may start with three collinear vertices. Preserve its remaining triangle.
      if (!c.equals(d))
        triangle(pose, consumer, a, c, d, color, alpha, light, marking, up, texture);
      return;
    }
    double sign = up && ny < 0 ? -1 : 1;
    if (sign < 0) {
      V swap = b;
      b = d;
      d = swap;
    } // outward winding permits real back-face culling
    float x = (float) (nx * sign / length),
        y = (float) (ny * sign / length),
        z = (float) (nz * sign / length);
    vertex(pose, consumer, a, color, alpha, light, marking, x, y, z, texture);
    vertex(pose, consumer, b, color, alpha, light, marking, x, y, z, texture);
    vertex(pose, consumer, c, color, alpha, light, marking, x, y, z, texture);
    vertex(pose, consumer, d, color, alpha, light, marking, x, y, z, texture);
  }

  private static int vertexLight(RoadSurface.Face face,V p,GpuSection section,Map<BlockPos,Integer> cache,boolean up) {
    var level=Minecraft.getInstance().level;
    return RoadLighting.sample(face,p,new RoadLighting.Access(){
      public boolean opaque(int x,int y,int z){
        BlockPos pos=new BlockPos(x,y,z);
        section.lighting.computeIfAbsent(net.minecraft.core.SectionPos.of(pos),RoadRenderer::lightPair);
        return level.getBlockState(pos).getLightBlock(level,pos)>=15;
      }
      public int packed(int x,int y,int z){
        BlockPos pos=new BlockPos(x,y,z);
        section.lighting.computeIfAbsent(net.minecraft.core.SectionPos.of(pos),RoadRenderer::lightPair);
        return cache.computeIfAbsent(pos,k->LevelRenderer.getLightColor(level,k));
      }
    },up);
  }

  private static V normal(RoadSurface.Face face) {
    var p = face.points(); V a = p.get(1).sub(p.get(0)), b = p.get(2).sub(p.get(0));
    V n = new V(a.y()*b.z()-a.z()*b.y(), a.z()*b.x()-a.x()*b.z(), a.x()*b.y()-a.y()*b.x());
    double length = Math.sqrt(n.dot(n));
    return length < 1e-10 ? new V(0, 1, 0) : n.mul(1 / length);
  }

  private static void furnitureFace(PoseStack pose, VertexConsumer builder,
      RoadSurface.Face face, int[] lights) {
    V n = normal(face);
    for (int i = 0; i < 4; i++)
      vertex(pose, builder, face.points().get(i), face.color(), 255, lights[i], true,
          (float)n.x(), (float)n.y(), (float)n.z(), face.texture());
  }

  private static void texturedFace(PoseStack pose, VertexConsumer builder,
      RoadSurface.Face face, int[] lights) {
    V n=normal(face);int count=face.points().size();
    if(count==4)for(int i=0;i<4;i++)texturedVertex(pose,builder,face,lights,n,i);
    else for(int i=1;i<count-1;i++)for(int j:new int[]{0,i,i+1,i+1})texturedVertex(pose,builder,face,lights,n,j);
  }
  private static void texturedVertex(PoseStack pose,VertexConsumer builder,RoadSurface.Face face,int[] lights,V n,int i){
    V p=face.points().get(i);var uv=face.uv().get(i);
    builder.vertex(pose.last().pose(),(float)(p.x()-cameraPosition.x()),(float)(p.y()-cameraPosition.y()),(float)(p.z()-cameraPosition.z()))
      .color((face.color()>>16)&255,(face.color()>>8)&255,face.color()&255,255).uv((float)uv.u(),(float)uv.v()).overlayCoords(OverlayTexture.NO_OVERLAY)
      .uv2(lights[i]).normal(pose.last().normal(),(float)n.x(),(float)n.y(),(float)n.z()).endVertex();
  }

  private static void vertex(
      PoseStack pose,
      VertexConsumer c,
      V p,
      int color,
      int alpha,
      int light,
      boolean marking,
      float nx,
      float ny,
      float nz,
      RoadSurface.Texture texture) {
    float u, v;
    if (texture != RoadSurface.Texture.PLAIN) {
      double px = p.x() - Math.floor(cameraPosition.x()),
          py = p.y() - Math.floor(cameraPosition.y()),
          pz = p.z() - Math.floor(cameraPosition.z());
      if (Math.abs(ny) >= Math.max(Math.abs(nx), Math.abs(nz))) {
        u = (float) px;
        v = (float) pz;
      } else if (Math.abs(nx) > Math.abs(nz)) {
        u = (float) pz;
        v = (float) py;
      } else {
        u = (float) px;
        v = (float) py;
      }
    } else {
      u = marking ? 0 : (float) (p.x() / 4 - Math.floor(cameraPosition.x() / 4));
      v = marking ? 0 : (float) (p.z() / 4 - Math.floor(cameraPosition.z() / 4));
    }
    c.vertex(
            pose.last().pose(),
            (float) (p.x() - cameraPosition.x()),
            (float) (p.y() - cameraPosition.y()),
            (float) (p.z() - cameraPosition.z()))
        .color((color >> 16) & 255, (color >> 8) & 255, color & 255, alpha)
        .uv(u, v)
        .overlayCoords(OverlayTexture.NO_OVERLAY)
        .uv2(light)
        .normal(pose.last().normal(), nx, ny, nz)
        .endVertex();
  }

  private RoadRenderer() {}
}
