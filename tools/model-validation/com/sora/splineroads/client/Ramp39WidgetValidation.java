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
  check(s.children().stream().filter(w->w instanceof Button).count()==12,"five paths, four mode selectors, three actions");
  for(var path:LanePoints.Path.values()){s.children().stream().filter(w->w instanceof Button b&&b.getMessage().getString().endsWith(path.label)).map(w->(Button)w).findFirst().orElseThrow().onPress();check(value(s,"path")==path,"path button changes actual state");}
  press(s,"汇出：");check(value(s,"departure")==LanePoints.Departure.DETACH,"departure changes to DETACH");check(value(s,"arrival")==LanePoints.Arrival.MERGE,"arrival is independent");press(s,"汇入：");press(s,"自动避让");press(s,"同车道弹性落点");
  ((EditBox)value(s,"radius")).setValue("48");((EditBox)value(s,"transition")).setValue("40");s.resize(Minecraft.getInstance(),1000,600);
  check(value(s,"departure")==LanePoints.Departure.DETACH&&value(s,"arrival")==LanePoints.Arrival.REPLACE,"resize retains topology mode");
  check(value(s,"elevation")==LanePoints.Elevation.OVER&&value(s,"landing")==LanePoints.Landing.EXACT,"resize retains crossing and precision mode");
  var method=s.getClass().getDeclaredMethod("command",String.class);method.setAccessible(true);var command=(CompoundTag)method.invoke(s,"laneRampPreview");var options=LanePointCodec.options(command.getCompound("Options"));
  check(options.radius()==48&&options.transition()==40&&options.departure()==LanePoints.Departure.DETACH&&options.arrival()==LanePoints.Arrival.REPLACE&&options.landing()==LanePoints.Landing.EXACT,"outgoing command contains displayed settings");
  LaneRampScreen.clear();check(ClientRoads.preview==null&&ClientRoads.nodePreviews.isEmpty(),"clear releases both shared preview fields");
  System.out.println("Ramp39WidgetValidation: "+checks+" checks passed; real screen state with test-only widgets, no Minecraft/GPU/network test");
 }
}
