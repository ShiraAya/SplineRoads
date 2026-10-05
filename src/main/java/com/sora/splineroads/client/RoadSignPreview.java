package com.sora.splineroads.client;
import com.sora.splineroads.core.*;
import com.sora.splineroads.core.RoadGeometry.V;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.network.chat.Component;
import net.minecraft.ChatFormatting;
import net.minecraft.resources.ResourceLocation;
import java.util.*;
/** Uses the same imported CB mesh, UVs and text metrics as the world renderer. */
public final class RoadSignPreview {
  private static final ResourceLocation ATLAS=ResourceLocation.fromNamespaceAndPath("splineroads","textures/road/cb_sign_atlas.png");
  private static final Map<String,List<RoadSurface.Face>> CACHE=new LinkedHashMap<>(16,.75f,true){protected boolean removeEldestEntry(Map.Entry<String,List<RoadSurface.Face>> e){return size()>32;}};
  public static void draw(GuiGraphics g,RoadSignCatalog.Model m,List<String> text,int x,int y,int w,int h,boolean markers){
    var faces=CACHE.computeIfAbsent(m.id(),id->{var p=new RoadStructures.Part(new V(-m.width()/2,0,0),new V(m.width()/2,0,0),m.depth(),m.height(),false,RoadStructures.Material.CB_SIGN,null,null,id);return p.faces().stream().filter(f->{V a=f.points().get(1).sub(f.points().get(0)),b=f.points().get(2).sub(f.points().get(0));return a.x()*b.y()-a.y()*b.x()>1e-10;}).sorted(Comparator.comparingDouble(f->f.points().stream().mapToDouble(V::z).average().orElse(0))).toList();});
    double scale=Math.min((w-8)/m.width(),(h-8)/m.height());if(scale<=0)return;double cx=x+w/2.,bottom=y+(h+m.height()*scale)/2.;
    g.flush();g.enableScissor(x,y,x+w,y+h);var pose=g.pose();pose.pushPose();pose.translate(cx,bottom,100);pose.scale((float)scale,(float)-scale,1);
    var consumer=g.bufferSource().getBuffer(RenderType.entityCutoutNoCull(ATLAS));
    for(var f:faces)for(int i=0;i<4;i++){var v=f.points().get(i);var uv=f.uv().get(i);consumer.vertex(pose.last().pose(),(float)v.x(),(float)v.y(),0).color(255,255,255,255).uv((float)uv.u(),(float)uv.v()).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(LightTexture.FULL_BRIGHT).normal(0,0,1).endVertex();}
    pose.popPose();g.flush();var font=Minecraft.getInstance().font;
    for(int i=0;i<m.fields().size();i++){
      var f=m.fields().get(i);String value=i<text.size()?text.get(i):f.initial();boolean empty=value.isEmpty();if(empty&&!markers)continue;
      if(empty)value="["+(i+1)+"]";var styled=Component.literal(value).withStyle(ChatFormatting.BOLD);int width=font.width(styled);double fs=RoadSignCatalog.fontScale(m,f,value,width)*scale;
      if(f.key().startsWith("expressway")){String type=value.toUpperCase(Locale.ROOT).startsWith("S")?"provicial":"national";int variant=value.replaceAll("[^0-9]","").length()==1?2:1;var logo=ResourceLocation.fromNamespaceAndPath("splineroads","textures/road/cb_logo_"+type+"_"+variant+".png");int size=(int)Math.round(.65*scale);g.blit(logo,(int)Math.round(cx+(f.x()-.325)*scale),(int)Math.round(bottom-(f.y()+.03125+.325)*scale),size,size,0,0,1,1,1,1);}
      pose.pushPose();pose.translate(cx+f.x()*scale,bottom-f.y()*scale,110);pose.scale((float)fs,(float)fs,1);
      float dx=f.align().equals("left")?0:f.align().equals("right")?-width:-width/2f;
      g.drawString(font,styled,(int)dx,-4,empty?0xb2b9ba:f.color(),false);pose.popPose();
      if(markers&&!empty){pose.pushPose();pose.translate(0,0,120);g.drawString(font,Integer.toString(i+1),(int)(cx+f.x()*scale)-3,(int)(bottom-f.y()*scale-fs*5)-8,0xffd878,true);pose.popPose();}
    }
    g.flush();g.disableScissor();
  }
  private RoadSignPreview(){}
}
