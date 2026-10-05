package com.sora.splineroads.client;

import com.sora.splineroads.core.*;
import com.sora.splineroads.core.RoadGeometry.*;
import com.sora.splineroads.net.RoadNetwork;
import com.sora.splineroads.world.*;
import java.util.*;
import java.util.concurrent.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.*;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.nbt.*;
import net.minecraft.network.chat.Component;

public final class InterchangeScreen extends Screen {
  private static final Map<Integer, CompoundTag> drafts = new HashMap<>();
  private static CompoundTag draft;
  private static final ExecutorService WORKER =
      Executors.newSingleThreadExecutor(
          r -> {
            Thread t = new Thread(r, "SplineRoads interchange preview");
            t.setDaemon(true);
            return t;
          });
  private static long generation;
  private final CompoundTag payload;
  private final Map<String, EditBox> fields = new LinkedHashMap<>();
  private InterchangePlanner.Options options;
  private InterchangePlanner.Plan plan;
  private Button save, preview, heights;
  private int left, top, panel, panelHeight, control, delay, page, axis = 1;
  private String deleteConfirmation = "";
  private boolean pending, initializing, computing, needsPlan;
  private String status = "正在计算整座立交…";

  public static void clear() {
    drafts.clear();
    draft = null;
    generation++;
    ClientRoads.nodePreviews = List.of();
  }

  public static void open(CompoundTag fresh) {
    var mc = Minecraft.getInstance();
    int arms =
        fresh.contains("GeneratorArms")
            ? fresh.getInt("GeneratorArms")
            : fresh.getLongArray("Points").length;
    draft = drafts.get(arms);
    String kind = fresh.getString("Kind");
    if (kind.equals("interchangeReset")) {
      drafts.remove(arms);
      draft = null;
      generation++;
      ClientRoads.nodePreviews = List.of();
      ClientRoads.preview = null;
      return;
    }
    if (kind.equals("interchangeResume")) {
      if (draft != null) mc.setScreen(new InterchangeScreen(draft));
      else if (mc.player != null)
        mc.player.displayClientMessage(
            Component.literal(arms == 3 ? "先选 A/B 主路两端，再选 C 支路端点" : arms == 4 ? "依次选择 A/B 与 C/D 两条主路端点" : arms == 5 ? "依次选择 A/B、C/D 两条主路与 E 支路端点" : "依次选择 A/B、C/D、E/F 三条主路端点"), true);
      return;
    }
    CompoundTag merged = fresh.copy();
    if (draft != null
        && Arrays.equals(draft.getLongArray("Points"), fresh.getLongArray("Points"))
        && draft.hasUUID("Id") == fresh.hasUUID("Id")
        && (!draft.hasUUID("Id") || draft.getUUID("Id").equals(fresh.getUUID("Id"))))
      for (String key : List.of("Options", "Main1", "Main2", "Main3"))
        merged.put(key, draft.getCompound(key).copy());
    draft = merged.copy();
    drafts.put(arms, draft);
    mc.setScreen(new InterchangeScreen(merged));
  }

  public InterchangeScreen(CompoundTag payload) {
    super(Component.literal("Spline Roads · 自动立交"));
    this.payload = payload.copy();
    for (String key : payload.getLongArray("Points").length>=5?List.of("Main1", "Main2", "Main3"):List.of("Main1", "Main2"))
      this.payload.put(
          key,
          RoadRecord.writeSettings(
              RoadProfile.editable(RoadRecord.readSettings(this.payload.getCompound(key)))));
    options = Interchanges.read(payload.getCompound("Options"));
    if (payload.getBoolean("Partial")) status = "立交已有路段被单独删除；整体更新将重新生成完整布局";
    if (!com.sora.splineroads.world.HostAxes.presets(payload)
        .contains(options.preset())) {
      options =
          changed(
              com.sora.splineroads.world.HostAxes.presets(payload).get(0),
              options.leftTraffic(),
              options.lanes(),
              options.rampWidth(),
              options.adjustEndpoints());
      status = HostAxes.retiredInterchange(payload)?HostAxes.RETIRED_MESSAGE:"旧布局已停用；更新时将使用当前预设，也可仅删除匝道保留主路";
    }
  }

