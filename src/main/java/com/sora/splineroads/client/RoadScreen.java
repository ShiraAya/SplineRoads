package com.sora.splineroads.client;
import com.sora.splineroads.core.RoadLanes;
import com.sora.splineroads.core.LaneRampAlignment;
import com.sora.splineroads.core.RoadTransitions;

import com.sora.splineroads.core.RoadGeometry.*;
import com.sora.splineroads.core.RoadPlanner;
import com.sora.splineroads.core.RoadInfrastructure;
import com.sora.splineroads.core.RoadProfile;
import com.sora.splineroads.core.RoadProfile.*;
import com.sora.splineroads.net.RoadNetwork;
import com.sora.splineroads.world.RoadData;
import com.sora.splineroads.world.RoadRecord;
import java.util.*;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.*;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;

public final class RoadScreen extends Screen {
  private final CompoundTag payload;
  private final boolean node;
  private Options options = Options.DEFAULT;
  private boolean extraPage, liftPage;
  private int optionsPage;


  private boolean laneRoad(){return payload.getString("Kind").equals("laneRampRoad");}

  private boolean optionsOnly() {
    return !node && width < 512 && extraPage;
  }

  private final Map<String, EditBox> fields = new LinkedHashMap<>();
  private Mode mode;
  private Style style;
  private Structure structure;
  private double roadWidth, thickness, tension, arc;
  private int left, top, debounce;
  private String status = "";
  private boolean pending, confirmDelete, advanced;
  private Button save, pathButton, styleButton;
  private static final Mode[] MODES = {Mode.AUTO, Mode.STRAIGHT, Mode.CURVE, Mode.ARC};
  private static final String[] STYLES = {"双向两车道", "双向四车道", "单向两车道", "无标线路面", "单车道匝道", "双车道匝道"};

  public RoadScreen(CompoundTag payload) {
    super(Component.literal("Spline Roads · 智能铺路"));
    this.payload = payload.copy();
    node = payload.getString("Kind").equals("node");

    advanced = payload.getBoolean("Advanced");
    extraPage = payload.getBoolean("ExtraPage");
    liftPage = (payload.getBoolean("LiftPage"));

    optionsPage = Math.max(0,Math.min(6,payload.getInt("OptionsPage")));

    if (!node) {
      var s = RoadProfile.editable(RoadRecord.readSettings(payload.getCompound("Settings")));
      mode = s.mode();
      options = s.options();

      style = s.style();
      if(RoadProfile.catalog(style).type()==Type.HIGHWAY)this.payload.putBoolean("ForceJunction",false);

      structure = s.structure();
      roadWidth = s.width();
      thickness = s.thickness();
      tension = s.tension();
      arc = s.arcDegrees();
    }
  }

