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

import org.immutables.value.Value;

@Value.Immutable
@Value.Style(get = {"get*", "is*"})
public interface PluginConfiguration {
  List<PluginInfo> DEFAULT_REQUESTED_PLUGINS_INFO = Collections.emptyList();
  boolean DEFAULT_EXTERNAL_PLUGINS_ENABLED = true;
  boolean DEFAULT_CONTINUE_ON_PLUGIN_ERROR = false;

  @Value.Default
  default List<PluginInfo> getRequestedPluginsInfo() {
    return DEFAULT_REQUESTED_PLUGINS_INFO;
  }

  @Value.Derived
  default List<String> getRequestedPlugins() {
    return getRequestedPluginsInfo().stream().map(PluginInfo::name).toList();
  }

  @Value.Default
  default Path getPluginsDir() {
    String pluginsDirProperty = System.getProperty("besu.plugins.dir");
    return pluginsDirProperty == null
        ? Paths.get(System.getProperty("besu.home", "."), "plugins")
        : Paths.get(pluginsDirProperty);
  }

  @Value.Default
  default boolean isExternalPluginsEnabled() {
    return DEFAULT_EXTERNAL_PLUGINS_ENABLED;
  }

  @Value.Default
  default boolean isContinueOnPluginError() {
    return DEFAULT_CONTINUE_ON_PLUGIN_ERROR;
  }
}