  private void button(String label, int x, int y, int w, Runnable click) {
    addRenderableWidget(
        Button.builder(
                Component.literal(label),
                b -> {
                  if (capture()) {
                    try {
                      click.run();
                      plan = null;
                      stash();
                      rebuildWidgets();
                    } catch (IllegalArgumentException e) {
                      status = e.getMessage();
                    }
                  }
                })
            .bounds(x, y, w, 20)
            .build());
  }

  private void tab(String text, int x, int y, int w, int target) {
    addRenderableWidget(
        Button.builder(
                Component.literal((page == target ? "• " : "") + text),
                b -> {
                  if (!capture()) return;
                  persist();
                  page = target;
                  rebuildWidgets();
                })
            .bounds(x, y, w, 20)
            .build());
  }

  private InterchangePlanner.Options changed(
      InterchangePlanner.Preset preset,
      boolean leftTraffic,
      int lanes,
      double width,
      boolean adjust) {
    return new InterchangePlanner.Options(
        preset,
        leftTraffic,
        lanes,
        options.transition(),
        options.radius(),
        options.clearance(),
        options.upper(),
        width,
        adjust,
        options.allowShrink(),
        options.maxLowering());
  }

  private Settings mainSettings() {
    return RoadRecord.readSettings(payload.getCompound("Main" + axis));
  }

  private void mainSettings(Settings value) {
    value.validate();
    payload.put("Main" + axis, RoadRecord.writeSettings(value));
  }

  private void changeExtras(RoadProfile.Options options) {
    var old = mainSettings();
    double lane = RoadProfile.layout(old, old.width()).laneWidth();
    mainSettings(
        new Settings(
                Mode.STRAIGHT,
                old.style(),
                RoadProfile.width(old.style(), options, lane),
                old.thickness(),
                .4,
                90)
            .options(options));
  }

  private void changeStyle(Style next) {
    var old = mainSettings();
    double lane = RoadProfile.layout(old, old.width()).laneWidth();
    mainSettings(
        new Settings(
                Mode.STRAIGHT,
                next,
                RoadProfile.width(next, old.options(), lane),
                old.thickness(),
                .4,
                90)
            .options(old.options()));
  }

