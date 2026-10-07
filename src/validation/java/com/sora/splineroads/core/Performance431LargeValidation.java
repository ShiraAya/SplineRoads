package com.sora.splineroads.core;
/** Cross the 1024-sample hybrid threshold; compare both accepted and rejected
 * long paths against exactly the previous predicates, not a weakened oracle. */
public final class Performance431LargeValidation {
 public static void main(String[] args){
  int checks=0;
  for(int k=0;k<24;k++){
   var mesh=Performance431Validation.ribbon(1023+k*53,k%3==0?20:0,k%2==0);
   String oldSelf=Performance431Validation.error(()->Performance431Validation.oldSelf(mesh,4));
   String indexedSelf=Performance431Validation.error(()->RoadRibbon.checkSelfIntersections(mesh,4));
   if(!oldSelf.equals(indexedSelf))throw new AssertionError("long self "+k+": "+oldSelf+" / "+indexedSelf);
   String oldVolume=Performance431Validation.error(()->Performance431Validation.oldVolume(mesh));
   String indexedVolume=Performance431Validation.error(()->LaneRampPaths.checkVolume(mesh));
   if(!oldVolume.equals(indexedVolume))throw new AssertionError("long volume "+k+": "+oldVolume+" / "+indexedVolume);
   checks+=2;
  }
  System.out.println("Performance431LargeValidation: "+checks+" long-path exact predicate checks PASS");
 }
}
