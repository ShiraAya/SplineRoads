package com.sora.splineroads.core;

import com.sora.splineroads.core.RoadGeometry.*;
import com.sora.splineroads.core.RoadProfile.*;
import java.util.*;

/** Deterministic complete interchange plan; preview and server use exactly the same inputs. */
public final class InterchangePlanner {
  public enum Preset {
    CLOVERLEAF("苜蓿叶 · 四向", false, 2),
    TURBINE("涡轮 · 四向", false, 5),
    STACK("堆栈 · 四向", false, 3),
    HYBRID("苜蓿叶＋定向 · 四向", false, 3),
    TRUMPET("喇叭 · 三向", true, 3),
    DIRECTIONAL_T("定向 T · 三向", true, 3),
    Y("定向 Y · 三向", true, 3),
    DIAMOND("菱形 · 四向", false, 1),
    PARCLO("部分苜蓿叶 · 四向", false, 2),
    DOUBLE_TRUMPET("双喇叭组合 · 四向", false, 3),
    ROUNDABOUT("三层环形 · 四向", false, 2),
    SPUI("单点式菱形 · 四向", false, 1),
    DIRECTIONAL_FIVE("全定向堆栈 · 五向", false, 3),
    DIRECTIONAL_SIX("全定向堆栈 · 六向", false, 3);
    public final String label;
    public final boolean three;

    /** Retain enum IDs only for loading old worlds; retired layouts cannot be generated. */
    public boolean supported() {
      return this != PARCLO && this != DOUBLE_TRUMPET;
    }

    public boolean multi(){return this==DIRECTIONAL_FIVE||this==DIRECTIONAL_SIX;}

    public final int heightSlots;

    Preset(String label, boolean three, int heightSlots) {
      this.label = label;
      this.three = three;
      this.heightSlots = heightSlots;
    }
  }

  /** Only common terminating layouts are offered for three real anchors. */
  public static List<Preset> presets(int anchors) {
    if(anchors==5)return List.of(Preset.DIRECTIONAL_FIVE);
    if(anchors==6)return List.of(Preset.DIRECTIONAL_SIX);
    if (anchors != 3 && anchors != 4) throw new IllegalArgumentException("请选择三至六个端点");
    return Arrays.stream(Preset.values())
        .filter(p -> p.supported() && p!=Preset.DIRECTIONAL_FIVE && p!=Preset.DIRECTIONAL_SIX && p.three == (anchors == 3))
        .toList();
  }

  public static Node[] expand(Node[] anchors, Options options) {
    if (anchors.length == 4) return anchors.clone(); // editable 0.8 three-way descriptors
    if (anchors.length != 3) throw new IllegalArgumentException("请选择 A/B 与 C，或 A/B 与 C/D");
    if (!options.preset.three) throw new IllegalArgumentException("三个端点只能选择三向立交");
    V a = anchors[0].position(), b = anchors[1].position(), c = anchors[2].position();
    V ab = b.sub(a).horizontalUnit();
    V foot = a.add(ab.mul(c.sub(a).dot(ab)));
    V d = new V(2 * foot.x() - c.x(), c.y(), 2 * foot.z() - c.z());
    return new Node[] {anchors[0], anchors[1], anchors[2], new Node(d, anchors[2].yaw(), 0)};
  }

  public record Options(
      Preset preset,
      boolean leftTraffic,
      int lanes,
      double transition,
      double radius,
      double clearance,
      int upper,
      double rampWidth,
      boolean adjustEndpoints,
      boolean allowShrink,
      double maxLowering) {
    public Options(Preset preset, boolean leftTraffic, int lanes, double transition,
        double radius, double clearance, int upper, double rampWidth,
        boolean adjustEndpoints, boolean allowShrink) {
      this(preset, leftTraffic, lanes, transition, radius, clearance, upper, rampWidth,
          adjustEndpoints, allowShrink, 8);
    }
    public Options(
        Preset preset,
        boolean leftTraffic,
        int lanes,
        double transition,
        double radius,
        double clearance,
        int upper,
        double rampWidth,
        boolean adjustEndpoints) {
      this(
          preset,
          leftTraffic,
          lanes,
          transition,
          radius,
          clearance,
          upper,
          rampWidth,
          adjustEndpoints,
          false);
    }

    public Options(
        Preset preset,
        boolean leftTraffic,
        int lanes,
        double transition,
        double radius,
        double clearance,
        int upper) {
      this(preset, leftTraffic, lanes, transition, radius, clearance, upper, 0, false);
    }

    public double width() {
      return rampWidth == 0 ? (lanes == 1 ? 5 : 9) : rampWidth;
    }

    public Options adjust(boolean value) {
      return new Options(
          preset,
          leftTraffic,
          lanes,
          transition,
          radius,
          clearance,
          upper,
          rampWidth,
          value,
          allowShrink,
          maxLowering);
    }

    public Options shrink(boolean value) {
      return new Options(
          preset,
          leftTraffic,
          lanes,
          transition,
          radius,
          clearance,
          upper,
          rampWidth,
          adjustEndpoints || value,
          value,
          maxLowering);
    }

    public Options lowering(double value) {
      return new Options(preset, leftTraffic, lanes, transition, radius, clearance, upper,
          rampWidth, adjustEndpoints, allowShrink, value);
    }

    public static final Options DEFAULT = new Options(Preset.CLOVERLEAF, false, 1, 96, 20, 5, 2);

    public Options {
      if (preset == null
          || lanes < 1
          || lanes > 2
          || !RoadGeometry.finite(transition, radius, clearance, rampWidth, maxLowering)
          || maxLowering < 0 || maxLowering > 64
          || rampWidth != 0 && (rampWidth < lanes * 4 || rampWidth > 32)
          || transition < 24
          || transition > 192
          || radius < 12
          || radius > 64
          || clearance < 4
          || clearance > 12
          || upper < 1
          || upper > 2)
        throw new IllegalArgumentException(
            "立交参数无效：匝道宽度每车道至少 4 格、总宽不超过 32；过渡 24–192（实际至少 80）、半径 12–64、净高 4–12 格");
    }
  }

  public record Leg(String name, int from, int to, Mesh mesh) {}

  public record Plan(
      List<Leg> legs,
      int movements,
      double minRadius,
      double highest,
      V center,
      List<Node> anchors) {
    public Plan(List<Leg> legs, int movements, double minRadius, double highest, V center) {
      this(legs, movements, minRadius, highest, center, List.of());
    }
  }

  private record Knot(V p, V direction) {}

  private static final V[] ARMS = {
    new V(-1, 0, 0), new V(0, 0, -1), new V(1, 0, 0), new V(0, 0, 1)
  };
  private final Node[] nodes;
  private final Settings[] main;
  private final Options options;
  private final V center, u, v;
  private final double[] lengths = new double[4];
  private final int traffic;
  private final double base, step, loopRadius;
  private final double[] heights;
  private final List<Leg> roads = new ArrayList<>();
  private final List<Route> routes = new ArrayList<>();
  private double directCurve = .40;
  private double cornerScale = 1;
  private static final class CornerRadiusFailure extends IllegalArgumentException { CornerRadiusFailure(String message){super(message);} }
  private final boolean preciseClearance;
  private final java.util.function.UnaryOperator<V> conformance;

  private static final class Route {
    String name;
    int from, to, leadEnd, tailStart, priority, leadCut, tailCut;
    List<V> points;
    List<Double> widths;
    int plateauStart = -1, plateauEnd = -1;
    double layer;

    Route(String n, int f, int t, List<V> p, List<Double> w, int a, int b, int priority) {
      this.priority = priority;
      name = n;
      from = f;
      to = t;
      points = p;
      widths = w;
      leadEnd = leadCut = a;
      tailStart = tailCut = b;
    }
  }

