package com.sora.splineroads.client;

import com.sora.splineroads.net.RoadNetwork;
import com.sora.splineroads.world.RoadRecord;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;

public final class RoadDeleteScreen extends Screen {
  private final CompoundTag payload;
  private Button remove;
  private String status = "删除所选整段道路、护栏和桥墩；保留端点";

  public RoadDeleteScreen(CompoundTag t) {
    super(Component.literal("删除道路"));
    payload = t.copy();
    var deps=t.getList("Dependencies",net.minecraft.nbt.Tag.TAG_STRING);if(!deps.isEmpty())status="级联删除以下依赖匝道："+java.util.stream.IntStream.range(0,deps.size()).mapToObj(i->deps.getString(i).substring(0,8)).collect(java.util.stream.Collectors.joining(", "));
    ClientRoads.preview = RoadRecord.load(t.getCompound("Road")).mesh();
    ClientRoads.nodePreviews = java.util.List.of();
  }

  @Override
  protected void init() {
    int x = width / 2 - 150, y = height / 2 - 38;
    remove =
        addRenderableWidget(
            Button.builder(
                    Component.literal("确认删除所选道路"),
                    b -> {
                      CompoundTag t = new CompoundTag();
                      t.putString("Action", "delete");
                      t.putUUID("Id", payload.getUUID("Id"));
                      t.put("ConfirmDependencies",payload.getList("Dependencies",net.minecraft.nbt.Tag.TAG_STRING).copy());
                      remove.active = false;
                      if(!payload.getList("Dependencies",net.minecraft.nbt.Tag.TAG_STRING).isEmpty()){minecraft.setScreen(new LaneDeleteConfirmScreen(this,payload.getList("Dependencies",net.minecraft.nbt.Tag.TAG_STRING),()->RoadNetwork.CHANNEL.sendToServer(new RoadNetwork.Action(t))));remove.active=true;}else RoadNetwork.CHANNEL.sendToServer(new RoadNetwork.Action(t));
                    })
                .bounds(x + 12, y + 67, 160, 20)
                .build());
    addRenderableWidget(
        Button.builder(Component.literal("取消"), b -> onClose())
            .bounds(x + 180, y + 67, 108, 20)
            .build());
  }

  public void failed(String message) {
    status = message;
    remove.active = true;
  }

  @Override
  public boolean isPauseScreen() {
    return false;
  }

  @Override
  public void onClose() {
    ClientRoads.preview = null;
    ClientRoads.draft = null;
    super.onClose();
  }

  @Override
  public void render(GuiGraphics g, int x, int y, float dt) {
    int l = width / 2 - 150, t = height / 2 - 38;
    g.fill(l, t, l + 300, t + 100, 0xEE12212C);
    g.drawString(font, "删除道路 · 红色高亮为所选路段", l + 12, t + 12, 0xFFFFAC99, false);
    g.drawWordWrap(font, Component.literal(status), l + 12, t + 32, 276, 0xFFFFFF);
    super.render(g, x, y, dt);
  }
}
