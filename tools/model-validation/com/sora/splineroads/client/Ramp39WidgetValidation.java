package com.sora.splineroads.client;
import com.sora.splineroads.core.*;
import com.sora.splineroads.world.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.*;
import net.minecraft.nbt.*;
import java.util.*;
/** Headless widget-state test with adapters. No input device, Forge event or GPU test. */
public final class Ramp39WidgetValidation {
 static int checks;static void check(boolean b,String m){checks++;if(!b)throw new AssertionError(m);}
 static Object value(Object o,String name)throws Exception{var field=o.getClass().getDeclaredField(name);field.setAccessible(true);return field.get(o);}
 static void press(LaneRampScreen s,String label){s.children().stream().filter(w->w instanceof Button b&&b.getMessage().getString().startsWith(label)).map(w->(Button)w).findFirst().orElseThrow().onPress();}
 public static void main(String[] args)throws Exception{
  var payload=new CompoundTag();var from=LanePoints.Ref.lane(new UUID(0,1),new UUID(0,2));var to=LanePoints.Ref.lane(new UUID(0,3),new UUID(0,4));payload.put("From",LanePointCodec.ref(from));payload.put("To",LanePointCodec.ref(to));payload.put("Options",LanePointCodec.options(LanePoints.Options.DEFAULT));
  LaneRampScreen.open(payload);var s=(LaneRampScreen)Minecraft.getInstance().screen;
  check(s.children().stream().filter(w->w instanceof Button).count()==15,"five paths, four mode selectors, grade/tunnel selectors, three actions and failure details");
  check(!((Boolean)value(s,"gradeOverride")),"override is off for existing/default payload");press(s,"坡比超限：");check((Boolean)value(s,"gradeOverride"),"override selector changes actual state");
  check(!((Boolean)value(s,"allowTunnel")),"tunnels are off by default");press(s,"允许隧道：");check((Boolean)value(s,"allowTunnel"),"tunnel selector changes actual state");
  for(var path:LanePoints.Path.values()){s.children().stream().filter(w->w instanceof Button b&&b.getMessage().getString().endsWith(path.label)).map(w->(Button)w).findFirst().orElseThrow().onPress();check(value(s,"path")==path,"path button changes actual state");}
  press(s,"汇出：");check(value(s,"departure")==LanePoints.Departure.DETACH,"departure changes to DETACH");check(value(s,"arrival")==LanePoints.Arrival.MERGE,"arrival is independent");press(s,"汇入：");press(s,"自动避让");press(s,"同车道弹性落点");
  ((EditBox)value(s,"radius")).setValue("48");((EditBox)value(s,"transition")).setValue("40");s.resize(Minecraft.getInstance(),1000,600);
  check(value(s,"departure")==LanePoints.Departure.DETACH&&value(s,"arrival")==LanePoints.Arrival.ADD,"resize retains topology mode");
  check(value(s,"elevation")==LanePoints.Elevation.OVER&&value(s,"landing")==LanePoints.Landing.EXACT,"resize retains crossing and precision mode");
  var method=s.getClass().getDeclaredMethod("command",String.class);method.setAccessible(true);var command=(CompoundTag)method.invoke(s,"laneRampPreview");var options=LanePointCodec.options(command.getCompound("Options"));
  check(options.allowTunnel()&&options.gradeOverride()&&options.radius()==48&&options.transition()==40&&options.departure()==LanePoints.Departure.DETACH&&options.arrival()==LanePoints.Arrival.ADD&&options.landing()==LanePoints.Landing.EXACT,"outgoing command contains displayed settings");
  check((Boolean)value(s,"gradeOverride"),"override survives resize and other options");
  check(!((Button)value(s,"details")).visible,"failure details visible before a failure");
  s.failed("固定接头冲突；X=12 Y=20 Z=34；输入坡比合法但净空不可达");
  check(((Button)value(s,"details")).visible&&((Button)value(s,"details")).active,"failure details unavailable");
  check(!((Button)value(s,"build")).active&&value(s,"token")==null,"failed draft remains buildable");
  press(s,"失败详情");var page=Minecraft.getInstance().screen;check(page!=s,"details page did not open");
  page.onClose();check(Minecraft.getInstance().screen==s,"details close loses selected connector");
  var start=new com.sora.splineroads.core.RoadGeometry.Node(new com.sora.splineroads.core.RoadGeometry.V(0,20,0),0,0);
  var end=new com.sora.splineroads.core.RoadGeometry.Node(new com.sora.splineroads.core.RoadGeometry.V(0,20,64),0,0);
  var settings=new com.sora.splineroads.core.RoadGeometry.Settings(com.sora.splineroads.core.RoadGeometry.Mode.STRAIGHT,com.sora.splineroads.core.RoadGeometry.Style.O1_ONE,4,1,.35,90);
  var road=new RoadRecord(new UUID(439,99),new UUID(0,1),new net.minecraft.core.BlockPos(0,20,0),new net.minecraft.core.BlockPos(0,20,64),start,end,settings,false,4);
  var reply=new CompoundTag();var conflicts=new ListTag();conflicts.add(road.header());reply.put("ConflictRoads",conflicts);
  s.failed("实际冲突道路 "+road.id(),reply);
  check(ClientRoads.conflictPreview&&ClientRoads.preview!=null&&ClientRoads.nodePreviews.size()==1,"failure did not publish red world geometry");
  check(!value(s,"status").toString().contains(road.id().toString()),"UUID still exposed in player status");
  s.onClose();check(ClientRoads.preview!=null&&ClientRoads.conflictPreview,"closing settings removed conflict highlight");
  LaneRampScreen.resume();check(ClientRoads.preview!=null&&ClientRoads.conflictPreview,"resuming settings removed conflict highlight");
  press(s,"汇入：");check(!ClientRoads.conflictPreview&&ClientRoads.preview==null,"changed options retained stale conflict");
  s.failed("实际冲突道路 "+road.id(),reply);
  LaneRampScreen.clear();check(!ClientRoads.conflictPreview,"shift-reset retained red state");check(ClientRoads.preview==null&&ClientRoads.nodePreviews.isEmpty(),"clear releases both shared preview fields");
  System.out.println("Ramp39WidgetValidation: "+checks+" checks passed; real screen state with test-only widgets, no Minecraft/GPU/network test");
 }
}
