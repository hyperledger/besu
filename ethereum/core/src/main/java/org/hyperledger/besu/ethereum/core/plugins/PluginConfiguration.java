package org.hyperledger.besu.ethereum.core.plugins;

import static java.util.Objects.requireNonNull;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Configuration for managing plugins, including their information, detection type, and directory.
 */
public class PluginConfiguration {
  private static final boolean DEFAULT_REGISTER_ALL = false;
  private final List<PluginInfo> requestedPlugins;
  private final boolean strictRegistration;
  private final Path pluginsDir;

  /**
   * Constructs a new PluginConfiguration with the specified plugin information, detection type, and
   * requestedPlugins directory.
   *
   * @param requestedPlugins List of {@link PluginInfo} objects representing the requestedPlugins.
   * @param strictRegistration If true, only listed plugins are registered; otherwise, all
   *     discoverable plugins are.
   * @param pluginsDir The directory where requestedPlugins are located.
   */
  public PluginConfiguration(
      final List<PluginInfo> requestedPlugins,
      final boolean strictRegistration,
      final Path pluginsDir) {
    this.requestedPlugins = requestedPlugins;
    this.strictRegistration = strictRegistration;
    this.pluginsDir = pluginsDir;
  }

  /**
   * Constructs a PluginConfiguration with specified plugins and registration type, using the
   * default directory.
   *
   * @param requestedPlugins List of plugins for consideration or registration.
   * @param strictRegistration If true, only listed plugins are registered; otherwise, all
   *     discoverable plugins are.
   */
  public PluginConfiguration(
      final List<PluginInfo> requestedPlugins, final boolean strictRegistration) {
    this.requestedPlugins = requestedPlugins;
    this.strictRegistration = strictRegistration;
    this.pluginsDir = PluginConfiguration.defaultPluginsDir();
  }

  /**
   * Constructs a PluginConfiguration with the specified plugins directory, defaulting to non-strict
   * registration.
   *
   * @param pluginsDir The directory where plugins are located. Cannot be null.
   */
  public PluginConfiguration(final Path pluginsDir) {
    this.requestedPlugins = null;
    this.strictRegistration = DEFAULT_REGISTER_ALL;
    this.pluginsDir = requireNonNull(pluginsDir);
  }

  /**
   * Returns the names of requested plugins, or an empty list if none.
   *
   * @return List of requested plugin names, never {@code null}.
   */
  public List<String> getRequestedPlugins() {
    return requestedPlugins == null
        ? Collections.emptyList()
        : requestedPlugins.stream().map(PluginInfo::name).collect(Collectors.toList());
  }

  public boolean isStrictRegistration() {
    return strictRegistration;
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
}
