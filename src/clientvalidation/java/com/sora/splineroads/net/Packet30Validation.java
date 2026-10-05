package com.sora.splineroads.net;
import com.sora.splineroads.core.*;
import com.sora.splineroads.core.RoadGeometry.*;
import com.sora.splineroads.world.RoadRecord;
import net.minecraft.core.BlockPos;
import java.util.*;
import java.util.concurrent.*;

public final class Packet30Validation {
  static int checks;static void check(boolean v,String m){checks++;if(!v)throw new AssertionError(m);}
  public static void main(String[] args)throws Exception{
    var parts=new ArrayList<RoadStructures.Part>();
    for(int i=0;i<20000;i++){V v=new V(i*.25,10,0);parts.add(new RoadStructures.Part(v,v.add(new V(.25,0,0)),.5,1,false,RoadStructures.Material.CONCRETE));}
    var record=new RoadRecord(UUID.randomUUID(),UUID.randomUUID(),BlockPos.ZERO,new BlockPos(5000,0,0),new Node(new V(0,10,0),-90,0),new Node(new V(5000,10,0),-90,0),Settings.defaults(),false,4,parts);
    var cache=new RoadPacketCache();long start=System.nanoTime();var future=cache.request("minecraft:overworld",record);long request=System.nanoTime()-start;
    var encoded=future.get(30,TimeUnit.SECONDS);double encodingMs=(System.nanoTime()-start)/1e6;check(encoded.size()>1,"large road uses multiple fragments");
    var assembler=new RoadWire.Assembler();net.minecraft.nbt.CompoundTag decoded=null;
    for(var fragment:encoded)decoded=assembler.accept(fragment,System.nanoTime());
    check(decoded!=null&&decoded.getCompound("Road").equals(record.save()),"background encoding preserves exact authoritative record");
    check(cache.request("minecraft:overworld",record)==future,"chunk re-entry reuses encoded immutable data");
    long warm=System.nanoTime();for(int i=0;i<1000;i++)cache.request("minecraft:overworld",record);double hitMs=(System.nanoTime()-warm)/1e6/1000;
    var changed=record.structures(parts.subList(0,100));
    check(cache.request("minecraft:overworld",changed)!=future,"editing same UUID invalidates cache");
    check(cache.request("minecraft:the_nether",record)!=future,"dimensions do not share packet headers");
    for(int i=0;i<100;i++){var f=cache.request("minecraft:overworld",record.structures(parts.subList(0,19000+i)));check(cache.jobs()<=4,"rapid replacements cannot grow the worker queue");}
    check(cache.retainedBytes()<=RoadPacketCache.MAX_BYTES,"retained packet cache bounded");
    cache.clear();check(cache.retainedBytes()==0,"server stop clears retained records");
    System.out.printf(java.util.Locale.ROOT,"Packet30 PASS %d checks; 20,000-part request %.3f ms; cold encode %.1f ms; cached request %.4f ms; %d fragments%n",checks,request/1e6,encodingMs,hitMs,encoded.size());
  }
}
