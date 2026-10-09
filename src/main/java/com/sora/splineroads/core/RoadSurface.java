package com.sora.splineroads.core;

import com.sora.splineroads.core.RoadGeometry.*;
import java.util.*;

/** Cached junction mesh. Coplanar overlap has one owner; internal stripes and walls are clipped. */
public final class RoadSurface {
  public enum Texture {
    PLAIN,
    CONCRETE,
    METAL,
    SOIL,
    LEAVES,
    SIGNAL_HOUSING,
    SIGNAL_GREEN,
    SIGNAL_RED,
    SIGNAL_AMBER,
    SIGNAL_ATLAS, WALK_STONE_BRICKS, WALK_BRICKS, WALK_ANDESITE, WALK_DIORITE, WALK_GRANITE, WALK_SMOOTH_STONE, CB_SIGNS, CB_NOISE, CB_NOISE_GLASS, OAK_LOG, BIRCH_LOG, SPRUCE_LOG, CHERRY_LOG, OAK_LEAVES, BIRCH_LEAVES, SPRUCE_LEAVES, CHERRY_LEAVES, FLOWERS
  }

  public record UV(double u, double v) {}

  public record Face(List<V> points, int color, boolean emissive, Texture texture, List<UV> uv) {
    public Face(List<V> points, int color, boolean emissive, Texture texture) {
      this(points, color, emissive, texture, List.of());
    }
    public Face(List<V> points, int color) {
      this(points, color, false, Texture.PLAIN);
    }

    public Face(List<V> points, int color, boolean emissive) {
      this(points, color, emissive, Texture.PLAIN);
    }
  }

  public record Geometry(List<Face> pavement, List<Face> markings) {}

  /** Ground closure fills are real pavement in both VBO and terrain rendering.
   * Neighboring decks own their coplanar overlap; sides remain structural solids. */
  public static Geometry closurePavement(Geometry geometry,List<RoadStructures.Part> parts,List<Mesh> decks){
    if(parts.stream().noneMatch(LaneClosureLandscape::paved))return geometry;
    var pavement=new ArrayList<>(geometry.pavement());var occupied=new Grid(decks);
    for(var part:parts)if(LaneClosureLandscape.paved(part)){
      var top=part.base().stream().map(v->v.add(new V(0,part.height(),0))).toList();
      for(int i=1;i<top.size()-1;i++)for(var poly:visible(List.of(top.get(0),top.get(i),top.get(i+1)),occupied,.025))
        pavement.add(new Face(poly,0xDCDCDC));
    }
    return new Geometry(List.copyOf(pavement),geometry.markings());
  }

  /** A host owns its paint at a lane connector, including when the host is itself a connector. */
  public static boolean higherPriority(UUID candidate,Mesh a,UUID current,Mesh b){
    if(candidate.equals(current))return false;
    var am=a.settings().options().lanePoints();var bm=b.settings().options().lanePoints();
    boolean ar=am.link()!=null||a.settings().style().ramp(),br=bm.link()!=null||b.settings().style().ramp();
    if(ar!=br)return !ar;
    // Compare ONE globally transitive key. Never combine pairwise host tests with UUIDs:
    // that creates A>B>C>A even for an acyclic host chain and removes all three decks.
    int ad=am.link()==null?0:Math.max(1,am.priorityDepth());
    int bd=bm.link()==null?0:Math.max(1,bm.priorityDepth());
    return ad!=bd?ad<bd:candidate.compareTo(current)<0;
  }

  private record Cut(List<V> points, double minX, double minZ, double maxX, double maxZ) {
    Cut(List<V> p) {
      this(
          p,
          p.stream().mapToDouble(V::x).min().orElseThrow(),
          p.stream().mapToDouble(V::z).min().orElseThrow(),
          p.stream().mapToDouble(V::x).max().orElseThrow(),
          p.stream().mapToDouble(V::z).max().orElseThrow());
    }

    double height(V p) {
      V a = points.get(0), b = points.get(1).sub(a), c = points.get(2).sub(a), q = p.sub(a);
      double det = b.x() * c.z() - b.z() * c.x();
      return a.y()
          + ((q.x() * c.z() - q.z() * c.x()) * b.y() + (b.x() * q.z() - b.z() * q.x()) * c.y())
              / det;
    }

    boolean overlaps(Cut b) {
      return maxX >= b.minX - 1e-8
          && b.maxX >= minX - 1e-8
          && maxZ >= b.minZ - 1e-8
          && b.maxZ >= minZ - 1e-8;
    }
  }

  private static final class Grid {
    final Map<Long, List<Cut>> cells = new HashMap<>();

