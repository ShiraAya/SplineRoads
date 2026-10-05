from pathlib import Path
import base64,zlib,hashlib,subprocess
# The full decoded patch is verified BEFORE applying anything. Exact staged transfer
# corrections below were obtained by comparing the returned artifact with local bytes.
p=Path('tools/checkpoints/r4-production.patch.z64');s=p.read_text().strip()
fixes=[('3jSPUuxpnRTBTHGbp2kFSTZJo','3jSPUuxpnRTBHGbp2kFSTZJo'),('uBmtolU6nji9cjdORkDLy1b5K','uBmtolU6nji9jdORkDLy1b5K'),('m89atMBiu/ncWc4pSoDTI2JoOD','m89atMBiu/nc4pSoDTI2JoOD'),('TI2JoODNcIoOloUloUJaOIJF1rn','TI2JoODNcIoOloUJaOIJF1rn'),('/3e3gGZ3tza2V2sVwpISBwe+JPC','/3e3gGZ3tza2VwpISBwe+JPC'),('aHjtLmxWbJuJbuJZ0S0Uvctb57z','aHjtLmxWbJuJZ0S0Uvctb57z'),('v7Xz2FVx6a2z2z3tW8SUxj0Acf','v7Xz2FVx6a2z3tW8SUxj0Acf'),('N6cEUHiafJa3b3x749o/7GHwDV','N6cEUHiafJa3x749o/7GHwDV')]
for a,b in fixes:
 assert s.count(a)==1,'Unexpected transfer revision';s=s.replace(a,b)
data=zlib.decompress(base64.b64decode(s,validate=True))
assert hashlib.sha256(data).hexdigest()=='c45e0d4740077212e02cb07b7f5676964ef22e44ec0aa57bf7b6db1a1790617b','Patch bytes do not match reviewed source'
before={'build.gradle':'17f389c27916a4d67340b6f3817154e6e537c3f4ab1161b9b5bb484db102012a','src/main/java/com/sora/splineroads/client/RoadRenderer.java':'18d98c6ee438c6fc98eb943b91bcc96ffb5af175538bfba4808b7442db093e9a','src/main/java/com/sora/splineroads/client/RoadTerrainModels.java':'9b63fec155afd1f2dafb1a816590cc42d2c04004917f45a28ef59ad9d8da7773','src/main/java/com/sora/splineroads/client/ShaderPackState.java':'1b3da0d35bf4bb55bee8f6fa866a301a27a45f749c2026bf2d89ff73a46b1b83','src/main/java/com/sora/splineroads/config/RoadClientConfig.java':'dac740ac8a3076142923e1fc734642f7fcb02855340bb638f9efaaddac19eb29','src/main/java/com/sora/splineroads/core/RoadRenderMesh.java':'2e0feec1387a5cf24ae7f4f2c667d16b7c9d1cd2c93f087f0133ab4c7f68f58e','src/main/resources/META-INF/mods.toml':'e1147c2da0d5e795513e160a6fdcac4183a900d7f07b432518e825fa05649045'}
for n,h in before.items():assert hashlib.sha256(Path(n).read_bytes()).hexdigest()==h,'Concurrent production change: '+n
patch=Path('/tmp/r4-production.patch');patch.write_bytes(data)
subprocess.run(['git','apply','--check','--unidiff-zero',str(patch)],check=True)
subprocess.run(['git','apply','--unidiff-zero',str(patch)],check=True)
after={'build.gradle':'f850dddad8f79b27776a7c6929ef6beb5dc2e66d54fa720b63091ec43da09c60','src/main/java/com/sora/splineroads/client/RoadRenderer.java':'4a1280b17b7a59f5bfe4c88b349e90bfcda1c9caa2c7ca6feca8434ef14c96c5','src/main/java/com/sora/splineroads/client/RoadTerrainModels.java':'fb35f4bdbc1c42aae8a2b1d7111943691f738bb059c116af4f3c476821479f53','src/main/java/com/sora/splineroads/client/ShaderPackState.java':'bf8f870786f07d35e4ecd4bf0f6ff5fe82f2ce0e4ddcb6cd2c887bc80b9d6119','src/main/java/com/sora/splineroads/config/RoadClientConfig.java':'9ea98d26a28a96951c0cb2d7f10349861883c81152bce52b5972344280dd7ddf','src/main/java/com/sora/splineroads/core/RoadRenderMesh.java':'734f1e2a8be3a5754084eeebd3b6d3651fa84c982e91a9a5cb9af5d12059b094','src/main/resources/META-INF/mods.toml':'a349671418886da2397e4d01ee40846b0c84986debcb4caf7a547befe6cc43a7'}
for n,h in after.items():assert hashlib.sha256(Path(n).read_bytes()).hexdigest()==h,'Unexpected output: '+n
p.write_text(s+'\n')
# Adapt explicit offline tests to the NEW cache semantics. No test double is shipped in production.
def edit(name,fn):
 p=Path(name);p.write_text(fn(p.read_text()))
