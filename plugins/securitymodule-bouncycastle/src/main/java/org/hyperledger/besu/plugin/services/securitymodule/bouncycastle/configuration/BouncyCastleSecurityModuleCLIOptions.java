package org.hyperledger.besu.plugin.services.securitymodule.bouncycastle.configuration;

import java.io.File;

import picocli.CommandLine.Option;

public class BouncyCastleSecurityModuleCLIOptions {
  private File privateKeyFile = null;

  @Option(
      names = {"--plugin-securitymodule-bc-private-key-file"},
      paramLabel = "<PATH>",
      description =
          "The node's private key file (default: a file named \"key\" in the Besu data folder)")
  public void setPrivateKeyFile(final File privateKeyFile) {
    this.privateKeyFile = privateKeyFile;
  }

  public File getPrivateKeyFile() {
    return privateKeyFile;
  }
}
