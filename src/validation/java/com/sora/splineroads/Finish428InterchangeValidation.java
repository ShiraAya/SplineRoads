package com.sora.splineroads;
import com.sora.splineroads.core.*;
import com.sora.splineroads.core.RoadGeometry.*;
import com.sora.splineroads.core.RoadProfile.*;
import com.sora.splineroads.core.InterchangePlanner.*;
import java.util.*;
/** Full production planners, deliberately large buildable fixtures, no game-world test. */
public final class Finish428InterchangeValidation {
 static int cases,checks;static void check(boolean b,String s){checks++;if(!b)throw new AssertionError(s);}
 static Node[] nodes(int n){double[] angles=n==5?new double[]{0,180,90,270,45}:n==6?new double[]{0,180,60,240,120,300}:new double[]{0,180,90,270};var nodes=new Node[n];for(int i=0;i<n;i++){double a=Math.toRadians(angles[i]);nodes[i]=new Node(new V(900*Math.cos(a),64,900*Math.sin(a)),0,0);}return nodes;}
 public static void main(String[]args){for(boolean left:new boolean[]{false,true})for(int n=3;n<=6;n++){
  cases++;Settings a=Finish428Validation.road(Type.HIGHWAY,3,2,left),b=Finish428Validation.road(Type.ORDINARY,2,1,left),c=Finish428Validation.road(Type.HIGHWAY,1,3,left);
  var option=new InterchangePlanner.Options(n==3?Preset.TRUMPET:n==4?Preset.STACK:n==5?Preset.DIRECTIONAL_FIVE:Preset.DIRECTIONAL_SIX,left,1,96,20,5,1,0,true,false,0);
  var plan=n<=4?InterchangePlanner.plan(nodes(n),a,b,option):MultiInterchange.plan(nodes(n),new Settings[]{a,b,c},option,64);
  check(plan.movements()>0,"no movements");check(plan.legs().size()>(n+1)/2,"no actual ramps generated");
  var desired=List.of(a,b,c);
  for(int i=0;i<(n+1)/2;i++){var m=plan.legs().get(i).mesh();check(RoadLanes.counts(m.settings()).equals(RoadLanes.counts(desired.get(i))),"authored main direction counts lost "+n+" axis="+i);var layout=RoadProfile.layout(m,m.first());
    check(layout.lanesOnSide(layout.outside())==RoadLanes.counts(desired.get(i)).forward(),"source forward count wrong");check(layout.lanesOnSide(-layout.outside())==RoadLanes.counts(desired.get(i)).reverse(),"source reverse count wrong");
  }
  for(var leg:plan.legs()){RoadGrades.validate(leg.mesh());check(leg.mesh().samples().stream().allMatch(s->Double.isFinite(s.center().y())&&s.halfWidth()>0),"invalid mesh");}
  System.out.println("PASS asymmetric "+n+"-arm left="+left+" legs="+plan.legs().size()+" minR="+plan.minRadius());
 }System.out.println("Finish428InterchangeValidation: "+cases+" real generated 3/4/5/6-arm layouts / "+checks+" checks PASS, not Minecraft world placement");}
}
