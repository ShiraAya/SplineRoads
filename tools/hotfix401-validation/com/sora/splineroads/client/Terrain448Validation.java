package com.sora.splineroads.client;
import com.sora.splineroads.core.*;
import com.sora.splineroads.core.RoadGeometry.V;
import com.sora.splineroads.config.RoadClientConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.model.*;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.client.event.ModelEvent;
import net.minecraftforge.client.model.data.ModelData;
import java.util.*;
public final class Terrain448Validation {
 static int checks;
 static void check(boolean ok,String why){checks++;if(!ok)throw new AssertionError(why);}
 static List<BakedQuad> read(BakedModel model){var state=new BlockState();return model.getQuads(state,null,RandomSource.create(),model.getModelData(Minecraft.getInstance().level,new BlockPos(0,99,0),state,ModelData.EMPTY),RenderType.solid());}
 public static void main(String[]args){
  var mc=Minecraft.getInstance();mc.level.loadedChunks=new HashSet<>(Set.of(0L));RoadRenderer.executor=Runnable::run;
  var id=new ModelResourceLocation("splineroads","road_collision","fill=none");
  Map<net.minecraft.resources.ResourceLocation,BakedModel> registry=new HashMap<>();registry.put(id,new BakedModel(){});
  RoadTerrainModels.wrap(new ModelEvent.ModifyBakingResult(registry));var model=registry.get(id);
  RoadTerrainModels.clear();RoadClientConfig.SURFACE_BACKEND.set(RoadClientConfig.SurfaceBackend.TERRAIN);
  RoadTerrainModels.baked(new ModelEvent.BakingCompleted());RoadTerrainModels.beginFrame();
  var deck=Terrain447Validation.face(0,1,100);
  var paint=Terrain447Validation.face(.4,.6,100.01);
  RoadTerrainModels.install(new UUID(448,1),RoadTerrainMesh.source(new RoadSurface.Geometry(List.of(deck),List.of(paint))));TerrainStreaming401Validation.drain();
  var quads=read(model);long baked=RoadTerrainModels.stats().baked();check(!quads.isEmpty(),"no initial road");
  int marks=0;
  for(var q:quads){var v=q.getVertices();var ps=new ArrayList<V>();for(int i=0;i<4;i++)ps.add(new V(Float.intBitsToFloat(v[i*8]),Float.intBitsToFloat(v[i*8+1]),Float.intBitsToFloat(v[i*8+2])));
   check(RoadLighting.normal(new RoadSurface.Face(ps,0)).y()>0,"actual packed quad faces down");
   check((byte)(v[7]>>8)>0,"packed BLOCK normal faces down");
   if(q.getSprite()==RoadTerrainModels.assets().paint())marks++;
  }check(marks>0,"model has no paint");
  for(boolean reload:new boolean[]{false,true,false,true}){
   RoadClientConfig.SURFACE_BACKEND.set(RoadClientConfig.SurfaceBackend.VBO);RoadTerrainModels.beginFrame();check(read(model).isEmpty(),"inactive terrain draws");
   if(reload)RoadTerrainModels.baked(new ModelEvent.BakingCompleted());
   RoadClientConfig.SURFACE_BACKEND.set(RoadClientConfig.SurfaceBackend.TERRAIN);RoadTerrainModels.beginFrame();
   // Read BEFORE publish/drain: a cache hit must be visible on the first frame.
   check(read(model).size()==quads.size(),"switch cleared valid snapshots and waits for incremental publication");
   check(RoadTerrainModels.stats().baked()==baked&&RoadTerrainModels.stats().queued()==0,"switch rebuilt geometry");
   for(var q:read(model))check(q.getSprite()==RoadTerrainModels.assets().paint()||q.getSprite()==RoadTerrainModels.assets().asphalt(),"stale atlas sprite");
  }
  RoadTerrainModels.remove(new UUID(448,1));check(read(model).isEmpty(),"deleted contributor remains visible before next publish");RoadTerrainModels.publish();check(read(model).isEmpty(),"deleted cached road resurrected");
  System.out.println("Terrain448Validation: "+checks+" checks PASS; packed facing and first-frame cache, API adapters, NO GPU claim");
 }
}
