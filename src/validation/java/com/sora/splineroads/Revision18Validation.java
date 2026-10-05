package com.sora.splineroads;

import com.sora.splineroads.core.*;
import com.sora.splineroads.core.RoadGeometry.*;
import com.sora.splineroads.core.RoadStructures.*;
import java.util.*;
import java.nio.file.*;

/** Regressions for the five user annotations after 0.17. */
public final class Revision18Validation {
  private static int checks, cases;
  private static void check(boolean yes,String why){checks++;if(!yes)throw new AssertionError(why);}
  private static Settings settings(Style s){return new Settings(Mode.STRAIGHT,s,s.defaultWidth(),1,.4,90);}
  public static void main(String[] args)throws Exception{run();}
  public static void run()throws Exception{
    gores();medians();arms();
    System.out.println("REVISION 18 PASS: "+checks+" assertions across "+cases+" annotation regressions");
  }
  private static void gores()throws Exception{
    for(Style style:List.of(Style.H6_RAIL,Style.O6_GREEN))
      for(var preset:List.of(InterchangePlanner.Preset.CLOVERLEAF,InterchangePlanner.Preset.STACK,
          InterchangePlanner.Preset.TRUMPET,InterchangePlanner.Preset.Y))
        for(boolean left:new boolean[]{false,true})for(int lanes:new int[]{1,2}){
          cases++;var s=settings(style);var o=new InterchangePlanner.Options(preset,left,lanes,96,20,5,2);
          var n=InterchangeValidation.raised(600,0,InterchangePlanner.minimumDifference(s,s,o),false);
          if(preset.three)n=Arrays.copyOf(n,3);
          var plan=InterchangePlanner.plan(n,s,s,o);var all=plan.legs().stream().map(InterchangePlanner.Leg::mesh).toList();
          int stripes=0,diagonals=0;
          for(var leg:plan.legs()){
            Mesh ramp=leg.mesh();if(!ramp.settings().style().ramp())continue;
            for(Mesh host:all.subList(0,2)){
              // Ordinary three-arm terminals have a real lane drop and retain its markings.
              if(host.settings().options().ends().persistent())continue;
              var guides=RoadJunction.markings(ramp,List.of(host),all.stream().filter(x->x!=ramp).toList());
              for(var g:guides){
                V a=g.points().get(0).add(g.points().get(1)).mul(.5);
                V b=g.points().get(2).add(g.points().get(3)).mul(.5),middle=a.add(b).mul(.5);
                var q=RoadQueries.horizontal(host,middle);V forward=q.tangent().horizontalUnit();
                double along=Math.abs(b.sub(a).dot(forward)),across=Math.abs(b.sub(a).dot(q.sample().left()));
                if(style==Style.O6_GREEN){
                  check(across<.02,"unchanged ordinary mainline must have boundary dashes only: "+preset+" "+across);
                  continue;
                }
                if(across<.15||along<.1||a.distance(b)<.45)continue;
                stripes++;
                boolean tip=List.of(ramp.first(),ramp.last()).stream().anyMatch(e->
                    e.halfWidth()*2<ramp.settings().width()*.7&&e.center().sub(middle).horizontalLength()<50);
                if(tip){
                  check(along/across>.75&&along/across<1.25,"terminal hatch is diagonal, not ladder-like: "+along/across);
                  diagonals++;continue;
                }
                for(V v:g.points()){
                  var hp=RoadQueries.horizontal(host,v);var rp=RoadQueries.horizontal(ramp,v);
                  int outside=hp.lateral()>0?1:-1;
                  double motor=RoadProfile.layout(host,hp.sample()).outer(outside);
                  check((hp.lateral()-motor)*outside>=-.13,"gore enters main motor lanes");
                  int toward=rp.sample().left().dot(hp.sample().center().sub(rp.sample().center()))>0?1:-1;
                  var layout=RoadProfile.layout(ramp,rp.sample());
                  check((rp.lateral()-layout.outer(toward))*toward>=-.16,
                      "gore enters ramp motor lane: "+preset+" "+lanes+" lateral="+rp.lateral()+" boundary="+layout.outer(toward));
                }
              }
            }
          }
          if(style==Style.H6_RAIL){check(stripes>3,"highway gore strokes exercised");check(diagonals>2,"shoulder taper diagonals exercised");}
          if(preset==InterchangePlanner.Preset.CLOVERLEAF&&!left&&lanes==2){
            var leg=plan.legs().stream().filter(l->l.name().endsWith(" 右转")).findFirst().orElseThrow();
            Mesh ramp=RoadRenderMesh.simplify(leg.mesh());
            Mesh host=RoadRenderMesh.simplify(all.get(leg.to()%2));
            Sample e=ramp.last(),h=RoadQueries.horizontal(host,e.center()).sample();
            int side=h.left().dot(e.center().sub(h.center()))>0?1:-1;
            V origin=h.at(RoadProfile.layout(host,h).outer(side),0);
            var geom=RoadSurface.build(host,List.of(),List.of(ramp));
            var second=RoadSurface.build(ramp,List.of(host),List.of(host));
            var faces=new ArrayList<RoadSurface.Face>(geom.pavement());faces.addAll(second.pavement());
            faces.addAll(geom.markings());faces.addAll(second.markings());
            export("gore-"+style,faces,origin,e.left().left().mul(-1),h.left().mul(side),-170,-8,180,30);
          }
        }
  }
  private static void medians()throws Exception{
    for(Style style:List.of(Style.O4_GREEN,Style.O6_GREEN))
      for(var preset:List.of(InterchangePlanner.Preset.DIAMOND,InterchangePlanner.Preset.SPUI))
        for(double rotation:new double[]{0,.37})for(boolean left:new boolean[]{false,true}){
          cases++;var s=settings(style);var o=new InterchangePlanner.Options(preset,left,1,96,20,5,1);
          var plan=InterchangePlanner.plan(InterchangeValidation.raised(600,rotation,
              InterchangePlanner.minimumDifference(s,s,o),true),s,s,o);
          var all=plan.legs().stream().map(InterchangePlanner.Leg::mesh).toList();
          Mesh road=all.get(1);var neighbors=all.stream().filter(m->m!=road).toList();
          var approaches=RoadSignals.approaches(road,neighbors);check(approaches.size()>=2,"opposing stop planes found");
          var ground=new Ground(){
            public double top(double x,double z,double y){return 64;}
            public boolean blocked(Part p){return false;}
            public boolean joined(V p){return neighbors.stream().anyMatch(m->RoadQueries.joins(road,m,p));}
            public boolean furnitureClear(V p){return RoadSignals.furnitureClear(p,approaches);}
          };
          var parts=RoadStructures.plan(road,ground);var leaves=parts.stream().filter(p->p.material()==Material.GREEN).toList();
          check(!leaves.isEmpty(),"greenery exists on the ground road");
          for(var a:approaches){
            double end=leaves.stream().flatMap(p->List.of(p.a(),p.b()).stream())
                .mapToDouble(p->p.sub(a.inner()).dot(a.forward())).filter(d->d<=.1).max().orElseThrow();
            check(end>=-.215&&end<=-.185,"median reaches stop-line edge without missing tile: "+preset+style+" gap="+(-end));
            for(var p:leaves){
              double da=p.a().sub(a.inner()).dot(a.forward()),db=p.b().sub(a.inner()).dot(a.forward());
              check(!(Math.min(da,db)<.4&&Math.max(da,db)>.4),"greenery does not cross the intersection opening");
            }
            var faces=RoadRenderMesh.structureFaces(parts,false);
            check(faces.stream().anyMatch(f->f.texture()==RoadSurface.Texture.LEAVES&&f.points().stream()
                .allMatch(v->Math.abs(v.sub(a.inner()).dot(a.forward())+.2)<.02)),"visible leaf cap at stop plane");
          }
          if(style==Style.O6_GREEN&&preset==InterchangePlanner.Preset.DIAMOND&&rotation==0&&!left){
            var a=approaches.get(0);var geometry=RoadSurface.build(RoadRenderMesh.simplify(road),List.of(),neighbors);
            var faces=new ArrayList<RoadSurface.Face>(geometry.pavement());faces.addAll(geometry.markings());
            faces.addAll(RoadRenderMesh.structureFaces(parts,false));
            export("median-stop",faces,RoadStructures.sample(road,a.station()).center(),a.forward(),a.forward().left(),-8,-4,12,8);
          }
        }
  }
  private static void arms(){
    for(double angle:new double[]{0,.37,1.57,2.4}){
      cases++;V f=new V(Math.sin(angle),0,Math.cos(angle)),head=new V(10,71.35,30);
      V mount=head.add(f.mul(.42)),post=mount.add(f.left().mul(7)).add(new V(0,-7.35,0));
      var a=new RoadSignals.Approach(new V(0,64,0),new V(10,64,0),post,head,f,false,0,1,10);
      var parts=RoadSignals.structures(List.of(a));
      Part arm=parts.stream().filter(p->p.material()==Material.CB_ARM).findFirst().orElseThrow();
      check(Math.abs(arm.b().sub(arm.a()).horizontalLength()-8)<1e-7,"crossarm extended exactly one block");
      check(Math.abs(arm.b().sub(mount).dot(f.left())+1)<1e-7,"extension is past the lamp, away from the post");
      Part lamp=parts.stream().filter(RoadSignals::signal).findFirst().orElseThrow();
      check(lamp.a().add(lamp.b()).mul(.5).distance(head)<1e-7,"lamp position and height unchanged");
    }
  }
  private static void export(String name,List<RoadSurface.Face> faces,V origin,V forward,V side,
      double x,double y,double w,double h)throws Exception{
    StringBuilder s=new StringBuilder("<svg xmlns=\"http://www.w3.org/2000/svg\" viewBox=\""+x+" "+y+" "+w+" "+h+"\"><rect x=\""+x+"\" y=\""+y+"\" width=\""+w+"\" height=\""+h+"\" fill=\"#47654a\"/>");
    for(var face:faces){
      String color=face.texture()==RoadSurface.Texture.LEAVES?"#538347":face.texture()==RoadSurface.Texture.SOIL?"#654832":face.color()==0xEDEEE2?"#f2f1e8":face.texture()==RoadSurface.Texture.CONCRETE?"#ababab":"#626267";
      s.append("<polygon fill=\"").append(color).append("\" points=\"");
      for(V p:face.points()){V q=p.sub(origin);s.append(q.dot(forward)).append(',').append(q.dot(side)).append(' ');}
      s.append("\"/>");
    }
    s.append("</svg>");Files.createDirectories(Path.of("validation"));Files.writeString(Path.of("validation/revision18-"+name+".svg"),s);
  }
}
