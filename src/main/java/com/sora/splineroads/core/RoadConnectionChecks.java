package com.sora.splineroads.core;

import com.sora.splineroads.core.RoadGeometry.*;

/** Identical preview/build preflight for an ordinary road's existing linked endpoints.
 * A manually selected shape is not automatically a valid seam. Do not merely show a
 * green client mesh then discover the same geometry is invalid only on the server. */
public final class RoadConnectionChecks {
  public static void require(RoadPlanner.Plan plan,RoadPlanner.Hint a,RoadPlanner.Hint b){
    if(plan.mesh().closed()||plan.settings().laneRamp()||plan.settings().style().ramp())return;
    endpoint("A",plan.mesh().first(),plan.start().grade(),a);
    endpoint("B",plan.mesh().last(),plan.end().grade(),b);
  }
  private static void endpoint(String name,Sample actual,double grade,RoadPlanner.Hint required){
    if(required==null||!required.linked())return;
    if(actual.center().distance(required.node().position())>1e-6)
      throw new IllegalArgumentException(name+" 端接点高程/位置不一致；预览不允许建造，请调整端点");
    V forward=actual.left().left().mul(-1).horizontalUnit();
    if(required.headingLocked()&&forward.dot(required.node().direction())<.99999999
        ||required.gradeLocked()&&Math.abs(grade-required.node().grade())>1e-5)
      throw new IllegalArgumentException(name+" 端接缝方向或坡度不连续；请切回智能模式或调整端点");
  }
  private RoadConnectionChecks(){}
}