  @Override
  protected void init() {
    fields.clear();
    pathButton = null;
    left = 12;
    top = Math.max(2, (height - 238) / 2);

    int x = left + 12, y = top + 40;
    if (node) {
      Node n = RoadRecord.readNode(payload.getCompound("Node"));
      BlockPos p = BlockPos.of(payload.getLong("Pos"));
      field("X 偏移", "x", n.position().x() - p.getX() - .5, x, y);
      field("Y 偏移", "y", n.position().y() - p.getY(), x + 136, y);
      field("Z 偏移", "z", n.position().z() - p.getZ() - .5, x, y + 42);
      field("朝向 / 度", "yaw", n.yaw(), x + 136, y + 42);
      field("坡度 / %", "grade", n.grade() * 100, x, y + 84);
      addRenderableWidget(
          Button.builder(
                  Component.literal("一键贴地"),
                  b -> {
                    fields.get("y").setValue("0");
                    changed();
                  })
              .bounds(x + 136, y + 96, 128, 18)
              .build());
    } else if (!optionsOnly()) {
      addRenderableWidget(
          Button.builder(
                  Component.literal(advanced ? "收起微调" : "展开微调"),
                  b -> {
                    if (capture()) {
                      advanced = !advanced;
                      payload.putBoolean("Advanced", advanced);
                      payload.putBoolean("ExtraPage", extraPage);
                      payload.putBoolean("LiftPage", liftPage);

                      rebuildWidgets();
                    }
                  })
              .bounds(left + 204, top + 10, 72, 18)
              .build());
      addRenderableWidget(
          Button.builder(
                  Component.literal(typeName()),
                  b -> {
                    if ((!capture())) return;
                    if(laneRoad())selectStyle(RoadProfile.highway(style)?Style.C1_RAMP:Style.C1_HIGHWAY_RAMP,true);
                    else applyLaneConfiguration(RoadProfile.highway(style)?Type.ORDINARY:Type.HIGHWAY,RoadLanes.counts(style,options),true);
                  })
              .bounds(x, y, 98, 20)
              .build());
      styleButton =
          addRenderableWidget(
              Button.builder(
                      Component.literal(styleName()),
                      b -> {
                        if (!capture()) return;
                        openLaneConfiguration();
                      })
                  .bounds(x + 102, y, 162, 20)
                  .build());
      pathButton =
          addRenderableWidget(
              Button.builder(
                      Component.literal("智能选线"),
                      b -> {
                        if (!capture()) return;


                        int i = Arrays.asList(MODES).indexOf(mode);
                        mode = MODES[(i + 1) % MODES.length];
                        changed();
                        refresh();
                      })
                  .bounds(x, y + 24, 264, 20)
                  .build());
      if (advanced) {
        field("单车道宽 / 格", "width", laneWidth(), x, y + 49);
        field("厚度 / 格", "thickness", thickness, x + 136, y + 49);
        field("曲线强度", "tension", tension, x, y + 81);
        field("圆弧角度 / 度", "angle", arc, x + 136, y + 81);
      } else {
        addRenderableWidget(
            Button.builder(
                    Component.literal(payload.getBoolean("LevelEnds") ? "两端贴地：开" : "两端贴地"),
                    b -> {
                      payload.putBoolean("LevelEnds", !payload.getBoolean("LevelEnds"));
                      rebuildWidgets();
                    })
                .bounds(x, top + 116, 128, 18)
                .build());
        addRenderableWidget(
            Button.builder(
                    Component.literal("−"),
                    b -> {
                      setLaneWidth(Math.max(2, laneWidth() - .5));
                      rebuildWidgets();
                    })
                .bounds(x, y + 48, 30, 20)
                .build());
        addRenderableWidget(
                    Button.builder(
                            Component.literal(
                                (RoadProfile.modern(style)
                                        ? "车道 " + format(laneWidth()) + " · 总宽 "
                                        : "路宽 ")
                                    + format(roadWidth)
                                    + " 格"),
                            b -> {})
                        .bounds(x + 34, y + 48, 196, 20)
                        .build())
                .active =
            false;
        addRenderableWidget(
            Button.builder(
                    Component.literal("+"),
                    b -> {
                      setLaneWidth(Math.min(RoadProfile.modern(style) ? 8 : 64, laneWidth() + .5));
                      rebuildWidgets();
                    })
                .bounds(x + 234, y + 48, 30, 20)
                .build());
      }
    }
    if (!node && !optionsOnly())
      addRenderableWidget(
          Button.builder(
                  Component.literal(structureName()),
                  b -> {
                    structure =
                        Structure.values()[(structure.ordinal() + 1) % Structure.values().length];
                    b.setMessage(Component.literal(structureName()));
                    changed();
                    refresh();
                  })
              .bounds(x, top + 156, 264, 18)
              .build());
    if (!node) {
      if (width >= 512 || optionsOnly())
        buildOptions(width >= 512 ? left + 294 : left + 12, top + 40, width >= 512 ? 194 : 264);
      if (width < 512 && !optionsOnly())
        option("▸", x+240,top+134,24,()->{extraPage=true;liftPage=false;});
    }
    if((!node)&&!optionsOnly()&&(payload.getInt("DegreeA")>0||payload.getInt("DegreeB")>0)){
      for(int endpoint=0;endpoint<2;endpoint++){
        String side=endpoint==0?"A":"B";boolean required=payload.getBoolean("Center"+side)||payload.getInt("Degree"+side)>=2;
        boolean incompatible=false;
        try {
          incompatible=!required&&payload.getInt("Degree"+side)>0&&payload.contains("JoinSection"+side)&&!com.sora.splineroads.core.RoadTransitions.compatible(new Settings(mode,style,roadWidth,thickness,tension,arc).options(options),RoadRecord.readSettings(payload.getCompound("JoinSection"+side)));
        } catch(IllegalArgumentException e) { incompatible=true;status="连接数据无效，请关闭后重新右键道路："+e.getMessage(); }
        required|=incompatible;boolean force=com.sora.splineroads.world.AutoJunctions.forced(payload,side);
        String label=side+" 端："+(required?"已有/必要路口":force?"生成路口":"连续道路");
        var button=addRenderableWidget(Button.builder(Component.literal(label),b->{payload.putBoolean("ForceJunction"+side,!com.sora.splineroads.world.AutoJunctions.forced(payload,side));rebuildWidgets();}).bounds(x+endpoint*134,top+177,130,18).build());
        button.active=!required&&payload.getInt("Degree"+side)>0&&RoadProfile.catalog(style).type()==Type.ORDINARY;
      }
    }

    boolean edit = payload.hasUUID("Id")&&!payload.getString("Kind").equals("armRoad");
    save =
        addRenderableWidget(
            Button.builder(Component.literal(node ? "保存端点" : "建造 / 保存"), b -> submit())
                .bounds(x, top + 206, edit ? 82 : 100, 20)
                .build());
    addRenderableWidget(
        Button.builder(
                Component.literal("实景预览"),
                b -> {
                  if (refresh()) minecraft.setScreen(null);
                })
            .bounds(x + (edit ? 86 : 104), top + 206, edit ? 62 : 76, 20)
            .build());
    addRenderableWidget(
        Button.builder(
                Component.literal("取消"),
                b -> {
                  ClientRoads.discardDraft();
                  ClientRoads.preview = null;
                  minecraft.setScreen(null);
                })
            .bounds(x + (edit ? 152 : 184), top + 206, edit ? 50 : 80, 20)
            .build());
    if (edit)
      addRenderableWidget(
          Button.builder(
                  Component.literal("删除"),
                  b -> {
                    if (!confirmDelete) {
                      confirmDelete = true;
                      b.setMessage(Component.literal("确认删"));
                    } else {
                      CompoundTag t = new CompoundTag();
                      t.putString("Action", "delete");
                      t.putUUID("Id", payload.getUUID("Id"));
                      pending = true;
                      RoadNetwork.CHANNEL.sendToServer(new RoadNetwork.Action(t));
                    }
                  })
              .bounds(x + 206, top + 206, 58, 20)
              .build());

    if(payload.getString("Kind").equals("armRoad")){
      if(pathButton!=null){pathButton.setMessage(Component.literal("接入线形随路口衔接"));pathButton.active=false;}
      for(var child:children())if(child instanceof Button b){String label=b.getMessage().getString();
        if((optionsPage==2||optionsPage==3)&&b.getX()>=(optionsOnly()?left+12:left+294)&&b.getY()>=top+40&&b.getY()<top+180)b.active=false;
        if(label.equals(typeName())||label.equals(structureName())||label.startsWith("两端贴地")||label.contains("靠左行驶")||label.startsWith("中间控制点")||label.contains("直路边缘")||label.startsWith("启用桥梁")||label.startsWith("启用隧道")||label.startsWith("龙门架")||label.startsWith("配跨")||label.startsWith("端点：")||label.startsWith("下潜：")||label.startsWith("最大坡度"))b.active=false;
      }
      for(String key:List.of("thickness","tension","angle","bridgeSpan","bridgeRise","tunnelHeight","tunnelDepth","gantrySpacing"))if(fields.containsKey(key))fields.get(key).setEditable(false);
    }
    if(laneRoad()){
      if(styleButton!=null)styleButton.active=false;
      if(pathButton!=null){pathButton.active=false;pathButton.setMessage(Component.literal("线路与扩出：用匝道连接器编辑"));}
      for(var child:children())if(child instanceof Button b&&b.getMessage().getString().startsWith("两端贴地"))b.active=false;
      for(String key:List.of("tension","angle"))if(fields.containsKey(key))fields.get(key).setEditable(false);
    }
    refresh();
  }

  private String structureName() {
    return switch (structure) {
      case AUTO -> "高架：自动识别悬空段";
      case GROUND -> "地面模式（高速仍保留护栏）";
      case BRIDGE -> "桥梁："+options.infrastructure().bridge().label;
      case TUNNEL -> "地下："+options.infrastructure().tunnel().label;
    };
  }

