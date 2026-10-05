// OPTIONAL API TEST DOUBLE ONLY. Not a shader engine; never on production/JAR classpath.
package net.irisshaders.iris.api.v0;
public final class IrisApi {
  public static boolean active,shadow,fail;
  private static final IrisApi INSTANCE=new IrisApi();
  public static IrisApi getInstance(){return INSTANCE;}
  public boolean isShaderPackInUse(){if(fail)throw new IllegalStateException("deliberate API failure");return active;}
  public boolean isRenderingShadowPass(){return shadow;}
}
