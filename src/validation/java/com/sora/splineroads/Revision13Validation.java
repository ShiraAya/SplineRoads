package com.sora.splineroads;

import com.sora.splineroads.core.*;
import com.sora.splineroads.core.RoadGeometry.*;
import com.sora.splineroads.core.RoadProfile.*;
import java.util.*;

public final class Revision13Validation {
  private static int checks;

  private static void check(boolean ok, String message) {
    checks++;
    if (!ok) throw new AssertionError(message);
  }

  private static Settings setting(Style s) {
    return new Settings(Mode.STRAIGHT, s, s.defaultWidth(), 1, .4, 90);
  }

  private static Mesh line(Settings s, double a, double b) {
    return RoadGeometry.build(new Node(new V(0, 64, a), 0, 0), new Node(new V(0, 64, b), 0, 0), s);
  }

  private static RoadStructures.Ground ground(double y) {
    return new RoadStructures.Ground() {
      public double top(double x, double z, double deck) {
        return y;
      }

      public boolean joined(V p) {
        return false;
      }

      public boolean blocked(RoadStructures.Part p) {
        return false;
      }
    };
  }

  public static void main(String[] args) {
    run();
  }

  public static void run() {
    for (Type type : List.of(Type.ORDINARY, Type.HIGHWAY)) {
      for (Style a : RoadProfile.styles(type))
        for (Style b : RoadProfile.styles(type)) {
          var ca = RoadProfile.catalog(a);
          var cb = RoadProfile.catalog(b);
          boolean expected =
              ca.twoWay() == cb.twoWay()
                  && Math.abs(ca.lanes() - cb.lanes()) <= (ca.twoWay() ? 2 : 1);
          check(
              RoadTransitions.compatible(setting(a), setting(b)) == expected,
              "complete lane compatibility matrix");
          if (!expected) continue;
          Settings sa = RoadTransitions.join(setting(a), null, setting(b));
          Settings sb = RoadTransitions.join(setting(b), setting(a), null);
          Mesh ma = line(sa, -180, 0), mb = line(sb, 0, 180);
          check(
              Math.abs(ma.last().halfWidth() - mb.first().halfWidth()) < 1e-8,
              "identical seam pavement widths");
          var la = RoadProfile.layout(ma, ma.last());
          var lb = RoadProfile.layout(mb, mb.first());
          check(
              Math.abs(la.motorMin() - lb.motorMin()) < 1e-8
                  && Math.abs(la.motorMax() - lb.motorMax()) < 1e-8
                  && Math.abs(la.median() - lb.median()) < 1e-8
                  && la.dividers().equals(lb.dividers()),
              "paint and median join exactly");
          check(
              RoadTransitions.common(setting(a), setting(b))
                  .equals(RoadTransitions.common(setting(b), setting(a))),
              "construction-order independent section");
          for (Mesh m : List.of(ma, mb)) {
            for (Sample p : m.samples()) {
              var l = RoadProfile.layout(m, p);
              check(
                  l.motorMin() >= -p.halfWidth() - 1e-7 && l.motorMax() <= p.halfWidth() + 1e-7,
                  "driving lanes remain on tapered pavement");
            }
            // Real generated stripe polygons, including one-way lane drops and median changes.
            for (var face :
                RoadSurface.build(RoadRenderMesh.simplify(m), List.of(), List.of()).markings())
              for (V p : face.points())
                check(RoadQueries.contains(m, p, .05, .1), "transition paint remains on pavement");
          }
        }
    }
    check(
        !RoadTransitions.compatible(setting(Style.O4_RAIL), setting(Style.H4_RAIL)),
        "ordinary/highway cannot connect directly");
    Mesh shortTaper = line(RoadTransitions.join(setting(Style.O2_YELLOW), null,
        setting(Style.O4_GREEN)), 0, 80);
    RoadProfile.Layout previousLayout = null;
    for (Sample sample : shortTaper.samples()) {
      var layout = RoadProfile.layout(shortTaper, sample);
      if (previousLayout != null)
        check(Math.abs(layout.motorMax() - previousLayout.motorMax()) < .15
            && Math.abs(layout.median() - previousLayout.median()) < .15,
            "a long one-ended taper never jumps at road midpoint");
      previousLayout = layout;
    }
    check(
        RoadTransitions.compatible(setting(Style.H6_GREEN), setting(Style.R1)),
        "ramp may link road families");
    check(
        !RoadProfile.styles(Type.HIGHWAY).contains(Style.H4_YELLOW),
        "yellow median absent from highway picker");
    for (Style s : List.of(Style.O4_GREEN, Style.H6_GREEN)) {
      Mesh m = line(setting(s), 0, 80);
      check(
          RoadStructures.plan(m, ground(64)).stream()
              .anyMatch(p -> p.material() == RoadStructures.Material.GREEN),
          "ground median planted");
      var raised = RoadStructures.plan(m, ground(50));
      check(
          raised.stream()
              .noneMatch(
                  p ->
                      p.material() == RoadStructures.Material.GREEN
                          || p.material() == RoadStructures.Material.SOIL),
          "elevated median has no planting");
      check(
          raised.stream()
              .anyMatch(
                  p ->
                      !p.pier()
                          && Math.abs(p.a().x()) < .8
                          && p.material()
                              == (RoadProfile.catalog(s).type() == Type.HIGHWAY
                                  ? RoadStructures.Material.CONCRETE
                                  : RoadStructures.Material.STEEL)),
          "elevated median has matching barrier");
    }
    Settings main = setting(Style.H6_RAIL);
    for (var preset : InterchangePlanner.Preset.values())
      if (preset.supported()&&!preset.multi()) {
        for (boolean left : new boolean[] {false, true}) {
          var o = new InterchangePlanner.Options(preset, left, 1, 96, 24, 5, 2);
          Node[] nodes =
              InterchangeValidation.raised(
                  600, .37, InterchangePlanner.minimumDifference(main, main, o), false);
          if (preset.three) nodes = Arrays.copyOf(nodes, 3);
          var plan = InterchangePlanner.plan(nodes, main, main, o);
          if (preset.three) {
            Mesh stem = plan.legs().get(1).mesh();
            check(
                stem.last().center().sub(plan.center()).horizontalLength() > 80,
                "terminating mainline ends before interchange center");
            check(
                stem.last().halfWidth() < stem.first().halfWidth(),
                "terminating mainline narrows before split");
            int contacts = 0;
            for (var leg : plan.legs())
              if (leg.mesh().settings().style().ramp())
                for (Sample p : List.of(leg.mesh().first(), leg.mesh().last())) {
                  V delta = p.center().sub(stem.last().center());
                  if (Math.abs(delta.dot(stem.last().left().left())) < 1e-6
                      && delta.horizontalLength() < stem.last().halfWidth()) contacts++;
                }
            check(contacts == 4, "all four movements directly meet the terminating mainline");
          }
          if (preset == InterchangePlanner.Preset.CLOVERLEAF)
            check(
                plan.legs().stream().filter(l -> l.name().startsWith("连续集散道")).count() == 4,
                "four continuous collectors replace opposing mouths");
          for (var leg : plan.legs())
            if (leg.mesh().settings().style().ramp())
              check(
                  RoadGrades.maximum(leg.mesh()) <= .15 + 1e-7,
                  "all layouts retain 15 percent maximum ramp grade");
        }
      }
    var o =
        new InterchangePlanner.Options(
                InterchangePlanner.Preset.STACK, false, 1, 96, 20, 5, 2, 0, true)
            .shrink(true);
    Node[] original = InterchangeValidation.raised(650, 0, 25, false);
    var compact = InterchangePlanner.plan(original, main, main, o);
    check(
        compact.anchors().get(0).position().sub(compact.center()).horizontalLength() < 500,
        "allow-shrink actually reduces a valid large layout");
    var fixed = InterchangePlanner.plan(original, main, main, o.shrink(false));
    check(
        fixed.anchors().equals(Arrays.asList(original)),
        "disabled shrinking retains valid existing dimensions");
    System.out.println(
        "REVISION 13 PASS: "
            + checks
            + " lane/median-transition, topology, elevated-greenery, compact-fit assertions");
  }
}
