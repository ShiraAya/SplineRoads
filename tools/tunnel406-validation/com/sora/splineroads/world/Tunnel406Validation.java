package com.sora.splineroads.world;
import com.sora.splineroads.core.*;
import com.sora.splineroads.core.RoadGeometry.*;
import net.minecraft.core.BlockPos;
import java.util.*;

/** Real infrastructure faces, clearance index and raster; no actual Level or GPU. */
public final class Tunnel406Validation {
  static int checks,cases;static long id=406;static boolean shellOnly;
  static void check(boolean ok,String message){checks++;if(!ok)throw new AssertionError(message);}
  static V cross(V a,V b){return new V(a.y()*b.z()-a.z()*b.y(),a.z()*b.x()-a.x()*b.z(),a.x()*b.y()-a.y()*b.x());}
  static double ray(V o,V d,List<RoadSurface.Face> faces){double nearest=Double.POSITIVE_INFINITY;for(var face:faces){var p=face.points();for(int i=1;i+1<p.size();i++){
    V e=p.get(i).sub(p.get(0)),f=p.get(i+1).sub(p.get(0)),h=cross(d,f);double det=e.dot(h);if(Math.abs(det)<1e-9)continue;
    V delta=o.sub(p.get(0));double u=delta.dot(h)/det;if(u< -1e-8||u>1+1e-8)continue;V q=cross(delta,e);double v=d.dot(q)/det;if(v< -1e-8||u+v>1+1e-8)continue;double t=f.dot(q)/det;if(t>=0)nearest=Math.min(nearest,t);
  }}return nearest;}
  static RoadStructures.Ground ground=new RoadStructures.Ground(){public double top(double x,double z,double ceiling){return ceiling-20;}public boolean blocked(RoadStructures.Part p){return false;}public boolean joined(V p){return false;}};
  static BlockPos pos(V p){return new BlockPos((int)Math.floor(p.x()),(int)Math.floor(p.y()),(int)Math.floor(p.z()));}
  public static void main(String[]args){
    shellOnly=args.length>0&&args[0].equals("shell-only");
    for(var type:RoadInfrastructure.Tunnel.values())for(var style:List.of(Style.O2_ONE,Style.O4_RAIL,Style.O8_GREEN))for(boolean reverse:new boolean[]{false,true})for(double angle:new double[]{0,.61})run(type,style,reverse,angle);
    System.out.println("Tunnel406Validation: "+cases+" box/arch width/grade/direction fixtures, "+checks+" shell-ray and eager/local excavation checks PASS. Actual geometry/index; no Minecraft blocks or GPU.");
  }
  static void run(RoadInfrastructure.Tunnel type,Style style,boolean reverse,double angle){
    var config=RoadInfrastructure.Config.DEFAULT.tunnel(type);var options=RoadProfile.Options.DEFAULT.infrastructure(config);var settings=new Settings(Mode.STRAIGHT,style,RoadProfile.width(style,options,4),1,.35,90).options(options).structure(Structure.TUNNEL);
    V direction=new V(Math.sin(angle),.025,Math.cos(angle)),a=new V(-90.2,100,32.3),b=a.add(direction.mul(64));if(reverse){V temp=a;a=b;b=temp;direction=direction.mul(-1);}
    var na=new Node(a,RoadPlanner.yaw(direction.horizontalUnit()),direction.y());var nb=new Node(b,na.yaw(),na.grade());
    var road=new RoadRecord(new UUID(406,++id),new UUID(406,1),pos(a),pos(b),na,nb,settings,true,4);var mesh=road.mesh();var parts=RoadInfrastructure.plan(mesh,ground);road=road.structures(parts);cases++;
    for(boolean far:new boolean[]{false,true}){
      var faces=RoadRenderMesh.structureFaces(parts.stream().filter(p->p.material()==RoadStructures.Material.TUNNEL).toList(),far);
      for(double station:new double[]{.6,7.3,31.3,63.4})for(int side:new int[]{-1,1}){
        var s=RoadStructures.sample(mesh,station);double y=config.headroom()+(type==RoadInfrastructure.Tunnel.ARCH?.2:-.2);
        V eye=s.at(side*(s.halfWidth()-1),-y);double hit=ray(eye,s.left().mul(side),faces);
        check(hit<1.15,"open upper wall/arch shoulder: "+type+" "+style+" station="+station+" far="+far+" hit="+hit);
      }
      var middle=RoadStructures.sample(mesh,32);double up=ray(middle.center().add(new V(0,1,0)),new V(0,1,0),faces);
      check(up>config.headroom()-1.1&&Double.isFinite(up),"roof missing or intrudes travel space "+type+" "+style+" far="+far+" up="+up);
    }
    if(shellOnly)return;
    var eager=new RoadIndex.Built(road);int size=eager.clearanceCells.size();var lazy=new RoadIndex.Built(road);
    for(double station:new double[]{1,5,15,32,48,62})for(int side:new int[]{-1,1}){
      var s=RoadStructures.sample(mesh,station);
      for(double lateral:new double[]{0,s.halfWidth()-.7,s.halfWidth()+2.4})for(int dy=0;dy<16;dy++){
        var p=pos(s.at(side*lateral,-dy));boolean expected=eager.clearanceCells.contains(p.asLong());
        check(lazy.clearanceAt(p)==expected,"lazy/eager excavation mismatch");
        if(lateral>s.halfWidth()+2)check(!expected,"outside apron incorrectly reserved as invisible air");
      }
    }
    long counted=0;for(long key:eager.clearanceCells){counted++;var p=BlockPos.of(key);check(eager.columns.containsKey(BlockPos.asLong(p.getX(),0,p.getZ())),"clearance footprint exceeds actual deck");}
    check(counted==size,"clearance iterator/count differ");check(!lazy.rasterized(),"local queries forced full raster");
    var middle=RoadStructures.sample(mesh,32);var cp=pos(middle.center());var ep=pos(middle.at(middle.halfWidth()-.6,0));
    double center=RoadInfrastructure.excavationTop(mesh,cp.getX(),cp.getZ(),100),edge=RoadInfrastructure.excavationTop(mesh,ep.getX(),ep.getZ(),100);
    if(type==RoadInfrastructure.Tunnel.ARCH)check(center>edge+.15,"arch excavation still uses uniform maximum height");
  }
}
