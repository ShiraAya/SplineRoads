# One-time reviewed checkpoint apply. Original and resulting production bytes are pinned.
# Historical migration helper after application, NOT a normal build step.
import hashlib
from pathlib import Path
CHECKS={'src/main/java/com/sora/splineroads/client/RoadRenderer.java': ('4a1280b17b7a59f5bfe4c88b349e90bfcda1c9caa2c7ca6feca8434ef14c96c5', 'a58dd9510d557d5182668634ba2eed2eb6662881f49d6bfcd9d42170968081fe'), 'src/main/java/com/sora/splineroads/core/RoadInfrastructure.java': ('1443600644748ac174e13ee380da1697212bc57222f3fd8c446aa6518829473b', '38272461f61f68c0c55c1fc67a08742c01399b18336a784c1a0cfdcc4893585b'), 'src/main/java/com/sora/splineroads/core/RoadStructures.java': ('b5e94f64677ee04964162092c28aca46534fe9f59b2b79fc510a14f291db5ae2', 'd1327fb5db9065f30e4cc75bea2ce0a3d544163fa730e2959b461e75684ca919'), 'src/main/java/com/sora/splineroads/core/RoadSurface.java': ('a8c3e9786790cd90d8590f3dbf13dfd2adb3b4daedabec7b9241cf2abff78bef', '38b7b313b18eccbdbec393b048ebc5540e41d6dddc1e0116389c4817f48c2c61'), 'build.gradle': ('af4a2bc8c501432422fe0ce9e8e81ac0d95156e2683f5facf53ce31ddb3f2feb', '55611caa6a4bd6c78651ca02e08b0cb7355601315a19e549d801cea79853087c'), 'src/main/resources/META-INF/mods.toml': ('9d4ee16d366cd0731d79ad06e5113e271d5980a76db568959d90c2aa8262b858', '065b3b3a70b84720b6f8789bf57d9969cfacf91974b5268549f8986010cc5b60')}
for name,(old,new) in CHECKS.items():
    assert hashlib.sha256(Path(name).read_bytes()).hexdigest()==old, 'Stale baseline: '+name
w=Path.cwd()
p=w/'src/main/java/com/sora/splineroads/core/RoadVisibility.java'
p.write_text('''package com.sora.splineroads.core;

/** Conservative horizontal view window for independently rendered road solids.
 * Terrain columns can remain visible at a high camera altitude or at a square-window
 * corner. A 3-D sphere must not remove their road/tunnel cover while the ground stays.
 * This is only a distance prefilter; the renderer still performs its normal frustum test.
 */
public final class RoadVisibility {
  public static boolean within(double minX,double minZ,double maxX,double maxZ,
      double cameraX,double cameraZ,int renderChunks) {
    if(!RoadGeometry.finite(minX,minZ,maxX,maxZ,cameraX,cameraZ)||minX>maxX||minZ>maxZ)return false;
    double reach=Math.max(0,renderChunks)*16.0+32;
    double dx=Math.max(0,Math.max(minX-cameraX,cameraX-maxX));
    double dz=Math.max(0,Math.max(minZ-cameraZ,cameraZ-maxZ));
    return dx<=reach&&dz<=reach;
  }
  private RoadVisibility(){}
}
''')
p=w/'src/main/java/com/sora/splineroads/client/RoadRenderer.java';s=p.read_text();old='''    int distance = mc.options.getEffectiveRenderDistance() * 16 + 32;
    double limit = (double) distance * distance;''';assert old in s;s=s.replace(old,'''    int renderChunks=mc.options.getEffectiveRenderDistance();''');old='''      if (d >= limit || !event.getFrustum().isVisible(s.bounds)) continue;''';assert old in s;s=s.replace(old,'''      if (!RoadVisibility.within(s.bounds.minX,s.bounds.minZ,s.bounds.maxX,s.bounds.maxZ,
          camera.x,camera.z,renderChunks)||!event.getFrustum().isVisible(s.bounds)) continue;''');p.write_text(s)
p=w/'src/main/java/com/sora/splineroads/core/RoadStructures.java';s=p.read_text();at=s.index('  private static void median(List<Part> out')
s=s[:at]+'''  /** Tunnel and open-road medians use the same actual transition profile. Do not
   * replace tunnel medians by a fixed narrow concrete bar or omit its last 2m tile. */
  public static void medianFurniture(Mesh mesh,Ground ground,List<Part> out) {
    var catalog=RoadProfile.catalog(mesh.settings().style());if(!catalog.twoWay())return;
    for(double d=0;d<mesh.length()-1e-6;d+=2)
      for(double[] span:medianSpans(mesh,ground,d,Math.min(mesh.length(),d+2)))
        median(out,mesh,ground,sample(mesh,span[0]),sample(mesh,span[1]),catalog.type()==RoadProfile.Type.HIGHWAY,span[0]);
  }

'''+s[at:]
s=s.replace('''    boolean raised = bridgeAt(mesh,sample(mesh,(a.distance()+b.distance())/2),ground);''','''    boolean raised = mesh.settings().structure()!=Structure.TUNNEL&&bridgeAt(mesh,sample(mesh,(a.distance()+b.distance())/2),ground);''')
p.write_text(s)
p=w/'src/main/java/com/sora/splineroads/core/RoadInfrastructure.java';s=p.read_text();s=s.replace('''if(mesh.settings().structure()==Structure.TUNNEL){tunnel(mesh,c,out);return checked(out);}''','''if(mesh.settings().structure()==Structure.TUNNEL){tunnel(mesh,c,out);RoadStructures.medianFurniture(mesh,ground,out);return checked(out);}''');old='''      var la=RoadProfile.layout(mesh,a);var lb=RoadProfile.layout(mesh,b);
      if(d>=2&&d+2<mesh.length()-2&&la.median()>=.4&&lb.median()>=.4)
        longitudinal(out,a,b,0,0,0,Math.min(.5,Math.min(la.median(),lb.median())),.8,Material.CONCRETE);
''';assert old in s;s=s.replace(old,'');p.write_text(s)
p=w/'src/main/java/com/sora/splineroads/core/RoadSurface.java';raw=p.read_bytes();s=raw.decode('utf8');crlf=b'\r\n' in raw;s=s.replace('\r\n','\n')
s=s.replace('''        var median = la.catalog().median();
        if (median == RoadProfile.Median.DOUBLE_YELLOW && la.median() < .12)''','''        // Classify the interval at its midpoint, not at the reference start. Reversing
        // construction direction must not change yellow/white treatment of the same tile.
        var middle=RoadProfile.layout(mesh,RoadStructures.sample(mesh,(a.distance()+b.distance())/2));
        var median = middle.catalog().median();
        if (median == RoadProfile.Median.DOUBLE_YELLOW && middle.median() < .12)''')
s=s.replace('''        else if (la.median() >= .12''','''        else if (middle.median() >= .12''');p.write_bytes(s.replace('\n','\r\n').encode() if crlf else s.encode())
for name in ['build.gradle','src/main/resources/META-INF/mods.toml']:
    p=Path(name);s=p.read_text();assert '0.40.3-alpha' in s;p.write_text(s.replace('0.40.3-alpha','0.40.4-alpha'))
for name,(old,new) in CHECKS.items():
    assert hashlib.sha256(Path(name).read_bytes()).hexdigest()==new, 'Transfer mismatch: '+name
print('PASS: six modified file hashes match reviewed local result; added RoadVisibility source.')
