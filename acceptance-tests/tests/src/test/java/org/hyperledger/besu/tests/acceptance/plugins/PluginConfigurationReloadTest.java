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

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import org.hyperledger.besu.tests.acceptance.dsl.AcceptanceTestBase;
import org.hyperledger.besu.tests.acceptance.dsl.node.BesuNode;

import java.io.File;
import java.util.Arrays;
import java.util.Collections;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class PluginConfigurationReloadTest extends AcceptanceTestBase {
  private BesuNode pluginNode;

  @BeforeEach
  public void setUp() throws Exception {
    pluginNode =
        besu.createQbftPluginsNode(
            "pluginNode",
            Collections.singletonList("testPlugins"),
            Collections.emptyList(),
            "PLUGINS");

    cluster.start(pluginNode);

    pluginNode.verify(net.netServicesAllActive());
  }

  @Test
  public void reloadSinglePluginConfiguration() {

    final var reloadConfTx =
        plugins.reloadConfiguration(TestReloadConfigurationPlugin1.class.getName());

    final var response = pluginNode.execute(reloadConfTx);

    assertThat(response).isEqualTo("Success");
    assertConfigReloadForPlugins(1);
  }

  @Test
  public void reloadAllPluginConfigurations() {

    final var reloadConfTx = plugins.reloadConfiguration();

    final var response = pluginNode.execute(reloadConfTx);

    assertThat(response).isEqualTo("Success");
    assertConfigReloadForPlugins(1, 2);
  }

  public void assertConfigReloadForPlugins(final int... pluginIds) {
    // use await since the reload is done asynchronously and the file could take more that than the
    // first check to appear
    await()
        .ignoreExceptions() // consider assertion failures as return false
        .until(
            () -> {
              final var reloadConfFiles =
                  pluginNode
                      .homeDirectory()
                      .resolve("plugins")
                      .toFile()
                      .listFiles((dir, name) -> name.startsWith("reloadConfiguration."));

              assertThat(reloadConfFiles).hasSize(pluginIds.length);

              final var foundFilenames = Arrays.stream(reloadConfFiles).map(File::getName).toList();
              assertThat(foundFilenames)
                  .containsExactlyInAnyOrderElementsOf(
                      Arrays.stream(pluginIds).mapToObj(i -> "reloadConfiguration." + i).toList());
              return true;
            });
  }
}
