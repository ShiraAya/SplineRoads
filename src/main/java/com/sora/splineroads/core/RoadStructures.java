package com.sora.splineroads.core;

import com.sora.splineroads.core.RoadGeometry.*;
import java.util.*;

/** Persisted, server-planned structure: no terrain scans or pier searches during rendering. */
public final class RoadStructures {
  public static final double PIER_SPACING = 24, MAX_DROP = 96;

  public enum Material {
    DEFAULT,
    STEEL,
    DARK_STEEL,
    CONCRETE,
    SOIL,
    GREEN,
    LAMP,
    SIGNAL_MAIN,
    SIGNAL_RAMP,
    CB_POST,
    CB_ARM,
    CB_BASE,
    SIGNAL_VEHICLE,
    SIGNAL_PEDESTRIAN,
    SIGNAL_LEFT, TUNNEL, SIGN_GREEN, SIGN_BLUE, SIGN_WHITE, ARCH_STEEL, GANTRY_FRAME, WALK_STONE_BRICKS, WALK_BRICKS, WALK_ANDESITE, WALK_DIORITE, WALK_GRANITE, WALK_SMOOTH_STONE, TACTILE, CB_SIGN, CB_NOISE, OAK_LOG, BIRCH_LOG, SPRUCE_LOG, CHERRY_LOG, OAK_LEAVES, BIRCH_LEAVES, SPRUCE_LEAVES, CHERRY_LEAVES, FLOWERS, LAMP_BLUE, FLOWER_PINK, FLOWER_YELLOW, FLOWER_WHITE
  }

