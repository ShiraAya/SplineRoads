package com.sora.splineroads.core;

import com.sora.splineroads.core.RoadGeometry.*;
import java.util.*;

/**
 * Conservative quarter-block XZ footprint (eighth-block on slopes), retaining top/bottom heights.
 * Every triangle is clipped before rasterizing; negative coordinates use floor throughout.
 */
public final class RoadRaster {
  public record Cell(int x, int y, int z) {}

  public record Box(double x0, double y0, double z0, double x1, double y1, double z1) {}

  private record Tile(int x, int z) {}

  private static final class Interval {
    double low, high;

    Interval(double a, double b) {
      low = a;
      high = b;
    }
  }

  /** Immutable four-block spatial bins for nearby collision queries. Fine geometry is unchanged. */
  public static final class Local {
    private record Segment(V l, V r, V ll, V rr, double thickness) {}

    private final Map<Long, List<Segment>> decks = new HashMap<>();
    private final Map<Long, List<RoadStructures.Part>> furniture = new HashMap<>();
    private final int resolution;
    public final int segmentCount;

    public Local(Mesh mesh, List<RoadStructures.Part> parts) {
      resolution = mesh.max().y() - mesh.min().y() - mesh.settings().thickness() > 1e-5 ? 8 : 4;
      int count = 0;
      for (Mesh piece : LaneDeck.rasterPieces(mesh,96)) {
        var points = piece.samples();
        for (int i = 1; i < points.size(); i++) {
          Sample a = points.get(i - 1), b = points.get(i);
          for(var strip:LaneDeck.strips(piece,a,b)) {
          Segment segment=new Segment(strip.al(),strip.ar(),strip.bl(),strip.br(),mesh.settings().thickness());
          var vertices=List.of(segment.l,segment.r,segment.ll,segment.rr);
          add(decks,segment,vertices.stream().mapToDouble(V::x).min().orElseThrow(),vertices.stream().mapToDouble(V::z).min().orElseThrow(),
              vertices.stream().mapToDouble(V::x).max().orElseThrow(),vertices.stream().mapToDouble(V::z).max().orElseThrow());count++;
          }
        }
      }
      segmentCount = count;
      for (var p : parts) {
        double w = p.halfExtent();
        add(
            furniture,
            p,
            Math.min(p.a().x(), p.b().x()) - w,
            Math.min(p.a().z(), p.b().z()) - w,
            Math.max(p.a().x(), p.b().x()) + w,
            Math.max(p.a().z(), p.b().z()) + w);
      }
    }

    private static long key(int x, int z) {
      return ((long) x << 32) ^ (z & 0xffffffffL);
    }

    private static <T> void add(
        Map<Long, List<T>> bins, T value, double minX, double minZ, double maxX, double maxZ) {
      for (int x = (int) Math.floor(minX / 4); x <= (int) Math.floor(maxX / 4); x++)
        for (int z = (int) Math.floor(minZ / 4); z <= (int) Math.floor(maxZ / 4); z++)
          bins.computeIfAbsent(key(x, z), ignored -> new ArrayList<>()).add(value);
    }

    public int candidates(Cell cell) {
      return decks
          .getOrDefault(key(Math.floorDiv(cell.x(), 4), Math.floorDiv(cell.z(), 4)), List.of())
          .size();
    }

    /** Legacy migration enumerates only the requested chunk, never the whole saved road. */
    public Set<Cell> cellsInChunk(int chunkX, int chunkZ) {
      Set<Cell> result = new HashSet<>();
      for (int x = chunkX * 16; x < chunkX * 16 + 16; x++)
        for (int z = chunkZ * 16; z < chunkZ * 16 + 16; z++) {
          long bin = key(Math.floorDiv(x, 4), Math.floorDiv(z, 4));
          result.addAll(deckColumn(x,z).keySet());
          for (var part : furniture.getOrDefault(bin, List.of())) {
            double w = part.halfExtent();
            if (x + 1 <= Math.min(part.a().x(),part.b().x()) - w || x >= Math.max(part.a().x(),part.b().x()) + w
                || z + 1 <= Math.min(part.a().z(),part.b().z()) - w || z >= Math.max(part.a().z(),part.b().z()) + w) continue;
            int low = (int)Math.floor(Math.min(part.a().y(),part.b().y()) - part.verticalFrame());
            int high = (int)Math.ceil(Math.max(part.a().y(),part.b().y()) + part.height() + part.verticalFrame());
            for (int y = low; y < high; y++) {
              Cell cell = new Cell(x,y,z);
              if (!structures(List.of(part),cell).isEmpty()) result.add(cell);
            }
          }
        }
      return result;
    }

