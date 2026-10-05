package com.sora.splineroads.world;
import com.sora.splineroads.SplineRoads;
import com.sora.splineroads.core.*;
import com.sora.splineroads.core.RoadGeometry.*;
import com.sora.splineroads.core.RoadInfrastructure.*;
import com.sora.splineroads.core.RoadTunnelFit.Adjustment;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.gametest.*;
import java.util.*;

@GameTestHolder("splineroads_revision291") @PrefixGameTestTemplate(false)
public final class Revision291GameTests {
  static BlockPos node(GameTestHelper h,int x,int y,int z){return Revision28GameTests.marker(h,x,y,z);}
  static Settings dip(Adjustment mode){return Revision28GameTests.road(Style.O2_YELLOW,Structure.TUNNEL,Config.DEFAULT.grade(.2).depth(11).adjustment(mode));}
  @GameTest(template="empty",templateNamespace="splineroads_revision28",timeoutTicks=12000)
  public static void dipMovesSelectedMouthsAndRetiresOldMarkers(GameTestHelper h){
    var level=h.getLevel();var data=RoadData.get(level);
    for(Adjustment mode:new Adjustment[]{Adjustment.BOTH,Adjustment.START,Adjustment.END}){
      int z=14000+mode.ordinal()*96;var a=node(h,14000,12,z);var b=node(h,14100,12,z);
      var beforeA=((NodeEntity)level.getBlockEntity(a)).constructionNode();var beforeB=((NodeEntity)level.getBlockEntity(b)).constructionNode();
      var r=data.connect(level,null,a,b,dip(mode),null);var built=data.index.roads.get(r.id());
      h.assertTrue(RoadGrades.maximum(built.mesh)<=.2001,"fitted world tunnel respects grade");
      if(mode==Adjustment.END)h.assertTrue(r.a().equals(a)&&r.start().equals(beforeA),"only end: original start remains exact");
      if(mode==Adjustment.START)h.assertTrue(r.b().equals(b)&&r.end().equals(beforeB),"only start: original end remains exact");
      for(var old:List.of(a,b))if(!old.equals(r.a())&&!old.equals(r.b()))h.assertTrue(!(level.getBlockEntity(old) instanceof NodeEntity),"moved old marker is retired");
      h.assertTrue(((NodeEntity)level.getBlockEntity(r.a())).node().position().equals(r.start().position()),"new start marker tracks actual mouth");
      h.assertTrue(((NodeEntity)level.getBlockEntity(r.b())).node().position().equals(r.end().position()),"new end marker tracks actual mouth");
      var reload=RoadData.load(data.save(new CompoundTag())).index.roads.get(r.id());
      h.assertTrue(reload.mesh.samples().equals(built.mesh.samples())&&reload.record.structures().equals(r.structures()),"fitted tunnel reload is exact");
      data.connect(level,null,r.a(),r.b(),dip(mode),r.id());
      var again=data.index.roads.get(r.id()).record;h.assertTrue(again.a().equals(r.a())&&again.b().equals(r.b()),"saving again causes no endpoint drift");
      data.remove(level,null,r.id());
    }
    h.assertTrue(RoadWorkChunks.heldCount(level)==0,"fit releases chunk tickets");h.succeed();
  }
  @GameTest(template="empty",templateNamespace="splineroads_revision28",timeoutTicks=12000)
  public static void occupiedMouthRejectsAtomically(GameTestHelper h){
    var level=h.getLevel();var data=RoadData.get(level);var a=node(h,14600,12,14600);var b=node(h,14700,12,14600);var s=dip(Adjustment.END);
    Node aa=((NodeEntity)level.getBlockEntity(a)).constructionNode(),bb=((NodeEntity)level.getBlockEntity(b)).constructionNode();
    var plan=RoadTunnelFit.plan(RoadPlanner.Hint.free(aa),RoadPlanner.Hint.free(bb),s);V end=plan.end().position();var target=BlockPos.containing(end.x(),end.y(),end.z());
    level.getChunkAt(target);level.setBlock(target,Blocks.CHEST.defaultBlockState(),2);var before=data.save(new CompoundTag());
    boolean rejected=false;try{data.connect(level,null,a,b,s,null);}catch(IllegalArgumentException e){rejected=true;}
    h.assertTrue(rejected&&data.save(new CompoundTag()).equals(before),"blocked relocation makes no road/save mutation");
    h.assertTrue(level.getBlockState(target).is(Blocks.CHEST)&&level.getBlockEntity(a) instanceof NodeEntity&&level.getBlockEntity(b) instanceof NodeEntity,"chest and original markers survive rejection");
    h.assertTrue(((NodeEntity)level.getBlockEntity(a)).constructionNode().equals(aa)&&((NodeEntity)level.getBlockEntity(b)).constructionNode().equals(bb),"failed fit does not partially move either endpoint");
    level.setBlock(target,Blocks.AIR.defaultBlockState(),2);h.succeed();
  }
  @GameTest(template="empty",templateNamespace="splineroads_revision28",timeoutTicks=12000)
  public static void mouthMovementRebuildsConnectedOrdinaryRoad(GameTestHelper h){
    var level=h.getLevel();var data=RoadData.get(level);var before=node(h,15000,12,15000);var a=node(h,15128,12,15000);var b=node(h,15228,12,15000);
    var surface=Revision28GameTests.road(Style.O2_YELLOW,Structure.GROUND,Config.DEFAULT.grade(.2).gantry(Gantry.OFF));
    var approach=data.connect(level,null,before,a,surface,null);var tunnel=data.connect(level,null,a,b,dip(Adjustment.START),null);
    var neighbor=data.index.roads.get(approach.id()).record;
    h.assertTrue(neighbor.b().equals(tunnel.a())&&neighbor.end().position().distance(tunnel.start().position())<1e-7,"approach follows relocated shared mouth without a gap");
    h.assertTrue(!(level.getBlockEntity(a) instanceof NodeEntity),"shared old marker is also removed");
    data.remove(level,null,tunnel.id());data.remove(level,null,approach.id());h.succeed();
  }
  @GameTest(template="empty",templateNamespace="splineroads_revision28",timeoutTicks=12000)
  public static void groundGantryDoesNotExcavateItsFoundation(GameTestHelper h){
    var level=h.getLevel();var data=RoadData.get(level);
    // Local grass shelf at Y=2 reproduces the ground gantry screenshot independent of world seed.
    for(int x=15540;x<=15556;x++)for(int z=15475;z<=15525;z++){
      var p=new BlockPos(x,1,z);level.getChunkAt(p);level.setBlock(p,Blocks.GRASS_BLOCK.defaultBlockState(),2);
    }
    var a=node(h,15500,2,15500);var b=node(h,15596,2,15500);
    var s=Revision28GameTests.road(Style.H4_RAIL,Structure.GROUND,Config.DEFAULT.grade(.2).gantry(Gantry.FRAME));
    var r=data.connect(level,null,a,b,s,null);var built=data.index.roads.get(r.id());
    var gantry=RoadGantry.parts(built.mesh,RoadGantry.station(built.mesh,0),new RoadStructures.Ground(){
      public double top(double x,double z,double y){return Math.min(2,y);}public boolean blocked(RoadStructures.Part p){return false;}public boolean joined(V p){return false;}});
    for(var part:gantry)if(part.material()==RoadStructures.Material.CONCRETE&&part.pier()){
      h.assertTrue(Math.abs(part.a().y()-2)<1e-7,"base plinth rests on actual grass height");
      var under=BlockPos.containing(part.a().x(),1,part.a().z());
      h.assertTrue(level.getBlockState(under).is(Blocks.GRASS_BLOCK),"grass under gantry remains a full block, without a pit");
    }
    h.assertTrue(built.record.structures().stream().anyMatch(p->p.material()==RoadStructures.Material.GANTRY_FRAME&&p.height()==.18),"world uses new open equipment truss");
    data.remove(level,null,r.id());h.succeed();
  }
  @GameTest(template="empty",templateNamespace="splineroads_revision28",timeoutTicks=12000)
  public static void everyBridgeConnectsToStandardViaductAtBothEnds(GameTestHelper h){
    var level=h.getLevel();var data=RoadData.get(level);
    for(Bridge style:Bridge.values())if(style!=Bridge.STANDARD){
      int z=16000+style.ordinal()*96;var a=node(h,15936,12,z);var b=node(h,16000,12,z);var c=node(h,16096,12,z);var d=node(h,16160,12,z);
      var standard=Revision28GameTests.road(Style.O4_YELLOW,Structure.BRIDGE,Config.DEFAULT.grade(.2).gantry(Gantry.OFF));
      System.out.println("Bridge seam "+style+" build both approaches");
      var left=data.connect(level,null,a,b,standard,null);
      RoadRecord middle;
      try{middle=data.connect(level,null,b,c,standard.options(standard.options().infrastructure(Config.DEFAULT.grade(.2).bridge(style).gantry(Gantry.OFF))),null);}
      catch(IllegalArgumentException e){throw new IllegalArgumentException(style+" start connection: "+e.getMessage(),e);}
      RoadRecord right;
      try{right=data.connect(level,null,c,d,standard,null);}
      catch(IllegalArgumentException e){throw new IllegalArgumentException(style+" end connection: "+e.getMessage(),e);}
      var lm=data.index.roads.get(left.id()).mesh;var mm=data.index.roads.get(middle.id()).mesh;var rm=data.index.roads.get(right.id()).mesh;
      for(int side:new int[]{-1,1}){
        h.assertTrue(lm.last().at(side*lm.last().halfWidth(),0).distance(mm.first().at(side*mm.first().halfWidth(),0))<1e-7,"bridge start edges meet exactly");
        h.assertTrue(mm.last().at(side*mm.last().halfWidth(),0).distance(rm.first().at(side*rm.first().halfWidth(),0))<1e-7,"bridge end edges meet exactly");
      }
      System.out.println("Bridge seam "+style+" remove approaches");
      data.remove(level,null,left.id());data.remove(level,null,right.id());data.remove(level,null,middle.id());
    }
    h.succeed();
  }
}
