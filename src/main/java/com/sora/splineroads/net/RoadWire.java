package com.sora.splineroads.net;

import java.io.*;
import java.util.*;
import java.util.zip.*;
import net.minecraft.nbt.*;

/** Bounded, compressed, fragmented server messages; vanilla NBT limits stay unchanged. */
public final class RoadWire {
  public static final int PART_BYTES = 32768, MAX_PARTS = 128, MAX_RAW = 16 * 1024 * 1024;

  public record Fragment(UUID id, int index, int count, byte[] bytes) {
    public Fragment {
      if (id == null
          || count < 1
          || count > MAX_PARTS
          || index < 0
          || index >= count
          || bytes.length < 1
          || bytes.length > PART_BYTES) throw new IllegalArgumentException("无效的道路数据分包");
    }
  }

  public static List<Fragment> split(CompoundTag tag) {
    try {
      var raw = new ByteArrayOutputStream();
      NbtIo.write(tag, new DataOutputStream(raw));
      if (raw.size() > MAX_RAW) throw new IllegalArgumentException("道路编辑数据过大，请减少同一端点附近的道路数量");
      var packed = new ByteArrayOutputStream();
      try (var gzip = new GZIPOutputStream(packed)) {
        raw.writeTo(gzip);
      }
      byte[] bytes = packed.toByteArray();
      int count = (bytes.length + PART_BYTES - 1) / PART_BYTES;
      if (count > MAX_PARTS) throw new IllegalArgumentException("道路同步数据过大");
      UUID id = UUID.randomUUID();
      List<Fragment> parts = new ArrayList<>(count);
      for (int i = 0; i < count; i++)
        parts.add(
            new Fragment(
                id,
                i,
                count,
                Arrays.copyOfRange(
                    bytes, i * PART_BYTES, Math.min(bytes.length, (i + 1) * PART_BYTES))));
      return parts;
    } catch (IOException e) {
      throw new IllegalArgumentException("道路数据编码失败", e);
    }
  }

  private static final class Pending {
    final long created;
    final byte[][] parts;
    int received;

    Pending(int count, long now) {
      parts = new byte[count][];
      created = now;
    }
  }

  public static final class Assembler {
    private final Map<UUID, Pending> pending = new LinkedHashMap<>();

    public void clear() {
      pending.clear();
    }

    public CompoundTag accept(Fragment part, long now) {
      pending.values().removeIf(p -> now - p.created > 30_000_000_000L);
      if (!pending.containsKey(part.id()) && pending.size() >= 16)
        throw new IllegalArgumentException("道路同步队列已满");
      Pending p = pending.computeIfAbsent(part.id(), id -> new Pending(part.count(), now));
      if (p.parts.length != part.count()) throw new IllegalArgumentException("道路分包数量不一致");
      if (p.parts[part.index()] == null) {
        p.parts[part.index()] = part.bytes();
        p.received++;
      }
      if (p.received != p.parts.length) return null;
      pending.remove(part.id());
      try {
        var packed = new ByteArrayOutputStream();
        for (byte[] bytes : p.parts) packed.write(bytes);
        var raw = new ByteArrayOutputStream();
        try (var gzip = new GZIPInputStream(new ByteArrayInputStream(packed.toByteArray()))) {
          byte[] block = new byte[8192];
          for (int n; (n = gzip.read(block)) != -1; ) {
            if (raw.size() + n > MAX_RAW) throw new IOException("inflated road data too large");
            raw.write(block, 0, n);
          }
        }
        return NbtIo.read(
            new DataInputStream(new ByteArrayInputStream(raw.toByteArray())),
            new NbtAccounter(32L * 1024 * 1024));
      } catch (IOException e) {
        throw new IllegalArgumentException("道路同步数据损坏", e);
      }
    }
  }

  private RoadWire() {}
}
