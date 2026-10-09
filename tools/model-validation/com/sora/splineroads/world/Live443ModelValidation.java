package com.sora.splineroads.world;
import com.sora.splineroads.core.*;
import com.sora.splineroads.core.RoadGeometry.*;
import java.util.*;

public final class Live443ModelValidation {
  static int checks;
  static void check(boolean ok,String why){checks++;if(!ok)throw new AssertionError(why);}
  static void fork(boolean incoming,int mirror){
    var s=new Settings(Mode.STRAIGHT,Style.C1_RAMP,4,1,.35,90).structure(Structure.BRIDGE);
    var source=Hotfix429ModelValidation.road(new V(0,20,0),new V(0,20,260),s);
    var target=Hotfix429ModelValidation.road(new V(80*mirror,28,180),new V(80*mirror,28,540),s);
    var all=new LinkedHashMap<UUID,RoadRecord>();all.put(source.id(),source);all.put(target.id(),target);
    var a=Hotfix429ModelValidation.point(all,source,60,0);
    var b=Hotfix429ModelValidation.point(all,target,220,0);
    var opts=new LanePoints.Options(LanePoints.Path.AUTO,LanePoints.Departure.BRANCH,LanePoints.Arrival.FLOW,24,32,LanePoints.Elevation.AUTO,LanePoints.Landing.EXACT);
    var id=UUID.randomUUID();var link=new LanePoints.Link(a,b,opts,null);
    var ramp=LaneRamps.generate(null,all,id,source.owner(),link);all.put(id,ramp);LaneCrossSections.reconcile(all);
    var m=ramp.mesh();
    for(boolean first:new boolean[]{true,false}){
      var host=(first?source:target).mesh();int end=LaneRampThroat.end(m,List.of(host),first);
      check(first?end>2:end<m.samples().size()-3,"fork has no shared throat");
      for(int i=first?0:end;i<(first?end+1:m.samples().size());i++){
        var at=m.samples().get(i);var q=RoadQueries.horizontal(host,at.center());
        check(Math.abs(at.center().y()-q.sample().center().y())<1e-5,"shared ordinary fork changed height early");
      }
    }
    LaneRamps.validate(m,all,id,LaneTopology.metadata(ramp).link());
    var unchanged=LaneRamps.generate(null,all,id,source.owner(),LaneTopology.metadata(ramp).link());
    check(unchanged.mesh().samples().equals(m.samples()),"unchanged fork refit drifts on repeated preview");
    check(RoadRecord.load(ramp.save()).mesh().samples().equals(m.samples()),"fork height lost on save/reload");
    check(all.get(source.id()).mesh().samples().equals(source.mesh().samples()),"ordinary fork moved straight host");
  }
  public static void main(String[] args){fork(false,1);fork(true,-1);System.out.println("Live443ModelValidation "+checks+" checks PASS; generated normal fork, merging throat, persistence and repeated preview");}
}
