package com.sora.splineroads;

import com.sora.splineroads.core.*;
import com.sora.splineroads.core.RoadGeometry.*;
import com.sora.splineroads.world.*;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraftforge.gametest.*;

@GameTestHolder(SplineRoads.ID)
@PrefixGameTestTemplate(false)
public final class JunctionRevisionTests {
  @GameTest(template = "empty", templateNamespace = "splineroads_regression15", batch = "interchangeSpace", timeoutTicks = 600)
  public static void stack350HighGapConstructionAndSave(GameTestHelper h) {
    var level = h.getLevel();
    var data = RoadData.get(level);
    long[] points = {
      node(level, 33650, 90, 34000).asLong(), node(level, 34350, 90, 34000).asLong(),
      node(level, 34000, 115, 33650).asLong(), node(level, 34000, 115, 34350).asLong()
    };
    var payload = Interchanges.payload(level, null, points, null);
    var style = Style.H6_RAIL;
    var settings = new Settings(Mode.STRAIGHT, style, style.defaultWidth(), 1, .4, 90);
    payload.put("Main1", RoadRecord.writeSettings(settings));
    payload.put("Main2", RoadRecord.writeSettings(settings));
    payload.put(
        "Options",
        Interchanges.write(
            new InterchangePlanner.Options(
                InterchangePlanner.Preset.STACK, false, 1, 96, 20, 4, 2)));
    Interchanges.build(level, null, payload);
    long initialPoint = points[0];
    var group =
        data.index.roads.values().stream()
            .filter(r -> r.record.a().asLong() == initialPoint)
            .findFirst()
            .orElseThrow()
            .record
            .assembly();
    var roads =
        data.index.roads.values().stream().filter(r -> group.equals(r.record.assembly())).toList();
    h.assertTrue(roads.size() == 18, "two mains, eight movements and eight shared feeders");
    for (var road : roads) {
      h.assertTrue(!road.cells.isEmpty(), "the 350-block stack has physical road collision");
      var copy = RoadRecord.load(road.record.save()).mesh();
      h.assertTrue(
          copy.samples().size() == road.mesh.samples().size(), "saved sample count matches");
      for (int i = 0; i < copy.samples().size(); i++)
        h.assertTrue(
            copy.samples().get(i).center().distance(road.mesh.samples().get(i).center()) < 1e-10,
            "the continuous height profile survives serialization");
      if (copy.settings().style().ramp())
        h.assertTrue(RoadGrades.maximum(copy) <= .15 + 1e-7, "loaded ramp stays below 15% grade");
    }
    Revision15GameTests.extendAssembly(h, BlockPos.of(points[0]), BlockPos.of(points[0]).offset(-85, 0, 0));
    var clientCollision = new RoadIndex(true);
    roads.forEach(r -> clientCollision.put(new RoadIndex.Built(r.record, true)));
    for (var r : roads)
      for (double fraction : new double[] {.2, .4, .6, .8}) {
        var p = RoadStructures.sample(r.mesh, r.mesh.length() * fraction).center();
        var at = BlockPos.containing(p.x(), p.y() - .1, p.z());
        var expected = data.index.shape(at);
        var actual = clientCollision.shape(at);
        h.assertTrue(
            !net.minecraft.world.phys.shapes.Shapes.joinIsNotEmpty(
                expected, actual, net.minecraft.world.phys.shapes.BooleanOp.NOT_SAME),
            "local client voxel shape matches authoritative collision");
      }
    var remover =
        net.minecraftforge.common.util.FakePlayerFactory.get(
            level, new com.mojang.authlib.GameProfile(new UUID(0, 0), "InterchangeDeleteTest"));
    remover.setGameMode(net.minecraft.world.level.GameType.CREATIVE);
    remover.setItemInHand(
        net.minecraft.world.InteractionHand.MAIN_HAND,
        new net.minecraft.world.item.ItemStack(SplineRoads.REMOVER.get()));
    var selectedRamp =
        roads.stream().filter(r -> r.record.settings().style().ramp()).findFirst().orElseThrow();
    var previewCopy = RoadRecord.load(selectedRamp.record.header()).mesh();
    h.assertTrue(
        previewCopy.samples().equals(selectedRamp.mesh.samples()),
        "delete preview retains sampled interchange ramp");
    var deletion = new CompoundTag();
    deletion.putString("Action", "delete");
    deletion.putUUID("Id", selectedRamp.record.id());
    com.sora.splineroads.net.RoadNetwork.forget(remover.getUUID());
    com.sora.splineroads.net.RoadNetwork.perform(remover, deletion);
    h.assertTrue(
        !data.index.roads.containsKey(selectedRamp.record.id()),
        "single-ramp deletion through actual command does not crash");
    h.assertTrue(
        Interchanges.payload(level, null, points, group).getBoolean("Partial"),
        "partially removed group remains editable and marked incomplete");
    roads =
        data.index.roads.values().stream().filter(r -> group.equals(r.record.assembly())).toList();
    h.assertTrue(roads.size() == 17, "single deletion leaves other seventeen roads");
    com.sora.splineroads.net.RoadNetwork.forget(remover.getUUID());
    remover.discard();
    // Edit an existing assembly, including an option that needs real marker relocation.
    var edited = Interchanges.payload(level, null, points, group);
    var yellow =
        new Settings(Mode.STRAIGHT, Style.H6_YELLOW, 37, 1, .4, 90)
            .options(RoadProfile.Options.DEFAULT.outerRail(RoadProfile.OuterRail.OFF));
    edited.put("Main1", RoadRecord.writeSettings(yellow));
    edited.put("Main2", RoadRecord.writeSettings(yellow));
    edited.put(
        "Options",
        Interchanges.write(
            new InterchangePlanner.Options(
                InterchangePlanner.Preset.STACK, false, 1, 96, 20, 10, 2, 6, true)));
    var fitted = Interchanges.plan(edited);
    long[] fittedPoints =
        fitted.anchors().stream()
            .mapToLong(
                n -> {
                  var p = n.position();
                  return BlockPos.containing(p.x(), p.y(), p.z()).asLong();
                })
            .toArray();
    h.assertTrue(
        !Arrays.equals(points, fittedPoints),
        "insufficient height produces an explicit endpoint preview");
    int moved = 0;
    while (moved < points.length && points[moved] == fittedPoints[moved]) moved++;
    h.assertTrue(moved < points.length, "fit moves an actual endpoint");
    var first = BlockPos.of(points[moved]);
    var end = first.offset(-80, 0, 0);
    UUID outsideId = UUID.randomUUID();
    var outside =
        new RoadRecord(
            outsideId,
            new UUID(0, 0),
            first,
            end,
            RoadData.requireNode(level, first, null).constructionNode(),
            new Node(new V(end.getX() + .5, end.getY(), end.getZ() + .5), -90, 0),
            yellow,
            false,
            0);
    data.index.put(new RoadIndex.Built(outside, true));
    boolean refused = false;
    try {
      Interchanges.build(level, null, edited);
    } catch (IllegalArgumentException e) {
      refused = e.getMessage().contains("选区外道路");
    }
    data.index.remove(outsideId);
    h.assertTrue(refused, "auto fit refuses to break an external endpoint connection");
    var protectedPoint = BlockPos.of(fittedPoints[moved]);
    try (var work = RoadWorkChunks.open(level)) {
      work.node(protectedPoint);
      var previous = level.getBlockState(protectedPoint);
      level.setBlockAndUpdate(
          protectedPoint, net.minecraft.world.level.block.Blocks.BEDROCK.defaultBlockState());
      refused = false;
      try {
        Interchanges.build(level, null, edited);
      } catch (IllegalArgumentException e) {
        refused = true;
      } finally {
        level.setBlockAndUpdate(protectedPoint, previous);
      }
      h.assertTrue(refused, "occupied / unbreakable fit destination aborts the whole update");
      for (int i = 0; i < points.length; i++) {
        var at = BlockPos.of(points[i]);
        work.node(at);
        h.assertTrue(
            level.getBlockEntity(at) instanceof NodeEntity, "failed fit leaves original markers");
      }
      for (var old : roads)
        h.assertTrue(
            data.index.roads.get(old.record.id()).record.equals(old.record),
            "failed fit leaves every existing road unchanged");
    }
    long start = System.nanoTime();
    Interchanges.build(level, null, edited);
    System.out.printf(
        java.util.Locale.ROOT,
        "REVISION 11 actual stack style/width/endpoint update: %.2f s%n",
        (System.nanoTime() - start) / 1e9);
    var reopened = Interchanges.payload(level, null, fittedPoints, group);
    h.assertTrue(
        !reopened.getBoolean("Partial") && data.index.roads.containsKey(selectedRamp.record.id()),
        "explicit whole update reconstructs the deleted ramp and clears partial flag");
    h.assertTrue(
        Arrays.equals(reopened.getLongArray("Points"), fittedPoints),
        "reopened group uses fitted markers");
    var savedOptions = Interchanges.read(reopened.getCompound("Options"));
    h.assertTrue(
        savedOptions.adjustEndpoints() && savedOptions.rampWidth() == 6,
        "ramp width and fit option survive reopen");
    try (var work = RoadWorkChunks.open(level)) {
      for (int i = 0; i < points.length; i++) {
        BlockPos from = BlockPos.of(points[i]), to = BlockPos.of(fittedPoints[i]);
        work.node(from);
        work.node(to);
        h.assertTrue(
            RoadData.requireNode(level, to, null)
                .constructionNode()
                .equals(fitted.anchors().get(i)),
            "physical marker matches exact preview coordinates");
        if (!from.equals(to))
          h.assertTrue(
              !(level.getBlockEntity(from) instanceof NodeEntity), "old marker physically removed");
      }
    }
    var loaded = RoadData.load(data.save(new CompoundTag()));
    for (var built : data.index.roads.values())
      if (group.equals(built.record.assembly())) {
        var copy = loaded.index.roads.get(built.record.id()).record;
        if (copy.settings().style().ramp())
          h.assertTrue(copy.settings().width() == 6, "all saved ramps use edited width");
        else
          h.assertTrue(
              copy.settings().style() == Style.H6_YELLOW
                  && copy.settings().options().outerRail() == RoadProfile.OuterRail.OFF,
              "edited median and outer rail option survive world save/load");
        h.assertTrue(
            RoadGrades.maximum(copy.mesh()) <= .15 + 1e-7,
            "fitted saved geometry remains within 15 percent");
      }
    points = fittedPoints;
    Interchanges.remove(level, null, group);
    try (var work = RoadWorkChunks.open(level)) {
      for (long point : points) {
        var pos = BlockPos.of(point);
        work.node(pos);
        level.removeBlock(pos, false);
      }
    }
    h.assertTrue(
        RoadWorkChunks.heldCount(level) == 0, "350-block construction releases chunk tickets");
    h.succeed();
  }

