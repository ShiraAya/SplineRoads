package com.sora.splineroads.core;

import com.sora.splineroads.core.RoadProfile.*;

/** Tunnel-only material policy. Keep authored lane axes and transition divider IDs.
 * Yellow markings remain yellow; a planted median becomes a physical central rail.
 * Do not move a live lane sideways merely because its neighbour is inside a tunnel. */
final class TunnelMedian {
  static Layout apply(Layout base) {
    var c=base.catalog();
    if(!c.twoWay()||c.median()!=Median.GREEN)return base;
    return new Layout(new Catalog(c.type(),c.lanes(),true,Median.RAIL,c.shoulder()),
        base.laneWidth(),base.median(),base.motorMin(),base.motorMax(),
        base.cycleWidth(),base.curbWidth(),base.shoulderWidth(),base.outside(),
        base.dividers(),base.medianCenter());
  }
  private TunnelMedian(){}
}