  public record Part(
      V a, V b, double width, double height, boolean pier, Material material, V frameA, V frameB,String model) {
    public Part(V a,V b,double width,double height,boolean pier,Material material,V frameA,V frameB){this(a,b,width,height,pier,material,frameA,frameB,"");}
    public Part(V a, V b, double width, double height, boolean pier, Material material) {
      this(a, b, width, height, pier, material, null, null);
    }

    public Part frames(V first, V last) {
      return new Part(a, b, width, height, pier, material, first, last,model);
    }

    public double verticalFrame() {return Math.max(frameA==null?0:Math.abs(frameA.y()),frameB==null?0:Math.abs(frameB.y()));}

    public double halfExtent() {
      return Math.max(
          width / 2,
          Math.max(
              frameA == null ? 0 : frameA.horizontalLength(),
              frameB == null ? 0 : frameB.horizontalLength()));
    }

    public Part(V a, V b, double width, double height, boolean pier) {
      this(a, b, width, height, pier, Material.DEFAULT);
    }

    public boolean luminous() {
      return material == Material.LAMP||material==Material.CB_SIGN&&model.equals("road_lighting_lamp");
    }

    public Part {
      if(model==null||model.length()>128)throw new IllegalArgumentException("无效的设施模型");
      if (!RoadGeometry.finite(a.x(), a.y(), a.z(), b.x(), b.y(), b.z(), width, height)
          || width <= 0
          || width > 8
          || height <= 0
          || height > MAX_DROP + 4) throw new IllegalArgumentException("无效的高架结构尺寸（"+material+"，宽="+width+"，高="+height+"）");
    }

    public List<V> base() {
      V side =
          pier || b.sub(a).horizontalLength() < 1e-8
              ? new V(width / 2, 0, 0)
              : b.sub(a).horizontalUnit().left().mul(width / 2);
      V aa = a, bb = b;
      if (pier) {
        aa = a.add(new V(0, 0, -width / 2));
        bb = b.add(new V(0, 0, width / 2));
      }
      V first = frameA == null ? side : frameA, last = frameB == null ? side : frameB;
      return List.of(aa.add(first), aa.sub(first), bb.sub(last), bb.add(last));
    }

    public List<RoadSurface.Face> faces() {
      if(material==Material.CB_NOISE)return RoadNoiseModel.faces(this,false);
      if(material==Material.CB_SIGN)return RoadSignCatalog.faces(this);
      if (RoadSignals.signal(this)) return RoadSignalModel.faces(this, false, false);
      if (RoadPoleModel.support(this)) return RoadPoleModel.faces(this);
      var base = base();
      var top = base.stream().map(v -> v.add(new V(0, height, 0))).toList();
      List<RoadSurface.Face> out = new ArrayList<>();
      int color =
          switch (material) {
            case DEFAULT -> pier ? 0xA5ABAC : 0xD2D8D9;
            case STEEL, GANTRY_FRAME -> 0xC3CDCF;
            case DARK_STEEL -> 0x697C82;
            case TUNNEL -> 0xC7CCC7;
            case SIGN_GREEN -> 0x14734A;
            case SIGN_BLUE, LAMP_BLUE -> 0x235994;
            case FLOWER_PINK -> 0xE783B2;case FLOWER_YELLOW -> 0xF0C73D;case FLOWER_WHITE -> 0xFFF8EB;
            case SIGN_WHITE -> 0xF4F3DC;
            case ARCH_STEEL -> 0xAC4E38;
            case CONCRETE -> 0xB9B9AD;
            case WALK_STONE_BRICKS,WALK_BRICKS,WALK_ANDESITE,WALK_DIORITE,WALK_GRANITE,WALK_SMOOTH_STONE -> 0xFFFFFF;
            case TACTILE -> 0xE9BE42;
            case CB_SIGN, CB_NOISE -> 0xFFFFFF;
            case SOIL -> 0x66543E;
            case OAK_LOG,BIRCH_LOG,SPRUCE_LOG,CHERRY_LOG,FLOWERS -> 0xFFFFFF;
            case OAK_LEAVES -> 0x6A9D46; case BIRCH_LEAVES -> 0x80A755; case SPRUCE_LEAVES -> 0x4C7455; case CHERRY_LEAVES -> 0xFFFFFF;
            case GREEN -> 0x73AA51;
            case LAMP -> 0xFFF1C7;
            case SIGNAL_MAIN, SIGNAL_RAMP, SIGNAL_VEHICLE, SIGNAL_PEDESTRIAN, SIGNAL_LEFT -> 0x181D20;
            case CB_POST, CB_ARM, CB_BASE -> 0xE7E7E7;
          };
      RoadSurface.Texture texture =
          switch (material) {
            case DEFAULT, CONCRETE, TUNNEL -> RoadSurface.Texture.CONCRETE;
            case STEEL, DARK_STEEL, ARCH_STEEL, GANTRY_FRAME -> RoadSurface.Texture.METAL;
            case CB_POST, CB_ARM, CB_BASE -> RoadSurface.Texture.METAL;
            case SOIL -> RoadSurface.Texture.SOIL;
            case OAK_LOG,BIRCH_LOG,SPRUCE_LOG,CHERRY_LOG,OAK_LEAVES,BIRCH_LEAVES,SPRUCE_LEAVES,CHERRY_LEAVES,FLOWERS -> RoadSurface.Texture.valueOf(material.name());
            case GREEN -> RoadSurface.Texture.LEAVES;
            case WALK_STONE_BRICKS,WALK_BRICKS,WALK_ANDESITE,WALK_DIORITE,WALK_GRANITE,WALK_SMOOTH_STONE -> RoadSurface.Texture.valueOf(material.name());
            case TACTILE -> RoadSurface.Texture.PLAIN;
            case CB_SIGN -> RoadSurface.Texture.CB_SIGNS;
            case CB_NOISE -> RoadSurface.Texture.CB_NOISE;
            case FLOWER_PINK,FLOWER_YELLOW,FLOWER_WHITE,SIGN_GREEN, SIGN_BLUE, LAMP_BLUE, SIGN_WHITE, LAMP, SIGNAL_MAIN, SIGNAL_RAMP, SIGNAL_VEHICLE, SIGNAL_PEDESTRIAN, SIGNAL_LEFT -> RoadSurface.Texture.PLAIN;
          };
      V center = a.add(b).mul(.5).add(new V(0, height / 2, 0));
      out.add(face(top, color, luminous(), texture, center));
      // No lower leaf face at the soil interface: both used to occupy exactly the same plane.
      if (material != Material.GREEN) out.add(face(base, color, luminous(), texture, center));
      for (int i = 0; i < 4; i++) {
        int j = (i + 1) % 4;
        out.add(
            face(
                List.of(base.get(i), base.get(j), top.get(j), top.get(i)),
                color,
                luminous(),
                texture,
                center));
      }
      return out;
    }

    private static RoadSurface.Face face(
        List<V> points, int color, boolean light, RoadSurface.Texture texture, V center) {
      V a = points.get(1).sub(points.get(0)), b = points.get(2).sub(points.get(0));
      V n =
          new V(
              a.y() * b.z() - a.z() * b.y(),
              a.z() * b.x() - a.x() * b.z(),
              a.x() * b.y() - a.y() * b.x());
      if (n.dot(points.get(0).sub(center)) < 0) {
        points = new ArrayList<>(points);
        Collections.reverse(points);
      }
      return new RoadSurface.Face(points, color, light, texture);
    }
  }

  public interface Ground {
    /** Highest solid terrain below the deck; NaN means no valid foundation in range. */
    double top(double x, double z, double deckY);

    /** True when a proposed volume would occupy another road's driving corridor. */
    boolean blocked(Part part);

    /** An overlapping deck at the same height: omit its internal guardrail. */
    boolean joined(V point);

    /** Fine boundary clipping is separate from broad furniture openings. Legacy callers
     * retain their old policy; the lane-connector planner supplies exact material clips. */
    default V railJoint(V position,V direction){return null;}
    default boolean railPost(V position){return true;}
    default V railJoint(V position,V direction,boolean highway,boolean raised){return railJoint(position,direction);}
    default boolean railPost(V position,boolean highway,boolean raised){return railPost(position);}
    default List<RoadRailJoin.Span> railSpans(V a,V b,V outside) {
      return joined(outside)?List.of():List.of(new RoadRailJoin.Span(a,b));
    }

    /** Leave the node's editing target clear of raised median furniture. */
    default boolean marker(V point) {
      return false;
    }

    default boolean furnitureClear(V point) { return false; }
  }

  public static List<Part> plan(Mesh mesh, Ground ground) {
    return plan(mesh, ground, RoadFurniture.Phase.DEFAULT);
  }

