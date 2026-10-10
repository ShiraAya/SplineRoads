package com.sora.splineroads.core;
import com.sora.splineroads.core.RoadGeometry.V;

/** Light is sampled in front of a visible face, never from an opaque lining cell behind it. */
public final class RoadLighting {
  public interface Access { boolean opaque(int x,int y,int z); int packed(int x,int y,int z); }
  public static V normal(RoadSurface.Face f){
    V origin=f.points().get(0);
    for(int i=1;i+1<f.points().size();i++){
      V a=f.points().get(i).sub(origin),b=f.points().get(i+1).sub(origin);
      V n=new V(a.y()*b.z()-a.z()*b.y(),a.z()*b.x()-a.x()*b.z(),a.x()*b.y()-a.y()*b.x());
      double length=Math.sqrt(n.dot(n));if(length>1e-10)return n.mul(1/length);
    }
    return new V(0,1,0);
  }
  public static int sample(RoadSurface.Face face,V vertex,Access access){
    return sample(face,vertex,access,false);
  }
  public static int sample(RoadSurface.Face face,V vertex,Access access,boolean forceUp){
    if(face.emissive())return 15728880;
    V n=normal(face),center=new V(0,0,0);
    if(forceUp&&n.y()<0)n=n.mul(-1);
    for(V v:face.points())center=center.add(v);
    center=center.mul(1.0/face.points().size());
    V inward=center.sub(vertex);double length=Math.sqrt(inward.dot(inward));
    V direction=length<1e-8?new V(0,0,0):inward.mul(1/length);
    // The lining is up to one world cell thick; larger offsets only serve opaque-cell fallbacks.
    for(double inset:new double[]{.2,.7,1.2})for(double outward:new double[]{.12,.65,1.15}){
      V p=vertex.add(direction.mul(Math.min(inset,length))).add(n.mul(outward));
      int x=(int)Math.floor(p.x()),y=(int)Math.floor(p.y()),z=(int)Math.floor(p.z());
      if(!access.opaque(x,y,z)){
        int light=access.packed(x,y,z);
        // Thin lamp/rail faces can straddle a voxel boundary. A single corner
        // sample used to paint a black patch beside the same illuminated face.
        // Share only a nearby visible centroid sample; large surfaces and opaque
        // walls keep their own local lighting. This does not make metal emissive.
        if(length<=2){
          V q=center.add(n.mul(outward));int cx=(int)Math.floor(q.x()),cy=(int)Math.floor(q.y()),cz=(int)Math.floor(q.z());
          if(!access.opaque(cx,cy,cz)){
            int other=access.packed(cx,cy,cz);
            light=Math.max(light&0xffff,other&0xffff)|(Math.max(light>>>16,other>>>16)<<16);
          }
        }
        return light;
      }
    }
    // Long, narrow pavement triangles can keep their corner inside a wall voxel even
    // after a one-block diagonal inset. Their centroid is safely on the same visible face.
    for(double outward:new double[]{.12,.65,1.15}){
      V point=center.add(n.mul(outward));int x=(int)Math.floor(point.x()),y=(int)Math.floor(point.y()),z=(int)Math.floor(point.z());
      if(!access.opaque(x,y,z))return access.packed(x,y,z);
    }
    V p=vertex.add(n.mul(.12));return access.packed((int)Math.floor(p.x()),(int)Math.floor(p.y()),(int)Math.floor(p.z()));
  }
  private RoadLighting(){}
}
