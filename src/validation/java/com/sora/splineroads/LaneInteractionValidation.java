package com.sora.splineroads;
import com.sora.splineroads.core.*;
import com.sora.splineroads.core.RoadGeometry.*;
import java.util.*;
public final class LaneInteractionValidation {
 static int checks;
 static void check(boolean v,String reason){checks++;if(!v)throw new AssertionError(reason);}
 static V rotate(V v,double angle){double c=Math.cos(angle),s=Math.sin(angle);return new V(v.x()*c-v.z()*s,v.y(),v.x()*s+v.z()*c);}
 public static void main(String[] args){
  var settings=new Settings(Mode.CURVE,Style.O1_ONE,5,1,.35,90);
  for(double rotation:new double[]{0,.37,Math.PI/2,Math.PI})for(double space:new double[]{24,30,40,55,80})for(var mode:List.of(LanePoints.Path.RIGHT,LanePoints.Path.AUTO)){
   V da=rotate(new V(-1,0,0),rotation),db=rotate(new V(0,0,-1),rotation);var a=new LaneRampPaths.Port(new V(0,100,0),da,da.left(),5,0);var b=new LaneRampPaths.Port(rotate(new V(-140,100,-space),rotation),db,db.left(),5,0);
   var candidate=LaneRampPaths.candidates(a,b,settings,new LanePoints.Options(mode,false,false,24,32)).get(0);var m=candidate.mesh();
   check(candidate.path()==LanePoints.Path.RIGHT,"Auto/manual select a genuine right turn");check(m.length()<250,"right turn cannot hide a 450 degree loop");check(RoadRibbon.minRadius(m)>23.8,"requested 24 block radius retained");
   check(m.first().center().distance(a.position())<1e-6&&m.last().center().distance(b.position())<1e-6,"selected endpoints retained");
   double total=0;for(int i=1;i<m.samples().size();i++){V p=m.samples().get(i-1).left(),q=m.samples().get(i).left();total+=Math.atan2(p.x()*q.z()-p.z()*q.x(),p.dot(q));}
   check(Math.abs(total-Math.PI/2)<1e-5,"right turn is a positive ninety degrees in Minecraft XZ");
  }
  for(var style:List.of(Style.O6_RAIL,Style.O4_YELLOW,Style.O3_ONE))for(double yaw:new double[]{0,37,180}){
   var a=new Node(new V(.5,2,.5),yaw,0);V end=rotate(new V(0,0,160),Math.toRadians(yaw));var opts=RoadProfile.Options.DEFAULT.sidewalk(new RoadSidewalks.Config(true,RoadSidewalks.Side.RIGHT,5,"minecraft:stone_bricks"));
   var mesh=RoadGeometry.build(a,new Node(a.position().add(end),yaw,0),new Settings(Mode.CURVE,style,RoadProfile.width(style,opts,4),1,.4,90).options(opts));
   var p=LanePoints.point(UUID.randomUUID(),LanePoints.Origin.MANUAL,mesh,mesh.length()*.6,0);
   var image=LanePointPreview.render(mesh,p,360,160,0);
   for(var marker:image.markers())check(image.laneAt(marker.x(),marker.y())==marker.lane(),"visible lane center clicks resolve to exact lane, including opposite direction");
   check(image.laneAt(-1,40)==-1&&image.laneAt(360,40)==-1,"outside viewport cannot select a lane");
   if(RoadProfile.catalog(style).twoWay())check(image.markers().get(0).dy()*image.markers().get(RoadProfile.catalog(style).lanes()/2).dy()<0,"opposite lanes show opposite arrows");
  }
  System.out.println("LaneInteractionValidation: "+checks+" checks passed");
 }
}
