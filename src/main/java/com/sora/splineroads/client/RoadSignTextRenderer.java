package com.sora.splineroads.client;
import com.sora.splineroads.core.*;
import com.sora.splineroads.world.RoadIndex;
import com.sora.splineroads.core.RoadGeometry.V;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.core.BlockPos;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import com.mojang.math.Axis;
import java.util.*;
/** The chunk index and placements are reused; only nearby text is drawn each frame. */
public final class RoadSignTextRenderer {
  private static final Map<UUID,List<RoadSigns.Placement>> CACHE=new LinkedHashMap<>();private static long revision=-1;
  public static void render(RenderLevelStageEvent event){var mc=Minecraft.getInstance();if(mc.level==null)return;var index=ClientRoads.INDEX;if(revision!=index.revision()){CACHE.clear();revision=index.revision();}
    var camera=event.getCamera().getPosition();var pose=event.getPoseStack();var buffers=mc.renderBuffers().bufferSource();
    for(var road:index.signsNear((int)Math.floor(camera.x)>>4,(int)Math.floor(camera.z)>>4)){
      var placements=CACHE.computeIfAbsent(road.record.id(),id->road.record.settings().options().infrastructure().signs().stream().map(a->RoadSigns.place(road.mesh,a)).toList());
      for(var p:placements){var a=p.attachment();var m=RoadSignCatalog.get(a.model());if(m.fields().isEmpty())continue;V origin=p.part().a().add(p.part().b()).mul(.5),front=p.front(),right=p.part().b().sub(p.part().a()).horizontalUnit();V eye=new V(camera.x,camera.y,camera.z);
        if(origin.distance(eye)>96||eye.sub(origin).dot(front)<=0)continue;
        var bounds=new net.minecraft.world.phys.AABB(p.part().a().x(),origin.y(),p.part().a().z(),p.part().b().x(),origin.y()+p.part().height(),p.part().b().z()).inflate(1);if(!event.getFrustum().isVisible(bounds))continue;
        int light=LevelRenderer.getLightColor(mc.level,BlockPos.containing(origin.x()+front.x(),origin.y()+p.part().height()/2,origin.z()+front.z()));
        if(a.model().equals("road_pole_text_display"))light=net.minecraft.client.renderer.LightTexture.FULL_BRIGHT;
        for(int i=0;i<m.fields().size();i++){
          var f=m.fields().get(i);String text=i<a.text().size()?a.text().get(i):f.initial();
          if(f.key().startsWith("expressway")){
            String type=text.toUpperCase(Locale.ROOT).startsWith("S")?"provicial":"national";int digits=text.replaceAll("[^0-9]","").length();
            var texture=net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("splineroads","textures/road/cb_logo_"+type+"_"+(digits==1?2:1)+".png");
            var consumer=buffers.getBuffer(net.minecraft.client.renderer.RenderType.entityCutout(texture));
            V at=origin.add(right.mul(f.x()*p.scale())).add(new V(0,(f.y()+.03125)*p.scale(),0)).add(front.mul(p.part().width()/2+.01));double half=.325*p.scale();
            int corner=0;for(var xy:List.of(new double[]{-1,-1},new double[]{1,-1},new double[]{1,1},new double[]{-1,1})){V v=at.add(right.mul(xy[0]*half)).add(new V(0,xy[1]*half,0));consumer.vertex(pose.last().pose(),(float)(v.x()-camera.x),(float)(v.y()-camera.y),(float)(v.z()-camera.z)).color(255,255,255,255).uv(corner==0||corner==3?0:1,corner<2?1:0).overlayCoords(net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY).uv2(light).normal((float)front.x(),0,(float)front.z()).endVertex();corner++;}
          }
          if(text.isEmpty())continue;var styled=net.minecraft.network.chat.Component.literal(text).withStyle(net.minecraft.ChatFormatting.BOLD);int width=mc.font.width(styled);
          double scale=RoadSignCatalog.fontScale(m,f,text,width)*p.scale();
          V at=origin.add(right.mul(f.x()*p.scale())).add(new V(0,f.y()*p.scale(),0)).add(front.mul(p.part().width()/2+.014));
          pose.pushPose();pose.translate(at.x()-camera.x,at.y()-camera.y,at.z()-camera.z);
          // The imported CB front and text share the same local X/Z orientation.
          pose.mulPose(Axis.YP.rotation((float)Math.atan2(-right.z(),right.x())));pose.scale((float)scale,(float)-scale,(float)scale);
          float dx=f.align().equals("left")?0:f.align().equals("right")?-width:-width/2f;
          mc.font.drawInBatch(styled,dx,-4.5f,f.color(),false,pose.last().pose(),buffers,Font.DisplayMode.NORMAL,0,light);pose.popPose();
        }
      }
    }
  }
  private RoadSignTextRenderer(){}
}
