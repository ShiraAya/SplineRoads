# One-shot, hash-verified import of locally tested P1 changes. Do not rerun after application.
from pathlib import Path
import hashlib
r=Path('src/main/java/com/sora/splineroads')
p=r/'core/RoadClearance.java';s=p.read_text();anchor='  private static List<Triangle> triangles(Mesh mesh){';assert anchor in s
s=s.replace(anchor,'''  /** Exact swept road travel-volume vs an actual framed structural prism.
   * The old shell check combined the entire part's vertical bounds with one midpoint
   * road elevation: a long sloped beam could be rejected where it never touches road.
   * Boundary-only contact is not an obstruction. LaneDeck preserves real cut slots. */
  public static boolean structureInvades(RoadStructures.Part part,Mesh road,double headroom){
    var base=part.base();if(base.size()<3)return false;
    var index=grid(road);
    for(int i=1;i<base.size()-1;i++){
      var t=new Triangle(base.get(0),base.get(i),base.get(i+1),0,0,0);
      if(Math.abs(t.det())<EPS)continue;
      for(var q:index.near(t)){
        var polygon=intersection(t.polygon(),q.polygon());if(area(polygon)<AREA_EPS)continue;
        double min=Double.POSITIVE_INFINITY,max=Double.NEGATIVE_INFINITY;
        for(var point:polygon){double gap=t.height(point)-q.height(point);min=Math.min(min,gap);max=Math.max(max,gap);}
        // The gap is affine over each clipped triangle. Its range intersects exactly
        // when the prism enters the live deck/travel interval, not just its XZ bounds.
        if(min<headroom-EPS&&max>-road.settings().thickness()+.04-part.height()+EPS)return true;
      }
    }
    return false;
  }

'''+anchor)
p.write_text(s)
p=r/'world/RoadInteractions.java';s=p.read_text();start=s.index('  static boolean invades(Part p,Mesh m){');end=s.index('  static List<Part> openPortal',start)
s=s[:start]+'''  static boolean invades(Part p,Mesh m){
    return RoadClearance.structureInvades(p,m,4.25);
  }
'''+s[end:];p.write_text(s)
p=r/'world/TunnelShellValidation.java';assert not p.exists();p.write_text('''package com.sora.splineroads.world;

import com.sora.splineroads.core.*;
import com.sora.splineroads.core.RoadGeometry.*;
import java.util.*;

/** Validate new shell/travel conflicts; unchanged saved conflicts do not veto a deletion.
 * This does NOT disable checks while deleting: new end collars and changed neighboring
 * decks are still checked. Old part equality and actual travel geometry must BOTH match. */
final class TunnelShellValidation {
  static void check(List<RoadIndex.Built> planned,Set<UUID> changed,Map<UUID,RoadIndex.Built> previous){
    for(var tube:planned){
      if(tube.record.settings().structure()!=Structure.TUNNEL)continue;
      var oldTube=previous.get(tube.record.id());
      Set<RoadStructures.Part> saved=oldTube==null?Set.of():new HashSet<>(oldTube.record.structures());
      for(var other:planned){
        if(other.record.id().equals(tube.record.id())||(!changed.contains(tube.record.id())&&!changed.contains(other.record.id()))
            ||!RoadIndex.overlapXZ(tube.mesh,other.mesh,2))continue;
        var oldOther=previous.get(other.record.id());
        boolean unchangedTravel=oldOther!=null&&sameTravel(oldOther.mesh,other.mesh);
        for(var part:tube.record.structures()){
          if(part.material()!=RoadStructures.Material.TUNNEL)continue;
          if(unchangedTravel&&saved.contains(part))continue;
          if(RoadInteractions.invades(part,other.mesh))throw new IllegalArgumentException(
              "隧道墙顶侵入另一条道路的通行空间：隧道 "+tube.record.id()+"，道路 "+other.record.id()+
              String.format(Locale.ROOT,"，结构位置 %.2f %.2f %.2f；请调整高度或走线",part.a().x(),part.a().y(),part.a().z()));
        }
      }
    }
  }
  private static boolean sameTravel(Mesh a,Mesh b){
    return a==b||a.settings().thickness()==b.settings().thickness()&&a.samples().equals(b.samples())
        &&a.settings().options().lanePoints().cuts().equals(b.settings().options().lanePoints().cuts());
  }
  private TunnelShellValidation(){}
}
''')
p=r/'world/RoadData.java';s=p.read_text();start=s.index('      // Shells are real solids: a new tunnel cannot seal another road');end=s.index('      if(!deleting)checkJoints',start)
s=s[:start]+'''      // Check the FINAL list (nose caps may have replaced entries since planning).
      // Replaying an unchanged saved conflict must not prevent removing an unrelated road.
      var shellFinal=new ArrayList<RoadIndex.Built>();
      for(var old:index.roads.values())if(!removed.contains(old.record.id()))shellFinal.add(old);
      shellFinal.addAll(built);
      var shellChanged=new HashSet<UUID>();for(var changed:built)shellChanged.add(changed.record.id());
      TunnelShellValidation.check(shellFinal,shellChanged,index.roads);
'''+s[end:]
s=s.replace('      boolean linkedA = ah.linked(), linkedB = bh.linked();','      boolean linkedA = ah.linked(), linkedB = bh.linked();\n      var seamA=ah;var seamB=bh;')
s=s.replace('      var plan = RoadTunnelFit.plan(ah, bh, settings);','      var plan = RoadTunnelFit.plan(ah, bh, settings);\n      RoadConnectionChecks.require(plan,seamA,seamB);')
p.write_text(s)
p=r/'core/RoadConnectionChecks.java';assert not p.exists();p.write_text('''package com.sora.splineroads.core;

import com.sora.splineroads.core.RoadGeometry.*;

/** Identical preview/build preflight for an ordinary road's existing linked endpoints.
 * A manually selected shape is not automatically a valid seam. Do not merely show a
 * green client mesh then discover the same geometry is invalid only on the server. */
public final class RoadConnectionChecks {
  public static void require(RoadPlanner.Plan plan,RoadPlanner.Hint a,RoadPlanner.Hint b){
    if(plan.mesh().closed()||plan.settings().laneRamp()||plan.settings().style().ramp())return;
    endpoint("A",plan.mesh().first(),plan.start().grade(),a);
    endpoint("B",plan.mesh().last(),plan.end().grade(),b);
  }
  private static void endpoint(String name,Sample actual,double grade,RoadPlanner.Hint required){
    if(required==null||!required.linked())return;
    if(actual.center().distance(required.node().position())>1e-6)
      throw new IllegalArgumentException(name+" 端接点高程/位置不一致；预览不允许建造，请调整端点");
    V forward=actual.left().left().mul(-1).horizontalUnit();
    if(required.headingLocked()&&forward.dot(required.node().direction())<.99999999
        ||required.gradeLocked()&&Math.abs(grade-required.node().grade())>1e-5)
      throw new IllegalArgumentException(name+" 端接缝方向或坡度不连续；请切回智能模式或调整端点");
  }
  private RoadConnectionChecks(){}
}
''')
p=r/'client/RoadScreen.java';s=p.read_text();anchor='        var plan = com.sora.splineroads.core.RoadTunnelFit.plan(a, b, s);';assert anchor in s
s=s.replace(anchor,anchor+'''
        com.sora.splineroads.core.RoadConnectionChecks.require(plan,
            payload.contains("AutoA")?RoadData.readHint(payload.getCompound("AutoA")):null,
            payload.contains("AutoB")?RoadData.readHint(payload.getCompound("AutoB")):null);''')
p.write_text(s)
expected={'core/RoadClearance.java':'c2bf550ac31b05f9eee417b74e3d23e744cb81a82c473791416b8f7cbbb5ac04','core/RoadConnectionChecks.java':'ca8349039006f6ce34fe06f743bef6965b32dce3d0c717df0a83bc4e659dbbf8','world/RoadData.java':'2e38e7d45806f0bba628e648c97ed0aeb85249f9c63512621a4db5e5a9aeed43','world/RoadInteractions.java':'5d1650bffcb80513bd65d94b21e2eb8a7d220e0f4e3f543f1a8364ae86a31073','world/TunnelShellValidation.java':'a8ec409363dd932a2cef436df0cbd960d801e743a379e65bfecc0d4aff43b1a9','client/RoadScreen.java':'efb5412b53bc7c6541dbb08da6d62d211130c1a7184571b79c45d63acb742ecb'}
for name,sha in expected.items():assert hashlib.sha256((r/name).read_bytes()).hexdigest()==sha,name
print('PASS: six production files exactly match the locally tested source')
