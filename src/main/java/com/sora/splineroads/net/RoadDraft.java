package com.sora.splineroads.net;

import java.util.List;
import net.minecraft.nbt.CompoundTag;

/** Preserve user edits when the server reopens the same target after world preview. */
public final class RoadDraft {
  public static CompoundTag merge(CompoundTag fresh, CompoundTag saved) {
    CompoundTag out = fresh.copy();
    if (saved == null
        || fresh.getBoolean("DeleteOnly")
        || saved.getBoolean("DeleteOnly")
        || !fresh.getString("Kind").equals(saved.getString("Kind"))) return out;
    boolean same =
        fresh.getString("Kind").equals("node")
            ? fresh.getLong("Pos") == saved.getLong("Pos")
            : fresh.hasUUID("Id")
                ? saved.hasUUID("Id") && fresh.getUUID("Id").equals(saved.getUUID("Id"))
                : !saved.hasUUID("Id")
                    && fresh.getLong("A") == saved.getLong("A")
                    && fresh.getLong("B") == saved.getLong("B");
    if(fresh.getString("Kind").equals("armRoad"))same &= fresh.getInt("Arm")==saved.getInt("Arm")&&fresh.getInt("Signature")==saved.getInt("Signature");
    if (same)
      for (String key :
          List.of(
              "Settings", "Node", "Advanced", "ExtraPage", "LiftPage", "OptionsPage", "LevelEnds", "ForceJunction", "ForceJunctionA", "ForceJunctionB"))
        if (saved.contains(key)) out.put(key, saved.get(key).copy());
    return out;
  }

  /** Commands never echo road meshes, neighboring structures or preview context. */
  public static CompoundTag action(CompoundTag draft, boolean node) {
    CompoundTag out = new CompoundTag();
    for (String key :
        node ? List.of("Pos", "Node") : List.of("A", "B", "Id", "Settings", "LevelEnds", "ForceJunction", "ForceJunctionA", "ForceJunctionB"))
      if (draft.contains(key)) out.put(key, draft.get(key).copy());
    out.putString("Action", node ? "node" : "connect");
    return out;
  }

  private RoadDraft() {}
}
