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
  static RoadTransitions.Section seam(RoadTransitions.Section section){
    var layout=section.layout(false);var c=layout.catalog();
    if(!c.twoWay()||c.median()!=Median.GREEN)return section;
    var rail=RoadLanes.carrier(c.type(),RoadLanes.counts(section.settings(false)),Median.RAIL);
    var old=section.port();
    var port=new RoadTransitions.Port(layout.motorMin(),layout.motorMax(),layout.median(),layout.dividers(),
        old==null?layout.curbWidth():old.curbLeft(),old==null?layout.curbWidth():old.curbRight(),layout.medianCenter());
    return new RoadTransitions.Section(rail,section.width(),section.cycle(),section.cycleRail(),section.curb(),
        section.outerRail(),section.sidewalk(),section.cycleAsphalt(),port,section.streetscape(),section.lanes());
  }
  private TunnelMedian(){}
}
