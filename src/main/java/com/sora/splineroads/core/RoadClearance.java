package com.sora.splineroads.core;

import com.sora.splineroads.core.RoadGeometry.*;
import java.util.*;

/** Exact piecewise-planar deck overlaps. A centerline projection is not a collision location. */
public final class RoadClearance {
  public static final double REQUIRED = 4.0;
  private static final double EPS = 1e-8, AREA_EPS = 1e-4;
  public record Contact(double from,double to,V ours,V other,double usableClearance,
                        double raise,double lower) {
    public boolean blocked(){return usableClearance < REQUIRED - .025;}
  }
  public static final class Conflict extends IllegalArgumentException {
    private final Contact contact;
    public Conflict(Contact c){
      super(String.format(Locale.ROOT,
        "路面实际相交于 X=%.2f Z=%.2f；新路 Y=%.2f、既有路 Y=%.2f，板底净空 %.2f / 要求 %.2f 格",
        c.ours().x(),c.ours().z(),c.ours().y(),c.other().y(),c.usableClearance(),REQUIRED));
      contact=c;
    }
    public Contact contact(){return contact;}
  }
  private record Triangle(V a,V b,V c,double sa,double sb,double sc) {
    double det(){return cross(b.sub(a),c.sub(a));}
    double value(V p,double va,double vb,double vc){
      V q=p.sub(a);double det=det();
      return va+cross(q,c.sub(a))/det*(vb-va)+cross(b.sub(a),q)/det*(vc-va);
    }
    double height(V p){return value(p,a.y(),b.y(),c.y());}
    double station(V p){return value(p,sa,sb,sc);}
    List<V> polygon(){return det()>=0?List.of(a,b,c):List.of(a,c,b);}
    double minX(){return Math.min(a.x(),Math.min(b.x(),c.x()));}
    double maxX(){return Math.max(a.x(),Math.max(b.x(),c.x()));}
    double minZ(){return Math.min(a.z(),Math.min(b.z(),c.z()));}
    double maxZ(){return Math.max(a.z(),Math.max(b.z(),c.z()));}
  }
  private static final class Grid {
    final Map<Long,List<Triangle>> cells=new HashMap<>();
    Grid(Mesh mesh){for(Triangle t:triangles(mesh))
      for(int x=cell(t.minX());x<=cell(t.maxX());x++)for(int z=cell(t.minZ());z<=cell(t.maxZ());z++)
        cells.computeIfAbsent(key(x,z),k->new ArrayList<>()).add(t);
    }
    Set<Triangle> near(Triangle t){var out=new LinkedHashSet<Triangle>();
      for(int x=cell(t.minX());x<=cell(t.maxX());x++)for(int z=cell(t.minZ());z<=cell(t.maxZ());z++)
        for(Triangle q:cells.getOrDefault(key(x,z),List.of()))
          if(q.maxX()>t.minX()+EPS&&q.minX()<t.maxX()-EPS&&q.maxZ()>t.minZ()+EPS&&q.minZ()<t.maxZ()-EPS)out.add(q);
      return out;
    }
  }
  // Grid values contain triangle vectors, never the Mesh key. Identity keys are weak and bounded;
  // a newly generated/deformed mesh therefore cannot reuse stale collision geometry.
  private static final WeakIdentityCache<Mesh,Grid> GRIDS=new WeakIdentityCache<>(256,500_000,
      grid->grid.cells.values().stream().mapToInt(List::size).sum());
  private static Grid grid(Mesh mesh){return GRIDS.get(mesh,Grid::new);}
  public static void clearPreparedCache(){GRIDS.clear();}
  public static WeakIdentityCache.Stats preparedCacheStats(){return GRIDS.stats();}
  /** Immutable prepared deck index; callers may retain it only for their current planning query. */
  public static final class Prepared {
    private final Mesh mesh;private final Grid grid;
    private Prepared(Mesh mesh){this.mesh=mesh;this.grid=grid(mesh);}
  }
  public static Prepared prepare(Mesh mesh){return new Prepared(mesh);}
  public static List<Contact> contacts(Mesh a,Prepared other){return contacts(a,other.mesh,null,other.grid);}
  public static void check(Mesh a,Mesh b){check(a,b,null);}
  public static void check(Mesh a,Mesh b,Mesh previous){
    for(Contact c:contacts(a,b,previous))if(c.blocked())throw new Conflict(c);
  }
  /** All geometric crossings, including currently-clear ones, for explicit OVER/UNDER planning. */
  public static List<Contact> contacts(Mesh a,Mesh b){return contacts(a,b,null);}
  public static List<Contact> contacts(Mesh a,Mesh b,Mesh previous){
    if(a.max().x()<=b.min().x()+EPS||b.max().x()<=a.min().x()+EPS||a.max().z()<=b.min().z()+EPS||b.max().z()<=a.min().z()+EPS)return List.of();
    return contacts(a,b,previous,grid(b));
  }
  private static List<Contact> contacts(Mesh a,Mesh b,Mesh previous,Grid grid){
    var out=new ArrayList<Contact>();
    for(Triangle t:triangles(a)){
      // Preserve only already-built, unchanged material during a non-ramp road edit.
      if(previous!=null&&t.polygon().stream().allMatch(v->RoadQueries.contains(previous,v,.015,.02)))continue;
      for(Triangle q:grid.near(t)){
        List<V> polygon=intersection(t.polygon(),q.polygon());if(area(polygon)<AREA_EPS)continue;
        double from=Double.POSITIVE_INFINITY,to=Double.NEGATIVE_INFINITY,min=Double.POSITIVE_INFINITY;
        double raise=0,lower=0,minDiff=Double.POSITIVE_INFINITY,maxDiff=Double.NEGATIVE_INFINITY;
        V ours=null,other=null,negative=null,positive=null;
        for(V p:polygon){
          double ya=t.height(p),yb=q.height(p),difference=ya-yb;
          double gap=Math.abs(difference)-(difference>=0?a.settings().thickness():b.settings().thickness());
          if(gap<min){min=gap;ours=new V(p.x(),ya,p.z());other=new V(p.x(),yb,p.z());}
          double station=t.station(p);from=Math.min(from,station);to=Math.max(to,station);
          if(difference<minDiff){minDiff=difference;negative=p;}if(difference>maxDiff){maxDiff=difference;positive=p;}
          raise=Math.max(raise,yb+REQUIRED+a.settings().thickness()+.10-ya);
          lower=Math.max(lower,ya+REQUIRED+b.settings().thickness()+.10-yb);
        }
        // Difference changes sign inside a polygon: the two deck planes intersect there.
        if(minDiff<0&&maxDiff>0){
          V crossing=negative.add(positive.sub(negative).mul(-minDiff/(maxDiff-minDiff)));
          ours=new V(crossing.x(),t.height(crossing),crossing.z());other=new V(crossing.x(),q.height(crossing),crossing.z());
          min=-Math.min(a.settings().thickness(),b.settings().thickness());
        }
        out.add(new Contact(from,to,ours,other,min,raise,lower));
      }
    }
    return List.copyOf(out);
  }
  private static List<Triangle> triangles(Mesh mesh){
    var out=new ArrayList<Triangle>();var samples=mesh.samples();
    for(int i=1;i<samples.size();i++){
      var a=samples.get(i-1);var b=samples.get(i);
      V al=a.at(a.halfWidth(),0),ar=a.at(-a.halfWidth(),0),bl=b.at(b.halfWidth(),0),br=b.at(-b.halfWidth(),0);
      for(var t:List.of(new Triangle(al,ar,br,a.distance(),a.distance(),b.distance()),new Triangle(al,br,bl,a.distance(),b.distance(),b.distance())))
        if(Math.abs(t.det())>EPS)out.add(t);
    }return out;
  }
  private static List<V> intersection(List<V> subject,List<V> clip){
    List<V> polygon=subject;
    for(int i=0;i<clip.size()&&!polygon.isEmpty();i++){
      V a=clip.get(i),b=clip.get((i+1)%clip.size()),d=b.sub(a);var next=new ArrayList<V>();
      V prev=polygon.get(polygon.size()-1);double dp=cross(d,prev.sub(a));
      for(V cur:polygon){double dc=cross(d,cur.sub(a));boolean pin=dp>=-EPS,cin=dc>=-EPS;
        if(pin!=cin){double den=dp-dc;if(Math.abs(den)>EPS)next.add(prev.add(cur.sub(prev).mul(dp/den)));}
        if(cin)next.add(cur);prev=cur;dp=dc;
      }polygon=next;
    }return polygon;
  }
  private static double area(List<V> p){if(p.size()<3)return 0;double sum=0;V origin=p.get(0);for(int i=1;i<p.size()-1;i++)sum+=cross(p.get(i).sub(origin),p.get(i+1).sub(origin));return Math.abs(sum)/2;}
  private static double cross(V a,V b){return a.x()*b.z()-a.z()*b.x();}
  private static int cell(double x){return (int)Math.floor(x/16);}
  private static long key(int x,int z){return ((long)x<<32)^(z&0xffffffffL);}
  private RoadClearance(){}
}
