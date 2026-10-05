package com.sora.splineroads.world;
import com.sora.splineroads.core.*;
import com.sora.splineroads.core.RoadGeometry.*;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.*;
import java.util.*;
/** Validates both sides and stages neighbor changes in the caller's single world transaction. */
final class SharedRoadEndpoints {
  static void validate(RoadData data,BlockPos at,Node target){
    var ids=data.index.atNode(at);if(ids.size()<2)return;
    if(ids.size()!=2||AutoJunctions.center(data,at)!=null)throw new IllegalArgumentException("共享端点属于路口或分岔，不能自动移动");
    var roads=ids.stream().map(id->data.index.roads.get(id).record).toList();var a=roads.get(0);var b=roads.get(1);V shared=a.a().equals(at)?a.start().position():a.end().position();
    if(a.assembly()!=null||b.assembly()!=null||a.junction()!=null||b.junction()!=null||!RoadContinuity.compatible(a.mesh(),b.mesh(),shared))throw new IllegalArgumentException("共享端点邻路须为适用范围内共线、水平且分方向车道数相同的道路");
    V axis=a.end().position().sub(a.start().position()).horizontalUnit();
    if(Math.abs(target.position().y()-shared.y())>.001||Math.abs(target.position().sub(shared).dot(axis.left()))>.001)throw new IllegalArgumentException("共享端点只能沿原水平道路移动，不能引入折角或坡度");
    for(var r:roads){V far=r.a().equals(at)?r.end().position():r.start().position();if(target.position().sub(far).dot(shared.sub(far))<=0||target.position().sub(far).horizontalLength()<2)throw new IllegalArgumentException("共享端点不能越过外端点或使相邻道路不足两格");}
  }
  static void stage(RoadData data,ServerLevel level,ServerPlayer player,BlockPos at,Node target,List<RoadIndex.Built> built,Set<UUID> removed){
    var outside=data.index.atNode(at).stream().filter(id->!removed.contains(id)).toList();if(outside.isEmpty())return;validate(data,at,target);
    BlockPos to=BlockPos.containing(target.position().x(),target.position().y(),target.position().z());
    for(UUID id:outside){var r=data.index.roads.get(id).record;if(player!=null)RoadData.requireOwner(player,r.owner());boolean first=r.a().equals(at);Node a=first?new Node(target.position(),r.start().yaw(),0):r.start(),b=first?r.end():new Node(target.position(),r.end().yaw(),0);
      var next=new RoadRecord(r.id(),r.owner(),first?to:r.a(),first?r.b():to,a,b,r.settings(),r.automatic(),r.clearance(),List.of(),r.endCaps(),r.buildVersion(),null,List.of(),r.furniturePhase());
      built.add(new RoadIndex.Built(next));removed.add(id);
    }
  }
  private SharedRoadEndpoints(){}
}