    Grid(List<Mesh> meshes) {
      for (Mesh original : meshes) {
        Mesh mesh=RoadRenderMesh.pavement(original);
        for (int i = 1; i < mesh.samples().size(); i++) {
          Sample a = mesh.samples().get(i - 1), b = mesh.samples().get(i);
          for(var strip:LaneDeck.strips(mesh,a,b)) {
            add(List.of(strip.al(),strip.ar(),strip.br()));add(List.of(strip.al(),strip.br(),strip.bl()));
          }
        }
      }
    }

    void add(List<V> points) {
      if (Math.abs(JunctionPaint.area(points)) < 1e-9) return;
      Cut c = new Cut(ccw(points));
      for (int x = cell(c.minX); x <= cell(c.maxX); x++)
        for (int z = cell(c.minZ); z <= cell(c.maxZ); z++)
          cells.computeIfAbsent(key(x, z), k -> new ArrayList<>()).add(c);
    }

    Set<Cut> candidates(List<V> points) {
      Cut box = new Cut(points);
      Set<Cut> found = new LinkedHashSet<>();
      for (int x = cell(box.minX); x <= cell(box.maxX); x++)
        for (int z = cell(box.minZ); z <= cell(box.maxZ); z++)
          for (Cut c : cells.getOrDefault(key(x, z), List.of())) if (c.overlaps(box)) found.add(c);
      return found;
    }

    static int cell(double v) {
      return (int) Math.floor(v / 8);
    }

    static long key(int x, int z) {
      return ((long) x << 32) ^ (z & 0xffffffffL);
    }
  }

  public static Geometry custom(Geometry base, Mesh mesh, List<Face> paint) {
    List<Face> clipped = new ArrayList<>();
    Grid grid = new Grid(List.of(mesh));
    for (Face f : paint) paintOnRoad(clipped, new RoadJunction.Paint(f.points(), f.color()), grid);
    return new Geometry(base.pavement(), List.copyOf(clipped));
  }

