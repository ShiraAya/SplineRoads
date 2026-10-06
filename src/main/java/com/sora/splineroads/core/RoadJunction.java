package com.sora.splineroads.core;

import com.sora.splineroads.core.RoadGeometry.*;
import java.util.*;

/** Lane guides, chevron gore and direction arrows share the actual two branch paths. */
public final class RoadJunction {
  public record Paint(List<V> points, int color) {}

  public record Zone(double from, double to) {
    public boolean contains(double d) {
      return d >= from && d <= to;
    }
  }

  private record Route(Mesh mesh, double origin, int sign) {
    Sample at(double d) {
      double distance = origin + sign * d;
      if (mesh.closed()) distance = ((distance % mesh.length()) + mesh.length()) % mesh.length();
      if (!mesh.closed() && (distance < 0 || distance > mesh.length())) {
        Sample end = distance < 0 ? mesh.first() : mesh.last();
        double along = distance < 0 ? distance : distance - mesh.length();
        V forward = end.left().left().mul(-1);
        return new Sample(end.center().add(forward.mul(along)), end.left(), distance, end.halfWidth());
      }
      return RoadStructures.sample(mesh, distance);
    }

    double available() {
      return mesh.closed() ? mesh.length() / 2 : sign > 0 ? mesh.length() - origin : origin;
    }
  }

  private record Join(Route a, Route b, double nose, double available, boolean parallel) {}

  private static Join join(Mesh ramp, Mesh host, boolean first) {
    if(ramp.settings().style().connectorRamp()||host.settings().style().connectorRamp())return null;
    var endpoint = first ? ramp.first() : ramp.last();
    var projection = RoadQueries.project(host, endpoint.center());
    if (projection.horizontalDistance() > projection.sample().halfWidth() + endpoint.halfWidth() - .10
        || Math.abs(projection.sample().center().y() - endpoint.center().y()) > .12) return null;
    V direction = endpoint.left().left().mul(first ? -1 : 1);
    double dot = direction.dot(projection.tangent().horizontalUnit());
    if (Math.abs(dot) < .94) return null;
    Route a = new Route(ramp, first ? 0 : ramp.length(), first ? 1 : -1),
        b = new Route(host, projection.sample().distance(), dot > 0 ? 1 : -1);
    double available = Math.min(256, Math.min(a.available(), b.available()));
    if (available < 8) return null;
    for (double d = 1; d <= available; d += .5) {
      Sample aa = a.at(d), bb = paired(a, b, d);
      if (aa.center().sub(bb.center()).horizontalLength() >= aa.halfWidth() + bb.halfWidth() - .10)
        return new Join(a, b, d, available, false);
    }
    // An auxiliary lane may share the mainline edge over its entire feeder. The physical fork
    // is in the next leg; still retain merge guidance at this narrowing terminal.
    if (!host.settings().style().ramp()
        && endpoint.halfWidth() * 2 < ramp.settings().width() * .94
        && a.at(available).halfWidth() * 2 >= ramp.settings().width() * .94)
      return new Join(a, b, available, available, true);
    return null;
  }

  /**
   * Only adjacent branches own a separator; a third branch between them prevents a giant
   * cross-gore.
   */
  private static boolean adjacent(
      Join pair, Mesh ramp, Mesh host, List<Mesh> neighbors, boolean first) {
    double d = Math.min(pair.nose, 32);
    V a = pair.a.at(d).center(), b = paired(pair.a, pair.b, d).center(), axis = b.sub(a);
    double length2 = axis.dot(axis);
    if (length2 < .01) return true;
    for (Mesh other : neighbors) {
      if (other == host || other == ramp || !other.settings().style().ramp()) continue;
      Join candidate = join(ramp, other, first);
      if (candidate == null) continue;
      V q = paired(candidate.a, candidate.b, d).center().sub(a);
      double t = q.dot(axis) / length2;
      if (t > .05 && t < .95 && q.sub(axis.mul(t)).horizontalLength() < 2) return false;
    }
    return true;
  }

  public static List<Zone> dividerZones(Mesh ramp, List<Mesh> neighbors) {
    if(MultiFanPaint.applies(ramp))return MultiFanPaint.zones(ramp);
    if (!ramp.settings().style().ramp()) return List.of();
    List<Zone> zones = new ArrayList<>(closureZones(ramp,neighbors));
    for (Mesh other : neighbors) if (twoLaneRamps(ramp, other))
      for (Join pair : twoLaneForks(ramp, other)) {
        Route route = pair.a.mesh == ramp ? pair.a : pair.b;
        double last = pair.a.mesh == ramp ? pair.a.at(forkLimit(pair)).distance()
            : paired(pair.a, pair.b, forkLimit(pair)).distance();
        zones.add(new Zone(Math.min(route.origin, last), Math.max(route.origin, last)));
      }
    for (boolean first : new boolean[] {true, false})
      for (Mesh other : neighbors) {
        if (!other.settings().style().ramp() || twoLaneRamps(ramp, other)) continue;
        Join j = join(ramp, other, first);
        if (j == null) continue;
        double end = forkLimit(j);
        zones.add(first ? new Zone(0, end) : new Zone(ramp.length() - end, ramp.length()));
      }
    return zones;
  }

