package com.sora.splineroads;
import com.sora.splineroads.core.*;
import com.sora.splineroads.core.RoadGeometry.*;
import com.sora.splineroads.core.RoadStructures.*;
import com.sora.splineroads.core.InterchangePlanner.*;
import java.util.*;
public final class Revision252Validation {
 static int checks;
 static void check(boolean b,String message){checks++;if(!b)throw new AssertionError(message);}
 static Settings road(Style style){return new Settings(Mode.STRAIGHT,style,style.defaultWidth(),1,.4,90);}
 static Mesh mesh(Settings s){return RoadGeometry.build(new Node(new V(0,64,0),-90,0),new Node(new V(120,64,0),-90,0),s);}
 static void highways(){
  var enabled=RoadProfile.Options.DEFAULT.sidewalk(RoadSidewalks.Config.DEFAULT.enabled(true));
  for(Style style:new Style[]{Style.H4_RAIL,Style.H6_GREEN,Style.R1,Style.R2}){
   var s=road(style).options(enabled);check(!s.options().sidewalk().enabled(),"ineligible settings clear enabled flag");
   check(RoadSidewalks.cells(mesh(s),enabled.sidewalk()).isEmpty(),"stale explicit config cannot render highway/ramp sidewalks");
  }
  check(!RoadSidewalks.cells(mesh(road(Style.O4_YELLOW).options(enabled)),enabled.sidewalk()).isEmpty(),"ordinary sidewalks remain available");
  for(Style a:new Style[]{Style.O2_YELLOW,Style.O4_YELLOW,Style.O6_GREEN})for(Style b:new Style[]{Style.H4_RAIL,Style.H6_RAIL}){
   var x=road(a);var y=road(b);boolean valid=Math.abs(RoadProfile.catalog(a).lanes()-RoadProfile.catalog(b).lanes())<=2;
   check(RoadTransitions.compatible(x,y)==valid,"cross-family lane step limit");if(!valid)continue;
   Mesh first=mesh(RoadTransitions.join(x,null,y)),last=mesh(RoadTransitions.join(y,x,null));
   var p=RoadProfile.layout(first,first.last());var q=RoadProfile.layout(last,last.first());
   check(Math.abs(first.last().halfWidth()-last.first().halfWidth())<1e-8,"shared port width");
   check(Math.abs(p.motorMin()-q.motorMin())<1e-8&&Math.abs(p.motorMax()-q.motorMax())<1e-8,"shared driving corridor");
   check(p.dividers().equals(q.dividers()),"lane separators meet across family change");
  }
 }
 static void curbToHighway(){
  var urban=road(Style.O4_YELLOW).options(RoadProfile.Options.DEFAULT.extras(false,false,true));
  Mesh m=mesh(RoadTransitions.join(urban,null,road(Style.H4_RAIL)));double previous=1;
  for(double d=0;d<=m.length();d+=.5){var p=RoadStructures.sample(m,d);double width=RoadProfile.curbExtent(RoadProfile.layout(m,p),p,1);
   check(width<=previous+1e-8&&width<=1.0000001,"curb fades out without expanding into highway shoulder");previous=width;}
  check(previous==0,"highway seam has no raised curb");
 }
 static void clipping(){
  Settings s=RoadTransitions.join(road(Style.O6_GREEN),road(Style.O4_YELLOW),null);Mesh full=mesh(s);
  double from=13.25,to=109;var samples=new ArrayList<Sample>();
  for(double d=from;d<to;d+=.5)samples.add(RoadStructures.sample(full,d));samples.add(RoadStructures.sample(full,to));
  var settings=s.options(s.options().ends(s.options().ends().trim(from,full.length()-to)));
  Mesh cut=RoadRibbon.mesh(samples,settings);
  for(double d=0;d<cut.length();d+=.25){
   var old=RoadProfile.layout(full,RoadStructures.sample(full,d+from));var next=RoadProfile.layout(cut,RoadStructures.sample(cut,d));
   check(Math.abs(old.motorMax()-next.motorMax())<1e-6,"trimming preserves cross-section station");
   for(int i=0;i<old.dividers().size();i++)check(Math.abs(old.dividers().get(i)-next.dividers().get(i))<1e-6,"trimmed dividers stay aligned");
  }
 }
 static void curbs(){
  Settings narrow=road(Style.O2_YELLOW).options(RoadProfile.Options.DEFAULT.extras(false,false,true));
  Settings wide=road(Style.O4_YELLOW).options(narrow.options());Mesh m=mesh(RoadTransitions.join(narrow,wide,null));
  Ground ground=new Ground(){public double top(double x,double z,double y){return y;}public boolean blocked(Part p){return false;}public boolean joined(V p){return p.x()>30.3&&p.x()<36.7;}};
  var parts=RoadStructures.plan(m,ground);int count=0;boolean exactStart=false,exactEnd=false;
  for(Part p:parts)if(p.material()==Material.CONCRETE&&p.height()==.2){
   count++;for(V v:p.base()) {
    var q=RoadQueries.horizontal(m,v);check(Math.abs(q.lateral())<=q.sample().halfWidth()+1e-5,"curb never projects outside pavement");
    exactStart|=Math.abs(v.x()-30.3)<1e-4;exactEnd|=Math.abs(v.x()-36.7)<1e-4;
   }
  }
  check(count>0&&exactStart&&exactEnd,"curbs cut at exact openings, not 2m tiles");
  Ground tiny=new Ground(){public double top(double x,double z,double y){return y-10;}public boolean blocked(Part p){return false;}public boolean joined(V p){return p.x()>1.5;}};
  Mesh ramp=mesh(road(Style.R1));var rails=RoadStructures.plan(ramp,tiny);
  // Edge fascia/slab parts are legitimate non-pier structures at the raised root. The old
  // "any non-pier" predicate therefore stopped meaning "barrier" once edge slabs were added.
  java.util.function.Predicate<Part> rail=p->!p.pier()&&(p.material()==Material.STEEL||p.material()==Material.DARK_STEEL);
  check(rails.stream().noneMatch(rail),"auto removes isolated root barrier stubs");
  Mesh forced=mesh(road(Style.R1).options(RoadProfile.Options.DEFAULT.outerRail(RoadProfile.OuterRail.ON)));
  check(RoadStructures.plan(forced,tiny).stream().anyMatch(rail),"explicit rail ON preserves short exposed run");
 }
 // Multi-arm geometry is now covered by Revision26Validation.
 public static void main(String[] args){highways();curbToHighway();clipping();curbs();System.out.println("Revision252 PASS "+checks+" checks");}
}
