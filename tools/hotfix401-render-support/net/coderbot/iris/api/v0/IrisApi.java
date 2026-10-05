// OPTIONAL LEGACY-NAMESPACE API TEST DOUBLE ONLY; never shipped with the mod.
package net.coderbot.iris.api.v0;
public final class IrisApi {
  private static final IrisApi INSTANCE=new IrisApi();
  public static IrisApi getInstance(){return INSTANCE;}
  public boolean isShaderPackInUse(){return true;}
  public boolean isRenderingShadowPass(){return false;}
}
