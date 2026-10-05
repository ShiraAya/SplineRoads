package com.sora.splineroads;

import com.sora.splineroads.core.*;
import com.sora.splineroads.core.RoadGeometry.*;
import java.util.List;

/** Production-core tests. No Minecraft/Forge/renderer test doubles are used here. */
public final class Transition402Validation {
  private static int checks, cases, rejected;
  private static void check(boolean ok, String message) {
    checks++;
    if (!ok) throw new AssertionError(message);
  }
  private static void near(double a, double b, String message) {
    check(Math.abs(a - b) < 1e-7, message + ": " + a + " vs " + b);
  }
  private static Settings settings(Style style, Structure structure, boolean left) {
    var options = RoadProfile.Options.DEFAULT.traffic(left).extras(true, true, true);
    return new Settings(Mode.STRAIGHT, style, RoadProfile.width(style, options, 4), 1, .35, 90)
        .options(options).structure(structure);
  }
  private static Mesh mesh(Settings settings, boolean reversed) {
    V origin = new V(100000, 100, -100000), delta = new V(300, 0, 400);
    V a = reversed ? origin.add(delta) : origin, b = reversed ? origin : origin.add(delta);
    double yaw = RoadPlanner.yaw(b.sub(a).horizontalUnit());
    return RoadGeometry.build(new Node(a, yaw, 0), new Node(b, yaw, 0), settings);
  }

  /** Also runs against the unmodified baseline, so the regression is demonstrably red first. */
  private static void reproduce(boolean expectFailure) {
    var a = settings(Style.O4_RAIL, Structure.BRIDGE, false);
    var b = settings(Style.O6_RAIL, Structure.BRIDGE, false);
    var road = mesh(RoadTransitions.ends(a, null, RoadTransitions.common(a, b)), false);
    List<Double> previous = null; int crossings = 0, countChanges = 0;
    for (double d = 0; d <= road.length(); d += .25) {
      var now = RoadProfile.layout(road, RoadStructures.sample(road, d)).dividers();
      if (previous != null) {
        if (previous.size() != now.size()) countChanges++;
        for (int i = 0; i < Math.min(previous.size(), now.size()); i++)
          if (previous.get(i) * now.get(i) < 0) crossings++;
      }
      previous = now;
    }
    System.out.println("Raised 4->6 probe: cross-median index changes=" + crossings
        + ", divider-count changes=" + countChanges);
    check(expectFailure ? crossings > 0 : crossings == 0 && countChanges == 0,
        expectFailure ? "baseline did not reproduce the reported stripe class" : "cross-median regression");
  }

  public static void main(String[] args) {
    if (args.length == 1 && args[0].equals("--expect-baseline-failure")) {
      reproduce(true); return;
    }
    reproduce(false);
    Style[][] groups = {
        {Style.O1_ONE, Style.O2_ONE, Style.O3_ONE, Style.O4_ONE},
        {Style.H1_ONE, Style.H2_ONE, Style.H3_ONE, Style.H4_ONE},
        {Style.O2_YELLOW, Style.O4_YELLOW, Style.O6_YELLOW, Style.O8_YELLOW},
        {Style.O2_RAIL, Style.O4_RAIL, Style.O6_RAIL, Style.O8_RAIL},
        {Style.O2_GREEN, Style.O4_GREEN, Style.O6_GREEN, Style.O8_GREEN},
        {Style.H4_RAIL, Style.H6_RAIL, Style.H8_RAIL},
        {Style.H4_GREEN, Style.H6_GREEN, Style.H8_GREEN},
        {Style.H4_YELLOW, Style.H6_YELLOW}
    };
    for (var group : groups) for (int n = 0; n < group.length - 1; n++)
      for (int m = n + 1; m < group.length; m++)
        for (boolean reducing : new boolean[] {false, true})
          for (boolean atStart : new boolean[] {false, true})
            for (boolean left : new boolean[] {false, true})
              for (boolean reversed : new boolean[] {false, true})
                for (var structure : List.of(Structure.GROUND, Structure.BRIDGE)) {
                  var a = settings(reducing ? group[m] : group[n], structure, left);
                  var b = settings(reducing ? group[n] : group[m], structure, left);
                  if (m > n + 1) {
                    boolean denied = false;
                    try { RoadTransitions.common(a, b); }
                    catch (IllegalArgumentException expected) { denied = true; }
                    check(denied, "unsupported multi-lane jump silently enabled"); rejected++; continue;
                  }
                  test(a, b, atStart, reversed);
                }
    // Explicit narrowing ends exercise a real lane DROP, not only common() choosing
    // the larger shared section. Both physical road orders and driving sides are tested.
    for (var group : groups) for (int n = 0; n < group.length - 1; n++)
      for (boolean atStart : new boolean[] {false, true})
        for (boolean left : new boolean[] {false, true})
          for (boolean reversed : new boolean[] {false, true})
            for (var structure : List.of(Structure.GROUND, Structure.BRIDGE)) {
              var a = settings(group[n + 1], structure, left);
              var b = settings(group[n], structure, left);
              test(a, b, atStart, reversed, RoadTransitions.Section.of(b));
            }
    for (var pair : List.of(new Style[] {Style.O4_YELLOW, Style.O6_GREEN},
        new Style[] {Style.O4_RAIL, Style.H6_RAIL}, new Style[] {Style.O2_DASHED, Style.O4_YELLOW}))
      for (boolean reversed : new boolean[] {false, true})
        test(settings(pair[0], Structure.BRIDGE, false), settings(pair[1], Structure.BRIDGE, false), false, reversed);
    System.out.println("Transition402Validation: " + cases + " valid transition cases, " + rejected
        + " unsupported requests rejected, " + checks + " sampled assertions PASS; production core only, NOT game/GPU.");
  }