  private void field(String label, String key, double value, int x, int y) {
    EditBox f = new EditBox(font, x, y + 12, 128, 18, Component.literal(label));
    f.setMaxLength(18);
    f.setValue(format(value));
    f.setResponder(s -> changed());
    fields.put(key, f);
    addRenderableWidget(f);
  }

  private String format(double n) {
    return String.format(Locale.ROOT, "%.3f", n).replaceAll("0+$", "").replaceAll("\\.$", "");
  }

  private double number(String key) {
    double n = Double.parseDouble(fields.get(key).getValue());
    if (!Double.isFinite(n)) throw new IllegalArgumentException("请输入有限数值");
    return n;
  }

  private boolean capture() {
    try {
      if (!node && fields.containsKey("width")) {
        setLaneWidth(number("width"));
        thickness = number("thickness");
        tension = number("tension");
        arc = number("angle");
      }
      if (!node && fields.containsKey("lift")){
        options=options.lift(number("liftPosition")/100,number("lift"));
      }
      if(!node)options=options.sidewalk(options.sidewalk().smooth(true));
      if(fields.containsKey("bridgeRise"))options=options.infrastructure(options.infrastructure().rise(number("bridgeRise")));
      if(fields.containsKey("bridgeSpan"))options=options.infrastructure(options.infrastructure().span(number("bridgeSpan")));
      if(fields.containsKey("tunnelHeight"))options=options.infrastructure(options.infrastructure().headroom(number("tunnelHeight")));
      if(fields.containsKey("tunnelDepth"))options=options.infrastructure(options.infrastructure().depth(number("tunnelDepth")));
      if(fields.containsKey("gantrySpacing"))options=options.infrastructure(options.infrastructure().spacing(number("gantrySpacing")));
      if(fields.containsKey("lampSpacing"))options=options.streetscape(options.streetscape().lampSpacing(number("lampSpacing")));
      if(fields.containsKey("walkLampSpacing"))options=options.streetscape(options.streetscape().walkLampSpacing(number("walkLampSpacing")));
      if(fields.containsKey("plantingSpacing"))options=options.streetscape(options.streetscape().plantingSpacing(number("plantingSpacing")));
      payload.putInt("OptionsPage",optionsPage);
      return true;
    } catch (IllegalArgumentException e) {
      status = e instanceof NumberFormatException ? "请先填写完整数值" : e.getMessage();
      return false;
    }
  }

  private boolean junctionMode(){return (!payload.getString("Kind").equals("armRoad")) && (com.sora.splineroads.world.AutoJunctions.requiresJunction(payload,new Settings(mode,style,roadWidth,thickness,tension,arc).structure(structure).options(options))
      ||RoadProfile.catalog(style).type()==RoadProfile.Type.HIGHWAY&&payload.getBoolean("AutoJunction")&&!com.sora.splineroads.core.RoadTunnelFit.enabled(new Settings(mode,style,roadWidth,thickness,tension,arc).structure(structure).options(options)));}
  private Settings settings() {
    if (!capture()) throw new IllegalArgumentException("请填写完整数值");
    if(laneRoad()||payload.getString("Kind").equals("armRoad"))return new Settings(mode,style,roadWidth,thickness,tension,arc).structure(structure).options(options);
    if(junctionMode())return new Settings(mode,style,roadWidth,thickness,tension,arc).structure(structure).options(options);
    return RoadData.joinSections(
        RoadData.joinWidths(
            new Settings(mode, style, roadWidth, thickness, tension, arc)
                .structure(structure)

                .options(options),
            payload.getDouble("JoinWidthA"),
            payload.getDouble("JoinWidthB")),
        payload);
  }

  private Node editedNode() {
    BlockPos p = BlockPos.of(payload.getLong("Pos"));
    return new Node(
        new V(p.getX() + .5 + number("x"), p.getY() + number("y"), p.getZ() + .5 + number("z")),
        number("yaw"),
        number("grade") / 100);
  }

  private void changed() {
    debounce = 4;
    confirmDelete = false;
    status = "";
    ClientRoads.error = "";
  }