  private static double closureLength(Mesh ramp, Mesh host, boolean first) {
    double reach = Math.min(Math.min(48, ramp.length() * .60),
        Math.max(24, host.settings().width() * 1.5));
    // Parametric road-tool ramps open their full-width lane after the first third
    // of the lead. Interchange ribbons have independently sampled lead geometry.
    if (ramp.settings().separatedPort(first))
      reach = Math.min(reach,
          6 + .35 * ((first ? ramp.settings().startLead() : ramp.settings().endLead()) - 6));
    else {
      // Sampled interchange leads can widen for longer than the legacy fixed
      // shoulder closure. Do not start a divider inside that unfinished taper.
      double limit = Math.min(96, ramp.length() * .60);
      for (double d = 0; d <= limit; d += .5) {
        Sample at = RoadStructures.sample(ramp, first ? d : ramp.length() - d);
        if (at.halfWidth() * 2 >= ramp.settings().width() * .94) {
          var q=RoadQueries.horizontal(host,at.center());
          boolean addedLane=(first?ramp.first():ramp.last()).halfWidth()<.5
              &&Math.abs(at.center().y()-q.sample().center().y())<.12
              &&q.horizontalDistance()<q.sample().halfWidth()+at.halfWidth()-.5;
          reach = addedLane?d:Math.max(reach, d);
          break;
        }
      }
    }
    return Math.max(8, reach);
  }
  private static boolean taperClosure(Mesh ramp,Mesh host,boolean first){
    // Ordinary roads also have a narrowing auxiliary port, even without a shoulder.
    if(ramp.settings().style().connectorRamp()||!ramp.settings().style().ramp()||host.settings().style().ramp()||host.settings().style()==Style.UNMARKED)return false;
    Sample end=first?ramp.first():ramp.last();if(end.halfWidth()*2>=ramp.settings().width()*.7)return false;
    var projection=RoadQueries.horizontal(host,end.center());return Math.abs(end.center().y()-projection.sample().center().y())<=.1&&projection.horizontalDistance()<=end.halfWidth()+projection.sample().halfWidth()&&Math.abs(end.left().dot(projection.sample().left()))>=.94;
  }
  private static List<Zone> closureZones(Mesh ramp,List<Mesh> neighbors){var out=new ArrayList<Zone>();for(Mesh host:neighbors)for(boolean first:new boolean[]{true,false})if(taperClosure(ramp,host,first)){double reach=closureLength(ramp,host,first);out.add(first?new Zone(0,reach):new Zone(ramp.length()-reach,ramp.length()));}return out;}

  public record EdgeZone(double from, double to, int side) {
    public boolean contains(double d, int edge) {
      return edge == side && d >= from && d <= to;
    }
  }

  public static List<EdgeZone> edgeZones(Mesh mesh, List<Mesh> neighbors) {
    if(MultiFanPaint.applies(mesh))return List.of();
    List<EdgeZone> zones = new ArrayList<>();
    for (Mesh other : neighbors)
      for (boolean first : new boolean[] {true, false}) {
        if (mesh.settings().style().ramp()) {
          Join j = join(mesh, other, first);
          if (j != null && !j.parallel && goreRequired(j) && adjacent(j, mesh, other, neighbors, first))
            addEdge(zones, j.a, j.b, j.nose, j.available);
        }
        if (other.settings().style().ramp()) {
          Join j = join(other, mesh, first);
          if (j != null && !j.parallel && goreRequired(j)) addEdge(zones, j.b, j.a, j.nose, j.available);
        }
      }
    return zones;
  }

  /** Parallel auxiliary roads use a dashed lane boundary, without a false chevron gore. */
  private static boolean parallelFeeder(Mesh ramp, Mesh host) {
    if (ramp.settings().style().connectorRamp() || !ramp.settings().style().ramp() || host.settings().style().ramp()
        || host.settings().style() == Style.UNMARKED) return false;
    for (double d = 0; d <= ramp.length(); d += Math.max(.5, ramp.length() / 24)) {
      Sample a = RoadStructures.sample(ramp, d);
      var b = RoadQueries.horizontal(host, a.center());
      if (Math.abs(a.center().y() - b.sample().center().y()) > .12
          // Two-lane feeders have a wider lateral taper before their parallel portion.
          || Math.abs(a.left().dot(b.sample().left())) < .90
          || b.horizontalDistance() > b.sample().halfWidth() + a.halfWidth() + .25)
        return false;
    }
    return true;
  }

  private static void addEdge(List<EdgeZone> out, Route a, Route b, double nose, double available) {
    Sample aa = a.at(nose), bb = paired(a, b, nose);
    int side = aa.left().dot(bb.center().sub(aa.center())) > 0 ? 1 : -1;
    double begin = a.origin + a.sign * Math.max(0, nose - 28);
    double end = a.origin + a.sign * Math.min(available, nose + 5);
    // Only the painted gore replaces solid borders. Clipping the whole approach erased
    // exposed edges when an auxiliary carriageway continued through a second branch.
    out.add(new EdgeZone(Math.min(begin, end), Math.max(begin, end), side));
  }

  public static List<Mesh> intersections(Mesh road, List<Mesh> neighbors) {
    List<Mesh> result = new ArrayList<>();
    for (Mesh other : neighbors) {
      if (other.settings().style() == Style.UNMARKED) {
        result.add(other);
        continue;
      }
      if (!other.settings().style().ramp()||other.settings().style().connectorRamp()) continue;
      for (int i = 0; i < other.samples().size(); i += 6) {
        Sample p = other.samples().get(i);
        if (!RoadQueries.contains(road, p.center(), 0, .05)) continue;
        var q = RoadQueries.project(road, p.center());
        if (q.horizontalDistance() < Math.max(2, other.settings().width() / 2)
            && Math.abs(q.sample().left().dot(p.left())) < .65) {
          result.add(other);
          break;
        }
      }
    }
    return result;
  }

  public static List<Paint> arrows(Mesh ramp, List<Mesh> neighbors) {
    return arrows(ramp, neighbors, intersections(ramp, neighbors));
  }

