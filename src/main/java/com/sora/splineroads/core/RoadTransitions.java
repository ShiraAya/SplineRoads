package com.sora.splineroads.core;

import com.sora.splineroads.core.RoadGeometry.*;
import com.sora.splineroads.core.RoadProfile.*;
import java.util.*;

/** A shared endpoint cross-section, independent of construction order and pavement sampling. */
public final class RoadTransitions {
  public record Section(
      Style style,
      double width,
      boolean cycle,
      boolean cycleRail,
      boolean curb,
      OuterRail outerRail,RoadSidewalks.Config sidewalk,boolean cycleAsphalt,Port port,RoadStreetscape.Config streetscape,RoadLanes.Counts lanes) {
    public Section(Style style,double width,boolean cycle,boolean cycleRail,boolean curb,OuterRail rail,RoadSidewalks.Config walk,boolean asphalt,Port port,RoadStreetscape.Config streetscape){this(style,width,cycle,cycleRail,curb,rail,walk,asphalt,port,streetscape,RoadLanes.Counts.AUTO);}
    public Section(Style style,double width,boolean cycle,boolean cycleRail,boolean curb,OuterRail rail,RoadSidewalks.Config walk,boolean asphalt,Port port){this(style,width,cycle,cycleRail,curb,rail,walk,asphalt,port,RoadStreetscape.Config.DEFAULT.separator(cycleRail?RoadStreetscape.Separator.RAIL:RoadStreetscape.Separator.LINE));}
    public Section(Style style,double width,boolean cycle,boolean cycleRail,boolean curb,OuterRail rail,RoadSidewalks.Config walk,boolean asphalt){this(style,width,cycle,cycleRail,curb,rail,walk,asphalt,null);}
    public Section(Style style,double width,boolean cycle,boolean cycleRail,boolean curb,OuterRail rail,RoadSidewalks.Config walk){this(style,width,cycle,cycleRail,curb,rail,walk,false);}
    public Section(Style style,double width,boolean cycle,boolean cycleRail,boolean curb,OuterRail outerRail){this(style,width,cycle,cycleRail,curb,outerRail,null);}
    public Section {
      if(lanes==null)lanes=RoadLanes.Counts.AUTO;
      if (style == null || outerRail == null || !Double.isFinite(width) || width < 2 || width > 64)
        throw new IllegalArgumentException("接缝断面无效");
    }

    public static Section of(Settings s) {
      var o = s.options();
      return new Section(s.style(), s.width(), o.cycle(), o.cycleRail(), o.curb(), o.outerRail(),o.sidewalk(),o.cycleAsphalt(),o.ends().port(),o.streetscape(),o.lanes());
    }

    public Settings settings(boolean left) {
      return new Settings(Mode.STRAIGHT, style, width, 1, .4, 90)
          .options(
              Options.DEFAULT.traffic(left).extras(cycle, cycleRail, curb).cycleFinish(!cycle?Options.CycleFinish.NONE:cycleAsphalt?Options.CycleFinish.ASPHALT:Options.CycleFinish.GREEN).outerRail(outerRail).sidewalk(sidewalk==null?RoadSidewalks.Config.DEFAULT:sidewalk).ends(Ends.NONE.port(port)).streetscape(streetscape).lanes(lanes));
    }
    public Section port(Port value){return new Section(style,width,cycle,cycleRail,curb,outerRail,sidewalk,cycleAsphalt,value,streetscape,lanes);}
    public Layout layout(boolean left){
      var base=RoadProfile.layout(settings(left),width);
      if(port==null)return base;
      return new Layout(base.catalog(),(port.motorMax()-port.motorMin()-port.median())/base.catalog().lanes(),port.median(),port.motorMin(),port.motorMax(),base.cycleWidth(),base.curbWidth(),base.shoulderWidth(),base.outside(),port.dividers(),port.medianCenter());
    }
  }

