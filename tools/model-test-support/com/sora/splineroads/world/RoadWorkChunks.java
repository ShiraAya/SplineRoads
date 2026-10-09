package com.sora.splineroads.world;
/** No world/chunk operations are simulated by model tests. */
final class RoadWorkChunks implements AutoCloseable {
 static RoadWorkChunks open(net.minecraft.server.level.ServerLevel level){throw new UnsupportedOperationException("no world chunks");}
 public void close(){}
}
