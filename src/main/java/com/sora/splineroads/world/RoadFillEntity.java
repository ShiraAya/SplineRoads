package com.sora.splineroads.world;

import com.sora.splineroads.SplineRoads;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.*;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/** Custom material infill uses an entity; common terrain and sidewalks use baked block models. */
public final class RoadFillEntity extends BlockEntity {
  private BlockState fill=Blocks.AIR.defaultBlockState();
  public RoadFillEntity(BlockPos pos,BlockState state){super(SplineRoads.FILL_ENTITY.get(),pos,state);}
  public BlockState fill(){return fill;}
  public void fill(BlockState state){fill=state;setChanged();if(level!=null)level.sendBlockUpdated(worldPosition,getBlockState(),getBlockState(),3);}
  @Override protected void saveAdditional(CompoundTag t){super.saveAdditional(t);t.put("Fill",NbtUtils.writeBlockState(fill));}
  @Override public void load(CompoundTag t){super.load(t);fill=NbtUtils.readBlockState(BuiltInRegistries.BLOCK.asLookup(),t.getCompound("Fill"));}
  @Override public CompoundTag getUpdateTag(){return saveWithoutMetadata();}
  @Override public ClientboundBlockEntityDataPacket getUpdatePacket(){return ClientboundBlockEntityDataPacket.create(this);}
}
