package com.sora.splineroads.core;

import com.sora.splineroads.core.RoadGeometry.V;
import java.util.*;

/**
 * Exact, visual-only clipping of asphalt and road paint to their existing collision-block cells.
 * No world access, atlas access or light sampling: safe on the mesh worker. It does not voxelize
 * the visible surface. Every resulting polygon retains the original sloped plane and boundary.
 */
public final class RoadTerrainMesh {
  public record Cell(int x, int y, int z) {
    public Section section() { return new Section(Math.floorDiv(x,16),Math.floorDiv(y,16),Math.floorDiv(z,16)); }
    public int localKey() { return (x&15)|((z&15)<<4)|((y&15)<<8); }
  }
  public record Section(int x,int y,int z) {}
  public record Chunk(int x,int z) {}
  public record Polygon(List<V> vertices,int color,boolean paint) {
    public Polygon { vertices=List.copyOf(vertices); }
  }
  public record Result(Map<Cell,List<Polygon>> cells,int polygons) {}
  /** Immutable broad-phase face index. Streaming one chunk must not rerun RoadSurface.build
   * or prepare the infrastructure VBOs of the entire road. Lists share immutable face objects. */
  public record Source(Map<Chunk,RoadSurface.Geometry> chunks) {}
  public static Source source(RoadSurface.Geometry geometry) {
    Map<Chunk,List<RoadSurface.Face>> pavement=new LinkedHashMap<>(),paint=new LinkedHashMap<>();
    for(var face:geometry.pavement())if(asphalt(face))index(pavement,face);
    for(var face:geometry.markings())index(paint,face);
    var keys=new LinkedHashSet<>(pavement.keySet());keys.addAll(paint.keySet());
    Map<Chunk,RoadSurface.Geometry> result=new LinkedHashMap<>();
    for(var chunk:keys)result.put(chunk,new RoadSurface.Geometry(
        List.copyOf(pavement.getOrDefault(chunk,List.of())),List.copyOf(paint.getOrDefault(chunk,List.of()))));
    return new Source(Collections.unmodifiableMap(result));
  }
  private static void index(Map<Chunk,List<RoadSurface.Face>> result,RoadSurface.Face face) {
    var points=face.points();if(points.size()<3)return;
    int x0=Math.floorDiv(floor(min(points,0)),16),x1=Math.floorDiv(ceil(max(points,0))-1,16);
    int z0=Math.floorDiv(floor(min(points,2)),16),z1=Math.floorDiv(ceil(max(points,2))-1,16);
    for(int x=x0;x<=x1;x++)for(int z=z0;z<=z1;z++)
      result.computeIfAbsent(new Chunk(x,z),key->new ArrayList<>()).add(face);
  }
  private static final double EPS=1e-8;

