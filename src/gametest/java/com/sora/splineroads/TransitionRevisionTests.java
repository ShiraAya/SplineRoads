package com.sora.splineroads;

import com.sora.splineroads.core.*;
import com.sora.splineroads.core.RoadGeometry.*;
import com.sora.splineroads.world.*;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.gametest.*;

@GameTestHolder(SplineRoads.ID)
@PrefixGameTestTemplate(false)
public final class TransitionRevisionTests {
  private static BlockPos node(ServerLevel level, int x, int y, int z) {
    BlockPos p = new BlockPos(x, y, z);
    level.getChunkAt(p);
    level.setBlockAndUpdate(p, SplineRoads.NODE.get().defaultBlockState());
    var n = (NodeEntity) level.getBlockEntity(p);
    n.heightExplicit = true;
    n.offsetY = .25;
    n.setChanged();
    return p;
  }

  private static Settings settings(Style style) {
    return new Settings(Mode.AUTO, style, style.defaultWidth(), 1, .4, 90);
  }

  @GameTest(template = "empty", batch = "transitions13", timeoutTicks = 600)
  public static void laneMedianTransitionBothOrders(GameTestHelper h) {
    var level = h.getLevel();
    var data = RoadData.get(level);
    Style[][] cases = {
      {Style.O4_GREEN, Style.O6_RAIL},
      {Style.H4_GREEN, Style.H6_RAIL},
      {Style.O1_ONE, Style.O2_ONE},
      {Style.O2_YELLOW, Style.O4_GREEN}
    };
    for (int c = 0; c < cases.length; c++)
      for (boolean reverse : new boolean[] {false, true}) {
        int x = 38000 + c * 100 + (reverse ? 50 : 0), z = 38000;
        BlockPos a = node(level, x, 110, z),
            b = node(level, x, 110, z + 100),
            end = node(level, x, 110, z + 200);
        var narrow = settings(cases[c][0]);
        var wide = settings(cases[c][1]);
        RoadRecord first, second;
        if (reverse) {
          second = data.connect(level, null, b, end, wide, null);
          first = data.connect(level, null, a, b, narrow, null);
        } else {
          first = data.connect(level, null, a, b, narrow, null);
          second = data.connect(level, null, b, end, wide, null);
        }
        var ma = data.index.roads.get(first.id()).mesh;
        var mb = data.index.roads.get(second.id()).mesh;
        h.assertTrue(
            Math.abs(ma.last().halfWidth() - mb.first().halfWidth()) < 1e-7,
            "joined widths match in both construction orders");
        var la = RoadProfile.layout(ma, ma.last());
        var lb = RoadProfile.layout(mb, mb.first());
        h.assertTrue(
            la.dividers().equals(lb.dividers()) && la.median() == lb.median(),
            "lane lines and median agree at seam");
        h.assertTrue(
            ma.last().halfWidth() > ma.first().halfWidth(), "narrow road receives actual taper");
        var restored = RoadRecord.load(data.index.roads.get(first.id()).record.save());
        h.assertTrue(
            restored.mesh().samples().equals(ma.samples()), "transition survives NBT save/load");
        var payload = new CompoundTag();
        data.jointPayload(payload, a, b, first.id());
        var preview = RoadData.joinSections(narrow, payload);
        h.assertTrue(
            preview.options().ends().equals(ma.settings().options().ends()),
            "client preview uses same endpoint sections");
        data.remove(level, null, second.id());
        h.assertTrue(
            data.index
                .roads
                .get(first.id())
                .record
                .settings()
                .options()
                .ends()
                .equals(RoadTransitions.Ends.NONE),
            "deleting neighbor removes obsolete taper");
        data.remove(level, null, first.id());
      }
    h.assertTrue(RoadWorkChunks.heldCount(level) == 0, "transition edits release chunks");
    h.succeed();
  }