    public Map<Cell,List<Box>> deckColumn(int x,int z) {
      Map<Tile,List<Interval>> tiles=new HashMap<>();
      Cell column=new Cell(x,0,z);
      for(var s:decks.getOrDefault(key(Math.floorDiv(x,4),Math.floorDiv(z,4)),List.of())) {
        triangle(tiles,s.l,s.r,s.rr,s.thickness,resolution,column);
        triangle(tiles,s.l,s.rr,s.ll,s.thickness,resolution,column);
      }
      return collect(tiles,resolution,null);
    }

    /** Narrow-phase structural columns for sidewalk headroom; never rasterizes a whole road. */
    public Map<Cell,List<Box>> structureColumn(int x,int z,java.util.function.Predicate<RoadStructures.Part> match) {
      Map<Cell,List<Box>> result=new HashMap<>();
      for(var part:furniture.getOrDefault(key(Math.floorDiv(x,4),Math.floorDiv(z,4)),List.of())) {
        if(!match.test(part))continue;
        double extent=part.halfExtent();
        if(x+1<=Math.min(part.a().x(),part.b().x())-extent||x>=Math.max(part.a().x(),part.b().x())+extent
            ||z+1<=Math.min(part.a().z(),part.b().z())-extent||z>=Math.max(part.a().z(),part.b().z())+extent)continue;
        int low=(int)Math.floor(Math.min(part.a().y(),part.b().y())-part.verticalFrame());
        int high=(int)Math.ceil(Math.max(part.a().y(),part.b().y())+part.height()+part.verticalFrame());
        for(int y=low;y<high;y++) {
          Cell cell=new Cell(x,y,z);
          structures(List.of(part),cell).forEach((c,boxes)->result.computeIfAbsent(c,k->new ArrayList<>()).addAll(boxes));
        }
      }
      return result;
    }

    public boolean structureAt(Cell cell,java.util.function.Predicate<RoadStructures.Part> match) {
      for(var part:furniture.getOrDefault(key(Math.floorDiv(cell.x(),4),Math.floorDiv(cell.z(),4)),List.of()))
        if(match.test(part)&&!structures(List.of(part),cell).isEmpty())return true;
      return false;
    }

    public List<Box> boxes(Cell cell) {
      long key = key(Math.floorDiv(cell.x(), 4), Math.floorDiv(cell.z(), 4));
      Map<Tile, List<Interval>> tiles = new HashMap<>();
      for (var s : decks.getOrDefault(key, List.of())) {
        double top = Math.max(Math.max(s.l.y(), s.r.y()), Math.max(s.ll.y(), s.rr.y()));
        double bottom =
            Math.min(Math.min(s.l.y(), s.r.y()), Math.min(s.ll.y(), s.rr.y())) - s.thickness;
        if (cell.y() >= top || cell.y() + 1 <= bottom) continue;
        triangle(tiles, s.l, s.r, s.rr, s.thickness, resolution, cell);
        triangle(tiles, s.l, s.rr, s.ll, s.thickness, resolution, cell);
      }
      List<Box> boxes =
          new ArrayList<>(collect(tiles, resolution, cell).getOrDefault(cell, List.of()));
      boxes.addAll(
          structures(furniture.getOrDefault(key, List.of()), cell).getOrDefault(cell, List.of()));
      return List.copyOf(boxes);
    }
  }

  public static Map<Cell, List<Box>> raster(Mesh mesh) {
    return raster(mesh, null);
  }

