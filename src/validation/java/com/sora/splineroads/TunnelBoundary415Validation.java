package com.sora.splineroads;
import com.sora.splineroads.core.*;
import com.sora.splineroads.core.RoadGeometry.*;
import java.util.*;

/** Actual interior/roof core; independent analytic and sampled-shell oracles. No game. */
public final class TunnelBoundary415Validation {
  static int checks,cases;static final double EPS=1e-7;
  static void check(boolean b,String message){checks++;if(!b)throw new AssertionError(message);}
  static Mesh mesh(RoadInfrastructure.Tunnel type,double width,double grade,double angle,double height){
    var o=RoadProfile.Options.DEFAULT.infrastructure(RoadInfrastructure.Config.DEFAULT.tunnel(type));
    var settings=new Settings(Mode.STRAIGHT,width<12?Style.O2_ONE:Style.O4_RAIL,width,1,.35,90).options(o).structure(Structure.TUNNEL);
    var dir=new V(Math.sin(angle),grade,Math.cos(angle));var a=new V(-20.2,height,12.3);
    return RoadGeometry.build(new Node(a,RoadPlanner.yaw(dir),grade),new Node(a.add(dir.mul(64)),RoadPlanner.yaw(dir),grade),settings);
  }
  static double oldTop(Mesh m,int x,int z,double deckTop){
    var q=RoadQueries.horizontal(m,new V(x+.5,deckTop,z+.5));double half=q.sample().halfWidth(),lat=Math.max(0,Math.abs(q.lateral())-.75);
    var c=m.settings().options().infrastructure();double rise=RoadInfrastructure.ceiling(c,half,lat);
    for(double dx:new double[]{0,1})for(double dz:new double[]{0,1})half=Math.max(half,RoadQueries.horizontal(m,new V(x+dx,deckTop,z+dz)).sample().halfWidth());
    return deckTop+Math.max(rise,RoadInfrastructure.ceiling(c,half,lat))+.02;
  }
  public static void main(String[]args){
    // Exact integer summit: old .02 overdig removed a complete extra block layer.
    var m=mesh(RoadInfrastructure.Tunnel.ARCH,36,0,0,100);
    int x=-21,z=30;double top=RoadTunnelSpace.ceiling(m,x,z);
    check(Math.abs(top-110)<EPS,"integer crown oracle");
    check(Math.ceil(oldTop(m,x,z,100)-EPS)==111&&Math.ceil(top-EPS)==110,"old extra layer reproduced and removed");
    check(!RoadTunnelSpace.intersects(m,x,110,z,1)&&RoadTunnelSpace.intersects(m,x,109,z,1),"roof boundary only is not interior");
    // No nearest-end extrapolation past the mouth, including negative coordinates.
    check(!RoadTunnelSpace.intersects(m,-21,101,11,1),"empty portal exterior falsely reserved");
    for(var type:RoadInfrastructure.Tunnel.values())for(double width:new double[]{9,18,36})for(double grade:new double[]{-.05,0,.05})for(double angle:new double[]{0,.61,2.91})for(double height:new double[]{-64,341}){
      var road=mesh(type,width,grade,angle,height);cases++;var raised=mesh(type,width,grade,angle,height+512);
      for(double station:new double[]{2.3,17.1,32.7,61.3})for(double fraction:new double[]{-.85,-.45,0,.45,.85}){
        var sample=RoadStructures.sample(road,station);var p=sample.at(sample.halfWidth()*fraction,0);
        int xx=(int)Math.floor(p.x()),zz=(int)Math.floor(p.z());double ceiling=RoadTunnelSpace.ceiling(road,xx,zz);
        check(Double.isFinite(ceiling),"inside column lost");
        double actual=sample.center().y()+RoadInfrastructure.ceiling(road.settings().options().infrastructure(),sample.halfWidth(),sample.halfWidth()*fraction);
        check(ceiling>=actual-EPS,"new ceiling clips actual lining interior");
        check(ceiling<=actual+2,"local maximum is not global rectangular arch extent");
        check(!RoadTunnelSpace.intersects(road,xx,(int)Math.ceil(ceiling+EPS),zz,1),"cell above actual roof cannot be interior");
        check(RoadTunnelSpace.intersects(road,xx,(int)Math.floor(p.y()+2),zz,1),"drive corridor lost");
        double shifted=RoadTunnelSpace.ceiling(raised,xx,zz);
        check(Math.abs(shifted-ceiling-512)<1e-6,"vertical translation changes excavation/retention classification");
      }
    }
    // Explicit old/new ceiling values for all fractional Y translations.
    double maximumReduction=0;int reduced=0;
    for(double y:new double[]{0,.01,.49,.98,100,341}){
      var road=mesh(RoadInfrastructure.Tunnel.ARCH,36,.04,.61,y);
      for(double station=3;station<61;station+=4)for(double f:new double[]{-.95,-.8,-.2,0,.2,.8,.95}){
        var q=RoadStructures.sample(road,station);var p=q.at(q.halfWidth()*f,0);int xx=(int)Math.floor(p.x()),zz=(int)Math.floor(p.z());
        double exact=RoadTunnelSpace.ceiling(road,xx,zz),old=oldTop(road,xx,zz,q.center().y()+.2);
        if(old>exact){maximumReduction=Math.max(maximumReduction,old-exact);reduced++;}
      }
    }
    System.out.printf(Locale.ROOT,"TunnelBoundary415Validation: %d cases, %d checks PASS; old extra crown layer reproduced; %d locally overestimated probes, max conservative difference %.6f. Pure core; no Minecraft terrain/GPU.%n",cases,checks,reduced,maximumReduction);
  }
}
