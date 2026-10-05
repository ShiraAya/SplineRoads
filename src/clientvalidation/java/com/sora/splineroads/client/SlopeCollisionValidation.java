package com.sora.splineroads.client;
import com.sora.splineroads.core.*;
import com.sora.splineroads.core.RoadGeometry.*;
import com.sora.splineroads.world.RoadCollisionShape;
import net.minecraft.world.phys.shapes.*;
import net.minecraft.world.phys.*;
import net.minecraft.core.*;
import java.util.*;
public final class SlopeCollisionValidation {
 static int checks;static void check(boolean b,String why){checks++;if(!b)throw new AssertionError(why);}
 static VoxelShape old(List<RoadRaster.Box> boxes){var shapes=boxes.stream().map(b->Shapes.box(b.x0(),b.y0(),b.z0(),b.x1(),b.y1(),b.z1())).toList();return union(shapes,0,shapes.size()).optimize();}
 static VoxelShape union(List<VoxelShape> p,int a,int b){if(a==b)return Shapes.empty();if(b-a==1)return p.get(a);int m=(a+b)/2;return Shapes.joinUnoptimized(union(p,a,m),union(p,m,b),BooleanOp.OR);}
 static long run(List<List<RoadRaster.Box>> fixtures,boolean fast,int repeat){long at=System.nanoTime();double blackhole=0;for(int n=0;n<repeat;n++)for(var f:fixtures){var s=fast?RoadCollisionShape.build(f):old(f);blackhole+=s.max(Direction.Axis.Y);}if(blackhole<1)throw new AssertionError();return System.nanoTime()-at;}
 static volatile double sink;
 static long queries(List<VoxelShape> shapes,int repeat){
  long start=System.nanoTime();double sum=0;
  for(int n=0;n<repeat;n++)for(var shape:shapes){double x=.15+(n%6)*.12,z=.1+(n%7)*.1;
   var box=new AABB(x-.2,1.05,z-.2,x+.2,2.85,z+.2);
   sum+=shape.collide(Direction.Axis.Y,box,-.8)+shape.collide(Direction.Axis.X,box,.7)+shape.collide(Direction.Axis.Z,box,-.7);
   var hit=shape.clip(new Vec3(x,2,z),new Vec3(x,-1,z),BlockPos.ZERO);if(hit!=null)sum+=hit.getLocation().y;
  }
  sink=sum;return System.nanoTime()-start;
 }
 public static void main(String[] args){
  var fixtures=new ArrayList<List<RoadRaster.Box>>();
  for(double angle:new double[]{0,.47,1.11})for(double slope:new double[]{0,.04,.15,.30}){
   V f=new V(Math.sin(angle),0,Math.cos(angle));var settings=new Settings(Mode.STRAIGHT,Style.O4_YELLOW,17,1,.35,90);
   Mesh mesh=RoadGeometry.build(new Node(new V(-80.5,64,-80.5),0,0),new Node(new V(-80.5,64+80*slope,-80.5).add(f.mul(80)),0,0),settings);
   fixtures.addAll(RoadRaster.raster(mesh).entrySet().stream().sorted(Comparator.comparingInt(e->e.getKey().hashCode())).filter(e->e.getValue().size()>1).limit(24).map(Map.Entry::getValue).toList());
  }
  for(var boxes:fixtures){var a=old(boxes);var b=RoadCollisionShape.build(boxes);check(!Shapes.joinIsNotEmpty(a,b,BooleanOp.NOT_SAME),"exact occupied volume differs");
   for(Direction.Axis axis:Direction.Axis.values())for(double move:new double[]{-.8,.8}){var body=new AABB(.2,1.05,.2,.8,2.85,.8);check(Math.abs(a.collide(axis,body,move)-b.collide(axis,body,move))<1e-9,"walking collision differs");}
   for(double x:new double[]{.05,.3,.65,.95})for(double z:new double[]{.05,.3,.65,.95}){var from=new Vec3(x,2,z);var to=new Vec3(x,-1,z);var aa=a.clip(from,to,BlockPos.ZERO);var bb=b.clip(from,to,BlockPos.ZERO);check((aa==null)==(bb==null),"ray presence differs");if(aa!=null)check(aa.getLocation().distanceTo(bb.getLocation())<1e-8,"ray height differs");}
  }
  run(fixtures,true,2);run(fixtures,false,2);long slow=Long.MAX_VALUE,fast=Long.MAX_VALUE;for(int i=0;i<3;i++){slow=Math.min(slow,run(fixtures,false,3));fast=Math.min(fast,run(fixtures,true,3));}
  var oldShapes=fixtures.stream().map(SlopeCollisionValidation::old).toList();var newShapes=fixtures.stream().map(RoadCollisionShape::build).toList();
  queries(oldShapes,20);queries(newShapes,20);long oldQueries=Long.MAX_VALUE,newQueries=Long.MAX_VALUE;
  for(int i=0;i<5;i++){oldQueries=Math.min(oldQueries,queries(oldShapes,50));newQueries=Math.min(newQueries,queries(newShapes,50));}
  System.out.printf(java.util.Locale.ROOT,"CACHED COLLISION QUERIES: prior %.2f ms, new %.2f ms per %d walking/ray queries (%.2fx).%n",oldQueries/1e6,newQueries/1e6,fixtures.size()*50*4,oldQueries/(double)newQueries);
  System.out.printf(java.util.Locale.ROOT,"SLOPE COLLISION PASS: %d checks, %d real road cells; prior %.2f ms, new %.2f ms per %d cold shapes (%.2fx). Exact 1/8-block slope collision retained.%n",checks,fixtures.size(),slow/1e6,fast/1e6,fixtures.size()*3,slow/(double)fast);
 }
}
