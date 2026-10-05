package com.sora.splineroads;

import com.sora.splineroads.core.*;
import com.sora.splineroads.core.RoadGeometry.*;
import com.sora.splineroads.core.InterchangePlanner.*;
import java.util.*;

/** Tests physical topology and actual paint, rather than only movement labels. */
public final class Revision26Validation {
  static int checks;
  static void check(boolean value,String reason){checks++;if(!value)throw new AssertionError(reason);}
  static String key(V p){return Math.round(p.x()*1e5)+","+Math.round(p.y()*1e5)+","+Math.round(p.z()*1e5);}
  public static Node[] nodes(int count,double radius,boolean staggered){
    double[] angle=count==5?new double[]{0,180,90,270,45}:new double[]{0,180,60,240,120,300};
    double[] height=staggered?new double[]{2,8,15}:new double[]{64,64,64};Node[] out=new Node[count];
    for(int i=0;i<count;i++){double a=Math.toRadians(angle[i]);out[i]=new Node(new V(Math.cos(a)*radius,height[i/2],Math.sin(a)*radius),0,0);}return out;
  }
  public static Plan plan(int n,int lanes,boolean left,Style style,boolean staggered){
    Settings s=new Settings(Mode.STRAIGHT,style,style.defaultWidth(),1,.4,90);
    return MultiInterchange.plan(nodes(n,640,staggered),new Settings[]{s,s,s},new Options(n==5?Preset.DIRECTIONAL_FIVE:Preset.DIRECTIONAL_SIX,left,lanes,96,20,5,2,0,true,true,0),staggered?2:64);
  }
  static void topology(Plan plan,int n,int lanes,boolean left){
    Map<String,Set<String>> next=new HashMap<>(),previous=new HashMap<>();Set<String> edges=new HashSet<>();Set<Long> chunks=new HashSet<>();
    var samples=new ArrayList<Sample>();
    for(Leg leg:plan.legs()){
      Mesh mesh=leg.mesh();chunks.addAll(RoadCoverage.chunks(mesh,2));check(RoadGrades.maximum(mesh)<=.150001,"maximum grade");
      if(!mesh.settings().style().ramp())continue;
      check(!MultiFanPaint.applies(mesh),"new binary trees use established fork/shoulder paint");
      samples.addAll(mesh.samples());
      check(RoadProfile.catalog(mesh.settings().style()).lanes()==lanes,"every intermediate collector retains selected lane count");
      for(int k=1;k<mesh.samples().size();k++){
        String a=key(mesh.samples().get(k-1).center()),b=key(mesh.samples().get(k).center());
        check(edges.add(a+">"+b),"shared trunk is stored only once");next.computeIfAbsent(a,x->new HashSet<>()).add(b);previous.computeIfAbsent(b,x->new HashSet<>()).add(a);
      }
    }
    check(chunks.size()<=RoadLimits.MAX_MULTI_INTERCHANGE_WORK_CHUNKS,"buildable chunk budget");
    check(next.values().stream().allMatch(v->v.size()<=2),"no three/four-way simultaneous split");
    check(previous.values().stream().allMatch(v->v.size()<=2),"no three/four-way simultaneous merge");
    check(next.values().stream().filter(v->v.size()==2).count()>=n*2,"both fork stages exist");
    var roots=new ArrayList<Set<String>>();var tails=new ArrayList<Set<String>>();
    for(int arm=0;arm<n;arm++){
      roots.add(new HashSet<>());tails.add(new HashSet<>());V radial=plan.anchors().get(arm).position().sub(plan.center()).horizontalUnit();
      double radius=plan.anchors().get(arm).position().sub(plan.center()).horizontalLength()*(n==5&&arm==4?.86:.97);
      for(Sample sample:samples){V delta=sample.center().sub(plan.center());if(Math.abs(delta.dot(radial)-radius)>.001)continue;
        double side=delta.dot(radial.mul(-1).left())*(left?-1:1);(side>0?roots:tails).get(arm).add(key(sample.center()));
        if(!(n==5&&arm==4)){
          Mesh main=plan.legs().get(arm/2).mesh();var p=RoadQueries.horizontal(main,sample.center());var layout=RoadProfile.layout(main,p.sample());
          check(Math.abs(p.lateral())-sample.halfWidth()>=Math.abs(layout.outer(1))-.01,"mainline attachment occupies shoulder, never a traffic lane");
          check(Math.abs(sample.halfWidth()*2-layout.shoulderWidth())<.01,"highway attachment width equals shoulder");
        }
      }
      check(roots.get(arm).size()==1&&tails.get(arm).size()==1,"one common departure and arrival per arm");
    }
    for(int arm=0;arm<n;arm++){
      Set<String> seen=new HashSet<>();var todo=new ArrayDeque<>(roots.get(arm));
      while(!todo.isEmpty()){String at=todo.remove();if(seen.add(at))todo.addAll(next.getOrDefault(at,Set.of()));}
      for(int target=0;target<n;target++)if(target/2!=arm/2)check(tails.get(target).stream().anyMatch(seen::contains),"every destination remains reachable through physical tree");
    }
    System.out.printf(Locale.ROOT,"TREE n=%d lanes=%d left=%s radius=%.1f height=%.3f chunks=%d%n",n,lanes,left,plan.anchors().get(0).position().sub(plan.center()).horizontalLength(),plan.highest(),chunks.size());
  }
  static void paint(Plan plan,int n){
    var all=plan.legs().stream().map(l->RoadRenderMesh.simplify(l.mesh())).toList();int arm=n==5?4:0,faces=0;
    for(int i=3;i<all.size();i++){
      Leg leg=plan.legs().get(i);if(leg.from()!=arm&&leg.to()!=arm)continue;
      Mesh mesh=all.get(i);var neighbors=new ArrayList<>(all);neighbors.remove(i);
      var marks=RoadJunction.markings(mesh,all.subList(0,i),neighbors,true);
      for(var mark:marks){faces++;for(V v:mark.points())check(all.stream().anyMatch(peer->RoadQueries.contains(peer,v,.05,.3)),"guide vertex stays on actual pavement");}
      var reverse=new ArrayList<>(neighbors);Collections.reverse(reverse);
      check(RoadJunction.dividerZones(mesh,neighbors).size()==RoadJunction.dividerZones(mesh,reverse).size(),"fork detection independent of neighbor order");
    }
    check(faces>100,"actual binary guides generated");
  }
  public static void main(String[] args){
    for(int n:new int[]{5,6})for(int lanes:new int[]{1,2})for(boolean left:new boolean[]{false,true}){
      Plan p=plan(n,lanes,left,Style.H4_RAIL,false);topology(p,n,lanes,left);if(lanes==2)paint(p,n);
    }
    for(int n:new int[]{5,6})for(Style style:new Style[]{Style.H4_RAIL,Style.H6_RAIL}){
      Plan p=plan(n,2,false,style,true);topology(p,n,2,false);check(p.highest()<54,"local elevations improve reported 2/8/15 fixture");
    }
    Plan single=plan(6,1,false,Style.H4_RAIL,true);topology(single,6,1,false);check(single.highest()<47.5,"reported single-ramp fixture is lower than 0.25.2");
    System.out.println("Revision26 PASS "+checks+" checks");
  }
}