  private static BlockPos node(ServerLevel level, int x, int y, int z) {
    var p = new BlockPos(x, y, z);
    try (var work = RoadWorkChunks.open(level)) {
      work.node(p);
      level.setBlockAndUpdate(p, SplineRoads.NODE.get().defaultBlockState());
      var entity = (NodeEntity) level.getBlockEntity(p);
      entity.offsetY = 0;
      entity.heightExplicit = true;
      entity.setChanged();
    }
    return p;
  }

  @GameTest(template = "empty", batch = "junctionRevision", timeoutTicks = 600)
  public static void ringFeedsAtBothDiameterNodes(GameTestHelper h) {
    var level = h.getLevel();
    var data = RoadData.get(level);
    var a = node(level, 32000, 110, 32000);
    var b = node(level, 32080, 110, 32000);
    var x = node(level, 31900, 110, 32000);
    var y = node(level, 32180, 110, 32000);
    var ringSettings =
        new Settings(Mode.RING, Style.O2_YELLOW, 9, 1, .4, 90).structure(Structure.GROUND);
    var ring = data.connect(level, null, a, b, ringSettings, null);
    var settings = RoadPlanner.mode(ringSettings, Mode.AUTO, 90);
    var first = data.connect(level, null, x, a, settings, null);
    var second = data.connect(level, null, b, y, settings, null);
    h.assertTrue(
        first.mesh().last().center().distance(ring.start().position()) < 1e-6,
        "feed reaches first diameter marker");
    h.assertTrue(
        second.mesh().first().center().distance(ring.end().position()) < 1e-6,
        "second diameter marker is opposite side, not closed seam");
    h.assertTrue(
        data.index.roads.get(ring.id()).mesh.closed(),
        "ring stays closed after attaching two feeders");
    var loaded = RoadData.load(data.save(new CompoundTag()));
    h.assertTrue(
        loaded.index.roads.get(ring.id()).mesh.closed(), "ring remains closed after save/load");
    var sampledRing = ring.alignment(UUID.randomUUID(), ring.mesh());
    h.assertTrue(
        RoadRecord.load(sampledRing.save()).mesh().closed(),
        "sampled interchange ring retains closure across serialization");
    h.assertTrue(
        loaded.index.atNode(a).contains(first.id()) && loaded.index.atNode(b).contains(second.id()),
        "both joins persist");
    data.remove(level, null, first.id());
    data.remove(level, null, second.id());
    h.assertTrue(data.index.roads.containsKey(ring.id()), "removing a feeder preserves the ring");
    data.remove(level, null, ring.id());
    h.assertTrue(RoadWorkChunks.heldCount(level) == 0, "ring lifecycle releases chunk tickets");
    h.succeed();
  }

