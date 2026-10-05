package com.sora.splineroads.core;

import com.sora.splineroads.core.RoadGeometry.*;
import com.sora.splineroads.core.InterchangePlanner.*;
import java.util.*;

/** Five/six-arm directional stack. Common departures split before crossing platforms;
 * common arrivals merge afterwards. No circulating/weaving carriageway or traffic lights. */
public final class LegacyMulti250 {
  private static final double STEP=1.5, MARGIN=.4;
  private final Node[] anchors;
  private final Settings[] settings;
  private final Options options;
  private final int n;
  private final V center;
  private final V[] radial;
  private final double[] lengths,levels;
  private final double gap;
  private final List<Mesh> mains=new ArrayList<>();
  private final List<Route> routes=new ArrayList<>();
  private static final class Route {
    final int from,to;final Mesh flat;final double sourceHeight,targetHeight;
    double holdStart,holdEnd,crossStart=Double.POSITIVE_INFINITY,crossEnd=-1;
    final Set<Integer> mainCross=new HashSet<>(),conflicts=new HashSet<>();
    int layer=-1;
    Route(int from,int to,Mesh flat,double h0,double h1){this.from=from;this.to=to;this.flat=flat;sourceHeight=h0;targetHeight=h1;holdEnd=flat.length();}
  }
  private record Hit(double distance,double gap,double height,double dot) {}
  /** Local segment index keeps fitting proportional to actual crossings, not every sample pair. */
  private static final class Grid {
    final Mesh mesh;final Map<Long,List<Integer>> cells=new HashMap<>();final int[] visited;int visit;
    Grid(Mesh m){mesh=m;visited=new int[m.samples().size()];for(int i=1;i<m.samples().size();i++){
      Sample a=m.samples().get(i-1),b=m.samples().get(i);double w=Math.max(a.halfWidth(),b.halfWidth())+MARGIN;
      int x0=cell(Math.min(a.center().x(),b.center().x())-w),x1=cell(Math.max(a.center().x(),b.center().x())+w);
      int z0=cell(Math.min(a.center().z(),b.center().z())-w),z1=cell(Math.max(a.center().z(),b.center().z())+w);
      for(int x=x0;x<=x1;x++)for(int z=z0;z<=z1;z++)cells.computeIfAbsent(key(x,z),v->new ArrayList<>()).add(i);
    }}
    static int cell(double v){return (int)Math.floor(v/16);}
    static long key(int x,int z){return (long)x<<32^(z&0xffffffffL);}
    Hit hit(Sample s){
      Hit best=null;double width=s.halfWidth()+MARGIN;int stamp=++visit;
      for(int x=cell(s.center().x()-width);x<=cell(s.center().x()+width);x++)for(int z=cell(s.center().z()-width);z<=cell(s.center().z()+width);z++)
        for(int i:cells.getOrDefault(key(x,z),List.of()))if(visited[i]!=stamp){visited[i]=stamp;
          Sample a=mesh.samples().get(i-1),b=mesh.samples().get(i);V d=b.center().sub(a.center());double ll=d.x()*d.x()+d.z()*d.z();if(ll<1e-10)continue;
          V q=s.center().sub(a.center());double t=Math.max(0,Math.min(1,(q.x()*d.x()+q.z()*d.z())/ll));
          V p=a.center().add(d.mul(t));double distance=p.sub(s.center()).horizontalLength();
          double separation=distance-(s.halfWidth()+a.halfWidth()*(1-t)+b.halfWidth()*t-.05);
          if(separation<0&&(best==null||separation<best.gap()))best=new Hit(a.distance()+(b.distance()-a.distance())*t,separation,p.y(),s.left().dot(a.left()));
        }
      return best;
    }
  }
  private LegacyMulti250(Node[] nodes,Settings[] main,Options options,double baseHeight){
    anchors=nodes.clone();n=nodes.length;settings=main.clone();this.options=options;
    if(n!=5&&n!=6||main.length!=3)throw new IllegalArgumentException("五、六向立交需要 AB、CD、E 或 EF 三组端点");
    for(var s:settings){s.validate();if(!RoadProfile.catalog(s.style()).twoWay()||s.style().ramp())throw new IllegalArgumentException("主路必须为双向普通道路或高速道路");}
    V a=nodes[0].position(),b=nodes[1].position(),c=nodes[2].position(),d=nodes[3].position();
    V ab=b.sub(a).horizontalUnit(),cd=d.sub(c).horizontalUnit();double det=cross(ab,cd);
    if(Math.abs(det)<.4)throw new IllegalArgumentException("AB 与 CD 夹角过小，请选择穿过同一中心的主路");
    V hit=a.add(ab.mul(cross(c.sub(a),cd)/det));
    gap=Math.ceil((options.clearance()+Arrays.stream(main).mapToDouble(Settings::thickness).max().orElse(1)+.5)*2)/2;
    double base=Arrays.stream(nodes).mapToDouble(v->v.position().y()).min().orElse(baseHeight);
    center=new V(hit.x(),base,hit.z());
    levels=new double[3];
    for(int axis=0;axis<3;axis++)levels[axis]=nodes[axis*2].position().y();
    // Preserve the user's vertical ordering, while retaining automatic clearance
    // fitting. Earlier axes win the lift tie: AB above CD above EF.
    double[] original=levels.clone();Integer[] order={0,1,2};
    Arrays.sort(order,Comparator.<Integer>comparingDouble(i->original[i]).thenComparing(Comparator.reverseOrder()));
    for(int i=1;i<3;i++)levels[order[i]]=Math.max(original[order[i]],levels[order[i-1]]+gap);
    if(options.adjustEndpoints())for(int i=0;i<n;i++) {
      double rise=levels[i/2]-original[i/2];V p=nodes[i].position();
      anchors[i]=new Node(new V(p.x(),p.y()+rise,p.z()),nodes[i].yaw(),nodes[i].grade());
    }
    radial=new V[n];lengths=new double[n];
    for(int i=0;i<n;i++){
      V delta=nodes[i].position().sub(center);lengths[i]=delta.horizontalLength();
      if(!RoadGeometry.finite(delta.x(),delta.y(),delta.z())||lengths[i]<70-1e-6||lengths[i]>1024+1e-6)throw new IllegalArgumentException("每个端点离中心需为 70–1024 格");
      radial[i]=delta.horizontalUnit();
      if(i%2==1&&radial[i].dot(radial[i-1])>-.9999)throw new IllegalArgumentException("AB、CD、EF 各自需穿过同一个中心且方向相反");
    }
    for(int i=0;i<n;i++)for(int j=i+1;j<n;j++)if(radial[i].dot(radial[j])>Math.cos(Math.toRadians(25)))throw new IllegalArgumentException("相邻方向夹角至少 25°");
  }
  public static Plan plan(Node[] nodes,Settings[] settings,Options options){return plan(nodes,settings,options,Arrays.stream(nodes).mapToDouble(v->v.position().y()).min().orElseThrow());}
  public static Plan plan(Node[] nodes,Settings[] settings,Options options,double baseHeight){
    if(!InterchangePlanner.presets(nodes.length).contains(options.preset()))throw new IllegalArgumentException("请选择对应向数的全定向立交");
    IllegalArgumentException failure;Plan initial=null;
    try{initial=new LegacyMulti250(nodes,settings,options,baseHeight).build();if(!options.allowShrink())return initial;failure=new IllegalArgumentException("收紧占地");}
    catch(IllegalArgumentException e){failure=e;if(!options.adjustEndpoints())throw e;}
    V a=nodes[0].position(),u=nodes[1].position().sub(a).horizontalUnit(),b=nodes[2].position(),v=nodes[3].position().sub(b).horizontalUnit();
    double det=cross(u,v);if(Math.abs(det)<.4)throw failure;V center=a.add(u.mul(cross(b.sub(a),v)/det));
    double shortest=Arrays.stream(nodes).mapToDouble(p->p.position().sub(center).horizontalLength()).min().orElse(70);
    double failed=options.allowShrink()?70:Math.max(70,shortest),radius=failed;Plan best=null;
    for(int attempt=0;attempt<17&&radius<1024;attempt++){
      radius=Math.min(1024,Math.ceil(Math.max(radius+24,radius*1.24)/4)*4);
      try{best=new LegacyMulti250(extend(nodes,center,radius,options.allowShrink()),settings,options,baseHeight).build();break;}
      catch(IllegalArgumentException e){failed=radius;failure=e;}
    }
    if(best==null){if(initial!=null)return initial;throw new IllegalArgumentException("五/六向布局无法满足净空、坡度或半径："+failure.getMessage());}
    for(int k=0;k<6&&radius-failed>8;k++){
      double middle=Math.ceil((radius+failed)/8)*4;
      try{Plan p=new LegacyMulti250(extend(nodes,center,middle,options.allowShrink()),settings,options,baseHeight).build();best=p;radius=middle;}
      catch(IllegalArgumentException e){failed=middle;}
    }
    if(initial!=null&&extent(best)>=extent(initial)-.5)return initial;
    return best;
  }
  private static double extent(Plan plan){return plan.anchors().stream().mapToDouble(p->p.position().sub(plan.center()).horizontalLength()).max().orElseThrow();}
  private static Node[] extend(Node[] nodes,V center,double length,boolean shrink){
    Node[] result=nodes.clone();for(int i=0;i<nodes.length;i++){
      V delta=nodes[i].position().sub(center),p=center.add(delta.horizontalUnit().mul(shrink?length:Math.max(length,delta.horizontalLength())));
      result[i]=new Node(new V(p.x(),nodes[i].position().y(),p.z()),nodes[i].yaw(),nodes[i].grade());
    }return result;
  }
  private static double cross(V a,V b){return a.x()*b.z()-a.z()*b.x();}
  private static V cubic(V a,V b,V c,V d,double t){double u=1-t;return a.mul(u*u*u).add(b.mul(3*u*u*t)).add(c.mul(3*u*t*t)).add(d.mul(t*t*t));}
  private Settings mainSettings(int axis){return settings[axis].structure(settings[axis].structure()==Structure.GROUND && Math.abs(levels[axis]-center.y())<.001 ? Structure.GROUND : Structure.AUTO).options(settings[axis].options().traffic(options.leftTraffic()).ends(RoadTransitions.Ends.NONE).route(settings[axis].options().routing().fit(false)));}
  private Plan build(){
    var legs=new ArrayList<Leg>();
    for(int axis=0;axis<3;axis++){
      int first=axis*2,second=first+1;V from=anchors[first].position();V to=second<n?anchors[second].position():center.add(radial[first].mul(lengths[first]*.86));
      double length=to.sub(from).horizontalLength();int steps=(int)Math.ceil(length/STEP);List<Sample> samples=new ArrayList<>();Settings main=mainSettings(axis);
      if(axis==2&&n==5){
        var end=new RoadTransitions.Section(options.lanes()==1?Style.O2_RAIL:Style.O4_RAIL,2*options.width()+.5,false,false,false,main.options().outerRail());
        main=RoadTransitions.ends(main,null,end).options(main.options().ends(new RoadTransitions.Ends(null,end,true)));
      }
      for(int k=0;k<=steps;k++){
        double t=(double)k/steps;V p=from.add(to.sub(from).mul(t));double r=p.sub(center).horizontalLength();
        int end=p.sub(center).dot(radial[first])>=0?first:second;
        double y=levels[axis];if(end<n){double blend=smooth((lengths[end]-r-4)/(lengths[end]*.13-4));y=anchors[end].position().y()+(levels[axis]-anchors[end].position().y())*blend;}
        samples.add(new Sample(new V(p.x(),y,p.z()),to.sub(from).horizontalUnit().left(),0,RoadTransitions.width(main,t*length,length)/2));
      }
      Mesh mesh=RoadRibbon.mesh(samples,main);if(RoadGrades.maximum(mesh)>.15+1e-7)throw new IllegalArgumentException("主路升降段超过 15% 纵坡；请允许调整端点或延长主路");mains.add(mesh);legs.add(new Leg(axis==2&&n==5?"E 支路":"主路 "+(axis+1),first,second<n?second:first,mesh));
    }
    for(int i=0;i<n;i++)for(int j=0;j<n;j++)if(i!=j&&i/2!=j/2)routes.add(route(i,j));
    var grids=mains.stream().map(Grid::new).toList();
    // Hold the grade at each actual common fork/merge; lift only after the decks separate.
    for(Route r:routes){
      r.holdStart=sharedEnd(r.flat,grids.get(r.from/2),true);
      r.holdEnd=sharedEnd(r.flat,grids.get(r.to/2),false);
    }
    var routeGrids=routes.stream().map(r->new Grid(r.flat)).toList();
    double[][] sharedFrom=new double[routes.size()][routes.size()],sharedTo=new double[routes.size()][routes.size()];
    for(int i=0;i<routes.size();i++)for(int j=0;j<routes.size();j++)if(i!=j){
      Route a=routes.get(i),b=routes.get(j);
      sharedFrom[i][j]=a.from==b.from?sharedEnd(a.flat,routeGrids.get(j),true):-1;
      sharedTo[i][j]=a.to==b.to?sharedEnd(a.flat,routeGrids.get(j),false):a.flat.length()+1;
      rHold(a,sharedFrom[i][j],sharedTo[i][j]);
    }
    for(int i=0;i<routes.size();i++){
      Route r=routes.get(i);
      for(Sample sample:r.flat.samples()){
        double d=sample.distance();
        for(int m=0;m<mains.size();m++){
          Hit hit=grids.get(m).hit(sample);if(hit==null)continue;
          if(m==r.from/2&&d<=r.holdStart+.01||m==r.to/2&&d>=r.holdEnd-.01)continue;
          crossing(r,d);for(int layer=0;layer<16;layer++)if(Math.abs(center.y()+layer*gap-levels[m])<options.clearance()+settings[m].thickness()+.4)r.mainCross.add(layer);
        }
        for(int j=0;j<routes.size();j++)if(j!=i){
          Hit hit=routeGrids.get(j).hit(sample);if(hit==null)continue;
          if(d<=sharedFrom[i][j]+.01||d>=sharedTo[i][j]-.01)continue;
          crossing(r,d);r.conflicts.add(j);routes.get(j).conflicts.add(i);
        }
      }
    }
    // Saturation coloring reuses levels between nonintersecting movements.
    for(int count=0;count<routes.size();count++){
      int best=-1,saturation=-1,degree=-1;
      for(int i=0;i<routes.size();i++)if(routes.get(i).layer<0){
        Route r=routes.get(i);Set<Integer> used=new HashSet<>(r.mainCross);for(int j:r.conflicts)if(routes.get(j).layer>=0)used.add(routes.get(j).layer);
        if(used.size()>saturation||used.size()==saturation&&r.conflicts.size()>degree){best=i;saturation=used.size();degree=r.conflicts.size();}
      }
      Route r=routes.get(best);Set<Integer> used=new HashSet<>(r.mainCross);for(int j:r.conflicts)if(routes.get(j).layer>=0)used.add(routes.get(j).layer);
      for(int layer=0;layer<16;layer++)if(!used.contains(layer)){r.layer=layer;break;}
    }
    double minRadius=Double.POSITIVE_INFINITY,highest=Arrays.stream(levels).max().orElseThrow();var elevated=new ArrayList<Mesh>();
    for(Route r:routes){
      Mesh mesh=elevate(r);RoadGrades.validate(mesh);RoadRibbon.checkSelfIntersections(mesh,options.clearance());double radius=minRadius(mesh);minRadius=Math.min(minRadius,radius);
      if(radius+1e-4<options.radius())throw new IllegalArgumentException(name(r)+" 转弯半径 "+(int)radius+"，需至少 "+(int)options.radius()+" 格；请延长端点");
      elevated.add(mesh);highest=Math.max(highest,mesh.max().y());
    }
    verify(elevated,sharedFrom,sharedTo);
    // Persist each physical collector only once. Movement paths may share a
    // prefix/suffix, but rendering, rail planning and collision must not duplicate it.
    Set<Edge> owned=new HashSet<>();
    for(int i=0;i<routes.size();i++) {
      Route r=routes.get(i);Mesh mesh=elevated.get(i);List<Sample> run=new ArrayList<>();
      for(int k=1;k<mesh.samples().size();k++) {
        Sample a=mesh.samples().get(k-1),b=mesh.samples().get(k);
        if(owned.add(new Edge(a,b))) {
          if(run.isEmpty())run.add(a);run.add(b);
        } else if(!run.isEmpty()){legs.add(new Leg(name(r),r.from,r.to,RoadRibbon.mesh(run,mesh.settings())));run=new ArrayList<>();}
      }
      if(!run.isEmpty())legs.add(new Leg(name(r),r.from,r.to,RoadRibbon.mesh(run,mesh.settings())));
    }
    return new Plan(List.copyOf(legs),routes.size(),minRadius,highest,center,List.of(anchors));
  }
  private record Edge(long ax,long ay,long az,long bx,long by,long bz,long aw,long bw) {
    Edge(Sample a,Sample b){this(q(a.center().x()),q(a.center().y()),q(a.center().z()),q(b.center().x()),q(b.center().y()),q(b.center().z()),q(a.halfWidth()),q(b.halfWidth()));}
    private static long q(double v){return Math.round(v*1e6);}
  }
  private static void rHold(Route a,double start,double end){a.holdStart=Math.max(a.holdStart,start);a.holdEnd=Math.min(a.holdEnd,end);}
  private static void crossing(Route r,double distance){r.crossStart=Math.min(r.crossStart,distance);r.crossEnd=Math.max(r.crossEnd,distance);}
  private static String name(Route r){return "定向 "+(char)('A'+r.from)+" → "+(char)('A'+r.to);}
  private Route route(int from,int to){
    double sign=options.leftTraffic()?-1:1;V in=radial[from].mul(-1),out=radial[to];
    var a=RoadProfile.layout(settings[from/2],settings[from/2].width());var b=RoadProfile.layout(settings[to/2],settings[to/2].width());
    if(a.catalog().lanes()/2<options.lanes()||b.catalog().lanes()/2<options.lanes())throw new IllegalArgumentException("主路单向车道少于匝道车道，请增加主路车道或选择单车道匝道");
    double edgeA=Math.abs(a.outer(1))+a.shoulderWidth(),edgeB=Math.abs(b.outer(1))+b.shoulderWidth();
    double portA=Math.min(options.width(),edgeA-a.median()/2),portB=Math.min(options.width(),edgeB-b.median()/2);
    double offsetA=edgeA-portA/2,offsetB=edgeB-portB/2;
    if(n==5&&from==4){portA=options.width();offsetA=(options.width()+.5)/2;}
    if(n==5&&to==4){portB=options.width();offsetB=(options.width()+.5)/2;}
    var departure=feeder(from,to,false,offsetA,portA);
    var arrival=feeder(to,from,true,offsetB,portB);
    var samples=new ArrayList<Sample>(departure);
    V lead1=departure.get(departure.size()-1).center(),tail0=arrival.get(arrival.size()-1).center();
    double span=lead1.sub(tail0).horizontalLength(),control=span*.46;
    curve(samples,lead1,lead1.add(in.mul(control)),tail0.sub(out.mul(control)),tail0,in,out,options.width()/2);
    // Reverse the collector tree for an arrival; its lane frame follows traffic.
    for(int k=arrival.size()-2;k>=0;k--){Sample p=arrival.get(k);samples.add(new Sample(p.center(),new V(-p.left().x(),0,-p.left().z()),0,p.halfWidth()));}
    Settings ramp=new Settings(Mode.CURVE,options.lanes()==1?Style.R1:Style.R2,options.width(),1,.4,90).rampTurn(RampTurn.AUTO).options(RoadProfile.Options.DEFAULT.traffic(options.leftTraffic()));
    return new Route(from,to,RoadRibbon.mesh(samples,ramp),levels[from/2],levels[to/2]);
  }
  private int rank(int from,int to){int rank=0;for(int j=0;j<to;j++)if(j!=from&&j/2!=from/2)rank++;return rank;}
  private List<Sample> feeder(int arm,int other,boolean arrival,double offset,double port) {
    int rank=rank(arm,other);
    int group=rank/2;
    double sign=(options.leftTraffic()?-1:1)*(arrival?-1:1),spacing=options.width()+3;
    V inward=radial[arm].mul(-1);
    double outer=settings[arm/2].width()/2+options.width()/2+4;
    double root=lengths[arm]*.86,leaf=lengths[arm]*.26;
    double mouth=Math.max(96,options.transition());
    double split=Math.max(55,Math.sqrt(options.radius()*spacing*2/.14));
    double available=root-leaf;
    if(available<mouth+2*split+24)throw new IllegalArgumentException("端点过近，无法容纳加长集散道和两级分合流");
    double first=root-mouth,second=first-split,third=second-split;
    var out=new ArrayList<Sample>();
    V a=point(arm,root,offset*sign),b=point(arm,first,outer*sign);
    curve(out,a,a.add(inward.mul(mouth*.375)),b.sub(inward.mul(mouth*.375)),b,inward,inward,options.width()/2);
    int end=out.size()-1;
    for(int i=0;i<=end;i++){Sample p=out.get(i);out.set(i,new Sample(p.center(),p.left(),0,(port+(options.width()-port)*Settings.smooth((double)i/end))/2));}
    // A common collector first divides 2+1 or 2+2; each pair then divides again.
    double groupOffset=outer+group*2*spacing;
    V c=point(arm,second,groupOffset*sign);
    curve(out,b,b.add(inward.mul(split*.375)),c.sub(inward.mul(split*.375)),c,inward,inward,options.width()/2);
    V d=point(arm,third,(outer+rank*spacing)*sign);
    curve(out,c,c.add(inward.mul(split*.375)),d.sub(inward.mul(split*.375)),d,inward,inward,options.width()/2);
    line(out,d,point(arm,leaf,(outer+rank*spacing)*sign),options.width()/2);
    return out;
  }
  private V point(int arm,double radius,double offset){return center.add(radial[arm].mul(radius)).add(radial[arm].mul(-1).left().mul(offset));}
  private static void curve(List<Sample> samples,V a,V b,V c,V d,V start,V end,double width){
    int steps=(int)Math.ceil((b.sub(a).horizontalLength()+c.sub(b).horizontalLength()+d.sub(c).horizontalLength())/STEP);
    for(int k=samples.isEmpty()?0:1;k<=steps;k++){
      double t=(double)k/steps;V p=cubic(a,b,c,d,t);
      V tangent=k==0?start:k==steps?end:cubic(a,b,c,d,Math.min(1,t+.00001)).sub(cubic(a,b,c,d,Math.max(0,t-.00001))).horizontalUnit();
      samples.add(new Sample(p,tangent.left(),0,width));
    }
  }
  private static void line(List<Sample> list,V a,V b,double width){int count=(int)Math.ceil(a.sub(b).horizontalLength()/STEP);V left=b.sub(a).horizontalUnit().left();for(int k=list.isEmpty()?0:1;k<=count;k++)list.add(new Sample(a.add(b.sub(a).mul((double)k/count)),left,0,width));}
  private static double sharedEnd(Mesh mesh,Grid other,boolean start){
    List<Sample> samples=mesh.samples();int begin=start?0:samples.size()-1,increment=start?1:-1;double last=start?0:mesh.length();
    for(int i=begin;i>=0&&i<samples.size();i+=increment){Sample s=samples.get(i);if(other.hit(s)==null)break;last=s.distance();}
    return last;
  }
  private Mesh elevate(Route r){
    double d0=r.holdStart+1,d1=r.holdEnd-1;
    double enter=r.crossStart-2,leave=r.crossEnd+2,layer=center.y()+r.layer*gap;
    if(d1<=d0)throw new IllegalArgumentException(name(r)+" 分合流区域重叠，请增加端点距离");
    boolean crossing=r.crossEnd>=0;
    if(crossing){climb(name(r),layer-r.sourceHeight,enter-d0);climb(name(r),r.targetHeight-layer,d1-leave);}
    else climb(name(r),r.targetHeight-r.sourceHeight,d1-d0);
    var samples=new ArrayList<Sample>();
    for(Sample s:r.flat.samples()){
      double d=s.distance(),y;
      if(d<=d0)y=r.sourceHeight;else if(d>=d1)y=r.targetHeight;
      else if(!crossing)y=r.sourceHeight+(r.targetHeight-r.sourceHeight)*smooth((d-d0)/(d1-d0));
      else if(d<enter)y=r.sourceHeight+(layer-r.sourceHeight)*smooth((d-d0)/(enter-d0));
      else if(d>leave)y=layer+(r.targetHeight-layer)*smooth((d-leave)/(d1-leave));else y=layer;
      samples.add(new Sample(new V(s.center().x(),y,s.center().z()),s.left(),0,s.halfWidth()));
    }
    return RoadRibbon.mesh(samples,r.flat.settings());
  }
  private static void climb(String name,double rise,double distance){
    double need=Math.abs(rise)*1.25/.15;
    if(distance+.01<need)throw new IllegalArgumentException(name+" 升降段可用 "+(int)distance+" 格，需 "+(int)Math.ceil(need)+" 格");
  }
  private static double smooth(double t){t=Math.max(0,Math.min(1,t));if(t<.2)return t*t/.32;if(t>.8)return 1-(1-t)*(1-t)/.32;return (t-.1)/.8;}
  private static double minRadius(Mesh mesh){double min=1e12;var s=mesh.samples();for(int k=2;k<s.size();k++){V a=s.get(k-2).center(),b=s.get(k-1).center(),c=s.get(k).center();double ab=b.sub(a).horizontalLength(),bc=c.sub(b).horizontalLength(),ca=c.sub(a).horizontalLength();double twice=Math.abs(cross(b.sub(a),c.sub(a)));if(twice>1e-8)min=Math.min(min,ab*bc*ca/(2*twice));}return min;}
  private void verify(List<Mesh> elevated,double[][] sharedFrom,double[][] sharedTo){
    var grids=new ArrayList<Grid>();mains.forEach(m->grids.add(new Grid(m)));elevated.forEach(m->grids.add(new Grid(m)));
    for(int i=0;i<routes.size();i++){
      Route r=routes.get(i);Mesh mesh=elevated.get(i);
      for(int sampleIndex=0;sampleIndex<mesh.samples().size();sampleIndex++){
        Sample sample=mesh.samples().get(sampleIndex);
        // Horizontal station remains the stable coordinate when the 3-D ribbon gains height.
        double d=r.flat.samples().get(sampleIndex).distance();
        for(int j=0;j<grids.size();j++)if(j!=i+3){
          Hit hit=grids.get(j).hit(sample);if(hit==null)continue;
          double dy=Math.abs(sample.center().y()-hit.height());
          boolean shared=j<3?(j==r.from/2&&d<=r.holdStart+2||j==r.to/2&&d>=r.holdEnd-2):(d<=sharedFrom[i][j-3]+2||d>=sharedTo[i][j-3]-2);
          if(shared&&dy<.05)continue;
          double thick=Math.max(mesh.settings().thickness(),grids.get(j).mesh.settings().thickness());
          if(dy<options.clearance()+thick-.05)throw new IllegalArgumentException(name(r)+" 与其他道路净空不足，请增加端点距离");
        }
      }
    }
  }
  private LegacyMulti250(){throw new AssertionError();}
}
