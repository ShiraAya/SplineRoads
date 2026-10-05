package com.sora.splineroads;
import com.sora.splineroads.core.*;
import com.sora.splineroads.core.RoadGeometry.*;
import com.sora.splineroads.core.InterchangePlanner.*;
import com.sora.splineroads.core.CorridorPlanner.*;
public final class Revision271Validation {
  public static void main(String[] args){int checks=0,plans=0;
    for(Kind kind:Kind.values())for(Sides sides:Sides.values())for(Access access:Access.values())for(boolean left:new boolean[]{true,false})for(boolean reverse:new boolean[]{true,false}){
      Node a=new Node(new V(0,9,0),-90,0),b=new Node(new V(80,11,0),-90,0),c=new Node(new V(8,5,1),90,0),d=new Node(new V(76,3,3),90,0);
      Node[] nodes=kind==Kind.FRONTAGE?new Node[]{a,b}:reverse?new Node[]{a,b,d,c}:new Node[]{a,b,c,d};
      Settings main=Revision27Validation.settings(Style.H4_RAIL),aux=Revision27Validation.settings(kind==Kind.FRONTAGE?Style.O2_ONE:Style.O4_YELLOW);
      Options o=new Options(Preset.CLOVERLEAF,left,2,48,20,5,1).adjust(true);Config config=new Config(kind,sides,access,14,2);
      var p=CorridorPlanner.plan(nodes,main,aux,o,config);plans++;
      for(var l:p.legs()){if(RoadGrades.maximum(l.mesh())>.15001)throw new AssertionError("excess grade");checks++;}
      var again=CorridorPlanner.plan(p.anchors().toArray(Node[]::new),main,aux,o,config);
      if(!again.anchors().equals(p.anchors()))throw new AssertionError("fitting is not idempotent");checks++;
      if(p.anchors().get(1).position().distance(p.anchors().get(0).position())>2048)throw new AssertionError("distance limit");checks++;
      if(kind==Kind.LAYERED && (p.anchors().get(3).position().x()>p.anchors().get(2).position().x())==reverse)throw new AssertionError("reversed CD lost");checks++;
      boolean rejected=false;try{CorridorPlanner.plan(nodes,main,aux,o.adjust(false),config);}catch(IllegalArgumentException expected){rejected=true;}
      if(!rejected)throw new AssertionError("adjust off must preserve invalid originals");checks++;
    }
    System.out.printf("Revision271 PASS %d checks / %d fitted corridor plans%n",checks,plans);
  }
}
