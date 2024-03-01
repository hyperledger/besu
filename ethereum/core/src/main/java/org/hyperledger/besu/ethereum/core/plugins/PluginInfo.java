package org.hyperledger.besu.ethereum.core.plugins;

public class PluginInfo {
  String name;

  public PluginInfo(final String name) {
    this.name = name;
  }

  public String getName() {
    return name;
  }
}
