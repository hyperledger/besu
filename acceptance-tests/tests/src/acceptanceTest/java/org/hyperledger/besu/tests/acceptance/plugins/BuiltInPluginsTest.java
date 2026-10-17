/*
 * Copyright contributors to Besu.
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
package org.hyperledger.besu.tests.acceptance.plugins;

import org.hyperledger.besu.tests.acceptance.dsl.AcceptanceTestBase;
import org.hyperledger.besu.tests.acceptance.dsl.node.BesuNode;
import org.hyperledger.besu.tests.acceptance.dsl.node.configuration.BesuNodeConfigurationBuilder;

import java.util.List;

import org.junit.jupiter.api.Test;

/**
 * The built-in plugins, RocksDB among them, go through the plugin context like external plugins but
 * are not subject to the options that select external plugins: a node runs on RocksDB with external
 * plugins disabled, and with the plugin selection naming only an external plugin.
 */
public class BuiltInPluginsTest extends AcceptanceTestBase {

  @Test
  public void nodeRunsOnRocksDbWithExternalPluginsDisabled() throws Exception {
    final BesuNode node =
        besu.create(
            new BesuNodeConfigurationBuilder()
                .name("node1")
                .extraCLIOptions(List.of("--Xplugins-external-enabled=false"))
                .build());
    cluster.start(node);

    node.verify(net.awaitPeerCount(0));
  }

  @Test
  public void nodeRunsOnRocksDbWhenThePluginSelectionNamesOnlyAnExternalPlugin() throws Exception {
    final BesuNode node =
        besu.create(
            new BesuNodeConfigurationBuilder()
                .name("node1")
                .plugins(List.of("testPlugins"))
                .requestedPlugins(List.of("TestPicoCLIPlugin"))
                .build());
    cluster.start(node);

    node.verify(net.awaitPeerCount(0));
  }
}
