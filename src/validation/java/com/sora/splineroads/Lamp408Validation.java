package com.sora.splineroads;
import com.sora.splineroads.core.*;
import com.sora.splineroads.core.RoadGeometry.V;
import com.sora.splineroads.core.RoadStructures.*;
import java.util.*;
/** Four actual procedural lamp assemblies versus external walkway solids. No world placement. */
public final class Lamp408Validation {
  public static void main(String[] args){
    int checks=0;
    for(int style=1;style<=4;style++){
      var lamp=RoadStreetscape.lamp(new V(15,0,3),new V(-1,0,0),style);
      var low=new Part(new V(15,6,0),new V(15,6,8),7,.5,false,Material.WALK_STONE_BRICKS);
      var high=new Part(new V(15,12,0),new V(15,12,8),7,.5,false,Material.WALK_STONE_BRICKS);
      var lowIndex=new RoadSolidOverlap.Index(List.of(low));var highIndex=new RoadSolidOverlap.Index(List.of(high));
      if(lamp.stream().noneMatch(lowIndex::intersects))throw new AssertionError("actual lamp "+style+" missed upper sidewalk");checks++;
      if(lamp.stream().anyMatch(highIndex::intersects))throw new AssertionError("actual lamp "+style+" incorrectly hits a clear ceiling");checks++;
    }
    System.out.println("Lamp408Validation: "+checks+" checks on four production lamp styles PASS; actual assembly geometry, not Minecraft placement/GPU.");
  }
}
