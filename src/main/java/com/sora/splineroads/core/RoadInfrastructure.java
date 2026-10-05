package com.sora.splineroads.core;

import com.sora.splineroads.core.RoadGeometry.*;
import com.sora.splineroads.core.RoadStructures.*;
import java.util.*;

/** Bridge superstructures, enclosed tunnels and direction-aware overhead equipment. */
public final class RoadInfrastructure {
  public enum Bridge {
    STANDARD("普通高架",24), BEAM("小河梁桥",32), OVERPASS("跨线桥",64),
    ARCH("大河拱桥",96), CABLE("斜拉桥",192), SUSPENSION("悬索桥",256), VIADUCT("跨海连续梁",64);
    public final String label; public final double span;
    Bridge(String label,double span){this.label=label;this.span=span;}
  }
  public enum Tunnel {
    BOX("箱形地下通道"), ARCH("拱顶隧道");
    public final String label; Tunnel(String label){this.label=label;}
  }
  public enum Gantry {
    AUTO("自动龙门架"), OFF("关闭"), FRAME("设备横梁"), SIGNS("普通龙门架");
    public final String label; Gantry(String label){this.label=label;}
  }
  public record Config(Bridge bridge,double span,Tunnel tunnel,double headroom,Gantry gantry,double spacing,
      double tunnelDepth,List<RoadGantry.Edit> gantryEdits,RoadTunnelFit.Adjustment adjustment,boolean efficientDip,double bridgeRise,double maxGrade,boolean autoSpan,List<RoadSigns.Attachment> signs) {
    public Config(Bridge bridge,double span,Tunnel tunnel,double headroom,Gantry gantry,double spacing,double depth,List<RoadGantry.Edit> edits,RoadTunnelFit.Adjustment adjustment,boolean efficientDip,double rise,double maxGrade,boolean autoSpan){this(bridge,span,tunnel,headroom,gantry,spacing,depth,edits,adjustment,efficientDip,rise,maxGrade,autoSpan,List.of());}
    public static final Config DEFAULT=new Config(Bridge.STANDARD,24,Tunnel.BOX,6,Gantry.AUTO,128,0,List.of(),RoadTunnelFit.Adjustment.OFF,true,0,.06,true);
    public Config(Bridge bridge,double span,Tunnel tunnel,double headroom,Gantry gantry,double spacing){
      this(bridge,span,tunnel,headroom,gantry,spacing,0,List.of());
    }
    public Config(Bridge bridge,double span,Tunnel tunnel,double headroom,Gantry gantry,double spacing,double depth,List<RoadGantry.Edit> edits){
      this(bridge,span,tunnel,headroom,gantry,spacing,depth,edits,RoadTunnelFit.Adjustment.OFF,true);
    }
    /** Legacy constructor retains the old geometry and grading policy. */
    public Config(Bridge bridge,double span,Tunnel tunnel,double headroom,Gantry gantry,double spacing,
        double depth,List<RoadGantry.Edit> edits,RoadTunnelFit.Adjustment adjustment,boolean efficientDip){
      this(bridge,span,tunnel,headroom,gantry,spacing,depth,edits,adjustment,efficientDip,0,0,false);
    }
    public Config {
      if(bridge==null||tunnel==null||gantry==null||adjustment==null||!RoadGeometry.finite(span,headroom,spacing,tunnelDepth,bridgeRise,maxGrade)
          ||span<12||span>2048||headroom<5||headroom>12||spacing<32||spacing>512||tunnelDepth<0||tunnelDepth>96||bridgeRise<0||bridgeRise>96||maxGrade<0||maxGrade>.2)
        throw new IllegalArgumentException("桥跨 12–2048；净高 5–12；间距 32–512；下潜 / 抬升 0–96 格；坡度不超过 20%");
      signs=List.copyOf(signs);if(signs.size()>128||signs.stream().map(RoadSigns.Attachment::id).distinct().count()!=signs.size())throw new IllegalArgumentException("路牌记录过多或重复");
      gantryEdits=List.copyOf(gantryEdits);
      if(gantryEdits.size()>256||gantryEdits.stream().map(RoadGantry.Edit::slot).distinct().count()!=gantryEdits.size())
        throw new IllegalArgumentException("龙门架编辑记录重复或过多");
    }
    public Config bridge(Bridge v){return new Config(v,v.span,tunnel,headroom,gantry,spacing,tunnelDepth,gantryEdits,adjustment,efficientDip,bridgeRise,maxGrade,autoSpan,signs);}
    public Config span(double v){return new Config(bridge,v,tunnel,headroom,gantry,spacing,tunnelDepth,gantryEdits,adjustment,efficientDip,bridgeRise,maxGrade,autoSpan,signs);}
    public Config tunnel(Tunnel v){return new Config(bridge,span,v,headroom,gantry,spacing,tunnelDepth,gantryEdits,adjustment,efficientDip,bridgeRise,maxGrade,autoSpan,signs);}
    public Config headroom(double v){return new Config(bridge,span,tunnel,v,gantry,spacing,tunnelDepth,gantryEdits,adjustment,efficientDip,bridgeRise,maxGrade,autoSpan,signs);}
    public Config gantry(Gantry v){return new Config(bridge,span,tunnel,headroom,v,spacing,tunnelDepth,gantryEdits,adjustment,efficientDip,bridgeRise,maxGrade,autoSpan,signs);}
    public Config spacing(double v){return new Config(bridge,span,tunnel,headroom,gantry,v,tunnelDepth,gantryEdits,adjustment,efficientDip,bridgeRise,maxGrade,autoSpan,signs);}
    public Config depth(double v){return new Config(bridge,span,tunnel,headroom,gantry,spacing,v,gantryEdits,adjustment,true,bridgeRise,maxGrade,autoSpan,signs);}
    public Config adjustment(RoadTunnelFit.Adjustment v){return new Config(bridge,span,tunnel,headroom,gantry,spacing,tunnelDepth,gantryEdits,v,efficientDip,bridgeRise,maxGrade,autoSpan,signs);}
    public Config profile(boolean v){return new Config(bridge,span,tunnel,headroom,gantry,spacing,tunnelDepth,gantryEdits,adjustment,v,bridgeRise,maxGrade,autoSpan,signs);}
    public Config clearEdits(){return new Config(bridge,span,tunnel,headroom,gantry,spacing,tunnelDepth,List.of(),adjustment,efficientDip,bridgeRise,maxGrade,autoSpan,List.of());}
    public Config edit(RoadGantry.Edit value){
      var edits=new ArrayList<>(gantryEdits);edits.removeIf(e->e.slot()==value.slot());edits.add(value);
      edits.sort(Comparator.comparingInt(RoadGantry.Edit::slot));
      return new Config(bridge,span,tunnel,headroom,gantry,spacing,tunnelDepth,edits,adjustment,efficientDip,bridgeRise,maxGrade,autoSpan,signs);
    }
    public Config reset(int slot){return new Config(bridge,span,tunnel,headroom,gantry,spacing,tunnelDepth,gantryEdits.stream().filter(e->e.slot()!=slot).toList(),adjustment,efficientDip,bridgeRise,maxGrade,autoSpan,signs);}
    public Config rise(double v){return new Config(bridge,span,tunnel,headroom,gantry,spacing,tunnelDepth,gantryEdits,adjustment,efficientDip,v,maxGrade,autoSpan,signs);}
    public Config grade(double v){return new Config(bridge,span,tunnel,headroom,gantry,spacing,tunnelDepth,gantryEdits,adjustment,efficientDip,bridgeRise,v,autoSpan,signs);}
    public Config autoSpan(boolean v){return new Config(bridge,span,tunnel,headroom,gantry,spacing,tunnelDepth,gantryEdits,adjustment,efficientDip,bridgeRise,maxGrade,v,signs);}
    public Config signs(List<RoadSigns.Attachment> v){return new Config(bridge,span,tunnel,headroom,gantry,spacing,tunnelDepth,gantryEdits,adjustment,efficientDip,bridgeRise,maxGrade,autoSpan,v);}

  }
  public static boolean customBridge(Mesh mesh){return mesh.settings().structure()==Structure.BRIDGE&&mesh.settings().options().infrastructure().bridge()!=Bridge.STANDARD;}
  public static double tunnelRise(Config c,double halfWidth){return c.tunnel()==Tunnel.ARCH?Math.min(4,halfWidth*.38):0;}
  /** Highest interior ceiling; used by excavation, including old saved clearance=4 records. */
  public static double clearance(Settings s){var c=s.options().infrastructure();return s.structure()==Structure.TUNNEL?c.headroom()+tunnelRise(c,s.width()/2)+1.25:0;}
  public static List<Part> plan(Mesh mesh,Ground ground) {
    var out=new ArrayList<Part>(); var c=mesh.settings().options().infrastructure();
    if(mesh.settings().structure()==Structure.TUNNEL){tunnel(mesh,c,out);RoadStructures.medianFurniture(mesh,ground,out);return checked(out);}
    if(customBridge(mesh)){bridge(mesh,c,ground,out);validateOwnClearance(mesh,out);
      if(out.stream().anyMatch(ground::blocked))throw new IllegalArgumentException("桥梁结构侵入其他道路净空；请抬高桥面或调整桥位");
    }
    out.addAll(RoadGantry.plan(mesh,ground));
    // Standalone signs and poles were retired in 0.32.0.
    return checked(out);
  }
  private static List<Part> checked(List<Part> parts){
    if(parts.size()>48000)throw new IllegalArgumentException("本段结构过多，请用中间端点拆分桥梁或隧道");
    return List.copyOf(parts);
  }
  private static V at(Sample s,double lateral,double up){return s.at(lateral,-up);}
  private static Sample sample(Mesh m,double d){return RoadStructures.sample(m,Math.max(0,Math.min(m.length(),d)));}
  private static void beam(List<Part> out,V a,V b,double width,double height,Material mat){
    if(a.sub(b).horizontalLength()<1e-6){post(out,a,width,Math.max(height,b.y()-a.y()+height),mat);return;}
    out.add(new Part(a,b,width,height,false,mat));
  }
  private static void post(List<Part> out,V a,double width,double height,Material mat){
    for(double offset=0;offset<height-.001;offset+=RoadStructures.MAX_DROP){
      V base=a.add(new V(0,offset,0));out.add(new Part(base,base,width,Math.min(RoadStructures.MAX_DROP,height-offset),true,mat));
    }
  }
  private static void longitudinal(List<Part> out,Sample a,Sample b,double offsetA,double offsetB,double y,double width,double height,Material mat){
    out.add(new Part(at(a,offsetA,y),at(b,offsetB,y),width,height,false,mat).frames(a.left().mul(width/2),b.left().mul(width/2)));
  }
  private static void tunnel(Mesh mesh,Config c,List<Part> out){
    // Road edge is the inside face of the lining. No unexcavated gap behind the shoulder.
    for(double d=0;d<mesh.length()-1e-6;d+=2){
      Sample a=sample(mesh,d),b=sample(mesh,d+2);
      for(int side:new int[]{-1,1}){
        longitudinal(out,a,b,side*(a.halfWidth()+.75),side*(b.halfWidth()+.75),-mesh.settings().thickness(),1.6,
            Math.max(wallTop(c,a.halfWidth()),wallTop(c,b.halfWidth()))+mesh.settings().thickness(),Material.TUNNEL);
        longitudinal(out,a,b,side*(a.halfWidth()-.05),side*(b.halfWidth()-.05),.8,.12,.18,Material.SIGN_WHITE);
      }
      int bands=roofBands(a.halfWidth(),b.halfWidth());
      for(int i=0;i<bands;i++){
        double u=-1+2.0*i/bands,v=-1+2.0*(i+1)/bands;
        double wa=a.halfWidth()+1.55,wb=b.halfWidth()+1.55;
        double ya=roof(c,a.halfWidth(),u),za=roof(c,a.halfWidth(),v);
        double yb=roof(c,b.halfWidth(),u),zb=roof(c,b.halfWidth(),v);
        out.add(new Part(at(a,wa*(u+v)/2,(ya+za)/2),at(b,wb*(u+v)/2,(yb+zb)/2),
            Math.max(wa,wb)*(v-u),.85,false,Material.TUNNEL)
            .frames(a.left().mul(wa*(v-u)/2).add(new V(0,(za-ya)/2,0)),
                    b.left().mul(wb*(v-u)/2).add(new V(0,(zb-yb)/2,0))));
      }
      // Ceiling lights follow the actual faceted lining, including the arched roof.
      int rows=Math.max(1,(int)Math.ceil(mesh.settings().width()/4));
      for(int row=0;row<rows;row++){
        double u=-1+(row+.5)*2/rows,oa=a.halfWidth()*u,ob=b.halfWidth()*u;
        double ya=ceiling(c,a.halfWidth(),oa),yb=ceiling(c,b.halfWidth(),ob);
        double tiltA=(ceiling(c,a.halfWidth(),oa+.11)-ceiling(c,a.halfWidth(),oa-.11))/2;
        double tiltB=(ceiling(c,b.halfWidth(),ob+.11)-ceiling(c,b.halfWidth(),ob-.11))/2;
        out.add(new Part(at(a,oa,ya-.22),at(b,ob,yb-.22),.22,.24,false,Material.LAMP)
            .frames(a.left().mul(.11).add(new V(0,tiltA,0)),b.left().mul(.11).add(new V(0,tiltB,0))));
      }
    }
    // End collars surround the opening; never put a solid cap across the travel lanes.
    for(double d:new double[]{.6,Math.max(.6,mesh.length()-.6)}){
      Sample s=sample(mesh,d);double w=s.halfWidth()+.65;
      for(int side:new int[]{-1,1})post(out,at(s,side*w,-mesh.settings().thickness()),1.3,wallTop(c,s.halfWidth())+mesh.settings().thickness(),Material.TUNNEL);
      int bands=16;
      for(int i=0;i<bands;i++){
        double u=-1+2.0*i/bands,v=-1+2.0*(i+1)/bands;
        beam(out,at(s,w*u,roof(c,s.halfWidth(),u)),at(s,w*v,roof(c,s.halfWidth(),v)),1.3,1.1,Material.TUNNEL);
      }
    }
    for(double d=3;d<mesh.length()-1;d+=8){
      Sample a=sample(mesh,d),b=sample(mesh,d+1.2);
      for(int side:new int[]{-1,1})longitudinal(out,a,b,side*(a.halfWidth()-.2),side*(b.halfWidth()-.2),c.headroom()-.5,.3,.25,Material.LAMP);
    }
  }
  /** The arch spans beyond the pavement, so its height at the INNER wall is above
   * headroom. Stop at that real roof band, not at the outer spring line. */
  private static double wallTop(Config c,double half){return ceiling(c,half,Math.max(0,half-.05))+.03;}

