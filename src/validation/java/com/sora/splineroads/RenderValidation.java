package com.sora.splineroads;

import com.sora.splineroads.core.*;
import com.sora.splineroads.core.RoadGeometry.*;
import com.sora.splineroads.core.RoadStructures.*;
import java.util.*;

/** Geometry/LOD invariants independent of the GPU; does not claim measured Minecraft FPS. */
public final class RenderValidation {
  private static int checks;

  private static void check(boolean value, String message) {
    checks++;
    if (!value) throw new AssertionError(message);
  }

  private static long vertices(Collection<RoadSurface.Face> faces) {
    return faces.stream().mapToLong(RoadRenderMesh::vertexCount).sum();
  }

  public static void run() {
    var a = new V(0, 64, 0);
    var b = new V(0, 64, 64);
    List<Part> blocks =
        List.of(
            new Part(a, a.add(new V(0, 0, 8)), .5, 1, false, Material.CONCRETE),
            new Part(
                a.add(new V(0, 0, 8)), a.add(new V(0, 0, 16)), .5, 1, false, Material.CONCRETE));
    var compact = RoadRenderMesh.compact(blocks, true);
    check(compact.size() == 1, "continuous distant rail is one solid");
    check(
        compact.get(0).a().equals(a) && compact.get(0).b().equals(a.add(new V(0, 0, 16))),
        "LOD retains actual rail endpoints");
    check(
        RoadRenderMesh.structureFaces(blocks, false).size() == 10,
        "only two hidden touching end caps disappear nearby");
    var leaves =
        blocks.stream()
            .map(p -> new Part(p.a(), p.b(), p.width(), p.height(), false, Material.GREEN))
            .toList();
    check(
        RoadRenderMesh.structureFaces(leaves, false).size() == 10,
        "foliage retains internal ends but omits the two coplanar soil-interface bottoms");
    var turn =
        List.of(
            blocks.get(0),
            new Part(blocks.get(0).b(), new V(8, 64, 8), .5, 1, false, Material.CONCRETE));
    check(RoadRenderMesh.compact(turn, true).size() == 2, "LOD never straightens a corner");
    for (Style style : List.of(Style.O4_RAIL, Style.H6_GREEN, Style.O6_GREEN)) {
      var s =
          new Settings(Mode.STRAIGHT, style, style.defaultWidth(), 1, .4, 90)
              .options(RoadProfile.Options.DEFAULT.extras(true, true, true));
      Mesh mesh = RoadGeometry.build(new Node(a, 0, 0), new Node(b, 0, 0), s);
      var ground =
          new Ground() {
            public double top(double x, double z, double y) {
              return 60;
            }

            public boolean blocked(Part p) {
              return false;
            }

            public boolean joined(V p) {
              return false;
            }
          };
      List<Part> parts = RoadStructures.plan(mesh, ground), saved = List.copyOf(parts);
      var simple = RoadRenderMesh.simplify(mesh);
      var deck = RoadRenderMesh.pavement(simple);
      check(
          deck.samples().size() < simple.samples().size(),
          "exact flat road tessellation is reduced");
      for (Sample q : simple.samples()) {
        var p = RoadQueries.project(deck, q.center());
        check(
            p.horizontalDistance() < 1e-8
                && Math.abs(p.sample().center().y() - q.center().y()) < 1e-8,
            "coarse deck occupies identical plane");
        check(Math.abs(p.sample().halfWidth() - q.halfWidth()) < 1e-8, "coarse deck retains edges");
      }
      var near = RoadRenderMesh.structureFaces(parts, false);
      var far = RoadRenderMesh.structureFaces(parts, true);
      check(vertices(far) < vertices(near), "distant facilities submit fewer vertices");
      check(parts.equals(saved), "rendering never changes saved physical parts");
      for (Part p : parts)
        if (p.pier() || p.luminous() || p.height() > 2)
          check(
              RoadRenderMesh.compact(parts, true).contains(p),
              "piers, lamps and tall poles remain in distant view");
      var pieces = RoadRenderMesh.sections(RoadSurface.build(simple, List.of(), List.of()), parts);
      check(
          pieces.values().stream().mapToLong(p -> vertices(p.detail())).sum()
              > pieces.values().stream().mapToLong(p -> vertices(p.distant())).sum(),
          "regionized LOD retains reduction");
      for (Part part : parts)
        for (var f : part.faces()) {
          var v = f.points();
          V e = v.get(1).sub(v.get(0)), g = v.get(2).sub(v.get(0));
          V n =
              new V(
                  e.y() * g.z() - e.z() * g.y(),
                  e.z() * g.x() - e.x() * g.z(),
                  e.x() * g.y() - e.y() * g.x());
          V center = part.a().add(part.b()).mul(.5).add(new V(0, part.height() / 2, 0));
          check(
              n.dot(v.get(0).sub(center)) >= -1e-8,
              "facility winding points outward for face culling");
        }
    }
    System.out.println(
        "RENDER PASS "
            + checks
            + " exact-surface / hidden-face / LOD / collision-invariance / winding assertions");
  }

  public static void main(String[] args) {
    run();
  }
}