  @Override
  protected void init() {
    initializing = true;
    fields.clear();
    panel = Math.min(700, width - 16);
    left = (width - panel) / 2;
    panelHeight = Math.min(316, height - 8);
    top = Math.max(4, (height - panelHeight) / 2);
    control = panel >= 550 ? 306 : panel - 24;
    int x = left + 12, y = top + 26, w = control, half = (w - 8) / 2, third = (w - 8) / 3;
    if(HostAxes.retiredInterchange(payload)){
      status=HostAxes.RETIRED_MESSAGE;needsPlan=false;initializing=false;page=0;
      save=addRenderableWidget(Button.builder(Component.literal("曲线立交建造已撤下"),b->{}).bounds(x,y,w,20).build());save.active=false;
      preview=save;
      addRenderableWidget(Button.builder(Component.literal("关闭"),b->onClose()).bounds(x,y+28,w,20).build());
      if(payload.hasUUID("Id"))for(int i=0;i<2;i++){
        String action=i==0?"interchangeDeleteRamps":"interchangeDelete";String label=i==0?"仅删匝道，保留主路":"删除整座";
        addRenderableWidget(Button.builder(Component.literal(label),b->{if(!action.equals(deleteConfirmation)){deleteConfirmation=action;b.setMessage(Component.literal("确认："+label));}else submit(action);}).bounds(x,y+56+i*28,w,20).build());
      }
      return;
    }
    tab("布局 / 匝道", x, y, third, 0);
    tab("主路样式", x + third + 4, y, third, 1);
    tab("端点调整", x + 2 * (third + 4), y, w - 2 * (third + 4), 2);
    y += 26;
    if (page == 0) {
      button(
          options.preset().label + (payload.getLongArray("Points").length>=5?"":" ▸"),
          x,
          y,
          w,
          () -> {
            var choices =
                new ArrayList<>(com.sora.splineroads.world.HostAxes.presets(payload));
            options =
                changed(
                    choices.get((choices.indexOf(options.preset()) + 1) % choices.size()),
                    options.leftTraffic(),
                    options.lanes(),
                    options.rampWidth(),
                    options.adjustEndpoints());
          });
      button(
          options.leftTraffic() ? "靠左行驶" : "靠右行驶",
          x,
          y + 24,
          half,
          () ->
              options =
                  changed(
                      options.preset(),
                      !options.leftTraffic(),
                      options.lanes(),
                      options.rampWidth(),
                      options.adjustEndpoints()));
      var nodes = payload.getList("Nodes", Tag.TAG_COMPOUND);
      double ab =
          (RoadRecord.readNode(nodes.getCompound(0)).position().y()
                  + RoadRecord.readNode(nodes.getCompound(1)).position().y())
              / 2;
      double cd =
          (RoadRecord.readNode(nodes.getCompound(2)).position().y()
                  + RoadRecord.readNode(nodes.getCompound(nodes.size() == 3 ? 2 : 3))
                      .position()
                      .y())
              / 2;
      double minimum =
          InterchangePlanner.minimumDifference(
              RoadRecord.readSettings(payload.getCompound("Main1")),
              RoadRecord.readSettings(payload.getCompound("Main2")),
              com.sora.splineroads.world.HostAxes.curved(payload)?CurvedRoadPlans.planningOptions(options):options);
      heights =
          addRenderableWidget(
              Button.builder(
                      Component.literal(
                          String.format(
                              Locale.ROOT, payload.getLongArray("Points").length>=5?"分层升降 · 自动拟合":"原高差 %.0f / 最低 %.0f", Math.abs(ab - cd), minimum)),
                      b -> {})
                  .bounds(x + half + 8, y + 24, half, 20)
                  .build());
      heights.active = false;
      updateHeightLabel();
      button(
          options.lanes() + " 车道匝道 ▸",
          x,
          y + 58,
          half,
          () -> {
            int lanes = 3 - options.lanes();
            double oldDefault = options.lanes() == 1 ? 5 : 9;
            double value =
                Math.abs(options.width() - oldDefault) < 1e-7
                    ? 0
                    : Math.max(lanes * 4, options.width() * lanes / options.lanes());
            options =
                changed(
                    options.preset(),
                    options.leftTraffic(),
                    lanes,
                    value,
                    options.adjustEndpoints());
          });
      field("匝道总宽 / 格", "rampWidth", options.width(), x + half + 8, y + 48, half);
      if (panelHeight < 280) {
        field("过渡 / 格", "transition", options.transition(), x, y + 82, third);
        field("半径 / 格", "radius", options.radius(), x + third + 4, y + 82, third);
        field("净高 / 格", "clearance", options.clearance(), x + 2 * (third + 4), y + 82, third);
        button(
            "调整端点：" + (options.adjustEndpoints() ? "允许" : "关闭"),
            x,
            y + 114,
            w,
            () -> options = options.adjust(!options.adjustEndpoints()));
      } else {
        field("过渡 / 格", "transition", options.transition(), x, y + 80, half);
        field("环绕半径 / 格", "radius", options.radius(), x + half + 8, y + 80, half);
        field("交叉净高 / 格", "clearance", options.clearance(), x, y + 112, half);
        button(
            "调整端点：" + (options.adjustEndpoints() ? "允许" : "关闭"),
            x + half + 8,
            y + 122,
            half,
            () -> options = options.adjust(!options.adjustEndpoints()));
      }
    } else if (page == 1) {
      if(payload.getLongArray("Points").length>=5){
        button((axis==1?"• ":"")+"AB 主路",x,y,third,()->axis=1);
        button((axis==2?"• ":"")+"CD 主路",x+third+4,y,third,()->axis=2);
        button((axis==3?"• ":"")+(payload.getLongArray("Points").length==5?"E 支路":"EF 主路"),x+2*(third+4),y,w-2*(third+4),()->axis=3);
      }else{
        button((axis == 1 ? "• " : "") + "AB 主路", x, y, half, () -> axis = 1);
        button((axis == 2 ? "• " : "") + "CD 主路", x + half + 8, y, half, () -> axis = 2);
      }
      var road = mainSettings();
      var catalog = RoadProfile.catalog(road.style());
      button(
          catalog.type() == RoadProfile.Type.HIGHWAY ? "高速道路 ▸" : "普通道路 ▸",
          x,
          y + 24,
          half,
          () -> {
            var type =
                catalog.type() == RoadProfile.Type.HIGHWAY
                    ? RoadProfile.Type.ORDINARY
                    : RoadProfile.Type.HIGHWAY;
            changeStyle(
                RoadProfile.choose(
                    type,
                    Math.max(type == RoadProfile.Type.HIGHWAY ? 4 : 2, catalog.lanes()),
                    true,
                    (catalog.median() == RoadProfile.Median.DASHED_YELLOW
                            || catalog.median() == RoadProfile.Median.NONE)
                        ? RoadProfile.Median.DOUBLE_YELLOW
                        : catalog.median(),
                    type == RoadProfile.Type.HIGHWAY));
          });
      button(
          catalog.lanes() + " 车道 ▸",
          x + half + 8,
          y + 24,
          half,
          () -> {
            int lanes =
                catalog.lanes() == 6
                    ? (catalog.type() == RoadProfile.Type.HIGHWAY ? 4 : 2)
                    : catalog.lanes() + 2;
            changeStyle(
                RoadProfile.choose(
                    catalog.type() == RoadProfile.Type.LEGACY
                        ? RoadProfile.Type.ORDINARY
                        : catalog.type(),
                    lanes,
                    true,
                    (catalog.median() == RoadProfile.Median.DASHED_YELLOW
                            || catalog.median() == RoadProfile.Median.NONE)
                        ? RoadProfile.Median.DOUBLE_YELLOW
                        : catalog.median(),
                    catalog.shoulder()));
          });
      button(
          RoadProfile.medianName(catalog.median()) + " ▸",
          x,
          y + 48,
          w,
          () -> {
            var medians =
                catalog.type() == RoadProfile.Type.HIGHWAY
                    ? List.of(RoadProfile.Median.RAIL, RoadProfile.Median.GREEN)
                    : List.of(
                        RoadProfile.Median.DOUBLE_YELLOW,
                        RoadProfile.Median.RAIL,
                        RoadProfile.Median.GREEN);
            changeStyle(
                RoadProfile.choose(
                    catalog.type() == RoadProfile.Type.LEGACY
                        ? RoadProfile.Type.ORDINARY
                        : catalog.type(),
                    catalog.lanes(),
                    true,
                    medians.get((medians.indexOf(catalog.median()) + 1) % medians.size()),
                    catalog.shoulder()));
          });
      field("主路总宽 / 格", "mainWidth", road.width(), x, y + 72, half);
      field("路板厚度 / 格", "thickness", road.thickness(), x + half + 8, y + 72, half);
      button(
          "外侧护栏：" + road.options().outerRail().label + " ▸",
          x,
          y + 106,
          w,
          () ->
              mainSettings(
                  mainSettings()
                      .options(
                          mainSettings()
                              .options()
                              .outerRail(
                                  RoadProfile.OuterRail.values()[
                                      (road.options().outerRail().ordinal() + 1) % RoadProfile.OuterRail.values().length]))));
      if (catalog.type() == RoadProfile.Type.ORDINARY && panelHeight >= 280) {
        button(
            "非机动车道：" + (road.options().cycle() ? "开" : "关"),
            x,
            y + 130,
            half,
            () -> {
              var o = mainSettings().options();
              changeExtras(o.extras(!o.cycle(), o.cycleRail(), o.curb()));
            });
        button(
            "路缘石：" + (road.options().curb() ? "开" : "关"),
            x + half + 8,
            y + 130,
            half,
            () -> {
              var o = mainSettings().options();
              changeExtras(o.extras(o.cycle(), o.cycleRail(), !o.curb()));
            });
      }
    } else if (page == 2) {
      button(
          "调整端点：" + (options.adjustEndpoints() ? "允许" : "关闭"),
          x,
          y,
          half,
          () -> options = options.adjust(!options.adjustEndpoints()));
      button(
          "允许缩小：" + (options.allowShrink() ? "开" : "关"),
          x + half + 8,
          y,
          half,
          () -> options = options.shrink(!options.allowShrink()));
      var lowering = addRenderableWidget(new AbstractSliderButton(
          x, y + 26, w, 20, Component.empty(), options.maxLowering() / 64) {
        { updateMessage(); }
        @Override protected void updateMessage() {
          setMessage(Component.literal("高度最多降低：" + Math.round(value * 64) + " 格"));
        }
        @Override protected void applyValue() {
          options = options.lowering(Math.round(value * 64));
          plan = null;
          delay = 8;
          needsPlan = true;
          save.active = false;
          preview.active = false;
          stash();
        }
      });
      lowering.active = options.allowShrink() && options.adjustEndpoints();
    }
    int footer = top + panelHeight - 26, n = payload.hasUUID("Id") ? 5 : 3;
    int bw = (panel - 24 - 8 * (n - 1)) / n;
    save =
        addRenderableWidget(
            Button.builder(
                    Component.literal(payload.hasUUID("Id") ? "整体更新" : "整体建造"),
                    b -> submit("interchange"))
                .bounds(x, footer, bw, 20)
                .build());
    preview =
        addRenderableWidget(
            Button.builder(
                    Component.literal("实景预览"),
                    b -> {
                      persist();
                      minecraft.setScreen(null);
                    })
                .bounds(x + bw + 8, footer, bw, 20)
                .build());
    addRenderableWidget(
        Button.builder(Component.literal("关闭"), b -> onClose())
            .bounds(x + (bw + 8) * 2, footer, bw, 20)
            .build());
    if (n == 5) {
      for (int index = 0; index < 2; index++) {
        final boolean rampsOnly = index == 0;
        addRenderableWidget(
            Button.builder(
                    Component.literal(rampsOnly ? "仅删匝道" : "删除整座"),
                    b -> {
                      String action = rampsOnly ? "interchangeDeleteRamps" : "interchangeDelete";
                      if (!action.equals(deleteConfirmation)) {
                        deleteConfirmation = action;
                        b.setMessage(Component.literal(rampsOnly ? "确认删匝道" : "确认删整座"));
                        status = rampsOnly ? "保留贯通主路；三/五向末端支路一并删除，仅保留端点" : "删除全部路段和设施，保留端点";
                      } else submit(action);
                    })
                .bounds(x + (bw + 8) * (3 + index), footer, bw, 20)
                .build());
      }
    }
    save.active = plan != null && !pending && !needsPlan && !computing;
    preview.active = plan != null && !needsPlan && !computing;
    initializing = false;
    delay = 1;
    needsPlan |= plan == null;
  }