  public static boolean painted(Mesh mesh,double station){
    double d=station+mesh.settings().options().ends().paintPhase()+mesh.settings().options().ends().trimmedStart();
    return ((d%6)+6)%6<3;
  }
  /** Split shifted dashes at their exact ends; pavement sampling is unchanged. */
  private static List<Sample> markingSamples(Mesh mesh){
    double phase=mesh.settings().options().ends().paintPhase()+mesh.settings().options().ends().trimmedStart();
    if(Math.abs(phase%3)<1e-8)return RoadAttachments.splitPaint(mesh,mesh.samples());
    var out=new ArrayList<Sample>();var samples=mesh.samples();out.add(samples.get(0));
    for(int i=1;i<samples.size();i++){
      double a=samples.get(i-1).distance(),b=samples.get(i).distance();
      for(double at=(Math.floor((a+phase)/3)+1)*3-phase;at<b-1e-7;at+=3)if(at>a+1e-7)out.add(RoadStructures.sample(mesh,at));
      out.add(samples.get(i));
    }
    return RoadAttachments.splitPaint(mesh,out);
  }
  private static boolean overrideLine(List<Face> out,Mesh mesh,Sample a,Sample b,String key,double x,double y,Grid cuts){
    var e=RoadAttachments.line(mesh,(a.distance()+b.distance())/2,key);var p=e.pattern();
    if(key.equals("center:-1")||key.equals("center:1")){
      var other=RoadAttachments.line(mesh,(a.distance()+b.distance())/2,key.equals("center:-1")?"center:1":"center:-1");
      if(p==RoadLaneLines.Pattern.DEFAULT&&other.pattern().mixed())return true;
      if(p.mixed()&&other.pattern()==RoadLaneLines.Pattern.DEFAULT){x=RoadProfile.layout(mesh,a).medianCenter();y=RoadProfile.layout(mesh,b).medianCenter();}
    }
    if(p==RoadLaneLines.Pattern.DEFAULT)return false;
    if(p==RoadLaneLines.Pattern.NONE||p.dashed()&&!painted(mesh,(a.distance()+b.distance())/2))return true;
    if(p.doubled())for(int side:new int[]{-1,1}){if(p.mixed()&&p.dashedSide(side)&&!painted(mesh,(a.distance()+b.distance())/2))continue;stripe(out,a,b,x+side*(e.width()+.06),y+side*(e.width()+.06),e.width(),p.yellow(),cuts);}
    else stripe(out,a,b,x,y,e.width(),p.yellow(),cuts);return true;
  }
  public static Geometry build(Mesh mesh, List<Mesh> higherPriority, List<Mesh> neighbors) {
    Grid owners = new Grid(higherPriority), joined = new Grid(neighbors);
    var edgeJoin=RoadRailJoin.paint(mesh,neighbors.stream().map(n->new RoadRailJoin.Neighbor(n,higherPriority.contains(n))).toList());
    // Preserve the main road's dividers. Only subordinate branch markings are suppressed.
    List<Mesh> dividerCuts = new ArrayList<>(higherPriority);
    var crossings = RoadJunction.intersections(mesh, neighbors);
    dividerCuts.addAll(crossings);
    Grid dividers = new Grid(dividerCuts);
    // Host dividers remain authoritative at an auxiliary merge. Boundary paint
    // has its own union clipping; a linked ramp alone cannot erase live lane dashes.
    Grid defaultDividers=new Grid(dividerCuts);
    for(var neighbor:neighbors)if(LaneMerge.linkedTo(neighbor,mesh)||LaneMerge.linkedTo(mesh,neighbor)){
      for(int i=1;i<neighbor.samples().size();i++){
        var a=neighbor.samples().get(i-1);var b=neighbor.samples().get(i);
        double da=a.center().y()-RoadQueries.horizontal(mesh,a.center()).sample().center().y();
        double db=b.center().y()-RoadQueries.horizontal(mesh,b.center()).sample().center().y();
        if(Math.abs(da)<.06&&Math.abs(db)<.06)continue;
        for(var strip:LaneDeck.strips(neighbor,a,b)){
          defaultDividers.add(List.of(strip.al(),strip.ar(),strip.br()));defaultDividers.add(List.of(strip.al(),strip.br(),strip.bl()));
        }
      }
    }

    for (var cut : RoadJunction.terminalCuts(mesh)){dividers.add(cut);defaultDividers.add(cut);}
    var approaches = RoadSignals.approaches(mesh, neighbors);
    for (var cut : RoadSignals.paintCuts(approaches)){dividers.add(cut);defaultDividers.add(cut);}
    List<Face> pavement = new ArrayList<>(), markings = new ArrayList<>();
    var samples = markingSamples(mesh);
    var junctionZones = RoadJunction.dividerZones(mesh, neighbors);
    var edgeZones = RoadJunction.edgeZones(mesh, neighbors);
    double thickness = mesh.settings().thickness();
    for (int i = 1; i < samples.size(); i++) {
      Sample a = samples.get(i - 1), b = samples.get(i);
      V l = a.at(a.halfWidth(), 0),
          r = a.at(-a.halfWidth(), 0),
          ll = b.at(b.halfWidth(), 0),
          rr = b.at(-b.halfWidth(), 0);
      if (mesh.settings().style() == Style.UNMARKED) continue;
      for (int side : new int[] {-1, 1}) {
        var edgeLayout = RoadProfile.layout(mesh, a);
        boolean curb =
            RoadProfile.modern(mesh.settings().style())
                && edgeLayout.curbWidth() > 0
                && (edgeLayout.catalog().twoWay() || edgeLayout.outside() == side);
        double edgeA=edgeOffset(mesh,a,side),edgeB=edgeOffset(mesh,b,side);
        if (!curb && !LaneDeck.outerOpening(mesh,(a.distance()+b.distance())/2,side) && !overrideLine(markings,mesh,a,b,"edge:"+side,mesh.settings().options().lanePoints().link()==null?side*(a.halfWidth()-.2):edgeA,mesh.settings().options().lanePoints().link()==null?side*(b.halfWidth()-.2):edgeB,joined)) boundaryStripe(markings,a,b,side,edgeJoin,edgeZones,edgeA,edgeB);
      }
      Style style = mesh.settings().style();
      if (RoadProfile.modern(style)) {
        var la = RoadProfile.layout(mesh, a);
        var lb = RoadProfile.layout(mesh, b);
        boolean dash = painted(mesh,(a.distance()+b.distance())/2);
        // Classify the interval at its midpoint, not at the reference start. Reversing
        // construction direction must not change yellow/white treatment of the same tile.
        var middle=RoadProfile.layout(mesh,RoadStructures.sample(mesh,(a.distance()+b.distance())/2));
        var median = middle.catalog().median();
        if (median == RoadProfile.Median.DOUBLE_YELLOW && middle.median() < .12) {
          if(!overrideLine(markings,mesh,a,b,"center:-1",la.medianCenter()-(.14+la.median()/2),lb.medianCenter()-(.14+lb.median()/2),dividers))stripe(
              markings,
              a,
              b,
              la.medianCenter()-(.14 + la.median() / 2),
              lb.medianCenter()-(.14 + lb.median() / 2),
              .1,
              true,
              dividers);
          if(!overrideLine(markings,mesh,a,b,"center:1",la.medianCenter()+.14+la.median()/2,lb.medianCenter()+.14+lb.median()/2,dividers))stripe(markings, a, b, la.medianCenter()+.14 + la.median() / 2, lb.medianCenter()+.14 + lb.median() / 2, .1, true, dividers);
        } else if (median == RoadProfile.Median.DASHED_YELLOW) {
          if(!overrideLine(markings,mesh,a,b,"center:0",la.medianCenter(),lb.medianCenter(),dividers)&&dash)stripe(markings, a, b, la.medianCenter(), lb.medianCenter(), .12, true, dividers);
        }
        else if (middle.median() >= .12
            || median == RoadProfile.Median.RAIL
            || median == RoadProfile.Median.GREEN)
          for (int side : new int[] {-1, 1})
            if(!overrideLine(markings,mesh,a,b,"median:"+side,la.medianCenter()+side*(la.median()/2+.12),lb.medianCenter()+side*(lb.median()/2+.12),dividers))stripe(
                markings,
                a,
                b,
                la.medianCenter()+side * (la.median() / 2 + .12),
                lb.medianCenter()+side * (lb.median() / 2 + .12),
                .12,
                false,
                dividers);
        for (int side : la.outsideSides()) {
          if ((la.shoulderWidth() > 0 || la.cycleWidth() > 0)&&!overrideLine(markings,mesh,a,b,"shoulder:"+side,la.outer(side),lb.outer(side),joined))
            shoulderStripe(markings, a, b, side, la.outer(side), lb.outer(side),
                joined,
                la.catalog().type() == RoadProfile.Type.HIGHWAY ? List.of() : edgeZones);
          if (la.cycleWidth() > 0 && RoadTransitions.greenCycle(mesh,a)) {
            // Subtle bicycle-lane surfacing, clipped at junctions just like the white boundary.
            double oa = la.outer(side) + side * (la.cycleWidth()+RoadStreetscape.separatorWidth(mesh.settings().options())) / 2,
                ob = lb.outer(side) + side * (lb.cycleWidth()+RoadStreetscape.separatorWidth(mesh.settings().options())) / 2;
            List<Face> color = new ArrayList<>();
            stripe(
                color,
                a,
                b,
                oa,
                ob,
                Math.min(la.cycleWidth(), lb.cycleWidth()) - RoadStreetscape.separatorWidth(mesh.settings().options()) - .16,
                false,
                joined);
            for (Face face : color) markings.add(new Face(face.points(), 0x536F61));
          }
        }
        if(mesh.settings().options().streetscape().parking()&&!RoadStreetscape.raised(mesh,a))for(int side:la.outsideSides()){
          if(la.cycleWidth()<1||lb.cycleWidth()<1)continue;

          double phase=mesh.settings().options().ends().paintPhase();
          double station=Math.ceil((a.distance()+phase)/RoadStreetscape.parkingLength())*RoadStreetscape.parkingLength()-phase;
          if(station>=a.distance()-1e-7&&station<b.distance()-1e-7){
            Sample p=RoadStructures.sample(mesh,station);var parkingLayout=RoadProfile.layout(mesh,p);
            V x=p.at(parkingLayout.outer(side),-.012),y=p.at(side*(p.halfWidth()-(parkingLayout.curbWidth()>0?RoadProfile.curbExtent(parkingLayout,p,side):.3)),-.012);
            var faces=new ArrayList<Face>();JunctionPaint.line(faces,x,y,.12,0xEEEEDE);
            for(var face:faces){List<List<V>> remaining=List.of(face.points());for(var cut:joined.candidates(face.points())){var next=new ArrayList<List<V>>();for(var poly:remaining)next.addAll(subtract(poly,cut.points()));remaining=next;}for(var poly:remaining)markings.add(new Face(poly,face.color()));}
          }
        }
        // Pavement ownership clips duplicate fork markings. A blanket longitudinal exclusion
        // erased both owners' two-lane dividers throughout an otherwise valid shared throat.
        if ((!style.ramp() || a.halfWidth() + b.halfWidth() >= mesh.settings().width() * .85)
            && junctionZones.stream().noneMatch(zone->zone.contains((a.distance()+b.distance())/2))) {
          var aa = la.dividers();
          var bb = lb.dividers();
          for (int j = 0; j < Math.min(aa.size(),bb.size()); j++)
            if ((aa.get(j)>la.motorMin()+.12&&aa.get(j)<la.motorMax()-.12||bb.get(j)>lb.motorMin()+.12&&bb.get(j)<lb.motorMax()-.12) && !overrideLine(markings,mesh,a,b,"divider:"+j,aa.get(j),bb.get(j),dividers) && (dash || closedSlotBoundary(mesh,(a.distance()+b.distance())/2,(aa.get(j)+bb.get(j))/2) || RoadSignals.solid(approaches, (a.distance() + b.distance()) / 2,
                (aa.get(j) + bb.get(j)-la.medianCenter()-lb.medianCenter()) / 2)))
              stripe(markings, a, b, aa.get(j), bb.get(j), .12, false, defaultDividers, .35);
        }
        continue;
      }
      if (style == Style.TWO_LANE || style == Style.FOUR_LANE) {
        stripe(markings, a, b, -.14, -.14, .1, true, dividers);
        stripe(markings, a, b, .14, .14, .1, true, dividers);
      }
      if (painted(mesh,(a.distance()+b.distance())/2)
          && junctionZones.stream()
              .noneMatch(zone -> zone.contains((a.distance() + b.distance()) / 2))) {
        if (style == Style.FOUR_LANE) {
          stripe(markings, a, b, a.halfWidth() / 2, b.halfWidth() / 2, .12, false, dividers);
          stripe(markings, a, b, -a.halfWidth() / 2, -b.halfWidth() / 2, .12, false, dividers);
        } else if (style == Style.ONE_WAY
            || (style == Style.RAMP_TWO
                && a.halfWidth() + b.halfWidth() >= mesh.settings().width() * .85))
          stripe(markings, a, b, 0, 0, .12, false, dividers);
      }
    }
    // Deck and stripes use independent sampling. Long flat deck spans need no extra triangles.
    var deck = RoadRenderMesh.pavement(mesh).samples();
    for (int i = 1; i < deck.size(); i++) {
      Sample a = deck.get(i - 1), b = deck.get(i);
      for(var strip:LaneDeck.strips(mesh,a,b)) {
        V l=strip.al(),r=strip.ar(),ll=strip.bl(),rr=strip.br();
        for(var triangle:List.of(List.of(l,r,rr),List.of(l,rr,ll)))
          if(area(triangle)>1e-9)for(var poly:visible(triangle,owners,.025)){
            pavement.add(new Face(poly,0xDCDCDC));
            pavement.add(new Face(poly.stream().map(v->v.add(new V(0,-thickness,0))).toList(),0xB9B9AD,false,Texture.CONCRETE));
          }
        if(strip.highWall())wall(pavement,ll,l,thickness,joined);
        if(strip.lowWall())wall(pavement,r,rr,thickness,joined);
      }
    }
    if(!mesh.closed())for(boolean start:new boolean[]{true,false}){
      Sample a=start?mesh.first():mesh.last();
      for(var span:LaneDeck.spans(mesh,a))if(span.high()-span.low()>1e-7)
        wall(pavement,a.at(start?span.high():span.low(),0),a.at(start?span.low():span.high(),0),thickness,joined);
    }

    for(var cap:LaneDeck.caps(mesh))wall(pavement,cap.a(),cap.b(),thickness,joined);

    List<Mesh> union = new ArrayList<>(neighbors);
    union.add(mesh);
    Grid paving = new Grid(union);
    for (var paint : RoadJunction.markings(mesh, higherPriority, neighbors, true)) {
      // Free lane-connector roads need a single owner in a coplanar throat to avoid
      // duplicate connector paint. Automatic/legacy interchange ramps are different:
      // their fork hatching is generated symmetrically from both branches and must not
      // change when UUID/construction ownership flips. Only real lane links use the
      // ownership clip; style.ramp() by itself is not a lane-connector identity.
      if(mesh.settings().options().lanePoints().link()!=null)
        for(var poly:visible(paint.points(),owners,.025))
          paintOnRoad(markings,new RoadJunction.Paint(poly,paint.color()),paving);
      else paintOnRoad(markings,paint,paving);
    }
    for (var paint : RoadJunction.arrows(mesh, neighbors, crossings)) {
      if(mesh.settings().options().lanePoints().link()==null)paintOnRoad(markings,paint,paving);
      else for(var poly:visible(paint.points(),owners,.025))paintOnRoad(markings,new RoadJunction.Paint(poly,paint.color()),paving);
    }

    for(var warning:LaneClosureWarnings.paint(mesh,neighbors))paintOnRoad(markings,warning,paving);

    if(LaneDeck.hasOpenings(mesh)) {
      Grid holes=new Grid(List.of());var points=mesh.samples();
      for(int i=1;i<points.size();i++)for(var q:LaneDeck.holeQuads(mesh,points.get(i-1),points.get(i))) {
        holes.add(List.of(q.get(0),q.get(1),q.get(2)));holes.add(List.of(q.get(0),q.get(2),q.get(3)));
      }
      var clipped=new ArrayList<Face>();
      for(var face:markings)for(var poly:visible(face.points(),holes,.03))clipped.add(new Face(poly,face.color(),face.emissive(),face.texture()));
      markings=clipped;
    }
    return new Geometry(List.copyOf(pavement), List.copyOf(markings));
  }