  /** Exact cross-section at a trimmed street / junction seam, in the approach frame. */
  public record Port(double motorMin,double motorMax,double median,List<Double> dividers,
      double curbLeft,double curbRight,double medianCenter) {
    public Port(double motorMin,double motorMax,double median,List<Double> dividers,double curbLeft,double curbRight){this(motorMin,motorMax,median,dividers,curbLeft,curbRight,0);}
    public Port {dividers=List.copyOf(dividers);if(!RoadGeometry.finite(motorMin,motorMax,median,curbLeft,curbRight,medianCenter)
        ||motorMin>=motorMax||Math.abs(motorMin)>64||Math.abs(motorMax)>64||median>8||median<0||curbLeft<0||curbRight<0||curbLeft>8||curbRight>8||dividers.size()>10
        ||dividers.stream().anyMatch(d->!Double.isFinite(d)||Math.abs(d)>32))throw new IllegalArgumentException("路口接缝断面无效");}
  }
  public record Ends(Section start, Section end, boolean persistent,double trimmedStart,double trimmedEnd,Port port,double paintPhase) {
    public Ends(Section start,Section end,boolean persistent,double trimmedStart,double trimmedEnd,Port port){this(start,end,persistent,trimmedStart,trimmedEnd,port,0);}
    public Ends(Section start,Section end,boolean persistent){this(start,end,persistent,0,0,null);}
    public Ends {if(!RoadGeometry.finite(trimmedStart,trimmedEnd,paintPhase)||trimmedStart<0||trimmedEnd<0||trimmedStart>8192||trimmedEnd>8192)throw new IllegalArgumentException("道路裁剪范围无效");}
    public Ends trim(double a,double b){return new Ends(start,end,persistent,trimmedStart+a,trimmedEnd+b,port,paintPhase);}
    public Ends port(Port p){return new Ends(start,end,persistent,trimmedStart,trimmedEnd,p,paintPhase);}
    public Ends paintPhase(double phase){return new Ends(start,end,persistent,trimmedStart,trimmedEnd,port,phase);}
    public Ends(Section start, Section end) {
      this(start, end, false);
    }

    public static final Ends NONE = new Ends(null, null, false);
  }

  public static boolean compatible(Settings a, Settings b) {
    if (a.style().ramp() || b.style().ramp()) return true;
    var x = RoadProfile.catalog(a);
    var y = RoadProfile.catalog(b);
    return (x.type() == y.type() || x.type() == Type.ORDINARY && y.type() == Type.HIGHWAY
        || x.type() == Type.HIGHWAY && y.type() == Type.ORDINARY)
        && x.twoWay() == y.twoWay()
        && Math.abs(RoadLanes.counts(a).forward()-RoadLanes.counts(b).forward())<=1
        && Math.abs(RoadLanes.counts(a).reverse()-RoadLanes.counts(b).reverse())<=1;
  }

  public static void requireCompatible(Settings a, Settings b) {
    if (!compatible(a, b))
      throw new IllegalArgumentException("接点两侧断面不匹配："+description(a)+" ↔ "+description(b)+"；需单双向一致，且每方向车道数最多相差 1");
  }
  private static String description(Settings s){return RoadLanes.counts(s).label();}

