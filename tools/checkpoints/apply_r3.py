from pathlib import Path
import hashlib, shutil
r=Path('src/main/java/com/sora/splineroads')
expected={'client/LaneRampScreen.java':'0f4e002739ddf42b043bdda5a77d028da942fadaf54d732bd9055dcb906cc1ac','core/LanePoints.java':'a1405079f9d139f34ac7348c9751ed03395a06a309dc52969573fc611224927e','core/LaneSections.java':'1b3ae0f4dd22df765aa4ff0947f3cd7d1131ee079c7e4e184a450c96de7b49db','net/RoadNetwork.java':'1b8d1d635f7adfb69ea118ed4b3a32468b49e84f71bd7a16be1350884969c16c','world/LaneCrossSections.java':'1eecf05563099749ac5a579e1026b56f6e2f4ddda86c95dd375ff281faca11f4','world/LaneRampTool.java':'0347d368fb9f129fc9a3af3eb2b89735246b1e6d07763a6cd7152bcd5b91297a','world/LaneRamps.java':'4afc61d5a34e88e429dee05ad50fedfd54c3ae8ab8099b1494facb578fe6b8a7','world/RoadData.java':'345aa9ae3882f4e94515651c988e0e0efe7afd79775efc5b8ec9bc2161d4f557'}
for n,h in expected.items():
 assert hashlib.sha256((r/n).read_bytes()).hexdigest()==h, 'Source changed: '+n
def edit(name,fn):
 p=r/name;s=p.read_text();p.write_text(fn(s))
def lane(s):
 s=s.replace('BRANCH("保留原车道分流"), DETACH("整车道分离"), EXTRA("额外扩出")','BRANCH("普通分流（原车道直行）"), DETACH("整车道分离"), EXTRA("额外扩出"), TEMPORARY("保留车道分离")')
 s=s.replace('public boolean sourceExtra(){return departure==Departure.EXTRA;}','public boolean sourceExtra(){return departure==Departure.EXTRA;}\n    public boolean separatesLane(){return departure==Departure.DETACH||departure==Departure.TEMPORARY;}')
 return s
edit('core/LanePoints.java',lane)
def sections(s):
 s=s.replace('public enum Kind { DEPART, REPLACE }','public enum Kind { DEPART, REPLACE, TEMPORARY }')
 s=s.replace('public record Event(UUID connection,Kind kind,int lane,int sign,double station,double transition){}','''public record Event(UUID connection,Kind kind,int lane,int sign,double station,double transition,double returnStation){
    public Event(UUID connection,Kind kind,int lane,int sign,double station,double transition){this(connection,kind,lane,sign,station,transition,Double.NaN);}
  }''')
 s=s.replace('if(event.kind()==Kind.DEPART){','if(event.kind()!=Kind.REPLACE){')
 s=s.replace('for(Event candidate:ordered)if(candidate.kind()==Kind.REPLACE','for(Event candidate:ordered)if(event.kind()==Kind.DEPART&&candidate.kind()==Kind.REPLACE')
 s=s.replace('double end=replacement==null?(event.sign()>0?raw.length()+2*event.transition():-2*event.transition()):replacement.station();','''double end=event.kind()==Kind.TEMPORARY&&Double.isFinite(event.returnStation())?event.returnStation():
          replacement==null?(event.sign()>0?raw.length()+2*event.transition():-2*event.transition()):replacement.station();
      if(event.kind()==Kind.TEMPORARY&&Double.isFinite(event.returnStation())&&
          (end<0||end>raw.length()||event.sign()*(end-event.station())<2*event.transition()))
        throw new IllegalArgumentException("保留车道分离没有足够空间完成封闭及安全恢复");''')
 s=s.replace('保留原车道分流','普通分流（原车道直行）')
 return s
