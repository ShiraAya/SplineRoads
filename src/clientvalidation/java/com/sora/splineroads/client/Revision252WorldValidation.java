package com.sora.splineroads.client;
import com.sora.splineroads.core.*;
import com.sora.splineroads.core.RoadGeometry.*;
import com.sora.splineroads.world.*;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.*;
import java.util.*;
public final class Revision252WorldValidation {
 static int checks;
 static void check(boolean b,String s){checks++;if(!b)throw new AssertionError(s);}
 static Settings road(Style style){return new Settings(Mode.STRAIGHT,style,style.defaultWidth(),1,.4,90).options(RoadProfile.Options.DEFAULT.extras(false,false,true));}
 static RoadRecord street(BlockPos a,BlockPos b,Settings s){V av=new V(a.getX(),a.getY(),a.getZ()),bv=new V(b.getX(),b.getY(),b.getZ());double yaw=RoadPlanner.yaw(bv.sub(av));return new RoadRecord(UUID.randomUUID(),new UUID(0,0),a,b,new Node(av,yaw,0),new Node(bv,yaw,0),s,false,4);}
 public static void main(String[] args){
  var port=new RoadTransitions.Port(-10,10,2,List.of(-6.,6.),1,1);
  var settings=road(Style.O6_GREEN).options(RoadProfile.Options.DEFAULT.route(RoadProfile.Routing.DEFAULT.fan(6,192,200)).ends(new RoadTransitions.Ends(null,null,false,21.3,33.7,port)));
  check(settings.equals(RoadRecord.readSettings(RoadRecord.writeSettings(settings))),"new fan/trim/port metadata roundtrip");
  var stale=RoadRecord.writeSettings(road(Style.H4_RAIL));var walk=new CompoundTag();walk.putBoolean("Enabled",true);walk.putString("Side","BOTH");walk.putInt("Width",10);walk.putString("Material","minecraft:stone_bricks");stale.put("Sidewalk",walk);
  check(!RoadRecord.readSettings(stale).options().sidewalk().enabled(),"old checked highway flag sanitized on load");
  for(boolean reverse:new boolean[]{false,true}){
   BlockPos c=new BlockPos(0,64,0),a=new BlockPos(160,64,0),b=new BlockPos(0,64,160);
   Settings tapered=RoadTransitions.ends(road(Style.O6_GREEN),reverse?null:RoadTransitions.Section.of(road(Style.O4_YELLOW)),reverse?RoadTransitions.Section.of(road(Style.O4_YELLOW)):null);
   var first=street(reverse?a:c,reverse?c:a,tapered);var second=street(c,b,road(Style.O6_GREEN));
   var config=new CompoundTag();config.putBoolean("Auto",true);config.putBoolean("Forced",true);config.putLong("CenterPos",c.asLong());config.putUUID("Id",UUID.randomUUID());config.putUUID("Owner",new UUID(0,0));
   var draft=AutoJunctions.plan(List.of(first,second),List.of(config));var trimmed=draft.roads().stream().filter(r->r.id().equals(first.id())).findFirst().orElseThrow();
   Mesh full=first.mesh(),cut=trimmed.mesh();Sample edge=reverse?cut.last():cut.first();var original=RoadQueries.horizontal(full,edge.center()).sample();var before=RoadProfile.layout(full,original);var after=RoadProfile.layout(cut,edge);
   check(Math.abs(before.motorMax()-after.motorMax())<1e-8,"trimmed street seam cross-section unchanged");
   check(before.dividers().equals(after.dividers()),"trimmed street dividers unchanged");
   var approach=draft.roads().stream().filter(r->r.junction()!=null&&r.junction().get().arm()>=0&&r.mesh().first().center().distance(edge.center())<1e-6).findFirst().orElseThrow();
   var ref=approach.junction();var arm=ref.spec().arms().get(ref.get().arm());var seam=arm.external().options().ends().port();
   check(seam!=null,"approach stores exact port");double sign=reverse?1:-1;
   for(double divider:after.dividers())check(seam.dividers().stream().anyMatch(d->Math.abs(d-sign*divider)<1e-8),"port orientation follows inward frame");
   double expected=after.curbWidth()>0?edge.halfWidth()-Math.abs(after.outer(1))-after.cycleWidth():0;
   check(Math.abs(expected-seam.curbLeft())<1e-8,"curb width copied from actual road edge");
   for(var part:ref.get().structures())if(part.height()==.2&&part.a().sub(edge.center()).horizontalLength()<edge.halfWidth()+1){
    check(part.frameA()!=null&&part.frameB()!=null,"tapered approach curb retains explicit widths");
   }
   for(var r:draft.roads()){
    var restored=RoadRecord.load(r.save());check(r.mesh().samples().equals(restored.mesh().samples()),"exact junction/trimmed road geometry roundtrip");check(r.settings().equals(restored.settings()),"exact trimmed settings roundtrip");
   }
   check(JunctionCodec.readRef(JunctionCodec.writeRef(ref)).geometryVersion()==27,"new geometry persisted as 27");
   check(new JunctionPlanner.Ref(ref.spec(),0,26).geometryVersion()==26,"old geometry version retained");
  }
  System.out.println("WORLD252 PASS "+checks+" checks");
 }
}
