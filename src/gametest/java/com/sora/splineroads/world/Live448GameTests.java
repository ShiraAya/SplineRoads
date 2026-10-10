package com.sora.splineroads.world;
import com.sora.splineroads.core.*;
import com.sora.splineroads.core.RoadGeometry.*;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.gametest.*;
import java.util.*;
@GameTestHolder("splineroads_live448") @PrefixGameTestTemplate(false)
public final class Live448GameTests {
 static void clearWidth(GameTestHelper h,RoadData data,RoadRecord road){
  var parts=data.index.roads.values().stream().flatMap(r->r.record.structures().stream())
      .filter(p->p.material()==RoadStructures.Material.CONCRETE&&(Math.abs(p.height()-.45)<1e-6||Math.abs(p.height()-.8)<1e-6)&&p.width()<=.621).toList();
  int checked=0;double minimum=Double.POSITIVE_INFINITY;var mesh=road.mesh();
  for(double d=2;d<mesh.length()-2;d+=1){var at=RoadStructures.sample(mesh,d);var forward=at.left().left().mul(-1);double low=Double.NEGATIVE_INFINITY,high=Double.POSITIVE_INFINITY;
   for(var p:parts){
    if(Math.min(p.a().y(),p.b().y())>at.center().y()+.2||Math.max(p.a().y(),p.b().y())+p.height()<at.center().y()+.1)continue;
    var vertices=p.base();var hits=new ArrayList<Double>();
    for(int i=0;i<vertices.size();i++){var a=vertices.get(i).sub(at.center());var b=vertices.get((i+1)%vertices.size()).sub(at.center());double x=a.dot(forward),y=b.dot(forward);
     if(x*y<=0&&Math.abs(x-y)>1e-9)hits.add(a.add(b.sub(a).mul(x/(x-y))).dot(at.left()));
    }
    if(hits.size()<2)continue;double a=Collections.min(hits),b=Collections.max(hits);
    if(b<0)low=Math.max(low,b);else if(a>0)high=Math.min(high,a);
   }
   if(Double.isFinite(low)&&Double.isFinite(high)){checked++;minimum=Math.min(minimum,high-low);h.assertTrue(high-low>=3.99,"guardrail clear width "+(high-low)+" at "+d);}
  }
  h.assertTrue(checked>15,"no actual paired guardrails checked");System.out.printf("LIVE448 GUARDRAIL_WIDTH PASS samples=%d minimum=%.3f%n",checked,minimum);
 }
 @GameTest(batch="splineroads_live448",template="empty",templateNamespace="splineroads_live448",timeoutTicks=18000)
 public static void preparedPreviewRechecksWorldBeforeCommit(GameTestHelper h){
  System.setProperty("sr.profile","true");var level=h.getLevel();var data=RoadData.get(level);int origin=294000;
  for(int x=-15;x<=15;x++)for(int z=-5;z<=85;z++){var p=new BlockPos(origin+x,199,origin+z);level.getChunkAt(p);level.setBlock(p,Blocks.GRASS_BLOCK.defaultBlockState(),2);}
  var road=data.connect(level,null,Revision32GameTests.marker(h,origin,200,origin,0),Revision32GameTests.marker(h,origin,200,origin+80,0),new Settings(Mode.STRAIGHT,Style.O2_ONE,9,1,.35,90),null);
  var next=road.settings(road.settings().options(road.settings().options().hideArrows(true)));var token=UUID.randomUUID();
  long revision=data.index.revision();var planned=data.prepareAssembly(level,null,List.of(new RoadIndex.Built(next)),Set.of(road.id()),Set.of(road.a(),road.b()),List.of(),token);
  h.assertTrue(data.index.revision()==revision,"preview wrote road state");
  var obstacle=new BlockPos(origin,202,origin+40);level.setBlock(obstacle,Blocks.BEDROCK.defaultBlockState(),2);
  boolean rejected=false;try{data.buildPrepared(level,null,token);}catch(IllegalArgumentException expected){rejected=true;}
  h.assertTrue(rejected&&data.index.revision()==revision&&level.getBlockState(obstacle).is(Blocks.BEDROCK),"cached preview overwrote a new obstacle or partly committed");
  level.setBlock(obstacle,Blocks.AIR.defaultBlockState(),2);
  token=UUID.randomUUID();long start=System.nanoTime();planned=data.prepareAssembly(level,null,List.of(new RoadIndex.Built(next)),Set.of(road.id()),Set.of(road.a(),road.b()),List.of(),token);double preview=(System.nanoTime()-start)/1e9;
  var expected=planned.stream().filter(r->r.record.id().equals(road.id())).findFirst().orElseThrow();start=System.nanoTime();data.buildPrepared(level,null,token);double commit=(System.nanoTime()-start)/1e9;
  h.assertTrue(data.index.roads.get(road.id())==expected,"build regenerated the planned mesh instead of reusing it");
  h.assertTrue(data.index.roads.get(road.id()).record.settings().options().hideArrows(),"planned edit not committed");
  rejected=false;try{data.buildPrepared(level,null,token);}catch(IllegalArgumentException consumed){rejected=true;}h.assertTrue(rejected,"preview token committed twice");
  System.out.printf("LIVE448 REAL_WORLD PASS cached preview=%.3fs commit=%.3fs, obstacle protection/atomicity/object reuse/token consumption%n",preview,commit);h.succeed();
 }
}
