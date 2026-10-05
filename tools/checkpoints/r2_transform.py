from pathlib import Path
r=Path('src/main/java/com/sora/splineroads/core')
p=r/'RoadProfile.java';s=p.read_text();s=s.replace('List<Double> transitionDividers) {','''List<Double> transitionDividers, double medianCenter) {
    public Layout(Catalog catalog, double laneWidth, double median, double motorMin, double motorMax,
        double cycleWidth, double curbWidth, double shoulderWidth, int outside, List<Double> dividers) {
      this(catalog,laneWidth,median,motorMin,motorMax,cycleWidth,curbWidth,shoulderWidth,outside,dividers,0);
    }
    public double medianEdge(int side) { return medianCenter + side * median / 2; }
    public int lanesOnSide(int side) {
      if(!catalog.twoWay())return catalog.lanes();
      double span=side<0?medianEdge(-1)-motorMin:motorMax-medianEdge(1);
      return Math.max(0,(int)Math.floor(span/Math.max(.001,laneWidth)+.5));
    }''');s=s.replace('out.add(sign * (median / 2 + n * laneWidth));','out.add(medianCenter + sign * (median / 2 + n * laneWidth));');p.write_text(s)
p=r/'LaneSections.java';s=p.read_text();s=s.replace('''      if(layout.catalog().twoWay())throw new IllegalArgumentException("整车道分离仅支持单向主线；双向道路请使用保留原车道分流");
      if(event.lane()!=0&&event.lane()!=layout.catalog().lanes()-1)throw new IllegalArgumentException("整车道分离须选择单向主线的一侧边缘车道");''','''      var c=layout.catalog();int per=c.lanes()/2;
      if(event.sign()!=lane.sign())throw new IllegalArgumentException("分离方向与所选车道的实际行驶方向不一致");
      boolean outer=c.twoWay()?event.lane()==per-1||event.lane()==c.lanes()-1:event.lane()==0||event.lane()==c.lanes()-1;
      if(!outer)throw new IllegalArgumentException(c.twoWay()?"双向整车道分离请选择该方向最外侧车道，不能挖走内部车道或中央隔离":"整车道分离须选择单向主线的一侧边缘车道");''')