  private InterchangePlanner(Node[] nodes, Settings a, Settings b, Options options,java.util.function.UnaryOperator<V> conformance) {
    this.preciseClearance=conformance!=null;this.conformance=conformance;
    if (!options.preset.supported()) throw new IllegalArgumentException("此立交预设已移除，请改选其他布局");
    this.nodes = nodes.clone();
    this.main = new Settings[] {a, b};
    this.options = options;
    for (Settings s : main) s.validate();
    for (Settings s : main)
      if (s.style().ramp() || !RoadProfile.catalog(s).twoWay())
        throw new IllegalArgumentException("预设立交的两条主路需要双向道路");
    V ab = nodes[1].position().sub(nodes[0].position()),
        cd = nodes[3].position().sub(nodes[2].position());
    if (ab.horizontalLength() < 100
        || cd.horizontalLength() < 70
        || ab.horizontalLength()
            > (options.preset == Preset.DOUBLE_TRUMPET ? 4096 : RoadLimits.MAX_ENDPOINT_DISTANCE)
        || cd.horizontalLength()
            > (options.preset == Preset.DOUBLE_TRUMPET || options.preset.three
                ? 4096
                : RoadLimits.MAX_ENDPOINT_DISTANCE))
      throw new IllegalArgumentException(
          options.preset == Preset.DOUBLE_TRUMPET
              ? "双喇叭两轴总长上限 4096 格"
              : "AB 总长需为 100–2048 格，四向 CD 总长 70–2048 格，三向支路不超过 2048 格");
    u = ab.horizontalUnit();
    v = cd.horizontalUnit();
    double det = cross(u, v);
    if (Math.abs(det) < .9) throw new IllegalArgumentException("预设立交需要两条约垂直的主路，夹角需为 65°–115°");
    V delta = nodes[2].position().sub(nodes[0].position());
    double along = cross(delta, v) / det;
    center = nodes[0].position().add(u.mul(along));
    lengths[0] = along;
    lengths[2] = ab.horizontalLength() - along;
    double cv = center.sub(nodes[2].position()).dot(v);
    lengths[1] = cv;
    lengths[3] = cd.horizontalLength() - cv;
    if (Math.min(lengths[0], lengths[2]) < 45
        || lengths[1] < 45
        || (!options.preset.three && lengths[3] < 45))
      throw new IllegalArgumentException("两条主路需要在端点之间相交，交点离各有效方向端点至少 45 格");
    if (options.preset.three) lengths[3] = Math.max(lengths[1], Math.min(lengths[0], lengths[2]));
    traffic = (options.leftTraffic ? -1 : 1) * (det < 0 ? -1 : 1);
    var levels = levels(nodes, a, b, options);
    heights = new double[] {levels.first(), levels.second()};
    base = Math.min(heights[0], heights[1]);
    step = (minimumDifference(a, b, options)) / options.preset.heightSlots;
    double gap = Math.abs(heights[0] - heights[1]);
    double sweep = Math.PI + Math.asin(Math.min(1, Math.abs(det)));
    loopRadius =
        Math.max(
            options.radius,
            options.preset == Preset.CLOVERLEAF
                ? (gap * 1.25 / RoadGrades.MAX_RAMP_GRADE + 42) / sweep + 2
                : gap * 2.5);
  }

  public static Plan plan(Node[] nodes, Settings a, Settings b, Options options) {
    return plan(nodes,a,b,options,null);
  }
  /** Curve conformance reserves the complete pavement boundary before assigning vertical plateaus. */
  public static Plan plan(Node[] nodes,Settings a,Settings b,Options options,java.util.function.UnaryOperator<V> conformance){
    if(nodes.length>=5)return MultiInterchange.plan(nodes,new Settings[]{a,b,b},options);
    if(options.preset().multi())throw new IllegalArgumentException("五、六向布局需要对应数量的端点");
    if (options.adjustEndpoints) return InterchangeFit.fit(nodes, a, b, options,conformance);
    IllegalArgumentException first = null;
    // Adapt the continuous curve to leaf crossings and the available descent distance.
    // Every candidate still runs the complete radius, grade and clearance validation.
    double[] curves = options.preset == Preset.CLOVERLEAF
        ? new double[] {.40, .52, .56, .60, .36, .32, .28, .24, .20, .16, .12, .64, .68, .72}
        : new double[] {.40, .36, .32, .28, .24, .20, .16, .52, .60};
    double[] cornerScales=conformance!=null&&options.preset==Preset.STACK?new double[]{1,1.25,1.5,2,3,4,6,8}:new double[]{1};
    for(double corner:cornerScales)for (double curve : curves) {
      var planner = new InterchangePlanner(expand(nodes, options), a, b, options,conformance);
      planner.directCurve = curve;
      planner.cornerScale = corner;
      try {
        var result = planner.build();
        // A fitted candidate is successful only after checking its mapped, physical geometry.
        // Otherwise endpoint fitting stops early and the caller retries the same invalid footprint.
        if(conformance!=null)CurvedRoadPlans.validate(result.legs,options);
        return new Plan(result.legs, result.movements, result.minRadius, result.highest,
            result.center, List.copyOf(Arrays.asList(nodes)));
      } catch (IllegalArgumentException error) {
        if (Boolean.getBoolean("sr.curveDebug")) System.err.println(options.preset+" curve="+curve+" "+error.getMessage());
        if (first == null) first = error;
        // Direct-curve tension changes right turns only. Do not repeat an identical
        // failing left turn nine times before trying a wider central bend.
        if(error instanceof CornerRadiusFailure)break;
      }
    }
    throw first;
  }

  private V world(V p) {
    return center.add(u.mul(p.x())).add(v.mul(p.z())).add(new V(0, p.y() - center.y(), 0));
  }

  private V local(V p) {
    V q = p.sub(center);
    double d = cross(u, v);
    return new V(cross(q, v) / d, p.y(), cross(u, q) / d);
  }

  private V direction(V d) {
    return u.mul(d.x()).add(v.mul(d.z())).horizontalUnit();
  }

  public record Levels(double first, double second, double difference, double required) {}

  public static Levels levels(Node[] nodes, Settings a, Settings b, Options options) {
    nodes = expand(nodes, options);
    for (Node node : nodes)
      if (!RoadGeometry.finite(
          node.position().x(), node.position().y(), node.position().z(), node.grade(), node.yaw()))
        throw new IllegalArgumentException("立交端点数值无效");
    double y1 = (nodes[0].position().y() + nodes[1].position().y()) / 2,
        y2 = (nodes[2].position().y() + nodes[3].position().y()) / 2;
    for (int i = 0; i < 4; i += 2)
      if (Math.abs(nodes[i].position().y() - nodes[i + 1].position().y()) > 1e-6)
        throw new IllegalArgumentException("自动立交要求 AB、CD 各自等高；请先调平主路端点");
    double required = minimumDifference(a, b, options), gap = Math.abs(y1 - y2);
    if (gap + 1e-6 < required) {
      String upper = y1 > y2 ? "AB" : "CD";
      throw new IllegalArgumentException(
          String.format(
              Locale.ROOT,
              "%s：当前高差 %.1f 格，最低 %.1f 格；请将 %s 再抬高 %.1f 格",
              options.preset.label,
              gap,
              required,
              upper,
              required - gap));
    }
    return new Levels(y1, y2, gap, required);
  }

  public static double minimumDifference(Settings a, Settings b, Options options) {
    int slots = options.preset == Preset.CLOVERLEAF ? 1 : options.preset.heightSlots;
    return Math.ceil(
        slots * (options.clearance + Math.max(1, Math.max(a.thickness(), b.thickness())) + .3)
            + (options.preset == Preset.SPUI ? 4 : 0));
  }

  private double height(int axis, double coordinate) {
    return heights[axis];
  }

  private Settings setting(int axis) {
    var s = main[axis];
    return s.options(
            s.options()
                .traffic(options.leftTraffic)
                .route(s.options().routing().fit(false))
                .ends(RoadTransitions.Ends.NONE))
        .structure(Structure.AUTO);
  }

