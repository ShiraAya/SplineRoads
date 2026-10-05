package com.sora.splineroads;

import com.sora.splineroads.core.*;
import com.sora.splineroads.core.RoadGeometry.*;
import java.util.*;

/**
 * User regressions: four fixed levels, editable widths, fitted anchors and exact local collision.
 */
public final class Revision11Validation {
  private static int checks;

  private static void check(boolean value, String message) {
    checks++;
    if (!value) throw new AssertionError(message);
  }

  private static Settings main() {
    return new Settings(Mode.STRAIGHT, Style.H6_RAIL, 38, 1, .4, 90);
  }

  private static List<RoadStructures.Part> parts(Mesh mesh, List<Mesh> all) {
    return RoadStructures.plan(
        mesh,
        new RoadStructures.Ground() {
          public double top(double x, double z, double y) {
            return y;
          }

          public boolean blocked(RoadStructures.Part part) {
            return false;
          }

          public boolean joined(V p) {
            return all.stream().filter(n -> n != mesh).anyMatch(n -> RoadQueries.joins(mesh, n, p));
          }
        });
  }

  public static void run() {
    Settings s = main();
    for (boolean left : new boolean[] {false, true})
      for (boolean upper : new boolean[] {false, true}) {
        var o =
            new InterchangePlanner.Options(InterchangePlanner.Preset.STACK, left, 1, 96, 20, 4, 2);
        var nodes = InterchangeValidation.raised(350, .37, 25, upper);
        var plan = InterchangePlanner.plan(nodes, s, s, o);
        Set<Double> middle = new HashSet<>();
        for (var leg : plan.legs()) {
          Mesh m = leg.mesh();
          if (!leg.name().contains(left ? "右转" : "左转")) continue;
          double plateau = 64 + 25.0 * (m.first().center().y() < m.last().center().y() ? 1 : 2) / 3;
          double flat = 0, sign = Math.signum(m.last().center().y() - m.first().center().y());
          for (int i = 1; i < m.samples().size(); i++) {
            V a = m.samples().get(i - 1).center(), b = m.samples().get(i).center();
            check((b.y() - a.y()) * sign >= -1e-7, "left turn never reverses its climb/descent");
            if (Math.abs(a.y() - plateau) < 1e-7 && Math.abs(b.y() - plateau) < 1e-7)
              flat += a.distance(b);
          }
          check(flat >= 35, "left turn has a real fixed middle deck");
          check(
              Math.abs(RoadStructures.sample(m, m.length() / 2).center().y() - plateau) < 1e-7,
              "center at assigned layer");
          middle.add(plateau);
        }
        check(middle.size() == 2, "two middle layers between two mainlines");
        check(plan.anchors().equals(Arrays.asList(nodes)), "fixed endpoints retained");
        var all = plan.legs().stream().map(InterchangePlanner.Leg::mesh).toList();
        for (var leg : plan.legs())
          if (leg.mesh().settings().style().ramp()) {
            var arrows =
                RoadJunction.arrows(leg.mesh(), all.stream().filter(m -> m != leg.mesh()).toList());
            check(
                arrows.size() % 3 == 0,
                "merge guidance has complete silhouettes");
            if (!arrows.isEmpty()) {
              var head = arrows.get(2).points();
              V base = head.get(1).add(head.get(2)).mul(.5),
                  dir = head.get(0).sub(base).horizontalUnit();
              for (int i = 0; i < 2; i++)
                for (V p : arrows.get(i).points())
                  check(p.sub(base).dot(dir) <= 1e-7, "stem does not overlap arrowhead");
            }
          }
        if (!left && !upper) {
          var structures = all.stream().map(m -> parts(m, all)).toList();
          var caps = RoadNoses.connectors(all, structures);
          check(
              caps.values().stream().flatMap(List::stream)
                  .filter(p -> p.a().distance(p.b()) > .1).count() >= 16,
              "eight ramp noses closed at both rail heights: " + caps.values().stream().flatMap(List::stream).count());
          for (var list : caps.values())
            for (var p : list) {
              check(p.a().distance(p.b()) <= 1.25, "nose closure stays small");
              for (V end : List.of(p.a(), p.b()))
                check(
                    structures.stream()
                        .flatMap(List::stream)
                        .anyMatch(q -> q.a().distance(end) < 1e-7 || q.b().distance(end) < 1e-7),
                    "cap reaches actual rail end");
            }
        }
      }
    for (int lanes : new int[] {1, 2}) {
      double width = lanes == 1 ? 6 : 10;
      var o =
          new InterchangePlanner.Options(
              InterchangePlanner.Preset.STACK, false, lanes, 96, 20, 4, 2, width, false);
      var p = InterchangePlanner.plan(InterchangeValidation.raised(500, 0, 25, false), s, s, o);
      for (var leg : p.legs())
        if (leg.mesh().settings().style().ramp())
          check(leg.mesh().settings().width() == width, "custom ramp width propagated to all legs");
    }
    var o = new InterchangePlanner.Options(InterchangePlanner.Preset.STACK, false, 1, 96, 20, 4, 2);
    var shortNodes = InterchangeValidation.nodes(180, .37);
    boolean rejected = false;
    try {
      InterchangePlanner.plan(shortNodes, s, s, o);
    } catch (IllegalArgumentException e) {
      rejected = true;
    }
    check(rejected, "disabled adjustment rejects insufficient points");
    var fitted = InterchangePlanner.plan(shortNodes, s, s, o.adjust(true));
    check(
        !fitted.anchors().equals(Arrays.asList(shortNodes)),
        "fit returns changed endpoint preview");
    check(
        shortNodes[0].position().y() == 64 && shortNodes[2].position().y() == 64,
        "fit never mutates inputs");
    var rebuilt = InterchangePlanner.plan(fitted.anchors().toArray(Node[]::new), s, s, o);
    check(rebuilt.legs().equals(fitted.legs()), "preview and fixed fitted construction agree");
    for (var node : fitted.anchors())
      check(node.position().sub(fitted.center()).horizontalLength() <= 1024, "bounded fit");
    var valid = InterchangeValidation.raised(350, 0, 25, false);
    check(
        InterchangePlanner.plan(valid, s, s, o.adjust(true)).anchors().equals(Arrays.asList(valid)),
        "fit leaves valid points unchanged");
    var compact = InterchangePlanner.plan(InterchangeValidation.raised(300, 0, 16, false), s, s, o);
    check(compact.legs().size() == 18, "continuous-curved stack fits at 300 blocks / 16 height");
    for (var mode : RoadProfile.OuterRail.values()) {
      var settings = s.options(s.options().outerRail(mode));
      Mesh m =
          RoadGeometry.build(
              new Node(new V(0, 64, 0), 0, 0), new Node(new V(0, 64, 64), 0, 0), settings);
      var rails = parts(m, List.of(m));
      long exterior = rails.stream().filter(p -> Math.abs(p.a().x()) > 3).count();
      check(
          mode == RoadProfile.OuterRail.OFF ? exterior == 0 : exterior > 0,
          "outer rail switch " + mode);
      check(!rails.isEmpty(), "median furniture remains independent");
    }
    localCollision();
    System.out.println(
        "REVISION 11 PASS "
            + checks
            + " fixed-layer / ramp-width / endpoint-fit / nose / arrow / local-collision"
            + " assertions");
  }