  private static void test(Settings a, Settings b, boolean atStart, boolean reversed) {
    test(a, b, atStart, reversed, RoadTransitions.common(a, b));
  }

  private static void test(Settings a, Settings b, boolean atStart, boolean reversed, RoadTransitions.Section common) {
    var road = mesh(RoadTransitions.ends(a, atStart ? common : null, atStart ? null : common), reversed);
    int lanes = Math.max(RoadProfile.catalog(a.style()).lanes(), RoadProfile.catalog(b.style()).lanes());
    boolean two = RoadProfile.catalog(a.style()).twoWay(); int dividerCount = two ? lanes - 2 : lanes - 1;
    List<Double> previous = null; cases++;
    for (double d = 0; d <= road.length(); d += .5) {
      var sample = RoadStructures.sample(road, d); var layout = RoadProfile.layout(road, sample);
      var dividers = layout.dividers();
      check(dividers.size() == dividerCount, "divider identities changed: " + a.style() + "->" + b.style() + " at " + d);
      for (int i = 0; i < dividers.size(); i++) {
        double v = dividers.get(i);
        check(v >= layout.motorMin() - 1e-7 && v <= layout.motorMax() + 1e-7, "stripe leaves motor surface");
        if (two) check((i < dividerCount / 2 ? -1 : 1) * v >= layout.median() / 2 - 1e-7,
            "stripe enters median/opposing carriageway");
        if (previous != null) check(Math.abs(v - previous.get(i)) < .25, "stripe jumps across catalog switch");
      }
      check(Double.isFinite(layout.laneWidth()) && layout.laneWidth() > 0, "invalid lane width");
      previous = dividers;
    }
    var at = RoadProfile.layout(road, atStart ? road.first() : road.last());
    var straight = mesh(common.settings(a.options().leftTraffic()), reversed);
    // Use the same physical structure for the comparison (raised medians are deliberate).
    straight = mesh(straight.settings().structure(a.structure()), reversed);
    var seam = RoadProfile.layout(straight, straight.first());
    near(at.motorMin(), seam.motorMin(), "seam min"); near(at.motorMax(), seam.motorMax(), "seam max");
    for (double line : seam.dividers()) check(at.dividers().stream().anyMatch(v -> Math.abs(v - line) < 1e-7), "live seam divider missing");
    for (double line : at.dividers()) check(seam.dividers().stream().anyMatch(v -> Math.abs(v - line) < 1e-7)
        || Math.abs(line - seam.motorMin()) < 1e-7 || Math.abs(line - seam.motorMax()) < 1e-7,
        "disappearing seam divider did not converge to outside edge");
  }
}
