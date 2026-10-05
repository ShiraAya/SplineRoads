package com.sora.splineroads.client;
import com.sora.splineroads.core.*;
import com.sora.splineroads.core.RoadGeometry.V;
import com.sora.splineroads.config.RoadClientConfig;
import net.irisshaders.iris.api.v0.IrisApi;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.model.ModelManager;
import net.minecraftforge.client.event.ModelEvent;
import java.util.*;
/** Actual terrain source/cache/publication, fake chunk/API and renderer counters. NOT GPU. */
public final class Warm404Validation {
  static int checks;
  static void check(boolean b,String why){checks++;if(!b)throw new AssertionError(why+"; "+RoadTerrainModels.status());}
  static void drain(){for(int i=0;i<2000;i++){RoadTerrainModels.publish();if(i>40&&RoadTerrainModels.stats().queued()==0)return;}throw new AssertionError("queue did not converge");}
  static RoadSurface.Geometry road(double y){return new RoadSurface.Geometry(List.of(new RoadSurface.Face(List.of(new V(0,y,1),new V(0,y,3),new V(48,y,3),new V(48,y,1)),0x404040)),List.of());}
  static void shader(boolean on){IrisApi.active=on;IrisApi.shadow=false;RoadTerrainModels.beginFrame();drain();}
  public static final class Encoding {public static final Encoding INSTANCE=new Encoding();static boolean value;public boolean shouldUseExtendedVertexFormat(){return value;}}
  public static void main(String[]a){
    var probe=ShaderPackState.discoverEncoding(Warm404Validation.class.getClassLoader(),Encoding.class.getName());Encoding.value=false;check(!probe.read(true),"encoding is not inferred only from shader enabled");Encoding.value=true;check(probe.read(false),"extended format state read");
    var mc=Minecraft.getInstance();mc.level.loadedChunks=new HashSet<>();for(int x=0;x<3;x++)mc.level.loadedChunks.add((long)x);
    RoadRenderer.executor=Runnable::run;RoadClientConfig.SURFACE_BACKEND.set(RoadClientConfig.SurfaceBackend.AUTO);RoadClientConfig.TERRAIN_CACHE_MIB.set(64);
    var manager=new ModelManager();RoadTerrainModels.baked(new ModelEvent.BakingCompleted(manager));shader(false);RoadTerrainModels.clear();
    var id=new UUID(404,1);RoadTerrainModels.install(id,RoadTerrainMesh.source(road(100)));drain();check(RoadTerrainModels.stats().baked()==0,"no eager baking while VBO active");
    int resets=RoadRenderer.resets,changes=RoadRenderer.changes;shader(true);check(RoadTerrainModels.stats().baked()==3,"first terrain entry bakes three tiles");
    for(int i=0;i<100;i++){shader(false);check(RoadTerrainModels.stats().active()==0&&RoadTerrainModels.stats().cached()==3,"suspend into bounded cache");RoadTerrainModels.baked(new ModelEvent.BakingCompleted(manager));shader(true);check(RoadTerrainModels.stats().active()==3&&RoadTerrainModels.stats().baked()==3,"unchanged tile reused on toggle");}
    check(RoadTerrainModels.stats().hits()==300,"exact reuse count");check(RoadRenderer.resets==resets&&RoadRenderer.changes==changes,"toggles cause no whole road invalidation");
    System.out.println("100 shader toggle pairs: 3 tile bakes, 300 cache reuses, 0 whole-road invalidations (counter adapter, not FPS)");
    shader(false);RoadTerrainModels.install(id,RoadTerrainMesh.source(road(110)));check(RoadTerrainModels.stats().cached()==0,"edit removes old revision");shader(true);check(RoadTerrainModels.stats().baked()==6,"edited source bakes once");
    shader(false);RoadTerrainModels.baked(new ModelEvent.BakingCompleted(new ModelManager()));RoadTerrainModels.beginFrame();check(RoadTerrainModels.stats().cached()==0,"resource change invalidates old UVs");shader(true);check(RoadTerrainModels.stats().baked()==9,"atlas reload keeps neutral source but updates quads");
    shader(false);RoadClientConfig.TERRAIN_CACHE_MIB.set(0);RoadTerrainModels.beginFrame();check(RoadTerrainModels.stats().cached()==0,"lower budget trims at rest");shader(true);check(RoadTerrainModels.stats().baked()==12,"zero budget deliberately rebakes");
    shader(false);RoadTerrainModels.remove(id);shader(true);check(RoadTerrainModels.stats().active()==0&&RoadTerrainModels.stats().cached()==0,"delete cannot resurrect on backend toggle");
    check(ClientRoads.error.isEmpty(),"no hidden worker failures");System.out.println("Warm404Validation: "+checks+" checks PASS; production terrain cache and optional API state. Fake chunks/renderer counter; NOT game/GPU.");
  }
}
