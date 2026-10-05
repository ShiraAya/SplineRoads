package com.sora.splineroads;

import com.sora.splineroads.core.*;
import com.sora.splineroads.core.RoadGeometry.*;
import com.sora.splineroads.core.JunctionSpec.*;
import com.sora.splineroads.core.RoadStructures.*;
import java.util.*;
import java.nio.file.*;
import java.awt.image.BufferedImage;
import javax.imageio.ImageIO;

/** Coverage sampled independently from the sidewalk raster, including fractional grid edges. */
public final class Revision232Validation {
  static int checks,sharedHeads,standaloneHeads;
  static void check(boolean ok,String why){checks++;if(!ok)throw new AssertionError(why);}
  static JunctionSpec fixture(boolean left,double... angles) {
    var old=Junction22Validation.fixture(Style.O2_YELLOW,left,angles);var arms=new ArrayList<Arm>();
    for(int i=0;i<old.arms().size();i++) {
      Arm a=old.arms().get(i);Style style=new Style[]{Style.O2_YELLOW,Style.O4_GREEN,Style.O6_RAIL}[i%3];
      var options=a.external().options().extras(true,true,true).sidewalk(RoadSidewalks.Config.DEFAULT.enabled(true));
      var external=new Settings(Mode.STRAIGHT,style,RoadProfile.width(style,options,3.5),1,.35,90).options(options);
      arms.add(JunctionSpec.arm(a.endpoint(),a.inward(),external,true,a.phase()));
    }
    return old.arms(arms);
  }
  static void signals(JunctionSpec spec,JunctionPlanner.Plan plan) {
    var all=plan.pieces().stream().flatMap(p->p.structures().stream()).toList();
    var posts=all.stream().filter(p->p.material()==Material.CB_POST).toList();
    for(var piece:plan.pieces())if(piece.arm()>=0){
      int index=piece.arm();Arm arm=spec.arms().get(index);Mesh mesh=piece.mesh();
      double overlap=spec.kind()==Kind.ROUNDABOUT?spec.islandRadius()+spec.ringLanes()*spec.ringLaneWidth()-mesh.last().center().sub(spec.center()).horizontalLength():0;
      Sample wait=RoadStructures.sample(mesh,mesh.length()-overlap-arm.crossingSetback()+.65);V forward=wait.left().left().mul(-1);
      for(Part head:piece.structures())if(head.material()==Material.SIGNAL_PEDESTRIAN){
        V front=head.b().sub(head.a()).horizontalUnit(),base=head.a().sub(front.mul(.19)).sub(new V(0,2.3,0));
        var pole=posts.stream().filter(p->p.a().add(p.b()).mul(.5).distance(base)<1e-6).findFirst();
        check(pole.isPresent(),"pedestrian housing has a physical post at its rear mount");
        check(Math.abs(front.dot(forward))<1e-6,"pedestrian face looks across the crossing");
        for(double x:new double[]{-.4,0,.4})for(double z:new double[]{-.4,0,.4}) {
          V origin=base.add(new V(x,2,z));
          check(plan.pieces().stream().noneMatch(p->Double.isFinite(RoadQueries.ray(p.mesh(),origin,new V(0,-1,0),4))),"pedestrian base has no actual deck beneath it");
        }
        if(pole.orElseThrow().height()>6) {sharedHeads++;check(base.distance(wait.center())<arm.width()/2+arm.crossingWidth()+4,"shared mast remains near same crossing");}
        else {standaloneHeads++;check(Math.abs(base.sub(wait.center()).dot(forward))<1e-5,"independent post is .65 block beyond intersection-side crossing edge");}
      }
    }
  }
  static Set<RoadSidewalks.Cell> walks(JunctionPlanner.Plan plan) {
    var cells=new HashSet<>(plan.sidewalks().keySet());for(var piece:plan.pieces())if(piece.arm()>=0)cells.addAll(RoadSidewalks.cells(piece.mesh(),piece.mesh().settings().options().sidewalk()));return cells;
  }
  static void corners(JunctionPlanner.Plan plan) {
    var cells=walks(plan);var boundary=plan.boundary();double area=0;
    for(int i=0;i<boundary.size();i++){V a=boundary.get(i),b=boundary.get((i+1)%boundary.size());area+=a.x()*b.z()-b.x()*a.z();}
    for(int i=0;i<boundary.size();i++) {
      V a=boundary.get(i),b=boundary.get((i+1)%boundary.size()),middle=a.add(b).mul(.5);
      if(plan.pieces().stream().filter(p->p.arm()>=0).anyMatch(p->RoadQueries.contains(p.mesh(),middle,.02,.1)))continue;
      V normal=b.sub(a).horizontalUnit().left().mul(area>0?-1:1);
      for(double t:new double[]{.15,.5,.85})for(double width:new double[]{.15,.8,2.5,4.8}){
        V at=a.mul(1-t).add(b.mul(t)).add(normal.mul(width));
        if(plan.pieces().stream().anyMatch(p->RoadQueries.contains(p.mesh(),at,.02,.2)))continue;
        var cell=new RoadSidewalks.Cell((int)Math.floor(at.x()),63,(int)Math.floor(at.z()));
        check(cells.contains(cell),"continuous five-block corner band missing "+cell+" at edge "+i);
      }
    }
  }
  static void picture(JunctionPlanner.Plan plan,String name)throws Exception {
    var preview=JunctionPreview.render(plan,900,900,0,0,false);var image=new BufferedImage(900,900,BufferedImage.TYPE_INT_ARGB);
    image.setRGB(0,0,900,900,preview.pixels(),0,900);ImageIO.write(image,"png",Path.of("build/validation232",name+".png").toFile());
  }
  public static void main(String[] args)throws Exception {
    Files.createDirectories(Path.of("build/validation232"));
    for(double offset:new double[]{-80.5,-80.25,.5,.73})for(double angle:new double[]{0,.37,1.19})for(int width:new int[]{1,5,15}) {
      V f=new V(Math.sin(angle),0,Math.cos(angle));var options=RoadProfile.Options.DEFAULT.sidewalk(RoadSidewalks.Config.DEFAULT.enabled(true).width(width));
      var settings=new Settings(Mode.STRAIGHT,Style.O2_YELLOW,9,1,.35,90).options(options);
      Mesh m=RoadGeometry.build(new Node(new V(offset,64,offset),0,0),new Node(new V(offset,64,offset).add(f.mul(60)),0,0),settings);
      var cells=new HashSet<>(RoadSidewalks.cells(m,options.sidewalk()));
      for(double at=2;at<58;at+=.37)for(int side:new int[]{-1,1})for(double across:new double[]{.05,.75,width-.05}){
        V p=RoadStructures.sample(m,at).at(side*(4.5+across),0);
        check(cells.contains(new RoadSidewalks.Cell((int)Math.floor(p.x()),63,(int)Math.floor(p.z()))),"requested width has no center-sampling gap at offset "+offset+" angle "+angle);
      }
    }
    double[][] layouts={{0,60},{0,90},{0,180},{0,245},{0,120,240},{0,90,230},{0,90,180,270},{10,95,200,285}};
    for(boolean left:new boolean[]{false,true})for(var layout:layouts) {
      var spec=fixture(left,layout);var plan=JunctionPlanner.plan(spec);signals(spec,plan);corners(plan);
      if(!left&&layout.length>=3)picture(plan,"junction-"+layout.length+"-"+(int)layout[0]);
    }
    var base=fixture(false,0,90,180,270);
    var ring=new JunctionSpec(base.center(),Kind.ROUNDABOUT,false,6,36,3,4,1,Control.SIGNALS,20,3,1,0,true,true,base.arms());
    var round=JunctionPlanner.plan(ring);corners(round);signals(ring,round);picture(round,"roundabout");
    check(round.sidewalks().keySet().stream().noneMatch(c->new V(c.x()+.5,64,c.z()+.5).sub(ring.center()).horizontalLength()<ring.islandRadius()),"outside sidewalk does not fill central island");
    var disabled=base.arms(base.arms().stream().map(a->a.node(a.endpoint(),a.inward(),a.external().options(a.external().options().sidewalk(RoadSidewalks.Config.DEFAULT)))).toList());
    check(JunctionPlanner.plan(disabled).sidewalks().isEmpty(),"disabled sidewalks leave corners untouched");
    check(sharedHeads>0&&standaloneHeads>0,"both shared and independent pedestrian masts exercised");
    System.out.printf("Revision 0.23.2: %d checks passed; 36 sidewalk footprints, 17 junction layouts, %d shared and %d independent pedestrian heads.%n",checks,sharedHeads,standaloneHeads);
  }
}
