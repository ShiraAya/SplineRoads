package com.sora.splineroads.client;
import com.sora.splineroads.core.*;
import com.sora.splineroads.core.RoadGeometry.V;
import com.sora.splineroads.core.RoadTerrainMesh.Chunk;
import com.sora.splineroads.config.RoadClientConfig;
import java.util.*;
import java.util.concurrent.Executor;
import net.minecraft.client.Minecraft;
import net.minecraftforge.client.event.ModelEvent;

/** Exercises production per-chunk queues, cached results, version cancellation and bounds.
 * GL/Forge and chunk lifetime are test adapters, not emulated Minecraft rendering. */
public final class TerrainStreaming401Validation {
  static int checks;
  static void check(boolean value,String why){checks++;if(!value)throw new AssertionError(why+"; "+RoadTerrainModels.status());}
  static long packed(Chunk c){return ((long)c.x()&0xffffffffL)|((long)c.z()<<32);}
  static void change(Chunk c,boolean on){
    var set=Minecraft.getInstance().level.loadedChunks;if(on)set.add(packed(c));else set.remove(packed(c));
    RoadTerrainModels.chunkChanged(c);
  }
  static void pump(int n){for(int i=0;i<n;i++)RoadTerrainModels.publish();}
  static void drain(){for(int i=0;i<2000;i++){pump(1);if(RoadTerrainModels.stats().queued()==0&&i>40)return;}throw new AssertionError("queue did not settle");}
  static RoadSurface.Geometry strip(int chunks){return new RoadSurface.Geometry(List.of(new RoadSurface.Face(List.of(
      new V(0,100,1),new V(0,100,3),new V(chunks*16,100,3),new V(chunks*16,100,1)),0x404040)),List.of());}
  static final class ManualExecutor implements Executor {final Deque<Runnable> tasks=new ArrayDeque<>();public void execute(Runnable task){tasks.add(task);}void run(){while(!tasks.isEmpty())tasks.removeFirst().run();}}
  static int quadCount(net.minecraft.client.resources.model.BakedModel model){
    var state=new net.minecraft.world.level.block.state.BlockState();
    var data=model.getModelData(Minecraft.getInstance().level,new net.minecraft.core.BlockPos(0,99,1),state,net.minecraftforge.client.model.data.ModelData.EMPTY);
    return model.getQuads(state,null,net.minecraft.util.RandomSource.create(),data,net.minecraft.client.renderer.RenderType.solid()).size();
  }
  public static void main(String[] args){
    var mc=Minecraft.getInstance();mc.level.loadedChunks=new HashSet<>();
    RoadClientConfig.SURFACE_BACKEND.set(RoadClientConfig.SurfaceBackend.TERRAIN);RoadClientConfig.TERRAIN_CACHE_MIB.set(1); // Explicit new weighted budget.
    RoadTerrainModels.baked(new ModelEvent.BakingCompleted());RoadTerrainModels.beginFrame();
    RoadRenderer.executor=Runnable::run;UUID id=new UUID(41,1);Chunk c0=new Chunk(0,0),c1=new Chunk(1,0),c2=new Chunk(2,0);
    change(c0,true);change(c1,true);RoadTerrainModels.install(id,RoadTerrainMesh.source(strip(6)));drain();
    check(RoadTerrainModels.stats().active()==2&&RoadTerrainModels.stats().baked()==2,"initial bake only two loaded tiles, not full road");
    int roadRebuilds=RoadRenderer.changes;long baked=RoadTerrainModels.stats().baked();
    for(int round=0;round<100;round++){
      for(int repeat=0;repeat<5;repeat++)RoadTerrainModels.chunkChanged(c1); // duplicate notifications
      change(c0,false);change(c2,true);drain();
      change(c2,false);change(c0,true);drain();
    }
    check(RoadRenderer.changes==roadRebuilds,"movement NEVER dirties whole road/infrastructure meshes");
    check(RoadTerrainModels.stats().baked()==baked+1,"three distinct chunks each baked once across 200 view changes");
    check(RoadTerrainModels.stats().hits()==199,"subsequent revisits restored cached baked tiles");
    check(RoadTerrainModels.stats().queued()==0,"idle queue reaches zero");
    check(RoadTerrainModels.stats().active()==2&&RoadTerrainModels.stats().cached()==1,"active/resident/cache counts are not work-queue counts");
    long before=RoadTerrainModels.stats().baked();for(int i=0;i<500;i++)RoadTerrainModels.chunkChanged(c0);drain();
    check(RoadTerrainModels.stats().baked()==before,"duplicate loaded events cannot rebake active tile");
    System.out.println("  same-road walk: 200 view changes, 3 tiles baked, 199 cache hits, 0 full-road invalidations");
    // Edited UUID and atlas invalidation must not reuse an old result even at the same coordinates.
    RoadTerrainModels.install(id,RoadTerrainMesh.source(strip(6)));drain();
    check(RoadTerrainModels.stats().baked()==before+2,"real road edit rebuilds loaded tiles");
    check(RoadTerrainModels.stats().cached()==0,"edit discards old revision's unloaded cache");
    var executor=new ManualExecutor();RoadRenderer.executor=executor;
    change(c2,true);pump(2);check(RoadTerrainModels.stats().queued()==1,"one job in progress");
    RoadTerrainModels.remove(id);executor.run();pump(60);
    check(RoadTerrainModels.stats().active()==0&&RoadTerrainModels.stats().cached()==0&&RoadTerrainModels.stats().queued()==0,"deleted road cannot resurrect from queued worker");
    RoadTerrainModels.install(id,RoadTerrainMesh.source(strip(6)));pump(2);RoadTerrainModels.clear();executor.run();pump(60);
    check(RoadTerrainModels.stats().active()==0&&RoadTerrainModels.stats().queued()==0,"resource/world clear invalidates stale workers");
    // New source with same ID supersedes queued source. Only replacement shape should be published.
    RoadTerrainModels.install(id,RoadTerrainMesh.source(strip(6)));pump(2);
    RoadTerrainModels.install(id,RoadTerrainMesh.source(strip(1)));executor.run();pump(2);executor.run();pump(60);
    check(RoadTerrainModels.stats().active()==1,"same-ID replacement accepts current source only");
    RoadTerrainModels.clear();RoadRenderer.executor=Runnable::run;mc.level.loadedChunks.clear();
    // Keep 140 tiles loaded, then unload: active model storage is not copied into unbounded history.
    for(int i=0;i<140;i++)change(new Chunk(i,0),true);
    RoadTerrainModels.install(id,RoadTerrainMesh.source(strip(140)));drain();
    check(RoadTerrainModels.stats().active()==140,"all requested loaded tiles prepared incrementally");
    for(int i=0;i<140;i++)change(new Chunk(i,0),false);pump(300);
    check(RoadTerrainModels.stats().active()==0,"unloaded chunks release active contributions");
    check(RoadTerrainModels.stats().cached()<=2048&&RoadTerrainModels.stats().cachedQuads()<=4096,"unloaded LRU bounded by tiles AND quads");
    check(RoadTerrainModels.stats().cached()>0&&RoadTerrainModels.stats().cached()<140,"weighted budget evicts oldest unloaded tiles");
    RoadTerrainModels.remove(id);pump(80);check(RoadTerrainModels.stats().cached()==0,"deletion frees all cached road tiles");
    // Publishing/removing one contributor must preserve another road in the same section.
    change(c0,true);UUID second=new UUID(41,2);
    var models=new HashMap<net.minecraft.resources.ResourceLocation,net.minecraft.client.resources.model.BakedModel>();
    var modelId=new net.minecraft.client.resources.model.ModelResourceLocation("splineroads","road_collision","fill=none");
    models.put(modelId,new net.minecraft.client.resources.model.BakedModel(){});
    RoadTerrainModels.wrap(new ModelEvent.ModifyBakingResult(models));var model=models.get(modelId);
    RoadTerrainModels.install(id,RoadTerrainMesh.source(strip(1)));drain();int single=quadCount(model);
    check(single>0,"first road published into shared section");
    RoadTerrainModels.install(second,RoadTerrainMesh.source(strip(1)));drain();
    check(quadCount(model)==single*2,"section combines separate contributors instead of last-writer wins");
    RoadTerrainModels.remove(id);pump(80);check(quadCount(model)==single,"removing one road preserves the other contributor");
    RoadTerrainModels.remove(second);pump(80);check(quadCount(model)==0,"last removal clears the section snapshot");
    RoadTerrainModels.clear();mc.level.loadedChunks.clear();change(c0,true);
    // Force the weighted (quad) eviction bound while the tile-count bound is not reached.
    var face=new RoadSurface.Face(List.of(new V(0,100,0),new V(0,100,16),new V(16,100,16),new V(16,100,0)),0x404040);
    var heavy=new RoadSurface.Geometry(Collections.nCopies(520,face),List.of());
    RoadTerrainModels.install(id,RoadTerrainMesh.source(heavy));drain();
    check(RoadTerrainModels.stats().active()==1,"oversized tile was actually baked and active");
    change(c0,false);pump(80);
    check(RoadTerrainModels.stats().cached()==0&&RoadTerrainModels.stats().cachedQuads()==0,"single oversized tile is evicted by quad cap, not tile-count cap");
    check(ClientRoads.error.isEmpty(),"no worker errors hidden during tests");
    System.out.println("TerrainStreaming401Validation: "+checks+" checks passed; actual tile logic with fake chunk lifecycle, NO FPS or GPU claim");
  }
}
