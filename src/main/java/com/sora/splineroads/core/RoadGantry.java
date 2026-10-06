package com.sora.splineroads.core;

import com.sora.splineroads.core.RoadGeometry.*;
import com.sora.splineroads.core.RoadInfrastructure.*;
import com.sora.splineroads.core.RoadStructures.*;
import java.util.*;

/** Deterministic per-road stations; edits affect one station and survive normal road rebuilds. */
public final class RoadGantry {
  public record Edit(int slot,double offset,double clearance,Gantry kind,boolean reverse) {
    public Edit {
      if(slot<0||slot>=256||kind==null||!RoadGeometry.finite(offset,clearance)
          ||Math.abs(offset)>256||clearance<5||clearance>16)
        throw new IllegalArgumentException("龙门架位移 -256–256 格，净高 5–16 格");
    }
    public static Edit defaults(int slot){return new Edit(slot,0,5.6,Gantry.AUTO,false);}
  }
  public record Station(int slot,double distance,Sample sample,Edit edit,Gantry kind) {}
  public static int count(Mesh m){return m.settings().structure()==Structure.TUNNEL||m.settings().style().ramp()&&!m.settings().style().connectorRamp()||m.length()<20?0:Math.max(1,(int)Math.floor(m.length()/m.settings().options().infrastructure().spacing()));}
  public static Edit edit(Config c,int slot){return c.gantryEdits().stream().filter(e->e.slot()==slot).findFirst().orElse(Edit.defaults(slot));}
  public static Station station(Mesh m,int slot){
    int n=count(m);if(slot<0||slot>=n)throw new IllegalArgumentException("该龙门架位置已失效，请重新选择");
    var c=m.settings().options().infrastructure();var e=edit(c,slot);double step=m.length()/n;
    if(Math.abs(e.offset())>step/2-3)throw new IllegalArgumentException(String.format(Locale.ROOT,"该龙门架可前后移动最多 %.1f 格",Math.max(0,step/2-3)));
    double d=(slot+.5)*step+e.offset();Sample s=RoadStructures.sample(m,d);
    Gantry kind=e.kind()==Gantry.AUTO?c.gantry():e.kind();
    if(kind==Gantry.FRAME){
      // Cardinal equipment trusses keep a grid-aligned mounting envelope.
      V f=s.left().left().mul(-1),p=s.center();
      if(Math.abs(f.x())>.999999||Math.abs(f.z())>.999999){
        double coordinate=Math.abs(f.x())>.999999?p.x():p.z();
        double move=(Math.floor(coordinate)+.5-coordinate)*(Math.abs(f.x())>.999999?f.x():f.z());
        d+=move;s=RoadStructures.sample(m,d);
      }
    }
    return new Station(slot,d,s,e,kind);
  }
  private static boolean enabled(Mesh m,Ground ground,Station st){
    if(st.kind()==Gantry.OFF)return false;var s=st.sample();
    if(st.kind()==Gantry.AUTO&&m.settings().options().infrastructure().signs().stream().noneMatch(a->a.mount()==RoadSigns.Mount.GANTRY&&a.station()==st.slot())&&!RoadProfile.highway(m.settings().style())&&m.settings().structure()!=Structure.BRIDGE){
      if(!Double.isFinite(ground.top(s.center().x(),s.center().z(),s.center().y()))||!RoadStructures.elevated(m,s,ground))return false;
    }
    return !ground.furnitureClear(s.center())&&!ground.joined(s.center());
  }
  public static boolean near(Mesh m,Ground ground,double d){
    for(int i=0;i<count(m);i++){var st=station(m,i);if(Math.abs(d-st.distance())<5&&enabled(m,ground,st))return true;}return false;
  }
  public static List<Part> plan(Mesh m,Ground ground){
    var out=new ArrayList<Part>();
    for(int i=0;i<count(m);i++){
      var st=station(m,i);if(!enabled(m,ground,st))continue;
      var parts=parts(m,st,ground);
      if(parts.stream().anyMatch(ground::blocked)){
        if(m.settings().options().infrastructure().gantryEdits().stream().anyMatch(e->e.slot()==st.slot()))
          throw new IllegalArgumentException("此龙门架位置与其他道路或设施冲突，请调整位移或净高");
      }else out.addAll(parts);
    }
    return List.copyOf(out);
  }
  private static V at(Sample s,double lateral,double up){return s.at(lateral,-up);}
  private static void beam(List<Part> out,V a,V b,double width,double height,Material mat){out.add(new Part(a,b,width,height,false,mat));}
  private static void post(List<Part> out,V a,double width,double height,Material mat){if(height>.001)out.add(new Part(a,a,width,height,true,mat));}
  public static double lowerBeamY(Station st){double y=st.sample().center().y()+st.edit().clearance()+1.5;return st.kind()==Gantry.FRAME?Math.ceil(y):y;}
  public static List<Part> parts(Mesh mesh,Station st,Ground ground){
    if(st.kind()==Gantry.OFF)return List.of();
    var s=st.sample();var profile=RoadProfile.catalog(mesh.settings().style());var parts=new ArrayList<Part>();
    boolean equipment=st.kind()==Gantry.FRAME;
    double edge=s.halfWidth()+1.3,clear=st.edit().clearance(),lower=clear+1.5;
    if(equipment)lower=Math.ceil(s.center().y()+lower)-s.center().y();
    V left=at(s,-edge,0),right=at(s,edge,0);
    if(equipment&&(Math.abs(s.left().x())>.999999||Math.abs(s.left().z())>.999999)){
      left=new V(Math.floor(left.x())+.5,left.y(),Math.floor(left.z())+.5);
      right=new V(Math.floor(right.x())+.5,right.y(),Math.floor(right.z())+.5);
    }
    for(V foot:List.of(left,right)){
      double underside=s.center().y()-mesh.settings().thickness();
      // Probe from above the surface, not from inside the road slab: the latter
      // truncates the terrain height and excavates a pit around a ground gantry.
      double floor=ground.top(foot.x(),foot.z(),s.center().y()+2);
      boolean grounded=Double.isFinite(floor)&&Math.abs(s.center().y()-floor)<2;
      var walk=mesh.settings().options().sidewalk();int walkSide=foot.sub(s.center()).dot(s.left())<0?-1:1;
      boolean onWalk=walk.enabled()&&walk.smooth()&&(walk.side()==RoadSidewalks.Side.BOTH||walk.side()==RoadSidewalks.Side.LEFT&&walkSide<0||walk.side()==RoadSidewalks.Side.RIGHT&&walkSide>0)&&Math.abs(foot.sub(s.center()).dot(s.left()))+.6<=s.halfWidth()+walk.width();
      double base=onWalk?s.center().y()+.2:grounded?floor:s.center().y();
      post(parts,new V(foot.x(),base,foot.z()),.65,s.center().y()+lower+.9-base,Material.STEEL);
      post(parts,new V(foot.x(),base,foot.z()),1.2,.45,Material.CONCRETE);
      post(parts,new V(foot.x(),base+.45,foot.z()),.85,.12,Material.DARK_STEEL);
      double side=foot.sub(s.center()).dot(s.left())<0?-1:1;
      if(!grounded&&!onWalk){
        beam(parts,at(s,side*(s.halfWidth()-.5),-.57),new V(foot.x(),s.center().y()-.57,foot.z()),1.2,.45,Material.CONCRETE);
        post(parts,new V(foot.x(),s.center().y()-.12,foot.z()),1.2,.12,Material.CONCRETE);
      }
    }
    if(equipment){
      // Open box truss: grid-aligned mounting faces, without the old solid 1x1 bar.
      V forward=s.left().left().mul(-1);
      beam(parts,left.add(new V(0,lower,0)),right.add(new V(0,lower,0)),1,.18,Material.GANTRY_FRAME);
      beam(parts,left.add(new V(0,lower+.82,0)),right.add(new V(0,lower+.82,0)),1,.18,Material.GANTRY_FRAME);
      V axis=right.sub(left);int bays=Math.max(2,(int)Math.ceil(axis.horizontalLength()/2.8));
      for(int i=0;i<bays;i++)for(int face:new int[]{-1,1}){
        V a=left.add(axis.mul((double)i/bays)).add(forward.mul(face*.42));
        V b=left.add(axis.mul((double)(i+1)/bays)).add(forward.mul(face*.42));
        beam(parts,a.add(new V(0,lower+(i%2==0?.18:.72),0)),b.add(new V(0,lower+(i%2==0?.72:.18),0)),.12,.1,Material.GANTRY_FRAME);
      }
      for(V foot:List.of(left,right)){
        double sign=foot.sub(s.center()).dot(s.left())<0?1:-1;
        beam(parts,foot.add(new V(0,lower-1.25,0)),foot.add(s.left().mul(sign*1.6)).add(new V(0,lower,0)),.22,.22,Material.STEEL);
      }
      return List.copyOf(parts);
    }
    beam(parts,at(s,-edge,lower),at(s,edge,lower),.45,.45,Material.STEEL);
    beam(parts,at(s,-edge,lower+.7),at(s,edge,lower+.7),.45,.3,Material.STEEL);
    for(double u=-edge;u<edge;u+=3)beam(parts,at(s,u,lower+.15),at(s,Math.min(edge,u+3),lower+.7),.12,.12,Material.DARK_STEEL);
    return List.copyOf(parts);
  }
  private RoadGantry(){}
}