  @GameTest(template = "empty", batch = "transitions13", timeoutTicks = 600)
  public static void incompatibleConnectionsAreAtomic(GameTestHelper h) {
    var level = h.getLevel();
    var data = RoadData.get(level);
    Style[][] pairs = {
      {Style.O2_RAIL, Style.O6_GREEN},
      {Style.O1_ONE, Style.O3_ONE},
      {Style.O4_RAIL, Style.H4_RAIL},
      {Style.O2_ONE, Style.O2_YELLOW}
    };
    for (int i = 0; i < pairs.length; i++) {
      BlockPos a = node(level, 38500 + i * 50, 110, 38000),
          b = node(level, 38500 + i * 50, 110, 38100),
          c = node(level, 38500 + i * 50, 110, 38200);
      var first = data.connect(level, null, a, b, settings(pairs[i][0]), null);
      CompoundTag before = data.index.roads.get(first.id()).record.save();
      int count = data.index.roads.size();
      boolean refused = false;
      try {
        data.connect(level, null, b, c, settings(pairs[i][1]), null);
      } catch (IllegalArgumentException e) {
        refused = true;
      }
      h.assertTrue(
          refused
              && data.index.roads.size() == count
              && data.index.roads.get(first.id()).record.save().equals(before),
          "incompatible connection changes nothing");
      data.remove(level, null, first.id());
    }
    h.succeed();
  }

  @GameTest(template = "empty", batch = "transitions13", timeoutTicks = 600)
  public static void greenEndpointAndRaisedMedian(GameTestHelper h) {
    var level = h.getLevel();
    var data = RoadData.get(level);
    BlockPos a = node(level, 38800, 115, 38000), b = node(level, 38800, 115, 38080);
    var road =
        data.connect(level, null, a, b, settings(Style.H4_GREEN).structure(Structure.BRIDGE), null);
    var parts = data.index.roads.get(road.id()).record.structures();
    h.assertTrue(
        parts.stream()
            .noneMatch(
                p ->
                    p.material() == RoadStructures.Material.GREEN
                        || p.material() == RoadStructures.Material.SOIL),
        "bridge uses barriers instead of plants");
    // An old 0.12 collider above a marker must not prevent the generator from selecting it.
    BlockPos covering = a.above();
    level.setBlockAndUpdate(covering, SplineRoads.COLLIDER.get().defaultBlockState());
    h.assertTrue(
        RoadTool.endpointAtHit(
                level, covering, new Vec3(a.getX() + .5, a.getY() + 1.2, a.getZ() + .5))
            .equals(a),
        "covered legacy marker can still be selected");
    level.removeBlock(covering, false);
    data.remove(level, null, road.id());
    h.succeed();
  }

  @GameTest(template = "empty", batch = "transitions13", timeoutTicks = 600)
  public static void terminatingRoadTipMayMissIntersection(GameTestHelper h) {
    var level = h.getLevel();
    var data = RoadData.get(level);
    BlockPos a = node(level, 39400, 100, 39000),
        b = node(level, 40600, 100, 39000),
        c = node(level, 40000, 119, 38400),
        tip = node(level, 40000, 119, 38996);
    var old = data.connect(level, null, c, tip, settings(Style.O4_GREEN), null);
    long[] points = {a.asLong(), b.asLong(), c.asLong()};
    var payload = Interchanges.payload(level, null, points, null);
    payload.put(
        "Options",
        Interchanges.write(
            new InterchangePlanner.Options(InterchangePlanner.Preset.Y, false, 1, 96, 20, 5, 2)));
    Interchanges.build(level, null, payload);
    h.assertTrue(
        !data.index.roads.containsKey(old.id()), "off-center existing C approach was replaced");
    UUID group =
        data.index.atNode(c).stream()
            .map(data.index.roads::get)
            .map(r -> r.record.assembly())
            .filter(Objects::nonNull)
            .findFirst()
            .orElseThrow();
    var trunk =
        data.index.roads.values().stream()
            .filter(r -> group.equals(r.record.assembly()) && r.record.a().equals(c))
            .findFirst()
            .orElseThrow();
    h.assertTrue(trunk.mesh.last().center().z() < 38800, "C mainline stops at its ramp split");
    Revision15GameTests.extendAssembly(h, a, a.offset(-85, 0, 0));
    Interchanges.removeRamps(level, null, group);
    h.assertTrue(!data.index.roads.containsKey(trunk.record.id()) && data.index.atNode(c).isEmpty(),
        "ramps-only deletion also removes the C stem");
    h.assertTrue(level.getBlockEntity(c) instanceof NodeEntity, "C marker survives");
    h.assertTrue(!(level.getBlockEntity(trunk.record.b()) instanceof NodeEntity),
        "no synthetic marker remains at the former C fork");
    h.assertTrue(data.index.atNode(a).size() == 1 && data.index.atNode(b).size() == 1,
        "only the through AB mainline survives");
    data.remove(level, null, data.index.atNode(a).iterator().next());
    h.succeed();
  }
}