  public static List<Part> plan(Mesh mesh, Ground ground, RoadFurniture.Phase phase) {
    if(mesh.settings().structure()==Structure.TUNNEL)return RoadInfrastructure.plan(mesh,ground);
    mesh=RoadStreetscape.resolve(mesh,ground);
    boolean modern = RoadProfile.modern(mesh.settings().style());
    boolean highway =
        RoadProfile.highway(mesh.settings().style());
    if (!modern && mesh.settings().structure() == Structure.GROUND) return List.of();
    List<Part> out = new ArrayList<>();
    // Accumulate each continuous exposed edge before creating rail parts. Tiny AUTO islands
    // at coincident fan roots are overlap artifacts, not separate barrier installations.
    for(int side:new int[]{-1,1}) {
      var run=new ArrayList<RailSpan>();
      for(double d=0;d<mesh.length()-1e-6;d+=.5) {
        Sample a=sample(mesh,d),b=sample(mesh,Math.min(mesh.length(),d+.5));
        V aa=a.at(side*(a.halfWidth()-RoadRailJoin.INSET),0),bb=b.at(side*(b.halfWidth()-RoadRailJoin.INSET),0),mid=aa.add(bb).mul(.5);
        boolean raised=mesh.settings().structure()==Structure.BRIDGE;
        for(V point:List.of(aa,mid,bb))raised|=elevated(mesh,point,ground.top(point.x(),point.z(),point.y()),ground);
        V sum=a.left().add(b.left());V normal=sum.horizontalLength()<1e-7?a.left():sum.horizontalUnit();
        V outside=mid.add(normal.mul(side*(modern?.40:.14)));
        boolean visible=bb.sub(aa).horizontalLength()>1e-7
            && mesh.settings().options().outerRail()!=RoadProfile.OuterRail.OFF
            && (mesh.settings().options().outerRail()==RoadProfile.OuterRail.ON || highway
                ||modern&&mesh.settings().style().ramp() ||mesh.settings().structure()==Structure.BRIDGE
                ||mesh.settings().structure()!=Structure.GROUND&&raised);
        var spans=visible?ground.railSpans(aa,bb,outside):List.<RoadRailJoin.Span>of();
        if(spans.isEmpty()){emitRailRun(out,run,mesh,ground,modern,highway,side);run.clear();}
        for(var span:spans){
          if(!run.isEmpty()&&run.get(run.size()-1).b().distance(span.a())>1e-5){emitRailRun(out,run,mesh,ground,modern,highway,side);run.clear();}
          double fraction=aa.sub(bb).horizontalLength()<1e-9?0:aa.sub(span.a()).horizontalLength()/aa.sub(bb).horizontalLength();
          double endFraction=aa.sub(bb).horizontalLength()<1e-9?1:aa.sub(span.b()).horizontalLength()/aa.sub(bb).horizontalLength();
          run.add(new RailSpan(span.a(),span.b(),raised,d+fraction*(b.distance()-a.distance()),d+endFraction*(b.distance()-a.distance())));
        }
        if(!spans.isEmpty()&&spans.get(spans.size()-1).b().distance(bb)>1e-5){emitRailRun(out,run,mesh,ground,modern,highway,side);run.clear();}
      }
      emitRailRun(out,run,mesh,ground,modern,highway,side);
    }
    if (modern) { furniture(mesh, ground, out, phase); terminalPosts(out,ground); }
    out.addAll(RoadInfrastructure.plan(mesh,ground));
    out.addAll(LaneClosureLandscape.plan(mesh,ground));

    if (RoadInfrastructure.customBridge(mesh)){out.addAll(RoadSidewalks.parts(mesh,mesh.settings().options().sidewalk(),ground,out).stream().filter(p->!ground.blocked(p)).toList());return List.copyOf(out);}
    out.addAll(edgeSlabs(mesh,ground));
    out.addAll(supports(mesh,ground,phase));
    out.addAll(RoadSidewalks.parts(mesh,mesh.settings().options().sidewalk(),ground,out).stream().filter(p->!ground.blocked(p)).toList());
    return List.copyOf(out);
  }

  /** Continuous concrete fascia for ordinary elevated road slabs. */
  public static List<Part> edgeSlabs(Mesh mesh,Ground ground){
    var out=new ArrayList<Part>();double depth=mesh.settings().thickness();
    for(int i=1;i<mesh.samples().size();i++){
      var a=mesh.samples().get(i-1);var b=mesh.samples().get(i);var mid=sample(mesh,(a.distance()+b.distance())/2);
      if(!RoadStreetscape.raised(mesh,mid))continue;
      for(int side:new int[]{-1,1}){
        double ra=.35*RoadInfrastructure.endTaper(mesh,a),rb=.35*RoadInfrastructure.endTaper(mesh,b);
        V first=a.at(side*(a.halfWidth()+(ra-.05)/2),depth),last=b.at(side*(b.halfWidth()+(rb-.05)/2),depth);
        var part=new Part(first,last,Math.max(ra,rb)+.05,depth,false,Material.CONCRETE).frames(a.left().mul((ra+.05)/2),b.left().mul((rb+.05)/2));
        if(!ground.joined(mid.at(side*(mid.halfWidth()+.2),0))&&!ground.blocked(part))out.add(part);
      }
    }return List.copyOf(out);
  }

