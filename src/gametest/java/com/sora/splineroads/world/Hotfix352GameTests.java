package com.sora.splineroads.world;
import com.sora.splineroads.core.*;
import com.sora.splineroads.core.RoadGeometry.*;
import com.sora.splineroads.core.RoadStructures.*;
import com.sora.splineroads.SplineRoads;
import com.sora.splineroads.net.RoadNetwork;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.*;
import net.minecraft.world.item.*;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.gametest.*;
import java.util.*;
@GameTestHolder("splineroads_hotfix352") @PrefixGameTestTemplate(false)
public final class Hotfix352GameTests {
 @GameTest(template="empty",templateNamespace="splineroads_hotfix352",timeoutTicks=12000)
 public static void floatingStraightCurveArcWithIntegerHeightAndDefaultGantry(GameTestHelper h){
  var l=h.getLevel();var d=RoadData.get(l);var player=FakePlayerFactory.getMinecraft(l);player.setGameMode(net.minecraft.world.level.GameType.CREATIVE);int x=86000,z=86000;
  for(int xx=x-24;xx<x+145;xx++)for(int zz=z-60;zz<z+24;zz++){var p=new BlockPos(xx,0,zz);l.getChunkAt(p);l.setBlock(p,Blocks.GRASS_BLOCK.defaultBlockState(),2);}
  for(int xx=x;xx<=x+100;xx++)l.setBlock(new BlockPos(xx,11,z),Blocks.STONE.defaultBlockState(),2);
  var a=Revision32GameTests.marker(h,x,12,z,-90);var b=Revision32GameTests.marker(h,x+100,12,z,-90);
  for(var p:List.of(a,b)){var n=(NodeEntity)l.getBlockEntity(p);n.owner=player.getUUID();n.apply(new Node(new V(p.getX()+.5,12,p.getZ()+.5),-90,0));}
  int cases=0;
  for(Mode mode:List.of(Mode.STRAIGHT,Mode.CURVE,Mode.ARC))for(Structure kind:List.of(Structure.AUTO,Structure.BRIDGE))for(Style style:List.of(Style.O6_GREEN,Style.H6_GREEN)){
   var settings=new Settings(mode,style,style.defaultWidth(),1,.4,60).structure(kind);
   var stack=new ItemStack(SplineRoads.CONNECTOR.get());stack.getOrCreateTag().putLong("Start",a.asLong());stack.getTag().putString("Dimension",l.dimension().location().toString());player.setItemInHand(InteractionHand.MAIN_HAND,stack);
   var command=new CompoundTag();command.putString("Action","connect");command.putLong("A",a.asLong());command.putLong("B",b.asLong());command.put("Settings",RoadRecord.writeSettings(settings));RoadNetwork.forget(player.getUUID());RoadNetwork.perform(player,command);
   var r=d.index.roads.values().stream().filter(v->v.record.a().equals(a)&&v.record.b().equals(b)).findFirst().orElseThrow().record;
   long piers=r.structures().stream().filter(p->p.pier()&&p.material()==Material.CONCRETE&&p.height()>2).count();long gantries=r.structures().stream().filter(p->!p.pier()&&p.material()==Material.STEEL&&p.a().y()>17&&p.a().sub(p.b()).horizontalLength()>10).count();
   System.out.println("FLOATING "+mode+" "+kind+" "+style+" piers="+piers+" gantries="+gantries);
   h.assertTrue(piers>=4,"floating piers "+mode+" "+kind+" "+style+": "+piers);h.assertTrue(gantries>0,"floating default gantry "+mode+" "+kind+" "+style);
   h.assertTrue(r.structures().stream().noneMatch(p->p.material()==Material.GREEN||p.material()==Material.SOIL),"floating median is barrier "+mode+" "+kind);
   h.assertTrue(RoadRecord.load(r.save()).structures().equals(r.structures()),"wire/save retains all structures");
   // Rebuild while the construction strip is now stored under SR collider blocks.
   var rebuilt=d.connect(l,player,a,b,settings,r.id(),false);
   h.assertTrue(rebuilt.structures().stream().filter(p->p.pier()&&p.material()==Material.CONCRETE&&p.height()>2).count()>=4,"rebuild keeps real foundations");
   h.assertTrue(rebuilt.structures().stream().noneMatch(p->p.material()==Material.GREEN||p.material()==Material.SOIL),"rebuild keeps median barrier");
   for(var pier:rebuilt.structures())if(pier.pier()&&pier.material()==Material.CONCRETE&&pier.height()>2)h.assertTrue(Math.abs(pier.a().y()-1)<1e-6,"pier reaches actual grass foundation");
   if(mode==Mode.STRAIGHT&&kind==Structure.AUTO&&style==Style.O6_GREEN){
    var noisy=settings.options(settings.options().outerRail(RoadProfile.OuterRail.SOUND_BOTH));var nr=d.connect(l,player,a,b,noisy,r.id(),false);
    h.assertTrue(nr.structures().stream().filter(p->p.material()==Material.CB_NOISE).count()>90,"noise barriers build in world");
    h.assertTrue(RoadRecord.load(nr.save()).structures().equals(nr.structures()),"noise mesh survives save and wire");
   }
   d.remove(l,player,r.id());cases++;
  }
  System.out.println("FLOATING MATRIX PASS "+cases);player.setItemInHand(InteractionHand.MAIN_HAND,ItemStack.EMPTY);RoadNetwork.forget(player.getUUID());h.succeed();
 }
}
