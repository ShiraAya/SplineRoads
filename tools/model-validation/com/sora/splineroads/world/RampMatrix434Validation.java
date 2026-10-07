package com.sora.splineroads.world;

import com.sora.splineroads.core.*;
import com.sora.splineroads.core.RoadGeometry.*;
import java.util.*;
import java.nio.file.*;

/** User's 501-block cross: independent cases, real planner and records, explicit MC adapters. */
public final class RampMatrix434Validation {
  static void check(boolean b,String message){if(!b)throw new AssertionError(message);}
  public static void main(String[] args) throws Exception {
    int row=Integer.parseInt(args[0]),col=Integer.parseInt(args[1]);
    boolean left=args.length>2&&Boolean.parseBoolean(args[2]);
    var type=args.length>3&&args[3].equals("highway")?RoadProfile.Type.HIGHWAY:RoadProfile.Type.ORDINARY;
    var settings=Hotfix429ModelValidation.settings(type,3,3,left);
    var ground=Hotfix429ModelValidation.road(new V(0,64,-250),new V(0,64,250),settings);
    var upper=Hotfix429ModelValidation.road(new V(-250,72,0),new V(250,72,0),settings);
    var all=new LinkedHashMap<UUID,RoadRecord>();all.put(ground.id(),ground);all.put(upper.id(),upper);
    var same=LaneSections.live(ground.mesh(),0).lanes().stream().filter(l->l.sign()>0)
        .sorted(Comparator.comparingDouble((LanePoints.Lane l)->l.position().sub(ground.mesh().first().center()).horizontalLength()).reversed()).toList();
    int sourceSlot=same.get(Math.max(0,row-1)).index();
    int[] targetSlots={2,2,1,0,3,4,5,5};int targetSlot=targetSlots[col-1];
    var tl=LanePoints.lane(upper.mesh(),250,targetSlot);double targetStation=args.length>5?Double.parseDouble(args[5]):tl.sign()>0?500:0;
    var a=Hotfix429ModelValidation.point(all,ground,0,sourceSlot);
    var b=Hotfix429ModelValidation.point(all,upper,targetStation,targetSlot);
    var sourceBefore=all.get(ground.id()).header();var targetBefore=all.get(upper.id()).header();
    var landing=args.length>6&&args[6].equals("flexible")?LanePoints.Landing.FLEXIBLE:LanePoints.Landing.EXACT;
    var path=LanePoints.Path.valueOf(System.getProperty("matrix.path","AUTO"));
    var options=new LanePoints.Options(path,row==0?LanePoints.Departure.EXTRA:LanePoints.Departure.TEMPORARY,
        col==1||col==8?LanePoints.Arrival.EXTRA:LanePoints.Arrival.MERGE,24,32,LanePoints.Elevation.AUTO,landing);
    var link=new LanePoints.Link(a,b,options,null);UUID id=new UUID(434,row*10+col);
    long start=System.nanoTime();String label=""+(char)('A'+row)+col;
    System.out.printf(Locale.ROOT,"START %s left=%s type=%s source_slot=%d target_slot=%d A=%s B=%s%n",label,left,type,sourceSlot,targetSlot,LanePoints.lane(ground.mesh(),0,sourceSlot).position(),LanePoints.lane(upper.mesh(),targetStation,targetSlot).position());
    try {
      var ramp=LaneRamps.generate(null,all,id,new UUID(434,100),link);var mesh=ramp.mesh();
      check(all.get(ground.id()).header().equals(sourceBefore)&&all.get(upper.id()).header().equals(targetBefore),"planning mutated host");
      var actual=LaneTopology.metadata(ramp).link();if(landing==LanePoints.Landing.EXACT)check(actual.targetOffset()==0,"exact B moved");
      check(LaneRampAlignment.axis(mesh,true).distance(LanePoints.lane(ground.mesh(),0,sourceSlot).position())<1e-5,"wrong departure axis");
      check(LaneRampAlignment.axis(mesh,false).distance(LanePoints.lane(upper.mesh(),targetStation+tl.sign()*actual.targetOffset(),targetSlot).position())<1e-5,"wrong arrival axis");
      check(RoadRibbon.start(mesh).direction().dot(LanePoints.lane(ground.mesh(),0,sourceSlot).direction())>.999,"wrong departure direction");
      check(RoadRibbon.end(mesh).direction().dot(tl.direction())>.999,"wrong arrival direction");
      LaneRampGrade.validate(mesh,LaneRamps.gradeLimit(all,actual));
      all.put(id,ramp);LaneCrossSections.reconcile(all);LaneRamps.validate(mesh,all,id,actual);
      if(row>0)check(LaneTopology.metadata(all.get(ground.id())).cuts().stream().anyMatch(c->c.connection().equals(id)&&!c.arrival()&&c.lane()==sourceSlot),"missing source closure");
      // A free road entrance has no upstream material to close. A real upstream
      // slot must be reserved; exact contacts and downstream clearance still run.
      if(col!=1&&col!=8&&(tl.sign()>0?targetStation:500-targetStation)+actual.targetOffset()>.1)
        check(LaneTopology.metadata(all.get(upper.id())).cuts().stream().anyMatch(c->c.connection().equals(id)&&c.arrival()&&c.lane()==targetSlot),"missing target closure");
      for(var road:List.of(all.get(ground.id()),all.get(upper.id()))) {
        int chosen=road.id().equals(ground.id())?sourceSlot:targetSlot;
        check(LaneTopology.metadata(road).cuts().stream().allMatch(c->c.lane()==chosen),"closed unrelated lane");
        check(RoadRecord.load(road.header()).settings().options().lanePoints().cuts().equals(LaneTopology.metadata(road).cuts()),"closure roundtrip failed");
      }
      if(args.length>4){var out=Path.of(args[4]);Files.createDirectories(out);var lines=new ArrayList<String>();lines.add("station,x,y,z,half_width");for(var s:mesh.samples())lines.add(String.format(Locale.ROOT,"%.6f,%.6f,%.6f,%.6f,%.6f",s.distance(),s.center().x(),s.center().y(),s.center().z(),s.halfWidth()));Files.write(out.resolve(label+".csv"),lines);}
      all.remove(id);LaneCrossSections.reconcile(all);
      check(all.get(ground.id()).header().equals(sourceBefore)&&all.get(upper.id()).header().equals(targetBefore),"delete did not restore hosts");
      double max=0,total=0;for(int i=1;i<mesh.samples().size();i++){var delta=mesh.samples().get(i).center().sub(mesh.samples().get(i-1).center());max=Math.max(max,Math.abs(delta.y())/delta.horizontalLength());total+=Math.abs(delta.y());}
      System.out.printf(Locale.ROOT,"RESULT %s PASS length=%.3f max_grade=%.6f ymin=%.3f ymax=%.3f vertical_travel=%.3f ms=%.3f%n",label,mesh.length(),max,mesh.min().y(),mesh.max().y(),total,(System.nanoTime()-start)/1e6);
    } catch(RuntimeException|AssertionError e){System.out.printf(Locale.ROOT,"RESULT %s FAIL ms=%.3f %s%n",label,(System.nanoTime()-start)/1e6,e.getMessage());throw e;}
  }
}
