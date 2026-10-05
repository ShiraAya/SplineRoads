package com.sora.splineroads;
import com.sora.splineroads.core.*;
import com.sora.splineroads.core.RoadGeometry.*;
import com.sora.splineroads.core.RoadInfrastructure.*;
import com.sora.splineroads.core.RoadStructures.*;
import com.sora.splineroads.world.RoadRecord;
import java.util.*;
public final class Revision32Validation {
  static int checks;static void check(boolean b,String s){checks++;if(!b)throw new AssertionError(s);}
  static Settings settings(Style style){return new Settings(Mode.STRAIGHT,style,style.defaultWidth(),1,.4,90).options(RoadProfile.Options.DEFAULT.infrastructure(Config.DEFAULT.gantry(Gantry.OFF)));}
  public static void main(String[] args){
    for(Style style:List.of(Style.O8_YELLOW,Style.O8_RAIL,Style.O8_GREEN,Style.O4_ONE,Style.H8_RAIL,Style.H8_GREEN,Style.H4_ONE)){
      var s=settings(style);s.validate();var m=RoadGeometry.build(new Node(new V(0,8,0),-90,0),new Node(new V(90,8,0),-90,0),s);
      var catalog=RoadProfile.catalog(style);check(catalog.lanes()==(catalog.twoWay()?8:4),"new lane count");check(RoadLaneLines.lines(m).size()>=5,"new lines selectable");check(!RoadSurface.build(m,List.of(),List.of()).markings().isEmpty(),"new paint exists");
      check(RoadRecord.readSettings(RoadRecord.writeSettings(s)).equals(s),"new style roundtrip");
    }
    var s=settings(Style.O4_YELLOW);var m=RoadGeometry.build(new Node(new V(0,2,0),-90,0),new Node(new V(72,2,0),-90,0),s);
    for(var line:RoadLaneLines.lines(m))for(var pattern:RoadLaneLines.Pattern.values()){
      var edit=new RoadLaneLines.Edit(line.key(),pattern,.16);var ss=s.options(s.options().laneLines(RoadLaneLines.with(List.of(),edit)));check(RoadRecord.readSettings(RoadRecord.writeSettings(ss)).equals(ss),"line roundtrip");check(ss.options().sidewalk(RoadSidewalks.Config.DEFAULT).infrastructure(Config.DEFAULT).traffic(true).extras(true,false,false).lift(.4,0).laneLines().equals(ss.options().laneLines()),"line edits survive other option changes");
      var mm=RoadGeometry.build(new Node(new V(0,2,0),-90,0),new Node(new V(72,2,0),-90,0),ss);var paint=RoadSurface.build(mm,List.of(),List.of());check(paint.pavement().equals(RoadSurface.build(m,List.of(),List.of()).pavement()),"paint does not change deck");
    }
    Ground lower=new Ground(){public double top(double x,double z,double deck){return 0;}public boolean joined(V p){return false;}public boolean blocked(Part p){return p.pier()&&Math.abs(p.a().x())<16;}};
    var support=RoadSupports.clearStandard(new Sample(new V(0,15,0),new V(1,0,0),12,9),1,lower);
    check(support.stream().filter(p->p.pier()&&p.height()>2).count()==2,"blocked center becomes two piers");check(support.stream().noneMatch(lower::blocked),"relocated piers outside lower carriageway");check(support.stream().anyMatch(p->!p.pier()&&p.a().distance(p.b())>=32),"crosshead extends between shifted supports");
    for(boolean left:List.of(false,true))for(Style main:List.of(Style.O2_YELLOW,Style.O4_RAIL,Style.O6_GREEN,Style.O8_YELLOW,Style.H4_RAIL,Style.H8_GREEN)){
      var a=settings(main).options(settings(main).options().traffic(left));var c=RoadProfile.catalog(main);var style=RoadProfile.choose(c.type(),c.lanes()/2,false,RoadProfile.Median.NONE,c.shoulder());var b=settings(style).options(settings(style).options().traffic(left));int sign=left?-1:1;
      var plan=YJunctionPlanner.plan(new Node(new V(0,2,0),180,0),new Node(new V(sign*24,2,-100),180,0),new Node(new V(-sign*24,2,-100),0,0),a,b,b,.4);
      check(plan.stem().first().center().equals(new V(0,2,0)),"Y stem begins at A");check(plan.outbound().last().center().equals(new V(sign*24,2,-100)),"outbound reaches B");check(plan.inbound().first().center().equals(new V(-sign*24,2,-100)),"inbound leaves C");
      double cap=plan.stem().last().halfWidth();var right=plan.stem().last().left();for(var p:List.of(plan.outbound().first(),plan.inbound().last()))check(Math.abs(p.center().sub(plan.throat().position()).dot(right))+p.halfWidth()<=cap+1e-6,"Y half-width stays within A");
    }
    var bridge=settings(Style.O4_YELLOW).structure(Structure.BRIDGE).options(s.options().infrastructure(Config.DEFAULT.bridge(Bridge.CABLE).gantry(Gantry.FRAME)).sidewalk(new RoadSidewalks.Config(true,RoadSidewalks.Side.BOTH,5,"minecraft:stone_bricks")));
    var bm=RoadGeometry.build(new Node(new V(0,20,0),-90,0),new Node(new V(240,20,0),-90,0),bridge);var parts=RoadStructures.plan(bm,Revision28Validation.ground(0));
    check(parts.stream().noneMatch(p->p.material()==Material.CB_SIGN||p.material()==Material.SIGN_BLUE||p.material()==Material.SIGN_GREEN),"gantry kept without signs");check(parts.stream().anyMatch(p->p.material()==Material.GANTRY_FRAME),"gantry frame remains");
    for(var finish:RoadProfile.Options.CycleFinish.values()){
      var cycle=s.options(s.options().cycleFinish(finish));var cm=RoadGeometry.build(new Node(new V(0,2,0),-90,0),new Node(new V(72,2,0),-90,0),cycle);var paint=RoadSurface.build(cm,List.of(),List.of());
      check(paint.markings().stream().anyMatch(f->f.color()==0x536F61)==(finish==RoadProfile.Options.CycleFinish.GREEN),"green surfacing only in green mode");
      check(RoadRecord.readSettings(RoadRecord.writeSettings(cycle)).equals(cycle),"cycle finish persists");check(cycle.options().sidewalk(RoadSidewalks.Config.DEFAULT).extras(cycle.options().cycle(),true,true).infrastructure(Config.DEFAULT).cycleFinish()==finish,"cycle finish survives other controls");
    }
    for(double[] angles:List.of(new double[]{0,90,180},new double[]{0,55,180},new double[]{0,90,180,270})){
      var spec=Junction22Validation.fixture(Style.O4_YELLOW,false,angles);var arms=new ArrayList<>(spec.arms());
      for(int i=0;i<arms.size();i++){var old=arms.get(i);arms.set(i,JunctionSpec.arm(old.endpoint(),old.inward(),old.external().options(old.external().options().extras(true,false,i%2==0).sidewalk(new RoadSidewalks.Config(true,RoadSidewalks.Side.BOTH,5,"minecraft:stone_bricks"))),true,i));}
      var jp=JunctionPlanner.plan(spec.arms(arms));var walks=jp.pieces().stream().flatMap(p->p.structures().stream()).filter(RoadSidewalks::smoothPart).toList();check(!walks.isEmpty(),"junction keeps sidewalks");
      var clipped=new SidewalkJoins(jp.pieces().stream().map(JunctionPlanner.Piece::mesh).toList(),List.of()).clip(walks);
      double before=walks.stream().mapToDouble(p->Math.abs(JunctionPaint.area(p.base()))).sum(),after=clipped.stream().mapToDouble(p->Math.abs(JunctionPaint.area(p.base()))).sum();check(Math.abs(before-after)<Math.max(1e-5,before*1e-6),"junction sidewalk/ tactile footprints do not overlap or enter carriageways "+Arrays.toString(angles)+" before="+before+" after="+after);
      check(new JunctionPlanner.Ref(spec.arms(arms),0).geometryVersion()==35,"new junction geometry versioned separately");
    }
    var elevated=settings(Style.O2_YELLOW).options(s.options().sidewalk(new RoadSidewalks.Config(true,RoadSidewalks.Side.BOTH,5,"minecraft:stone_bricks")));
    var em=RoadGeometry.build(new Node(new V(0,2,0),-90,0),new Node(new V(40,2,0),-90,0),elevated);Ground rising=new Ground(){public double top(double x,double z,double y){return x<30?1.4:2;}public boolean blocked(Part p){return false;}public boolean joined(V v){return false;}};
    var wp=RoadSidewalks.parts(em,elevated.options().sidewalk(),rising,List.of());check(wp.stream().anyMatch(p->p.material()==Material.STEEL&&!p.pier()&&p.b().x()>29),"walkway rail continues while still above ground");check(wp.stream().anyMatch(p->p.material()==Material.STEEL&&p.pier()&&p.a().x()>29),"walkway rail ends with a post");
    for(double groundY:new double[]{0,1.5}){
      var attachment=new RoadSigns.Attachment(0,"sign_speed_limit_120",RoadSigns.Mount.POLE,0,.5,2,4,1,false,List.of());var legacy=s.options(s.options().infrastructure(Config.DEFAULT.gantry(Gantry.FRAME).signs(List.of(attachment))));var lm=RoadGeometry.build(new Node(new V(0,2,0),-90,0),new Node(new V(120,2,0),-90,0),legacy);var old=new ArrayList<>(RoadSigns.plan(lm,Revision28Validation.ground(groundY)));var frame=new Part(new V(0,0,0),new V(1,0,0),.2,5,true,Material.GANTRY_FRAME);old.add(frame);var removed=RetiredRoadSigns.removed(lm,old);check(removed.size()==old.size()-1&&!removed.contains(frame),"old sign, pole, footing and optional cantilever removed together");
    }
    System.out.println("Revision32 PASS "+checks+" checks: lane catalog/paint isolation, portal supports, Y carriageways, frame-only gantries");
  }
}