  private void field(String label, String key, double value, int x, int y, int w) {
    var f = new EditBox(font, x, y + 10, w, 18, Component.literal(label));
    f.setMaxLength(8);
    f.setValue(String.format(Locale.ROOT, "%.1f", value));
    f.setResponder(
        v -> {
          if (!initializing) {
            delay = 8;
            needsPlan = true;
            save.active = false;
            preview.active = false;
          }
        });
    fields.put(key, f);
    addRenderableWidget(f);
  }

  private double number(String key, double fallback) {
    return fields.containsKey(key) ? Double.parseDouble(fields.get(key).getValue()) : fallback;
  }

  private boolean capture() {
    try {
      options =
          new InterchangePlanner.Options(
              options.preset(),
              options.leftTraffic(),
              options.lanes(),
              number("transition", options.transition()),
              number("radius", options.radius()),
              number("clearance", options.clearance()),
              options.upper(),
              number("rampWidth", options.rampWidth()),
              options.adjustEndpoints(),
              options.allowShrink(),
              options.maxLowering());
      if (fields.containsKey("mainWidth")) {
        var old = mainSettings();
        mainSettings(
            new Settings(
                    Mode.STRAIGHT,
                    old.style(),
                    number("mainWidth", old.width()),
                    number("thickness", old.thickness()),
                    .4,
                    90)
                .options(old.options()));
      }
      return true;
    } catch (IllegalArgumentException e) {
      status = e instanceof NumberFormatException ? "请输入有效数字" : e.getMessage();
      return false;
    }
  }