  private boolean refresh() {
    try {
      ClientRoads.nodePreviews = List.of();
      if (node) {
        Node n = editedNode();
        if (Math.abs(number("x")) > 8
            || Math.abs(number("z")) > 8
            || number("y") < -8
            || number("y") > 16
            || Math.abs(n.grade()) > .5) throw new IllegalArgumentException("偏移或坡度超出范围");
        payload.put("Node", RoadRecord.writeNode(n));
        ClientRoads.preview = null;
        List<Mesh> connected = new ArrayList<>();
        for (var tag : payload.getList("Attached", net.minecraft.nbt.Tag.TAG_COMPOUND)) {
          CompoundTag entry = (CompoundTag) tag;
          var record = RoadRecord.load(entry.getCompound("Road"));
          connected.add(
              RoadData.planNodeEdit(
                      record,
                      BlockPos.of(payload.getLong("Pos")),
                      n,
                      RoadRecord.readNode(payload.getCompound("OriginalNode")),
                      RoadData.readHint(entry.getCompound("AutoA")),
                      RoadData.readHint(entry.getCompound("AutoB")))
                  .mesh());
        }
        ClientRoads.nodePreviews = List.copyOf(connected);
        if (!connected.isEmpty()) ClientRoads.preview = connected.get(0);
        status = "保存时同步调整相连道路，并自动清障";
      } else {
        Settings s = settings();
        s.validate();
        payload.put("Settings", RoadRecord.writeSettings(s));
        // Keep the selected mode visible even when the automatic-junction preview returns early.
        if((pathButton!=null))pathButton.setMessage(Component.literal(mode==Mode.AUTO?"智能选线 ▸":"手动："+RoadPlanner.name(mode)+" ▸"));

        if(laneRoad()){
          var cached=ClientRoads.INDEX.roads.get(payload.getUUID("Id"));
          var old=cached==null?RoadRecord.load(payload.getCompound("Road")):cached.record;
          // Attribute preview uses the saved path; route search belongs to explicit server validation.
          ClientRoads.preview=old.settings(s).mesh();ClientRoads.nodePreviews=List.of();
          status="单向 1 车道匝道；路宽、附属设置可编辑，路径使用匝道连接器调整";
          if(pathButton!=null)pathButton.setMessage(Component.literal("线路与扩出：用匝道连接器编辑"));
          ClientRoads.storeDraft(payload);if(save!=null)save.active=!pending;return true;
        }
    if(payload.getString("Kind").equals("armRoad")){
          if(pathButton!=null)pathButton.setMessage(Component.literal("接入线形随路口衔接"));
          var spec=com.sora.splineroads.world.JunctionCodec.read(payload.getCompound("Spec"));int index=payload.getInt("Arm");
          var arms=new ArrayList<>(spec.arms());arms.set(index,com.sora.splineroads.world.JunctionRoads.change(arms.get(index),s));
          var plan=com.sora.splineroads.core.JunctionPlanner.plan(spec.arms(arms));var piece=plan.pieces().get(index);
          ClientRoads.preview=piece.mesh();ClientRoads.nodePreviews=List.of();JunctionScreen.previewMesh=piece.mesh();
          var faces=new ArrayList<com.sora.splineroads.core.RoadSurface.Face>(piece.paint());piece.structures().forEach(p->faces.addAll(p.faces()));JunctionScreen.previewFaces=List.copyOf(faces);
          status="仅更新接入路段 "+(index+1)+" · 总宽 "+format(arms.get(index).width())+" 格";ClientRoads.storeDraft(payload);if(save!=null)save.active=!pending;return true;
        }
        if(junctionMode()){
          var draft=com.sora.splineroads.world.AutoJunctions.preview(payload);
          var selected=com.sora.splineroads.world.AutoJunctions.selected(draft,payload.hasUUID("Id")?payload.getUUID("Id"):com.sora.splineroads.world.AutoJunctions.proposed(payload).id());
          ClientRoads.nodePreviews=selected.stream().map(RoadRecord::mesh).toList();
          ClientRoads.preview=ClientRoads.nodePreviews.get(0);JunctionScreen.previewMesh=ClientRoads.preview;
          var faces=new ArrayList<com.sora.splineroads.core.RoadSurface.Face>();
          var walkGroups=new java.util.HashSet<com.sora.splineroads.core.JunctionSpec>();
          for(var r:selected)if(r.junction()!=null){var piece=r.junction().get();faces.addAll(com.sora.splineroads.core.RoadSurface.custom(new com.sora.splineroads.core.RoadSurface.Geometry(List.of(),List.of()),piece.mesh(),piece.paint()).markings());piece.structures().forEach(part->faces.addAll(part.faces()));}
          JunctionScreen.previewFaces=List.copyOf(faces);status="自动路口预览："+draft.centers().size()+" 处；各方向保留自己的车道和宽度";ClientRoads.storeDraft(payload);if(save!=null)save.active=!pending;return true;
        }
        var a =
            mode == Mode.AUTO && payload.contains("AutoA")
                ? RoadData.readHint(payload.getCompound("AutoA"))
                : RoadPlanner.Hint.free(RoadRecord.readNode(payload.getCompound("StartNode")));
        var b =
            mode == Mode.AUTO && payload.contains("AutoB")
                ? RoadData.readHint(payload.getCompound("AutoB"))
                : RoadPlanner.Hint.free(RoadRecord.readNode(payload.getCompound("EndNode")));
        if (payload.getBoolean("LevelEnds")) {
          a =
              new RoadPlanner.Hint(
                  RoadData.atGround(a.node(), BlockPos.of(payload.getLong("A"))),
                  a.headingLocked(),
                  true,
                  a.linked());
          b =
              new RoadPlanner.Hint(
                  RoadData.atGround(b.node(), BlockPos.of(payload.getLong("B"))),
                  b.headingLocked(),
                  true,
                  b.linked());
        }
        if(payload.getBoolean("LiveSectionA")&&payload.contains("AutoA")){var n=RoadData.readHint(payload.getCompound("AutoA")).node();a=new RoadPlanner.Hint(new Node(n.position(),a.node().yaw(),a.node().grade()),a.headingLocked(),a.gradeLocked(),a.linked());}
        if(payload.getBoolean("LiveSectionB")&&payload.contains("AutoB")){var n=RoadData.readHint(payload.getCompound("AutoB")).node();b=new RoadPlanner.Hint(new Node(n.position(),b.node().yaw(),b.node().grade()),b.headingLocked(),b.gradeLocked(),b.linked());}
        var plan = com.sora.splineroads.core.RoadTunnelFit.plan(a, b, s);
        var seamA=payload.contains("AutoA")?RoadData.readHint(payload.getCompound("AutoA")):null;
        var seamB=payload.contains("AutoB")?RoadData.readHint(payload.getCompound("AutoB")):null;
        if(payload.getBoolean("LevelEnds")) {
          seamA=com.sora.splineroads.core.RoadConnectionChecks.atLevel(seamA,BlockPos.of(payload.getLong("A")).getY());
          seamB=com.sora.splineroads.core.RoadConnectionChecks.atLevel(seamB,BlockPos.of(payload.getLong("B")).getY());
        }
        com.sora.splineroads.core.RoadConnectionChecks.require(plan,seamA,seamB);
        int caps =
            (payload.getCompound("AutoA").getBoolean("Linked") ? 0 : 1)
                | (payload.getCompound("AutoB").getBoolean("Linked") ? 0 : 2);
        ClientRoads.preview =
            style.ramp()
                ? plan.mesh()
                : com.sora.splineroads.core.RoadGeometry.endCaps(
                    plan.mesh(), caps, plan.start().grade(), plan.end().grade());
        payload.put("Settings", RoadRecord.writeSettings(s));
        if (pathButton != null && !optionsOnly())
          pathButton.setMessage(
              Component.literal(
                  mode == Mode.AUTO
                          ? "智能识别：" + RoadPlanner.name(plan.settings().mode()) + "  ▸"
                          : "手动：" + RoadPlanner.name(mode) + "  ▸"));
        status = String.format(Locale.ROOT, "%s · %.1f 格", plan.reason(), plan.mesh().length());
        if(com.sora.splineroads.core.RoadVertical.automatic(s)){
          double da=plan.start().position().distance(a.node().position()),db=plan.end().position().distance(b.node().position());
          boolean rising=com.sora.splineroads.core.RoadVertical.rising(s);
          status=String.format(Locale.ROOT,(rising?"最高":"最低")+" Y %.2f；两端延长 %.1f / %.1f 格",rising?com.sora.splineroads.core.RoadVertical.maximum(plan.start(),plan.end(),s):com.sora.splineroads.core.RoadTunnel.minimum(plan.start(),plan.end(),s),da,db);
        }
      }
      payload.putBoolean("Advanced", advanced);
      payload.putBoolean("ExtraPage", extraPage);
      payload.putBoolean("LiftPage", liftPage);

      ClientRoads.storeDraft(payload);
      ClientRoads.error = "";
      if (save != null) save.active = !pending;
      return true;
    } catch (IllegalArgumentException e) {
      payload.putBoolean("Advanced", advanced);
      payload.putBoolean("ExtraPage", extraPage);
      payload.putBoolean("LiftPage", liftPage);

      ClientRoads.storeDraft(payload);
      ClientRoads.preview = null;
      status = e instanceof NumberFormatException ? "请输入完整数值" : e.getMessage();
      ClientRoads.error = status;
      if (save != null) save.active = false;
      if (pathButton != null)
        if (pathButton != null && !optionsOnly())
          pathButton.setMessage(
              Component.literal(
                  mode == Mode.AUTO ? "智能识别：需调整  ▸" : "手动：" + RoadPlanner.name(mode) + "  ▸"));
      return false;
    }
  }

