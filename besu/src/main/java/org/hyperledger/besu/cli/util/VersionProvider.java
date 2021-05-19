/*
 * Copyright ConsenSys AG.
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
package org.hyperledger.besu.cli.util;

import org.hyperledger.besu.BesuInfo;
import org.hyperledger.besu.plugin.services.PluginVersionsProvider;

import java.util.stream.Stream;

import picocli.CommandLine;

public class VersionProvider implements CommandLine.IVersionProvider {
  private final PluginVersionsProvider pluginVersionsProvider;

  public VersionProvider(final PluginVersionsProvider pluginVersionsProvider) {
    this.pluginVersionsProvider = pluginVersionsProvider;
  }

  @Override
  public String[] getVersion() {
    // the PluginVersionsProvider has registered plugins and their versions by this time.
    return Stream.concat(
            Stream.of(BesuInfo.version()), pluginVersionsProvider.getPluginVersions().stream())
        .toArray(String[]::new);
  }
}
