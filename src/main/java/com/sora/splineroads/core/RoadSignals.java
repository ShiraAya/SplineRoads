package com.sora.splineroads.core;

import com.sora.splineroads.core.RoadGeometry.*;
import com.sora.splineroads.core.RoadStructures.*;
import java.util.*;

/** A single approach definition owns the stop, solid approach lines and far-side signal. */
public final class RoadSignals {
  public static final long PHASE_TICKS = 20 * 20;
  public static final double SOLID_APPROACH = 24;

  public record Approach(V inner, V outer, V post, V head, V forward, boolean ramp,
      double station, int direction, double crossingLength) {}

  public static boolean mainGreen(long gameTime) {
    return Math.floorMod(Math.floorDiv(gameTime, PHASE_TICKS), 2) == 0;
  }

  public static boolean signal(Part p) {
    return p.material() == Material.SIGNAL_MAIN || p.material() == Material.SIGNAL_RAMP || RoadJunctionSignalModel.supports(p);
  }

  public static List<Approach> approaches(Mesh road, List<Mesh> neighbors) {
    if (road.closed() || !RoadProfile.modern(road.settings().style())) return List.of();
    List<Approach> out = new ArrayList<>();
    boolean ramp = road.settings().style().ramp();
    for (var pad : neighbors) {
      if (pad.settings().style() != Style.UNMARKED) continue;
      if (ramp) {
        if (!RoadQueries.contains(pad, road.last().center(), .1, .1)) continue;
        for (double d = road.length() - .25; d > 1; d -= .25) {
          Sample at = RoadStructures.sample(road, d);
          if (!RoadQueries.contains(pad, at.center(), .1, .1)) {
            add(out, road, pad, RoadStructures.sample(road, d - .6), 1, true);
            break;
          }
        }
      } else if (RoadProfile.catalog(road.settings().style()).twoWay()) {
        double entry = -1, exit = -1;
        // Use fixed world-space steps: render simplification must not move the stop lines.
        for (double d = 0; d <= road.length(); d += .25) {
          Sample at = RoadStructures.sample(road, d);
          if (!RoadQueries.contains(pad, at.center(), .1, .1)) continue;
          if (entry < 0) entry = d;
          exit = d;
        }
        if (entry > 1 && exit < road.length() - 1) {
          add(out, road, pad, RoadStructures.sample(road, entry - .85), 1, false);
          add(out, road, pad, RoadStructures.sample(road, exit + .85), -1, false);
        }
      }
    }
    return List.copyOf(out);
  }

  private static void add(List<Approach> out, Mesh road, Mesh pad, Sample at,
      int direction, boolean ramp) {
    var l = RoadProfile.layout(road, at);
    int side = l.outside() * direction;
    double inner = ramp ? l.outer(-side) + side * .1 : side * (l.median() / 2 + .1);
    double outer = l.outer(side) - side * .1;
    V forward = at.left().left().mul(-direction);
    V middle = at.at((inner + outer) / 2, 0);
    double far = 0;
    for (double d = .5; d <= 160; d += .5) {
      if (RoadQueries.contains(pad, middle.add(forward.mul(d)), .2, .1)) far = d;
      else if (far > 0) break;
    }
    if (far == 0) return;
    V post = at.at(side * (at.halfWidth() - .28), 0).add(forward.mul(far + .92));
    V head = middle.add(forward.mul(far + .5)).add(new V(0, 7.35, 0));
    var approach = new Approach(at.at(inner, 0), at.at(outer, 0), post, head,
        forward, ramp, at.distance(), direction, far + 2);
    if (out.stream().noneMatch(a -> a.inner().distance(approach.inner()) < 2)) out.add(approach);
  }

  public static boolean solid(List<Approach> approaches, double station, double lateral) {
    for (var a : approaches) {
      double back = (a.station() - station) * a.direction();
      V left = a.forward().left().mul(a.direction());
      double lo = a.inner().sub(a.outer()).dot(left);
      // Sign of the controlled carriageway; one-way ramps control all dividers.
      if (back >= .2 && back <= SOLID_APPROACH
          && (a.ramp() || Math.signum(lateral) == -Math.signum(lo))) return true;
    }
    return false;
  }

  /** Clear every incoming lane up to the far side, including the full stop-line width. */
  public static List<List<V>> paintCuts(List<Approach> approaches) {
    List<List<V>> out = new ArrayList<>();
    for (var a : approaches) {
      V side = a.outer().sub(a.inner()).horizontalUnit();
      V inner = a.inner().sub(side.mul(.2)).sub(a.forward().mul(.25));
      V outer = a.outer().add(side.mul(.2)).sub(a.forward().mul(.25));
      V reach = a.forward().mul(a.crossingLength());
      out.add(List.of(inner, outer, outer.add(reach), inner.add(reach)));
    }
    return out;
  }

  public static boolean furnitureClear(V p, List<Approach> approaches) {
    for (var a : approaches) {
      double ahead = p.sub(a.inner()).dot(a.forward());
      double side = Math.abs(p.sub(a.inner()).dot(a.forward().left()));
      double end = a.crossingLength() - 1.5;
      for (var opposite : approaches) {
        if (opposite == a || opposite.ramp() || a.ramp()
            || opposite.forward().dot(a.forward()) > -.99) continue;
        double stop = opposite.inner().sub(a.inner()).dot(a.forward());
        if (stop > 0 && stop < a.crossingLength() + 2) end = Math.min(end, stop);
      }
      // The paired stop planes, rather than the signal-post margin, bound the median.
      if (Math.abs(p.y() - a.inner().y()) < .5 && ahead >= -.20
          && ahead <= end + .20 && side < a.inner().distance(a.outer()) + 2) return true;
      if (p.sub(a.post()).horizontalLength() < 4) return true;
    }
    return false;
  }

  public static List<Part> structures(List<Approach> approaches) {
    List<Part> out = new ArrayList<>();
    for (var a : approaches) {
      V p = a.post(), f = a.forward();
      V head = a.head();
      double beamBottom = head.y() + .20;
      out.add(new Part(p.sub(f.mul(.35)), p.add(f.mul(.35)), .7, 1, false, Material.CB_BASE));
      out.add(new Part(p.sub(f.mul(.19)), p.add(f.mul(.19)), .38,
          beamBottom - p.y() + .25, false, Material.CB_POST));
      V top = new V(p.x(), beamBottom, p.z());
      V mount = head.add(f.mul(.42));
      // The horizontal CB arm meets the rear mounting lug at housing mid-height.
      // Keep the lamp in front of the arm, as on the original CB assembly.
      V armEnd = new V(mount.x(), beamBottom, mount.z());
      armEnd = armEnd.add(armEnd.sub(top).horizontalUnit());
      out.add(new Part(top, armEnd, .25, .25, false, Material.CB_ARM));
      // The part is the collision envelope; its visible faces come from the original CB model.
      out.add(new Part(head.add(f.mul(.5)), head.sub(f.mul(.5)), 1.9, .85, false,
          a.ramp() ? Material.SIGNAL_RAMP : Material.SIGNAL_MAIN));
    }
    return List.copyOf(out);
  }

  public static List<RoadSurface.Face> lights(List<Part> heads, boolean mainGreen) {
    List<RoadSurface.Face> out = new ArrayList<>();
    for (var p : heads) {
      if (!signal(p)) continue;
      boolean green = p.material() == Material.SIGNAL_MAIN ? mainGreen : !mainGreen;
      out.addAll(RoadSignalModel.faces(p, true, green));
    }
    return List.copyOf(out);
  }

  private RoadSignals() {}
}