  public static Result build(RoadSurface.Geometry geometry) {return build(geometry,null);}
  /** A null filter means all chunks (tests/tools); an empty filter means no loaded terrain. */
  public static Result build(RoadSurface.Geometry geometry,Set<Chunk> loaded) {
    Map<Cell,List<Polygon>> cells=new LinkedHashMap<>();
    for(var face:geometry.pavement()) if(asphalt(face)) add(cells,face,false,loaded);
    for(var face:geometry.markings()) add(cells,face,true,loaded);
    int count=0;
    for(var e:cells.entrySet()) { e.setValue(List.copyOf(e.getValue())); count+=e.getValue().size(); }
    return new Result(Collections.unmodifiableMap(cells),count);
  }
  public static boolean asphalt(RoadSurface.Face face) {
    return face.texture()==RoadSurface.Texture.PLAIN;
  }
  /** Keep bridge undersides, sidewalls and all infrastructure in the independent renderer. */
  public static RoadSurface.Geometry remainder(RoadSurface.Geometry geometry) {
    return new RoadSurface.Geometry(geometry.pavement().stream().filter(f->!asphalt(f)).toList(),List.of());
  }
  private static void add(Map<Cell,List<Polygon>> out,RoadSurface.Face face,boolean paint,Set<Chunk> loaded) {
    List<V> p=face.points();
    if(p.size()<3)return;
    // Triangles also handle the very small non-planarity of curved road quads correctly.
    for(int t=1;t+1<p.size();t++) {
      List<V> tri=List.of(p.get(0),p.get(t),p.get(t+1));
      if(area(tri)<1e-12)continue;
      int x0=floor(min(tri,0)),x1=ceil(max(tri,0)),z0=floor(min(tri,2)),z1=ceil(max(tri,2));
      if(loaded==null)rasterize(out,tri,face.color(),paint,x0,x1,z0,z1);
      else for(var chunk:loaded){
        // Restrict the scan BEFORE clipping. A long face may span kilometres; preparing
        // a single newly loaded chunk must not walk every cell in that face's bounds.
        int cx=chunk.x()*16,cz=chunk.z()*16;
        rasterize(out,tri,face.color(),paint,Math.max(x0,cx),Math.min(x1,cx+16),
            Math.max(z0,cz),Math.min(z1,cz+16));
      }
    }
  }
  private static void rasterize(Map<Cell,List<Polygon>> out,List<V> tri,int color,boolean paint,
      int x0,int x1,int z0,int z1){
    if(x0>=x1||z0>=z1)return;
    for(int x=x0;x<x1;x++){
      var strip=clip(clip(tri,0,x,true),0,x+1,false);
      if(strip.size()<3)continue;
      for(int z=z0;z<z1;z++){
        var tile=clip(clip(strip,2,z,true),2,z+1,false);
        if(tile.size()<3||area(tile)<1e-12)continue;
        // A flat surface on Y=64 belongs to the collider below it, never to air at Y=64.
        double low=min(tile,1),high=max(tile,1);
        int first=ceil(low-EPS)-1,last=ceil(high-EPS)-1;
        for(int y=first;y<=last;y++){
          var part=clip(clip(tile,1,y,true),1,y+1,false);
          if(part.size()<3||area(part)<1e-12)continue;
          if(normalY(part)<0){var reversed=new ArrayList<>(part);Collections.reverse(reversed);part=reversed;}
          out.computeIfAbsent(new Cell(x,y,z),k->new ArrayList<>()).add(new Polygon(part,color,paint));
        }
      }
    }
  }
  private static List<V> clip(List<V> in,int axis,double plane,boolean greater) {
    if(in.isEmpty())return List.of();
    List<V> out=new ArrayList<>(in.size()+2);V a=in.get(in.size()-1);
    double da=coord(a,axis)-plane;boolean ia=greater?da>=-EPS:da<=EPS;
    for(V b:in) {
      double db=coord(b,axis)-plane;boolean ib=greater?db>=-EPS:db<=EPS;
      if(ia!=ib) {
        double t=da/(da-db);V v=a.add(b.sub(a).mul(Math.max(0,Math.min(1,t))));
        out.add(snap(v,axis,plane));
      }
      if(ib)out.add(b);
      a=b;da=db;ia=ib;
    }
    // Boundary vertices otherwise turn a triangle into many degenerate baked quads.
    for(int i=out.size()-1;i>=0&&out.size()>1;i--)
      if(out.get(i).sub(out.get((i+1)%out.size())).dot(out.get(i).sub(out.get((i+1)%out.size())))<1e-20)out.remove(i);
    return out;
  }
  private static V snap(V p,int axis,double value) {return axis==0?new V(value,p.y(),p.z()):axis==1?new V(p.x(),value,p.z()):new V(p.x(),p.y(),value);}
  private static int floor(double x){return (int)Math.floor(x+EPS);}
  private static int ceil(double x){return (int)Math.ceil(x-EPS);}
  private static double coord(V p,int axis){return axis==0?p.x():axis==1?p.y():p.z();}
  private static double min(List<V> p,int axis){double r=Double.POSITIVE_INFINITY;for(V v:p)r=Math.min(r,coord(v,axis));return r;}
  private static double max(List<V> p,int axis){double r=Double.NEGATIVE_INFINITY;for(V v:p)r=Math.max(r,coord(v,axis));return r;}
  public static double area(List<V> p){return Math.abs(normalY(p))*.5;}
  private static double normalY(List<V> p) {
    V o=p.get(0);double sum=0;
    for(int i=1;i+1<p.size();i++){V a=p.get(i).sub(o),b=p.get(i+1).sub(o);sum+=a.z()*b.x()-a.x()*b.z();}
    return sum;
  }
  private RoadTerrainMesh(){}
}
