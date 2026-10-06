package com.sora.splineroads.net;
import com.sora.splineroads.world.*;
import com.sora.splineroads.core.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
/** Server-thread snapshot and publish, pure geometry between them. No blocking join/get. */
public final class RoadPlanningJobs {
  private static final ThreadPoolExecutor WORKER=new ThreadPoolExecutor(1,1,30,TimeUnit.SECONDS,
      new ArrayBlockingQueue<>(8),r->{Thread t=new Thread(r,"SR-route-planner");t.setDaemon(true);t.setPriority(Thread.NORM_PRIORITY-1);return t;},new ThreadPoolExecutor.AbortPolicy());
  private static final ScheduledExecutorService WATCH=Executors.newSingleThreadScheduledExecutor(r->{Thread t=new Thread(r,"SR-route-deadlines");t.setDaemon(true);return t;});
  private static final Map<UUID,Job> ACTIVE=new HashMap<>(); // accessed on server thread only
  static {WORKER.allowCoreThreadTimeOut(true);}
  private static final class Job {
    final long request;final AtomicBoolean cancelled=new AtomicBoolean();FutureTask<Void> task;ScheduledFuture<?> deadline;
    Job(long request){this.request=request;}
    void stop(){cancelled.set(true);if(task!=null)task.cancel(true);if(deadline!=null)deadline.cancel(false);WORKER.purge();}
  }
  public static void cancel(UUID player){var old=ACTIVE.remove(player);if(old!=null)old.stop();}
  public static void cancel(UUID player,long request){var old=ACTIVE.get(player);if(old!=null&&old.request==request)cancel(player);}
  public static int activeCount(){return ACTIVE.size();}
  public static void begin(ServerPlayer player,ItemStack tool,CompoundTag command){
    var work=LaneRamps.preparePreview(player,tool,command);var server=player.getServer();
    if(server==null)throw new IllegalArgumentException("服务器已关闭");
    UUID owner=player.getUUID();var dimension=player.level().dimension();var t=command.copy();cancel(owner);
    var job=new Job(t.getLong("Request"));ACTIVE.put(owner,job);
    job.task=new FutureTask<>(()->{
      LaneRamps.PreviewRoute route=null;RuntimeException error=null;
      try(var budget=RoadPlanningBudget.open("匝道路线/净空搜索",8,job.cancelled::get)){route=work.compute();}
      catch(RuntimeException e){error=e;}
      finally{RoadQueries.clearThreadCache();}
      var result=route;var failure=error;
      server.execute(()->{
        if(ACTIVE.get(owner)!=job||job.cancelled.get())return;
        ACTIVE.remove(owner);if(job.deadline!=null)job.deadline.cancel(false);
        if(server.getPlayerList().getPlayer(owner)!=player)return;
        try {
          if(!player.level().dimension().equals(dimension)||player.getMainHandItem()!=tool&&player.getOffhandItem()!=tool)
            throw new IllegalArgumentException("维度或所持工具已改变，旧计算已丢弃");
          if(failure!=null)throw failure;
          try(var budget=RoadPlanningBudget.open("预览事务校验",4)){
            RoadNetwork.open(player,LaneRamps.finishPreview(player,tool,t,work,result));
          }
        }catch(RuntimeException e){failed(player,t,e);}
      });return null;
    });
    try{
      WORKER.execute(job.task);
      job.deadline=WATCH.schedule(()->{
        job.cancelled.set(true);job.task.cancel(true);WORKER.purge();
        server.execute(()->{if(ACTIVE.get(owner)!=job)return;ACTIVE.remove(owner);
          if(server.getPlayerList().getPlayer(owner)==player)failed(player,t,new RoadPlanningBudget.Aborted("匝道计算等待超过 12 秒，已取消且未写入道路；请重试，或调整路线/落点。"));});
      },12,TimeUnit.SECONDS);
    }catch(RejectedExecutionException e){ACTIVE.remove(owner);job.stop();throw new IllegalArgumentException("匝道规划队列已满，请稍后重试（未写入道路）");}
  }
  private static void failed(ServerPlayer p,CompoundTag t,RuntimeException e){
    System.getLogger("SplineRoads/planner").log(e instanceof RoadPlanningBudget.Aborted?System.Logger.Level.WARNING:System.Logger.Level.INFO,"Ramp preview: "+e.getMessage());
    if(!(e instanceof IllegalArgumentException)&&!(e instanceof RoadPlanningBudget.Aborted))System.getLogger("SplineRoads/planner").log(System.Logger.Level.ERROR,"Preview error",e);
    var reply=new CompoundTag();reply.putString("Kind","laneRampCheck");reply.putLong("Request",t.getLong("Request"));
    reply.putString("Dimension",p.level().dimension().location().toString());reply.putString("Error",e.getMessage()==null?"匝道预览失败，详见 latest.log":e.getMessage());RoadNetwork.open(p,reply);
  }
  private RoadPlanningJobs(){}
}
