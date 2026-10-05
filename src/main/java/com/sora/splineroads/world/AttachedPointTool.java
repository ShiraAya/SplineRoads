package com.sora.splineroads.world;

import com.sora.splineroads.core.*;
import com.sora.splineroads.core.RoadGeometry.*;
import com.sora.splineroads.net.RoadNetwork;
import net.minecraft.nbt.*;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.*;
import net.minecraft.world.*;
import net.minecraft.world.item.*;
import net.minecraft.world.item.context.UseOnContext;
import java.util.*;

public final class AttachedPointTool extends Item {
  public AttachedPointTool(){super(new Properties().stacksTo(1));}
  public static RoadAttachments.Point nearest(RoadIndex.Built road,V hit){return road.record.settings().options().attachments().points().stream().filter(p->p.position().distance(hit)<1.1).min(Comparator.comparingDouble(p->p.position().distance(hit))).orElse(null);}
  @Override public InteractionResult useOn(UseOnContext c){
    if(c.getLevel().isClientSide)return InteractionResult.SUCCESS;
    if(c.getPlayer() instanceof ServerPlayer p)try{
      if(!p.mayBuild()||p.isSpectator())throw new IllegalArgumentException("没有建设权限");
      var road=RoadTool.pick(p);if(road==null||road.record.junction()!=null)throw new IllegalArgumentException("请右键道路路面，不能选路口中心铺装");
      RoadData.requireOwner(p,road.record.owner());var hit=c.getClickLocation();V v=new V(hit.x,hit.y,hit.z);var point=nearest(road,v);
      if(point==null){var q=RoadQueries.horizontal(road.mesh,v);if(q.horizontalDistance()>q.sample().halfWidth()+.05)throw new IllegalArgumentException("附属点必须位于道路路面");
        V pos=new V(v.x(),q.sample().center().y(),v.z());point=new RoadAttachments.Point(UUID.randomUUID(),pos,pos,false);
        var data=road.record.settings().options().attachments();var points=new ArrayList<>(data.points());points.add(point);
        RoadData.get(p.serverLevel()).updatePointMetadata(p.serverLevel(),p,road.record,data.points(points));
        road=RoadData.get(p.serverLevel()).index.roads.get(road.record.id());
      }
      RoadNetwork.open(p,payload(road.record,point));
    }catch(IllegalArgumentException e){p.displayClientMessage(Component.literal(e.getMessage()),false);}
    return InteractionResult.CONSUME;
  }
  public static CompoundTag payload(RoadRecord r,RoadAttachments.Point p){var t=new CompoundTag();t.putString("Kind","attachedPoint");t.put("Road",r.header());t.putUUID("Id",r.id());t.putUUID("Point",p.id());t.putInt("Signature",GantryTool.signature(r));t.put("Position",RoadRecord.writeNode(new Node(p.position(),0,0)));t.put("Origin",RoadRecord.writeNode(new Node(p.origin(),0,0)));t.putBoolean("MarkingsOnly",r.assembly()!=null);t.putBoolean("XYZ",r.settings().mode()==Mode.CURVE);return t;}
  public static String edit(ServerLevel level,ServerPlayer player,CompoundTag t){
    var data=RoadData.get(level);var built=data.index.roads.get(t.getUUID("Id"));if(built==null)throw new IllegalArgumentException("道路不存在");var r=built.record;
    if(player!=null){RoadData.requireOwner(player,r.owner());if(!player.mayBuild()||player.isSpectator())throw new IllegalArgumentException("没有建设权限");}
    if(t.getInt("Signature")!=GantryTool.signature(r))throw new IllegalArgumentException("道路已改变，请重新选择附属点");
    var metadata=r.settings().options().attachments();var point=metadata.points().stream().filter(p->p.id().equals(t.getUUID("Point"))).findFirst().orElseThrow(()->new IllegalArgumentException("附属点已改变所属道路，请重新选择"));
    var points=new ArrayList<>(metadata.points());
    if(t.getBoolean("Delete")){
      double d=RoadAttachments.station(built.mesh,point.position());
      if(d>1e-5&&d<built.mesh.length()-1e-5&&!RoadAttachments.paint(built.mesh,d-1e-5).equals(RoadAttachments.paint(built.mesh,d+1e-5)))throw new IllegalArgumentException("附属点两侧标线不同，请先统一两个区间的标线后再删除");
      points.remove(point);if(point.controlled())data.removeAttachedPoint(level,player,r,metadata.points(points),point.id());else data.updatePointMetadata(level,player,r,metadata.points(points));return "附属点已删除，其调形影响已撤销";
    }
    if(r.assembly()!=null)throw new IllegalArgumentException("立交主路附属点仅用于标线分段，不能调形");
    V target=t.contains("Offset")?point.origin().add(RoadRecord.readNode(t.getCompound("Offset")).position()):RoadRecord.readNode(t.getCompound("Position")).position();
    if(!RoadGeometry.finite(target.x(),target.y(),target.z())||Math.abs(target.x())>29999984||Math.abs(target.z())>29999984||target.y()<level.getMinBuildHeight()||target.y()>=level.getMaxBuildHeight())throw new IllegalArgumentException("坐标超出世界范围");
    if(r.settings().mode()!=Mode.CURVE&&(Math.abs(target.x()-point.position().x())>1e-7||Math.abs(target.z()-point.position().z())>1e-7))throw new IllegalArgumentException("只有平滑曲线附属点可调整 X/Z");
    points.set(points.indexOf(point),point.at(target));
    var next=r.withAttachments(metadata.points(points)).structures(List.of());var proposed=new RoadIndex.Built(next);
    for(var other:data.index.roads.values())if(!other.record.id().equals(r.id())&&RoadIndex.overlapXZ(proposed.mesh,other.mesh,1))Interchanges.checkExternal(proposed.mesh,other.mesh,built.mesh);
    data.replaceAssembly(level,player,List.of(proposed),Set.of(r.id()),Set.of(r.a(),r.b()));return "附属点及道路形状已更新";
  }
}
