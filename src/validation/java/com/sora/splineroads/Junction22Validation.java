package com.sora.splineroads;

import com.sora.splineroads.core.*;
import com.sora.splineroads.core.JunctionSpec.*;
import com.sora.splineroads.core.RoadGeometry.*;
import com.sora.splineroads.core.RoadStructures.*;
import java.util.*;
import java.nio.file.*;
import java.awt.image.BufferedImage;
import javax.imageio.ImageIO;

/** Regression cases from the twelve 0.22 screenshots; runs without Minecraft. */
public final class Junction22Validation {
  static int checks;
  static void check(boolean ok,String message){checks++;if(!ok)throw new AssertionError(message);}
  public static JunctionSpec fixture(Style style,boolean left,double... degrees) {
    var arms=new ArrayList<Arm>();
    for(int i=0;i<degrees.length;i++) {
      double angle=Math.toRadians(degrees[i]);V f=new V(-Math.cos(angle),0,-Math.sin(angle));
      var options=new RoadProfile.Options(left,false,false,true,.5,0);
      Settings settings=new Settings(Mode.STRAIGHT,style,RoadProfile.width(style,options,3.5),1,.35,90).options(options);
      arms.add(JunctionSpec.arm(new Node(f.mul(-100).add(new V(0,64,0)),Math.toDegrees(Math.atan2(-f.x(),f.z())),0),f,settings,true,i));
    }
    return new JunctionSpec(new V(0,64,0),Kind.INTERSECTION,left,6,18,2,4,1,Control.SIGNALS,20,3,1,0,true,true,arms);
  }
  static boolean contains(JunctionPlanner.Plan p,V v){return p.pieces().stream().anyMatch(x->RoadQueries.contains(x.mesh(),v,.02,.1));}
  static void png(JunctionPlanner.Plan plan,String name,boolean movement)throws Exception {
    var view=JunctionPreview.render(plan,640,640,0,0,movement);var image=new BufferedImage(640,640,BufferedImage.TYPE_INT_ARGB);
    image.setRGB(0,0,640,640,view.pixels(),0,640);ImageIO.write(image,"png",Path.of("build/validation22",name+".png").toFile());
    check(view.labels().size()==plan.pieces().stream().filter(p->p.arm()>=0).count(),"all approach labels appear");
  }
  public static void main(String[] args)throws Exception {
    Files.createDirectories(Path.of("build/validation22"));
    for(double angle:new double[]{35,60,90,120,150,180,210,270,325})for(Style style:new Style[]{Style.O2_YELLOW,Style.O4_GREEN,Style.O6_RAIL})for(boolean left:new boolean[]{false,true}) {
      var spec=fixture(style,left,0,angle);var plan=JunctionPlanner.plan(spec);
      for(var move:plan.movements())for(int k=1;k<move.path().size()-1;k++) {
        V p=move.path().get(k),side=move.path().get(k+1).sub(move.path().get(k-1)).horizontalUnit().left();
        check(contains(plan,p),"two-road center path remains on pavement "+angle+" "+style+" "+left);
        for(double d:new double[]{-.75,.75})check(contains(plan,p.add(side.mul(d))),"two-road vehicle footprint on pavement "+angle+" "+style);
      }
      if(angle==90&&style==Style.O4_GREEN&&!left)png(plan,"two-road-corner",false);
    }
    var spec=fixture(Style.O4_GREEN,false,0,90,180,270);var plan=JunctionPlanner.plan(spec);
    png(plan,"junction-preview",false);png(plan,"selected-lane-preview",true);
    for(int i=0;i<spec.arms().size();i++) {
      var piece=plan.pieces().get(i);var arm=spec.arms().get(i);var mesh=piece.mesh();V inward=mesh.last().left().left().mul(-1);
      var vehicle=piece.structures().stream().filter(p->p.material()==Material.SIGNAL_VEHICLE).toList();
      check(vehicle.size()==1,"one far-side vehicle head per incoming approach");
      Part head=vehicle.get(0);
      check(head.a().sub(spec.center()).dot(inward)>JunctionPlanner.radius(spec),"vehicle light beyond opposite mouth");
      check(head.b().sub(head.a()).horizontalUnit().dot(inward)<-.99,"vehicle face points toward approaching driver");
      check(piece.structures().stream().filter(p->p.material()==Material.SIGNAL_PEDESTRIAN).count()==2,"pedestrian heads on both crossing ends");
      for(Part part:piece.structures())if(part.material()==Material.CB_BASE)check(Math.abs(part.a().y()-64)<.0001,"pole foot stands outside curb on ground");
      var faces=RoadJunctionSignalModel.faces(head,false,0);var vertices=faces.stream().flatMap(f->f.points().stream()).toList();
      double height=vertices.stream().mapToDouble(V::y).max().orElseThrow()-vertices.stream().mapToDouble(V::y).min().orElseThrow();
      check(height>1.3,"original CB vertical head height");
      check(!RoadJunctionSignalModel.faces(head,true,0).isEmpty(),"authored CB gray signal lenses present");
      check(piece.structures().stream().anyMatch(p->p.material()==Material.CONCRETE&&p.a().distance(p.b())>arm.median()-.4&&p.a().distance(p.b())<arm.median()+.4),"green median has end curb");
      double end=mesh.length()-arm.crossingSetback()-arm.crossingWidth()-.5;
      double soilEnd=piece.structures().stream().filter(p->p.material()==Material.SOIL).mapToDouble(p->p.b().sub(mesh.first().center()).dot(inward)).max().orElseThrow();
      check(Math.abs(soilEnd-end)<.01,"green median fill reaches its end cap without gap");
    }
    boolean green=false;
    var clock=plan.signals();
    for(long t=0;t<20*200;t++)for(int i=0;i<4;i++) {
      int ped=clock.pedestrian(i,t);if(ped==1)green=true;
      check(ped==(clock.vehicle(clock.pedestrianVehicleArm(i),t)==0?1:0),"pedestrian head opposes its local vehicle head");
    }
    check(green,"pedestrian green occurs during local vehicle red");
    var rail=JunctionPlanner.plan(fixture(Style.O6_RAIL,false,0,90,180,270));
    check(rail.pieces().stream().flatMap(p->p.structures().stream()).noneMatch(p->p.material()==Material.SOIL),"rail medians never create soil");
    var ring=new JunctionSpec(spec.center(),Kind.ROUNDABOUT,false,6,18,2,4,1,Control.YIELD,20,3,1,0,true,true,spec.arms());
    var open=JunctionPlanner.plan(ring);var guarded=JunctionPlanner.plan(ring.outerRail(true));png(guarded,"roundabout-preview",false);
    var island=open.pieces().get(4).structures();
    for(int i=0;i<360;i++){double a=Math.toRadians(i);V p=ring.center().add(new V(Math.cos(a),0,Math.sin(a)).mul(ring.islandRadius()-.15));check(island.stream().anyMatch(b->b.material()==Material.CONCRETE&&segmentDistance(p,b.a(),b.b())<.08),"continuous central island curb at "+i);}
    check(guarded.pieces().get(4).structures().size()>island.size(),"outer guardrail toggle affects ring");
    for(Part part:guarded.pieces().get(4).structures())if(part.material()!=Material.SOIL&&part.material()!=Material.GREEN&&part.material()!=Material.CONCRETE) {
      V at=part.a().add(part.b()).mul(.5);for(int i=0;i<4;i++)check(!RoadQueries.contains(open.pieces().get(i).mesh(),at,.05,.1),"outer rail does not block a connected entry");
    }
    // Arrow geometry scales uniformly even at the minimum supported lane width.
    for(int mask:new int[]{1,2,4,3,5,6,7,8,15}) {
      var full=new ArrayList<RoadSurface.Face>();var small=new ArrayList<RoadSurface.Face>();
      JunctionPaint.arrow(full,new V(0,0,0),new V(0,0,1),mask,1);JunctionPaint.arrow(small,new V(0,0,0),new V(0,0,1),mask,.55);
      check(full.size()==small.size(),"scaled arrow topology stable");
      for(int i=0;i<full.size();i++)for(int j=0;j<full.get(i).points().size();j++)check(full.get(i).points().get(j).mul(.55).distance(small.get(i).points().get(j))<1e-8,"arrow heads and strokes share scale");
    }
    var single=JunctionPlanner.plan(fixture(Style.O2_YELLOW,false,0,90,180,270));
    check(single.pieces().stream().filter(p->p.arm()<0).allMatch(p->p.paint().isEmpty()),"single-lane junction has no crossing trajectory web");
    var legacy=new JunctionPlanner.Ref(spec,4,21).get();
    check(legacy.mesh()!=null,"legacy geometry references remain readable");
    System.out.println("Junction 0.22: "+checks+" checks passed; 54 two-road fixtures, signals, curbs, rail toggle, arrows and four preview renders");
  }
  static double segmentDistance(V p,V a,V b){V d=b.sub(a);double t=Math.max(0,Math.min(1,p.sub(a).dot(d)/d.dot(d)));return p.distance(a.add(d.mul(t)));}
}