  /**
   * Both sides independently choose the same complete section, even after rebuilding a neighbor.
   */
  public static Section common(Settings a, Settings b) {
    requireCompatible(a, b);
    Section x = Section.of(a), y = Section.of(b);
    Comparator<Section> order =
        Comparator.comparingInt((Section s) -> RoadProfile.catalog(s.settings(false)).lanes())
            .thenComparingDouble(Section::width)
            .thenComparing(s -> s.style().name())
            .thenComparing(Section::cycle)
            .thenComparing(Section::cycleAsphalt)
            .thenComparing(Section::curb)
            .thenComparing(Section::cycleRail)
            .thenComparing(s -> s.outerRail().name())
            .thenComparing(s -> s.port()==null?"":s.port().toString());
    Section chosen=order.compare(x,y)>=0?x:y;
    var wx=a.options().sidewalk();var wy=b.options().sidewalk();
    var walk=(wx.enabled()&&(!wy.enabled()||wx.width()>=wy.width())?wx:wy).tactile(wx.enabled()&&wy.enabled()&&wx.tactile()&&wy.tactile());
    var shared=new Section(chosen.style(),chosen.width(),chosen.cycle(),chosen.cycleRail(),chosen.curb(),chosen.outerRail(),walk,chosen.cycleAsphalt(),chosen.port(),chosen.streetscape(),chosen.lanes());
    // The common material must be supported by BOTH sides. Keep the authored lane
    // axes in an exact port; replacing GREEN by a nominal RAIL section would widen lanes.
    return a.structure()==Structure.TUNNEL||b.structure()==Structure.TUNNEL?TunnelMedian.seam(shared):shared;
  }

  public static Settings join(Settings s, Settings a, Settings b) {
    if (s.style().ramp() || !RoadProfile.modern(s.style())) return s;
    Section start = a == null || a.style().ramp() ? null : common(s, a);
    Section end = b == null || b.style().ramp() ? null : common(s, b);
    return ends(s, start, end);
  }

  public static Settings ends(Settings s, Section a, Section b) {
    if (s.options().ends().persistent()) {
      if (a == null) a = s.options().ends().start();
      if (b == null) b = s.options().ends().end();
    }
    return s.taper(a == null ? s.width() : a.width(), b == null ? s.width() : b.width())
        .options(s.options().ends(new Ends(a,b,s.options().ends().persistent(),s.options().ends().trimmedStart(),s.options().ends().trimmedEnd(),s.options().ends().port(),s.options().ends().paintPhase())));
  }

  public static double span(Settings s, double length) {
    double delta =
        Math.max(Math.abs(s.startWidth() - s.width()), Math.abs(s.endWidth() - s.width()));
    var e = s.options().ends();
    boolean both =
        e.start() != null
            && !e.start().equals(Section.of(s))
            && e.end() != null
            && !e.end().equals(Section.of(s));
    return Math.min(length * (both ? .45 : .9), Math.max(36, delta * 8));
  }

  private static double weight(double d, double span) {
    return 1 - Settings.smooth(Math.max(0, Math.min(1, d / Math.max(.001, span))));
  }

  public static double width(Settings s, double d, double length) {
    return s.width()
        + (s.startWidth() - s.width()) * weight(d, span(s, length))
        + (s.endWidth() - s.width()) * weight(length - d, span(s, length));
  }

  private record Blend(Layout base, Layout target, double weight) {}

  private static Blend blend(Mesh mesh, Sample p) {
    Settings s = mesh.settings();
    Layout base = RoadProfile.layout(s, p.halfWidth() * 2);
    var ends = s.options().ends();
    double station=p.distance()+ends.trimmedStart(),length=mesh.length()+ends.trimmedStart()+ends.trimmedEnd();
    Section baseSection = Section.of(s);
    double startWeight =
        ends.start() == null || ends.start().equals(baseSection)
            ? 0
            : weight(station, span(s, length));
    double endWeight =
        ends.end() == null || ends.end().equals(baseSection)
            ? 0
            : weight(length - station, span(s, length));
    boolean start = startWeight >= endWeight;
    Section target = start ? ends.start() : ends.end();
    double w = Math.max(startWeight, endWeight);
    return new Blend(
        base,
        target == null
            ? base
            : target.layout(s.options().leftTraffic()),
        w);
  }

