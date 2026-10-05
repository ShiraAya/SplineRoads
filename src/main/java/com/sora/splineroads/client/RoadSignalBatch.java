package com.sora.splineroads.client;

import com.mojang.blaze3d.vertex.*;
import com.sora.splineroads.core.RoadGeometry.V;
import com.sora.splineroads.core.RoadSurface.*;
import java.util.*;
import java.util.function.Function;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.texture.OverlayTexture;

/** Bake lamp polygons once per state change; submit grouped, camera-relative vertices each frame. */
final class RoadSignalBatch {
  private record Vertex(float x, float y, float z, int color, float u, float v,
      float nx, float ny, float nz) {}
  private final V origin;
  private final EnumMap<Texture, List<Vertex>> materials = new EnumMap<>(Texture.class);

  RoadSignalBatch(List<Face> faces) {
    origin = faces.stream().filter(f -> !f.points().isEmpty())
        .map(f -> f.points().get(0)).findFirst().orElse(new V(0, 0, 0));
    for (Face face : faces) {
      int count = face.points().size();
      if (count < 3) continue;
      if (!face.uv().isEmpty() && face.uv().size() != count)
        throw new IllegalArgumentException("Signal UV count does not match its polygon");
      var vertices = materials.computeIfAbsent(face.texture(), key -> new ArrayList<>());
      if (count == 4) quad(vertices, face, 0, 1, 2, 3);
      else for (int i = 1; i < count - 1; i++) quad(vertices, face, 0, i, i + 1, i + 1);
    }
  }

  private void quad(List<Vertex> out, Face face, int a, int b, int c, int d) {
    V ab = face.points().get(b).sub(face.points().get(a));
    V ac = face.points().get(c).sub(face.points().get(a));
    V normal = new V(ab.y()*ac.z()-ab.z()*ac.y(), ab.z()*ac.x()-ab.x()*ac.z(),
        ab.x()*ac.y()-ab.y()*ac.x());
    double length = Math.sqrt(normal.dot(normal));
    if (length < 1e-10) {
      if (c != d) quad(out, face, a, c, d, d);
      return;
    }
    normal = normal.mul(1 / length);
    vertex(out, face, a, normal); vertex(out, face, b, normal);
    vertex(out, face, c, normal); vertex(out, face, d, normal);
  }

  private void vertex(List<Vertex> out, Face face, int index, V normal) {
    V point = face.points().get(index).sub(origin);
    UV uv = face.uv().isEmpty() ? new UV(0, 0) : face.uv().get(index);
    out.add(new Vertex((float)point.x(), (float)point.y(), (float)point.z(), face.color(),
        (float)uv.u(), (float)uv.v(), (float)normal.x(), (float)normal.y(), (float)normal.z()));
  }

  void emit(PoseStack pose, MultiBufferSource buffers, V camera,
      Function<Texture, RenderType> types, Set<RenderType> used) {
    pose.pushPose();
    pose.translate(origin.x()-camera.x(), origin.y()-camera.y(), origin.z()-camera.z());
    var transform = pose.last().pose();
    var normals = pose.last().normal();
    try {
      for (var entry : materials.entrySet()) {
        RenderType type = Objects.requireNonNull(types.apply(entry.getKey()),
            "Missing SR signal material: " + entry.getKey());
        used.add(type);
        VertexConsumer target = buffers.getBuffer(type);
        for (Vertex v : entry.getValue())
          target.vertex(transform, v.x, v.y, v.z)
              .color((v.color >> 16) & 255, (v.color >> 8) & 255, v.color & 255, 255)
              .uv(v.u, v.v).overlayCoords(OverlayTexture.NO_OVERLAY)
              .uv2(LightTexture.FULL_BRIGHT).normal(normals, v.nx, v.ny, v.nz).endVertex();
      }
    } finally { pose.popPose(); }
  }
}