s=s.replace('    if(c.twoWay())throw new IllegalArgumentException("整车道分离当前仅支持单向道路；双向道路请先拆分为单向主线或使用保留车道分流");','')
a=s.index('    int low=0,high=count-1;');b=s.index('    double shift=',a)
s=s[:a]+'''    double trimLow=0,trimHigh=0;
    if(c.twoWay()) {
      int per=count/2;
      for(int i=0;i<count;i++)if(removal[i]>.000001) {
        if(i!=per-1&&i!=count-1)throw new IllegalArgumentException("双向整车道分离只支持各方向最外侧车道");
        if(count!=RoadProfile.catalog(raw.settings().style()).lanes())
          throw new IllegalArgumentException("分离区间不能跨越车道数变化接缝，请在同一稳定断面内设置接头");
        if(i<per)trimLow+=layout.laneWidth()*removal[i];else trimHigh+=layout.laneWidth()*removal[i];
      }
      if(layout.medianEdge(-1)-layout.motorMin()-trimLow<-.001||
          layout.motorMax()-layout.medianEdge(1)-trimHigh<-.001)
        throw new IllegalArgumentException("分离范围超出相应半幅机动车道");
    } else {
      int low=0,high=count-1;
      while(low<count&&removal[low]>.000001){trimLow+=layout.laneWidth()*removal[low];low++;}
      while(high>=low&&removal[high]>.000001){trimHigh+=layout.laneWidth()*removal[high];high--;}
      for(int i=low;i<=high;i++)if(removal[i]>.000001)throw new IllegalArgumentException("不能在保留两侧车道时挖走内部车道；请先通过普通分流接头将它引至外侧");
    }
    double motorWidth=layout.motorMax()-layout.motorMin()-layout.median()-trimLow-trimHigh;
    if(motorWidth<layout.laneWidth()*.99)throw new IllegalArgumentException("整车道分离后至少保留一条主路通行车道，不能挖空整条道路");
''' +s[b:]
s=s.replace('var catalog=new RoadProfile.Catalog(c.type(),Math.max(1,retained),false,c.median(),c.shoulder());','var catalog=c.twoWay()?c:new RoadProfile.Catalog(c.type(),Math.max(1,retained),false,c.median(),c.shoulder());')
s=s.replace('layout.outside(),List.copyOf(dividers)));','layout.outside(),List.copyOf(dividers),layout.medianCenter()-shift));');p.write_text(s)
p=r/'RaisedRoadProfile.java';s=p.read_text();s=s.replace('int side = divider < 0 ? -1 : 1;','int side = divider < base.medianCenter() ? -1 : 1;').replace('side * base.outer(side)','side * (base.outer(side) - base.medianCenter())').replace('Math.abs(divider) - base.median()','Math.abs(divider - base.medianCenter()) - base.median()').replace('dividers.add(side *','dividers.add(base.medianCenter() + side *').replace('base.outside(), List.copyOf(dividers))','base.outside(), List.copyOf(dividers), base.medianCenter())');p.write_text(s)
# preserve file endings for the user's patched RoadSurface
p=r/'RoadSurface.java';original=p.read_bytes();s=original.decode().replace('\r\n','\n');s=s.replace('{x=0;y=0;}','{x=RoadProfile.layout(mesh,a).medianCenter();y=RoadProfile.layout(mesh,b).medianCenter();}')
s=s.replace('"center:-1",-(.14+la.median()/2),-(.14+lb.median()/2)','"center:-1",la.medianCenter()-(.14+la.median()/2),lb.medianCenter()-(.14+lb.median()/2)').replace('              -(.14 + la.median() / 2),','              la.medianCenter()-(.14 + la.median() / 2),').replace('              -(.14 + lb.median() / 2),','              lb.medianCenter()-(.14 + lb.median() / 2),')
s=s.replace('"center:1",.14+la.median()/2,.14+lb.median()/2','"center:1",la.medianCenter()+.14+la.median()/2,lb.medianCenter()+.14+lb.median()/2').replace('a, b, .14 + la.median() / 2, .14 + lb.median() / 2','a, b, la.medianCenter()+.14 + la.median() / 2, lb.medianCenter()+.14 + lb.median() / 2')
s=s.replace('"center:0",0,0,dividers)','"center:0",la.medianCenter(),lb.medianCenter(),dividers)').replace('stripe(markings, a, b, 0, 0, .12, true, dividers)','stripe(markings, a, b, la.medianCenter(), lb.medianCenter(), .12, true, dividers)')
s=s.replace('"median:"+side,side*(la.median()/2+.12),side*(lb.median()/2+.12)','"median:"+side,la.medianCenter()+side*(la.median()/2+.12),lb.medianCenter()+side*(lb.median()/2+.12)').replace('                side * (la.median() / 2 + .12),','                la.medianCenter()+side * (la.median() / 2 + .12),').replace('                side * (lb.median() / 2 + .12),','                lb.medianCenter()+side * (lb.median() / 2 + .12),')
s=s.replace('(aa.get(j) + bb.get(j)) / 2)))','(aa.get(j) + bb.get(j)-la.medianCenter()-lb.medianCenter()) / 2)))');p.write_bytes(s.replace('\n','\r\n').encode() if b'\r\n' in original else s.encode())
p=r/'RoadStructures.java';s=p.read_text();s=s.replace('Math.abs(q.lateral()) + margin > RoadProfile.layout(lower, s).median() / 2','Math.abs(q.lateral()-RoadProfile.layout(lower,s).medianCenter()) + margin > RoadProfile.layout(lower, s).median() / 2').replace('RoadQueries.contains(road, s.center(), 2, .2)','RoadQueries.contains(road, s.at(RoadProfile.layout(lower,s).medianCenter(),0), 2, .2)');a=s.index('  private static void median(');b=s.index('  /** Tapered pole',a);body=s[a:b].replace('a.center()','a.at(la.medianCenter(),0)').replace('b.center()','b.at(lb.medianCenter(),0)').replace('a.at(side * (soilWidth / 2 + .1), 0)','a.at(la.medianCenter()+side * (soilWidth / 2 + .1), 0)').replace('b.at(side * (soilWidth / 2 + .1), 0)','b.at(lb.medianCenter()+side * (soilWidth / 2 + .1), 0)');s=s[:a]+body+s[b:];s=s.replace('V center = sample(mesh, distance).center();\n    return !ground.joined(center)','var at=sample(mesh,distance);\n    V center=at.at(RoadProfile.layout(mesh,at).medianCenter(),0);\n    return !ground.joined(center)');p.write_text(s)
p=r/'RoadStreetscape.java';s=p.read_text().replace('if(center)addLamp(out,mesh,ground,at,0,raised?0:.3','if(center)addLamp(out,mesh,ground,at,l.medianCenter(),raised?0:.3');key='    return new Mesh(mesh.samples(),mesh.settings().options(next),mesh.min(),mesh.max(),mesh.length(),mesh.closed(),mesh.controlPoint(),mesh.controls());';assert key in s;s=s.replace(key,'''    Mesh reference=mesh.reference();
    if(reference!=null) {
      var ro=reference.settings().options();
      var rs=reference.settings().options(ro.streetscape(ro.streetscape().raisedSpans(next.streetscape().raisedSpans())));
      reference=new Mesh(reference.samples(),rs,reference.min(),reference.max(),reference.length(),reference.closed(),reference.controlPoint(),reference.controls(),reference.reference());
    }
    return new Mesh(mesh.samples(),mesh.settings().options(next),mesh.min(),mesh.max(),mesh.length(),mesh.closed(),mesh.controlPoint(),mesh.controls(),reference);''');p.write_text(s)
