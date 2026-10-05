package com.sora.splineroads.core;

import com.sora.splineroads.core.RoadGeometry.*;
import java.util.*;

/**
 * Conservative quarter-block XZ footprint (eighth-block on slopes), retaining top/bottom heights.
 * Every triangle is clipped before rasterizing; negative coordinates use floor throughout.
 */
public final class LegacyRaster232 {
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
      for (Mesh piece : mesh.length() <= 256 ? List.of(mesh) : RoadRibbon.split(mesh, 96)) {
        var points = piece.samples();
        for (int i = 1; i < points.size(); i++) {
          Sample a = points.get(i - 1), b = points.get(i);
          Segment segment =
              new Segment(
                  a.at(a.halfWidth(), 0),
                  a.at(-a.halfWidth(), 0),
                  b.at(b.halfWidth(), 0),
                  b.at(-b.halfWidth(), 0),
                  mesh.settings().thickness());
          var vertices = List.of(segment.l, segment.r, segment.ll, segment.rr);
          add(
              decks,
              segment,
              vertices.stream().mapToDouble(V::x).min().orElseThrow(),
              vertices.stream().mapToDouble(V::z).min().orElseThrow(),
              vertices.stream().mapToDouble(V::x).max().orElseThrow(),
              vertices.stream().mapToDouble(V::z).max().orElseThrow());
          count++;
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
    for (Mesh part : RoadRibbon.split(mesh, 96)) {
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
      V l = a.at(a.halfWidth(), 0),
          r = a.at(-a.halfWidth(), 0),
          ll = b.at(b.halfWidth(), 0),
          rr = b.at(-b.halfWidth(), 0);
      triangle(tiles, l, r, rr, mesh.settings().thickness(), resolution, only);
      triangle(tiles, l, rr, ll, mesh.settings().thickness(), resolution, only);
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
    for (int x = x0; x <= x1; x++)
      for (int z = z0; z <= z1; z++) {
        List<V> poly = List.of(a, b, c);
        poly = clip(poly, 0, x * step, true);
        poly = clip(poly, 0, (x + 1) * step, false);
        poly = clip(poly, 2, z * step, true);
        poly = clip(poly, 2, (z + 1) * step, false);
        if (poly.size() < 3 || area(poly) < 1e-11) continue;
        double min = Double.MAX_VALUE, max = -Double.MAX_VALUE;
        for (V p : poly) {
          min = Math.min(min, p.y());
          max = Math.max(max, p.y());
        }
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
            || only.y() + 1 <= Math.min(part.a().y(), part.b().y())
            || only.y() >= Math.max(part.a().y(), part.b().y()) + part.height()) continue;
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

  private static double coord(V p, int axis) {
    return axis == 0 ? p.x() : p.z();
  }

  private static List<V> clip(List<V> input, int axis, double edge, boolean above) {
    if (input.isEmpty()) return input;
    List<V> out = new ArrayList<>();
    V prev = input.get(input.size() - 1);
    boolean pi = above ? coord(prev, axis) >= edge : coord(prev, axis) <= edge;
    for (V p : input) {
      boolean inside = above ? coord(p, axis) >= edge : coord(p, axis) <= edge;
      if (inside != pi) {
        double t = (edge - coord(prev, axis)) / (coord(p, axis) - coord(prev, axis));
        out.add(prev.add(p.sub(prev).mul(t)));
      }
      if (inside) out.add(p);
      prev = p;
      pi = inside;
    }
    return out;
  }

  private static double area(List<V> p) {
    V origin = p.get(0);
    double sum = 0;
    for (int i = 1; i < p.size() - 1; i++) {
      V a = p.get(i).sub(origin), b = p.get(i + 1).sub(origin);
      sum += a.x() * b.z() - a.z() * b.x();
    }
    return Math.abs(sum) * .5;
  }

  private LegacyRaster232() {}
}