  /** Clip generated junction paint to the paved union, with one owner for every overlap. */
  private static void paintOnRoad(List<Face> output, RoadJunction.Paint paint, Grid paving) {
    List<Face> pieces = new ArrayList<>();
    Cut plane = null;
    List<List<V>> remaining = new ArrayList<>();
    remaining.add(ccw(paint.points()));
    for (Cut cut : paving.candidates(paint.points())) {
      if (paint.points().stream().anyMatch(p -> Math.abs(p.y() - cut.height(p)) > .35)) continue;
      if (plane == null) plane = cut;
      List<List<V>> next = new ArrayList<>();
      for (var subject : remaining) {
        var intersection = subject;
        for (int i = 0; i < cut.points.size(); i++)
          intersection =
              halfPlane(
                  intersection,
                  cut.points.get(i),
                  cut.points.get((i + 1) % cut.points.size()),
                  true);
        if (area(intersection) > 1e-9)
          pieces.add(
              new Face(
                  intersection.stream().map(p -> new V(p.x(), cut.height(p), p.z())).toList(),
                  paint.color()));
        next.addAll(subtract(subject, cut.points));
      }
      remaining = next;
      if (remaining.isEmpty()) break;
    }
    if (remaining.isEmpty() && plane != null && !pieces.isEmpty()) {
      Cut flat = plane;
      boolean coplanar =
          pieces.stream()
              .flatMap(f -> f.points().stream())
              .allMatch(p -> Math.abs(p.y() - flat.height(p)) < 1e-7);
      if (coplanar) {
        output.add(
            new Face(
                ccw(paint.points()).stream().map(p -> new V(p.x(), flat.height(p), p.z())).toList(),
                paint.color()));
        return;
      }
    }
    output.addAll(pieces);
  }

