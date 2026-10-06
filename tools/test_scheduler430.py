#!/usr/bin/env python3
"""Compile the production scheduler against explicit server/transport adapters.
This checks thread routing/cancellation, NOT an actual Minecraft connection or FPS.
"""
from pathlib import Path
import subprocess, sys
root=Path(__file__).resolve().parents[1]
out=root/'build/scheduler430'; src=out/'src'; classes=out/'classes'
files={
'net/minecraft/nbt/CompoundTag.java':'''package net.minecraft.nbt;import java.util.*;public class CompoundTag {private final Map<String,Object> m=new HashMap<>();public long getLong(String k){return (long)m.getOrDefault(k,0L);}public void putLong(String k,long v){m.put(k,v);}public void putString(String k,String v){m.put(k,v);}public String getString(String k){return (String)m.getOrDefault(k,"");}public CompoundTag copy(){var t=new CompoundTag();t.m.putAll(m);return t;}}''',
'net/minecraft/world/item/ItemStack.java':'''package net.minecraft.world.item;public class ItemStack {}''',
'net/minecraft/server/level/ServerPlayer.java':'''package net.minecraft.server.level;import java.util.*;import java.util.concurrent.*;import net.minecraft.world.item.ItemStack;public class ServerPlayer {public final UUID id=UUID.randomUUID();public final Server server;public ItemStack tool=new ItemStack();public String dim="overworld";public ServerPlayer(Server s){server=s;s.players.put(id,this);}public UUID getUUID(){return id;}public Server getServer(){return server;}public ServerPlayer level(){return this;}public Dim dimension(){return new Dim(dim);}public record Dim(String location){}public ItemStack getMainHandItem(){return tool;}public ItemStack getOffhandItem(){return null;}public static class Server {public final Thread main=Thread.currentThread();public final Map<UUID,ServerPlayer> players=new HashMap<>();public final Queue<Runnable> callbacks=new ConcurrentLinkedQueue<>();public void execute(Runnable r){callbacks.add(r);}public Server getPlayerList(){return this;}public ServerPlayer getPlayer(UUID id){return players.get(id);}public void drain(){if(Thread.currentThread()!=main)throw new AssertionError("not main");for(Runnable r;(r=callbacks.poll())!=null;)r.run();}}}''',
'com/sora/splineroads/world/LaneRamps.java':'''package com.sora.splineroads.world;import java.util.concurrent.*;import java.util.function.*;import net.minecraft.nbt.*;import net.minecraft.world.item.*;import net.minecraft.server.level.*;public class LaneRamps {public static final ConcurrentHashMap<Long,Supplier<PreviewRoute>> RUN=new ConcurrentHashMap<>();public record PreviewRoute(){}public record PreviewWork(Supplier<PreviewRoute> body,Thread main){public PreviewRoute compute(){if(Thread.currentThread()==main)throw new AssertionError("search ran on main");return body.get();}}public static PreviewWork preparePreview(ServerPlayer p,ItemStack tool,CompoundTag t){if(Thread.currentThread()!=p.server.main)throw new AssertionError("snapshot off main");return new PreviewWork(RUN.get(t.getLong("Request")),p.server.main);}public static CompoundTag finishPreview(ServerPlayer p,ItemStack tool,CompoundTag t,PreviewWork w,PreviewRoute route){if(Thread.currentThread()!=p.server.main)throw new AssertionError("publish off main");if(route==null)throw new AssertionError("missing route");return t.copy();}}''',
'com/sora/splineroads/net/RoadNetwork.java':'''package com.sora.splineroads.net;import java.util.*;import net.minecraft.nbt.*;import net.minecraft.server.level.*;public class RoadNetwork {public static final List<CompoundTag> REPLIES=new ArrayList<>();public static void open(ServerPlayer p,CompoundTag t){if(Thread.currentThread()!=p.server.main)throw new AssertionError("network off main");REPLIES.add(t);}}''',
'com/sora/splineroads/net/Scheduler430Validation.java':'''package com.sora.splineroads.net;
import com.sora.splineroads.core.RoadPlanningBudget;import com.sora.splineroads.world.LaneRamps;import net.minecraft.nbt.CompoundTag;import net.minecraft.server.level.ServerPlayer;import java.util.*;import java.util.concurrent.atomic.*;import java.util.function.*;
public class Scheduler430Validation {
 static int checks,heartbeats;static final ServerPlayer.Server server=new ServerPlayer.Server();
 static void check(boolean ok,String s){checks++;if(!ok)throw new AssertionError(s);}
 static void until(BooleanSupplier done,long ms)throws Exception{long end=System.nanoTime()+ms*1000000;while(!done.getAsBoolean()){server.drain();heartbeats++;if(System.nanoTime()>end)throw new AssertionError("test deadline");Thread.sleep(1);}server.drain();}
 static void begin(ServerPlayer p,long request,Supplier<LaneRamps.PreviewRoute> work){LaneRamps.RUN.put(request,work);var t=new CompoundTag();t.putLong("Request",request);RoadPlanningJobs.begin(p,p.tool,t);}
 static LaneRamps.PreviewRoute waitFor(AtomicBoolean release,AtomicBoolean started){started.set(true);while(!release.get()){RoadPlanningBudget.check();java.util.concurrent.locks.LockSupport.parkNanos(1000000);}return new LaneRamps.PreviewRoute();}
 static Optional<CompoundTag> reply(long request){return RoadNetwork.REPLIES.stream().filter(t->t.getLong("Request")==request).findFirst();}
 public static void main(String[]args)throws Exception{
  var p=new ServerPlayer(server);var release=new AtomicBoolean();var started=new AtomicBoolean();long start=System.nanoTime();begin(p,1,()->waitFor(release,started));
  double submit=(System.nanoTime()-start)/1e6;check(submit<500,"submission blocked on route");until(started::get,2000);int first=heartbeats;
  long heartbeatUntil=System.nanoTime()+50000000;until(()->System.nanoTime()>heartbeatUntil,1000);check(heartbeats>first+5,"main loop did not advance while worker busy");
  RoadPlanningJobs.cancel(p.id,1);begin(p,2,LaneRamps.PreviewRoute::new);until(()->reply(2).isPresent(),3000);check(reply(1).isEmpty(),"cancelled/stale job published");check(reply(2).get().getString("Error").isEmpty(),"replacement failed");check(RoadPlanningJobs.activeCount()==0,"completed job retained");
  var staleRelease=new AtomicBoolean();var staleStarted=new AtomicBoolean();begin(p,3,()->waitFor(staleRelease,staleStarted));until(staleStarted::get,2000);p.dim="nether";staleRelease.set(true);until(()->reply(3).isPresent(),3000);check(reply(3).get().getString("Error").contains("维度"),"changed dimension accepted");p.dim="overworld";
  begin(p,4,()->{while(true){RoadPlanningBudget.check();java.util.concurrent.locks.LockSupport.parkNanos(1000000);}});start=System.nanoTime();until(()->reply(4).isPresent(),11500);double timeout=(System.nanoTime()-start)/1e6;
  check(reply(4).get().getString("Error").contains("预算"),"cooperative deadline missing");check(timeout>=7500&&timeout<11000,"deadline timing unexpected");check(RoadPlanningJobs.activeCount()==0,"timed out job retained");
  var people=new ArrayList<ServerPlayer>();var queueRelease=new AtomicBoolean();var queueStarted=new AtomicBoolean();var firstPlayer=new ServerPlayer(server);people.add(firstPlayer);begin(firstPlayer,100,()->waitFor(queueRelease,queueStarted));until(queueStarted::get,2000);
  for(int i=1;i<=8;i++){var q=new ServerPlayer(server);people.add(q);begin(q,100+i,()->waitFor(queueRelease,new AtomicBoolean()));}
  boolean rejected=false;try{begin(new ServerPlayer(server),999,LaneRamps.PreviewRoute::new);}catch(IllegalArgumentException e){rejected=e.getMessage().contains("队列");}check(rejected,"queue unbounded or CallerRuns used");
  for(var q:people)RoadPlanningJobs.cancel(q.id);queueRelease.set(true);check(RoadPlanningJobs.activeCount()==0,"cancelled queued jobs retained");
  begin(p,200,LaneRamps.PreviewRoute::new);until(()->reply(200).isPresent(),3000);check(reply(200).get().getString("Error").isEmpty(),"queue did not recover after cancellation");
  System.out.printf(java.util.Locale.ROOT,"Scheduler430Validation: %d checks PASS; submit_ms=%.3f timeout_ms=%.3f simulated_server_heartbeats=%d. ACTUAL RoadPlanningJobs/RoadPlanningBudget, server/network ADAPTERS, not live Minecraft/FPS.%n",checks,submit,timeout,heartbeats);
 }
}'''
}
for name,text in files.items():
 p=src/name;p.parent.mkdir(parents=True,exist_ok=True);p.write_text(text,encoding='utf-8')
classes.mkdir(parents=True,exist_ok=True)
core=root/(sys.argv[1] if len(sys.argv)>1 else 'build/ramp39-core/classes')
subprocess.run(['javac','--release','17','-encoding','UTF-8','-cp',str(core),'-d',str(classes),*[str(p) for p in src.rglob('*.java')],str(root/'src/main/java/com/sora/splineroads/net/RoadPlanningJobs.java')],check=True)
subprocess.run(['java','-Dfile.encoding=UTF-8','-cp',f'{classes}:{core}','com.sora.splineroads.net.Scheduler430Validation'],check=True,timeout=30)