p=r/'RoadSignals.java';s=p.read_text().replace(': side * (l.median() / 2 + .1)',': l.medianCenter() + side * (l.median() / 2 + .1)');p.write_text(s)
p=r/'RoadLaneLines.java';s=p.read_text().replace('"中央黄线（左）",-.14','"中央黄线（左）",l.medianCenter()-.14').replace('"中央黄线（右）",.14','"中央黄线（右）",l.medianCenter()+.14').replace('"中央虚线",0','"中央虚线",l.medianCenter()').replace('side*(l.median()/2+.12)','l.medianCenter()+side*(l.median()/2+.12)');p.write_text(s)
p=r/'RoadJunction.java';s=p.read_text();key='      for (int i = 0; i < c.lanes(); i++) {\n        double lateral;';assert key in s;s=s.replace(key,'''      if(mesh.reference()!=null) {
        var raw=LaneSections.reference(mesh);
        int slots=RoadProfile.layout(raw,RoadStructures.sample(raw,d)).catalog().lanes();
        for(int slot=0;slot<slots;slot++)if(LaneSections.active(mesh,d,slot)) {
          var lane=LanePoints.lane(raw,d,slot);
          arrow(out,lane.position(),lane.direction(),s.left().mul(lane.sign()));
        }
        continue;
      }
      for (int i = 0; i < c.lanes(); i++) {
        double lateral;''').replace('lateral = side * (l.median() / 2 + (i % per + .5) * l.laneWidth());','lateral = l.medianCenter() + side * (l.median() / 2 + (i % per + .5) * l.laneWidth());');p.write_text(s)
for name in ['LaneRampApproach.java','../world/LaneRamps.java']:
 p=r/name;s=p.read_text().replace('(lateral<0?-1:1)','(lateral<layout.medianCenter()?-1:1)');p.write_text(s)
