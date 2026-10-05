package com.sora.splineroads.client;

import com.sora.splineroads.core.*;
import com.sora.splineroads.core.RoadGeometry.V;
import com.sora.splineroads.config.RoadClientConfig;
import java.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.model.*;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.core.*;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.client.event.ModelEvent;
import net.minecraftforge.client.model.data.ModelData;

/** Production terrain bridge + explicit API test adapters, NOT a real Minecraft render test. */
public final class Terrain40ModelValidation {
  private static int checks;
  private static void check(boolean ok,String why){checks++;if(!ok)throw new AssertionError(why);}
  private static List<BakedQuad> quads(BakedModel model,BlockPos pos){var state=new BlockState();var data=model.getModelData(Minecraft.getInstance().level,pos,state,ModelData.EMPTY);return model.getQuads(state,null,RandomSource.create(),data,RenderType.solid());}
  private static RoadSurface.Geometry surface(double x){return new RoadSurface.Geometry(List.of(new RoadSurface.Face(List.of(new V(x,100,0),new V(x,100,2),new V(x+2,100,2),new V(x+2,100,0)),0xDCDCDC)),List.of(new RoadSurface.Face(List.of(new V(x+.4,100,0),new V(x+.4,100,2),new V(x+.6,100,2),new V(x+.6,100,0)),0xFAC136)));}
  public static void main(String[] args) {
    BakedModel original=new BakedModel(){};
    var nodeId=new ModelResourceLocation("splineroads","road_node","normal");var collisionId=new ModelResourceLocation("splineroads","road_collision","fill=none");var itemId=new ModelResourceLocation("splineroads","road_node","inventory");
    Map<net.minecraft.resources.ResourceLocation,BakedModel> registry=new HashMap<>();for(var id:List.of(nodeId,collisionId,itemId))registry.put(id,original);
    RoadTerrainModels.wrap(new ModelEvent.ModifyBakingResult(registry));
    check(registry.get(itemId)==original,"inventory marker model preserved");check(registry.get(nodeId)!=original,"world node wrapped");check(RoadTerrainModels.markerModel(registry.get(nodeId))==original,"explicit node-marker renderer gets original model");
    RoadClientConfig.SURFACE_BACKEND.set(RoadClientConfig.SurfaceBackend.TERRAIN);
    RoadTerrainModels.baked(new ModelEvent.BakingCompleted());RoadTerrainModels.beginFrame();
    check(RoadTerrainModels.assets()!=null,"TERRAIN selected");var baked=RoadTerrainModels.prepare(surface(0),RoadTerrainModels.assets(),null);
    check(!baked.sections().isEmpty()&&baked.quads()>0,"real geometry baked");
    UUID id=UUID.randomUUID();RoadTerrainModels.submit(id,baked);RoadTerrainModels.publish();
    var model=registry.get(collisionId);var pos=new BlockPos(0,99,0);var q=quads(model,pos);check(!q.isEmpty(),"published cell supplies baked quads");
    for(var quad:q){check(quad.getDirection()==Direction.UP,"upward terrain direction");int[] v=quad.getVertices();check(v.length==32,"BLOCK format, not NEW_ENTITY");for(int i=0;i<4;i++){
      float u=Float.intBitsToFloat(v[i*8+4]),vv=Float.intBitsToFloat(v[i*8+5]);check(u>=.0999&&u<=.4001&&vv>=.1999&&vv<=.6001,"UV cannot wrap into adjacent atlas sprite");float y=Float.intBitsToFloat(v[i*8+1]);check(y>1&&y<1.004,"render lift prevents retained-block z-fighting without changing owner");check(v[i*8+6]==0,"no fake fullbright");check((v[i*8+7]>>8&255)>0,"valid upward normal");}}
    check(quads(model,new BlockPos(0,100,0)).isEmpty(),"integer road surface not owned by air cell");check(!quads(registry.get(nodeId),pos).isEmpty(),"node cells do not cut holes in terrain road");
    ModelData previous=model.getModelData(Minecraft.getInstance().level,pos,new BlockState(),ModelData.EMPTY);
    RoadTerrainModels.remove(id);RoadTerrainModels.publish();check(quads(model,pos).isEmpty(),"deletion removes published cells");
    check(!model.getQuads(new BlockState(),null,RandomSource.create(),previous,RenderType.solid()).isEmpty(),"captured ModelData snapshot is immutable");
    var refreshed=model.getModelData(Minecraft.getInstance().level,pos,new BlockState(),previous);
    check(model.getQuads(new BlockState(),null,RandomSource.create(),refreshed,RenderType.solid()).isEmpty(),"refresh strips stale road property without mutating captured snapshot");
    RoadTerrainModels.submit(id,RoadTerrainModels.prepare(surface(32),RoadTerrainModels.assets(),null));
    RoadTerrainModels.submit(id,baked);RoadTerrainModels.publish();check(quads(model,new BlockPos(32,99,0)).isEmpty(),"superseded pending geometry cannot resurrect");
    RoadTerrainModels.baked(new ModelEvent.BakingCompleted());RoadTerrainModels.beginFrame();check(quads(model,pos).isEmpty(),"atlas reload invalidates old UV data");
    RoadClientConfig.SURFACE_BACKEND.set(RoadClientConfig.SurfaceBackend.VBO);RoadTerrainModels.beginFrame();check(RoadTerrainModels.assets()==null,"VBO fallback selected");
    check(!Minecraft.getInstance().levelRenderer.dirty.isEmpty(),"local chunk rebuilds requested");
    System.out.println("Terrain40ModelValidation: "+checks+" checks passed (production bridge with explicit API adapters; NOT Forge/Oculus/Embeddium/GPU validation)");
  }
}
