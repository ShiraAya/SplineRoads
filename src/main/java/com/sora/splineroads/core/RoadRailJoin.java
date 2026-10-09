package com.sora.splineroads.core;

import com.sora.splineroads.core.RoadGeometry.*;
import java.util.*;

/** The common boundary of lane-connected decks, at the rail's actual inset.
 * Opening metadata is a broad furniture-clearance capsule, not the paved outline:
 * using it here removes exposed rails around tapers and grade-separated mouths.
 * Triangles retain real deck heights, holes, widths and flat longitudinal ends. */
public final class RoadRailJoin {
  // Keep even the 0.62 m highway footing wholly inside its owning deck.
  // A 0.16 m inset left the base in the adjacent live-lane clearance corridor.
  public static final double INSET=.34;
  /** Fit a new permanent-cut railing inside its existing shoulder. Do not
   * widen the saved deck: an already connected continuation owns that exact port. */
  public static double inset(Mesh mesh,Sample at,int side){
    if(mesh.reference()==null)return INSET;
    var raw=LaneSections.reference(mesh);var original=RoadStructures.sample(raw,at.distance());boolean cutSide=false;
    for(var cut:mesh.settings().options().lanePoints().cuts())if(!cut.temporary()&&cut.rectangular()&&cut.removed(at.distance())>.999){
      var lane=LanePoints.lane(raw,at.distance(),cut.lane());
      if(Math.signum(lane.position().sub(original.center()).dot(original.left()))==side){cutSide=true;break;}
    }
    if(!cutSide)return INSET;
    var layout=RoadProfile.layout(mesh,at);double shoulder=side>0?at.halfWidth()-layout.motorMax():at.halfWidth()+layout.motorMin();
    double half=RoadProfile.highway(mesh.settings().style())?.31:.21;
    return Math.max(half+.015,Math.min(INSET,shoulder-half-.015));
  }
  public record Span(V a,V b) {}
  /** Higher-priority neighbour owns coincident boundaries; strict interiors always win. */
  public record Neighbor(Mesh mesh,boolean ownsBoundary) {}
  private record Range(double from,double to) {}
  private record Face(V a,V b,V c,boolean ownsBoundary) {
    double cross(V p,V q,V r){return (q.x()-p.x())*(r.z()-p.z())-(q.z()-p.z())*(r.x()-p.x());}
    double height(V p){
      double det=cross(a,b,c);
      return a.y()+((p.x()-a.x())*(c.z()-a.z())-(p.z()-a.z())*(c.x()-a.x()))/det*(b.y()-a.y())
          +((b.x()-a.x())*(p.z()-a.z())-(b.z()-a.z())*(p.x()-a.x()))/det*(c.y()-a.y());
    }
    Range intersection(V from,V to){
      double[] t={0,1};V[] p={a,b,c};
      for(int i=0;i<3;i++) {
        V x=p[i],y=p[(i+1)%3];double length=y.sub(x).horizontalLength();
        double fa=cross(x,y,from)/length,fb=cross(x,y,to)/length;
        // A non-owner cannot erase the coincident perimeter of its owner. Positive
        // epsilon also keeps tangential point contacts from creating visible holes.
        if(!clip(t,fa,fb,ownsBoundary?-1e-7:1e-7))return null;
      }
      double da=from.y()-height(from),db=to.y()-height(to);
      if(!clip(t,da,db,-.12)||!clip(t,-da,-db,-.12))return null;
      return t[1]-t[0]>1e-7?new Range(t[0],t[1]):null;
    }
  }
  private record Edge(V a,V b,boolean ownsBoundary,int profile) {}
  private static final int NONE=-1,SOUND=4;
  /** One profile per actual outer assembly. A deck without a rail cannot own a
   * coincident rail or its terminal post merely because its paint has priority. */
  private static int profile(Mesh mesh,Sample sample,int side){
    var settings=mesh.settings();var outer=settings.options().outerRail();
    if(settings.structure()==Structure.TUNNEL||outer==RoadProfile.OuterRail.OFF)return NONE;
    boolean highway=RoadProfile.catalog(settings.style()).type()==RoadProfile.Type.HIGHWAY;
    boolean raised=RoadStreetscape.raised(mesh,sample);
    boolean modern=RoadProfile.modern(settings.style());
    if(!modern)return settings.structure()==Structure.GROUND?NONE:5;
    if(!(outer==RoadProfile.OuterRail.ON||highway||settings.style().ramp()||raised))return NONE;
    if(raised&&!settings.style().ramp()&&outer.sound(side))return SOUND;
    return profile(highway,raised);
  }
  private static int profile(boolean highway,boolean raised){return (highway?2:0)+(raised?1:0);}
  private final List<Mesh> neighbors;
  private final Map<Long,List<Face>> grid=new HashMap<>();
  private final Map<Long,List<Face>> material=new HashMap<>();
  private final Map<Long,List<Edge>> edges=new HashMap<>();
  public RoadRailJoin(List<Neighbor> neighbors){
    this.neighbors=neighbors.stream().map(Neighbor::mesh).toList();
    for(var neighbor:neighbors){var mesh=neighbor.mesh();
      for(int i=1;i<mesh.samples().size();i++){
        var a=mesh.samples().get(i-1);var b=mesh.samples().get(i);
        for(var strip:LaneDeck.strips(mesh,a,b)) {
          // The rail is inset but the supporting pavement is not. A .15-block
          // welded overlap used by an auxiliary lane used to disappear after both
          // faces were inset .16, leaving a barrier across an otherwise open merge.
          add(material,strip.al(),strip.ar(),strip.br(),true);
          add(material,strip.al(),strip.br(),strip.bl(),true);
          // Only exposed band boundaries get an inset. Zero-size slots split a
          // continuous face too, but those split lines must not become false gutters.
          double highA=strip.al().distance(a.at(a.halfWidth(),0))<1e-6?inset(mesh,a,1):INSET,highB=strip.bl().distance(b.at(b.halfWidth(),0))<1e-6?inset(mesh,b,1):INSET;
          double lowA=strip.ar().distance(a.at(-a.halfWidth(),0))<1e-6?inset(mesh,a,-1):INSET,lowB=strip.br().distance(b.at(-b.halfWidth(),0))<1e-6?inset(mesh,b,-1):INSET;
          V al=strip.al().sub(a.left().mul(strip.highWall()?highA:0));
          V ar=strip.ar().add(a.left().mul(strip.lowWall()?lowA:0));
          V bl=strip.bl().sub(b.left().mul(strip.highWall()?highB:0));
          V br=strip.br().add(b.left().mul(strip.lowWall()?lowB:0));
          if(al.sub(ar).dot(a.left())<0||bl.sub(br).dot(b.left())<0)continue;
          var sample=RoadStructures.sample(mesh,(a.distance()+b.distance())/2);
          int high=profile(mesh,sample,1),low=profile(mesh,sample,-1);
          boolean boundaryOwner=neighbor.ownsBoundary()&&(high!=NONE||low!=NONE);
          add(al,ar,br,boundaryOwner);add(al,br,bl,boundaryOwner);
          // Hole edges still clip real material, but no invented rail/post may be
          // mitered to a hole boundary which the outer-rail planner never emits.
          if(strip.highWall()&&strip.al().distance(a.at(a.halfWidth(),0))<1e-6&&strip.bl().distance(b.at(b.halfWidth(),0))<1e-6)
            edge(al,bl,neighbor.ownsBoundary(),high);
          if(strip.lowWall()&&strip.ar().distance(a.at(-a.halfWidth(),0))<1e-6&&strip.br().distance(b.at(-b.halfWidth(),0))<1e-6)
            edge(ar,br,neighbor.ownsBoundary(),low);
        }
      }
    }
  }
  private void add(V a,V b,V c,boolean owner){add(grid,a,b,c,owner);}
  private void add(Map<Long,List<Face>> target,V a,V b,V c,boolean owner){
    double area=(b.x()-a.x())*(c.z()-a.z())-(b.z()-a.z())*(c.x()-a.x());
    if(Math.abs(area)<1e-10)return;if(area<0){V swap=b;b=c;c=swap;}
    var face=new Face(a,b,c,owner);
    for(int x=cell(Math.min(a.x(),Math.min(b.x(),c.x()))-1e-6);x<=cell(Math.max(a.x(),Math.max(b.x(),c.x()))+1e-6);x++)
      for(int z=cell(Math.min(a.z(),Math.min(b.z(),c.z()))-1e-6);z<=cell(Math.max(a.z(),Math.max(b.z(),c.z()))+1e-6);z++)
        target.computeIfAbsent(key(x,z),k->new ArrayList<>()).add(face);
  }
  private void edge(V a,V b,boolean owner,int profile){
    if(profile==NONE||a.sub(b).horizontalLength()<1e-9)return;var edge=new Edge(a,b,owner,profile);
    for(int x=cell(Math.min(a.x(),b.x())-1e-5);x<=cell(Math.max(a.x(),b.x())+1e-5);x++)
      for(int z=cell(Math.min(a.z(),b.z())-1e-5);z<=cell(Math.max(a.z(),b.z())+1e-5);z++)
        edges.computeIfAbsent(key(x,z),k->new ArrayList<>()).add(edge);
  }
  private boolean touches(Edge edge,V point){
    V d=edge.b().sub(edge.a());double length=d.x()*d.x()+d.z()*d.z();
    double t=((point.x()-edge.a().x())*d.x()+(point.z()-edge.a().z())*d.z())/length;
    if(t< -1e-6||t>1+1e-6)return false;V at=edge.a().add(d.mul(Math.max(0,Math.min(1,t))));
    return point.sub(at).horizontalLength()<5e-5&&Math.abs(point.y()-at.y())<1e-5;
  }
  /** Reciprocal mitres share their cut face across records, not just their centre.
   * Frames are unit-width and may be sign-reversed on a reversed reference direction. */
  public V joint(V point,V direction){return joint(point,direction,-2);}
  public V joint(V point,V direction,boolean highway,boolean raised){return joint(point,direction,profile(highway,raised));}
  private V joint(V point,V direction,int expected){
    V a=direction.horizontalUnit(),found=null;double smallest=1;
    for(var edge:edges.getOrDefault(key(cell(point.x()),cell(point.z())),List.of()))if((expected==-2||edge.profile()==expected)&&touches(edge,point)){
      V b=edge.b().sub(edge.a()).horizontalUnit();double dot=a.dot(b);if(dot<0){b=b.mul(-1);dot=-dot;}
      if(dot<.25||dot>=smallest-1e-9)continue;smallest=dot;found=a.left().add(b.left()).mul(1/(1+dot));
    }
    return found;
  }
  /** One terminal post at a shared edge, with the same stable owner as the rail. */
  public boolean ownsPost(V point){return ownsPost(point,-2);}
  public boolean ownsPost(V point,boolean highway,boolean raised){return ownsPost(point,profile(highway,raised));}
  private boolean ownsPost(V point,int expected){
    for(var edge:edges.getOrDefault(key(cell(point.x()),cell(point.z())),List.of()))
      if(edge.ownsBoundary()&&edge.profile()>=0&&edge.profile()<3&&(expected==-2||edge.profile()==expected)&&touches(edge,point))return false;
    return true;
  }
  /** Exact continuous cut positions, not rounded .5-block visibility samples. */
  public List<Span> exposed(V a,V b){return exposed(grid,a,b);}
  private List<Span> exposed(Map<Long,List<Face>> surface,V a,V b){
    if(a.sub(b).horizontalLength()<1e-9)return List.of();
    var faces=new LinkedHashSet<Face>();
    for(int x=cell(Math.min(a.x(),b.x())-1e-6);x<=cell(Math.max(a.x(),b.x())+1e-6);x++)
      for(int z=cell(Math.min(a.z(),b.z())-1e-6);z<=cell(Math.max(a.z(),b.z())+1e-6);z++)
        faces.addAll(surface.getOrDefault(key(x,z),List.of()));
    var cuts=new ArrayList<Range>();for(var face:faces){var hit=face.intersection(a,b);if(hit!=null)cuts.add(hit);}
    cuts.sort(Comparator.comparingDouble(Range::from));
    var result=new ArrayList<Span>();double at=0;V d=b.sub(a);
    double epsilon=1e-5/d.horizontalLength(); // Reject only sub-numerical cracks between adjacent triangle faces.
    for(var cut:cuts){if(cut.from()>at+epsilon)result.add(new Span(a.add(d.mul(at)),a.add(d.mul(cut.from()))));at=Math.max(at,cut.to());}
    if(at<1-epsilon)result.add(new Span(a.add(d.mul(at)),b));return List.copyOf(result);
  }
  public List<Span> exposed(V a,V b,V outside){return exposed(a,b,outside,INSET);}
  public List<Span> exposed(V a,V b,V outside,double inset){
    V mid=a.add(b).mul(.5),direction=outside.sub(mid);
    if(direction.horizontalLength()<1e-8)return exposed(a,b);
    // Clip shared merge seams against actual neighboring pavement at the outer
    // edge, not its inset rail. Real positive gaps and height separation retain rails.
    V shift=direction.horizontalUnit().mul(inset+1e-5);
    var inside=exposed(a,b);
    // At a diverging/merging nose the inset boundaries meet before the raw deck
    // boundary. Clipping both independently cuts a gap between their meeting tips.
    // Exact parallel auxiliary seams still need the material test (their inset
    // polygons are disjoint despite the small deliberate paved overlap).
    V tangent=b.sub(a).horizontalUnit();
    for(var host:neighbors){var q=RoadQueries.horizontal(host,mid);
      if(q.horizontalDistance()<q.sample().halfWidth()+inset+.05&&Math.abs(q.sample().center().y()-mid.y())<.12
          &&Math.abs(q.tangent().horizontalUnit().dot(tangent))<.9999)return inside;
    }
    var boundary=exposed(material,a.add(shift),b.add(shift));
    var out=new ArrayList<Span>();V d=b.sub(a);double length=d.dot(d);
    for(var first:inside)for(var second:boundary){
      double lo=Math.max(first.a().sub(a).dot(d)/length,second.a().sub(shift).sub(a).dot(d)/length);
      double hi=Math.min(first.b().sub(a).dot(d)/length,second.b().sub(shift).sub(a).dot(d)/length);
      if(hi-lo>1e-7)out.add(new Span(a.add(d.mul(lo)),a.add(d.mul(hi))));
    }
    return List.copyOf(out);
  }
  /** Account for the slab end cap and the inward rail offset at a declared joining port.
   * The caller supplies only linked neighbors; no proximity-only opening is invented. */
  public List<Span> exposedMouth(V a,V b){return exposed(material,a,b);}
  public static RoadRailJoin mouths(List<Mesh> linked){
    var extensions=new ArrayList<Neighbor>();
    for(var mesh:linked)for(boolean start:new boolean[]{true,false}){
      int n=mesh.samples().size();if(n<2)continue;
      var at=start?mesh.first():mesh.last();var next=mesh.samples().get(start?1:n-2);
      V delta=at.center().sub(next.center());double run=delta.horizontalLength();if(run<1e-8)continue;
      var layout=RoadProfile.layout(mesh,at);
      // Motor width only. Shoulders do not authorize opening a neighboring slot.
      double half=(layout.motorMax()-layout.motorMin())/2;
      V center=at.at(layout.motorCenter(),0),extension=delta.mul(.86/run);
      var samples=List.of(new Sample(center.sub(extension),at.left(),0,half),new Sample(center.add(extension),at.left(),1.72,half));
      extensions.add(new Neighbor(RoadRibbon.mesh(samples,mesh.settings()),true));
    }
    return new RoadRailJoin(extensions);
  }
  private static boolean clip(double[] t,double a,double b,double min){
    double d=b-a;if(Math.abs(d)<1e-12)return a>=min;
    double crossing=(min-a)/d;if(d>0)t[0]=Math.max(t[0],crossing);else t[1]=Math.min(t[1],crossing);
    return t[0]<=t[1]&&t[1]>=0&&t[0]<=1;
  }
  private static int cell(double v){return (int)Math.floor(v/8);}
  private static long key(int x,int z){return ((long)x<<32)^(z&0xffffffffL);}
}