  /** Shared by ordinary roads and elevated junction surfaces. */
  public static List<Part> supports(Mesh mesh,Ground ground,RoadFurniture.Phase phase){
    var out=new ArrayList<Part>();
    double previous = -100;
    for (double target = Math.min(phase.first(12, PIER_SPACING),mesh.length()/2); target < mesh.length() - 1e-6;
        target += PIER_SPACING) {
      // Local clearance detours never alter the phase of subsequent spans.
      for (double shift : new double[] {0, -4, 4, -8, 8}) {
        double d = target + shift;
        if (d < 0 || d >= mesh.length() || d - previous < 10) continue;
        Sample s = sample(mesh, d);
        var support=mesh.settings().style().ramp()&&!mesh.settings().style().connectorRamp()?RoadSupports.ramp(s,mesh.settings().thickness(),ground):RoadSupports.clearStandard(s,mesh.settings().thickness(),ground);
        if(support.isEmpty()||support.stream().anyMatch(ground::blocked))continue;
        out.addAll(support);
        previous = d;
        break;
      }
    }
    return List.copyOf(out);
  }

  private record RailSpan(V a,V b,boolean raised,double distance,double endDistance){}
  private static void emitRailRun(List<Part> out,List<RailSpan> run,Mesh mesh,Ground ground,boolean modern,boolean highway,int side){
    if(run.isEmpty())return;
    double length=run.stream().mapToDouble(r->r.a.distance(r.b)).sum();
    if(modern && mesh.settings().options().lanePoints().link()==null && mesh.settings().options().lanePoints().openings().isEmpty() && mesh.length()>8 && length<3
        && mesh.settings().options().outerRail()==RoadProfile.OuterRail.AUTO)return;
    if(modern&&(!mesh.settings().style().ramp()||mesh.settings().style().connectorRamp())&&mesh.settings().options().outerRail().sound(side)){
      var ordinary=new ArrayList<RailSpan>();
      for(int i=0;i<run.size();){var first=run.get(i);
        if(!first.raised()&&mesh.settings().structure()!=Structure.BRIDGE){ordinary.add(first);i++;continue;}
        int end=i;double panelLength=first.a().distance(first.b());
        while(end+1<run.size()&&panelLength<1.999&&run.get(end).b().distance(run.get(end+1).a())<1e-6&&(run.get(end+1).raised()||mesh.settings().structure()==Structure.BRIDGE)){end++;panelLength+=run.get(end).a().distance(run.get(end).b());}
        // Original CB module is two metres long. Side sign mirrors its inward cap.
        var part=new Part(first.a(),run.get(end).b(),.7,RoadNoiseModel.HEIGHT,false,Material.CB_NOISE).frames(sample(mesh,first.distance()).left().mul(side*.35),sample(mesh,run.get(end).endDistance()).left().mul(side*.35));
        var assembly=RoadNoiseModel.assembly(part);
        // Validate and keep/remove the footing and panel as one unit, never half a wall.
        if(assembly.stream().noneMatch(ground::blocked))out.addAll(assembly);else for(int j=i;j<=end;j++)ordinary.add(run.get(j));i=end+1;
      }
      run=ordinary;
    }
    for(var r:run) {
      if(modern){
        var pieces=new ArrayList<Part>();barrier(pieces,r.a,r.b,highway,r.raised,r.distance);
        V direction=r.b.sub(r.a),first=ground.railJoint(r.a,direction,highway,r.raised),last=ground.railJoint(r.b,direction,highway,r.raised);
        for(var piece:pieces){
          if(piece.material()==Material.DARK_STEEL&&!ground.railPost(r.a,highway,r.raised))continue;
          if(piece.a().sub(r.a).horizontalLength()<1e-6&&piece.b().sub(r.b).horizontalLength()<1e-6)
            piece=piece.frames(first==null?piece.frameA():first.mul(piece.width()/2),last==null?piece.frameB():last.mul(piece.width()/2));
          add(out,piece);
        }
      }
      else {var part=new Part(r.a,r.b,.24,1.05,false);if(!ground.blocked(part))add(out,part);}
    }
  }

  /** A marker hole is harmless; a one-block construction spine is not a supported deck. */
  public static boolean elevated(Mesh mesh,V point,double top,Ground ground) {
    double threshold=Math.max(.5,mesh.settings().thickness()+.125);
    int unsupported=0;
    for(int x=-1;x<=1;x++)for(int z=-1;z<=1;z++)if(x!=0||z!=0) {
      double near=ground.top(point.x()+x*.65,point.z()+z*.65,point.y());
      if(Double.isNaN(near)||point.y()-near>threshold)unsupported++;
    }
    return unsupported>=5;
  }

