from pathlib import Path
import hashlib
root=Path('src/main/java/com/sora/splineroads')
expected={
 'core/RoadInfrastructure.java':('9ef1316d3427946a3e330d57d2560b4a2c9118e8573203d4039f7e9e22c24d62','1443600644748ac174e13ee380da1697212bc57222f3fd8c446aa6518829473b'),
 'world/RoadIndex.java':('ac5cfc68bb58309adbd3f2453a4b6846ebc21b43f4d862e4e31417da2fdfa9f7','09df00fe46f363bf9820acf0d4533cdcfc7c52652a5dad8a3dc38d14d93936e2')}
old={n:(root/n).read_bytes() for n in expected}
for n,v in old.items():assert hashlib.sha256(v).hexdigest()==expected[n][0], 'Unexpected source revision: '+n
s=old['core/RoadInfrastructure.java'].decode().replace('\r\n','\n')
s=s.replace('c.headroom()+mesh.settings().thickness(),Material.TUNNEL);','Math.max(wallTop(c,a.halfWidth()),wallTop(c,b.halfWidth()))+mesh.settings().thickness(),Material.TUNNEL);',1)
s=s.replace('1.3,c.headroom()+mesh.settings().thickness(),Material.TUNNEL);','1.3,wallTop(c,s.halfWidth())+mesh.settings().thickness(),Material.TUNNEL);')
s=s.replace('  /** Height of the actual planar roof band at a lateral position. */','''  /** The arch spans beyond the pavement, so its height at the INNER wall is above
   * headroom. Stop at that real roof band, not at the outer spring line. */
  private static double wallTop(Config c,double half){return ceiling(c,half,Math.max(0,half-.05))+.03;}

  /** A conservative column-specific interior ceiling, not a full-height rectangular
   * excavation outside the tunnel. Shell volume is handled by its own collision body.
   * Projection uses the existing spatial index. The lateral cell radius preserves all
   * interior air under an arch while no longer clearing to the centre height at its sides. */
  public static double excavationTop(Mesh mesh,int x,int z,double deckTop){
    var c=mesh.settings().options().infrastructure();
    var q=RoadQueries.horizontal(mesh,new V(x+.5,deckTop,z+.5));
    double half=q.sample().halfWidth(),lateral=Math.max(0,Math.abs(q.lateral())-.75);
    double rise=ceiling(c,half,lateral);
    for(double dx:new double[]{0,1})for(double dz:new double[]{0,1}) {
      var corner=RoadQueries.horizontal(mesh,new V(x+dx,deckTop,z+dz));
      half=Math.max(half,corner.sample().halfWidth());
    }
    rise=Math.max(rise,ceiling(c,half,lateral));
    return deckTop+rise+.02;
  }
  /** Height of the actual planar roof band at a lateral position. */''')
new={'core/RoadInfrastructure.java':s}
s=old['world/RoadIndex.java'].decode().replace('\r\n','\n')
s=s.replace('clearEnd(c);','clearEnd(c,new BlockPos(p.getX(),0,p.getZ()).asLong());',1)
s=s.replace('for (var c : clearanceColumns().values()) n += clearEnd(c) - clearMin(c);','for (var e : clearanceColumns().entrySet()) n += Math.max(0,clearEnd(e.getValue(),e.getKey())-clearMin(e.getValue()));')
s=s.replace('end = clearEnd(e.getValue());','end = clearEnd(e.getValue(),e.getKey());')
s=s.replace('    private volatile RoadRaster.Local tunnelClearance;\n','')
a=s.index('      if(dryColumns==null){\n        var m=new Mesh');b=s.index('    private double effectiveClearance()',a)
s=s[:a]+'''      // Do not mark the 1.65-block lining/exterior apron as invisible air clearance.
      // Wall and roof blocks already belong to shellCells/cellData and get their own exact body.
      return columnData;
    }
'''+s[b:]
s=s.replace('''    private int clearEnd(Column c) {
      return (int) Math.ceil(c.maxTop() + effectiveClearance() - 1e-7);
    }''','''    private final Map<Long,Integer> tunnelEnds=bounded(2048);
    private int clearEnd(Column c,long key) {
      if(record.settings().structure()==Structure.TUNNEL) {
        var previous=tunnelEnds.get(key);if(previous!=null)return previous;
        var p=BlockPos.of(key);
        int result=(int)Math.ceil(RoadInfrastructure.excavationTop(mesh,p.getX(),p.getZ(),c.maxTop())-1e-7);
        tunnelEnds.put(key,result);return result;
      }
      return (int) Math.ceil(c.maxTop() + effectiveClearance() - 1e-7);
    }''')
a=s.index('        if(record.settings().structure()==Structure.TUNNEL) {',s.index('public boolean clearanceAt'));b=s.index('        } else {',a)
s=s[:a]+'''        if(record.settings().structure()==Structure.TUNNEL) {
          c=column(p);
'''+s[b:]
s=s.replace('p.getY()<clearEnd(c);','p.getY()<clearEnd(c,key);')
new['world/RoadIndex.java']=s
for n,s in new.items():
    if b'\r\n' in old[n]:s=s.replace('\n','\r\n')
    new[n]=s.encode();assert hashlib.sha256(new[n]).hexdigest()==expected[n][1], 'Different from tested source: '+n
for n,data in new.items():(root/n).write_bytes(data);print('APPLIED',n,expected[n][1])
p=Path('PROGRESS.md');previous=p.read_text()
p.write_text('# WIP: 隧道侧墙/拱肩与开挖范围修复已保存，待本次CI\n\n墙顶接实际拱顶，而非外侧固定起拱净高；端部柱同步延伸。空气开挖只沿真实路面范围并按侧向拱顶高度算，壳体由自身碰撞体处理，不再把外扩1.65格都保留成不可放方块的空气柱。\n\n本地24组箱形/拱形宽度、坡度、方向、远近LOD的几何射线与索引检查通过，旧源码分别复现拱肩开口及过宽开挖；未实测Minecraft，不保证已破坏的旧地形自动复原。U08/U09/U10/U11为本次针对项，U12远景虚空及其他问题仍未标为解决。\n\n---\n\n'+previous)
