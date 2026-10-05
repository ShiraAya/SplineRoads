package com.sora.splineroads;

import com.sora.splineroads.core.*;
import com.sora.splineroads.core.RoadGeometry.*;
import java.util.*;

public final class Revision12Validation {
  private static int checks;

  private static void check(boolean ok, String message) {
    checks++;
    if (!ok) throw new AssertionError(message);
  }

  public static void main(String[] args) {
    run();
  }

  public static void run() {
    check(InterchangePlanner.presets(3).size() == 3, "three three-way layouts retained");
    check(InterchangePlanner.presets(4).size() == 7, "seven supported four-way layouts");
    check(
        InterchangePlanner.presets(3).contains(InterchangePlanner.Preset.TRUMPET),
        "three-way trumpet retained");
    check(
        InterchangePlanner.presets(4).contains(InterchangePlanner.Preset.HYBRID),
        "hybrid retained");
    check(
        !RoadSignals.mainGreen(400)
            && RoadSignals.mainGreen(399)
            && !RoadSignals.mainGreen(799)
            && RoadSignals.mainGreen(800),
        "exact 20-second alternation");
    for (boolean left : new boolean[] {false, true})
      for (boolean upper : new boolean[] {false, true})
        for (var preset :
            List.of(InterchangePlanner.Preset.DIAMOND, InterchangePlanner.Preset.SPUI)) {
          var settings = new Settings(Mode.STRAIGHT, Style.H6_RAIL, 38, 1, .4, 90);
          var options = new InterchangePlanner.Options(preset, left, 1, 96, 20, 5, 2);
          var plan =
              InterchangePlanner.plan(
                  InterchangeValidation.raised(600, .37, 16, upper), settings, settings, options);
          List<Mesh> all = plan.legs().stream().map(InterchangePlanner.Leg::mesh).toList();
          int mainCount = 0, rampCount = 0;
          for (int i = 0; i < all.size(); i++) {
            var mesh = all.get(i);
            var nearby = all.stream().filter(m -> m != mesh).toList();
            var entries = RoadSignals.approaches(mesh, nearby);
            if (i == 0) check(entries.isEmpty(), "grade-separated highway has no signals/stops");
            for (var entry : entries) {
              check(
                  entry.post().sub(entry.inner()).dot(entry.forward()) > 5
                      && all.stream().anyMatch(m -> RoadQueries.contains(m, entry.post(), .5, .2)),
                  "signal post rests on the far-side paved edge");
              if (entry.ramp()) rampCount++;
              else mainCount++;
              check(
                  entry.inner().distance(entry.outer()) > 3.5,
                  "stop line spans usable carriageway");
              check(entry.forward().horizontalLength() > .999, "signal follows entering traffic");
              for (var pad : all)
                if (pad.settings().style() == Style.UNMARKED)
                  check(
                      !RoadQueries.contains(pad, entry.inner(), .5, .1)
                          && !RoadQueries.contains(pad, entry.outer(), .5, .1),
                      "stop is before junction");
              var onRoad = RoadQueries.project(mesh, entry.inner());
              var l = RoadProfile.layout(mesh.settings(), onRoad.sample().halfWidth() * 2);
              if (!entry.ramp()) {
                double sign =
                    Math.signum(
                        entry.inner().sub(onRoad.sample().center()).dot(onRoad.sample().left()));
                V direction = onRoad.sample().left().left().mul(sign == l.outside() ? -1 : 1);
                check(
                    direction.dot(entry.forward()) > .999,
                    "stop line on incoming, not outgoing, half");
              }
            }
            var parts = RoadSignals.structures(entries);
            var heads = parts.stream().filter(RoadSignals::signal).toList();
            check(heads.size() == entries.size(), "one head per approach");
            var first = RoadSignals.lights(heads, true);
            var second = RoadSignals.lights(heads, false);
            check(
                first.size() == entries.size() * 3 && second.size() == first.size(),
                "three lenses per head");
            for (int face = 0; face < first.size(); face++) {
              check(
                  first.get(face).points().equals(second.get(face).points()),
                  "phase never moves signal geometry");
              if (face % 3 != 1)
                check(
                    first.get(face).texture() != second.get(face).texture(), "red and green alternate");
            }
            if (mesh.settings().style().ramp())
              check(RoadGrades.maximum(mesh) <= .15 + 1e-7, "ramp grade remains <=15%");
          }
          check(
              mainCount == (preset == InterchangePlanner.Preset.DIAMOND ? 4 : 2),
              "all main approaches have stops");
          check(rampCount == 2, "only entering ramps have stops");
        }
    System.out.println("REVISION 12 VALIDATION PASSED: " + checks + " assertions");
  }
}