  public static List<Paint> arrows(Mesh ramp, List<Mesh> neighbors, List<Mesh> crossings) {
    if(ramp.settings().style().connectorRamp())return LaneRampPaint.arrows(ramp,neighbors);
    if(MultiFanPaint.applies(ramp))return List.of();
    if (RoadProfile.modern(ramp.settings().style())) {
      List<Paint> out = new ArrayList<>();
      var closures=closureZones(ramp,neighbors);
      for (var p : laneArrows(ramp, neighbors))
        if (p.points().stream().noneMatch(v->closures.stream().anyMatch(z->z.contains(RoadQueries.project(ramp,v).sample().distance()))) && crossings.stream()
            .noneMatch(m -> p.points().stream().anyMatch(v -> RoadQueries.contains(m, v, .5, .1))))
          out.add(p);
      for (var entry : RoadSignals.approaches(ramp, neighbors))
        stroke(out, entry.inner(), entry.outer(), .4);
      return out;
    }
    if (!ramp.settings().style().ramp()||ramp.settings().options().hideArrows()) return List.of();
    List<Paint> out = new ArrayList<>();
    for (boolean first : new boolean[] {true, false}) {
      double distance = -1;
      for (Mesh host : neighbors) {
        Join j = join(ramp, host, first);
        if (j != null) distance = Math.max(distance, Math.min(j.a.available() - 2, j.nose + 5));
      }
      if (distance < 0) continue;
      Sample s = RoadStructures.sample(ramp, first ? distance : ramp.length() - distance);
      double lateral =
          RoadProfile.modern(ramp.settings().style())
              ? RoadProfile.layout(ramp.settings(), s.halfWidth() * 2).motorCenter()
              : 0;
      arrow(out, s.at(lateral, 0), s.left().left().mul(-1), s.left());
    }
    return out;
  }

  /** Arrow direction follows the configured traffic side; all offsets exclude shoulders/medians. */
  private static List<Paint> laneArrows(Mesh mesh, List<Mesh> neighbors) {
    List<Paint> out = new ArrayList<>();
    Join merge = null;
    var closures = closureZones(mesh, neighbors);
    if (mesh.settings().style().ramp())
      for (Mesh host : neighbors) {
        if (host.settings().style().ramp() || host.settings().style() == Style.UNMARKED) continue;
        if (parallelFeeder(mesh, host) && mesh.last().halfWidth() * 2 >= mesh.settings().width() * .94) continue;
        Join candidate = join(mesh, host, false);
        if (candidate != null && (merge == null || candidate.nose > merge.nose)) merge = candidate;
      }
    for (double d = 12; !mesh.settings().style().ramp() && d < mesh.length() - 5; d += 48) {
      if (RoadAttachments.paint(mesh,d).hideArrows())continue;
      if (merge != null && d > mesh.length() - merge.nose - 8) continue;
      Sample s = RoadStructures.sample(mesh, d);
      if (Double.isFinite(RoadTransitions.dropBoundary(mesh, s))) continue;
      if (mesh.settings().style().ramp() && s.halfWidth() * 2 < mesh.settings().width() * .95)
        continue;
      var l = RoadProfile.layout(mesh, s);
      var c = l.catalog();
      if(mesh.reference()!=null) {
        var raw=LaneSections.reference(mesh);
        int slots=RoadProfile.layout(raw,RoadStructures.sample(raw,d)).catalog().lanes();
        for(int slot=0;slot<slots;slot++)if(LaneSections.active(mesh,d,slot)) {
          var lane=LanePoints.lane(raw,d,slot);
          arrow(out,lane.position(),lane.direction(),s.left().mul(lane.sign()));
        }
        continue;
      }
      for (int i = 0; i < c.lanes(); i++) {
        double lateral;
        if (c.twoWay()) {
          int per = c.lanes() / 2, side = i < per ? -1 : 1;
          lateral = l.medianCenter() + side * (l.median() / 2 + (i % per + .5) * l.laneWidth());
        } else lateral = l.motorMin() + (i + .5) * l.laneWidth();
        int sign = c.twoWay() && (lateral < 0 ? -1 : 1) != l.outside() ? -1 : 1;
        arrow(out, s.at(lateral, 0), s.left().left().mul(-sign), s.left().mul(sign));
      }
    }
    if (merge != null) for (int lane = 0; lane < RoadProfile.catalog(mesh.settings().style()).lanes(); lane++) {
      double back = Math.min(merge.nose - 2, Math.max(36, merge.nose * .60));
      // A two-lane ramp may expose its inner motor lane only just beyond the fork nose.
      for (; back < Math.max(merge.nose + 40, 110) && back < mesh.length() - 4; back += 1) {
        Sample at = RoadStructures.sample(mesh, mesh.length() - back);
        if (at.halfWidth() * 2 < mesh.settings().width() * .94) continue;
        Sample host = RoadQueries.horizontal(merge.b.mesh, at.center()).sample();
        double sign = Math.signum(at.left().dot(host.center().sub(at.center())));
        if (sign == 0) break;
        double inside = guideEdge(merge.b, host, at).sub(at.center()).dot(at.left());
        double outside = -sign * (at.halfWidth() - .3);
        var layout = RoadProfile.layout(mesh, at);
        // The ramp's motor lanes, not the entire two-lane strip, own the merge arrows.
        double laneCenter = layout.motorMin() + (lane + .5) * layout.laneWidth();
        double width = layout.laneWidth();
        if (laneCenter < Math.min(inside, outside) + .7
            || laneCenter > Math.max(inside, outside) - .7) continue;
        if (width < 2) continue;
        V f = at.left().left().mul(-1), toward = at.left().mul(sign);
        double shift = Math.min(.95, width * .22);
        // Center the whole silhouette in the available lane, including its inward head.
        V center = at.at(laneCenter - sign * shift * .5, 0);
        V tail = center.sub(f.mul(2.2)), bend = center.add(f.mul(.2));
        V base = bend.add(f.mul(1.55)).add(toward.mul(shift));
        V aim = base.sub(bend).horizontalUnit(), cross = aim.left();
        V normal = f.left().add(cross).horizontalUnit();
        V miter = normal.mul(.11 / Math.max(.5, normal.dot(f.left())));
        V a = tail.add(f.left().mul(.11)), b = tail.sub(f.left().mul(.11));
        V c = bend.add(miter), d = bend.sub(miter);
        V e = base.add(cross.mul(.11)), g = base.sub(cross.mul(.11));
        int firstPaint = out.size();
        out.add(new Paint(List.of(a, b, d, c), 0xEDEEE2));
        out.add(new Paint(List.of(c, d, g, e), 0xEDEEE2));
        out.add(
            new Paint(
                List.of(base.add(aim.mul(1.1)), base.sub(cross.mul(.52)), base.add(cross.mul(.52))),
                0xEDEEE2));
        double min =
            out.subList(firstPaint, out.size()).stream()
                .flatMap(p -> p.points().stream())
                .mapToDouble(p -> p.sub(at.center()).dot(at.left()))
                .min()
                .orElse(0);
        double max =
            out.subList(firstPaint, out.size()).stream()
                .flatMap(p -> p.points().stream())
                .mapToDouble(p -> p.sub(at.center()).dot(at.left()))
                .max()
                .orElse(0);
        V correction = at.left().mul(laneCenter - (min + max) / 2);
        out.subList(firstPaint, out.size()).replaceAll(
            p -> new Paint(p.points().stream().map(v -> v.add(correction)).toList(), p.color()));
        // Fit the complete arrow (including its head) in this lane at every vertex.
        // Per-face closure filtering used to remove the outer arrowhead while leaving
        // its shaft; a center-only check let the inner head cross the main boundary.
        boolean fits = true;
        for (Paint paint : out.subList(firstPaint, out.size())) for (V point : paint.points()) {
          var q = RoadQueries.horizontal(mesh, point);
          var profile = RoadProfile.layout(mesh, q.sample());
          double lo = profile.motorMin() + lane * profile.laneWidth() + .18;
          double hi = lo + profile.laneWidth() - .36;
          var h = RoadQueries.horizontal(merge.b.mesh, point).sample();
          V boundary = guideEdge(merge.b, h, q.sample());
          double towardHost = Math.signum(q.sample().left().dot(h.center().sub(q.sample().center())));
          if (q.lateral() < lo || q.lateral() > hi
              || point.sub(boundary).dot(q.sample().left()) * towardHost > -.18
              || closures.stream().anyMatch(z -> z.contains(q.sample().distance()))) fits = false;
        }
        if (!fits) { out.subList(firstPaint, out.size()).clear(); continue; }
        break;
      }
    }
    return out.stream()
        .map(
            p ->
                new Paint(
                    p.points().stream()
                        .map(
                            v -> {
                              var q = RoadQueries.project(mesh, v);
                              return new V(v.x(), q.sample().center().y(), v.z());
                            })
                        .toList(),
                    p.color()))
        .toList();
  }