  /** Classify the usable cross section, independently of blocks used to lay out its axis. */
  public static boolean elevated(Mesh mesh,Sample at,Ground ground){
    if(mesh.settings().structure()==Structure.BRIDGE)return true;
    if(mesh.settings().structure()==Structure.GROUND||mesh.settings().structure()==Structure.TUNNEL)return false;
    // Longitudinal voting filters marker holes. Inspect each half separately: a bank
    // under the opposite carriageway must not classify an overhanging deck as ground.
    double threshold=Math.max(.5,mesh.settings().thickness()+.125);int raisedRows=0;
    for(double step:new double[]{-.8,0,.8}){
      var sample=sample(mesh,Math.max(0,Math.min(mesh.length(),at.distance()+step)));boolean raised=false;
      for(int side:new int[]{-1,1}){int missing=0;for(double fraction:new double[]{.35,.65,.9}){
        V p=sample.at(side*sample.halfWidth()*fraction,0);double top=ground.top(p.x(),p.z(),p.y());
        if(!Double.isFinite(top)||p.y()-top>threshold)missing++;
      }if(missing>=2)raised=true;}if(raised)raisedRows++;
    }
    return raisedRows>=2;
  }

  /** The entire shaft plus its vehicle margin must fit within an uninterrupted lower median. */
  public static boolean fitsMedian(Part pier, Mesh lower, List<Mesh> neighbors) {
    if (!pier.pier() || !RoadProfile.modern(lower.settings().style())
        || !RoadProfile.catalog(lower.settings().style()).twoWay()) return false;
    double margin = pier.height()<=.4?.04:.10;
    for (V p : pier.base()) {
      var q = RoadQueries.horizontal(lower, p);
      var s = q.sample();
      if (s.distance() < 2 || s.distance() > lower.length() - 2
          || Math.abs(q.lateral()-RoadProfile.layout(lower,s).medianCenter()) + margin > RoadProfile.layout(lower, s).median() / 2)
        return false;
      for (var road : neighbors) {
        if (road == lower || road.settings().style() != Style.UNMARKED) continue;
        if (RoadQueries.contains(road, s.at(RoadProfile.layout(lower,s).medianCenter(),0), 2, .2)) return false;
      }
    }
    return true;
  }

  /** Furniture is baked once together with the collision; never generated in the render loop. */
  private static void furniture(Mesh mesh, Ground ground, List<Part> out, RoadFurniture.Phase phase) {
    var profile = RoadProfile.catalog(mesh.settings().style());
    boolean highway = RoadProfile.highway(mesh.settings().style());
    for (double d = 0; d < mesh.length() - 1e-6; d += 2) {
      Sample a = sample(mesh, d), b = sample(mesh, Math.min(mesh.length(), d + 2));
      var la = RoadProfile.layout(mesh, a);
      var lb = RoadProfile.layout(mesh, b);
      if (profile.twoWay())
        for (double[] span : medianSpans(mesh, ground, d, Math.min(mesh.length(), d + 2)))
          median(out, mesh, ground, sample(mesh, span[0]), sample(mesh, span[1]), highway, span[0]);
      for (int side : la.outsideSides()) {
        boolean raised=bridgeAt(mesh,sample(mesh,Math.min(mesh.length(),d+1)),ground);
        double separation=RoadStreetscape.separatorWidth(mesh.settings().options());
        if (la.cycleWidth() > 0 && (mesh.settings().options().cycleRail()||raised&&separation>0)) {
          V aa = a.at(la.outer(side)+side*separation/2, 0), bb = b.at(lb.outer(side)+side*separation/2, 0);
          if (!ground.joined(aa.add(bb).mul(.5))) barrier(out, aa, bb, false, false, d);
        }
        if(!raised&&separation>0&&la.cycleWidth()>0&&lb.cycleWidth()>0){
          V aa=a.at(la.outer(side)+side*separation/2,0),bb=b.at(lb.outer(side)+side*separation/2,0);
          if(!ground.joined(aa.add(bb).mul(.5))&&!ground.furnitureClear(aa.add(bb).mul(.5))){
            out.add(new Part(aa,bb,separation,.3,false,Material.CONCRETE).frames(a.left().mul(separation/2),b.left().mul(separation/2)));
            out.add(new Part(aa.add(new V(0,.3,0)),bb.add(new V(0,.3,0)),separation-.24,.15,false,Material.SOIL).frames(a.left().mul((separation-.24)/2),b.left().mul((separation-.24)/2)));
            out.add(new Part(aa.add(new V(0,.45,0)),bb.add(new V(0,.45,0)),separation-.3,.4,false,Material.GREEN).frames(a.left().mul((separation-.3)/2),b.left().mul((separation-.3)/2)));
          }
        }
        if (la.curbWidth() > 0 || lb.curbWidth() > 0) {
          for(double[] span:openSpans(d,Math.min(mesh.length(),d+2),station->{
            Sample at=sample(mesh,station);
            return !bridgeAt(mesh,at,ground) && !ground.joined(at.at(side*(at.halfWidth()+.02),0));
          })) {
            Sample first=sample(mesh,span[0]),last=sample(mesh,span[1]);
            var fl=RoadProfile.layout(mesh,first);var ll=RoadProfile.layout(mesh,last);
            double ca=RoadProfile.curbExtent(fl,first,side);
            double cb=RoadProfile.curbExtent(ll,last,side);
            if(Math.max(ca,cb)<.001)continue;
            add(out,new Part(first.at(side*(first.halfWidth()-ca/2),0),last.at(side*(last.halfWidth()-cb/2),0),
                Math.max(ca,cb),.2,false,Material.CONCRETE).frames(first.left().mul(ca/2),last.left().mul(cb/2)));
          }
        }
      }
    }
    var amenities=RoadStreetscape.plan(mesh,ground,phase);
    var lampBases=amenities.stream().filter(p->p.material()==Material.CONCRETE&&p.width()==.38&&p.height()==.16).toList();
    trimHedges(out,lampBases);
    out.addAll(amenities);
  }

