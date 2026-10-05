package com.sora.splineroads.net;

import com.sora.splineroads.world.RoadRecord;
import net.minecraft.nbt.CompoundTag;
import java.util.*;
import java.util.concurrent.*;

/** Immutable records only: no world, chunk or player access on this worker. */
public final class RoadPacketCache {
  public static final int MAX_JOBS=4,MAX_ENTRIES=128,MAX_BYTES=32*1024*1024;
  private final ExecutorService worker=Executors.newSingleThreadExecutor(r->{var t=new Thread(r,"SR road encoder");t.setDaemon(true);return t;});
  private record Key(String dimension,UUID id){}
  private static final class Entry {
    final RoadRecord record;final CompletableFuture<List<RoadWire.Fragment>> future;int bytes;
    Entry(RoadRecord record,CompletableFuture<List<RoadWire.Fragment>> future){this.record=record;this.future=future;}
  }
  private final LinkedHashMap<Key,Entry> entries=new LinkedHashMap<>(16,.75f,true);
  private int retainedBytes;
  private final java.util.concurrent.atomic.AtomicInteger outstanding=new java.util.concurrent.atomic.AtomicInteger();
  /** Called on the server thread. Null means backpressure; retry next tick. */
  public CompletableFuture<List<RoadWire.Fragment>> request(String dimension,RoadRecord record){
    trim();var key=new Key(dimension,record.id());var found=entries.get(key);
    if(found!=null&&found.record==record)return found.future;
    if(outstanding.get()>=MAX_JOBS)return null;
    if(found!=null){retainedBytes-=found.bytes;entries.remove(key);}
    outstanding.incrementAndGet();
    var future=CompletableFuture.supplyAsync(()->{
      var tag=new CompoundTag();tag.putString("Type","road");tag.putString("Dimension",dimension);tag.put("Road",record.save());
      return List.copyOf(RoadWire.split(tag));
    },worker).whenComplete((v,e)->outstanding.decrementAndGet());
    entries.put(key,new Entry(record,future));return future;
  }
  private void trim(){
    for(var entry:entries.values())if(entry.bytes==0&&entry.future.isDone()&&!entry.future.isCompletedExceptionally()){
      entry.bytes=entry.future.join().stream().mapToInt(f->f.bytes().length).sum();retainedBytes+=entry.bytes;
    }
    for(var it=entries.entrySet().iterator();it.hasNext()&&(retainedBytes>MAX_BYTES||entries.size()>=MAX_ENTRIES);){
      var entry=it.next().getValue();if(!entry.future.isDone())continue;
      retainedBytes-=entry.bytes;it.remove();
    }
  }
  public int retainedBytes(){trim();return retainedBytes;}
  public int jobs(){return outstanding.get();}
  public void clear(){entries.clear();retainedBytes=0;}
}
