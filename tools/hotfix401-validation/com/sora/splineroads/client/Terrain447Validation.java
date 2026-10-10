package com.sora.splineroads.client;
import com.sora.splineroads.core.*;
import com.sora.splineroads.core.RoadGeometry.V;
import com.sora.splineroads.core.RoadTerrainMesh.*;
import com.sora.splineroads.config.RoadClientConfig;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.Minecraft;
import net.minecraft.core.Direction;
import net.minecraftforge.client.event.ModelEvent;
import java.util.*;
public final class Terrain447Validation {
 static int checks;static void check(boolean ok,String why){checks++;if(!ok)throw new AssertionError(why);}
 static RoadSurface.Face face(double x0,double x1,double y){return new RoadSurface.Face(List.of(new V(x0,y,0),new V(x0,y,1),new V(x1,y,1),new V(x1,y,0)),0xeeeeee);}
 static void projection(){
  var deck=new RoadSurface.Face(List.of(new V(0,100,0),new V(0,100.2,2),new V(2,100,2),new V(2,100.2,0)),0x444444);
  var paint=new RoadSurface.Face(List.of(new V(.8,100.01,0),new V(.8,100.01,2),new V(1.2,100.01,2),new V(1.2,100.01,0)),0xeeeeee);
  var result=RoadTerrainMesh.build(new RoadSurface.Geometry(List.of(deck),List.of(paint)));int marks=0;
  for(var e:result.cells().entrySet())for(var polygon:e.getValue())if(polygon.paint()){
   check(RoadLighting.normal(new RoadSurface.Face(polygon.vertices(),polygon.color())).y()>0,"projected paint faces underground and is backface-culled");
   marks++;check(e.getValue().stream().anyMatch(p->!p.paint()&&polygon.vertices().stream().allMatch(v->Math.abs(v.y()-RoadTerrainMesh.height(p.vertices(),v))<1e-7)),"marking did not follow a real deck triangle in its collision cell");
  }
  check(marks>0,"test has no projected paint");
 }
 static BakedQuad top(double x0,double x1,double y,TextureAtlasSprite sprite){
  int[] v=new int[32];var points=face(x0,x1,y).points();for(int i=0;i<4;i++){
   var p=points.get(i);v[i*8]=Float.floatToRawIntBits((float)p.x());v[i*8+1]=Float.floatToRawIntBits((float)y);v[i*8+2]=Float.floatToRawIntBits((float)p.z());v[i*8+3]=-1;
   v[i*8+4]=Float.floatToRawIntBits(sprite.getU(p.x()*16));v[i*8+5]=Float.floatToRawIntBits(sprite.getV(p.z()*16));
  }return new BakedQuad(v,0,Direction.UP,sprite,true);
 }
 static double area(List<BakedQuad> quads){double area=0;for(var q:quads){var ps=new ArrayList<V>();for(int i=0;i<4;i++){var v=q.getVertices();ps.add(new V(Float.intBitsToFloat(v[8*i]),Float.intBitsToFloat(v[8*i+1]),Float.intBitsToFloat(v[8*i+2])));}area+=RoadTerrainMesh.area(ps);}return area;}
 static void fill(){
  var sprite=new TextureAtlasSprite();var grass=top(0,1,1,sprite);var half=top(.5,1,1.001,sprite);
  var remaining=RoadTerrainModels.exposedFill(List.of(grass),List.of(half));
  check(Math.abs(area(remaining)-.5)<1e-6,"grass under the road still renders, or grass outside road removed");
  check(RoadTerrainModels.exposedFill(List.of(grass),List.of(top(0,1,1.001,sprite))).isEmpty(),"fully covered grass top remains");
  check(RoadTerrainModels.exposedFill(List.of(grass),List.of(top(0,1,2,sprite))).equals(List.of(grass)),"elevated road erased ground underneath");
  for(var q:remaining){check(q.getTintIndex()==0&&q.getSprite()==sprite,"clipped grass lost biome tint/texture");var v=q.getVertices();for(int i=0;i<4;i++)check(Float.intBitsToFloat(v[8*i+4])<=sprite.getU(8)+1e-6,"clipped fill UV stretched");}
 }
 static void cache(){
  var mc=Minecraft.getInstance();mc.level.loadedChunks=new HashSet<>(Set.of(0L));RoadRenderer.executor=Runnable::run;
  RoadTerrainModels.clear();RoadClientConfig.SURFACE_BACKEND.set(RoadClientConfig.SurfaceBackend.TERRAIN);
  RoadTerrainModels.baked(new ModelEvent.BakingCompleted());RoadTerrainModels.beginFrame();
  var geometry=new RoadSurface.Geometry(List.of(face(0,1,100)),List.of());
  RoadTerrainModels.install(new UUID(447,1),RoadTerrainMesh.source(geometry));TerrainStreaming401Validation.drain();
  long baked=RoadTerrainModels.stats().baked();check(baked==1,"tile not actually baked");
  for(int i=0;i<5;i++){
   RoadClientConfig.SURFACE_BACKEND.set(RoadClientConfig.SurfaceBackend.VBO);RoadTerrainModels.beginFrame();
   RoadTerrainModels.baked(new ModelEvent.BakingCompleted()); // New sprite identity on EVERY switch.
   RoadClientConfig.SURFACE_BACKEND.set(RoadClientConfig.SurfaceBackend.TERRAIN);RoadTerrainModels.beginFrame();TerrainStreaming401Validation.drain();
   check(RoadTerrainModels.stats().baked()==baked&&RoadTerrainModels.stats().active()==1,"shader resource reload retriangulated a valid cached tile");
  }
  check(RoadTerrainModels.stats().hits()==5,"atlas reload lost LRU reuse");check(mc.levelRenderer.resets==0,"shader toggle forces a global world reload");
  var old=RoadTerrainModels.assets();var remapped=new TextureAtlasSprite(){public float getU(double u){return (float)(.55+u*.002);}public float getV(double v){return (float)(.7+v*.003);}};
  var prepared=RoadTerrainModels.prepare(geometry,old,null);var next=RoadTerrainModels.rebind(prepared,old,new RoadTerrainModels.Assets(remapped,remapped));
  for(var cells:next.sections().values())for(var quads:cells.values())for(var q:quads){check(q.getSprite()==remapped,"retained quad still references discarded atlas sprite");var v=q.getVertices();for(int i=0;i<4;i++)check(Float.intBitsToFloat(v[8*i+4])>=.55&&Float.intBitsToFloat(v[8*i+4])<=.583,"changed atlas layout reused obsolete UVs");}
  RoadTerrainModels.install(new UUID(447,1),RoadTerrainMesh.source(new RoadSurface.Geometry(List.of(face(0,1,101)),List.of())));TerrainStreaming401Validation.drain();
  check(RoadTerrainModels.stats().baked()==baked+1,"actual road edit reused stale geometry");
 }
 public static void main(String[]args){projection();fill();cache();System.out.println("Terrain447Validation: "+checks+" checks PASS; production projection/fill clipping/atlas-rebind cache with API adapters, not GPU");}
}
