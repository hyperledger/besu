package org.hyperledger.besu.cli.options.unstable;

import picocli.CommandLine;

public class LightNodeOptions {

  @CommandLine.Option(
      names = {"--Xlight-node-enabled"},
      hidden = true,
      description =
          "Enable light node mode. This cannot connect directly to mainnet. (default: ${DEFAULT-VALUE})")
  private final Boolean lightNodeEnabled = null;

  public static LightNodeOptions create() {
    return new LightNodeOptions();
  }

  public Boolean getLightNodeEnabled() {
    return lightNodeEnabled;
  }
}
