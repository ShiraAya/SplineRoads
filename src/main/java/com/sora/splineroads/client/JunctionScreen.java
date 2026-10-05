package com.sora.splineroads.client;

import com.sora.splineroads.core.*;
import com.sora.splineroads.core.RoadGeometry.*;
import com.sora.splineroads.world.*;
import com.sora.splineroads.net.RoadNetwork;
import java.util.*;
import java.util.function.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.*;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.nbt.*;
import net.minecraft.network.chat.Component;

public final class JunctionScreen extends Screen {
  private static CompoundTag draft;
  public static Mesh previewMesh;
  public static List<RoadSurface.Face> previewFaces=List.of();
  private final CompoundTag payload;
  private CompoundTag spec;
  private int page,arm,lane,turn,top;
  private String status="";
  private boolean pending,confirmDelete,signalLanes;
  private JunctionPlanner.Plan plan;
  private JunctionPreview.Image map;
  private Button build;
  private final List<Runnable> captures=new ArrayList<>();
  private final List<Label> labels=new ArrayList<>();
  private record Label(String text,int x,int y) {}
  public JunctionScreen(CompoundTag t){super(Component.literal("Spline Roads · 路口"));payload=t.copy();spec=payload.getCompound("Spec");if(payload.contains("SelectedArm")){arm=Math.max(0,Math.min(spec.getList("Arms",Tag.TAG_COMPOUND).size()-1,payload.getInt("SelectedArm")));page=1;}}
  public static void clear(){draft=null;previewMesh=null;previewFaces=List.of();ClientRoads.preview=null;ClientRoads.nodePreviews=List.of();}
  public static void open(CompoundTag fresh) {
    if(fresh.getString("Kind").equals("junctionReset")){clear();return;}
    CompoundTag t=fresh.copy();
    if(draft!=null&&!t.getBoolean("DeleteOnly")&&Arrays.equals(t.getLongArray("Points"),draft.getLongArray("Points"))&&Objects.equals(t.hasUUID("Id")?t.getUUID("Id"):null,draft.hasUUID("Id")?draft.getUUID("Id"):null)) {
      CompoundTag edited=draft.getCompound("Spec").copy();var incoming=t.getCompound("Spec").getList("Arms",Tag.TAG_COMPOUND);var saved=edited.getList("Arms",Tag.TAG_COMPOUND);
      if(incoming.size()==saved.size()){edited.put("Center",t.getCompound("Spec").getCompound("Center").copy());for(int i=0;i<saved.size();i++)for(String key:List.of("Node","DX","DZ","External","Attached"))saved.getCompound(i).put(key,incoming.getCompound(i).get(key).copy());t.put("Spec",edited);}
    }
    Minecraft.getInstance().setScreen(new JunctionScreen(t));
  }
  private CompoundTag arm(){return spec.getList("Arms",Tag.TAG_COMPOUND).getCompound(arm);}
  private void remember(){payload.put("Spec",spec);if(!payload.getBoolean("DeleteOnly"))draft=payload.copy();}
  private boolean capture(){try{captures.forEach(Runnable::run);resizeLanes();remember();return true;}catch(IllegalArgumentException e){status=e.getMessage();return false;}}
  private void resizeLanes(){
    for(Tag entry:spec.getList("Arms",Tag.TAG_COMPOUND)){var a=(CompoundTag)entry;int count=a.getInt("In");if(count<0||count>6)throw new IllegalArgumentException("进出车道数范围为 0–6");var list=a.getList("Lanes",Tag.TAG_COMPOUND);while(list.size()>count)list.remove(list.size()-1);while(list.size()<count){var l=new CompoundTag();l.putInt("Mask",-1);l.putIntArray("Targets",new int[]{-1,-1,-1,-1});l.putIntArray("TargetLanes",new int[]{-1,-1,-1,-1});list.add(l);}a.put("Lanes",list);}
    if(!spec.getList("Arms",Tag.TAG_COMPOUND).isEmpty())lane=Math.max(0,Math.min(lane,arm().getInt("In")-1));
  }
  private void change(Runnable r){if(capture()){r.run();remember();rebuildWidgets();}}
  private Button button(String text,int x,int y,int w,Runnable r){return addRenderableWidget(Button.builder(Component.literal(text),b->r.run()).bounds(x,y,w,20).build());}
  private void field(String label,CompoundTag tag,String key,int col,double row,boolean integer){
    int x=16+col*146,y=top+(int)(row*28);labels.add(new Label(label,x,y));
    EditBox box=new EditBox(font,x,y+10,136,17,Component.literal(label));box.setMaxLength(12);box.setValue(integer?Integer.toString(tag.getInt(key)):String.format(Locale.ROOT,"%.2f",tag.getDouble(key)));addRenderableWidget(box);
    captures.add(()->{try{if(integer)tag.putInt(key,Integer.parseInt(box.getValue()));else{double value=Double.parseDouble(box.getValue());if(!Double.isFinite(value))throw new NumberFormatException();tag.putDouble(key,value);}}catch(NumberFormatException e){throw new IllegalArgumentException(label+"：请输入有效数字");}});
  }
  private void toggle(String title,CompoundTag tag,String key,int x,int y,int w){button(title+"："+(tag.getBoolean(key)?"开":"关"),x,y,w,()->change(()->tag.putBoolean(key,!tag.getBoolean(key))));}
  @Override protected void init(){
    captures.clear();labels.clear();top=57;
    String[] tabs=spec.getList("Arms",Tag.TAG_COMPOUND).isEmpty()?new String[]{"环岛"}:new String[]{"路口","接入口","车道","斑马线","信号"};for(int i=0;i<tabs.length;i++){int p=i;button((page==i?"● ":"")+tabs[i],12+i*59,29,57,()->change(()->page=p));}
    if(page>0)button("接入口 "+(arm+1)+" / "+spec.getList("Arms",Tag.TAG_COMPOUND).size()+"  →",16,top,282,()->change(()->{arm=(arm+1)%spec.getList("Arms",Tag.TAG_COMPOUND).size();lane=0;}));
    if(page==0){
      button("类型："+(spec.getString("Kind").equals("ROUNDABOUT")?"环岛":"普通路口"),16,top,136,()->{if(!payload.getBoolean("Auto"))change(()->spec.putString("Kind",spec.getString("Kind").equals("ROUNDABOUT")?"INTERSECTION":"ROUNDABOUT"));});
      toggle("左侧通行",spec,"LeftTraffic",162,top,136);
      field("转角半径 / 格",spec,"Corner",0,1,false);field("路面厚度 / 格",spec,"Thickness",1,1,false);
      if(!payload.getBoolean("Auto")&&spec.getString("Kind").equals("ROUNDABOUT")){
        field("中央岛半径 / 格",spec,"Island",0,2,false);field("环道车道数 1–3",spec,"RingLanes",1,2,true);
        field("环道车道宽 / 格",spec,"RingWidth",0,3,false);
      } else if(!payload.getBoolean("Auto")){
        labels.add(new Label("支持双向 A ＋ 同侧出向 B / 入向 C",16,top+65));
        labels.add(new Label("横向道路可选；各入口车道数可不同",16,top+83));
      } else if(payload.getBoolean("GeneratedRing")) {
        labels.add(new Label("环岛尺寸在生成时设置，八向接点固定",16,top+72));
      } else labels.add(new Label("增删相连道路时自动更新路口方向",16,top+72));
      if(spec.getString("Kind").equals("ROUNDABOUT"))toggle("中央岛绿化",spec,"GreenIsland",162,top+95,136);
      toggle("路口导向虚线",spec,"Guides",16,top+121,136);
      if(spec.getString("Kind").equals("ROUNDABOUT"))toggle("环岛外侧护栏",spec,"OuterRail",162,top+121,136);
    }else if(page==1){
      if(payload.getBoolean("Auto")) {
        labels.add(new Label("驶入 "+arm().getInt("In")+" 车道 · 驶出 "+arm().getInt("Out")+" 车道",16,top+34));
        labels.add(new Label("道路总宽："+String.format(Locale.ROOT,"%.1f",arm().getDouble("Width"))+" 格",16,top+57));
        labels.add(new Label("继承各方向道路的宽度与分隔带",16,top+82));
        labels.add(new Label("右键路口外侧路面可修改该道路",16,top+105));
      } else {
        field("进入路口车道数",arm(),"In",0,1,true);field("驶出路口车道数",arm(),"Out",1,1,true);
        field("接入口总宽 / 格",arm(),"Width",0,2,false);field("中央分隔带宽 / 格",arm(),"Median",1,2,false);
        field("两侧非机动车道宽",arm(),"Cycle",0,3,false);field("两侧路缘宽",arm(),"Curb",1,3,false);
        button("分隔带："+(arm().getString("MedianKind").equals("GREEN")?"绿化":"护栏"),16,top+121,136,()->change(()->arm().putString("MedianKind",arm().getString("MedianKind").equals("GREEN")?"RAIL":"GREEN")));
      }
    }else if(page==2){
      var lanes=arm().getList("Lanes",Tag.TAG_COMPOUND);
      if(lanes.isEmpty())labels.add(new Label("此方向没有驶入车道",16,top+30));
      else {
        var l=lanes.getCompound(lane);int mask=l.getInt("Mask");
        button("驶入第 "+(lane+1)+" 车道（从左到右）→",16,top+21,282,()->change(()->lane=(lane+1)%lanes.size()));
        String[] names={"直行","左转","右转","掉头"};for(int i=0;i<4;i++){int bit=JunctionSpec.TURNS[i];button(((mask&bit)!=0&&mask>=0?"☑ ":"□ ")+names[i],16+i*72,top+42,66,()->change(()->l.putInt("Mask",(mask<0?0:mask)^bit)));}
        button("自动分配",16,top+62,136,()->change(()->l.putInt("Mask",-1)));button("禁行",162,top+62,136,()->change(()->l.putInt("Mask",0)));
        button("出口映射："+names[turn]+" →",16,top+83,282,()->change(()->turn=(turn+1)%4));
        int[] targets=l.getIntArray("Targets"),targetLanes=l.getIntArray("TargetLanes");
        button("出口："+(targets[turn]<0?"自动":targets[turn]+1),16,top+104,136,()->change(()->{targets[turn]++;if(targets[turn]>=spec.getList("Arms",Tag.TAG_COMPOUND).size())targets[turn]=-1;l.putIntArray("Targets",targets);}));
        button("出口车道："+(targetLanes[turn]<0?"自动":targetLanes[turn]+1),162,top+104,136,()->change(()->{targetLanes[turn]++;if(targetLanes[turn]>5)targetLanes[turn]=-1;l.putIntArray("TargetLanes",targetLanes);}));
        labels.add(new Label("当前："+JunctionSpec.maskName(mask),16,top+127));
      }
    }else if(page==3){
      toggle("斑马线",arm(),"Crosswalk",16,top+25,282);
      field("斑马线纵向宽度",arm(),"CrossWidth",0,2,false);field("距路口边缘 / 格",arm(),"Setback",1,2,false);
      field("停止线退后 / 格",arm(),"StopGap",0,3,false);
      labels.add(new Label("斑马线前自动收束中央分隔带",16,top+121));
    }else {
      button(signalLanes?"◂ 信号周期（1 / 2）":"车道信号（2 / 2）▸",16,top+22,282,()->change(()->signalLanes=!signalLanes));
      if(!signalLanes){
        String c=spec.getString("Control");button("控制："+(c.equals("SIGNALS")?"红绿灯":c.equals("YIELD")?"让行":"无信号灯"),16,top+43,282,()->change(()->spec.putString("Control",c.equals("NONE")?"YIELD":c.equals("YIELD")?"SIGNALS":"NONE")));
        field("绿灯 / 秒",spec,"Green",0,2.5,true);field("黄灯 / 秒",spec,"Yellow",1,2.5,true);
        field("全红间隔 / 秒",spec,"AllRed",0,3.5,true);field("入口相位组 0–7",arm(),"Phase",1,3.5,true);
      }else{
        var lanes=arm().getList("Lanes",Tag.TAG_COMPOUND);
        if(lanes.isEmpty())labels.add(new Label("此方向没有驶入车道",16,top+55));
        else{
          var l=lanes.getCompound(lane);if(!l.contains("LeftPhase"))l.putInt("LeftPhase",-1);
          button("驶入第 "+(lane+1)+" 车道（从左到右）→",16,top+44,282,()->change(()->lane=(lane+1)%lanes.size()));
          button(l.getBoolean("SplitLeft")?"左转 / 掉头：独立箭头灯":"左转 / 直行 / 右转：合用信号灯",16,top+65,282,()->change(()->l.putBoolean("SplitLeft",!l.getBoolean("SplitLeft"))));
          labels.add(new Label("沿用入口相位：前半直行，后半左转",16,top+98));
          labels.add(new Label("无独立左转灯的入口保持全程绿灯",16,top+121));
        }
      }
    }
    int bottom=Math.max(top+141,height-49);
    build=button(payload.hasUUID("Id")?"保存路口":"建造路口",16,bottom,91,()->submit(false));
    button("预览 / 返回世界",111,bottom,112,()->{if(refresh()){remember();minecraft.setScreen(null);}});
    if(payload.hasUUID("Id"))button(confirmDelete?"确认删除":"删除整体",227,bottom,71,()->{if(confirmDelete)submit(true);else{confirmDelete=true;rebuildWidgets();}});
    else button("关闭",227,bottom,71,this::onClose);
    build.active=!pending&&!payload.getBoolean("DeleteOnly");refresh();
  }
  private boolean refresh(){if(!capture())return false;try{var parsed=JunctionCodec.read(spec);AutoJunctions.Draft network=null;if(payload.getBoolean("Auto")){network=AutoJunctions.previewEdit(payload);for(var center:network.centers())if(center.getUUID("Id").equals(payload.getUUID("Id")))parsed=JunctionCodec.read(center.getCompound("Spec"));}plan=JunctionPlanner.plan(parsed);map=null;previewMesh=plan.pieces().get(0).mesh();
      var faces=new ArrayList<RoadSurface.Face>();
      faces.addAll(RoadSidewalks.preview(plan.sidewalks().keySet()));
      for(var piece:plan.pieces()) {
        faces.addAll(RoadSurface.custom(new RoadSurface.Geometry(List.of(),List.of()),piece.mesh(),piece.paint()).markings());
        piece.structures().forEach(part->faces.addAll(part.faces()));
      }
      previewFaces=List.copyOf(faces);ClientRoads.preview=previewMesh;ClientRoads.nodePreviews=network==null?plan.pieces().stream().map(JunctionPlanner.Piece::mesh).toList():network.roads().stream().map(RoadRecord::mesh).toList();status=JunctionPlanner.separatedEntrances(parsed)?"分隔式路口预览有效：同侧 B/C 保持独立，A 双向接入":"几何预览有效；建造时校验地形、权限和已有道路";return true;}catch(IllegalArgumentException e){plan=null;previewMesh=null;previewFaces=List.of();ClientRoads.preview=null;ClientRoads.nodePreviews=List.of();status=e.getMessage();return false;}}
  private void submit(boolean delete){if(pending)return;if(delete&&!payload.getList("Dependencies",Tag.TAG_STRING).isEmpty()&&!payload.contains("ConfirmDependencies")){minecraft.setScreen(new LaneDeleteConfirmScreen(this,payload.getList("Dependencies",Tag.TAG_STRING),()->{payload.put("ConfirmDependencies",payload.getList("Dependencies",Tag.TAG_STRING).copy());submit(true);}));return;}if(!delete&&!refresh())return;var command=payload.copy();command.putString("Action",delete?"junctionDelete":"junction");command.put("Spec",spec.copy());pending=true;build.active=false;status="正在校验并建造…";RoadNetwork.CHANNEL.sendToServer(new RoadNetwork.Action(command));}
  public void failed(String message){pending=false;status=message;if(build!=null)build.active=!payload.getBoolean("DeleteOnly");}
  @Override public void onClose(){capture();minecraft.setScreen(null);}
  @Override public boolean isPauseScreen(){return false;}
  @Override public void render(GuiGraphics g,int mx,int my,float dt){
    renderBackground(g);g.fill(6,5,Math.min(width-6,306),height-5,0xe51b2530);g.drawString(font,title,16,13,0xffffff);super.render(g,mx,my,dt);
    for(Label l:labels)g.drawString(font,l.text(),l.x(),l.y(),0xd6e2ed,false);
    g.drawString(font,font.plainSubstrByWidth(status,Math.max(280,width-30)),16,height-19,plan==null?0xffa799:0xb8d6cb,false);
    if(plan!=null&&width>510)drawPlan(g,318,38,width-330,height-76);
  }
  private void drawPlan(GuiGraphics g,int x,int y,int w,int h){
    int imageHeight=Math.max(40,h-28);
    if(map==null||map.width()!=w||map.height()!=imageHeight)map=JunctionPreview.render(plan,w,imageHeight,arm,lane,page==2);
    g.fill(x,y,x+w,y+h,0xff202c30);
    for(int row=0;row<map.height();row++)for(int col=0;col<w;){int start=col,color=map.pixels()[row*w+col];while(col<w&&map.pixels()[row*w+col]==color)col++;g.fill(x+start,y+row,x+col,y+row+1,color);}
    for(var label:map.labels()){int xx=x+label.x(),yy=y+label.y();g.fill(xx-6,yy-5,xx+7,yy+6,label.arm()==arm?0xff238d98:0xff26363e);g.drawCenteredString(font,Integer.toString(label.arm()+1),xx,yy-4,0xffffff);}
    g.drawString(font,"北 ↑",x+5,y+4,0xbad0db,false);
    g.drawString(font,"蓝色：当前接入口",x+5,y+imageHeight+3,0x8ed6dc,false);
    if(page==2)g.drawString(font,"黄色：选中车道的通行方向",x+5,y+imageHeight+15,0xf4cb77,false);
  }
}
