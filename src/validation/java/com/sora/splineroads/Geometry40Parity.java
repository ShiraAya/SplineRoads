package com.sora.splineroads;
import com.sora.splineroads.core.*;import com.sora.splineroads.core.RoadGeometry.*;import java.io.*;import java.security.*;import java.util.*;
/** Differential fixture: execute unchanged test against original/new geometry classes. */
public final class Geometry40Parity {
 public static void main(String[] args)throws Exception {
  var bytes=new ByteArrayOutputStream();var out=new DataOutputStream(bytes);int valid=0,rejected=0;
  for(Style style:List.of(Style.O2_ONE,Style.O4_YELLOW,Style.H4_RAIL,Style.R1))for(Mode mode:List.of(Mode.STRAIGHT,Mode.CURVE,Mode.ARC,Mode.RING))for(double grade:new double[]{0,.05})for(double origin:new double[]{0,100000}){
   out.writeUTF(style+":"+mode+":"+grade+":"+origin);
   try {
    var settings=new Settings(mode,style,RoadProfile.width(style,RoadProfile.Options.DEFAULT,4),1,.35,90);
    var mesh=RoadGeometry.build(new Node(new V(origin,100,origin),0,grade),new Node(new V(origin+40,100+grade*80,origin+80),0,grade),settings);
    out.writeUTF("valid");out.writeInt(mesh.samples().size());out.writeDouble(mesh.length());
    for(var s:mesh.samples()){write(out,s.center());write(out,s.left());out.writeDouble(s.distance());out.writeDouble(s.halfWidth());}write(out,mesh.min());write(out,mesh.max());valid++;
   }catch(IllegalArgumentException e){out.writeUTF(e.getClass().getName());out.writeUTF(e.getMessage());rejected++;}
  }
  out.flush();System.out.println("Geometry40Parity: valid="+valid+", rejected="+rejected+", sha256="+HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes.toByteArray())));
 }
 private static void write(DataOutputStream d,V v)throws IOException{d.writeDouble(v.x());d.writeDouble(v.y());d.writeDouble(v.z());}
}