  public static Map<Cell, List<Box>> raster(Mesh mesh, Cell only) {
    int resolution = mesh.max().y() - mesh.min().y() - mesh.settings().thickness() > 1e-5 ? 8 : 4;
    if (mesh.length() <= 256) return rasterPart(mesh, only, resolution);
    // Rasterize long roads in bounded strips without retaining millions of sub-block tiles.
    Map<Cell, List<Box>> result = new HashMap<>();
    for (Mesh part : LaneDeck.rasterPieces(mesh,96)) {
      if (only != null
          && (only.x() + 1 < part.min().x()
              || only.x() > part.max().x()
              || only.z() + 1 < part.min().z()
              || only.z() > part.max().z())) continue;
      rasterPart(part, only, resolution)
          .forEach(
              (cell, boxes) ->
                  result.computeIfAbsent(cell, ignored -> new ArrayList<>()).addAll(boxes));
      if (result.size() > RoadLimits.MAX_BODY_CELLS)
        throw new IllegalArgumentException("道路实体占位过大，请增加中间端点");
    }
    result.replaceAll((cell, boxes) -> compact(boxes));
    return result;
  }

  private static Map<Cell, List<Box>> rasterPart(Mesh mesh, Cell only, int resolution) {
    Map<Tile, List<Interval>> tiles = new HashMap<>();
    var points = mesh.samples();
    double step = 1.0 / resolution;
    for (int i = 1; i < points.size(); i++) {
      Sample a = points.get(i - 1), b = points.get(i);
      for(var strip:LaneDeck.strips(mesh,a,b)) {
        triangle(tiles,strip.al(),strip.ar(),strip.br(),mesh.settings().thickness(),resolution,only);
        triangle(tiles,strip.al(),strip.br(),strip.bl(),mesh.settings().thickness(),resolution,only);
      }
      if (tiles.size() > 1500000) throw new IllegalArgumentException("碰撞面积过大，请将道路分为多段");
    }
    return collect(tiles, resolution, only);
  }

  private static Map<Cell, List<Box>> collect(
      Map<Tile, List<Interval>> tiles, int resolution, Cell only) {
    double step = 1.0 / resolution;
    Map<Cell, List<Box>> result = new HashMap<>();
    for (var entry : tiles.entrySet()) {
      Tile tile = entry.getKey();
      double x0 = tile.x * step, z0 = tile.z * step;
      int x = Math.floorDiv(tile.x, resolution), z = Math.floorDiv(tile.z, resolution);
      for (Interval v : entry.getValue()) {
        int minY = (int) Math.floor(v.low), maxY = (int) Math.ceil(v.high - 1e-8) - 1;
        for (int y = minY; y <= maxY; y++) {
          if (only != null && y != only.y()) continue;
          Box box =
              new Box(
                  x0 - x,
                  Math.max(0, v.low - y),
                  z0 - z,
                  x0 + step - x,
                  Math.min(1, v.high - y),
                  z0 + step - z);
          if (box.y1 - box.y0 > 1e-8)
            result.computeIfAbsent(new Cell(x, y, z), k -> new ArrayList<>()).add(box);
        }
      }
    }
    if (result.size() > RoadLimits.MAX_BODY_CELLS)
      throw new IllegalArgumentException("道路实体占位过大，请增加中间端点");
    result.replaceAll((cell, boxes) -> compact(boxes));
    return result;
  }

