package com.sora.splineroads;

import com.sora.splineroads.core.*;
import com.sora.splineroads.core.RoadGeometry.*;
import java.util.*;

/** Regressions for the eleven 0.9.2 user screenshots. */
public final class InterchangeSafetyValidation {
  private static int checks;

  private static void check(boolean ok, String message) {
    checks++;
    if (!ok) throw new AssertionError(message);
  }

  private static Settings main() {
    return new Settings(Mode.STRAIGHT, Style.H4_RAIL, 28, 1, .4, 90);
  }

  private static double distanceToPart(RoadStructures.Part part, V p) {
    V d = part.b().sub(part.a()), q = p.sub(part.a());
    double dd = d.x() * d.x() + d.z() * d.z();
    double t = dd < 1e-9 ? 0 : Math.max(0, Math.min(1, (q.x() * d.x() + q.z() * d.z()) / dd));
    return p.sub(part.a().add(d.mul(t))).horizontalLength();
  }

  public static void run() {
    for (boolean left : new boolean[] {false, true}) {
      var s = main();
      var o =
          new InterchangePlanner.Options(
              InterchangePlanner.Preset.STACK, left, left ? 2 : 1, 96, 20, 5, 2);
      var plan = InterchangePlanner.plan(InterchangeValidation.raised(600, 0, 21, false), s, s, o);
      List<Mesh> all = plan.legs().stream().map(InterchangePlanner.Leg::mesh).toList();
      check(
          plan.legs().stream().filter(l -> l.name().startsWith("共用")).count() == 8,
          "four single exit feeders and four single merge feeders");
      for (var leg : plan.legs()) {
        Mesh m = leg.mesh();
        if (leg.name().contains(left ? " 左转" : " 右转")) {
          V a = m.first().center(), b = m.last().center();
          double minX = Math.min(a.x(), b.x()) - .01, maxX = Math.max(a.x(), b.x()) + .01;
          double minZ = Math.min(a.z(), b.z()) - .01, maxZ = Math.max(a.z(), b.z()) + .01;
          for (Sample q : m.samples())
            check(
                q.center().x() >= minX
                    && q.center().x() <= maxX
                    && q.center().z() >= minZ
                    && q.center().z() <= maxZ,
                "outer right turn stays within endpoint rectangle");
        }
        if (!leg.name().startsWith("共用")) continue;
        boolean exit = leg.name().startsWith("共用汇出");
        Mesh host = all.get((exit ? leg.from() : leg.to()) % 2);
        double usable = 0;
        for (int i = 1; i < m.samples().size(); i++) {
          Sample p = m.samples().get(i);
          var q = RoadQueries.horizontal(host, p.center());
          if (p.halfWidth() * 2 >= m.settings().width() * .999
              && q.horizontalDistance() < q.sample().halfWidth() + p.halfWidth() - .15)
            usable += p.center().sub(m.samples().get(i - 1).center()).horizontalLength();
        }
        check(
            usable >= 40,
            "full width parallel lane remains joined for at least 40 blocks, got " + usable);
        List<Mesh> neighbors = all.stream().filter(n -> n != m).toList();
        var parts =
            new ArrayList<>(
                RoadStructures.plan(
                    m,
                    new RoadStructures.Ground() {
                      public double top(double x, double z, double y) {
                        return y;
                      }

                      public boolean blocked(RoadStructures.Part p) {
                        return false;
                      }

                      public boolean joined(V p) {
                        return neighbors.stream().anyMatch(n -> RoadQueries.joins(m, n, p));
                      }
                    }));
        parts.addAll(
            RoadStructures.plan(
                host,
                new RoadStructures.Ground() {
                  public double top(double x, double z, double y) {
                    return y;
                  }

                  public boolean blocked(RoadStructures.Part p) {
                    return false;
                  }

                  public boolean joined(V p) {
                    return all.stream()
                        .filter(n -> n != host)
                        .anyMatch(n -> RoadQueries.joins(host, n, p));
                  }
                }));
        for (double distance = 12; distance < m.length() - 8; distance += 4) {
          Sample p = RoadStructures.sample(m, distance);
          var q = RoadQueries.horizontal(host, p.center());
          int outer = p.left().dot(p.center().sub(q.sample().center())) > 0 ? 1 : -1;
          V rampEdge = p.at(outer * (p.halfWidth() - .16), 0);
          int hostSide = q.sample().left().dot(p.center().sub(q.sample().center())) > 0 ? 1 : -1;
          V edge =
              RoadQueries.contains(host, rampEdge, 0, .12)
                  ? q.sample().at(hostSide * (q.sample().halfWidth() - .16), 0)
                  : rampEdge;
          boolean found =
              parts.stream()
                  .filter(r -> r.material() == RoadStructures.Material.STEEL)
                  .anyMatch(r -> distanceToPart(r, edge) < .30);
          check(found, "outer rail continues through the full transition at " + distance);
        }
        check(RoadJunction.arrows(m, neighbors).isEmpty()
                == (m.last().halfWidth() >= m.first().halfWidth()),
            "narrowing shared feeders have merge arrows; widening exits do not");
      }
    }
    var s = main();
    for (var preset :
        List.of(
            InterchangePlanner.Preset.DIAMOND,
            InterchangePlanner.Preset.SPUI,
            InterchangePlanner.Preset.ROUNDABOUT)) {
      var o = new InterchangePlanner.Options(preset, false, 1, 96, 20, 5, 2);
      double gap = InterchangePlanner.minimumDifference(s, s, o);
      var plan = InterchangePlanner.plan(InterchangeValidation.raised(400, 0, gap, false), s, s, o);
      check(
          plan.legs().get(0).mesh().length() == 800 && plan.legs().get(1).mesh().length() == 800,
          "both mainlines are retained");
      long pads =
          plan.legs().stream().filter(l -> l.mesh().settings().style() == Style.UNMARKED).count();
      check(
          pads
              == (preset == InterchangePlanner.Preset.DIAMOND
                  ? 2
                  : preset == InterchangePlanner.Preset.SPUI ? 1 : 0),
          "two diamond terminals / one SPUI center / no at-grade roundabout mains");
      if (preset == InterchangePlanner.Preset.ROUNDABOUT) {
        var rings = plan.legs().stream().filter(l -> l.mesh().closed()).toList();
        check(rings.size() == 1, "one independent ring");
        Mesh ring = rings.get(0).mesh();
        check(
            ring.first().center().y() > 64 && ring.first().center().y() < 64 + gap,
            "ring has its own intermediate elevation");
        check(
            plan.legs().stream().filter(l -> l.name().startsWith("环道 ")).count() == 8,
            "eight independent ring slip roads");
        for (var leg : plan.legs())
          if (leg.name().startsWith("环道 ")) {
            Sample end = leg.from() < 0 ? leg.mesh().first() : leg.mesh().last();
            check(
                RoadQueries.contains(ring, end.center(), .01, .01),
                "every slip is connected to the ring");
          }
      }
    }
    boolean rejected = false;
    try {
      InterchangePlanner.plan(
          InterchangeValidation.raised(250, 0, 33, false),
          s,
          s,
          new InterchangePlanner.Options(InterchangePlanner.Preset.STACK, false, 1, 96, 20, 5, 2));
    } catch (IllegalArgumentException e) {
      rejected = e.getMessage().contains("15%");
    }
    check(
        rejected,
        "250-block arms / 33-block gap rejected by a usable 15% climbing-length constraint");
    System.out.println(
        "INTERCHANGE SAFETY PASS "
            + checks
            + " topology / full-width merge lane / continuous outer rails / inward arrows / 15%"
            + " rejection assertions");
  }

  public static void main(String[] args) {
    run();
  }
}
