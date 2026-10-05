package com.sora.splineroads.client;

import com.sora.splineroads.SplineRoads;
import com.sora.splineroads.core.RoadGeometry;
import com.sora.splineroads.world.*;
import java.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

@Mod.EventBusSubscriber(modid = SplineRoads.ID, value = Dist.CLIENT)
public final class ClientRoads {
  public static final RoadIndex INDEX = new RoadIndex(true);
  public static CompoundTag draft;
  private static final RoadInbox inbox = new RoadInbox();

  public static void fragment(com.sora.splineroads.net.RoadWire.Fragment fragment) {
    ensureWorld();
    inbox.submit(fragment);
  }

  public static void storeDraft(CompoundTag value) {
    if (value.getBoolean("DeleteOnly")) return;
    draft = value.copy();

  }

  public static void discardDraft() {

    draft = null;
  }

  public static RoadGeometry.Mesh preview;
  public static List<RoadGeometry.Mesh> nodePreviews = List.of();
  public static String error = "";
  private static String dimension = "";
  private static Object currentLevel;
  private static int ticks;
  private static final ArrayDeque<UUID> prune=new ArrayDeque<>();

  @Mod.EventBusSubscriber(
      modid = SplineRoads.ID,
      value = Dist.CLIENT,
      bus = Mod.EventBusSubscriber.Bus.MOD)
  public static final class Setup {
    @SubscribeEvent
    public static void setup(FMLClientSetupEvent e) {
      e.enqueueWork(
          () -> {
            RoadBlocks.clientIndex = l -> INDEX;
            RoadTool.openClient = ClientRoads::open;
          });
    }
  }

  public static void receive(CompoundTag t) { receive(t, null); }

  private static void receive(CompoundTag t, RoadIndex.Built decoded) {
    Minecraft mc = Minecraft.getInstance();
    String type = t.getString("Type");
    if (type.equals("open")) {
      open(t.getCompound("Payload"));
      return;
    }
    if (type.equals("result")) {
      if (t.getBoolean("Success")) {
        InterchangeScreen.clear();
      LaneRampScreen.clear();
      CorridorScreen.clear();
        JunctionScreen.clear();
        discardDraft();
        preview = null;
        error = "";
        if (mc.screen instanceof LanePointScreen || mc.screen instanceof LaneRampScreen || mc.screen instanceof AttachedPointScreen || mc.screen instanceof JunctionRoadScreen || mc.screen instanceof LaneLineScreen || mc.screen instanceof YJunctionScreen || mc.screen instanceof GantryScreen || mc.screen instanceof RoadScreen
            || mc.screen instanceof RoadDeleteScreen
            || mc.screen instanceof CorridorScreen || mc.screen instanceof InterchangeScreen || mc.screen instanceof JunctionScreen || mc.screen instanceof RoundaboutScreen) mc.setScreen(null);
      } else {
        error = t.getString("Message");
        if (mc.screen instanceof InterchangeScreen screen) screen.failed(error);
        if (mc.screen instanceof CorridorScreen screen) screen.failed(error);
        if (mc.screen instanceof RoadScreen screen) screen.failed(error);
        if (mc.screen instanceof LaneLineScreen screen) screen.failed(error);
        if (mc.screen instanceof AttachedPointScreen screen) screen.failed(error);
        if (mc.screen instanceof LanePointScreen screen) screen.failed(error);
        if (mc.screen instanceof LaneRampScreen screen) screen.failed(error);else LaneRampScreen.failedPending(error);
        if (mc.screen instanceof JunctionRoadScreen screen) screen.failed(error);
        if (mc.screen instanceof YJunctionScreen screen) screen.failed(error);
        if (mc.screen instanceof GantryScreen screen) screen.failed(error);
        if (mc.screen instanceof JunctionScreen screen) screen.failed(error);
        if (mc.screen instanceof RoundaboutScreen screen) screen.failed(error);
        if (mc.screen instanceof RoadDeleteScreen screen) screen.failed(error);
      }
      return;
    }
    if (mc.level == null
        || !mc.level.dimension().location().toString().equals(t.getString("Dimension"))) return;
    ensureWorld();
    try {
      if (type.equals("road")) {
        RoadRecord r = decoded == null ? RoadRecord.load(t.getCompound("Road")) : decoded.record;
        var previous = INDEX.roads.get(r.id());
        if (previous == null || !previous.record.equals(r)) {
          Set<UUID> changed = new HashSet<>();
          changed.add(r.id());
          if (previous != null) INDEX.neighbors(previous).forEach(b -> changed.add(b.record.id()));
          var built = decoded == null ? new RoadIndex.Built(r, true) : decoded;
          INDEX.put(built);
          INDEX.neighbors(built).forEach(b -> changed.add(b.record.id()));
          RoadRenderer.changed(changed);
        }
      } else if (type.equals("lanePoints")) {
        var old=INDEX.roads.get(t.getUUID("Id"));
        if(old!=null&&old.record.header().hashCode()==t.getInt("Signature"))
          INDEX.lanePoints(old.record.id(),LanePointCodec.read(t.getCompound("Points")));
        else {
          var request=new CompoundTag();request.putString("Action","lanePointResync");request.putUUID("Id",t.getUUID("Id"));
          com.sora.splineroads.net.RoadNetwork.CHANNEL.sendToServer(new com.sora.splineroads.net.RoadNetwork.Action(request));
        }
      } else if (type.equals("delete")) {
        remove(t.getUUID("Id"));
        if (draft != null && draft.hasUUID("Id") && draft.getUUID("Id").equals(t.getUUID("Id"))) {
          preview = null;
        }
      }
    } catch (IllegalArgumentException e) {
      error = e.getMessage();
    }
  }

