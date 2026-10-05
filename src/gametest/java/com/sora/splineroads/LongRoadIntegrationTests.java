package com.sora.splineroads;

import com.sora.splineroads.core.*;
import com.sora.splineroads.core.RoadGeometry.*;
import com.sora.splineroads.net.*;
import com.sora.splineroads.world.*;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.gametest.*;

@GameTestHolder(SplineRoads.ID)
@PrefixGameTestTemplate(false)
public final class LongRoadIntegrationTests {
  private static void place(ServerLevel level, BlockPos p) {
    try (var work = RoadWorkChunks.open(level)) {
      work.node(p);
      level.setBlockAndUpdate(p, SplineRoads.NODE.get().defaultBlockState());
      var n = (NodeEntity) level.getBlockEntity(p);
      n.offsetY = 0;
      n.heightExplicit = true;
      n.setChanged();
    }
  }

  @GameTest(template = "empty", batch = "longRoads", timeoutTicks = 900)
  public static void unloadedLongRoadLifecycle(GameTestHelper h) {
    var level = h.getLevel();
    BlockPos a = new BlockPos(20000, 100, 20000), b = a.offset(0, 0, 1600);
    place(level, a);
    place(level, b);
    var originals = new LinkedHashMap<BlockPos, net.minecraft.world.level.block.state.BlockState>();
    try (var work = RoadWorkChunks.open(level)) {
      for (int z : new int[] {1, 96, 192, 800, 1599}) {
        var p = a.offset(0, -1, z);
        work.node(p);
        originals.put(p, level.getBlockState(p));
      }
    }
    h.assertTrue(RoadWorkChunks.heldCount(level) == 0, "seed node tickets released");
    h.runAtTickTime(
        100,
        () -> {
          h.assertTrue(
              !level.hasChunkAt(a), "first selected node actually unloaded before operation");
          h.assertTrue(!level.hasChunkAt(a.offset(0, 0, 800)), "middle corridor not preloaded");
          var node = RoadData.requireNode(level, a, null);
          h.assertTrue(node.getBlockPos().equals(a), "saved remote block entity recovered");
          var data = RoadData.get(level);
          var profile =
              new Settings(Mode.STRAIGHT, Style.H4_RAIL, 28, 1, .4, 90).structure(Structure.GROUND);
          var player =
              net.minecraftforge.common.util.FakePlayerFactory.get(
                  level, new com.mojang.authlib.GameProfile(UUID.randomUUID(), "LongRoadTest"));
          player.setGameMode(net.minecraft.world.level.GameType.CREATIVE);
          player.moveTo(b.getX() + .5, b.getY() + 3, b.getZ() + .5);
          var stack = new net.minecraft.world.item.ItemStack(SplineRoads.CONNECTOR.get());
          player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, stack);
          stack.getOrCreateTag().putLong("Start", a.asLong());
          stack.getOrCreateTag().putString("Dimension", level.dimension().location().toString());
          var command = new CompoundTag();
          command.putString("Action", "connect");
          command.putLong("A", a.asLong());
          command.putLong("B", b.asLong());
          command.put("Settings", RoadRecord.writeSettings(profile));
          h.assertTrue(
              RoadNetwork.perform(player, command).contains("已建成"),
              "actual packet command builds 1600-block highway");
          var built = data.index.roads.get(data.index.atNode(a).iterator().next());
          h.assertTrue(built.mesh.length() >= 1600, "long road retained as one editable road");
          for (int z : new int[] {1, 96, 192, 800, 1599})
            h.assertTrue(
                level.getBlockState(a.offset(0, -1, z)).is(SplineRoads.COLLIDER.get()),
                "collision continuous across long road");
          var packed = built.record.save();
          var copy = RoadRecord.load(packed);
          h.assertTrue(
              copy.mesh().length() == built.mesh.length(), "long road survives record save/load");
          var envelope = new CompoundTag();
          envelope.put("Road", packed);
          var assembler = new RoadWire.Assembler();
          CompoundTag roundtrip = null;
          for (var fragment : RoadWire.split(envelope)) {
            var decoded = assembler.accept(fragment, 0);
            if (decoded != null) roundtrip = decoded;
          }
          h.assertTrue(
              roundtrip != null && roundtrip.equals(envelope),
              "long road survives compressed network transport");
          h.assertTrue(
              RoadWorkChunks.heldCount(level) == 0, "build releases temporary chunk tickets");
          var id = built.record.id();
          h.runAfterDelay(
              100,
              () -> {
                h.assertTrue(
                    !level.hasChunkAt(a),
                    "long road does not force its endpoint chunk to remain loaded");
                data.connect(level, player, a, b, profile, id);
                h.assertTrue(RoadWorkChunks.heldCount(level) == 0, "edit releases chunk tickets");
                data.remove(level, player, id);
                h.assertTrue(!data.index.roads.containsKey(id), "long road deleted as one road");
                for (var original : originals.entrySet())
                  h.assertTrue(
                      level.getBlockState(original.getKey()).equals(original.getValue()),
                      "delete restores actual corridor terrain at " + original.getKey());
                h.assertTrue(
                    RoadData.requireNode(level, a, player) != null
                        && RoadData.requireNode(level, b, player) != null,
                    "endpoints survive deletion");
                h.assertTrue(RoadWorkChunks.heldCount(level) == 0, "delete releases chunk tickets");
                RoadNetwork.forget(player.getUUID());
                h.succeed();
              });
        });
  }

  @GameTest(template = "empty", batch = "remoteNodes", timeoutTicks = 500)
  public static void remoteThreeAndFourAnchorPayloads(GameTestHelper h) {
    var level = h.getLevel();
    BlockPos a = new BlockPos(25000, 100, 25000),
        b = a.offset(1200, 0, 0),
        c = a.offset(600, 21, -600),
        d = a.offset(600, 21, 600);
    for (var p : List.of(a, b, c, d)) place(level, p);
    h.runAtTickTime(
        100,
        () -> {
          h.assertTrue(
              !level.hasChunkAt(a) && !level.hasChunkAt(c),
              "remote interchange anchors are unloaded");
          long[] three = {a.asLong(), b.asLong(), c.asLong()},
              four = {a.asLong(), b.asLong(), c.asLong(), d.asLong()};
          var t = Interchanges.payload(level, null, three, null);
          h.assertTrue(
              t.getList("Nodes", Tag.TAG_COMPOUND).size() == 3,
              "three-point preview recovers remote anchors");
          h.assertTrue(Interchanges.plan(t).movements() == 4, "remote three-way preview generates");
          var q = Interchanges.payload(level, null, four, null);
          h.assertTrue(
              q.getList("Nodes", Tag.TAG_COMPOUND).size() == 4,
              "four-point preview recovers all anchors");
          // A deleted node still fails, and a foreign owner still cannot be edited.
          try (var work = RoadWorkChunks.open(level)) {
            work.node(d);
            level.removeBlock(d, false);
            var n = RoadData.requireNode(level, a, null);
            n.owner = UUID.randomUUID();
            n.setChanged();
          }
          boolean missing = false;
          try {
            Interchanges.payload(level, null, four, null);
          } catch (IllegalArgumentException e) {
            missing = e.getMessage().contains("端点不存在");
          }
          h.assertTrue(missing, "deleted remote anchor is never recreated");
          var player =
              net.minecraftforge.common.util.FakePlayerFactory.get(
                  level, new com.mojang.authlib.GameProfile(UUID.randomUUID(), "ForeignRoadTest"));
          player.setGameMode(net.minecraft.world.level.GameType.CREATIVE);
          boolean denied = false;
          try {
            RoadData.requireNode(level, a, player);
          } catch (IllegalArgumentException e) {
            denied = e.getMessage().contains("自己");
          }
          h.assertTrue(denied, "remote node retains ownership enforcement");
          h.assertTrue(
              RoadWorkChunks.heldCount(level) == 0,
              "failed preview and ownership checks release tickets");
          h.succeed();
        });
  }

  @GameTest(template = "empty", batch = "longRollback", timeoutTicks = 500)
  public static void longRoadFailureIsAtomic(GameTestHelper h) {
    var level = h.getLevel();
    var data = RoadData.get(level);
    BlockPos a = new BlockPos(28000, 100, 28000),
        b = a.offset(0, 0, 600),
        obstacle = a.offset(0, 0, 300),
        early = a.offset(0, -1, 100);
    place(level, a);
    place(level, b);
    try (var work = RoadWorkChunks.open(level)) {
      work.node(obstacle);
      work.node(early);
      level.setBlockAndUpdate(obstacle, Blocks.BEDROCK.defaultBlockState());
    }
    // Randomly generated terrain can occupy this cell; rollback must preserve its real state.
    var earlyBefore = level.getBlockState(early);
    var before = new HashSet<>(data.index.roads.keySet());
    var profile =
        new Settings(Mode.STRAIGHT, Style.TWO_LANE, 9, 1, .4, 90).structure(Structure.GROUND);
    boolean denied = false;
    try {
      data.connect(level, null, a, b, profile, null);
    } catch (IllegalArgumentException e) {
      denied = e.getMessage().contains("不可破坏");
    }
    h.assertTrue(denied, "protected block in distant corridor rejects long build");
    h.assertTrue(
        before.equals(data.index.roads.keySet()), "failed long build preserves road graph");
    h.assertTrue(
        level.getBlockState(early).equals(earlyBefore),
        "failed long build leaves early corridor untouched");
    h.assertTrue(level.getBlockState(obstacle).is(Blocks.BEDROCK), "obstacle unchanged");
    h.assertTrue(RoadWorkChunks.heldCount(level) == 0, "failed long build releases tickets");
    h.succeed();
  }
}
