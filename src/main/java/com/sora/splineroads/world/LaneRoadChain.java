package com.sora.splineroads.world;

import com.sora.splineroads.core.*;
import com.sora.splineroads.core.RoadGeometry.*;
import java.util.*;
import net.minecraft.core.BlockPos;

/** A lane-continuous road chain, not a whole-road count/style equivalence test.
 * Coordinates are signed distance from the clicked point, in its driving direction.
 * Never guesses through a junction or jumps sideways to an unrelated lane. */
public final class LaneRoadChain {
  public record Leg(RoadRecord road,int slot,int sign,double zero){
    public double coordinate(double station){return zero+sign*station;}
    public double low(){return coordinate(sign>0?0:road.rawMesh().length());}
    public double high(){return coordinate(sign>0?road.rawMesh().length():0);}
    public double station(double coordinate){return Math.max(0,Math.min(road.rawMesh().length(),sign*(coordinate-zero)));}
  }
  public record Position(RoadRecord road,LanePoints.Point point,double station){}
  private final List<Leg> legs;
  private final LanePoints.Point origin;
  private LaneRoadChain(List<Leg> legs,LanePoints.Point origin){this.legs=List.copyOf(legs);this.origin=origin;}
  public List<Leg> legs(){return legs;}
  public Set<UUID> ids(){var out=new LinkedHashSet<UUID>();for(var leg:legs)out.add(leg.road().id());return out;}
  public static LaneRoadChain of(Map<UUID,RoadRecord> all,LanePoints.Ref ref){
    var root=all.get(ref.road());if(root==null)throw new IllegalArgumentException("所选连续车道已不存在");
    var point=LaneTopology.point(root,ref.point());var lane=LanePoints.lane(root.rawMesh(),point);
    var first=new Leg(root,point.lane(),lane.sign(),-lane.sign()*lane.station());
    var out=new ArrayList<Leg>();out.add(first);var seen=new HashSet<UUID>();seen.add(root.id());
    var incident=new HashMap<BlockPos,List<RoadRecord>>();
    for(var road:all.values())if(road.assembly()==null&&road.junction()==null&&LaneTopology.metadata(road).link()==null){
      incident.computeIfAbsent(road.a(),k->new ArrayList<>()).add(road);
      if(!road.b().equals(road.a()))incident.computeIfAbsent(road.b(),k->new ArrayList<>()).add(road);
    }
    for(int direction:new int[]{-1,1}){
      Leg current=first;
      for(int n=0;n<64;n++){
        double d=current.sign()*direction>0?current.road().rawMesh().length():0;
        double boundary=current.coordinate(d);if(Math.abs(boundary)>1024)break;
        BlockPos node=d==0?current.road().a():current.road().b();
        var near=incident.getOrDefault(node,List.of());if(near.size()!=2)break;
        var old=LanePoints.lane(current.road().rawMesh(),d,current.slot());Leg next=null;
        for(var road:near)if(!seen.contains(road.id())){
          if(!LanePoints.supported(road.settings())||road.rawMesh().closed())continue;
          double at=road.a().equals(node)?0:road.rawMesh().length();
          for(int slot:LaneAdditions.slots(road.rawMesh(),at)){
            var lane2=LanePoints.lane(road.rawMesh(),at,slot);
            // Same physical lane center, heading and width. Unrelated lane-count
            // changes on the other side of the median do not break continuity.
            if(old.direction().dot(lane2.direction())<.995||old.position().distance(lane2.position())>.25
                ||Math.abs(old.width()-lane2.width())>.25)continue;
            if((at==0?1:-1)!=lane2.sign()*direction)continue;
            if(next!=null)throw new IllegalArgumentException("车道接缝存在多个同向匹配，不能自动换道");
            next=new Leg(road,slot,lane2.sign(),boundary-lane2.sign()*at);
          }
        }
        if(next==null)break;out.add(next);seen.add(next.road().id());current=next;
      }
    }
    out.sort(Comparator.comparingDouble(Leg::low));return new LaneRoadChain(out,point);
  }
  public Position at(double offset){
    Leg selected=null;
    for(var leg:legs)if(offset>=leg.low()-1e-6&&offset<=leg.high()+1e-6){selected=leg;if(offset<leg.high()-1e-6)break;}
    if(selected==null)throw new IllegalArgumentException("同车道搜索到达实际断头、分岔或不连续接缝，未跨入相邻车道");
    double at=selected.station(offset);var point=LanePoints.point(origin.id(),LanePoints.Origin.MANUAL,selected.road().rawMesh(),at,selected.slot());
    return new Position(selected.road(),point,at);
  }
  public Mesh sweep(){
    var out=new ArrayList<Sample>();double shift=legs.get(0).low();double thickness=0;
    for(var leg:legs){var raw=leg.road().rawMesh();thickness=Math.max(thickness,raw.settings().thickness());
      var samples=new ArrayList<>(raw.samples());if(leg.sign()<0)Collections.reverse(samples);
      for(var sample:samples){double station=leg.coordinate(sample.distance())-shift;
        var lane=LanePoints.lane(raw,sample.distance(),leg.slot());
        if(lane.sign()!=leg.sign())throw new IllegalArgumentException("所选车道在断面变化处已终止，不能跨到反向车道");
        if(!out.isEmpty()&&station-out.get(out.size()-1).distance()<1e-7)continue;
        out.add(new Sample(lane.position(),lane.direction().left(),station,lane.width()/2+.30));
      }
    }
    var settings=new Settings(Mode.CURVE,Style.C1_RAMP,4,thickness,.35,90);
    var bounds=RoadRibbon.mesh(out,settings);
    return new Mesh(List.copyOf(out),settings,bounds.min(),bounds.max(),out.get(out.size()-1).distance(),false,null);
  }
  /** Append only the real unsafe interval, in each host's own station system. */
  public void reserve(Map<UUID,List<LaneSections.Event>> events,UUID connection,Mesh ramp,
      List<RoadStructures.Part> parts,boolean arrival,double endOffset,double transition,boolean rectangular){
    double low=legs.get(0).low(),high=legs.get(legs.size()-1).high();
    double begin=arrival?low:0,end=arrival?endOffset:high;
    if(ramp!=null){
      var sweep=sweep();var prepared=RoadClearance.prepare(sweep);var contacts=new ArrayList<>(RoadClearance.contacts(prepared,ramp));
      for(var part:parts){RoadPlanningBudget.check();contacts.addAll(RoadClearance.contacts(prepared,LaneReopening.envelope(part,ramp.settings())));}
      if(arrival){
        begin=endOffset;
        for(var c:contacts)if(c.blocked()){
          double from=c.from()+low,to=c.to()+low;
          if(from<=endOffset+.01)begin=Math.min(begin,from);
          if(to>endOffset+.5)throw new IllegalArgumentException("汇入点下游仍有实际净空侵占，不能恢复车道");
        }
        begin-=1+(rectangular?0:transition);
      }else{
        end=0;for(var c:contacts)if(c.blocked()&&c.to()+low>=-.01)end=Math.max(end,c.to()+low);
        end+=1+(rectangular?0:transition);
      }
    }
    begin=Math.max(low,begin);end=Math.min(high,end);
    for(var leg:legs){double a=Math.max(begin,leg.low()),b=Math.min(end,leg.high());if(b-a<.02)continue;
      double start=leg.station(a),finish=leg.station(b);boolean under=false;
      if(ramp!=null){
        // Choose the strongest locally overlapping vertical displacement, not the
        // request's AUTO label. Below-grade mouths never get above-grade planters.
        double delta=0;
        for(double d=a;d<=b+.001;d+=.5){var lane=LanePoints.lane(leg.road().rawMesh(),leg.station(d),leg.slot());var q=RoadQueries.horizontal(ramp,lane.position());
          if(q.horizontalDistance()>q.sample().halfWidth()+lane.width()/2+.3)continue;
          double dy=q.sample().center().y()-lane.position().y();if(Math.abs(dy)>Math.abs(delta))delta=dy;
        }under=delta<-.1;
      }
      var event=new LaneSections.Event(connection,arrival?LaneSections.Kind.ARRIVE:LaneSections.Kind.TEMPORARY,
          leg.slot(),leg.sign(),arrival?finish:start,transition,arrival?start:finish,rectangular,under);
      events.computeIfAbsent(leg.road().id(),k->new ArrayList<>()).add(event);
    }
  }
}
