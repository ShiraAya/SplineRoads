package com.sora.splineroads;
import com.sora.splineroads.core.*;
import com.sora.splineroads.core.RoadGeometry.*;
import com.sora.splineroads.core.RoadInfrastructure.*;
import com.sora.splineroads.core.RoadStructures.*;
import com.sora.splineroads.world.*;
import net.minecraft.core.BlockPos;
import java.util.*;
public final class Revision31Validation {
  static int count;static void check(boolean value,String message){count++;if(!value)throw new AssertionError(message);}
  public static void main(String[] args){
    var ground=Revision28Validation.ground(0);var base=Revision28Validation.settings(Style.O4_YELLOW,Structure.GROUND,Config.DEFAULT.gantry(Gantry.FRAME));
    for(var finish:RoadSidewalks.Finish.values())for(int width:new int[]{1,2,5,15}){
      var walk=new RoadSidewalks.Config(true,RoadSidewalks.Side.BOTH,width,finish.id);
      var s=base.options(base.options().sidewalk(walk));var m=RoadGeometry.build(new Node(new V(.5,4,.5),-70,0),new Node(new V(100.5,9,36.5),-70,0),s);
      var parts=RoadSidewalks.parts(m,walk);check(!parts.isEmpty(),"continuous geometry");check(parts.stream().anyMatch(p->p.material()==Material.TACTILE)==(width>=2),"tactile threshold");
      for(var p:parts){check(p.height()<.51,"thin continuous walking slab");for(var v:p.base()){check(Double.isFinite(v.y()),"finite sloped corner");}}
      check(parts.stream().filter(p->p.material()!=Material.TACTILE).allMatch(p->p.material()==finish.material()),"all six finishes");check(RoadRecord.readSettings(RoadRecord.writeSettings(s)).equals(s),"sidewalk mode round trip");
    }
    var legacy=RoadRecord.writeSettings(base);legacy.getCompound("Sidewalk").remove("Smooth");legacy.getCompound("Sidewalk").putString("Material","minecraft:gold_block");var old=RoadRecord.readSettings(legacy).options().sidewalk();check(!old.smooth()&&old.material().equals("minecraft:gold_block"),"old block-based walks survive load");check(old.smooth(true).material().equals("minecraft:stone_bricks"),"unsupported legacy material converts to default only on update");
    var mesh=RoadGeometry.build(Revision30Validation.node(.5,8),Revision30Validation.node(120.5,8),base);
    check(RoadSignCatalog.MODELS.size()==344,"complete road-sign catalog");
    for(var model:RoadSignCatalog.MODELS){var a=new RoadSigns.Attachment(0,model.id(),RoadSigns.Mount.GANTRY,0,.5,0,5.6,1,false,List.of("示例文字"));var p=RoadSigns.place(mesh,a);var faces=p.part().faces();check(!faces.isEmpty(),"model exists: "+model.id());check(RoadRenderMesh.compact(List.of(p.part()),true).equals(List.of(p.part())),"sign model cannot disappear or lose its model at distance");for(var face:faces){check(face.uv().size()==4,"authored UVs");for(var uv:face.uv())check(uv.u()>=0&&uv.u()<=1&&uv.v()>=0&&uv.v()<=1,"atlas range");}
      check(RoadSignCodec.read(RoadSignCodec.write(a)).equals(a),"text/placement save exact");
      var reversed=RoadSigns.place(mesh,new RoadSigns.Attachment(0,model.id(),RoadSigns.Mount.GANTRY,0,.5,0,5.6,1,true,List.of()));check(p.front().dot(reversed.front())<-.999,"front orientation flips");
    }
    var a=new RoadSigns.Attachment(0,"sign_expressway_distance_from_location_1",RoadSigns.Mount.GANTRY,0,.5,0,5.6,1,false,List.of("南城","中央駅","North","2","3","4"));
    var settings=base.options(base.options().infrastructure(base.options().infrastructure().signs(List.of(a))));mesh=RoadGeometry.build(Revision30Validation.node(.5,8),Revision30Validation.node(120.5,8),settings);var parts=RoadStructures.plan(mesh,ground);
    var r=new RoadRecord(UUID.randomUUID(),UUID.randomUUID(),new BlockPos(0,8,0),new BlockPos(120,8,0),Revision30Validation.node(.5,8),Revision30Validation.node(120.5,8),settings).structures(parts);
    check(RoadRecord.load(r.save()).equals(r),"new packed model format + multilingual text round trip");
    check(settings.options().infrastructure().depth(2).gantry(Gantry.FRAME).spacing(64).autoSpan(false).signs().equals(List.of(a)),"unrelated road settings retain signs");
    boolean reject=false;try{new RoadSigns.Attachment(0,a.model(),a.mount(),0,.5,0,5,1,false,List.of("x".repeat(81)));}catch(IllegalArgumentException e){reject=true;}check(reject,"bounded text payload");
    var bridge=RoadGeometry.build(Revision30Validation.node(.5,20),Revision30Validation.node(240.5,20),Revision28Validation.settings(Style.O4_YELLOW,Structure.BRIDGE,Config.DEFAULT.bridge(Bridge.CABLE).gantry(Gantry.OFF)));parts=RoadStructures.plan(bridge,ground);
    for(var cable:parts)if(cable.material()==Material.STEEL&&cable.width()==.14){check(parts.stream().anyMatch(p->p.material()==Material.DARK_STEEL&&p.width()==.45&&p.a().sub(cable.a()).horizontalLength()<1e-6&&p.a().y()<=cable.a().y()&&p.a().y()+p.height()>=cable.a().y()),"every cable end is inside an anchor shoe");}
    System.out.println("Revision31 PASS "+count+" checks");
  }
}
