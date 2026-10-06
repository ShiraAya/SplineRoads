package com.sora.splineroads.core;

import com.sora.splineroads.core.RoadGeometry.*;
import java.util.*;

/** The common boundary of lane-connected decks, at the rail's actual inset.
 * Opening metadata is a broad furniture-clearance capsule, not the paved outline:
 * using it here removes exposed rails around tapers and grade-separated mouths.
 * Triangles retain real deck heights, holes, widths and flat longitudinal ends. */
public final class RoadRailJoin {
  public static final double INSET=.16;
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
  private record Edge(V a,V b,boolean ownsBoundary) {}
  private final Map<Long,List<Face>> grid=new HashMap<>();
  private final Map<Long,List<Edge>> edges=new HashMap<>();
  public RoadRailJoin(List<Neighbor> neighbors){
    for(var neighbor:neighbors){var mesh=neighbor.mesh();
      for(int i=1;i<mesh.samples().size();i++){
        var a=mesh.samples().get(i-1);var b=mesh.samples().get(i);
        for(var strip:LaneDeck.strips(mesh,a,b)) {
          // Only exposed band boundaries get an inset. Zero-size slots split a
          // continuous face too, but those split lines must not become false gutters.
          V al=strip.al().sub(a.left().mul(strip.highWall()?INSET:0));
          V ar=strip.ar().add(a.left().mul(strip.lowWall()?INSET:0));
          V bl=strip.bl().sub(b.left().mul(strip.highWall()?INSET:0));
          V br=strip.br().add(b.left().mul(strip.lowWall()?INSET:0));
          if(al.sub(ar).dot(a.left())<0||bl.sub(br).dot(b.left())<0)continue;
          add(al,ar,br,neighbor.ownsBoundary());add(al,br,bl,neighbor.ownsBoundary());
          if(strip.highWall())edge(al,bl,neighbor.ownsBoundary());
          if(strip.lowWall())edge(ar,br,neighbor.ownsBoundary());
        }
      }
    }
  }
  private void add(V a,V b,V c,boolean owner){
    double area=(b.x()-a.x())*(c.z()-a.z())-(b.z()-a.z())*(c.x()-a.x());
    if(Math.abs(area)<1e-10)return;if(area<0){V swap=b;b=c;c=swap;}
    var face=new Face(a,b,c,owner);
    for(int x=cell(Math.min(a.x(),Math.min(b.x(),c.x()))-1e-6);x<=cell(Math.max(a.x(),Math.max(b.x(),c.x()))+1e-6);x++)
      for(int z=cell(Math.min(a.z(),Math.min(b.z(),c.z()))-1e-6);z<=cell(Math.max(a.z(),Math.max(b.z(),c.z()))+1e-6);z++)
        grid.computeIfAbsent(key(x,z),k->new ArrayList<>()).add(face);
  }
  private void edge(V a,V b,boolean owner){
    if(a.sub(b).horizontalLength()<1e-9)return;var edge=new Edge(a,b,owner);
    for(int x=cell(Math.min(a.x(),b.x())-1e-5);x<=cell(Math.max(a.x(),b.x())+1e-5);x++)
      for(int z=cell(Math.min(a.z(),b.z())-1e-5);z<=cell(Math.max(a.z(),b.z())+1e-5);z++)
        edges.computeIfAbsent(key(x,z),k->new ArrayList<>()).add(edge);
  }
  private boolean touches(Edge edge,V point){
    V d=edge.b().sub(edge.a());double length=d.x()*d.x()+d.z()*d.z();
    double t=((point.x()-edge.a().x())*d.x()+(point.z()-edge.a().z())*d.z())/length;
    if(t< -1e-6||t>1+1e-6)return false;V at=edge.a().add(d.mul(Math.max(0,Math.min(1,t))));
    return point.sub(at).horizontalLength()<5e-5&&Math.abs(point.y()-at.y())<.120001;
  }
  /** Reciprocal mitres share their cut face across records, not just their centre.
   * Frames are unit-width and may be sign-reversed on a reversed reference direction. */
  public V joint(V point,V direction){
    V a=direction.horizontalUnit(),found=null;double smallest=1;
    for(var edge:edges.getOrDefault(key(cell(point.x()),cell(point.z())),List.of()))if(touches(edge,point)){
      V b=edge.b().sub(edge.a()).horizontalUnit();double dot=a.dot(b);if(dot<0){b=b.mul(-1);dot=-dot;}
      if(dot<.25||dot>=smallest-1e-9)continue;smallest=dot;found=a.left().add(b.left()).mul(1/(1+dot));
    }
    return found;
  }
  /** One terminal post at a shared edge, with the same stable owner as the rail. */
  public boolean ownsPost(V point){
    for(var edge:edges.getOrDefault(key(cell(point.x()),cell(point.z())),List.of()))
      if(edge.ownsBoundary()&&touches(edge,point))return false;
    return true;
  }
  /** Exact continuous cut positions, not rounded .5-block visibility samples. */
  public List<Span> exposed(V a,V b){
    if(a.sub(b).horizontalLength()<1e-9)return List.of();
    var faces=new LinkedHashSet<Face>();
    for(int x=cell(Math.min(a.x(),b.x())-1e-6);x<=cell(Math.max(a.x(),b.x())+1e-6);x++)
      for(int z=cell(Math.min(a.z(),b.z())-1e-6);z<=cell(Math.max(a.z(),b.z())+1e-6);z++)
        faces.addAll(grid.getOrDefault(key(x,z),List.of()));
    var cuts=new ArrayList<Range>();for(var face:faces){var hit=face.intersection(a,b);if(hit!=null)cuts.add(hit);}
    cuts.sort(Comparator.comparingDouble(Range::from));
    var result=new ArrayList<Span>();double at=0;V d=b.sub(a);
    double epsilon=1e-5/d.horizontalLength(); // Reject only sub-numerical cracks between adjacent triangle faces.
    for(var cut:cuts){if(cut.from()>at+epsilon)result.add(new Span(a.add(d.mul(at)),a.add(d.mul(cut.from()))));at=Math.max(at,cut.to());}
    if(at<1-epsilon)result.add(new Span(a.add(d.mul(at)),b));return List.copyOf(result);
  }
  private static boolean clip(double[] t,double a,double b,double min){
    double d=b-a;if(Math.abs(d)<1e-12)return a>=min;
    double crossing=(min-a)/d;if(d>0)t[0]=Math.max(t[0],crossing);else t[1]=Math.min(t[1],crossing);
    return t[0]<=t[1]&&t[1]>=0&&t[0]<=1;
  }
  private static int cell(double v){return (int)Math.floor(v/8);}
  private static long key(int x,int z){return ((long)x<<32)^(z&0xffffffffL);}
}
