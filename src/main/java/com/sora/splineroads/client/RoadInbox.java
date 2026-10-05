package com.sora.splineroads.client;

import com.sora.splineroads.net.RoadWire;
import com.sora.splineroads.world.RoadIndex;
import com.sora.splineroads.world.RoadRecord;
import java.util.concurrent.*;
import java.util.function.Consumer;
import net.minecraft.nbt.CompoundTag;

/** Ordered background decoding. Only the client thread publishes roads into the live index. */
public final class RoadInbox {
  public record Event(CompoundTag tag, RoadIndex.Built built, String error) {}
  private record Ready(long epoch, Event event) {}
  private final ThreadPoolExecutor worker = new ThreadPoolExecutor(1, 1, 0,
      TimeUnit.SECONDS, new LinkedBlockingQueue<>(), task -> {
        Thread t = new Thread(task, "SplineRoads network decoder"); t.setDaemon(true); return t;
      });
  private final ArrayBlockingQueue<Ready> ready = new ArrayBlockingQueue<>(64);
  private final RoadWire.Assembler assembler = new RoadWire.Assembler();
  private volatile long epoch;
  private long workerEpoch = -1;

  public void reset() { epoch++; worker.getQueue().clear(); ready.clear(); }

  public void submit(RoadWire.Fragment fragment) {
    long generation = epoch;
    worker.execute(() -> {
      if (generation != epoch) return;
      if (workerEpoch != generation) { assembler.clear(); workerEpoch = generation; }
      Event result;
      try {
        CompoundTag tag = assembler.accept(fragment, System.nanoTime());
        if (tag == null) return;
        RoadIndex.Built built = null;
        if (tag.getString("Type").equals("road")) {
          built = new RoadIndex.Built(RoadRecord.load(tag.getCompound("Road")), true);
          built.prepareCollision();
        }
        result = new Event(tag, built, null);
      } catch (IllegalArgumentException e) {
        assembler.clear(); result = new Event(null, null, "道路同步失败：" + e.getMessage());
      }
      try {
        while (generation == epoch)
          if (ready.offer(new Ready(generation, result), 25, TimeUnit.MILLISECONDS)) break;
      } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
    });
  }

  /** A small time budget keeps a burst of city data from occupying one entire frame. */
  public int drain(Consumer<Event> sink) {
    long deadline = System.nanoTime() + 2_000_000;
    int count = 0;
    while (count < 8 && System.nanoTime() < deadline) {
      Ready next = ready.poll(); if (next == null) break;
      if (next.epoch == epoch) { sink.accept(next.event); count++; }
    }
    return count;
  }
}
