package com.sora.splineroads.client;

import com.sora.splineroads.core.*;
import com.sora.splineroads.core.RoadGeometry.*;
import com.sora.splineroads.world.*;
import com.sora.splineroads.net.RoadWire;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;

public final class Loading233Validation {
  static int checks;static volatile long sink;
  static void check(boolean value,String why){checks++;if(!value)throw new AssertionError(why);}
  static RoadRecord road(int i){var a=new BlockPos(i*128,90,0);var b=a.offset(48,8,16);return new RoadRecord(new UUID(0,i+1),new UUID(0,0),a,b,new Node(new V(a.getX()+.5,90,.5),0,0),new Node(new V(b.getX()+.5,98,16.5),0,0),new Settings(Mode.STRAIGHT,Style.O2_YELLOW,9,1,.35,90),false,4);}
  static long load(List<RoadRecord> roads,boolean deferred){long start=System.nanoTime();var index=new RoadIndex();for(var r:roads)index.put(deferred?RoadIndex.Built.loading(r):new RoadIndex.Built(r));sink=index.roads.size();if(deferred)for(var b:index.roads.values())check(!b.rasterized(),"indexing does not trigger deferred full collision");return System.nanoTime()-start;}
  static void send(RoadInbox inbox,CompoundTag t){RoadWire.split(t).forEach(inbox::submit);}
  static CompoundTag event(String type,int sequence){var t=new CompoundTag();t.putString("Type",type);t.putInt("Sequence",sequence);return t;}
  static void drain(RoadInbox inbox,List<RoadInbox.Event> results,int size)throws Exception{long deadline=System.nanoTime()+20_000_000_000L;while(results.size()<size&&System.nanoTime()<deadline){int count=inbox.drain(results::add);check(count<=8,"bounded main-thread publication");Thread.sleep(2);}check(results.size()==size,"ordered asynchronous events all arrive");}
  static RoadRecord street(BlockPos a,BlockPos b,Settings s){return new RoadRecord(UUID.randomUUID(),new UUID(0,0),a,b,new Node(new V(a.getX()+.5,a.getY(),a.getZ()+.5),0,0),new Node(new V(b.getX()+.5,b.getY(),b.getZ()+.5),0,0),s,false,4);}
  public static void main(String[] args)throws Exception{
    var roads=new ArrayList<RoadRecord>();for(int i=0;i<48;i++)roads.add(road(i));
    load(roads.subList(0,4),false);load(roads.subList(0,4),true);
    long old=Long.MAX_VALUE,next=Long.MAX_VALUE;for(int n=0;n<3;n++){old=Math.min(old,load(roads,false));next=Math.min(next,load(roads,true));}
    System.out.printf(Locale.ROOT,"WORLD INDEX LOAD: 48 sloped roads, eager %.2f ms, deferred %.2f ms; excludes disk/NBT and vanilla chunk loading.%n",old/1e6,next/1e6);
    // A cap from a previously free end must not be baked into a newly shared port.
    var c=new BlockPos(0,90,0);var aPos=new BlockPos(140,90,0);var bPos=new BlockPos(0,90,140);var ePos=new BlockPos(280,90,0);
    var narrow=new Settings(Mode.STRAIGHT,Style.O4_YELLOW,19,1,.35,90);var wide=new Settings(Mode.STRAIGHT,Style.O6_RAIL,27,1,.35,90);
    var first=street(c,aPos,narrow).caps(3);var second=street(c,bPos,narrow);
    var config=new CompoundTag();config.putBoolean("Auto",true);config.putBoolean("Forced",true);config.putLong("CenterPos",c.asLong());config.putUUID("Id",UUID.randomUUID());config.putUUID("Owner",new UUID(0,0));
    var junction=AutoJunctions.plan(List.of(first,second),List.of(config));
    var logical=new ArrayList<>(junction.streets());var continuation=street(aPos,ePos,wide);logical.add(continuation);
    var draft=AutoJunctions.plan(logical,junction.centers());
    var stem=draft.roads().stream().filter(r->r.id().equals(first.id())).findFirst().orElseThrow();var join=draft.roads().stream().filter(r->r.id().equals(continuation.id())).findFirst().orElseThrow();
    check(stem.mesh().last().center().distance(join.mesh().first().center())<1e-7,"former free cap removed before alignment baking");
    check(Math.abs(stem.mesh().last().halfWidth()-join.mesh().first().halfWidth())<1e-7,"both junction continuation widths normalized");
    var inbox=new RoadInbox();var results=new ArrayList<RoadInbox.Event>();
    var packet=event("road",0);packet.put("Road",roads.get(0).save());send(inbox,packet);
    for(int i=1;i<100;i++)send(inbox,event(i%2==0?"delete":"result",i));
    drain(inbox,results,100);
    for(int i=0;i<100;i++)check(results.get(i).error()==null&&results.get(i).tag().getInt("Sequence")==i,"road/delete/result order preserved");
    check(results.get(0).built()!=null&&!results.get(0).built().rasterized(),"road prepared off-thread without full collision raster");
    for(int i=0;i<100;i++)send(inbox,packet);inbox.reset();
    results.clear();send(inbox,event("result",777));drain(inbox,results,1);check(results.get(0).tag().getInt("Sequence")==777,"old-world work cannot reappear after reset");
    inbox.reset();
    System.out.printf("LOADING/INBOX PASS: %d checks; background decode, bounded ordered publish, backlog and world-reset cancellation.%n",checks);
  }
}
