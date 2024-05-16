/*
 * Copyright contributors to Hyperledger Besu.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package org.hyperledger.besu.ethereum.core.plugins;

import static java.util.Objects.requireNonNull;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.List;

/**
 * Configuration for managing plugins, including their information, detection type, and directory.
 */
public class PluginConfiguration {
  private final List<PluginInfo> requestedPlugins;
  private final Path pluginsDir;

  /**
   * Constructs a new PluginConfiguration with the specified plugin information and requestedPlugins
   * directory.
   *
   * @param requestedPlugins List of {@link PluginInfo} objects representing the requestedPlugins.
   * @param pluginsDir The directory where requestedPlugins are located.
   */
  public PluginConfiguration(final List<PluginInfo> requestedPlugins, final Path pluginsDir) {
    this.requestedPlugins = requestedPlugins;
    this.pluginsDir = pluginsDir;
  }

  /**
   * Constructs a PluginConfiguration with specified plugins using the default directory.
   *
   * @param requestedPlugins List of plugins for consideration or registration. discoverable plugins
   *     are.
   */
  public PluginConfiguration(final List<PluginInfo> requestedPlugins) {
    this.requestedPlugins = requestedPlugins;
    this.pluginsDir = PluginConfiguration.defaultPluginsDir();
  }

  /**
   * Constructs a PluginConfiguration with the specified plugins directory
   *
   * @param pluginsDir The directory where plugins are located. Cannot be null.
   */
  public PluginConfiguration(final Path pluginsDir) {
    this.requestedPlugins = null;
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
        : requestedPlugins.stream().map(PluginInfo::name).toList();
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
