package com.sora.splineroads.client;
import com.sora.splineroads.core.*;
import com.sora.splineroads.core.RoadGeometry.*;
import com.sora.splineroads.core.InterchangePlanner.*;
import com.sora.splineroads.world.*;
import net.minecraft.core.BlockPos;
import java.util.*;
public final class Alignment25Validation {
 public static void main(String[] args) {
  int count=0,negativeZero=0;
  for(int n:new int[]{5,6})for(boolean left:new boolean[]{false,true}) {
   double[] angles=n==5?new double[]{0,180,90,270,45}:new double[]{0,180,60,240,120,300};Node[] nodes=new Node[n];
   for(int i=0;i<n;i++){double t=Math.toRadians(angles[i]);nodes[i]=new Node(new V(92000.5+Math.round(Math.cos(t)*900),90,92000.5+Math.round(Math.sin(t)*900)),0,0);}
   var s=new Settings(Mode.STRAIGHT,Style.H4_RAIL,Style.H4_RAIL.defaultWidth(),1,.4,90);
   var plan=MultiInterchange.plan(nodes,new Settings[]{s,s,s},new Options(n==5?Preset.DIRECTIONAL_FIVE:Preset.DIRECTIONAL_SIX,left,1,96,20,5,2,5,true,true,0),90);
   for(var leg:plan.legs()) {
    var mesh=leg.mesh();var a=RoadRibbon.start(mesh);var b=RoadRibbon.end(mesh);
    var record=new RoadRecord(UUID.randomUUID(),new UUID(0,0),BlockPos.containing(a.position().x(),a.position().y(),a.position().z()),BlockPos.containing(b.position().x(),b.position().y(),b.position().z()),a,b,mesh.settings(),false,4).alignment(new UUID(0,1),mesh);
    var before=record.mesh();var restored=RoadRecord.load(record.save());var after=restored.mesh();
    if(!before.samples().equals(after.samples()))throw new AssertionError("packed geometry differs: "+leg.name());
    if(!record.settings().equals(restored.settings()))throw new AssertionError("settings lost on reload");
    // Reproduce the obsolete arrival-frame sign only: positions and shape were
    // equal, but record equality saw -0.0 versus the format's implicit +0.0 Y.
    if(mesh.settings().style().ramp()) {
     var points=new ArrayList<>(mesh.samples());var sample=points.get(0);points.set(0,new Sample(sample.center(),new V(sample.left().x(),-0.0,sample.left().z()),sample.distance(),sample.halfWidth()));
     var old=record.alignment(new UUID(0,1),RoadRibbon.mesh(points,mesh.settings()));var roundtrip=RoadRecord.load(old.save());
     var oldMesh=old.mesh();var loadedMesh=roundtrip.mesh();
     if(oldMesh.samples().equals(loadedMesh.samples()))throw new AssertionError("signed-zero reproduction missing");
     for(int k=0;k<points.size();k++){
      var x=oldMesh.samples().get(k);var y=loadedMesh.samples().get(k);
      if(x.center().distance(y.center())!=0||x.left().distance(y.left())!=0||x.halfWidth()!=y.halfWidth()||x.distance()!=y.distance())throw new AssertionError("signed-zero reproduction changes actual geometry");
     }
     negativeZero++;
    }
    count++;
   }
  }
  System.out.println("ALIGNMENT 0.25 PASS: "+count+" exact mesh/settings round trips; "+negativeZero+" signed-zero reproductions show zero position/orientation/width/station error.");
 }
}
