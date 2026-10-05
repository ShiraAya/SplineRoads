package com.sora.splineroads.world;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.*;
/** Excavated air with no collision, visibility or ticking, but no liquid refill. */
public final class TunnelAir extends AirBlock implements LiquidBlockContainer {
  public TunnelAir(){super(Properties.of().air().noCollission().noOcclusion().replaceable().strength(-1,3600000).noLootTable());}
  @Override public boolean canPlaceLiquid(BlockGetter world,BlockPos pos,BlockState state,Fluid fluid){return false;}
  @Override public boolean placeLiquid(LevelAccessor world,BlockPos pos,BlockState state,FluidState fluid){return false;}
}
