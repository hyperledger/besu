package org.hyperledger.besu.cli.options.unstable;

import net.consensys.quorum.mainnet.launcher.options.Options;
import picocli.CommandLine.Option;

/** Unstable support for eth1/2 merge, rayonism hackathon: https://rayonism.io/ */
public class RayonismMergeOptions implements Options {
  // To make it easier for tests to reset the value to default
  public static final boolean MERGE_ENABLED_DEFAULT_VALUE = false;

  @Option(
      hidden = true,
      names = {"--Xmerge-support"},
      description = "Enable experimental support for eth1/eth2 merge (default: ${DEFAULT-VALUE})",
      arity = "1")
  @SuppressWarnings("FieldCanBeFinal")
  private static boolean mergeEnabled = MERGE_ENABLED_DEFAULT_VALUE;

  public static RayonismMergeOptions create() {
    return new RayonismMergeOptions();
  }

  public Boolean isMergeEnabled() {
    return mergeEnabled;
  }
}