edit('core/LaneSections.java',sections)
p=r/'world/LaneCrossSections.java';s=p.read_text()
s=s.replace('var result=new LinkedHashMap<>(records);derive(result,edited,proposal,null);return result;','''return staged(records,edited,proposal,null);
  }
  public static Map<UUID,RoadRecord> staged(Map<UUID,RoadRecord> records,UUID edited,LanePoints.Link proposal,RoadGeometry.Mesh candidate){
    var result=new LinkedHashMap<>(records);Set<UUID> hosts=new HashSet<>();hosts.add(proposal.from().road());
    if(proposal.to().road()!=null)hosts.add(proposal.to().road());
    var old=records.get(edited);var oldLink=old==null?null:LaneTopology.metadata(old).link();
    if(oldLink!=null){hosts.add(oldLink.from().road());if(oldLink.to().road()!=null)hosts.add(oldLink.to().road());}
    derive(result,edited,proposal,hosts,candidate);return result;''')
s=s.replace('derive(records,null,null,null);','derive(records,null,null,null,null);').replace('derive(records,null,null,hosts);','derive(records,null,null,hosts,null);')
s=s.replace('LanePoints.Link proposal,Set<UUID> hosts){','LanePoints.Link proposal,Set<UUID> hosts,RoadGeometry.Mesh candidate){')
s=s.replace('for(var road:records.values())if(!road.id().equals(edited))add(events,records,road.id(),LaneTopology.metadata(road).link(),hosts);','''for(var road:records.values())if(!road.id().equals(edited)){
      var link=LaneTopology.metadata(road).link();if(link==null)continue;
      if(hosts==null||hosts.contains(link.from().road())||hosts.contains(link.to().road()))
        add(events,records,road.id(),link,hosts,link.options().departure()==LanePoints.Departure.TEMPORARY?road.rawMesh():null,road.structures());
    }''')
s=s.replace('add(events,records,edited,proposal,hosts);','add(events,records,edited,proposal,hosts,candidate,List.of());')
s=s.replace('LanePoints.Link link,Set<UUID> hosts){','LanePoints.Link link,Set<UUID> hosts,RoadGeometry.Mesh candidate,List<RoadStructures.Part> parts){')
old='if(link.options().departure()==LanePoints.Departure.DETACH&&(hosts==null||hosts.contains(link.from().road())))add(events,all,connection,link.from(),LaneSections.Kind.DEPART,0,link.options().transition());'
new='''if(link.options().separatesLane()&&(hosts==null||hosts.contains(link.from().road()))) {
      if(link.options().departure()==LanePoints.Departure.DETACH)add(events,all,connection,link.from(),LaneSections.Kind.DEPART,0,link.options().transition());
      else {
        var source=all.get(link.from().road());if(source==null)throw new IllegalArgumentException("分离车道的宿主道路已不存在");
        var point=LaneTopology.point(source,link.from().point());var raw=source.rawMesh();var lane=LanePoints.lane(raw,point);
        if(lane.sign()>0?raw.length()-lane.station()>=.02:lane.station()>=.02){
        double end=candidate==null?Double.NaN:LaneReopening.restoreStation(raw,point.lane(),lane.station(),candidate,parts,link.options().transition());
        events.computeIfAbsent(source.id(),key->new ArrayList<>()).add(new LaneSections.Event(connection,LaneSections.Kind.TEMPORARY,point.lane(),lane.sign(),lane.station(),link.options().transition(),end));
        }
      }
    }'''
assert old in s;s=s.replace(old,new)
s=s.replace('  private LaneCrossSections(){}','''  /** After structures were planned, rederive only temporary hosts touched by this transaction. */
  static boolean needsRestoreRefresh(List<RoadIndex.Built> planning,Collection<UUID> changed){
    Set<UUID> changes=new HashSet<>(changed),hosts=new HashSet<>();var all=new LinkedHashMap<UUID,RoadRecord>();
    for(var b:planning)all.put(b.record.id(),b.record);
    for(var r:all.values()){
      var l=LaneTopology.metadata(r).link();if(l!=null&&l.options().departure()==LanePoints.Departure.TEMPORARY&&
          (changes.contains(r.id())||changes.contains(l.from().road())))hosts.add(l.from().road());
    }
    if(hosts.isEmpty())return false;var staged=new LinkedHashMap<>(all);derive(staged,null,null,hosts,null);
    for(UUID host:hosts)if(!LaneTopology.metadata(all.get(host)).cuts().equals(LaneTopology.metadata(staged.get(host)).cuts()))return true;
    return false;
  }
  private LaneCrossSections(){}''')
