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

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;

import org.hyperledger.besu.tests.acceptance.dsl.AcceptanceTestBase;
import org.hyperledger.besu.tests.acceptance.dsl.node.BesuNode;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class BlockchainServicePluginTest extends AcceptanceTestBase {
  private BesuNode pluginNode;

  @BeforeEach
  public void setUp() throws Exception {
    pluginNode =
        besu.createQbftPluginsNode(
            "pluginNode",
            Collections.singletonList("testPlugins"),
            Collections.singletonList("--plugin-blockchain-service-test-enabled=true"),
            "DEBUG");

    cluster.start(pluginNode);
  }

  @Test
  public void hardforkListIsCorrect() throws IOException {
    final Path hardforkListFile = pluginNode.homeDirectory().resolve("plugins/hardfork.list");
    waitForFile(hardforkListFile);
    final var fileContents = Files.readAllLines(hardforkListFile);

    final var expectedLines =
        List.of("0,BERLIN,BERLIN,BERLIN", "1,BERLIN,BERLIN,LONDON", "2,LONDON,LONDON,LONDON");

    for (int i = 0; i < expectedLines.size(); i++) {
      assertThat(fileContents.get(i)).isEqualTo(expectedLines.get(i));
    }
  }
}
