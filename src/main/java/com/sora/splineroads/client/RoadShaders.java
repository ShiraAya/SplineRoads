package com.sora.splineroads.client;
import com.sora.splineroads.SplineRoads;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import net.minecraft.client.renderer.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterShadersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import java.io.IOException;
/** VBO world solids need fog from the fully camera-relative vertex, not entity-local
 * components. Shader packs still receive the standard intercepted entity program. */
@Mod.EventBusSubscriber(modid=SplineRoads.ID,bus=Mod.EventBusSubscriber.Bus.MOD,value=Dist.CLIENT)
public final class RoadShaders {
  private static ShaderInstance worldSolid;
  @SubscribeEvent public static void register(RegisterShadersEvent event)throws IOException{
    event.registerShader(new ShaderInstance(event.getResourceProvider(),ResourceLocation.fromNamespaceAndPath(SplineRoads.ID,"road_world_solid"),DefaultVertexFormat.NEW_ENTITY),s->worldSolid=s);
  }
  public static ShaderInstance solid(){return worldSolid==null||ShaderPackState.current().active()?GameRenderer.getRendertypeEntityCutoutNoCullShader():worldSolid;}
  private RoadShaders(){}
}