  public static List<Paint> markings(Mesh ramp, List<Mesh> hosts) {
    return markings(ramp, hosts, hosts);
  }

  public static List<Paint> markings(Mesh ramp, List<Mesh> hosts, List<Mesh> neighbors) {
    return markings(ramp, hosts, neighbors, false);
  }

  public static List<Paint> markings(Mesh ramp, List<Mesh> hosts, List<Mesh> neighbors, boolean mainOwned) {
    if(MultiFanPaint.applies(ramp))return MultiFanPaint.markings(ramp,neighbors);
    if (!ramp.settings().style().ramp()||ramp.settings().style().connectorRamp()) {
      List<Paint> out = terminalMarkings(ramp);
      if (mainOwned) mainGuides(out, ramp, neighbors);
      return out;
    }
    List<Paint> out = new ArrayList<>();
    // The lower-priority surface emits both bands, regardless of which branch owns
    // the endpoint (a loop can attach to the middle of a continuous collector).
    for (Mesh host : hosts) if (twoLaneRamps(ramp, host))
      for (Join pair : twoLaneForks(ramp, host))
        if (adjacent(pair, pair.a.mesh, pair.b.mesh, neighbors, pair.a.sign > 0))
          twoLaneForkGuides(out, pair);
    for (Mesh host : hosts) if (parallelFeeder(ramp, host)) {
      // One owner and one dash phase for the entire shared lane, not one per endpoint.
      for (double d = 1; !mainOwned && d < ramp.length(); d += 3) {
        Sample a = RoadStructures.sample(ramp, d),
            b = RoadStructures.sample(ramp, Math.min(ramp.length(), d + 1.5));
        var ah = RoadQueries.horizontal(host, a.center()).sample();
        var bh = RoadQueries.horizontal(host, b.center()).sample();
        Route route = new Route(host, 0, 1);
        stroke(out, guideEdge(route, ah, a), guideEdge(route, bh, b), .15);
      }
      taperMarkings(out, ramp, host);
      return out;
    }
    for (boolean first : new boolean[] {true, false})
      for (Mesh host : hosts) {
        if (host.settings().style() == Style.UNMARKED || twoLaneRamps(ramp, host)) continue;
        Join pair = join(ramp, host, first);
        if (pair == null || !adjacent(pair, ramp, host, neighbors, first)) continue;
        Route a = pair.a, b = pair.b;
        double nose = pair.nose, available = pair.available;
        if (parallelFeeder(ramp, host) || pair.parallel) {
          for (double d = 1; !mainOwned && d < available; d += 3)
            stroke(out, guideEdge(b, paired(a, b, d), a.at(d)),
                guideEdge(b, paired(a, b, Math.min(available, d + 1.5)),
                    a.at(Math.min(available, d + 1.5))), .15);
          continue;
        }
        if (host.settings().style().ramp()) {
          rampGuides(out, pair);
          continue;
        }
        if (!goreRequired(pair)) {
          // An unchanged ordinary mainline has no shoulder/lane-drop wedge to hatch.
          // Keep its normal edge and short auxiliary-boundary dashes.
          if (!mainOwned && !host.settings().style().ramp())
            for (double d = 1; d < nose; d += 3)
              stroke(out, guideEdge(b, paired(a, b, d), a.at(d)),
                  guideEdge(b, paired(a, b, Math.min(nose, d + 1.5)),
                      a.at(Math.min(nose, d + 1.5))), .15);
          continue;
        }
        // Pair stations by projection; equal distances on curves are not cross-sections.
        boolean shoulder = shoulder(b.mesh);
        double begin = nose;
        double search = Math.max(1, nose - 28);
        if(shoulder && a.at(0).halfWidth()<.5){
          // An added lane first opens outside the shoulder. Its parallel transfer
          // window is drivable; start the gore only when that lane peels away.
          for(double d=0;d<nose;d+=.5){Sample aa=a.at(d),bb=paired(a,b,d);
            double overlap=aa.halfWidth()+bb.halfWidth()-aa.center().sub(bb.center()).horizontalLength();
            if(aa.halfWidth()*2>=ramp.settings().width()*.999&&overlap>.6&&Math.abs(aa.left().dot(bb.left()))>.9999)search=Math.max(search,d);
          }
        }
        // A full-width loop/branch can fork immediately at the end of a collector.
        // Extend the paint approach onto that paved predecessor; never require extra tails.
        if (a.at(0).halfWidth() * 2 >= ramp.settings().width() * .94)
          search = Math.min(search, nose - 18);
        for (double d = search; d < nose; d += .5) {
          Sample aa = a.at(d), bb = paired(a, b, d);
          double overlap = aa.halfWidth() + bb.halfWidth()
              - aa.center().sub(bb.center()).horizontalLength();
          // The painted wedge begins in the paved overlap, before the physical fork.
          // Waiting for the pavement edges to separate left no room for any chevrons.
          V hostEdge = guideEdge(b, bb, aa), rampEdge = innerEdge(a, aa, bb);
          double opening = hostEdge.sub(rampEdge).dot(separation(aa, bb));
          if (shoulder ? opening > .08 : overlap < 3.5) { begin = d; break; }
        }
        // Short dashed separation guides lead into the wider chevron zone.
        for (double d = 2; !mainOwned && !host.settings().style().ramp() && d < begin; d += 3) {
          double e = Math.min(begin, d + 1.5);
          Sample aa = a.at(d), ab = a.at(e), ba = paired(a, b, d), bb = paired(a, b, e);
          if (aa.center().sub(ba.center()).horizontalLength()
              > Math.min(aa.halfWidth(), ba.halfWidth()))
            stroke(out, guideEdge(b, ba, aa), guideEdge(b, bb, ab), .15);
        }
        if (nose > begin + 2) {
          V prevLeft = null, prevRight = null;
          int steps = (int) Math.ceil((nose - begin) / .5);
          for (int step = 0; step <= steps; step++) {
            double d = begin + (nose - begin) * step / steps;
            V[] edge = gore(a, b, d, begin, nose);
            if (prevLeft != null) {
              stroke(out, prevLeft, edge[0], .16);
              stroke(out, prevRight, edge[1], .16);
            }
            prevLeft = edge[0];
            prevRight = edge[1];
          }
          for (double d = begin + 2; d < nose - 1; d += 4.0) {
            V[] edge = gore(a, b, d, begin, nose),
                tip = gore(a, b, Math.max(begin, d - 1.6), begin, nose);
            V center = tip[0].add(tip[1]).mul(.5);
            stroke(out, edge[0], center, .20);
            stroke(out, center, edge[1], .20);
          }
        }
        // Continue the two borders independently after the physical nose. Never paint a
        // connector across the unpaved gap between the separated carriageways.
        double tail = Math.min(5, available - nose);
        for (double d = nose; d < nose + tail - .01; d += .5) {
          double e = Math.min(nose + tail, d + .5);
          V[] left = goreTail(a, b, d, nose), right = goreTail(a, b, e, nose);
          stroke(out, left[0], right[0], .18);
          stroke(out, left[1], right[1], .18);
        }
      }
    for (Mesh host : hosts) if (!host.settings().style().ramp()) taperMarkings(out, ramp, host);
    return out;
  }

