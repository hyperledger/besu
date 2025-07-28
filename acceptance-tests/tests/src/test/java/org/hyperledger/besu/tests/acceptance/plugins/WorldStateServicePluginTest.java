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

import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.datatypes.Wei;
import org.hyperledger.besu.tests.acceptance.dsl.AcceptanceTestBase;
import org.hyperledger.besu.tests.acceptance.dsl.account.Account;
import org.hyperledger.besu.tests.acceptance.dsl.blockchain.Amount;
import org.hyperledger.besu.tests.acceptance.dsl.node.BesuNode;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class WorldStateServicePluginTest extends AcceptanceTestBase {
  private BesuNode pluginNode;

  @BeforeEach
  public void setUp() throws Exception {
    pluginNode =
        besu.createQbftPluginsNode(
            "pluginNode",
            Collections.singletonList("testPlugins"),
            Collections.singletonList("--plugin-world-state-service-test-enabled=true"));

    cluster.start(pluginNode);
  }

  @Test
  public void balanceFromPluginIsCorrect() throws IOException {
    final Path resultFile =
        pluginNode.homeDirectory().resolve("plugins/TestWorldStateServicePlugin.txt");
    waitForFile(resultFile);
    final var fileContents = Files.readString(resultFile);
    final var balanceFromPlugin = Wei.fromHexString(fileContents);

    final Account account =
        Account.create(
            ethTransactions, Address.fromHexString("0xfe3b557e8fb62b89f4916b721be55ceb828dbd73"));
    cluster.verify(account.balanceEquals(Amount.wei(balanceFromPlugin.getAsBigInteger())));
  }
}
