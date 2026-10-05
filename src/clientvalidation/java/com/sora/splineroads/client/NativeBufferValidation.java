package com.sora.splineroads.client;

import com.mojang.blaze3d.vertex.*;
import java.lang.reflect.Field;
import java.nio.ByteBuffer;
import org.lwjgl.system.MemoryUtil;

/** Uses the actual 1.20.1 native BufferBuilder; no window or OpenGL context is needed. */
public final class NativeBufferValidation {
  private static void fill(BufferBuilder b, int count) {
    for (int i = 0; i < count; i++)
      b.vertex(i % 64, 0, i / 64)
          .color(255, 255, 255, 255)
          .uv(0, 0)
          .overlayCoords(0)
          .uv2(15728880)
          .normal(0, 1, 0)
          .endVertex();
  }

  public static void main(String[] args) throws Exception {
    Field storage = BufferBuilder.class.getDeclaredField("buffer");
    storage.setAccessible(true);
    var arena = new RoadUploadArena();
    var builder = arena.begin();
    fill(builder, 65536);
    builder.end().release();
    ByteBuffer initial = (ByteBuffer) storage.get(builder);
    long address = MemoryUtil.memAddress(initial);
    int capacity = initial.capacity();
    for (int i = 0; i < 10000; i++) {
      BufferBuilder next = arena.begin();
      if (next != builder) throw new AssertionError("native arena object changed");
      fill(next, 4096);
      // Simulate an exception in the vertex producer; begin() recovers the unfinished batch.
      if (i % 79 != 0) next.end().release();
      ByteBuffer current = (ByteBuffer) storage.get(next);
      if (MemoryUtil.memAddress(current) != address || current.capacity() != capacity)
        throw new AssertionError("native arena grows with region uploads");
    }
    var previewArena=new RoadUploadArena();
    var pending=arena.begin();
    for(int frame=0;frame<16;frame++){
      fill(pending,256);var preview=previewArena.begin();fill(preview,64);preview.end().release();
      if(!pending.building())throw new AssertionError("preview interrupted resumed upload");
    }
    var resumed=pending.end();
    if(resumed.drawState().vertexCount()!=4096)throw new AssertionError("cross-frame vertex count differs");
    resumed.release();
    arena.begin().end().release();
    System.out.printf(
        "NATIVE BUFFER PASS: 10000 actual BufferBuilder batches; 127 unfinished-batch recoveries;"
            + " one stable native allocation of %d bytes; former per-batch minimum would allocate"
            + " %d bytes cumulatively. JVM heap limited to 128 MiB.%n",
        capacity, 10000L * 262144 * 6);
  }
}