  private void stash() {
    payload.put("Options", Interchanges.write(options));
    draft = payload.copy();
    drafts.put(payload.getLongArray("Points").length, draft);
  }

  private void persist() {
    if (capture()) stash();
  }

  @Override
  public void tick() {
    super.tick();
    if (delay > 0) delay--;
    if (delay == 0 && needsPlan && !computing) calculate();
  }

  private void calculate() {
    if(HostAxes.retiredInterchange(payload)){needsPlan=false;status=HostAxes.RETIRED_MESSAGE;return;}
    if (!capture()) {
      needsPlan = false;
      return;
    }
    persist();
    plan = null;
    save.active = false;
    preview.active = false;
    status = "正在检查全部转向、15% 纵坡与交叉净高…";
    computing = true;
    needsPlan = false;
    long token = ++generation;
    CompoundTag inputs = payload.copy();
    CompletableFuture.supplyAsync(() -> Interchanges.plan(inputs), WORKER)
        .whenComplete(
            (result, error) ->
                Minecraft.getInstance()
                    .execute(
                        () -> {
                          computing = false;
                          if (token != generation || draft == null) return;
                          if (needsPlan) {
                            delay = 1;
                            return;
                          }
                          if (error != null) {
                            Throwable cause = error.getCause() == null ? error : error.getCause();
                            status = cause.getMessage() == null ? "预设无法放入当前范围" : cause.getMessage();
                            ClientRoads.preview = null;
                            ClientRoads.nodePreviews = List.of();
                            return;
                          }
                          plan = result;
                          updateHeightLabel();
                          ClientRoads.nodePreviews =
                              result.legs().stream()
                                  .map(l -> RoadRenderMesh.simplify(l.mesh()))
                                  .toList();
                          ClientRoads.preview = ClientRoads.nodePreviews.get(0);
                          status =
                              result.movements()
                                  + " 条转向 · 最小半径 "
                                  + Math.round(result.minRadius())
                                  + " 格 · 最大坡度 "
                                  + String.format(
                                      java.util.Locale.ROOT,
                                      "%.1f%%",
                                      result.legs().stream()
                                              .mapToDouble(l -> RoadGrades.maximum(l.mesh()))
                                              .max()
                                              .orElse(0)
                                          * 100)
                                  + " · 最高 Y="
                                  + Math.round(result.highest())
                                  + (switch (options.preset()) {
                                    case DIAMOND, PARCLO -> " · C/D 侧为平面路口";
                                    case SPUI -> " · 中心为平面路口";
                                    case ROUNDABOUT -> " · 双主路直通 / 独立中层环道";
                                    case STACK -> " · 四层堆栈";
                                    default -> " · 分层匝道";
                                  });
                          if (payload.getBoolean("Partial")) status += " · 更新将补回已删除路段";
                          if (adjusted()) status += " · 端点将调整，见端点页";
                          save.active = !pending;
                          preview.active = true;
                        }));
  }