  private static void stripe(
      List<Face> output,
      Sample a,
      Sample b,
      double oa,
      double ob,
      double width,
      boolean yellow,
      Grid joined) {stripe(output,a,b,oa,ob,width,yellow,joined,.06);}
  private static void stripe(List<Face> output,Sample a,Sample b,double oa,double ob,double width,boolean yellow,Grid joined,double tolerance) {
    List<V> quad =
        List.of(
            a.at(oa - width / 2, 0),
            a.at(oa + width / 2, 0),
            b.at(ob + width / 2, 0),
            b.at(ob - width / 2, 0));
    for (List<V> triangle :
        List.of(
            List.of(quad.get(0), quad.get(1), quad.get(2)),
            List.of(quad.get(0), quad.get(2), quad.get(3))))
      for (List<V> part : visible(triangle, joined, tolerance))
        output.add(new Face(part, yellow ? 0xFAC136 : 0xEDEEE2));
  }

  private static void shoulderStripe(
      List<Face> out,
      Sample a,
      Sample b,
      int side,
      double oa,
      double ob,
      Grid joined,
      List<RoadJunction.EdgeZone> zones) {
    TreeSet<Double> cuts = new TreeSet<>(List.of(0.0, 1.0));
    double span = b.distance() - a.distance();
    for (var zone : zones) {
      if (zone.side() != side) continue;
      for (double d : new double[] {zone.from(), zone.to()})
        if (d > a.distance() && d < b.distance()) cuts.add((d - a.distance()) / span);
    }
    double previous = 0;
    for (double t : cuts) {
      double mid = a.distance() + (previous + t) * span / 2;
      if (t > previous && zones.stream().noneMatch(z -> z.contains(mid, side))) {
        Sample p = blend(a, b, previous), q = blend(a, b, t);
        stripe(out, p, q, oa + (ob - oa) * previous, oa + (ob - oa) * t, .15, false, joined);
      }
      previous = t;
    }
  }