  private Plan build() {
    for (int axis = 0; axis < 2; axis++) {
      double lo = -lengths[axis == 0 ? 0 : 1],
          hi = axis == 1 && options.preset.three ? -terminalStation() : lengths[axis == 0 ? 2 : 3];
      Settings settings = setting(axis);
      if (axis == 1 && options.preset.three) {
        settings = RoadTransitions.ends(settings, null, terminalSection());
        settings =
            settings.options(
                settings.options().ends(new RoadTransitions.Ends(null, terminalSection(), true)));
      }
      List<Sample> samples = new ArrayList<>();
      int count = (int) Math.ceil((hi - lo) / .5);
      for (int i = 0; i <= count; i++) {
        double x = lo + (hi - lo) * i / count;
        V p = world(axis == 0 ? new V(x, height(axis, x), 0) : new V(0, height(axis, x), x));
        V d = axis == 0 ? u : v;
        samples.add(
            new Sample(
                p,
                d.left(),
                0,
                (axis == 1 && options.preset.three
                    ? RoadTransitions.width(settings, x - lo, hi - lo) / 2
                    : main[axis].width() / 2 + padding(axis))));
      }
      roads.add(
          new Leg(
              "主路 " + (axis + 1),
              axis == 0 ? 0 : 1,
              axis == 0 ? 2 : 3,
              RoadRibbon.mesh(samples, settings)));
    }
    if (options.preset == Preset.ROUNDABOUT) return roundabout();
    if (servicePreset()) return service();
    if (options.preset == Preset.DOUBLE_TRUMPET) return doubleTrumpet();
    if (options.preset == Preset.CLOVERLEAF) addCollectors();
    int active = options.preset.three ? 3 : 4;
    for (int from = 0; from < active; from++)
      for (int to = 0; to < active; to++) {
        if (to == from || Math.abs(from - to) == 2) continue;
        boolean right = ((to - from + 4) % 4) == (traffic > 0 ? 3 : 1);
        boolean leaf =
            !right
                && (options.preset == Preset.CLOVERLEAF
                    || options.preset == Preset.HYBRID && from % 2 == 0
                    || options.preset == Preset.TRUMPET && from == 1);
        routes.add(route(from, to, right, leaf));
      }
    if(conformance!=null){
      // Fit elevations against the ACTUAL curved hosts and ramp lengths, not a deformed result
      // whose already-finalised vertical profile could become too steep or clip a road edge.
      for(int i=0;i<roads.size();i++){var leg=roads.get(i);for(Sample sample:leg.mesh().samples())checkBounds(sample.center());roads.set(i,new Leg(leg.name(),leg.from(),leg.to(),CurvedRoadPlans.map(leg.mesh(),conformance)));}
      for(Route route:routes){for(V p:route.points)checkBounds(p);route.points=route.points.stream().map(conformance).toList();}
      for(Route route:routes){double radius=RoadRibbon.minRadius(horizontal(route));
        if(radius+.1<options.radius){String message=String.format(Locale.ROOT,"%s 实际转弯半径 %.1f 格，要求 %.1f 格",route.name,radius,options.radius);
          if(options.preset==Preset.STACK&&route.priority==2)throw new CornerRadiusFailure(message);
          throw new IllegalArgumentException(message);}}
    }
    routes.sort(java.util.Comparator.comparing(r -> r.priority));
    // Keep a shared entrance/exit flat until neighboring branches have actually separated.
    for (Route r : routes) extendThroat(r);
    // One horizontal contact pass fixes the preset layers; no vertical retry/search loop.
    List<Mesh> flat = routes.stream().map(this::horizontal).toList();
    List<Mesh> mains = roads.stream().map(Leg::mesh).toList();
    int mainCount = mains.size();
    for (int i = 0; i < routes.size(); i++) {
      Route r = routes.get(i);
      int slot =
          r.priority < 2
              ? 1
              : switch (options.preset) {
                case TURBINE -> r.from + 1;
                case STACK -> r.from % 2 + 1;
                case HYBRID -> 2;
                case DOUBLE_TRUMPET -> r.name.startsWith("C") ? 1 : 2;
                case TRUMPET, DIRECTIONAL_T, Y -> r.from == 1 ? 2 : 1;
                default -> 1;
              };
      // The trumpet's loop uses the second intermediate deck.
      if ((options.preset == Preset.TRUMPET || options.preset == Preset.DOUBLE_TRUMPET)
          && r.priority == 1) slot = 2;
      r.layer =
          options.preset == Preset.STACK && r.priority == 2
              ? base
                  + Math.abs(heights[0] - heights[1])
                      * (heights[r.from % 2] < heights[r.to % 2] ? 1 : 2)
                      / 3
              : base + slot * step;
      List<Mesh> others = new ArrayList<>(mains);
      others.addAll(flat);
      for (int k = 0; k < others.size(); k++) {
        if (k == i + mainCount
            || r.priority < 2
                && k >= mainCount
                && !(r.priority == 1
                    && options.preset == Preset.TRUMPET
                    && routes.get(k - mainCount).priority >= 2)) continue;
        Mesh other = others.get(k);
        for (int j = 0; j < flat.get(i).samples().size(); j += 2) {
          Sample p = flat.get(i).samples().get(j);
          var q = overlap(other, p);
          if (q == null
              || shared(r, j, p, k < mainCount ? null : routes.get(k - mainCount), k, other, q))
            continue;
          if (j <= r.leadEnd || j >= r.tailStart) continue;
          r.plateauStart = r.plateauStart < 0 ? j : Math.min(r.plateauStart, j);
          r.plateauEnd = Math.max(r.plateauEnd, j);
        }
      }
    }
    List<Mesh> ramps = routes.stream().map(this::elevate).toList();
    for (var mesh : ramps) RoadRibbon.checkSelfIntersections(mesh, options.clearance);
    for (int i = 0; i < routes.size(); i++) {
      var hit = conflict(ramps.get(i), mains, ramps.subList(0, i), routes.get(i), i);
      if (hit != null)
        throw new IllegalArgumentException(
            routes.get(i).name
                + " "
                + hit.reason
                + "，请扩大范围或调整预设"
                + (Boolean.getBoolean("sr.debug")
                    ? " sample="
                        + hit.sample
                        + "/"
                        + routes.get(i).points.size()
                        + " holds="
                        + routes.get(i).leadEnd
                        + ","
                        + routes.get(i).tailStart
                        + " plate="
                        + routes.get(i).plateauStart
                        + ","
                        + routes.get(i).plateauEnd
                    : ""));
    }
    for (int i = 0; i < ramps.size(); i++) {
      var r = routes.get(i);
      addWithSharedPorts(r, ramps.get(i));
    }
    double min = ramps.stream().mapToDouble(RoadRibbon::minRadius).min().orElse(0),
        highest = roads.stream().mapToDouble(l -> l.mesh.max().y()).max().orElse(base);
    if(conformance==null)for(Leg road:roads)for(Sample p:road.mesh.samples())checkBounds(p.center());
    for (Leg road : roads) RoadGrades.validate(road.mesh());
    return new Plan(List.copyOf(roads), ramps.size(), min, highest, center);
  }

  private void checkBounds(V point){
    V q=local(point);double pad=Math.max(main[0].width(),main[1].width());
    if(q.x() < -lengths[0]-pad||q.x()>lengths[2]+pad||q.z() < -lengths[1]-pad||q.z()>lengths[3]+pad)
      throw new IllegalArgumentException("该预设超出选定范围，请减小环绕半径或扩大范围");
  }

  public double transitionLength() {
    return Math.max(80, Math.max(options.transition, options.lanes == 1 ? 80 : 108));
  }

  private double terminalStation() {
    double shortest = Math.min(lengths[0], Math.min(lengths[1], lengths[2]));
    return Math.min(
        lengths[1] - Math.max(transitionLength(), 100), shortest * .94 - transitionLength());
  }

  private RoadTransitions.Section terminalSection() {
    return new RoadTransitions.Section(
        options.lanes() == 1 ? Style.O2_RAIL : Style.O4_RAIL,
        options.width() * 2 + 1,
        false,
        false,
        false,
        main[1].options().outerRail());
  }

  /** One continuous auxiliary carriageway between each pair of adjacent loop mouths. */
  private void addCollectors() {
    for (int arm = 0; arm < 4; arm++) {
      int axis = arm % 2;
      double offset = auxiliaryOffset(axis);
      double station = Math.max(auxiliaryOffset(0), auxiliaryOffset(1)) + loopRadius;
      V flow = direction(ARMS[arm].mul(-1));
      V side = flow.left().mul(options.leftTraffic ? -1 : 1);
      V b = world(ARMS[arm].mul(station)).add(side.mul(offset));
      V c = b.add(flow.mul(station * 2));
      List<V> path = new ArrayList<>();
      List<Double> widths = new ArrayList<>();
      int count = (int) Math.ceil(station * 2 / .4);
      for (int i = 0; i <= count; i++) {
        path.add(b.add(c.sub(b).mul(i / (double) count)));
        widths.add(options.width());
      }
      List<Sample> samples = new ArrayList<>();
      for (int i = 0; i < path.size(); i++) {
        V p = path.get(i);
        V tangent =
            path.get(Math.min(i + 1, path.size() - 1))
                .sub(path.get(Math.max(0, i - 1)))
                .horizontalUnit();
        samples.add(
            new Sample(new V(p.x(), heights[axis], p.z()), tangent.left(), 0, widths.get(i) / 2));
      }
      roads.add(
          new Leg("连续集散道 " + arm, arm, (arm + 2) % 4, RoadRibbon.mesh(samples, rampSettings())));
    }
  }

