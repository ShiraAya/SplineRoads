package com.sora.splineroads.client;
import com.sora.splineroads.config.RoadClientConfig;
import net.irisshaders.iris.api.v0.IrisApi;
import net.minecraft.client.Minecraft;
import net.minecraftforge.client.event.ModelEvent;

/** Real optional-API bridge and backend selector with explicit test doubles. Not GPU/Oculus. */
public final class ShaderBackend401Validation {
  static int checks;
  static void check(boolean value,String message){checks++;if(!value)throw new AssertionError(message);}
  static void terrain(boolean wanted,String why){RoadTerrainModels.beginFrame();check((RoadTerrainModels.assets()!=null)==wanted,why);}
  public static void main(String[] args){
    var loader=ShaderBackend401Validation.class.getClassLoader();
    check(!ShaderPackState.discover(loader,"no.such.OptionalApi").read().active(),"absent API uses VBO safely");
    var legacy=ShaderPackState.discover(loader,"no.such.OptionalApi","net.coderbot.iris.api.v0.IrisApi").read();
    check(legacy.active()&&legacy.provider().contains("coderbot"),"legacy namespace queried, not guessed by mod presence");
    var modern=ShaderPackState.discover(loader,"net.irisshaders.iris.api.v0.IrisApi");
    IrisApi.active=false;check(!modern.read().active(),"installed API with disabled pack is inactive");
    IrisApi.active=true;check(modern.read().active(),"actual enabled pack state queried");
    IrisApi.shadow=true;check(modern.read().shadowPass(),"shadow pass identified");
    IrisApi.fail=true;check(!modern.read().active(),"unavailable API falls back without crashing");
    IrisApi.fail=false;IrisApi.shadow=false;IrisApi.active=false;
    RoadTerrainModels.baked(new ModelEvent.BakingCompleted());
    RoadClientConfig.SURFACE_BACKEND.set(RoadClientConfig.SurfaceBackend.AUTO);
    terrain(false,"AUTO + installed but disabled -> VBO");
    int resets=RoadRenderer.resets;for(int i=0;i<100;i++)terrain(false,"idle disabled state stays VBO");
    check(RoadRenderer.resets==resets,"unchanged frames never reset all roads");
    IrisApi.active=true;terrain(true,"enable pack in same world -> terrain");
    check(RoadRenderer.resets==resets,"switch preserves shared geometry");check(RoadRenderer.switches>0,"encoding keyed separately");
    for(int i=0;i<100;i++)terrain(true,"active state stays terrain");
    check(RoadRenderer.resets==resets,"active steady frames do not repeatedly invalidate");
    IrisApi.shadow=true;RoadTerrainModels.baked(new ModelEvent.BakingCompleted());terrain(true,"shadow pass defers pending asset switch");
    check(RoadRenderer.resets==resets,"no reset or rebuild driven by shadow pass");
    IrisApi.shadow=false;terrain(true,"new atlas adopted on main pass");check(RoadRenderer.resets==resets,"atlas changes tiles only, not shared road geometry");
    IrisApi.active=false;terrain(false,"disable/fail pack -> VBO in same world");
    RoadClientConfig.SURFACE_BACKEND.set(RoadClientConfig.SurfaceBackend.TERRAIN);terrain(true,"explicit terrain override remains available");
    IrisApi.active=true;RoadClientConfig.SURFACE_BACKEND.set(RoadClientConfig.SurfaceBackend.VBO);terrain(false,"explicit VBO override remains available");
    check(Minecraft.getInstance().levelRenderer.resets>0,"actual selector schedules chunk invalidation when switching");
    System.out.println("ShaderBackend401Validation: "+checks+" checks passed; actual reflection/backend code, fake API/world, NO GPU runtime claim");
  }
}