p.write_text(s)
def ramps(s):
 s=s.replace('validate(mesh,context,id,actual);var start=RoadRibbon.start(mesh);','''var finalContext=actual.options().departure()==LanePoints.Departure.TEMPORARY?
              LaneCrossSections.staged(all,id,actual,mesh):context;
          validate(mesh,finalContext,id,actual);
          if(actual.options().departure()==LanePoints.Departure.TEMPORARY)
            LaneReopening.validateRestored(host(finalContext,actual.from()).mesh(),p.lane(),mesh,id);
          var start=RoadRibbon.start(mesh);''')
 s=s.replace('double from=mesh.samples().get(contactEnd(mesh,all,sourceIds,true)).distance();','double from=link.options().separatesLane()?0:mesh.samples().get(contactEnd(mesh,all,sourceIds,true)).distance();')
 s=s.replace('double ws=source.isEmpty()?0:','double ws=source.isEmpty()||link.options().separatesLane()?0:')
 s=s.replace('if(sourceHosts.contains(road.id())&&c.to()<=sourceLimit+.01||targetHosts.contains(road.id())&&c.from()>=targetLimit-.01)continue;','''boolean sourceJoin=sourceHosts.contains(road.id())&&c.to()<=sourceLimit+.01;
        if(sourceJoin&&link.options().separatesLane())sourceJoin=separationThroat(c,road,link);
        if(sourceJoin||targetHosts.contains(road.id())&&c.from()>=targetLimit-.01)continue;''')
 s=s.replace('double from=base.samples().get(contactEnd(base,all,contactRoads(all,link.from()),true)).distance();','double from=link.options().separatesLane()?0:base.samples().get(contactEnd(base,all,contactRoads(all,link.from()),true)).distance();')
 s=s.replace('  private static List<Mesh> heightCandidates(','''  /** Only the selected slot joins; opposite traffic is not exempt merely because it has the same host ID. */
  private static boolean separationThroat(RoadClearance.Contact contact,RoadRecord road,LanePoints.Link link){
    if(!road.id().equals(link.from().road()))return false;
    var raw=road.rawMesh();var point=LaneTopology.point(road,link.from().point());var start=LanePoints.lane(raw,point);
    var q=RoadQueries.horizontal(raw,contact.ours());var lane=LanePoints.lane(raw,q.sample().distance(),point.lane());
    return start.sign()*(lane.station()-start.station())>=-.25&&
        Math.abs(contact.ours().sub(lane.position()).dot(q.sample().left()))<=lane.width()/2+.55;
  }
  private static List<Mesh> heightCandidates(''')
 s=s.replace('reply.put("ChangedRoads",changed);','''reply.put("ChangedRoads",changed);
    if(options.departure()==LanePoints.Departure.TEMPORARY)for(var cut:LaneTopology.metadata(staging.get(from.road())).cuts())if(cut.connection().equals(id)){
      reply.putBoolean("TemporaryClosure",true);reply.putDouble("ReopenAfter",cut.sign()*(cut.end()-cut.begin())-cut.transition());reply.putDouble("RestoredAfter",cut.sign()*(cut.end()-cut.begin()));break;
    }''')
 return s
edit('world/LaneRamps.java',ramps)
def data(s):
 return s.replace('root.getInt("Version") > 34','root.getInt("Version") > 35').replace('root.putInt("Version", 34)','root.putInt("Version", 35)').replace('LaneTopology.needsRefresh(this,planning,built.stream().map(b->b.record.id()).toList());pass++','(LaneTopology.needsRefresh(this,planning,built.stream().map(b->b.record.id()).toList())||LaneCrossSections.needsRestoreRefresh(planning,built.stream().map(b->b.record.id()).toList()));pass++')