  /** Subtract only the pedestal footprint, retaining both sides of the planted strip. */
  public static void trimHedges(List<Part> out,List<Part> bases){
    var result=new ArrayList<Part>();
    for(var p:out){if(p.material()!=Material.GREEN){result.add(p);continue;}
      List<List<V>> polygons=List.of(p.base());boolean changed=false;
      for(var b:bases){double before=polygons.stream().mapToDouble(RoadSurface::area).sum();var next=new ArrayList<List<V>>();for(var poly:polygons)next.addAll(RoadSurface.subtract(poly,b.base()));
        if(before-next.stream().mapToDouble(RoadSurface::area).sum()>1e-8){changed=true;polygons=next;}}
      if(!changed){result.add(p);continue;}
      for(var poly:polygons)for(int i=1;i+1<poly.size();i++){V a=poly.get(0),b=poly.get(i),c=poly.get(i+1);if(RoadSurface.area(List.of(a,b,c))<1e-8)continue;
        result.add(new Part(a.add(b).mul(.5),c,Math.max(.001,Math.min(8,a.distance(b))),p.height(),false,p.material()).frames(a.sub(b).mul(.5),new V(0,0,0)));}
    }out.clear();out.addAll(result);
  }
  private static boolean bridgeAt(Mesh mesh,Sample at,Ground ground) {
    return mesh.settings().structure()==Structure.BRIDGE || mesh.settings().structure()!=Structure.GROUND
        && elevated(mesh,at,ground);
  }

  private static boolean medianOpen(Mesh mesh, Ground ground, double distance) {
    var at=sample(mesh,distance);
    V center=at.at(RoadProfile.layout(mesh,at).medianCenter(),0);
    return !ground.joined(center) && !ground.furnitureClear(center);
  }

  /** Trim furniture at the actual stop/pad boundary instead of dropping a whole 2m tile. */
  private static List<double[]> medianSpans(Mesh mesh, Ground ground, double from, double to) {
    return openSpans(from,to,d->medianOpen(mesh,ground,d));
  }

  private static List<double[]> openSpans(double from,double to,java.util.function.DoublePredicate visible) {
    List<double[]> spans = new ArrayList<>();
    double previous = from, begin = from;
    boolean open = visible.test(from);
    int steps = (int)Math.ceil((to - from) / .5);
    for (int i = 1; i <= steps; i++) {
      double next = from + (to - from) * i / steps;
      boolean after = visible.test(next);
      if (after != open) {
        double lo = previous, hi = next;
        for (int j = 0; j < 16; j++) {
          double mid = (lo + hi) / 2;
          if (visible.test(mid) == open) lo = mid; else hi = mid;
        }
        double boundary = (lo + hi) / 2;
        if (open && boundary - begin > 1e-5) spans.add(new double[] {begin, boundary});
        else begin = boundary;
        open = after;
      }
      previous = next;
    }
    if (open && to - begin > 1e-5) spans.add(new double[] {begin, to});
    return spans;
  }

  /** Tunnel and open-road medians use the same actual transition profile. Do not
   * replace tunnel medians by a fixed narrow concrete bar or omit its last 2m tile. */
  public static void medianFurniture(Mesh mesh,Ground ground,List<Part> out) {
    var catalog=RoadProfile.catalog(mesh.settings().style());if(!catalog.twoWay())return;
    for(double d=0;d<mesh.length()-1e-6;d+=2)
      for(double[] span:medianSpans(mesh,ground,d,Math.min(mesh.length(),d+2)))
        median(out,mesh,ground,sample(mesh,span[0]),sample(mesh,span[1]),catalog.type()==RoadProfile.Type.HIGHWAY,span[0]);
  }