  private static List<double[]> intervals(List<RoadRaster.Box> boxes, double x, double z) {
    var list = new ArrayList<double[]>();
    for (var b : boxes)
      if (x > b.x0() + 1e-9 && x < b.x1() - 1e-9 && z > b.z0() + 1e-9 && z < b.z1() - 1e-9)
        list.add(new double[] {b.y0(), b.y1()});
    list.sort(Comparator.comparingDouble(a -> a[0]));
    var out = new ArrayList<double[]>();
    for (var b : list)
      if (out.isEmpty() || b[0] > out.get(out.size() - 1)[1] + 1e-7) out.add(b.clone());
      else out.get(out.size() - 1)[1] = Math.max(out.get(out.size() - 1)[1], b[1]);
    return out;
  }

  private static void localCollision() {
    var s = new Settings(Mode.AUTO, Style.H4_RAIL, 28, 1, .4, 90);
    var m =
        RoadGeometry.build(
            new Node(new V(-170, 70, -700), 12, .035), new Node(new V(180, 102, 200), 20, .035), s);
    var rails =
        RoadStructures.plan(
            m,
            new RoadStructures.Ground() {
              public double top(double x, double z, double y) {
                return 64;
              }

              public boolean blocked(RoadStructures.Part p) {
                return false;
              }

              public boolean joined(V p) {
                return false;
              }
            });
    long started = System.nanoTime();
    var local = new RoadRaster.Local(m, rails);
    double indexMs = (System.nanoTime() - started) / 1e6;
    var cells = new ArrayList<RoadRaster.Cell>();
    for (int i = 0; i < 48; i++) {
      var q = RoadStructures.sample(m, m.length() * (.03 + i * .019));
      V p =
          q.at(
              i % 3 == 0 ? q.halfWidth() - .2 : i % 3 == 1 ? -q.halfWidth() + .2 : 0,
              i % 3 == 0 ? .5 : -.3);
      cells.add(
          new RoadRaster.Cell(
              (int) Math.floor(p.x()), (int) Math.floor(p.y()), (int) Math.floor(p.z())));
    }
    long oldNanos = 0, newNanos = 0;
    int candidates = 0;
    for (var c : cells) {
      started = System.nanoTime();
      var expected = new ArrayList<>(RoadRaster.raster(m, c).getOrDefault(c, List.of()));
      expected.addAll(RoadRaster.structures(rails, c).getOrDefault(c, List.of()));
      oldNanos += System.nanoTime() - started;
      started = System.nanoTime();
      var actual = local.boxes(c);
      newNanos += System.nanoTime() - started;
      candidates += local.candidates(c);
      for (double x = .031; x < 1; x += .0625)
        for (double z = .037; z < 1; z += .0625) {
          var a = intervals(expected, x, z);
          var b = intervals(actual, x, z);
          check(a.size() == b.size(), "local collision has identical solid interval count " + c);
          for (int i = 0; i < a.size(); i++)
            check(
                Math.abs(a.get(i)[0] - b.get(i)[0]) < 1e-7
                    && Math.abs(a.get(i)[1] - b.get(i)[1]) < 1e-7,
                "local collision preserves exact top and bottom " + c);
        }
    }
    check(
        candidates / cells.size() < local.segmentCount / 10, "queries visit only nearby segments");
    System.out.printf(
        Locale.ROOT,
        "LOCAL COLLISION 48 cold cells: full-road %.2f ms, local %.2f ms, one-time index %.2f ms;"
            + " candidates %.1f / %d; rail parts %d%n",
        oldNanos / 1e6,
        newNanos / 1e6,
        indexMs,
        candidates / (double) cells.size(),
        local.segmentCount,
        rails.size());
  }

  public static void main(String[] args) {
    run();
  }
}
