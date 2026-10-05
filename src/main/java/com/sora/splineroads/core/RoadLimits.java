package com.sora.splineroads.core;

/** Shared limits for preview, authoritative construction and persisted alignments. */
public final class RoadLimits {
  public static final int MAX_ENDPOINT_DISTANCE = 2048;
  public static final int MAX_PATH_LENGTH = 4096;
  public static final int MAX_SAMPLES = 16384;
  public static final int MAX_BODY_CELLS = 262144;
  public static final int MAX_EDIT_CELLS = 1_000_000;
  public static final int MAX_MULTI_INTERCHANGE_EDIT_CELLS = 2_000_000;
  public static final int MAX_MULTI_INTERCHANGE_SCAN_CELLS = 8_000_000;
  public static final int MAX_WORK_CHUNKS = 2048;
  public static final int MAX_MULTI_INTERCHANGE_WORK_CHUNKS = 4096;

  private RoadLimits() {}
}