  private void submit() {
    if (pending || !refresh()) return;
    CompoundTag t = com.sora.splineroads.net.RoadDraft.action(payload, node);

    if(payload.getString("Kind").equals("armRoad")){t=new CompoundTag();t.putString("Action","junctionRoad");t.putUUID("Id",payload.getUUID("Id"));t.putInt("Arm",payload.getInt("Arm"));t.putInt("Signature",payload.getInt("Signature"));t.put("Settings",payload.getCompound("Settings").copy());}
    if(laneRoad()){t=new CompoundTag();t.putString("Action","laneRampRoad");t.putUUID("Id",payload.getUUID("Id"));t.putInt("Signature",payload.getInt("Signature"));t.put("Settings",payload.getCompound("Settings").copy());}
    pending = true;
    save.active = false;
    status = "正在建造并清理障碍…";
    RoadNetwork.CHANNEL.sendToServer(new RoadNetwork.Action(t));
  }

  public void previewFailed(String reason) {
    pending = false;
    status = reason;
    if (save != null) save.active = false;
  }

  public void failed(String reason) {
    pending = false;
    status = reason;
    save.active = true;
  }

  @Override
  public void tick() {
    super.tick();
    fields.values().forEach(EditBox::tick);
    if (debounce > 0 && --debounce == 0) refresh();
  }

  @Override
  public boolean isPauseScreen() {
    return false;
  }

  @Override
  public void onClose() {
    refresh();
    super.onClose();
  }

  @Override
  public void render(GuiGraphics g, int mx, int my, float dt) {

    g.fill(left, top, left + 288, top + 238, 0xEC12212C);
    g.fill(left, top, left + 4, top + 238, 0xFF50DED3);
    g.drawString(
        font,
        node ? "端点微调" : "Spline Roads · 智能铺路",
        left + 12,
        top + 12,
        0xFFFFFF,
        false);
    g.drawString(
        font,
        node
            ? "普通铺路无需调整这些参数"
            : optionsOnly() ? "" : "选两点 → 自动预览 → 建造",
        left + 12,
        top + 27,
        0xA8C3CB,
        false);
    if (node) {
      label(g, "X 偏移", "x");
      label(g, "Y 偏移", "y");
      label(g, "Z 偏移", "z");
      label(g, "朝向 / 度", "yaw");
      label(g, "坡度 / %", "grade");
    } else if (!optionsOnly() && advanced) {
      label(g, "单车道宽 / 格", "width");
      label(g, "厚度 / 格", "thickness");
      label(g, "曲线强度", "tension");
      label(g, "圆弧角度 / 度", "angle");
    } else if (!optionsOnly()) {
      if (ClientRoads.preview != null)
        g.drawString(
            font,
            "Y "
                + format(ClientRoads.preview.first().center().y())
                + " → "
                + format(ClientRoads.preview.last().center().y()),
            left + 148,
            top + 121,
            0xBBD3DC,
            false);
      g.drawString(font, "自动清障 · 可在空中建造", left + 12, top + 139, 0xBBD3DC, false);
    }
    if ((!optionsOnly()&&!node)&&(payload.getInt("DegreeA")>0||payload.getInt("DegreeB")>0))
      g.drawString(font,font.plainSubstrByWidth(status,264),left+12,top+196,ClientRoads.error.isEmpty()?0xB9D4DD:0xFF9B89,false);
    else if (!optionsOnly())
      g.drawWordWrap(
          font,
          Component.literal(status),
          left + 12,
          top + 180,
          264,
          ClientRoads.error.isEmpty() ? 0xB9D4DD : 0xFF9B89);
    if (!node && (width >= 512 || optionsOnly())) {
      int sx = width >= 512 ? left + 294 : left + 12;
      if (width >= 512) g.fill(sx - 6, top, sx + 200, top + 238, 0xEC12212C);
      g.drawString(font, liftPage ? "高度控制点与自动避让" : new String[]{"基本选项","人行道","桥梁 / 跨线桥","地下通道","龙门架 / 隔音板","道路 / 人行道路灯","人行道绿化"}[optionsPage]+" · "+(optionsPage+1)+("/7"), sx, top + 27, 0xFFFFFF, false);
      if(optionsPage>=5&&!liftPage) {
        label(g,"道路路灯间距 / 8–96 格","lampSpacing");label(g,"人行道灯距 / 8–96 格","walkLampSpacing");label(g,"绿化间距 / 8–64 格","plantingSpacing");
        g.drawWordWrap(font,Component.literal(optionsPage==5?"灯型按道路断面自动选择；高速不设路灯，隧道使用洞内照明。":"绿化仅地面段生成；与灯位冲突时让位。空间不足自动拓宽人行道，盲道保留在外侧。"),sx,top+171,width>=512?194:264,0xBBD3DC);
      } else if(optionsPage>=2&&!liftPage) {
        if (optionsPage == 2) {
          label(g,"手动桥跨 / 格","bridgeSpan");
          label(g,"抬升高度 / 格","bridgeRise");
        }
        else if (optionsPage == 3) {
          label(g,"边缘净高 / 格","tunnelHeight");
          label(g,"下潜深度 / 格","tunnelDepth");
        }
        else
          label(g,"布设间距 / 格","gantrySpacing");
        String help=optionsPage==2?"小河用梁桥，跨道路用跨线桥；大河可用拱桥或斜拉桥，跨海可用悬索桥或连续梁。斜拉 / 悬索可自动按全长配跨。抬升从较高端向上量。":optionsPage==3?"两端为洞口；深度从较低洞口向下量，0 沿端点。":"隔音板用于高架，左右按 A→B。龙门架不用于匝道、隧道。";
        if(optionsPage==3&&options.infrastructure().tunnelDepth()>0){
          double low=Math.min(payload.getCompound("StartNode").getDouble("Y"),payload.getCompound("EndNode").getDouble("Y"))-options.infrastructure().tunnelDepth();
          help=String.format(Locale.ROOT,"最低路面 Y %.2f；深度从较低洞口向下量。",low);
        }
        if((optionsPage==2||optionsPage==3)&&!status.isEmpty())help=status;
        if((optionsPage==2||optionsPage==3)&&!ClientRoads.error.isEmpty())help=ClientRoads.error;
        g.drawWordWrap(font,Component.literal(help),sx,top+((optionsPage==2||optionsPage==3)?(optionsOnly()?181:166):158),width>=512?194:264,optionsPage==3&&!ClientRoads.error.isEmpty()?0xFF9B89:0xBBD3DC);
      } else if(optionsPage==1&&!liftPage) {

        g.drawWordWrap(font,Component.literal("SR 连续人行道随弯道和坡度铺设；左右按起点→终点。"),sx,top+(optionsOnly()?184:214), width>=512?194:264,0xBBD3DC);
      } else if (liftPage) {
        label(g, "沿路位置 / %", "liftPosition");
        label(g, "相对抬升 / 格", "lift");
        g.drawWordWrap(
            font,
            Component.literal("保持两端高度与接入坡度；正值上抬，负值下挖。"),
            sx,
            top + (128),
            width >= 512 ? 194 : 264,
            0xBBD3DC);
      } else {
        String note =
            (laneRoad()||style.connectorRamp())
                ? "一般箭头关闭；额外扩入保留并线导向"
                : RoadProfile.catalog(style).type() == Type.HIGHWAY
                ? "含外侧应急车道及全段护栏"
                : RoadProfile.modern(style) ? "普通路自动路灯 · 24 格间距" : "旧断面保留；切换样式升级";
        g.drawWordWrap(
            font,
            Component.literal(width >= 512 ? note : ""),
            sx,
            top + 214,
            width >= 512 ? 110 : 154,
            0xBBD3DC);
      }
    }
    super.render(g, mx, my, dt);
  }