  private void updateHeightLabel(){
    if(heights==null||plan==null||plan.anchors().size()<3||plan.anchors().size()>4)return;
    var old=payload.getList("Nodes",Tag.TAG_COMPOUND);var fitted=plan.anchors();
    double before=Math.abs(RoadRecord.readNode(old.getCompound(0)).position().y()-RoadRecord.readNode(old.getCompound(2)).position().y());
    double after=Math.abs(fitted.get(0).position().y()-fitted.get(2).position().y());
    heights.setMessage(Component.literal(String.format(Locale.ROOT,"高差 %.0f → %.0f（已拟合）",before,after)));
  }

  private void submit(String action) {
    boolean deletion = !action.equals("interchange");
    if (pending || !deletion && (plan == null || needsPlan || computing)) return;
    persist();
    CompoundTag t = new CompoundTag();
    for (String key : List.of("Id", "Points", "Options", "Main1", "Main2", "Main3"))
      if (payload.contains(key)) t.put(key, payload.get(key).copy());
    t.putString("Action", action);
    pending = true;
    save.active = false;
    t.putInt("SourceAxesHash",com.sora.splineroads.world.HostAxes.signature(payload));
    t.put("SourceNodes", payload.getList("Nodes", Tag.TAG_COMPOUND).copy());
    if (plan != null) {
      ListTag targets = new ListTag();
      plan.anchors().forEach(node -> targets.add(RoadRecord.writeNode(node)));
      t.put("FittedNodes", targets);
    }
    status =
        deletion
            ? "正在删除并恢复地形…"
            : minecraft.hasSingleplayerServer() ? "正在检查地形并建造立交…" : "正在请求服务器检查并建造立交…";
    RoadNetwork.CHANNEL.sendToServer(new RoadNetwork.Action(t));
  }