  private final Set<String> emittedPorts = new HashSet<>();

  private void addWithSharedPorts(Route r, Mesh mesh) {
    boolean sharedStart =
        r.leadCut > 0
            && routes.stream()
                .anyMatch(
                    o ->
                        o != r
                            && o.from == r.from
                            && o.points.get(0).distance(r.points.get(0)) < .01
                            && o.points.get(o.leadCut).distance(r.points.get(r.leadCut)) < .01
                            && o.points.get(o.leadCut * 4 / 5).distance(r.points.get(r.leadCut * 4 / 5)) < .01);
    boolean sharedEnd =
        r.tailCut < mesh.samples().size() - 1
            && routes.stream()
                .anyMatch(
                    o ->
                        o != r
                            && o.to == r.to
                            && o.points
                                    .get(o.points.size() - 1)
                                    .distance(r.points.get(r.points.size() - 1))
                                < .01
                            && o.points.get(o.tailCut).distance(r.points.get(r.tailCut)) < .01
                            && o.points.get((o.tailCut * 4 + o.points.size() - 1) / 5)
                                .distance(r.points.get((r.tailCut * 4 + r.points.size() - 1) / 5)) < .01);
    if (sharedStart && emittedPorts.add("out:" + r.from))
      roads.add(
          new Leg(
              "共用汇出 " + r.from,
              r.from,
              -1,
              RoadRibbon.mesh(mesh.samples().subList(0, r.leadCut + 1), mesh.settings())));
    if (sharedEnd && emittedPorts.add("in:" + r.to))
      roads.add(
          new Leg(
              "共用汇入 " + r.to,
              -1,
              r.to,
              RoadRibbon.mesh(
                  mesh.samples().subList(r.tailCut, mesh.samples().size()), mesh.settings())));
    int first = sharedStart ? r.leadCut : 0,
        last = sharedEnd ? r.tailCut : mesh.samples().size() - 1;
    roads.add(
        new Leg(
            r.name,
            r.from,
            r.to,
            RoadRibbon.mesh(mesh.samples().subList(first, last + 1), mesh.settings())));
  }

  private boolean servicePreset() {
    return switch (options.preset) {
      case DIAMOND, PARCLO, ROUNDABOUT, SPUI -> true;
      default -> false;
    };
  }

  private Plan service() {
    boolean single = options.preset == Preset.SPUI;
    double width = options.width();
    double station = Math.max(options.radius * 2, main[0].width() / 2 + width + 26);
    if (options.preset == Preset.PARCLO) station = loopRadius * 1.5 + main[0].width() * 2;
    double corner = Math.max(14, width * 2);
    if (!single && station + main[1].width() > Math.min(lengths[1], lengths[3]) - 16)
      throw new IllegalArgumentException("菱形的两个匝道端部路口放不下，请扩大 C/D 范围");
    double singleStation = main[0].width() / 2 + width / 2 + 3;
    if (single) intersection(0, corner, singleStation + width / 2);
    else for (int side : new int[] {-1, 1}) intersection(side * station, corner, width / 2);
    for (int arm : new int[] {0, 2})
      for (boolean exit : new boolean[] {true, false}) {
        double orientation = arm == 0 ? 1 : -1, side = traffic * orientation * (exit ? 1 : -1);
        double lead = transitionLength(), source = lengths[arm] - 8;
        double outer = detachedOffset(0);
        V a = world(new V(-orientation * source, heights[0], side * attachment(0))),
            b = world(new V(-orientation * (source - lead), heights[0], side * outer));
        V forward = direction(new V(orientation, 0, 0));
        List<V> points = new ArrayList<>();
        List<Double> widths = new ArrayList<>();
        appendLead(0, points, widths, a, b, forward, lead, portWidth(0), width, false);
        int hold = points.size() - 1;
        double targetX = main[1].width() / 2 + corner - 1;
        V target =
            world(
                new V(-orientation * targetX, heights[1], side * (single ? singleStation : station)));
        V tangent = forward;
        if (source - lead < targetX + 24)
          throw new IllegalArgumentException("四条菱形匝道需要独立的减速段和爬坡段，请延长 A/B 范围");
        List<Knot> approach = new ArrayList<>();
        approach.add(new Knot(b, leadTangent(a, b, forward, lead, 0)));
        if (single)
          approach.add(
              new Knot(
                  world(new V(-orientation * (targetX + 75), heights[1], side * outer)), forward));
        approach.add(new Knot(target, tangent));
        appendCurve(points, widths, curve(approach), width);
        Mesh ramp =
            serviceRamp(
                points,
                widths,
                hold,
                heights[0],
                heights[1],
                single ? 85 : 12,
                !exit,
                (arm == 0 ? "A" : "B") + (exit ? " 汇出" : " 汇入"));
        roads.add(
            new Leg(
                (arm == 0 ? "A" : "B") + (exit ? " 汇出至" : " 汇入自") + (single ? "单点中心路口" : "菱形端部路口"),
                exit ? arm : -1,
                exit ? -1 : arm,
                ramp));
      }
    if (options.preset == Preset.PARCLO) {
      // A4 partial cloverleaf: two opposite free-flow leaves plus the four diamond ramps.
      for (int arm : new int[] {0, 2}) {
        int to = (arm + (traffic > 0 ? 1 : 3)) % 4;
        Route r = route(arm, to, false, true);
        Mesh m = horizontal(r);
        // The leaves cross the axes on the single intermediate deck.
        List<Mesh> mains = roads.subList(0, 2).stream().map(Leg::mesh).toList();
        for (int i = r.leadEnd + 1; i < r.tailStart; i++)
          for (Mesh main : mains)
            if (overlap(main, m.samples().get(i)) != null) {
              r.plateauStart = r.plateauStart < 0 ? i : Math.min(r.plateauStart, i);
              r.plateauEnd = Math.max(r.plateauEnd, i);
            }
        r.layer = base + step;
        roads.add(new Leg(r.name, arm, to, elevate(r)));
      }
    }
    validateService();
    return finish(8);
  }

  private Settings rampSettings() {
    Style style = options.lanes == 1 ? Style.R1 : Style.R2;
    return new Settings(Mode.CURVE, style, options.width(), 1, .4, 90)
        .options(
            RoadProfile.Options.DEFAULT
                .traffic(options.leftTraffic)
                .route(Routing.DEFAULT.fit(false)));
  }

  private Mesh straight(V a, V b, Settings s) {
    List<Sample> samples = new ArrayList<>();
    int n = (int) Math.ceil(a.sub(b).horizontalLength() / .5);
    V left = direction(b.sub(a)).left();
    for (int i = 0; i <= n; i++)
      samples.add(
          new Sample(
              world(a.mul(1 - i / (double) n).add(b.mul(i / (double) n))), left, 0, s.width() / 2));
    return RoadRibbon.mesh(samples, s);
  }

  /** Rounded paved corners, with a real crossroad through the two ramp terminals. */
  private void intersection(double station, double corner, double rampHalf) {
    double inner = main[1].width() / 2, reach = inner + corner;
    Settings s =
        new Settings(
                Mode.STRAIGHT,
                Style.UNMARKED,
                Math.min(64, rampHalf * 2),
                Math.max(1, main[1].thickness()),
                .4,
                90)
            .structure(Structure.AUTO)
            .options(RoadProfile.Options.DEFAULT.route(Routing.DEFAULT.fit(false)));
    List<Sample> samples = new ArrayList<>();
    int n = (int) Math.ceil(reach * 4);
    for (int i = 0; i <= n; i++) {
      double x = -reach + 2 * reach * i / n;
      double offset = Math.max(0, Math.abs(x) - inner);
      double half =
          rampHalf
              + corner
              - Math.sqrt(Math.max(0, corner * corner - (corner - offset) * (corner - offset)));
      samples.add(
          new Sample(world(new V(x, heights[1], station)), u.left(), 0, Math.min(32, half)));
    }
    roads.add(
        new Leg(
            options.preset == Preset.SPUI ? "单点中心路口" : "菱形端部路口",
            -1,
            -1,
            RoadRibbon.mesh(samples, s)));
  }

