package org.hyperledger.besu.ethereum.core.plugins;

import java.io.File;
import java.nio.file.Path;
import java.util.List;

public class PluginConfiguration {
  final List<PluginInfo> pluginInfos;
  final boolean strictPluginRegistrationEnabled;

  public PluginConfiguration(
      final List<PluginInfo> pluginInfos, final boolean pluginsStrictRegistration) {
    this.pluginInfos = pluginInfos;
    this.strictPluginRegistrationEnabled = pluginsStrictRegistration;
  }

  public List<PluginInfo> getPluginInfos() {
    return pluginInfos;
  }

  public boolean isStrictPluginRegistrationEnabled() {
    return strictPluginRegistrationEnabled;
  }

  public Path pluginsDir() {
    final String pluginsDir = System.getProperty("besu.plugins.dir");
    if (pluginsDir == null) {
      return new File(System.getProperty("besu.home", "."), "plugins").toPath();
    } else {
      return new File(pluginsDir).toPath();
    }
  }
}