  /** Main roads own the dashed boundary along every tangential auxiliary contact.
   * This cannot disappear when ramp UUID order changes or a feeder is split into legs. */
  private static void mainGuides(List<Paint> out, Mesh main, List<Mesh> neighbors) {
    if (main.settings().style() == Style.UNMARKED) return;
    Route route = new Route(main, 0, 1);
    for (double d = 1; d < main.length(); d += 3) {
      Sample a = RoadStructures.sample(main, d);
      Sample b = RoadStructures.sample(main, Math.min(main.length(), d + 1.5));
      Sample mid = RoadStructures.sample(main, Math.min(main.length(), d + .75));
      for (int side : new int[] {-1, 1}) {
        V edge = mid.at(side * mid.halfWidth(), 0);
        for (Mesh ramp : neighbors) {
          if (!ramp.settings().style().ramp() && ramp.settings().options().lanePoints().link()==null) continue;
          var q = RoadQueries.horizontal(ramp, edge);
          if (Math.abs(q.sample().center().y() - mid.center().y()) > .12
              || q.horizontalDistance() > q.sample().halfWidth() + .12
              || Math.abs(q.sample().left().dot(mid.left())) < .90
              || q.sample().center().sub(mid.center()).dot(mid.left()) * side < 0) continue;
          // Use the motor/shoulder boundary for highways, the paved edge for other roads.
          V other = mid.at(side * (mid.halfWidth() + 2), 0);
          Sample target = new Sample(other, mid.left(), 0, 1);
          stroke(out, guideEdge(route, a, target), guideEdge(route, b, target), .15);
          break;
        }
      }
    }
  }

