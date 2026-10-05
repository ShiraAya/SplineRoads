package com.sora.splineroads.core;

import com.sora.splineroads.core.RoadGeometry.*;
import java.util.*;

/** Material bands of a road. Temporary interior cuts are real gaps, not a thinner
 * bounding ribbon or a hidden stripe. The same strips feed rendering, collision,
 * clearance and ray selection. DETACH keeps its original outer-ribbon rules. */
public final class LaneDeck {
  public record Span(double low,double high,boolean lowWall,boolean highWall){}
  public record Strip(V al,V ar,V bl,V br,boolean lowWall,boolean highWall){}
  public static boolean hasOpenings(Mesh mesh){return mesh.settings().options().lanePoints().cuts().stream().anyMatch(LaneSections.Cut::temporary);}
  private static List<Integer> slots(Mesh mesh){
    var raw=LaneSections.reference(mesh);double d=(raw.first().distance()+raw.last().distance())/2;
    var out=new ArrayList<Integer>();for(var cut:mesh.settings().options().lanePoints().cuts())if(cut.temporary()&&!out.contains(cut.lane()))out.add(cut.lane());
    var at=RoadStructures.sample(raw,d);out.sort(Comparator.comparingDouble(slot->LanePoints.lane(raw,d,slot).position().sub(at.center()).dot(at.left())));return out;
  }
  private static List<Span> holes(Mesh mesh,Sample sample){
    var raw=LaneSections.reference(mesh);var holes=new ArrayList<Span>();
    for(int slot:slots(mesh)){
      double removed=0;for(var cut:mesh.settings().options().lanePoints().cuts())if(cut.temporary()&&cut.lane()==slot)removed=Math.max(removed,cut.removed(sample.distance()));
      var lane=LanePoints.lane(raw,sample.distance(),slot);
      double center=lane.position().sub(sample.center()).dot(sample.left()),half=lane.width()*removed/2;
      double lo=Math.max(-sample.halfWidth(),Math.min(sample.halfWidth(),center-half));
      double hi=Math.max(lo,Math.min(sample.halfWidth(),center+half));holes.add(new Span(lo,hi,false,false));
    }return holes;
  }
  public static List<Span> spans(Mesh mesh,Sample sample){
    if(!hasOpenings(mesh))return List.of(new Span(-sample.halfWidth(),sample.halfWidth(),true,true));
    var out=new ArrayList<Span>();double low=-sample.halfWidth();boolean wall=true;
    for(var hole:holes(mesh,sample)){
      double high=Math.max(low,hole.low());boolean open=hole.high()-hole.low()>1e-7;
      out.add(new Span(low,high,wall,open));low=Math.max(low,hole.high());wall=open;
    }
    out.add(new Span(low,sample.halfWidth(),wall,true));return out;
  }
  public static List<Strip> strips(Mesh mesh,Sample a,Sample b){
    var aa=spans(mesh,a);var bb=spans(mesh,b);var result=new ArrayList<Strip>();
    for(int i=0;i<aa.size();i++){
      var x=aa.get(i);var y=bb.get(i);if(Math.max(x.high()-x.low(),y.high()-y.low())<1e-8)continue;
      result.add(new Strip(a.at(x.high(),0),a.at(x.low(),0),b.at(y.high(),0),b.at(y.low(),0),x.lowWall()||y.lowWall(),x.highWall()||y.highWall()));
    }return result;
  }
  public static List<List<V>> holeQuads(Mesh mesh,Sample a,Sample b){
    var aa=holes(mesh,a);var bb=holes(mesh,b);var out=new ArrayList<List<V>>();
    for(int i=0;i<aa.size();i++){var x=aa.get(i);var y=bb.get(i);
      if(Math.max(x.high()-x.low(),y.high()-y.low())>1e-8)out.add(List.of(a.at(x.high(),0),a.at(x.low(),0),b.at(y.low(),0),b.at(y.high(),0)));
    }return out;
  }
  public static boolean present(Mesh mesh,Sample sample,double lateral,double margin){
    if(!hasOpenings(mesh))return true;
    for(var hole:holes(mesh,sample))if(hole.high()-hole.low()>1e-7&&lateral>hole.low()+Math.max(0,margin)+1e-8&&lateral<hole.high()-Math.max(0,margin)-1e-8)return false;
    return true;
  }
  /** Raster batches retain authored longitudinal stations and original slot axes.
   * RoadRibbon.split rebases stations and would reopen/close the wrong part of a long road. */
  public static List<Mesh> rasterPieces(Mesh mesh,double length){
    if(!hasOpenings(mesh))return mesh.length()<=256?List.of(mesh):RoadRibbon.split(mesh,length);
    var out=new ArrayList<Mesh>();var points=mesh.samples();int start=0;
    for(int i=1;i<points.size();i++)if(points.get(i).distance()-points.get(start).distance()>=length||i==points.size()-1){
      var subset=List.copyOf(points.subList(start,i+1));
      var bounds=RoadRibbon.mesh(subset,mesh.settings());
      out.add(new Mesh(subset,mesh.settings(),bounds.min(),bounds.max(),mesh.length(),false,null,mesh.controls(),LaneSections.reference(mesh)));start=i;
    }return out;
  }
  private LaneDeck(){}
}
