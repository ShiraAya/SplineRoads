package com.sora.splineroads.core;
import com.sora.splineroads.core.RoadGeometry.*;
import java.util.*;
/** Compare against the unmodified 0.40.18 raster, not approximated occupancy. */
public final class Performance432Validation {
  static long checks; static void check(boolean b,String why){checks++;if(!b)throw new AssertionError(why);}
  static Map<RoadRaster.Cell,List<RoadRaster.Box>> ref(Mesh mesh){
    var out=new HashMap<RoadRaster.Cell,List<RoadRaster.Box>>();
    Reference432Raster.raster(mesh).forEach((c,bs)->out.put(new RoadRaster.Cell(c.x(),c.y(),c.z()),bs.stream().map(b->new RoadRaster.Box(b.x0(),b.y0(),b.z0(),b.x1(),b.y1(),b.z1())).toList()));return out;
  }
  static Mesh road(double angle,double x,double z,double len,double width,double y,double slope){
    var points=new ArrayList<Sample>();V dir=new V(Math.sin(angle),0,Math.cos(angle));var settings=new Settings(Mode.CURVE,Style.C1_RAMP,Math.max(4,width),1,.35,90);
    int n=(int)Math.ceil(len/.6);for(int i=0;i<=n;i++){double d=len*i/n;points.add(new Sample(new V(x, y+slope*d,z).add(dir.mul(d)),dir.left(),d,width/2));}return RoadRibbon.mesh(points,settings);
  }
  static List<RoadRaster.Box> convert(List<Reference432Raster.Box> boxes){return boxes.stream().map(b->new RoadRaster.Box(b.x0(),b.y0(),b.z0(),b.x1(),b.y1(),b.z1())).toList();}
  public static void main(String[] args){
    var random=new Random(432);
    for(int i=0;i<140;i++){
      double angle=i%4==0?.319:i%4==1?Math.PI/2:0;
      double x=i%3==0?92000:-45+random.nextDouble(),z=i%3==0?95000:-50+random.nextDouble();
      var m=road(angle,x,z,3+random.nextDouble()*23,3+random.nextDouble()*12,80+(i%5)*.000000001,i%6==0?.1:0);
      var expected=ref(m);var actual=RoadRaster.raster(m);
      check(actual.equals(expected),"exact full raster "+i+" expectedCells="+expected.size()+" actual="+actual.size());
      for(var c:actual.keySet().stream().limit(6).toList()){
        var old=Reference432Raster.raster(m,new Reference432Raster.Cell(c.x(),c.y(),c.z()));var newOne=RoadRaster.raster(m,c);
        check(newOne.get(c).equals(convert(old.get(new Reference432Raster.Cell(c.x(),c.y(),c.z())))),"local/full consistency "+i);
      }
    }
    for(int i=0;i<1000;i++){
      double x=(i%7==0?92000:0)+random.nextDouble()*10,z=(i%7==0?95000:0)+random.nextDouble()*10;
      double len=.02+random.nextDouble()*7,w=.02+random.nextDouble()*3,y=12+random.nextDouble(),h=.01+random.nextDouble()*2;
      var p=new RoadStructures.Part(new V(x,y,z),new V(x,y,z+len),w,h,false,RoadStructures.Material.CONCRETE);
      var e=Reference432Raster.structures(List.of(p),null);var a=RoadRaster.structures(List.of(p),null);check(a.size()==e.size(),"part size "+i);
      for(var en:e.entrySet())check(convert(en.getValue()).equals(a.get(new RoadRaster.Cell(en.getKey().x(),en.getKey().y(),en.getKey().z()))),"part exact "+i+" "+en);
    }
    // Near boundary slivers must use the unchanged triangle area threshold.
    for(double d:new double[]{1e-12,1e-9,1e-6,1e-4,.125,.249999999999}){
      var m=road(0,d,d,6+d,4+d,80,0);check(RoadRaster.raster(m).equals(ref(m)),"sliver fallback "+d);
    }
    for(int i=0;i<1500;i++){
      var list=new ArrayList<RoadRaster.Box>();for(int x=0;x<8;x++)for(int z=0;z<8;z++)if(random.nextBoolean()){
        double low=i%2==0?0:(z/8.*.05+(i%3==0?random.nextDouble()*1e-9:0));list.add(new RoadRaster.Box(x/8.,low,z/8.,(x+1)/8.,low+.5,(z+1)/8.));}
      Collections.shuffle(list,random);check(RoadRaster.compact(list).equals(Performance431Validation.oldCompact(list)),"compact exact/tolerance "+i);
    }
    System.out.println("Performance432Validation exact raster/structure/compaction checks="+checks+" PASS");
  }
}
