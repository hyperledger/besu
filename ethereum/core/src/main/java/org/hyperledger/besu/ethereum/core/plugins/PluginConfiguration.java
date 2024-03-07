package org.hyperledger.besu.ethereum.core.plugins;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Configuration for managing plugins, including their information, detection type, and directory.
 */
public class PluginConfiguration {
  private final List<PluginInfo> pluginInfos;
  private final DetectionType detectionType;
  private final Path pluginsDir;

  /**
   * Constructs a new PluginConfiguration with the specified plugin information, detection type, and
   * plugins directory.
   *
   * @param pluginInfos List of {@link PluginInfo} objects representing the plugins.
   * @param detectionType The {@link DetectionType} indicating how plugins should be detected.
   * @param pluginsDir The directory where plugins are located.
   */
  public PluginConfiguration(
      final List<PluginInfo> pluginInfos,
      final DetectionType detectionType,
      final Path pluginsDir) {
    this.pluginInfos =
        Collections.unmodifiableList(
            Objects.requireNonNull(pluginInfos, "pluginInfos cannot be null"));
    this.detectionType = Objects.requireNonNull(detectionType, "detectionType cannot be null");
    this.pluginsDir = Objects.requireNonNull(pluginsDir, "pluginsDir cannot be null");
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

  /**
   * Returns the default plugins directory based on system properties.
   *
   * @return The default {@link Path} to the plugin's directory.
   */
  public static Path defaultPluginsDir() {
    final String pluginsDirProperty = System.getProperty("besu.plugins.dir");
    if (pluginsDirProperty == null) {
      return Paths.get(System.getProperty("besu.home", "."), "plugins");
    } else {
      return Paths.get(pluginsDirProperty);
    }
  }

  /**
   * Enumerates the types of plugin detection mechanisms.
   *
   * <p>This enum defines how plugins should be detected and loaded by the system.
   */
  public enum DetectionType {
    /**
     * Indicates that all discoverable plugins should be loaded.
     *
     * <p>When set to {@code ALL}, the system will attempt to load all plugins found within the
     * specified plugins directory, without requiring explicit configuration for each plugin.
     */
    ALL,

    /**
     * Indicates that only explicitly specified plugins should be loaded.
     *
     * <p>When set to {@code EXPLICIT}, the system will only load plugins that have been explicitly
     * listed in the configuration. This allows for more controlled and selective loading of
     * plugins.
     */
    EXPLICIT
  }
}
