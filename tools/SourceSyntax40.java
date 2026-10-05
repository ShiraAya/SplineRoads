import com.sun.source.util.JavacTask;
import javax.tools.*;
import java.nio.file.*;
import java.util.*;

/** Syntax-only guard, intentionally does NOT resolve Forge/MC types or claim a build. */
public final class SourceSyntax40 {
  public static void main(String[] args) throws Exception {
    Path root=Path.of(args.length==0?"src/main/java":args[0]);
    JavaCompiler compiler=ToolProvider.getSystemJavaCompiler();
    if(compiler==null)throw new IllegalStateException("Run with a JDK, not a JRE");
    List<Path> files;
    try(var walk=Files.walk(root)){files=walk.filter(p->p.toString().endsWith(".java")).sorted().toList();}
    DiagnosticCollector<JavaFileObject> diagnostics=new DiagnosticCollector<>();
    try(var manager=compiler.getStandardFileManager(diagnostics,Locale.ROOT,java.nio.charset.StandardCharsets.UTF_8)){
      var units=manager.getJavaFileObjectsFromPaths(files);
      var task=(JavacTask)compiler.getTask(null,manager,diagnostics,List.of("--release","17","-proc:none"),null,units);
      int parsed=0;for(var ignored:task.parse())parsed++;
      boolean failed=false;
      for(var d:diagnostics.getDiagnostics())if(d.getKind()==Diagnostic.Kind.ERROR){failed=true;System.err.println(d);}
      if(failed)throw new AssertionError("Java syntax errors");
      System.out.println("SourceSyntax40: "+parsed+" production Java units parsed with --release 17. SYNTAX ONLY; no symbol/API/type checking or Forge build.");
    }
  }
}
