package com.sora.splineroads;
import com.sora.splineroads.core.*;
import com.sora.splineroads.core.RoadGeometry.*;
import com.sora.splineroads.core.JunctionSpec.*;
import com.sora.splineroads.core.RoadStructures.*;
import java.util.*;
public final class Junction23Validation {
 static int checks;static void check(boolean ok,String why){checks++;if(!ok)throw new AssertionError(why);}
 public static void main(String[] args)throws Exception {
  for(int mask:new int[]{2,4,3,5,6,7,15})for(double scale:new double[]{.55,1,1.5}){
   var paint=new ArrayList<RoadSurface.Face>();JunctionPaint.arrow(paint,new V(0,0,0),new V(0,0,1),mask,scale);
   // Every quadratic bend is made of strips whose adjacent edges share both vertices.
   int joined=0;for(int i=1;i<paint.size();i++){var a=paint.get(i-1).points();var b=paint.get(i).points();if(a.size()==4&&b.size()==4&&a.get(3).distance(b.get(0))<1e-8&&a.get(2).distance(b.get(1))<1e-8)joined++;}
   check(joined>=8,"curved arrow shares complete cross-sections, mask "+mask);
  }
  for(int width:new int[]{1,5,15})for(var side:RoadSidewalks.Side.values())for(int sign:new int[]{-1,1}) {
   var options=RoadProfile.Options.DEFAULT.sidewalk(new RoadSidewalks.Config(true,side,width,"minecraft:stone_bricks"));
   var settings=new Settings(Mode.STRAIGHT,Style.O2_YELLOW,9,1,.35,90).options(options);
   Mesh mesh=RoadGeometry.build(new Node(new V(sign*100+.5,64,sign*100+.5),0,0),new Node(new V(sign*100+.5,64,sign*100+60.5),0,0),settings);
   var walk=options.sidewalk();
   // Options may widen a requested sidewalk to reserve the tactile/streetscape rows.
   // Validate the effective saved width; Revision37 separately asserts the widening policy.
   check(walk.width()>=width,"effective sidewalk never narrows the requested width "+width+" -> "+walk.width());
   var cells=RoadSidewalks.cells(mesh,walk);check(cells.size()==walk.width()*60*(side==RoadSidewalks.Side.BOTH?2:1),"exact effective sidewalk width and length requested="+width+" effective="+walk.width()+" "+side+" "+cells.size());
   for(var c:cells)check(c.y()==63,"real blocks finish flush with deck");
   check(options.extras(true,true,true).route(options.routing().fit(false)).ends(RoadTransitions.Ends.NONE).sidewalk().equals(options.sidewalk()),"other road edits preserve sidewalk options");
  }
  for(boolean left:new boolean[]{false,true})for(Style style:new Style[]{Style.O2_YELLOW,Style.O4_GREEN,Style.O6_RAIL}) {
   var old=Junction22Validation.fixture(style,left,0,90,180,270);var arms=new ArrayList<Arm>();
   for(var a:old.arms()){var o=a.external().options().extras(true,true,true);var s=a.external().options(o);s=new Settings(s.mode(),style,RoadProfile.width(style,o,3.5),1,.35,90).options(o);arms.add(JunctionSpec.arm(a.endpoint(),a.inward(),s,true,a.phase()));}
   var spec=old.arms(arms);var plan=JunctionPlanner.plan(spec);
   for(int i=0;i<4;i++) {
    var a=arms.get(i);var piece=plan.pieces().get(i);var m=piece.mesh();V f=m.first().left().left().mul(-1);double stop=m.length()-a.crossingSetback()-a.crossingWidth()-a.stopGap();
    check(piece.paint().stream().noneMatch(face->face.color()==0x486D62),"no mismatched bicycle color");
    var bike=piece.paint().stream().filter(face->face.color()==0x536F61).toList();check(!bike.isEmpty(),"bike surfacing exists");
    double motor=RoadProfile.layout(a.external(),a.width()).motorMax();
    check(bike.stream().anyMatch(face->face.points().stream().anyMatch(p->Math.abs(p.sub(m.first().center()).dot(m.first().left())-motor-.08)<1e-7)),"bike color matches ordinary lane edge");
    for(var face:piece.paint())if(face.color()==JunctionPaint.WHITE&&face.points().size()==4){var ps=face.points();double min=ps.stream().mapToDouble(p->p.sub(m.first().center()).dot(m.first().left())).min().orElseThrow(),max=ps.stream().mapToDouble(p->p.sub(m.first().center()).dot(m.first().left())).max().orElseThrow();if(Math.abs(max-min-.15)<1e-6&&Math.abs((min+max)/2)<motor-.1)check(ps.stream().allMatch(p->p.sub(m.first().center()).dot(f)<=stop-.225+1e-6),"divider ends behind stop bar");}
    for(var p:piece.structures())if(p.material()==Material.CB_BASE){V base=p.a().add(p.b()).mul(.5);check(plan.pieces().stream().noneMatch(q->RoadQueries.contains(q.mesh(),base,.35,.6)),"pedestrian and vehicle poles outside whole road union");}
   }
  }
  System.out.println("Junction 0.23: "+checks+" checks passed (arrows, stop bars, cycle seams, poles, real-block sidewalks)");
 }
}
