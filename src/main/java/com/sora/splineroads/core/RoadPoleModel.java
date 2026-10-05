package com.sora.splineroads.core;

import com.sora.splineroads.core.RoadGeometry.V;
import com.sora.splineroads.core.RoadStructures.*;
import java.io.*;
import java.util.*;

/** Original CB modular signal support geometry; plain e7e7e7 authored material. */
public final class RoadPoleModel {
  private static final Map<Material, List<List<V>>> MODELS = Map.of(
      Material.CB_POST, load("cb_post"), Material.CB_ARM, load("cb_arm"),
      Material.CB_BASE, load("cb_base"));
  private static List<List<V>> load(String name) {
    try (var stream = RoadPoleModel.class.getResourceAsStream(
        "/assets/splineroads/models/road/" + name + ".mesh")) {
      if (stream == null) throw new IOException("Missing CB pole: " + name);
      var in = new DataInputStream(stream); int n = in.readInt();
      if (n < 1 || n > 10000) throw new IOException("Invalid CB pole");
      List<List<V>> out = new ArrayList<>();
      for (int i = 0; i < n; i++) {
        List<V> q = new ArrayList<>();
        for (int k = 0; k < 4; k++) q.add(new V(in.readFloat(), in.readFloat(), in.readFloat()));
        out.add(List.copyOf(q));
      }
      return List.copyOf(out);
    } catch (IOException e) { throw new IllegalStateException(e); }
  }
  public static boolean support(Part part) { return MODELS.containsKey(part.material()); }
  public static List<RoadSurface.Face> faces(Part part) {
    V along = part.b().sub(part.a()).horizontalUnit(), side = along.left();
    V center = part.a().add(part.b()).mul(.5);
    return MODELS.get(part.material()).stream().map(q -> {
      List<V> points = q.stream().map(p -> part.material() == Material.CB_ARM
          ? part.a().add(part.b().sub(part.a()).mul(p.z())).add(side.mul(p.x()))
              .add(new V(0, p.y(), 0))
          : center.add(side.mul(p.x())).add(along.mul(p.z()))
              .add(new V(0, p.y() * (part.material() == Material.CB_POST ? part.height() : 1), 0)))
          .toList();
      points = new ArrayList<>(points);
      Collections.reverse(points);
      return new RoadSurface.Face(points, 0xE7E7E7, false, RoadSurface.Texture.METAL);
    }).toList();
  }
  private RoadPoleModel() {}
}