  @GameTest(template = "empty", batch = "junctionRevision", timeoutTicks = 600)
  public static void ringLaneRampsUseOuterCarriageway(GameTestHelper h) {
    var level = h.getLevel();
    var data = RoadData.get(level);
    var a = node(level, 35960, 110, 36000);
    var b = node(level, 36040, 110, 36000);
    var x = node(level, 35840, 110, 36120);
    var y = node(level, 36160, 110, 35880);
    var ring =
        data.connect(
            level,
            null,
            a,
            b,
            new Settings(Mode.RING, Style.O2_YELLOW, 9, 1, .4, 90).structure(Structure.GROUND),
            null);
    var settings = new Settings(Mode.AUTO, Style.R1, 5, 1, .4, 90).rampTurn(RampTurn.AUTO);
    var first = data.connect(level, null, a, x, settings, null);
    var second = data.connect(level, null, b, y, settings, null);
    h.assertTrue(
        first.start().position().x() < ring.start().position().x(),
        "west port uses outer carriageway");
    h.assertTrue(
        second.start().position().x() > ring.end().position().x(),
        "east port uses opposite ring point and outer carriageway");
    for (var ramp : List.of(first, second)) {
      h.assertTrue(
          !RoadJunction.markings(ramp.mesh(), List.of(ring.mesh())).isEmpty(),
          "closed-host ramp has gore markings across circle seam");
      h.assertTrue(
          !data.index.roads.get(ramp.id()).cells.isEmpty(), "ring ramp has physical collision");
    }
    data.remove(level, null, first.id());
    data.remove(level, null, second.id());
    data.remove(level, null, ring.id());
    h.assertTrue(RoadWorkChunks.heldCount(level) == 0, "ring ramp operations release tickets");
    h.succeed();
  }