  /** Ramp-to-ramp throats have a dashed continuation, never a triangular exclusion island. */
  private static void rampGuides(List<Paint> out, Join pair) {
    if (twoLaneFork(pair)) {
      twoLaneForkGuides(out, pair);
      return;
    }
    // Single-lane branches keep the existing common dashed continuation.
    double limit=forkLimit(pair);
    for(double d=0;d<limit;d+=.5){double e=Math.min(limit,d+.5);Sample aa=pair.a.at(d),bb=paired(pair.a,pair.b,d),ab=pair.a.at(e),bc=paired(pair.a,pair.b,e);
      boolean dash=((d+e)/2)%6<3;
      int lanesA=RoadProfile.catalog(pair.a.mesh.settings().style()).lanes(),lanesB=RoadProfile.catalog(pair.b.mesh.settings().style()).lanes();
      double separation=aa.center().sub(bb.center()).horizontalLength();
      double lane=Math.max(2.8,Math.min(pair.a.mesh.settings().width()/Math.max(1,lanesA),pair.b.mesh.settings().width()/Math.max(1,lanesB))*.8);
      if(d<pair.nose&&dash&&(lanesA>1||lanesB>1||separation>lane))forkStroke(out,median(aa,bb),median(ab,bc),.15,pair);
      if(dash && (separation>=lane*2 || d>=pair.nose)) {
        if(lanesA>1)forkStroke(out,aa.center(),ab.center(),.12,pair);
        if(lanesB>1)forkStroke(out,bb.center(),bc.center(),.12,pair);
      }
    }
  }

  private static boolean twoLaneRamps(Mesh a, Mesh b) {
    return a.settings().style().ramp() && b.settings().style().ramp()
        && RoadProfile.catalog(a.settings().style()).lanes() == 2
        && RoadProfile.catalog(b.settings().style()).lanes() == 2;
  }

  private static int meshOrder(Mesh a, Mesh b) {
    for (double[] values : new double[][] {{a.min().x(), b.min().x()}, {a.min().z(), b.min().z()},
        {a.max().x(), b.max().x()}, {a.max().z(), b.max().z()}, {a.length(), b.length()}}) {
      int compare = Double.compare(values[0], values[1]);
      if (compare != 0) return compare;
    }
    return 0;
  }

  private static List<Join> twoLaneForks(Mesh a, Mesh b) {
    if (meshOrder(a, b) > 0) { Mesh swap = a; a = b; b = swap; }
    List<Join> result = new ArrayList<>();
    for (Mesh ramp : List.of(a, b)) for (boolean first : new boolean[] {true, false}) {
      Join candidate = join(ramp, ramp == a ? b : a, first);
      if (candidate == null || candidate.parallel) continue;
      V origin = candidate.a.at(0).center();
      if (result.stream().anyMatch(p -> p.a.at(0).center().distance(origin) < .5
          && p.a.at(1).center().sub(p.a.at(0).center()).dot(
              candidate.a.at(1).center().sub(origin)) > 0)) continue;
      result.add(candidate);
    }
    return result;
  }

  private static boolean twoLaneFork(Join pair) {
    return RoadProfile.catalog(pair.a.mesh.settings().style()).lanes() == 2
        && RoadProfile.catalog(pair.b.mesh.settings().style()).lanes() == 2;
  }

  private static double forkLimit(Join pair) {
    if (!twoLaneFork(pair)) return Math.min(pair.available, pair.nose + 7);
    double lane = Math.max(RoadProfile.layout(pair.a.mesh, pair.a.at(pair.nose)).laneWidth(),
        RoadProfile.layout(pair.b.mesh, paired(pair.a, pair.b, pair.nose)).laneWidth());
    return Math.min(pair.available, pair.nose + Math.max(24, lane * 8));
  }

  /** One lane from each branch forms the two-lane common throat. Each inner lane
   * opens gradually beyond the nose; reversing travel gives the same bounded merge. */
  private static void twoLaneForkGuides(List<Paint> out, Join pair) {
    double limit = forkLimit(pair);
    for (double d = 0; d < limit - 1e-6; d += .5) {
      double e = Math.min(limit, d + .5);
      V[][] a = forkEdges(pair, d), b = forkEdges(pair, e);
      double width = a[0][0].sub(a[0][1]).horizontalLength()
          + a[1][0].sub(a[1][1]).horizontalLength();
      if (d < pair.nose && width < .16) {
        if (((d + e) / 2) % 6 < 3)
          forkStroke(out, a[0][0].add(a[1][0]).mul(.5),
              b[0][0].add(b[1][0]).mul(.5), .15, pair);
      } else for (int side = 0; side < 2; side++)
        forkStroke(out, a[side][0], b[side][0], .16, pair);
      // The inner lane reopens while the exclusion band tapers toward the edge.
      // Its center divider must resume here, not only after the entire fork zone.
      if (d >= pair.nose) for (int side = 0; side < 2; side++) {
        Route route = side == 0 ? pair.a : pair.b;
        Sample sa = side == 0 ? pair.a.at(d) : paired(pair.a, pair.b, d);
        Sample sb = side == 0 ? pair.a.at(e) : paired(pair.a, pair.b, e);
        V ca = sa.at(RoadProfile.layout(route.mesh, sa).motorCenter(), 0);
        V cb = sb.at(RoadProfile.layout(route.mesh, sb).motorCenter(), 0);
        if (ca.sub(a[side][0]).horizontalLength() > .3
            && cb.sub(b[side][0]).horizontalLength() > .3
            && ((sa.distance() + sb.distance()) / 2) % 6 < 3)
          forkStroke(out, ca, cb, .12, pair);
      }
    }
    for (int side = 0; side < 2; side++)
      for (double d = 2; d < limit - 1; d += 4) {
        V[] edge = forkEdges(pair, d)[side];
        double run = Math.min(limit - d, edge[0].sub(edge[1]).horizontalLength());
        if (run < .35) continue;
        // A hatch is one straight diagonal in plan view. Interpolating between
        // moving cross-sections bent it at every change in the paired road frame.
        forkStroke(out, edge[1], forkEdges(pair, d + run)[side][0], .18, pair);
      }
  }

