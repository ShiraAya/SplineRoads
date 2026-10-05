package com.sora.splineroads.world;
import com.sora.splineroads.SplineRoads;
import com.sora.splineroads.core.*;
import com.sora.splineroads.core.RoadGeometry.*;
import com.sora.splineroads.core.RoadInfrastructure.*;
import com.sora.splineroads.net.RoadNetwork;
import java.util.*;
import net.minecraft.core.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.gametest.*;

@GameTestHolder("splineroads_revision29") @PrefixGameTestTemplate(false)
public final class Revision29GameTests {
  static Settings road(Style style,Structure type,Config c){return Revision28GameTests.road(style,type,c);}
  static BlockPos node(GameTestHelper h,int x,int y,int z){return Revision28GameTests.marker(h,x,y,z);}
  static CompoundTag edit(RoadRecord r,int slot,double offset,double height,Gantry kind){
    var t=new CompoundTag();t.putString("Action","gantry");t.putUUID("Id",r.id());t.putInt("Signature",GantryTool.signature(r));t.putInt("Slot",slot);t.putDouble("Offset",offset);t.putDouble("Clearance",height);t.putString("Style",kind.name());return t;
  }
  @GameTest(template="empty",templateNamespace="splineroads_revision28",timeoutTicks=12000)
  public static void wideTunnelSurfaceLighting(GameTestHelper h){
    var level=h.getLevel();var data=RoadData.get(level);var built=new ArrayList<RoadIndex.Built>();Set<Long> forced=new HashSet<>();
    for(var type:Tunnel.values()){
      int z=11200+type.ordinal()*96;var a=node(h,11200,4,z);var b=node(h,11248,4,z);
      var r=data.connect(level,null,a,b,road(Style.O6_YELLOW,Structure.TUNNEL,Config.DEFAULT.grade(.2).tunnel(type)),null);var value=data.index.roads.get(r.id());built.add(value);
      forced.addAll(value.chunks);
    }
    for(long key:forced){var c=new net.minecraft.world.level.ChunkPos(key);level.setChunkForced(c.x,c.z,true);}
    h.runAfterDelay(60,()->{
      try{
        RoadLighting.Access light=new RoadLighting.Access(){
          public boolean opaque(int x,int y,int z){var p=new BlockPos(x,y,z);return level.getBlockState(p).getLightBlock(level,p)>=15;}
          public int packed(int x,int y,int z){return level.getBrightness(LightLayer.BLOCK,new BlockPos(x,y,z))<<4;}
        };
        for(var r:built){
          int min=15,faces=0;double z=r.mesh.first().center().z();
          for(int x=11212;x<=11236;x+=4)for(double lateral:new double[]{-9,-6,-3,0,3,6,9}){
            var pos=BlockPos.containing(x,r.mesh.first().center().y()+1,z+lateral);int value=level.getBrightness(LightLayer.BLOCK,pos);min=Math.min(min,value);
            h.assertTrue(value>=3,"real block light reaches every driving lane: "+pos+" = "+value);
          }
          for(var part:r.record.structures())if(part.material()==RoadStructures.Material.TUNNEL)for(var face:part.faces()){
            var n=RoadLighting.normal(face);if(n.y()>-.5)continue;
            if(face.points().stream().anyMatch(p->p.x()<11212||p.x()>11236))continue;
            // Lining bands on the outside of the wall are hidden; inspect the visible interior roof.
            if(face.points().stream().anyMatch(p->Math.abs(p.z()-z)>r.mesh.first().halfWidth()-.8))continue;
            for(V p:face.points())h.assertTrue(RoadLighting.sample(face,p,light)>0,"ceiling vertices sample illuminated interior air");faces++;
          }
          int pavements=0;
          for(var face:RoadSurface.build(r.mesh,List.of(),List.of()).pavement())if(face.color()==0xDCDCDC){
            for(V p:face.points())h.assertTrue(RoadLighting.sample(face,p,light,true)>0,"actual pavement face corners never inherit black wall light");pavements++;
          }
          h.assertTrue(pavements>0,"actual rendered pavement triangles probed");
          h.assertTrue(faces>8,"wide tunnel roof faces actually probed");
          System.out.println("Tunnel "+r.record.settings().options().infrastructure().tunnel()+" lane min="+min+" lit roof faces="+faces);
          data.remove(level,null,r.record.id());
        }
        h.succeed();
      }finally{for(long key:forced){var c=new net.minecraft.world.level.ChunkPos(key);level.setChunkForced(c.x,c.z,false);}}
    });
  }
  @GameTest(template="empty",templateNamespace="splineroads_revision28",timeoutTicks=12000)
  public static void automaticDipRoundTripAndExcavation(GameTestHelper h){
    var level=h.getLevel();var data=RoadData.get(level);var a=node(h,11800,12,11800);var b=node(h,12040,14,11800);
    var stone=new BlockPos(11920,8,11800);level.setBlock(stone,Blocks.STONE.defaultBlockState(),2);
    var s=road(Style.O2_YELLOW,Structure.TUNNEL,Config.DEFAULT.grade(.2).tunnel(Tunnel.ARCH).depth(6));
    var r=data.connect(level,null,a,b,s,null);var built=data.index.roads.get(r.id());
    h.assertTrue(Math.abs(built.mesh.samples().stream().mapToDouble(p->p.center().y()).min().orElseThrow()-6.25)<1e-7,"two mouth nodes generate requested minimum road elevation");
    h.assertTrue(level.getBlockState(stone).isAir(),"dipped tube is excavated at generated elevation");
    var reload=RoadData.load(data.save(new CompoundTag())).index.roads.get(r.id());
    h.assertTrue(reload.mesh.samples().equals(built.mesh.samples())&&reload.record.structures().equals(built.record.structures()),"dip, roof and lamps reload exactly");
    var legacy=RoadRecord.writeSettings(s);var old=legacy.getCompound("Infrastructure");old.remove("TunnelDepth");old.remove("GantryEdits");
    h.assertTrue(RoadRecord.readSettings(legacy).options().infrastructure().tunnelDepth()==0,"0.28 facility data keeps its original route");
    data.remove(level,null,r.id());h.assertTrue(level.getBlockState(stone).is(Blocks.STONE),"tunnel removal restores underground terrain");h.succeed();
  }
  @GameTest(template="empty",templateNamespace="splineroads_revision28",timeoutTicks=12000)
  public static void singleGantryCommandSaveResetAndPermissions(GameTestHelper h){
    var level=h.getLevel();var data=RoadData.get(level);var a=node(h,12400,12,12400);var b=node(h,12656,12,12400);
    var s=road(Style.H4_RAIL,Structure.BRIDGE,Config.DEFAULT.grade(.2).gantry(Gantry.SIGNS));var r=data.connect(level,null,a,b,s,null);var original=data.index.roads.get(r.id());
    var player=net.minecraftforge.common.util.FakePlayerFactory.get(level,new com.mojang.authlib.GameProfile(new UUID(0,0),"SR29Tester"));player.setGameMode(net.minecraft.world.level.GameType.CREATIVE);
    player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND,new net.minecraft.world.item.ItemStack(SplineRoads.GANTRY_EDITOR.get()));
    var command=edit(original.record,0,8,8,Gantry.FRAME);RoadNetwork.forget(player.getUUID());RoadNetwork.perform(player,command);
    var next=data.index.roads.get(r.id());
    h.assertTrue(next.mesh.samples().equals(original.mesh.samples()),"gantry-only command keeps road geometry exact");
    double station=RoadGantry.station(original.mesh,1).sample().center().x();
    var before=original.record.structures().stream().filter(p->Math.abs(p.a().x()-station)<3&&Math.abs(p.b().x()-station)<3).toList();
    var after=next.record.structures().stream().filter(p->Math.abs(p.a().x()-station)<3&&Math.abs(p.b().x()-station)<3).toList();h.assertTrue(before.equals(after),"second gantry remains exactly unchanged");
    h.assertTrue(next.record.structures().stream().anyMatch(p->p.material()==RoadStructures.Material.GANTRY_FRAME),"first gantry changed to mountable equipment frame");
    var loaded=RoadData.load(data.save(new CompoundTag())).index.roads.get(r.id());h.assertTrue(loaded.record.settings().equals(next.record.settings())&&loaded.record.structures().equals(next.record.structures()),"individual override and structure persist through reload");
    boolean stale=false;try{data.editGantry(level,player,command);}catch(IllegalArgumentException expected){stale=expected.getMessage().contains("重新");}h.assertTrue(stale,"stale editor does not overwrite a later change");
    player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND,new net.minecraft.world.item.ItemStack(SplineRoads.CONNECTOR.get()));RoadNetwork.forget(player.getUUID());boolean wrong=false;
    try{RoadNetwork.perform(player,edit(next.record,0,0,6,Gantry.SIGNS));}catch(IllegalArgumentException expected){wrong=true;}h.assertTrue(wrong,"ordinary connector cannot send gantry editor commands");
    player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND,new net.minecraft.world.item.ItemStack(SplineRoads.GANTRY_EDITOR.get()));
    var reset=edit(next.record,0,0,5.6,Gantry.AUTO);reset.putBoolean("Reset",true);RoadNetwork.forget(player.getUUID());RoadNetwork.perform(player,reset);
    h.assertTrue(data.index.roads.get(r.id()).record.settings().options().infrastructure().gantryEdits().isEmpty(),"reset restores inherited station defaults");
    data.remove(level,null,r.id());h.succeed();
  }
  @GameTest(template="empty",templateNamespace="splineroads_revision28",timeoutTicks=12000)
  public static void diagonalEquipmentInfillSurvivesUpdateAndDeletion(GameTestHelper h){
    var level=h.getLevel();var data=RoadData.get(level);var a=node(h,13000,14,13000);var b=node(h,13128,14,13064);
    var s=road(Style.H4_RAIL,Structure.BRIDGE,Config.DEFAULT.grade(.2).gantry(Gantry.FRAME));var r=data.connect(level,null,a,b,s,null);var built=data.index.roads.get(r.id());var fill=Blocks.RED_WOOL.defaultBlockState();
    var cell=built.cells.keySet().stream().map(BlockPos::of).filter(p->RoadBlocks.gantryCell(data.index,p)&&RoadBlocks.canInfill(level,p,fill)).findFirst().orElseThrow();
    h.assertTrue(data.infill(level,cell,fill,null),"diagonal equipment beam accepts a full block in its partially occupied cell");
    var cmd=edit(built.record,0,0,5.6,Gantry.FRAME);data.editGantry(level,null,cmd);
    h.assertTrue(level.getBlockEntity(cell) instanceof RoadFillEntity f&&f.fill().equals(fill),"equipment mount survives road furniture rebuild");
    var loaded=RoadData.load(data.save(new CompoundTag()));h.assertTrue(loaded.save(new CompoundTag()).getList("OriginalPalette",10).stream().map(t->(CompoundTag)t).anyMatch(t->java.util.Arrays.stream(t.getLongArray("Positions")).anyMatch(pos->pos==cell.asLong())&&t.getCompound("State").equals(net.minecraft.nbt.NbtUtils.writeBlockState(fill))),"equipment mount's independent original survives save");
    data.remove(level,null,r.id());h.assertTrue(level.getBlockState(cell).equals(fill),"placed equipment remains after road removal");h.succeed();
  }
}
