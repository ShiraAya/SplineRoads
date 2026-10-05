package com.sora.splineroads.world;

import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import java.io.*;
import java.util.*;
import net.minecraft.nbt.*;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

public final class Storage292Validation {
  static int checks;
  static void check(boolean ok, String message) { checks++; if (!ok) throw new AssertionError(message); }
  static long heap() { var r=Runtime.getRuntime(); return r.totalMemory()-r.freeMemory(); }
  public static void main(String[] args) throws Exception {
    net.minecraft.SharedConstants.tryDetectVersion();
    try { net.minecraft.server.Bootstrap.bootStrap(); }
    catch (ExceptionInInitializerError failure) {
      // This headless codec benchmark has no Forge launch transformer. Registries have
      // already bootstrapped; only the subsequent injected networking constructor is absent.
      Throwable cause=failure;while(cause.getCause()!=null)cause=cause.getCause();
      if (!(cause instanceof NoSuchMethodException) || !"net.minecraftforge.network.NetworkEvent.<init>()".equals(cause.getMessage())) throw failure;
    }
    if (args.length > 0 && args[0].equals("legacy")) {
      Map<Long,BlockState> originals=new HashMap<>();
      for (int i=0;i<1_000_000;i++) originals.put((long)i,Blocks.AIR.defaultBlockState());
      ListTag original=new ListTag();
      originals.forEach((p,s)->{var t=new CompoundTag();t.putLong("Pos",p);t.put("State",NbtUtils.writeBlockState(s));original.add(t);});
      System.out.println("LEGACY COMPLETED: "+original.size()); return;
    }
    var air=Blocks.AIR.defaultBlockState();var stone=Blocks.STONE.defaultBlockState();
    var stair=Blocks.OAK_STAIRS.defaultBlockState().setValue(BlockStateProperties.WATERLOGGED,true);
    var fixture=new RoadData();var originalsField=RoadData.class.getDeclaredField("originals");originalsField.setAccessible(true);
    @SuppressWarnings("unchecked") var blocks=(Long2ObjectOpenHashMap<BlockState>)originalsField.get(fixture);
    for(int i=0;i<1_000_000;i++) blocks.put(BlockPos.asLong(i%4096-2048,i%384-64,i/4096-128),i%9==0?stair:i%3==0?stone:air);
    long start=System.nanoTime();var root=fixture.save(new CompoundTag());var palette=root.getList("OriginalPalette",10);
    check(palette.size()==3,"one state record per unique state");
    var bytes=new ByteArrayOutputStream();NbtIo.writeCompressed(root,bytes);
    var decoded=NbtIo.readCompressed(new ByteArrayInputStream(bytes.toByteArray()));
    @SuppressWarnings("unchecked") var loaded=(Long2ObjectOpenHashMap<BlockState>)originalsField.get(RoadData.load(decoded));
    check(loaded.equals(blocks),"million negative/positive positions, air and properties round-trip exactly");
    System.out.printf(Locale.ROOT,"COMPACT million blocks: %.2f ms save/compress/read, %d compressed bytes, %.1f MiB heap used, %.1f MiB heap cap%n",(System.nanoTime()-start)/1e6,bytes.size(),heap()/1048576d,Runtime.getRuntime().maxMemory()/1048576d);
    // Use the actual RoadData old-save reader, including custom entity NBT and sidewalk ownership.
    var legacy=new CompoundTag();legacy.putInt("Version",17);var old=new ListTag();var walks=new ListTag();
    var entity=new CompoundTag();entity.putString("id","minecraft:chest");entity.putString("CustomName","保留箱子数据");entity.putLongArray("CustomModData",new long[]{-77,0,Long.MAX_VALUE});
    for(int i=0;i<256;i++){var t=new CompoundTag();t.putLong("Pos",i-128);t.put("State",NbtUtils.writeBlockState(i==0?Blocks.CHEST.defaultBlockState():i%3==0?air:stair));if(i==0)t.put("Entity",entity);old.add(t);if(i%7==0)walks.add(t.copy());}
    legacy.put("Originals",old);legacy.put("SidewalkBlocks",walks);
    var converted=RoadData.load(legacy).save(new CompoundTag());
    check(converted.getInt("Version")==19&&!converted.contains("Originals"),"old format migrates with explicit version guard");
    var values=new Long2ObjectOpenHashMap<BlockState>();RoadBlockStorage.read(converted.getList("OriginalPalette",10),values);
    check(values.size()==256&&values.get(-127L)==stair&&values.get(-125L)==air,"legacy restored states retain properties and air");
    check(converted.getList("OriginalEntities",10).getCompound(0).getCompound("Entity").equals(entity),"entity data is lossless");
    values.clear();RoadBlockStorage.read(converted.getList("SidewalkPalette",10),values);check(values.size()==walks.size(),"sidewalk ownership survives migration");
    var again=RoadData.load(converted).save(new CompoundTag());
    check(again.getList("OriginalEntities",10).equals(converted.getList("OriginalEntities",10)),"entity data survives another save");
    boolean rejected=false;var corrupt=converted.copy();corrupt.remove("OriginalPalette");try{RoadData.load(corrupt);}catch(IllegalStateException e){rejected=true;}check(rejected,"missing compact journal is rejected, not silently discarded");
    System.out.println("STORAGE 292 PASS: "+checks+" checks");
  }
}
