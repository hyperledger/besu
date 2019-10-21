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

import java.util.ArrayList;
import java.util.List;

import picocli.CommandLine;

public class VersionProvider implements CommandLine.IVersionProvider {
  private final String[] versions;

  public VersionProvider(final List<String> pluginVersions) {
    final List<String> versionsList = new ArrayList<>();
    versionsList.add(BesuInfo.version());
    if (!pluginVersions.isEmpty()) {
      versionsList.addAll(pluginVersions);
    }
    versions = versionsList.toArray(String[]::new);
  }

  @Override
  public String[] getVersion() {
    return versions;
  }
}