  /** Maximum of the actual faceted roof above this cell's interior footprint.
   * Shell volume is handled separately; no centre-height padding or extrapolation
   * outside a portal may turn exterior terrain into a reserved air cell. */
  public static double excavationTop(Mesh mesh,int x,int z,double deckTop){
    double top=RoadTunnelSpace.ceiling(mesh,x,z);
    // A rounded raster boundary without actual interior needs no air reservation.
    return Double.isFinite(top)?top:deckTop;
  }
  static int roofBands(double halfA,double halfB){return Math.max(16,(int)Math.ceil(Math.max(halfA,halfB)/3));}
  static double roofHeight(Config c,double half,double u){return roof(c,half,u);}

  /** Height of the actual planar roof band at a lateral position. */
  public static double ceiling(Config c,double half,double lateral){
    int bands=Math.max(16,(int)Math.ceil(half/3));
    double t=Math.max(0,Math.min(bands-1e-9,(lateral/(half+1.55)+1)*bands/2));
    int i=(int)t;double u=-1+2.0*i/bands,v=-1+2.0*(i+1)/bands;
    return roof(c,half,u)*(1-(t-i))+roof(c,half,v)*(t-i);
  }
  private static double roof(Config c,double half,double u){return c.headroom()+tunnelRise(c,half)*Math.sqrt(Math.max(0,1-u*u));}
  public static double endTaper(Mesh mesh,Sample s){
    double t=Math.max(0,Math.min(1,Math.min(s.distance(),mesh.length()-s.distance())/Math.min(12,mesh.length()*.12)));
    return t*t*(3-2*t);
  }
  private static void bridge(Mesh mesh,Config c,Ground ground,List<Part> out){
    double depth=c.bridge()==Bridge.BEAM?.7:c.bridge()==Bridge.OVERPASS?.9:1.2;
    // Two longitudinal box girders remain below the driving slab, following curve and grade.
    for(double d=0;d<mesh.length()-1e-6;d+=2){
      Sample a=sample(mesh,d),b=sample(mesh,d+2);
      for(int side:new int[]{-1,1}){
        longitudinal(out,a,b,side*a.halfWidth()*.62,side*b.halfWidth()*.62,
            -mesh.settings().thickness()-depth,Math.min(2.4,mesh.settings().width()/5),depth,Material.CONCRETE);
        // A continuous edge slab attaches outboard ribs, hangers and cable anchors to the deck.
        double reach=c.bridge()==Bridge.CABLE||c.bridge()==Bridge.SUSPENSION?2.1:c.bridge()==Bridge.ARCH?1.25:.35;
        double ra=reach*endTaper(mesh,a),rb=reach*endTaper(mesh,b);
        out.add(new Part(at(a,side*(a.halfWidth()+(ra-.05)/2),-mesh.settings().thickness()),
            at(b,side*(b.halfWidth()+(rb-.05)/2),-mesh.settings().thickness()),Math.max(ra,rb)+.05,
            mesh.settings().thickness(),false,Material.CONCRETE)
            .frames(a.left().mul((ra+.05)/2),b.left().mul((rb+.05)/2)));
      }
    }
    int spans=c.autoSpan()&&(c.bridge()==Bridge.CABLE||c.bridge()==Bridge.SUSPENSION)?1:Math.max(1,(int)Math.ceil(mesh.length()/c.span()-1e-3));double span=mesh.length()/spans;
    for(int i=0;i<=spans;i++)support(mesh,Math.min(mesh.length()-1,Math.max(1,i*span)),depth,c.bridge(),ground,out);
    for(int i=0;i<spans;i++){
      double start=i*span;
      if(c.bridge()==Bridge.ARCH)arch(mesh,start,span,out);
      if(c.bridge()==Bridge.CABLE||c.bridge()==Bridge.SUSPENSION)cables(mesh,start,span,depth,c.bridge(),ground,out);
    }
  }
  private static void support(Mesh mesh,double station,double depth,Bridge style,Ground ground,List<Part> out){
    boolean portal=style==Bridge.OVERPASS;
    for(double shift:new double[]{0,-4,4,-8,8,-12,12}){
      if(station+shift<1||station+shift>mesh.length()-1)continue;
      Sample s=sample(mesh,station+shift);var parts=new ArrayList<Part>();
      double offset=portal?s.halfWidth()+1.4:s.halfWidth()*.62;
      double top=s.center().y()-mesh.settings().thickness()-depth;
      boolean valid=true;
      for(int side:new int[]{-1,1}){
        V foot=at(s,side*offset,0);
        if(!RoadSupports.shaft(parts,foot,top,portal?1.8:2.2,ground)){valid=false;break;}
      }
      if(!valid||parts.stream().anyMatch(ground::blocked))continue;
      if(!parts.isEmpty())beam(parts,at(s,-offset,-mesh.settings().thickness()-depth),at(s,offset,-mesh.settings().thickness()-depth),2.4,depth,Material.CONCRETE);
      if(parts.stream().anyMatch(ground::blocked))continue;
      out.addAll(parts);return;
    }
    if(mesh.settings().style().ramp())return;
    Sample portalSample=sample(mesh,station);
    var relocated=RoadSupports.portal(portalSample,mesh.settings().thickness(),depth,
        portal?portalSample.halfWidth()+1.4:portalSample.halfWidth()*.62,portal?1.8:2.2,ground);
    if(!relocated.isEmpty()){out.addAll(relocated);return;}
    // A support may land on the approach terrain, but not silently vanish over deep water.
    Sample s=sample(mesh,station);double floor=ground.top(s.center().x(),s.center().z(),s.center().y());
    if(!Double.isFinite(floor)||s.center().y()-floor>mesh.settings().thickness()+depth+1)
      throw new IllegalArgumentException("桥墩无可用基础或与下方道路冲突；请调整桥跨、桥位或桥面高度");
  }
  private static void arch(Mesh mesh,double start,double length,List<Part> out){
    // Leave the bridge-to-approach seam clear. Anchor on the widened slab inside
    // the bridge, never inside the next road's endpoint/collision cell.
    double trim=Math.min(6,length*.12),trimA=start<1e-6?trim:0,trimB=start+length>mesh.length()-1e-6?trim:0;
    start+=trimA;length-=trimA+trimB;
    double rise=Math.max(7,Math.min(28,length*.22));int steps=Math.max(12,(int)Math.ceil(length/3));
    for(int side:new int[]{-1,1}){
      V previous=null;
      for(int i=0;i<=steps;i++){
        double t=(double)i/steps;Sample s=sample(mesh,start+length*t);
        double y=1+rise*4*t*(1-t);V p=at(s,side*(s.halfWidth()+.8*endTaper(mesh,s)),y);
        if(previous!=null)beam(out,previous,p,.8,.8,Material.ARCH_STEEL);
        if(i==0||i==steps)post(out,at(s,side*(s.halfWidth()+.8*endTaper(mesh,s)),-mesh.settings().thickness()),.8,y+mesh.settings().thickness(),Material.CONCRETE);
        if(i>0&&i<steps&&i%2==0)post(out,at(s,side*(s.halfWidth()+.8*endTaper(mesh,s)),-.4),.18,y+.4,Material.STEEL);
        previous=p;
      }
    }
    for(double t:new double[]{.3,.5,.7}){
      Sample s=sample(mesh,start+length*t);double y=1+rise*4*t*(1-t);
      beam(out,at(s,-s.halfWidth()-.8,y),at(s,s.halfWidth()+.8,y),.45,.5,Material.ARCH_STEEL);
    }
  }
  public static double towerHeight(double span,Bridge style){return Math.max(12,span*(style==Bridge.CABLE?.22:.18));}
  private static void cables(Mesh mesh,double start,double length,double depth,Bridge style,Ground ground,List<Part> out){
    // Leave the bridge-to-approach seam clear. Anchor on the widened slab inside
    // the bridge, never inside the next road's endpoint/collision cell.
    double trim=Math.min(6,length*.12),trimA=start<1e-6?trim:0,trimB=start+length>mesh.length()-1e-6?trim:0;
    start+=trimA;length-=trimA+trimB;
    double tower=towerHeight(length,style);
    for(double fraction:new double[]{.2,.8}){
      Sample s=sample(mesh,start+length*fraction);
      support(mesh,start+length*fraction,depth,Bridge.VIADUCT,ground,out);
      for(int side:new int[]{-1,1})post(out,at(s,side*(s.halfWidth()+1.3),-mesh.settings().thickness()),1.5,tower+mesh.settings().thickness(),Material.CONCRETE);
      beam(out,at(s,-s.halfWidth()-1.3,tower*.8),at(s,s.halfWidth()+1.3,tower*.8),1.1,1.1,Material.CONCRETE);
      // Transverse foundation beam ties the outside towers to the paired piers.
      beam(out,at(s,-s.halfWidth()-1.3,-mesh.settings().thickness()-depth),at(s,s.halfWidth()+1.3,-mesh.settings().thickness()-depth),2.4,depth,Material.CONCRETE);
      if(style==Bridge.CABLE)for(int side:new int[]{-1,1})for(int j=0;j<=10;j++){
        double t=(fraction<.5?0:.5)+j*.05;Sample anchor=sample(mesh,start+length*t);
        if(Math.abs(t-fraction)<.03)continue;
        double offset=side*(anchor.halfWidth()+1.3*endTaper(mesh,anchor));
        post(out,at(anchor,offset,-.15),.45,.5,Material.DARK_STEEL);
        beam(out,at(anchor,offset,.12),at(s,side*(s.halfWidth()+1.3),tower-.8),.14,.16,Material.STEEL);
      }
    }
    if(style==Bridge.SUSPENSION){
      int steps=Math.max(24,(int)Math.ceil(length/3));
      for(int side:new int[]{-1,1}){
        V prev=null;
        for(int i=0;i<=steps;i++){
          double t=(double)i/steps;Sample s=sample(mesh,start+length*t);
          double h=t<.2?1+(tower-1)*t/.2:t>.8?1+(tower-1)*(1-t)/.2:tower*.35+tower*.65*Math.pow((t-.5)/.3,2);
          V p=at(s,side*(s.halfWidth()+1.3*endTaper(mesh,s)),h);
          if(prev!=null)beam(out,prev,p,.32,.35,Material.DARK_STEEL);
          if(i==0||i==steps)post(out,at(s,side*(s.halfWidth()+1.3*endTaper(mesh,s)),-mesh.settings().thickness()),.6,h+mesh.settings().thickness(),Material.CONCRETE);
          if(i>0&&i<steps&&i%2==0)post(out,at(s,side*(s.halfWidth()+1.3*endTaper(mesh,s)),.1),.12,h-.1,Material.STEEL);
          prev=p;
        }
      }
    }
  }
  private static void validateOwnClearance(Mesh mesh,List<Part> parts){
    for(var part:parts){
      int steps=Math.max(1,(int)Math.ceil(part.b().sub(part.a()).horizontalLength()/2));
      for(int k=0;k<=steps;k++){
        V p=part.a().add(part.b().sub(part.a()).mul((double)k/steps));var q=RoadQueries.horizontal(mesh,p);var s=q.sample();
        if(Math.abs(q.lateral())<s.halfWidth()-.5+part.width()/2
            &&p.y()+part.height()>s.center().y()+.5&&p.y()<s.center().y()+5)
          throw new IllegalArgumentException("桥拱或拉索侵入本桥行车净空；请缩短桥跨、减小弯曲或改用梁桥");
      }
    }
  }
  public static boolean nearGantry(Mesh mesh,Ground ground,double distance){return RoadGantry.near(mesh,ground,distance);}
  private RoadInfrastructure(){}
}
