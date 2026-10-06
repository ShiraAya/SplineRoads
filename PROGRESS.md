# SR429 urgent build hotfix checkpoint

Work branch SR-0.40.15 only. Candidate 0.40.17-alpha-hotfix1, protocol63.
User reports minutes waiting to construct, mixed crossing rejected near B, and replaces vacancy prerequisite with permanent outer-lane addition 1->2/2->3/3->4 (four rejects).
Implemented lazy fallback route groups, node-adjacency traversal, mixed over/under AUTO corridors retaining final checks, explicit runtime failure replies and stage timings, owned persistent outer-lane growth with live directional limits, legacy REPLACE retained only for saved links.
Local core/model chain passed and 48 outer-addition cases passed. Actual RC1 isolated normal construction baseline did NOT reproduce the minutes-long stall: 100m/200m roads completed in about0.5/0.85s in a real ServerLevel test. Do not claim this is the user world or a before/after speedup.
This commit awaits full regression and new actual ServerLevel normal/ramp construction. No verified release or GPU/client/user-save acceptance yet.
