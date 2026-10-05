# One-shot P1 stage2 import, preserving the saved source checkpoint before verification.
from pathlib import Path
import hashlib
r=Path('src/main/java/com/sora/splineroads')
p=r/'world/RoadData.java';s=p.read_text();assert hashlib.sha256(p.read_bytes()).hexdigest()=='2e38e7d45806f0bba628e648c97ed0aeb85249f9c63512621a4db5e5a9aeed43'
s=s.replace('import com.sora.splineroads.core.RoadGeometry;','import com.sora.splineroads.core.RoadGeometry;\nimport com.sora.splineroads.core.RoadConnectionChecks;\nimport com.sora.splineroads.core.RoadWaterPolicy;')
old='roadBody ? collisionState(key, state, body.get(key), sidewalks.get(key)) : walkway';assert old in s
s=s.replace(old,'roadBody ? collisionState(key, state, body.get(key), sidewalks.get(key),dry.contains(key)) : walkway')
old='''  private BlockState collisionState(long key, BlockState previous, List<RoadIndex.Built> roads,BlockState sidewalk) {
    BlockPos p = BlockPos.of(key);'''
new='''  private BlockState collisionState(long key, BlockState previous, List<RoadIndex.Built> roads,BlockState sidewalk) {
    var pos=BlockPos.of(key);
    return collisionState(key,previous,roads,sidewalk,roads.stream().anyMatch(r->r.record.settings().structure()==Structure.TUNNEL&&r.clearanceAt(pos)));
  }
  private BlockState collisionState(long key, BlockState previous, List<RoadIndex.Built> roads,BlockState sidewalk,boolean dryInterior) {
    BlockPos p = BlockPos.of(key);'''
assert old in s;s=s.replace(old,new)
start=s.index('    // Open structural cells keep the surrounding water up to the rendered solid.');end=s.index('    return (generic?',start)
s=s[:start]+'''    // Keep water only in exterior partial-shell cells, never in an interior clearance
    // cell or the actual road slab. The caller supplies the union of ALL tunnel air
    // reservations, including tunnels not owning this particular body cell.
    boolean deckHere=roads.stream().anyMatch(r->{var c=r.column(p);return c!=null
        &&p.getY()+1>c.minTop()-r.record.settings().thickness()+1e-7&&p.getY()<c.maxTop()-1e-7;});
    boolean tunnelOwner=roads.stream().anyMatch(r->r.record.settings().structure()==Structure.TUNNEL);
    boolean shell=roads.stream().anyMatch(r->r.shellAt(p));
    boolean permeable=RoadWaterPolicy.permeable(deckHere,generic||fill!=RoadBlocks.Fill.NONE,tunnelOwner,shell,dryInterior);
    boolean originalWater=terrainOriginal.getOrDefault(key,Blocks.AIR.defaultBlockState()).getFluidState().is(net.minecraft.tags.FluidTags.WATER);
    boolean water=RoadWaterPolicy.waterlogged(permeable,terrain.getFluidState().is(net.minecraft.tags.FluidTags.WATER),
        previous.getFluidState().is(net.minecraft.tags.FluidTags.WATER),originalWater);
''' +s[end:]
s=s.replace('.setValue(RoadBlocks.Road.SEALED, roads.stream().anyMatch(r -> r.shellAt(p)));','.setValue(RoadBlocks.Road.SEALED, shell);')
p.write_text(s)
p=r/'world/RoadBlocks.java';s=p.read_text();assert hashlib.sha256(p.read_bytes()).hexdigest()=='0871b2e34b008c7ef7a09c3e457fe982aa6f06d16370e40f52da1e9af9cb5331'
old='return s.getValue(PERMEABLE)&&!s.getValue(SEALED)&&!s.getValue(WATERLOGGED)&&f==net.minecraft.world.level.material.Fluids.WATER;';assert old in s
s=s.replace(old,'''// PERMEABLE is set only for exterior/open structure cells. SEALED controls
      // shell light blocking; using it as a blanket water ban drained the exterior.
      return s.getValue(PERMEABLE)&&!s.getValue(WATERLOGGED)&&f==net.minecraft.world.level.material.Fluids.WATER;''');p.write_text(s)
for name in ['build.gradle','src/main/resources/META-INF/mods.toml']:
 p=Path(name);s=p.read_text();assert '0.40.6-alpha' in s;p.write_text(s.replace('0.40.6-alpha','0.40.7-alpha'))
expected={'src/main/java/com/sora/splineroads/world/RoadData.java':'fd88f1828e9546d00a2b085624d5a5eea7cc71a6303f7d0c1e77641970e45a6c','src/main/java/com/sora/splineroads/world/RoadBlocks.java':'7ba0523e2e56bc3b5c6f91c9b7b57905fabd5dfa5db38ce428bba5fa057c1017','src/main/java/com/sora/splineroads/core/RoadConnectionChecks.java':'1409d4c803ba10a06363d3d224cbe51e4b935e2af68f753c8e98e07a39de0fed','src/main/java/com/sora/splineroads/core/RoadWaterPolicy.java':'8a78f859fe6e4897b23bae061d77d8a5b74b59bd186baf9f2b535856aedafedc','build.gradle':'7b0bb8a75983ec69e70c05ef2865fc63dbdab085ca5157ed78a28ababf17d75f','src/main/resources/META-INF/mods.toml':'398ce0744b88cb3950107c53a0cfd3cb54fd3cc430b4a5b0dea003afcd9a8641'}
for name,sha in expected.items():assert hashlib.sha256(Path(name).read_bytes()).hexdigest()==sha,name
for name in ['AGENTS.md','docs/CURRENT_REQUIREMENTS.md']:
 p=Path(name);s=p.read_text();p.write_text('> 当前排期补充：必须先处理完《问题2》全部 P1，之后才可开始《问题3》的四项 terrain 缺陷及多线程专项 P2。读取 `docs/PRIORITIES.md` 和 `docs/issues/problem3-second-priority.md`。Q2-06 等旧 P1 显示问题不降级。\n\n'+s)
print('PASS: stage2 files exactly match local tested bytes; 0.40.7 candidate, save36/protocol54 unchanged')
