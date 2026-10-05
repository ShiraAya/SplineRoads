package com.sora.splineroads.world;
import com.sora.splineroads.core.*;
import com.sora.splineroads.core.RoadGeometry.*;
import com.sora.splineroads.core.RoadSigns.*;
import com.sora.splineroads.net.RoadNetwork;
import java.util.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.*;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
public final class RoadSignTool extends Item {
  private final boolean pole;
  public RoadSignTool(boolean pole){super(new Properties().stacksTo(1));this.pole=pole;}
  @Override public InteractionResult useOn(UseOnContext context){
    if(context.getLevel().isClientSide)return InteractionResult.SUCCESS;
    if(!(context.getPlayer() instanceof ServerPlayer player))return InteractionResult.PASS;
    try {
      var data=RoadData.get(player.serverLevel());var hit=context.getClickLocation();V point=new V(hit.x,hit.y,hit.z);
      var ids=data.index.at(context.getClickedPos().asLong());RoadIndex.Built chosen=null;double best=Double.POSITIVE_INFINITY;
      for(var id:ids){var r=data.index.roads.get(id);if(r.record.junction()!=null||r.mesh.settings().structure()==Structure.TUNNEL)continue;double d=RoadQueries.horizontal(r.mesh,point).sample().center().distance(point);if(d<best){best=d;chosen=r;}}
      if(chosen==null)throw new IllegalArgumentException("请右键道路、龙门架或已安装的路牌");
      RoadData.requireOwner(player,chosen.record.owner());if(!player.mayBuild()||player.isSpectator())throw new IllegalArgumentException("没有建设权限");
      var existing=chosen.record.settings().options().infrastructure().signs();Attachment selected=null;double nearest=6;
      if(!pole&&!player.isShiftKeyDown())for(var a:existing){var p=RoadSigns.place(chosen.mesh,a).part();double d=point.distance(p.a().add(p.b()).mul(.5).add(new V(0,p.height()/2,0)));if(d<nearest){nearest=d;selected=a;}}
      var payload=new CompoundTag();payload.putString("Kind","sign");payload.putUUID("Id",chosen.record.id());payload.putInt("Signature",GantryTool.signature(chosen.record));payload.put("Settings",RoadRecord.writeSettings(chosen.record.settings()));payload.putInt("Count",RoadGantry.count(chosen.mesh));
      var sample=RoadQueries.horizontal(chosen.mesh,point).sample();payload.putDouble("HalfWidth",sample.halfWidth());
      if(selected==null){int next=0;var used=existing.stream().map(Attachment::id).collect(java.util.stream.Collectors.toSet());while(used.contains(next))next++;if(next>=128)throw new IllegalArgumentException("本段最多安装 128 件设施");
        int slot=0;double min=Double.POSITIVE_INFINITY;for(int i=0;i<RoadGantry.count(chosen.mesh);i++){double d=Math.abs(sample.distance()-RoadGantry.station(chosen.mesh,i).distance());if(d<min){min=d;slot=i;}}
        boolean roadside=pole||RoadGantry.count(chosen.mesh)==0;double side=point.sub(sample.center()).dot(sample.left())<0?-1:1;
        selected=new Attachment(next,"sign_guide_intersection_warning_1",roadside?Mount.POLE:Mount.GANTRY,slot,Math.max(.02,Math.min(.98,sample.distance()/chosen.mesh.length())),roadside?side*Math.max(1.5,chosen.mesh.settings().options().sidewalk().enabled()?chosen.mesh.settings().options().sidewalk().width()-.35:1.5):0,roadside?3:5.6,1,false,List.of());
        payload.putBoolean("New",true);
      }
      payload.put("Attachment",RoadSignCodec.write(selected));RoadNetwork.open(player,payload);
    }catch(IllegalArgumentException e){player.displayClientMessage(Component.literal(e.getMessage()),false);}
    return InteractionResult.CONSUME;
  }
  @Override public void appendHoverText(ItemStack stack,Level level,List<Component> lines,TooltipFlag flag){lines.add(Component.literal(pole?"右键道路增设路杆及标牌、设备":"右键标牌编辑文字；Shift＋右键道路/龙门架增加设施"));}
}
