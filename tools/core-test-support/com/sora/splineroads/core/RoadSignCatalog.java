package com.sora.splineroads.core;
import java.util.List;
/** Audit-only fail-fast adapter. No signs, JSON, Minecraft or Forge runtime are exercised. */
public final class RoadSignCatalog {
 public record Field(String key,String label,double x,double y,double scale,String align,int color,String initial){}
 public record Model(String id,String label,double width,double height,double depth,List<Field> fields){}
 public static Model get(String id){throw new UnsupportedOperationException("Sign catalog is outside this isolated audit harness");}
 public static List<RoadSurface.Face> faces(RoadStructures.Part part){throw new UnsupportedOperationException("Sign rendering is outside this isolated audit harness");}
}