  private static void median(List<Part> out, Mesh mesh, Ground ground,
      Sample a, Sample b, boolean highway, double d) {
    var la = RoadProfile.layout(mesh, a);
    var lb = RoadProfile.layout(mesh, b);
    boolean raised = mesh.settings().structure()!=Structure.TUNNEL&&bridgeAt(mesh,sample(mesh,(a.distance()+b.distance())/2),ground);
    double green = Math.min(RoadTransitions.green(mesh, a), RoadTransitions.green(mesh, b));
    double terrainBlend = raised || mesh.settings().structure()==Structure.TUNNEL ? 0 : 1;
    green *= terrainBlend;
    double median = Math.min(la.median(), lb.median());
    if (median >= .35 && (green < .12 || median <= .5)) {
      barrier(out, a.at(la.medianCenter(),0), b.at(lb.medianCenter(),0), highway, raised, d);
    }
    if (green >= .12 && median > .5) {
      double soilWidth = Math.max(.08, (median - .3) * green);
      add(
          out,
          new Part(a.at(la.medianCenter(),0), b.at(lb.medianCenter(),0), soilWidth, .3, false, Material.SOIL)
              .frames(
                  a.left().mul(Math.max(.04, (la.median() - .3) * green / 2)),
                  b.left().mul(Math.max(.04, (lb.median() - .3) * green / 2))));
      add(
          out,
          new Part(
              a.at(la.medianCenter(),0).add(new V(0, .3, 0)),
              b.at(lb.medianCenter(),0).add(new V(0, .3, 0)),
              Math.max(.06, (median - .5) * green),
              .55 * green,
              false,
              Material.GREEN));
      add(
          out,
          new Part(
              a.at(la.medianCenter(),0).add(new V(0, .3 + .52 * green, 0)),
              b.at(lb.medianCenter(),0).add(new V(0, .3 + .52 * green, 0)),
              Math.max(.04, (median - .85) * green),
              .25 * green,
              false,
              Material.GREEN));
      for (int side : new int[] {-1, 1})
        add(
            out,
            new Part(
                a.at(la.medianCenter()+side * (soilWidth / 2 + .1), 0),
                b.at(lb.medianCenter()+side * (soilWidth / 2 + .1), 0),
                .2,
                .35,
                false,
                Material.CONCRETE));
    }
  }

  /** Tapered pole, collar, swept arm, housing and recessed LED lens. */
  private static List<Part> lamp(V foot, V along, V inward, boolean bridge) {
    List<Part> parts = new ArrayList<>();
    double height = bridge ? 5.9 : 7, lower = bridge ? 2.6 : 3.4;
    double armLength = bridge ? 1.35 : 2.2;
    parts.add(postPart(foot, along, .4, .18, Material.CONCRETE));
    parts.add(postPart(foot.add(new V(0, .16, 0)), along, .29, .12, Material.DARK_STEEL));
    parts.add(postPart(foot.add(new V(0, .26, 0)), along, .19, lower, Material.DARK_STEEL));
    parts.add(
        postPart(
            foot.add(new V(0, lower + .24, 0)), along, .13, height - lower - .24, Material.STEEL));
    parts.add(postPart(foot.add(new V(0, lower + .21, 0)), along, .25, .13, Material.DARK_STEEL));
    V root = foot.add(new V(0, height - .3, 0));
    V bend = root.add(inward.mul(armLength * .55)).add(new V(0, bridge ? .15 : .35, 0));
    V head = root.add(inward.mul(armLength)).add(new V(0, bridge ? .15 : .35, 0));
    parts.add(new Part(root, bend, .12, .10, false, Material.STEEL));
    parts.add(new Part(bend, head, .12, .10, false, Material.STEEL));
    parts.add(
        new Part(
            head.sub(inward.mul(.36)),
            head.add(inward.mul(.55)),
            .46,
            .16,
            false,
            Material.DARK_STEEL));
    parts.add(
        new Part(
            head.sub(inward.mul(.26)).add(new V(0, -.04, 0)),
            head.add(inward.mul(.45)).add(new V(0, -.04, 0)),
            .34,
            .06,
            false,
            Material.LAMP));
    return parts;
  }

  private static Part postPart(V center, V along, double width, double height, Material material) {
    return new Part(
        center.sub(along.mul(width / 2)),
        center.add(along.mul(width / 2)),
        width,
        height,
        false,
        material);
  }

  /** Four distinct assemblies: urban rail, urban bridge rail, highway beam, highway parapet. */
  public static void barrier(
      List<Part> out, V a, V b, boolean highway, boolean raised, double distance) {
    if (highway && raised) {
      add(out, new Part(a, b, .62, .8, false, Material.CONCRETE));
      add(
          out,
          new Part(
              a.add(new V(0, .8, 0)), b.add(new V(0, .8, 0)), .36, .35, false, Material.CONCRETE));
    } else if (raised) {
      add(out, new Part(a, b, .42, .45, false, Material.CONCRETE));
      for (double y : new double[] {.65, 1.05})
        add(
            out,
            new Part(
                a.add(new V(0, y, 0)), b.add(new V(0, y, 0)), .12, .12, false, Material.STEEL));
      if (((int) Math.floor(distance * 2)) % 4 == 0) post(out, a, b, .45, 1.15);
    } else if (highway) {
      for (double y : new double[] {.58, .8})
        add(
            out,
            new Part(a.add(new V(0, y, 0)), b.add(new V(0, y, 0)), .3, .18, false, Material.STEEL));
      if (((int) Math.floor(distance * 2)) % 4 == 0) post(out, a, b, 0, .94);
    } else {
      for (double y : new double[] {.38, .95})
        add(
            out,
            new Part(a.add(new V(0, y, 0)), b.add(new V(0, y, 0)), .1, .1, false, Material.STEEL));
      if (((int) Math.floor(distance * 2)) % 2 == 0) post(out, a, b, 0, 1.05);
    }
  }

