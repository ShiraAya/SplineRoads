package com.sora.splineroads.core;

import com.sora.splineroads.core.RoadGeometry.*;
import com.sora.splineroads.core.RoadStructures.*;
import java.util.*;

/** Authored street amenities. All positions share the same road cross section. */
public final class RoadStreetscape {
  public enum Separator { LINE("白实线"), RAIL("护栏"), GREEN("绿化带");
    public final String label; Separator(String label){this.label=label;} }
  public enum Planting { NONE("关闭"), OAK("橡木"), BIRCH("白桦"), SPRUCE("云杉"), CHERRY("樱花树"), FLOWERS("花坛");
    public final String label; Planting(String label){this.label=label;} }
  public record Config(Separator separator,boolean parking,double lampSpacing,boolean walkLamps,
      double walkLampSpacing,Planting planting,double plantingSpacing,List<Span> raisedSpans) {
    public Config(Separator separator,boolean parking,double lampSpacing,boolean walkLamps,double walkLampSpacing,Planting planting,double plantingSpacing){this(separator,parking,lampSpacing,walkLamps,walkLampSpacing,planting,plantingSpacing,List.of());}
    public Config raisedSpans(List<Span> v){return new Config(separator,parking,lampSpacing,walkLamps,walkLampSpacing,planting,plantingSpacing,v);}
    public static final Config DEFAULT=new Config(Separator.LINE,false,24,false,24,Planting.NONE,16);
    public Config {
      if(separator==null||planting==null||!RoadGeometry.finite(lampSpacing,walkLampSpacing,plantingSpacing)
          ||lampSpacing<8||lampSpacing>96||walkLampSpacing<8||walkLampSpacing>96||plantingSpacing<8||plantingSpacing>64)
        throw new IllegalArgumentException("路灯间距 8–96 格，绿化间距 8–64 格");
      if(parking)separator=Separator.LINE;
      raisedSpans=List.copyOf(raisedSpans);if(raisedSpans.size()>8192)throw new IllegalArgumentException("高架分段过多");
    }
    public Config separator(Separator v){return new Config(v,parking,lampSpacing,walkLamps,walkLampSpacing,planting,plantingSpacing,raisedSpans);}
    public Config parking(boolean v){return new Config(separator,v,lampSpacing,walkLamps,walkLampSpacing,planting,plantingSpacing,raisedSpans);}
    public Config lampSpacing(double v){return new Config(separator,parking,v,walkLamps,walkLampSpacing,planting,plantingSpacing,raisedSpans);}
    public Config walkLamps(boolean v){return new Config(separator,parking,lampSpacing,v,walkLampSpacing,planting,plantingSpacing,raisedSpans);}
    public Config walkLampSpacing(double v){return new Config(separator,parking,lampSpacing,walkLamps,v,planting,plantingSpacing,raisedSpans);}
    public Config planting(Planting v){return new Config(separator,parking,lampSpacing,walkLamps,walkLampSpacing,v,plantingSpacing,raisedSpans);}
    public Config plantingSpacing(double v){return new Config(separator,parking,lampSpacing,walkLamps,walkLampSpacing,planting,v,raisedSpans);}
  }
  public record Span(double from,double to){public Span{if(!RoadGeometry.finite(from,to)||from<0||to<=from)throw new IllegalArgumentException("无效的高架范围");}}
  public static List<Span> classify(Mesh mesh,Ground ground){
    if(mesh.settings().structure()!=Structure.AUTO)return List.of();
    mesh=ground.terrainReference(mesh);
    var out=new ArrayList<Span>();double begin=-1;
    for(double d=0;d<mesh.length()-1e-6;d+=1){double end=Math.min(mesh.length(),d+1);boolean raised=bridge(mesh,RoadStructures.sample(mesh,(d+end)/2),ground);
      if(raised&&begin<0)begin=d;if(!raised&&begin>=0){out.add(new Span(begin,d));begin=-1;}}
    if(begin>=0)out.add(new Span(begin,mesh.length()));return List.copyOf(out);
  }
  public static Mesh resolve(Mesh mesh,Ground ground){
    var options=mesh.settings().options();var next=options.streetscape(options.streetscape().raisedSpans(classify(mesh,ground)));
    Mesh reference=mesh.reference();
    if(reference!=null) {
      var ro=reference.settings().options();
      var rs=reference.settings().options(ro.streetscape(ro.streetscape().raisedSpans(next.streetscape().raisedSpans())));
      reference=new Mesh(reference.samples(),rs,reference.min(),reference.max(),reference.length(),reference.closed(),reference.controlPoint(),reference.controls(),reference.reference());
    }
    return new Mesh(mesh.samples(),mesh.settings().options(next),mesh.min(),mesh.max(),mesh.length(),mesh.closed(),mesh.controlPoint(),mesh.controls(),reference);
  }
  public static boolean raised(Mesh mesh,Sample sample){return mesh.settings().structure()==Structure.BRIDGE||mesh.settings().structure()==Structure.AUTO&&mesh.settings().options().streetscape().raisedSpans().stream().anyMatch(s->sample.distance()>=s.from()&&sample.distance()<s.to());}
  public static boolean walkLampAllowed(RoadProfile.Options o,Structure structure,Style style){return RoadProfile.catalog(style).type()==RoadProfile.Type.ORDINARY&&RoadProfile.catalog(style).twoWay()&&structure!=Structure.BRIDGE&&structure!=Structure.TUNNEL&&o.sidewalk().enabled()&&o.cycle()&&o.streetscape().separator()!=Separator.LINE;}
  public static double parkingLength(){return 8;}
  public static double separatorWidth(RoadProfile.Options o){return o.cycle()&&!o.streetscape().parking()&&o.streetscape().separator()==Separator.GREEN?1.5:0;}
  public static boolean walkSide(RoadSidewalks.Config c,int side){return c.enabled()&&(c.side()==RoadSidewalks.Side.BOTH||c.side()==RoadSidewalks.Side.LEFT&&side<0||c.side()==RoadSidewalks.Side.RIGHT&&side>0);}
  public static RoadSidewalks.Config fitWalk(RoadSidewalks.Config walk,Config c){
    if(!walk.enabled())return walk;
    // Inner tree/lamp row, an optional separate pedestrian-lamp row, outer tactile row.
    double inner=c.planting()==Planting.NONE?1.2:c.planting()==Planting.FLOWERS?2.2:3.6;
    double minimum=inner+(c.walkLamps()?1.2:0)+(walk.tactile()?1.4:.4);
    return walk.width(Math.max(walk.width(),(int)Math.ceil(minimum)));
  }
  public static double tactileOffset(double width){return Math.max(.5,width-.8);}
  public static boolean bridge(Mesh mesh,Sample at,Ground g){return mesh.settings().structure()==Structure.BRIDGE||mesh.settings().structure()!=Structure.GROUND&&RoadStructures.elevated(mesh,at,g);}
  public static List<Part> plan(Mesh mesh,Ground ground,RoadFurniture.Phase phase){
    if(RoadProfile.catalog(mesh.settings().style()).type()!=RoadProfile.Type.ORDINARY&&mesh.settings().style()!=Style.C1_RAMP||mesh.settings().structure()==Structure.TUNNEL)return List.of();
    var out=new ArrayList<Part>();var o=mesh.settings().options();var c=o.streetscape();var walk=o.sidewalk();
    for(double d=phase.first(8,c.lampSpacing());d<mesh.length()-1e-6;d+=c.lampSpacing()){
      if(d<1||mesh.length()-d<1||RoadInfrastructure.nearGantry(mesh,ground,d))continue;
      var at=RoadStructures.sample(mesh,d);var l=RoadProfile.layout(mesh,at);boolean raised=bridge(mesh,at,ground);
      if(!l.catalog().twoWay()){
        // Preserve 0.35.3's one-way outside edge / curb and traffic-side convention.
        int side=l.outside();double curb=l.curbWidth()>0&&!raised?RoadProfile.curbExtent(l,at,side):0;
        boolean sound=raised&&o.outerRail().sound(side);
        double offset=sound?at.halfWidth()+.5:at.halfWidth()-(curb>0?curb/2:.28);
        addLamp(out,mesh,ground,at,side*offset,curb>0?.2:0,3,side,sound);continue;
      }
      boolean center=raised?o.cycle():l.catalog().median()==RoadProfile.Median.GREEN&&l.median()>=1.4;
      if(center)addLamp(out,mesh,ground,at,l.medianCenter(),raised?0:.3,1,1,false);
      for(int side:l.outsideSides()){
        boolean paved=walkSide(walk,side),cycle=o.cycle()&&l.cycleWidth()>1;
        if(raised){
          int style=cycle?(paved?2:4):(paved?1:4);
          addLamp(out,mesh,ground,at,side*(at.halfWidth()+(paved?.65:.45)),paved?.2:0,style,side,!paved);continue;
        }
        if(cycle){
          if(c.separator()==Separator.GREEN)addLamp(out,mesh,ground,at,l.outer(side)+side*.75,.3,2,side,false);
          else if(c.separator()==Separator.RAIL)addLamp(out,mesh,ground,at,l.outer(side),0,c.walkLamps()&&paved&&walkLampAllowed(o,mesh.settings().structure(),mesh.settings().style())?3:2,side,false);
          else addLamp(out,mesh,ground,at,side*(at.halfWidth()+(paved?.65:.55)),paved?.2:0,1,side,false);
        }else if(!center)addLamp(out,mesh,ground,at,side*(at.halfWidth()+(paved?.65:.55)),paved?.2:0,paved?1:3,side,false);
      }
    }
    if(c.walkLamps()&&walkLampAllowed(o,mesh.settings().structure(),mesh.settings().style()))for(double d=phase.first(8,c.walkLampSpacing());d<mesh.length()-1;d+=c.walkLampSpacing()){
      if(d<1||RoadInfrastructure.nearGantry(mesh,ground,d))continue;
      var at=RoadStructures.sample(mesh,d);if(bridge(mesh,at,ground))continue;
      for(int side:new int[]{-1,1})if(walkSide(walk,side)){
        // The inner row is reserved for trees/main lamps. The separate path row stays inside tactile paving.
        double offset=Math.max(1.7,walk.width()/2.0);
        addLamp(out,mesh,ground,at,side*(at.halfWidth()+offset),.2,1,side,false);
      }
    }
    if(c.planting()!=Planting.NONE)for(double d=phase.first(8,c.plantingSpacing());d<mesh.length()-3;d+=c.plantingSpacing()){
      if(d<3||RoadInfrastructure.nearGantry(mesh,ground,d))continue;
      var at=RoadStructures.sample(mesh,d);if(bridge(mesh,at,ground))continue;
      for(int side:new int[]{-1,1})if(walkSide(walk,side)){
        double offset=c.planting()==Planting.FLOWERS?1.1:1.8;
        V foot=at.at(side*(at.halfWidth()+offset),-.2);
        if(ground.joined(foot)||ground.furnitureClear(foot))continue;
        var plants=plant(foot,c.planting(),at.left().left().mul(-1));
        // A main lamp wins when both schedules land in the same inner planting row.
        if(out.stream().anyMatch(p->p.a().y()<foot.y()+2&&p.a().sub(foot).horizontalLength()<2.3))continue;
        if(plants.stream().anyMatch(ground::blocked))continue;
        out.addAll(plants);
      }
    }
    return List.copyOf(out);
  }
  private static void addLamp(List<Part> out,Mesh mesh,Ground g,Sample at,double lateral,double up,int style,int side,boolean bracket){
    // An outer closure removes the supporting shoulder too. Keep median lamps,
    // but do not leave an exterior lamp/bracket suspended beside that opening.
    if(LaneDeck.outerOpening(mesh,at.distance(),side)&&Math.abs(lateral)>at.halfWidth()-1)return;
    V foot=at.at(lateral,-up);if(g.joined(foot)||g.furnitureClear(foot))return;
    var parts=new ArrayList<>(lamp(foot,at.left().mul(-side),style));
    if(bracket){
      // Recess the cross-deck support below the paint; only the outboard riser reaches the pedestal.
      parts.add(new Part(at.at(side*(at.halfWidth()-.55),.47),foot.add(new V(0,-.47,0)),.7,.35,false,Material.CONCRETE));
      parts.add(new Part(foot.add(new V(0,-.12,0)),foot.add(new V(0,-.12,0)),.38,.12,true,Material.CONCRETE));
    }
    if(parts.stream().noneMatch(g::blocked))out.addAll(parts);
  }
  public static List<Part> lamp(V foot,V inward,int style){
    var out=new ArrayList<Part>();double h=style==4?6:7.5;
    out.add(new Part(foot,foot,.38,.16,true,Material.CONCRETE));
    out.add(new Part(foot.add(new V(0,.16,0)),foot.add(new V(0,.16,0)),.22,1.8,true,Material.LAMP_BLUE));
    out.add(new Part(foot.add(new V(0,1.96,0)),foot.add(new V(0,1.96,0)),.14,h-1.96,true,Material.STEEL));
    arm(out,foot,inward,h,style==4?1.45:2.5,style==4?.15:.65);
    if(style==1||style==2)arm(out,foot,inward.mul(-1),style==2?h-1.7:h,style==2?1.7:2.5,.65);
    if(style==1)out.add(new Part(foot.add(new V(0,h,0)),foot.add(new V(0,h,0)),.09,.65,true,Material.STEEL));
    if(style==3){V a=foot.add(new V(0,h-1.4,0)),b=foot.add(new V(0,h-.2,0));V last=a;
      for(int i=1;i<=8;i++){double t=i/8.;V next=a.add(b.sub(a).mul(t)).add(inward.mul(-.42*Math.sin(Math.PI*t)));out.add(new Part(last,next,.07,.07,false,Material.STEEL));last=next;}}
    return List.copyOf(out);
  }
  private static void arm(List<Part> out,V foot,V direction,double h,double length,double rise){
    V root=foot.add(new V(0,h-.2,0)),last=root;
    for(int i=1;i<=8;i++){double t=i/8.;V p=root.add(direction.mul(length*t)).add(new V(0,rise*(2*t-t*t),0));out.add(new Part(last,p,.10,.10,false,Material.STEEL));last=p;}
    out.add(new Part(last.sub(direction.mul(.3)),last.add(direction.mul(.55)),.36,.16,false,Material.LAMP_BLUE));
    out.add(new Part(last.sub(direction.mul(.24)).add(new V(0,-.035,0)),last.add(direction.mul(.45)).add(new V(0,-.035,0)),.28,.05,false,Material.LAMP));
  }
  /** Bounded procedural models: tree pit and grate, branching trunks and species crowns. */
  public static List<Part> plant(V foot,Planting type,V along){
    var out=new ArrayList<Part>();if(type==Planting.NONE)return List.of();
    along=along.horizontalUnit();V side=along.left();
    if(type==Planting.FLOWERS){
      // Recessed soil surrounded by a real continuous rim, with planted clusters and petals.
      box(out,foot.add(new V(0,.025,0)),along,3.5,1.65,.16,Material.SOIL);
      for(int sign:new int[]{-1,1}){
        box(out,foot.add(side.mul(sign*.84)),along,3.8,.16,.32,Material.CONCRETE);
        box(out,foot.add(along.mul(sign*1.84)),side,1.65,.16,.32,Material.CONCRETE);
      }
      for(int row=-1;row<=1;row++)for(int i=-3;i<=3;i++){
        double h=.3+.05*Math.sin(i*2.3+row);V at=foot.add(along.mul(i*.48+row*.05)).add(side.mul(row*.45)).add(new V(0,.16,0));
        box(out,at,along,.43,.36,h,Material.OAK_LEAVES);
        V bloom=at.add(new V(0,h+.02,0));Material petal=((i-row)%3==0)?Material.FLOWER_WHITE:((i+row)%2==0)?Material.FLOWER_PINK:Material.FLOWER_YELLOW;
        box(out,bloom,along,.28,.11,.055,petal);box(out,bloom,side,.28,.11,.055,petal);
        box(out,bloom.add(new V(0,.045,0)),along,.07,.07,.04,Material.FLOWER_YELLOW);
      }return List.copyOf(out);
    }
    // Soil sits just above the paving and the inset grate leaves room for trunk growth.
    box(out,foot.add(new V(0,.008,0)),along,2.25,2.25,.035,Material.SOIL);
    for(int sign:new int[]{-1,1}){
      box(out,foot.add(side.mul(sign*1.16)),along,2.48,.16,.12,Material.CONCRETE);
      box(out,foot.add(along.mul(sign*1.16)),side,2.2,.16,.12,Material.CONCRETE);
    }
    for(int i=-5;i<=5;i++){
      double offset=i*.19;double gap=Math.abs(offset)<.37?.42:0;
      for(int sign:new int[]{-1,1}){double length=1.08-gap;if(length<=0)continue;
        V a=foot.add(side.mul(offset)).add(along.mul(sign*(gap+length/2))).add(new V(0,.055,0));
        box(out,a,along,length,.035,.035,Material.DARK_STEEL);
      }
    }
    Material trunk=switch(type){case BIRCH->Material.BIRCH_LOG;case SPRUCE->Material.SPRUCE_LOG;case CHERRY->Material.CHERRY_LOG;default->Material.OAK_LOG;};
    Material leaves=switch(type){case BIRCH->Material.BIRCH_LEAVES;case SPRUCE->Material.SPRUCE_LEAVES;case CHERRY->Material.CHERRY_LEAVES;default->Material.OAK_LEAVES;};
    double height=type==Planting.SPRUCE?6.1:type==Planting.BIRCH?5.7:type==Planting.CHERRY?4.6:5.1;
    out.add(new Part(foot,foot,type==Planting.BIRCH?.34:.46,height-.7,true,trunk));
    int tiers=type==Planting.SPRUCE?6:4;
    for(int level=0;level<tiers;level++){
      double y=type==Planting.SPRUCE?2.25+level*.72:2.7+level*.65;
      double radius=type==Planting.SPRUCE?1.45-level*.21:type==Planting.BIRCH?new double[]{.82,1.1,.9,.55}[level]:type==Planting.CHERRY?new double[]{1.35,1.65,1.4,.85}[level]:new double[]{1.05,1.4,1.2,.65}[level];
      V center=foot.add(new V(0,y,0));
      if(level<3)for(int k=0;k<4;k++){
        double angle=k*Math.PI/2+level*.65;V direction=along.mul(Math.cos(angle)).add(side.mul(Math.sin(angle)));
        V end=center.add(direction.mul(radius*.77)).add(new V(0,.27,0));
        out.add(new Part(center.add(new V(0,-.55,0)),end,.16,.18,false,trunk));
      }
      // Offset lobes produce an irregular crown rather than stacked flat boxes.
      box(out,center,along,radius*1.18,radius*1.15,.68,leaves);
      for(int k=0;k<5;k++){
        double angle=k*2*Math.PI/5+level*.71;V delta=along.mul(Math.cos(angle)*radius*.58).add(side.mul(Math.sin(angle)*radius*.58));
        V pos=center.add(delta).add(new V(0,.025+.017*k+.12*Math.sin(k*1.7+level),0));
        box(out,pos,along,radius*.98,radius*.9,.65,leaves);
      }
    }
    return List.copyOf(out);
  }
  private static void box(List<Part> out,V center,V along,double length,double width,double height,Material material){
    out.add(new Part(center.sub(along.mul(length/2)),center.add(along.mul(length/2)),width,height,false,material));
  }
  private RoadStreetscape(){}
}
