package com.sora.splineroads;
import com.sora.splineroads.core.*;
import com.sora.splineroads.core.RoadGeometry.*;
import com.sora.splineroads.core.RoadInfrastructure.*;
import com.sora.splineroads.core.RoadStructures.*;
import java.util.*;

public final class Revision29Validation {
  static int checks;
  static void check(boolean value,String message){checks++;if(!value)throw new AssertionError(message);}
  static Ground ground(){return Revision28Validation.ground(0);}
  static Settings settings(Style style,Structure type,Config c){return Revision28Validation.settings(style,type,c);}
  static Mesh mesh(Settings s,double length,double y,double z,double yaw){return RoadGeometry.build(new Node(new V(.5,y,.5),yaw,0),new Node(new V(.5+length,y+z,.5),yaw,0),s);}
  static boolean hits(List<Part> parts,V p){return Revision28Validation.hits(RoadRaster.structures(parts,null),p);}
  public static void main(String[] args)throws Exception{
    for(Style style:new Style[]{Style.O6_YELLOW,Style.H6_RAIL,Style.O2_ONE})for(boolean left:new boolean[]{false,true})for(double yaw:new double[]{-90,90,0,180}){
      var s=settings(style,Structure.BRIDGE,Config.DEFAULT.grade(.2)).options(RoadProfile.Options.DEFAULT.infrastructure(Config.DEFAULT.grade(.2)).traffic(left));
      Node a=new Node(new V(.5,18,.5),yaw,0);Node b=new Node(a.position().add(a.direction().mul(96)),yaw,0);
      var m=RoadGeometry.build(a,b,s);var station=RoadGantry.station(m,0);var center=station.sample();V f=center.left().left().mul(-1);
      for(var part:RoadGantry.parts(m,station,ground()))if(part.material()==Material.SIGN_WHITE){
        V delta=part.a().sub(center.center());double lateral=delta.dot(center.left());
        double flow=RoadProfile.catalog(style).twoWay()?Math.signum(lateral)*RoadProfile.trafficSign(left):1;
        check(delta.dot(f)*flow<-.05,"sign front faces oncoming traffic for both driving sides and headings");
      }
      var edited=s.options(s.options().infrastructure(s.options().infrastructure().edit(new RoadGantry.Edit(0,4,7,Gantry.SIGNS,true))));
      var changed=RoadGeometry.build(a,b,edited);var next=RoadGantry.station(changed,0);
      check(Math.abs(next.distance()-station.distance()-4)<1e-7,"single station moves along road");
    }
    for(Bridge bridge:Bridge.values()){
      var c=Config.DEFAULT.grade(.2).bridge(bridge).gantry(Gantry.OFF);var m=Revision28Validation.mesh(settings(Style.O4_YELLOW,Structure.BRIDGE,c),96,false);var parts=RoadStructures.plan(m,ground());
      if(bridge!=Bridge.STANDARD){var cells=RoadRaster.structures(parts,null);double reach=bridge==Bridge.ARCH?1.15:bridge==Bridge.CABLE||bridge==Bridge.SUSPENSION?2:.25;
        for(double d=5;d<90;d+=7){var q=RoadStructures.sample(m,d);for(int side:new int[]{-1,1})for(double off=.01;off<reach*RoadInfrastructure.endTaper(m,q);off+=.1)
          check(Revision28Validation.hits(cells,q.at(side*(q.halfWidth()+off),.5)),"continuous edge connection for "+bridge);}
      }
    }
    for(Tunnel type:Tunnel.values()){
      var m=Revision28Validation.mesh(settings(Style.O6_YELLOW,Structure.TUNNEL,Config.DEFAULT.grade(.2).tunnel(type)),48,true);var parts=RoadStructures.plan(m,ground());
      for(var p:parts)if(p.luminous()&&p.width()==.22)for(V v:List.of(p.a(),p.b())){
        var q=RoadQueries.horizontal(m,v);double roof=RoadInfrastructure.ceiling(m.settings().options().infrastructure(),q.sample().halfWidth(),q.lateral())+q.sample().center().y();
        check(Math.abs(v.y()+p.height()-roof)<.1,"arched/box lights touch actual ceiling without hangers");
      }
      check(parts.stream().noneMatch(p->p.material()==Material.STEEL&&p.width()==.08),"no suspended arch lamp rods");
      var c=Config.DEFAULT.grade(.2).tunnel(type).depth(8);var dipping=mesh(settings(Style.O2_YELLOW,Structure.TUNNEL,c),240,24,3,-90);
      check(Math.abs(dipping.first().center().y()-24)<1e-8&&Math.abs(dipping.last().center().y()-27)<1e-8,"mouth positions retained");
      check(Math.abs(dipping.samples().stream().mapToDouble(p->p.center().y()).min().orElseThrow()-16)<1e-8,"lowest Y equals lower mouth minus depth");
      double previous=24;boolean rising=false;
      for(var p:dipping.samples()){
        if(p.center().y()>previous+1e-8)rising=true;
        if(rising)check(p.center().y()>=previous-1e-8,"no repeated dip after bottom");previous=p.center().y();
      }
      check(dipping.samples().stream().filter(p->Math.abs(p.center().y()-16)<1e-7).count()>20,"level low segment generated");
      Revision28Validation.export("DIP_"+type,dipping,RoadStructures.plan(dipping,ground()));
    }
    boolean rejected=false;try{mesh(settings(Style.O2_YELLOW,Structure.TUNNEL,Config.DEFAULT.grade(.2).depth(20)),50,24,0,-90);}catch(IllegalArgumentException expected){rejected=true;}check(rejected,"over-steep dip rejected");
    var m=mesh(settings(Style.H4_RAIL,Structure.BRIDGE,Config.DEFAULT.grade(.2).gantry(Gantry.FRAME)),256,18.25,0,-90);
    var st=RoadGantry.station(m,0);var frame=RoadGantry.parts(m,st,ground()).stream().filter(p->p.material()==Material.GANTRY_FRAME).findFirst().orElseThrow();
    for(V p:frame.base())check(Math.abs(p.x()-Math.rint(p.x()))<1e-7&&Math.abs(p.y()-Math.rint(p.y()))<1e-7,"equipment attachment faces follow block grid");
    check(frame.width()==1&&frame.height()==.18,"equipment truss has a grid-wide mounting chord");
    var cfg=m.settings().options().infrastructure().edit(new RoadGantry.Edit(0,8,8,Gantry.SIGNS,false));
    var edited=mesh(m.settings().options(m.settings().options().infrastructure(cfg)),256,18.25,0,-90);
    check(RoadGantry.parts(m,RoadGantry.station(m,1),ground()).equals(RoadGantry.parts(edited,RoadGantry.station(edited,1),ground())),"editing first gantry leaves second gantry exact");
    // Reproduce light samples behind an opaque ceiling: the old +Y query is black.
    var ceiling=new Part(new V(0,10,0),new V(12,10,0),4,1,false,Material.TUNNEL);
    RoadLighting.Access light=new RoadLighting.Access(){public boolean opaque(int x,int y,int z){return y>=10;}public int packed(int x,int y,int z){return y>=10?0:9<<4;}};
    for(var face:ceiling.faces())if(RoadLighting.normal(face).y()<-.9)for(V p:face.points())check(RoadLighting.sample(face,p,light)==9<<4,"ceiling underside samples lit air instead of roof solid");
    var downward=new RoadSurface.Face(List.of(new V(0,4,0),new V(8,4,8),new V(0,4,8)),0xDCDCDC);
    RoadLighting.Access pavementLight=new RoadLighting.Access(){public boolean opaque(int x,int y,int z){return false;}public int packed(int x,int y,int z){return y<4?0:8<<4;}};
    check(RoadLighting.sample(downward,downward.points().get(0),pavementLight,true)==8<<4,"pavement light follows the same upward winding as the renderer");
    var narrow=new RoadSurface.Face(List.of(new V(0,4,5),new V(0,4,-5),new V(24,4,-5)),0xDCDCDC);
    RoadLighting.Access wallLight=new RoadLighting.Access(){public boolean opaque(int x,int y,int z){return z>=4||z<=-5;}public int packed(int x,int y,int z){return opaque(x,y,z)?0:7<<4;}};
    check(RoadLighting.sample(narrow,narrow.points().get(0),wallLight,true)==7<<4,"long pavement triangle escapes the opaque wall voxel at its corner");
    var config=Config.DEFAULT.grade(.2).depth(6).edit(new RoadGantry.Edit(1,3,7,Gantry.FRAME,true));
    check(config.bridge(Bridge.ARCH).headroom(8).spacing(96).gantry(Gantry.OFF).tunnel(Tunnel.ARCH).gantryEdits().equals(config.gantryEdits()),"other facility edits preserve per-gantry overrides");
    System.out.println("Revision29 PASS "+checks+" checks");
  }
}
