package com.sora.splineroads.core;

import com.sora.splineroads.core.RoadGeometry.V;
import com.sora.splineroads.core.RoadStructures.Part;
import com.sora.splineroads.core.RoadSurface.*;
import java.io.*;
import java.util.*;

/** Original CityBuild/Yunbei horizontal signal, baked once with its authored element UVs. */
public final class RoadSignalModel {
  private record Quad(boolean lens, List<V> vertices, List<UV> uv) {}
  private static final List<Quad> MODEL = load();
  private static List<Quad> load() {
    try (var stream = RoadSignalModel.class.getResourceAsStream(
            "/assets/splineroads/models/road/cb_signal.mesh")) {
      if (stream == null) throw new IOException("Missing CB signal mesh");
      var in = new DataInputStream(stream);
      int count = in.readInt();
      if (count < 1 || count > 10000) throw new IOException("Invalid CB mesh");
      List<Quad> out = new ArrayList<>();
      for (int i = 0; i < count; i++) {
        boolean lens = in.readBoolean();
        List<V> points = new ArrayList<>(); List<UV> uv = new ArrayList<>();
        for (int j = 0; j < 4; j++) {
          points.add(new V(in.readFloat(), in.readFloat(), in.readFloat()));
          uv.add(new UV(in.readFloat(), in.readFloat()));
        }
        out.add(new Quad(lens, List.copyOf(points), List.copyOf(uv)));
      }
      return List.copyOf(out);
    } catch (IOException e) { throw new IllegalStateException("Cannot load CB signal", e); }
  }

  public static List<Face> faces(Part part, boolean lenses, boolean green) {
    return faces(part,lenses,green ? 1 : 0);
  }

  public static List<Face> faces(Part part, boolean lenses, int state) {
    if(RoadJunctionSignalModel.supports(part))return RoadJunctionSignalModel.faces(part,lenses,state);
    V front = part.b().sub(part.a()).horizontalUnit(), side = front.left();
    V center = part.a().add(part.b()).mul(.5);
    List<Face> out = new ArrayList<>();
    for (var q : MODEL) {
      if (q.lens() != lenses) continue;
      List<V> points = q.vertices().stream().map(v -> center.add(side.mul(v.x()))
          .add(new V(0, v.y(), 0)).sub(front.mul(v.z()))).toList();
      out.add(new Face(points, 0xFFFFFF, lenses,
          lenses ? (state == 1 ? Texture.SIGNAL_GREEN : state == 2 ? Texture.SIGNAL_AMBER : Texture.SIGNAL_RED) : Texture.SIGNAL_HOUSING,
          q.uv()));
    }
    return List.copyOf(out);
  }
  private RoadSignalModel() {}
}
