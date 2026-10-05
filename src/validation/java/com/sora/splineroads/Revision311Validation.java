package com.sora.splineroads;
import com.sora.splineroads.core.*;
import com.sora.splineroads.core.RoadGeometry.*;
import com.sora.splineroads.core.RoadInfrastructure.*;
import com.sora.splineroads.core.RoadStructures.*;
import java.util.*;
public final class Revision311Validation {
  static int checks;static void check(boolean ok,String reason){checks++;if(!ok)throw new AssertionError(reason);}
  static double projected(RoadSurface.Face f){return Math.abs(JunctionPaint.area(f.points()));}
  static double upwardArea(List<RoadSurface.Face> faces,RoadSurface.Texture material){return faces.stream().filter(f->f.texture()==material&&f.points().size()>2).filter(f->{V a=f.points().get(1).sub(f.points().get(0)),b=f.points().get(2).sub(f.points().get(0));return a.z()*b.x()-a.x()*b.z()>1e-9;}).mapToDouble(Revision311Validation::projected).sum();}
  public static void main(String[] args){
    var ground=Revision28Validation.ground(0);var walk=new RoadSidewalks.Config(true,RoadSidewalks.Side.BOTH,5,"minecraft:stone_bricks");
    var base=Revision28Validation.settings(Style.O4_YELLOW,Structure.BRIDGE,Config.DEFAULT.bridge(Bridge.CABLE).gantry(Gantry.FRAME));base=base.options(base.options().sidewalk(walk));
    var mesh=RoadGeometry.build(Revision30Validation.node(.5,20),Revision30Validation.node(240.5,20),base);var parts=RoadStructures.plan(mesh,ground);
    check(parts.stream().anyMatch(p->p.material()==Material.STEEL&&p.height()==.12&&Math.abs(p.a().z()-.5)>mesh.settings().width()/2+4),"outer pedestrian guardrail exists");
    check(parts.stream().filter(p->p.material()==Material.TACTILE&&p.width()==.6).anyMatch(p->Math.abs(p.a().z()-.5)>mesh.settings().width()/2+1.8),"blind path moves outside bridge anchor/gantry foot");
    for(var p:parts)if(p.material()==Material.TACTILE&&p.width()==.6){double d=p.a().sub(p.b()).horizontalLength();if(d>1e-7)check(Math.abs(Math.abs(p.a().z()-.5)-Math.abs(p.b().z()-.5))<=d*.32+.001,"gentle blind-path transitions");}
    for(double angle:new double[]{0,17,45,89}){
      double r=Math.toRadians(angle);V f=new V(Math.cos(r),0,Math.sin(r)),n=f.left();var strips=new ArrayList<Part>();
      for(double d=-150;d<180;d+=.5){V a=f.mul(d).add(new V(-.3,6,-.7)),b=f.mul(d+.5).add(new V(-.3,6,-.7));strips.add(new Part(a,b,5,.5,false,Material.WALK_STONE_BRICKS));strips.add(new Part(a.add(n.mul(6)),b.add(n.mul(6)),.14,.12,false,Material.STEEL));strips.add(new Part(a.add(new V(0,7,0)),b.add(new V(0,7,0)),4,.85,false,Material.TUNNEL));}
      var sections=RoadRenderMesh.sections(new RoadSurface.Geometry(List.of(),List.of()),strips);
      for(var e:sections.entrySet())for(var texture:List.of(RoadSurface.Texture.WALK_STONE_BRICKS,RoadSurface.Texture.CONCRETE,RoadSurface.Texture.METAL)){
        double a=upwardArea(e.getValue().detail(),texture),b=upwardArea(e.getValue().distant(),texture);check(Math.abs(a-b)<1e-5,"same per-section coverage for mixed LOD "+angle+" "+e.getKey()+" "+texture+" "+a+" vs "+b);
        for(var face:e.getValue().distant())for(var v:face.points())check(v.x()>=e.getKey().x()*64-1e-7&&v.x()<=(e.getKey().x()+1)*64+1e-7&&v.z()>=e.getKey().z()*64-1e-7&&v.z()<=(e.getKey().z()+1)*64+1e-7,"face remains in its owner section");
      }
    }
    var cutSign=new Part(new V(62.8,5,64),new V(65.2,5,64),.1,2,false,Material.CB_SIGN,null,null,"sign_expressway_distance_from_location_1");
    var signSections=RoadRenderMesh.sections(new RoadSurface.Geometry(List.of(),List.of()),List.of(cutSign));for(var piece:signSections.values())for(var face:piece.detail())check(face.uv().size()==face.points().size(),"clipped sign UVs retain vertex correspondence");
    for(double angle:new double[]{0,17,45,89}){double r=Math.toRadians(angle);V f=new V(Math.cos(r),0,Math.sin(r));double yaw=Math.toDegrees(Math.atan2(-f.x(),f.z()));var s=base.structure(Structure.GROUND);V mid=f.mul(80).add(new V(.5,2,.5));var first=RoadGeometry.build(new Node(new V(.5,2,.5),yaw,0),new Node(mid,yaw,0),s);var second=RoadGeometry.build(new Node(mid,yaw,0),new Node(mid.add(f.mul(80)),yaw,0),s);for(var part:RoadSidewalks.parts(first,walk))check(!RoadSidewalks.overlapsDeck(part,second),"touching continuation must not erase sidewalk "+angle);}
    var polygon=List.of(new V(1000000,0,1000000),new V(1000004,0,1000000),new V(1000004,0,1000000),new V(1000008,0,1000000),new V(1000008,0,1000005),new V(1000000,0,1000005),new V(1000000,0,1000000));var triangles=JunctionPaint.triangulate(polygon);check(Math.abs(triangles.stream().mapToDouble(v->Math.abs(JunctionPaint.area(v))).sum()-40)<1e-8,"duplicate/collinear translated polygon triangulates exactly");
    for(double angle:new double[]{35,90,150,179.999,180,180.001,210,270,325})for(var style:List.of(Style.O2_YELLOW,Style.O4_GREEN,Style.O6_RAIL)){
      var spec=Junction22Validation.fixture(style,false,0,angle);var plan=JunctionPlanner.plan(spec);check(!plan.pieces().isEmpty(),"junction robust triangulation "+angle+" "+style);check(new JunctionPlanner.Ref(spec,0).geometryVersion()==35,"new topology version isolated");
    }
    for(double turn:new double[]{178,179,181,182}){
      var spec=Junction22Validation.fixture(Style.O4_YELLOW,false,0,90,turn);var arms=new ArrayList<>(spec.arms());var old=arms.get(2);var narrow=Revision28Validation.settings(Style.O2_YELLOW,Structure.GROUND,Config.DEFAULT.gantry(Gantry.OFF));arms.set(2,JunctionSpec.arm(old.endpoint(),old.inward(),narrow,true,2));
      var plan=JunctionPlanner.plan(spec.arms(arms));check(!plan.pieces().isEmpty(),"near straight unequal-width T junction no self crossing "+turn);
      check(plan.boundary().stream().allMatch(v->v.sub(spec.center()).horizontalLength()<100),"near parallel curb intersection stays bounded");
    }
    boolean rejectsCross=false;try{JunctionPaint.triangulate(List.of(new V(0,0,0),new V(6,0,6),new V(0,0,6),new V(6,0,0)));}catch(IllegalArgumentException e){rejectsCross=true;}check(rejectsCross,"genuine self crossing remains rejected");
    var legacySpec=Junction22Validation.fixture(Style.O4_GREEN,false,0,90,180,270);check(new JunctionPlanner.Ref(legacySpec,4,28).get()!=null,"existing junction topology still loads");
    var m=RoadSignCatalog.get("sign_guide_intersection_warning_1");check(m.fields().size()==1,"shared CB renderer selects only this sign's field");check(Math.abs(m.fields().get(0).y()-m.height()/2)<.05,"guide label is vertically centered");
    m=RoadSignCatalog.get("sign_expressway_distance_from_location_1");check(m.fields().size()==9,"distance sign has three names, distances and units");check(m.fields().get(6).scale()==.025,"unit native scale, not generic fallback");
    var attachment=new RoadSigns.Attachment(0,"sign_speed_limit_120",RoadSigns.Mount.GANTRY,0,.5,0,5.6,1,false,List.of());var settings=base.options(base.options().infrastructure(base.options().infrastructure().signs(List.of(attachment))));var gm=RoadGeometry.build(Revision30Validation.node(.5,2),Revision30Validation.node(120.5,2),settings);var installation=RoadSigns.plan(gm,ground);double beam=RoadGantry.lowerBeamY(RoadGantry.station(gm,0));check(installation.stream().anyMatch(p->p.pier()&&p.material()==Material.DARK_STEEL&&p.a().y()<beam&&p.a().y()+p.height()>beam),"hanger reaches actual rounded equipment beam");
    System.out.println("Revision311 PASS "+checks+" checks");
  }
}
