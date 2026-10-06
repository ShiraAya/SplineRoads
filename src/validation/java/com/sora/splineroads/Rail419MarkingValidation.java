package com.sora.splineroads;
import com.sora.splineroads.core.*;
import com.sora.splineroads.core.RoadGeometry.*;
import java.util.*;
/** Actual generated painted faces, not only a policy helper. No GPU/world execution. */
public final class Rail419MarkingValidation {
 static int checks,cases,continuous,dashed;
 static void check(boolean v,String why){checks++;if(!v)throw new AssertionError(why);}
 static boolean paintAt(RoadSurface.Geometry geometry,V p){return geometry.markings().stream().anyMatch(f->JunctionPaint.inside(f.points(),p));}
 static Mesh override(Mesh raw,int divider,RoadLaneLines.Pattern pattern){var o=raw.settings().options();var edits=RoadLaneLines.with(o.laneLines(),new RoadLaneLines.Edit("divider:"+divider,pattern,.12));var m=RoadRibbon.mesh(raw.samples(),raw.settings().options(o.laneLines(edits)));return LaneSections.apply(m);}
 public static void main(String[] args){
  for(var style:List.of(Style.O3_ONE,Style.O4_ONE,Style.O6_RAIL,Style.H6_RAIL))for(boolean left:new boolean[]{false,true})for(boolean curve:new boolean[]{false,true}){
   var o=RoadProfile.Options.DEFAULT.traffic(left);var settings=new Settings(Mode.STRAIGHT,style,RoadProfile.width(style,o,4),1,.35,90).options(o);var samples=new ArrayList<Sample>();
   for(double d=0;d<=200;d+=.25){double angle=curve?d/1200:0;V center=curve?new V(100000+1200*(1-Math.cos(angle)),300+d*.01,-100000+1200*Math.sin(angle)):new V(100000,300+d*.01,-100000+d);samples.add(new Sample(center,new V(Math.cos(angle),0,-Math.sin(angle)),d,settings.width()/2));}
   var original=RoadRibbon.mesh(samples,settings);
   for(int slot=0;slot<RoadProfile.catalog(style).lanes();slot++){
    var lane=LanePoints.lane(original,100,slot);int sign=lane.sign();var cut=new LaneSections.Cut(new UUID(419,slot+1),slot,sign,sign>0?50:150,sign>0?150:50,32,null,true,true,true);
    var raw=RoadRibbon.mesh(original.samples(),settings.options(o.lanePoints(o.lanePoints().cuts(List.of(cut)))));var mesh=LaneSections.apply(raw);var geo=RoadSurface.build(mesh,List.of(),List.of());cases++;
    // Choose an actual sample interval in the default six-block dash OFF phase.
    for(int i=1;i<mesh.samples().size();i++){var a=mesh.samples().get(i-1);var b=mesh.samples().get(i);double d=(a.distance()+b.distance())/2;if(d<100||d>101||d%6<3.7)continue;
     var sample=RoadStructures.sample(mesh,d);var layout=RoadProfile.layout(mesh,sample);var lines=layout.dividers();
     for(int j=0;j<lines.size();j++){double lateral=lines.get(j);boolean lo=LaneDeck.present(mesh,sample,lateral-.025,0),hi=LaneDeck.present(mesh,sample,lateral+.025,0);boolean boundary=lo!=hi;
      check(RoadSurface.closedSlotBoundary(mesh,d,lateral)==boundary,"closed-boundary helper doesn't follow actual strip material");
      if(boundary){V p=sample.at(lateral+(lo?-.025:.025),0);check(paintAt(geo,p),"dash gap remains on live edge beside closed slot: "+style+" slot "+slot+" line "+j);continuous++;
       check(!paintAt(RoadSurface.build(override(raw,j,RoadLaneLines.Pattern.NONE),List.of(),List.of()),p),"explicit hidden paint override ignored");
       check(!paintAt(RoadSurface.build(override(raw,j,RoadLaneLines.Pattern.WHITE_DASHED),List.of(),List.of()),p),"explicit dashed paint override ignored");
      }else if(lo&&hi){check(!paintAt(geo,sample.at(lateral,0)),"unrelated two-live-lane divider was made solid");dashed++;}
     }break;
    }
    var before=RoadStructures.sample(mesh,40.5);var first=RoadProfile.layout(mesh,before).dividers().get(0);check(!RoadSurface.closedSlotBoundary(mesh,40.5,first),"closed-boundary policy leaked outside actual reservation");
   }
  }
  check(continuous>0&&dashed>0,"test never exercised both boundary and two-live-lane markings");
  System.out.println("Rail419MarkingValidation: "+cases+" actual painted-face cases, "+checks+" checks, "+continuous+" closed boundaries / "+dashed+" live dash gaps; explicit overrides retained. NO GPU/world.");
 }
}