  private static void appendCurve(
      List<V> points, List<Double> widths, List<V> curve, double width) {
    for (int i = 1; i < curve.size(); i++) {
      points.add(curve.get(i));
      widths.add(width);
    }
  }

  private Mesh serviceRamp(
      List<V> points,
      List<Double> widths,
      int hold,
      double startY,
      double endY,
      double flat,
      boolean reverse,
      String name) {
    double[] distance = new double[points.size()];
    for (int i = 1; i < points.size(); i++)
      distance[i] = distance[i - 1] + points.get(i).sub(points.get(i - 1)).horizontalLength();
    double begin = distance[hold], end = distance[distance.length - 1] - flat;
    checkClimb(name, "主路过渡与平交路口之间的升降区", endY - startY, end - begin);
    List<Sample> samples = new ArrayList<>();
    for (int i = 0; i < points.size(); i++) {
      V p = points.get(i),
          dir = points.get(Math.min(i + 1, points.size() - 1)).sub(points.get(Math.max(0, i - 1)));
      double y = startY + (endY - startY) * grade((distance[i] - begin) / (end - begin));
      samples.add(
          new Sample(new V(p.x(), y, p.z()), dir.horizontalUnit().left(), 0, widths.get(i) / 2));
    }
    if (reverse) {
      Collections.reverse(samples);
      samples.replaceAll(q -> new Sample(q.center(), q.left().mul(-1), 0, q.halfWidth()));
    }
    Mesh mesh = RoadRibbon.mesh(samples, rampSettings());
    RoadGrades.validate(mesh);
    RoadRibbon.checkSelfIntersections(mesh, options.clearance);
    return mesh;
  }

  /** Two uninterrupted mainlines, a separate intermediate ring, eight one-way slip roads. */
  private Plan roundabout() {
    double width = options.width();
    double radius =
        Math.max(
            100,
            Math.max(options.radius * 2, Math.max(main[0].width(), main[1].width()) + width + 48));
    double ringY = (heights[0] + heights[1]) / 2;
    List<Sample> samples = new ArrayList<>();
    int count = (int) Math.ceil(2 * Math.PI * radius / .4);
    for (int i = 0; i <= count; i++) {
      double angle = -traffic * 2 * Math.PI * i / count;
      V p = new V(radius * Math.cos(angle), ringY, radius * Math.sin(angle));
      V d = direction(new V(traffic * Math.sin(angle), 0, -traffic * Math.cos(angle)));
      samples.add(new Sample(world(p), d.left(), 0, width / 2));
    }
    Mesh ring = RoadRibbon.mesh(samples, rampSettings());
    roads.add(
        new Leg(
            "独立中层环道",
            -1,
            -1,
            new Mesh(
                ring.samples(),
                ring.settings(),
                ring.min(),
                ring.max(),
                ring.length(),
                true,
                null)));
    for (int arm = 0; arm < 4; arm++)
      for (boolean exit : new boolean[] {true, false}) {
        int axis = arm % 2, sign = exit ? 1 : -1;
        V inward = ARMS[arm].mul(-1),
            side = inward.left().mul(traffic * sign),
            forward = direction(inward);
        double source = lengths[arm] - 8, lead = transitionLength();
        if (source - lead < radius + 90)
          throw new IllegalArgumentException("独立环道和八条匝道需要更大范围，请将各端点外移");
        double outer = detachedOffset(axis);
        V a = world(ARMS[arm].mul(source).add(side.mul(attachment(axis)))),
            b = world(ARMS[arm].mul(source - lead).add(side.mul(outer)));
        double angle = .62;
        V radial = ARMS[arm].mul(Math.cos(angle)).add(side.mul(Math.sin(angle)));
        V target = world(new V(radial.x() * radius, ringY, radial.z() * radius));
        V tangent = direction(new V(traffic * radial.z() * sign, 0, -traffic * radial.x() * sign));
        List<V> points = new ArrayList<>();
        List<Double> widths = new ArrayList<>();
        appendLead(axis, points, widths, a, b, forward, lead, portWidth(axis), width, false);
        int hold = points.size() - 1;
        V approach = world(ARMS[arm].mul(radius + 60).add(side.mul(outer)));
        appendCurve(
            points,
            widths,
            curve(
                List.of(
                    new Knot(b, leadTangent(a, b, forward, lead, axis)), new Knot(approach, forward), new Knot(target, tangent))),
            width);
        Mesh mesh =
            serviceRamp(points, widths, hold, heights[axis], ringY, 22, !exit, "环道匝道 " + arm);
        roads.add(
            new Leg("环道 " + arm + (exit ? " 汇出" : " 汇入"), exit ? arm : -1, exit ? -1 : arm, mesh));
      }
    validateService();
    return finish(8);
  }

  /** Flat junctions are intentional. Every overlap during a climb must still meet clearance. */
  private void validateService() {
    for (int i = 2; i < roads.size(); i++) {
      Mesh a = roads.get(i).mesh();
      for (int j = 0; j < i; j++) {
        Mesh b = roads.get(j).mesh();
        for (int k = 0; k < a.samples().size(); k += 3) {
          Sample p = a.samples().get(k);
          var q = overlap(b, p);
          if (q == null) continue;
          double dy = p.center().y() - q.sample().center().y();
          if (Math.abs(dy) < .12) continue;
          double need =
              options.clearance + (dy >= 0 ? a.settings().thickness() : b.settings().thickness());
          if (Math.abs(dy) < need - .05)
            throw new IllegalArgumentException(
                roads.get(i).name + "与" + roads.get(j).name + "净高不足，请扩大范围");
        }
      }
    }
  }

  private Plan finish(int movements) {
    for (Leg road : roads) RoadGrades.validate(road.mesh());
    for (Leg road : roads)
      for (Sample sample : road.mesh.samples()) {
        V p = local(sample.center());
        double pad = Math.max(main[0].width(), main[1].width());
        if (p.x() < -lengths[0] - pad
            || p.x() > lengths[2] + pad
            || p.z() < -lengths[1] - pad
            || p.z() > lengths[3] + pad) throw new IllegalArgumentException("该预设超出选定范围，请扩大范围");
      }
    double min =
        roads.stream()
            .filter(l -> l.mesh.settings().style().ramp())
            .mapToDouble(l -> RoadRibbon.minRadius(l.mesh))
            .min()
            .orElse(0);
    return new Plan(
        List.copyOf(roads),
        movements,
        min,
        roads.stream().mapToDouble(l -> l.mesh.max().y()).max().orElse(base),
        center);
  }

  private Plan doubleTrumpet() {
    // Two independently validated three-way trumpets share AB. C/D join different T junctions.
    if (Math.min(lengths[0], lengths[2]) < 800 - 1e-6
        || Math.min(lengths[1], lengths[3]) < 760 - 1e-6)
      throw new IllegalArgumentException(
          "双喇叭由两座三向喇叭组成：A/B 到交点各至少 800 格，C/D 各至少 760 格；小范围请选部分苜蓿叶或混合式");
    roads.remove(1);
    double offset =
        Math.min(Math.min(lengths[0], lengths[2]) * .50, Math.min(lengths[1], lengths[3]) * .60);
    for (int arm : new int[] {1, 3}) {
      int sign = arm == 1 ? -1 : 1;
      V left = world(new V(arm == 1 ? -lengths[0] : 0, heights[0], 0)),
          right = world(new V(arm == 1 ? 0 : lengths[2], heights[0], 0)),
          branch = world(new V(sign * offset, heights[1], sign * lengths[arm] * .65));
      var opts =
          new Options(
              Preset.TRUMPET,
              options.leftTraffic,
              options.lanes,
              options.transition,
              options.radius,
              options.clearance,
              options.upper,
              options.rampWidth,
              false);
      var child =
          plan(
              new Node[] {new Node(left, 0, 0), new Node(right, 0, 0), new Node(branch, 0, 0)},
              main[0],
              main[1],
              opts);
      Mesh branchRoad = child.legs.get(1).mesh();
      V real = nodes[arm == 1 ? 2 : 3].position(), dir = branchRoad.first().left().left().mul(-1);
      var feed = curve(List.of(new Knot(real, dir), new Knot(branch, dir)));
      List<Sample> samples = new ArrayList<>();
      for (int i = 0; i < feed.size() - 1; i++) {
        V d = i == 0 ? feed.get(1).sub(feed.get(0)) : feed.get(i + 1).sub(feed.get(i - 1));
        samples.add(
            new Sample(
                new V(feed.get(i).x(), heights[1], feed.get(i).z()),
                d.horizontalUnit().left(),
                0,
                main[1].width() / 2));
      }
      samples.addAll(branchRoad.samples());
      roads.add(
          new Leg(
              arm == 1 ? "C 方向喇叭支路" : "D 方向喇叭支路", arm, -1, RoadRibbon.mesh(samples, setting(1))));
      for (int i = 2; i < child.legs.size(); i++) {
        Leg l = child.legs.get(i);
        roads.add(
            new Leg(
                (arm == 1 ? "C 侧 " : "D 侧 ") + l.name,
                l.from == 1 ? arm : l.from,
                l.to == 1 ? arm : l.to,
                l.mesh));
      }
    }
    validateService();
    return finish(8);
  }

