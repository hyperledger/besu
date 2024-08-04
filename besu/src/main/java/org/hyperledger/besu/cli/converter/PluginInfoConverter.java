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
package org.hyperledger.besu.cli.converter;

import org.hyperledger.besu.ethereum.core.plugins.PluginInfo;

import java.util.List;
import java.util.stream.Stream;

import picocli.CommandLine;

/**
 * Converts a comma-separated string into a list of {@link PluginInfo} objects. This converter is
 * intended for use with PicoCLI to process command line arguments that specify plugin information.
 */
public class PluginInfoConverter implements CommandLine.ITypeConverter<List<PluginInfo>> {
  /** Default Constructor. */
  public PluginInfoConverter() {}

  /**
   * Converts a comma-separated string into a list of {@link PluginInfo}.
   *
   * @param value The comma-separated string representing plugin names.
   * @return A list of {@link PluginInfo} objects created from the provided string.
   */
  @Override
  public List<PluginInfo> convert(final String value) {
    if (value == null || value.isBlank()) {
      return List.of();
    }
    return Stream.of(value.split(",")).map(String::trim).map(this::toPluginInfo).toList();
  }

  /**
   * Creates a {@link PluginInfo} object from a plugin name.
   *
   * @param pluginName The name of the plugin.
   * @return A {@link PluginInfo} object representing the plugin.
   */
  private PluginInfo toPluginInfo(final String pluginName) {
    return new PluginInfo(pluginName);
  }
}
