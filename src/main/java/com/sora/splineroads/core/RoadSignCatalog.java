package com.sora.splineroads.core;
import com.google.gson.*;
import com.sora.splineroads.core.RoadGeometry.V;
import com.sora.splineroads.core.RoadSurface.*;
import com.sora.splineroads.core.RoadStructures.Part;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
/** Immutable CB/Yunbei catalog; only the selected meshes are loaded, with a bounded cache. */
public final class RoadSignCatalog {
  public record Field(String key,String label,double x,double y,double scale,String align,int color,String initial){}
  public record Model(String id,String label,double width,double height,double depth,List<Field> fields){}
  public static final List<Model> MODELS=loadCatalog();
  private static final Map<String,Model> BY_ID=new HashMap<>();
  static {for(var model:MODELS)BY_ID.put(model.id(),model);}
  public static Model get(String id){var m=BY_ID.get(id);if(m==null)throw new IllegalArgumentException("未知路牌或设备："+id);return m;}
  private static List<Model> loadCatalog(){
    try(var in=RoadSignCatalog.class.getResourceAsStream("/assets/splineroads/models/road/cb_signs.json")){
      if(in==null)throw new IOException("missing sign catalog");var out=new ArrayList<Model>();
      for(var value:JsonParser.parseReader(new InputStreamReader(in,StandardCharsets.UTF_8)).getAsJsonArray()){
        var o=value.getAsJsonObject();var fields=new ArrayList<Field>();
        for(var item:o.getAsJsonArray("fields")){var f=item.getAsJsonObject();fields.add(new Field(f.get("key").getAsString(),f.get("label").getAsString(),f.get("x").getAsDouble(),f.get("y").getAsDouble(),f.get("scale").getAsDouble(),f.get("align").getAsString(),f.get("color").getAsInt(),f.has("initial")?f.get("initial").getAsString():""));}
        out.add(new Model(o.get("id").getAsString(),o.get("label").getAsString(),o.get("width").getAsDouble(),o.get("height").getAsDouble(),o.get("depth").getAsDouble(),List.copyOf(fields)));
      }return List.copyOf(out);
    }catch(IOException e){throw new IllegalStateException(e);}
  }
  private static final Map<String,List<Face>> CACHE=new LinkedHashMap<>(32,.75f,true){protected boolean removeEldestEntry(Map.Entry<String,List<Face>> e){return size()>64;}};
  private static synchronized List<Face> mesh(String id){return CACHE.computeIfAbsent(id,key->{
    get(key);try(var stream=RoadSignCatalog.class.getResourceAsStream("/assets/splineroads/models/road/cb_signs/"+key+".mesh")){
      if(stream==null)throw new IOException("missing CB mesh");var in=new DataInputStream(stream);int count=in.readInt();if(count<1||count>10000)throw new IOException("invalid mesh");var out=new ArrayList<Face>();
      for(int i=0;i<count;i++){var p=new ArrayList<V>();var uv=new ArrayList<UV>();for(int j=0;j<4;j++){p.add(new V(in.readFloat(),in.readFloat(),in.readFloat()));uv.add(new UV(in.readFloat(),in.readFloat()));}out.add(new Face(List.copyOf(p),0xFFFFFF,false,Texture.CB_SIGNS,List.copyOf(uv)));}return List.copyOf(out);
    }catch(IOException e){throw new IllegalStateException(e);}
  });}
  public static List<Face> faces(Part p){var m=get(p.model());double scale=p.height()/m.height();V right=p.b().sub(p.a()).horizontalUnit(),front=right.left(),origin=p.a().add(p.b()).mul(.5);
    return mesh(m.id()).stream().map(q->new Face(q.points().stream().map(v->origin.add(right.mul(v.x()*scale)).add(front.mul(v.z()*scale)).add(new V(0,v.y()*scale,0))).toList(),q.color(),false,q.texture(),q.uv())).toList();
  }
  public static double textWidth(Model m,Field f){
    if(f.key().startsWith("expressway"))return .55;
    double edge=f.align().equals("left")?m.width()/2-f.x():f.align().equals("right")?m.width()/2+f.x():2*Math.min(m.width()/2-f.x(),m.width()/2+f.x());
    double width=Math.min(m.width()*.8,Math.max(.2,edge-.08));
    for(var other:m.fields())if(other!=f&&Math.abs(f.y()-other.y())<.2&&Math.abs(f.x()-other.x())>.05){double distance=Math.abs(f.x()-other.x());if(f.align().equals("center")||f.align().equals("left")&&other.x()>f.x()||f.align().equals("right")&&other.x()<f.x())width=Math.min(width,distance*.8);}
    return Math.max(.15,width);
  }
  public static double fontScale(Model m,Field f,String text,int pixels){
    long cjk=text.codePoints().filter(c->c>=0x3400&&c<=0x9fff||c>=0x3040&&c<=0x30ff).count();
    long latin=text.codePoints().filter(c->c>='A'&&c<='Z'||c>='a'&&c<='z').count();
    double factor=f.key().startsWith("expressway")?.68:cjk==0&&latin>=2?.58:.82;
    return Math.min(f.scale()*factor,textWidth(m,f)/Math.max(1,pixels));
  }
  private RoadSignCatalog(){}
}
