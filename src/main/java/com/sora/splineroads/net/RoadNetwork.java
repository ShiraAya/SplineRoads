package com.sora.splineroads.net;

import com.sora.splineroads.SplineRoads;
import com.sora.splineroads.client.ClientRoads;
import com.sora.splineroads.world.*;
import java.util.*;
import java.util.function.Supplier;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.*;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.*;
import net.minecraftforge.network.simple.SimpleChannel;

public final class RoadNetwork {
  private static final String PROTOCOL = "59";
  public static final SimpleChannel CHANNEL =
      NetworkRegistry.newSimpleChannel(
          ResourceLocation.fromNamespaceAndPath(SplineRoads.ID, "roads"),
          () -> PROTOCOL,
          PROTOCOL::equals,
          PROTOCOL::equals);

  public record ServerMessage(RoadWire.Fragment fragment) {
    public void encode(FriendlyByteBuf buf) {
      buf.writeUUID(fragment.id());
      buf.writeVarInt(fragment.index());
      buf.writeVarInt(fragment.count());
      buf.writeByteArray(fragment.bytes());
    }

    public static ServerMessage decode(FriendlyByteBuf buf) {
      return new ServerMessage(
          new RoadWire.Fragment(
              buf.readUUID(),
              buf.readVarInt(),
              buf.readVarInt(),
              buf.readByteArray(RoadWire.PART_BYTES)));
    }

    public static void handle(ServerMessage msg, Supplier<NetworkEvent.Context> supplier) {
      var ctx = supplier.get();
      ctx.enqueueWork(
          () ->
              DistExecutor.unsafeRunWhenOn(
                  Dist.CLIENT, () -> () -> ClientRoads.fragment(msg.fragment)));
      ctx.setPacketHandled(true);
    }
  }

  public record Action(CompoundTag tag) {
    public void encode(FriendlyByteBuf buf) {
      CompoundTag command=tag.copy();
      command.remove("JunctionGraph"); // Preview context is rebuilt from authoritative roads.
      buf.writeNbt(command);
    }

    public static Action decode(FriendlyByteBuf buf) {
      if (buf.readableBytes() > 65536) throw new IllegalArgumentException("道路操作消息过大");
      CompoundTag t = buf.readNbt();
      return new Action(t == null ? new CompoundTag() : t);
    }

    public static void handle(Action msg, Supplier<NetworkEvent.Context> supplier) {
      var ctx = supplier.get();
      ctx.enqueueWork(
          () -> {
            if (ctx.getSender() != null) act(ctx.getSender(), msg.tag);
          });
      ctx.setPacketHandled(true);
    }
  }

  public static void register() {
    CHANNEL.registerMessage(
        0,
        ServerMessage.class,
        ServerMessage::encode,
        ServerMessage::decode,
        ServerMessage::handle,
        Optional.of(NetworkDirection.PLAY_TO_CLIENT));
    CHANNEL.registerMessage(
        1,
        Action.class,
        Action::encode,
        Action::decode,
        Action::handle,
        Optional.of(NetworkDirection.PLAY_TO_SERVER));
  }

