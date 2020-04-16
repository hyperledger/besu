package org.hyperledger.besu.plugin.services.securitymodule.localfile;

import picocli.CommandLine.Option;

public interface TestCommand {
  @Option(names = {"--id"})
  int getId();
}