  private static void remove(UUID id) {
    var old = INDEX.roads.get(id);
    Set<UUID> changed = new HashSet<>();
    changed.add(id);
    if (old != null) INDEX.neighbors(old).forEach(b -> changed.add(b.record.id()));
    INDEX.remove(id);
    RoadRenderer.changed(changed);
  }

  private static void ensureWorld() {
    Minecraft mc = Minecraft.getInstance();
    String d = mc.level == null ? "" : mc.level.dimension().location().toString();
    if (currentLevel != mc.level || !d.equals(dimension)) {
      InterchangeScreen.clear();
      LaneRampScreen.clear();
      CorridorScreen.clear();
      JunctionScreen.clear();
      RoadRenderer.reset();
      INDEX.clear();prune.clear();

      inbox.reset();
      discardDraft();
      preview = null;
      error = "";
      dimension = d;
      currentLevel = mc.level;
    }
  }

  public static void open(CompoundTag t) {
    ensureWorld();
    Minecraft mc = Minecraft.getInstance();
    String kind = t.getString("Kind");
    if(kind.equals("laneRampReset")){LaneRampScreen.clear();error="";if(mc.screen instanceof LaneRampScreen)mc.setScreen(null);return;}
    if(kind.equals("lanePoint")){
      mc.setScreen(new LanePointScreen(t));
      if(t.hasUUID("Interaction")){var ack=new CompoundTag();ack.putString("Action","lanePointReady");ack.putUUID("Interaction",t.getUUID("Interaction"));com.sora.splineroads.net.RoadNetwork.CHANNEL.sendToServer(new com.sora.splineroads.net.RoadNetwork.Action(ack));}
      return;
    }
    if(kind.equals("laneRamp")){LaneRampScreen.open(t);return;}
    if(kind.equals("laneRampResume")){LaneRampScreen.resume(t);return;}
    if(kind.equals("laneRampCheck")){LaneRampScreen.checkedReply(t);return;}
    if(kind.equals("armRoad")){Minecraft.getInstance().setScreen(new RoadScreen(t));return;}
    if(kind.equals("attachedPoint")){Minecraft.getInstance().setScreen(new AttachedPointScreen(t));return;}
    if(kind.equals("laneLines")){Minecraft.getInstance().setScreen(new LaneLineScreen(t));return;}
    if(kind.equals("yJunction")){Minecraft.getInstance().setScreen(new YJunctionScreen(t));return;}
    if(kind.equals("gantry")){Minecraft.getInstance().setScreen(new GantryScreen(t));return;}
    if(kind.equals("roundabout")){Minecraft.getInstance().setScreen(new RoundaboutScreen(t));return;}
    if(kind.startsWith("corridor")){CorridorScreen.open(t);return;}
    if (kind.startsWith("junction")) { JunctionScreen.open(t); return; }
    if (kind.startsWith("interchange")) {
      InterchangeScreen.open(t);
      return;
    }
    if (kind.equals("reset")) {


      discardDraft();
      preview = null;
      error = "";
      return;
    }
    if (kind.equals("resume")) {
      CompoundTag saved = draft;
      if (saved != null) {
        draft = saved.copy();
        mc.setScreen(new RoadScreen(draft));
      } else if (mc.player != null)
        mc.player.displayClientMessage(Component.literal("先右键选择两个道路端点"), true);
      return;
    }
    if (t.getBoolean("DeleteOnly")) {
      preview = null;
      error = "";
      mc.setScreen(new RoadDeleteScreen(t));
      return;
    }
    t = com.sora.splineroads.net.RoadDraft.merge(t, draft);
    storeDraft(t);
    preview = null;
    error = "";
    mc.setScreen(new RoadScreen(draft));
  }

  @SubscribeEvent
  public static void logout(ClientPlayerNetworkEvent.LoggingOut e) {
    InterchangeScreen.clear();
      LaneRampScreen.clear();
      CorridorScreen.clear();
    RoadRenderer.reset();
    INDEX.clear();prune.clear();

    inbox.reset();
    discardDraft();
    preview = null;
    error = "";
    dimension = "";
    currentLevel = null;
  }

  @SubscribeEvent
  public static void tick(TickEvent.ClientTickEvent e) {
    if (e.phase != TickEvent.Phase.END) return;
    ensureWorld();
    inbox.drain(event -> {
      if (event.error() != null) error = event.error();
      else receive(event.tag(), event.built());
    });
    var level = Minecraft.getInstance().level;
    if (level == null) return;
    if(++ticks%100==0&&prune.isEmpty())prune.addAll(INDEX.roads.keySet());
    long deadline=System.nanoTime()+500_000;
    for(int n=0;n<4&&!prune.isEmpty()&&System.nanoTime()<deadline;n++){
      UUID id=prune.removeFirst();var r=INDEX.roads.get(id);
      if(r!=null&&r.chunks.stream().noneMatch(c->level.hasChunk((int)(long)c,(int)(c>>32))))remove(id);
    }
  }
}
