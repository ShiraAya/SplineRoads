# One-shot final consistency guard: preserve configured level-end behavior in both sides.
from pathlib import Path
import hashlib
p=Path('src/main/java/com/sora/splineroads/core/RoadConnectionChecks.java');s=p.read_text();assert hashlib.sha256(p.read_bytes()).hexdigest()=='1409d4c803ba10a06363d3d224cbe51e4b935e2af68f753c8e98e07a39de0fed'
s=s.replace('  public static void require(','''  /** Same level-ends normalization as the server construction hint, without world APIs. */
  public static RoadPlanner.Hint atLevel(RoadPlanner.Hint h,double y){
    if(h==null)return null;
    var n=h.node();return new RoadPlanner.Hint(new Node(new V(n.position().x(),y,n.position().z()),n.yaw(),0),h.headingLocked(),true,h.linked());
  }
  public static void require(''');p.write_text(s)
p=Path('src/main/java/com/sora/splineroads/client/RoadScreen.java');s=p.read_text();assert hashlib.sha256(p.read_bytes()).hexdigest()=='efb5412b53bc7c6541dbb08da6d62d211130c1a7184571b79c45d63acb742ecb'
old='''        com.sora.splineroads.core.RoadConnectionChecks.require(plan,
            payload.contains("AutoA")?RoadData.readHint(payload.getCompound("AutoA")):null,
            payload.contains("AutoB")?RoadData.readHint(payload.getCompound("AutoB")):null);'''
new='''        var seamA=payload.contains("AutoA")?RoadData.readHint(payload.getCompound("AutoA")):null;
        var seamB=payload.contains("AutoB")?RoadData.readHint(payload.getCompound("AutoB")):null;
        if(payload.getBoolean("LevelEnds")) {
          seamA=com.sora.splineroads.core.RoadConnectionChecks.atLevel(seamA,BlockPos.of(payload.getLong("A")).getY());
          seamB=com.sora.splineroads.core.RoadConnectionChecks.atLevel(seamB,BlockPos.of(payload.getLong("B")).getY());
        }
        com.sora.splineroads.core.RoadConnectionChecks.require(plan,seamA,seamB);'''
assert old in s;s=s.replace(old,new);p.write_text(s)
p=Path('src/validation/java/com/sora/splineroads/Problem2GeometryValidation.java');s=p.read_text();s=s.replace('    System.out.println("Problem2GeometryValidation:', '''    for(double y:List.of(2.0,100.0,341.0))for(double angle:List.of(0.0,.4)){
      V d=new V(Math.sin(angle),0,Math.cos(angle));var original=new RoadPlanner.Hint(new Node(new V(0,y+.5,0),RoadPlanner.yaw(d),.1),true,true,true);
      var a=RoadConnectionChecks.atLevel(original,y);var b=RoadConnectionChecks.atLevel(new RoadPlanner.Hint(new Node(d.mul(100).add(new V(0,y+.5,0)),RoadPlanner.yaw(d),.1),true,true,true),y);
      var plan=RoadPlanner.plan(RoadPlanner.Hint.free(a.node()),RoadPlanner.Hint.free(b.node()),settings());RoadConnectionChecks.require(plan,a,b);checks++;
      check(a.node().position().y()==y&&a.node().grade()==0&&a.gradeLocked()&&a.linked(),"client level normalization matches server contract");fixtures++;
    }
    check(RoadConnectionChecks.atLevel(null,0)==null,"missing seam remains free");
    System.out.println("Problem2GeometryValidation:''');p.write_text(s)
expected={'src/main/java/com/sora/splineroads/core/RoadConnectionChecks.java':'b78582625f3728b3328703af1724ef2f4722eabb62f239f547eade7463f0a5d4','src/main/java/com/sora/splineroads/client/RoadScreen.java':'4abd499290036146e8458562908bd645799275d6d1b51a018219d5d225edc695','src/validation/java/com/sora/splineroads/Problem2GeometryValidation.java':'7b00cf2a4cea68c430f1f953c8f41fd77f86f6cf33cb46952576d3eac53a7534'}
for name,sha in expected.items():assert hashlib.sha256(Path(name).read_bytes()).hexdigest()==sha,name
print('PASS: level-end normalized seam checks and six new fixtures exactly match local validation')