  private Route route(int from, int to, boolean right, boolean leaf) {
    V di = ARMS[from].mul(-1), dj = ARMS[to];
    // A growing auxiliary lane holds its inner edge on the mainline edge. Its center never
    // makes a second lateral excursion before the turn, for any preset or traffic side.
    double separation = -.2;
    int ia = from % 2, ib = to % 2;
    double width = options.width(),
        lead = transitionLength(),
        oa = leaf && options.preset == Preset.CLOVERLEAF ? auxiliaryOffset(ia) : detachedOffset(ia),
        ob = leaf && options.preset == Preset.CLOVERLEAF ? auxiliaryOffset(ib) : detachedOffset(ib);
    if (leaf && options.preset.three) lead = Math.max(lead, Math.max(oa, ob) + loopRadius + 24);
    double
        sa =
            Math.min(
                lengths[from] - 8, Math.min(lengths[0], Math.min(lengths[1], lengths[2])) * .94),
        sb = Math.min(lengths[to] - 8, sa);
    if (!options.preset.three) {
      double shortest = Arrays.stream(lengths).min().orElseThrow();
      sa = Math.min(sa, shortest * .94);
      sb = Math.min(sb, shortest * .94);
    }
    V centerA, centerB;
    if (leaf) {
      double station = Math.max(oa, ob) + loopRadius;
      centerA = di.mul(station - lead);
      centerB = dj.mul(-(station - lead));
    } else {
      centerA = ARMS[from].mul(sa);
      centerB = ARMS[to].mul(sb);
    }
    V fwa = direction(di), fwb = direction(dj), wa = world(centerA), wb = world(centerB);
    double atA = attachment(ia), atB = attachment(ib);
    V start = wa.add(fwa.left().mul((options.leftTraffic ? -1 : 1) * atA)),
        end = wb.add(fwb.left().mul((options.leftTraffic ? -1 : 1) * atB));
    V aa = wa.add(fwa.mul(lead)).add(fwa.left().mul((options.leftTraffic ? -1 : 1) * oa));
    V bb = wb.sub(fwb.mul(lead)).add(fwb.left().mul((options.leftTraffic ? -1 : 1) * ob));
    V loopA = aa, loopB = bb;
    boolean terminalA = options.preset.three && from == 1;
    boolean terminalB = options.preset.three && to == 1;
    boolean collector = leaf && options.preset == Preset.CLOVERLEAF;
    if (terminalA)
      aa =
          world(ARMS[1].mul(terminalStation()))
              .add(fwa.left().mul((options.leftTraffic ? -1 : 1) * (width / 2 + .5)));
    if (terminalB)
      bb =
          world(ARMS[1].mul(terminalStation()))
              .add(fwb.left().mul((options.leftTraffic ? -1 : 1) * (width / 2 + .5)));
    List<V> path = new ArrayList<>();
    List<Double> widths = new ArrayList<>();
    double pwA = portWidth(ia), pwB = portWidth(ib);
    if (terminalA || collector) {
      path.add(aa);
      widths.add(width);
    } else appendLead(ia, path, widths, start, aa, fwa, lead, pwA, width, false, !leaf);
    int leadEnd = path.size() - 1;
    // Match the nonzero peel slope at the lead/curve seam. A smoothstep returning to
    // zero here made the highway ramp straighten and bend a second time.
    double slopeA = leaf || terminalA || collector ? 0 : peelSlope(ia, lead);
    double slopeB = leaf || terminalB || collector ? 0 : peelSlope(ib, lead);
    int handed = options.leftTraffic ? -1 : 1;
    V tangentA = fwa.add(fwa.left().mul(handed * slopeA)).horizontalUnit();
    V tangentB = fwb.sub(fwb.left().mul(handed * slopeB)).horizontalUnit();
    List<V> middle;
    if (leaf) {
      RoadLoop loop = new RoadLoop(loopA, loopB, tangentA, tangentB, loopRadius, !options.leftTraffic);
      int n = (int) Math.ceil(loop.length() / .45);
      middle = new ArrayList<>();
      if (terminalA) middle.addAll(curve(List.of(new Knot(aa, fwa), new Knot(loopA, fwa))));
      for (int k = terminalA ? 1 : 0; k <= n; k++) middle.add(loop.point(k / (double) n));
    } else if (right) {
      // Broad continuous inward bend. Existing radius, ribbon and clearance checks still
      // reject layouts that cut into a leaf; fitting enlarges the footprint when necessary.
      middle = curve(List.of(new Knot(aa, tangentA), new Knot(bb, tangentB)), directCurve);
    } else if (options.preset == Preset.TURBINE || options.preset == Preset.Y) {
      double radius =
          Math.max(options.radius * 2, Math.max(main[0].width(), main[1].width()) * 2 + 18);
      V enter = world(dj.mul(-radius)), leave = world(di.mul(radius));
      middle = new ArrayList<>(curve(List.of(new Knot(aa, tangentA), new Knot(enter, fwa))));
      int n = (int) Math.ceil(Math.PI * radius / .8);
      for (int i = 1; i <= n; i++) {
        double angle = Math.PI / 2 * i / n;
        middle.add(world(dj.mul(-radius * Math.cos(angle)).add(di.mul(radius * Math.sin(angle)))));
      }
      var tail = curve(List.of(new Knot(leave, fwb), new Knot(bb, tangentB)));
      middle.addAll(tail.subList(1, tail.size()));
    } else {
      List<Knot> knots = new ArrayList<>();
      knots.add(new Knot(aa, tangentA));
      double room = Math.min(sa, sb) - lead;
      if (!right && options.preset != Preset.TURBINE && options.preset != Preset.Y) {
        double reach =
            options.preset == Preset.STACK
                ? Math.max(
                    0, room - cornerScale*Math.max(options.radius, Math.max(oa, ob) * 2.5 + width * 1.5 + 2))
                : Math.max(20, room * .42);
        if(reach>1){knots.add(new Knot(aa.add(fwa.mul(reach)), fwa));
        knots.add(new Knot(bb.sub(fwb.mul(reach)), fwb));}
      }
      knots.add(new Knot(bb, tangentB));
      boolean compactCorner = right;
      middle = curve(knots, compactCorner ? .18 : .4);
    }
    for (int i = 1; i < middle.size(); i++) {
      path.add(middle.get(i));
      widths.add(width);
    }
    int tailStart = path.size() - 1;
    if (!terminalB && !collector) appendLead(ib, path, widths, bb, end, fwb, lead, width, pwB, true, !leaf);
    if (path.size() > RoadLimits.MAX_SAMPLES) throw new IllegalArgumentException("立交匝道过长，请缩小范围或半径");
    return new Route(
        (char) ('A' + (from == 1 ? 2 : from == 2 ? 1 : from))
            + "→"
            + (char) ('A' + (to == 1 ? 2 : to == 2 ? 1 : to))
            + (leaf
                ? " 环绕"
                : right
                    ? (options.leftTraffic ? " 左转" : " 右转")
                    : (options.leftTraffic ? " 右转" : " 左转")),
        from,
        to,
        path,
        widths,
        leadEnd,
        tailStart,
        right ? 0 : leaf ? 1 : 2);
  }

