from pathlib import Path
ROOT=Path('.')
def edit(name,changes):
 p=ROOT/name; original=p.read_bytes();s=original.decode('utf8');crlf=b'\r\n' in original;s=s.replace('\r\n','\n')
 for old,new in changes:
  assert s.count(old)==1,(name,old[:90],s.count(old))
  s=s.replace(old,new)
 p.write_bytes(s.replace('\n','\r\n').encode('utf8') if crlf else s.encode('utf8'))
C='src/main/java/com/sora/splineroads/core/'
W='src/main/java/com/sora/splineroads/world/'
edit(C+'LanePoints.java',[
 ('double targetOffset,boolean protectedMerge) {','double targetOffset,boolean protectedMerge,boolean rectangularClosure) {\n    public Link(Ref from,Ref to,Options options,V mouth,double offset,boolean protectedMerge){this(from,to,options,mouth,offset,protectedMerge,false);}'),
 ('public Link withProtectedMerge(){return new Link(from,to,options,junctionMouth,targetOffset,true);}','public Link withProtectedMerge(){return new Link(from,to,options,junctionMouth,targetOffset,true,rectangularClosure);}\n    public Link withRectangularClosure(){return new Link(from,to,options,junctionMouth,targetOffset,protectedMerge,true);}'),
 ('return new Link(from,to,options,junctionMouth,value,protectedMerge);','return new Link(from,to,options,junctionMouth,value,protectedMerge,rectangularClosure);')])
edit(C+'LaneSections.java',[
 ('boolean temporary,boolean arrival) {','boolean temporary,boolean arrival,boolean rectangular) {\n    public Cut(UUID connection,int lane,int sign,double begin,double end,double transition,UUID replacement,boolean temporary,boolean arrival){this(connection,lane,sign,begin,end,transition,replacement,temporary,arrival,false);}'),
 ('if(arrival&&!temporary||connection==null','if((arrival||rectangular)&&!temporary||connection==null'),
 ('double distance=sign*(end-begin);if(distance<1e-6)return 0;','double distance=sign*(end-begin);if(distance<1e-6)return 0;\n      if(rectangular)return sign*(station-begin)>1e-8&&sign*(end-station)>1e-8?1:0;'),
 ('double transition,double returnStation){','double transition,double returnStation,boolean rectangular){\n    public Event(UUID connection,Kind kind,int lane,int sign,double station,double transition,double returnStation){this(connection,kind,lane,sign,station,transition,returnStation,false);}'),
 ('replacement.connection(),event.kind()==Kind.TEMPORARY));','replacement.connection(),event.kind()==Kind.TEMPORARY,false,event.kind()==Kind.TEMPORARY&&event.rectangular()));'),
 ('event.transition(),null,true,true);','event.transition(),null,true,true,event.rectangular());')])
edit(W+'LaneCrossSections.java',[
 ('link.options().transition(),end));','link.options().transition(),end,link.rectangularClosure()));'),
 ('link.options().transition(),begin));','link.options().transition(),begin,link.rectangularClosure()));')])
edit(W+'LaneRamps.java',[
 ('link=link.withProtectedMerge();','link=link.withProtectedMerge().withRectangularClosure();')])
edit(W+'LaneTopology.java',[
 ('l.targetOffset(),l.protectedMerge()))','l.targetOffset(),l.protectedMerge(),l.rectangularClosure()))')])
edit(W+'LanePointCodec.java',[
 ('t.putBoolean("ProtectedMerge",link.protectedMerge());','t.putBoolean("ProtectedMerge",link.protectedMerge());t.putBoolean("RectangularClosure",link.rectangularClosure());'),
 ('t.getDouble("TargetOffset"),t.getBoolean("ProtectedMerge"));','t.getDouble("TargetOffset"),t.getBoolean("ProtectedMerge"),t.getBoolean("RectangularClosure"));'),
 ('q.putBoolean("ArrivalClosure",c.arrival());','q.putBoolean("ArrivalClosure",c.arrival());q.putBoolean("Rectangular",c.rectangular());'),
 ('q.getBoolean("Temporary"),q.getBoolean("ArrivalClosure")));','q.getBoolean("Temporary"),q.getBoolean("ArrivalClosure"),q.getBoolean("Rectangular")));')])
edit(C+'LaneDeck.java',[
 ('cut.removed(cut.arrival()&&Math.abs(sample.distance()-cut.end())<1e-7?interval:sample.distance())','cut.removed(cut.rectangular()||cut.arrival()&&Math.abs(sample.distance()-cut.end())<1e-7?interval:sample.distance())'),
 ('  public static boolean present(Mesh mesh,Sample sample,double lateral,double margin){','''  public record Cap(V a,V b){}
  /** Cut endpoints are one-sided cross sections. Close the transverse slab faces,
   * but do not add a wall where two adjacent closed intervals continue each other. */
  public static List<Cap> caps(Mesh mesh){
    var stations=new TreeSet<Double>();
    for(var cut:mesh.settings().options().lanePoints().cuts())if(cut.rectangular()){
      stations.add(cut.begin());stations.add(cut.end());
    }
    var result=new ArrayList<Cap>();
    for(double d:stations){
      if(d<=mesh.first().distance()+1e-7||d>=mesh.last().distance()-1e-7)continue;
      var sample=RoadStructures.sample(mesh,d);
      var before=holes(mesh,sample,d-1e-5);var after=holes(mesh,sample,d+1e-5);
      for(int i=0;i<before.size();i++){
        var a=before.get(i);var b=after.get(i);
        if(Math.abs((a.high()-a.low())-(b.high()-b.low()))<1e-6)continue;
        boolean opening=b.high()-b.low()>a.high()-a.low();var hole=opening?b:a;
        V low=sample.at(hole.low(),0),high=sample.at(hole.high(),0);
        result.add(new Cap(opening?low:high,opening?high:low));
      }
    }
    return List.copyOf(result);
  }
  public static boolean present(Mesh mesh,Sample sample,double lateral,double margin){''')])
edit(C+'RoadSurface.java',[
 ('    List<Mesh> union = new ArrayList<>(neighbors);','    for(var cap:LaneDeck.caps(mesh))wall(pavement,cap.a(),cap.b(),thickness,joined);\n\n    List<Mesh> union = new ArrayList<>(neighbors);')])
edit(C+'RoadStructures.java',[
 ('    out.addAll(RoadInfrastructure.plan(mesh,ground));','    out.addAll(RoadInfrastructure.plan(mesh,ground));\n    out.addAll(LaneClosureLandscape.plan(mesh,ground));')])
edit(W+'RoadData.java',[
 ('root.getInt("Version") > 37','root.getInt("Version") > 38'),
 ('root.putInt("Version", 37)','root.putInt("Version", 38)')])
edit('src/main/java/com/sora/splineroads/net/RoadNetwork.java', [('PROTOCOL = "55"','PROTOCOL = "56"')])
edit('build.gradle',[("version = '0.40.9-alpha'","version = '0.40.10-alpha'")])
edit('src/main/resources/META-INF/mods.toml',[('version="0.40.9-alpha"','version="0.40.10-alpha"')])
print('Applied closure418 integration to exact stage3 baseline; tests still required.')