  public void failed(String reason) {
    pending = false;
    status = reason;
    save.active = plan != null&&!HostAxes.retiredInterchange(payload);
  }

  @Override
  public boolean isPauseScreen() {
    return false;
  }

  @Override
  public void onClose() {
    generation++;
    persist();
    ClientRoads.preview = null;
    ClientRoads.nodePreviews = List.of();
    super.onClose();
  }

  private boolean adjusted() {
    if (plan == null) return false;
    var old = payload.getList("Nodes", Tag.TAG_COMPOUND);
    for (int i = 0; i < old.size(); i++)
      if (RoadRecord.readNode(old.getCompound(i))
              .position()
              .distance(plan.anchors().get(i).position())
          > 1e-6) return true;
    return false;
  }

  @Override
  public void render(GuiGraphics g, int mouseX, int mouseY, float partial) {
    renderBackground(g);
    g.fill(left, top, left + panel, top + panelHeight, 0xEF182530);
    g.drawString(font, title, left + 12, top + 10, 0xFFFFFF, false);
    super.render(g, mouseX, mouseY, partial);
    for (var f : fields.values())
      g.drawString(font, f.getMessage(), f.getX(), f.getY() - 9, 0xC8DAE5, false);
    if (page == 2) {
      var old = payload.getList("Nodes", Tag.TAG_COMPOUND);
      g.drawString(
          font,
          options.adjustEndpoints()
              ? (options.allowShrink() ? "自动采用搜索到的最小可行范围" : "原布点不可行时向外调整")
              : "自动调整已关闭",
          left + 12,
          top + 105,
          0xFFDC91,
          false);
      int y = top + 117;
      for (int i = 0; i < old.size(); i++) {
        V before = RoadRecord.readNode(old.getCompound(i)).position();
        V after = plan == null ? before : plan.anchors().get(i).position();
        g.drawString(
            font,
            String.format(
                Locale.ROOT, "%c 原：%.1f, %.1f, %.1f", 'A' + i, before.x(), before.y(), before.z()),
            left + 12,
            y,
            0xA9BAC8,
            false);
        g.drawString(
            font,
            String.format(Locale.ROOT, "  → %.1f, %.1f, %.1f", after.x(), after.y(), after.z()),
            left + 12,
            y + 11,
            before.distance(after) > 1e-6 ? 0xFFD27C : 0xBFE7D1,
            false);
        y += panelHeight < 300 ? 21 : 24;
      }
      g.drawString(font, "整体建造 / 更新时才移动端点", left + 12, y + 4, 0xE0E8EF, false);
    }
    if (panel >= 550 && plan != null)
      map(g, left + control + 24, top + 28, panel - control - 36, panelHeight - 92);
    int statusY = top + panelHeight - (panelHeight < 260 ? 42 : 54);
    var lines = font.split(Component.literal(status == null ? "" : status), panel - 24);
    for (int i = 0; i < Math.min(panelHeight < 260 ? 1 : 2, lines.size()); i++)
      g.drawString(
          font,
          lines.get(i),
          left + 12,
          statusY + i * 10,
          plan == null ? 0xFFB6A3 : 0xC8E9DA,
          false);
    if (lines.size() > (panelHeight < 260 ? 1 : 2) && mouseY >= statusY && mouseY < statusY + 22)
      g.renderTooltip(font, Component.literal(status), mouseX, mouseY);
  }