  private double laneWidth() {
    return RoadProfile.modern(style)
        ? RoadProfile.layout(
                new Settings(Mode.AUTO, style, roadWidth, thickness, tension, arc).options(options),
                roadWidth)
            .laneWidth()
        : roadWidth;
  }

  private void setLaneWidth(double value) {
    if(laneRoad()||style.connectorRamp()){
      var sized=LaneRampAlignment.usableWidth(new Settings(Mode.AUTO,style,roadWidth,thickness,tension,arc).options(options),value);
      roadWidth=sized.width();options=sized.options();return;
    }
    roadWidth = RoadProfile.modern(style)?RoadProfile.width(style,options,value):value;
  }

  private String typeName() {
    if(laneRoad()||style.connectorRamp())return RoadProfile.highway(style)?"自由匝道（高速）":"自由匝道（普通）";
    return RoadProfile.catalog(style).type() == Type.HIGHWAY ? "高速道路" : "普通道路";
  }

  private String styleName() {
    if(laneRoad()||style.connectorRamp())return "单车道匝道";
    return RoadProfile.modern(style) ? RoadLanes.counts(style,options).label()+" ▸" : STYLES[style.ordinal()];
  }

  private void selectStyle(Style next, boolean resetWidth) {
    double lane = RoadProfile.modern(style) ? laneWidth() : 4;
    style = next;

    if(RoadProfile.catalog(style).type()==Type.HIGHWAY)payload.putBoolean("ForceJunction",false);
    if ((RoadProfile.catalog(style).type()!=Type.ORDINARY)) options=options.sidewalk(options.sidewalk().enabled(false));
    if (resetWidth) lane = RoadProfile.catalog(style).laneWidth();
    setLaneWidth(lane);
    rebuildWidgets();
  }

  private void openLaneConfiguration() {
    if(laneRoad()||style.connectorRamp())return;
    minecraft.setScreen(new RoadLaneConfigScreen(this,RoadProfile.highway(style)?Type.HIGHWAY:Type.ORDINARY,RoadLanes.counts(style,options)));
  }
  void applyLaneConfiguration(Type type,RoadLanes.Counts counts,boolean resetWidth) {
    double lane=resetWidth?(type==Type.HIGHWAY?5:4):laneWidth();
    style=RoadLanes.carrier(type,counts,RoadProfile.catalog(style).median());
    options=options.lanes(counts).ends(RoadTransitions.Ends.NONE);
    if(type==Type.HIGHWAY){payload.putBoolean("ForceJunction",false);options=options.sidewalk(options.sidewalk().enabled(false));}
    setLaneWidth(lane);changed();rebuildWidgets();
  }

  private Button option(String text, int x, int y, int w, Runnable action) {
    int buttonHeight=20;
    if((optionsOnly()&&optionsPage==0&&!liftPage)&&y>=top+40){y=top+55+(y-top-40)*5/6;buttonHeight=18;}
    return addRenderableWidget(
        Button.builder(
                Component.literal(text),
                b -> {
                  if (capture()) {
                    action.run();
                    rebuildWidgets();
                  }
                })
            .bounds(x, y, w, buttonHeight)
            .build());
  }

  private void page(int delta){

    var pages=List.of(0,1,2,3,4,5,6);
    optionsPage=pages.get(Math.floorMod(pages.indexOf(optionsPage)+delta,pages.size()));liftPage=false;
  }

