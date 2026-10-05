package com.sora.splineroads;

import com.sora.splineroads.core.*;
import com.sora.splineroads.core.RoadGeometry.*;
import java.util.*;
import java.nio.file.*;

/** Regression coverage for bounded shoulder hatching and dashed ramp merges. */
public final class Revision19Validation {
  private static int checks, cases, rampPairs, closures;
  private static void check(boolean value, String message) {
    checks++;
    if (!value) throw new AssertionError(message);
  }
  private static Settings settings(Style style) {
    return new Settings(Mode.STRAIGHT, style, style.defaultWidth(), 1, .4, 90);
  }
  private static V start(RoadJunction.Paint paint) {
    return paint.points().get(0).add(paint.points().get(1)).mul(.5);
  }
  private static V end(RoadJunction.Paint paint) {
    return paint.points().get(2).add(paint.points().get(3)).mul(.5);
  }
  public static void main(String[] args) throws Exception { run(); }
  public static void run() throws Exception {
    for (Style first : List.of(Style.O6_GREEN, Style.H6_RAIL))
      for (Style second : List.of(Style.O4_YELLOW, Style.H4_RAIL))
        for (var preset : List.of(InterchangePlanner.Preset.CLOVERLEAF, InterchangePlanner.Preset.STACK,
            InterchangePlanner.Preset.TRUMPET, InterchangePlanner.Preset.Y))
          for (int lanes : new int[] {1, 2}) for (boolean left : new boolean[] {false, true}) {
            cases++;
            var options = new InterchangePlanner.Options(preset, left, lanes, 96, 20, 5, 2);
            var a = settings(first); var b = settings(second);
            Node[] nodes = InterchangeValidation.raised(600, left ? .37 : 0,
                InterchangePlanner.minimumDifference(a, b, options), false);
            if (preset.three) nodes = Arrays.copyOf(nodes, 3);
            var plan = InterchangePlanner.plan(nodes, a, b, options);
            var all = plan.legs().stream().map(InterchangePlanner.Leg::mesh).toList();
            var ramps = all.stream().filter(m -> m.settings().style().ramp()).toList();
            for (Mesh ramp : ramps) {
              var neighbors = all.stream().filter(m -> m != ramp).toList();
              for (Mesh host : ramps) if (host != ramp) {
                var guides = RoadJunction.markings(ramp, List.of(host), neighbors, true);
                if (!guides.isEmpty()) rampPairs++;
                for (var guide : guides) {
                  V p = start(guide), q = end(guide), middle = p.add(q).mul(.5);
                  if (lanes == 1) check(p.distance(q) <= .53, "ramp merge has a long triangle/chevron stroke");
                  V direction = q.sub(p).horizontalUnit();
                  double t1 = Math.abs(direction.dot(RoadQueries.horizontal(ramp, middle).tangent().horizontalUnit()));
                  double t2 = Math.abs(direction.dot(RoadQueries.horizontal(host, middle).tangent().horizontalUnit()));
                  if (lanes == 1) check(Math.max(t1, t2) > .8, "ramp guide crosses the lane instead of following it");
                  for (V vertex : guide.points())
                    check(RoadQueries.contains(ramp, vertex, .18, .2)
                        || RoadQueries.contains(host, vertex, .18, .2), "ramp dash leaves paved union");
                }
              }
              for (Mesh host : all.subList(0, 2)) {
                if (RoadProfile.layout(host, host.first()).shoulderWidth() < .1) continue;
                var guides = RoadJunction.markings(ramp, List.of(host), neighbors, true);
                for (boolean atStart : new boolean[] {true, false}) {
                  Sample terminal = atStart ? ramp.first() : ramp.last();
                  if (terminal.halfWidth() * 2 >= ramp.settings().width() * .7) continue;
                  var projection = RoadQueries.horizontal(host, terminal.center());
                  if (Math.abs(terminal.center().y() - projection.sample().center().y()) > .1
                      || projection.horizontalDistance() > terminal.halfWidth() + projection.sample().halfWidth()
                      || Math.abs(terminal.left().dot(projection.sample().left())) < .94) continue;
                  V forward = terminal.left().left().mul(atStart ? -1 : 1);
                  double far = -1; int stripes = 0, borders = 0;
                  for (var guide : guides) {
                    V p = start(guide), q = end(guide), middle = p.add(q).mul(.5);
                    if (middle.sub(terminal.center()).horizontalLength() > 56) continue;
                    Sample otherEnd=atStart?ramp.last():ramp.first();
                    if(middle.sub(terminal.center()).horizontalLength()>middle.sub(otherEnd.center()).horizontalLength())continue;
                    double along = middle.sub(terminal.center()).dot(forward);
                    if (along < -.3) continue;
                    double length = p.distance(q);
                    if (length > 1) stripes++; else borders++;
                    for (V vertex : guide.points()) {
                      double distance = RoadQueries.project(ramp, vertex).sample().distance();
                      double station = atStart ? distance : ramp.length() - distance;
                      far = Math.max(far, station);
                      check(station <= 48.3, "shoulder stripe overshoots its outlined closure: " + station+" / "+first+" "+second+" "+preset+" lanes="+lanes+" left="+left+" start="+atStart+" terminal="+terminal.center()+" other="+otherEnd.center()+" p="+p+" q="+q+" vertex="+vertex);
                    }
                  }
                  check(stripes >= 2 && borders > 20, "closure has diagonal strokes and both continuous borders");
                  check(far > Math.min(23.7,ramp.length()*.55), "closure reaches its final diagonal cap");
                  closures++;
                }
              }
            }
            if (first == Style.H6_RAIL && second == Style.H4_RAIL && lanes == 2 && !left
                && preset == InterchangePlanner.Preset.STACK) {
              exportMerge(plan);
            }
          }
    check(rampPairs > 100 && closures > 100, "real merge pairs and shoulder tips exercised");
    System.out.println("REVISION 19 PASS: " + checks + " assertions / " + cases
        + " layouts / " + rampPairs + " ramp contacts / " + closures + " shoulder closures");
  }
  private static void exportMerge(InterchangePlanner.Plan plan) throws Exception {
    var all = plan.legs().stream().map(l -> RoadRenderMesh.simplify(l.mesh())).toList();
    double best = 0; Mesh ramp = null, host = null; V focus = null;
    for (Mesh a : all) if (a.settings().style().ramp())
      for (Mesh b : all) if (b != a && b.settings().style().ramp()) {
        var guides = RoadJunction.markings(a, List.of(b), all.stream().filter(m -> m != a).toList());
        if (guides.size() > best) {
          best = guides.size(); ramp = a; host = b;
          focus = start(guides.get(guides.size() / 2));
        }
      }
    check(ramp != null, "stack has a real shared ramp throat");
    var a = RoadSurface.build(ramp, List.of(), List.of(host));
    var b = RoadSurface.build(host, List.of(ramp), List.of(ramp));
    var faces = new ArrayList<RoadSurface.Face>(a.pavement()); faces.addAll(b.pavement());
    faces.addAll(a.markings()); faces.addAll(b.markings());
    var projection = RoadQueries.horizontal(ramp, focus);
    V forward = projection.tangent().horizontalUnit(), side = forward.left();
    export("ramp-merge", faces, focus, forward, side, -40, -20, 80, 40);
    for (Mesh m : all) if (m.settings().style().ramp())
      for (boolean first : new boolean[] {true, false}) {
        Sample terminal = first ? m.first() : m.last();
        if (terminal.halfWidth() * 2 >= m.settings().width() * .7) continue;
        for (Mesh main : all.subList(0, 2)) {
          var q = RoadQueries.horizontal(main, terminal.center());
          if (q.horizontalDistance() > terminal.halfWidth() + q.sample().halfWidth()
              || Math.abs(q.sample().center().y() - terminal.center().y()) > .1) continue;
          int outside = q.sample().left().dot(terminal.center().sub(q.sample().center())) > 0 ? 1 : -1;
          V origin = q.sample().at(RoadProfile.layout(main, q.sample()).outer(outside), 0);
          var road = RoadSurface.build(main, List.of(), List.of(m));
          var auxiliary = RoadSurface.build(m, List.of(main), List.of(main));
          var close = new ArrayList<RoadSurface.Face>(road.pavement()); close.addAll(auxiliary.pavement());
          close.addAll(road.markings()); close.addAll(auxiliary.markings());
          export("shoulder-closure", close, origin, terminal.left().left().mul(first ? -1 : 1),
              q.sample().left().mul(outside), -3, -4, 58, 15);
          return;
        }
      }
  }
  static void export(String name, List<RoadSurface.Face> faces, V origin, V forward, V side,
      double x, double y, double w, double h) throws Exception {
    var svg = new StringBuilder("<svg xmlns=\"http://www.w3.org/2000/svg\" viewBox=\""
        + x + " " + y + " " + w + " " + h + "\">");
    for (var face : faces) {
      svg.append("<polygon fill=\"").append(face.color() == 0xEDEEE2 ? "#f2f1e8" : "#626267")
          .append("\" points=\"");
      for (V p : face.points()) {
        V q = p.sub(origin); svg.append(q.dot(forward)).append(',').append(q.dot(side)).append(' ');
      }
      svg.append("\"/>");
    }
    svg.append("</svg>"); Files.createDirectories(Path.of("validation"));
    Files.writeString(Path.of("validation/revision19-" + name + ".svg"), svg);
  }
}
