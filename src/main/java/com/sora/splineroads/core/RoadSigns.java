package com.sora.splineroads.core;
import com.sora.splineroads.core.RoadGeometry.*;
import com.sora.splineroads.core.RoadStructures.*;
import java.util.*;
/** Road-relative attachments; no detached block entities or per-tick geometry rebuilding. */
public final class RoadSigns {
  public enum Mount { GANTRY("龙门架"), POLE("路侧杆");public final String label;Mount(String s){label=s;} }
  public record Attachment(int id,String model,Mount mount,int station,double position,double lateral,double height,double scale,boolean reverse,List<String> text){
    public Attachment {
      if(id<0||id>=128||mount==null||station<0||station>=256||!RoadGeometry.finite(position,lateral,height,scale)||position<0||position>1||Math.abs(lateral)>128||height<1||height>16||scale<.5||scale>3)throw new IllegalArgumentException("安装位置或尺寸无效；高度 1–16，缩放 0.5–3");
      var catalog=RoadSignCatalog.get(model);if(text.size()>16||text.stream().anyMatch(s->s==null||s.length()>80||s.codePoints().anyMatch(c->c<32||c==127||c==167)))throw new IllegalArgumentException("每项文字最多 80 字，不能包含控制符");
      text=List.copyOf(text);if(catalog.depth()*scale>8)throw new IllegalArgumentException("设备尺寸过大");
    }
  }
  public record Placement(Attachment attachment,Part part,V foot,V front,double scale){}
  public static Placement place(Mesh mesh,Attachment a){
    Sample s;double lateral=a.lateral(),height=a.height();
    if(a.mount()==Mount.GANTRY){var st=RoadGantry.station(mesh,a.station());if(st.kind()==RoadInfrastructure.Gantry.OFF)throw new IllegalArgumentException("该龙门架已关闭，请先启用");s=st.sample();if(Math.abs(lateral)>s.halfWidth())throw new IllegalArgumentException("横向位置超出龙门架范围");if(height<5)throw new IllegalArgumentException("龙门架设施底缘净高至少 5 格");}
    else {s=RoadStructures.sample(mesh,mesh.length()*a.position());double sign=lateral<0?-1:1;lateral=sign*(s.halfWidth()+Math.max(.65,Math.abs(lateral)));}
    var model=RoadSignCatalog.get(a.model());V right=s.left().mul(a.reverse()?-1:1),front=right.left();V foot=s.at(lateral,0);
    V origin=foot.add(new V(0,height,0));double w=model.width()*a.scale();
    Part part=new Part(origin.sub(right.mul(w/2)),origin.add(right.mul(w/2)),model.depth()*a.scale(),model.height()*a.scale(),false,Material.CB_SIGN,null,null,a.model());
    return new Placement(a,part,foot,front,a.scale());
  }
  public static List<Part> plan(Mesh mesh,Ground ground){
    var out=new ArrayList<Part>();var installed=new ArrayList<Part>();
    for(var a:mesh.settings().options().infrastructure().signs()){
      var p=place(mesh,a);var part=p.part();
      for(var corner:part.base()){var q=RoadQueries.horizontal(mesh,corner);if(Math.abs(q.lateral())<q.sample().halfWidth()-.15&&corner.y()<q.sample().center().y()+4.25&&corner.y()+part.height()>q.sample().center().y()+.5)throw new IllegalArgumentException("标牌伸入本路通行空间，请抬高或向外移动");}
      for(var old:installed)if(Math.abs(old.a().add(old.b()).mul(.5).sub(part.a().add(part.b()).mul(.5)).dot(p.front()))<(old.width()+part.width())/2-.01&&Math.max(old.a().y(),part.a().y())<Math.min(old.a().y()+old.height(),part.a().y()+part.height())-.02){
        var lo=old.base();var now=part.base();double ax=lo.stream().mapToDouble(V::x).min().orElseThrow(),bx=lo.stream().mapToDouble(V::x).max().orElseThrow(),az=lo.stream().mapToDouble(V::z).min().orElseThrow(),bz=lo.stream().mapToDouble(V::z).max().orElseThrow();
        if(now.stream().mapToDouble(V::x).max().orElseThrow()>ax+.01&&now.stream().mapToDouble(V::x).min().orElseThrow()<bx-.01&&now.stream().mapToDouble(V::z).max().orElseThrow()>az+.01&&now.stream().mapToDouble(V::z).min().orElseThrow()<bz-.01)throw new IllegalArgumentException("两件标牌或设备重叠，请调整横向位置或高度");
      }installed.add(part);
      var assembly=new ArrayList<Part>();assembly.add(part);
      V back=p.foot().sub(p.front().mul(part.width()/2+.14));
      if(a.mount()==Mount.POLE){
        double floor=ground.top(back.x(),back.z(),back.y()+2);boolean grounded=Double.isFinite(floor)&&Math.abs(floor-back.y())<2;double y=grounded?floor:back.y()-mesh.settings().thickness();
        if(!grounded){var sample=RoadStructures.sample(mesh,a.position()*mesh.length());double side=a.lateral()<0?-1:1;V root=sample.at(side*(sample.halfWidth()-.25),mesh.settings().thickness());assembly.add(new Part(root,new V(back.x(),y,back.z()),.65,mesh.settings().thickness(),false,Material.CONCRETE));}
        if(a.height()<2.5&&Math.abs(a.lateral())<part.a().distance(part.b())/2+.65)throw new IllegalArgumentException("低位路牌请向路外移动，避免占用通行空间");
        out.add(new Part(new V(back.x(),y,back.z()),new V(back.x(),y,back.z()),.65,.3,true,Material.CONCRETE));
        assembly.add(new Part(new V(back.x(),y+.3,back.z()),new V(back.x(),y+.3,back.z()),.15,part.a().y()+part.height()*.75-y-.3,true,Material.STEEL));
      }
      double bottom=part.a().y(),top=bottom+part.height();
      double support=a.mount()==Mount.GANTRY?RoadGantry.lowerBeamY(RoadGantry.station(mesh,a.station()))+.09:top-.1;
      V center=part.a().add(part.b()).mul(.5);
      assembly.add(new Part(new V(back.x(),Math.min(bottom+part.height()*.55,support),back.z()),new V(back.x(),Math.min(bottom+part.height()*.55,support),back.z()),.12,Math.max(.15,Math.abs(support-(bottom+part.height()*.55))+.12),true,Material.DARK_STEEL));
      assembly.add(new Part(center.add(new V(0,part.height()*.65,0)),new V(back.x(),bottom+part.height()*.65,back.z()),.12,.12,false,Material.DARK_STEEL));
      if(assembly.stream().anyMatch(ground::blocked))throw new IllegalArgumentException("路牌或路杆与其他道路冲突，请调整位置或高度");out.addAll(assembly);
    }
    var result=new ArrayList<Part>();
    for(var p:out){if(result.contains(p))continue;int same=-1;if(p.pier())for(int i=0;i<result.size();i++){var old=result.get(i);if(old.pier()&&old.a().equals(p.a())&&old.width()==p.width()&&old.material()==p.material()){same=i;break;}}
      if(same<0)result.add(p);else if(result.get(same).height()<p.height())result.set(same,p);
    }return List.copyOf(result);
  }
  private RoadSigns(){}
}
