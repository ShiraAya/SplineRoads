package com.sora.splineroads;

import com.sora.splineroads.core.*;
import com.sora.splineroads.core.RoadGeometry.*;
import java.nio.file.*;
import java.util.*;

/** SR0.23.1 user diagrams: 2 -> (1+1) -> (2+2), reverse merges and mirrored hatching. */
public final class Revision231Validation {
  static int checks, forks, closures;
  static void check(boolean yes, String why) { checks++; if (!yes) throw new AssertionError(why); }
  static Settings settings(Style style, boolean left) {
    return new Settings(Mode.CURVE, style, style.defaultWidth(), 1, .4, 90)
        .options(RoadProfile.Options.DEFAULT.traffic(left));
  }
  static Mesh branch(int sign, boolean reverse, boolean straight, boolean left, double angle) {
    var points = new ArrayList<Sample>();
    V f = new V(Math.sin(angle),0,Math.cos(angle)), side=f.left();
    for (double z=-24; z<=105; z+=.25) {
      double bend = straight ? 0 : sign*.0035*Math.pow(Math.max(0,z),2);
      double dx = straight ? 0 : sign*.007*Math.max(0,z);
      V p = f.mul(z).add(side.mul(bend)).add(new V(0,64,0));
      V tangent = f.add(side.mul(dx)).horizontalUnit();
      points.add(new Sample(p,tangent.left(),0,4.5));
    }
    if(reverse) {Collections.reverse(points);points.replaceAll(p->new Sample(p.center(),p.left().mul(-1),0,p.halfWidth()));}
    return RoadRibbon.mesh(points,settings(Style.R2,left));
  }
  static List<RoadSurface.Face> paint(Mesh a, Mesh b) {
    var result = new ArrayList<RoadSurface.Face>(RoadSurface.build(a,List.of(),List.of(b)).markings());
    result.addAll(RoadSurface.build(b,List.of(a),List.of(a)).markings());
    return result;
  }
  static final Map<List<RoadSurface.Face>,Map<Long,List<RoadSurface.Face>>> grids=new IdentityHashMap<>();
  static long key(int x,int z){return ((long)x<<32)^(z&0xffffffffL);}
  static boolean painted(List<RoadSurface.Face> faces,V p) {
    var grid=grids.computeIfAbsent(faces,fs->{var g=new HashMap<Long,List<RoadSurface.Face>>();
      for(var f:fs){int x0=(int)Math.floor(f.points().stream().mapToDouble(V::x).min().orElseThrow()/2),x1=(int)Math.floor(f.points().stream().mapToDouble(V::x).max().orElseThrow()/2),z0=(int)Math.floor(f.points().stream().mapToDouble(V::z).min().orElseThrow()/2),z1=(int)Math.floor(f.points().stream().mapToDouble(V::z).max().orElseThrow()/2);for(int x=x0;x<=x1;x++)for(int z=z0;z<=z1;z++)g.computeIfAbsent(key(x,z),k->new ArrayList<>()).add(f);}return g;});
    return grid.getOrDefault(key((int)Math.floor(p.x()/2),(int)Math.floor(p.z()/2)),List.of()).stream().anyMatch(f->JunctionPaint.inside(f.points(),p));
  }
  static void forkFixtures() throws Exception {
    for(boolean reverse:new boolean[]{false,true}) for(boolean straight:new boolean[]{false,true})
      for(boolean left:new boolean[]{false,true}) for(double angle:new double[]{0,.63}) {
        Mesh a=branch(-1,reverse,false,left,angle), b=branch(1,reverse,straight,left,angle);
        List<RoadSurface.Face> ab=paint(a,b),ba=paint(b,a);
        V f=new V(Math.sin(angle),0,Math.cos(angle)),side=f.left();
        // Actual combined surface must not depend on ramp UUID/construction order.
        int mismatch=0,white=0;
        for(double z=-18;z<102;z+=.71) for(double x=-36;x<36;x+=.63) {
          V p=f.mul(z).add(side.mul(x)).add(new V(0,64,0));
          boolean ap=painted(ab,p),bp=painted(ba,p);
          if(ap||bp)white++;if(ap!=bp)mismatch++;
        }
        check(white>50,"real marking coverage");
        check(mismatch<=2,"construction order changes fork paint: "+mismatch);
        for(var face:ab) for(V p:face.points())
          check(RoadQueries.contains(a,p,.19,.15)||RoadQueries.contains(b,p,.19,.15),"fork paint off pavement");
        for(Mesh m:List.of(a,b)) {
          // A continuous unobstructed outer lane remains through the one-lane throat.
          for(double z=10;z<=48;z+=1) {
            double x=(m==a?-1:straight?0:1)*.0035*z*z;
            V center=f.mul(z).add(side.mul(x)).add(new V(0,64,0));
            var q=RoadQueries.horizontal(m,center);Sample at=q.sample();
            Mesh other=m==a?b:a;V toward=RoadQueries.horizontal(other,center).sample().center().sub(center);
            int sign=at.left().dot(toward)>0?1:-1;
            V lane=at.at(-sign*2,0);
            check(!painted(ab,lane),"live outer lane obstructed by fork hatch");
          }
          // Full two-lane branch recovers its middle divider after the transition.
          int hits=0;
          for(double z=92;z<102;z+=.4){double x=(m==a?-1:straight?0:1)*.0035*z*z;V p=f.mul(z).add(side.mul(x)).add(new V(0,64,0));if(painted(ab,p))hits++;}
          check(hits>4,"branch divider missing after taper");
        }
        int common=0;for(double z=-20;z<-3;z+=.4)if(painted(ab,f.mul(z).add(new V(0,64,0))))common++;
        check(common>10,"common two-lane divider lost");
        // Two inside bands must contain diagonals; a dashed common line alone fails.
        var guides=RoadJunction.markings(a,List.of(b),List.of(b),true);
        long diagonals=guides.stream().filter(g->{V p=g.points().get(0).add(g.points().get(1)).mul(.5),q=g.points().get(2).add(g.points().get(3)).mul(.5);V d=q.sub(p);var t=RoadQueries.horizontal(a,p.add(q).mul(.5)).tangent();return d.horizontalLength()>.1&&Math.abs(d.horizontalUnit().dot(t))<.9;}).count();
        check(diagonals>10,"two-lane split must have hatched inner closures");
        forks++;
        if(!left&&angle==0&&!reverse)export(straight?"collector-fork":"two-lane-fork",List.of(a,b),ab);
      }
  }
  static void closureFixtures() throws Exception {
    for(Style first:List.of(Style.H6_RAIL,Style.O6_GREEN))
      for(Style second:List.of(Style.H4_RAIL,Style.O4_YELLOW))
        for(var preset:List.of(InterchangePlanner.Preset.CLOVERLEAF,InterchangePlanner.Preset.STACK,
            InterchangePlanner.Preset.TRUMPET,InterchangePlanner.Preset.Y))
          for(boolean left:new boolean[]{false,true}) {
            var a=settings(first,left);var b=settings(second,left);
            var options=new InterchangePlanner.Options(preset,left,2,96,20,5,2);
            var nodes=InterchangeValidation.raised(600,left?.37:0,InterchangePlanner.minimumDifference(a,b,options),false);
            if(preset.three)nodes=Arrays.copyOf(nodes,3);
            var plan=InterchangePlanner.plan(nodes,a,b,options);
            var all=plan.legs().stream().map(InterchangePlanner.Leg::mesh).toList();
            for(Mesh ramp:all)if(ramp.settings().style().ramp()) for(Mesh host:all.subList(0,2)) {
              if(RoadProfile.layout(host,host.first()).shoulderWidth()<.1)continue;
              var guides=RoadJunction.markings(ramp,List.of(host),all.stream().filter(m->m!=ramp).toList(),true);
              for(boolean start:new boolean[]{true,false}) {
                Sample end=start?ramp.first():ramp.last();if(end.halfWidth()*2>=ramp.settings().width()*.7)continue;
                var hp=RoadQueries.horizontal(host,end.center());
                if(hp.horizontalDistance()>hp.sample().halfWidth()+end.halfWidth()||Math.abs(hp.sample().center().y()-end.center().y())>.1)continue;
                V forward=end.left().left().mul(start?-1:1);int count=0;
                for(var g:guides) {
                  V p=g.points().get(0).add(g.points().get(1)).mul(.5),q=g.points().get(2).add(g.points().get(3)).mul(.5);
                  V mid=p.add(q).mul(.5);if(mid.sub(end.center()).horizontalLength()>49||p.distance(q)<1)continue;
                  var pp=RoadQueries.horizontal(host,p);var qq=RoadQueries.horizontal(host,q);
                  double lp=Math.abs(pp.lateral()),lq=Math.abs(qq.lateral());
                  if(Math.abs(lp-lq)<.5)continue;
                  V inner=lp<lq?p:q,outer=lp<lq?q:p;
                  check(outer.sub(inner).dot(forward)>.4,"red-reference diagonal points the wrong way");
                  check(Math.max(p.sub(end.center()).dot(forward),q.sub(end.center()).dot(forward))<48.4,"hatch exceeds closure end");
                  count++;
                }
                check(count>=2,"entry/exit taper has multiple reference diagonals");closures++;
              }
            }
          }
    check(closures>80,"highway/road, 3/4-arm, mirrored entry and exit cases exercised");
  }
  static void export(String name,List<Mesh> meshes,List<RoadSurface.Face> paint)throws Exception {
    var svg=new StringBuilder("<svg xmlns=\"http://www.w3.org/2000/svg\" viewBox=\"-45 -26 90 134\">");
    var faces=new ArrayList<RoadSurface.Face>();
    for(int i=0;i<meshes.size();i++){var m=meshes.get(i);faces.addAll(RoadSurface.build(m,meshes.subList(0,i),meshes.stream().filter(n->n!=m).toList()).pavement());}
    faces.addAll(paint);
    for(var face:faces){svg.append("<polygon fill=\"").append(face.color()==0xEDEEE2?"#f2f1e8":"#626267").append("\" points=\"");for(V p:face.points())svg.append(p.x()).append(',').append(p.z()).append(' ');svg.append("\"/>");}
    svg.append("</svg>");Files.createDirectories(Path.of("validation/0.23.1"));Files.writeString(Path.of("validation/0.23.1/"+name+".svg"),svg);
  }
  public static void main(String[] args)throws Exception {
    forkFixtures();closureFixtures();
    System.out.println("REVISION 0.23.1 PASS: "+checks+" checks / "+forks+" fork fixtures / "+closures+" shoulder closures");
  }
}
