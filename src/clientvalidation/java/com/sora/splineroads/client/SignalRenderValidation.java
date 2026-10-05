package com.sora.splineroads.client;

import com.mojang.blaze3d.vertex.*;
import com.sora.splineroads.core.*;
import com.sora.splineroads.core.JunctionSpec.*;
import com.sora.splineroads.core.RoadGeometry.*;
import com.sora.splineroads.core.RoadStructures.*;
import com.sora.splineroads.core.RoadSurface.*;
import com.sora.splineroads.world.*;
import java.lang.management.ManagementFactory;
import java.util.*;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;

/** Real Minecraft vertex buffers and strict material routing, without an OpenGL window. */
public final class SignalRenderValidation {
  private static int checks;
  private static volatile long sink;
  private static void check(boolean pass, String why) {
    checks++; if (!pass) throw new AssertionError(why);
  }
  private static final class StrictSource implements MultiBufferSource {
    final BufferBuilder builder;
    int requests;
    StrictSource(BufferBuilder builder) { this.builder = builder; }
    public VertexConsumer getBuffer(RenderType type) {
      Objects.requireNonNull(type, "Oculus-compatible non-null RenderType contract");
      requests++; return builder;
    }
  }
  private static Part head(Material material, double x, double z, int direction) {
    V at = new V(x, 70, z);
    double angle = direction * Math.PI / 2;
    return new Part(at, at.add(new V(Math.cos(angle),0,Math.sin(angle))), .657, 1.594, false, material);
  }
  private static void materialAndVertices() {
    var arena = new RoadUploadArena();
    var pose = new PoseStack();
    Set<RenderType> used = new HashSet<>();
    int cases = 0, triangles = 0;
    for (Material material : List.of(Material.SIGNAL_MAIN, Material.SIGNAL_RAMP,
        Material.SIGNAL_VEHICLE, Material.SIGNAL_PEDESTRIAN, Material.SIGNAL_LEFT))
      for (int state=0; state<3; state++) for (int direction=0; direction<4; direction++) {
        var at = head(material, 29_000_000.25, -29_000_000.5, direction);
        var faces = RoadSignalModel.faces(at, true, state);
        check(!faces.isEmpty(), "authored lamps are present");
        for (var face : faces) {
          check(RoadRenderer.signalMaterial(face.texture()) != null, "all lamp materials mapped");
          if (face.points().size() == 3) triangles++;
        }
        var batch = new RoadSignalBatch(faces);
        var builder = arena.begin(); var source = new StrictSource(builder); used.clear();
        V camera = at.a().add(new V(1.25, -.5, 2.75));
        batch.emit(pose, source, camera, RoadRenderer::signalMaterial, used);
        var rendered = builder.end();
        int expected = faces.stream().mapToInt(f -> f.points().size()==4?4:(f.points().size()-2)*4).sum();
        check(rendered.drawState().vertexCount() == expected, "triangles become degenerate quads; no missing arrow tips");
        check(source.requests == faces.stream().map(Face::texture).distinct().count(), "one material request per batch group");
        var data = rendered.vertexBuffer(); int stride = DefaultVertexFormat.NEW_ENTITY.getVertexSize();
        for (int i=0; i<expected; i++) for (int axis=0; axis<3; axis++) {
          float p = data.getFloat(i*stride+axis*4);
          check(Float.isFinite(p) && Math.abs(p)<8, "camera-relative precision at world border");
        }
        check(pose.last().pose().equals(new org.joml.Matrix4f()), "balanced pose stack");
        rendered.release(); cases++;
      }
    check(triangles > 0, "regression includes the actual untextured left-arrow triangles");
    System.out.printf("SIGNAL VERTICES: %d material/state/orientation cases; %d arrow triangles; actual BufferBuilder, no null material or missing UV access.%n", cases, triangles);
  }
  private static JunctionSpec fixture(boolean left, boolean split, int yellow, int offset) {
    var arms = new ArrayList<Arm>();
    for (int i=0;i<4;i++) {
      double angle = i*Math.PI/2; V inward = new V(-Math.cos(angle),0,-Math.sin(angle));
      var options = new RoadProfile.Options(left,false,false,true,.5,0);
      var settings = new Settings(Mode.STRAIGHT,Style.O6_RAIL,
          RoadProfile.width(Style.O6_RAIL,options,3.5),1,.35,90).options(options);
      var arm = JunctionSpec.arm(new Node(inward.mul(-100).add(new V(0,64,0)),0,0),inward,settings,true,i);
      var lanes = new ArrayList<>(arm.lanes());
      if(split)lanes.set(0,lanes.get(0).signals(true,-1));
      arms.add(arm.lanes(lanes));
    }
    return new JunctionSpec(new V(0,64,0),Kind.INTERSECTION,left,6,18,2,4,1,
        Control.SIGNALS,5,yellow,0,offset,true,true,arms);
  }
  private static void clocksAndIndex() {
    int fixtures=0;
    for(boolean left:new boolean[]{false,true})for(boolean split:new boolean[]{false,true})
      for(int yellow:new int[]{0,3}) {
        var spec=fixture(left,split,yellow,-17); var plan=JunctionPlanner.plan(spec);
        for(int arm=0;arm<4;arm++) {
          var ref=new JunctionPlanner.Ref(spec,arm);
          var piece=ref.get(); var mesh=piece.mesh();
          var record=new RoadRecord(new UUID(0,arm+1),new UUID(0,0),new BlockPos(0,64,0),
              new BlockPos(100,64,0),new Node(mesh.first().center(),0,0),
              new Node(mesh.last().center(),0,0),mesh.settings()).junction(new UUID(0,100),ref);
          var built=new RoadIndex.Built(record,true);
          for(long time=-21;time<plan.signals().periodSeconds()*20L+21;time+=19) {
            var expected=new ArrayList<Face>();
            for(Part part:built.signalHeads)expected.addAll(RoadSignalModel.faces(part,true,ref.headState(part,time)));
            check(built.signalFaces(time).equals(expected),"cached split/normal/pedestrian states match arithmetic clock");
          }
          for(long time:new long[]{12000,0,-1,20,19}) {
            var expected=new ArrayList<Face>();
            for(Part part:built.signalHeads)expected.addAll(RoadSignalModel.faces(part,true,ref.headState(part,time)));
            check(built.signalFaces(time).equals(expected),"time jumps and reversal invalidate cache correctly");
          }
          long cut=(arm*(spec.greenSeconds()+spec.yellowSeconds()+spec.allRedSeconds())*20L+spec.greenSeconds()*10L-spec.timeOffset()*20L);
          for(long time=cut-11;time<=cut+1;time++) {
            var expected=new ArrayList<Face>();
            for(Part part:built.signalHeads)expected.addAll(RoadSignalModel.faces(part,true,ref.headState(part,time)));
            check(built.signalFaces(time).equals(expected),"actual RoadIndex lamp cache switches at the half-second boundary");
          }
          for(Part part:built.signalHeads)for(int state=0;state<3;state++)
            for(Face face:RoadSignalModel.faces(part,true,state))for(V v:face.points())
              check(built.signalBounds.contains(v.x(),v.y(),v.z()),"frustum bounds enclose all lens geometry");
        }
        fixtures++;
      }
    var heads=new ArrayList<Part>();for(int i=0;i<96;i++)heads.add(head(Material.SIGNAL_LEFT,i,0,0));
    int[] calls={0};
    var cache=new RoadSignalFrames(heads,(part,time)->{calls[0]++;return (int)Math.floorMod(Math.floorDiv(time,400),3);});
    var frame=cache.at(0);
    for(int i=0;i<2400;i++)check(cache.at(i/120)==frame,"same-second frames reuse lamp geometry");
    check(calls[0]==96,"2400 frames perform one evaluation per head");
    check(cache.at(20)==frame,"unchanged colors keep baked frame across seconds");
    check(cache.at(400)!=frame,"phase changes replace geometry");
    check(cache.at(0).equals(frame),"time reset restores previous colors");
    System.out.printf("SIGNAL CLOCK: %d fixtures, 32 actual RoadIndex pieces; 2400 same-second frames reduce state evaluations from 230400 to 96; time jumps and culling bounds passed.%n",fixtures);
  }
  /** The previous renderer's per-face CPU path, restricted to its supported textured lamps. */
  private static void oldEmit(List<Face> faces, PoseStack pose, StrictSource source, V camera) {
    for(Face face:faces) {
      var target=source.getBuffer(RoadRenderer.signalMaterial(face.texture()));
      int[] lights={LightTexture.FULL_BRIGHT,LightTexture.FULL_BRIGHT,LightTexture.FULL_BRIGHT,LightTexture.FULL_BRIGHT};
      var p=face.points();V a=p.get(1).sub(p.get(0)),b=p.get(2).sub(p.get(0));
      V n=new V(a.y()*b.z()-a.z()*b.y(),a.z()*b.x()-a.x()*b.z(),a.x()*b.y()-a.y()*b.x());
      double length=Math.sqrt(n.dot(n));n=length<1e-10?new V(0,1,0):n.mul(1/length);
      for(int i=0;i<4;i++) {
        V v=p.get(i);UV uv=face.uv().get(i);
        target.vertex(pose.last().pose(),(float)(v.x()-camera.x()),(float)(v.y()-camera.y()),(float)(v.z()-camera.z()))
            .color((face.color()>>16)&255,(face.color()>>8)&255,face.color()&255,255)
            .uv((float)uv.u(),(float)uv.v()).overlayCoords(OverlayTexture.NO_OVERLAY)
            .uv2(lights[i]).normal(pose.last().normal(),(float)n.x(),(float)n.y(),(float)n.z()).endVertex();
      }
    }
  }
  private static long[] benchmark(List<Face> faces, RoadSignalBatch batch, boolean cached, int frames) {
    var arena=new RoadUploadArena();var pose=new PoseStack();var used=new HashSet<RenderType>();
    V camera=new V(3,65,4);var allocation=(com.sun.management.ThreadMXBean)ManagementFactory.getThreadMXBean();
    long tid=Thread.currentThread().getId(),bytes=allocation.getThreadAllocatedBytes(tid),start=System.nanoTime(),requests=0;
    for(int i=0;i<frames;i++) {
      var builder=arena.begin();var source=new StrictSource(builder);
      if(cached)batch.emit(pose,source,camera,RoadRenderer::signalMaterial,used);
      else oldEmit(faces,pose,source,camera);
      var rendered=builder.end();sink+=rendered.drawState().vertexCount();rendered.release();requests+=source.requests;
    }
    return new long[]{System.nanoTime()-start,allocation.getThreadAllocatedBytes(tid)-bytes,requests};
  }
  private static void benchmark() {
    var faces=new ArrayList<Face>();for(int i=0;i<12;i++)faces.addAll(RoadSignalModel.faces(head(i%3==0?Material.SIGNAL_PEDESTRIAN:Material.SIGNAL_VEHICLE,i,0,0),true,1));
    var batch=new RoadSignalBatch(faces);benchmark(faces,batch,false,2000);benchmark(faces,batch,true,2000);
    long[] old=null,next=null;
    for(int i=0;i<3;i++) {
      var a=benchmark(faces,batch,false,5000);var b=benchmark(faces,batch,true,5000);
      if(old==null||a[0]<old[0])old=a;if(next==null||b[0]<next[0])next=b;
    }
    check(next[2]<old[2],"grouped material requests reduced");
    System.out.printf(Locale.ROOT,"SIGNAL SUBMISSION: 12 textured heads, 5000 frames, same vertex count; prior %.2f ms / %.2f MiB / %d material requests; cached %.2f ms / %.2f MiB / %d requests. CPU-only microbenchmark, excludes GPU, Oculus and other mods.%n",old[0]/1e6,old[1]/1048576.0,old[2],next[0]/1e6,next[1]/1048576.0,next[2]);
  }
  public static void main(String[] args) {
    net.minecraft.SharedConstants.tryDetectVersion();
    try { net.minecraft.server.Bootstrap.bootStrap(); }
    catch (ExceptionInInitializerError failure) {
      // Standalone JavaExec uses the mapped Forge JAR, not ModLauncher event transformers.
      // Vanilla registries and Forge's vanilla snapshot are already initialized here; only
      // the subsequent networking hook needs the injected NetworkEvent constructor.
      Throwable cause=failure;
      while(cause.getCause()!=null)cause=cause.getCause();
      if(!(cause instanceof NoSuchMethodException)
          || !"net.minecraftforge.network.NetworkEvent.<init>()".equals(cause.getMessage()))throw failure;
      System.out.println("Standalone fixture: vanilla registries initialized; Forge networking is outside this vertex-buffer test.");
    }
    materialAndVertices();clocksAndIndex();benchmark();
    System.out.printf("SIGNAL RENDER PASS: %,d checks.%n",checks);
  }
}
