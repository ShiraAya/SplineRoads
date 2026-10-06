#!/usr/bin/env python3
"""Verbatim editor math/guard methods with actual core/model and explicit adapters.
Not a complete RoadTool/RoadScreen/Forge build or a game GUI test.
Args: source-root output-dir compiled-core-and-model-classpath [--expect-failure]
"""
from pathlib import Path
import subprocess,sys
root,out,cp=Path(sys.argv[1]).resolve(),Path(sys.argv[2]).resolve(),sys.argv[3]
out.mkdir(parents=True,exist_ok=True)
def method(path,name):
 text=(root/path).read_text();start=text.index(name);start=text.rfind('  private',0,start);i=text.index('{',start);depth=1;j=i+1
 while depth:
  if text[j]=='{':depth+=1
  elif text[j]=='}':depth-=1
  j+=1
 return text[start:j]
guard=method('src/main/java/com/sora/splineroads/world/RoadTool.java','void requireTool(')
width=method('src/main/java/com/sora/splineroads/client/RoadScreen.java','void setLaneWidth(')
java='''package com.sora.splineroads.world;
import com.sora.splineroads.core.*;import com.sora.splineroads.core.RoadGeometry.*;import java.util.*;
public final class EditorGuardPost420 {
 static int checks,failed;static long sequence=900;
 static void check(boolean b,String message){checks++;if(!b){failed++;System.out.println("FAILED: "+message);}}
 static UUID id(){return new UUID(420,++sequence);}
 static LanePoints.Link link(){return new LanePoints.Link(LanePoints.Ref.lane(id(),id()),LanePoints.Ref.lane(id(),id()),LanePoints.Options.DEFAULT,null).withProtectedMerge();}
 static RoadRecord road(Style style,boolean linked){var o=RoadProfile.Options.DEFAULT;if(linked)o=o.lanePoints(LanePoints.Data.EMPTY.link(link()));var s=new Settings(Mode.STRAIGHT,style,RoadProfile.width(style,o,4),1,.35,90).options(o);return new RoadRecord(id(),id(),new net.minecraft.core.BlockPos(0,100,0),new net.minecraft.core.BlockPos(0,100,200),new Node(new V(0,100,0),0,0),new Node(new V(0,100,200),0,0),s);}
 static final class Tool {
''' +guard+'''
 static boolean allowed(RoadRecord road){try{requireTool(road);return true;}catch(IllegalArgumentException expected){return false;}}
 }
 static final class Width {Style style;RoadProfile.Options options;double roadWidth;boolean laneRoad(){return options.lanePoints().link()!=null;}
''' +width+'''
 }
 public static void main(String[]args){
  check(Tool.allowed(road(Style.C1_RAMP,true)),"ordinary-associated connector remains editable");
  check(Tool.allowed(road(Style.C1_HIGHWAY_RAMP,true)),"highway-associated connector remains editable");
  check(Tool.allowed(road(Style.O1_ONE,false)),"ordinary road remains editable");
  check(!Tool.allowed(road(Style.R1,false)),"legacy automatic ramp not globally unlocked");
  check(!Tool.allowed(road(Style.C1_RAMP,false)),"malformed unlinked connector not admitted");
  for(var style:List.of(Style.O1_ONE,Style.H1_ONE,Style.C1_RAMP,Style.C1_HIGHWAY_RAMP)){
   var w=new Width();w.style=style;w.options=RoadProfile.Options.DEFAULT.lanePoints(LanePoints.Data.EMPTY.link(link()));w.setLaneWidth(4);
   check(Math.abs(w.roadWidth-4)<1e-9,"protected width edit has no phantom shoulder: "+style);
  }
  var normal=new Width();normal.style=Style.O1_ONE;normal.options=RoadProfile.Options.DEFAULT;normal.setLaneWidth(4);check(normal.roadWidth==RoadProfile.width(Style.O1_ONE,normal.options,4),"unrelated ordinary road width preserved");
  var ground=new RoadStructures.Ground(){public double top(double x,double z,double y){return y-1;}public boolean blocked(RoadStructures.Part p){return false;}public boolean joined(V p){return false;}};
  check(!RoadGantry.plan(road(Style.C1_HIGHWAY_RAMP,true).mesh(),ground).isEmpty(),"explicit later AUTO honors highway-associated facilities");
  var r=road(Style.C1_HIGHWAY_RAMP,true);r=r.settings(r.settings().options(r.settings().options().infrastructure(r.settings().options().infrastructure().gantry(RoadInfrastructure.Gantry.OFF))));check(RoadGantry.plan(r.mesh(),ground).isEmpty(),"OFF remains off");
  System.out.println("EditorGuardPost420: "+checks+" checks, failures="+failed+"; verbatim editor guard/math, actual Gantry/core/model, NBT adapters; NOT full GUI/Forge/world.");
  if(failed>0)throw new AssertionError("post-review guard checks failed");
 }
}
'''
p=out/'EditorGuardPost420.java';p.write_text(java)
subprocess.run(['javac','--release','17','-encoding','UTF-8','-cp',cp,'-d',str(out),str(p),str(root/'src/main/java/com/sora/splineroads/core/RoadGantry.java')],check=True)
r=subprocess.run(['java','-Dfile.encoding=UTF-8','-cp',str(out)+':'+cp+':'+str(root/'src/main/resources'),'com.sora.splineroads.world.EditorGuardPost420'])
if '--expect-failure' in sys.argv:
 if r.returncode==0:raise RuntimeError('expected original guard regression was not reproduced')
else:r.check_returncode()
