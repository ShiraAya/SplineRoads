#!/usr/bin/env python3
"""Verbatim production VBO cache methods + fake handles. NOT full renderer/GL test."""
from pathlib import Path
import subprocess,sys
root=Path(__file__).resolve().parents[1];out=Path(sys.argv[1]).resolve();cp=sys.argv[2];out.mkdir(parents=True,exist_ok=True)
s=(root/'src/main/java/com/sora/splineroads/client/RoadRenderer.java').read_text()
a=s[s.index('  private static final class GpuSection {'):s.index('  public static void changed(')].replace('net.minecraft.core.SectionPos','Section')
b=s[s.index('  static void shaderMode('):s.index('  private static double buildDistance(')]
header='''package com.sora.splineroads.client;
import java.util.*;import com.sora.splineroads.core.*;import com.sora.splineroads.core.RoadRenderMesh.Section;import net.minecraft.world.phys.AABB;import com.sora.splineroads.config.RoadClientConfig;
public final class VboCache404 {
  static class Matrix4f{}static class RoadLightSnapshot{}
  static class VertexBuffer{static int made,closed;boolean dead;VertexBuffer(){made++;}void close(){if(dead)throw new AssertionError("double free");dead=true;closed++;}}
  static class Upload{void cancel(){cancelled++;}}
  record Prepared(AABB bounds){}
  static Map<Section,GpuSection> regions=new LinkedHashMap<>(),dormant=new LinkedHashMap<>();static Map<UUID,Set<Section>> roadSections=new HashMap<>();static Map<UUID,Object> signalBatches=new HashMap<>();static List<GpuSection> visible=new ArrayList<>();static boolean extendedEncoding;static long useClock,retainedBytes,lastBudget=-1;static Upload pendingUpload;static int checks,cancelled;
  static void clearPreviewBuffer(){}static void check(boolean b,String why){checks++;if(!b)throw new AssertionError(why);}
'''
footer='''
  static void bake(GpuSection s){s.close();s.pavement.add(new VertexBuffer());s.paint.add(new VertexBuffer());s.near.put(RoadSurface.Texture.PLAIN,new ArrayList<>(List.of(new VertexBuffer())));s.far.put(RoadSurface.Texture.PLAIN,new ArrayList<>(List.of(new VertexBuffer())));s.nearVertices=120;s.farVertices=100;s.surfaceVertices=60;s.dirty=false;}
  public static void main(String[]args){
    RoadClientConfig.INACTIVE_VBO_CACHE_MIB.set(64);UUID id=new UUID(404,1);Section key=new Section(0,0,0);var p=new Prepared(new AABB(0,0,0,1,1,1));
    installPieces(regions,id,Set.of(),Map.of(key,p),false);roadSections.put(id,Set.of(key));var vanilla=regions.get(key);bake(vanilla);var v=vanilla.pavement.get(0);
    pendingUpload=new Upload();shaderMode(true);check(cancelled==1&&pendingUpload==null,"partial old encoding canceled");var iris=regions.get(key);check(iris!=vanilla&&iris.pieces.get(id)==p,"independent GPU variants share CPU source");bake(iris);var e=iris.pavement.get(0);int made=VertexBuffer.made;
    for(int i=0;i<100;i++){shaderMode(false);check(regions.get(key).pavement.get(0)==v,"vanilla handle reused");shaderMode(true);check(regions.get(key).pavement.get(0)==e,"extended handle reused");}
    check(VertexBuffer.made==made&&VertexBuffer.closed==0,"no allocation/free after warmup");
    var next=new Prepared(new AABB(0,0,0,2,2,2));installPieces(regions,id,Set.of(key),Map.of(key,next),false);installPieces(dormant,id,Set.of(key),Map.of(key,next),true);check(v.dead&&vanilla.dirty&&iris.dirty,"real edit invalidates both");
    bake(iris);shaderMode(false);bake(vanilla);shaderMode(true);RoadClientConfig.INACTIVE_VBO_CACHE_MIB.set(0);shaderMode(true);check(vanilla.pavement.isEmpty()&&vanilla.dirty,"zero budget immediately releases dormant resources");remove(id);check(regions.isEmpty()&&dormant.isEmpty(),"delete clears both variants");check(VertexBuffer.made==VertexBuffer.closed,"all handles closed exactly once");
    RoadClientConfig.INACTIVE_VBO_CACHE_MIB.set(1);for(int i=0;i<3;i++){var k=new Section(i,0,0);var sec=new GpuSection(k);bake(sec);sec.nearVertices=10000;sec.farVertices=0;sec.lastUsed=i;dormant.put(k,sec);}trimDormant();check(retainedBytes<=1048576,"dormant budget bounded");check(dormant.get(new Section(0,0,0)).pavement.isEmpty()&&!dormant.get(new Section(2,0,0)).pavement.isEmpty(),"LRU keeps recent");for(var sec:dormant.values())sec.close();check(VertexBuffer.made==VertexBuffer.closed,"eviction cleanup does not leak dummy handles");
    System.out.println("VboCache404: "+checks+" checks PASS; verbatim cache methods, 100 toggle pairs. Fake resource handles, NOT full renderer/OpenGL/Minecraft.");
  }
}
class RoadTerrainModels{static void remove(UUID id){}}
'''
p=out/'VboCache404.java';p.write_text(header+a+b+footer)
subprocess.run(['javac','--release','17','-encoding','UTF-8','-cp',cp,'-d',str(out),str(p)],check=True)
subprocess.run(['java','-Dfile.encoding=UTF-8','-cp',str(out)+':'+cp,'com.sora.splineroads.client.VboCache404'],check=True)
