package org.hyperledger.besu.config.experimental;

import picocli.CommandLine.Option;

/** Experimental support for eth1/2 merge, rayonism hackathon: https://rayonism.io/ */
public class RayonismMerge {
  // To make it easier for tests to reset the value to default
  public static final boolean MERGE_ENABLED_DEFAULT_VALUE = false;

  @Option(
      hidden = true,
      names = {"--Xmerge-support"},
      description = "Enable experimental support for eth1/eth2 merge (default: ${DEFAULT-VALUE})",
      arity = "1")
  public static boolean mergeEnabled = MERGE_ENABLED_DEFAULT_VALUE;
}
