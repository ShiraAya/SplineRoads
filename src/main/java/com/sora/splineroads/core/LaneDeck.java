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
  private static final WeakIdentityCache<Mesh,List<Integer>> SLOT_ORDER=new WeakIdentityCache<>(256,8192,List::size);
  private static List<Integer> slots(Mesh mesh){return SLOT_ORDER.get(mesh,LaneDeck::slotOrder);}
  private static List<Integer> slotOrder(Mesh mesh){
    var raw=LaneSections.reference(mesh);double d=(raw.first().distance()+raw.last().distance())/2;
    var out=new ArrayList<Integer>();for(var cut:mesh.settings().options().lanePoints().cuts())if(cut.temporary()&&!out.contains(cut.lane()))out.add(cut.lane());
    var at=RoadStructures.sample(raw,d);out.sort(Comparator.comparingDouble(slot->LanePoints.lane(raw,d,slot).position().sub(at.center()).dot(at.left())));return List.copyOf(out);
  }
  private static List<Span> holes(Mesh mesh,Sample sample){
    // Terminal faces use the interior cross-section. A rectangular cut is open
    // at its exact mathematical endpoint, but that must not restore a paper-thin
    // wall across a lane which remains closed right up to the road end.
    double probe=sample.distance();
    if(Math.abs(probe-mesh.first().distance())<1e-7)probe+=1e-5;
    if(Math.abs(probe-mesh.last().distance())<1e-7)probe-=1e-5;
    return holes(mesh,sample,probe);
  }
  private static List<Span> holes(Mesh mesh,Sample sample,double interval){
    var raw=LaneSections.reference(mesh);var holes=new ArrayList<Span>();
    for(int slot:slots(mesh)){
      double removed=0;for(var cut:mesh.settings().options().lanePoints().cuts())if(cut.temporary()&&cut.lane()==slot)removed=Math.max(removed,cut.removed(cut.rectangular()||cut.arrival()&&Math.abs(sample.distance()-cut.end())<1e-7?interval:sample.distance()));
      var lane=LanePoints.lane(raw,sample.distance(),slot);
      double center=lane.position().sub(sample.center()).dot(sample.left()),half=lane.width()*removed*(slot>=8?LaneAdditions.find(raw,slot).fraction(sample.distance()):1)/2;
      double lo=Math.max(-sample.halfWidth(),Math.min(sample.halfWidth(),center-half));
      double hi=Math.max(lo,Math.min(sample.halfWidth(),center+half));
      boolean rectangular=mesh.settings().options().lanePoints().cuts().stream().anyMatch(c->c.lane()==slot&&c.rectangular()&&c.removed(interval)>.999);
      if(rectangular&&half>1e-7){
        boolean lowOuter=true,highOuter=true;
        for(int other:LaneAdditions.slots(raw,sample.distance()))if(other!=slot){
          double x=LanePoints.lane(raw,sample.distance(),other).position().sub(sample.center()).dot(sample.left());
          if(x<center-1e-6)lowOuter=false;if(x>center+1e-6)highOuter=false;
        }
        // Remove only the redundant shoulder. A separate cycle lane still needs
        // its deck and outer protection when the adjacent motor lane closes.
        var layout=RoadProfile.layout(raw,sample);
        if(lowOuter&&(layout.cycleWidth()<.01||!layout.catalog().twoWay()&&layout.outside()>0))lo=-sample.halfWidth();
        if(highOuter&&(layout.cycleWidth()<.01||!layout.catalog().twoWay()&&layout.outside()<0))hi=sample.halfWidth();
      }
      holes.add(new Span(lo,hi,false,false));
    }return holes;
  }
  /** A physically reserved outer slot is not an intact outer road boundary. */
  public static boolean outerOpening(Mesh mesh,double station,int side){
    var sample=RoadStructures.sample(mesh,station);
    return !present(mesh,sample,side*(sample.halfWidth()-.025),0);
  }
  public static List<Span> spans(Mesh mesh,Sample sample){
    double probe=sample.distance();
    if(Math.abs(probe-mesh.first().distance())<1e-7)probe+=1e-5;
    if(Math.abs(probe-mesh.last().distance())<1e-7)probe-=1e-5;
    return spans(mesh,sample,probe);
  }
  private static List<Span> spans(Mesh mesh,Sample sample,double interval){
    if(!hasOpenings(mesh))return List.of(new Span(-sample.halfWidth(),sample.halfWidth(),true,true));
    var out=new ArrayList<Span>();double low=-sample.halfWidth();boolean wall=true;
    for(var hole:holes(mesh,sample,interval)){
      double high=Math.max(low,hole.low());boolean open=hole.high()-hole.low()>1e-7;
      out.add(new Span(low,high,wall,open));low=Math.max(low,hole.high());wall=open;
    }
    out.add(new Span(low,sample.halfWidth(),wall,true));return out;
  }
  public static List<Strip> strips(Mesh mesh,Sample a,Sample b){
    double probe=(a.distance()+b.distance())/2;var aa=spans(mesh,a,probe);var bb=spans(mesh,b,probe);var result=new ArrayList<Strip>();
    for(int i=0;i<aa.size();i++){
      var x=aa.get(i);var y=bb.get(i);if(Math.max(x.high()-x.low(),y.high()-y.low())<1e-8)continue;
      result.add(new Strip(a.at(x.high(),0),a.at(x.low(),0),b.at(y.high(),0),b.at(y.low(),0),x.lowWall()||y.lowWall(),x.highWall()||y.highWall()));
    }return result;
  }
  public static List<List<V>> holeQuads(Mesh mesh,Sample a,Sample b){
    double probe=(a.distance()+b.distance())/2;var aa=holes(mesh,a,probe);var bb=holes(mesh,b,probe);var out=new ArrayList<List<V>>();
    for(int i=0;i<aa.size();i++){var x=aa.get(i);var y=bb.get(i);
      if(Math.max(x.high()-x.low(),y.high()-y.low())>1e-8)out.add(List.of(a.at(x.high(),0),a.at(x.low(),0),b.at(y.low(),0),b.at(y.high(),0)));
    }return out;
  }
  public record Cap(V a,V b){}
  /** Cut endpoints are one-sided cross sections. Close the transverse slab faces,
   * but do not add a wall where two adjacent closed intervals continue each other. */
  public static List<Cap> caps(Mesh mesh){
    var stations=new TreeSet<Double>();
    for(var cut:mesh.settings().options().lanePoints().cuts())if(cut.rectangular()){
      stations.add(cut.begin());stations.add(cut.end());
    }
    var result=new ArrayList<Cap>();
    for(double d:stations){
      if(d<=mesh.first().distance()+1e-7||d>=mesh.last().distance()-1e-7)continue;
      var sample=RoadStructures.sample(mesh,d);
      var before=holes(mesh,sample,d-1e-5);var after=holes(mesh,sample,d+1e-5);
      var edges=new TreeSet<Double>();for(var h:before){edges.add(h.low());edges.add(h.high());}for(var h:after){edges.add(h.low());edges.add(h.high());}
      var boundaries=new ArrayList<>(edges);Double from=null;boolean opening=false;
      for(int i=1;i<boundaries.size();i++){
        double lo=boundaries.get(i-1),hi=boundaries.get(i),middle=(lo+hi)/2;
        boolean was=before.stream().anyMatch(h->middle>h.low()&&middle<h.high());
        boolean now=after.stream().anyMatch(h->middle>h.low()&&middle<h.high());
        if(from!=null&&(was==now||now!=opening)){V low=sample.at(from,0),high=sample.at(lo,0);result.add(new Cap(opening?low:high,opening?high:low));from=null;}
        if(was!=now&&from==null){from=lo;opening=now;}
      }
      if(from!=null){V low=sample.at(from,0),high=sample.at(boundaries.get(boundaries.size()-1),0);result.add(new Cap(opening?low:high,opening?high:low));}
    }
    return List.copyOf(result);
  }
  public static boolean present(Mesh mesh,Sample sample,double lateral,double margin){
    if(!hasOpenings(mesh))return true;
    for(var hole:holes(mesh,sample))if(hole.high()-hole.low()>1e-7&&lateral>hole.low()+Math.max(0,margin)+1e-8&&lateral<hole.high()-Math.max(0,margin)-1e-8)return false;
    return true;
  }
  /** Raster batches retain authored longitudinal stations and original slot axes.
   * RoadRibbon.split rebases stations and would reopen/close the wrong part of a long road. */
  public static List<Mesh> rasterPieces(Mesh mesh,double length){
    if(!hasOpenings(mesh)&&mesh.reference()==null&&mesh.settings().options().lanePoints().additions().isEmpty())return mesh.length()<=256?List.of(mesh):RoadRibbon.split(mesh,length);
    var out=new ArrayList<Mesh>();var points=mesh.samples();int start=0;
    for(int i=1;i<points.size();i++)if(points.get(i).distance()-points.get(start).distance()>=length||i==points.size()-1){
      var subset=List.copyOf(points.subList(start,i+1));
      var bounds=RoadRibbon.mesh(subset,mesh.settings());
      out.add(new Mesh(subset,mesh.settings(),bounds.min(),bounds.max(),mesh.length(),false,null,mesh.controls(),LaneSections.reference(mesh)));start=i;
    }return out;
  }
  /** Motor bands and the median, not shoulders which form part of the joining mouth. */
  public static Mesh motorOnly(Mesh host){
    var samples=new ArrayList<Sample>();
    for(var sample:host.samples()){
      var l=RoadProfile.layout(host,sample);samples.add(new Sample(sample.at(l.motorCenter(),0),sample.left(),sample.distance(),(l.motorMax()-l.motorMin())/2));
    }
    return new Mesh(List.copyOf(samples),host.settings(),host.min(),host.max(),host.length(),host.closed(),host.controlPoint(),host.controls(),LaneSections.reference(host));
  }
  public static Mesh excludingSlot(Mesh host,int slot){
    var cuts=new ArrayList<>(host.settings().options().lanePoints().cuts());
    cuts.add(new LaneSections.Cut(new UUID(0,slot+1),slot,1,-1024,host.length()+1024,2,null,true));
    var settings=host.settings().options(host.settings().options().lanePoints(host.settings().options().lanePoints().cuts(cuts)));
    return new Mesh(host.samples(),settings,host.min(),host.max(),host.length(),host.closed(),host.controlPoint(),host.controls(),LaneSections.reference(host));
  }
  private LaneDeck(){}
}
