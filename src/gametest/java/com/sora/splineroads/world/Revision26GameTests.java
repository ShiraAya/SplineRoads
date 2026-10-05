package com.sora.splineroads.world;

import com.sora.splineroads.core.*;
import com.sora.splineroads.core.RoadGeometry.*;
import com.sora.splineroads.core.InterchangePlanner.*;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraftforge.gametest.*;

@GameTestHolder("splineroads_revision26") @PrefixGameTestTemplate(false)
public final class Revision26GameTests {
  @GameTest(template="empty",templateNamespace="splineroads_revision26",timeoutTicks=12000)
  public static void fiveWayTreePersists(GameTestHelper h){check(h,5);}
  @GameTest(template="empty",templateNamespace="splineroads_revision26",timeoutTicks=12000)
  public static void sixWayTreePersists(GameTestHelper h){check(h,6);}
  private static void check(GameTestHelper h,int n){
    double[] angles=n==5?new double[]{0,180,90,270,45}:new double[]{0,180,60,240,120,300};Node[] nodes=new Node[n];
    for(int i=0;i<n;i++){double a=Math.toRadians(angles[i]);nodes[i]=new Node(new V(Math.cos(a)*900,90,Math.sin(a)*900),0,0);}
    Style style=Style.H6_RAIL;Settings s=new Settings(Mode.STRAIGHT,style,style.defaultWidth(),1,.4,90);
    Plan plan=MultiInterchange.plan(nodes,new Settings[]{s,s,s},new Options(n==5?Preset.DIRECTIONAL_FIVE:Preset.DIRECTIONAL_SIX,false,2,96,20,5,2,0,true,true,0),90);
    UUID group=UUID.randomUUID();long body=0,scan=0;int parts=0;
    for(Leg leg:plan.legs()){
      Mesh mesh=leg.mesh();Node a=RoadRibbon.start(mesh),b=RoadRibbon.end(mesh);
      BlockPos ap=BlockPos.containing(a.position().x(),a.position().y(),a.position().z()),bp=BlockPos.containing(b.position().x(),b.position().y(),b.position().z());
      RoadRecord r=new RoadRecord(UUID.randomUUID(),new UUID(0,0),ap,bp,a,b,mesh.settings(),false,4).alignment(group,mesh);
      RoadRecord restored=RoadRecord.load(r.save());
      h.assertTrue(restored.mesh().samples().equals(mesh.samples()),"binary collector geometry survives exact save/load");
      h.assertTrue(restored.settings().equals(mesh.settings()),"collector lane and paint settings survive save/load");
      RoadIndex.Built built=new RoadIndex.Built(restored);body+=built.cells.size();scan+=built.cells.size()+built.clearanceCells.size();parts++;
    }
    h.assertTrue(body<=RoadLimits.MAX_MULTI_INTERCHANGE_EDIT_CELLS,"new footprint remains inside multi-interchange edit budget");
    h.assertTrue(scan<=RoadLimits.MAX_MULTI_INTERCHANGE_SCAN_CELLS,"new footprint remains inside scan budget");
    System.out.println("REVISION26 SERVER n="+n+" parts="+parts+" body="+body+" scan="+scan);
    h.succeed();
  }
}