  /** Each pair is [live-lane boundary, inside border of the exclusion band]. */
  private static V[][] forkEdges(Join pair, double d) {
    Sample a = pair.a.at(d), b = paired(pair.a, pair.b, d);
    V middle = median(a, b);
    V[][] edges = new V[2][];
    double open = Settings.smooth(Math.max(0, Math.min(1,
        (d - pair.nose) / Math.max(.5, forkLimit(pair) - pair.nose))));
    for (int i = 0; i < 2; i++) {
      Route route = i == 0 ? pair.a : pair.b;
      Sample at = i == 0 ? a : b, other = i == 0 ? b : a;
      int toward = at.left().dot(other.center().sub(at.center())) >= 0 ? 1 : -1;
      double center = RoadProfile.layout(route.mesh, at).motorCenter();
      double inner = toward * (at.halfWidth() - .3);
      // Within the overlap, partition the band at its common center. Past the
      // physical nose each ramp owns only its own paved strip.
      double common = middle.sub(at.center()).dot(at.left());
      double boundary = center + toward * Math.max(0,
          Math.min((inner - center) * toward, (common - center) * toward));
      V closed = at.at(boundary, 0);
      // Use the very same world-space vertex on both sides of the paved overlap.
      // Projecting it independently onto curved cross-sections leaves a slit in each V.
      if (d < pair.nose && (common - inner) * toward <= 0
          && RoadQueries.contains(pair.a.mesh, middle, .03, .1)
          && RoadQueries.contains(pair.b.mesh, middle, .03, .1)) closed = middle;
      V flow = at.at(center + (boundary - center) * open, 0);
      edges[i] = new V[] {flow, closed};
    }
    return edges;
  }

  private static void forkStroke(List<Paint> out,V a,V b,double width,Join pair){
    var paint=new ArrayList<Paint>();stroke(paint,a,b,width);
    for(Paint face:paint)if(face.points().stream().allMatch(v->RoadQueries.contains(pair.a.mesh,v,.03,.1)||RoadQueries.contains(pair.b.mesh,v,.03,.1)))out.add(face);
  }

  private static V taperEdge(Mesh ramp, Mesh host, boolean first, double d, boolean outer) {
    Sample at = RoadStructures.sample(ramp, first ? d : ramp.length() - d);
    Sample hs = RoadQueries.horizontal(host, at.center()).sample();
    if (!outer) return guideEdge(new Route(host, 0, 1), hs, at);
    int toward = at.left().dot(hs.center().sub(at.center())) > 0 ? 1 : -1;
    int outside = hs.left().dot(at.center().sub(hs.center())) >= 0 ? 1 : -1;
    V rampEdge = at.at(-toward * (at.halfWidth() - .3), 0);
    V mainEdge = hs.at(outside * (hs.halfWidth() - .3), 0);
    // Follow the exposed boundary of the paved union; a narrow ramp footprint may still
    // lie inside the main shoulder, where its own edge would draw a second floating line.
    return rampEdge.sub(mainEdge).dot(hs.left()) * outside > 0 ? rampEdge : mainEdge;
  }

  private static double taperDiagonal(Mesh ramp, Mesh host, boolean first, double inner, double outer) {
    V start = taperEdge(ramp, host, first, inner, false);
    V delta = taperEdge(ramp, host, first, outer, true).sub(start);
    V axis = RoadQueries.horizontal(host, start).tangent().horizontalUnit();
    V away = (first ? ramp.first() : ramp.last()).left().left().mul(first ? -1 : 1);
    if (axis.dot(away) < 0) axis = axis.mul(-1);
    return delta.dot(axis) - Math.abs(delta.dot(axis.left()));
  }

  private static void taperMarkings(List<Paint> out, Mesh ramp, Mesh host) {
    for (boolean first : new boolean[] {true, false}) {
      if (!taperClosure(ramp, host, first)) continue;
      double reach = closureLength(ramp,host,first);
      // Red-line reference: inner end is nearer the narrow terminal, outer end
      // farther along the widening auxiliary road. Mirror in station space at exits.
      double lo = 0, hi = reach;
      for (int step = 0; step < 24; step++) {
        double mid = (lo + hi) / 2;
        if (taperDiagonal(ramp, host, first, mid, reach) > 0) lo = mid; else hi = mid;
      }
      double innerEnd = (lo + hi) / 2;
      for (boolean outer : new boolean[] {false, true}) {
        double limit = outer ? reach : innerEnd;
        for (double d = 0; d < limit - 1e-6; d += .5)
          stroke(out, taperEdge(ramp, host, first, d, outer),
              taperEdge(ramp, host, first, Math.min(limit, d + .5), outer), .16);
      }
      double spacing=(first?ramp.first():ramp.last()).halfWidth()<.5?2.5:4;
      for (double d = 1; d < innerEnd - 2; d += spacing) {
        double from = d, to = reach;
        // The outer boundary widens between endpoints: solve the actual diagonal,
        // rather than using its width at the inner station (which makes steep bars).
        for (int step = 0; step < 24; step++) {
          double mid = (from + to) / 2;
          if (taperDiagonal(ramp, host, first, d, mid) < 0) from = mid; else to = mid;
        }
        double outer = (from + to) / 2;
        if (outer - d > .4)
          stroke(out, taperEdge(ramp, host, first, d, false),
              taperEdge(ramp, host, first, outer, true), .18);
      }
      stroke(out, taperEdge(ramp, host, first, innerEnd, false),
          taperEdge(ramp, host, first, reach, true), .18);
    }
  }

  private static V[] goreTail(Route a, Route b, double d, double nose) {
    V[] edge = borders(a, b, d);
    Sample aa = a.at(d), bb = paired(a, b, d);
    if (shoulder(b.mesh)) return new V[] {edge[0], guideEdge(b, bb, aa)};
    V side = separation(aa, bb);
    double half = Math.min(1.1, Math.min(aa.halfWidth(), bb.halfWidth()) * .3);
    double extra = Math.max(0, half - .3) * Math.max(0, 1 - (d - nose) / 5);
    return new V[] {edge[0].sub(side.mul(extra)), edge[1].add(side.mul(extra))};
  }

