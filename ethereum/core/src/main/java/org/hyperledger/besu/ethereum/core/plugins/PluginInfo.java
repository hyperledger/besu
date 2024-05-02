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

/** Represents information about a plugin, including its name. */
public final class PluginInfo {
  private final String name;

  /**
   * Constructs a new PluginInfo instance with the specified name.
   *
   * @param name The name of the plugin. Cannot be null or empty.
   * @throws IllegalArgumentException if the name is null or empty.
   */
  public PluginInfo(final String name) {
    if (name == null || name.isBlank()) {
      throw new IllegalArgumentException("Plugin name cannot be null or empty.");
    }
    this.name = name;
  }

  public String name() {
    return name;
  }
}