  private double padding(int axis) {
    if (!main[axis].options().routing().fitEdges()) return 0;
    V d = axis == 0 ? u : v, p = nodes[axis == 0 ? 0 : 2].position();
    double coordinate;
    if (Math.abs(d.x()) > .99999) coordinate = p.z();
    else if (Math.abs(d.z()) > .99999) coordinate = p.x();
    else return 0;
    double half = main[axis].width() / 2;
    return Math.abs(coordinate - Math.floor(coordinate) - .5) < 1e-7
        ? Math.ceil(coordinate + half - 1e-7) - coordinate - half
        : 0;
  }

  private double detachedOffset(int axis) {
    return main[axis].width() / 2 + padding(axis) + options.width() / 2 - .2;
  }

  /** The existing shoulder becomes the innermost ramp lane along the entire parallel lead. */
  private double auxiliaryOffset(int axis) {
    var layout = RoadProfile.layout(main[axis], main[axis].width() + 2 * padding(axis));
    if (layout.catalog().type() == RoadProfile.Type.HIGHWAY)
      return Math.abs(layout.outer(layout.outside())) + (options.width() - 1) / 2;
    return main[axis].width() / 2 + padding(axis) + options.width() / 2 - .2;
  }

  private double attachment(int axis) {
    if (RoadProfile.catalog(main[axis].style()).type() == RoadProfile.Type.HIGHWAY) {
      var layout = RoadProfile.layout(main[axis], main[axis].width() + 2 * padding(axis));
      return Math.abs(layout.outer(layout.outside())) + layout.shoulderWidth() / 2;
    }
    return main[axis].width() / 2 + padding(axis) - .2 + portWidth(axis) / 2;
  }

  private double portWidth(int axis) {
    if (RoadProfile.catalog(main[axis].style()).type() == RoadProfile.Type.HIGHWAY)
      return RoadProfile.layout(main[axis], main[axis].width() + 2 * padding(axis)).shoulderWidth();
    return .5;
  }

  private V leadTangent(V a, V b, V forward, double lead, int axis) {
    V side = b.sub(a).sub(forward.mul(lead)).horizontalUnit();
    return forward.add(side.mul(peelSlope(axis, lead))).horizontalUnit();
  }

  private double peelSlope(int axis, double lead) {
    if (servicePreset() || RoadProfile.catalog(main[axis].style()).type() != RoadProfile.Type.HIGHWAY) return 0;
    return 2 * (detachedOffset(axis) - auxiliaryOffset(axis)) / (.35 * lead);
  }

  private void appendLead(
      int axis, List<V> out,
      List<Double> widths,
      V a,
      V b,
      V forward,
      double lead,
      double wa,
      double wb,
      boolean skip) {
    appendLead(axis, out, widths, a, b, forward, lead, wa, wb, skip, false);
  }

  private void appendLead(int axis, List<V> out, List<Double> widths, V a, V b,
      V forward, double lead, double wa, double wb, boolean skip, boolean continuous) {
    V side = b.sub(a).sub(forward.mul(lead));
    int n = (int) Math.ceil(lead / .4);
    for (int i = skip ? 1 : 0; i <= n; i++) {
      double t = i / (double) n;
      boolean merging = wa > wb;
      double q = merging ? 1 - t : t;
      double grow = Settings.smooth(Math.min(1, q / .32));
      double shift = grow;
      if (RoadProfile.catalog(main[axis].style()).type() == RoadProfile.Type.HIGHWAY) {
        double total = side.horizontalLength();
        double occupied = auxiliaryOffset(axis) - attachment(axis);
        // First use the existing shoulder as the inner lane. Peel away only after the
        // full-width parallel section, so a later elevated segment can clear the main deck.
        double peel = Math.max(0, Math.min(1, (q - .65) / .35));
        double leave = continuous ? peel * peel : Settings.smooth(peel);
        shift = total < 1e-8 ? grow
            : (occupied * grow + Math.max(0, total - occupied) * leave) / total;
      }
      if (merging) {
        grow = 1 - grow;
        shift = 1 - shift;
      }
      out.add(a.add(forward.mul(lead * t)).add(side.mul(shift)));
      widths.add(wa + (wb - wa) * grow);
    }
  }

  private static List<V> curve(List<Knot> knots) {
    return curve(knots, .4);
  }

  private static List<V> curve(List<Knot> knots, double strength) {
    List<V> out = new ArrayList<>();
    for (int j = 1; j < knots.size(); j++) {
      Knot a = knots.get(j - 1), b = knots.get(j);
      double chord = a.p.sub(b.p).horizontalLength(), handle = chord * strength;
      V c = a.p.add(a.direction.mul(handle)), d = b.p.sub(b.direction.mul(handle));
      int n = (int) Math.ceil(chord * 1.65 / .4);
      for (int i = j == 1 ? 0 : 1; i <= n; i++) {
        double t = i / (double) n, u = 1 - t;
        out.add(
            a.p
                .mul(u * u * u)
                .add(c.mul(3 * u * u * t))
                .add(d.mul(3 * u * t * t))
                .add(b.p.mul(t * t * t)));
      }
    }
    return out;
  }

  private void extendThroat(Route r) {
    int originalA = r.leadEnd, originalB = r.tailStart;
    // Shoulder-sharing leads remain at host height until the full ramp ribbon clears it.
    for (int i = originalA; i < r.points.size() / 2; i++) {
      if (overlap(roads.get(r.from % 2).mesh, routeSample(r, i)) == null) break;
      r.leadEnd = Math.max(r.leadEnd, i);
    }
    for (int i = originalB; i > r.points.size() / 2; i--) {
      if (overlap(roads.get(r.to % 2).mesh, routeSample(r, i)) == null) break;
      r.tailStart = Math.min(r.tailStart, i);
    }
    if (options.preset == Preset.CLOVERLEAF && r.priority == 1) {
      // A short level landing keeps loop elevation continuous with the shared lane.
      // It is part of the loop itself; it does not extend the collector past the mouth.
      double landing = options.width() * 2 + 4, distance = 0;
      for (int i = 1; i < r.points.size() / 2; i++) {
        distance += r.points.get(i).distance(r.points.get(i - 1));
        if (distance > landing) break;
        r.leadEnd = Math.max(r.leadEnd, i);
      }
      distance = 0;
      for (int i = r.points.size() - 2; i > r.points.size() / 2; i--) {
        distance += r.points.get(i).distance(r.points.get(i + 1));
        if (distance > landing) break;
        r.tailStart = Math.min(r.tailStart, i);
      }
    }
    // A loop joins its continuous collector tangentially. Stay flat until the full ribbon has
    // separated, then use the remaining arc for the climb (and symmetrically on arrival).
    if (options.preset == Preset.CLOVERLEAF) {
      for (int i = originalA; i < r.points.size() / 2; i++) {
        Sample p = routeSample(r, i);
        boolean touches = false;
        for (int k = 2; k < roads.size(); k++)
          if (roads.get(k).from == r.from && overlap(roads.get(k).mesh, p) != null) touches = true;
        if (!touches) break;
        r.leadEnd = Math.max(r.leadEnd, i);
      }
      for (int i = originalB; i > r.points.size() / 2; i--) {
        Sample p = routeSample(r, i);
        boolean touches = false;
        for (int k = 2; k < roads.size(); k++)
          if (roads.get(k).to == r.to && overlap(roads.get(k).mesh, p) != null) touches = true;
        if (!touches) break;
        r.tailStart = Math.min(r.tailStart, i);
      }
    }
    for (Route other : routes) {
      if (other == r) continue;
      if (r.from == other.from && r.points.get(0).distance(other.points.get(0)) < .1) {
        int limit = Math.min(originalB, r.points.size() / 2);
        for (int i = originalA; i < limit; i++)
          if (near(other, r.points.get(i), r.widths.get(i) / 2 + options.lanes * 2 + 3))
            r.leadEnd = Math.max(r.leadEnd, i);
          else break;
      }
      if (r.to == other.to
          && r.points.get(r.points.size() - 1).distance(other.points.get(other.points.size() - 1))
              < .1) {
        int limit = Math.max(originalA, r.points.size() / 2);
        for (int i = originalB; i > limit; i--)
          if (near(other, r.points.get(i), r.widths.get(i) / 2 + options.lanes * 2 + 3))
            r.tailStart = Math.min(r.tailStart, i);
          else break;
      }
    }
  }

