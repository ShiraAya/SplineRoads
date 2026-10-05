package com.sora.splineroads.core;
import com.sora.splineroads.core.RoadGeometry.*;
import com.sora.splineroads.core.RoadProfile.*;
import java.util.*;
/** A two-way stem separates into an outbound A->B and inbound C->A carriageway. */
public final class YJunctionPlanner {
  public record Plan(Mesh stem,Mesh outbound,Mesh inbound,Node throat){}
  public static Plan plan(Node a,Node b,Node c,Settings main,Settings out,Settings in,double tension){
    var profile=RoadProfile.catalog(main.style());
    if(!profile.twoWay()||profile.type()==Type.RAMP||profile.type()==Type.LEGACY)throw new IllegalArgumentException("A 必须为普通或高速双向道路");
    for(var s:List.of(out,in))if(RoadProfile.catalog(s.style()).twoWay()||!RoadProfile.modern(s.style()))throw new IllegalArgumentException("B、C 须为单向道路");
    if(!Double.isFinite(tension)||tension<.2||tension>.8)throw new IllegalArgumentException("曲线强度须为 0.2–0.8");
    V forward=a.direction();double distance=Math.min(a.position().sub(b.position()).horizontalLength(),a.position().sub(c.position()).horizontalLength());
    if(distance<24)throw new IllegalArgumentException("A 至 B/C 至少 24 格，以留出分流过渡空间");
    double lead=Math.min(12,distance*.18);Node throat=new Node(a.position().add(forward.mul(lead)).add(new V(0,a.grade()*lead,0)),a.yaw(),a.grade());
    Settings mainFlat=flat(main,tension);Mesh stem=RoadGeometry.build(a,throat,mainFlat);
    double median=RoadProfile.medianWidth(profile),half=(main.width()-median)/2,offset=median/2+half/2;
    int side=RoadProfile.trafficSign(main.options().leftTraffic());
    V right=forward.left();Node first=new Node(throat.position().add(right.mul(side*offset)),a.yaw(),a.grade());
    Node last=new Node(throat.position().sub(right.mul(side*offset)),a.yaw()+180,-a.grade());
    Settings outSettings=branch(out,main,tension,half,true);Settings inSettings=branch(in,main,tension,half,false);
    Mesh outbound=RoadGeometry.build(first,b,outSettings),inbound=RoadGeometry.build(c,last,inSettings);
    // The two carriageways must not cross. Sharing their outer boundary at the throat is valid.
    for(var s:outbound.samples()){
      var q=RoadQueries.horizontal(inbound,s.center());
      if(s.center().sub(q.sample().center()).horizontalLength()<s.halfWidth()+q.sample().halfWidth()-.15&&Math.abs(s.center().y()-q.sample().center().y())<3.5)
        throw new IllegalArgumentException("B/C 方位导致两股车流交叉，请交换 B、C 或调整端点朝向");
    }
    // Keep the 3-on / 3-off stripe rhythm in the stem frame, including C -> A.
    outbound=phase(outbound,stem.length());
    inbound=phase(inbound,3-stem.length()-inbound.length());
    return new Plan(stem,outbound,inbound,throat);
  }
  private static Mesh phase(Mesh mesh,double phase){var s=mesh.settings();return RoadRibbon.mesh(mesh.samples(),s.options(s.options().ends(s.options().ends().paintPhase(phase))));}
  private static Settings flat(Settings s,double t){return new Settings(Mode.CURVE,s.style(),s.width(),s.thickness(),t,90,s.width(),s.width(),s.structure(),s.taperVersion(),s.rampTurn(),s.options().ends(RoadTransitions.Ends.NONE).infrastructure(s.options().infrastructure().clearEdits()).laneLines(List.of()));}
  private static Settings branch(Settings target,Settings main,double tension,double half,boolean outgoing){
    var s=flat(target,tension);var walk=s.options().sidewalk();
    var throatWalk=main.options().sidewalk().side(main.options().leftTraffic()?RoadSidewalks.Side.LEFT:RoadSidewalks.Side.RIGHT);
    var profile=RoadProfile.catalog(main.style());
    var halfStyle=RoadProfile.choose(profile.type(),profile.lanes()/2,false,Median.NONE,profile.shoulder());
    double start=outgoing?half:s.width(),end=outgoing?s.width():half;
    // A half carriageway does not acquire a second inner edge allowance. Preserve
    // A's actual lane coordinates instead of recalculating it as a standalone one-way.
    var layout=RoadProfile.layout(main,main.width());int side=RoadProfile.trafficSign(main.options().leftTraffic());
    double center=side*(layout.median()/2+half/2),inner=side*layout.median()/2,outer=layout.outer(side);
    var markings=new RoadTransitions.Port(Math.min(inner,outer)-center,Math.max(inner,outer)-center,0,
        layout.dividers().stream().filter(d->d*side>0).map(d->d-center).sorted().toList(),side<0?layout.curbWidth():0,side>0?layout.curbWidth():0);
    var halfSection=new RoadTransitions.Section(halfStyle,half,main.options().cycle(),main.options().cycleRail(),main.options().curb(),main.options().outerRail(),throatWalk,main.options().cycleAsphalt(),markings);
    var full=RoadTransitions.Section.of(s);
    return new Settings(Mode.CURVE,outgoing?s.style():halfStyle,outgoing?s.width():half,s.thickness(),tension,90,start,end,s.structure(),s.taperVersion(),s.rampTurn(),s.options().traffic(main.options().leftTraffic()).sidewalk(walk).ends(new RoadTransitions.Ends(outgoing?halfSection:full,outgoing?full:halfSection,true)));
  }
  public static double yaw(V direction){return Math.toDegrees(Math.atan2(-direction.x(),direction.z()));}
  private YJunctionPlanner(){}
}
