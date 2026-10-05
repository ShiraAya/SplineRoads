package com.sora.splineroads.world;
import com.sora.splineroads.SplineRoads;
import com.sora.splineroads.client.*;
import com.sora.splineroads.core.*;
import com.sora.splineroads.core.RoadGeometry.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.server.level.*;
import net.minecraft.nbt.*;
import java.nio.file.*;
import java.util.*;
import static com.sora.splineroads.world.Visual38Suite.*;
final class AttachedRelativeVisual {
 static int stage;static UUID road,point;static V origin;
 static Scene build(ServerLevel l,RoadData data){var player=l.getServer().getPlayerList().getPlayers().get(0);var aa=marker(l,8600,-60,0,-90);var bb=marker(l,8720,-60,0,-90);var s=RoadPlanner.mode(settings(Style.O2_YELLOW,false,false),Mode.CURVE,90);var r=data.connect(l,player,aa,bb,s,null);road=r.id();origin=RoadStructures.sample(r.mesh(),40).center();click(player,SplineRoads.ATTACHED_POINT.get(),origin);point=data.index.roads.get(road).record.settings().options().attachments().points().get(0).id();stage=0;V at=new V(8660,-60,0);return new Scene(road,at,at.add(new V(5,70,40)),at,at.add(new V(5,40,35)));}
 static boolean tick(Minecraft mc)throws Exception{
  if(!CASES.get(index).equals("attached-relative")||stage==5)return false;
  if(stage==0){LaneRampWorkflowVisual.server(mc,p->click(p,SplineRoads.ATTACHED_POINT.get(),origin));stage=1;return true;}
  if(stage==1&&mc.screen instanceof AttachedPointScreen screen){for(String key:List.of("xBox","yBox","zBox"))require(Double.parseDouble(((EditBox)field(screen,key)).getValue())==0,"new attached point shows zero relative "+key);((EditBox)field(screen,"xBox")).setValue("2");((EditBox)field(screen,"yBox")).setValue("2");((EditBox)field(screen,"zBox")).setValue("3");screen.resize(mc,mc.getWindow().getGuiScaledWidth(),mc.getWindow().getGuiScaledHeight());require(((EditBox)field(screen,"yBox")).getValue().equals("2"),"resize preserves relative edits");LaneRampWorkflowVisual.press(screen,"应用");stage=2;return true;}
  if(stage==2&&mc.screen==null){var r=ClientRoads.INDEX.roads.get(road).record;var p=r.settings().options().attachments().points().get(0);if(!p.controlled())return true;require(p.position().distance(origin.add(new V(2,2,3)))<1e-6&&p.origin().equals(origin),"actual Apply packet resolves offset from immutable origin");LaneRampWorkflowVisual.server(mc,player->click(player,SplineRoads.ATTACHED_POINT.get(),p.position()));stage=3;wait=20;return true;}
  if(stage==3&&mc.screen instanceof AttachedPointScreen screen){require(Double.parseDouble(((EditBox)field(screen,"xBox")).getValue())==2&&Double.parseDouble(((EditBox)field(screen,"yBox")).getValue())==2&&Double.parseDouble(((EditBox)field(screen,"zBox")).getValue())==3,"reopen displays original-placement offsets, not world coordinates or a new zero");LaneRampWorkflowVisual.photo(mc,"attached-relative-controls");LaneRampWorkflowVisual.press(screen,"删除附属点");stage=4;return true;}
  if(stage==4&&mc.screen==null){var r=ClientRoads.INDEX.roads.get(road).record;if(!r.settings().options().attachments().points().isEmpty())return true;require(r.mesh().samples().stream().allMatch(s->Math.abs(s.center().y()-origin.y())<1e-6&&Math.abs(s.center().z()-origin.z())<1e-6),"actual Delete restores straight base XY shape and elevation");Files.writeString(OUT.resolve("attached-relative-interactions.txt"),"Actual widgets/packets: initial 0,0,0; edit 2,2,3; resize; Apply; reopen still 2,2,3; Delete removes control influence from real road. Passed.\n");stage=5;camera(mc);wait=25;return true;}return true;
 }
 static Scene crossing(ServerLevel l,RoadData data){
  var player=l.getServer().getPlayerList().getPlayers().get(0);UUID selected=null;
  for(boolean under:List.of(false,true)){
   int x=under?9260:9100;double y=under?-46:-56;var s=new Settings(Mode.STRAIGHT,Style.O1_ONE,5,1,.35,90).structure(Structure.AUTO);
   var aa=marker(l,x,y,0,0);var ab=marker(l,x,y,60,0);var a=data.connect(l,player,aa,ab,s,null);var ba=marker(l,x,y,380,0);var bb=marker(l,x,y,440,0);var b=data.connect(l,player,ba,bb,s,null);
   var ca=marker(l,x-70,under?y+4:y,220,-90);var cb=marker(l,x+70,under?y+4:y,220,-90);var cross=data.connect(l,player,ca,cb,new Settings(Mode.STRAIGHT,Style.O2_ONE,9,1,.35,90).structure(Structure.AUTO),null);
   var p=LaneTopology.metadata(data.index.roads.get(a.id()).record).points().stream().filter(v->v.origin()==LanePoints.Origin.AUTOMATIC_END).findFirst().orElseThrow();var q=LaneTopology.metadata(data.index.roads.get(b.id()).record).points().stream().filter(v->v.origin()==LanePoints.Origin.AUTOMATIC_START).findFirst().orElseThrow();
   var link=new LanePoints.Link(LanePoints.Ref.lane(a.id(),p.id()),LanePoints.Ref.lane(b.id(),q.id()),new LanePoints.Options(LanePoints.Path.DIRECT,false,false,24,32),null);var r=LaneRamps.generate(data,LaneTopology.records(data),UUID.randomUUID(),player.getUUID(),link);LaneRamps.build(data,l,player,r);r=data.index.roads.get(r.id()).record;
   var at=RoadQueries.horizontal(r.mesh(),new V(x,y,220)).sample().center();require(under?at.y()<p.position().y()-1:at.y()>p.position().y()+5,"actual client world builds automatic "+(under?"underpass":"overpass"));require(Math.abs(at.y()-cross.mesh().first().center().y())>=5,"built crossing has clear volume");if(!under)selected=r.id();
  }
  V above=new V(9100,-50,220),below=new V(9260,-48,220);return new Scene(selected,above,above.add(new V(38,24,45)),below,below.add(new V(22,20,45)));
 }
}
