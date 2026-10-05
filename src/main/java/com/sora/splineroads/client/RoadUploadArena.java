package com.sora.splineroads.client;

import com.mojang.blaze3d.vertex.*;

/** Render-thread native scratch storage, reused for every material, region and lighting refresh. */
final class RoadUploadArena {
  private BufferBuilder builder;

  BufferBuilder begin() {
    if (builder == null) builder = new BufferBuilder(4096);
    // A failed producer may have left an unfinished batch. It must not pin another batch's bytes.
    if (builder.building()) builder.end().release();
    builder.discard();
    builder.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.NEW_ENTITY);
    return builder;
  }
}
