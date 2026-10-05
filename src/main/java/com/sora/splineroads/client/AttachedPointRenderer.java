package com.sora.splineroads.client;
import com.sora.splineroads.SplineRoads;
import com.sora.splineroads.world.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.*;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
@Mod.EventBusSubscriber(modid=SplineRoads.ID,value=Dist.CLIENT)
public final class AttachedPointRenderer {
  private static boolean accepts(ItemStack s){return s.getItem() instanceof AttachedPointTool||s.getItem() instanceof LaneLineTool||s.getItem() instanceof InterchangeTool;}
  @SubscribeEvent public static void render(RenderLevelStageEvent e){
    if(e.getStage()!=RenderLevelStageEvent.Stage.AFTER_BLOCK_ENTITIES)return;var mc=Minecraft.getInstance();if(mc.player==null||LanePointRenderer.accepts(mc.player.getMainHandItem())||LanePointRenderer.accepts(mc.player.getOffhandItem())||!(accepts(mc.player.getMainHandItem())||accepts(mc.player.getOffhandItem())))return;
    var camera=e.getCamera().getPosition();var pose=e.getPoseStack();var buffer=mc.renderBuffers().bufferSource();var lines=buffer.getBuffer(RenderType.lines());
    pose.pushPose();pose.translate(-camera.x,-camera.y,-camera.z);
    for(var road:ClientRoads.INDEX.roads.values())for(var p:road.record.settings().options().attachments().points()){
      var v=p.position();if(camera.distanceToSqr(v.x(),v.y(),v.z())>96*96)continue;
      LevelRenderer.renderLineBox(pose,lines,new AABB(v.x()-.22,v.y()+.04,v.z()-.22,v.x()+.22,v.y()+.48,v.z()+.22),1f,.12f,.12f,1f);
    }pose.popPose();buffer.endBatch(RenderType.lines());
  }
}
