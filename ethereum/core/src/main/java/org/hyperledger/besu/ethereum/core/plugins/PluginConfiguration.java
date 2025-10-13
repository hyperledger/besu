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

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.List;

public class PluginConfiguration {
  private final List<PluginInfo> requestedPlugins;
  private final Path pluginsDir;
  private final boolean externalPluginsEnabled;
  private final boolean continueOnPluginError;

  public PluginConfiguration(
      final List<PluginInfo> requestedPlugins,
      final Path pluginsDir,
      final boolean externalPluginsEnabled,
      final boolean continueOnPluginError) {
    this.requestedPlugins = requestedPlugins;
    this.pluginsDir = pluginsDir;
    this.externalPluginsEnabled = externalPluginsEnabled;
    this.continueOnPluginError = continueOnPluginError;
  }

  public List<String> getRequestedPlugins() {
    return requestedPlugins == null
        ? Collections.emptyList()
        : requestedPlugins.stream().map(PluginInfo::name).toList();
  }

  public Path getPluginsDir() {
    return pluginsDir;
  }

  public boolean isExternalPluginsEnabled() {
    return externalPluginsEnabled;
  }

  public boolean isContinueOnPluginError() {
    return continueOnPluginError;
  }

  public static Path defaultPluginsDir() {
    String pluginsDirProperty = System.getProperty("besu.plugins.dir");
    return pluginsDirProperty == null
        ? Paths.get(System.getProperty("besu.home", "."), "plugins")
        : Paths.get(pluginsDirProperty);
  }

  public static Builder builder() {
    return new Builder();
  }

  public static class Builder {
    private List<PluginInfo> requestedPlugins;
    private Path pluginsDir;
    private boolean externalPluginsEnabled = true;
    private boolean continueOnPluginError = false;

    public Builder requestedPlugins(final List<PluginInfo> requestedPlugins) {
      this.requestedPlugins = requestedPlugins;
      return this;
    }

    public Builder pluginsDir(final Path pluginsDir) {
      this.pluginsDir = pluginsDir;
      return this;
    }

    public Builder externalPluginsEnabled(final boolean externalPluginsEnabled) {
      this.externalPluginsEnabled = externalPluginsEnabled;
      return this;
    }

    public Builder continueOnPluginError(final boolean continueOnPluginError) {
      this.continueOnPluginError = continueOnPluginError;
      return this;
    }

    public PluginConfiguration build() {
      if (pluginsDir == null) {
        pluginsDir = PluginConfiguration.defaultPluginsDir();
      }
      return new PluginConfiguration(
          requestedPlugins, pluginsDir, externalPluginsEnabled, continueOnPluginError);
    }
  }
}
