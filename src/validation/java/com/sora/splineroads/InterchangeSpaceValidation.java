package com.sora.splineroads;

import com.sora.splineroads.core.*;
import com.sora.splineroads.core.RoadGeometry.*;
import java.util.*;

/** Regression for the user's six-lane / gap 25 / 350-block screenshots. */
public final class InterchangeSpaceValidation {
  private static int checks;

  private static void check(boolean ok, String message) {
    checks++;
    if (!ok) throw new AssertionError(message);
  }

  public static void run() {
    for (Style style : List.of(Style.O6_RAIL, Style.H6_RAIL)) {
      var settings = new Settings(Mode.STRAIGHT, style, style.defaultWidth(), 1, .4, 90);
      for (var preset :
          List.of(InterchangePlanner.Preset.CLOVERLEAF, InterchangePlanner.Preset.STACK))
        for (boolean left : new boolean[] {false, true})
          for (boolean abUpper : new boolean[] {false, true})
            for (int clearance : new int[] {4, 5}) {
              var options = new InterchangePlanner.Options(preset, left, 1, 96, 20, clearance, 2);
              for (int size : new int[] {350, 400, 450, 500}) {
                var plan =
                    InterchangePlanner.plan(
                        InterchangeValidation.raised(size, 0, 25, abUpper),
                        settings,
                        settings,
                        options);
                check(plan.movements() == 8, "all eight turns survive expansion");
                for (var leg : plan.legs()) {
                  var mesh = leg.mesh();
                  if (!mesh.settings().style().ramp()) continue;
                  check(
                      RoadGrades.maximum(mesh) <= .15 + 1e-7,
                      preset + " " + size + " " + leg.name() + " respects 15% grade");
                  if (leg.name().startsWith("共用"))
                    check(mesh.length() >= 96, "shared exit/merge retains the 96-block transition");
                }
              }
            }
    }
    var style = Style.H6_RAIL;
    var settings = new Settings(Mode.STRAIGHT, style, style.defaultWidth(), 1, .4, 90);
    var options =
        new InterchangePlanner.Options(
            InterchangePlanner.Preset.CLOVERLEAF, false, 1, 96, 20, 4, 2);
    try {
      InterchangePlanner.plan(
          InterchangeValidation.raised(250, 0, 25, false), settings, settings, options);
      throw new AssertionError("short high-gap layout must still be rejected");
    } catch (IllegalArgumentException error) {
      check(
          error.getMessage().contains("汇出与汇入之间的升降区") && error.getMessage().contains("非端点到交点距离"),
          "length rejection identifies the constrained segment");
    }
    System.out.println("Interchange space validation: " + checks + " checks passed");
  }
}
