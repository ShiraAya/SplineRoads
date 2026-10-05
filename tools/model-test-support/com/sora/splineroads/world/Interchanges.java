package com.sora.splineroads.world;
import com.sora.splineroads.core.RoadGeometry.Mesh;
/** TEST ONLY: no world/assembly operation is simulated. Unrelated-junction scope tests
 * must never reach this operation; core clearance is tested separately. */
public final class Interchanges {
 public static void checkExternal(Mesh a,Mesh b){throw new UnsupportedOperationException("assembly external clearance not exercised by this test adapter");}
}