  private record RailEndKey(long x,long y,long z,double width,double height) {
    RailEndKey(V p,Part part){this(Math.round(p.x()*100000),Math.round(p.y()*100000),Math.round(p.z()*100000),part.width(),part.height());}
  }
  private record RailEnd(V p,V into,Part part) {}
  public static void terminalPosts(List<Part> parts) {terminalPosts(parts,null);}
  private static void terminalPosts(List<Part> parts,Ground ground) {
    var ends=new LinkedHashMap<RailEndKey,List<RailEnd>>();
    var lower=new HashSet<RailEndKey>();
    for(Part p:parts)if(p.material()==Material.STEEL&&
        (p.width()==.1&&p.height()==.1||p.width()==.12&&p.height()==.12||p.width()==.3&&p.height()==.18)) {
      V dir=p.b().sub(p.a()).horizontalUnit();
      ends.computeIfAbsent(new RailEndKey(p.a(),p),k->new ArrayList<>()).add(new RailEnd(p.a(),dir,p));
      ends.computeIfAbsent(new RailEndKey(p.b(),p),k->new ArrayList<>()).add(new RailEnd(p.b(),dir.mul(-1),p));
      lower.add(new RailEndKey(p.a(),p));lower.add(new RailEndKey(p.b(),p));
    }
    var posts=new HashSet<String>();
    for(Part p:parts)if(p.material()==Material.DARK_STEEL&&p.width()==.16)posts.add(postKey(p.a()));
    for(var group:ends.values())if(group.size()==1){
      RailEnd e=group.get(0);double separation=e.part().width()==.1?.57:e.part().width()==.12?.4:.22;
      if(!lower.contains(new RailEndKey(e.p().add(new V(0,-separation,0)),e.part())))continue;
      double top=e.part().width()==.1?1.05:e.part().width()==.12?1.15:.94;
      double railY=e.part().width()==.1?.95:e.part().width()==.12?1.05:.8;
      double bottom=e.part().width()==.12?.45:0;
      V base=e.p().add(new V(0,-railY,0));
      if(ground!=null&&!ground.railPost(base,e.part().width()==.3,e.part().width()==.12))continue;
      if(posts.add(postKey(base.add(new V(0,bottom>0?bottom-.02:bottom,0)))))post(parts,base,base.add(e.into()),bottom,top);
    }
  }
  private static String postKey(V p){return Math.round(p.x()*100000)+":"+Math.round(p.y()*100000)+":"+Math.round(p.z()*100000);}

  private static void post(List<Part> out, V a, V b, double bottom, double top) {
    V dir = b.sub(a).horizontalUnit(), p = a.add(new V(0, bottom > 0 ? bottom - .02 : bottom, 0));
    add(
        out,
        new Part(
            p,
            p.add(dir.mul(.16)),
            .16,
            top - bottom + .03 + (bottom > 0 ? .02 : 0),
            false,
            Material.DARK_STEEL));
  }

  private static void add(List<Part> out, Part part) {
    for (int i = out.size() - 1; i >= Math.max(0, out.size() - 12); i--) {
      Part old = out.get(i);
      if (old.pier()
          || old.material() != part.material()
          || old.width() != part.width()
          || old.height() != part.height()
          || old.b().distance(part.a()) > 1e-6) continue;
      V before = old.b().sub(old.a()), after = part.b().sub(part.a());
      if (before.horizontalLength() < 1e-8 || after.horizontalLength() < 1e-8) continue;
      V da = before.horizontalUnit(), db = after.horizontalUnit();
      double dot = da.dot(db);
      if (dot > .99999999
          && old.a().distance(part.b()) <= 8
          && Math.abs(before.y() / before.horizontalLength() - after.y() / after.horizontalLength())
              < 1e-6) {
        out.set(
            i,
            new Part(
                old.a(),
                part.b(),
                old.width(),
                old.height(),
                false,
                old.material(),
                old.frameA(),
                part.frameB()));
        return;
      }
      if (dot > -.9 && old.frameB()==null && part.frameA()==null) {
        V joint = da.left().add(db.left()).mul(old.width() / 2 / (1 + dot));
        out.set(i, old.frames(old.frameA(), joint));
        part = part.frames(joint, part.frameB());
      }
      break;
    }
    out.add(part);
  }

  public static Sample sample(Mesh mesh, double distance) {
    var s = mesh.samples();
    int lo = 0, hi = s.size() - 1;
    distance = Math.max(0, Math.min(mesh.length(), distance));
    while (hi - lo > 1) {
      int mid = (lo + hi) / 2;
      if (s.get(mid).distance() < distance) lo = mid;
      else hi = mid;
    }
    Sample a = s.get(lo), b = s.get(hi);
    double span = b.distance() - a.distance();
    double t = span < 1e-9 ? 0 : (distance - a.distance()) / span;
    V direction = a.left().add(b.left().sub(a.left()).mul(t));
    if (direction.horizontalLength() < 1e-7) direction = a.left();
    return new Sample(
        a.center().add(b.center().sub(a.center()).mul(t)),
        direction.horizontalUnit(),
        distance,
        a.halfWidth() + (b.halfWidth() - a.halfWidth()) * t);
  }

  private RoadStructures() {}
}
