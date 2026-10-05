# One-time reviewed update, not a normal build step. Reject changed baselines.
import hashlib
from pathlib import Path
CHECKS={'src/main/java/com/sora/splineroads/client/RoadRenderer.java': ('a58dd9510d557d5182668634ba2eed2eb6662881f49d6bfcd9d42170968081fe', '71125339401267ffee42c34e3907e40b5e1e10d4248af7bb7db58a522652f244'), 'src/main/java/com/sora/splineroads/core/RoadNoiseModel.java': ('7a57ef4721640e4ee6f24afec378d33ab9026e7c0d9a88046ec068c4d7b4850e', '85988275a36d5c87c14e2da8f124273d0fa9c07466839a4c23ab743877a24026'), 'src/main/java/com/sora/splineroads/core/RoadStructures.java': ('d1327fb5db9065f30e4cc75bea2ce0a3d544163fa730e2959b461e75684ca919', '1820a9be5e2e2db89500262814f1b3ed8d625a797cf198b7481046f24d1e8742'), 'src/main/java/com/sora/splineroads/world/StructurePlanner.java': ('1da0997b262fa975fa9c6a44932a714098b9350327eebe96b6ebe1c9dc9ce382', '183be79caa3a76227c3eb3f6f7438b6f4bbf77e248935281936dd0cb9f898f76'), 'src/main/java/com/sora/splineroads/world/RoadInteractions.java': ('cb04c87b3a38ef4ab3354ef70d577e9c4e7d824b77f452f6c38b53eb03a1f728', 'e23d2343c5d21591303b5db6f8e987f959fccc59a9f6844c6cd2ef2f4f3e7e4a')}
for name,(old,new) in CHECKS.items():
    assert hashlib.sha256(Path(name).read_bytes()).hexdigest()==old, 'Stale baseline: '+name
from pathlib import Path
w=Path.cwd()
p=w/'src/main/java/com/sora/splineroads/client/RoadRenderer.java';s=p.read_text();old='RoadRenderTypes.surface(ResourceLocation.fromNamespaceAndPath("minecraft","textures/block/"+finish.id.substring(10)+".png"),true)';new='RoadRenderTypes.solid(ResourceLocation.fromNamespaceAndPath("minecraft","textures/block/"+finish.id.substring(10)+".png"))';assert old in s;s=s.replace(old,new);p.write_text(s)
p=w/'src/main/java/com/sora/splineroads/core/RoadNoiseModel.java';s=p.read_text();s=s.replace('  public static final double HEIGHT=2.5043584;','''  public static final double HEIGHT=2.5043584;
  public static final double BASE_HEIGHT=.5;
  /** Keep the authored panel/UVs untouched, lift the complete panel onto a real solid base. */
  public static List<Part> assembly(Part panel){
    var up=new V(0,BASE_HEIGHT,0);
    var base=new Part(panel.a(),panel.b(),panel.width(),BASE_HEIGHT,false,RoadStructures.Material.CONCRETE,panel.frameA(),panel.frameB());
    var raised=new Part(panel.a().add(up),panel.b().add(up),panel.width(),panel.height(),false,panel.material(),panel.frameA(),panel.frameB(),panel.model());
    return List.of(base,raised);
  }''');p.write_text(s)
p=w/'src/main/java/com/sora/splineroads/core/RoadStructures.java';s=p.read_text();old='''        if(!ground.blocked(part))out.add(part);else for(int j=i;j<=end;j++)ordinary.add(run.get(j));i=end+1;''';assert old in s;s=s.replace(old,'''        var assembly=RoadNoiseModel.assembly(part);
        // Validate and keep/remove the footing and panel as one unit, never half a wall.
        if(assembly.stream().noneMatch(ground::blocked))out.addAll(assembly);else for(int j=i;j<=end;j++)ordinary.add(run.get(j));i=end+1;''');p.write_text(s)
p=w/'src/main/java/com/sora/splineroads/world/StructurePlanner.java';s=p.read_text();needle='''    var ground = new RoadStructures.Ground() {''';assert needle in s;s=s.replace(needle,'''    // Road deck columns exclude smooth sidewalks outside the deck. Index their actual
    // slabs independently so lamp arms/posts cannot tunnel through an upper walkway.
    var sidewalkSolids=new RoadSolidOverlap.Index(obstacles.stream().flatMap(r->r.record.structures().stream())
        .filter(p->p.material().name().startsWith("WALK_")).toList());
    var ground = new RoadStructures.Ground() {''');s=s.replace('''              public boolean blocked(RoadStructures.Part part) {
''','''              public boolean blocked(RoadStructures.Part part) {
                if(!RoadSidewalks.smoothPart(part)&&sidewalkSolids.intersects(part))return true;
''');p.write_text(s)
p=w/'src/main/java/com/sora/splineroads/world/RoadInteractions.java';s=p.read_text();s=s.replace('''    if(!RoadIndex.overlapXZ(a.mesh,b.mesh,3))return false;''','''    double walkway=Math.max(walkExtent(a.mesh),walkExtent(b.mesh));
    if(!RoadIndex.overlapXZ(a.mesh,b.mesh,3+walkway))return false;''');s=s.replace('''s.halfWidth()+q.sample().halfWidth()+1)return true;''','''s.halfWidth()+q.sample().halfWidth()+1+walkway)return true;''').replace('''s.halfWidth()+q.sample().halfWidth()+3)return true;''','''s.halfWidth()+q.sample().halfWidth()+3+walkway)return true;''');at=s.index('  private static boolean near(');s=s[:at]+'''  private static double walkExtent(Mesh mesh){
    var walk=mesh.settings().options().sidewalk();
    return walk.enabled()&&walk.smooth()&&mesh.settings().structure()!=Structure.TUNNEL&&RoadProfile.catalog(mesh.settings().style()).type()==RoadProfile.Type.ORDINARY?walk.width():0;
  }
'''+s[at:];p.write_text(s)

for name,(old,new) in CHECKS.items():
    assert hashlib.sha256(Path(name).read_bytes()).hexdigest()==new, 'Transfer mismatch: '+name
print('PASS: reviewed furniture and material code hashes verified')
