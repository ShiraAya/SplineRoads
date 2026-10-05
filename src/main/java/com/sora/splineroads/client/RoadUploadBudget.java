package com.sora.splineroads.client;
import com.sora.splineroads.core.*;
import java.util.*;
/** 288 KiB at the current 36-byte NEW_ENTITY vertex stride, not a whole region in one GL call. */
public final class RoadUploadBudget {
  public static final int MAX_VERTICES=8192;
  /** Split exceptional large polygons on the mesh worker, preserving the renderer's triangle fan and UVs. */
  public static List<RoadSurface.Face> faces(List<RoadSurface.Face> input){
    if(input.stream().noneMatch(f->RoadRenderMesh.vertexCount(f)>MAX_VERTICES))return input;
    var out=new ArrayList<RoadSurface.Face>();
    for(var f:input){
      if(RoadRenderMesh.vertexCount(f)<=MAX_VERTICES){out.add(f);continue;}
      for(int first=1;first<f.points().size()-1;){
        int last=Math.min(f.points().size()-1,first+MAX_VERTICES/4);
        var points=new ArrayList<RoadGeometry.V>();points.add(f.points().get(0));points.addAll(f.points().subList(first,last+1));
        var uv=new ArrayList<RoadSurface.UV>();if(!f.uv().isEmpty()){uv.add(f.uv().get(0));uv.addAll(f.uv().subList(first,last+1));}
        out.add(new RoadSurface.Face(List.copyOf(points),f.color(),f.emissive(),f.texture(),List.copyOf(uv)));first=last;
      }
    }
    return List.copyOf(out);
  }
  private RoadUploadBudget(){}
}