edit('world/RoadData.java',data)
edit('net/RoadNetwork.java',lambda s:s.replace('PROTOCOL = "52"','PROTOCOL = "53"'))
def screen(s):
 s=s.replace('status="设置已改变，请重新预览。";','''status=switch(departure){
      case TEMPORARY -> "保留车道分离：直连匝道，主路暂时关闭该车道；净空安全后渐变恢复。请预览。";
      case DETACH -> "整车道分离：直连匝道，主路下游取消该车道。请预览。";
      case BRANCH -> "普通分流：原车道继续直行；不是先关闭再恢复。请预览。";
      case EXTRA -> "额外扩出：保持既有车道，另拓出匝道。请预览。";
    };''')
 s=s.replace('status=String.format(Locale.ROOT,"预览有效：%s，长 %.1f 格。%s 右键空气返回并建造。",t.contains("ResolvedPath")?LanePoints.Path.valueOf(t.getString("ResolvedPath")).label:path.label,r.mesh().length(),landing);','''String closure=t.getBoolean("TemporaryClosure")?String.format(Locale.ROOT," 主路从A暂时关闭此车道，下游 %.1f 格开始恢复、%.1f 格恢复完整。",t.getDouble("ReopenAfter"),t.getDouble("RestoredAfter")):"";
      status=String.format(Locale.ROOT,"预览有效：%s，长 %.1f 格。%s%s 右键空气返回并建造。",t.contains("ResolvedPath")?LanePoints.Path.valueOf(t.getString("ResolvedPath")).label:path.label,r.mesh().length(),landing,closure);''')
 return s
edit('client/LaneRampScreen.java',screen)
edit('world/LaneRampTool.java',lambda s:s.replace('public LaneRampTool(){','''private static LanePoints.Options freshOptions(){return new LanePoints.Options(LanePoints.Path.AUTO,LanePoints.Departure.TEMPORARY,LanePoints.Arrival.MERGE,24,32,LanePoints.Elevation.AUTO,LanePoints.Landing.FLEXIBLE);}
  public LaneRampTool(){''').replace(':LanePoints.Options.DEFAULT',':freshOptions()').replace('LanePointCodec.options(LanePoints.Options.DEFAULT)','LanePointCodec.options(freshOptions())'))
for name in ['build.gradle','src/main/resources/META-INF/mods.toml']:
 p=Path(name);p.write_text(p.read_text().replace('0.40.1-alpha','0.40.2-r3-checkpoint'))
for name,dest in [('LaneReopening.java','src/main/java/com/sora/splineroads/core/LaneReopening.java'),('Temporary403Validation.java','src/validation/java/com/sora/splineroads/Temporary403Validation.java'),('Temporary403ModelValidation.java','tools/model-validation/com/sora/splineroads/world/Temporary403ModelValidation.java'),('test_temporary403.sh','tools/test_temporary403.sh')]:
 p=Path(dest);assert not p.exists(),dest;p.parent.mkdir(parents=True,exist_ok=True);shutil.copyfile(Path('tools/checkpoints/r3')/(name+'.txt'),p)
after={'client/LaneRampScreen.java':'043a68c238ff581e96e51f94cdef32bf44ba0eb67f22b094f014d6c7a681ab73','core/LanePoints.java':'1f4e59a611b77d65963d95d05cc51d91a188e57491a78f0018c81ca443556e75','core/LaneSections.java':'5c7f3d17774dd84173a4708bbf5a24129d2814c699d88fecfe9a26660947c41d','core/LaneReopening.java':'8b8f45cc92895db735568cb8e954e0eec7ccb75accdaabe5af39559b0eee020c','net/RoadNetwork.java':'2d3bff848195e8272c13e97f40ae7b4eeb34d34256cabf49981139ae754706f0','world/LaneCrossSections.java':'2e3674e702e6a39701b41ecd9d9e3d2792db7dd05efe6ce519147c2ebe1231bb','world/LaneRampTool.java':'0d640bb7c09ba95ff2d50ed8077b405d7b5d38a466b8b4bffa68bb42567bcfe8','world/LaneRamps.java':'0ef5cdeb5ecf99e8b163ae89c7e94d947126180af74fa05675e861d730587986','world/RoadData.java':'f1d637bacc1609ded4da4b8f749f013342404fc48026d940e9f0d16f88faacbd'}
for n,h in after.items():
 assert hashlib.sha256((r/n).read_bytes()).hexdigest()==h,'Output differs from reviewed/tested code: '+n
print('R3 exact production hashes PASS; full Forge validation still required.')
