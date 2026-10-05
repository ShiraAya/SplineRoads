package com.sora.splineroads;

import com.sora.splineroads.core.*;
import com.sora.splineroads.core.RoadGeometry.*;
import java.util.*;

/** Actual paint coverage for ordinary mouths and the previously blank fork transitions. */
public final class Revision263Validation {
  static int checks, mouths, branches;
  static void check(boolean value, String why) { checks++; if (!value) throw new AssertionError(why); }

  static void ordinaryMouths() {
    for (int n : new int[] {5, 6}) for (boolean left : new boolean[] {false, true})
      for (Style style : new Style[] {Style.O4_YELLOW, Style.O6_GREEN}) {
        var plan = Revision26Validation.plan(n, 2, left, style, false);
        var all = plan.legs().stream().map(l -> RoadRenderMesh.simplify(l.mesh())).toList();
        for (Mesh ramp : all) if (ramp.settings().style().ramp()) {
          var peers = all.stream().filter(m -> m != ramp).toList();
          for (boolean first : new boolean[] {true, false}) {
            Sample end = first ? ramp.first() : ramp.last();
            if (end.halfWidth() * 2 >= ramp.settings().width() * .7) continue;
            Mesh host = all.stream().filter(m -> !m.settings().style().ramp()).filter(m -> {
              var q = RoadQueries.horizontal(m, end.center());
              return Math.abs(q.sample().center().y() - end.center().y()) < .1
                  && q.horizontalDistance() < q.sample().halfWidth() + end.halfWidth();
            }).findFirst().orElse(null);
            if (host == null) continue;
            mouths++;
            var zones = RoadJunction.dividerZones(ramp, peers);
            for (double d = 1; d < 65; d += 2) {
              double station = first ? d : ramp.length() - d;
              if (RoadStructures.sample(ramp, station).halfWidth() * 2 >= ramp.settings().width() * .94) break;
              check(zones.stream().anyMatch(z -> z.contains(station)),
                  "ordinary narrowing mouth still exposes a divider");
            }
            var paint = RoadJunction.markings(ramp, List.of(host), peers, true);
            V axis = end.left().left().mul(first ? -1 : 1);
            int diagonals = 0;
            for (var face : paint) {
              V a = face.points().get(0).add(face.points().get(1)).mul(.5);
              V b = face.points().get(2).add(face.points().get(3)).mul(.5);
              V delta = b.sub(a), middle = a.add(b).mul(.5);
              double run = middle.sub(end.center()).dot(axis);
              if (run < 0 || run > 65 || delta.horizontalLength() < .6
                  || Math.abs(delta.horizontalUnit().dot(axis)) > .95) continue;
              diagonals++;
              for (V p : face.points()) check(RoadQueries.contains(host, p, .1, .15)
                  || RoadQueries.contains(ramp, p, .1, .15), "ordinary hatch off pavement");
            }
            check(diagonals >= 3, "ordinary entry/exit closure missing hatching");
          }
        }
      }
    check(mouths == 80, "five/six-way ordinary arrivals/departures covered: " + mouths);
  }

  static void forks() {
    for (boolean reverse : new boolean[] {false, true})
      for (boolean left : new boolean[] {false, true}) for (double angle : new double[] {0, .63}) {
        Mesh a = RoadRenderMesh.simplify(Revision231Validation.branch(-1, reverse, false, left, angle));
        Mesh b = RoadRenderMesh.simplify(Revision231Validation.branch(1, reverse, false, left, angle));
        var faces = Revision231Validation.paint(a, b);
        V axis = new V(Math.sin(angle), 0, Math.cos(angle)), side = axis.left();
        for (int sign : new int[] {-1, 1}) {
          int hits = 0, samples = 0;
          // The physical fork is near z=36. Between 52 and 64 the inner lane
          // is reopening, but the old blanket exclusion erased its center line.
          for (double z = 52; z < 64; z += .2) {
            V p = axis.mul(z).add(side.mul(sign * .0035 * z * z)).add(new V(0,64,0));
            if (Revision231Validation.painted(faces, p)) hits++;
            samples++;
          }
          check(hits > samples * .25, "two-lane divider missing while inner lane reopens: " + hits);
          branches++;
        }
        // Each hatch diagonal is a single straight quad before pavement clipping;
        // the old curved interpolation emitted only tiny bent pieces here.
        var guides = RoadJunction.markings(a, List.of(b), List.of(b), true);
        int straightDiagonals = 0;
        for (var face : guides) {
          V p = face.points().get(0).add(face.points().get(1)).mul(.5);
          V q = face.points().get(2).add(face.points().get(3)).mul(.5);
          V delta = q.sub(p);
          if (delta.horizontalLength() > 1 && Math.abs(delta.horizontalUnit().dot(axis)) < .95)
            straightDiagonals++;
        }
        check(straightDiagonals >= 10, "fork hatches still consist of bent fragments");
        Revision231Validation.grids.clear();
      }
  }

  public static void main(String[] args) {
    if (args.length == 0 || args[0].equals("ordinary")) ordinaryMouths();
    if (args.length == 0 || args[0].equals("fork")) forks();
    System.out.printf("Revision263 PASS %d checks / %d ordinary mouths / %d reopened branches%n",
        checks, mouths, branches);
  }
}