  public static Layout layout(Mesh mesh, Sample p) {
    if (mesh.settings().options().ends().equals(Ends.NONE))
      return RoadProfile.layout(mesh.settings(), p.halfWidth() * 2);
    var b = blend(mesh, p);
    var a = b.base();
    var z = b.target();
    double w = b.weight();
    int lanes = a.catalog().lanes();
    for (Section end :
        new Section[] {
          mesh.settings().options().ends().start(), mesh.settings().options().ends().end()
        }) if (end != null) lanes = Math.max(lanes, RoadProfile.catalog(end.settings(false)).lanes());
    List<Double> dividers = new ArrayList<>();
    if (a.catalog().twoWay()) {
      for (int sign : new int[] {-1, 1})
        for (int n = 1; n < Math.max(a.lanesOnSide(sign),z.lanesOnSide(sign)); n++)
          dividers.add(lerp(divider(a, n, sign), divider(z, n, sign), w));
    } else {
      for (int n = 1; n < lanes; n++)
        dividers.add(
            lerp(
                Math.min(a.motorMax(), a.motorMin() + n * a.laneWidth()),
                Math.min(z.motorMax(), z.motorMin() + n * z.laneWidth()),
                w));
    }
    return new Layout(
        w >= .5 ? z.catalog() : a.catalog(),
        lerp(a.laneWidth(), z.laneWidth(), w),
        lerp(a.median(), z.median(), w),
        lerp(a.motorMin(), z.motorMin(), w),
        lerp(a.motorMax(), z.motorMax(), w),
        lerp(a.cycleWidth(), z.cycleWidth(), w),
        lerp(a.curbWidth(), z.curbWidth(), w),
        lerp(a.shoulderWidth(), z.shoulderWidth(), w),
        a.outside(),
        List.copyOf(dividers),lerp(a.medianCenter(),z.medianCenter(),w));
  }

  public static boolean greenCycle(Mesh mesh,Sample p){
    Settings s=mesh.settings();var e=s.options().ends();double station=p.distance()+e.trimmedStart(),length=mesh.length()+e.trimmedStart()+e.trimmedEnd();
    boolean green=s.options().cycle()&&!s.options().cycleAsphalt();
    double a=e.start()==null?0:weight(station,span(s,length)),b=e.end()==null?0:weight(length-station,span(s,length));
    var target=a>=b?e.start():e.end();return target!=null&&target.cycle()&&(!s.options().cycle()||Math.max(a,b)>=.5)?!target.cycleAsphalt():green;
  }
  public static double green(Mesh mesh, Sample p) {
    var b = blend(mesh, p);
    return lerp(
        b.base().catalog().median() == Median.GREEN ? 1 : 0,
        b.target().catalog().median() == Median.GREEN ? 1 : 0,
        b.weight());
  }

  /** Painted closure of the surplus lanes before a terminating three-way split. */
  public static double dropBoundary(Mesh mesh, Sample p) {
    var e = mesh.settings().options().ends();
    if (!e.persistent() || e.end() == null) return Double.NaN;
    int oldLanes = RoadProfile.catalog(mesh.settings()).lanes();
    int newLanes = RoadProfile.catalog(e.end().settings(false)).lanes();
    if (newLanes >= oldLanes) return Double.NaN;
    double length=mesh.length()+e.trimmedStart()+e.trimmedEnd();
    double t = 1 - (mesh.length()+e.trimmedEnd()-p.distance()) / span(mesh.settings(),length);
    if (t <= 0) return Double.NaN;
    var layout = layout(mesh, p);
    double outer = Math.abs(layout.outer(1)) - .2;
    double first = Math.min(outer, layout.median() / 2 + newLanes / 2.0 * layout.laneWidth());
    return outer - Math.max(0, outer - first) * Settings.smooth(Math.min(1, t * 4));
  }

  private static double divider(Layout l, int lane,int side) {
    return l.medianCenter()+side*Math.min(Math.abs(l.outer(side)-l.medianCenter()),l.median()/2+lane*l.laneWidth());
  }

  private static double lerp(double a, double b, double t) {
    return a + (b - a) * t;
  }

  private RoadTransitions() {}
}