  public static void send(ServerPlayer player, CompoundTag t) {
    for (var fragment : RoadWire.split(t))
      CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), new ServerMessage(fragment));
  }

  public static void open(ServerPlayer p, CompoundTag payload) {
    CompoundTag t = new CompoundTag();
    t.putString("Type", "open");
    t.put("Payload", payload);
    send(p, t);
  }

  private static final class Watching {
    final String dimension;
    final Set<Long> chunks = new HashSet<>();
    final Map<UUID, RoadRecord> sent = new HashMap<>();
    final Map<UUID, Transfer> pending = new LinkedHashMap<>();

    Watching(String dimension) {
      this.dimension = dimension;
    }
  }

  private static final class Transfer {
    final RoadRecord record;List<RoadWire.Fragment> parts;int next;
    Transfer(RoadRecord record){this.record=record;}
  }
  private static final RoadPacketCache PACKETS=new RoadPacketCache();
  private static final Map<UUID, Watching> WATCHING = new HashMap<>();

  private static Watching watching(ServerPlayer player) {
    String dimension = player.level().dimension().location().toString();
    Watching state = WATCHING.get(player.getUUID());
    if (state == null || !state.dimension.equals(dimension)) {
      state = new Watching(dimension);
      WATCHING.put(player.getUUID(), state);
    }
    return state;
  }

  public static void dimensionChanged(ServerPlayer player) {
    watching(player);
    LAST_ACTION.remove(player.getUUID());
    LAST_PREVIEW.remove(player.getUUID());
  }

  public static void watch(ServerPlayer player, long chunk, boolean added) {
    var state = watching(player);
    if (added) state.chunks.add(chunk);
    else {
      state.chunks.remove(chunk);
      var index = RoadData.get(player.serverLevel()).index;
      for(UUID id:index.inChunk(chunk)) {
        var built=index.roads.get(id);
        if(built==null||Collections.disjoint(built.chunks,state.chunks)){state.sent.remove(id);state.pending.remove(id);}
      }
    }
  }

  public static void road(ServerPlayer p, RoadRecord r) {
    var state=watching(p);
    if(r==state.sent.get(r.id()))return;
    var pending=state.pending.get(r.id());
    if(pending==null||pending.record!=r)state.pending.put(r.id(),new Transfer(r));
  }

  /** Bounded publication. Superseded/deleted roads and departed dimensions cannot reappear. */
  public static void tick(net.minecraft.server.MinecraftServer server){
    long deadline=System.nanoTime()+1_500_000;
    for(var p:server.getPlayerList().getPlayers()){
      var state=watching(p);int bytes=0,examined=0;
      var index=RoadData.get(p.serverLevel()).index;
      for(var it=state.pending.entrySet().iterator();it.hasNext()&&bytes<262144&&examined++<32;){
        if(System.nanoTime()>=deadline)return;
        var item=it.next();var t=item.getValue();var current=index.roads.get(item.getKey());
        if(current==null||current.record!=t.record||Collections.disjoint(current.chunks,state.chunks)){it.remove();continue;}
        if(t.parts==null){
          var encoded=PACKETS.request(state.dimension,t.record);
          if(encoded==null||!encoded.isDone())continue;
          try{t.parts=encoded.join();}catch(java.util.concurrent.CompletionException e){
            it.remove();p.displayClientMessage(Component.literal("道路同步失败："+e.getCause().getMessage()),false);continue;
          }
        }
        while(t.next<t.parts.size()&&bytes<262144&&System.nanoTime()<deadline){
          var fragment=t.parts.get(t.next++);bytes+=fragment.bytes().length;
          CHANNEL.send(PacketDistributor.PLAYER.with(()->p),new ServerMessage(fragment));
        }
        if(t.next==t.parts.size()){state.sent.put(item.getKey(),t.record);it.remove();}
      }
    }
  }
  public static void stop(){RoadRecord.clearMeshCaches();com.sora.splineroads.core.RoadClearance.clearPreparedCache();WATCHING.clear();PACKETS.clear();LAST_ACTION.clear();LAST_PREVIEW.clear();}

  public static void broadcastRoad(ServerLevel level, RoadRecord r) {
    var built = RoadData.get(level).index.roads.get(r.id());
    if (built == null) return;
    for (ServerPlayer player : level.players())
      if (!Collections.disjoint(built.chunks, watching(player).chunks)) road(player, r);
  }

  /** Ordered, small point delta; a client still loading the host gets a full latest snapshot. */
  public static void broadcastLanePoints(ServerLevel level, RoadRecord before, RoadRecord after) {
    var built = RoadData.get(level).index.roads.get(after.id());
    for (var player : level.players()) {
      var state = watching(player);
      if (Collections.disjoint(built.chunks, state.chunks)) continue;
      if (state.sent.get(before.id()) == before && !state.pending.containsKey(before.id())) {
        var t = new CompoundTag();
        t.putString("Type", "lanePoints");t.putString("Dimension", state.dimension);
        t.putUUID("Id", before.id());t.putInt("Signature", before.header().hashCode());
        t.put("Points", LanePointCodec.write(LaneTopology.metadata(after)));
        send(player, t);state.sent.put(after.id(), after);
      } else road(player, after);
    }
  }

  public static void broadcastDelete(ServerLevel level, UUID id) {
    CompoundTag t = new CompoundTag();
    t.putString("Type", "delete");
    t.putUUID("Id", id);
    t.putString("Dimension", level.dimension().location().toString());
    for (ServerPlayer p : level.players()) {
      watching(p).sent.remove(id);
      watching(p).pending.remove(id);
      send(p, t);
    }
  }

  public static void result(ServerPlayer p, boolean success, String message) {
    CompoundTag t = new CompoundTag();
    t.putString("Type", "result");
    t.putBoolean("Success", success);
    t.putString("Message", message);
    send(p, t);
    p.displayClientMessage(Component.literal(message), false);
  }

  private static final Map<UUID, Long> LAST_ACTION = new HashMap<>();
  private static final Map<UUID, Long> LAST_PREVIEW = new HashMap<>();

  public static void forget(UUID id) {
    LAST_ACTION.remove(id);
    LAST_PREVIEW.remove(id);
    WATCHING.remove(id);
  }

  private static void act(ServerPlayer player, CompoundTag t) {
    if(t.getString("Action").equals("lanePointReady")) {
      if(t.hasUUID("Interaction")) LanePointTool.acknowledge(player,t.getUUID("Interaction"));
      return;
    }
    if(t.getString("Action").equals("lanePointResync")) {
      var b=RoadData.get(player.serverLevel()).index.roads.get(t.getUUID("Id"));
      var state=watching(player);
      if(b!=null&&!Collections.disjoint(b.chunks,state.chunks)){state.sent.remove(b.record.id());road(player,b.record);}
      return;
    }
    if(t.getString("Action").equals("interchangePreview")||t.getString("Action").equals("laneRampPreview")){
      try{perform(player,t);}catch(IllegalArgumentException e){
        CompoundTag reply=new CompoundTag();reply.putString("Kind",t.getString("Action").equals("laneRampPreview")?"laneRampCheck":"corridorCheck");reply.putLong("Request",t.getLong("Request"));reply.putString("Dimension",player.level().dimension().location().toString());reply.putString("Error",e.getMessage()==null?"预览参数无效":e.getMessage());open(player,reply);
      }return;
    }
    try {
      result(player, true, perform(player, t));
    } catch (IllegalArgumentException e) {
      result(player, false, e.getMessage() == null ? "道路参数无效" : e.getMessage());
    }
  }

  /** Same authoritative command path used by packets and server integration tests. */
  public static String perform(ServerPlayer player, CompoundTag t) {
    long now = player.serverLevel().getGameTime();
    boolean preview=t.getString("Action").equals("interchangePreview")||t.getString("Action").equals("laneRampPreview");
    var throttle=preview?LAST_PREVIEW:LAST_ACTION;
    Long last = throttle.get(player.getUUID());
    if (last != null && now - last < 5) throw new IllegalArgumentException("操作过快，请稍候重试");
    throttle.put(player.getUUID(), now);
    String action = t.getString("Action");
    ItemStack tool =
        usable(player.getMainHandItem(), action)
            ? player.getMainHandItem()
            : player.getOffhandItem();
    if (!(tool.getItem() instanceof RoadTool
            || (action.equals("gantry") && tool.getItem() instanceof GantryTool)
            || (action.equals("laneLines") && tool.getItem() instanceof LaneLineTool)
            || (action.equals("attachedPoint") && tool.getItem() instanceof AttachedPointTool)
            || (action.equals("lanePoint") && tool.getItem() instanceof LanePointTool)
            || (action.startsWith("laneRamp") && tool.getItem() instanceof LaneRampTool)
            || (action.equals("yJunction") && (tool.getItem() instanceof YJunctionTool || tool.getItem() instanceof JunctionTool))
            || (action.startsWith("junction") && usable(tool,action))
            || (action.startsWith("roundabout") && tool.getItem() instanceof RoundaboutTool)
            || (action.startsWith("interchange")
                && tool.getItem() instanceof InterchangeTool generator
                && generator.available())
            || (action.equals("delete") && tool.is(SplineRoads.REMOVER.get())))
        || !player.mayBuild()
        || player.isSpectator()) throw new IllegalArgumentException("请手持道路连接器并确保拥有建设权限");
    ServerLevel level = player.serverLevel();
    RoadData data = RoadData.get(level);
    switch (action) {
      case "lanePoint" -> {if(!(tool.getItem() instanceof LanePointTool))throw new IllegalArgumentException("请手持车道点工具");return LanePointTool.edit(level,player,t);}
      case "laneRampRoad" -> {if(!(tool.getItem() instanceof RoadTool))throw new IllegalArgumentException("请手持道路连接器");return LaneRamps.editRoad(level,player,t);}
      case "laneRampPreview" -> {if(!(tool.getItem() instanceof LaneRampTool))throw new IllegalArgumentException("请手持匝道连接器");open(player,LaneRamps.preview(player,tool,t));return "匝道预览已校验";}
      case "laneRamp" -> {if(!(tool.getItem() instanceof LaneRampTool))throw new IllegalArgumentException("请手持匝道连接器");return LaneRamps.build(player,tool,t);}
      case "attachedPoint" -> {if(!(tool.getItem() instanceof AttachedPointTool))throw new IllegalArgumentException("请手持端点创建器");return AttachedPointTool.edit(level,player,t);}
      case "laneLines" -> {if(!(tool.getItem() instanceof LaneLineTool))throw new IllegalArgumentException("请手持车道线编辑器");data.editLaneLine(level,player,t);return t.contains("HideArrows")?(t.getBoolean("HideArrows")?"所选路段的方向箭头已隐藏":"所选路段的方向箭头已显示"):"所选路段的单根车道线已更新";}
      case "yJunction" -> {return YJunctionTool.build(level,player,t,tool);}

      case "gantry" -> {
        if(!(tool.getItem() instanceof GantryTool))throw new IllegalArgumentException("请手持龙门架编辑器");
        data.editGantry(level,player,t);
        return "所选龙门架已更新";
      }
      case "interchangePreview" -> {
        requireGenerator(tool,t,data);
        if(!t.contains("Corridor"))throw new IllegalArgumentException("预览仅用于主辅路与双层同侧组合");
        if(!t.hasUUID("Id")&&(!Arrays.equals(t.getLongArray("Points"),tool.getOrCreateTag().getLongArray("Points"))
            ||!level.dimension().location().toString().equals(tool.getTag().getString("Dimension"))))throw new IllegalArgumentException("组合选点已失效，请重新选点");
        var reply=new CompoundTag();reply.putString("Kind","corridorCheck");reply.putLong("Request",t.getLong("Request"));reply.putString("Dimension",level.dimension().location().toString());reply.put("Inputs",Corridors.preview(level,player,t));open(player,reply);return "组合预览已检查";
      }
      case "roundabout" -> { return RoundaboutTool.build(level,player,t,tool); }
      case "junctionRoad" -> {if(!tool.is(SplineRoads.CONNECTOR.get()))throw new IllegalArgumentException("请手持道路连接器");return JunctionRoads.build(level,player,t);}
      case "junction" -> {
        if (!(tool.getItem() instanceof RoundaboutTool) && !(tool.getItem() instanceof JunctionTool)) throw new IllegalArgumentException("请手持普通路口编辑器");
        if(!t.hasUUID("Id")&&!(tool.getItem() instanceof JunctionTool))throw new IllegalArgumentException("新建独立路口请使用普通路口编辑器");
        if (!t.hasUUID("Id") && (!Arrays.equals(t.getLongArray("Points"),tool.getOrCreateTag().getLongArray("Points"))
            || !level.dimension().location().toString().equals(tool.getTag().getString("Dimension"))))
          throw new IllegalArgumentException("路口选点已失效，请重新选点");
        String message=Junctions.build(level,player,t);tool.getOrCreateTag().remove("Points");return message;
      }
      case "junctionDelete" -> {LaneDeletes.removeJunction(level,player,t);return "路口已整体删除，外部道路和端点保留";}

      case "interchange" -> {
        requireGenerator(tool, t, data);
        if (!t.hasUUID("Id")
            && (!Arrays.equals(
                    t.getLongArray("Points"), tool.getOrCreateTag().getLongArray("Points"))
                || !level
                    .dimension()
                    .location()
                    .toString()
                    .equals(tool.getTag().getString("Dimension"))))
          throw new IllegalArgumentException("立交端点选择失效，请重新选择");
        String message = Interchanges.build(level, player, t);
        tool.getOrCreateTag().remove("Points");
        return message;
      }
      case "interchangeDelete", "interchangeDeleteRamps" -> {
        requireGenerator(tool, t, data);
        boolean corridor=Interchanges.descriptor(data,t.getUUID("Id")).contains("Corridor");
        if (action.equals("interchangeDeleteRamps")) {
          Interchanges.removeRamps(level, player, t.getUUID("Id"));
          return corridor?"连接匝道已删除；主路、辅路或第二层道路保留，可单独编辑":"匝道及路口设施已删除；主路保留，可用道路连接器单独编辑";
        }
        Interchanges.remove(level, player, t.getUUID("Id"));
        return "整座立交已删除，原端点保留";
      }

      case "connect" -> {
        BlockPos a = BlockPos.of(t.getLong("A")), b = BlockPos.of(t.getLong("B"));
        UUID id = t.hasUUID("Id") ? t.getUUID("Id") : null;
        var settings = RoadRecord.readSettings(t.getCompound("Settings"));
        if (id == null) {
          if (!tool.getOrCreateTag().contains("Start")
              || tool.getTag().getLong("Start") != a.asLong()
              || !level
                  .dimension()
                  .location()
                  .toString()
                  .equals(tool.getTag().getString("Dimension")))
            throw new IllegalArgumentException("起点选择已失效，请重新选择两个端点");
        }
        settings=settings.options(settings.options().sidewalk(settings.options().sidewalk().smooth(true)));
        if (settings.style().ramp()) throw new IllegalArgumentException("道路连接器仅用于普通道路和高速道路");
        RoadRecord r = data.connect(level, player, a, b, settings, id, t.getBoolean("LevelEnds"),AutoJunctions.forced(t,"A"),AutoJunctions.forced(t,"B"));
        tool.getOrCreateTag().put("Settings", RoadRecord.writeSettings(settings));
        tool.getOrCreateTag().putInt("PreferencesVersion", 3);
        BlockPos next=AutoJunctions.center(data,r.a())!=null?r.a():r.b();
        tool.getOrCreateTag().putLong("Start",next.asLong());
        tool.getOrCreateTag().putString("Dimension", level.dimension().location().toString());
        if(AutoJunctions.center(data,next)!=null)return "道路和路口已更新；保留中心端点，可直接选择下一方向的外侧端点";
        return "道路已建成并自动清障；继续右键下一个端点即可续接";
      }
      case "node" -> {
        BlockPos p = BlockPos.of(t.getLong("Pos"));
        data.editNode(level, player, p, RoadRecord.readNode(t.getCompound("Node")));
        return "端点及相连道路已更新";
      }
      case "delete" -> {
        UUID id = t.getUUID("Id");
        LaneDeletes.remove(level, player, t);
        return "道路已删除";
      }
      default -> throw new IllegalArgumentException("未知操作");
    }
  }

  private static void requireGenerator(ItemStack stack, CompoundTag command, RoadData data) {
    if (!(stack.getItem() instanceof InterchangeTool tool) || !tool.available())
      throw new IllegalArgumentException("请手持对应的立交、主辅路或双层出入口生成器");
    tool.requireDescriptor(
        command.hasUUID("Id")
            ? Interchanges.descriptor(data,command.getUUID("Id"))
            : command);
    if(command.getString("Action").equals("interchange")||command.getString("Action").equals("interchangePreview"))tool.requireDescriptor(command);
  }

  private static boolean usable(ItemStack stack, String action) {
    if(action.equals("lanePoint"))return stack.getItem() instanceof LanePointTool;
    if(action.equals("laneRampRoad"))return stack.getItem() instanceof RoadTool;
    if(action.startsWith("laneRamp"))return stack.getItem() instanceof LaneRampTool;
    if(action.equals("attachedPoint"))return stack.getItem() instanceof AttachedPointTool;
    if(action.equals("laneLines"))return stack.getItem() instanceof LaneLineTool;
    if(action.equals("yJunction"))return stack.getItem() instanceof YJunctionTool || stack.getItem() instanceof JunctionTool;
    if(action.equals("gantry"))return stack.getItem() instanceof GantryTool;
    if(action.startsWith("roundabout"))return stack.getItem() instanceof RoundaboutTool;
    if(action.equals("junctionRoad"))return stack.is(SplineRoads.CONNECTOR.get());
    if (action.startsWith("junction")) return stack.getItem() instanceof JunctionTool || stack.getItem() instanceof RoundaboutTool || action.equals("junctionDelete") && stack.is(SplineRoads.REMOVER.get());
    if (action.startsWith("interchange"))
      return stack.getItem() instanceof InterchangeTool generator && generator.available();
    return stack.getItem() instanceof RoadTool
        || (action.equals("delete") && stack.is(SplineRoads.REMOVER.get()));
  }

  private RoadNetwork() {}
}
