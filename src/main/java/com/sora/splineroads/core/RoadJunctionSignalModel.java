package com.sora.splineroads.core;

import com.sora.splineroads.core.RoadGeometry.V;
import com.sora.splineroads.core.RoadStructures.*;
import com.sora.splineroads.core.RoadSurface.*;
import java.io.*;
import java.util.*;

/** Original CB vertical and pedestrian heads. Coordinates pivot at the rear mounting lug. */
public final class RoadJunctionSignalModel {
  private record Quad(int texture,List<V> vertices,List<UV> uv) {}
  private static final List<Quad> VEHICLE=load("vehicle"),PEDESTRIAN=load("pedestrian");
  public static boolean supports(Part part){return part.material()==Material.SIGNAL_VEHICLE||part.material()==Material.SIGNAL_PEDESTRIAN||part.material()==Material.SIGNAL_LEFT;}
  private static List<Quad> load(String name) {
    try(var stream=RoadJunctionSignalModel.class.getResourceAsStream("/assets/splineroads/models/road/cb_"+name+".mesh")) {
      if(stream==null)throw new IOException("Missing CB "+name+" signal");
      var in=new DataInputStream(stream);int count=in.readInt();var quads=new ArrayList<Quad>();
      for(int i=0;i<count;i++){int key=in.readInt();var vertices=new ArrayList<V>();var uv=new ArrayList<UV>();for(int j=0;j<4;j++){vertices.add(new V(in.readFloat(),in.readFloat(),in.readFloat()));uv.add(new UV(in.readFloat(),in.readFloat()));}quads.add(new Quad(key,List.copyOf(vertices),List.copyOf(uv)));}
      return List.copyOf(quads);
    }catch(IOException e){throw new IllegalStateException(e);}
  }
  public static List<Face> faces(Part part,boolean lamps,int state) {
    V front=part.b().sub(part.a()).horizontalUnit(),side=front.left();var faces=new ArrayList<Face>();
    for(var quad:part.material()!=Material.SIGNAL_PEDESTRIAN?VEHICLE:PEDESTRIAN) {
      if((quad.texture()>=3)!=lamps)continue;
      if(lamps&&part.material()==Material.SIGNAL_LEFT){arrowLens(faces,part,quad,front,side,state);continue;}
      int texture=quad.texture();
      if(lamps)texture=texture==6?(state==1?7:6):(state==1?5:state==2?4:3);
      final int tile=texture;
      var points=quad.vertices().stream().map(v->part.a().add(side.mul(v.x())).add(new V(0,v.y(),0)).sub(front.mul(v.z()))).toList();
      var uv=quad.uv().stream().map(t->new UV((tile+t.u())/8,t.v())).toList();
      faces.add(new Face(points,0xffffff,lamps,Texture.SIGNAL_ATLAS,uv));
    }
    return List.copyOf(faces);
  }
  private static void arrowLens(List<Face> out,Part part,Quad q,V front,V side,int state){
    var background=q.vertices().stream().map(v->part.a().add(side.mul(v.x())).add(new V(0,v.y(),0)).sub(front.mul(v.z()))).toList();
    out.add(new Face(background,0x111714,false));
    double y=q.vertices().stream().mapToDouble(V::y).average().orElseThrow();
    int lamp=y>1?0:y>.55?2:1;boolean on=lamp==state;
    int color=!on?0x242B28:state==1?0x46FF61:state==2?0xFFD142:0xFF3830;
    V center=part.a().add(new V(0,y,0)).sub(front.mul(-.631));
    // Left as seen by the approaching driver; same CB housing and mounting lug.
    lensFace(out,List.of(center.add(side.mul(.035)).add(new V(0,.04,0)),center.sub(side.mul(.14)).add(new V(0,.04,0)),center.sub(side.mul(.14)).add(new V(0,-.04,0)),center.add(side.mul(.035)).add(new V(0,-.04,0))),color,on,front);
    lensFace(out,List.of(center.add(side.mul(.145)),center.add(side.mul(.015)).add(new V(0,.11,0)),center.add(side.mul(.015)).add(new V(0,-.11,0))),color,on,front);
  }
  private static void lensFace(List<Face> out,List<V> vertices,int color,boolean light,V front){
    V a=vertices.get(1).sub(vertices.get(0)),b=vertices.get(2).sub(vertices.get(0));
    V normal=new V(a.y()*b.z()-a.z()*b.y(),a.z()*b.x()-a.x()*b.z(),a.x()*b.y()-a.y()*b.x());
    if(normal.dot(front)<0){vertices=new ArrayList<>(vertices);Collections.reverse(vertices);}
    out.add(new Face(vertices,color,light));
  }
  private RoadJunctionSignalModel(){}
}
