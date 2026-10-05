package com.sora.splineroads.world;
import com.sora.splineroads.core.*;
import com.sora.splineroads.core.RoadGeometry.*;
import com.sora.splineroads.core.CorridorPlanner.*;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.gametest.*;
@GameTestHolder("splineroads_revision272") @PrefixGameTestTemplate(false)
public final class Revision272GameTests {
  static Settings style(Style s){return new Settings(Mode.STRAIGHT,s,s.defaultWidth(),1,.4,90);}
  static BlockPos marker(GameTestHelper h,int x,int y,int z){BlockPos p=new BlockPos(x,y,z);Revision27GameTests.marker(h.getLevel(),p);return p;}
  static CompoundTag input(GameTestHelper h,int x,int z,int length,Kind kind,Adjustment mode){
    var a=marker(h,x,kind==Kind.LAYERED?10:2,z);var b=marker(h,x+length,a.getY(),z);
    long[] points=kind==Kind.FRONTAGE?new long[]{a.asLong(),b.asLong()}:new long[]{a.asLong(),b.asLong(),marker(h,x+length,2,z).asLong(),marker(h,x,2,z).asLong()};
    var t=Corridors.initialize(Interchanges.payload(h.getLevel(),null,points,null),kind);
    t.put("Main1",RoadRecord.writeSettings(style(Style.O6_GREEN)));t.put("Main2",RoadRecord.writeSettings(style(kind==Kind.FRONTAGE?Style.O2_ONE:Style.O4_YELLOW)));
    t.put("Corridor",Corridors.write(new Config(kind,Sides.BOTH,Access.BOTH,14,2.25,mode)));
    t.put("Options",Interchanges.write(new InterchangePlanner.Options(InterchangePlanner.Preset.CLOVERLEAF,false,2,24,12,5,1).adjust(true)));return t;
  }
  static UUID group(RoadData data,Set<UUID> previous){return data.interchanges.keySet().stream().filter(id->!previous.contains(id)).findFirst().orElseThrow();}
  static RoadRecord cross(GameTestHelper h,int x,int z){return RoadData.get(h.getLevel()).connect(h.getLevel(),null,marker(h,x,2,z-90),marker(h,x,2,z+90),style(Style.O2_YELLOW),null);}
  @GameTest(template="empty",templateNamespace="splineroads_revision272",timeoutTicks=12000)
  public static void autoAvoidsBlockedEndAndBuildsPreview(GameTestHelper h){
    var level=h.getLevel();var data=RoadData.get(level);var t=input(h,2200,3200,80,Kind.FRONTAGE,Adjustment.AUTO);var crossing=cross(h,2160,3200);
    var before=data.save(new CompoundTag());var checked=Corridors.preview(level,null,t);
    h.assertTrue("END".equals(checked.getString("ResolvedFit")),"left obstruction chooses B-only extension");
    h.assertTrue(before.equals(data.save(new CompoundTag())),"preview does not mutate road data");
    var plan=Interchanges.plan(checked);h.assertTrue(plan.anchors().get(0).equals(RoadRecord.readNode(t.getList("Nodes",10).getCompound(0))),"A remains fixed");
    var oldIds=new HashSet<>(data.interchanges.keySet());Interchanges.build(level,null,t);UUID id=group(data,oldIds);var saved=data.interchanges.get(id);
    h.assertTrue(saved.getString("ResolvedFit").equals("END"),"construction selects same obstacle-free side");
    for(int i=0;i<2;i++)h.assertTrue(RoadRecord.readNode(saved.getList("Nodes",10).getCompound(i)).equals(plan.anchors().get(i)),"construction matches checked preview");
    h.assertTrue(data.index.roads.get(crossing.id()).record.start().equals(crossing.start()),"existing crossing remains intact");
    h.assertTrue(RoadData.load(data.save(new CompoundTag())).interchanges.get(id).getString("ResolvedFit").equals("END"),"fit choice persists");
    Interchanges.remove(level,null,id);data.remove(level,null,crossing.id());h.assertTrue(RoadWorkChunks.heldCount(level)==0,"tickets released");h.succeed();
  }
  @GameTest(template="empty",templateNamespace="splineroads_revision272",timeoutTicks=12000)
  public static void explicitAndBothBlockedRejectWithoutWrites(GameTestHelper h){
    var level=h.getLevel();var data=RoadData.get(level);var t=input(h,2200,3500,80,Kind.FRONTAGE,Adjustment.BOTH);var left=cross(h,2160,3500);var before=data.save(new CompoundTag());
    boolean rejected=false;try{Corridors.preview(level,null,t);}catch(IllegalArgumentException e){rejected=e.getMessage().contains("净空冲突");}
    h.assertTrue(rejected&&before.equals(data.save(new CompoundTag())),"explicit both-end option does not silently switch or write");
    var right=cross(h,2320,3500);t.put("Corridor",Corridors.write(Corridors.read(t.getCompound("Corridor")).adjustment(Adjustment.AUTO)));before=data.save(new CompoundTag());rejected=false;
    try{Interchanges.build(level,null,t);}catch(IllegalArgumentException e){rejected=e.getMessage().contains("仅 A 侧")&&e.getMessage().contains("仅 B 侧");}
    h.assertTrue(rejected&&before.equals(data.save(new CompoundTag())),"all blocked alternatives reject atomically");
    data.remove(level,null,left.id());data.remove(level,null,right.id());h.assertTrue(RoadWorkChunks.heldCount(level)==0,"failed fits release tickets");h.succeed();
  }
  @GameTest(template="empty",templateNamespace="splineroads_revision272",timeoutTicks=12000)
  public static void singleEndWithReversedDeckAndOccupiedTarget(GameTestHelper h){
    var level=h.getLevel();var data=RoadData.get(level);var t=input(h,3200,3800,80,Kind.LAYERED,Adjustment.START);var checked=Corridors.preview(level,null,t);var plan=Interchanges.plan(checked);
    V p=plan.anchors().get(0).position();var target=BlockPos.containing(p.x(),p.y(),p.z());level.setBlock(target,Blocks.CHEST.defaultBlockState(),2);var before=data.save(new CompoundTag());boolean rejected=false;
    try{Corridors.preview(level,null,t);}catch(IllegalArgumentException e){rejected=e.getMessage().contains("minecraft:chest");}
    h.assertTrue(rejected&&before.equals(data.save(new CompoundTag()))&&level.getBlockState(target).is(Blocks.CHEST),"occupied endpoint is rejected during preview");level.removeBlock(target,false);
    var ids=new HashSet<>(data.interchanges.keySet());Interchanges.build(level,null,t);UUID id=group(data,ids);var saved=data.interchanges.get(id);
    for(int i:new int[]{1,2})h.assertTrue(saved.getLongArray("Points")[i]==t.getLongArray("Points")[i],"fixed B-side and reversed C stay put");
    Interchanges.remove(level,null,id);h.assertTrue(RoadWorkChunks.heldCount(level)==0,"tickets released");h.succeed();
  }
  @GameTest(template="empty",templateNamespace="splineroads_revision272",timeoutTicks=12000)
  public static void actualFrontageAccessHasNoBarrierAcrossOpening(GameTestHelper h){
    var level=h.getLevel();var data=RoadData.get(level);var t=input(h,2200,4100,600,Kind.FRONTAGE,Adjustment.BOTH);var ids=new HashSet<>(data.interchanges.keySet());Interchanges.build(level,null,t);UUID id=group(data,ids);
    var all=data.index.roads.values().stream().filter(r->id.equals(r.record.assembly())).toList();int tested=0;
    for(var ramp:all)if(ramp.mesh.settings().style().ramp())for(boolean first:new boolean[]{true,false}){
      Sample end=first?ramp.mesh.first():ramp.mesh.last();var host=all.stream().filter(r->!r.mesh.settings().style().ramp()&&RoadQueries.contains(r.mesh,end.center(),end.halfWidth()-.1,.1)).findFirst().orElseThrow();
      Sample contact=null;
      for(double d=0;d<Math.min(100,ramp.mesh.length()/2);d+=.5){Sample at=RoadStructures.sample(ramp.mesh,first?d:ramp.mesh.length()-d);var q=RoadQueries.horizontal(host.mesh,at.center());double overlap=q.sample().halfWidth()+at.halfWidth()-q.horizontalDistance();
        if(at.halfWidth()*2>=ramp.mesh.settings().width()*.999&&overlap>.95&&Math.abs(at.left().dot(q.sample().left()))>.99999){contact=at;break;}}
      h.assertTrue(contact!=null,"full-width parallel access exists in actual built ramp");var q=RoadQueries.horizontal(host.mesh,contact.center());V point=q.sample().at(Math.signum(q.lateral())*(q.sample().halfWidth()-.3),0);
      for(var road:List.of(host,ramp))for(var part:road.record.structures()){
        if(part.pier()||part.width()>.5||Math.min(part.a().y(),part.b().y())<point.y()+.15||Math.max(part.a().y(),part.b().y())>point.y()+2)continue;
        V delta=part.b().sub(part.a());double f=Math.max(0,Math.min(1,point.sub(part.a()).dot(new V(delta.x(),0,delta.z()))/Math.max(1e-9,delta.horizontalLength()*delta.horizontalLength())));
        h.assertTrue(point.sub(part.a().add(delta.mul(f))).horizontalLength()>.3,"no guardrail spans the main/ramp opening");
      }tested++;
    }
    h.assertTrue(tested==12,"both ends of four connectors and two common segments checked");Interchanges.remove(level,null,id);h.assertTrue(RoadWorkChunks.heldCount(level)==0,"tickets released");h.succeed();
  }
}
