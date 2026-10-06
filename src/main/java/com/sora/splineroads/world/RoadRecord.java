package com.sora.splineroads.world;

import com.sora.splineroads.core.RoadGeometry;
import com.sora.splineroads.core.RoadGeometry.*;
import com.sora.splineroads.core.RoadProfile;
import com.sora.splineroads.core.RoadStructures.Part;
import com.sora.splineroads.core.RoadTransitions;
import com.sora.splineroads.core.RoadFurniture;
import java.io.*;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.*;
import net.minecraft.nbt.CompoundTag;

public record RoadRecord(
    UUID id,
    UUID owner,
    BlockPos a,
    BlockPos b,
    Node start,
    Node end,
    Settings settings,
    boolean automatic,
    int clearance,
    List<Part> structures,
    int endCaps,
    int buildVersion,
    UUID assembly,
    List<Sample> alignment,
    RoadFurniture.Phase furniturePhase, com.sora.splineroads.core.JunctionPlanner.Ref junction) {
  public RoadRecord(UUID id, UUID owner, BlockPos a, BlockPos b, Node start, Node end, Settings settings, boolean automatic, int clearance, List<Part> structures, int endCaps, int buildVersion, UUID assembly, List<Sample> alignment, RoadFurniture.Phase phase) {
    this(id,owner,a,b,start,end,settings,automatic,clearance,structures,endCaps,buildVersion,assembly,alignment,phase,null);
  }
  public RoadRecord junction(UUID group, com.sora.splineroads.core.JunctionPlanner.Ref ref) {
    return new RoadRecord(id,owner,a,b,start,end,settings,automatic,clearance,ref.get().structures(),0,18,group,List.of(),furniturePhase,ref);
  }
  public RoadRecord(UUID id, UUID owner, BlockPos a, BlockPos b, Node start, Node end,
      Settings settings, boolean automatic, int clearance, List<Part> structures,
      int endCaps, int buildVersion, UUID assembly, List<Sample> alignment) {
    this(id, owner, a, b, start, end, settings, automatic, clearance, structures,
        endCaps, buildVersion, assembly, alignment, null);
  }

  public RoadRecord furniturePhase(RoadFurniture.Phase phase) {
    if(Objects.equals(furniturePhase,phase))return this;
    return new RoadRecord(id, owner, a, b, start, end, settings, automatic, clearance,
        structures, endCaps, buildVersion, assembly, alignment, phase, junction);
  }
  public RoadRecord(
      UUID id,
      UUID owner,
      BlockPos a,
      BlockPos b,
      Node start,
      Node end,
      Settings settings,
      boolean automatic,
      int clearance,
      List<Part> structures,
      int caps,
      int version) {
    this(
        id,
        owner,
        a,
        b,
        start,
        end,
        settings,
        automatic,
        clearance,
        structures,
        caps,
        version,
        null,
        List.of());
  }

  public RoadRecord alignment(UUID group, Mesh mesh) {
    return new RoadRecord(
        id,
        owner,
        a,
        b,
        start,
        end,
        settings,
        automatic,
        clearance,
        structures,
        endCaps,
        buildVersion,
        group,
        mesh.samples(), furniturePhase, junction);
  }

  public RoadRecord(
      UUID id,
      UUID owner,
      BlockPos a,
      BlockPos b,
      Node start,
      Node end,
      Settings settings,
      boolean automatic,
      int clearance,
      List<Part> structures) {
    this(id, owner, a, b, start, end, settings, automatic, clearance, structures, 0, 17);
  }

  public RoadRecord caps(int mask) {
    return new RoadRecord(
        id,
        owner,
        a,
        b,
        start,
        end,
        settings,
        automatic,
        clearance,
        structures,
        mask,
        junction == null ? 17 : 18,
        assembly,
        alignment, furniturePhase, junction);
  }

  public RoadRecord(
      UUID id,
      UUID owner,
      BlockPos a,
      BlockPos b,
      Node start,
      Node end,
      Settings settings,
      boolean automatic,
      int clearance) {
    this(id, owner, a, b, start, end, settings, automatic, clearance, List.of());
  }

  public RoadRecord {
    structures = List.copyOf(structures);
    alignment = List.copyOf(alignment);
  }

  public RoadRecord structures(List<Part> parts) {
    if(structures.equals(parts))return this;
    return new RoadRecord(
        id,
        owner,
        a,
        b,
        start,
        end,
        settings,
        automatic,
        clearance,
        parts,
        endCaps,
        buildVersion,
        assembly,
        alignment, furniturePhase, junction);
  }

  public RoadRecord(
      UUID id, UUID owner, BlockPos a, BlockPos b, Node start, Node end, Settings settings) {
    this(id, owner, a, b, start, end, settings, false, 0);
  }

  public CompoundTag save() {
    CompoundTag t = new CompoundTag();
    t.putUUID("Id", id);
    t.putUUID("Owner", owner);
    t.putLong("A", a.asLong());
    t.putLong("B", b.asLong());
    t.put("Start", writeNode(start));
    t.put("End", writeNode(end));
    t.put("Settings", writeSettings(settings));
    t.putBoolean("Automatic", automatic);
    t.putInt("Clearance", clearance);
    t.putInt("EndCaps", endCaps);
    t.putInt("BuildVersion", buildVersion);
    t.putByteArray("StructuresPacked", packStructures());
    if (furniturePhase != null) {
      t.putDouble("FurnitureOrigin", furniturePhase.origin());
      t.putInt("FurnitureDirection", furniturePhase.direction());
    }
    if (junction != null) t.put("Junction", JunctionCodec.writeRef(junction));
    if (assembly != null) t.putUUID("Assembly", assembly);
    if (!alignment.isEmpty()) t.putByteArray("Alignment", packAlignment());
    return t;
  }

  /** Editing and host lookup only need the path, never the baked furniture. */
  public CompoundTag header() {
    return structures(List.of()).save();
  }

  private byte[] packStructures() {return packStructures(structures);}
  public static byte[] packStructures(List<Part> structures) {
    try {
      var bytes = new ByteArrayOutputStream();
      var out = new DataOutputStream(bytes);
      out.writeInt(-31);out.writeInt(structures.size());
      for (Part p : structures) {
        writeVector(out, p.a());
        writeVector(out, p.b());
        out.writeDouble(p.width());
        out.writeDouble(p.height());
        out.writeByte(
            (p.pier() ? 1 : 0) | (p.frameA() != null ? 2 : 0) | (p.frameB() != null ? 4 : 0));
        out.writeByte(p.material().ordinal());
        if (p.frameA() != null) writeVector(out, p.frameA());
        if (p.frameB() != null) writeVector(out, p.frameB());
        out.writeUTF(p.model());
      }
      out.flush();
      return bytes.toByteArray();
    } catch (IOException e) {
      throw new IllegalStateException(e);
    }
  }

  private static void writeVector(DataOutput out, V v) throws IOException {
    out.writeDouble(v.x());
    out.writeDouble(v.y());
    out.writeDouble(v.z());
  }

  private static V readVector(DataInput in) throws IOException {
    return new V(in.readDouble(), in.readDouble(), in.readDouble());
  }

  public static List<Part> unpackStructures(byte[] bytes) {
    try {
      var in = new DataInputStream(new ByteArrayInputStream(bytes));
      int count = in.readInt();boolean models=count==-31;if(models)count=in.readInt();
      if (count < 0 || count > 50000 || (long) count * 66 > bytes.length - 4)
        throw new IOException("invalid structure count");
      List<Part> parts = new ArrayList<>(count);
      var materials = com.sora.splineroads.core.RoadStructures.Material.values();
      for (int i = 0; i < count; i++) {
        V a = readVector(in), b = readVector(in);
        double w = in.readDouble(), h = in.readDouble();
        int flags = in.readUnsignedByte(), material = in.readUnsignedByte();
        if (flags > 7 || material >= materials.length)
          throw new IOException("invalid structure type");
        parts.add(
            new Part(
                a,
                b,
                w,
                h,
                (flags & 1) != 0,
                materials[material],
                (flags & 2) != 0 ? readVector(in) : null,
                (flags & 4) != 0 ? readVector(in) : null,models?in.readUTF():""));
      }
      if (in.available() != 0) throw new IOException("trailing structure data");
      return parts;
    } catch (IOException e) {
      throw new IllegalArgumentException("道路设施数据损坏", e);
    }
  }

  public static RoadRecord load(CompoundTag t) {
    List<Part> parts = new ArrayList<>();
    for (Tag tag : t.getList("Structures", Tag.TAG_COMPOUND)) {
      CompoundTag q = (CompoundTag) tag;
      parts.add(
          new Part(
              readNode(q.getCompound("A")).position(),
              readNode(q.getCompound("B")).position(),
              q.getDouble("Width"),
              q.getDouble("Height"),
              q.getBoolean("Pier"),
              q.contains("Material")
                  ? com.sora.splineroads.core.RoadStructures.Material.valueOf(
                      q.getString("Material"))
                  : com.sora.splineroads.core.RoadStructures.Material.DEFAULT,
              q.contains("FrameA") ? readNode(q.getCompound("FrameA")).position() : null,
              q.contains("FrameB") ? readNode(q.getCompound("FrameB")).position() : null));
    }
    if (t.contains("StructuresPacked", Tag.TAG_BYTE_ARRAY))
      parts = unpackStructures(t.getByteArray("StructuresPacked"));
    return new RoadRecord(
        t.getUUID("Id"),
        t.getUUID("Owner"),
        BlockPos.of(t.getLong("A")),
        BlockPos.of(t.getLong("B")),
        readNode(t.getCompound("Start")),
        readNode(t.getCompound("End")),
        readSettings(t.getCompound("Settings")),
        t.getBoolean("Automatic"),
        Math.max(0, Math.min(4, t.getInt("Clearance"))),
        parts,
        t.getInt("EndCaps"),
        t.getInt("BuildVersion"),
        t.hasUUID("Assembly") ? t.getUUID("Assembly") : null,
        t.contains("Alignment") ? unpackAlignment(t.getByteArray("Alignment")) : List.of(),
        t.contains("FurnitureOrigin")
            ? new RoadFurniture.Phase(t.getDouble("FurnitureOrigin"), t.getInt("FurnitureDirection"))
            : null,
        t.contains("Junction") ? JunctionCodec.readRef(t.getCompound("Junction")) : null);
  }

  public static CompoundTag writeNode(Node n) {
    CompoundTag t = new CompoundTag();
    t.putDouble("X", n.position().x());
    t.putDouble("Y", n.position().y());
    t.putDouble("Z", n.position().z());
    t.putDouble("Yaw", n.yaw());
    t.putDouble("Grade", n.grade());
    return t;
  }

  public static Node readNode(CompoundTag t) {
    return new Node(
        new V(t.getDouble("X"), t.getDouble("Y"), t.getDouble("Z")),
        t.getDouble("Yaw"),
        t.getDouble("Grade"));
  }

  public static CompoundTag writeSettings(Settings s) {
    CompoundTag t = new CompoundTag();
    t.putString("Mode", s.mode().name());
    t.putString("Style", s.style().name());
    t.putString("Structure", s.structure().name());
    t.putInt("TaperVersion", s.taperVersion());
    t.putString("RampTurn", s.rampTurn().name());
    t.putDouble("Width", s.width());
    t.putDouble("StartWidth", s.startWidth());
    t.putDouble("EndWidth", s.endWidth());
    t.putDouble("Thickness", s.thickness());
    t.putDouble("Tension", s.tension());
    t.putDouble("Angle", s.arcDegrees());
    var o = s.options();
    if(o.lanes().explicit()){var lanes=new CompoundTag();lanes.putInt("Forward",o.lanes().forward());lanes.putInt("Reverse",o.lanes().reverse());t.put("DirectionalLanes",lanes);}
    ListTag lines=new ListTag();for(var line:o.laneLines()){var tag=new CompoundTag();tag.putString("Key",line.key());tag.putString("Pattern",line.pattern().name());tag.putDouble("Width",line.width());lines.add(tag);}t.put("LaneLines",lines);
    t.putBoolean("LeftTraffic", o.leftTraffic());
    t.putBoolean("CycleLane", o.cycle());t.putBoolean("CycleAsphalt",o.cycleAsphalt());
    t.putBoolean("CycleRail", o.cycleRail());
    t.putBoolean("Curb", o.curb());
    CompoundTag walk=new CompoundTag();walk.putBoolean("Enabled",o.sidewalk().enabled());walk.putString("Side",o.sidewalk().side().name());walk.putInt("Width",o.sidewalk().width());walk.putString("Material",o.sidewalk().material());walk.putBoolean("Smooth",o.sidewalk().smooth());walk.putBoolean("Tactile",o.sidewalk().tactile());t.put("Sidewalk",walk);
    var infra=o.infrastructure();CompoundTag facilities=new CompoundTag();
    facilities.putString("Bridge",infra.bridge().name());facilities.putDouble("Span",infra.span());facilities.putString("Tunnel",infra.tunnel().name());facilities.putDouble("Headroom",infra.headroom());facilities.putString("Gantry",infra.gantry().name());facilities.putDouble("Spacing",infra.spacing());facilities.putDouble("TunnelDepth",infra.tunnelDepth());facilities.putString("TunnelAdjustment",infra.adjustment().name());facilities.putInt("DipProfile",infra.efficientDip()?1:0);facilities.putDouble("BridgeRise",infra.bridgeRise());facilities.putDouble("MaxGrade",infra.maxGrade());facilities.putBoolean("AutoSpan",infra.autoSpan());
    ListTag edits=new ListTag();for(var edit:infra.gantryEdits()){
      CompoundTag e=new CompoundTag();e.putInt("Slot",edit.slot());e.putDouble("Offset",edit.offset());e.putDouble("Clearance",edit.clearance());e.putString("Kind",edit.kind().name());e.putBoolean("Reverse",edit.reverse());edits.add(e);
    }facilities.put("GantryEdits",edits);facilities.put("Signs",RoadSignCodec.write(infra.signs()));t.put("Infrastructure",facilities);
    t.putString("OuterRail", o.outerRail().name());
    t.putBoolean("HideArrows",o.hideArrows());
    t.put("AttachedRoadPoints",AttachedPointCodec.write(o.attachments()));
    t.put("LanePointsV2",LanePointCodec.write(o.lanePoints()));
    {var decor=new CompoundTag();var c=o.streetscape();decor.putString("Separator",c.separator().name());decor.putBoolean("Parking",c.parking());decor.putDouble("LampSpacing",c.lampSpacing());decor.putBoolean("WalkLamps",c.walkLamps());decor.putDouble("WalkLampSpacing",c.walkLampSpacing());decor.putString("Planting",c.planting().name());decor.putDouble("PlantingSpacing",c.plantingSpacing());var raised=new ListTag();for(var span:c.raisedSpans()){var item=new CompoundTag();item.putDouble("From",span.from());item.putDouble("To",span.to());raised.add(item);}decor.put("Raised",raised);t.put("Streetscape",decor);}
    t.putDouble("LiftPosition", o.liftPosition());
    t.putDouble("LiftHeight", o.liftHeight());
    t.putString("SourceSide", o.ports().source().name());
    t.putString("TargetSide", o.ports().target().name());
    t.putDouble("SourceOutset", o.ports().sourceOutset());
    t.putDouble("TargetOutset", o.ports().targetOutset());
    t.putString("SourceJoin", o.ports().sourceJoin().name());
    t.putString("TargetJoin", o.ports().targetJoin().name());
    t.putString("RouteKind", o.routing().kind().name());
    t.putDouble("LoopRadius", o.routing().radius());
    t.putDouble("ControlX", o.routing().offsetX());
    t.putDouble("ControlZ", o.routing().offsetZ());
    t.putBoolean("FitEdges", o.routing().fitEdges());
    t.putDouble("Transition", o.routing().transition());
    if(o.routing().fanArms()>0){t.putInt("FanArms",o.routing().fanArms());t.putDouble("FanStart",o.routing().fanStart());t.putDouble("FanEnd",o.routing().fanEnd());}
    if(o.ends().paintPhase()!=0)t.putDouble("PaintPhase",o.ends().paintPhase());
    if (o.ends().start() != null) t.put("StartSection", writeSection(o.ends().start()));
    if (o.ends().end() != null) t.put("EndSection", writeSection(o.ends().end()));
    t.putBoolean("KeepEndSections", o.ends().persistent());
    if(o.ends().trimmedStart()!=0||o.ends().trimmedEnd()!=0){t.putDouble("TrimmedStart",o.ends().trimmedStart());t.putDouble("TrimmedEnd",o.ends().trimmedEnd());}
    if(o.ends().port()!=null){var p=o.ends().port();var c=new CompoundTag();c.putDouble("MotorMin",p.motorMin());c.putDouble("MotorMax",p.motorMax());c.putDouble("Median",p.median());c.putDouble("MedianCenter",p.medianCenter());c.putDouble("CurbLeft",p.curbLeft());c.putDouble("CurbRight",p.curbRight());var d=new ListTag();p.dividers().forEach(v->d.add(DoubleTag.valueOf(v)));c.put("Dividers",d);t.put("JunctionPort",c);}
    return t;
  }

  public static Settings readSettings(CompoundTag t) {
    if (!t.contains("Mode")) return Settings.defaults();
    Settings s =
        new Settings(
            Mode.valueOf(t.getString("Mode")),
            Style.valueOf(t.getString("Style")),
            t.getDouble("Width"),
            t.getDouble("Thickness"),
            t.getDouble("Tension"),
            t.getDouble("Angle"),
            t.contains("StartWidth") ? t.getDouble("StartWidth") : t.getDouble("Width"),
            t.contains("EndWidth") ? t.getDouble("EndWidth") : t.getDouble("Width"),
            t.contains("Structure") ? Structure.valueOf(t.getString("Structure")) : Structure.AUTO,
            t.contains("TaperVersion") ? t.getInt("TaperVersion") : 1,
            t.contains("RampTurn") ? RampTurn.valueOf(t.getString("RampTurn")) : RampTurn.LEGACY);
    s =
        s.options(
            new RoadProfile.Options(
                t.getBoolean("LeftTraffic"),
                t.getBoolean("CycleLane"),
                t.getBoolean("CycleRail"),
                t.getBoolean("Curb"),
                t.contains("LiftPosition") ? t.getDouble("LiftPosition") : .5,
                t.getDouble("LiftHeight"),
                new RoadProfile.Ports(
                    t.contains("SourceSide")
                        ? RoadProfile.Side.valueOf(t.getString("SourceSide"))
                        : RoadProfile.Side.AUTO,
                    t.contains("TargetSide")
                        ? RoadProfile.Side.valueOf(t.getString("TargetSide"))
                        : RoadProfile.Side.AUTO,
                    t.getDouble("SourceOutset"),
                    t.getDouble("TargetOutset"),
                    t.contains("SourceJoin")
                        ? RoadProfile.JoinMode.valueOf(t.getString("SourceJoin"))
                        : RoadProfile.JoinMode.ADDED,
                    t.contains("TargetJoin")
                        ? RoadProfile.JoinMode.valueOf(t.getString("TargetJoin"))
                        : RoadProfile.JoinMode.ADDED),
                new RoadProfile.Routing(
                    t.contains("RouteKind")
                        ? RoadProfile.RouteKind.valueOf(t.getString("RouteKind"))
                        : RoadProfile.RouteKind.DIRECT,
                    t.contains("LoopRadius") ? t.getDouble("LoopRadius") : 24,
                    t.getDouble("ControlX"),
                    t.getDouble("ControlZ"),
                    t.getBoolean("FitEdges"),
                    t.contains("Transition") ? t.getDouble("Transition") : 36,
                    t.getInt("FanArms"),t.getDouble("FanStart"),t.getDouble("FanEnd")),
                t.contains("OuterRail")
                    ? RoadProfile.OuterRail.valueOf(t.getString("OuterRail"))
                    : RoadProfile.OuterRail.AUTO));
    s =
        s.options(
            s.options()
                .ends(
                    new com.sora.splineroads.core.RoadTransitions.Ends(
                        t.contains("StartSection")
                            ? readSection(t.getCompound("StartSection"))
                            : null,
                        t.contains("EndSection") ? readSection(t.getCompound("EndSection")) : null,
                        t.getBoolean("KeepEndSections"),t.getDouble("TrimmedStart"),t.getDouble("TrimmedEnd"),null)));
    if(t.contains("PaintPhase"))s=s.options(s.options().ends(s.options().ends().paintPhase(t.getDouble("PaintPhase"))));
    if(t.contains("JunctionPort")){var p=t.getCompound("JunctionPort");var d=new ArrayList<Double>();for(Tag v:p.getList("Dividers",Tag.TAG_DOUBLE))d.add(((DoubleTag)v).getAsDouble());s=s.options(s.options().ends(s.options().ends().port(new RoadTransitions.Port(p.getDouble("MotorMin"),p.getDouble("MotorMax"),p.getDouble("Median"),d,p.getDouble("CurbLeft"),p.getDouble("CurbRight"),p.getDouble("MedianCenter")))));}
    if(t.contains("Sidewalk")) {
      var walk=t.getCompound("Sidewalk");s=s.options(s.options().sidewalk(new com.sora.splineroads.core.RoadSidewalks.Config(walk.getBoolean("Enabled"),com.sora.splineroads.core.RoadSidewalks.Side.valueOf(walk.getString("Side")),walk.getInt("Width"),walk.getString("Material"),walk.getBoolean("Smooth"),!walk.contains("Tactile")||walk.getBoolean("Tactile"))));
    }
    if(t.contains("Infrastructure")){var i=t.getCompound("Infrastructure");s=s.options(s.options().infrastructure(new com.sora.splineroads.core.RoadInfrastructure.Config(com.sora.splineroads.core.RoadInfrastructure.Bridge.valueOf(i.getString("Bridge")),i.getDouble("Span"),com.sora.splineroads.core.RoadInfrastructure.Tunnel.valueOf(i.getString("Tunnel")),i.getDouble("Headroom"),com.sora.splineroads.core.RoadInfrastructure.Gantry.valueOf(i.getString("Gantry")),i.getDouble("Spacing"),i.getDouble("TunnelDepth"),i.getList("GantryEdits",Tag.TAG_COMPOUND).stream().map(tag->{var e=(CompoundTag)tag;return new com.sora.splineroads.core.RoadGantry.Edit(e.getInt("Slot"),e.getDouble("Offset"),e.getDouble("Clearance"),com.sora.splineroads.core.RoadInfrastructure.Gantry.valueOf(e.getString("Kind")),e.getBoolean("Reverse"));}).toList(),i.contains("TunnelAdjustment")?com.sora.splineroads.core.RoadTunnelFit.Adjustment.valueOf(i.getString("TunnelAdjustment")):com.sora.splineroads.core.RoadTunnelFit.Adjustment.OFF,i.getInt("DipProfile")>=1,i.getDouble("BridgeRise"),i.getDouble("MaxGrade"),i.getBoolean("AutoSpan"),RoadSignCodec.read(i.getList("Signs",Tag.TAG_COMPOUND)))));}
    else s=s.options(s.options().infrastructure(s.options().infrastructure().grade(0).autoSpan(false)));
    var lines=new ArrayList<com.sora.splineroads.core.RoadLaneLines.Edit>();for(Tag tag:t.getList("LaneLines",Tag.TAG_COMPOUND)){var e=(CompoundTag)tag;lines.add(new com.sora.splineroads.core.RoadLaneLines.Edit(e.getString("Key"),com.sora.splineroads.core.RoadLaneLines.Pattern.valueOf(e.getString("Pattern")),e.getDouble("Width")));}s=s.options(s.options().laneLines(lines).hideArrows(t.getBoolean("HideArrows")).attachments(AttachedPointCodec.read(t.getCompound("AttachedRoadPoints"))).lanePoints(LanePointCodec.read(t.getCompound("LanePointsV2"))));
    if(t.contains("DirectionalLanes")){var lanes=t.getCompound("DirectionalLanes");s=s.options(s.options().lanes(new com.sora.splineroads.core.RoadLanes.Counts(lanes.getInt("Forward"),lanes.getInt("Reverse"))));}
    s.validate();
    if(s.options().cycle()&&t.getBoolean("CycleAsphalt"))s=s.options(s.options().cycleFinish(RoadProfile.Options.CycleFinish.ASPHALT));
    if(t.contains("Streetscape")){var c=t.getCompound("Streetscape");s=s.options(s.options().streetscape(new com.sora.splineroads.core.RoadStreetscape.Config(com.sora.splineroads.core.RoadStreetscape.Separator.valueOf(c.getString("Separator")),c.getBoolean("Parking"),c.getDouble("LampSpacing"),c.getBoolean("WalkLamps"),c.getDouble("WalkLampSpacing"),com.sora.splineroads.core.RoadStreetscape.Planting.valueOf(c.getString("Planting")),c.getDouble("PlantingSpacing"))));var spans=new ArrayList<com.sora.splineroads.core.RoadStreetscape.Span>();for(var item:c.getList("Raised",Tag.TAG_COMPOUND)){var r=(CompoundTag)item;spans.add(new com.sora.splineroads.core.RoadStreetscape.Span(r.getDouble("From"),r.getDouble("To")));}s=s.options(s.options().streetscape(s.options().streetscape().raisedSpans(spans)));}
    return s;
  }

  private static CompoundTag writeSection(com.sora.splineroads.core.RoadTransitions.Section s) {
    var tag=writeSettings(s.settings(false));if(s.sidewalk()==null)tag.remove("Sidewalk");return tag;
  }

  private static com.sora.splineroads.core.RoadTransitions.Section readSection(CompoundTag t) {
    // Endpoint descriptions contain a plain cross-section, never nested transition descriptions.
    CompoundTag flat = t.copy();
    flat.remove("StartSection");
    flat.remove("EndSection");
    var settings=readSettings(flat);var section=com.sora.splineroads.core.RoadTransitions.Section.of(settings).port(settings.options().ends().port());return t.contains("Sidewalk")?section:new com.sora.splineroads.core.RoadTransitions.Section(section.style(),section.width(),section.cycle(),section.cycleRail(),section.curb(),section.outerRail(),null,section.cycleAsphalt(),section.port(),section.streetscape(),section.lanes());
  }

  public RoadRecord derivedStreetscape(com.sora.splineroads.core.RoadStreetscape.Config config){
    if(settings.options().streetscape().equals(config))return this;
    return new RoadRecord(id,owner,a,b,start,end,settings.options(settings.options().streetscape(config)),automatic,clearance,structures,endCaps,buildVersion,assembly,alignment,furniturePhase,junction);
  }
  public RoadRecord withLanePoints(com.sora.splineroads.core.LanePoints.Data value){
    if(settings.options().lanePoints().equals(value))return this;
    return new RoadRecord(id,owner,a,b,start,end,settings.options(settings.options().lanePoints(value)),automatic,clearance,structures,endCaps,buildVersion,assembly,alignment,furniturePhase,junction);
  }
  public RoadRecord withAttachments(com.sora.splineroads.core.RoadAttachments.Data value){
    if(settings.options().attachments().equals(value))return this;
    return new RoadRecord(id,owner,a,b,start,end,settings.options(settings.options().attachments(value)),automatic,clearance,structures,endCaps,buildVersion,assembly,alignment,furniturePhase,junction);
  }
  private static boolean widthChanged(Settings before,Settings after){
    return before.width()!=after.width()||before.style()!=after.style()
      ||before.options().cycle()!=after.options().cycle()||before.options().curb()!=after.options().curb()
      ||!Objects.equals(before.options().ends().start(),after.options().ends().start())
      ||!Objects.equals(before.options().ends().end(),after.options().ends().end());
  }
  public RoadRecord settings(Settings value) {
    if(settings.equals(value))return this;
    List<Sample> adjusted = alignment;
    if (!alignment.isEmpty() && widthChanged(settings,value)) {
      double length = alignment.get(alignment.size() - 1).distance();
      adjusted =
          alignment.stream()
              .map(
                  p ->
                      new Sample(
                          p.center(),
                          p.left(),
                          p.distance(),
                          RoadTransitions.width(value,p.distance()+value.options().ends().trimmedStart(),length+value.options().ends().trimmedStart()+value.options().ends().trimmedEnd()) / 2))
              .toList();
    }
    return new RoadRecord(
        id,
        owner,
        a,
        b,
        start,
        end,
        value,
        automatic,
        clearance,
        List.of(),
        endCaps,
        buildVersion,
        assembly,
        adjusted, furniturePhase, junction);
  }

  private byte[] packAlignment() {
    try {
      var bytes = new ByteArrayOutputStream();
      var out = new DataOutputStream(bytes);
      out.writeInt(alignment.size());
      for (var p : alignment) {
        writeVector(out, p.center());
        out.writeDouble(p.left().x());
        out.writeDouble(p.left().z());
        out.writeDouble(p.halfWidth());
      }
      return bytes.toByteArray();
    } catch (IOException e) {
      throw new IllegalArgumentException("道路路径编码失败", e);
    }
  }

  private static List<Sample> unpackAlignment(byte[] bytes) {
    try {
      var in = new DataInputStream(new ByteArrayInputStream(bytes));
      int n = in.readInt();
      if (n < 2
          || n > com.sora.splineroads.core.RoadLimits.MAX_SAMPLES
          || bytes.length != 4 + n * 48) throw new IOException("invalid alignment");
      List<Sample> samples = new ArrayList<>();
      double distance = 0;
      V previous = null;
      for (int i = 0; i < n; i++) {
        V p = readVector(in), left = new V(in.readDouble(), 0, in.readDouble());
        double half = in.readDouble();
        if (!RoadGeometry.finite(p.x(), p.y(), p.z(), left.x(), left.z(), half)
            || half < .25
            || half > 32
            || Math.abs(left.horizontalLength() - 1) > .001)
          throw new IOException("invalid path point");
        if (previous != null) distance += p.distance(previous);
        samples.add(new Sample(p, left, distance, half));
        previous = p;
      }
      return List.copyOf(samples);
    } catch (IOException e) {
      throw new IllegalArgumentException("道路路径数据损坏", e);
    }
  }

  // Immutable snapshots are the cache key, not UUIDs: preview/edit geometry cannot go stale.
  private static final com.sora.splineroads.core.WeakIdentityCache<RoadRecord,Mesh> RAW_MESHES =
      new com.sora.splineroads.core.WeakIdentityCache<>(2048,750_000,m->m.samples().size());
  private static final com.sora.splineroads.core.WeakIdentityCache<RoadRecord,Mesh> SHAPED_MESHES =
      new com.sora.splineroads.core.WeakIdentityCache<>(2048,750_000,m->m.samples().size());
  public static void clearMeshCaches(){com.sora.splineroads.core.RoadTunnelSpace.clearPreparedCache();RAW_MESHES.clear();SHAPED_MESHES.clear();}
  public static String meshCacheStats(){return "raw="+RAW_MESHES.stats()+", shaped="+SHAPED_MESHES.stats();}
  public RoadGeometry.Mesh mesh() {return SHAPED_MESHES.get(this,r->com.sora.splineroads.core.LaneSections.apply(r.rawMesh()));}
  public RoadGeometry.Mesh rawMesh() {return RAW_MESHES.get(this,RoadRecord::buildRawMesh);}
  private RoadGeometry.Mesh buildRawMesh() {
    if (junction != null) return junction.get().mesh();
    if (!alignment.isEmpty()) {
      var mesh = com.sora.splineroads.core.RoadRibbon.mesh(alignment, settings);
      if (mesh.first().center().distance(mesh.last().center()) < 1e-6)
        return new Mesh(
            mesh.samples(),
            mesh.settings(),
            mesh.min(),
            mesh.max(),
            mesh.length(),
            true,
            mesh.controlPoint());
      return com.sora.splineroads.core.RoadAttachments.deform(mesh,settings);
    }
    return RoadGeometry.endCaps(
        RoadGeometry.build(start, end, settings), endCaps, start.grade(), end.grade());
  }
}
