package com.sora.splineroads.core;
import com.sora.splineroads.core.RoadGeometry.V;
import com.sora.splineroads.core.RoadTerrainMesh.*;
import java.util.*;
public final class TerrainChunk401Validation {
  static int checks;
  static void check(boolean v,String why){checks++;if(!v)throw new AssertionError(why);}
  static RoadSurface.Geometry shape(int i){
    double x=-31.7+i*.31,z=-18.3+i*.11,y=60+(i%5)*.25,dy=i%3==0?0:7.15;
    return new RoadSurface.Geometry(List.of(new RoadSurface.Face(List.of(new V(x,y,z),new V(x-5,y,z+41.3),new V(x+51,y+dy,z+52),new V(x+62,y+dy,z+2)),0x343434)),
      List.of(new RoadSurface.Face(List.of(new V(x+12,y+dy*.3,z+10),new V(x+12.2,y+dy*.3,z+10),new V(x+18.2,y+dy*.7,z+33),new V(x+18,y+dy*.7,z+33)),0xffffff)));
  }
  public static void main(String[] args){
    for(int i=0;i<12;i++){
      var g=shape(i);var full=RoadTerrainMesh.build(g);var source=RoadTerrainMesh.source(g);
      var combined=new HashMap<Cell,List<Polygon>>();int polygons=0;
      for(var entry:source.chunks().entrySet()){
        var part=RoadTerrainMesh.build(entry.getValue(),Set.of(entry.getKey()));polygons+=part.polygons();combined.putAll(part.cells());
        for(var e:part.cells().entrySet()){
          check(e.getValue().equals(full.cells().get(e.getKey())),"per-tile decomposition preserves exact vertex values/owner/color");
          check(e.getKey().section().x()==entry.getKey().x()&&e.getKey().section().z()==entry.getKey().z(),"tile doesn't publish neighboring chunk cells");
        }
      }
      check(combined.equals(full.cells())&&polygons==full.polygons(),"full coverage identical including negative coords/slopes/paint");
      check(RoadTerrainMesh.build(g,Set.of()).cells().isEmpty(),"empty loaded set is empty result");
    }
    // A huge face filtered to one tile must be bounded before cell scanning (not scan 65 km).
    var huge=new RoadSurface.Geometry(List.of(new RoadSurface.Face(List.of(new V(0,100,1),new V(0,100,3),new V(65536,100,3),new V(65536,100,1)),0x404040)),List.of());
    var one=RoadTerrainMesh.build(huge,Set.of(new Chunk(512,0)));
    check(one.cells().size()==32,"large spanning face rasterized only in selected 16x16 chunk");
    System.out.println("TerrainChunk401Validation: "+checks+" checks passed (production CPU clipping, no block/render world)");
  }
}
