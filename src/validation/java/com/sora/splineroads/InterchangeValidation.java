package com.sora.splineroads;

import com.sora.splineroads.core.*;
import com.sora.splineroads.core.RoadGeometry.*;
import java.nio.file.*;
import java.util.*;

public final class InterchangeValidation {
  private static int checks;

  private static void check(boolean value, String text) {
    checks++;
    if (!value) throw new AssertionError(text);
  }

  public static Node[] nodes(int size, double angle) {
    V u = new V(Math.cos(angle), 0, Math.sin(angle)),
        v = u.left(),
        center = new V(1024.5, 64, -1024.5);
    return new Node[] {
      new Node(center.sub(u.mul(size)), RoadPlanner.yaw(u), 0),
      new Node(center.add(u.mul(size)), RoadPlanner.yaw(u), 0),
      new Node(center.sub(v.mul(size)), RoadPlanner.yaw(v), 0),
      new Node(center.add(v.mul(size)), RoadPlanner.yaw(v), 0)
    };
  }

  public static Node[] raised(int size, double angle, double gap, boolean ab) {
    var nodes = nodes(size, angle);
    for (int i = ab ? 0 : 2; i < (ab ? 2 : 4); i++)
      nodes[i] = new Node(nodes[i].position().add(new V(0, gap, 0)), nodes[i].yaw(), 0);
    return nodes;
  }

  private static Settings main() {
    return new Settings(Mode.STRAIGHT, Style.H4_RAIL, 28, 1, .4, 90);
  }

