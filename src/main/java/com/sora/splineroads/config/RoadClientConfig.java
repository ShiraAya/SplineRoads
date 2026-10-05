package com.sora.splineroads.config;

import net.minecraftforge.common.ForgeConfigSpec;

/** Client-only settings, with no references to client classes on a dedicated server. */
public final class RoadClientConfig {
  public enum SurfaceBackend { AUTO, TERRAIN, VBO }
  public static final ForgeConfigSpec SPEC;
  public static final ForgeConfigSpec.EnumValue<SurfaceBackend> SURFACE_BACKEND;
  public static final ForgeConfigSpec.IntValue TERRAIN_SECTIONS_PER_FRAME;
  public static final ForgeConfigSpec.IntValue VBO_UPLOAD_MICROS;
  public static final ForgeConfigSpec.IntValue INACTIVE_VBO_CACHE_MIB,TERRAIN_CACHE_MIB;
  static {
    var b=new ForgeConfigSpec.Builder();b.push("rendering");
    SURFACE_BACKEND=b.comment("AUTO: terrain ONLY while an Iris/Oculus shader pack is actually rendering; otherwise VBO.",
        "TERRAIN submits asphalt/paint through chunk block rendering, enabling terrain shader materials.",
        "Puddle appearance still depends on the shader pack. VBO is the legacy compatibility fallback.",
        "AUTO follows shader toggles at runtime. Manual TERRAIN/VBO overrides are retained; no world migration.")
        .defineEnum("surfaceBackend",SurfaceBackend.AUTO);
    TERRAIN_SECTIONS_PER_FRAME=b.comment("Maximum 16x16x16 model sections published/rebuilt per frame.",
        "A time budget also applies; geometry is computed off the render thread.")
        .defineInRange("terrainSectionsPerFrame",4,1,32);
    VBO_UPLOAD_MICROS=b.comment("Soft render-thread budget for infrastructure VBO upload (microseconds).")
        .defineInRange("vboUploadMicros",900,200,5000);
    INACTIVE_VBO_CACHE_MIB=b.comment("Estimated MiB budget for dormant vertex-encoding VBOs. 0 disables retention; shared CPU geometry is not duplicated.").defineInRange("inactiveVboCacheMiB",64,0,512);
    TERRAIN_CACHE_MIB=b.comment("Estimated MiB budget for unloaded/inactive terrain tiles (256 bytes/quad). 0 disables retention.").defineInRange("terrainCacheMiB",64,0,512);
    b.pop();SPEC=b.build();
  }
  private RoadClientConfig(){}
}
