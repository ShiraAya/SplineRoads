package com.sora.splineroads.world;
import com.sora.splineroads.core.*;
import com.sora.splineroads.core.RoadGeometry.*;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.gametest.*;
import java.util.*;

@GameTestHolder("splineroads_live443") @PrefixGameTestTemplate(false)
public final class Live443GameTests {
  static RoadRecord road(V a,V b){
    var d=b.sub(a).horizontalUnit();var start=new Node(a,RoadPlanner.yaw(d),0);var end=new Node(b,start.yaw(),0);
    var s=new Settings(Mode.STRAIGHT,Style.C1_RAMP,4,1,.35,90).structure(Structure.BRIDGE);
    s=s.options(s.options().route(s.options().routing().fit(false)));
    return new RoadRecord(UUID.randomUUID(),new UUID(443,1),BlockPos.containing(a.x(),a.y(),a.z()),BlockPos.containing(b.x(),b.y(),b.z()),start,end,s,false,4);
  }
  @GameTest(batch="splineroads_live443",template="empty",templateNamespace="splineroads_live443",timeoutTicks=12000)
  public static void supportsAvoidUnbuiltLowerRoad(GameTestHelper h){
    var level=h.getLevel();int x=224000,z=224000;
    for(int xx=x-48;xx<=x+48;xx++)for(int zz=z-4;zz<=z+104;zz++){var p=new BlockPos(xx,188,zz);level.getChunkAt(p);level.setBlock(p,Blocks.STONE.defaultBlockState(),2);}
    var upper=new RoadIndex.Built(road(new V(x,208,z),new V(x,208,z+96)));
    var lower=new RoadIndex.Built(road(new V(x-40,195,z+12),new V(x+40,195,z+12)));
    h.assertTrue(!lower.rasterized(),"fixture already rasterized lower candidate");
    var plain=StructurePlanner.plan(level,upper,List.of(upper),Map.of(),new HashMap<>());
    h.assertTrue(plain.structures().stream().anyMatch(p->p.pier()&&RoadClearance.structureInvades(p,lower.mesh,4.25)),"fixture did not put a support through lower road");
    var checked=StructurePlanner.plan(level,upper,List.of(upper,lower),Map.of(),new HashMap<>());
    h.assertTrue(checked.structures().stream().anyMatch(RoadStructures.Part::pier),"collision fix erased all supports");
    for(var p:checked.structures())if(p.pier())h.assertTrue(!RoadClearance.structureInvades(p,lower.mesh,4.25),"planned shaft crosses lower live road");
    h.assertTrue(RoadRecord.load(checked.save()).structures().equals(checked.structures()),"precise support layout lost in Mojang NBT");
    System.out.println("LIVE443 REAL_SERVER PASS exact support planning against an unrasterized candidate and Mojang NBT");h.succeed();
  }
}