  private void buildOptions(int x, int y, int w) {
    int navY=liftPage?(optionsOnly()?top+182:top+206):optionsOnly()?top+31:top+206;
    option("◀",x+w-58,navY,26,()->page(-1));
    option("▶",x+w-28,navY,26,()->page(1));

    if((optionsOnly()&&optionsPage>=1&&!liftPage))y=Math.max(y,top+55);
    if((optionsPage>=5&&!liftPage)){
      if(!com.sora.splineroads.core.RoadStreetscape.walkLampAllowed(options,structure,style)&&options.streetscape().walkLamps())options=options.streetscape(options.streetscape().walkLamps(false));
      var c=options.streetscape();boolean eligible=(RoadProfile.catalog(style).type()==Type.ORDINARY);
      if(optionsPage==5){
        field("道路路灯间距 / 8–96 格","lampSpacing",c.lampSpacing(),x,y);fields.get("lampSpacing").setWidth(w);
        option("人行道路灯："+(c.walkLamps()?"开启":com.sora.splineroads.core.RoadStreetscape.walkLampAllowed(options,structure,style)?"关闭":"关闭（当前断面禁用）"),x,y+42,w,()->options=options.streetscape(options.streetscape().walkLamps(!options.streetscape().walkLamps()))).active=eligible&&com.sora.splineroads.core.RoadStreetscape.walkLampAllowed(options,structure,style);
        field("人行道灯距 / 8–96 格","walkLampSpacing",c.walkLampSpacing(),x,y+68);fields.get("walkLampSpacing").setWidth(w);
      }else{
        option("人行道绿化："+c.planting().label+" ▸",x,y,w,()->{var values=com.sora.splineroads.core.RoadStreetscape.Planting.values();options=options.streetscape(options.streetscape().planting(values[(options.streetscape().planting().ordinal()+1)%values.length]));}).active=eligible&&options.sidewalk().enabled()&&structure!=Structure.TUNNEL;
        field("绿化间距 / 8–64 格","plantingSpacing",c.plantingSpacing(),x,y+36);fields.get("plantingSpacing").setWidth(w);
      }
      if(optionsOnly())option("返回道路设置",x+164,top+10,100,()->extraPage=false);return;
    }
    if((optionsPage>=2&&!liftPage)) {
      var infra=options.infrastructure();
      if(optionsPage==2||optionsPage==3){
        boolean bridge=optionsPage==2;int half=(w-8)/2,right=x+(w+8)/2;
        option(bridge?"启用桥梁":"启用隧道",x,y,half,()->structure=bridge?Structure.BRIDGE:Structure.TUNNEL);
        option((bridge?infra.bridge().label:infra.tunnel().label)+" ▸",right,y,half,()->{
          options=options.infrastructure(bridge?infra.bridge(RoadInfrastructure.Bridge.values()[(infra.bridge().ordinal()+1)%RoadInfrastructure.Bridge.values().length]):infra.tunnel(RoadInfrastructure.Tunnel.values()[(infra.tunnel().ordinal()+1)%2]));
          structure=bridge?Structure.BRIDGE:Structure.TUNNEL;
        });
        String upper=bridge?"bridgeSpan":"tunnelHeight",lower=bridge?"bridgeRise":"tunnelDepth";
        field(bridge?"手动桥跨 / 格":"边缘净高 / 格",upper,bridge?infra.span():infra.headroom(),x,y+32);fields.get(upper).setWidth(half);
        field(bridge?"抬升高度 / 格":"下潜深度 / 格",lower,bridge?infra.bridgeRise():infra.tunnelDepth(),x,y+66);fields.get(lower).setWidth(half);
        if(bridge){
          option("配跨："+(infra.autoSpan()?"自动":"手动"),right,y+44,half,()->options=options.infrastructure(options.infrastructure().autoSpan(!infra.autoSpan())))
              .active=infra.bridge()==RoadInfrastructure.Bridge.CABLE||infra.bridge()==RoadInfrastructure.Bridge.SUSPENSION;
        }else option("下潜："+(infra.tunnelDepth()>0?"开":"关"),right,y+44,half,()->{
          options=options.lift(.5,0).infrastructure(options.infrastructure().depth(infra.tunnelDepth()>0?0:8));structure=Structure.TUNNEL;
        });
        option("端点："+infra.adjustment().label,right,y+78,half,()->{
          var modes=com.sora.splineroads.core.RoadTunnelFit.Adjustment.values();
          options=options.infrastructure(options.infrastructure().adjustment(modes[(infra.adjustment().ordinal()+1)%modes.length]));
        });
        option(infra.maxGrade()==0?"最大坡度：旧版 ▸":String.format(Locale.ROOT,"最大坡度：%.0f%% ▸",infra.maxGrade()*100),x,y+102,w,()->{
          double grade=infra.maxGrade(),next=grade==0?.06:grade<.04?.04:grade<.06?.06:grade<.08?.08:grade<.10?.10:grade<.12?.12:.04;
          options=options.infrastructure(options.infrastructure().grade(next));
        });
      }else{
        option("龙门架："+infra.gantry().label+" ▸",x,y,w,()->options=options.infrastructure(infra.gantry(RoadInfrastructure.Gantry.values()[(infra.gantry().ordinal()+1)%4])));
        field("布设间距 / 格","gantrySpacing",infra.spacing(),x,y+32);
        option("高架外侧："+options.outerRail().label+" ▸",x,y+70,w,()->options=options.outerRail(OuterRail.values()[(options.outerRail().ordinal()+1)%OuterRail.values().length])).active=(structure!=Structure.TUNNEL);
      }
      if(optionsOnly())option("返回道路设置",x+164,top+10,100,()->extraPage=false);
      return;
    }
    if((optionsPage==1&&!liftPage)) {
      boolean eligible=(structure!=Structure.TUNNEL)&&RoadProfile.catalog(style).type()==Type.ORDINARY;
      var walk=options.sidewalk();
      option((walk.enabled()?"☑":"☐")+" 智能人行道",x,y,w,()->options=options.sidewalk(options.sidewalk().enabled(!options.sidewalk().enabled()))).active=eligible;
      option("铺设位置："+walk.side().label,x,y+24,w,()->options=options.sidewalk(options.sidewalk().side(com.sora.splineroads.core.RoadSidewalks.Side.values()[(options.sidewalk().side().ordinal()+1)%3]))).active=eligible;
      option("−",x,y+48,26,()->options=options.sidewalk(options.sidewalk().width(Math.max(1,options.sidewalk().width()-1)))).active=eligible;
      option("宽度 "+walk.width()+" 格",x+30,y+48,w-60,()->{}).active=false;
      option("+",x+w-26,y+48,26,()->options=options.sidewalk(options.sidewalk().width(Math.min(15,options.sidewalk().width()+1)))).active=eligible;
      option("材质："+com.sora.splineroads.core.RoadSidewalks.Finish.of(walk.material()).label+" ▸",x,y+76,w,()->{
        var values=com.sora.splineroads.core.RoadSidewalks.Finish.values();options=options.sidewalk(options.sidewalk().material(values[(com.sora.splineroads.core.RoadSidewalks.Finish.of(options.sidewalk().material()).ordinal()+1)%values.length].id));
      }).active=eligible;
      option(walk.width()>=2?"盲道："+(walk.tactile()?"开":"关"):"盲道：宽度需至少 2 格",x,y+104,w,()->options=options.sidewalk(options.sidewalk().tactile(!options.sidewalk().tactile()))).active=eligible&&walk.enabled()&&walk.width()>=2;
      if(optionsOnly())option("返回道路设置",x+164,top+10,100,()->extraPage=false);
      return;
    }

    if (liftPage) {
      field("沿路位置 / %", "liftPosition", options.liftPosition() * 100, x, y + 4);
      fields.get("liftPosition").setWidth((w - 8) / 2);
      field("相对抬升 / 格", "lift", options.liftHeight(), x + (w + 8) / 2, y + 4);
      fields.get("lift").setWidth((w - 8) / 2);
      option(
          "降低 1 格",
          x,
          y + 40,
          (w - 8) / 2,
          () ->
              options =
                  options.lift(options.liftPosition(), Math.max(-64, options.liftHeight() - 1)));
      option(
          "抬高 1 格",
          x + (w + 8) / 2,
          y + 40,
          (w - 8) / 2,
          () ->
              options =
                  options.lift(options.liftPosition(), Math.min(64, options.liftHeight() + 1)));
      option("重置控制点", x, y + 64, w, () -> options = options.lift(.5, 0));
      option("返回额外选项", x, y + 126, w, () -> liftPage = false);
      if (optionsOnly()) option("返回道路设置", x, y + 150, w, () -> extraPage = false);
      return;
    }
    option(
        (options.leftTraffic() ? "☑" : "☐") + " 靠左行驶（默认靠右）",
        x,
        y,
        w,
        () -> options = options.traffic(!options.leftTraffic()));
    var c = RoadProfile.catalog(style);
    boolean ordinary = c.type() == Type.ORDINARY;
    {
      option(
                  RoadProfile.medianName(c.median()) + " ▸",
                  x,
                  y + 24,
                  w,
                  () -> {
                    if (!c.twoWay() || c.type() == Type.LEGACY) return;
                    List<Median> choices =
                        c.type() == Type.HIGHWAY
                            ? List.of(Median.RAIL, Median.GREEN)
                            : c.lanes() == 2
                                ? List.of(
                                    Median.DOUBLE_YELLOW,
                                    Median.DASHED_YELLOW,
                                    Median.RAIL,
                                    Median.GREEN)
                                : List.of(Median.DOUBLE_YELLOW, Median.RAIL, Median.GREEN);
                    Median m = choices.get((choices.indexOf(c.median()) + 1) % choices.size());
                    double lane = laneWidth();
                    style = RoadLanes.carrier(c.type(),RoadLanes.counts(style,options),m);
                    setLaneWidth(lane);
                  })
              .active =
          c.twoWay() && c.type() != Type.LEGACY;
      option(
                  options.cycleFinish().label,
                  x,
                  y + 48,
                  w,
                  () -> {
                    double lane = laneWidth();
                    options = options.cycleFinish(Options.CycleFinish.values()[(options.cycleFinish().ordinal()+1)%Options.CycleFinish.values().length]);
                    setLaneWidth(lane);
                  })
              .active =
          ordinary;
      option(
                  "非机动车分隔：" + options.streetscape().separator().label,
                  x,
                  y + 72,
                  w,
                  () -> {double lane=laneWidth();options=options.streetscape(options.streetscape().separator(com.sora.splineroads.core.RoadStreetscape.Separator.values()[(options.streetscape().separator().ordinal()+1)%3]));setLaneWidth(lane);})
              .active =
          ordinary && options.cycle()&&!options.streetscape().parking();
      option(
                  (options.curb() ? "☑" : "☐") + " 最外侧马路牙子",
                  x,
                  y + 96,
                  w,
                  () -> {
                    double lane = laneWidth();
                    options = options.extras(options.cycle(), options.cycleRail(), !options.curb());
                    setLaneWidth(lane);
                  })
              .active =
          ordinary;
    }
    option("中间控制点："+format(options.liftHeight())+" 格 ▸",x,y+120,w,()->liftPage=true);
    option(
          (options.routing().fitEdges() ? "☑" : "☐") + " 直路边缘对齐方块",
          x,
          y + 144,
          w,
          () -> options = options.route(options.routing().fit(!options.routing().fitEdges())));
    if (optionsOnly()) option("返回道路设置", x + 164, top + 10, 100, () -> extraPage = false);
  }

  private void label(GuiGraphics g, String text, String key) {
    EditBox f = fields.get(key);
    if (f == null) return;
    g.drawString(font, text, f.getX(), f.getY() - 11, 0xBBD3DC, false);
  }

  private void plan(GuiGraphics g, int x, int y, int w, int h) {
    var mesh = ClientRoads.preview;
    g.fill(x, y, x + w, y + h, 0xD9112029);
    g.drawString(font, "俯视路径", x + 8, y + 8, 0xFFFFFF, false);
    double dx = Math.max(1, mesh.max().x() - mesh.min().x()),
        dz = Math.max(1, mesh.max().z() - mesh.min().z()),
        scale = Math.min((w - 24) / dx, (h - 44) / dz);
    for (var p : mesh.samples()) {
      int px = x + 12 + (int) ((p.center().x() - mesh.min().x()) * scale),
          py = y + 30 + (int) ((p.center().z() - mesh.min().z()) * scale);
      g.fill(px, py, px + 2, py + 2, 0xFF5FE3D5);
    }
    g.drawString(font, "完整路面在世界中同步预览", x + 8, y + h - 12, 0xBBD3DC, false);
  }
}
