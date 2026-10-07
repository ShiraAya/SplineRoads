package com.sora.splineroads.core;
import com.sora.splineroads.core.RoadGeometry.*;
import java.util.*;
public final class Clearance432Validation {
 public static void main(String[] args){long checks=0;var r=new Random(432);
  for(int i=0;i<300;i++){
   double x=i%4==0?92000:-10,z=i%4==0?95000:-15;
   Mesh a=Performance432Validation.road(r.nextDouble()*6,x,z,12+r.nextDouble()*24,4+r.nextDouble()*8,20,i%3==0?.18:0);
   Mesh b=Performance432Validation.road(r.nextDouble()*6,x+r.nextDouble()*4,z+r.nextDouble()*4,12+r.nextDouble()*24,4+r.nextDouble()*8,18+r.nextDouble()*8,i%2==0?-.18:0);
   var old=Reference432Clearance.contacts(a,b);var current=RoadClearance.contacts(a,b);
   if(old.size()!=current.size())throw new AssertionError("contact count "+i+" old="+old.size()+" new="+current.size());
   for(int j=0;j<old.size();j++){var c=old.get(j);var n=current.get(j);checks++;
     if(!new RoadClearance.Contact(c.from(),c.to(),c.ours(),c.other(),c.usableClearance(),c.raise(),c.lower()).equals(n))throw new AssertionError("contact exact "+i+"/"+j+" old="+c+" new="+n);}
   var part=new RoadStructures.Part(new V(x,20,z),new V(x+12,21,z+6),1.2,3,false,RoadStructures.Material.CONCRETE);
   if(RoadClearance.structureInvades(part,a,4)!=Reference432Clearance.structureInvades(part,a,4))throw new AssertionError("structure exact "+i);checks++;
  }
  System.out.println("Clearance432Validation "+checks+" exact contact/order/structure assertions PASS");
 }
}