  private static Sample blend(Sample a, Sample b, double t) {
    return new Sample(
        a.center().add(b.center().sub(a.center()).mul(t)),
        a.left().add(b.left().sub(a.left()).mul(t)).horizontalUnit(),
        a.distance() + (b.distance() - a.distance()) * t,
        a.halfWidth() + (b.halfWidth() - a.halfWidth()) * t);
  }

  private static List<List<V>> visible(List<V> polygon, Grid grid, double tolerance) {
    List<List<V>> pieces = new ArrayList<>();
    pieces.add(ccw(polygon));
    for (Cut cut : grid.candidates(polygon)) {
      if (polygon.stream().anyMatch(p -> Math.abs(p.y() - cut.height(p)) > tolerance)) continue;
      List<List<V>> next = new ArrayList<>();
      for (var p : pieces) next.addAll(subtract(p, cut.points));
      pieces = next;
      if (pieces.isEmpty()) break;
    }
    return pieces;
  }

  /** Disjoint convex pieces of subject minus clip; coordinates are relative in every predicate. */
  public static List<List<V>> subtract(List<V> subject, List<V> clip) {
    List<List<V>> result = new ArrayList<>();
    List<V> remaining = ccw(subject), boundary = ccw(clip);
    for (int i = 0; i < boundary.size() && !remaining.isEmpty(); i++) {
      V a = boundary.get(i), b = boundary.get((i + 1) % boundary.size());
      if(a.sub(b).horizontalLength()<1e-9)continue; // Triangle prisms may repeat the tip vertex.
      List<V> outside = halfPlane(remaining, a, b, false);
      if (area(outside) > 1e-10) result.add(outside);
      remaining = halfPlane(remaining, a, b, true);
    }
    return result;
  }