  private Sample routeSample(Route r, int i) {
    V before = r.points.get(Math.max(0, i - 1)),
        after = r.points.get(Math.min(r.points.size() - 1, i + 1));
    return new Sample(
        r.points.get(i), after.sub(before).horizontalUnit().left(), 0, r.widths.get(i) / 2);
  }

  private static boolean near(Route r, V p, double radius) {
    for (int i = 0; i < r.points.size(); i += 3)
      if (r.points.get(i).sub(p).horizontalLength() < radius) return true;
    return false;
  }

  private Mesh horizontal(Route r) {
    List<Sample> points = new ArrayList<>();
    for (int i = 0; i < r.points.size(); i++) {
      V p = r.points.get(i);
      V d =
          i == 0
              ? r.points.get(1).sub(p)
              : i == r.points.size() - 1
                  ? p.sub(r.points.get(i - 1))
                  : r.points.get(i + 1).sub(r.points.get(i - 1));
      points.add(
          new Sample(new V(p.x(), base, p.z()), d.horizontalUnit().left(), 0, r.widths.get(i) / 2));
    }
    return RoadRibbon.mesh(points, rampSettings());
  }

  private Mesh elevate(Route r) {
    double[] distance = new double[r.points.size()];
    for (int i = 1; i < distance.length; i++)
      distance[i] = distance[i - 1] + r.points.get(i).sub(r.points.get(i - 1)).horizontalLength();
    double d0 = distance[r.leadEnd],
        d1 = distance[r.tailStart],
        h0 = hostHeight(r.from, r.points.get(r.leadEnd)),
        h1 = hostHeight(r.to, r.points.get(r.tailStart));
    if (r.plateauStart >= 0) {
      checkClimb(r.name, "汇出后至首个交叉平台", r.layer - h0, distance[r.plateauStart] - d0);
      checkClimb(r.name, "末个交叉平台至汇入", h1 - r.layer, d1 - distance[r.plateauEnd]);
    } else checkClimb(r.name, "汇出与汇入之间的升降区", h1 - h0, d1 - d0);
    List<Sample> samples = new ArrayList<>();
    for (int i = 0; i < r.points.size(); i++) {
      V p = r.points.get(i);
      double y;
      if (i <= r.leadEnd) y = hostHeight(r.from, p);
      else if (i >= r.tailStart) y = hostHeight(r.to, p);
      else {
        double t = (distance[i] - d0) / (d1 - d0);
        if (r.plateauStart >= 0) {
          double begin = distance[r.plateauStart], end = distance[r.plateauEnd];
          if (distance[i] < begin)
            y = h0 + (r.layer - h0) * grade((distance[i] - d0) / (begin - d0));
          else if (distance[i] > end)
            y = r.layer + (h1 - r.layer) * grade((distance[i] - end) / (d1 - end));
          else y = r.layer;
        } else y = h0 + (h1 - h0) * grade(t);
      }
      V delta =
          i == 0
              ? r.points.get(1).sub(p)
              : i == r.points.size() - 1
                  ? p.sub(r.points.get(i - 1))
                  : r.points.get(i + 1).sub(r.points.get(i - 1));
      samples.add(
          new Sample(
              new V(p.x(), y, p.z()), delta.horizontalUnit().left(), 0, r.widths.get(i) / 2));
    }
    Style style = options.lanes == 1 ? Style.R1 : Style.R2;
    Settings s =
        new Settings(Mode.CURVE, style, options.width(), 1, .4, 90)
            .rampTurn(RampTurn.AUTO)
            .options(RoadProfile.Options.DEFAULT.traffic(options.leftTraffic));
    try {
      Mesh mesh = RoadRibbon.mesh(samples, s);
      RoadGrades.validate(mesh);
      return mesh;
    } catch (IllegalArgumentException e) {
      throw new IllegalArgumentException(
          r.name
              + "："
              + e.getMessage()
              + (Boolean.getBoolean("sr.debug")
                  ? " holds="
                      + r.leadEnd
                      + ","
                      + r.tailStart
                      + " plate="
                      + r.plateauStart
                      + ","
                      + r.plateauEnd
                      + " d="
                      + d0
                      + ","
                      + d1
                      + " hp="
                      + (r.plateauStart < 0
                          ? "none"
                          : distance[r.plateauStart] + "," + distance[r.plateauEnd])
                      + " layer="
                      + r.layer
                      + " endpoints="
                      + h0
                      + ","
                      + h1
                  : ""));
    }
  }

  private static void checkClimb(String name, String section, double height, double available) {
    double need = Math.abs(height) * 1.25 / RoadGrades.MAX_RAMP_GRADE;
    if (available + 1e-6 < need)
      throw new IllegalArgumentException(
          String.format(
              Locale.ROOT,
              "%s：%s可用 %.1f 格，升降 %.1f 格按 15%% 纵坡至少需 %.1f 格；这是该段约束，非端点到交点距离",
              name,
              section,
              available,
              Math.abs(height),
              need));
  }

  private static double grade(double t) {
    t = Math.max(0, Math.min(1, t));
    // Parabolic vertical curves at both ends with a constant-grade middle.
    // Unlike a whole-span smoothstep this uses the available climb length evenly.
    double ease = .20, scale = 1 / (1 - ease);
    if (t < ease) return scale * t * t / (2 * ease);
    if (t > 1 - ease) return 1 - scale * (1 - t) * (1 - t) / (2 * ease);
    return scale * (t - ease / 2);
  }

  private double hostHeight(int arm, V point) {
    V local = local(point);
    return height(arm % 2, arm % 2 == 0 ? local.x() : local.z());
  }

  private record Collision(int sample, double height, String reason) {}

  private Collision conflict(
      Mesh mesh, List<Mesh> mains, List<Mesh> previous, Route route, int index) {
    int mainCount = mains.size();
    List<Mesh> all = new ArrayList<>(mains);
    all.addAll(previous);
    for (int k = 0; k < all.size(); k++) {
      Mesh other = all.get(k);
      for (int i = 0; i < mesh.samples().size(); i += 3) {
        Sample p = mesh.samples().get(i);
        var q = overlap(other, p);
        if (q == null) continue;
        double dy = p.center().y() - q.sample().center().y();
        if (Math.abs(dy) < .2
            && shared(route, i, p, k < mainCount ? null : routes.get(k - mainCount), k, other, q))
          continue;
        double need =
            options.clearance
                + (dy >= 0 ? mesh.settings().thickness() : other.settings().thickness());
        if (Math.abs(dy) < need - .05)
          return new Collision(
              i,
              q.sample().center().y(),
              "与" + (k < mainCount ? roads.get(k).name : routes.get(k - mainCount).name) + "净高不足");
      }
    }
    return null;
  }

  private RoadQueries.Projection overlap(Mesh other, Sample p) {
    if (p.center().x() + p.halfWidth() < other.min().x()
        || p.center().x() - p.halfWidth() > other.max().x()
        || p.center().z() + p.halfWidth() < other.min().z()
        || p.center().z() - p.halfWidth() > other.max().z()) return null;
    var q = RoadQueries.horizontal(other, p.center());
    double along = p.center().sub(q.sample().center()).dot(q.tangent().horizontalUnit());
    double extension = p.halfWidth() * Math.abs(p.left().dot(q.tangent().horizontalUnit()));
    if (q.sample().distance() < 1e-6 && along < -extension
        || q.sample().distance() > other.length() - 1e-6 && along > extension) return null;
    return q.horizontalDistance() < p.halfWidth() + q.sample().halfWidth() - (preciseClearance?0:.3) ? q : null;
  }

  private boolean shared(
      Route r, int i, Sample p, Route other, int k, Mesh mesh, RoadQueries.Projection q) {
    double dot = p.left().dot(q.sample().left());
    int host = i <= r.leadEnd + 4 ? r.from % 2 : i >= r.tailStart - 4 ? r.to % 2 : -1;
    if (host < 0) return false;
    if (other == null) return roads.get(k).from % 2 == host && Math.abs(dot) > .3;
    int otherHost =
        q.sample().distance() <= mesh.samples().get(other.leadEnd).distance() + 2
            ? other.from % 2
            : q.sample().distance() >= mesh.samples().get(other.tailStart).distance() - 2
                ? other.to % 2
                : -1;
    return host == otherHost && dot > .3;
  }

  private static double cross(V a, V b) {
    return a.x() * b.z() - a.z() * b.x();
  }

  private InterchangePlanner() {
    throw new AssertionError();
  }
}