  private static List<Paint> terminalMarkings(Mesh mesh) {
    List<Paint> out = new ArrayList<>();
    for (double d = .5; d <= mesh.length(); d += .5) {
      Sample a = RoadStructures.sample(mesh, d - .5), b = RoadStructures.sample(mesh, d);
      double ia = RoadTransitions.dropBoundary(mesh, a), ib = RoadTransitions.dropBoundary(mesh, b);
      if (!Double.isFinite(ia) || !Double.isFinite(ib)) continue;
      for (int side : new int[] {-1, 1}) {
        double oa = Math.abs(RoadProfile.layout(mesh, a).outer(side)) - .2;
        double ob = Math.abs(RoadProfile.layout(mesh, b).outer(side)) - .2;
        if (oa - ia < .1 || ob - ib < .1) continue;
        stroke(out, a.at(side * ia, 0), b.at(side * ib, 0), .15);
        if (((int)Math.round(d * 2)) % 8 == 0) {
          Sample tip = RoadStructures.sample(mesh, Math.min(mesh.length(), d + 1.3));
          double inner = RoadTransitions.dropBoundary(mesh, tip);
          if (Double.isFinite(inner)) stroke(out, b.at(side * ob, 0), tip.at(side * inner, 0), .18);
        }
      }
    }
    return out;
  }

  /** Exact painted closure polygons: clip full dash faces, including those crossing a taper. */
  public static List<List<V>> terminalCuts(Mesh mesh) {
    List<List<V>> out = new ArrayList<>();
    if (!mesh.settings().options().ends().persistent()) return out;
    for (double d = .25; d <= mesh.length() + .249; d += .25) {
      Sample a = RoadStructures.sample(mesh, Math.max(0, d - .25));
      Sample b = RoadStructures.sample(mesh, Math.min(d, mesh.length()));
      double ia = RoadTransitions.dropBoundary(mesh, a), ib = RoadTransitions.dropBoundary(mesh, b);
      if (!Double.isFinite(ib)) continue;
      if (!Double.isFinite(ia)) ia = Math.abs(RoadProfile.layout(mesh, a).outer(1));
      for (int side : new int[] {-1, 1})
        out.add(List.of(a.at(side * (ia - .15), 0), a.at(side * (a.halfWidth() + 1), 0),
            b.at(side * (b.halfWidth() + 1), 0), b.at(side * (ib - .15), 0)));
    }
    return out;
  }

  private static V separation(Sample a, Sample b) {
    V d = b.center().sub(a.center());
    return d.horizontalLength() < 1e-5 ? a.left() : d.horizontalUnit();
  }

  private static V median(Sample a, Sample b) {
    return a.center()
        .add(b.center())
        .mul(.5)
        .add(separation(a, b).mul((a.halfWidth() - b.halfWidth()) / 2));
  }

  private static V guideEdge(Route route, Sample at, Sample other) {
    var layout = RoadProfile.layout(route.mesh, at);
    int side = at.left().dot(other.center().sub(at.center())) >= 0 ? 1 : -1;
    if (layout.catalog().type() == RoadProfile.Type.HIGHWAY)
      return at.at(layout.outer(side), 0);
    return innerEdge(route, at, other);
  }

  private static V innerEdge(Route route, Sample at, Sample other) {
    int side = at.left().dot(other.center().sub(at.center())) >= 0 ? 1 : -1;
    double offset = side * (at.halfWidth() - .3);
    // The physical fork is between pavement edges, not between the motor lane and shoulder.
    return at.at(offset, 0);
  }

  private static Sample paired(Route a, Route b, double distance) {
    return RoadQueries.horizontal(b.mesh, a.at(distance).center()).sample();
  }

  private static V[] borders(Route a, Route b, double d) {
    Sample aa = a.at(d), bb = paired(a, b, d);
    return new V[] {innerEdge(a, aa, bb), innerEdge(b, bb, aa)};
  }

  private static boolean shoulder(Mesh mesh) {
    return RoadProfile.layout(mesh, mesh.first()).shoulderWidth() > .1;
  }

  private static boolean goreRequired(Join join) {
    if (join.b.mesh.settings().style().ramp()) return false;
    if (shoulder(join.b.mesh)) return true;
    for (double d = 0; d <= join.nose; d += 2)
      if (Double.isFinite(RoadTransitions.dropBoundary(join.b.mesh, paired(join.a, join.b, d))))
        return true;
    return false;
  }

  private static V[] gore(Route a, Route b, double d, double start, double end) {
    Sample aa = a.at(d), bb = paired(a, b, d);
    if (shoulder(b.mesh)) {
      V host = guideEdge(b, bb, aa), ramp = innerEdge(a, aa, bb);
      double opening = host.sub(ramp).dot(separation(aa, bb));
      if (opening < 0) ramp = host;
      double blend = Settings.smooth(Math.max(0, Math.min(1, (d - start) / Math.min(6, end - start))));
      // Anchor one border to the main motor/shoulder line and the other to the ramp's
      // inner stripe. Never center a constant-width wedge inside a driving lane.
      return new V[] {host.add(ramp.sub(host).mul(blend)), host};
    }
    V center = median(aa, bb), side = separation(aa, bb);
    double half = Math.min(1.1, Math.min(aa.halfWidth(), bb.halfWidth()) * .3);
    half *= Settings.smooth(Math.max(0, Math.min(1, (d - start) / (end - start))));
    return new V[] {center.sub(side.mul(half)), center.add(side.mul(half))};
  }

  private static void arrow(List<Paint> out, V p, V f, V left) {
    stroke(out, p.sub(f.mul(1.4)), p.add(f.mul(.2)), .22);
    out.add(
        new Paint(
            List.of(
                p.add(f.mul(1.5)),
                p.add(f.mul(.2)).add(left.mul(.65)),
                p.add(f.mul(.2)).sub(left.mul(.65))),
            0xEDEEE2));
  }

  private static void stroke(List<Paint> out, V a, V b, double width) {
    V d = b.sub(a);
    if (d.horizontalLength() < 1e-6) return;
    V side = d.horizontalUnit().left().mul(width / 2);
    out.add(new Paint(List.of(a.add(side), a.sub(side), b.sub(side), b.add(side)), 0xEDEEE2));
  }

  private RoadJunction() {}
}