  private static void triangle(
      Map<Tile, List<Interval>> tiles, V a, V b, V c, double thickness, int resolution, Cell only) {
    double step = 1.0 / resolution;
    int x0 = (int) Math.floor(Math.min(a.x(), Math.min(b.x(), c.x())) * resolution),
        x1 = (int) Math.ceil(Math.max(a.x(), Math.max(b.x(), c.x())) * resolution) - 1;
    int z0 = (int) Math.floor(Math.min(a.z(), Math.min(b.z(), c.z())) * resolution),
        z1 = (int) Math.ceil(Math.max(a.z(), Math.max(b.z(), c.z())) * resolution) - 1;
    if (only != null) {
      x0 = Math.max(x0, only.x() * resolution);
      x1 = Math.min(x1, (only.x() + 1) * resolution - 1);
      z0 = Math.max(z0, only.z() * resolution);
      z1 = Math.min(z1, (only.z() + 1) * resolution - 1);
    }
    if (x0 > x1 || z0 > z1) return;
    // A triangle clipped by a rectangle has at most seven vertices. Reuse two arrays per
    // triangle instead of allocating four lists and many vectors for every 1/8-block tile.
    double[] poly = new double[36], scratch = new double[36];
    for (int x = x0; x <= x1; x++)
      for (int z = z0; z <= z1; z++) {
        poly[0]=a.x();poly[1]=a.y();poly[2]=a.z();
        poly[3]=b.x();poly[4]=b.y();poly[5]=b.z();
        poly[6]=c.x();poly[7]=c.y();poly[8]=c.z();
        int n=clip(poly,9,scratch,0,x*step,true);
        n=clip(scratch,n,poly,0,(x+1)*step,false);
        n=clip(poly,n,scratch,2,z*step,true);
        n=clip(scratch,n,poly,2,(z+1)*step,false);
        if(n<9||area(poly,n)<1e-11)continue;
        double min=Double.MAX_VALUE,max=-Double.MAX_VALUE;
        for(int i=1;i<n;i+=3){min=Math.min(min,poly[i]);max=Math.max(max,poly[i]);}
        Interval next = new Interval(min - thickness, max);
        var list = tiles.computeIfAbsent(new Tile(x, z), k -> new ArrayList<>());
        for (var it = list.iterator(); it.hasNext(); ) {
          Interval old = it.next();
          if (next.low <= old.high + 1e-7 && next.high >= old.low - 1e-7) {
            next.low = Math.min(next.low, old.low);
            next.high = Math.max(next.high, old.high);
            it.remove();
          }
        }
        list.add(next);
      }
  }

  /** Merge flat tile runs before storing them; interior slabs normally become one box. */
  public static List<Box> compact(List<Box> input) {
    List<Box> out = new ArrayList<>(input);
    Comparator<Box> order =
        Comparator.comparingDouble(Box::y0)
            .thenComparingDouble(Box::y1)
            .thenComparingDouble(Box::z0)
            .thenComparingDouble(Box::z1)
            .thenComparingDouble(Box::x0)
            .thenComparingDouble(Box::x1);
    out.sort(order);
    for (int axis = 0; axis < 2; axis++) {
      for (int i = 0; i < out.size(); i++)
        for (int j = i + 1; j < out.size(); ) {
          Box a = out.get(i), b = out.get(j);
          boolean height = Math.abs(a.y0 - b.y0) < 1e-9 && Math.abs(a.y1 - b.y1) < 1e-9;
          boolean merge =
              height
                  && (axis == 0
                      ? a.z0 == b.z0 && a.z1 == b.z1 && (a.x1 == b.x0 || b.x1 == a.x0)
                      : a.x0 == b.x0 && a.x1 == b.x1 && (a.z1 == b.z0 || b.z1 == a.z0));
          if (merge) {
            out.set(
                i,
                new Box(
                    Math.min(a.x0, b.x0),
                    a.y0,
                    Math.min(a.z0, b.z0),
                    Math.max(a.x1, b.x1),
                    a.y1,
                    Math.max(a.z1, b.z1)));
            out.remove(j);
            j = i + 1;
          } else j++;
        }
    }
    out.sort(order);
    return out;
  }