  public static void run() {
    List<InterchangePlanner.Plan> pictures = new ArrayList<>();
    check(
        InterchangePlanner.presets(3).size() == 3,
        "three-point chooser contains only three common templates");
    check(
        InterchangePlanner.presets(3).stream().allMatch(p -> p.three),
        "no four-way templates at three points");
    check(
        InterchangePlanner.presets(4).stream().noneMatch(p -> p.three),
        "four-point chooser contains four-way templates");
    for (var preset : InterchangePlanner.Preset.values()) {
      if (!preset.supported()||preset.multi()) continue;
      int size = preset == InterchangePlanner.Preset.DOUBLE_TRUMPET ? 1200 : 600;
      var options = new InterchangePlanner.Options(preset, false, 1, 36, 20, 5, 2);
      var plan =
          InterchangePlanner.plan(
              raised(size, 0, InterchangePlanner.minimumDifference(main(), main(), options), false),
              main(),
              main(),
              options);
      if (preset.three) {
        var three =
            Arrays.copyOf(
                raised(
                    size, 0, InterchangePlanner.minimumDifference(main(), main(), options), false),
                3);
        var automatic = InterchangePlanner.plan(three, main(), main(), options);
        check(
            automatic.legs().size() == plan.legs().size(),
            "three real anchors need no fourth node");
        for (int i = 0; i < plan.legs().size(); i++)
          check(
              automatic.legs().get(i).mesh().samples().equals(plan.legs().get(i).mesh().samples()),
              "three-point geometry matches legacy descriptor");
      }
      pictures.add(plan);
      verify(plan, preset);
      var leftOptions = new InterchangePlanner.Options(preset, true, 2, 45, 24, 5, 2);
      double gap = InterchangePlanner.minimumDifference(main(), main(), leftOptions);
      var left =
          InterchangePlanner.plan(raised(size, .37, gap, false), main(), main(), leftOptions);
      verify(left, preset);
      var inverted = InterchangePlanner.plan(raised(size, 0, gap, true), main(), main(), options);
      verify(inverted, preset);
      check(
          Math.abs(plan.legs().get(0).mesh().first().center().y() - 64) < 1e-9,
          "AB height is retained");
      check(
          Math.abs(plan.legs().get(1).mesh().first().center().y() - 64 - gap) < 1e-9,
          "CD height is retained");
      boolean low = false;
      try {
        InterchangePlanner.plan(raised(size, 0, gap - .01, false), main(), main(), options);
      } catch (IllegalArgumentException e) {
        low = e.getMessage().contains("最低");
      }
      check(low, "each preset enforces its height threshold");
      var sloped = raised(size, 0, gap, false);
      sloped[1] = new Node(sloped[1].position().add(new V(0, 1, 0)), 0, 0);
      boolean uneven = false;
      try {
        InterchangePlanner.plan(sloped, main(), main(), options);
      } catch (IllegalArgumentException e) {
        uneven = e.getMessage().contains("等高");
      }
      check(uneven, "uneven road rejected instead of silently reshaped");
    }
    var small =
        InterchangePlanner.plan(
            raised(300, 0, 12, false),
            new Settings(Mode.STRAIGHT, Style.O2_YELLOW, 9, 1, .4, 90),
            new Settings(Mode.STRAIGHT, Style.O2_YELLOW, 9, 1, .4, 90),
            new InterchangePlanner.Options(
                InterchangePlanner.Preset.CLOVERLEAF, false, 1, 24, 14, 4, 2));
    verify(small, InterchangePlanner.Preset.CLOVERLEAF);
    boolean rejected = false;
    try {
      InterchangePlanner.plan(nodes(65, 0), main(), main(), InterchangePlanner.Options.DEFAULT);
    } catch (IllegalArgumentException expected) {
      rejected = true;
    }
    check(rejected, "small invalid range rejected before world mutation");
    try {
      export(pictures);
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
    System.out.println(
        "INTERCHANGE PASS "
            + checks
            + " complete-movement / handedness / segmentation / slope assertions");
  }

  private static void verify(InterchangePlanner.Plan plan, InterchangePlanner.Preset preset) {
    check(plan.movements() == (preset.three ? 4 : 8), "all required directional turns " + preset);
    Set<String> movements = new HashSet<>();
    for (var leg : plan.legs()) {
      Mesh mesh = leg.mesh();
      if (mesh.settings().style().ramp()) {
        if (leg.from() >= 0 && leg.to() >= 0 && !leg.name().startsWith("连续集散道")) {
          check(movements.add(leg.from() + ":" + leg.to()), "unique directed movement");
          check(leg.from() != leg.to() && Math.abs(leg.from() - leg.to()) != 2, "ramp is a turn");
        }
        check(RoadRibbon.minRadius(mesh) > mesh.settings().width() / 2, "no folded ramp edge");
      }
      var pieces = RoadRibbon.split(mesh, 96);
      check(!pieces.isEmpty(), "nonempty persisted segments");
      for (int i = 1; i < pieces.size(); i++) {
        check(
            pieces.get(i - 1).last().center().distance(pieces.get(i).first().center()) < 1e-8,
            "segment seam has no gap");
        check(
            pieces.get(i - 1).last().left().distance(pieces.get(i).first().left()) < 1e-8,
            "segment seam has common tangent");
      }
      for (int i = 1; i < mesh.samples().size(); i++) {
        V d = mesh.samples().get(i).center().sub(mesh.samples().get(i - 1).center());
        check(
            Math.abs(d.y()) / d.horizontalLength()
                <= (mesh.settings().style().ramp() ? .1500001 : .505),
            "whole route climb is bounded");
      }
    }
  }

  private static void export(List<InterchangePlanner.Plan> plans) throws Exception {
    String[] titles =
        Arrays.stream(InterchangePlanner.Preset.values())
            .filter(p->p.supported()&&!p.multi())
            .map(Enum::name)
            .toArray(String[]::new);
    int imageHeight = (int) Math.ceil(plans.size() / 3.0) * 600 + 40;
    StringBuilder svg =
        new StringBuilder(
            "<svg xmlns='http://www.w3.org/2000/svg' width='1800' height='"
                + imageHeight
                + "'><rect width='1800' height='"
                + imageHeight
                + "' fill='#11232e'/><g font-family='sans-serif' fill='#ecf5f7'>");
    for (int k = 0; k < plans.size(); k++) {
      var p = plans.get(k);
      int x = (k % 3) * 600, y = (k / 3) * 600;
      svg.append("<text x='")
          .append(x + 24)
          .append("' y='")
          .append(y + 35)
          .append("' font-size='23'>")
          .append(titles[k])
          .append("</text>");
      double span =
          p.legs().stream().mapToDouble(l -> l.mesh().max().x()).max().orElse(1)
              - p.legs().stream().mapToDouble(l -> l.mesh().min().x()).min().orElse(0);
      double scale = Math.min(.6, 550 / span);
      List<Sample[]> faces = new ArrayList<>();
      for (var leg : p.legs())
        for (int j = 2; j < leg.mesh().samples().size(); j += 2)
          faces.add(new Sample[] {leg.mesh().samples().get(j - 2), leg.mesh().samples().get(j)});
      faces.sort(Comparator.comparingDouble(f -> f[0].center().y() + f[1].center().y()));
      for (var f : faces) {
        double h = (f[0].center().y() + f[1].center().y()) / 2 - 64;
        int r = (int) Math.min(230, 74 + h * 6), g = (int) Math.max(125, 182 - h), b = 200;
        svg.append("<polygon fill='")
            .append(String.format("#%02x%02x%02x", r, g, b))
            .append("' points='");
        for (V v :
            List.of(
                f[0].at(f[0].halfWidth(), 0),
                f[0].at(-f[0].halfWidth(), 0),
                f[1].at(-f[1].halfWidth(), 0),
                f[1].at(f[1].halfWidth(), 0)))
          svg.append(
              String.format(
                  Locale.ROOT,
                  "%.2f,%.2f ",
                  x + 300 + (v.x() - p.center().x()) * scale,
                  y + 305 + (v.z() - p.center().z()) * scale));
        svg.append("'/>");
      }
      for (var leg : p.legs())
        if (leg.mesh().settings().style().ramp())
          for (double d = 40; d < leg.mesh().length() - 20; d += 85) {
            var q = RoadStructures.sample(leg.mesh(), d);
            V forward = q.left().left().mul(-1),
                tip = q.center().add(forward.mul(5)),
                a = q.center().sub(forward.mul(3)).add(q.left().mul(3)),
                b = q.center().sub(forward.mul(3)).sub(q.left().mul(3));
            svg.append("<polygon fill='#faf8e8' points='");
            for (V v : List.of(tip, a, b))
              svg.append(
                  String.format(
                      Locale.ROOT,
                      "%.2f,%.2f ",
                      x + 300 + (v.x() - p.center().x()) * scale,
                      y + 305 + (v.z() - p.center().z()) * scale));
            svg.append("'/>");
          }
      svg.append("<text x='")
          .append(x + 24)
          .append("' y='")
          .append(y + 581)
          .append("' font-size='15'>")
          .append(p.movements())
          .append(" turns; min radius ")
          .append(Math.round(p.minRadius()))
          .append(" blocks; height ")
          .append(Math.round(p.highest() - 64))
          .append(" blocks</text>");
    }
    svg.append(
        "<text x='24' y='"
            + (imageHeight - 15)
            + "' font-size='17'>Actual generated geometry. Color encodes elevation."
            + " This is not a Minecraft screenshot.</text></g></svg>");
    Files.writeString(Path.of("validation/interchanges.svg"), svg);
  }

  public static void main(String[] args) {
    run();
  }
}