  @GameTest(template = "empty", templateNamespace = "splineroads_removal15", batch = "junctionRevision", timeoutTicks = 600)
  public static void highDiamondConstructionAndReuse(GameTestHelper h) {
    var level = h.getLevel();
    var data = RoadData.get(level);
    long[] points = {
      node(level, 33400, 90, 34000).asLong(), node(level, 34600, 90, 34000).asLong(),
      node(level, 34000, 123, 33400).asLong(), node(level, 34000, 123, 34600).asLong()
    };
    var payload = Interchanges.payload(level, null, points, null);
    payload.put(
        "Options",
        Interchanges.write(
            new InterchangePlanner.Options(
                InterchangePlanner.Preset.DIAMOND, false, 1, 36, 20, 5, 2)));
    long begin = System.nanoTime();
    Interchanges.build(level, null, payload);
    System.out.printf(
        Locale.ROOT,
        "HIGH DIAMOND 1200x500 / gap 33 build %.3fs%n",
        (System.nanoTime() - begin) / 1e9);
    var group =
        data.index.roads.values().stream()
            .filter(r -> r.record.a().asLong() == points[0])
            .findFirst()
            .orElseThrow()
            .record
            .assembly();
    var roads =
        data.index.roads.values().stream().filter(r -> group.equals(r.record.assembly())).toList();
    h.assertTrue(roads.size() == 8, "two mains, two junction pads and four continuous ramps");
    for (var road : roads) {
      var rebuilt = new RoadIndex.Built(road.record);
      h.assertTrue(
          road.cells.equals(rebuilt.cells),
          "reused road deck collision equals fresh full raster with structures");
      h.assertTrue(
          road.columns.equals(rebuilt.columns), "clearance columns unchanged by structure reuse");
      h.assertTrue(road.chunks.equals(rebuilt.chunks), "same region coverage with reused deck");
    }
    for (var road : roads) {
      var copy = RoadRecord.load(road.record.save());
      var actual = copy.mesh().samples();
      h.assertTrue(
          actual.size() == road.mesh.samples().size(),
          "alignment sample count survives serialization");
      for (int i = 0; i < actual.size(); i++) {
        var a = actual.get(i);
        var b = road.mesh.samples().get(i);
        h.assertTrue(
            a.center().distance(b.center()) < 1e-10
                && a.left().distance(b.left()) < 1e-10
                && Math.abs(a.distance() - b.distance()) < 1e-10
                && a.halfWidth() == b.halfWidth(),
            "whole ramp alignment geometry survives serialization");
      }
    }
    long heads =
        roads.stream()
            .flatMap(r -> r.record.structures().stream())
            .filter(RoadSignals::signal)
            .count();
    h.assertTrue(heads == 6, "diamond has four main approaches and two ramp signals");
    for (var road : roads) {
      var loadedRoad = RoadRecord.load(road.record.save());
      h.assertTrue(
          loadedRoad.structures().equals(road.record.structures()),
          "signal bodies and phase groups persist");
      var lazy = new RoadIndex.Built(loadedRoad, true);
      h.assertTrue(
          lazy.signalFaces(true).equals(RoadSignals.lights(lazy.signalHeads, true)),
          "client cached light geometry uses persisted heads");
    }
    var mains =
        roads.stream()
            .filter(
                r ->
                    !r.record.settings().style().ramp()
                        && r.record.settings().style() != Style.UNMARKED)
            .toList();
    var commandPlayer =
        net.minecraftforge.common.util.FakePlayerFactory.get(
            level, new com.mojang.authlib.GameProfile(new UUID(0, 0), "GeneratorRevisionTest"));
    commandPlayer.setGameMode(net.minecraft.world.level.GameType.CREATIVE);
    var deletion = new CompoundTag();
    deletion.putString("Action", "interchangeDeleteRamps");
    deletion.putUUID("Id", group);
    for (var invalid :
        List.of(
            SplineRoads.INTERCHANGE_3.get(),
            SplineRoads.INTERCHANGE_5.get(),
            SplineRoads.INTERCHANGE_6.get())) {
      commandPlayer.setItemInHand(
          net.minecraft.world.InteractionHand.MAIN_HAND,
          new net.minecraft.world.item.ItemStack(invalid));
      com.sora.splineroads.net.RoadNetwork.forget(commandPlayer.getUUID());
      boolean rejected = false;
      try {
        com.sora.splineroads.net.RoadNetwork.perform(commandPlayer, deletion);
      } catch (IllegalArgumentException expected) {
        rejected = true;
      }
      h.assertTrue(
          rejected && data.index.roads.containsKey(mains.get(0).record.id()),
          "wrong arity and placeholders cannot modify a four-way layout");
    }
    commandPlayer.setItemInHand(
        net.minecraft.world.InteractionHand.MAIN_HAND,
        new net.minecraft.world.item.ItemStack(SplineRoads.INTERCHANGE.get()));
    com.sora.splineroads.net.RoadNetwork.forget(commandPlayer.getUUID());
    com.sora.splineroads.net.RoadNetwork.perform(commandPlayer, deletion);
    for (var old : mains) {
      var kept = data.index.roads.get(old.record.id());
      h.assertTrue(
          kept != null && kept.record.assembly() == null,
          "only-ramp deletion detaches both mainlines");
      h.assertTrue(
          kept.mesh.samples().equals(old.mesh.samples()),
          "mainline shape and elevation are exactly preserved");
      h.assertTrue(kept.signalHeads.isEmpty(), "junction signals removed with the ramps");
      var neighbors =
          data.index.roads.values().stream()
              .filter(r -> !r.record.id().equals(kept.record.id()))
              .map(r -> r.mesh)
              .toList();
      h.assertTrue(
          RoadSignals.approaches(kept.mesh, neighbors).isEmpty(),
          "no stray stop lines after deleting junction pads");
      data.connect(
          level, null, kept.record.a(), kept.record.b(), kept.record.settings(), kept.record.id());
    }
    h.assertTrue(
        data.index.roads.values().stream().noneMatch(r -> group.equals(r.record.assembly())),
        "no residual assembly roads");
    for (var old : mains) data.remove(level, null, old.record.id());
    // Reuse the real endpoints for a three-way trumpet, then remove its entire C stem.
    long[] three = Arrays.copyOf(points, 3);
    three[2] = node(level, 34000, 111, 33400).asLong();
    var trumpet = Interchanges.payload(level, null, three, null);
    Interchanges.build(level, null, trumpet);
    UUID threeGroup =
        data.index.roads.values().stream()
            .filter(r -> r.record.a().asLong() == points[0])
            .findFirst()
            .orElseThrow()
            .record
            .assembly();
    var threeMains =
        data.index.roads.values().stream()
            .filter(
                r -> threeGroup.equals(r.record.assembly()) && !r.record.settings().style().ramp())
            .toList();
    h.assertTrue(threeMains.size() == 2, "three-way has AB mainline and C stub");
    deletion.putUUID("Id", threeGroup);
    commandPlayer.setItemInHand(
        net.minecraft.world.InteractionHand.MAIN_HAND,
        new net.minecraft.world.item.ItemStack(SplineRoads.INTERCHANGE_3.get()));
    com.sora.splineroads.net.RoadNetwork.forget(commandPlayer.getUUID());
    com.sora.splineroads.net.RoadNetwork.perform(commandPlayer, deletion);
    for (var old : threeMains) {
      if (old.record.a().asLong() == three[2] || old.record.b().asLong() == three[2]) {
        h.assertTrue(!data.index.roads.containsKey(old.record.id()),
            "three-way ramp deletion removes the terminating C stem");
        h.assertTrue(data.index.atNode(BlockPos.of(three[2])).isEmpty()
            && level.getBlockEntity(BlockPos.of(three[2])) instanceof NodeEntity,
            "C is an isolated editable endpoint after ramp deletion");
        h.assertTrue(!(level.getBlockEntity(old.record.b()) instanceof NodeEntity),
            "no generated marker remains at the former C fork");
        continue;
      }
      var kept = data.index.roads.get(old.record.id()).record;
      h.assertTrue(kept.assembly() == null, "through AB mainline is detached");
      h.assertTrue(kept.mesh().samples().equals(old.mesh.samples()),
          "ramp-only deletion preserves the through mainline exactly");
      h.assertTrue(RoadData.requireNode(level, kept.a(), null) != null
          && RoadData.requireNode(level, kept.b(), null) != null,
          "through mainline endpoints remain editable");
      data.connect(level, null, kept.a(), kept.b(), kept.settings(), kept.id());
      data.remove(level, null, kept.id());
    }
    com.sora.splineroads.net.RoadNetwork.forget(commandPlayer.getUUID());
    commandPlayer.discard();
    h.assertTrue(RoadWorkChunks.heldCount(level) == 0, "interchange releases tickets");
    h.succeed();
  }
}
