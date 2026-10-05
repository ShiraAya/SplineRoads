package com.sora.splineroads.core;

import com.sora.splineroads.core.RoadGeometry.*;

public final class RoadQueries {
  public record Projection(Sample sample, V tangent, double lateral, double horizontalDistance) {}

  // Meshes are immutable. Bound retention per worker and use identity to avoid hashing all samples.
  private static final ThreadLocal<java.util.IdentityHashMap<Mesh, RoadProjection>> PROJECTIONS =
      ThreadLocal.withInitial(java.util.IdentityHashMap::new);

  private static RoadProjection index(Mesh mesh) {
    var cache = PROJECTIONS.get();
    var found = cache.get(mesh);
    if (found != null) return found;
    if (cache.size() >= 128) cache.clear();
    found = new RoadProjection(mesh);
    cache.put(mesh, found);
    return found;
  }

  public static Projection project(Mesh mesh, V point) {
    return index(mesh).project(point, false);
  }

  public static Projection horizontal(Mesh mesh, V point) {
    return index(mesh).project(point, true);
  }

  public static boolean contains(Mesh mesh, V point, double margin, double heightTolerance) {
    if (point.x() < mesh.min().x() - margin
        || point.x() > mesh.max().x() + margin
        || point.z() < mesh.min().z() - margin
        || point.z() > mesh.max().z() + margin
        || point.y() < mesh.min().y() - heightTolerance
        || point.y() > mesh.max().y() + heightTolerance) return false;
    var p = project(mesh, point);
    // A road ribbon has flat ends. Nearest-point distance alone creates semicircular caps
    // and incorrectly considers the adjoining road's guardrail to be inside this road.
    if (!mesh.closed()) {
      V forwardA = mesh.first().left().left().mul(-1), forwardB = mesh.last().left().left().mul(-1);
      if (p.sample().distance() < 1e-7 && point.sub(mesh.first().center()).dot(forwardA) < -1e-7)
        return false;
      if (p.sample().distance() > mesh.length() - 1e-7
          && point.sub(mesh.last().center()).dot(forwardB) > 1e-7) return false;
    }
    return Math.abs(p.lateral()) <= p.sample().halfWidth() + margin
        && Math.abs(point.y() - p.sample().center().y()) <= heightTolerance;
  }

  /** Legacy full-width Y branches retain their graded, shared throat. */
  public static boolean joins(Mesh road, Mesh other, V point) {
    boolean legacyFork =
        (road.settings().style().ramp() && !road.settings().laneRamp())
            || (other.settings().style().ramp() && !other.settings().laneRamp());
    boolean shared =
        road.first().center().distance(other.first().center()) < .01
            || road.first().center().distance(other.last().center()) < .01
            || road.last().center().distance(other.first().center()) < .01
            || road.last().center().distance(other.last().center()) < .01;
    return contains(other, point, -.02, legacyFork && shared ? 1.05 : .12);
  }

  public static double ray(Mesh mesh, V origin, V direction, double limit) {
    double best = limit;
    for (int i = 1; i < mesh.samples().size(); i++) {
      Sample a = mesh.samples().get(i - 1), b = mesh.samples().get(i);
      V l = a.at(a.halfWidth(), 0),
          r = a.at(-a.halfWidth(), 0),
          ll = b.at(b.halfWidth(), 0),
          rr = b.at(-b.halfWidth(), 0);
      best = Math.min(best, triangle(origin, direction, l, r, rr));
      best = Math.min(best, triangle(origin, direction, l, rr, ll));
    }
    return best < limit ? best : Double.POSITIVE_INFINITY;
  }

  private static double triangle(V o, V d, V a, V b, V c) {
    V e = b.sub(a), f = c.sub(a), p = cross(d, f);
    double det = e.dot(p);
    if (Math.abs(det) < 1e-10) return Double.POSITIVE_INFINITY;
    V t = o.sub(a);
    double u = t.dot(p) / det;
    if (u < 0 || u > 1) return Double.POSITIVE_INFINITY;
    V q = cross(t, e);
    double v = d.dot(q) / det;
    if (v < 0 || u + v > 1) return Double.POSITIVE_INFINITY;
    double hit = f.dot(q) / det;
    return hit >= 0 ? hit : Double.POSITIVE_INFINITY;
  }

  private static V cross(V a, V b) {
    return new V(
        a.y() * b.z() - a.z() * b.y(),
        a.z() * b.x() - a.x() * b.z(),
        a.x() * b.y() - a.y() * b.x());
  }

  private RoadQueries() {}
}
