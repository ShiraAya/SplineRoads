from pathlib import Path
p=Path('src/main/java/com/sora/splineroads/world/LaneRamps.java')
s=p.read_text(encoding='utf8')
a=s.index('      reply.putBoolean("TemporaryClosure",true);')
b=s.index('\n    }',a)
s=s[:a]+'''      double length=cut.sign()*(cut.end()-cut.begin());
      reply.putBoolean("TemporaryClosure",true);reply.putBoolean("RectangularClosure",cut.rectangular());
      reply.putDouble("ReopenAfter",cut.rectangular()?length:length-cut.transition());reply.putDouble("RestoredAfter",length);break;'''+s[b:]
p.write_text(s,encoding='utf8')
p=Path('src/main/java/com/sora/splineroads/client/LaneRampScreen.java');s=p.read_text(encoding='utf8')
s=s.replace('净空安全后渐变恢复。请预览。','净空安全后恢复。地面封闭绿化、高架直角孔区。请预览。')
a=s.index('      String closure=');b=s.index('\n',a)
s=s[:a]+'''      String closure=t.getBoolean("TemporaryClosure")?(t.getBoolean("RectangularClosure")?String.format(Locale.ROOT," 主路从 A 暂时关闭此车道，下游 %.1f 格处恢复；封闭区不收尖。",t.getDouble("RestoredAfter")):String.format(Locale.ROOT," 主路从A暂时关闭此车道，下游 %.1f 格开始恢复、%.1f 格恢复完整。",t.getDouble("ReopenAfter"),t.getDouble("RestoredAfter"))):"";'''+s[b:]
p.write_text(s,encoding='utf8')
