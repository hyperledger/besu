package org.hyperledger.besu.ethereum.core.plugins;

import java.io.File;
import java.nio.file.Path;
import java.util.List;

public class PluginConfiguration {
  final List<PluginInfo> pluginInfos;
  final DetectionType detectionType;
  final Path pluginsDir;

  public PluginConfiguration(
      final List<PluginInfo> pluginInfos,
      final DetectionType pluginsStrictRegistration,
      final Path pluginsDir) {
    this.pluginInfos = pluginInfos;
    this.detectionType = pluginsStrictRegistration;
    this.pluginsDir = pluginsDir;
  }

  public List<PluginInfo> getPluginInfos() {
    return pluginInfos;
  }

  public DetectionType getDetectionType() {
    return detectionType;
  }

  public Path getPluginsDir() {
    return pluginsDir;
  }

  public static Path defaultPluginsDir() {
    final String pluginsDir = System.getProperty("besu.plugins.dir");
    if (pluginsDir == null) {
      return new File(System.getProperty("besu.home", "."), "plugins").toPath();
    } else {
      return new File(pluginsDir).toPath();
    }
  }

  public enum DetectionType {
    ALL,
    EXPLICIT
  }
}