  private void map(GuiGraphics g, int x, int y, int w, int h) {
    g.fill(x, y, x + w, y + h, 0xFF09161F);
    if (plan == null) {
      g.drawString(font, "方案生成后显示整体俯视图", x + 8, y + 8, 0xAFC8D3, false);
      return;
    }
    var meshes = plan.legs().stream().map(InterchangePlanner.Leg::mesh).toList();
    double minX = meshes.stream().mapToDouble(m -> m.min().x()).min().orElse(0),
        minZ = meshes.stream().mapToDouble(m -> m.min().z()).min().orElse(0),
        maxX = meshes.stream().mapToDouble(m -> m.max().x()).max().orElse(1),
        maxZ = meshes.stream().mapToDouble(m -> m.max().z()).max().orElse(1),
        scale = Math.min((w - 20) / (maxX - minX), (h - 20) / (maxZ - minZ));
    List<Sample> samples = new ArrayList<>();
    for (Mesh m : meshes)
      for (int i = 0; i < m.samples().size(); i += 3) samples.add(m.samples().get(i));
    samples.sort(Comparator.comparingDouble(p -> p.center().y()));
    for (var p : samples) {
      int px = x + 10 + (int) ((p.center().x() - minX) * scale),
          py = y + 10 + (int) ((p.center().z() - minZ) * scale),
          r = Math.max(1, (int) (p.halfWidth() * scale));
      int shade = (int) Math.max(0, Math.min(150, (p.center().y() - plan.center().y()) * 5));
      g.fill(px - r, py - r, px + r + 1, py + r + 1, 0xFF479FC2 + (shade << 16));
    }
    var nodes = payload.getList("Nodes", Tag.TAG_COMPOUND);
    for (int i = 0; i < nodes.size(); i++) {
      V p = plan.anchors().get(i).position();
      int px = x + 10 + (int) ((p.x() - minX) * scale),
          py = y + 10 + (int) ((p.z() - minZ) * scale);
      g.drawString(
          font,
          String.valueOf((char) ('A' + i)),
          Math.min(x + w - 9, Math.max(x + 2, px)),
          Math.min(y + h - 10, Math.max(y + 2, py)),
          0xFFFFFF,
          false);
    }
  }
}
