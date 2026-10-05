package com.sora.splineroads;
import com.sora.splineroads.core.*;
import com.sora.splineroads.core.RoadGeometry.*;
import com.sora.splineroads.core.RoadStructures.*;
import com.sora.splineroads.core.InterchangePlanner.*;
import java.util.*;
public class Probe35 {
 public static void main(String[] args) {
  var s=Revision34Validation.road(Style.O6_YELLOW);s=s.options(s.options().sidewalk(new RoadSidewalks.Config(true,RoadSidewalks.Side.BOTH,5,"minecraft:stone_bricks")));
  for(boolean curve:new boolean[]{false,true}){
   var mesh=RoadGeometry.build(new Node(new V(0,20,0),curve?-87:-90,0),new Node(new V(800,20,0),curve?-93:-90,0),s);
   var parts=RoadStructures.plan(mesh,new Ground(){public double top(double x,double z,double y){return 0;}public boolean blocked(Part p){return false;}public boolean joined(V p){return false;}});
   System.out.println("PROFILE curve="+curve+" parts="+parts.size());
   for(Material mat:Material.values()){
    var group=parts.stream().filter(p->p.material()==mat).toList();if(group.isEmpty())continue;
    System.out.println(mat+" n="+group.size()+" near="+RoadRenderMesh.structureFaces(group,false).stream().mapToLong(RoadRenderMesh::vertexCount).sum()+" far="+RoadRenderMesh.structureFaces(group,true).stream().mapToLong(RoadRenderMesh::vertexCount).sum());
   }
  }
  var main=Revision34Validation.road(Style.H4_RAIL);
  for(double amplitude:new double[]{12,40}){
   var a=Revision34Validation.axis(false,20,amplitude,-350,350);var b=Revision34Validation.axis(true,30,-amplitude,-350,350);
   var nodes=new Node[]{new Node(a.at(0),-90,0),new Node(a.at(a.length()),-90,0),new Node(b.at(0),0,0),new Node(b.at(b.length()),0,0)};
   try {var p=CurvedRoadPlans.interchange(nodes,main,main,new Options(Preset.STACK,false,1,96,20,5,1).adjust(true),List.of(a,b));System.out.println("FIT PASS amplitude="+amplitude+" anchors="+p.anchors());}
   catch(Exception e){System.out.println("FIT FAIL amplitude="+amplitude+": "+e.getMessage());}
  }
 }
}