  private static List<V> halfPlane(List<V> input, V a, V b, boolean inside) {
    if (input.isEmpty()) return input;
    List<V> out = new ArrayList<>();
    V last = input.get(input.size() - 1);
    double prior = cross(a, b, last) * (inside ? 1 : -1);
    boolean was = prior >= 0;
    for (V p : input) {
      double value = cross(a, b, p) * (inside ? 1 : -1);
      boolean now = value >= 0;
      if (was != now) {
        double t = prior / (prior - value);
        out.add(last.add(p.sub(last).mul(t)));
      }
      if (now) out.add(p);
      last = p;
      prior = value;
      was = now;
    }
    List<V> clean = new ArrayList<>();
    for (V p : out)
      if (clean.isEmpty() || p.distance(clean.get(clean.size() - 1)) > 1e-9) clean.add(p);
    if (clean.size() > 1 && clean.get(0).distance(clean.get(clean.size() - 1)) < 1e-9)
      clean.remove(clean.size() - 1);
    return clean;
  }

  private static void wall(List<Face> output, V a, V b, double thickness, Grid neighbors) {
    var intervals = exposed(a, b, neighbors);
    for (double[] range : intervals)
      if (range[1] > range[0] + 1e-9) {
        V p = a.add(b.sub(a).mul(range[0])), q = a.add(b.sub(a).mul(range[1]));
        output.add(
            new Face(
                List.of(p, q, q.add(new V(0, -thickness, 0)), p.add(new V(0, -thickness, 0))),
                0xB9B9AD,false,Texture.CONCRETE));
      }
  }

  private static List<double[]> exposed(V a, V b, Grid neighbors) {
    List<double[]> intervals = new ArrayList<>();
    intervals.add(new double[] {0, 1});
    for (Cut cut : neighbors.candidates(List.of(a, b))) {
      if (Math.abs(a.y() - cut.height(a)) > .025 || Math.abs(b.y() - cut.height(b)) > .025)
        continue;
      double lo = 0, hi = 1;
      boolean empty = false;
      for (int i = 0; i < cut.points.size(); i++) {
        V p = cut.points.get(i), q = cut.points.get((i + 1) % cut.points.size());
        double va = cross(p, q, a), vb = cross(p, q, b);
        // Adjacent triangle samples reconstruct their shared edge independently.
        // Roundoff away from the origin must not turn a coincident edge into an
        // exposed internal wall (visible as radial seams under depth bias).
        double epsilon = 1e-7 * Math.max(1, q.sub(p).horizontalLength());
        if (Math.abs(va) < epsilon) va = 0;
        if (Math.abs(vb) < epsilon) vb = 0;
        double d = vb - va;
        if (Math.abs(d) < 1e-12) {
          if (va < -1e-9) {
            empty = true;
            break;
          }
        } else if (d > 0) lo = Math.max(lo, -va / d);
        else hi = Math.min(hi, -va / d);
      }
      if (empty || hi <= lo + 1e-9) continue;
      List<double[]> next = new ArrayList<>();
      for (double[] range : intervals) {
        if (lo > range[0] + 1e-9) next.add(new double[] {range[0], Math.min(lo, range[1])});
        if (hi < range[1] - 1e-9) next.add(new double[] {Math.max(hi, range[0]), range[1]});
      }
      intervals = next;
    }
    return intervals;
  }