  public static Map<Cell, List<Box>> structures(List<RoadStructures.Part> parts, Cell only) {
    Map<Cell, List<Box>> out = new HashMap<>();
    for (var part : parts) {
      if (only != null) {
        double w = part.halfExtent();
        if (only.x() + 1 <= Math.min(part.a().x(), part.b().x()) - w
            || only.x() >= Math.max(part.a().x(), part.b().x()) + w
            || only.z() + 1 <= Math.min(part.a().z(), part.b().z()) - w
            || only.z() >= Math.max(part.a().z(), part.b().z()) + w
            || only.y() + 1 <= Math.min(part.a().y(), part.b().y()) - part.verticalFrame()
            || only.y() >= Math.max(part.a().y(), part.b().y()) + part.height() + part.verticalFrame()) continue;
      }
      if (part.pier()) {
        V a = part.a();
        double w = part.halfExtent();
        addBox(
            out,
            a.x() - w,
            a.y(),
            a.z() - w,
            a.x() + w,
            a.y() + part.height(),
            part.b().z() + w,
            only);
      } else if (!RoadSignals.signal(part) && !RoadPoleModel.support(part)) {
        var top = part.base().stream().map(v -> v.add(new V(0, part.height(), 0))).toList();
        Map<Tile, List<Interval>> tiles = new HashMap<>();
        triangle(tiles, top.get(0), top.get(1), top.get(2), part.height(), 4, only);
        triangle(tiles, top.get(0), top.get(2), top.get(3), part.height(), 4, only);
        collect(tiles, 4, only).forEach((cell, boxes) ->
            out.computeIfAbsent(cell, k -> new ArrayList<>()).addAll(boxes));
      } else {
        // Authored CB meshes do not use the generic prism footprint.
        var vertices = part.faces().stream().flatMap(f -> f.points().stream()).toList();
        addBox(out, vertices.stream().mapToDouble(V::x).min().orElseThrow(),
            vertices.stream().mapToDouble(V::y).min().orElseThrow(),
            vertices.stream().mapToDouble(V::z).min().orElseThrow(),
            vertices.stream().mapToDouble(V::x).max().orElseThrow(),
            vertices.stream().mapToDouble(V::y).max().orElseThrow(),
            vertices.stream().mapToDouble(V::z).max().orElseThrow(), only);
      }
    }
    return out;
  }

  private static void addBox(
      Map<Cell, List<Box>> out,
      double x0,
      double y0,
      double z0,
      double x1,
      double y1,
      double z1,
      Cell only) {
    int minX = (int) Math.floor(x0),
        minY = (int) Math.floor(y0),
        minZ = (int) Math.floor(z0),
        maxX = (int) Math.ceil(x1) - 1,
        maxY = (int) Math.ceil(y1) - 1,
        maxZ = (int) Math.ceil(z1) - 1;
    if (only != null) {
      minX = Math.max(minX, only.x);
      maxX = Math.min(maxX, only.x);
      minY = Math.max(minY, only.y);
      maxY = Math.min(maxY, only.y);
      minZ = Math.max(minZ, only.z);
      maxZ = Math.min(maxZ, only.z);
    }
    for (int x = minX; x <= maxX; x++)
      for (int y = minY; y <= maxY; y++)
        for (int z = minZ; z <= maxZ; z++)
          out.computeIfAbsent(new Cell(x, y, z), k -> new ArrayList<>())
              .add(
                  new Box(
                      Math.max(0, x0 - x),
                      Math.max(0, y0 - y),
                      Math.max(0, z0 - z),
                      Math.min(1, x1 - x),
                      Math.min(1, y1 - y),
                      Math.min(1, z1 - z)));
  }

  private static int clip(double[] in,int length,double[] out,int axis,double edge,boolean above) {
    if(length==0)return 0;
    int previous=length-3,count=0;
    boolean pi=above?in[previous+axis]>=edge:in[previous+axis]<=edge;
    for(int p=0;p<length;p+=3){
      boolean inside=above?in[p+axis]>=edge:in[p+axis]<=edge;
      if(inside!=pi){
        double t=(edge-in[previous+axis])/(in[p+axis]-in[previous+axis]);
        for(int k=0;k<3;k++)out[count++]=in[previous+k]+(in[p+k]-in[previous+k])*t;
      }
      if(inside){out[count++]=in[p];out[count++]=in[p+1];out[count++]=in[p+2];}
      previous=p;pi=inside;
    }
    return count;
  }
  private static double area(double[] p,int length){
    double sum=0;
    for(int i=3;i<length-3;i+=3)sum+=(p[i]-p[0])*(p[i+5]-p[2])-(p[i+2]-p[2])*(p[i+3]-p[0]);
    return Math.abs(sum)*.5;
  }

  private RoadRaster() {}
}
