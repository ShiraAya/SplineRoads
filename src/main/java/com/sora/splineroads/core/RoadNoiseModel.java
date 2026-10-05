package com.sora.splineroads.core;

import com.sora.splineroads.core.RoadGeometry.V;
import com.sora.splineroads.core.RoadStructures.Part;
import com.sora.splineroads.core.RoadSurface.*;
import java.io.*;
import java.util.*;

/** CityBuild green type-1 barrier, including its original inward-curved cap and UVs. */
public final class RoadNoiseModel {
  public static final double HEIGHT=2.5043584;
  public static final double BASE_HEIGHT=.5;
  /** Keep the authored panel/UVs untouched, lift the complete panel onto a real solid base. */
  public static List<Part> assembly(Part panel){
    var up=new V(0,BASE_HEIGHT,0);
    var base=new Part(panel.a(),panel.b(),panel.width(),BASE_HEIGHT,false,RoadStructures.Material.CONCRETE,panel.frameA(),panel.frameB());
    var raised=new Part(panel.a().add(up),panel.b().add(up),panel.width(),panel.height(),false,panel.material(),panel.frameA(),panel.frameB(),panel.model());
    return List.of(base,raised);
  }
  private record Quad(List<V> points,List<UV> uv,boolean distant,boolean glass) {}
  private static final List<Quad> MODEL=load();
  private static List<Quad> load(){
    try(var stream=RoadNoiseModel.class.getResourceAsStream("/assets/splineroads/models/road/cb_noise.mesh")){
      if(stream==null)throw new IOException("Missing CB noise barrier");
      var in=new DataInputStream(stream);int count=in.readInt();
      if(count<6||count>10000||count%6!=0)throw new IOException("Invalid CB noise barrier");
      var out=new ArrayList<Quad>();
      for(int i=0;i<count;i+=6){
        var cube=new ArrayList<Quad>();double minY=Double.POSITIVE_INFINITY,maxY=-Double.MAX_VALUE,minX=Double.POSITIVE_INFINITY,maxX=-Double.MAX_VALUE;
        for(int f=0;f<6;f++){
          boolean glass=in.readBoolean();var points=new ArrayList<V>();var uv=new ArrayList<UV>();
          for(int j=0;j<4;j++){V p=new V(in.readFloat(),in.readFloat(),in.readFloat());points.add(p);uv.add(new UV(in.readFloat(),in.readFloat()));minY=Math.min(minY,p.y());maxY=Math.max(maxY,p.y());minX=Math.min(minX,p.x());maxX=Math.max(maxX,p.x());}
          cube.add(new Quad(List.copyOf(points),List.copyOf(uv),true,glass));
        }
        // Subpixel louvres disappear at distance; posts, panels and bent cap remain.
        boolean detail=maxY-minY<.06&&maxX-minX<.2;
        for(var q:cube){
          V a=q.points().get(1).sub(q.points().get(0)),b=q.points().get(2).sub(q.points().get(0));
          double nx=a.y()*b.z()-a.z()*b.y(),ny=a.z()*b.x()-a.x()*b.z(),nz=a.x()*b.y()-a.y()*b.x();
          boolean facing=Math.abs(nz)>.5*Math.sqrt(nx*nx+ny*ny+nz*nz);
          out.add(new Quad(q.points(),q.uv(),!detail&&facing,q.glass()));
        }
      }
      return List.copyOf(out);
    }catch(IOException e){throw new IllegalStateException("Cannot load CB noise barrier",e);}
  }
  public static List<Face> faces(Part part,boolean distant){
    V along=part.b().sub(part.a()),normal=along.horizontalUnit().left();
    V na=part.frameA()==null?normal:part.frameA().horizontalUnit(),nb=part.frameB()==null?normal:part.frameB().horizontalUnit();
    // The authored model bends toward negative local Z. frame points outwards.
    boolean mirrored=normal.dot(na)<0;
    var out=new ArrayList<Face>();
    for(var q:MODEL){if(distant&&!q.distant())continue;
      var points=new ArrayList<V>();var uv=new ArrayList<>(q.uv());
      for(V p:q.points())points.add(part.a().add(along.mul(p.x())).add(na.mul(1-p.x()).add(nb.mul(p.x())).mul(p.z())).add(new V(0,p.y(),0)));
      if(mirrored){Collections.reverse(points);Collections.reverse(uv);}
      out.add(new Face(List.copyOf(points),0xFFFFFF,false,q.glass()?Texture.CB_NOISE_GLASS:Texture.CB_NOISE,List.copyOf(uv)));
    }return List.copyOf(out);
  }
  private RoadNoiseModel(){}
}