  /** A divider bordering a physically closed slot is no longer a divider between
   * two through lanes. Keep that short boundary continuous; do not turn the whole
   * host road solid and do not override an explicit line-editor selection. */
  public static boolean closedSlotBoundary(Mesh mesh,double station,double lateral){
    if(!LaneDeck.hasOpenings(mesh))return false;var sample=RoadStructures.sample(mesh,station);
    var raw=LaneSections.reference(mesh);
    for(int slot:LaneAdditions.slots(raw,station))if(mesh.settings().options().lanePoints().cuts().stream().anyMatch(c->c.temporary()&&c.lane()==slot&&c.removed(station)>.999)||LaneClosureWarnings.covers(mesh,station,slot)){
      var lane=LanePoints.lane(raw,station,slot);double center=lane.position().sub(sample.center()).dot(sample.left());
      if(Math.abs(Math.abs(lateral-center)-lane.width()/2)<.2)return true;
    }
    return LaneDeck.present(mesh,sample,lateral-.14,0)!=LaneDeck.present(mesh,sample,lateral+.14,0);
  }
  /** At lane-point mouths the edge paint meets the selected lane boundary, not the independent road shoulder rim. */
  public static double edgeOffset(Mesh mesh,Sample sample,int side){
    double normal=side*Math.max(.08,sample.halfWidth()-.3);
    var layout=RoadProfile.layout(mesh,sample);
    var link=mesh.settings().options().lanePoints().link();
    if(link==null){
      // Ordinary roads without a separate verge mark their actual driving edge,
      // including asymmetric lane cuts. The old fixed .3 inset widened only the
      // outside painted lane by absorbing the remaining pavement margin.
      if(layout.catalog().type()==RoadProfile.Type.ORDINARY&&layout.cycleWidth()<.01&&layout.curbWidth()<.01)return layout.outer(side);
      return normal;
    }
    // A connector has a persisted four-block motor band plus separate rail shoulders.
    // Mark that band, so the outer lane cannot visually absorb both shoulders.
    if(mesh.settings().style().connectorRamp())return layout.outer(side);
    if(layout.catalog().type()!=RoadProfile.Type.ORDINARY||layout.cycleWidth()>.01||layout.curbWidth()>.01)return normal;
    double span=Math.min(mesh.length()/2,Math.max(16,link.options().transition()));
    double distance=Math.min(sample.distance(),mesh.length()-sample.distance());
    double weight=1-Settings.smooth(Math.min(1,distance/Math.max(.01,span)));
    return normal+(layout.outer(side)-normal)*weight;
  }
  private static void boundaryStripe(
      List<Face> out,
      Sample a,
      Sample b,
      int side,
      RoadRailJoin neighbors,
      List<RoadJunction.EdgeZone> zones,double oa,double ob) {
    V a0 = a.at(oa - .06, 0),
        a1 = a.at(oa + .06, 0),
        b0 = b.at(ob - .06, 0),
        b1 = b.at(ob + .06, 0);
    V first=a.at(oa,0),last=b.at(ob,0),delta=last.sub(first);
    double inset=(Math.abs(side*a.halfWidth()-oa)+Math.abs(side*b.halfWidth()-ob))/2;
    List<double[]> ranges=new ArrayList<>();double length=delta.dot(delta);
    if(length<1e-12)return;
    V outside=first.add(last).mul(.5).add(a.left().add(b.left()).horizontalUnit().mul(side*.4));
    for(var span:neighbors.exposed(first,last,outside,inset))ranges.add(new double[]{span.a().sub(first).dot(delta)/length,span.b().sub(first).dot(delta)/length});
    for (var zone : zones) {
      if (zone.side() != side) continue;
      double lo = Math.max(0, (zone.from() - a.distance()) / (b.distance() - a.distance())),
          hi = Math.min(1, (zone.to() - a.distance()) / (b.distance() - a.distance()));
      if (hi <= lo) continue;
      List<double[]> next = new ArrayList<>();
      for (double[] r : ranges) {
        if (lo > r[0] + 1e-9) next.add(new double[] {r[0], Math.min(lo, r[1])});
        if (hi < r[1] - 1e-9) next.add(new double[] {Math.max(hi, r[0]), r[1]});
      }
      ranges = next;
    }
    for (var r : ranges) {
      if (r[1] <= r[0] + 1e-9) continue;
      var polygon=List.of(a0.add(b0.sub(a0).mul(r[0])),a1.add(b1.sub(a1).mul(r[0])),
          a1.add(b1.sub(a1).mul(r[1])),a0.add(b0.sub(a0).mul(r[1])));
      out.add(new Face(polygon,0xEDEEE2));
    }
  }

  private static double cross(V a, V b, V p) {
    return (b.x() - a.x()) * (p.z() - a.z()) - (b.z() - a.z()) * (p.x() - a.x());
  }

  private static double signedArea(List<V> p) {
    if (p.size() < 3) return 0;
    double a = 0;
    for (int i = 1; i < p.size() - 1; i++) a += cross(p.get(0), p.get(i), p.get(i + 1));
    return a / 2;
  }

  public static double area(List<V> p) {
    return Math.abs(signedArea(p));
  }

  private static List<V> ccw(List<V> p) {
    if (signedArea(p) >= 0) return p;
    List<V> r = new ArrayList<>(p);
    Collections.reverse(r);
    return r;
  }

  private RoadSurface() {}
}
