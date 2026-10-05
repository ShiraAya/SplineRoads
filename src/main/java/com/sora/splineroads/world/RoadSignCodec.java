package com.sora.splineroads.world;
import com.sora.splineroads.core.RoadSigns.*;
import net.minecraft.nbt.*;
import java.util.*;
public final class RoadSignCodec {
  public static CompoundTag write(Attachment a){var t=new CompoundTag();t.putInt("Id",a.id());t.putString("Model",a.model());t.putString("Mount",a.mount().name());t.putInt("Station",a.station());t.putDouble("Position",a.position());t.putDouble("Lateral",a.lateral());t.putDouble("Height",a.height());t.putDouble("Scale",a.scale());t.putBoolean("Reverse",a.reverse());var text=new ListTag();for(var s:a.text())text.add(StringTag.valueOf(s));t.put("Text",text);return t;}
  public static ListTag write(List<Attachment> a){var list=new ListTag();for(var v:a)list.add(write(v));return list;}
  public static Attachment read(CompoundTag t){var text=t.getList("Text",Tag.TAG_STRING);if(text.size()>16)throw new IllegalArgumentException("路牌文字项过多");return new Attachment(t.getInt("Id"),t.getString("Model"),Mount.valueOf(t.getString("Mount")),t.getInt("Station"),t.getDouble("Position"),t.getDouble("Lateral"),t.getDouble("Height"),t.getDouble("Scale"),t.getBoolean("Reverse"),text.stream().map(Tag::getAsString).toList());}
  public static List<Attachment> read(ListTag t){if(t.size()>128)throw new IllegalArgumentException("路牌记录过多");return t.stream().map(v->read((CompoundTag)v)).toList();}
  private RoadSignCodec(){}
}