edit('tools/perf40-render-support/com/sora/splineroads/client/RoadRenderer.java',lambda s:s.replace('public static int changes,resets;','public static int changes,resets,switches;public static boolean encoded;public static void shaderMode(boolean e){if(e!=encoded){encoded=e;switches++;}}'))
edit('tools/hotfix401-validation/com/sora/splineroads/client/ShaderBackend401Validation.java',lambda s:s.replace('check(RoadRenderer.resets==resets+1,"single enable transition reset");resets=RoadRenderer.resets;','check(RoadRenderer.resets==resets,"switch preserves shared geometry");check(RoadRenderer.switches>0,"encoding keyed separately");').replace('check(RoadRenderer.resets==resets+1,"atlas invalidates once");','check(RoadRenderer.resets==resets,"atlas changes tiles only, not shared road geometry");'))
edit('tools/hotfix401-validation/com/sora/splineroads/client/TerrainStreaming401Validation.java',lambda s:s.replace('RoadClientConfig.SURFACE_BACKEND.set(RoadClientConfig.SurfaceBackend.TERRAIN);','RoadClientConfig.SURFACE_BACKEND.set(RoadClientConfig.SurfaceBackend.TERRAIN);RoadClientConfig.TERRAIN_CACHE_MIB.set(1); // Explicit new weighted budget.').replace('RoadTerrainModels.stats().cached()<=128&&RoadTerrainModels.stats().cachedQuads()<=131072','RoadTerrainModels.stats().cached()<=2048&&RoadTerrainModels.stats().cachedQuads()<=4096').replace('check(RoadTerrainModels.stats().cached()==128,"oldest unloaded tiles evicted at configured bound");','check(RoadTerrainModels.stats().cached()>0&&RoadTerrainModels.stats().cached()<140,"weighted budget evicts oldest unloaded tiles");'))
Path('tools/perf40-render-support/net/minecraft/client/renderer/texture/TextureAtlas.java').write_text('''// TEST ONLY: identities remain stable until another atlas is constructed.
package net.minecraft.client.renderer.texture;
public class TextureAtlas{private final java.util.Map<net.minecraft.resources.ResourceLocation,TextureAtlasSprite> sprites=new java.util.HashMap<>();public TextureAtlasSprite getSprite(net.minecraft.resources.ResourceLocation r){return sprites.computeIfAbsent(r,k->new TextureAtlasSprite());}}
''')
Path('tools/perf40-render-support/net/minecraft/client/resources/model/ModelManager.java').write_text('''// TEST ONLY: not actual Minecraft resource reloading.
package net.minecraft.client.resources.model;
public class ModelManager{private final net.minecraft.client.renderer.texture.TextureAtlas atlas=new net.minecraft.client.renderer.texture.TextureAtlas();public net.minecraft.client.renderer.texture.TextureAtlas getAtlas(net.minecraft.resources.ResourceLocation r){return atlas;}}
''')
edit('tools/perf40-render-support/net/minecraftforge/client/event/ModelEvent.java',lambda s:s.replace('public static class BakingCompleted {public ModelManager getModelManager(){return new ModelManager();}}','public static class BakingCompleted {private final ModelManager m;public BakingCompleted(){this(new ModelManager());}public BakingCompleted(ModelManager m){this.m=m;}public ModelManager getModelManager(){return m;}}'))
print('Reviewed R4 production before/after SHA256 PASS. Source must be committed before long tests.')
